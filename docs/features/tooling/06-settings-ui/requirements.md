---
id: "TOOLING-06"
title: "06: Settings UI Consolidation"
type: "feature"
status: "planned"
priority: "high"
parent_id: "TOOLING"
folders:
  - "[[features/tooling/requirements|requirements]]"
---

# TOOLING-06: Settings UI Consolidation

## Overview

Collapses Lunar's five tool-related settings pages into **one Lua settings tree**
(user decision, [PRD Resolved Decision 3](../tooling-product-requirements.md)): the existing
*Lua* application page keeps its language options; a new app-level **Toolchain** child page
presents the unified tool inventory (all kinds, interpreters included) with
add/discover/provision/remove/re-check actions and per-kind option sections; the existing
project-level **Lua Project** child page gains the environment selector and per-kind binding
combos, and its interpreter combo is replaced by the RUNTIME binding plus a read-only
resolved-target/language-level display. The UI target is fixed by the
[architecture contract §8](../tooling-architecture.md).

## Scope

### In Scope
- The `toolchain.ui` package (contract §1): the new **Toolchain** application configurable,
  the rewritten **Lua Project** project configurable, and the inventory table component.
- Removal of the settings **pages and panels** that fold in: *Lua Tools*
  (`tool/ui/LuaToolsConfigurable`), *LuaRocks* (`rocks/run/LuaRocksSettingsConfigurable`),
  *LuaCheck* (`analysis/luacheck/LuaCheckSettingsPanel`), the interpreters table on the *Lua*
  app page (`settings/LuaInterpretersTable`), and the legacy Lua Project panel
  (`settings/LuaProjectSettingsPanel` + `settings/LuaProjectSettingsConfigurable`) — including
  their `plugin.xml` registrations.
- Correct `isModified`/`apply`/`reset` semantics against the TOOLING-01/02 state, with change
  events fired on apply (fixes today's silent panel apply,
  `settings/LuaProjectSettingsPanel.kt:124-156`).
- Unit tests (configurable instantiation, registration-tree assertions, apply round-trips,
  event firing) plus human-verification items for the visual flows.

### Out of Scope
- Registry/resolver/state **design and implementation** — TOOLING-01/02 (this feature consumes
  their APIs; the consumed surface is pinned in [design.md §2.6](design.md)).
- The Provision dialog internals — TOOLING-04 (the *Provision…* button only opens it).
- Consumer cutover and deletion of **non-UI** legacy classes/services/state fields
  (`LuaToolManager`, `LuaCheckSettings`, `LuaRocksSettings`, `LuaInterpreterService`,
  `LuaApplicationSettings.State.interpreters`, run-config interpreter dropdowns via
  `platform/LuaInterpreterComponent.kt:18`) — TOOLING-05.
- Health banners/notifications and their "configure" links — TOOLING-07 (the inventory
  *Health* column here only renders the `LuaToolHealth` shape from TOOLING-01,
  contract §2.3).

## Functional Requirements

| ID | Requirement | Priority | Description |
|----|-------------|----------|-------------|
| TOOLING-06-01 | **One Lua settings tree** | M | Exactly one Lua tree under *Languages & Frameworks*: *Lua* (app) → *Toolchain* (app child) + *Lua Project* (project child). No Lua-related page remains under *Tools*. |
| TOOLING-06-02 | **Unified inventory table** | M | The Toolchain page shows one table over the whole registry inventory — every kind, RUNTIME entries included — with columns Kind, Name, Path, Version, Origin, Health. |
| TOOLING-06-03 | **Inventory actions** | M | Toolbar actions: Add (file picker), Auto-Discover, Provision…, Remove, Re-check. Probing/discovery/provisioning never run on the EDT; the table refreshes on the EDT afterwards. |
| TOOLING-06-04 | **Kind option sections** | M | The Toolchain page hosts app-default per-kind options: luacheck arguments and LuaRocks default server URL, buffered (apply/reset) and firing the toolchain topic on apply. |
| TOOLING-06-05 | **Project bindings & environment UI** | M | The Lua Project page offers an active-environment selector and one binding combo per built-in kind, filtered to inventory entries of that kind, with an "Inherit" entry as default. |
| TOOLING-06-06 | **Runtime replaces interpreter combo** | M | The interpreter combo, platform/version combos, and hererocks-managed checkbox are gone; the RUNTIME binding row plus a read-only resolved-runtime/target/language-level display replace them (TOOLING-02 resolution semantics). |
| TOOLING-06-07 | **Retained project fields** | M | Rocks server URL override, source path patterns, and the underscore-suppression checkbox remain on the Lua Project page with today's semantics. |
| TOOLING-06-08 | **Legacy page removal** | M | The Lua Tools, LuaRocks and LuaCheck pages (classes + `plugin.xml` registrations) are deleted; the interpreters table disappears from the Lua app page. |
| TOOLING-06-09 | **Apply fires events** | M | Every apply that changes toolchain state fires `LuaToolchainListener.TOPIC`; applying non-toolchain project fields fires `LuaSettingsChangedListener.TOPIC`. No silent state writes from panels. |
| TOOLING-06-10 | **Stable searchable IDs** | S | Both new configurables implement `SearchableConfigurable` with stable IDs equal to their FQ class names. |
| TOOLING-06-11 | **Project luacheck-arguments override** | S | The Lua Project page offers a luacheck arguments override (empty = inherit the app default), per contract §7's project-retained kind options. |
| TOOLING-06-12 | **Health column rendering** | S | Health renders as icon + short text with the full reason as tooltip, distinguishing missing / not-executable / probe-failed / never-probed / OK. |

## Detailed Specifications

### TOOLING-06-01: One Lua settings tree
Target tree (contract §8):

```
Settings → Languages & Frameworks
└─ Lua                    net.internetisalie.lunar.settings.LuaApplicationSettingsConfigurable (kept)
   ├─ Lua Project         net.internetisalie.lunar.toolchain.ui.LuaProjectConfigurable (new)
   └─ Toolchain           net.internetisalie.lunar.toolchain.ui.LuaToolchainConfigurable (new)
```

Sibling order is the platform's alphabetical default ("Lua Project" before "Toolchain");
the contract §8 diagram's ordering is illustrative, not normative. The three legacy
`groupId="tools"` registrations (`plugin.xml:436-439`, `507-510`, `512-515`) are removed.

### TOOLING-06-02: Unified inventory table
Column value rules are specified in [design.md §3.3](design.md). RUNTIME entries (kinds with
`Capability.RUNTIME`, contract §2.1) appear as ordinary rows; their Name cell shows the probed
product/version from `LuaRegisteredTool.runtime`.

### TOOLING-06-03: Inventory actions
Threading mirrors the current page's proven pattern (`tool/ui/LuaToolsConfigurable.kt:49-57`
for pooled discovery + `invokeLater` refresh; `:109-117` for pooled re-check). The current
page's Add action runs the version probe on the EDT (`LuaToolsConfigurable.kt:94-100` calls
`LuaToolManager.registerTool`, which shells out) — the new Add action must move registration
to a pooled thread. *Provision…* opens the TOOLING-04 dialog and does nothing else; the table
refreshes via the toolchain topic subscription.

### TOOLING-06-04: Kind option sections
Two groups below the table: **Luacheck** (arguments, expandable text field; today
`analysis/luacheck/LuaCheckSettings.arguments`) and **LuaRocks** (default server URL; today
`rocks/run/LuaRocksSettings.serverUrl`). The executable-path fields of both legacy pages are
NOT carried over — paths live only in the inventory/bindings (contract "Supersedes"). The
LuaCheck page's download link (`analysis/luacheck/LuaCheckSettingsPanel.kt:25`, an empty
no-op handler today) is dropped; *Provision…* supersedes it. Verified: the LuaCheck page
holds no inspection-behavior options — only executable, dead link, and arguments
(`LuaCheckSettingsPanel.kt:16-33`) — so the whole page folds in.

### TOOLING-06-05: Project bindings & environment UI
Environment combo lists the project's environments by name plus a `None (use bindings)`
entry. Binding rows are generated from `LuaToolKindRegistry` (data-driven — a new kind
descriptor produces a new row with no UI change, contract §2.1). Each combo's items:
`Inherit` (shows what inheriting currently resolves to) followed by the inventory tools of
that kind. Absence of a project binding means "inherit" — there is no "disabled" state
(contract §3 precedence).

### TOOLING-06-06: Runtime replaces interpreter combo
The read-only display shows the applied-state resolution: resolved runtime tool (path,
product, version) and the derived language level. When no runtime resolves, it shows
`No runtime configured` and the default language level from `Target.default()`
(`settings/LuaProjectSettings.kt:157`). The display recomputes on reset/apply and on
toolchain-topic events — it intentionally reflects **applied** settings, not unsaved combo
edits (see design §3.5). The deleted controls: interpreter combo
(`settings/LuaProjectSettingsPanel.kt:48-49`), platform/version combos (`:34-39`),
hererocks-managed checkbox (`:43-46`) — the ROCKS-16 mode machine they served is deleted by
the contract (§2.4).

### TOOLING-06-09: Apply fires events
Today `LuaProjectSettingsPanel.apply` writes `state.interpreter`
(`LuaProjectSettingsPanel.kt:152`), `state.sourcePath` (`:127`) and `state.rocksServerUrl`
(`:129`) directly with **no** message-bus notification — consumers with caches (terminal
environment, annotators) go stale until another mutation fires. The new panels only mutate
state through notify-firing mutators (design §3.6 apply matrix).

## Behavior Rules
- **Buffered vs live**: inventory mutations (add/discover/provision/remove/re-check) are
  live — applied immediately through registry mutators, same model as today's page
  (`tool/ui/LuaToolsConfigurable.kt:24-25`, `:80-84`). Option fields and everything on the
  Lua Project page are buffered behind isModified/apply/reset.
- **Threading**: registry reads are cheap/thread-safe and never probe (contract §2.3, §10) —
  safe from the EDT. Anything that spawns a process runs pooled.
- **Event discipline**: one mutator call per changed field on apply; each mutator fires its
  topic (contract §4).

## Test Cases

| # | Requirement | Given (input) | When (action) | Then (expected) |
|---|-------------|---------------|---------------|-----------------|
| 1 | TOOLING-06-01 | Plugin descriptor loaded in a `BasePlatformTestCase` fixture | Read `Configurable.APPLICATION_CONFIGURABLE` / `Configurable.PROJECT_CONFIGURABLE` EP extensions | An app EP with `id=net.internetisalie.lunar.toolchain.ui.LuaToolchainConfigurable` and `parentId=net.internetisalie.lunar.settings.LuaApplicationSettingsConfigurable` exists; a project EP with `id=net.internetisalie.lunar.toolchain.ui.LuaProjectConfigurable`, same `parentId`, `nonDefaultProject=true` exists |
| 2 | TOOLING-06-08 | Same fixture | Scan all configurable EPs for legacy IDs | No EP with id `net.internetisalie.lunar.tool.ui.LuaToolsConfigurable`, `net.internetisalie.lunar.rocks.run.LuaRocksSettingsConfigurable`, or `net.internetisalie.lunar.analysis.luacheck.LuaCheckSettingsPanel` |
| 3 | TOOLING-06-02 | Registry inventory seeded with a `lua` RUNTIME entry (runtime product "Lua", version "5.4.6") and a `luacheck` entry (version "1.1.0", origin DISCOVERED, health OK) | Create the Toolchain page component (EDT) and read the table model | 2 rows; columns exactly `Kind, Name, Path, Version, Origin, Health`; lua row Name = `Lua 5.4.6`, Origin = `Discovered`/`Manual` per seed, Health = `OK` |
| 4 | TOOLING-06-12 | Inventory entry with `health.fileExists=false` | Read the Health cell value | Text `Missing`, tooltip = `health.reason` |
| 5 | TOOLING-06-04 | Toolchain page open; app luacheck arguments = `""` | Type `--std max` into the arguments field → `isModified()` → `apply()` | `isModified()` true before apply; after apply the app-state luacheck arguments equal `--std max` and `LuaToolchainListener.TOPIC` fired exactly once |
| 6 | TOOLING-06-04 | Applied state from case 5 | Call `reset()` after typing junk into the field | Field text returns to `--std max`; `isModified()` false |
| 7 | TOOLING-06-05 | Inventory has two `luacheck` tools A and B; no project binding | Open Lua Project page; select B in the luacheck binding combo; `apply()` | Project `bindings["luacheck"] == B.id`; `LuaToolchainListener.TOPIC` fired; combo previously showed `Inherit` selected |
| 8 | TOOLING-06-05 | Project has environments E1, E2; no active environment (`activeEnvironmentId` blank) | Select E2 in the environment combo; `apply()` | `activeEnvironmentId == E2.id`; topic fired |
| 9 | TOOLING-06-06 | Inventory has a usable `lua` RUNTIME tool with `runtime.languageLevel = LUA54` and it resolves for the project | Open Lua Project page | Resolved-runtime label shows the tool's path/product/version; language-level label shows `Lua 5.4`; no interpreter/platform/version combo or hererocks checkbox present in the component tree |
| 10 | TOOLING-06-06 | Empty inventory (nothing resolves) | Open Lua Project page | Display shows `No runtime configured`; language level = `Target.default().getImplicitLanguageLevel()` |
| 11 | TOOLING-06-07 / -09 | Lua Project page open; `sourcePath` at default | Change source path to `src/?.lua`; `apply()` | `LuaProjectSettings.state.sourcePath == "src/?.lua"` **and** `LuaSettingsChangedListener.TOPIC` fired (regression test for the silent apply, `LuaProjectSettingsPanel.kt:127`) |
| 12 | TOOLING-06-09 | Lua Project page open, nothing edited | `isModified()` → `apply()` | `isModified()` false; apply fires **no** topic (no-op applies are silent) |
| 13 | TOOLING-06-03 | Toolchain page with a seeded tool | Trigger Re-check action, wait for pooled task | Probe ran off the EDT (assert via the registry's recorded probe thread or by test-service stub); table refreshed with updated health |
| 14 | TOOLING-06-11 | App luacheck args `--std max`; project override empty | Set project override `--globals foo`; `apply()` | Project state stores `--globals foo`; topic fired; clearing the field back to empty on next apply removes the override (inherit) |

Cases 1–2 and 5–12 are automatable under `BasePlatformTestCase` (EDT work via
`EdtTestUtil.runInEdtAndWait`, message-bus assertion pattern per
`src/test/kotlin/net/internetisalie/lunar/settings/LuaSettingsNotificationTest.kt:22-38`).
Cases 3–4 automatable against the table model without showing a dialog. Case 13 is
automatable with a stubbed probe; the visual flows (tree placement, toolbar icons, Provision
dialog opening) are human/VNC checks — see the implementation plan's verification tasks.

## Acceptance Criteria
- [ ] Settings shows exactly the TOOLING-06-01 tree; nothing Lua-related under Tools (06-01, 06-08).
- [ ] All registered tools — interpreters included — appear in one table with the six specified columns (06-02, 06-12).
- [ ] Add/Auto-Discover/Provision…/Remove/Re-check work with all probing off the EDT (06-03).
- [ ] Luacheck arguments and rocks default server are editable on the Toolchain page and round-trip through isModified/apply/reset (06-04).
- [ ] Per-kind binding combos and the environment selector edit TOOLING-02 state and fire the toolchain topic on apply (06-05, 06-09).
- [ ] Interpreter/platform/version combos and the hererocks checkbox are gone; the resolved-runtime display shows the applied resolution (06-06).
- [ ] Rocks server override, source path, and underscore suppression still work (06-07).
- [ ] Searchable IDs stable; buildSearchableOptions stays disabled (`build.gradle.kts:82-86`) so no index work (06-10).

## Non-Functional Requirements
- **EDT discipline** (engineering contract + contract §10): no process I/O on the EDT; UI
  refresh via `invokeLater`; page opening must not block (registry reads are snapshot reads).
- **Memory**: configurables hold no hard `Project` refs beyond the platform-managed
  configurable lifetime; message-bus connections tied to the configurable's `disposable`.
- **No `!!`, immutability-first** per `docs/engineering-contract.md`.

## Dependencies
- **TOOLING-01** (registry, inventory, kinds, health model) and **TOOLING-02** (bindings,
  environments, resolver, project state + mutators) must land first — this feature consumes
  the API surface pinned in design §2.6.
- **TOOLING-04** provides the Provision dialog the *Provision…* button opens.
- **TOOLING-05** deletes the legacy services/state this UI no longer references; the page
  *class* deletions here are no-ops if 05's wholesale `tool/` package deletion lands first
  (contract §1). Coordination note: the legacy health banner navigates to the Lua Tools page
  (`tool/health/LuaToolEditorNotificationProvider.kt:51-52`); it is deleted by 05 and its
  TOOLING-07 successor must target the new Toolchain page ID.

## See Also
- Design: [design.md](design.md)
- Plan: [implementation-plan.md](implementation-plan.md)
- Contract: [../tooling-architecture.md](../tooling-architecture.md) (§8 UI target)
