---
id: "MAINT-28"
title: "28: Completion Correctness & Performance"
type: "feature"
parent_id: "MAINT"
status: "in_progress"
priority: "medium"
folders:
  - "[[features/maint/requirements|requirements]]"
---

# MAINT-28: Completion Correctness & Performance

Coalesces the completion-stack defects from [`docs/review.md`](../../../review.md) (re-verified
2026-07-17): the copy-file/original-file confusion that silently disables the cross-file phase,
duplicate symbol passes, ranking mis-extraction, and the per-session performance sinks the
review's systemic analysis attributed to this stack.

## Absorbed review findings

| Review # | Defect |
| :--- | :--- |
| 24 (rest) | Uses the completion **copy** file, not `parameters.originalFile` — index queries hit a never-indexed file (cross-file phase returns nothing); proximity tiers unreachable. (`extractRequires` caching already fixed.) |
| 25 | `LuaEnterBetweenBlockHandler` guard unsatisfiable — COMP-08-04 never fires |
| 39 | `addSymbolCompletions` invoked up to 3× per completion (three call sites) |
| 40 | `extractFuncDeclName` surfaces method *receivers* as standalone global candidates |
| 62 | `hasPrefix` cannot detect a typed prefix (dummy-identifier merge); shadowed variable |
| §2.5.5 | Full snapshot incl. `checkTypes` per completion session; `StubIndex.getAllKeys` ×2 per invocation (cache on `PsiModificationTracker`) |

## Requirements

| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :---: | :---: | :--- |
| MAINT-28-01 | Original-file discipline | M | Full | Index/proximity work against `parameters.originalFile` (#24) |
| MAINT-28-02 | Single symbol pass | S | Full | One `addSymbolCompletions` call site; fold the identifier provider in (#39) |
| MAINT-28-03 | Ranking accuracy | S | Full | Skip method-separator decls in global ranking (#40); fix `hasPrefix` (#62) |
| MAINT-28-04 | Enter-between-blocks | C | Not Implemented | Fix the off-by-one guard (#25) — real-flow DoD test required |
| MAINT-28-05 | Session cost | S | Not Implemented | Modification-tracked key caching; avoid full `checkTypes` snapshot per session (§2.5.5) |

**DoD note:** completion features gate on real-flow tests (`completeBasic()`), per the roadmap's
DoD clause — engine-only tests hid exactly this class of bug (#24) for a full wave.

## Test Cases

Every case is real-flow: `myFixture.completeBasic()` (or actual Enter), never engine-only.

| TC | Requirement | Input (fixture) | Action | Expected output |
| :--- | :--- | :--- | :--- | :--- |
| TC-24 | MAINT-28-01 | `main.lua` = `require("mod")\nfoo<caret>`; `mod.lua` = `function foobar() end`; both on real disk (heavy fixture, source content root registered) | `completeBasic()` | Lookup strings contain `foobar` (cross-file require phase resolves against the indexed original file). Before fix: absent. |
| TC-39 | MAINT-28-02 | `local price = 1\npri<caret>` | `completeBasic()` | Lookup offers `price` exactly once (no duplicate `price` entries); scope-walk runs once per session. |
| TC-39b | MAINT-28-02 | `local price = 1\nlocal t = {}\nt.pri<caret>` | `completeBasic()` | Lookup does **not** offer the standalone local `price` after the dot (the deleted IDENTIFIER provider's member-position leak is gone); member completion for `t` unaffected. |
| TC-40 | MAINT-28-03 | `function Account:deposit() end\nfunction ledger() end\nledg<caret>` and separately `Acc<caret>` | `completeBasic()` | `ledger` offered; typing `Acc`, `Account` is **not** offered as a global function (method receiver excluded). |
| TC-62 | MAINT-28-03 | `local x = tr<caret>` (typed prefix `tr`) | `completeBasic()` | Expression keyword `true` is **not** injected into the list (real prefix suppresses the keyword literals); with empty prefix `local x = <caret>`, `true`/`false`/`nil` **are** offered. |
| TC-25p | MAINT-28-05 | Any file with ≥1 project global | Two consecutive `completeBasic()` invocations with no intervening PSI edit | The `CachedValue` instance returns the **identical `List` object** on both invocations (`assertSame` on `funcKeyCache.value` snapshots taken around each completion) — proving no recomputation; after a PSI edit, a different instance. No spy/counter seam needed. |
| TC-25 | MAINT-28-04 | `if x then<caret>end` (caret between `then` and `end`) | Type Enter (`myFixture.type("\n")`) | A blank, indented body line is opened above `end` (`DefaultForceIndent` fires). Negative: Enter *after* `end` leaves default behavior. |
