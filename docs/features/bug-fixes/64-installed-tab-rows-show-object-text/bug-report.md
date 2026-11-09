---
id: "BUG-64"
title: "The Installed tab lists rocks as raw object text"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-64: The Installed tab lists rocks as raw object text

Found in the ROCKS-16 live verification run (2026-11-09).

## 1. Reproduction
Open *LuaRocks Packages* → *Installed* with rocks installed in the project tree.

## 2. Expected vs Actual Behavior
- **Expected**: Each row reads `name  version`.
- **Actual**: Rows read `InstalledRockRow(name=argparse, version=0.7.2-1)`.

## 3. Root Cause
`LuaRocksBrowserPanel`'s `installedList` had no cell renderer, so Swing drew the data class's `toString()`.

## 4. Fix
`InstalledCellRenderer` renders `installedRowText(row)` with the rockspec icon.

## 5. Test
`RockspecJsonMetadataTest.testInstalledRowsReadAsNameAndVersion`; seen live.
