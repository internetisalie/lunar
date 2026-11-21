---
id: "BUG-112"
title: "Whether a mismatch is an ERROR or a HYPOTHESIS depends on which variable examined the pair first"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-112: A mismatch's severity depends on which variable examined its pair first

Found by the cluster-collapse prototype for BUG-97 part 2: collapsing a cycle of variables changed the
order pairs were met in, and six lazy.nvim findings in `manage/process.lua` turned from HYPOTHESIS
(hidden) into ERROR, with `chain.golden` losing a duplicate ERROR rendering. BUG-111 has since removed
the lazy.nvim false report at its source; the order dependence itself is what this report fixes.

## 1. Reproduction
Both programs say the same thing; only the order of the two declarations differs.
```lua
---@param n number
local function count(n) end
local d = "s"
local e = wx.thing
e = d
count(e)
```
```lua
---@param n number
local function count(n) end
local e = wx.thing
local d = "s"
e = d
count(e)
```
`e` may hold `wx.thing`, which the engine cannot model, so the string reaching `count(e)` is not certain.
Run `LuaTypesSnapshot.getErrors()`: the first program has 3 ERRORs and 1 HYPOTHESIS (one ERROR at the
call), the second has 1 ERROR and 3 HYPOTHESIS (none at the call).

## 2. Expected vs Actual
- **Expected**: the same severities either way; the call `count(e)` is a HYPOTHESIS in both.
- **Actual**: ERROR in the first program, HYPOTHESIS in the second.

## 3. Root Cause
`LuaTypeGraph.reportIncompatible` (`LuaTypeGraph.kt`) picks ERROR when `declaredDemand &&
!unknownProvenance`, and `unknownProvenance` is a fact of the *visit* (`visitFactsOf`): whether the
visited variable's up-set holds an unknown write (BUG-441). A `(value, use)` pair is held by every
variable between the value and the use (`d` and `e` above: transitive closure puts `count(e)`'s demand
in both down-sets and `"s"` in both up-sets), but MAINT-61 examines it once, in the first variable that
holds it (`_nodes` creation order). `d` knows nothing of `e`'s unknown sibling, `e` does, so the
creation order of two variables decided whether the same pair was an ERROR.

The same shape applies to `addError`'s de-duplication on `(element, message)`: the first pair to yield
a message decided the severity of every later pair yielding it, a hypothesis from a non-declared demand
hiding an ERROR from a declared one and the reverse.

## 4. Fix Strategy
Severity is a function of the pair and every fact about it. Rule, **conservative** (v0.21.21 goal 2, no
wrong warnings):

- A pair is a HYPOTHESIS if its use is not a declared demand, or **any** variable holding it has an
  unknown write in its up-set.
- A diagnostic (one `(element, message)`) is an ERROR only if **every** pair that yields it is an ERROR.

Why not the other readings: "ERROR if any visit is certain" reports a mismatch the engine knows it
cannot fully account for (BUG-441); and a de-duplicated diagnostic taking the most severe pair measured
as three new ERRORs in the LuaCATS conformance record (`assign-type-mismatch-006`/`007`/`008`: a
`number | boolean` union at the declaring line `---@type integer m.x = 1` that is a HYPOTHESIS on base
because a non-declared pair reached the message first), so the rule takes the least severe.

How, without rescanning pairs: MAINT-61's skip is untouched. `reportIncompatible` records a claim
`(value, use, anchor, message, declared)` for the pair being examined (`LuaSeverityClaims`). When the
fixed point ends, `settleSeverities` makes one pass over the variables: a variable whose up-set holds a
claimed value and whose up-set has an unknown write demotes every claim whose use is in its down-set.
The verdict per `(element, message)` rewrites the entries `addError` kept. Hub replicas (TYPE-12) claim
the same pair at each replica site. Unknown-ness is read from the final graph, not the visit's
snapshot: the up-set only grows, so a later sibling counts, which is the point.

Cost: one membership probe per up-set entry of every variable (hash lookups, no `write` resolution),
plus `write` resolution only for variables that hold a claimed value. No claim, no work.

## 5. Test Strategy
`LuaSeverityOrderTest` (light fixture, `LuaTypesSnapshot.getErrors()`):
- both orders of the reproduction give the same severity counts (red on base: 3 ERROR + 1 HYPOTHESIS
  against 1 ERROR + 3 HYPOTHESIS);
- in both orders the mismatch at `count(e)` is a HYPOTHESIS (red on base for the first order);
- control: replacing the unknown write with `1` keeps an ERROR in both orders.

Mutation-proof: commenting out the `settleSeverities()` call turns the first two red with exactly the
counts above.

`LuaPairScanTest` and `LuaTypePairScanDifferential` are unchanged and green.

## 6. Findings classification

Measured on the libvirt builder from `60fc4fb5` (base = the fix with the `settleSeverities()` call
commented out, which is behaviourally the unfixed engine).

| Artifact | Result | Class |
|----------|--------|-------|
| Type goldens (`member-files`, `shapes`) and the annotated-class tests | byte-identical to the checked-in recording | unchanged |
| Conformance `results.json` | byte-identical to base; no case leaves `agree` | unchanged |
| Type audit (`testLuacheckMember`) | byte-identical to base | unchanged |
| Corpus baselines | no changes (see §6.1) | unchanged |
| `chain.golden` (60,94) | no change on this HEAD; the duplicate ERROR rendering was an artifact of the collapse's order | unchanged |

No ERROR was lost and none was added on any recorded artifact. The order dependence was observable
only on a program shaped like §1, and no golden, corpus member or conformance case here has one.

### 6.1 Full suite

`test --rerun --no-build-cache -PwithCorpus` on the libvirt builder: 601 files, 3905 tests, 0 failures,
0 errors, 4 skipped. The corpus baselines are asserted by that run and none changed.

### 6.2 Residual
A pair whose *use* is itself the variable with the unknown write (`local e = ...` as the demand `e`
receives from `count(e)`) is held by no visit of `e`, so `e`'s sibling does not demote it. It is the
same ERROR in both orders, so it is not an order dependence; whether it should also be a HYPOTHESIS is
a separate question.
