---
id: "TOOLING"
title: "TOOLING: Unified Lua Toolchain Management"
type: "epic"
status: "planned"
priority: "high"
folders:
  - "[[features]]"
---

# Unified Lua Toolchain Management (`TOOLING`)

A ground-up reimagining of how Lunar models, discovers, provisions, resolves, and executes
external Lua binaries — interpreters, LuaRocks, linters, formatters, test runners — replacing
the organically-grown TOOL epic, the interpreter subsystem in `platform/`/`settings/`, the
per-tool settings services (`LuaCheckSettings`, `LuaRocksSettings`), and the Python-based
hererocks environment lifecycle (ROCKS-14/15/16) with **one coherent model**.

| ID | Feature | Priority | Status | Description |
| :--- | :--- | :---: | :--- | :--- |
| [`TOOLING-00`](00-de-risking/requirements.md) | **De-risking & Technical Spikes** | **M** | planned | Native-build and prebuilt-download spikes; download-infra validation. |
| [`TOOLING-01`](01-toolchain-model/requirements.md) | **Unified Toolchain Model & Registry** | **M** | planned | Descriptor-driven tool kinds; one inventory covering interpreters and tools; one discovery + version-probe engine. |
| [`TOOLING-02`](02-resolution-and-environments/requirements.md) | **Resolution, Binding & Environments** | **M** | planned | Project/global binding precedence, environment (toolchain set) concept, change events. |
| [`TOOLING-03`](03-execution-and-injection/requirements.md) | **Execution & Environment Injection** | **M** | planned | One process-execution service; unified PATH / LUA_PATH / LUA_CPATH injection for run configs and terminal. |
| [`TOOLING-04`](04-native-provisioning/requirements.md) | **Native Provisioning Engine** | **M** | planned | In-plugin replacement for hererocks: download/build interpreters + LuaRocks, install tools — no Python dependency. |
| [`TOOLING-05`](05-consumer-migration/requirements.md) | **Consumer Migration & Legacy Removal** | **M** | planned | Every consumer resolves through the new registry; legacy state/services deleted (clean break). |
| [`TOOLING-06`](06-settings-ui/requirements.md) | **Settings UI Consolidation** | **M** | planned | One Lua settings tree; per-tool pages fold in. |
| [`TOOLING-07`](07-health-and-diagnostics/requirements.md) | **Health Monitoring & Diagnostics** | **S** | planned | Unified health model with separated file-exists / probe-passed states; banners, notifications, diagnostics. |

**Delivery:** [Implementation Plan](tooling-implementation-plan.md) (dependency order,
milestones, effort ~60–95 eng-days, two-engineer parallelization) ·
[Product Requirements](tooling-product-requirements.md) ·
[Architecture Contract](tooling-architecture.md) ·
[Risks & Gaps](tooling-risks-and-gaps.md).

---

## Motivation

Tool handling grew organically across four epochs (interpreters in `platform/`+`settings/`,
TOOL Wave 10, ROCKS-06 server/executable resolution, ROCKS-14/15/16 hererocks lifecycle) and is
now inconsistent, dispersed, and hard to use or understand:

- **Four resolution patterns** for external binaries: `LuaToolManager.getEffectiveTool`
  (stylua, busted), dedicated settings services bypassing the registry entirely (luacheck via
  `LuaCheckSettings.executablePath`; luarocks run configs via `LuaRocksSettings.executablePath`),
  PATH/python probing (hererocks), and the parallel interpreter subsystem (lua).
- **Interpreters duplicate the whole tool stack**: separate PATH scanning
  (`LuaInterpreterService` vs `LuaToolDiscoveryService`), version probing (`Banner` vs
  `LuaToolValidator`), inventory, binding, and UI — and hererocks environments must straddle
  both systems plus the ROCKS-16 mode state machine.
- **State and UI dispersal**: four persistent components, five settings pages, duplicated
  path fields.
- **Defects**: silent cache staleness (`registerTool` fires no settings event), ambiguous
  `isValid`, mutating getters, run configs that ignore project bindings, dead validation APIs.
- **External dependency**: environment provisioning requires a working Python + hererocks
  install, and covers only lua/luarocks.

## Benefits

- **One mental model**: every external binary is a *tool*; an interpreter is a tool with a
  runtime capability; an environment is a provisioned set of tools.
- **Zero-dependency provisioning**: the plugin itself downloads/builds interpreters and
  installs tools — no Python, works on a fresh machine.
- **Consistency**: one resolution path, one execution service, one injection mechanism, one
  settings tree — every consumer behaves identically.
- **Extensibility**: adding a tool kind (ldoc, lua-language-server, redis-cli for REDIS-01)
  is a data descriptor, not a code change across five files.

## Supersedes

- **TOOL epic** (registry/binding/health) — concepts absorbed and generalized.
- **`platform/` interpreter discovery + `settings/` interpreter inventory** — unified into the
  registry (the `Target`/language-level model is retained and fed by it).
- **ROCKS-14/15/16 hererocks lifecycle** — replaced by native provisioning + environments.
- **`LuaCheckSettings` / `LuaRocksSettings` executable paths** — replaced by registry bindings
  (non-path options like luacheck args and rocks server URL are retained, relocated).
