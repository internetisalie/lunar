---
id: "TOOLING-08-DESIGN"
title: "Technical Design"
type: "design"
parent_id: "TOOLING-08"
folders:
  - "[[features/tooling/08-settings-restructure/requirements|requirements]]"
---

# Technical Design: TOOLING-08 — Lua Settings Restructure

## 1. Architecture Overview

### Current State
- The *Lua Project* page is `LuaProjectConfigurable` (`toolchain/ui/LuaProjectConfigurable.kt`), a
  `BoundSearchableConfigurable`. It renders: an *Environment* combo, a *Toolchain Bindings* group
  that iterates **all** kinds one row each (`:203-206` `orderedKinds()` → `:67-71`), a read-only
  *Resolved Runtime* display (`:72-76`), *Luacheck*, *LuaRocks*, and *Source & Completion* groups.
- **There is no target control.** The project `Target` is derived from the resolved runtime by
  `LuaTargetSynchronizer` (`toolchain/resolve/LuaTargetSynchronizer.kt:62-93`), which listens on
  `LuaToolchainListener.TOPIC` and calls `LuaProjectSettings.setTargetAndNotify(...)`. A user whose
  discovered interpreter probes as Standard cannot pin `Redis` — **BUG-362**.
- The app-level *Toolchain* page is `LuaToolchainConfigurable`
  (`toolchain/ui/LuaToolchainConfigurable.kt`): an inventory table + app-default Luacheck-args and
  LuaRocks-URL fields. **No global-bindings UI** exists even though
  `LuaToolchainRegistry.setGlobalBinding` (`toolchain/registry/LuaToolchainRegistry.kt:185-213`) is
  implemented and honored by the resolver.
- **Layout heterogeneity — BUG-369**: `LuaProjectConfigurable`, `LuaToolchainConfigurable`,
  `LuaRedisConnectionsConfigurable` use the Kotlin UI DSL; `LuaApplicationSettingsPanel`
  (`settings/LuaApplicationSettingsPanel.kt:41-49`) and `LuaRocksGeneratorPeer`
  (`rocks/init/LuaRocksGeneratorPeer.kt:84`) use `FormBuilder` with hand-tuned vgaps.

### Prior Art in This Repo
- **`LuaProjectConfigurable`** (`toolchain/ui/LuaProjectConfigurable.kt`) — **EXTENDED**. We add a
  *Platform Target* group and split its bindings group; we reuse its `ProjectControls` inner-class
  pattern (`:225-259`), `LuaBindingItem` combos, and buffered apply/reset shape.
- **`LuaToolchainConfigurable`** (`toolchain/ui/LuaToolchainConfigurable.kt`) — **EXTENDED** with a
  global-bindings group.
- **`LuaBindingItem` / `LuaEnvironmentItem`** (`toolchain/ui/LuaBindingItem.kt:11-25`) — **REUSED**
  as the combo item model for both project and (new) global binding combos.
- **`LuaRuntimeComboBox`** (`toolchain/ui/LuaRuntimeComboBox.kt:144`) — already filters kinds by
  `Capability.RUNTIME in it.capabilities`; the eviction/split filter follows the **same idiom**
  (capability-based, not name-based). Not reused directly (different item model) but the pattern is
  the precedent.
- **`LuaTargetSynchronizer`** (`toolchain/resolve/LuaTargetSynchronizer.kt`) — **EXTENDED**: gains an
  explicit-target guard so it no-ops when the user has pinned a target.
- **`PlatformVersionRegistry`** (`platform/target/PlatformVersionRegistry.kt`) — **REUSED** unchanged
  (`platforms()`, `getVersions()`, `defaultVersion()`, `findVersion()`).
- Searched `toolchain/ui/`, `settings/`, `platform/target/` for an existing platform/target combo:
  **none found** (`grep 'platform.*ComboBox' src/main` → only `LuaRuntimeComboBox`, which is a
  *runtime tool* combo, not a platform/version target combo).

### Target State
- `LuaProjectConfigurable` gains a *Platform Target* group (platform + version combos, Auto sentinel)
  above the bindings, and its bindings group splits into common + collapsible advanced, filtered by
  a new `LuaToolKindClassifier`.
- `LuaProjectSettings.State` gains a `var explicitTarget: Boolean` flag; `LuaTargetSynchronizer`
  reads it and skips auto-apply when set.
- `LuaToolchainConfigurable` gains a *Global Default Bindings* group.
- `LuaApplicationSettingsPanel` and `LuaRocksGeneratorPeer` are re-expressed with `panel { }`.

## 2. Core Components

### 2.1 `net.internetisalie.lunar.toolchain.ui.LuaToolKindClassifier`
- **Responsibility**: partition `LuaToolKindRegistry.all()` into common / advanced / platform-server
  buckets by capability, so both the project and global binding UIs share one classification.
- **Threading**: pure function, any thread (in-memory registry read).
- **Collaborators**: `LuaToolKindRegistry.all()`, `LuaToolKind.capabilities`,
  `LuaToolKind.isRuntime`, `net.internetisalie.lunar.toolchain.model.Capability`.
- **Key API**:
  ```kotlin
  object LuaToolKindClassifier {
      enum class Tier { COMMON, ADVANCED, PLATFORM_SERVER }
      // COMMON kind ids beyond the runtime; single source of truth
      val COMMON_TOOL_KIND_IDS: Set<String> = setOf("luarocks", "luacheck", "stylua", "busted")
      fun tierOf(kind: LuaToolKind): Tier
      fun bindable(): List<LuaToolKind>            // COMMON + ADVANCED, runtime-first
      fun byTier(): Map<Tier, List<LuaToolKind>>   // ordered, runtime-first within COMMON
  }
  ```

### 2.2 `net.internetisalie.lunar.toolchain.ui.LuaProjectConfigurable` (edited)
- **Responsibility**: adds the Platform Target group + common/advanced bindings split.
- **Threading**: EDT (panel build/reset/apply); no I/O.
- **Collaborators**: `PlatformVersionRegistry`, `LuaProjectSettings`, `Target`, `LuaPlatform`,
  `VersionEntry`, `LuaToolKindClassifier`, `LuaTargetSynchronizer` (read for the Auto-mode read-only
  version display).
- **Key API** (new/changed private members; all ≤3 args):
  ```kotlin
  private fun buildTargetGroup(panelBuilder: Panel)          // §3.2 renders platform+version
  private fun resetTargetControls()                          // §3.2 step "reset"
  private fun applyTarget()                                  // §3.3
  private fun isTargetModified(): Boolean
  private fun repopulateVersionCombo(platform: LuaPlatform?) // §3.2 repopulate rule
  private fun commonKinds(): List<LuaToolKind>               // = classifier COMMON tier
  private fun advancedKinds(): List<LuaToolKind>             // = classifier ADVANCED tier
  ```
  The platform/version combos and their `TargetItem` sentinel live in the existing `ProjectControls`
  inner class (extended), keeping helpers within the arg cap.

### 2.3 `net.internetisalie.lunar.toolchain.ui.TargetItem`
- **Responsibility**: combo item model for the platform combo (Auto sentinel vs a concrete platform).
- **Threading**: N/A (data).
- **Key API**:
  ```kotlin
  sealed interface TargetItem {
      data object Auto : TargetItem
      data class Platform(val platform: LuaPlatform) : TargetItem
  }
  ```

### 2.4 `net.internetisalie.lunar.settings.LuaProjectSettings.State` (edited)
- **Responsibility**: persist whether the target is user-pinned.
- **Threading**: N/A (persistent state).
- **Key API**: add `var explicitTarget: Boolean = false` (XML-serialized alongside `target`).
  `setTarget(...)` is unchanged; the explicit flag is set by the configurable, not by `setTarget`.

### 2.5 `net.internetisalie.lunar.toolchain.resolve.LuaTargetSynchronizer` (edited)
- **Responsibility**: skip runtime→target auto-apply when the user has pinned an explicit target.
- **Threading**: EDT for `applyTarget` (`invokeLater`, unchanged); event thread otherwise.
- **Key API**: `recompute()` (`:62`) gains an early `if (LuaProjectSettings.getInstance(project)
  .state.explicitTarget) return` guard before `effectiveRuntimeTool()`.

### 2.6 `net.internetisalie.lunar.toolchain.ui.LuaToolchainConfigurable` (edited)
- **Responsibility**: add the *Global Default Bindings* group.
- **Threading**: EDT.
- **Collaborators**: `LuaToolchainRegistry.globalBindings()` / `setGlobalBinding(...)`,
  `LuaToolKindClassifier.commonKinds`, `LuaBindingItem`.
- **Key API**:
  ```kotlin
  private fun buildGlobalBindings(panelBuilder: Panel)
  private fun resetGlobalBindings()
  private fun applyGlobalBindings()
  ```

### 2.7 `net.internetisalie.lunar.settings.LuaApplicationSettingsPanel` (rewritten)
- **Responsibility**: same two toggles (`enableTypeInference`, `includeAllFieldsInCompletions`) on
  the Kotlin UI DSL. Public API (`apply`/`reset`/`setData`/`getData`/`isModified`/`mainPanel`)
  unchanged so `LuaApplicationSettingsConfigurable` (`settings/LuaApplicationSettingsConfigurable.kt`)
  is untouched.
- **Threading**: EDT.

### 2.8 `net.internetisalie.lunar.rocks.init.LuaRocksGeneratorPeer` (edited)
- **Responsibility**: replace the `FormBuilder` panel build (`:84`) with `panel { }`; the peer's
  `getComponent()`/settings-transfer contract is preserved.
- **Threading**: EDT.

## 3. Algorithms

### 3.1 Kind classification (LuaToolKindClassifier.tierOf)
- **Input → Output**: `LuaToolKind` → `Tier`.
- **Steps**:
  1. If `kind.capabilities.isEmpty()` → `PLATFORM_SERVER`. (Today: `redis-server`, `valkey-server`,
     `LuaToolKindRegistry.kt:131-152`.)
  2. Else if `kind.isRuntime` (`Capability.RUNTIME in capabilities`) → `COMMON`.
  3. Else if `kind.id in COMMON_TOOL_KIND_IDS` → `COMMON`.
  4. Else → `ADVANCED`.
- **`bindable()`**: `all().filter { tierOf(it) != PLATFORM_SERVER }`, ordered runtime-kinds first
  (reuse the `partition { it.isRuntime }` idiom from `LuaProjectConfigurable.kt:204`), then common
  tools in `COMMON_TOOL_KIND_IDS` declaration order, then advanced in registry order.
- **Rules / edge handling**: name-independent — adding a future empty-capability kind auto-excludes
  it; adding a new capability'd kind lands in ADVANCED unless its id is added to
  `COMMON_TOOL_KIND_IDS`. `tarantool` is COMMON (runtime rule) even though it is niche.
- **Complexity**: O(n) over ≤~12 kinds; called on panel build/reset only.

### 3.2 Platform-target combos: build, reset, repopulate
- **Input → Output**: `LuaProjectSettings.State` → two combo selections.
- **Reset steps** (`resetTargetControls`):
  1. `platformCombo.model` = `[TargetItem.Auto] + PlatformVersionRegistry.platforms().sortedBy { it.label }.map { TargetItem.Platform(it) }`.
  2. If `state.explicitTarget`: select `TargetItem.Platform(state.getTarget().platform)`,
     `repopulateVersionCombo(that platform)`, then select the version whose
     `label == state.getTarget().version.label` (fallback `defaultVersion`).
  3. Else: select `TargetItem.Auto`; `versionCombo.isEnabled = false`; set the version combo model to
     a single read-only entry = the synchronizer-derived `state.getTarget().version` (shows what Auto
     resolved to) with the combo disabled.
- **Repopulate rule** (`repopulateVersionCombo(platform)`): on platform-combo change, if
  `platform == null` (Auto) disable the version combo per step 3; else set `versionCombo.model` =
  `PlatformVersionRegistry.getVersions(platform)`, enable it, and select `defaultVersion(platform)`
  (or keep the current selection if its label still exists).
- **Edge handling**: an unregistered persisted version label falls back to
  `PlatformVersionRegistry.defaultVersion(platform)` (mirrors `TargetState.toTarget()`,
  `LuaProjectSettings.kt:36-40`). Empty version list (never true for registered platforms) → disable.

### 3.3 Applying the target (applyTarget / isTargetModified)
- **Input → Output**: combo selections → `LuaProjectSettings` mutation.
- **`isTargetModified`**: `true` iff `selectedIsAuto != !state.explicitTarget`, OR (explicit and)
  `selectedPlatform != state.getTarget().platform`, OR `selectedVersionLabel !=
  state.getTarget().version.label`.
- **Apply steps**:
  1. If `platformCombo.selectedItem is TargetItem.Auto`:
     - If `state.explicitTarget`: set `state.explicitTarget = false`, then request a synchronizer
       recompute via `LuaTargetSynchronizer.getInstance(project).ensureSynchronized()` (`:47`) so the
       target immediately reflows to the runtime. No `setTargetAndNotify` here.
  2. Else (concrete platform P + version V):
     - `val target = PlatformVersionRegistry.resolveTarget(P, V.label)` (non-null, `:85`).
     - `state.explicitTarget = true`.
     - `LuaProjectSettings.getInstance(project).setTargetAndNotify(target)` (`:123`) — fires the
       settings-changed topic and drives library/luacheck-std reload downstream.
- **Ordering**: `applyTarget` runs first in `apply()` (before `applyBindings`) so a target change and
  a binding change in the same OK are both honored; the explicit-target guard prevents the binding's
  `TOOL_UPDATED` event from clobbering the just-set explicit target.

### 3.4 Bindings split rendering
- **Input → Output**: classifier tiers → DSL groups.
- **Steps**: in `buildPanel()`, replace the single `group("Toolchain Bindings")` loop with:
  1. `group("Toolchain Bindings") { commonKinds().forEach { row(...) { cell(bindingCombo(it.id)) } } }`.
  2. `collapsibleGroup("Advanced tools") { advancedKinds().forEach { row(...) { cell(bindingCombo(it.id)) } } }`
     — `com.intellij.ui.dsl.builder.Panel.collapsibleGroup` starts collapsed.
- `resetControls`/`applyBindings` iterate `LuaToolKindClassifier.bindable()` instead of
  `orderedKinds()` (which is deleted), so platform servers are never bound from this page.

### 3.5 Global bindings (LuaToolchainConfigurable)
- **Reset**: for each kind in `LuaToolKindClassifier.commonKinds()` (runtime + 4), build a
  `LuaBindingItem` combo = `[Inherit] + registry.toolsOfKind(kind.id).map(Tool)`; select the tool
  whose id == `registry.globalBindings()[kind.id]`, else `Inherit`.
- **Apply**: for each kind, `registry.setGlobalBinding(kind.id, selectedToolIdOrNull)`; `null` for
  `Inherit`. `setGlobalBinding` already de-dupes unchanged and enforces the single-runtime invariant
  (`LuaToolchainRegistry.kt:186-213`).

### 3.6 Inherit placeholder text
- **Luacheck-args project field** empty-text: `app = registry.kindOption(LUACHECK_ARGUMENTS)`;
  placeholder = `if (app.isBlank()) "Inherit (no app default)" else "Inherit (app default: $app)"`.
- **Rocks-URL project field** empty-text: `app = registry.kindOption(LUAROCKS_SERVER_URL)`;
  placeholder = `if (app.isBlank()) "Inherit (luarocks.org)" else "Inherit (app default: $app)"`.
- Computed in `resetControls()` after reading the registry; replaces the static
  `"Empty = use app default or luarocks.org"` text at `LuaProjectConfigurable.kt:237`.

## 4. External Data & Parsing
This feature consumes **no external/unstructured input**. All inputs are in-memory model reads
(`PlatformVersionRegistry`, `LuaToolchainRegistry`, `LuaProjectSettings`). The only "format" concern
is the **settings-page wording standard** this feature standardizes on (Out-of-Scope §, requirements):

- Target control label: **"Platform target:"** / **"Version:"**.
- Auto sentinel display: **"Auto (from runtime)"**.
- Inherit forms: **"Inherit (<resolved>)"** for combos (existing, `LuaProjectConfigurable.kt:267`);
  **"Inherit (app default: <value>)"** / **"Inherit (no app default)"** / **"Inherit (luarocks.org)"**
  for the two text fields.
- Advanced group title: **"Advanced tools"**.

## 5. Data Flow

### Example 1: User pins Redis (BUG-362 fix)
Open *Lua Project* → platform combo `Auto`→`Redis` → version combo repopulates to `[5,6,7+]`
(§3.2) → pick `7+` → OK → `applyTarget` sets `explicitTarget=true` and
`setTargetAndNotify(Target(REDIS,"7+"))` (§3.3) → `LuaSettingsChangedListener` fires → luacheck
`--std redis7` + `redis.*`/`KEYS`/`ARGV` library resolution take effect. A later interpreter re-probe
event reaches `LuaTargetSynchronizer.recompute`, which now returns early on `explicitTarget` (§3.3
step 1 / §2.5) → target stays Redis.

### Example 2: Global luacheck default
App *Toolchain* page → global luacheck combo `Inherit`→tool T1 → OK → `setGlobalBinding("luacheck",
T1.id)` (§3.5) → a project with no project-level luacheck binding now resolves luacheck to T1 via the
resolver's global-binding precedence (`ResolutionSource.GLOBAL_BINDING`).

### Example 3: Advanced tool hidden but usable
Registry has a `luacov` tool. *Lua Project* page shows *Toolchain Bindings* (5 rows) and a collapsed
*Advanced tools* group; expanding it reveals the `LuaCov` row (§3.4). `redis-server` never appears,
yet `LuaRedisServerConnection` still resolves it via `LuaToolResolver.resolve(project,"redis-server")`.

## 6. Edge Cases
- **No inventory tools for a kind**: binding combo shows only `Inherit` — unchanged from today.
- **Persisted explicit target whose platform was removed from the registry**: cannot occur —
  `LuaPlatform` is a fixed enum; `PlatformVersionRegistry.resolveTarget` falls back to
  `defaultVersion`/`Target.default()`.
- **Auto mode with no resolvable runtime**: version combo shows the default target's version
  (`Target.default()` = Standard 5.4) read-only; identical to today's *Resolved Runtime* fallback
  (`LuaProjectConfigurable.kt:300-301`).
- **Old `lunar.xml` without `explicitTarget`**: XML deserializes the missing boolean to its default
  `false` → Auto mode — the correct backward-compatible default (clean-break policy, no migration
  needed; `LuaProjectSettings.kt:110-114`).
- **StyLua/Busted not installed**: still shown as common rows with `Inherit (none)` — discoverability
  is the goal even when unbound.

## 7. Integration Points
No new `plugin.xml` registrations are required — the two configurables are already registered
(`plugin.xml:564-575`) and this feature edits their panels in place. `LuaProjectSettings.State`'s new
`explicitTarget` field piggybacks on the existing `@State(name="LuaProjectSettings",
storages=[Storage("lunar.xml")])` component — no new `<projectService>`.

```xml
<!-- plugin.xml (UNCHANGED — shown for grounding) -->
<!-- <applicationConfigurable parentId="..." instance="net.internetisalie.lunar.toolchain.ui.LuaToolchainConfigurable" id="net.internetisalie.lunar.toolchain.ui.LuaToolchainConfigurable" displayName="Toolchain"/> -->
<!-- <projectConfigurable parentId="..." instance="net.internetisalie.lunar.toolchain.ui.LuaProjectConfigurable" id="net.internetisalie.lunar.toolchain.ui.LuaProjectConfigurable" displayName="Lua Project" nonDefaultProject="true"/> -->
```

Settings keys reused: `LuaKindOptionKeys.LUACHECK_ARGUMENTS`, `LuaKindOptionKeys.LUAROCKS_SERVER_URL`
(`toolchain/registry/LuaKindOptionKeys.kt`).

## 8. Requirement Coverage

| Requirement | Priority | Implemented by (section) |
|-------------|----------|--------------------------|
| TOOLING-08-01 | M | §2.2, §2.3, §3.2 |
| TOOLING-08-02 | M | §2.4, §2.5, §3.3 |
| TOOLING-08-03 | M | §2.1, §3.1, §3.4 |
| TOOLING-08-04 | M | §2.1, §3.1 (PLATFORM_SERVER tier excluded from `bindable()`) |
| TOOLING-08-05 | M | §2.6, §3.5 |
| TOOLING-08-06 | S | §3.6 |
| TOOLING-08-07 | S | §2.7, §2.8 |
| TOOLING-08-08 | C | §1 (audit: BeanConfigurable/CustomCodeStyleSettings are platform-driven) |

## 9. Alternatives Considered
- **Remove `setGlobalBinding` instead of adding UI (08-05)**: rejected — it is honored by the
  resolver/diagnostics/banner and referenced by the TOOLING PRD Use Case 2 and the E2E verification
  checklist; a minimal UI is cheaper than unwinding those references (recorded in
  [risks-and-gaps.md §Gap 2.1](risks-and-gaps.md)).
- **Name-based eviction of redis/valkey servers**: rejected — capability-based classification
  (`capabilities.isEmpty()`) is future-proof and matches the `LuaRuntimeComboBox` capability-filter
  idiom (`LuaRuntimeComboBox.kt:144`).
- **Make the synchronizer the sole target authority (no explicit override)**: rejected — that is the
  BUG-362 status quo; an explicit user pin is the whole point.
- **Migrate `LuaEditorOptionsConfigurable` / `LuaCodeStyleSettings` to DSL**: unnecessary —
  the former is a `BeanConfigurable` (platform lays it out) and the latter is a
  `CustomCodeStyleSettings` model with no panel; neither carries manual vgaps (audit-only, 08-08).

## 10. Open Questions

_None — feature has cleared the planning bar; the two product-owner judgments are resolved in [risks-and-gaps.md](risks-and-gaps.md) Gaps 2.1 and 2.2._
