---
id: "TOOLING-06-DESIGN"
title: "Technical Design"
type: "design"
parent_id: "TOOLING-06"
folders:
  - "[[features/tooling/06-settings-ui/requirements|requirements]]"
---

# Technical Design: TOOLING-06 — Settings UI Consolidation

## 1. Architecture Overview

### Current State

Five separate pages, three parents, two duplicated inventories:

| Page | Class | Registration | Contents |
|---|---|---|---|
| *Lua* (app) | `net.internetisalie.lunar.settings.LuaApplicationSettingsConfigurable` | `plugin.xml:445-449`, `parentId="language"` | Editor checkboxes + a `ListTableWithButtons` interpreter inventory (`LuaApplicationSettingsPanel.kt:52-55`, `LuaInterpretersTable.kt:35`) |
| *Lua Project* | `net.internetisalie.lunar.settings.LuaProjectSettingsConfigurable` | `plugin.xml:451-456`, child of *Lua* | Platform/version combos, language-level label, hererocks-managed checkbox, interpreter combo (`LuaProjectSettingsPanel.kt:34-49`), source path, completion checkbox, rocks server override (`:79-92`) |
| *Lua Tools* | `net.internetisalie.lunar.tool.ui.LuaToolsConfigurable` | `plugin.xml:436-439`, `groupId="tools"` | Tool inventory table (`JBTable` + `AbstractTableModel`, `LuaToolsConfigurable.kt:120-151`) with Add/Remove/Auto-Discover/Re-check |
| *LuaRocks* | `net.internetisalie.lunar.rocks.run.LuaRocksSettingsConfigurable` | `plugin.xml:507-510`, `groupId="tools"` | luarocks executable path + default server URL (`LuaRocksSettingsConfigurable.kt:22-40`) |
| *LuaCheck* | `net.internetisalie.lunar.analysis.luacheck.LuaCheckSettingsPanel` | `plugin.xml:512-515`, `groupId="tools"` | luacheck executable path, dead download link (`LuaCheckSettingsPanel.kt:25` — empty handler), arguments |

Known defects fixed here: the Lua Project panel's `apply` writes `state.interpreter`
(`LuaProjectSettingsPanel.kt:152`), `state.sourcePath` (`:127`) and `state.rocksServerUrl`
(`:129`) without firing any topic; the Lua Tools Add action probes on the EDT
(`LuaToolsConfigurable.kt:94-100` → `LuaToolManager.registerTool`, `LuaToolManager.kt:49-90`
shells out via `LuaToolValidator.extractVersion`).

### Prior Art in This Repo

Searched `plugin.xml` for every `Configurable` registration and grepped all
`*Configurable*`/`*SettingsPanel*` classes. Disposition of each:

- `tool/ui/LuaToolsConfigurable.kt` — **replaced** by §2.1/§2.2 (its threading pattern
  `:49-57`, `:109-117` and live-mutation model `:24-25` are carried over).
- `rocks/run/LuaRocksSettingsConfigurable.kt` — **replaced**; it is also the Kotlin UI DSL
  `BoundConfigurable` exemplar this design follows (`:17-41`).
- `analysis/luacheck/LuaCheckSettingsPanel.kt` — **replaced** (whole page folds in; verified
  it holds only executable/link/arguments, no inspection-behavior options).
- `settings/LuaProjectSettingsPanel.kt` + `settings/LuaProjectSettingsConfigurable.kt` —
  **replaced** by §2.3.
- `settings/LuaInterpretersTable.kt` + the interpreters section of
  `settings/LuaApplicationSettingsPanel.kt` — **deleted** (runtimes join the Toolchain
  inventory); the *Lua* page class itself is **extended** (kept, minus the table — §2.5).
- `platform/LuaInterpreterComponent.kt:18` (`customizeLuaInterpreterComboBox`) — used by the
  project panel (`LuaProjectSettingsPanel.kt:49`) and run configs
  (`run/LuaRunConfiguration.kt:318`, `run/test/LuaTestRunConfiguration.kt:278`). This feature
  removes the project-panel usage; the run-config usages and the helper's deletion are
  TOOLING-05's cutover.
- Platform UI patterns from the research doc §5 (`SdkListModelBuilder`, `JdkDownloadDialog`)
  inform the look but the platform `SdkType` machinery is **not** adopted (research doc
  recommendation).

### Target State

Contract §8 tree, implemented by two new configurables in
`net.internetisalie.lunar.toolchain.ui` (contract §1) plus a slimmed *Lua* page:

```
Lua (kept: settings.LuaApplicationSettingsConfigurable — editor options only)
├─ Lua Project (new: toolchain.ui.LuaProjectConfigurable — buffered, project)
└─ Toolchain   (new: toolchain.ui.LuaToolchainConfigurable — live inventory + buffered options, app)
```

## 2. Core Components

### 2.1 `net.internetisalie.lunar.toolchain.ui.LuaToolchainConfigurable`
- **Responsibility**: app-level *Toolchain* page — hosts the inventory table (§2.2) and the
  buffered kind-option fields.
- **Threading**: constructed/rendered on EDT; all actions delegating process work to pooled
  threads (§3.2); subscribes to `LuaToolchainListener.TOPIC` on its `disposable` and
  refreshes the table via `invokeLater` (§3.1).
- **Collaborators**: `LuaToolchainRegistry` (reads + live mutators), TOOLING-04 provision
  dialog, app toolchain state for option fields (§2.6).
- **Key API**:
  ```kotlin
  class LuaToolchainConfigurable :
      BoundSearchableConfigurable(
          displayName = "Toolchain",
          helpTopic = "settings.lua.toolchain",
          _id = "net.internetisalie.lunar.toolchain.ui.LuaToolchainConfigurable",
      ) {
      override fun createPanel(): DialogPanel   // table (row + resizableRow) then option groups
      override fun apply()                      // super.apply() + conditional topic fire (§3.6)
  }
  ```
  `BoundSearchableConfigurable(displayName, helpTopic, _id)` is platform API —
  intellij-community `platform/platform-api/src/com/intellij/openapi/options/BoundConfigurable.kt:69`;
  the DSL exemplar in-repo is `rocks/run/LuaRocksSettingsConfigurable.kt:17-41`.

### 2.2 `net.internetisalie.lunar.toolchain.ui.LuaToolchainInventoryTable`
- **Responsibility**: the one inventory table + toolbar. Encapsulates the
  `TableView<LuaRegisteredTool>`, its `ListTableModel`/`ColumnInfo`s, and the five actions.
- **Threading**: `refresh()` EDT-only (sets a fresh snapshot on the model); actions per §3.2.
- **Collaborators**: `LuaToolchainRegistry`, `LuaToolKindRegistry`, `FileChooser`,
  `ToolbarDecorator`, TOOLING-04 dialog entry point.
- **Key API**:
  ```kotlin
  class LuaToolchainInventoryTable(private val parentDisposable: Disposable) {
      val component: JComponent                        // ToolbarDecorator.createPanel()
      fun refresh()                                    // EDT: model.items = registry.inventory()
      fun selectedTool(): LuaRegisteredTool?
  }
  ```
- **Table choice — `TableView`, not `ListTableWithButtons`**: rows are *not* inline-editable
  (Add goes through a file picker; Kind/Name/Version/Origin/Health are probe-derived), which
  is exactly the current Lua Tools model (read-only `AbstractTableModel`,
  `LuaToolsConfigurable.kt:137`). `ListTableWithButtons` (the `LuaInterpretersTable.kt:35`
  precedent) exists to support in-cell editing of list entries and buffers edits until apply
  — wrong fit for a live, probe-driven inventory. `TableView` + `ListTableModel<LuaRegisteredTool>`
  + `ColumnInfo` (platform API: `platform/platform-api/src/com/intellij/ui/table/TableView.java`,
  `platform/platform-api/src/com/intellij/util/ui/ColumnInfo.java` in intellij-community)
  gives typed rows, per-column renderers (Health icon/tooltip) and sorting for free.
- **Columns** (all read-only `ColumnInfo<LuaRegisteredTool, String>` except Health, which
  supplies a renderer): `Kind`, `Name`, `Path`, `Version`, `Origin`, `Health` — value rules
  in §3.3.

### 2.3 `net.internetisalie.lunar.toolchain.ui.LuaProjectConfigurable`
- **Responsibility**: the rewritten project page. Buffered editor over TOOLING-02 project
  state + retained `LuaProjectSettings` fields.
- **Threading**: EDT; resolution reads for the runtime display are snapshot reads (no
  probing — contract §2.3/§10) so they are EDT-safe.
- **Collaborators**: TOOLING-02 project toolchain state + mutators, `LuaToolResolver`,
  `LuaToolKindRegistry`, `LuaToolchainRegistry` (combo item lists),
  `LuaProjectSettings` (retained non-toolchain fields), both message-bus topics.
- **Key API**:
  ```kotlin
  class LuaProjectConfigurable(private val project: Project) :
      BoundSearchableConfigurable(
          displayName = "Lua Project",
          helpTopic = "settings.lua.project",
          _id = "net.internetisalie.lunar.toolchain.ui.LuaProjectConfigurable",
      ) {
      override fun createPanel(): DialogPanel
      override fun isModified(): Boolean        // §3.4 diff rules
      override fun apply()                      // §3.6 mutator matrix
      override fun reset()                      // reload all controls from state; §3.5 display
  }
  ```
- **Layout** (Kotlin UI DSL groups, top to bottom): *Environment* (combo), *Toolchain
  Bindings* (one row per built-in kind; the RUNTIME rows come first), *Resolved Runtime*
  (read-only labels: runtime, language level), *Luacheck* (project arguments override),
  *LuaRocks* (server URL override), *Source & Completion* (source path
  `ExpandableTextField` with `PathConfiguration.TEMPLATE_SEPARATOR` split/join exactly as
  today, `LuaProjectSettingsPanel.kt:51-56`; underscore-suppression checkbox).

### 2.4 Binding combo item model
- **Responsibility**: typed items for the per-kind binding combos and the environment combo.
- **Key API**:
  ```kotlin
  sealed interface LuaBindingItem {
      data object Inherit : LuaBindingItem                       // no project binding
      data class Tool(val tool: LuaRegisteredTool) : LuaBindingItem
  }
  sealed interface LuaEnvironmentItem {
      data object None : LuaEnvironmentItem
      data class Env(val env: LuaEnvironmentState) : LuaEnvironmentItem
  }
  ```
- Combos are `ComboBox<LuaBindingItem>` bound with the DSL's `bindItem`
  (intellij-community `platform/platform-impl/src/com/intellij/ui/dsl/builder/comboBox.kt`)
  against the snapshot model described in §3.4. Renderer: `SimpleListCellRenderer` —
  `Inherit` renders as `Inherit (<resolved-label or "none">)` where the resolved label is the
  global-tier resolution for that kind (path + version); `Tool` renders `path — version`.

### 2.5 `net.internetisalie.lunar.settings.LuaApplicationSettingsPanel` (edit) + deletions
- **Edit**: remove the interpreters section — field `interpretersTable`
  (`LuaApplicationSettingsPanel.kt:40`), the "Lua Interpreters" panel (`:52-55`), the
  identify background loop in `setData` (`:76-83`), and the table lines in
  `getData`/`isModified` (`:94`, `:100`). The page keeps the two editor checkboxes; class,
  registration and ID (`plugin.xml:445-449`) are unchanged.
- **Deleted classes** (this feature; no-ops where TOOLING-05's package deletions land
  first): `settings/LuaInterpretersTable.kt`, `settings/LuaProjectSettingsPanel.kt`,
  `settings/LuaProjectSettingsConfigurable.kt`, `tool/ui/LuaToolsConfigurable.kt`,
  `rocks/run/LuaRocksSettingsConfigurable.kt`, `analysis/luacheck/LuaCheckSettingsPanel.kt`.

### 2.6 Consumed API (provided by TOOLING-01/02 — the surface this UI compiles against)

Signatures below are taken from the sibling feature designs (grounded per-row); **Phase 0 of
the implementation plan re-verifies this table against the landed TOOLING-01/02 code before
any UI work starts** (rename-only reconciliation; semantics are contract-pinned).

> **Phase 0 reconciliation (landed TOOLING-05 tree, verified against source):** the per-kind
> read is `toolsOfKind(kindId)` (not `tools(kindId)`); `autoDiscover()` and `refreshTool(id)`
> both return `Unit` (there is also an `autoDiscover(extraRoots: List<Path>)` overload);
> `LuaToolKindRegistry` is a Kotlin `object` (static `all()`/`findById()`, no `getInstance()`).
> `registerTool`/`refreshTool`/`autoDiscover` all `assertIsNonDispatchThread()`. All other §2.6
> symbols compile as designed (`resolveDetailed`/`resolveRuntimeDetailed`,
> `LuaToolResolution.Resolved(tool, source)`, `ResolutionSource`, `LuaKindOptionKeys`,
> `LuaToolKind.displayName`/`.isRuntime`, `LuaToolHealth` fields, `LuaRuntimeInfo`).
> **TOOLING-04 Provision entry point:** `LuaProvisionDialog(project, initial = null)` +
> `.showAndGet()` → `LuaToolProvisioner.getInstance().provision(project, dialog.toRequest())`
> (pattern: `LuaProvisionToolchainAction`). The dialog and provisioner are **project-scoped**
> (require a non-null `Project`); the app-level Toolchain page has no project, so Provision…
> guesses an open project and disables the action when none is available (registry topic
> refreshes the table — no completion callback).

| API | Defined by | Used by |
|---|---|---|
| `LuaToolchainRegistry.getInstance(): LuaToolchainRegistry` (APP service) | TOOLING-01 design §2.4; contract §1 | §2.1, §2.2 |
| `registry.tools(): List<LuaRegisteredTool>` / `toolsOfKind(kindId: String)` — thread-safe snapshot reads, never probe | TOOLING-01 design §2.4 | table refresh, combo items |
| `registry.registerTool(path: String, kindIdHint: String? = null, origin: Origin = MANUAL, environmentId: String? = null): LuaRegisteredTool?` — probes; BGT only (asserts non-dispatch thread); fires topic | TOOLING-01 design §2.4 | Add action |
| `registry.unregisterTool(id: String): Boolean` — fires topic | TOOLING-01 design §2.4 | Remove action |
| `registry.autoDiscover()` (returns `Unit`; also `autoDiscover(extraRoots: List<Path>)`) — BGT only; registers each hit (fires topic per tool) | TOOLING-01 design §2.4 | Auto-Discover |
| `registry.refreshTool(id: String)` (returns `Unit`) — explicit re-probe; BGT only; fires topic when changed | TOOLING-01 design §2.4 | Re-check (looped over `tools()`) |
| `registry.setKindOption(key: String, value: String?)` / `kindOption(key: String): String` ("" when absent) — app-level kind-option defaults; mutator fires `KIND_OPTION_CHANGED` | TOOLING-02 design §2.9 (contract §7 one-field addition, flagged there) | §2.1 option fields |
| `LuaToolchainListener.TOPIC` (app-level `Topic`), member `toolchainChanged(event: LuaToolchainEvent)` | TOOLING-01 design §2.5; contract §4 | table auto-refresh; §3.5 recompute |
| `LuaToolKindRegistry.all(): List<LuaToolKind>` (ordered data list) / `findById(id: String): LuaToolKind?` — Kotlin `object` (call statically, no `getInstance()`) | TOOLING-01 design §2.2; contract §2.1 | binding rows, Kind/Name columns |
| `LuaToolResolver.getInstance()`; `resolve(project: Project?, kindId: String): LuaRegisteredTool?` (`project = null` ⇒ global tiers only) | TOOLING-02 design §2.1; contract §3 | Inherit labels |
| `LuaToolResolver.resolveRuntimeDetailed(project: Project?): LuaToolResolution` (`Resolved(tool, source)` / `Unresolved`) | TOOLING-02 design §2.1, §3.3 | §3.5 display |
| `LuaToolchainProjectSettings.getInstance(project)`; `State.bindings: MutableMap<String,String>`, `State.kindOptions: MutableMap<String,String>`, `environments(): List<LuaEnvironmentState>`, `activeEnvironment(): LuaEnvironmentState?` | TOOLING-02 design §2.2; contract §7 | §2.3 reads |
| TOOLING-02 project mutators (each fires the topic, TOOLING-02 design §3.9): `setBinding(kindId: String, toolId: String?)`, `activateEnvironment(envId: String): Boolean`, `deactivateEnvironment()`, `setKindOption(key: String, value: String?)` | TOOLING-02 design §2.2 | §3.6 apply |
| `LuaKindOptionKeys.LUACHECK_ARGUMENTS` (`"luacheck.arguments"`), `LuaKindOptionKeys.LUAROCKS_SERVER_URL` (`"luarocks.serverUrl"`) | TOOLING-02 design §2.4 | option fields, §2.7 mapping |
| `LuaRegisteredTool` fields `kindId/path/version/runtime/origin/health`; `LuaToolHealth` fields; `LuaRuntimeInfo.languageLevel` | TOOLING-01 design §2.x; contract §2.1–2.3 | §3.3, §3.5 |
| TOOLING-04 dialog entry point (opens the provision dialog for an optional project) | TOOLING-04 design (contract §6; research doc §5 `JdkDownloadDialog` shape) | Provision… |

Retained existing symbols (verified): `LuaProjectSettings.getInstance(project)` /
`state.sourcePath` / `state.suppressUnderscorePrefixedGlobals`
(`settings/LuaProjectSettings.kt:79-80,374`), `LuaSettingsChangedListener.TOPIC`
(`settings/LuaSettingsChangedEvent.kt`), `Target.default()` +
`getImplicitLanguageLevel()` (`settings/LuaProjectSettings.kt:157`,
`LuaProjectSettingsPanel.kt:109`), `FileChooserDescriptorFactory.singleFile()`
(`tool/ui/LuaToolsConfigurable.kt:95`), `LuaBundle.message("luacheck.arguments")`
(`analysis/luacheck/LuaCheckSettingsPanel.kt:31`).

### 2.7 Panel field ↔ state field mapping

| Page | Control | State field (owner) | Buffered? | Applied via | Topic on apply |
|---|---|---|---|---|---|
| Toolchain | inventory table rows | app `LuaToolchainRegistry.State` inventory (TOOLING-01, contract §7) | No — live | registry mutators (§3.2) | fired by mutator |
| Toolchain | Luacheck arguments (default) | app `kindOptions[LUACHECK_ARGUMENTS]` (TOOLING-02 design §2.9) | Yes | `registry.setKindOption(...)` from the DSL binding's setter | fired by mutator (`KIND_OPTION_CHANGED`) |
| Toolchain | LuaRocks default server URL | app `kindOptions[LUAROCKS_SERVER_URL]` (TOOLING-02 design §2.9) | Yes | `registry.setKindOption(...)` from the DSL binding's setter | fired by mutator (`KIND_OPTION_CHANGED`) |
| Lua Project | Environment combo | project `State.activeEnvironmentId` ("" = none; TOOLING-02 design §2.2) | Yes | `activateEnvironment(id)` / `deactivateEnvironment()` | fired by mutator |
| Lua Project | Binding combo × kind | project `State.bindings[kindId]` (TOOLING-02 design §2.2) | Yes | `setBinding(kindId, toolId?)` | fired by mutator |
| Lua Project | Luacheck arguments override | project `State.kindOptions[LUACHECK_ARGUMENTS]` (TOOLING-02 design §2.2/§2.4) | Yes | `setKindOption(key, valueOrNull)` | fired by mutator |
| Lua Project | Rocks server URL override | project `State.kindOptions[LUAROCKS_SERVER_URL]` (successor of `LuaProjectSettings.kt:109`) | Yes | `setKindOption(key, valueOrNull)` | fired by mutator |
| Lua Project | Source path | `LuaProjectSettings.state.sourcePath` (`:79`) | Yes | direct write + explicit fire | `LuaSettingsChangedListener` |
| Lua Project | Underscore suppression | `LuaProjectSettings.state.suppressUnderscorePrefixedGlobals` (`:80`) | Yes | direct write + explicit fire | `LuaSettingsChangedListener` |
| Lua Project | Resolved runtime / language level | *(derived — read-only)* | n/a | n/a | n/a |
| Lua | 2 editor checkboxes | `LuaApplicationSettings.state.includeAllFieldsInCompletions` / `.enableTypeInference` | Yes (unchanged) | existing panel `apply` | none (unchanged behavior) |

Gone from any panel: `state.interpreter`, `state.interpreterMode`,
`explicitInterpreter/Target`, `target` platform/version selection, `hererocksEnvs`
UI (`settings/LuaProjectSettings.kt:53,62,76-78,124,130` — fields deleted by TOOLING-05 per
contract §7), `LuaApplicationSettings.state.interpreters` (`settings/LuaApplicationSettings.kt:39`).

## 3. Algorithms

### 3.1 Inventory refresh & event subscription
- **Input → Output**: registry snapshot → table model rows.
- **Steps**:
  1. `createPanel()` builds the table, calls `refresh()` once, then
     `ApplicationManager.getApplication().messageBus.connect(disposable)
     .subscribe(LuaToolchainListener.TOPIC) { invokeLater { refresh() } }`
     (`disposable` is `DslConfigurableBase.disposable`, non-null after panel creation).
     Every `LuaToolchainEvent` triggers a refresh — reads are cheap; no event filtering.
  2. `refresh()` (EDT): `model.items = registry.tools()` — a fresh snapshot;
     reads never mutate health (contract §2.3, replacing the mutating
     `LuaToolManager.getTools()`, `tool/LuaToolManager.kt:110-122`).
- **Edge handling**: events arriving after disposal are dropped by the disposed connection;
  `invokeLater` with `ModalityState.any()` so refresh works while the Settings dialog is modal.

### 3.2 Toolbar action flows (threading exact)
All follow the proven pattern of `tool/ui/LuaToolsConfigurable.kt:49-57`:
`executeOnPooledThread { <registry call> ; invokeLater { refresh() } }` — the `invokeLater`
is belt-and-braces; the topic subscription (§3.1) already refreshes.
1. **Add**: `FileChooser.chooseFile(FileChooserDescriptorFactory.singleFile()
   .withTitle("Select Lua Tool Binary"), component, null, null)` on EDT (as
   `LuaToolsConfigurable.kt:95-97`); if non-null → pooled `registry.registerTool(path)`
   (defaults `origin = MANUAL`; fixes the EDT probe of `LuaToolsConfigurable.kt:98`); a
   `null` return (unrecognized/unusable binary) → `invokeLater` an error balloon over the
   table: `"Not a recognized Lua tool: <path>"`.
2. **Auto-Discover**: pooled `registry.autoDiscover()`.
3. **Provision…**: EDT — open the TOOLING-04 dialog (entry point per §2.6; internals out of
   scope). No completion callback needed: provisioning registers results through the
   registry, whose topic refreshes the table.
4. **Remove**: EDT — `selectedTool() ?: return`, then `registry.unregisterTool(tool.id)`
   (no process I/O → no pooled hop; mirrors `LuaToolsConfigurable.kt:102-107`).
5. **Re-check**: pooled `registry.tools().forEach { registry.refreshTool(it.id) }`
   (replaces the manual checker loop of `LuaToolsConfigurable.kt:109-117`).

### 3.3 Column value & Health rendering rules
- **Input → Output**: `LuaRegisteredTool` → cell strings/renderer, per row.
- `Kind` = `LuaToolKindRegistry.findById(tool.kindId)?.displayName ?: tool.kindId`.
- `Name` = `tool.runtime?.let { "${it.product} ${it.version}" } ?: kindDisplayName` (RUNTIME
  rows show the probed product, e.g. `LuaJIT 2.1.0`; others repeat the kind display name —
  matches today's `name` semantics, `tool/LuaToolManager.kt:219-225`).
- `Path` = `tool.path` verbatim.
- `Version` = `tool.version ?: "-"`.
- `Origin` = `DISCOVERED→"Discovered"`, `MANUAL→"Manual"`, `PROVISIONED→"Provisioned"`.
- `Health` (ordered rules, first match wins; from `LuaToolHealth`, contract §2.3):
  1. `!fileExists` → text `Missing`, icon `AllIcons.General.Error`
  2. `!executable` → `Not executable`, `AllIcons.General.Error`
  3. `probeOk == false` → `Probe failed`, `AllIcons.General.Warning`
  4. `probeOk == null` → `Not checked`, `AllIcons.General.Note`
  5. else → `OK`, `AllIcons.General.InspectionsOK`
  Tooltip = `health.reason ?: text`. Implemented as a `ColumnInfo.getRenderer` returning a
  `DefaultTableCellRenderer` with icon+text (same mechanism as
  `settings/LuaInterpretersTable.kt:65-72`).

### 3.4 Binding/environment combos: population, isModified, apply
- **Population** (on `reset()`, EDT):
  1. `kinds = LuaToolKindRegistry.all()`, stably partitioned so RUNTIME-capability kinds
     come first (registry order preserved within each partition).
  2. For each kind: items = `[Inherit] + registry.toolsOfKind(kind.id).map(LuaBindingItem::Tool)`.
     Selected item: `bindings[kind.id]`'s tool if that id is still in the inventory, else
     `Inherit` (a dangling binding id renders as `Inherit` and is *cleared on next apply* —
     see rules below).
  3. Inherit label = `LuaToolResolver.getInstance().resolve(null, kind.id)` (global-tier
     resolution — `project = null` skips project tiers per contract §3) rendered as
     `Inherit (path — version)` or `Inherit (none)`.
  4. Environment combo: `[None] + settings.environments().map(::Env)`; selected =
     `settings.activeEnvironment()?.id` match or `None`.
- **isModified** (pure diff, no writes):
  `selectedToolId(kind) != bindings[kind.id]` for any kind (where `Inherit` ⇒ `null`,
  dangling saved ids normalize to `null` before comparison), or environment selection id ≠
  `activeEnvironmentId`, or any text/checkbox differs from its state field (string compare;
  server URLs and luacheck args compared `.trim()`ed, matching today's rule
  `LuaProjectSettingsPanel.kt:187`).
- **apply** (EDT): for each *changed* field only, invoke the §3.6 mutator. Unchanged fields
  produce no mutator call — a no-op apply fires nothing (TC 12).

### 3.5 Resolved-runtime display
- **Input → Output**: applied project state → two label strings.
- **Steps**:
  1. `resolution = LuaToolResolver.getInstance().resolveRuntimeDetailed(project)`
     (runtime-resolution semantics owned by TOOLING-02 design §3.3).
  2. If `resolution is Resolved` and `tool.runtime != null`: runtime label =
     `"${tool.path} — ${runtime.product} ${runtime.version}"` plus a source suffix from
     `resolution.source`: `ACTIVE_ENVIRONMENT→" (from active environment)"`,
     `PROJECT_BINDING→" (project binding)"`, `GLOBAL_BINDING→" (global binding)"`,
     `INVENTORY_FALLBACK→" (inventory fallback)"`; level label =
     `runtime.languageLevel.toString()`.
  3. Else (`Unresolved`, or a Resolved tool with null runtime info): runtime label =
     `"No runtime configured"`; level label =
     `Target.default().getImplicitLanguageLevel().toString()` (the same fallback the state
     layer uses, `settings/LuaProjectSettings.kt:157`), suffixed `" (default)"`.
  4. Recompute on: `reset()`, successful `apply()`, and `LuaToolchainListener` events
     (marshalled via `invokeLater`, connection on `disposable`).
- **Rule**: the display reflects **applied** state, not unsaved combo edits. Rationale:
  previewing unsaved edits would require re-implementing the contract §3 precedence chain
  inside the UI (a second resolver — a correctness hazard); a caption under the labels reads
  `"Reflects applied settings"`. The language-level → stdlib reload chain stays where it is
  today (state layer / TOOLING-02), not in the panel.

### 3.6 Apply → notification matrix
Per changed control, exactly one mutator/notification (fixing the silent writes at
`LuaProjectSettingsPanel.kt:127,129,152`):

| Changed control | Call |
|---|---|
| Toolchain option field(s) | DSL `bindText` setter = `registry.setKindOption(KEY, text.trim().ifEmpty { null })`; the DSL invokes setters only for modified bindings, and the mutator itself fires `KIND_OPTION_CHANGED` — no manual topic fire, unchanged fields stay silent |
| Environment combo | `settings.activateEnvironment(env.id)` for `Env`, `settings.deactivateEnvironment()` for `None` (`settings = LuaToolchainProjectSettings.getInstance(project)`) |
| Binding combo (kind K) | `settings.setBinding(K.id, toolIdOrNull)` (`Inherit` ⇒ `null`) |
| Project luacheck args / rocks URL | `settings.setKindOption(LuaKindOptionKeys.…, text.trim().ifEmpty { null })` (empty field ⇒ `null` ⇒ inherit the app default) |
| Source path / underscore checkbox | write `LuaProjectSettings.state` field, then fire `LuaSettingsChangedListener.TOPIC` once via `project.messageBus.syncPublisher(...)` (pattern: `settings/LuaProjectSettings.kt:302-304`) |

Multiple toolchain mutators in one apply fire the topic once each — acceptable per contract
§4 (listeners are invalidation-style; precedent: two fires asserted in
`LuaSettingsNotificationTest.kt:43-62`).

## 4. External Data & Parsing

None. This feature consumes no CLI output, files, or network responses — probing, version
parsing and discovery formats are TOOLING-01's; provisioning feeds are TOOLING-04's. The UI
renders already-parsed `LuaRegisteredTool`/`LuaToolHealth` data only.

## 5. Data Flow

### Example 1: Auto-Discover on the Toolchain page
User clicks *Auto-Discover* → pooled thread runs `registry.autoDiscover()` → registry
probes binaries, mutates the inventory, fires `LuaToolchainListener.TOPIC` → the page's
subscription (§3.1) does `invokeLater { refresh() }` → table shows new rows with probed
Version/Health. The EDT never blocks.

### Example 2: Binding luacheck for one project
User opens *Lua Project*, luacheck row shows `Inherit (/usr/bin/luacheck — 1.1.0)`; selects
tool B → `isModified()` true → OK → `apply()` calls `setBinding("luacheck", B.id)` →
mutator persists `.idea/lunar.xml` and fires the toolchain topic → the luacheck external
annotator (migrated in TOOLING-05) resolves B on its next run; the resolved-runtime display
recomputes (unchanged — RUNTIME binding untouched).

## 6. Edge Cases

- **Empty inventory**: table shows the standard empty text `"No tools registered — use Add,
  Auto-Discover or Provision"`; binding combos contain only `Inherit (none)`; runtime display
  shows the §3.5 fallback.
- **Dangling binding id** (bound tool removed from inventory): combo selects `Inherit`;
  normalization in §3.4 means `isModified()` is false until the user changes something —
  the stale id is cleared the next time that combo's kind is applied. (Resolution-side
  fallback already skips unusable bindings, contract §3.)
- **Environment removed while page open**: selection falls back to `None` on next `reset()`;
  apply of `None` clears `activeEnvironmentId`.
- **Two settings dialogs / project + default project**: `nonDefaultProject="true"` keeps the
  project page off the default-project settings (as today, `plugin.xml:456`).
- **Modal-dialog refresh**: §3.1 uses `ModalityState.any()` so background discovery completing
  while Settings is open still updates the table.
- **Provision dialog cancelled**: nothing fires, nothing refreshes — no partial state.
- **buildSearchableOptions**: disabled (`build.gradle.kts:82-86`) — no headless-index work;
  IDs must simply stay stable for runtime Settings search and `ShowSettingsUtil` navigation
  (TOOLING-07 banners will target `LuaToolchainConfigurable`'s ID).

## 7. Integration Points

**Added** to `src/main/resources/META-INF/plugin.xml` (adjacent to the existing Lua
registration at `plugin.xml:445-449`, which is unchanged):

```xml
<applicationConfigurable
        parentId="net.internetisalie.lunar.settings.LuaApplicationSettingsConfigurable"
        instance="net.internetisalie.lunar.toolchain.ui.LuaToolchainConfigurable"
        id="net.internetisalie.lunar.toolchain.ui.LuaToolchainConfigurable"
        displayName="Toolchain"/>

<projectConfigurable
        parentId="net.internetisalie.lunar.settings.LuaApplicationSettingsConfigurable"
        instance="net.internetisalie.lunar.toolchain.ui.LuaProjectConfigurable"
        id="net.internetisalie.lunar.toolchain.ui.LuaProjectConfigurable"
        displayName="Lua Project"
        nonDefaultProject="true"/>
```

**Removed** (exact current blocks):

```xml
<!-- plugin.xml:436-439 -->
<applicationConfigurable groupId="tools"
        instance="net.internetisalie.lunar.tool.ui.LuaToolsConfigurable"
        id="net.internetisalie.lunar.tool.ui.LuaToolsConfigurable"
        displayName="Lua Tools"/>

<!-- plugin.xml:451-456 (superseded by the new projectConfigurable above) -->
<projectConfigurable
        parentId="net.internetisalie.lunar.settings.LuaApplicationSettingsConfigurable"
        instance="net.internetisalie.lunar.settings.LuaProjectSettingsConfigurable"
        id="net.internetisalie.lunar.settings.LuaProjectSettingsConfigurable"
        displayName="Lua Project"
        nonDefaultProject="true"/>

<!-- plugin.xml:507-510 -->
<applicationConfigurable groupId="tools"
        instance="net.internetisalie.lunar.rocks.run.LuaRocksSettingsConfigurable"
        id="net.internetisalie.lunar.rocks.run.LuaRocksSettingsConfigurable"
        displayName="LuaRocks"/>

<!-- plugin.xml:512-515 -->
<applicationConfigurable groupId="tools"
        instance="net.internetisalie.lunar.analysis.luacheck.LuaCheckSettingsPanel"
        id="net.internetisalie.lunar.analysis.luacheck.LuaCheckSettingsPanel"
        displayName="LuaCheck"/>
```

The old *Lua Project* ID (`net.internetisalie.lunar.settings.LuaProjectSettingsConfigurable`)
is retired — verified no production code navigates to it (only `plugin.xml` and its own
classes reference it). Registration EPs are `com.intellij.applicationConfigurable` /
`com.intellij.projectConfigurable` (`Configurable.APPLICATION_CONFIGURABLE` /
`PROJECT_CONFIGURABLE`, intellij-community
`platform/ide-core/src/com/intellij/openapi/options/Configurable.java:134-136` — also the
test seam for TC 1–2).

## 8. Requirement Coverage

| Requirement | Priority | Implemented by (section) |
|-------------|----------|--------------------------|
| TOOLING-06-01 | M | §7 (registrations), §1 target state |
| TOOLING-06-02 | M | §2.2, §3.3 |
| TOOLING-06-03 | M | §3.1, §3.2 |
| TOOLING-06-04 | M | §2.1, §2.7, §3.6 |
| TOOLING-06-05 | M | §2.3, §2.4, §3.4 |
| TOOLING-06-06 | M | §2.3 layout, §3.5, §2.7 (deleted controls row) |
| TOOLING-06-07 | M | §2.3 layout, §2.7 |
| TOOLING-06-08 | M | §2.5, §7 removals |
| TOOLING-06-09 | M | §3.6 |
| TOOLING-06-10 | S | §2.1/§2.3 (`BoundSearchableConfigurable` IDs), §6 (buildSearchableOptions) |
| TOOLING-06-11 | S | §2.3 layout, §2.7, §3.6 |
| TOOLING-06-12 | S | §3.3 Health rules |

## 9. Alternatives Considered

- **`ListTableWithButtons` for the inventory** (the `LuaInterpretersTable.kt:35` precedent):
  rejected — it exists for inline-editable, apply-buffered lists; the inventory is
  probe-derived and live-mutated (§2.2 justification).
- **Rewriting the Lua Project page in place** (keep
  `settings.LuaProjectSettingsConfigurable`, preserve its ID): rejected — contract §1 places
  consolidated configurables in `toolchain.ui`; nothing navigates to the old ID, and keeping
  a `settings.*` class whose content is 80% toolchain would blur the package boundary the
  epic exists to draw.
- **Buffered inventory (apply-on-OK) like the platform SDK table**: rejected — discovery,
  probing and provisioning are inherently immediate side-effects; buffering them would need
  a shadow inventory + delta apply for no user benefit. Today's page already uses the live
  model deliberately (`tool/ui/LuaToolsConfigurable.kt:24-25`).
- **Live preview of unsaved binding edits in the runtime display**: rejected (§3.5) — it
  duplicates the resolver's precedence chain in the UI.
- **One binding table instead of per-kind combo rows**: rejected — a table invites inline
  editing of paths (the anti-pattern being deleted) and hides the `Inherit (…)` resolution
  hint that makes precedence understandable at a glance.
- **Per-kind sub-pages under Toolchain** (contract §8 allowed "or as sub-panels"): rejected —
  two small option groups don't justify tree depth; a flat page keeps Settings search hits on
  one page.

## 10. Open Questions

_None — the planning bar is cleared. Cross-feature preconditions (the §2.6 consumed API) are pinned against the TOOLING-01/02 designs and re-verified as implementation-plan Phase 0 gates._