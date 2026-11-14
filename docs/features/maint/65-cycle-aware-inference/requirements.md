---
id: "MAINT-65"
title: "65: Lambda-Gated Callee Resolution"
type: "feature"
status: "planned"
priority: "high"
parent_id: "MAINT"
folders:
  - "[[features/maint/requirements|MAINT]]"
---

# MAINT-65: Lambda-Gated Callee Resolution

## Overview
A call with no lambda argument stops resolving its callee's expected callback type. That
resolution seeds nothing for such a call, yet it reads the snapshot of the callee's declaring file:
a nested build, or a partial snapshot when that file is still being built. Parent:
[[features/maint/requirements|MAINT]]. Measurement feature: [[MAINT-60]]. Precondition:
[[BUG-98]] (see MAINT-65-02).

## Measured Cause
[[MAINT-63]] DR-08 attributed the edit phase's provisional builds to the module guard
(`LuaTypeManagerImpl.resolvingModules`) inside import cycles. Building a cycle's files as one unit
does not beat base ([risks-and-gaps.md](risks-and-gaps.md), DR-03). Under that unit-build variant,
nearly all remaining cross-file in-progress hits come through TYPE-10's expected-callback
resolution (DR-02). `LuaTypesVisitor.propagateExpectedLambdaParams` runs that resolution for every
call. On base invalidation, skipping it for calls without a lambda argument is the measured lever
(DR-04).

## Scope
- In scope: resolve the callee's expected callback type only when the call has at least one lambda
  argument.
- Out of scope: building an import cycle as one unit (DR-03), and per-file edit invalidation
  (MAINT-63; it adds nothing on top of this item, DR-04).

## Functional Requirements

| ID | Requirement | Priority | Status | Description |
|----|-------------|----------|--------|-------------|
| MAINT-65-01 | **No callee resolution without a lambda argument** | M | Not Implemented | When no argument of a call unwraps to a `LuaFuncDef`, the caller's snapshot build does not resolve the callee's expected callback type. Observable effect: that call adds no entry to the `inProgressHits` of the built snapshot's TYPE-11 frame (`LuaTypeSourceRecorder.snapshotFrames`). TYPE-10's lambda seeding is unchanged, including the argument index each lambda is seeded from. |
| MAINT-65-02 | **Findings unchanged** | M | Not Implemented | The MAINT-54 audit's `luacheck` member and the MAINT-55 conformance results are byte-identical before and after the change. Every candidate file has the same findings digest before and after the change when it is read alone. "Read alone" means read first, in a fresh fixture. Candidates are selected by design §3.2. BUG-98 lands before this item's acceptance runs. The digest check excludes no sites. |
| MAINT-65-03 | **Fewer edit misses, no more cold misses** | M | Not Implemented | The full MAINT-60 sweep is used, and each commit is swept twice. For each bound (`cold-250` and `edit-250`), a miss is counted exactly as `SnapshotCostVerdict` counts it: over every sweep row, a pass misses when its status is not `ok` or its time is 250 ms or more. Base's run-to-run spread is taken from four base sweeps of one commit (risks DR-09): 41 for `edit-250` and 24 for `cold-250`. **`edit-250`**: the higher change count must be more than 41 below the lower base count. **`cold-250`**: the higher change count must be no more than 24 above the lower base count. |

## Test Cases
The base commit is this item's Phase 1 commit (implementation plan): it has `LuaAloneDigestTest`
and not the gate. TC 1 to TC 4 read `snapshot = LuaTypesSnapshot.forFile(file)` and
`frame = LuaTypeSourceRecorder.snapshotFrames[snapshot]` for a single file, `main.lua`.

| # | Requirement | Given (input) | When (action) | Then (expected) |
|---|-------------|---------------|---------------|-----------------|
| 1 | MAINT-65-01 | `---@param cb fun(x: string)`, then `local function run(cb) end`, then `run(1)` | read `snapshot` and `frame` | `frame.inProgressHits` is empty |
| 2 | MAINT-65-01 | as TC 1, with the last line `run(function(x) end)` | read `snapshot`, `frame` and the type of `x` | `frame.inProgressHits` holds `main.lua`'s URL; `x` is `string` |
| 3 | MAINT-65-01 | `---@param n number`, then `---@param cb fun(x: string)`, then `local function run2(n, cb) end`, then `run2(1, function(x) end)` | read the type of `x` | `x` is `string` |
| 4 | MAINT-65-01 | the existing tests of `LambdaParamInferenceTest` and `ExpectedCallbackResolverTest` | run them, unmodified | all pass |
| 5 | MAINT-65-02 | `test -PwithTypeAudit -PrecordConformance --tests '*LuaTypeFalseNegativeAuditTest.testLuacheckMember' --tests '*LuaCatsConformanceTest*'`, at the base commit and at this item's commit | compare `build/type-audit/luacheck.json` and `build/conformance/results.json` | byte-identical |
| 6 | MAINT-65-03 | `test -PwithSnapshotSweep --tests '*SnapshotCostSweep*'`, twice at the base commit and twice at this item's commit | for each `sweep.jsonl`, count rows with `coldStatus != "ok"` or `coldMs >= 250`, and separately rows with `editStatus != "ok"` or `editMs >= 250` | each bound meets MAINT-65-03's rule |
| 7 | MAINT-65-02 | the first base sweep and the first change sweep from TC 6 | run design §7's command at the base commit and at this item's commit | every row of `build/alone-digest/digests.tsv` has `status` `ok` in both runs and the same `digest` in both. With zero candidates, the file is empty, TC 7 passes trivially, and the PR records "0 candidates" |

### Mutation checks
Each mutation is a plausible implementation error that the test case turns red. TC 1 to TC 4 are
the gate's falsifiers. TC 6 is a performance acceptance check, and TC 5 and TC 7 are regression
checks.

| # | Mutation | Why it turns red |
|---|----------|------------------|
| 1 | No gate: the parent commit | DR-07: `run(1)` records `main.lua` as an in-progress hit on base |
| 2 | A gate that also skips calls with a lambda, for example `if (lambdas.isNotEmpty()) return` | the callee is never resolved, so `x` is not seeded and no hit is recorded |
| 3 | Seeding each lambda from its position in `lambdas` instead of its argument index | `expectedCallbackAt(0, …)` returns `n: number`, which is not a function type, so `x` stays unseeded |
| 4 | A gate that checks only the first argument | `LambdaParamInferenceTest.testTableSortComparatorInfersAny_TC3` passes an inline lambda second, `table.sort(t, function(a, b) return a end)`, and asserts `a` is `any`. The mutant skips that call, and an unseeded lambda parameter reads `undefined` (DR-07, the `r:run(function(x) end)` row) |
| 5 | None known | no gate mutation is known to change the `luacheck` member or the conformance results. DR-05 shows both are byte-identical under the prototype gate. TC 5 guards against unrelated regressions; TC 1 to TC 4 catch the gate's own mutations |
| 6 | Always resolve, as base does (for example, the gate's early return deleted) | the change sweeps then come from base's distribution. The four DR-09 base sweeps span exactly 41 `edit-250` misses, so a change drawn from base's observed range cannot be more than 41 below the lower base count. No single-line mutant is known that keeps most of the gain and still fails this check. That is why TC 6 is an acceptance check, not a falsifier |
| 7 | A gate that checks only the first argument (as 4) | a candidate file that passes a lambda after another argument into a typed callback slot loses those seeds, which changes its findings. TC 7 turns red only if such a file is a candidate, so it is weaker than TC 4 for this mutation |
