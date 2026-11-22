---
id: "BUG-114"
title: "A file's type findings depend on the order its value/use pairs are checked"
type: "bug"
parent_id: "BUG"
status: "done"
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

## 5. Fix (option 2: wire during the fixed point, report once against the final graph)
Built on `07c8b217`. `checkCompatibility` runs in one of two phases (`LuaTypeGraph.CheckPhase`).
- **WIRE**, during the fixed point and for a value→use edge met while the graph is built: the `addEdge` calls of
  `checkFunctionCompatibility` (returns, params) and `checkTableCompatibility` (member edges through
  `memberDemandHubs`). Every report site is skipped; the control flow that decides what to wire is unchanged. A
  value→use edge sits in no variable's sets, so `wireDirectPair` also records it in `directPairs`.
- **REPORT**, once, after the fixed point (`reportFindings`): no edge is added. Every pair `checkedPairs` admitted is
  judged with its final `write` and `read`, then each `directPairs` entry (`certain = true`, as at the edge).
  `settleSeverities` (BUG-112) runs after it. `LuaCheckedPairs.forEachInCreationOrder` lists the pairs by use and
  then value, in each node's creation order (`creationOrder`: `order` on all four node classes, which is its index in
  `_nodes`). The first pair to yield a finding, and so `addError`'s first-wins de-duplication and the claim order, are
  then properties of the graph and not of the schedule.
- **`unknownProvenance` is gone from the check.** It only chose the initial tier of an error that
  `settleSeverities` re-decides for every claimed `(element, message)`, which is every error `reportIncompatible`
  emits. The per-visit fact computation (`visitFactsOf`) is gone with it.
- **`certain` is a property of the pair's first holder in creation order** (`LuaPairCertainty`): the variable
  that holds both the Nil and the use and was created first, which has exactly one non-declared reaching definition.
  It is asked only when a Nil fails a use (BUG-416), and the holder index is built on first use. The base used the
  first variable the schedule visited.
- **Inventory of `addError` and `severityClaims` callers.** `reportIncompatible` (all five sites in
  `checkCompatibility`, and the claim `record`), `reportUndefinedFields` (a Generic value), `reportArity` from
  `checkFunctionCompatibility`, the `Missing required field` error in `checkTableCompatibility`, and
  `replicateHubAnchoredErrors` (with `severityClaims.replicate`) are all REPORT. `reportArity` called by
  `LuaTypesVisitor.reportDeclaredArity` stays at build time: a declared signature against a call's own arguments
  reads no graph state.
- **The 5 s limit and the iteration cap.** `runFixedPoint` returns when either trips and `reportFindings` judges the
  pairs admitted so far. The pairs the stopped fixed point never admitted are not reported, which is what the base
  did.
- **Kept as they were.** MAINT-61's revisit skip and `LuaTypePairScanDifferential` and `LuaTypePairWriteDifferential`
  gate the wiring scan only, and their counts did not change (`LuaPairScanTest` pinned counts unchanged).
  `reportFindings` never revisits: it lists the admitted set once. `warmWriteMemos` resolves the `write` of every
  value in a variable's up-set, which the per-visit facts did, so cross-file call-site resolution after the build still
  finds the walks memoized (`LuaColonCallCrossFileFanOutTest`).
- **Test-only.** `LuaTypeGraph.scheduleReversed` makes `runFixedPointPass` visit variables last-created first.
  `closureDigest` is the closure as a relation: the distinct `(source, target)` creation positions in the sets, cluster
  expanded, so it does not depend on which member represents a cluster.

- **BUG-116 composes with the report pass.** `reportIncompatible` records a claim for every mismatch the pass reports,
  with the pair as it was examined. `LuaSeverityClaims.resolve` then settles a claim whose use is a variable by
  `settleVariableUseProvenance`: declared only if the value alone fails a declared use in the variable's down-set. That
  reads the final graph, so it needs nothing from the report pass beyond the claim, and a direct value→use pair (no
  variable) keeps the `declaredDemand` of its use.

**Wiring is not schedule independent, and re-examining a pair does not make it so.** A sweep that re-wired every
admitted pair against the final graph, repeated until it added no edge, added 0 edges on `compile.lua`, `zip.lua` and
the eight small fixtures, so it was dropped. On luarocks `zip.lua` the closure still differs between schedules (5 649
and 5 630 distinct edges). The pair `_tl_compat and _tl_compat.string or string` against the demand `{ char = ... }`
is a union with an `Any` arm, so `checkCompatibility` wires an arm only when `isCompatible(arm, use)` holds. Whether
it holds depends on what the member's `read` has already collected, which the wiring itself adds to: examined early the
pair is compatible, wires `string.char`, and stays compatible; examined late it is incompatible, wires nothing, and
stays incompatible. Both are fixed points of a rule that is not monotone in the graph. A schedule-independent closure
needs that gate to be, which changes what is wired in every file.

## 6. Test strategy and measurements
`LuaScheduleIndependenceTest` builds six small fixtures twice through `LuaTypesVisitor.buildSnapshot`, once with
`scheduleReversed`, and compares sorted `(severity, anchor, message)` rows and `closureDigest`. Against `07c8b217` it
went red for `method` (8 rows against 11) and `nilsum` (7 against 3), the two fixtures whose findings change with the
schedule there. `LuaParamTagOrderTest` compares its two tag orders as sorted rows, because the order of
`getErrors()` follows node creation order and is no contract.

The corpus runs are a throwaway probe (never committed), on `origin/bug-117` (BUG-116, BUG-117 and BUG-118 in) and the
fix on top of it:

| build of | base, normal | fix, normal / reversed |
|---|---|---|
| `compile.lua` rows | 831 (824 HYPOTHESIS, 7 WARNING, 0 ERROR) | 1 150 / 1 150, identical (1 143 HYPOTHESIS, 7 WARNING, 0 ERROR) |
| `compile.lua` closure | | 333 181 edges, identical |
| `zip.lua` rows | | 2 / 1 |
| `zip.lua` closure | | 5 649 / 5 630 edges |
| small fixtures (eight) | `method` 8 / 11, `nilsum` 7 / 3 (reversed) | all identical |

**Cost.** `compile.lua`'s own build (`LuaSnapshotBuildMeter`), builder `libvirt`, shared with `~/sim-app`. Six reads
after appending `"\n"` to the file, median wall time per run, runs back to back: base 980 ms, fix 1 401 ms; fix 1 205 ms,
base 1 193 ms; fix 1 372 ms. The pairs differ from +1 % to +43 %: the builder's load moves a single configuration by
up to 25 %, so the cost is not resolved better than +0 to +40 %. The pass judges 356 607 pairs and drops the per-visit
facts; enumerating the admitted pairs costs 9 ms. A product scan of every variable's up and down sets instead
(40 678 119 attempts) was 1.8 s and was not kept.

**Differential on `compile.lua`** (831 rows to 1 150): 0 anchors lost, 0 ERROR rows in either build, 42 new
anchors, all HYPOTHESIS; 7 base rows are gone, all union renderings (`{ ... } | { ... } | { ... } | nil | ... is not
assignable to { ... }`) at anchors that keep a row. The new anchors are one family: `nil | { ... } is not assignable to
{ ... }` or a wider union of tables, at `node`, `name`, `parseSimple(...)`, `.finish` reads and the like. Their demand
is a union or an inferred table demand that BUG-115 rejects for a union value's table arm, which main reports at other
anchors of the same file already. BUG-116 settles the claim against the variable by the value alone against the
declared uses in its down-set, so none of them is an ERROR.

| item | change | class |
|---|---|---|
| `compile.lua`, 42 anchors | HYPOTHESIS added | schedule artifact removed: the pair is judged against the final graph, where the demand is in place; BUG-115 family, hypothesis tier |
| `compile.lua`, 7 rows | union rendering gone | schedule artifact removed |
| penlight `lua/pl/pretty.lua` 59 | `LuaReturnTypeMismatch string is not assignable to number` gone | schedule artifact removed; `tostring(value)` returns a string on every path, so the base's finding was false |
| lazynvim `manage/init.lua` 18, `manage/task/init.lua` 39 | absent in both builds | not reported on this base: BUG-118 (optional `opts`) and BUG-116 (declared provenance) cover them |
| zerobrane, luacheck, luarocks, lazynvim | identical to the base | unchanged |
| `shapes/` goldens (`selfCalls`, `twoReceivers`, `arity`, `chain`, `unionReceiver`) | HYPOTHESIS `fun(n) \| fun(n) is not assignable to fun(n)` added at method sites (the base reported them under the reversed schedule, `method` fixture); `chain` gains `ERROR string \| number ...` at (60, 94) beside the row at (76, 77); `unionReceiver` renders a wider union | schedule artifact removed; goldens and `LuaMemberDemandHubTest` re-recorded |

Across the five corpus members the sweep differs from the base in one finding, the penlight one above.

## 7. Open
- `zip.lua`'s closure and rows still depend on the schedule (section 5): a monotone gate for the `Any` arm of a union
  value is BUG-119, and it changes wiring everywhere.
- The extra pass costs up to about 40 % of `compile.lua`'s build on a loaded builder; the ratio is not resolved
  tighter than that.
