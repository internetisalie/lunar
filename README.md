# Lunar

[![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
![Lua 5.1–5.5](https://img.shields.io/badge/Lua-5.1--5.5-000080.svg)
![IntelliJ Platform 2026.1+](https://img.shields.io/badge/IntelliJ_Platform-2026.1%2B-000000.svg)

**Lunar** is a Lua language plugin for the IntelliJ Platform — GoLand, IntelliJ IDEA,
PyCharm, CLion, WebStorm, and other JetBrains IDEs. It targets **Lua 5.1–5.5** and provides
syntax highlighting, code completion, navigation, type inference (LuaCATS/LuaDoc),
documentation, inspections, static analysis (Luacheck), formatting, and remote debugging.

## Features

- **[SYNTAX]** Syntax & editor support — highlighting, folding, brace matching, code formatting
- **[COMP]** Code completion — keywords, symbols, cross-file, type-driven
- **[NAV]** Navigation — go to definition, find usages, structure view, gutter markers, references
- **[TYPE]** Type system — LuaCATS annotations, type inference, function signatures
- **[DOC]** Documentation — Quick Doc, LuaCATS/LuaDoc highlighting, parameter info
- **[INSP]** Inspections & diagnostics — undeclared/unused variables, type mismatches, quick fixes
- **[ANALYSIS]** Static analysis — Luacheck integration via external annotator
- **[FORMAT]** Formatting — indentation, alignment, spacing, StyLua-compatible
- **[REFACT / INTENT]** Refactoring & intentions — rename, labels, introduce variable, string conversions
- **[DEBUG / RUN]** Debugging & execution — breakpoints, stack frames, remote (DBGp/MobDebug) debugging, REPL
- **[EDITOR]** Editor ergonomics — structural editing, breadcrumbs, and the long-tail editor extension points
- **[TARGET]** Runtime environment configuration — select a platform/version target with target-aware standard-library resolution, stub-backed today for **Standard Lua (5.1–5.4), Redis (5/6/7), and Valkey (7.2/8)** (LuaJIT, OpenResty, Tarantool, and Pandoc are selectable targets but not yet backed by bundled library stubs)
- **[TOOLING]** Unified Lua toolchain — model, discover, provision, and resolve interpreters and tools
- **[TOOL]** Tool inventory — registry for external Lua binaries (`luarocks`, `luacheck`, `lua-format`, …)
- **[ROCKS]** LuaRocks integration — rockspec support, dependency management, package discovery, multi-rock workspaces
- **[REDIS]** Redis & Valkey — server-side Lua scripting (`redis.*` / `server.*`), sandbox inspections, connection-aware typing
- **[SCHEMA]** Schema-driven data files — JSON-schema-backed validation & completion for `.rockspec`, `.luacheckrc`, and other Lua data/config
- **[AI]** AI-assisted development — MCP server + semantic context tools *(planned, post-MVP)*

See [docs/features.md](docs/features.md) for the full feature index and
[docs/roadmap.md](docs/roadmap.md) for the dependency-ordered backlog.

## Building & Testing

Requires **JDK 21**. The Gradle wrapper (`./gradlew`) pins Gradle 8.14.4.

```bash
./gradlew buildPlugin      # compile + verifyPlugin + build the distributable zip
./gradlew test             # unit-test suite
./gradlew test --tests "*Glob*"   # a single test pattern
./gradlew integrationTest  # IDE-Starter integration tests (downloads GoLand, ~1.2 GB first run)
```

The distributable plugin zip is written to `build/distributions/lunar-<version>.zip`.

## IDE Configuration

The IDE the plugin compiles and tests against is controlled by `gradle.properties`:

```properties
platformType    = GO         # IDE for builds: GO (GoLand), IC (IntelliJ Community), PY, WS, …
platformVersion = 2026.1.3   # compile + unit-test platform (e.g. go:goland:2026.1.3)
testVersion     = 2026.1.3   # ide-starter INTEGRATION-test IDE (read at runtime by IdeProductResolver)
```

`pluginSinceBuild = 261` keeps the plugin compatible across the whole 2026.1.x branch.
`testVersion` is parsed directly from this file by `IdeProductResolver` in `src/integrationTest`
(not by the Gradle plugin), so keep it in sync by hand. The containerized debug IDE version is
separate — `IDE_VERSION` in [docker/Dockerfile](docker/Dockerfile).

To target a different IDE:

```bash
sed -i 's/platformType = .*/platformType = IC/' gradle.properties   # IntelliJ Community
./gradlew buildPlugin
```

## Docker / VNC (containerized IDE)

A containerized GoLand with the plugin pre-installed, driven over VNC, is available for live
verification:

```bash
cd docker
./docker-helper.sh build    # build the image (pre-stages GoLand)
./docker-helper.sh run      # start the container; IDE auto-launches
# connect a VNC client to localhost:5900
./docker-helper.sh stop
```

See [docker/README.md](docker/README.md) for the full guide and
[docs/agent-debugging-requirements.md](docs/agent-debugging-requirements.md) for the
terminal + VNC debug loop.

## Continuous Integration

GitHub Actions ([`.github/workflows/`](.github/workflows/)) runs the build + unit-test gate on every
push and pull request — `buildPlugin` plus the unit suite, provisioning fonts and a Lua toolchain for
the headless editor/debug tests. `integrationTest` and parser/lexer regeneration are **not** run in
CI (they need a display/license and local generator artifacts respectively).

**Cutting a release:** the git tag is the source of truth for the version. Push a `v*` tag on a green
commit (`git tag v1.2.3 && git push origin v1.2.3`); the build job builds + tests at that version
and, only if it passes, the release job publishes the exact tested zip as a GitHub Release with the
top `CHANGELOG.md` section as notes.

## Project Structure

```
src/main/kotlin/net/internetisalie/lunar/
├── analysis/     # Static analysis (Luacheck integration)
├── lang/         # Core language support (lexer, parser, psi, indexing, syntax, completion, format)
├── luacats/      # LuaCATS type-annotation support
├── luadoc/       # LuaDoc support
├── platform/     # Platform/target version model
├── refactoring/  # Rename and refactor providers
├── run/          # Run/Debug configuration (DBGp/MobDebug adapters, REPL)
├── settings/     # Language level (5.1–5.5) & interpreter settings
├── toolchain/    # Interpreter discovery/probing & provisioning
└── util/         # Utilities

src/main/gen/                # Generated parser + lexer (committed by hand; never regenerated in CI)
src/main/lua/                # Vendored debugger runtime (MobDebug)
src/main/resources/runtime/  # Lua stdlib API stubs (5.1–5.4)
src/integrationTest/kotlin/  # IDE-Starter integration tests
src/test/kotlin/             # Unit tests
docs/                        # Spec-driven feature documentation (see docs/features.md)
docker/                      # Containerized IDE test harness (VNC)
```

The on-disk tree is authoritative — packages are added/renamed over time.

## Contributing

1. Read [docs/engineering-contract.md](docs/engineering-contract.md) — the binding coding standard
   (threading/EDT rules, PSI/memory hygiene, naming, test conventions).
2. Make atomic, well-described commits (explain the *why*).
3. Verify before pushing: `./gradlew test` and, for style, `./gradlew ktlintFormat ktlintCheck`.
4. Update [CHANGELOG.md](CHANGELOG.md) for any user-facing change.
5. Open a PR (keep it small and focused).

## License

Lunar is licensed under the [Apache License, Version 2.0](LICENSE).

It embeds and derives from several third-party works (the Sylvanaar "Lua for IDEA" plugin, the
IntelliJ Platform, EmmyLua, MobDebug, and the Lua standard library). Their copyright notices and
licenses are recorded in [NOTICE](NOTICE) and [THIRD-PARTY.md](THIRD-PARTY.md).
