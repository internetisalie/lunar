#!/usr/bin/env bash
# Runs as root on every VM boot (passed via --metadata-from-file startup-script=).
# Must be idempotent: spot VMs are re-created and the cache disk is re-attached.
set -euo pipefail
exec > >(tee -a /var/log/lunar-bootstrap.log) 2>&1
echo "=== lunar-builder bootstrap $(date -u) ==="

export DEBIAN_FRONTEND=noninteractive

# --- packages (skip if already present so reboots are fast) ---------------------------------
if ! command -v git >/dev/null || ! command -v rsync >/dev/null; then
  apt-get update -y
  apt-get install -y --no-install-recommends git rsync wget ca-certificates python3 tar
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
# Powers the VM off after $IDLE_MINUTES of no real build activity, so a forgotten VM stops
# billing. A guest poweroff -> instance STOPPED (disks persist, restartable). Complements the
# native --max-run-duration hard TTL set at create time.
#
# IMPORTANT: liveness is measured by CPU LOAD, *not* by the presence of an SSH connection.
# An idle agent/automation client is known to leak background SSH sessions; if those counted as
# "active" they would pin the VM alive indefinitely (defeating this safeguard). An active
# ./gradlew build drives 1-min loadavg well above the threshold on this many vCPUs, while an
# idle Gradle daemon, an idle shell, or a leaked-but-idle SSH tunnel all sit near zero — so a
# leaked connection can no longer keep the VM up. sshd keepalive (below) reaps dead tunnels too.
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
# Active build -> high load -> keep alive. Idle (incl. a leaked idle SSH session) -> low load.
if awk "BEGIN{exit !(\$load1 >= \$THRESHOLD)}"; then
  touch "\$marker"; exit 0
fi
[ -f "\$marker" ] || touch "\$marker"
age=\$(( (\$(date +%s) - \$(stat -c %Y "\$marker")) / 60 ))
[ "\$age" -lt "\$IDLE" ] && exit 0
logger "lunar-builder: idle \${IDLE}m (loadavg \${load1} < \${THRESHOLD}), powering off to stop billing"
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
