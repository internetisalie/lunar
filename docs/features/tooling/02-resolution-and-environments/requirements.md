---
id: "TOOLING-02"
title: "02: Resolution, Binding & Environments"
type: "feature"
status: "done"
priority: "high"
parent_id: "TOOLING"
folders:
  - "[[features/tooling/requirements|requirements]]"
---

# TOOLING-02: Resolution, Binding & Environments

## Overview

The single answer to "which binary runs for kind X in project P". This feature delivers the
`toolchain.resolve.LuaToolResolver` precedence engine (architecture contract §3), the
project-level toolchain persistence (bindings, environments, active environment, kind-scoped
options — contract §7, clean break), the environment lifecycle operations
(create/adopt/switch/deactivate/remove records — provisioning itself is TOOLING-04), the
change-event contract (contract §4), and the **replacement semantics for the ROCKS-16
interpreter-mode state machine**, which is deleted: activating an environment changes
*resolution only*; stored bindings are never touched, so deactivation restores them
implicitly. Parent epic: [TOOLING](../requirements.md).

## Scope

### In Scope
- `LuaToolResolver`: the five-step precedence algorithm (active environment → project binding
  → global binding → first usable → null), a detailed-outcome API carrying kind-specific
  "not configured" information, a per-environment overload for the matrix runner, and
  capability-based runtime resolution.
- Project-level persisted state (`.idea/lunar.xml`): `bindings`, `environments`,
  `activeEnvironmentId`, kind-scoped options (`luacheck.arguments`, `luarocks.serverUrl`).
  Clean break — no migration of legacy fields.
- Environment lifecycle record operations: upsert-and-activate, activate/switch, deactivate,
  remove (with tool unregistration and optional directory deletion).
- Environment detection and adoption of existing env-shaped directories (generalizing
  `HererocksEnvDetector` heuristics to descriptor-driven kinds).
- The `LuaToolchainListener` event enumeration for every binding/environment/option mutation,
  plus the subscriber contract for caches.
- Target / language-level synchronization: deriving the project `Target` from the effective
  RUNTIME tool when it changes (replaces the `HererocksEnvBinder` managed-mode cascade).
- Stale-binding behavior at each precedence tier (skip + surface).

### Out of Scope
- Tool kind descriptors, registry/inventory, discovery, and version probing — **TOOLING-01**
  (this feature consumes `LuaToolKind`, `LuaRegisteredTool`, `LuaToolchainRegistry`).
- Process execution, PATH / `LUA_PATH` / `LUA_CPATH` injection, terminal customizer and its
  cache — **TOOLING-03** (it subscribes to the events defined here).
- Provisioning environments (downloading/building tools into them) — **TOOLING-04** (it calls
  the lifecycle operations defined here to register its results).
- Cutting consumers over (luacheck annotator, run configs, run-config interpreter dropdown,
  matrix runner, status-bar widget) and deleting legacy code (`rocks/env/*`, the
  `InterpreterMode` machine, `LuaToolManager`) — **TOOLING-05**. TOOLING-02 builds the new
  subsystem alongside the legacy one; the legacy mode machine keeps driving legacy consumers
  until 05 deletes it.
- Settings UI — **TOOLING-06**. Health monitoring / re-probing — **TOOLING-07**.

## Functional Requirements

| ID | Requirement | Priority | Description |
|----|-------------|----------|-------------|
| TOOLING-02-01 | **Precedence resolution** | M | `resolve(project, kindId)` returns the first hit of: active environment tool → project binding → global binding → first usable inventory tool of the kind → `null`. Exactly one implementation; no hardcoded default paths. |
| TOOLING-02-02 | **Resolution outcome reporting** | M | A detailed API returns *which tier* resolved (`ResolutionSource`) or, on failure, the kind id plus every skipped stale binding, so callers can render a kind-specific "not configured" hint. |
| TOOLING-02-03 | **Per-environment resolution** | M | `resolveIn(environment, kindId)` resolves strictly within one `LuaEnvironmentState` (no fallback to bindings/inventory) for the matrix runner. |
| TOOLING-02-04 | **Runtime resolution** | M | `resolveRuntime(project)` resolves the project's effective RUNTIME-capability tool across all runtime kinds; binding a RUNTIME kind clears any other RUNTIME kind binding in the same scope (single-runtime invariant). |
| TOOLING-02-05 | **Project toolchain persistence** | M | New project service persists `bindings: Map<kindId,toolId>`, `environments: List<LuaEnvironmentState>`, `activeEnvironmentId`, `kindOptions` in `.idea/lunar.xml` (component `LuaToolchainProjectSettings`). Clean break: legacy fields untouched/unread. |
| TOOLING-02-06 | **Binding mutation API** | M | Project (`setBinding`) and global (`setGlobalBinding`) binding mutators: set/clear, no-op suppression, single-runtime invariant, event fire. |
| TOOLING-02-07 | **Environment lifecycle** | M | Upsert-and-activate (dedupe by id *and* normalized root dir), activate/switch (unknown id = no-op), deactivate, remove — record operations only; no provisioning. |
| TOOLING-02-08 | **Mode machine replaced** | M | No `InterpreterMode`: activating an environment changes resolution only — the stored `bindings` map is not modified; deactivating restores binding-based resolution implicitly. No stash/restore state exists. |
| TOOLING-02-09 | **Target synchronization** | M | When the *effective* runtime (from environment or binding, not inventory fallback) changes, the project `Target`/language level is re-derived from its `LuaRuntimeInfo` and platform libraries reload on level change. |
| TOOLING-02-10 | **Change events** | M | Every mutation in 06/07/12 fires `LuaToolchainListener.TOPIC` synchronously with a typed payload (change kind, project, kindId/toolId/environmentId/optionKey); unchanged writes fire nothing. Subscriber contract: cheap handlers, drop-and-recompute caches, project filtering. |
| TOOLING-02-11 | **Stale-binding fallback** | M | At every tier, a bound/environment toolId that is missing from the inventory, of the wrong kind, or not usable is skipped (resolution falls through) and recorded in the detailed outcome. |
| TOOLING-02-12 | **Kind-scoped options** | M | `luacheck.arguments` and `luarocks.serverUrl` relocate to kind-scoped option maps: project override (non-blank) → app default (non-blank) → `""`. Mutators fire events. |
| TOOLING-02-13 | **Environment removal cleanup** | S | Removing an environment unregisters its inventory tools (`environmentId` match) and optionally deletes its directory on a pooled thread. |
| TOOLING-02-14 | **Detection & adoption** | M | On project open, an env-shaped directory (runtime + package-manager binaries present, conventional names) not already recorded is offered for one-click **Adopt**: its binaries are registered, an environment record is upserted and activated. |

## Detailed Specifications

### TOOLING-02-01: Precedence resolution
Five tiers, evaluated in order; the first usable hit wins. Tier 1 uses the project's
`activeEnvironmentId`; a `null` project skips tiers 1–2. Tier 4 is inventory order. There is
no tier 6 — in particular no hardcoded default path (deletes the `/usr/local/bin/luacheck`
default in `LuaCheckSettings`). See design §3.1.

### TOOLING-02-02: Resolution outcome reporting
`resolveDetailed` returns `Resolved(tool, source)` or `Unresolved(kindId, skipped)`.
`Unresolved` is enough for a caller to render "No usable <kind display name> configured —
add or bind one under Settings → Languages & Frameworks → Lua → Toolchain" without consulting
anything else; a helper builds that string from the kind registry. See design §2.1, §3.1.

### TOOLING-02-03: Per-environment resolution
The matrix runner (ROCKS-15) runs each row with *that row's* environment tools. `resolveIn`
therefore never falls back: an environment lacking a usable tool of the kind returns
`null` (contract §10.4) even when a project/global binding exists — a 5.1 row must not
silently run the project's 5.4 `luarocks`. See design §3.2.

### TOOLING-02-04: Runtime resolution
"The project interpreter" becomes "the resolved RUNTIME tool". RUNTIME is a capability
spanning multiple kinds (`lua`, `luajit`), so `resolveRuntime` iterates runtime kinds in
`LuaToolKindRegistry` declaration order at each tier. To keep the project's runtime choice
single-valued, `setBinding`/`setGlobalBinding` on a RUNTIME kind removes every other RUNTIME
kind entry from that scope's map in the same mutation. See design §3.3.

### TOOLING-02-05: Project toolchain persistence
A new `@State`-annotated project service (design §2.2) writing storage `lunar.xml`
(project level ⇒ `.idea/lunar.xml`, VCS-shared). Fields are XML-serializer-friendly
(`var` + defaults — the sanctioned serialization exception). Legacy fields on
`LuaProjectSettings.State` (`interpreter`, `interpreterMode`, `interpreterModeMigrated`,
`explicitInterpreter`, `explicitTarget`, `projectToolBindings`, `hererocksEnv`,
`hererocksEnvs`, `activeEnvId`, `rocksServerUrl`) are neither read nor written by the new
subsystem; TOOLING-05 deletes them.

### TOOLING-02-07 / TOOLING-02-08: Lifecycle & mode-machine replacement
`upsertEnvironmentAndActivate` replaces `LuaProjectSettings.upsertAndActivate`
(`settings/LuaProjectSettings.kt:236`) *minus* the bind step: it only mutates records and
fires events. There is no analog of `HererocksEnvBinder.bind`'s interpreter/target repointing
(`rocks/env/HererocksEnvBinder.kt:39`) — resolution (02-01) and the target synchronizer
(02-09) subsume it. Deactivation is a first-class operation (legacy conflated it with
removal in `HererocksEnvBinder.unbind`, `rocks/env/HererocksEnvBinder.kt:76`).

### TOOLING-02-09: Target synchronization
Replaces the `HererocksEnvState.toTarget` + managed-mode cascade
(`rocks/env/HererocksEnvState.kt:45-50`, `rocks/env/HererocksEnvBinder.kt:49-59`). A
project-level subscriber re-derives `Target` from the effective runtime's `LuaRuntimeInfo`
via `PlatformVersionRegistry.resolveTarget` and applies it through
`LuaProjectSettings.setTargetAndNotify`, reloading `PlatformLibraryIndex` when the language
level changes. It applies only when the effective runtime *tool id* changes and only for
sources `ACTIVE_ENVIRONMENT` / `PROJECT_BINDING` / `GLOBAL_BINDING` — the inventory-fallback
tier never drives the target, so a user who binds nothing keeps a manually chosen target.
When nothing resolves after a deactivation, the target keeps its last value (documented
behavior rule below). See design §2.5, §3.5.

### TOOLING-02-14: Detection & adoption
Generalizes `HererocksEnvDetector` (`rocks/env/HererocksEnvDetector.kt:17-64`): same
conventional directory names and immediate-children scan, but "env-shaped" is derived from
kind descriptors (a RUNTIME-capability binary and a PACKAGE_MANAGER-capability binary under
`<dir>/bin/` or `<dir>/` for Windows layouts). Adoption registers *every* kind binary found
in the environment (not just lua/luarocks — a provisioned luacheck/busted is adopted too),
then upserts-and-activates the environment record. Replaces `HererocksDetectStartup`
(`rocks/env/HererocksDetectStartup.kt:17`); the plugin.xml swap happens in TOOLING-05 to
avoid double notifications during the transition (design §7).

## Behavior Rules

- **Precedence is total and single-sourced**: no consumer implements its own ordering; the
  four legacy patterns (contract §3) all route here after TOOLING-05.
- **Activation never writes bindings**; deactivation never restores anything — there is
  nothing to restore. The `bindings` map is exclusively user/UI-mutated.
- **Skip-and-fall-through everywhere**: a stale reference never aborts resolution and never
  resolves to a broken tool; it is skipped and reported (mirrors today's
  `LuaToolManager.getEffectiveTool` fallback, `tool/LuaToolManager.kt:163-177`).
- **Resolution never touches disk**: usability comes from recorded `LuaToolHealth`
  (contract §2.3 — reads never mutate health). A tool deleted since its last check may
  resolve; execution (TOOLING-03) and health monitoring (TOOLING-07) surface that.
- **Events fire only on actual change**; setting a value to itself is silent.
- **Target stickiness**: if, after a change, no runtime resolves from env/bindings, the
  project target/language level keeps its current value rather than silently flipping.
- **VCS-shared environments with foreign toolIds** (checked out on another machine) simply
  skip at tier 1 (NOT_IN_INVENTORY) until re-adoption/health re-registers them.

## Test Cases

| # | Requirement | Given (input) | When (action) | Then (expected) |
|---|-------------|---------------|---------------|-----------------|
| 1 | TOOLING-02-01 | Inventory: luacheck A (usable), luacheck B (usable); project binds `luacheck→B`; no env | `resolve(project, "luacheck")` | B; `resolveDetailed` source = `PROJECT_BINDING` |
| 2 | TOOLING-02-01 | TC1 state + env E `toolIds=[C]` (C = usable luacheck) upserted and active | `resolve(project, "luacheck")` | C, source `ACTIVE_ENVIRONMENT`; after `deactivateEnvironment()` → B; after `setBinding("luacheck", null)` + global binds A → A (`GLOBAL_BINDING`); after `setGlobalBinding("luacheck", null)` → A (`INVENTORY_FALLBACK`, inventory order) |
| 3 | TOOLING-02-01/02 | Empty inventory | `resolveDetailed(project, "luacheck")` | `Unresolved(kindId="luacheck", skipped=[])`; `notConfiguredMessage` contains the kind display name "Luacheck" and the settings path; `resolve` = null |
| 4 | TOOLING-02-11 | Project binds `luacheck→B`; B unregistered from inventory | `resolveDetailed(project, "luacheck")` — (i) with no other tool, then (ii) after global binds A (usable) | (i) `Unresolved(kindId="luacheck", skipped=[(PROJECT_BINDING, "B", NOT_IN_INVENTORY)])` — the stale binding is recorded; (ii) `Resolved(A, GLOBAL_BINDING)` — B is skipped and resolution falls through to the global tier. (Note: `skipped` is carried only by `Unresolved`, per the §2.1 result type — a successful `Resolved` reports its `source`, not skips.) |
| 5 | TOOLING-02-11 | Project binds `luacheck→B`; B in inventory but `health.fileExists=false` | `resolveDetailed(project, "luacheck")` | B skipped with reason `UNUSABLE`, falls through per precedence |
| 6 | TOOLING-02-03 | Env E `toolIds=[R, P]` (R usable lua, P usable luarocks); project binds `luacheck→B` (usable) | `resolveIn(E, "luarocks")`; `resolveIn(E, "luacheck")` | `P`; `null` — **no** fallback to B |
| 7 | TOOLING-02-04 | Inventory: lua L (usable, runtime 5.4), luajit J (usable, runtime 2.1); project binds `luajit→J` | `resolveRuntime(project)`; then `setBinding("lua", L.id)` | J; after the set: bindings contain `lua→L` and **no** `luajit` entry; `resolveRuntime` = L |
| 8 | TOOLING-02-05 | State with 1 binding, 1 environment (id/name/rootDir/toolIds), active id, 1 kind option | `XmlSerializer` round-trip of `LuaToolchainProjectState` | Deserialized state deep-equals the original |
| 9 | TOOLING-02-06/10 | Subscriber on `LuaToolchainListener.TOPIC` | `setBinding("luacheck", B.id)` twice | Exactly one event: `PROJECT_BINDING_CHANGED`, project set, `kindId="luacheck"`, `toolId=B.id` |
| 10 | TOOLING-02-07 | Env spec S (blank id, rootDir `/p/.lua`); then spec S′ (different id, rootDir `/p/./.lua`) | `upsertEnvironmentAndActivate(S)`; `upsertEnvironmentAndActivate(S′)` | One environment record (dir-deduped, id from first upsert), fields merged from S′, active; events: `ENVIRONMENT_ADDED`+`ACTIVE_ENVIRONMENT_CHANGED`, then `ENVIRONMENT_UPDATED` only |
| 11 | TOOLING-02-07 | Environments [E1] active E1 | `activateEnvironment("nope")` | Returns false, active id still E1, no event |
| 12 | TOOLING-02-08 | Project binds `luacheck→B`; env E (usable luacheck C) | `upsertEnvironmentAndActivate(E)`; inspect `state.bindings`; `deactivateEnvironment()` | Bindings map still exactly `{luacheck→B}` throughout; resolve = C while active, B after deactivation; no stash fields exist on the state class |
| 13 | TOOLING-02-09 | Env E active; its runtime tool has `LuaRuntimeInfo(platform=LUAJIT, version="2.1.0-beta3")` | Synchronizer processes the activation event | Project target = `Target(LUAJIT, "2.1")` (via `resolveTarget`), language level `LUA51`; on deactivate with project-bound lua 5.4 runtime → `Target(STANDARD, "5.4")`, `LUA54` |
| 14 | TOOLING-02-09 | No env, no runtime binding; inventory has usable lua 5.1 (fallback tier); user target = STANDARD 5.4 | Any toolchain event | Target unchanged (fallback tier never drives target) |
| 15 | TOOLING-02-12 | App `kindOptions["luacheck.arguments"]="--std max"`; project `kindOptions` empty; then project sets `"--no-color"` | `effectiveKindOption("luacheck.arguments")` before/after | `"--std max"` then `"--no-color"`; the set fires `KIND_OPTION_CHANGED` with `optionKey="luacheck.arguments"` |
| 16 | TOOLING-02-13 | Env E with tools [R, P] (`environmentId=E.id`) + unrelated tool A | `removeEnvironment(E.id, deleteDir=false)` | E gone from state, active cleared if E was active; R and P unregistered from inventory; A untouched; events `ACTIVE_ENVIRONMENT_CHANGED` (if was active), `ENVIRONMENT_REMOVED` |
| 17 | TOOLING-02-14 | Temp dir `<base>/.lua` with executable `bin/lua` + `bin/luarocks`; no matching env record | Run detection startup logic; invoke Adopt | Detector returns the dir; adopter registers both binaries (environmentId set) and upserts+activates an env whose `toolIds` contain both; a dir already recorded (by normalized path) is not re-offered |

## Acceptance Criteria
- [ ] `resolve`/`resolveDetailed`/`resolveIn`/`resolveRuntime`/`resolveAll` implemented with
      the §3 precedence and covered by TC 1–7 (TOOLING-02-01…04).
- [ ] `LuaToolchainProjectSettings` persists and round-trips the four state fields
      (TOOLING-02-05, TC 8).
- [ ] All mutators fire the enumerated events exactly once per actual change (TOOLING-02-06,
      -07, -10, -12; TC 9–11, 15).
- [ ] No `InterpreterMode`-like state exists in the new subsystem; TC 12 passes
      (TOOLING-02-08).
- [ ] Target/language-level follow the effective runtime per TC 13–14 (TOOLING-02-09).
- [ ] Stale bindings skip and surface per TC 4–5 (TOOLING-02-11).
- [x] Detection/adoption works per TC 17 (TOOLING-02-14); removal cleanup per TC 16
      (TOOLING-02-13). Note (2026-07-09): the `LuaEnvironmentDetectionStartup` postStartupActivity
      registration — deferred at TOOLING-02 authoring ("no plugin.xml registration in this
      feature") and left dangling when TOOLING-05 removed the legacy `HererocksDetectStartup` — was
      wired up, deduped against the TOOLING-04 `LuaEnvRedetectionStartup` by skipping
      `.lunar-env.json` manifest-bearing (Lunar-provisioned) trees so a single project-open pass
      never double-notifies.

## Non-Functional Requirements
- **Threading**: resolution and state reads are safe on any thread and perform no disk,
  PSI, or VFS access. Detection/adoption run off the EDT (`ProjectActivity` coroutine /
  background task; VFS reads inside `runReadAction`). Directory deletion runs on a pooled
  thread. Target application (settings mutation + `PlatformLibraryIndex.reload`) marshals to
  the EDT. Events are delivered synchronously on the mutating thread; handlers must be cheap.
- **Memory**: no hard `Project` refs in app-level services (`LuaToolResolver` takes the
  project per call, as `LuaToolManager.getEffectiveTool` does today).
- **Performance**: `resolveDetailed` is O(env tools + bindings + inventory) map/list scans
  over in-memory state — no caching needed at this layer (TOOLING-03 caches derived
  directory sets).

## Dependencies
- **TOOLING-01** (blocking): `LuaToolKind` / `Capability` / `LuaToolKindRegistry`,
  `LuaRegisteredTool` (+ `isUsable`, `origin`, `environmentId`, `runtime: LuaRuntimeInfo?`
  with `platform: LuaPlatform` and `version: String`), `LuaToolchainRegistry` with
  `tool(id)`, `tools()`, `registerTool(path, kindId, origin, environmentId)`,
  `unregisterTool(id)`, `unregisterByEnvironment(environmentId)`, and the app-state
  `globalBindings` map (contract §2, §7).
- **TOOLING-00-06** (clean-break serialization spike) de-risks the XML round-trip of the new
  state shapes (TC 8).
- Consumed by: TOOLING-03 (env builder + terminal cache subscribe to the events), TOOLING-04
  (calls `upsertEnvironmentAndActivate` with provisioned results), TOOLING-05 (consumer cutover),
  TOOLING-06 (UI edits bindings/environments), TOOLING-07 (surfaces `Unresolved.skipped`).

## See Also
- Design: [design.md](design.md)
- Plan: [implementation-plan.md](implementation-plan.md)
- Contract: [../tooling-architecture.md](../tooling-architecture.md)
