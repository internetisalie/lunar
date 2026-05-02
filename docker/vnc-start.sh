#!/bin/bash
set -e

# Start Xvfb in background
echo "Starting Xvfb..."
Xvfb ${DISPLAY} -screen 0 1920x1080x24 &
XVFB_PID=$!
sleep 2

# Start x11vnc in background
echo "Starting x11vnc on port ${VNC_PORT}..."
x11vnc -display ${DISPLAY} -forever -rfbauth ~/.vnc/passwd -listen localhost -rfbport ${VNC_PORT} &
VNC_PID=$!

# Allow x11vnc to start
sleep 2

# Start openbox window manager
echo "Starting Openbox..."
openbox --replace &
OPENBOX_PID=$!

# Trap to cleanup on exit
cleanup() {
    echo "Shutting down..."
    kill $XVFB_PID 2>/dev/null || true
    kill $VNC_PID 2>/dev/null || true
    kill $OPENBOX_PID 2>/dev/null || true
}
trap cleanup EXIT

# Keep script running
wait $XVFB_PID
