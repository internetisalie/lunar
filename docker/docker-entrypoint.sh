#!/bin/bash
set -e

COMMAND="${1:-start}"

case "$COMMAND" in
    start)
        echo "=== Lunar IDE Docker Container ==="
        echo "Display: $DISPLAY"
        echo "VNC Port: $VNC_PORT"
        
        # Start Xvfb
        echo "[*] Starting Xvfb (virtual X display)..."
        Xvfb ${DISPLAY} -screen 0 1920x1080x24 &
        XVFB_PID=$!
        sleep 2
        
        # Start window manager
        echo "[*] Starting Openbox (window manager)..."
        openbox --replace &
        OPENBOX_PID=$!
        sleep 1
        
        # Start VNC server
        echo "[*] Starting x11vnc server..."
        x11vnc -display ${DISPLAY} \
            -forever \
            -nopw \
            -listen 0.0.0.0 \
            -rfbport ${VNC_PORT} \
            -rfbauth /home/lunar/.vnc/passwd &
        VNC_PID=$!
        sleep 2
        
        echo "[✓] VNC Server ready at 0.0.0.0:${VNC_PORT}"
        
        # Detect IDE executable (supports multiple IDE types)
        IDE_BIN=""
        for exe in goland pycharm idea clion rider jetbrains-gateway webstorm phpstorm datagrip rubymine android-studio aqua dataspell; do
            if [ -f "/home/lunar/ide/bin/${exe}.sh" ]; then
                IDE_BIN="/home/lunar/ide/bin/${exe}.sh"
                break
            fi
        done
        
        # Start IDE if found
        if [ -n "$IDE_BIN" ]; then
            echo "[*] Starting IDE ($(basename $IDE_BIN))..."
            
            # Open test project if it exists
            if [ -d "/home/lunar/test" ]; then
                echo "[*] Opening test project: /home/lunar/test"
                $IDE_BIN /home/lunar/test &
            else
                $IDE_BIN &
            fi
            
            IDE_PID=$!
            sleep 5
            echo "[✓] IDE started (PID: $IDE_PID)"
        else
            echo "[!] IDE not found. Use 'docker cp' to copy IDE installation."
            echo "[!] Expected executable in: /home/lunar/ide/bin/*.sh"
        fi
        
        # Setup cleanup
        cleanup() {
            echo "[*] Shutting down..."
            kill $XVFB_PID 2>/dev/null || true
            kill $VNC_PID 2>/dev/null || true
            kill $OPENBOX_PID 2>/dev/null || true
            [ -n "$IDE_PID" ] && kill $IDE_PID 2>/dev/null || true
        }
        trap cleanup EXIT INT TERM
        
        echo "[✓] Container running. Connect via VNC at localhost:${VNC_PORT}"
        echo "[*] Press Ctrl+C to shutdown"
        
        # Keep running
        wait $XVFB_PID
        ;;
        
    bash)
        /bin/bash
        ;;
        
    *)
        echo "Usage: $0 {start|bash}"
        exit 1
        ;;
esac
