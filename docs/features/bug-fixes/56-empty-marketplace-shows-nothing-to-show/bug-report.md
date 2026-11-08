---
id: "BUG-56"
title: "The Marketplace tab with no query shows \"Nothing to show\" instead of popular packages or a search prompt"
type: "bug"
parent_id: "BUG"
status: "done"
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

## 4. Root Cause
The popular fetch works: on 2026-11-08 `luarocks.org/stats/this-week` returned 200 and the parser
found 40 entries. Three defects kept them from the tab:
- **Never loaded at open.** `LuaRocksBrowserModel.loadPopular` ran only from `runMarketplaceSearch`
  with a blank query, which the panel calls only when the search text changes. Opening the tool
  window never fetched the list, so `marketModel` stayed empty and the list showed the platform's
  "Nothing to show".
- **Rank read as the count.** The live table's columns are Rank, Downloads, Module, and
  `PopularListParser` took the first numeric cell. Its test fixture had been written by hand with no
  rank column, so the test could not see it.
- **Count stored as the version.** `LuaRocksBrowserModel.popularPackage` put the count in
  `LuaRockPackage.version`, so the version picker offered it and Install would have run
  `luarocks install <name> 1`.

## 5. Fix
- `LuaRocksBrowserPanel` loads the popular list when it opens. With no popular list, the Marketplace
  list's empty text reads "Search luarocks.org"; a search with no results reads "No packages found".
- `PopularListParser` takes the row's last numeric cell, the download count.
- Popular rows carry a blank version, so Install and Add to rockspec take the latest, and
  `LuaRocksMetadataService.show` omits a blank version argument.

## 6. Test
- `LuaRocksBrowserPanelPopularTest`: opening the panel lists the popular entries; with none, the
  empty text is the search prompt. Removing the load at open turns both red.
- `PopularListParserTest` now runs over markup trimmed from the live page and asserts the downloads
  cell. Restoring the first-cell match turns it red.
- `LuaRocksBrowserModelTest`: a popular row's version is blank.
