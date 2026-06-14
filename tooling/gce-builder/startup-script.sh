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
# Powers the VM off after $IDLE_MINUTES with no established inbound SSH session (sync/run/shell),
# so a forgotten idle VM stops billing. A guest poweroff -> instance STOPPED (disks persist,
# restartable). Complements the native --max-run-duration hard TTL set at create time.
IDLE_MINUTES="$(wget -qO- --header='Metadata-Flavor: Google' \
  'http://metadata.google.internal/computeMetadata/v1/instance/attributes/idle-minutes' 2>/dev/null || echo 30)"
cat >/usr/local/sbin/lunar-idle-check <<EOF
#!/bin/bash
IDLE=${IDLE_MINUTES}
marker=/var/run/lunar-last-activity
# An established connection to local port 22 = an active sync/run/shell session -> keep alive.
if ss -tH state established 2>/dev/null | awk '{print \$4}' | grep -q ':22\$'; then
  touch "\$marker"; exit 0
fi
[ -f "\$marker" ] || touch "\$marker"
age=\$(( (\$(date +%s) - \$(stat -c %Y "\$marker")) / 60 ))
[ "\$age" -lt "\$IDLE" ] && exit 0
logger "lunar-builder: idle \${IDLE}m with no SSH session, powering off to stop billing"
/sbin/poweroff
EOF
chmod +x /usr/local/sbin/lunar-idle-check
touch /var/run/lunar-last-activity
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
