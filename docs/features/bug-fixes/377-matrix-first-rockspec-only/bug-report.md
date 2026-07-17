---
id: "BUG-377"
title: "Run Test Matrix silently tests only the first discovered rockspec (and its \"…\" promises a dialog)"
type: "bug"
parent_id: "BUG"
priority: "low"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-377: Run Test Matrix silently tests only the first discovered rockspec (and its "…" promises a dialog)

> **RESOLVED 2026-07-17 (this commit)**: Replaced `firstRockspec()` with `allRockspecs()` in `RunMatrixAction`. The action now launches one `MatrixRun` per discovered rockspec (env × rockspec product). Added `rockspecLabel` to `MatrixRow`; updated the results table to show a "Rockspec" column so the user can distinguish rows across multiple rocks. Progress title shows `<rockspec> / <env>`.

## 1. Reproduction

1. Open a multi-rock workspace (two or more rockspecs discovered by ROCKS-09, e.g. `a.rockspec`
   and `b/b.rockspec`).
2. Provision ≥1 environment, then run *Tools → Lua Toolchain → Run Test Matrix…*.

The matrix launches immediately (no dialog, despite the "…" in the menu item) and runs `test` for
only **one** of the rockspecs; the other rock's tests are never executed and nothing indicates the
narrowing.

## 2. Expected vs Actual Behavior

- **Expected**: in a multi-rock workspace, either all discovered rockspecs are run (rows per
  env × rockspec), or the user chooses which rockspec to test. Menu text with a trailing ellipsis
  should open a dialog per platform UX convention — or drop the ellipsis.
- **Actual**: only the first discovered rockspec is tested, silently; the "…" suffix implies a
  chooser that never appears.

## 3. Context / Environment

- **Confidence**: high — root-caused in code.
- **Root cause**: `src/main/kotlin/net/internetisalie/lunar/rocks/matrix/RunMatrixAction.kt`:
  - `firstRockspec()` at lines 95-96 —
    `LuaRockspecDiscoveryService.getInstance(project).discoverRockspecPaths().firstOrNull()?.rockspec`;
    `actionPerformed` (line 41) builds the whole matrix request from that single path.
  - The action title `"Run Test Matrix…"` is declared at line 26; no dialog exists anywhere in
    the flow (rows fan out directly via `launchMatrix`).
- Origin: ROCKS-15-04 (design §2.6, §3.3) — the matrix predates multi-rock emphasis; discovery
  (ROCKS-09) intentionally returns *all* rockspecs, so the `firstOrNull()` is a silent truncation.

## 4. Other Notes

- **Fix direction**: when discovery returns >1 rockspec, either (a) run the matrix across all of
  them (extend `MatrixRunner.Request`/`MatrixRow` with the rockspec dimension and group the
  results table), or (b) show a rockspec chooser — which would also justify keeping the "…". If
  single-rockspec stays, drop the ellipsis and state the chosen rockspec in the aggregate
  notification.
- The results tool window (`MatrixResultsToolWindow`) currently keys rows by environment only —
  option (a) needs a small model extension there too.
