---
id: "TOOLING-08-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "TOOLING-08"
folders:
  - "[[features/tooling/08-settings-restructure/requirements|requirements]]"
---

# TOOLING-08: Implementation Plan

Phased so all decision logic (classification, target modes, global bindings, inherit text) is
**unit-testable via light fixtures** first, and the pure-appearance work (DSL panel layout, spacing)
is **VNC-verified** last. Every task names the class/file and the design section it realizes.

## Phases

### Phase 1: Model & classifier logic [Must]
- **Goal**: the pure, testable core — kind classification and the persisted explicit-target flag —
  with no UI dependency.
- **Tasks**:
  - [x] Create `net.internetisalie.lunar.toolchain.ui.LuaToolKindClassifier` (`object`) — realizes
        design §2.1 / §3.1 (`tierOf`, `COMMON_TOOL_KIND_IDS`, `bindable()`, `byTier()`).
  - [x] Add `var explicitTarget: Boolean = false` to `LuaProjectSettings.State` — realizes §2.4.
  - [x] Add the explicit-target early-return guard to `LuaTargetSynchronizer.recompute()` — realizes
        §2.5 / §3.3 step 1.
  - [x] (review #50) Pin `Target.default()` to `findVersion(STANDARD, "5.4")`; corrected `TargetTest`.
  - [x] (review #41) Make `LuaSettingsChangeListener` a real, lifecycle-scoped subscriber and
        instantiate it from the `LuaTargetSyncStartup` ProjectActivity so the notify chain fires.
- **Exit criteria**: `LuaToolKindClassifierTest` passes (TC 6, 7); `LuaTargetSynchronizerTest`
  extended so an explicit target survives a `TOOL_UPDATED` event (TC 4). Build green.

### Phase 2: Target-control apply/reset logic [Must]
- **Goal**: buffered platform/version selection, mode switching, and persistence — driven through the
  configurable's `reset`/`isModified`/`apply` so it is fixture-testable.
- **Tasks**:
  - [x] Add `TargetItem` sealed interface (`toolchain/ui`) — realizes §2.3.
  - [x] Add `ProjectControls.platformCombo` / `versionCombo` and `buildTargetGroup`,
        `resetTargetControls`, `repopulateVersionCombo`, `isTargetModified`, `applyTarget` to
        `LuaProjectConfigurable` — realizes §2.2 / §3.2 / §3.3. Wire `applyTarget` first in `apply()`
        and `isTargetModified` into `isModified()`.
- **Exit criteria**: `LuaProjectConfigurableTargetTest` passes TC 1–5 (Auto default, Redis
  repopulation, explicit persist, synchronizer no-op, Auto-return). Build green.

### Phase 3: Bindings split + platform-server eviction [Must]
- **Goal**: replace `orderedKinds()` with the classifier; render common + collapsible advanced.
- **Tasks**:
  - [x] Replace `orderedKinds()` usages with `LuaToolKindClassifier.bindable()` in
        `resetControls`/`applyBindings`/`isModified` and `ProjectControls.bindingCombos` — realizes
        §3.4. Delete `orderedKinds()`.
  - [x] Split the DSL `group("Toolchain Bindings")` into common `group` + `collapsibleGroup("Advanced
        tools")` in `buildPanel()` — realizes §3.4.
- **Exit criteria**: `LuaProjectConfigurableBindingsTest` confirms `bindable()` excludes
  `redis-server`/`valkey-server` and that resolution of `redis-server` is unaffected (TC 6, 7). Build
  green.

### Phase 4: Global default bindings + inherit labels [Must/Should]
- **Goal**: wire `setGlobalBinding` into the app page; make project inherit text explicit.
- **Tasks**:
  - [x] Add `buildGlobalBindings`/`resetGlobalBindings`/`applyGlobalBindings` + global binding combos
        to `LuaToolchainConfigurable` — realizes §2.6 / §3.5. [Must]
  - [x] Compute the two inherit placeholders in `LuaProjectConfigurable.resetControls()`
        (`applyInheritPlaceholders`) — realizes §3.6. [Should]
- **Exit criteria**: `LuaToolchainConfigurableGlobalBindingsTest` passes TC 8–9; placeholder tests
  assert TC 10 (`LuaProjectConfigurableTest.testInheritPlaceholdersRenderAppDefaults_TC10` + `_TC10b`).
  Build green.

### Phase 5: DSL panel migration [Should]
- **Goal**: BUG-369 layout standardization.
- **Tasks**:
  - [x] Rewrite `LuaApplicationSettingsPanel` with `panel { }`, preserving its public API — realizes
        §2.7. Removed `FormBuilder`/`IdeBorderFactory` imports. Full Configurable lifecycle
        (`reset()`/`disposeUIResources()` + clone-edit-commit) added to `LuaApplicationSettingsConfigurable`
        (review #44).
  - [x] Rewrite the `LuaRocksGeneratorPeer` panel build with `panel { }` (lazy, EDT-built) — realizes
        §2.8. Removed `FormBuilder`/`JBLabel` imports.
- **Exit criteria**: `LuaApplicationSettingsPanelTest` (TC 11: `isModified` after a toggle) passes; no
  `com.intellij.util.ui.FormBuilder` import remains in either file; `LuaRocksGeneratorPeerTest` green.
  Build green.

### Phase 6: Spacing audit + live verification [Could/Must-DoD]
- **Goal**: confirm the tree's vertical rhythm and the discoverable target control live.
- **Tasks**:
  - [x] Record in design §1 / requirements 08-08 that `LuaEditorOptionsConfigurable`
        (`BeanConfigurable`) and `LuaCodeStyleSettings` (`CustomCodeStyleSettings`) are platform-driven
        (no manual layout) — realizes 08-08. (design §1 "Spacing audit conclusion".)
  - [ ] Run the `verify-in-ide` VNC pass over every Lua settings page; capture screenshots of the new
        target control and consistent spacing. **DEFERRED** to a separate supervised verify-in-ide
        session (awaiting VNC gate).
- **Exit criteria**: audit recorded (done); `human-verification-checklists.md` VNC pass is the only
  remaining item — deferred to a supervised session.

## Requirement → Phase Coverage

| Requirement | Priority | Delivered in |
|-------------|----------|--------------|
| TOOLING-08-01 | M | Phase 2 |
| TOOLING-08-02 | M | Phase 1 (guard) + Phase 2 |
| TOOLING-08-03 | M | Phase 1 (classifier) + Phase 3 |
| TOOLING-08-04 | M | Phase 1 + Phase 3 |
| TOOLING-08-05 | M | Phase 4 |
| TOOLING-08-06 | S | Phase 4 |
| TOOLING-08-07 | S | Phase 5 |
| TOOLING-08-08 | C | Phase 6 |

## Verification Tasks
- [x] `LuaToolKindClassifierTest` — covers TC 6, 7 (classification + eviction).
- [x] `LuaTargetSynchronizerTest` (extend) — covers TC 4 (explicit-target no-op).
- [x] `LuaProjectConfigurableTargetTest` — covers TC 1–5.
- [x] `LuaProjectConfigurableBindingsTest` — covers TC 6, 7.
- [x] `LuaToolchainConfigurableGlobalBindingsTest` — covers TC 8, 9.
- [x] Inherit-placeholder test — covers TC 10 (`LuaProjectConfigurableTest` TC 10/10b).
- [x] `LuaApplicationSettingsPanelTest` — covers TC 11.
- [ ] Run [human-verification-checklists.md](human-verification-checklists.md) via `verify-in-ide` — deferred (awaiting VNC gate).

## Task Summary

| Phase | Status | Priority |
|-------|--------|----------|
| Phase 1: Model & classifier logic | done | Must |
| Phase 2: Target-control apply/reset logic | done | Must |
| Phase 3: Bindings split + eviction | done | Must |
| Phase 4: Global bindings + inherit labels | done | Must |
| Phase 5: DSL panel migration | done | Should |
| Phase 6: Spacing audit (done) + live VNC verification (deferred) | in_progress | Could |
