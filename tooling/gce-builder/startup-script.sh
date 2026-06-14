#!/usr/bin/env bash
# Runs as root on every VM boot (passed via --metadata-from-file startup-script=).
# Must be idempotent: spot VMs are re-created and the cache disk is re-attached.
set -euo pipefail
exec > >(tee -a /var/log/lunar-bootstrap.log) 2>&1
echo "=== lunar-builder bootstrap $(date -u) ==="

export DEBIAN_FRONTEND=noninteractive

# --- packages (skip if already present so reboots are fast) ---------------------------------
# fontconfig + a base font are REQUIRED: IntelliJ editor/inlay tests initialize an editor color
# scheme, which calls into AWT FontManager. On a headless minimal image with no fonts this throws
# "Fontconfig head is null, check your fonts or fonts configuration" and every editor-touching
# test fails. fonts-dejavu-core provides a font set; fontconfig provides the config/cache.
if ! command -v git >/dev/null || ! command -v rsync >/dev/null || ! command -v fc-cache >/dev/null \
   || ! command -v lua5.4 >/dev/null; then
  apt-get update -y
  # lua5.4 + lua-socket: the debug-harness integration test execs an interpreter (sync wires
  # ~/bin/lua -> it) and its mobdebug bootstrap requires LuaSocket for the DBGp TCP connection.
  apt-get install -y --no-install-recommends \
    git rsync wget ca-certificates python3 tar fontconfig fonts-dejavu-core lua5.4 lua-socket
  fc-cache -f >/dev/null 2>&1 || true
fi

# --- persistent cache disk: format once, then mount at /opt/cache --------------------------
DEVICE="/dev/disk/by-id/google-lunar-cache"
MOUNT="/opt/cache"
if [ -b "$DEVICE" ]; then
  if ! blkid "$DEVICE" >/dev/null 2>&1; then
    echo "Formatting fresh cache disk $DEVICE"
    mkfs.ext4 -F -m 0 -E lazy_itable_init=0,lazy_journal_init=0 "$DEVICE"
  fi
  mkdir -p "$MOUNT"
  mountpoint -q "$MOUNT" || mount -o discard,defaults "$DEVICE" "$MOUNT"
  # Sticky + world-writable (like /tmp) so any SSH/OS-Login user can own its gradle dir.
  chmod 1777 "$MOUNT"
  mkdir -p "$MOUNT/gradle"
  chmod 1777 "$MOUNT/gradle"
else
  echo "WARNING: cache disk $DEVICE not attached; builds will use ephemeral GRADLE_USER_HOME"
fi

# --- JDK 21 (Amazon Corretto, matching the project's JAVA_HOME) ----------------------------
JAVA_HOME="/opt/jdk"
if [ ! -x "$JAVA_HOME/bin/java" ]; then
  echo "Installing Corretto 21 -> $JAVA_HOME"
  tmp="$(mktemp -d)"
  wget -qO "$tmp/corretto.tar.gz" \
    "https://corretto.aws/downloads/latest/amazon-corretto-21-x64-linux-jdk.tar.gz"
  mkdir -p "$JAVA_HOME"
  tar xzf "$tmp/corretto.tar.gz" -C "$JAVA_HOME" --strip-components=1
  rm -rf "$tmp"
fi
cat >/etc/profile.d/lunar-java.sh <<EOF
export JAVA_HOME="$JAVA_HOME"
export PATH="\$JAVA_HOME/bin:\$PATH"
export GRADLE_USER_HOME="$MOUNT/gradle"
EOF

# --- idle auto-shutdown safeguard ----------------------------------------------------------
# A friendly EARLY-STOP: powers the VM off after $IDLE_MINUTES only when it is genuinely
# abandoned. The hard --max-run-duration TTL (set at create time) is the real guarantee/cap;
# this just stops sooner when nothing is happening. A guest poweroff -> instance STOPPED (disks
# persist, restartable).
#
# "Busy" = a live SSH session is attached (interactive shell, or a run/sync in flight) OR CPU
# loadavg shows an active build. So the VM is NOT killed merely because no build is running this
# instant — an interactive or intermittent session keeps it up. Leak protection comes from the
# layers around this, not from refusing to count connections:
#   - sshd ClientAliveInterval/CountMax (below) reaps DEAD/half-open tunnels in ~6 min, so a
#     killed agent's leaked connection stops counting as "alive" shortly after.
#   - the hard TTL caps a live-but-idle leaked session at a couple of hours (cents at spot price).
# Connections are sampled every 5 min, but a real run/shell holds its session continuously, so
# it is always observed; a connection-less build (dropped link) is covered by the load test.
meta() { wget -qO- --header='Metadata-Flavor: Google' \
  "http://metadata.google.internal/computeMetadata/v1/instance/attributes/$1" 2>/dev/null; }
IDLE_MINUTES="$(meta idle-minutes || echo 30)"; IDLE_MINUTES="${IDLE_MINUTES:-30}"
LOAD_THRESHOLD="$(meta idle-load-threshold || echo 1.0)"; LOAD_THRESHOLD="${LOAD_THRESHOLD:-1.0}"
cat >/usr/local/sbin/lunar-idle-check <<EOF
#!/bin/bash
IDLE=${IDLE_MINUTES}
THRESHOLD=${LOAD_THRESHOLD}
marker=/var/run/lunar-last-activity
load1=\$(awk '{print \$1}' /proc/loadavg)
# Keep alive if a build is running (load) OR a live SSH session is attached (established :22;
# dead ones already reaped by sshd keepalive). Either one refreshes the activity marker.
if awk "BEGIN{exit !(\$load1 >= \$THRESHOLD)}" \\
   || ss -tH state established 2>/dev/null | awk '{print \$4}' | grep -q ':22\$'; then
  touch "\$marker"; exit 0
fi
[ -f "\$marker" ] || touch "\$marker"
age=\$(( (\$(date +%s) - \$(stat -c %Y "\$marker")) / 60 ))
[ "\$age" -lt "\$IDLE" ] && exit 0
logger "lunar-builder: idle \${IDLE}m (no SSH session, loadavg \${load1} < \${THRESHOLD}), powering off to stop billing"
/sbin/poweroff
EOF
chmod +x /usr/local/sbin/lunar-idle-check
touch /var/run/lunar-last-activity

# Reap dead/half-open SSH tunnels server-side: if a client stops responding (its process was
# killed, network dropped) sshd closes the session after ~ClientAliveInterval*CountMax seconds,
# so leaked connections don't linger as established sockets.
cat >/etc/ssh/sshd_config.d/lunar-keepalive.conf <<'EOF'
ClientAliveInterval 120
ClientAliveCountMax 3
TCPKeepAlive yes
EOF
systemctl reload ssh 2>/dev/null || systemctl reload sshd 2>/dev/null || true
cat >/etc/systemd/system/lunar-idle.service <<'EOF'
[Unit]
Description=Lunar builder idle auto-shutdown check
[Service]
Type=oneshot
ExecStart=/usr/local/sbin/lunar-idle-check
EOF
cat >/etc/systemd/system/lunar-idle.timer <<'EOF'
[Unit]
Description=Run the lunar idle check every 5 minutes
[Timer]
OnBootSec=5min
OnUnitActiveSec=5min
[Install]
WantedBy=timers.target
EOF
systemctl daemon-reload
systemctl enable --now lunar-idle.timer
echo "Idle auto-shutdown armed: ${IDLE_MINUTES}m."

echo "=== bootstrap complete: $($JAVA_HOME/bin/java -version 2>&1 | head -1) ==="
touch /var/run/lunar-bootstrap-done
