# Lunar Plugin IDE Docker Container

Docker setup to run GoLand (or other JetBrains IDEs) with the Lunar plugin as a desktop application accessible via VNC.

## Features

- **Pre-staged IDE**: GoLand 2026.1.3 baked into image (no download needed)
- **Xvfb**: Virtual X11 display (headless)
- **x11vnc**: VNC server for remote desktop access
- **Openbox**: Lightweight window manager
- **Java 21**: Required for JetBrains IDEs
- **Multi-IDE Support**: Switch between any JetBrains IDE via gradle.properties

## Quick Start

### 1. Build Docker Image (One Time)

```bash
./docker-helper.sh build
```

This will:
- Pre-stage GoLand 2026.1.3 (the `IDE_VERSION` baked into the `ide` layer of the Dockerfile)
- Build the `vnc` target (default) and tag it `lunar-ide:vnc` + `lunar-ide:latest`

### 2. Run Container

```bash
./docker-helper.sh run
```

This will:
- Start container as `lunar-ide`
- Expose VNC on port 5900
- Expose HTTP on port 9090
- Auto-launch GoLand

### 3. Connect via VNC

Open VNC client to `localhost:5900`:
- **Address**: `localhost:5900`
- **Password**: `vncpass` (default)
- **Resolution**: 1920x1080x24

Recommended VNC viewers:
- **MacOS**: Built-in Screen Sharing or [VNC Viewer](https://www.realvnc.com/en/connect/download/viewer/)
- **Linux**: `vncviewer localhost:5900` or [TigerVNC](https://tigervnc.org/)
- **Windows**: [TigerVNC](https://tigervnc.org/) or [TightVNC](https://www.tightvnc.com/)

### 4. Stop Container

```bash
./docker-helper.sh stop
```

## Configuration

The IDE **type** comes from `IDE_TYPE` and the **version** from `IDE_VERSION`, both set as `ENV`
in the `ide` stage of the Dockerfile (the container does **not** read `gradle.properties`):

```dockerfile
ENV DISPLAY=:99 \
    IDE_TYPE=GO \
    IDE_VERSION=2026.1.3
```

`IDE_TYPE` accepts the same codes as gradle's `platformType` (GO=GoLand, IC=IntelliJ Community, …).

Supported IDE types:
- `GO` - GoLand
- `IU` - IntelliJ IDEA Ultimate
- `IC` - IntelliJ IDEA Community
- `PY` - PyCharm Pro
- `PC` - PyCharm Community
- `WS` - WebStorm
- `CL` - CLion
- `RD` - Rider
- `RM` - RubyMine
- `PS` - PhpStorm
- `DB` - DataGrip
- `AI` - Android Studio

To use a different IDE, change `gradle.properties` and rebuild:

```bash
# Change to IntelliJ Community
sed -i 's/platformType = .*/platformType = IC/' gradle.properties

# Rebuild (will stage new IDE)
./docker-helper.sh build
./docker-helper.sh run
```

## First-run dialogs (auto-bypassed)

The entrypoint pre-accepts the JetBrains first-run dialogs so the container boots straight into the
open project (no VNC clicking required):

- **EULA** — seeds the accepted EUA version as a `java.util.prefs` value
  (`jetbrains/privacy_policy/eua_accepted_version`, compared by *major* version). If JetBrains bumps
  the EULA major and it starts prompting again, override `LUNAR_EULA_VERSION` (default `2.0`).
- **Data sharing** — writes a declined `consentOptions/accepted` (versions matched to the bundled
  `consents.json`) and sets `-Djb.consents.confirmation.enabled=false`.
- **Project trust** — seeds `trusted-paths.xml` for the mounted `/home/lunar/test`.

**Not auto-bypassed: the license.** GoLand is commercial, so the *License / Register* dialog still
appears on a fresh container — a license can't be baked into the image (it's signed and
account/trial-bound; it's saved to `~/.config/JetBrains/<product>/goland.key`). To make it a
**one-time** step, persist the config dir across container recreations:

```bash
# docker named volume (survives 'run' which recreates the container):
LUNAR_PERSIST_CONFIG=1 ./docker-helper.sh run
# ...or bind-mount a host directory:
LUNAR_PERSIST_CONFIG=$HOME/.lunar-ide-config ./docker-helper.sh run
```

First run → click **Start Trial** (or activate) once; every subsequent `run` then boots straight
into the licensed IDE with no prompts. Only `~/.config/JetBrains` is persisted (where `goland.key`
lives); `~/.local/share/JetBrains` is left alone so the bundled Lunar plugin isn't shadowed.

## Docker Helper Commands

Full usage: `./docker-helper.sh {command}`

### Build
```bash
./docker-helper.sh build
```
Build Docker image with pre-staged IDE.

### Run
```bash
./docker-helper.sh run
```
Start container (auto-launches IDE).

### Stop
```bash
./docker-helper.sh stop
```
Stop and remove container.

### Setup Plugin
```bash
./docker-helper.sh setup-plugin
```
Build and install Lunar plugin into running container.

### Connect
```bash
./docker-helper.sh connect
```
Open VNC connection (requires `open` command on macOS).

### Logs
```bash
./docker-helper.sh logs
```
Show container logs (VNC and IDE startup info).

### Shell
```bash
./docker-helper.sh shell
```
Open interactive bash shell in container.

### Cache Status
```bash
./docker-helper.sh cache status
```
Show cached IDE archives and sizes.

### Cache Clean
```bash
./docker-helper.sh cache clean
```
Remove all cached IDE files (free up disk space).

## IDE Caching System

IDEs are downloaded and cached before Docker build to ensure:
- **Reliable builds** (no CDN failures during docker build)
- **Offline builds** (use cached IDEs when CDN unavailable)
- **Fast rebuilds** (cached IDEs reused across builds)
- **Reusable cache** (switch between IDE types without re-downloading)

Cache directory: `docker/.ide-cache/`

To manually stage an IDE:
```bash
./docker-helper.sh cache stage GO 2026.1.3
```

## Directory Structure

Inside container:
- `/home/lunar/ide/` - GoLand/IDE installation (pre-staged)
- `/home/lunar/.vnc/` - VNC configuration (password, etc.)
- `/home/lunar/.config/openbox/` - Window manager config
- `/home/lunar/.config/` - IDE user config and plugins

## Advanced Usage

### Manual Container Commands

```bash
# View logs
docker logs -f lunar-ide

# Interactive shell
docker exec -it lunar-ide bash

# Check IDE installation
docker exec lunar-ide ls -la /home/lunar/ide/bin/

# Install Lunar plugin manually
docker exec lunar-ide docker cp build/distributions/lunar-*.zip lunar-ide:/home/lunar/.local/share/goland/plugins/

# Change VNC password
docker exec lunar-ide bash -c "echo 'newpass' | vncpasswd -f > ~/.vnc/passwd && chmod 600 ~/.vnc/passwd"
```

### Volume Mount for Projects

```bash
# Add to docker run command (edit docker-helper.sh)
docker run -d \
  -v ~/projects:/home/lunar/projects \
  --name lunar-ide \
  -p 5900:5900 \
  lunar-ide:latest
```

### Custom Display Resolution

Edit `docker-entrypoint.sh` line 14:
```bash
Xvfb ${DISPLAY} -screen 0 1920x1080x24 &  # Change 1920x1080 to desired resolution
```

Rebuild image: `./docker-helper.sh build`

## Integration with IDE Testing

The IDE version is configured in **two independent places** (intentionally — they serve different
purposes and can differ):

- **Compile + unit tests** — `platformVersion` in `gradle.properties` (currently `2026.1.3`).
  Becomes the artifact coordinate `go:goland:<platformVersion>`.
- **ide-starter integration tests** — `testVersion` in `gradle.properties` (currently `2026.1.3`),
  read at runtime by `IdeProductResolver` in `src/integrationTest` (not by the gradle plugin).
- **Docker containerized IDE** — `IDE_VERSION` in `docker/Dockerfile` (currently `2026.1.3`).

All three track the latest 2026.1 patch so dev/test runs on the same bugfixed platform users get.
`pluginSinceBuild = 261` keeps the whole 2026.1.x branch as the declared compatibility floor
(patch releases are bugfix-only, so compiling against `.3` doesn't narrow it).

> **Note:** the integration tests launch GoLand, which is commercial — a fresh ide-starter config
> has no license, so the **License/Activation modal blocks the run** (5-min timeout "due to a dialog
> being shown"). The plugin only depends on `com.intellij.modules.platform`, so the license-free fix
> is to run the ide-starter tests on IntelliJ IDEA **Community** instead. See the Wave-6 plan §2.2.

## Troubleshooting

### Build Fails with "IDE staging failed"
- Check internet connection (first build downloads IDE)
- Verify gradle.properties has valid IDE type
- Check available disk space (IDE archive ~1.2GB)
- Try clearing cache: `./docker-helper.sh cache clean`

### VNC Connection Refused
```bash
# Check container is running
docker ps | grep lunar-ide

# Check logs
docker logs lunar-ide

# Verify port is exposed
netstat -tuln | grep 5900
```

### IDE Won't Start in Container
```bash
# Check IDE files exist
docker exec lunar-ide ls -la /home/lunar/ide/bin/ | grep -E "goland|idea|pycharm"

# Check Java is working
docker exec lunar-ide java -version

# View IDE startup logs
docker logs lunar-ide | tail -50
```

### Poor VNC Performance
- Use a faster network connection
- Reduce display resolution (edit docker-entrypoint.sh)
- Use VNC client with compression enabled
- Adjust quality settings in VNC viewer (typically 70-80%)

### "Address already in use" for port 5900
Another container is using port 5900:
```bash
# Find and stop other container
docker ps | grep 5900
docker stop <container-id>

# Or use different port (edit docker-helper.sh run command)
-p 5901:5900
```

## Docker Image Details

### Image Size
- **Compressed**: ~1.68GB (pushed to registry)
- **Uncompressed**: ~6.5GB (running on disk)
- **IDE cached**: ~1.2GB + 3.4GB extracted

### Build Targets

The Dockerfile is split into four layered targets (`docker build --target <name> docker/`):

| Target | Builds on | Contents | Purpose |
|--------|-----------|----------|---------|
| `build` | ubuntu:24.04 | JDK 21 + git/build deps + fonts | reproducible `./gradlew build` / `test` |
| `ide` | `build` | + xvfb + native render libs + pre-staged GoLand | the heavy ~1.2GB IDE layer (cached) |
| `integration-test` | `ide` | + entrypoint running `./gradlew integrationTest` under Xvfb | headless integration tests |
| `vnc` | `ide` | + x11vnc/openbox/firefox + bundled plugin | interactive VNC desktop IDE (**default**) |

Select a target with the helper: `./docker-helper.sh build [build|ide|integration-test|vnc]`
(default `vnc`, which is also tagged `lunar-ide:latest`). The repo source is **not** baked in —
`build` / `integration-test` bind-mount it at `/workspace`.

Run integration tests in the container (bind-mounts the repo, persists a gradle cache volume):

```bash
./docker-helper.sh build integration-test
./docker-helper.sh integration-test            # runs ./gradlew integrationTest
./docker-helper.sh integration-test --tests "*CrossFile*"   # extra gradle args pass through
```

### Pre-installed
- Ubuntu 24.04
- OpenJDK 21 JDK
- Xvfb, x11vnc, Openbox
- Git, wget, curl, tar
- X11 libraries and fonts
- GoLand 2026.1.3 (or other selected IDE)

## Next Steps

1. ✅ Build image: `./docker-helper.sh build`
2. ✅ Run container: `./docker-helper.sh run`
3. ✅ Connect via VNC: `localhost:5900`
4. Install Lunar plugin (see plugin docs)
5. Create/open Lua projects
6. Test plugin features

## Documentation

- [Container Execution Guide](../docs/implementation/container-execution.md) - Detailed container usage
- [IDE Execution Guide](../docs/implementation/ide-execution-guide.md) - IDE Starter framework
- [Integration Tests Guide](../docs/implementation/integration-tests.md) - Running integration tests
- [Run Configuration API](../docs/implementation/run-configuration-api.md) - Lua run configuration

## Cleanup

Remove all Docker artifacts:

```bash
# Stop and remove container
./docker-helper.sh stop

# Remove image
docker rmi lunar-ide:latest lunar-ide:GO

# Clean IDE cache (optional)
./docker-helper.sh cache clean

# Remove docker build artifacts
cd docker && rm -rf ide-staging .ide-cache
```
