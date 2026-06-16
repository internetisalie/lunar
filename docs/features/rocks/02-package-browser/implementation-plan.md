---
id: ROCKS-02-PLAN
title: "Implementation Plan"
type: plan
parent_id: ROCKS-02
status: "planned"
priority: "medium"
folders:
  - "[[features/rocks/02-package-browser/requirements|requirements]]"
---

# Implementation Plan: Package Browser (ROCKS-02)

Implements `design.md` using `luarocks --porcelain` output and `LuaRocksSettings` (ROCKS-04)
for the binary path. Phases map to requirement IDs.

## Phase 1: Services, Parsing & Cache [Must/Should] — ROCKS-02-01/03/05
- [ ] **Pre-req (shared)**: add `val ROCKET = getIcon("/icons/rocket_16.png", LuaIcons::class.java)` to
      `net.internetisalie.lunar.lang.LuaIcons` (currently only `FILE` exists, which already maps to that
      asset) — referenced by the tool-window/icon registrations.
- [ ] Create package `net.internetisalie.lunar.rocks.browser`; data models `LuaRockPackage`,
      `LuaRockMetadata` (§2.1).
- [ ] `LuaRocksSearchService.search`/`installed` (§3.1) parsing §4.1 (5-field, collapse
      `(name,version)`).
- [ ] `LuaRocksMetadataService.show` (§3.2) parsing §4.2 (tab-delimited keys).
- [ ] `LuaRocksSearchCache` (§2.5): TTL 300 000 ms, `get`/`put`/`invalidateAll`.
- [ ] Unit tests: TC-ROCKS-02-04 (search parse), TC-ROCKS-02-05 (show parse),
      TC-ROCKS-02-06 (cache staleness).

## Phase 2: Tool Window UI [Must] — ROCKS-02-01/02
- [ ] `LuaRocksPackageBrowserToolWindowFactory` + `<toolWindow id="LuaRocks Packages">` (§7).
- [ ] `SearchTextField` + `Alarm`(300 ms) debounce; CLI under `Task.Backgroundable`, results
      to EDT via `invokeLater`.
- [ ] `OnePixelSplitter`: `JBList<LuaRockPackage>` + detail panel (summary/license/homepage/
      deps).

## Phase 3: Actions & Versioning [Must/Should] — ROCKS-02-04/06/07
- [ ] `LuaRocksActionHandler.install`/`uninstall` (§3.3) under progress, notifications,
      `invalidateAll` on success.
- [ ] Version picker from the distinct versions of the selected name (§4.1).
- [ ] Refresh toolbar action → `invalidateAll` + re-search.
- [ ] Manual verification per `human-verification-checklists.md`.

## Verification Tasks
- Unit: porcelain parsers + cache (Phase 1).
- Integration: mock install → cache invalidated, row shows Installed.
- Manual: search "inspect"/"busted", view metadata, install; verify split-view + notification.
