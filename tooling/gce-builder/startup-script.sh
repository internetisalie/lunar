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

echo "=== bootstrap complete: $($JAVA_HOME/bin/java -version 2>&1 | head -1) ==="
touch /var/run/lunar-bootstrap-done
