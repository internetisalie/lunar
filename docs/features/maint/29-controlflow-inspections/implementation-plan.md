---
id: "MAINT-29-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "MAINT-29"
folders:
  - "[[features/maint/29-controlflow-inspections/requirements|requirements]]"
---

# MAINT-29: Implementation Plan

Four files, seven defects, sequenced so each phase leaves the build green and is verifiable via
real-flow tests. Baseline on `main` `8b1e4586`: **2123 pass / 0 fail / 1 ignored**. Every phase
must keep that green (regression-relative).

Test discipline (per the repo's past false-green on `getAllQuickFixes`): quick-fix tests use
`myFixture.findSingleIntention(text)` + `myFixture.launchAction(fix)` with **before/after buffer
text assertions** (`myFixture.checkResult(...)`), not a bare `getAllQuickFixes` presence check.
CFG tests use `ControlFlowCache.getControlFlow(owner)` + `LuaControlFlow.isReachable` (as
`LuaControlFlowTest` already does) for edge mechanics, and `enableInspections` + `doHighlighting`
for the user-visible unreachable-code assertions.

## Phases

### Phase 1: Safe quick fixes (#8, #9) [Must]
- **Goal**: The two destructive fixes produce valid Lua or are withheld. This is the user-facing
  headline.
- **Tasks**:
  - [x] Rewrite `ReplaceIntegerDivisionFix.invoke`
    (`lang/syntax/LuaLanguageLevelQuickFixes.kt:86-107`) to navigate to the enclosing
    `LuaBinOpExpr`, guard on `LuaElementTypes.INTDIV`, and rebuild
    `math.floor(left / right)` via `LuaElementFactory.createExpression` then `binOp.replace(...)`
    — realizes design §3.1. Keep `startInWriteAction() = true`; add no `WriteCommandAction`.
  - [x] Make the `LuaMakeLocalQuickFix()` registration conditional in
    `LuaGlobalCreationInspection.buildVisitor` (`LuaGlobalCreationInspection.kt:60-66`): compute
    `eligible = assignStat.varList.varList.size == 1 && variable.varSuffixList.isEmpty()` and
    prepend the make-local fix only when eligible — realizes design §3.2. Add the defensive
    single-target early-return in `LuaMakeLocalQuickFix.applyFix`.
- **Exit criteria**: TC-01..TC-04 pass — `7 // 2` → `math.floor(7 / 2)` (checkResult); `(a+b) // c`
  → `math.floor((a+b) / c)`; make-local offered on `x = 1`, **not** offered on `x, t.f = 1, 2`
  (findSingleIntention throws / returns absent). Full suite still green.

### Phase 2: CFG correctness (#32, #33) [Must]
- **Goal**: Fall-through/pending/label edges correct; conditions descended.
- **Tasks**:
  - [x] Replace every fall-through `builder.controlFlow.instructions.lastOrNull()` with
    `builder.prevInstruction` in `LuaControlFlowBuilder` (lines 110, 126, 150, 197, 224) —
    realizes design §3.3.
  - [x] Reorder `visitIfStatement` (lines 98-141) so each branch's fall-through pending edge is
    computed from the branch body's `prevInstruction`, then `flowAbrupted()` runs before the next
    condition node — realizes design §3.4.
  - [x] Replace the flat `labelInstructions: Map<String, Instruction>` (line 16) with a
    `Map<LabelKey, Instruction>` keyed on `(name, enclosing LuaBlock)`; capture `gotoElement` in
    `GotoRecord` (`visitGotoStatement`, line 258); add `resolveGoto` that ascends enclosing blocks;
    rewire the `build` label-linking loop (lines 58-63) — realizes design §3.5.
  - [x] `expr.accept(this)` on the condition in `visitIfStatement` (each condition at line 101),
    `visitWhileStatement` (line 144), and `visitRepeatStatement` (line 173), after the
    `startNode` — realizes design §3.3/#33.
- **Exit criteria**: `LuaControlFlowTest` + `LuaUnreachableCodeInspectionTest` still green; new
  TC-05..TC-08 pass (spurious-edge, sibling-`::continue::`, condition-READ, elseif-leak).

### Phase 3: Unused-local accuracy (#34, #69) [Should]
- **Goal**: Assigned-only locals flagged; ambiguous usages retained.
- **Tasks**:
  - [ ] Add `isSimpleWriteTarget(nameRef: LuaNameRef)` and a `when` branch in
    `LuaUnusedLocalInspection.classify` (lines 88-108) that skips simple write targets before the
    `else -> usages.add` — realizes design §3.6.
  - [ ] Replace `usage.reference?.resolve()` in `collectUsedDeclarations` (line 131) with a
    `multiResolve(false)` iteration over `PsiPolyVariantReference` results — realizes design §3.7.
- **Exit criteria**: `LuaUnusedLocalInspectionTest` still green; new TC-09..TC-10 pass
  (`local flag; flag = true` flagged; shadowed-ambiguous usage not falsely flagged).

### Phase 4: Concat false positives (#68) [Could]
- **Goal**: `__concat` classes not flagged.
- **Tasks**:
  - [ ] Change the `is LuaGraphType.Table` branch in
    `LuaSuspiciousConcatenationInspection.isConcatenable` (lines 71-79) to
    `type.localMembers.containsKey("__concat")` (or `resolveMember("__concat") != null` per DR-01's evidence) — realizes design §3.8 (confirm the `Table.localMembers`
    accessor via DR-01 first).
- **Exit criteria**: `LuaSuspiciousConcatenationInspectionTest` still green; new TC-11 passes
  (a `@class` with `__concat` is not flagged; a plain table still is).

## Requirement → Phase Coverage

| Requirement | Priority | Delivered in |
|-------------|----------|--------------|
| MAINT-29-01 | M | Phase 1 |
| MAINT-29-02 | M | Phase 2 |
| MAINT-29-03 | S | Phase 3 |
| MAINT-29-04 | C | Phase 4 |

## Verification Tasks
- [ ] Extend `LuaLanguageLevelInspectionTest` with quick-fix before/after `checkResult` cases for
  #8 (integer-division rewrite) — covers TC-01, TC-02.
- [ ] Extend `LuaGlobalCreationInspectionTest` with make-local offered/withheld cases via
  `findSingleIntention` — covers TC-03, TC-04.
- [ ] Extend `LuaControlFlowTest` with `isReachable` edge assertions for the spurious-edge,
  sibling-`::continue::`, and condition-READ (`LuaReadWriteInstruction` for a condition name)
  cases — covers TC-05, TC-06, TC-07.
- [ ] Extend `LuaUnreachableCodeInspectionTest` (`doHighlighting`) for the elseif-leak
  false-negative — covers TC-08.
- [ ] Extend `LuaUnusedLocalInspectionTest` — covers TC-09, TC-10.
- [ ] Extend `LuaSuspiciousConcatenationInspectionTest` — covers TC-11.
- [ ] Run the full suite via `tooling/gce-builder/gce-builder.sh run "test --rerun --no-build-cache"`
  (defeats the remote-cache false-green on deletion/edit-heavy changes) and confirm ≥ 2123 pass /
  0 fail.
- [ ] Run `python3 scripts/lint_docs.py docs` and `python3 scripts/lint_planning.py docs` — both
  clean.

## Task Summary

| Phase | Status | Priority |
|-------|--------|----------|
| Phase 1: Safe quick fixes (#8, #9) | done | Must |
| Phase 2: CFG correctness (#32, #33) | done | Must |
| Phase 3: Unused-local accuracy (#34, #69) | todo | Should |
| Phase 4: Concat false positives (#68) | todo | Could |
