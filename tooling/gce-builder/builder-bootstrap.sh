#!/usr/bin/env bash
# Lunar compute5 builder bootstrap (de-GCP'd port of tooling/gce-builder/startup-script.sh).
# Installs the exact build deps + Corretto 21 (Gradle) and 25 (toolchain); no GCP idle/TTL/cache-disk machinery.
set -euo pipefail
export DEBIAN_FRONTEND=noninteractive

echo "=== apt deps ==="
apt-get update -y
apt-get install -y --no-install-recommends \
  git rsync wget curl ca-certificates python3 tar unzip \
  fontconfig fonts-dejavu-core lua5.4 lua-socket \
  build-essential \
  gcc-mingw-w64-x86-64
fc-cache -f >/dev/null 2>&1 || true

echo "=== Corretto 21 -> /opt/jdk ==="
JAVA_HOME=/opt/jdk
if [ ! -x "$JAVA_HOME/bin/java" ]; then
  tmp="$(mktemp -d)"
  wget -qO "$tmp/corretto.tar.gz" \
    "https://corretto.aws/downloads/latest/amazon-corretto-21-x64-linux-jdk.tar.gz"
  mkdir -p "$JAVA_HOME"
  tar xzf "$tmp/corretto.tar.gz" -C "$JAVA_HOME" --strip-components=1
  rm -rf "$tmp"
fi

echo "=== Corretto 25 -> /usr/lib/jvm/corretto-25 (Gradle toolchain; JAVA_HOME stays 21) ==="
JDK25=/usr/lib/jvm/corretto-25
if [ ! -x "$JDK25/bin/java" ]; then
  tmp="$(mktemp -d)"
  wget -qO "$tmp/corretto25.tar.gz" \
    "https://corretto.aws/downloads/latest/amazon-corretto-25-x64-linux-jdk.tar.gz"
  mkdir -p "$JDK25"
  tar xzf "$tmp/corretto25.tar.gz" -C "$JDK25" --strip-components=1
  rm -rf "$tmp"
fi
# Toolchains come from the VM, never from a download (MAINT-21-09).
mkdir -p /opt/cache/gradle
grep -qx 'org.gradle.java.installations.auto-download=false' /opt/cache/gradle/gradle.properties 2>/dev/null \
  || echo 'org.gradle.java.installations.auto-download=false' >> /opt/cache/gradle/gradle.properties

echo "=== profile.d (JAVA_HOME + GRADLE_USER_HOME) ==="
mkdir -p /opt/cache/gradle
cat >/etc/profile.d/lunar-java.sh <<'EOF'
export JAVA_HOME=/opt/jdk
export PATH="$JAVA_HOME/bin:$PATH"
export GRADLE_USER_HOME=/opt/cache/gradle
EOF

echo "=== hostname + ~/bin/lua (TestLuaDebugHarness) ==="
hostnamectl set-hostname lunar-builder 2>/dev/null || hostname lunar-builder
mkdir -p /root/bin
command -v lua5.4 >/dev/null && ln -sf "$(command -v lua5.4)" /root/bin/lua

echo "=== verify ==="
/opt/jdk/bin/java -version
/usr/lib/jvm/corretto-25/bin/java -version
echo "mingw: $(x86_64-w64-mingw32-gcc -dumpversion 2>/dev/null)"
echo "lua:   $(lua5.4 -v 2>&1)"
echo "=== BOOTSTRAP DONE ==="
