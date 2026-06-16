---
id: REFACT-06-PLAN
title: Create from Usage Implementation Plan
type: plan
parent_id: REFACT-06
status: done
priority: medium
folders:
  - "[[features/refactoring/06-stubs-for-declaring/requirements|requirements]]"
---

# Implementation Plan: Create from Usage (REFACT-06)

References: [requirements.md](requirements.md) · [design.md](design.md) ·
[risks-and-gaps.md](risks-and-gaps.md). Run the DR tasks in the risks doc **before** Phase 1.

## Phase 0: De-risking (see risks-and-gaps.md)
- Complete **REFACT-06-00-DR-01** (extract shared undeclared-ness helper) and
  **REFACT-06-00-DR-02** (confirm `local ` + `assignment.text` re-parse) before coding the
  intentions. These retire the two highest-impact unknowns.

## Phase 1: Shared undeclared-ness helper [Must]
**Tasks**
1. Add `net.internetisalie.lunar.analysis.inspections.LuaUndeclaredNames` (or a neutral
   `lang/insight` location if preferred) with
   `fun isUnresolvedNonGlobal(ref: LuaNameRef): Boolean`, lifting the resolve + `isExemptGlobal`
   logic from `LuaUndeclaredVariableInspection.kt:49–72`.
2. Refactor `LuaUndeclaredVariableInspection.inspectNameRef` to delegate the resolve/exempt
   decision to the helper (keep `isReadUse` + suppression in the inspection).
**Verification**
- `LuaUndeclaredVariableInspectionTest` (existing) stays green — proves no behavior change.

## Phase 2: REFACT-06-01 Create Local Variable [Must]
**Tasks**
1. `net.internetisalie.lunar.lang.insight.LuaCreateLocalVariableIntention` extending
   `BaseIntentionAction` (write-target gate + undeclared gate per design §4).
2. `invoke`: replace the enclosing `LuaAssignmentStatement` with a re-parsed
   `local <assignment.text>` statement.
3. Register `<intentionAction>` in `plugin.xml` (next to `plugin.xml:355`) and add
   `intentionDescriptions/LuaCreateLocalVariableIntention/{description.html,before.template.lua,after.template.lua}`.
**Verification** — `LuaCreateLocalVariableIntentionTest`:
- TC1 positive: `findSingleIntention("Create local variable 'x'")` → `launchAction` →
  `checkResult("local x = 1")`.
- TC2 negative (read `print(y)`) and TC3 negative (already-declared `x`):
  assert `filterAvailableIntentions("Create local variable …")` is empty.

## Phase 3: REFACT-06-02 Create Function [Must]
**Tasks**
1. `net.internetisalie.lunar.lang.insight.LuaCreateFunctionIntention` extending
   `BaseIntentionAction` (single-name-callee gate + undeclared gate per design §5.1).
2. Arg count via `call.nameAndArgsList.firstOrNull()?.args?.exprList?.exprList?.size ?: 0`;
   generate `local function name(arg1..argN) end`; insert before the enclosing top-level
   `LuaStatement` with `block.addBefore` + `createNewLine` (design §5.3).
3. Register `<intentionAction>` + `intentionDescriptions/LuaCreateFunctionIntention/` resources.
**Verification** — `LuaCreateFunctionIntentionTest`:
- TC4 (`myFunc(1, 2)` → 2-param stub inserted above) and TC5 (`f()` → 0-param) positive via
  `findSingleIntention` / `launchAction` / `checkResult`.
- TC6 (already-declared `f`) and TC7 (`obj.method(1)`) negative via empty
  `filterAvailableIntentions`.

## Phase 4: Docs & changelog [Should]
- Set REFACT-06 statuses to reflect implementation; add a `CHANGELOG.md` entry (user-facing
  intentions). Run `ktlintFormat` / `ktlintCheck` on the new Kotlin (match surrounding style).

## Test idiom reference
Mirror `LuaDocGenerationTest.kt:142–173` (`findSingleIntention` / `launchAction` /
`checkResult`) and `LuaGlobalCreationInspectionTest` for the inspection-side parity test. Use
`BasePlatformTestCase`-style fixtures (`myFixture.configureByText(LuaFileType, …)`); intention
launch + `checkResult` need no extra EDT wrapping (no live template is started by these actions,
unlike the doc intention).
