---
id: INSP-04-RISKS
title: Unreachable Code Risks
type: risk
parent_id: INSP-04
status: "done"
folders:
  - "[[features/inspections/04-unreachable-code/requirements|requirements]]"
---

# Risks and Gaps: Unreachable Code (INSP-04)

> The original "we must build a CFG first" risk is **obsolete**: ANALYSIS-06 shipped the
> control-flow graph (`src/main/kotlin/net/internetisalie/lunar/analysis/controlflow/`,
> exercised by `src/test/kotlin/net/internetisalie/lunar/analysis/LuaControlFlowTest.kt`).
> INSP-04 only *consumes* it. The remaining items are scope boundaries and consumer-side
> correctness, each tracked as a de-risking task.

## 1. CFG models fewer terminators than a textbook unreachable-code analyzer

**Risk:** Medium. The shipped CFG abrupts flow on `return`/`break`/`goto` and fully-abrupting
`if` branches, but **not** on `error(...)`/`os.exit(...)`/`assert(false)`, and it treats
`while true do … end` (no `break`) as exitable (always adds `addPendingEdge(whileStatement,
condInst)` — `LuaControlFlowBuilder.kt:160`). Users may expect those to flag dead code.
**Mitigation / decision:** v1 reports *exactly* what the CFG proves (zero false positives).
The gaps are captured as requirements INSP-04-C1/C2 (Could / Future Work) and de-risking
task **DR-1**, plus a *negative* regression test (TC-8) that pins the v1 boundary.

- **DR-1 (deferred, Could):** Extend `LuaControlFlowBuilder` to (a) abrupt flow after a call
  whose callee text is `error`/`os.exit`/`assert(false)`, and (b) drop the loop-exit edge for
  `while <literal true> do … end` with no `break`. This is a *CFG* change (ANALYSIS-06
  surface) and must re-run `LuaControlFlowTest` plus add INSP-04 cases. Owner: a follow-up
  ticket; not required for INSP-04 v1 `done`.

## 2. Mapping CFG instructions back to a single highlightable statement

**Risk:** Medium. The flow contains sub-statement instructions
(`LuaReadWriteInstruction`, condition exprs) as well as statement nodes; naively iterating
could highlight a fragment of a live statement, or emit N overlapping warnings across a dead
run.
**Mitigation:** design §3.3 — filter to `inst.element as? LuaStatement` (the builder gives
plain statements and `break`/`return`/`goto`/`for`/`repeat` a node whose `element` is the
statement), and apply the *head rule* so only the first statement of a contiguous dead run is
flagged (TC-04-06 verifies a single warning).

- **DR-2 (resolved — decision recorded, design §3.3 "Highlight range"):** The highlight range is
  the **whole head `LuaStatement` node**, uniformly, with no per-construct special-casing. Compound
  statements that the builder gives no statement-`element` node (`LuaIfStatement`, `LuaWhileStatement`
  — `LuaControlFlowBuilder.kt:90-141`) never surface as an `inst.element is LuaStatement` head, so the
  only multi-line head is a dead `for`/`repeat` (`…:166,189`); greying its whole node via
  `LIKE_UNUSED_SYMBOL` is the intended JetBrains "Unreachable code" presentation, so the range is
  **not** narrowed to a keyword or first child. This is now a *verification* item, not an open design
  decision: TC-04-06 pins single-warning behaviour and TC-04-09 (Phase 3) pins a dead `for`-loop's
  range spans the loop statement.

## 3. No statement-level inline suppression

**Risk:** Low. `LuaInspectionSuppression.isSuppressed(ref: LuaNameRef, …)`
(`analysis/inspections/LuaInspectionSuppression.kt:43`) keys on a `LuaNameRef`, so the
existing suppression helper cannot be reused for a statement-granular diagnostic without
extension.
**Mitigation:** out of scope for v1 (requirement INSP-04-C3, Future Work). Standard IDE
"Suppress for statement/file" intentions still work via the platform `shortName`
(`LuaUnreachableCode`) with no extra code.

## 4. Owner double-analysis / nested functions

**Risk:** Low. Analyzing both the `LuaFile` owner and each function owner could in principle
judge a statement twice.
**Mitigation:** design §3.2 — the builder's per-owner graphs contain only their own block's
statements (nested function bodies get their own owner pass), so each statement is judged by
its smallest enclosing owner exactly once. INSP-04-06 + a Phase-3 nested-dead-code test guard
this.
