#!/bin/bash
set -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

print_header() {
    echo -e "${GREEN}=== $1 ===${NC}"
}

print_info() {
    echo -e "${YELLOW}[*]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[✓]${NC} $1"
}

case "${1:-help}" in
    build)
        print_header "Building Lunar IDE Docker Image"
        
        # Prepare plugin
        print_info "Building plugin..."
        cd "$PROJECT_ROOT" && ./gradlew buildPlugin -x test > /dev/null 2>&1
        
        PLUGIN_PATH=$(ls "$PROJECT_ROOT/build/distributions/lunar-"*.zip | head -1 2>/dev/null || echo "")
        if [ -n "$PLUGIN_PATH" ]; then
            print_info "Copying plugin to build context..."
            cp "$PLUGIN_PATH" "$SCRIPT_DIR/lunar-plugin.zip"
        else
            touch "$SCRIPT_DIR/lunar-plugin.zip"
        fi
        
        docker build -t lunar-ide:latest "$SCRIPT_DIR"
            
        rm -f "$SCRIPT_DIR/lunar-plugin.zip"
        
        print_success "Docker image built: lunar-ide:latest"
        ;;
        
    run)
        print_header "Running Lunar IDE Container"
        
        # Check if container already running
        if docker ps -a --format '{{.Names}}' | grep -q "^lunar-ide$"; then
            print_info "Container already exists, removing..."
            docker rm -f lunar-ide || true
        fi
        
        # Use default if not set
        TEST_PROJECT_PATH="${LUNAR_TEST_PROJECT_PATH:-$HOME/Documents/src/lua/test}"
        
        # Build command with optional volume mount.
        # Bind published ports to loopback only — the VNC password is weak/known, so don't
        # expose it on the LAN. (The container itself listens on 0.0.0.0 inside its netns.)
        #
        # apparmor=unconfined: with snap-packaged Docker the daemon runs under the
        # 'snap.docker.dockerd' AppArmor label, and the default 'docker-default' container
        # profile refuses to *receive* signals from that peer — so 'docker stop/kill/restart'
        # fail with "permission denied". Dropping the profile for this dev container restores
        # normal lifecycle control. (Local dev IDE container only; not for production images.)
        RUN_CMD="docker run -d \
            --name lunar-ide \
            --security-opt apparmor=unconfined \
            -p 127.0.0.1:5900:5900 \
            -p 127.0.0.1:9090:9090 \
            -e DISPLAY=:99 \
            -e VNC_PORT=5900"

        # Optional JVM debugging:  LUNAR_DEBUG=1 ./docker-helper.sh run
        if [ -n "${LUNAR_DEBUG:-}" ]; then
            RUN_CMD="$RUN_CMD -e LUNAR_DEBUG=1 -p 127.0.0.1:${JDWP_PORT:-5005}:${JDWP_PORT:-5005}"
            print_info "JDWP enabled on ${JDWP_PORT:-5005} (jdb -attach localhost:${JDWP_PORT:-5005})"
        fi

        if [ -d "$TEST_PROJECT_PATH" ]; then
            RUN_CMD="$RUN_CMD -v \"$TEST_PROJECT_PATH\":/home/lunar/test"
        fi

        # Optional: persist the IDE config dir (which holds the license key goland.key) across
        # container recreations, so a one-time "Start Trial"/activation survives. This is the only
        # prompt that can't be pre-seeded into the image (the license is account/trial-bound).
        #   LUNAR_PERSIST_CONFIG=1        -> docker named volume 'lunar-ide-config'
        #   LUNAR_PERSIST_CONFIG=/abs/dir -> bind-mount that host directory
        # NOTE: only ~/.config/JetBrains is persisted; ~/.local/share/JetBrains is intentionally
        # left alone because the bundled Lunar plugin lives there and a volume would shadow it.
        if [ -n "${LUNAR_PERSIST_CONFIG:-}" ]; then
            case "$LUNAR_PERSIST_CONFIG" in
                1|true|yes) CONFIG_SRC="lunar-ide-config" ;;
                *)          CONFIG_SRC="$LUNAR_PERSIST_CONFIG" ;;
            esac
            RUN_CMD="$RUN_CMD -v \"$CONFIG_SRC\":/home/lunar/.config/JetBrains"
            print_info "Persisting IDE config (license) via: $CONFIG_SRC -> /home/lunar/.config/JetBrains"
        fi

        RUN_CMD="$RUN_CMD lunar-ide:latest"
        
        eval $RUN_CMD
        
        sleep 2
        print_success "Container started: lunar-ide"
        print_info "VNC available at: localhost:5900"
        ;;
        
    connect)
        print_header "Connecting to IDE via VNC"
        
        SYSTEM=$(uname -s)
        if [ "$SYSTEM" = "Darwin" ]; then
            open "vnc://localhost:5900"
        elif [ "$SYSTEM" = "Linux" ]; then
            if command -v vncviewer &> /dev/null; then
                vncviewer localhost:5900 &
            else
                print_info "Install tigervnc-viewer to use 'connect'"
            fi
        fi
        ;;
        
    logs)
        docker logs -f lunar-ide
        ;;
        
    shell)
        docker exec -it lunar-ide bash
        ;;
        
    stop)
        docker stop lunar-ide || true
        print_success "Container stopped"
        ;;
        
    clean)
        docker stop lunar-ide || true
        docker rm lunar-ide || true
        docker rmi lunar-ide:latest || true
        print_success "Cleanup complete"
        ;;

    setup-plugin)
        print_header "Rebuilding & hot-installing plugin into lunar-ide"
        cd "$PROJECT_ROOT" && ./gradlew buildPlugin -x test
        ZIP=$(ls "$PROJECT_ROOT/build/distributions/lunar-"*.zip 2>/dev/null | head -1)
        [ -n "$ZIP" ] || { print_info "No plugin zip in build/distributions"; exit 1; }
        docker cp "$ZIP" lunar-ide:/tmp/lunar-plugin.zip
        # Reinstall into the IDE's user plugin dir (the <Product><version> dir, which has a
        # digit — excludes the JetBrains 'Daemon' dir), then restart to load it.
        docker exec lunar-ide bash -c \
            'PD=$(ls -d /home/lunar/.local/share/JetBrains/*[0-9]*/ | head -1); unzip -o /tmp/lunar-plugin.zip -d "$PD" >/dev/null && echo "installed into $PD"'
        print_info "Restarting IDE to load the new plugin..."
        docker restart lunar-ide >/dev/null || print_info "restart failed — restart the container manually to load the plugin"
        print_success "Plugin reinstalled (restart to load)"
        ;;

    *)
        echo "Lunar IDE Docker Helper"
        echo ""
        echo "Usage: $0 {build|run|connect|logs|shell|stop|clean|setup-plugin}"
        echo ""
        echo "  LUNAR_DEBUG=1 $0 run   # start with JDWP on :5005 for jdb (see jdb-debugger skill)"
        ;;
esac
