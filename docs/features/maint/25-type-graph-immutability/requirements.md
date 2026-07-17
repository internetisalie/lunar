---
id: "MAINT-25"
title: "25: Type-Graph Immutability & Safety"
type: "feature"
parent_id: "MAINT"
status: "todo"
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
| MAINT-25-01 | Immutable graph types | M | Not Implemented | `LuaGraphType` immutable-by-copy; fresh instances from `TYPEOF_MAP`; copy-on-mutate in `handleSetMetatable` (#1, #58) |
| MAINT-25-02 | Cycle-safe conversion | M | Not Implemented | Thread a `visited` map through `graphTypeToLuaType` (#2) |
| MAINT-25-03 | No refresh under read lock | M | Not Implemented | `refreshIfNeeded = false` at all three sites (#3, #47) |
| MAINT-25-04 | Error-reporting hygiene | S | Not Implemented | Cutoffs → `log.warn`; never log PCE (#14) |
| MAINT-25-05 | Snapshot-cost pass | C | Not Implemented | Fix the O(n²) cats-comment scan and per-access graph re-traversals (§2.5.5) |

**Note:** TYPE-10 (lambda-parameter inference) touches `LuaTypesVisitor`/`LuaTypeGraph`
concurrently — serialize these two features (same hot files, roadmap `Serial` cluster).
