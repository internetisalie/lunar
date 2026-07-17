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
  - [x] Create `net.internetisalie.lunar.rocks.browser.LuaRocksInstallCommand` — realizes
    design §2.1 / §3.1 (`buildInstallArgs`, `buildRemoveArgs`, `resolveTargetTree` delegating to
    `LuaRocksTreeLocator.treeRoot`).
  - [x] Create `net.internetisalie.lunar.rocks.browser.LuaRocksInstallExecutor` +
    `InstallRequest` — realizes design §2.2 (background `Task.Backgroundable`, cache invalidation,
    notifications; `--tree` args + `withWorkDirectory`).
  - [x] Delete `LuaRocksActionHandler`; repoint the (temporary) legacy `PackageDetailPanel`
    install/uninstall calls at `LuaRocksInstallExecutor` so the build stays green until Phase 4
    replaces the panel. (Also repointed the second caller `LuaCoverageProgramRunner`.)
- **Exit criteria**: TC-ROCKS-16-01, -02, -03, -04 pass (unit). ✅ 5 tests / 0 failures.

### Phase 2: Installed listing + error model [Must] — [logic / unit-testable]
- **Goal**: Per-tree installed listing and honest CLI-error propagation.
- **Tasks**:
  - [x] Create `net.internetisalie.lunar.rocks.browser.LuaRocksInstalledService` +
    `InstalledRockRow` — realizes design §2.3 / §4.1 (`list`, `parseInstalled`).
  - [x] Add `net.internetisalie.lunar.rocks.browser.BrowserCliError`; extend
    `LuaRocksSearchService.search`/`installed` to accept `treeRoot: Path?` and throw
    `BrowserCliError` on unresolved binary / non-zero exit, plus `searchOrEmpty`/`installedOrEmpty`
    wrappers — realizes design §3.5 / §6. Migrated the legacy panel caller to `searchOrEmpty`.
    Also keyed `LuaRocksSearchCache` on the resolved server (review finding #70).
  - [x] Create `net.internetisalie.lunar.rocks.browser.LuaRocksUpdateDetector` — realizes
    design §2.4 / §3.2 (+ `LuaRockRow` pulled forward as the shared row type).
- **Exit criteria**: TC-ROCKS-16-05, -06, -11 pass (unit) ✅. TC-ROCKS-16-07/-08 (model error
  transitions) land with the model in Phase 3 — the service-level throw substrate is in place here.

### Phase 3: Browser state model [Must] — [logic / unit-testable]
- **Goal**: EDT-confined `LuaRocksBrowserModel` + `BrowserState` driving all state, with in-place
  refresh.
- **Tasks**:
  - [x] Create `net.internetisalie.lunar.rocks.browser.LuaRocksBrowserModel`, `BrowserState`,
    `Listener` (+ `LuaRockRow` landed in Phase 2) — realizes design §2.5 / §3.3 / §3.4 (search flow,
    `onInstallSucceeded`, `onRemoveSucceeded`, monotonic `requestId` staleness guard). Added a
    `LuaRocksBrowserBackend` CLI+threading seam (`ProjectBackend` prod / fake in tests) so the model
    is verifiable headlessly.
- **Exit criteria**: TC-ROCKS-16-07, -08, -09 pass (unit — model transitions/mutations tested
  headlessly with injected fake services). ✅ 6 tests / 0 failures (incl. staleness-drop §6).

### Phase 4: Detail pane redesign [Must] — [pure-UI / VNC-verified]
- **Goal**: Replace `PackageDetailPanel` with `PackageDetailPane` in the Plugins idiom.
- **Tasks**:
  - [x] Create `net.internetisalie.lunar.rocks.browser.PackageDetailPane` + `DependencyRow` —
    realizes design §2.6 (`JBHtmlPane` description, `JBPanelWithEmptyText` empty card, `JBList`
    deps with click-to-search, inline Install/Uninstall/Update button with progress, Error card
    with Configure link, NoTree card, `CardLayout`). Selection-staleness guard (finding #48) via a
    monotonic `selectionToken` checked in the metadata callback.
  - [x] Delete `PackageDetailPanel`. (Legacy `PackageBrowserPanel` bridged to the new pane + a model
    until Phase 5 replaces it.)
- **Exit criteria**: TC-ROCKS-16-10 passes (unit on `dependencyRows`) ✅ 2 tests / 0 failures.
  Human-verification items (font parity BUG-363, alignment BUG-365, empty state BUG-367, clickable
  deps BUG-368, error/Configure link ROCKS-16-05) DEFERRED to the supervised verify-in-ide pass.

### Phase 5: Two-tab panel + tool-window differentiation [Must] — [pure-UI / VNC-verified]
- **Goal**: `JBTabbedPane` Marketplace/Installed surface, target-tree strip, renamed tool windows.
- **Tasks**:
  - [x] Create `net.internetisalie.lunar.rocks.browser.LuaRocksBrowserPanel` — realizes design §2.7
    (JBTabbedPane Marketplace/Installed, shared PackageDetailPane, target-tree strip, Alarm debounce
    parented to the panel Disposable).
  - [x] Create `net.internetisalie.lunar.rocks.browser.LuaRocksBrowserToolWindowFactory`; delete
    `LuaRocksPackageBrowserToolWindowFactory`; update `plugin.xml` `factoryClass` — realizes design §7.
  - [x] Set stripe titles on both factories (`LuaRocksToolWindowFactory` → "LuaRocks Dependencies";
    browser → "LuaRocks Packages") — realizes design §7 (BUG-366). **Deviation**: `ToolWindow` has no
    `setToolTipText` (design's "tooltips" call does not exist in the 2026.1 SDK — `ToolWindow.java`
    exposes only `setStripeTitle`/`setTitle`/`setHelpId`); role tooltip dropped, differentiation is
    via the distinct stripe titles.
- **Exit criteria**: TC-ROCKS-16-12 stripe/title assertion ✅ 2 tests / 0 failures (via a recording
  proxy — the headless mock ToolWindow no-ops the setters, so titles are asserted at the setter call
  site, not read back from the manager). Live two-tab layout / zero-query Installed tab / stripe
  rendering DEFERRED to the supervised verify-in-ide pass.

### Phase 6: Update affordance [Should] — [pure-UI + logic]
- **Goal**: Surface the Update badge/button.
- **Tasks**:
  - [x] Wire `LuaRocksUpdateDetector` (§3.2) into `LuaRockRow.hasUpdate` (model `buildRows` computes
    it per name for installed rocks) and render the Update badge in the list cell renderer (`⬆`) +
    the detail-pane Update button (installs the latest into the same tree).
- **Exit criteria**: TC-ROCKS-16-06 (update detection) green in Phase 2; model `buildRows` hasUpdate
  wiring covered by 2 new model tests ✅ (8 total / 0 failures). Live badge/button surface DEFERRED
  to the supervised verify-in-ide pass.

### Phase 7: Add-to-rockspec affordance [Should — in scope per DR-05] — [logic]
- **Goal**: "Add to rockspec dependencies" action (owner decision 2026-07-16: build in this
  feature, not a follow-on).
- **Tasks**:
  - [x] Append the installed rock to the discovered rockspec's `dependencies` via
    `RockspecDependencyEditor` (pure text edit) + `LuaRocksRockspecDependencyService` (ROCKS-09
    discovery + `WriteCommandAction` VFS write). Detail pane gains an "Add to rockspec" button shown
    for installed rocks.
- **Exit criteria**: a unit test asserts the rockspec edit ✅ — `RockspecDependencyEditorTest`
  (6, pure) + `LuaRocksRockspecDependencyServiceTest` (2, write under command). 0 failures.

### Phase 8: Popular-packages Marketplace list [Could — ROCKS-16-15] — [logic + UI]
- **Goal**: Populate the Marketplace zero-query view with a scraped "Popular / Trending" list
  instead of the neutral prompt; degrade silently on failure (owner decision 2026-07-16: build in
  this feature as a Could-have). Do this LAST — it must not gate the Must/Should phases.
- **Tasks**:
  - [x] Add `LuaRocksPopularService` (fetches `luarocks.org/stats/this-week` off the EDT via
    `HttpRequests` with a 5 s timeout, TTL-cached 1 h) + a pure `PopularListParser` (derives each
    package name from the row's `/modules/<author>/<name>` link, captures the count, skips
    malformed rows). Any non-200 / empty / unparseable response → empty list, no throw.
  - [x] Zero-query Marketplace → `LuaRocksBrowserModel.loadPopular` renders the list as `Results`
    (click-to-detail via the existing renderer); an empty list falls back to `Idle` (neutral prompt),
    never an error state. Popular fetch is a new `LuaRocksBrowserBackend.fetchPopular` seam.
- **Exit criteria**: TC-ROCKS-16-15a (parser over the static HTML fixture, 3) + TC-ROCKS-16-15b
  (fetch failure → neutral prompt, not the error state — service 4 + model blank-Idle) green ✅.
  Live popular-list render / network-kill fallback DEFERRED to the supervised verify-in-ide pass.

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
- [x] Add `LuaRocksInstallCommandTest` — covers TC-ROCKS-16-01/-02/-03/-04.
- [x] Add `LuaRocksInstalledServiceParseTest` — covers TC-ROCKS-16-05.
- [x] Add `LuaRocksUpdateDetectorTest` — covers TC-ROCKS-16-06.
- [x] Add `LuaRocksBrowserModelTest` (fake services) — covers TC-ROCKS-16-07/-08/-09.
- [x] Extend `LuaRocksSearchServiceParseTest` regression guard — covers TC-ROCKS-16-11.
- [x] Add `PackageDetailPaneDependencyTest` — covers TC-ROCKS-16-10.
- [x] Add/extend an integration check enumerating tool-window ids/titles — covers TC-ROCKS-16-12.
- [ ] Run `human-verification-checklists.md` over the VNC gate (verify-in-ide) — all UI phases
  (4, 5, 6) require live confirmation; unit tests cannot render the tool window.

## Task Summary

| Phase | Status | Priority |
|-------|--------|----------|
| Phase 1: Canonical install/uninstall target | done | Must |
| Phase 2: Installed listing + error model | done | Must |
| Phase 3: Browser state model | done | Must |
| Phase 4: Detail pane redesign | done (unit; VNC deferred) | Must |
| Phase 5: Two-tab panel + tool-window differentiation | done (unit; VNC deferred) | Must |
| Phase 6: Update affordance | done (unit; VNC deferred) | Should |
| Phase 7: Add-to-rockspec affordance | done | Should |
| Phase 8: Popular-packages Marketplace list | done (unit; VNC deferred) | Could |
