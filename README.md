# lunar

Lua support for IntelliJ Platform (GoLand, IntelliJ IDEA, PyCharm, and other JetBrains IDEs).

## Quick Start

### Build & Test

```bash
# Build plugin
./gradlew build

# Run integration tests with GoLand 2026.1.1
./gradlew integrationTest

# Build Docker container with IDE
cd docker && ./docker-helper.sh build
./docker-helper.sh run

# Connect via VNC to localhost:5900 (password: vncpass)
```

### Docker Setup

Complete containerized IDE testing with GoLand:

```bash
# Build Docker image (pre-stages GoLand 2026.1.1)
./docker-helper.sh build

# Run container
./docker-helper.sh run

# Open VNC client to localhost:5900
# IDE auto-launches with plugin installed

# Stop container
./docker-helper.sh stop
```

See [docker/README.md](docker/README.md) for detailed Docker instructions.

## IDE Configuration

The IDE the plugin builds and tests against is controlled by `gradle.properties`:

```properties
platformType = GO            # IDE for builds: GO (GoLand), IC (IntelliJ Community), etc.
platformVersion = 2026.1.3   # platform version (becomes the artifact coordinate, e.g. go:goland:2026.1.3)
```

The containerized debug IDE version is set separately by `IDE_VERSION` in
[docker/Dockerfile](docker/Dockerfile).

Change to use different IDE:
```bash
# Switch to IntelliJ Community Edition
sed -i 's/platformType = .*/platformType = IC/' gradle.properties
./gradlew build            # Uses IC
./gradlew integrationTest  # Tests IC with 2026.1.1
```

## Requirements & Roadmap

For detailed specifications on all features, implementation status, and planned enhancements, see the [Requirements Documentation](docs/requirements.md).

The project covers the following areas:

- **[SYNTAX]** Syntax & Editor support (Lua 5.4, folding, highlighting, formatting)
- **[COMP]** Code Completion (keywords, symbols, cross-file, type inference)
- **[NAV]** Code Navigation (go to definition, find usages, structure view, markers, references)
- **[TYPE]** Type System (LuaCATS, type inference, function signatures)
- **[DOC]** Documentation (Quick Doc, LuaCATS/LuaDoc highlighting, parameter info)
- **[INSP]** Inspections & Diagnostics (undeclared variables, type mismatches, unused locals)
- **[ANALYSIS]** Static Analysis (Luacheck integration, external annotator)
- **[FORMAT]** Formatting (indentation, alignment, spacing, stylua compatibility)
- **[REFACT/INTENT]** Refactoring & Intentions (rename, labels, introduce variable, string conversions)
- **[DEBUG/RUN]** Debugging & Execution (breakpoints, stack frames, remote debugging, REPL)
- **[Non-Functional]** Technical requirements (Kotlin idiomaticity, performance, caching)

## Documentation

### Getting Started

- [Docker Container Guide](docker/README.md) - Run IDE in Docker with VNC
- [Container Execution](docs/implementation/container-execution.md) - Docker setup details
- [Integration Tests](docs/implementation/integration-tests.md) - IDE Starter framework

### API & Development

- [IDE Execution Guide](docs/implementation/ide-execution-guide.md) - IDE Starter framework usage
- [Run Configuration API](docs/implementation/run-configuration-api.md) - Lua run configuration

## Development

### Build

```bash
./gradlew build
```

### Test

```bash
# Run unit tests
./gradlew test

# Run integration tests (downloads GoLand 2026.1.1)
./gradlew integrationTest

# Run specific test
./gradlew test --tests "*Glob*"
```

### IDE Testing

```bash
# Build and run in Docker container
cd docker
./docker-helper.sh build    # Create image with GoLand
./docker-helper.sh run      # Start container
# Connect to localhost:5900 with VNC client

./docker-helper.sh stop     # Stop container
```

### Change IDE for Testing

Edit `gradle.properties`:
```properties
platformType = IC           # IntelliJ Community Edition
# or
platformType = PY           # PyCharm Pro
# or
platformType = WS           # WebStorm
```

Then rebuild:
```bash
./gradlew build
./gradlew integrationTest
cd docker && ./docker-helper.sh build && ./docker-helper.sh run
```

## Project Structure

```
src/
├── main/kotlin/net/internetisalie/lunar/
│   ├── analysis/           # Static analysis (Luacheck integration)
│   ├── lang/               # Language support
│   │   ├── lexer/         # Tokenization
│   │   ├── parser/        # AST parsing
│   │   ├── psi/           # Program Structure Interface elements
│   │   ├── structure/     # Structure view
│   │   └── syntax/        # Syntax highlighting
│   ├── luacats/           # LuaCATS documentation support
│   ├── luadoc/            # LuaDoc documentation support
│   ├── run/               # Run/Debug configuration
│   ├── settings/          # IDE settings
│   └── util/              # Utilities
├── integrationTest/kotlin/ # IDE Starter integration tests
└── test/kotlin/            # Unit tests

docker/
├── Dockerfile              # Multi-stage Docker build
├── docker-helper.sh        # Helper commands (build, run, stop, etc.)
├── ide-stage.sh           # IDE download/staging script
├── docker-entrypoint.sh   # Container startup script
├── README.md              # Docker setup guide
└── openbox-rc.xml         # Window manager config

docs/implementation/
├── container-execution.md  # Docker container guide
├── ide-execution-guide.md  # IDE Starter framework
├── integration-tests.md    # Integration test infrastructure
└── run-configuration-api.md # Lua run configuration
```

## System Requirements

### Development

- **Java**: JDK 21+ (for Gradle and IDE)
- **Gradle**: 8.13+
- **Kotlin**: 1.9+

### Integration Testing

- **GoLand/IntelliJ**: 2026.1.1 (auto-downloaded)
- **Network**: Required for first IDE download (~1.2GB)

### Docker

- **Docker**: 20.10+
- **Disk Space**: ~6.5GB for image (with pre-staged IDE)
- **Ports**: 5900 (VNC), 9090 (HTTP)
- **Memory**: 4GB+ recommended for IDE

## Contributing

1. Make changes to plugin code
2. Run `./gradlew build` to verify
3. Run `./gradlew integrationTest` to test in IDE
4. Test in Docker: `cd docker && ./docker-helper.sh build && ./docker-helper.sh run`
5. Create PR with changes

## Debugging

### In IDE (Docker Container)

1. Start container: `./docker-helper.sh run`
2. Connect via VNC: `localhost:5900`
3. Open project in GoLand
4. Set breakpoints and debug normally

### Integration Tests

```bash
# Add diagnostic logging to code
# Run tests with debugging
./gradlew integrationTest --debug
```

### Docker Build Issues

```bash
# Check logs
docker logs lunar-ide

# Interactive shell in container
docker exec -it lunar-ide bash

# Check IDE installation
docker exec lunar-ide ls -la /home/lunar/ide/bin/
```

See [Container Execution Guide](docs/implementation/container-execution.md) for detailed troubleshooting.

## License

[Your License Here]

