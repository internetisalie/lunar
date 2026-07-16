---
id: "ROCKS"
title: "ROCKS: LuaRocks Integration"
type: "epic"
status: "done"
vf_icon: ✅
priority: "high"
folders:
  - "[[features]]"
---

# LuaRocks Integration Requirements (`ROCKS`)

Lunar provides deep integration with LuaRocks for dependency management, package discovery, multi-rock workspaces, registry configuration, and project lifecycle automation.

| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :---: | :--- | :--- |
| [`ROCKS-01`](01-project-initialization/requirements.md) | **Project Initialization & Setup** | **M** | **Full** | Scaffolding, Rockspec generation, and module resolution. |
| [`ROCKS-02`](02-package-browser/requirements.md) | **Package Browser** | **S** | **Full** | Remote search and repository exploration (split-view). |
| [`ROCKS-03`](03-dependency-resolution/requirements.md) | **Dependency Resolution** | **M** | **Full** | Hierarchical tree view and conflict detection. |
| [`ROCKS-04`](04-task-execution/requirements.md) | **Task Execution & Run Configurations** | **M** | **Full** | Target-based command execution (similar to Maven/Makefile). |
| [`ROCKS-05`](05-module-resolution/requirements.md) | **Rockspec Module Resolution** | **S** | **Full** | Derive source-path patterns from rockspec `build.modules` for require resolution, completion, and indexing. |
| [`ROCKS-06`](06-project-environment/requirements.md) | **Project LuaRocks Environment** | **M** | **Full** | Project-scoped server/registry + executable (TOOL-02) config; per-server credentials. |
| `ROCKS-08` | **Publishing & Lifecycle** | **C** | **Full** | Wizard for versioning and remote uploads. |
| [`ROCKS-09`](09-workspace-discovery/requirements.md) | **Multi-Rock Workspace Discovery** | **M** | **Full** | Recursively discover all project rockspecs (replaces single-root `projectRockspec`); foundational for multi-rock resolution. |
| [`ROCKS-10`](10-workspace-build/requirements.md) | **Workspace Build Orchestration** | **M** | **Full** | Build discovered rocks with `luarocks make` in topological dependency order. |
| [`ROCKS-11`](11-makefile-tasks/requirements.md) | **Makefile Task Integration** | **C** | **Full** | Enrich scaffolded Makefile targets; optional Makefile-plugin integration. |
| [`ROCKS-12`](12-project-view-roots/requirements.md) | **Project-View Roots & Marking** | **M** | **Full** | Mark the installed-rock tree (`lua_modules`) as External Libraries and first-party `build.modules` source roots in the Project view (`LuaRocksLibraryProvider` + `LuaRockSourceRootDecorator`, registered in `plugin.xml`). |
| [`ROCKS-14`](14-hererocks-environment/requirements.md) | **Hererocks Environment Lifecycle** | **S** | **Superseded** | Shipped, then removed by the TOOLING-05 clean break (`b277bc46`); replaced by TOOLING-02/04 native provisioning — see [TOOLING](../tooling/requirements.md). |
| [`ROCKS-15`](15-multi-version-development/requirements.md) | **Multi-Version Rocks Development** | **C** | **Superseded** | Shipped, then removed with ROCKS-14 by the TOOLING-05 clean break; replaced by TOOLING-02/04 — the matrix-runner UI survived, rehomed under Tools → Lua Toolchain. See [TOOLING](../tooling/requirements.md). |
| [`ROCKS-16`](16-package-browser-redesign/requirements.md) | **Package Browser Redesign (Plugins idiom)** | **S** | **Not Implemented** | Redesign the ROCKS-02 browser to the IDE Plugins-page idiom (tool window): Marketplace/Installed tabs, canonical `--tree` install target, honest error/empty states, rich detail pane. Absorbs BUG-363/365/366/367/368. |

> **Table aligned to front-matter 2026-07-16:** the previously flagged drift (ROCKS-12 shown as
> Not Implemented though `done`; ROCKS-14/15 shown as Full though `superseded`) is corrected above.
> Per-feature `requirements.md` front-matter remains canonical.

---

## Relationship to the TOOLING track

*(Rewritten 2026-07-16 — the narrative below previously described the pre-TOOLING-05 world of an
app-level `LuaRocksSettings.executablePath` with a `luarocks`-on-`PATH` default. `LuaRocksSettings`
no longer exists.)*

All ROCKS consumers (run configs, workspace build, publish, browser search/metadata/actions) resolve
the `luarocks` binary through the shared facade
[`LuaRocksEnvironment.resolveExecutable`](../../../src/main/kotlin/net/internetisalie/lunar/rocks/LuaRocksEnvironment.kt),
which delegates to the TOOLING-01/02 toolchain stack (`LuaToolResolver.resolve(project, "luarocks")`)
— the cutover happened in TOOLING-05 Phase 2. There is **no hardcoded `PATH` fallback**: when no
usable `luarocks` tool resolves, the result is `null` and each call site surfaces a kind-specific
"configure a LuaRocks tool" hint. The registry server likewise resolves via
`LuaRocksEnvironment.resolveServer` (project `rocksServerUrl` override > app-level toolchain kind
option > none). See [ROCKS-06](06-project-environment/requirements.md) for the environment facade and
[TOOLING](../tooling/requirements.md) for the resolution stack.

## Motivation
Managing Lua dependencies manually is error-prone and lacks IDE visibility. Integrating LuaRocks directly into the workflow provides a standardized ecosystem for package management, reducing context switching between the terminal and the editor.

## Benefits
- **Efficiency**: Faster dependency management through parallel operations and IDE-native UI.
- **Reliability**: Semantic versioning validation and conflict detection.
- **Traceability**: Full visibility into package origins and transitive dependencies.
- **Workflow Integration**: Automatic path configuration and rockspec maintenance.

## Detailed Implementation Status

The generated rollup files this section used to point at (`docs/status.md` / `docs/status-detail.md`)
were **retired 2026-07-09** — canonical per-feature status is each feature's `requirements.md`
front-matter (`status:`); `git grep '^status:' docs/features` is the live picture. There is no
hand-maintained class table here either — an earlier one had drifted to fictional class names
(`LuaRockspecGenerator`/`LuaRocksProjectTemplates`/`LuaRocksPathResolver`/`LuaRocksCommandLine` — none
exist; the real backing classes are `LuaRocksProjectGenerator`, `LuaRocksScaffolder`, `LuaRocksTemplates`,
and `LuaRocksRunConfiguration`). *(Section updated 2026-07-16.)*
