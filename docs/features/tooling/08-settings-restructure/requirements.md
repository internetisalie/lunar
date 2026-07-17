---
id: "TOOLING-08"
title: "08: Lua Settings Restructure"
type: "feature"
status: "in_progress"
priority: "medium"
parent_id: "TOOLING"
folders:
  - "[[features/tooling/requirements|requirements]]"
---

# TOOLING-08: Lua Settings Restructure

## Overview

The consolidated *Lua Project* settings page shipped by [TOOLING-06](../06-settings-ui/requirements.md)
optimizes for rare actions (environment activation, all-kind bindings) and buries the common ones
(picking a platform target, editing the four tools most users touch). This feature restructures the
Lua settings pages of the [TOOLING epic](../requirements.md) for discoverability and layout
consistency: it surfaces an explicit platform-target control, splits the binding list into
common/advanced, adds the missing global-bindings UI, and standardizes the FormBuilder-era panels on
the Kotlin UI DSL. It absorbs [BUG-362](../../bug-fixes/362-platform-target-selection-discoverability/bug-report.md)
and [BUG-369](../../bug-fixes/369-settings-panels-vertical-spacing/bug-report.md) as acceptance
criteria.

## Scope

### In Scope
- A prominent, always-visible **Platform Target** control (platform + version) on the *Lua Project*
  page, backed by `PlatformVersionRegistry` and persisted through `LuaProjectSettings`.
- Splitting the *Toolchain Bindings* group into a visible **common** set and a collapsible
  **advanced** set, filtered by tool capability.
- Evicting the `redis-server` / `valkey-server` kinds (empty-capability platform servers) from the
  general bindings list while keeping them registerable and resolvable.
- A minimal **global default bindings** section on the app-level *Toolchain* page, driving
  `LuaToolchainRegistry.setGlobalBinding`.
- Migrating `LuaApplicationSettingsPanel` and `LuaRocksGeneratorPeer` from `FormBuilder` to the
  Kotlin UI DSL, and auditing the remaining Lua settings panels for spacing consistency.
- Making the app-vs-project inheritance of the duplicated Luacheck-arguments and LuaRocks server-URL
  fields explicit in their labels/placeholders.

### Out of Scope
- Status-bar widget visibility in non-Lua projects, provision-dialog defects, and the
  lua-language-server kind-registry gap — filed as separate bugs in the current BUG-370–377
  workstream; cross-referenced by topic, not planned here.
- Terminology renames outside the settings pages (e.g. the run-config "Interpreter" label) — a
  separate chore. Any label this feature *touches* must still land on the wording defined in
  [design.md §4](design.md).
- The LuaRocks package-browser redesign — planned as [ROCKS-16](../../rocks/16-package-browser-redesign/requirements.md).
- Any change to the toolchain resolution/precedence engine, environment model, or persisted schema
  beyond adding the explicit-target override flag.

## Functional Requirements

| ID | Requirement | Priority | Description |
|----|-------------|----------|-------------|
| TOOLING-08-01 | **Explicit platform-target control** | M | The *Lua Project* page shows an always-visible Platform + Version pair of combos. Selecting a platform repopulates the version combo from `PlatformVersionRegistry.getVersions(platform)`. Applying persists the chosen `Target` via `LuaProjectSettings`. |
| TOOLING-08-02 | **Auto-vs-explicit target modes** | M | The platform combo includes an `Auto (from runtime)` sentinel. In Auto mode the target follows the resolved runtime (existing `LuaTargetSynchronizer` behavior). Choosing a concrete platform switches to explicit mode and pins the target; the synchronizer must not overwrite an explicit target. |
| TOOLING-08-03 | **Common/advanced bindings split** | M | The *Toolchain Bindings* group renders only common-capability kinds by default; the remaining kinds live in a collapsed *Advanced tools* sub-group. Platform-server kinds (`redis-server`, `valkey-server`) appear in neither. |
| TOOLING-08-04 | **Platform servers evicted, still resolvable** | M | `redis-server` / `valkey-server` are absent from the Project bindings UI, yet `LuaToolResolver.resolve(project, "redis-server")` still resolves them exactly as before. |
| TOOLING-08-05 | **Global default bindings UI** | M | The app-level *Toolchain* page exposes one binding combo per common non-runtime kind (plus the runtime), writing through `LuaToolchainRegistry.setGlobalBinding` on apply and reading `LuaToolchainRegistry.globalBindings()` on reset. |
| TOOLING-08-06 | **Explicit inherit labelling** | S | The project Luacheck-arguments and LuaRocks server-URL fields show the effective inherited value in their empty-text placeholder (e.g. `Inherit (app default: --std max)`), mirroring how binding combos render `Inherit (<resolved>)`. |
| TOOLING-08-07 | **DSL panel migration** | S | `LuaApplicationSettingsPanel` and `LuaRocksGeneratorPeer` are rebuilt with `com.intellij.ui.dsl.builder.panel { }`; the FormBuilder imports are removed. |
| TOOLING-08-08 | **Spacing consistency audit** | C | The remaining Lua settings panels (`LuaEditorOptionsConfigurable`, `LuaCodeStyleSettings`) are recorded as platform-driven (no manual layout) so the tree's vertical rhythm is uniform once 08-07 lands. |

## Detailed Specifications

### TOOLING-08-01 / 08-02: Platform-target control and modes
The Project page currently has **no** target control — `LuaProjectConfigurable` shows only a
read-only *Resolved Runtime* label (`LuaProjectConfigurable.kt:72-76`), and the `Target` is derived
from the resolved runtime by `LuaTargetSynchronizer` (`LuaTargetSynchronizer.kt:64-78`). The user
cannot pin `Redis` if their discovered interpreter probes as Standard Lua — the exact BUG-362 gap.

- **Platform combo** items: an `Auto` sentinel followed by every platform in
  `PlatformVersionRegistry.platforms()` (`STANDARD, LUAJIT, NGX, PANDOC, REDIS, VALKEY, TARANTOOL`),
  rendered by `LuaPlatform.label`.
- **Version combo** items: `PlatformVersionRegistry.getVersions(selectedPlatform)`, rendered by
  `VersionEntry.label`; disabled while `Auto` is selected.
- **Auto mode** is the state when `LuaProjectSettings.State.explicitTarget == false`. Applying Auto
  clears the explicit flag and lets the synchronizer own the target.
- **Explicit mode**: applying a concrete platform+version sets `explicitTarget = true` and calls
  `LuaProjectSettings.setTargetAndNotify(Target(platform, version))`.

### TOOLING-08-03 / 08-04: Bindings split and platform-server eviction
`orderedKinds()` today returns *all 10* kinds (`LuaProjectConfigurable.kt:203-206`), one combo row
each. Classification is by capability (see [design §3.1](design.md)):
- **Common**: the runtime kind (`Capability.RUNTIME`) plus `luarocks`, `luacheck`, `stylua`,
  `busted`.
- **Advanced**: every other kind that has at least one capability (`tarantool` runtime is common by
  the runtime rule; `luacov` is advanced).
- **Excluded (platform servers)**: kinds whose `capabilities.isEmpty()` — today exactly
  `redis-server` and `valkey-server` (`LuaToolKindRegistry.kt:131-152`). They stay in
  `LuaToolKindRegistry.all()` and remain resolvable; only the Project bindings UI omits them.

### TOOLING-08-05: Global default bindings
`LuaToolchainRegistry.setGlobalBinding` (`LuaToolchainRegistry.kt:185-213`) is fully implemented and
honored by resolution, but no panel calls it. Add one binding combo per common kind to
`LuaToolchainConfigurable`, each with an `Inherit` (unset → remove global binding) item and the
inventory tools for that kind, mirroring the project page's `LuaBindingItem` combos.

### TOOLING-08-06: Explicit inherit labelling
The project Luacheck-args field placeholder becomes `Inherit (app default: <value>)` when the app
default is non-blank, else `Inherit (no app default)`. The rocks-URL placeholder becomes
`Inherit (app default: <url>)` / `Inherit (luarocks.org)`. Values are read from
`LuaToolchainRegistry.kindOption(...)`.

## Behavior Rules
- The platform/version combos are **buffered** — edits take effect only on `apply()`, consistent
  with the existing `BoundSearchableConfigurable` pattern (`LuaProjectConfigurable.kt:99-105`).
- Switching the platform combo to a platform with a different version set resets the version combo
  to that platform's `defaultVersion(...)`.
- In Auto mode the version combo is disabled and shows the synchronizer-derived version read-only.
- Global-binding runtime selection continues to honor the single-runtime invariant already enforced
  in `setGlobalBinding` (`LuaToolchainRegistry.kt:189`).
- Advanced-tools group starts collapsed; its state need not persist across dialog reopen.

## Test Cases

| # | Requirement | Given (input) | When (action) | Then (expected) |
|---|-------------|---------------|---------------|-----------------|
| 1 | TOOLING-08-01 | Fresh project, `LuaProjectSettings` default target `STANDARD/5.4` | Panel is built and reset | Platform combo selects `Auto`; version combo is disabled |
| 2 | TOOLING-08-01 | Panel open, platform combo set to `REDIS` | Version combo repopulated | Version combo items are exactly `["5", "6", "7+"]` (from `PlatformVersionRegistry.getVersions(REDIS)`) |
| 3 | TOOLING-08-02 | Platform=`REDIS`, version=`7+` selected | `apply()` | `LuaProjectSettings.state.getTarget()` == `Target(REDIS, "7+")` and `state.explicitTarget == true` |
| 4 | TOOLING-08-02 | `explicitTarget == true`, a runtime probing as `STANDARD` is bound | `LuaTargetSynchronizer.onEvent(TOOL_UPDATED)` fires | Target stays `REDIS` (synchronizer no-ops on explicit target) |
| 5 | TOOLING-08-02 | Platform combo set back to `Auto` | `apply()` | `state.explicitTarget == false`; next synchronizer recompute applies the runtime-derived target |
| 6 | TOOLING-08-03 | Default registry (10 kinds) | Panel built | Common group rows == `[Lua/LuaJIT/Tarantool runtime, LuaRocks, luacheck, StyLua, Busted]`; Advanced group contains `LuaCov`; neither contains `redis-server`/`valkey-server` |
| 7 | TOOLING-08-04 | A `redis-server` tool registered, no project binding | `LuaToolResolver.resolve(project, "redis-server")` | Returns the registered `redis-server` tool (unchanged from pre-08) |
| 8 | TOOLING-08-05 | App inventory has a `luacheck` tool T1, no global binding | Select T1 in the global luacheck combo, `apply()` | `LuaToolchainRegistry.globalBindings()["luacheck"] == T1.id` |
| 9 | TOOLING-08-05 | Global luacheck bound to T1 | Select `Inherit`, `apply()` | `globalBindings()` has no `luacheck` key |
| 10 | TOOLING-08-06 | App luacheck-args = `--std max`, project field empty | Panel reset | Project luacheck-args field empty-text == `Inherit (app default: --std max)` |
| 11 | TOOLING-08-07 | `LuaApplicationSettingsPanel` rebuilt on the DSL | `isModified` after toggling type-inference | Returns `true` (behavior preserved through the migration) |

## Acceptance Criteria
- [ ] TOOLING-08-01: platform + version combos are visible on the *Lua Project* page and persist the chosen target.
- [ ] TOOLING-08-02: Auto and explicit modes behave per TC 3–5; the synchronizer respects an explicit target.
- [ ] TOOLING-08-03: bindings render as common/advanced per TC 6.
- [ ] TOOLING-08-04: platform-server kinds are UI-absent but still resolvable per TC 7.
- [ ] TOOLING-08-05: global bindings UI writes/reads `setGlobalBinding`/`globalBindings` per TC 8–9.
- [ ] TOOLING-08-06: inherit placeholders render per TC 10.
- [ ] TOOLING-08-07: both target panels compile on the Kotlin UI DSL with no `FormBuilder` import.
- [ ] VNC/`verify-in-ide` confirms consistent vertical spacing and a discoverable target control (DoD real-flow gate).

## Non-Functional Requirements
- **Threading**: all panel construction, reset, and apply run on the EDT with no I/O; combo
  repopulation is pure in-memory registry reads (`PlatformVersionRegistry`, `LuaToolchainRegistry`).
  Any tool resolution used for placeholder text (`LuaToolResolver.resolve`) must already be
  fast/cached (it is used inline today at `LuaProjectConfigurable.kt:272`).
- **Contract**: Kotlin UI DSL / `JB*` components only; `val`-first; no `!!`; ≤30 logic lines/function;
  ≤3 args including private helpers (buffered controls carried in a `Controls` inner class, per the
  existing `ProjectControls` pattern).
- **Memory**: no hard `Project`/`Editor` refs retained; the configurables already scope their message
  bus connections to `disposable`.

## Dependencies
- [TOOLING-02](../02-resolution-and-environments/requirements.md) — bindings, environments, resolver.
- [TOOLING-06](../06-settings-ui/requirements.md) — the settings pages this restructures.
- `PlatformVersionRegistry` / `Target` / `LuaProjectSettings` — the target model.

## See Also
- Design: [design.md](design.md)
- Plan: [implementation-plan.md](implementation-plan.md)
- Risks: [risks-and-gaps.md](risks-and-gaps.md)
