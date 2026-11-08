---
id: "BUG-56"
title: "The Marketplace tab with no query shows \"Nothing to show\" instead of popular packages or a search prompt"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "low"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-56: The Marketplace tab with no query shows "Nothing to show"

## 1. Reproduction
Open the *LuaRocks Packages* tool window, Marketplace tab, with an empty search field (seen
2026-11-08). The list reads "Nothing to show", and the detail pane reads "No package selected".

## 2. Expected vs Actual Behavior
- **Expected** (ROCKS-16-15): a "Popular / Trending" list from luarocks.org. If that fetch fails, a
  neutral zero-query prompt such as "Search luarocks.org".
- **Actual**: `BrowserState.Idle` clears the list (`LuaRocksBrowserPanel.kt:69`), so the platform's
  generic "Nothing to show" appears. Either the popular fetch returned nothing, or it was never tried.
  Which of the two is not yet known.

## 3. Context / Environment
- First measure whether `LuaRocksPopularService.fetch` returns entries from the IDE host. ROCKS-16-15
  scrapes `/stats/this-week`, and that page's markup may have changed.
