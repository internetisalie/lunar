---
id: "BUG-59"
title: "Packages browser search lists a row per version, and the version picker offers only the clicked one"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-59: Search lists a row per version

## 1. Reproduction
In the *LuaRocks Packages* tool window, Marketplace tab, search for `argparse` (seen 2026-11-08).

## 2. Expected vs Actual Behavior
- **Expected** (ROCKS-16, the Plugins-page idiom; `LuaRockPackage`'s KDoc): one row per package,
  showing its newest version, with every version offered by the detail pane's version picker.
- **Actual**: twelve `argparse` rows, one per version (`0.7.2-1` down to `0.1.0-1`), push other
  packages such as `argparse-temp` to the bottom of the list. `LuaRocksSearchService` collapses only
  arch variants of one `(name, version)`, and `LuaRocksBrowserPanel` calls
  `PackageDetailPane.showPackage(row, listOf(row.pkg.version))`, so the picker holds the clicked
  row's version alone.

## 3. Context / Environment
- Found during the first live use of ROCKS-16, whose human-verification checklist was never run.
