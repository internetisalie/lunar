---
id: "BUG-67"
title: "The dependency tool window's stripe reads \"LuaRocks\""
type: "bug"
parent_id: "BUG"
status: "done"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-67: The dependency tool window's stripe reads "LuaRocks"

Found in the ROCKS-16 live verification run (2026-11-09).

## 1. Reproduction
Hover the dependency tool window's stripe button before ever opening it.

## 2. Expected vs Actual Behavior
- **Expected**: *LuaRocks Dependencies*, distinct from *LuaRocks Packages* (BUG-366).
- **Actual**: *LuaRocks*, the registration id.

## 3. Root Cause
Both factories set `stripeTitle` in `createToolWindowContent`, which runs only when the window is first opened.

## 4. Fix
The stripe title is set in `ToolWindowFactory.init`, which runs at registration.

## 5. Test
`LuaRocksToolWindowTitlesTest` now calls `init` first and asserts the stripe title there (red under mutation); seen live.
