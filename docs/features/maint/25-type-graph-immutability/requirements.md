---
id: "MAINT-25"
title: "25: Type-Graph Immutability & Safety"
type: "feature"
parent_id: "MAINT"
status: "in_progress"
priority: "high"
folders:
  - "[[features/maint/requirements|requirements]]"
---

# MAINT-25: Type-Graph Immutability & Safety

Coalesces the type-engine correctness defects from [`docs/review.md`](../../../review.md)
(re-verified 2026-07-17). The root cause identified by the review's systemic analysis (§2.5.1)
is **mutable type identity**: `LuaGraphType.Table` is a mutable data class used both as a shared
static singleton and as a hash key. Making graph types immutable(-by-copy) eliminates the class
of defects; the remaining items are cycle guards, forbidden-context VFS refreshes, and
error-reporting hygiene in the same subsystem.

**Blast radius:** the shared type engine — gate with the REDIS-04 §3.1c-style regression
contract (`lang/psi/types/*` + consumers), the same contract TYPE-10 uses.

## Absorbed review findings

| Review # | Defect |
| :--- | :--- |
| 1 | `TYPEOF_MAP` maps `"table"` to a shared mutable singleton; `handleSetMetatable` mutates it — JVM-global member leakage across files |
| 2 | `graphTypeToLuaType` has no cycle guard — `StackOverflowError` on self-referential tables (`t.self = t`) |
| 3 | Synchronous VFS refresh under the read lock in `LuaTypeManagerImpl.doResolveModule` |
| 14 | `Logger.error` on designed iteration/time cutoffs (fatal-error popups); PCE logged before rethrow |
| 47 | Same sync-refresh pattern in `LuaRocksLibraryProvider` / `PlatformLibraryProvider` roots computation |
| 58 | `compatMemo`/`visited` keyed on mutable `LuaGraphType` — hashCode changes corrupt the memo (falls out of immutability) |
| §2.5.5 (types) | `LuaRecursiveVisitor` O(n²) cats-comment scan; `VariableElement.write/read` re-traversal; `nodes` list copies |

## Requirements

| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :---: | :---: | :--- |
| MAINT-25-01 | Immutable graph types | M | Full | `LuaGraphType` immutable-by-copy; fresh instances from `TYPEOF_MAP`; copy-on-mutate in `handleSetMetatable` (#1, #58) |
| MAINT-25-02 | Cycle-safe conversion | M | Full | Thread a `visited` map through `graphTypeToLuaType` (#2) |
| MAINT-25-03 | No refresh under read lock | M | Full | `refreshIfNeeded = false` at all three sites (#3, #47) |
| MAINT-25-04 | Error-reporting hygiene | S | Not Implemented | Cutoffs → `log.warn`; never log PCE (#14) |
| MAINT-25-05 | Snapshot-cost pass | C | Not Implemented | Fix the O(n²) cats-comment scan and per-access `nodes`-list copy (§2.5.5); `write`/`read` memo deferred |
| MAINT-25-06 | No-regression gate | M | Not Implemented | REDIS-04 §3.1c-style contract: full `lang/types/*` + consumers green on a cache-defeated full-suite run vs the 2123/0/1 baseline |

**Note:** TYPE-10 (lambda-parameter inference) touches `LuaTypesVisitor`/`LuaTypeGraph`
concurrently — serialize these two features (same hot files, roadmap `Serial` cluster).

**Refinements (2026-07-17, grounding):** MAINT-25-06 added — the shared-engine blast radius makes
the regression contract a first-class `Must` acceptance gate (mirrors TYPE-10-06), not an implicit
task. MAINT-25-05 narrowed: `VariableElement.write`/`read` memoization is **unsafe mid-build**
(`checkTypes` reads them during the fixed-point loop, `LuaTypeGraph.kt:222`), so only the O(n²)
cats-comment scan (`LuaRecursiveVisitor.kt:22`) and the `nodes`-list copy
(`LuaTypeGraph.kt:42`, `LuaGraphType.kt:132/139/161/176`) are in scope; `write`/`read` memo is
deferred (risks TBD). All line citations re-verified on `main` @ `0566cfbc`.

## Test Cases

| TC | Requirement | Input | Expected output |
| :--- | :--- | :--- | :--- |
| TC-01 | MAINT-25-01 | File A: `if type(t) == "table" then setmetatable(t, {__index={leak=1}}) end`; File B (separate snapshot): `local u = {}` where `u` narrows to `table` | `u` in File B has **no** `leak` member — the `type(t)=="table"` singleton was not polluted (fresh instance per lookup) |
| TC-02 | MAINT-25-01 | Two independent `local x; if type(x) == "table" then ... end` blocks in one file | Each block's narrowed `table` is a distinct instance; augmenting one does not affect the other |
| TC-03 | MAINT-25-02 | `local t = {}; t.self = t` then query `graphTypeToLuaType` on `t` | Returns a finite `LuaTableLiteralType` (member `self` present); **no** `StackOverflowError` |
| TC-04 | MAINT-25-03 | Module resolution of `require("mod")` from an annotator read action where `mod.lua` exists on disk | Resolves via `refreshIfNeeded = false`; no synchronous VFS refresh / no platform assertion under the read lock |
| TC-05 | MAINT-25-01 | `local t = {}; setmetatable(t, {__index = base}); t.` (completion) | Baseline preserved: `base`'s members surface on the `setmetatable` result (existing `LuaTypeInferredCompletionTest` stays green) |
| TC-06 | MAINT-25-01 | Build a snapshot for a file with `function M.a() end function M.b() end` (member-accumulation sites) | `M` has both `a` and `b`; construct-once conversion did not drop members |
| TC-07 | MAINT-25-04 | `checkTypes(maxIterations = 1)` on a snapshot graph with ≥1 edge (cutoffs are defaulted params per design §3.4 step 1b — no pathological input needed) | `log.warn` emitted, loop `break`s, snapshot returned; **no** `log.error` / fatal-error popup / thrown exception |
| TC-08 | MAINT-25-04 | `resolveType` cancelled mid-flight (PCE thrown from `doResolveType`) | PCE rethrown unlogged; no `log.error("Error resolving type ...")` record |
| TC-09 | MAINT-25-06 | Full `lang/types/*` suite + consumers, `--rerun-tasks --no-build-cache` | 0 failures against the 2123 tests / 0 failures / 1 skipped baseline (`main` @ `0566cfbc` — measured from builder JUnit XML after BUG-361's cache-defeated gate, 2026-07-17) |
