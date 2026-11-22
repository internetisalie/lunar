---
id: "BUG-114"
title: "A file's type findings depend on the order its value/use pairs are checked"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "high"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-114: A file's type findings depend on the order its value/use pairs are checked

## 1. Summary
The type checker examines each `(value, use)` pair once, at whatever state the graph is in when the
pair is reached, and keeps the outcome. An analysis that computes a fixed point should reach the same
findings under any order of checks. This one does not: changing only the order of checks changes which
mismatches are reported, their severity, their message text, and the edges the graph ends with.
BUG-110 (a value's inferred type) and BUG-112 (a mismatch's severity) removed two instances. The rest
remain. BUG-113 is a candidate instance: `isCompatible(File, { seek = fun(p, p) })` is false only in
the final graph, so whether it is reported depends on when the pair is checked.

## 2. Reproduction
Observed with BUG-97's candidate-9 prototype (`bug-97-once`, `ae6d9432`, not merged). It keeps every
check and changes only when a pair is examined: a visit skips a value whose pair an upstream variable
will examine. On luals `script/parser/compile.lua` (Standard Lua 5.4, one cold
`LuaTypesSnapshot.forFile(file).getErrors()`), against main at `afe42f57`:

| | main | prototype |
|---|---|---|
| pairs admitted | 356 607 | 356 607 |
| closure entries at the end | 158 840 | 158 637 |
| errors | 831 | 947 |
| anchors lost | | 0 |
| new anchors | | 15 (4 ERROR, 11 HYPOTHESIS) |
| anchors HYPOTHESIS → ERROR | | 6 |

The same set of pairs is admitted, so the difference is when they are examined, not which. A second
schedule (variant A: pairs driven from each value's direct targets) gave 1 149 errors and 12 517 more
closure entries.

The corpus baselines and the full unit suite did not change, apart from two tests in `LuaPairScanTest`
that pin pair-attempt counts. No existing gate checks schedule independence.

## 3. Expected vs actual
- **Expected:** the findings of a file (anchor, severity, message) and the final graph are a function of
  the program, whatever order the pairs are checked in.
- **Actual:** they depend on the checker's schedule: today, the first variable in creation order whose
  visit holds both sides of a pair.

## 4. Root cause (grounded in code; the mechanism behind each observed delta is not yet traced)
In `src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaTypeGraph.kt`:
- **A pair is checked once and never invalidated.** `scanUse` admits a pair through `LuaCheckedPairs` and
  calls `checkAdmittedPair` with the value's `write` and the use's `read` as they are at that moment.
  Nothing re-examines the pair when either grows later. The KDoc of `checkNodePairs` states this for one
  visit ("`checkedPairs` is monotone, so that pair is never re-examined"). The same holds across passes.
- **Checks add edges.** `checkCompatibility`'s function branch (`addEdge(value.returns[i], …)`,
  `addEdge(useParam.node, …)`) and `checkTableCompatibility`'s member wiring (`addEdge(valueNode,
  target)` / `addEdge(target, valueNode)`) wire the graph only for the members present when the pair is
  checked. A different schedule wires different members, which changes the closure.
- **Errors are emitted at check time and never retracted.** `reportIncompatible` → `addError` records the
  message rendered from the types of that moment. `settleSeverities` (BUG-112) re-decides only the
  severity of claims already recorded. It does not re-check a pair or drop a mismatch that the final
  graph would not produce.

## 5. Fix strategy (to be planned)
Make the findings a function of the final graph:
1. Re-examine a pair when its value's `write` or its use's `read` changes after the check, or
2. split checking in two: wiring during the fixed point, and reporting once at the end against the final
   graph, with BUG-112's settle step as the precedent.

Option 2 also lets the fixed point use any schedule, which BUG-97's remaining speed work needs. The cost
of either option on `compile.lua` and fallo's `init.lua` must be measured before choosing (BUG-97's
250 ms target).

## 6. Test strategy
- A schedule-independence check: run the checker on the same file under two schedules (creation order and
  reversed node order) and require identical `(severity, anchor, message)` rows and an identical closure.
  Fixtures: small reductions first, then `compile.lua` and luarocks `zip.lua` (BUG-113) under the corpus.
- The corpus baselines must not lose a true finding. Every changed finding is classified as in BUG-97 §8.
