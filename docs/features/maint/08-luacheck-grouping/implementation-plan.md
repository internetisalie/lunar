---
id: "MAINT-08-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "MAINT-08"
folders:
  - "[[features/maint/08-luacheck-grouping/requirements|requirements]]"
---

# MAINT-08: Implementation Plan

## Phases

### Phase 1: Paired batch inspection [Must]
- **Goal**: introduce the LuaCheck inspection tool and pair the annotator to it.
- **Tasks**:
  - [x] Create `net.internetisalie.lunar.analysis.luacheck.LuaCheckInspection`
        (`src/main/kotlin/net/internetisalie/lunar/analysis/luacheck/LuaCheckInspection.kt`):
        `class LuaCheckInspection : LocalInspectionTool(), ExternalAnnotatorBatchInspection`
        with `override fun getShortName() = SHORT_NAME` and
        `companion object { const val SHORT_NAME = "LuaCheck" }` — realizes design §2.1.
  - [x] Add `override fun getPairedBatchInspectionShortName(): String = LuaCheckInspection.SHORT_NAME`
        to `LuaCheckAnnotator` (`analysis/luacheck/LuaCheckAnnotator.kt`) — realizes design §2.2.
- **Exit criteria**: project compiles; `LuaCheckAnnotator().getPairedBatchInspectionShortName()`
  and `LuaCheckInspection().shortName` both equal `"LuaCheck"` (TC3, TC4).

### Phase 2: Registration & grouping [Must]
- **Goal**: register the inspection so it renders under **Lua → Luacheck → LuaCheck**.
- **Tasks**:
  - [x] Add the `<localInspection … shortName="LuaCheck" groupPath="Lua" groupName="Luacheck"
        displayName="LuaCheck" enabledByDefault="true" level="WARNING" unfair="true"
        implementationClass="…LuaCheckInspection"/>` element after `plugin.xml:262-264`
        — realizes design §7 and the §3.1 group-path resolution.
- **Exit criteria**: `getInspectionTool("LuaCheck", project)` is non-null (TC1) and its
  `groupPath` equals `["Lua", "Luacheck"]` (TC2); default level WARNING, enabled (TC5).

### Phase 3: Tests [Must]
- **Goal**: lock the behavior with an automated test.
- **Tasks**:
  - [x] Create `LuaCheckInspectionGroupingTest`
        (`src/test/kotlin/net/internetisalie/lunar/analysis/luacheck/LuaCheckInspectionGroupingTest.kt`),
        `BasePlatformTestCase`, implementing TC1–TC5 via
        `InspectionProjectProfileManager.getInstance(project).currentProfile
        .getInspectionTool("LuaCheck", project)` and `LuaCheckAnnotator()` — realizes
        requirements Test Cases.
- **Exit criteria**: `tooling/gce-builder/gce-builder.sh run "test --tests *LuaCheckInspectionGrouping*"`
  passes green.

## Requirement → Phase Coverage

| Requirement | Priority | Delivered in |
|-------------|----------|--------------|
| MAINT-08-01 | M | Phase 1 (class), Phase 2 (registration) |
| MAINT-08-02 | M | Phase 2 |
| MAINT-08-03 | M | Phase 1 |
| MAINT-08-04 | S | Phase 2 (inherited `checkFile`); verified manually |
| MAINT-08-05 | S | Phase 2 |

## Verification Tasks
- [x] Add `LuaCheckInspectionGroupingTest` covering TC1–TC5 (Phase 3).
- [x] Manual: open **Settings → Editor → Inspections**, confirm the node appears at
      **Lua ▸ Luacheck ▸ LuaCheck**, enabled, WARNING — covers TC1, TC2, TC5, MAINT-08-04.
- [x] Manual: uncheck the node, reopen a `.lua` file with a LuaCheck warning, confirm no
      LuaCheck squiggle; re-check and confirm it returns — covers MAINT-08-03.
- [x] Manual: **Analyze → Inspect Code…** lists LuaCheck problems under the node — covers
      MAINT-08-04.
- [x] `tooling/gce-builder/gce-builder.sh run "ktlintFormat ktlintCheck"` on the two new/edited files.

## Task Summary

| Phase | Status | Priority |
|-------|--------|----------|
| Phase 1: Paired batch inspection | done | Must |
| Phase 2: Registration & grouping | done | Must |
| Phase 3: Tests | done | Must |
