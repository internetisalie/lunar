#!/bin/bash
# Entrypoint for the `integration-test` Docker target: start a virtual X display,
# then hand off to gradle. The repo is expected bind-mounted at /workspace.
# Args after the script name are passed to gradle (default: integrationTest).
set -e

DISPLAY_NUM="${DISPLAY#:}"
# Clear any stale lock/socket from a reused writable layer so Xvfb can claim the display.
rm -f "/tmp/.X${DISPLAY_NUM}-lock" "/tmp/.X11-unix/X${DISPLAY_NUM}" 2>/dev/null || true

echo "[*] Starting Xvfb on ${DISPLAY:-:99}..."
Xvfb "${DISPLAY:-:99}" -screen 0 1920x1080x24 &
sleep 2

# A fresh `-v lunar-gradle-cache:/home/lunar/.gradle` named volume arrives root-owned; make it
# writable by the build user before gradle tries to create its wrapper/dist lock files.
sudo chown -R "$(id -u):$(id -g)" "$HOME/.gradle" 2>/dev/null || true

cd /workspace

GRADLE_ARGS=("$@")
[ ${#GRADLE_ARGS[@]} -eq 0 ] && GRADLE_ARGS=("integrationTest")

echo "[*] Running ./gradlew ${GRADLE_ARGS[*]} ..."
# exec so gradle's exit code propagates to `docker run` (CI-friendly).
exec ./gradlew "${GRADLE_ARGS[@]}"
