---
id: "BUG-69"
title: "An error state keeps the previous search's results"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-69: An error state keeps the previous search's results

Found in the ROCKS-16 live verification run (2026-11-09).

## 1. Reproduction
Unbind LuaRocks, then search.

## 2. Expected vs Actual Behavior
- **Expected**: Only the error and its Configure link.
- **Actual**: The error beside the previous query's results.

## 3. Root Cause
`BrowserState.Error` updated the detail panes only.

## 4. Fix
`LuaRocksBrowserPanel.showError` clears the Marketplace list first.

## 5. Test
`LuaRocksBrowserPanelPopularTest` checks that an error replaces the previous results (red under mutation); seen live.
