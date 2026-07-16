---
id: "ROCKS-16-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "ROCKS-16"
folders:
  - "[[features/rocks/16-package-browser-redesign/requirements|requirements]]"
---

# ROCKS-16: Implementation Plan

Phases are ordered so each leaves the build green and is independently verifiable. Each phase
is tagged **[logic / unit-testable]** or **[pure-UI / VNC-verified]** to make the DoD gate
explicit. The load-bearing correctness fix (canonical install target, §3.1) is Phase 1 and is
fully unit-testable **before** any UI work.

## Phases

### Phase 1: Canonical install/uninstall target [Must] — [logic / unit-testable]
- **Goal**: Every install/uninstall targets the project rock tree (`--tree <root>`), replacing
  the tree-blind `LuaRocksActionHandler`.
- **Tasks**:
  - [ ] Create `net.internetisalie.lunar.rocks.browser.LuaRocksInstallCommand` — realizes
    design §2.1 / §3.1 (`buildInstallArgs`, `buildRemoveArgs`, `resolveTargetTree` delegating to
    `LuaRocksTreeLocator.treeRoot`).
  - [ ] Create `net.internetisalie.lunar.rocks.browser.LuaRocksInstallExecutor` +
    `InstallRequest` — realizes design §2.2 (background `Task.Backgroundable`, cache invalidation,
    notifications; `--tree` args + `withWorkDirectory`).
  - [ ] Delete `LuaRocksActionHandler`; repoint the (temporary) legacy `PackageDetailPanel`
    install/uninstall calls at `LuaRocksInstallExecutor` so the build stays green until Phase 4
    replaces the panel.
- **Exit criteria**: TC-ROCKS-16-01, -02, -03, -04 pass (unit).

### Phase 2: Installed listing + error model [Must] — [logic / unit-testable]
- **Goal**: Per-tree installed listing and honest CLI-error propagation.
- **Tasks**:
  - [ ] Create `net.internetisalie.lunar.rocks.browser.LuaRocksInstalledService` +
    `InstalledRockRow` — realizes design §2.3 / §4.1 (`list`, `parseInstalled`).
  - [ ] Add `net.internetisalie.lunar.rocks.browser.BrowserCliError`; extend
    `LuaRocksSearchService.search`/`installed` to accept `treeRoot: Path?` and throw
    `BrowserCliError` on unresolved binary / non-zero exit, plus `searchOrEmpty`/`installedOrEmpty`
    wrappers — realizes design §3.5 / §6. Migrate existing non-browser callers to the wrapper.
  - [ ] Create `net.internetisalie.lunar.rocks.browser.LuaRocksUpdateDetector` — realizes
    design §2.4 / §3.2.
- **Exit criteria**: TC-ROCKS-16-05, -06, -07, -08, -11 pass (unit).

### Phase 3: Browser state model [Must] — [logic / unit-testable]
- **Goal**: EDT-confined `LuaRocksBrowserModel` + `BrowserState` driving all state, with in-place
  refresh.
- **Tasks**:
  - [ ] Create `net.internetisalie.lunar.rocks.browser.LuaRocksBrowserModel`, `BrowserState`,
    `LuaRockRow`, `Listener` — realizes design §2.5 / §3.3 / §3.4 (search flow, `onInstallSucceeded`,
    `onRemoveSucceeded`, monotonic `requestId` staleness guard).
- **Exit criteria**: TC-ROCKS-16-07, -08, -09 pass (unit — model transitions/mutations tested
  headlessly with injected fake services).

### Phase 4: Detail pane redesign [Must] — [pure-UI / VNC-verified]
- **Goal**: Replace `PackageDetailPanel` with `PackageDetailPane` in the Plugins idiom.
- **Tasks**:
  - [ ] Create `net.internetisalie.lunar.rocks.browser.PackageDetailPane` + `DependencyRow` —
    realizes design §2.6 (`JBHtmlPane` description, `JBPanelWithEmptyText` empty card, `JBList`
    deps with click-to-search, inline Install/Uninstall/Update button with progress, Error card
    with Configure link, NoTree card, `CardLayout`).
  - [ ] Delete `PackageDetailPanel`.
- **Exit criteria**: TC-ROCKS-16-10 passes (unit on `dependencyRows`); human-verification
  checklist items for font parity (BUG-363), alignment (BUG-365), empty state (BUG-367),
  clickable deps (BUG-368), error/Configure link (ROCKS-16-05).

### Phase 5: Two-tab panel + tool-window differentiation [Must] — [pure-UI / VNC-verified]
- **Goal**: `JBTabbedPane` Marketplace/Installed surface, target-tree strip, renamed tool windows.
- **Tasks**:
  - [ ] Create `net.internetisalie.lunar.rocks.browser.LuaRocksBrowserPanel` — realizes design §2.7.
  - [ ] Create `net.internetisalie.lunar.rocks.browser.LuaRocksBrowserToolWindowFactory`; delete
    `LuaRocksPackageBrowserToolWindowFactory`; update `plugin.xml:75-80` `factoryClass` — realizes
    design §7.
  - [ ] Set stripe titles/tooltips on both factories (`LuaRocksToolWindowFactory` →
    "LuaRocks Dependencies"; browser → "LuaRocks Packages") — realizes design §7 (BUG-366).
- **Exit criteria**: TC-ROCKS-16-12 (integration/enumeration); human-verification of the two-tab
  layout, zero-query Installed tab, and unambiguous stripe names.

### Phase 6: Update affordance [Should] — [pure-UI + logic]
- **Goal**: Surface the Update badge/button.
- **Tasks**:
  - [ ] Wire `LuaRocksUpdateDetector` (§3.2) into `LuaRockRow.hasUpdate` and render the Update
    badge in the list cell renderer + the detail-pane Update button (runs install of `latestOf`).
- **Exit criteria**: TC-ROCKS-16-06 (update detection) already green in Phase 2; VNC-verify the
  badge/button surface.

### Phase 7: Add-to-rockspec affordance [Should — in scope per DR-05] — [logic]
- **Goal**: "Add to rockspec dependencies" action (owner decision 2026-07-16: build in this
  feature, not a follow-on).
- **Tasks**:
  - [ ] Append the installed rock to the discovered rockspec's `dependencies` via the ROCKS-05
    rockspec machinery.
- **Exit criteria**: a unit test asserts the rockspec edit.

### Phase 8: Popular-packages Marketplace list [Could — ROCKS-16-15] — [logic + UI]
- **Goal**: Populate the Marketplace zero-query view with a scraped "Popular / Trending" list
  instead of the neutral prompt; degrade silently on failure (owner decision 2026-07-16: build in
  this feature as a Could-have). Do this LAST — it must not gate the Must/Should phases.
- **Tasks**:
  - [ ] Add a `LuaRocksPopularService` that fetches `luarocks.org/stats/this-week` (and/or
    `/stats/dependencies`) off the EDT and parses the `<table class="table">` rows, deriving each
    package name from the row's `/modules/<author>/<name>` link; TTL-cached (reuse the
    `LuaRocksSearchCache` pattern). Any non-200 / empty / unparseable response → empty list, no throw.
  - [ ] When the Marketplace tab has no query, render the popular list through the existing result
    renderer (installed-✓ cross-ref, click-to-detail); on empty result, show the neutral prompt.
- **Exit criteria**: TC-ROCKS-16-15a (parser over a static HTML fixture) + TC-ROCKS-16-15b (fetch
  failure → neutral prompt, not the error state) green; VNC-verify the popular list renders and that
  killing network access falls back to the prompt (not a red error state).

## Requirement → Phase Coverage

| Requirement | Priority | Delivered in |
|-------------|----------|--------------|
| ROCKS-16-01 | M | Phase 5 |
| ROCKS-16-02 | M | Phase 1 |
| ROCKS-16-03 | M | Phase 2, Phase 5 |
| ROCKS-16-04 | M | Phase 4 |
| ROCKS-16-05 | M | Phase 2, Phase 4 |
| ROCKS-16-06 | M | Phase 4 |
| ROCKS-16-07 | M | Phase 4 |
| ROCKS-16-08 | M | Phase 3 |
| ROCKS-16-09 | M | Phase 4 |
| ROCKS-16-10 | M | Phase 5 |
| ROCKS-16-11 | M | Phase 2 |
| ROCKS-16-12 | S | Phase 6 |
| ROCKS-16-13 | S | Phase 7 (in scope per DR-05) |
| ROCKS-16-14 | C | Phase 4 |
| ROCKS-16-15 | C | Phase 8 |

## Verification Tasks
- [ ] Add `LuaRocksInstallCommandTest` — covers TC-ROCKS-16-01/-02/-03/-04.
- [ ] Add `LuaRocksInstalledServiceParseTest` — covers TC-ROCKS-16-05.
- [ ] Add `LuaRocksUpdateDetectorTest` — covers TC-ROCKS-16-06.
- [ ] Add `LuaRocksBrowserModelTest` (fake services) — covers TC-ROCKS-16-07/-08/-09.
- [ ] Extend `LuaRocksSearchServiceParseTest` regression guard — covers TC-ROCKS-16-11.
- [ ] Add `PackageDetailPaneDependencyTest` — covers TC-ROCKS-16-10.
- [ ] Add/extend an integration check enumerating tool-window ids/titles — covers TC-ROCKS-16-12.
- [ ] Run `human-verification-checklists.md` over the VNC gate (verify-in-ide) — all UI phases
  (4, 5, 6) require live confirmation; unit tests cannot render the tool window.

## Task Summary

| Phase | Status | Priority |
|-------|--------|----------|
| Phase 1: Canonical install/uninstall target | planned | Must |
| Phase 2: Installed listing + error model | planned | Must |
| Phase 3: Browser state model | planned | Must |
| Phase 4: Detail pane redesign | planned | Must |
| Phase 5: Two-tab panel + tool-window differentiation | planned | Must |
| Phase 6: Update affordance | planned | Should |
| Phase 7: Add-to-rockspec affordance | planned | Should |
