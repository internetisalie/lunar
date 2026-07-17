---
id: "MAINT-31"
title: "31: Dead-Code Sweep"
type: "feature"
parent_id: "MAINT"
status: "done"
priority: "low"
folders:
  - "[[features/maint/requirements|requirements]]"
---

# MAINT-31: Dead-Code Sweep

Executes §3 of [`docs/review.md`](../../../review.md) (re-verified 2026-07-17): removal of
~1,000+ confirmed zero-reference lines. Pure-win chore — **schedule first** among the
review-remediation features, since every other cluster's diffs shrink once the dead code is gone.

## Scope (from review §3 — re-verify each at implementation time)

- **Whole files:** `LuaBindings.kt` (267 lines), `LuaCheckNodes.kt` (179 lines + dead
  `LuaCheckModel` halves, closes review #63), `LuaRunProfile.kt`, `InputStreamReaderExt.kt`.
- **Registered no-ops that cost cycles:** the empty `LuaEnterHandlerDelegate.preprocessEnter`
  body; the `INDEX_PATTERNS_CHANGED` subscription in `LuaFileBindingsIndex` (TodoIndex
  copy-paste — undisposed connection, needless full rebuilds). *(The three no-op annotators were
  already removed by MAINT-15.)*
- **Dead declarations/branches:** `collectGenerics`/`substitute` in `LuaTypeGraph`,
  `LuaBranchInstruction` (or use it fixing MAINT-29), `Dotted<T>`, `PackageFileBindings`,
  `processChildDeclarationsS`, `prevSiblingSkipWhitespace/Newline`, dead completion params,
  duplicate lexer map entry, unreachable `\\'` flex rule, never-emitted `WITH`/`CONTINUE`
  tokens, `TAG_OVERLOAD` state block, dead `tokenSet`/`LUACATS_TOKENS`/`STRINGS`/`KEYWORDS`,
  unused `DebugCommandKind.EXIT`/`terminate()` (or wire from `stop()` — decide in MAINT-24),
  `LuaCoverageAnnotator` super-only overrides, `RockUploadCommand.force`, the dead
  settings-event API *(only if TOOLING-08 opts to delete rather than wire it — coordinate)*,
  redundant nested write actions in two quick fixes, duplicate snapshot entry point
  (`getTypes` vs `forFile`), `withServer` on local `luarocks list`.

## Requirements

| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :---: | :---: | :--- |
| MAINT-31-01 | Whole-file removals | S | Full | 4 files + their orphaned tests/imports |
| MAINT-31-02 | No-op deregistration | S | Full | Cycle-costing no-ops removed |
| MAINT-31-03 | Dead-declaration sweep | S | Full | Every §3 line item re-verified zero-reference, then removed |

**Rule:** each removal is re-verified by repo-wide search at implementation time (the review is
two weeks old); anything that has since gained a caller is skipped and noted. Behavior-preserving:
full suite green, no baseline regression.
