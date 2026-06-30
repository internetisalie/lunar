---
id: "DEBUG-CONTAINER-GUIDE"
title: "Container Execution Guide"
type: "spec"
parent_id: "DEBUG/RUN"
priority: "low"
folders:
  - "[[features/debug/requirements|requirements]]"
---

# Container Execution Guide

Running the Lunar IDE plugin in a Docker container with desktop GUI access via VNC.

## Overview

The Docker container provides:
- **Virtual X Display** (Xvfb): Headless X11 server
- **VNC Server** (x11vnc): Remote desktop access
- **Window Manager** (Openbox): Lightweight desktop environment
- **Java 21 Runtime**: Required for IntelliJ IDEA
- **Plugin Sandbox**: Pre-configured plugin environment

## Prerequisites

- Docker installed ([Install Docker](https://docs.docker.com/get-docker/))
- Docker Compose (optional, included with Docker Desktop)
- VNC viewer for desktop access
- 2+ GB disk space for IDE installation
- 2+ GB RAM available

## Quick Start

### 1-Minute Setup

```bash
cd docker
./docker-helper.sh full-setup
./docker-helper.sh connect
```

This will:
1. Build Docker image
2. Start container
3. Download and install IntelliJ IDEA 2024.3.1
4. Build and install Lunar plugin
5. Open VNC viewer for desktop access

## Detailed Usage

### Step 1: Build Docker Image

```bash
cd docker
./docker-helper.sh build
```

Or with docker-compose:

```bash
docker-compose build
```

Expected output:
```
=== Building Lunar IDE Docker Image ===
[+] Building 45.2s (18/18) FINISHED
...
[✓] Docker image built: lunar-ide:latest
```

### Step 2: Start Container

```bash
./docker-helper.sh run
```

Expected output:
```
=== Running Lunar IDE Container ===
[✓] Container started: lunar-ide
[*] VNC available at: localhost:5900
[*] Default VNC password: vncpass
```

Or with docker-compose:

```bash
docker-compose up -d
```

### Step 3: Install IDE

```bash
./docker-helper.sh setup-ide
```

This will:
- Download IntelliJ IDEA Community 2024.3.1 (~800 MB)
- Extract to local cache
- Copy into container at `/home/lunar/ide/`

Expected output:
```
=== Setting up IDE in Container ===
[*] Downloading IntelliJ IDEA Community 2024.3.1...
[download...]
[✓] Downloaded: ideaIC-2024.3.1.tar.gz
[*] Extracting IDE...
[✓] Extracted: idea-IC-243.22562.145
[*] Copying IDE to container...
[✓] IDE installed in container
```

### Step 4: Install Plugin

```bash
./docker-helper.sh setup-plugin
```

This will:
- Build plugin JAR from source
- Copy to container at `/home/lunar/plugin/`

> **For live VNC verification of a feature, use the `verify-in-ide` skill**
> (`.agents/skills/verify-in-ide/SKILL.md`). It documents the reliable loop: hot-swap the jar
> (`docker cp` over `~/.local/share/JetBrains/GoLand*/lunar/lib/`) then a **clean in-container
> relaunch** (kill GoLand + clear locks + `goland.sh <project>`). **Do not `docker restart` to
> reload a plugin** — it races two IDE instances into a `DirectoryLock` cascade that wedges the
> container. The `docker restart` shown later is only for recovering a dead VNC connection, not for
> reloading plugin code.

Expected output:
```
=== Setting up Plugin in Container ===
[*] Building plugin...
[✓] Plugin built
[*] Copying plugin to container...
[✓] Plugin installed: lunar-1.0.0-SNAPSHOT.zip
```

### Step 5: Connect via VNC

```bash
./docker-helper.sh connect
```

Or connect manually:

**macOS:**
```bash
open vnc://localhost:5900
```

**Linux:**
```bash
vncviewer localhost:5900
```

**Windows:**
- Use TigerVNC Viewer or TightVNC
- Connect to: `localhost:5900`

**Web Browser:**
- Some VNC servers support web access
- Try: `http://localhost:6080/` (if noVNC is available)

## VNC Connection Details

| Setting | Value |
|---------|-------|
| **Host** | `localhost` or `127.0.0.1` |
| **Port** | `5900` |
| **Password** | `vncpass` (default) |
| **Resolution** | 1920x1080x24 |
| **Color Depth** | 24-bit (True Color) |

## Container Commands

### View Logs

```bash
./docker-helper.sh logs
```

Shows real-time container output:
```
[05:53:06]: Starter DI was initialized
[05:53:07]: Resolving IDE build for SimplePrintTest...
[05:53:27]: IDE to run...
```

### Interactive Shell

```bash
./docker-helper.sh shell
```

Access container bash:
```bash
lunar@container:~$ ls -la
lunar@container:~$ cd /home/lunar/ide && ./bin/idea.sh &
```

### Stop Container

```bash
./docker-helper.sh stop
```

Container remains available, can be restarted:
```bash
docker start lunar-ide
```

### Clean Up

```bash
./docker-helper.sh clean
```

Removes:
- Running container
- Container filesystem
- Docker image
- Cached IDE downloads (local machine)

## Docker Compose Usage

### Start with Compose

```bash
docker-compose up -d
```

View logs:
```bash
docker-compose logs -f
```

Stop:
```bash
docker-compose down
```

Remove everything:
```bash
docker-compose down --rmi all -v
```

## Directory Mapping

### Inside Container

| Path | Purpose |
|------|---------|
| `/home/lunar/ide/` | IntelliJ IDEA installation |
| `/home/lunar/plugin/` | Lunar plugin files |
| `/home/lunar/.vnc/` | VNC configuration & password |
| `/home/lunar/.config/openbox/` | Window manager config |
| `/home/lunar/projects/` | Project files (optional volume) |

### Volume Mounting

Mount local directory for project files:

```bash
docker run -d \
  -v ~/my-lua-projects:/home/lunar/projects \
  lunar-ide:latest
```

Then in container, access projects at `/home/lunar/projects/`

## Customization

### Change VNC Password

```bash
docker exec lunar-ide bash -c \
  "echo 'mynewpass' | vncpasswd -f > ~/.vnc/passwd && chmod 600 ~/.vnc/passwd"
```

Reconnect with VNC to use new password.

### Change Display Resolution

Edit `docker-entrypoint.sh` line ~22:

```bash
# FROM:
Xvfb ${DISPLAY} -screen 0 1920x1080x24 &

# TO:
Xvfb ${DISPLAY} -screen 0 2560x1440x24 &
```

Then rebuild:
```bash
./docker-helper.sh build
```

### Change VNC Port

Set environment variable when running:

```bash
docker run -d \
  -p 5901:5900 \
  -e VNC_PORT=5901 \
  lunar-ide:latest
```

Connect to `localhost:5901`

### Environment Variables

Available environment variables:

```bash
docker run -d \
  -e DISPLAY=:99 \
  -e VNC_PORT=5900 \
  -e JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
  lunar-ide:latest
```

## Troubleshooting

### Connection Refused

**Problem:** VNC client shows "Connection refused"

**Solutions:**
1. Container not running: `docker ps` - should show `lunar-ide`
2. Port not exposed: Check `docker run` has `-p 5900:5900`
3. Container crashed: `docker logs lunar-ide` for errors

```bash
# Restart container
docker restart lunar-ide

# Check status
docker ps | grep lunar-ide
```

### IDE Won't Start

**Problem:** IDE doesn't appear after connecting via VNC

**Solutions:**
1. IDE not installed:
   ```bash
   docker exec lunar-ide test -f /home/lunar/ide/bin/idea.sh || echo "IDE not found"
   ```

2. Java not working:
   ```bash
   docker exec lunar-ide java -version
   ```

3. X11 display not ready - wait 5-10 seconds after container start

### Poor VNC Performance

**Problem:** VNC connection is slow or laggy

**Solutions:**
1. Reduce display resolution (see Customization section)
2. Use VNC quality settings:
   - Try quality: 60-80
   - Compression: 6-9
3. Use local network (avoid internet/VPN)

### Out of Memory

**Problem:** Container crashes or becomes unresponsive

**Solutions:**
1. Allocate more RAM to Docker:
   - Docker Desktop: Preferences → Resources → Memory
   - Increase to 4-8 GB recommended for IDE

2. Monitor container memory:
   ```bash
   docker stats lunar-ide
   ```

### Plugin Not Loading

**Problem:** Lunar plugin doesn't appear in IDE

**Solutions:**
1. Check plugin was copied:
   ```bash
   docker exec lunar-ide ls /home/lunar/plugin/
   ```

2. Manual installation in IDE:
   - File → Settings → Plugins
   - Click gear icon → Install Plugin from Disk
   - Select `/home/lunar/plugin/lunar-*.zip`
   - Restart IDE

## Advanced Usage

### Manual IDE Start

In case IDE doesn't auto-start:

```bash
docker exec -it lunar-ide bash
cd /home/lunar/ide/bin
./idea.sh
```

### Execute Test

Run integration tests in container:

```bash
docker exec lunar-ide bash -c "cd /project && gradle integrationTest"
```

(Requires project volume mounted at `/project`)

### Custom Entrypoint

Override entrypoint for special setup:

```bash
docker run -it \
  --entrypoint /bin/bash \
  lunar-ide:latest
```

### Export Container to Image

Save modified container as new image:

```bash
docker commit lunar-ide lunar-ide:with-projects
```

Then run modified image:

```bash
docker run -d \
  --name lunar-ide-v2 \
  lunar-ide:with-projects
```

## Monitoring

### View Resource Usage

Real-time stats:

```bash
docker stats lunar-ide
```

Output:
```
CONTAINER   CPU%   MEM USAGE / LIMIT   MEM%   NET I/O
lunar-ide   2.1%   1.2GiB / 8GiB       15%    500MB / 50MB
```

### View Full Logs

All container output:

```bash
docker logs lunar-ide > container-logs.txt
```

### Check Port Status

Verify ports are open:

```bash
netstat -an | grep 5900
# or
lsof -i :5900
```

## Performance Tips

1. **Use SSD** for container storage
2. **Allocate sufficient RAM** (minimum 2GB, recommended 4-8GB)
3. **Network**: Use local machine (avoid remote Docker hosts)
4. **VNC Quality**: Balance quality vs performance in VNC viewer settings
5. **X11 Rendering**: Hardware acceleration limited in virtual X display

## Cleanup and Reset

### Remove Specific Containers

```bash
docker ps -a  # List all
docker rm lunar-ide  # Remove specific
```

### Remove Images

```bash
docker images  # List
docker rmi lunar-ide:latest  # Remove
```

### Free Disk Space

```bash
docker system prune -a
docker system prune -a --volumes
```

### Reset Everything

```bash
./docker-helper.sh clean
docker system prune -a --volumes
```

## Integration with CI/CD

### GitHub Actions

```yaml
- name: Start IDE Container
  run: ./docker/docker-helper.sh full-setup

- name: Run Tests
  run: docker exec lunar-ide gradle integrationTest
```

### GitLab CI

```yaml
test:
  image: lunar-ide:latest
  script:
    - gradle integrationTest
```

## Next Steps

1. ✅ Container is running
2. ✅ IDE installed with plugin loaded
3. → Open Lua project in IDE
4. → Test Lua syntax highlighting
5. → Run Lua programs
6. → Debug with DBGp protocol

## Support

For issues:
1. Check logs: `./docker-helper.sh logs`
2. Review troubleshooting section above
3. Verify prerequisites are met
4. Try full reset: `./docker-helper.sh clean && ./docker-helper.sh full-setup`

## References

- [Docker Docs](https://docs.docker.com/)
- [x11vnc Documentation](http://www.karlrunge.com/x11vnc/)
- [Xvfb Manual](https://www.x.org/archive/current/doc/man/man1/Xvfb.1.xhtml)
- [IntelliJ IDEA Documentation](https://www.jetbrains.com/help/idea/)
