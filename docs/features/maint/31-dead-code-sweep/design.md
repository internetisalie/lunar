---
id: "MAINT-31-DESIGN"
title: "Technical Design"
type: "design"
parent_id: "MAINT-31"
folders:
  - "[[features/maint/31-dead-code-sweep/requirements|requirements]]"
---

# MAINT-31: Dead-Code Sweep — Technical Design

Behavior-preserving deletion of the zero-reference code inventoried in
[`docs/review.md`](../../../review.md) §3. No new behavior; the only observable change is the
removal of two *registered no-ops that cost cycles* (an empty enter-handler body and a needless
index-rebuild subscription), which is a pure performance win.

## Verification protocol (per item — the core of this design)

The review is dated 2026-07-02; the EDITOR/REDIS/SYNTAX-18 waves landed after it. Therefore
**every §3 item is re-verified at implementation time** before deletion:

1. Repo-wide reference search (`grep -rn` over `src/main`, `src/test`, `src/integrationTest`,
   `src/main/gen`, `plugin.xml` and all `META-INF/*.xml`) for the symbol *and* its string form
   (declarative registrations, reflection-style usage).
2. An item with **any** hit beyond its own declaration/test is **skipped and recorded** in the
   risks/deviation note — never "fixed up" to stay deletable.
3. Deleting a symbol also deletes its now-orphaned imports, tests, and registrations in the same
   commit.

## Scope refinements (deviations from the raw §3 list, decided here)

- **Lexer-source items are deferred to MAINT-27's regen pass**: `lua.flex:160` (unreachable
  `\\'` rule) and `luacats.flex` `TAG_OVERLOAD`/`COMMENT_END` require `.flex` edits + parser/lexer
  regeneration — out of place in a pure-deletion chore. Kotlin-side lexer items (duplicate
  `LuaLexer.kt` map entry; `WITH`/`CONTINUE` in `LuaTokenTypes.kt`) stay in scope **iff** the
  reference search proves the generated lexer/parser never references them.
- **Items owned by other features are skipped here**: the settings-event API
  (`setProjectToolBindingAndNotify`/`setGlobalBinding` halves — TOOLING-08 wires the listener;
  note `setTargetAndNotify` has live callers now), and `DebugCommandKind.EXIT`/`terminate()`
  (wire-vs-delete is a MAINT-24 decision). `LuaBranchInstruction` is deleted — MAINT-29 can
  reintroduce it if it uses it for condition nodes.
- **Whole-file items are re-validated against today's tree first** (e.g. review #65 found
  `command/LuaCommandLine.kt` already gone — the `LuaRunProfile`/`newLuaDefaultInterpreterCommandLine`
  entries may be partially stale).

## Phases

| Phase | Maps to | Content |
|---|---|---|
| 1 | MAINT-31-01 | Whole-file removals (`LuaBindings.kt`, `LuaCheckNodes.kt` + dead `LuaCheckModel` halves incl. review #63, `LuaRunProfile.kt` remnants, `InputStreamReaderExt.kt`) + their orphaned tests/imports |
| 2 | MAINT-31-02 | Registered no-ops: empty `LuaEnterHandlerDelegate.preprocessEnter` body; `INDEX_PATTERNS_CHANGED` subscription in `LuaFileBindingsIndex` (undisposed connection, needless rebuilds) |
| 3 | MAINT-31-03 | Dead declarations/branches per §3 (as re-verified; skip-and-note anything that gained callers), incl. the redundant nested `WriteCommandAction` wrappers in the two quick fixes and consolidation of the duplicate snapshot entry point (`getTypes` vs `forFile` — keep `forFile`, mechanical caller redirect) |

One commit per phase; the final commit flips `status: done` and records skipped items in
`risks-and-gaps.md` (created then; not needed for a deletion chore up front).

## Gate

Deletion-heavy changes are exactly the case where the Gradle remote cache masks broken tests —
the final gate runs on gce-builder as `test --rerun-tasks --no-build-cache` (full suite), plus
`ktlintCheck` scoped to touched files (repo-wide is not green pre-existing). Baseline: current
main is fully green (2001/0 as of REDIS-06).

## Open Questions

None.
