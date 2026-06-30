---
id: INSP-04-PLAN
title: Unreachable Code Implementation Plan
type: plan
parent_id: INSP-04
folders:
  - "[[features/inspections/04-unreachable-code/requirements|requirements]]"
---

# Implementation Plan: Unreachable Code (INSP-04)

ANALYSIS-06 (the control-flow graph) is **already shipped**
(`src/main/kotlin/net/internetisalie/lunar/analysis/controlflow/` — `LuaControlFlow.kt`,
`LuaControlFlowBuilder.kt`, `ControlFlowCache.kt`, `LuaInstruction.kt`). There is **no CFG
work in this plan** — INSP-04 is the first *consumer* of that graph. Each task names the
file to create and the `design.md` section that specifies it.

## Phase 1 — Inspection + reachability pass [Must]

1. **Create `LuaUnreachableCodeInspection`**
   (`src/main/kotlin/net/internetisalie/lunar/analysis/inspections/LuaUnreachableCodeInspection.kt`)
   extending `LocalInspectionTool`; overrides per design §2.1 (`getShortName="LuaUnreachableCode"`,
   `getDisplayName="Unreachable code"`, `getGroupDisplayName="Lua"`, `getDefaultLevel=WARNING`,
   `isEnabledByDefault=true`). Implements `buildVisitor` (function owners) + `checkFile`
   (file owner) per design §3.2.
2. **Implement the shared core `unreachableHeads(owner): List<LuaStatement>`** per design §3.3 —
   iterate `flow.instructions`, filter `inst.element as? LuaStatement`, query
   `flow.isReachable(inst)`, apply the head rule (§3.3 `isFirstUnreachableInItsBlock(stmt, flow)`).
   `analyze(owner, holder)` (visitor path) maps the heads to `holder.registerProblem(...,
   ProblemHighlightType.LIKE_UNUSED_SYMBOL, LuaRemoveUnreachableCodeQuickFix())`; `checkFile`
   maps them to `manager.createProblemDescriptor(...)` (nullable return).
3. **Register in `plugin.xml`** the `<localInspection>` block from design §7 (literal
   `displayName`/`groupName`, after the `LuaDeprecatedApi` entry ~line 191).
   - **Verification:** TC-04-01, TC-04-02, TC-04-03, TC-04-05, TC-04-04, TC-04-07 in
     `LuaUnreachableCodeInspectionTest` (package `net.internetisalie.lunar.lang.insight`),
     real-flow via `enableInspections` + `doHighlighting()`/`checkHighlighting()`.

## Phase 2 — Quick fix [Must]

4. **Create `LuaRemoveUnreachableCodeQuickFix`** (same file or sibling) implementing
   `LocalQuickFix` per design §2.2: resolve the flagged element's enclosing `LuaStatement`
   and `stmt.delete()` inside `WriteCommandAction.runWriteCommandAction(project,
   "Remove unreachable code", null) { … }`.
   - **Verification:** TC-04-06 (`getAllQuickFixes` → apply → `checkResult`).

## Phase 3 — Scope-boundary regression guard [Should]

5. **Add the negative/boundary tests** documenting v1 scope: TC-04-07 (`error()` not a
   terminator) and TC-04-08 (INSP-04-06 once-only owner analysis — a dead statement inside a
   nested function is flagged once at the outer head). These pin the boundary so future DR-1
   work has a clear before/after.
   - **Verification:** TC-04-07 asserts **no** warning; TC-04-08 asserts a single warning for
     nested dead code.
6. **Add the highlight-range test** TC-04-09 (DR-2 decision): a dead `for` loop produces one
   `"Unreachable code"` warning whose range spans the whole loop statement node.
   - **Verification:** TC-04-09 asserts a single warning and that the highlighted range equals the
     `for … end` statement's text range (design §3.3 "Highlight range").

## Requirement → Phase Coverage

| Requirement | Phase / Task |
|---|---|
| INSP-04-01 CFG-based reachability | Phase 1 (T1, T2) |
| INSP-04-02 Highlighting | Phase 1 (T2, T3) |
| INSP-04-03 Quick fix | Phase 2 (T4) |
| INSP-04-04 Single head per run / highlight range | Phase 1 (T2) / TC-04-04 / Phase 3 (T6, TC-04-09) |
| INSP-04-05 goto/label correctness | Phase 1 (T2) / TC-04-05 |
| INSP-04-06 Owner-scoped once-only | Phase 1 (T1) / Phase 3 (T5) |
| INSP-04-C1 / C2 / C3 (Could/Future) | Deferred — DR-1 in `risks-and-gaps.md` (DR-2 resolved) |

## Pre-commit gate (CLAUDE.md §Contribution)
`./gradlew test --tests "*UnreachableCode*"`, then `ktlintFormat` / `ktlintCheck` on the new
files, and update `CHANGELOG.md` (user-facing inspection). Full suite must stay
regression-green per the Wave baseline rule.
