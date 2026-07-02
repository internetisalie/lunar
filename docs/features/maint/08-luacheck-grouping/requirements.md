---
id: "MAINT-08"
title: "MAINT-08: LuaCheck UI Grouping"
type: "feature"
status: "done"
priority: "low"
parent_id: "MAINT"
folders:
  - "[[features/maint/requirements|requirements]]"
---

# MAINT-08: LuaCheck UI Grouping

## Overview

LuaCheck static analysis is currently delivered only by `LuaCheckAnnotator`, an
`ExternalAnnotator` (`src/main/kotlin/net/internetisalie/lunar/analysis/luacheck/LuaCheckAnnotator.kt`),
registered via `<externalAnnotator>` at `plugin.xml:262`. Because an `ExternalAnnotator`
with no paired batch inspection is invisible to the Inspections settings tree, the user
has **no** way to see, toggle, or configure LuaCheck under
**Settings → Editor → Inspections**. This feature exposes LuaCheck as a first-class
inspection entry, hierarchically grouped under **Lua → Luacheck**, and wires the
annotator to that entry so the toggle gates on-the-fly highlighting. Parent epic:
[MAINT](../requirements.md).

## Scope

### In Scope
- Add a paired batch-inspection tool that represents LuaCheck in the Inspections tree.
- Register that inspection so it appears at the tree path **Lua → Luacheck → LuaCheck**.
- Pair the existing `LuaCheckAnnotator` to the new inspection so enabling/disabling the
  Inspections entry enables/disables the on-the-fly LuaCheck annotator.
- Preserve LuaCheck batch execution ("Inspect Code…") via the platform-provided
  `ExternalAnnotatorBatchInspection.checkFile` default.

### Out of Scope
- Changing LuaCheck's CLI invocation, parsing, dedup, or severity logic (owned by
  `LuaCheckInvoker` / `LuaCheckAnnotator`; unchanged here).
- Re-grouping the other flat `groupName="Lua"` inspections (`LuaTypeAssignabilityInspection`,
  `LuaUnusedLocalInspection`, etc.) — those remain directly under "Lua".
- The separate **Tools → LuaCheck** settings page (`LuaCheckSettingsPanel`,
  `plugin.xml:506`) — that Configurable is unrelated to the Inspections tree.
- Per-warning-code enable/disable UI (Shellcheck-style options panel) — deferred.

## Functional Requirements

| ID | Requirement | Priority | Description |
|----|-------------|----------|-------------|
| MAINT-08-01 | **Inspection entry exists** | M | LuaCheck is represented in the current inspection profile by a tool whose short name is `LuaCheck`; it did not exist before this feature. |
| MAINT-08-02 | **Hierarchical grouping** | M | The LuaCheck inspection's group path is exactly `["Lua", "Luacheck"]`, so it renders under **Lua → Luacheck** in the Inspections tree — not flat under "Lua" and not under the default "General" group. |
| MAINT-08-03 | **Annotator pairing / toggle gating** | M | `LuaCheckAnnotator.getPairedBatchInspectionShortName()` returns `LuaCheck`, so the platform gates the on-the-fly annotator on the inspection's enabled state. |
| MAINT-08-04 | **Batch inspection runs** | S | Running **Analyze → Inspect Code…** with the LuaCheck inspection enabled reports LuaCheck problems (via the inherited `checkFile` default). |
| MAINT-08-05 | **Default enabled, WARNING level** | S | The inspection is enabled by default at `WARNING` severity, matching the annotator's `HighlightSeverity.WARNING`. |

## Detailed Specifications

### MAINT-08-01: Inspection entry exists
A new `LocalInspectionTool` (`net.internetisalie.lunar.analysis.luacheck.LuaCheckInspection`)
implementing `com.intellij.codeInspection.ex.ExternalAnnotatorBatchInspection` is registered
under `<localInspection>` with `shortName="LuaCheck"`. After registration,
`InspectionProjectProfileManager.getInstance(project).currentProfile.getInspectionTool("LuaCheck", project)`
returns a non-null `InspectionToolWrapper`. Before this feature that call returns `null`.

### MAINT-08-02: Hierarchical grouping
The `<localInspection>` element declares `groupPath="Lua"` and `groupName="Luacheck"`.
Per `InspectionEP.getGroupPath()` (`platform/analysis-api/.../InspectionEP.java:146`), the
resolved path is `groupPath.split(",")` with `groupName` appended as the leaf, yielding
`["Lua", "Luacheck"]`. The tool leaf `displayName="LuaCheck"` renders as the selectable
node: **Lua ▸ Luacheck ▸ LuaCheck**.

### MAINT-08-03: Annotator pairing / toggle gating
`LuaCheckAnnotator` overrides `getPairedBatchInspectionShortName(): String? = LuaCheckInspection.SHORT_NAME`
(constant `"LuaCheck"`). The platform's `ExternalToolPass` skips an external annotator whose
paired inspection short name is disabled in the active profile, so unchecking **Lua → Luacheck →
LuaCheck** stops on-the-fly LuaCheck highlighting without any additional code.

## Behavior Rules
- The short name string `"LuaCheck"` MUST be identical in three places: the `SHORT_NAME`
  constant, the `<localInspection shortName>` attribute, and the value returned by
  `LuaCheckAnnotator.getPairedBatchInspectionShortName()`. A mismatch breaks toggle gating
  and batch dispatch.
- `groupName` (leaf group) is `"Luacheck"`; `groupPath` (ancestors) is `"Lua"`. The two are
  distinct attributes — do not collapse them into one.
- The inspection performs no local PSI visiting itself (it is `unfair="true"`); all problem
  discovery stays in `LuaCheckAnnotator` / `LuaCheckInvoker`.

## Test Cases

| # | Requirement | Given (input) | When (action) | Then (expected) |
|---|-------------|---------------|---------------|-----------------|
| 1 | MAINT-08-01 | A `BasePlatformTestCase` project with the plugin loaded | `InspectionProjectProfileManager.getInstance(project).currentProfile.getInspectionTool("LuaCheck", project)` | Returns a non-null `InspectionToolWrapper`. |
| 2 | MAINT-08-02 | The wrapper resolved in TC1 | Read `wrapper.groupPath` | `arrayOf("Lua", "Luacheck")` (content-equal, in order). |
| 3 | MAINT-08-03 | A fresh `LuaCheckAnnotator()` instance | Call `getPairedBatchInspectionShortName()` | Equals `"LuaCheck"` (== `LuaCheckInspection.SHORT_NAME`). |
| 4 | MAINT-08-01 | A fresh `LuaCheckInspection()` instance | Call `getShortName()` | Equals `"LuaCheck"`. |
| 5 | MAINT-08-05 | The wrapper resolved in TC1 | Read `wrapper.defaultLevel.severity` and `currentProfile.isToolEnabled(HighlightDisplayKey.find("LuaCheck"))` | Severity name is `WARNING`; enabled is `true`. |

## Acceptance Criteria
- [x] MAINT-08-01: `getInspectionTool("LuaCheck", project)` is non-null (TC1, TC4).
- [x] MAINT-08-02: group path equals `["Lua", "Luacheck"]` (TC2).
- [x] MAINT-08-03: annotator returns paired short name `"LuaCheck"` (TC3).
- [x] MAINT-08-04: LuaCheck problems appear in an "Inspect Code…" run (manual — see implementation-plan verification tasks / VNC).
- [x] MAINT-08-05: enabled by default at WARNING (TC5).

## Non-Functional Requirements
- **Threading**: no new runtime work. Registration is declarative in `plugin.xml`. The
  batch `checkFile` default and the annotator's `doAnnotate` run off the EDT (via
  `ExternalToolPass`), unchanged. `getPairedBatchInspectionShortName()` and `getShortName()`
  are pure constant returns.
- **Engineering contract**: `LuaCheckInspection` holds no `Project`/`Editor`/`PsiFile`
  fields; ≤30 logic lines/method; declarative registration (contract §4).

## Dependencies
- Existing `LuaCheckAnnotator`, `LuaCheckInvoker` (unchanged).
- Platform APIs: `com.intellij.codeInspection.ex.ExternalAnnotatorBatchInspection`,
  `com.intellij.codeInspection.LocalInspectionTool`, `com.intellij.lang.annotation.ExternalAnnotator`.

## See Also
- Design: [design.md](design.md)
- Plan: [implementation-plan.md](implementation-plan.md)
