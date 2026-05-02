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
        
        # Build command with optional volume mount
        RUN_CMD="docker run -d \
            --name lunar-ide \
            -p 5900:5900 \
            -p 9090:9090 \
            -e DISPLAY=:99 \
            -e VNC_PORT=5900"
        
        if [ -d "$TEST_PROJECT_PATH" ]; then
            RUN_CMD="$RUN_CMD -v \"$TEST_PROJECT_PATH\":/home/lunar/test"
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
        
    *)
        echo "Lunar IDE Docker Helper"
        echo ""
        echo "Usage: $0 {build|run|connect|logs|shell|stop|clean}"
        ;;
esac
