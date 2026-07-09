---
id: "TOOLING-06-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "TOOLING-06"
folders:
  - "[[features/tooling/06-settings-ui/requirements|requirements]]"
---

# TOOLING-06: Implementation Plan

Preconditions: TOOLING-01/02 implemented (registry, kinds, resolver, project state +
mutators), TOOLING-04 dialog available (for the *Provision…* wiring), per the epic
dependency order (`00 → 01 → 02 → 03 → 04 → 05 → 06`). TOOLING-05 may land before or after;
Phase 3's class deletions are conditional no-ops where 05's wholesale package deletions got
there first (contract §1).

## Phases

### Phase 0: Consumed-API reconciliation [Must]
- **Goal**: confirm the design's §2.6 table (pinned against the TOOLING-01/02 *designs*)
  matches the *landed* TOOLING-01/02 code so Phases 1–3 are mechanical.
- **Tasks**:
  - [x] Grep the landed `toolchain.*` packages for every §2.6 symbol:
        `LuaToolchainRegistry.tools/registerTool/unregisterTool/refreshTool/autoDiscover/
        setKindOption/kindOption`, `LuaToolchainListener.toolchainChanged(event)` + `TOPIC`,
        `LuaToolKindRegistry.all/findById`, `LuaToolResolver.resolve/resolveRuntimeDetailed`
        + `LuaToolResolution`/`ResolutionSource`, `LuaToolchainProjectSettings`
        (`setBinding`, `activateEnvironment`, `deactivateEnvironment`, `setKindOption`,
        `environments`, `activeEnvironment`), `LuaKindOptionKeys`. Recorded drift in
        design §2.6/§2.7 (rename-only: `toolsOfKind`, `Unit`-returning `autoDiscover`/
        `refreshTool`, `LuaToolKindRegistry` object). No semantic gaps.
  - [x] Verify the app-level kind-options mutators exist on `LuaToolchainRegistry`
        (present: `kindOption`/`setKindOption`, fire `KIND_OPTION_CHANGED`).
  - [x] Verify all §2.6 mutators fire `LuaToolchainListener.TOPIC` (confirmed in source).
- **Exit criteria**: design §2.6/§2.7 tables match compiling symbols; no TODO markers left
  in the tables.

### Phase 1: Toolchain application page [Must]
- **Goal**: the app-level *Toolchain* page under *Lua*, feature-complete.
- **Tasks**:
  - [x] Create `net.internetisalie.lunar.toolchain.ui.LuaToolchainInventoryTable`
        (design §2.2): `TableView<LuaRegisteredTool>` + `ListTableModel` + six `ColumnInfo`s
        with the §3.3 value/renderer rules; `ToolbarDecorator` wiring for
        Add / Auto-Discover / Provision… / Remove / Re-check with the §3.2 threading.
  - [x] Create `net.internetisalie.lunar.toolchain.ui.LuaToolchainConfigurable`
        (design §2.1): `BoundSearchableConfigurable`; panel = table (resizable row) +
        *Luacheck* / *LuaRocks* option groups (`bindText` to the app defaults); topic
        subscription per §3.1; option apply via the DSL `.onApply` setters (§3.6 row 1).
  - [x] Register the `applicationConfigurable` block (design §7 "Added").
- **Exit criteria**: TC 3, 4, 5, 6, 13 pass (unit); page render in `runIde`/VNC deferred to
  Phase 4.

### Phase 2: Lua Project page rewrite [Must]
- **Goal**: the project page speaks TOOLING-02 state; interpreter-era controls gone; no
  silent applies.
- **Tasks**:
  - [ ] Create `net.internetisalie.lunar.toolchain.ui.LuaProjectConfigurable` (design §2.3)
        with the §2.3 group layout and the `LuaBindingItem`/`LuaEnvironmentItem` combo model
        (design §2.4).
  - [ ] Implement combo population / `isModified` diff rules / dangling-id normalization
        (design §3.4) and the resolved-runtime display incl. fallback + recompute triggers
        (design §3.5).
  - [ ] Implement `apply()` per the §3.6 mutator matrix (changed-fields-only; source path &
        underscore checkbox fire `LuaSettingsChangedListener.TOPIC`).
  - [ ] Register the new `projectConfigurable` and remove the old one
        (`plugin.xml:451-456`) — design §7.
- **Exit criteria**: TC 7, 8, 9, 10, 11, 12, 14 pass.

### Phase 3: Legacy page removal [Must]
- **Goal**: TOOLING-06-08 — one tree, nothing under *Tools*.
- **Tasks**:
  - [ ] Remove `plugin.xml:436-439` (*Lua Tools*), `:507-510` (*LuaRocks*), `:512-515`
        (*LuaCheck*) — design §7 "Removed".
  - [ ] Delete `tool/ui/LuaToolsConfigurable.kt`, `rocks/run/LuaRocksSettingsConfigurable.kt`,
        `analysis/luacheck/LuaCheckSettingsPanel.kt`, `settings/LuaProjectSettingsPanel.kt`,
        `settings/LuaProjectSettingsConfigurable.kt`, `settings/LuaInterpretersTable.kt`
        (each a no-op if TOOLING-05 already deleted the containing package).
  - [ ] Edit `settings/LuaApplicationSettingsPanel.kt` per design §2.5 (drop the
        interpreters section: lines 40, 52-55, 76-83, 94, 100).
  - [ ] Sweep dangling references: `tool/health/LuaToolEditorNotificationProvider.kt:51-52`
        imports `LuaToolsConfigurable` for its settings link — if the provider still exists
        at this point (05 not yet landed), retarget the link to
        `LuaToolchainConfigurable::class.java`; TOOLING-07 owns the banner's successor.
        Confirmed no test imports the deleted panels (the `LuaProjectSettingsPanelLogicTest`
        class in `src/test/.../settings/LuaProjectSettingsTest.kt:497` tests
        registry/Target logic only — keep it, rename to drop the "Panel" misnomer).
- **Exit criteria**: TC 1, 2 pass; `run build` green (no unresolved references).

### Phase 4: Verification & polish [Must]
- **Goal**: prove the feature end to end.
- **Tasks**:
  - [ ] Unit test class `net.internetisalie.lunar.toolchain.ui.LuaToolchainConfigurableTest`
        (`BasePlatformTestCase`): TC 1–6, 13 — EP assertions via
        `Configurable.APPLICATION_CONFIGURABLE`/`PROJECT_CONFIGURABLE` extension lists;
        panel work inside `EdtTestUtil.runInEdtAndWait`; topic assertions via
        `messageBus.connect(testRootDisposable)` (pattern:
        `src/test/.../settings/LuaSettingsNotificationTest.kt:22-38`).
  - [ ] Unit test class `net.internetisalie.lunar.toolchain.ui.LuaProjectConfigurableTest`:
        TC 7–12, 14, including the silent-apply regression (TC 11).
  - [ ] `tooling/gce-builder/gce-builder.sh run test`, then `"ktlintFormat ktlintCheck"`.
  - [ ] Human/VNC verification (verify-in-ide skill): tree shows Lua → {Lua Project,
        Toolchain}; nothing Lua under Tools; all five toolbar actions live (Provision…
        opens the TOOLING-04 dialog); Settings search finds "Toolchain" / "luacheck
        arguments"; no EDT freeze during Auto-Discover on a slow PATH.
  - [ ] Update `CHANGELOG.md` (user-facing settings relocation) and regenerate
        `docs/status.md` (`python3 scripts/gen_status.py`).
- **Exit criteria**: suite green on gce-builder; VNC checklist signed off.

## Requirement → Phase Coverage

| Requirement | Priority | Delivered in |
|-------------|----------|--------------|
| TOOLING-06-01 | M | Phase 1 + 2 (registrations), verified Phase 3/4 |
| TOOLING-06-02 | M | Phase 1 |
| TOOLING-06-03 | M | Phase 1 |
| TOOLING-06-04 | M | Phase 1 (defaults exist via Phase 0) |
| TOOLING-06-05 | M | Phase 2 |
| TOOLING-06-06 | M | Phase 2 |
| TOOLING-06-07 | M | Phase 2 |
| TOOLING-06-08 | M | Phase 3 |
| TOOLING-06-09 | M | Phase 1 (options) + Phase 2 (project fields) |
| TOOLING-06-10 | S | Phase 1 + 2 (`BoundSearchableConfigurable` IDs) |
| TOOLING-06-11 | S | Phase 2 |
| TOOLING-06-12 | S | Phase 1 (§3.3 renderer) |

## Verification Tasks
- [ ] `LuaToolchainConfigurableTest` — covers TC 1, 2, 3, 4, 5, 6, 13.
- [ ] `LuaProjectConfigurableTest` — covers TC 7, 8, 9, 10, 11, 12, 14.
- [ ] VNC session per Phase 4 (visual tree, actions, Settings search) — covers the
      human-only rows of the requirements Test Cases note.
- [ ] Full suite + lint on gce-builder (regression gate).

## Task Summary

| Phase | Status | Priority |
|-------|--------|----------|
| Phase 0: Consumed-API reconciliation | done | Must |
| Phase 1: Toolchain application page | done | Must |
| Phase 2: Lua Project page rewrite | todo | Must |
| Phase 3: Legacy page removal | todo | Must |
| Phase 4: Verification & polish | todo | Must |
