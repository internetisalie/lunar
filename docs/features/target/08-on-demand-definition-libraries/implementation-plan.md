---
id: TARGET-08-PLAN
parent_id: TARGET-08
type: plan
folders:
  - "[[features/target/08-on-demand-definition-libraries/requirements|requirements]]"
title: "Implementation Plan"
---

# TARGET-08: Implementation Plan

Sequenced from [design.md](design.md). Each phase leaves the build green and is independently testable. Preconditions: TARGET-04 (`AdditionalLibraryRootsProvider` + `PlatformLibraryIndex.reload()`) and the TOOLING download/extract utilities are present (DONE).

## Phases

### Phase 1: Catalog model, loader & bundled data [Must]
- **Goal**: parse the bundled catalog into a validated model.
- **Tasks**:
  - [ ] Create `net.internetisalie.lunar.definitions.LuaDefinitionCatalog` + `LuaDefinitionEntry` — realizes design §2.1.
  - [ ] Create `net.internetisalie.lunar.definitions.LuaDefinitionCatalogLoader` (parse-once cache, explicit field validation, `LuaProvisionException` on corruption) — realizes design §2.2, §3.1.
  - [ ] Add bundled resource `src/main/resources/definitions/lunar-definitions-catalog.json` with the v1 curated set (data from DR-01/DR-02) — realizes design §4.1.
- **Exit criteria**: `LuaDefinitionCatalogLoader.load()` returns the catalog with a `love2d` entry; a JSON missing `sha256` throws `LuaProvisionException` (TC 1, 2).

### Phase 2: Per-project enable list [Must]
- **Goal**: persist and read the enabled ids.
- **Tasks**:
  - [ ] Add `enabledDefinitionLibraries: MutableList<String>` to `LuaProjectSettings.State`; add `setEnabledDefinitionLibrariesAndRefresh(ids)` to `LuaProjectSettings` (calls `PlatformLibraryIndex.reload()` + `PsiManager.dropResolveCaches()` via `invokeLater`) — realizes design §2.5, §3.3.
- **Exit criteria**: setting the list and re-reading it round-trips through `lunar.xml` in a `BasePlatformTestCase` (TC 3).

### Phase 3: Fetcher (download + extract + cache) [Must]
- **Goal**: resolve/fetch an entry's on-disk cache off-EDT.
- **Tasks**:
  - [ ] Create `net.internetisalie.lunar.definitions.LuaDefinitionLibraryFetcher` (`cacheDir`/`isCached`/`ensureCached`), injecting `LuaArtifactDownloader` + `cacheRoot`; reuse `LuaArchiveExtractor`; error → balloon on `notification.group.lunar.tools` — realizes design §2.3, §3.2.
- **Exit criteria**: a pre-seeded cache dir is returned with zero downloader calls (TC 4); a throwing downloader yields `null` + no cache dir + an error balloon request (TC 8).

### Phase 4: Library-root provider + registration [Must]
- **Goal**: expose enabled+cached trees as `SyntheticLibrary` roots so `@meta` defs are indexed.
- **Tasks**:
  - [ ] Create `net.internetisalie.lunar.definitions.LuaDefinitionLibraryProvider` (`AdditionalLibraryRootsProvider`, `getAdditionalProjectLibraries`, `getRootsToWatch`, inner `DefinitionLibrary : SyntheticLibrary`) — realizes design §2.4, §3.5.
  - [ ] Register `<additionalLibraryRootsProvider>` in `plugin.xml` — realizes design §7.
- **Exit criteria**: with `busted` enabled + a pre-seeded `@meta` file, the provider returns one `SyntheticLibrary` over the cache dir (TC 5), completion resolves a busted symbol (TC 6), and no enabled libraries → empty (TC 7b); enable-list change triggers `reload()` + `dropResolveCaches()` (TC 7).

### Phase 5: Settings UI [Should]
- **Goal**: enable/disable + attribution UI.
- **Tasks**:
  - [ ] Create `net.internetisalie.lunar.definitions.ui.LuaDefinitionLibrariesConfigurable` (+ panel) with a per-row checkbox, status, license, attribution `HyperlinkLabel`; `apply()` persists + dispatches fetch off-EDT via `newProjectBackgroundTask` — realizes design §2.6, §3.4.
  - [ ] Register `<projectConfigurable>` under `LuaProjectConfigurable` in `plugin.xml` — realizes design §7.
- **Exit criteria**: the settings page lists the catalog with checkboxes, status, license, and a working attribution link (TC 9); toggling a checkbox fetches off-EDT and refreshes roots.

## Requirement → Phase Coverage

| Requirement | Priority | Delivered in |
|-------------|----------|--------------|
| TARGET-08-01 | M | Phase 1 |
| TARGET-08-02 | M | Phase 2 |
| TARGET-08-03 | M | Phase 3 |
| TARGET-08-04 | M | Phase 4 |
| TARGET-08-05 | M | Phase 2, Phase 4 |
| TARGET-08-06 | S | Phase 5 |
| TARGET-08-07 | M | Phase 3 |
| TARGET-08-08 | S | Phase 5 |

## Verification Tasks
- [ ] Unit: `LuaDefinitionCatalogLoaderTest` — valid load + corrupt-field failure (TC 1, 2).
- [ ] Unit: `LuaProjectSettingsTest` extension — enable-list round-trip (TC 3).
- [ ] Unit: `LuaDefinitionLibraryFetcherTest` — cached-hit no-network (spy downloader, TC 4); failure → null + balloon (TC 8).
- [ ] Light fixture: `LuaDefinitionLibraryProviderTest` — provider returns root for pre-seeded cache (TC 5); empty when none enabled (TC 7b).
- [ ] Light fixture: `LuaDefinitionCompletionTest` — `myFixture.configureByText` + pre-seeded busted `@meta` root → completion contains the busted symbol (TC 6); enable-list change refresh (TC 7).
- [ ] DR-03 spike: real online fetch of one catalog entry (busted) against the live URL — verifies download+extract+register end to end (not a unit test; run once).
- [ ] Run [human-verification-checklists.md](human-verification-checklists.md) (VNC real-flow DoD with love2d).

## Task Summary

| Phase | Status | Priority |
|-------|--------|----------|
| Phase 1: Catalog model, loader & bundled data | todo | Must |
| Phase 2: Per-project enable list | todo | Must |
| Phase 3: Fetcher (download + extract + cache) | todo | Must |
| Phase 4: Library-root provider + registration | todo | Must |
| Phase 5: Settings UI | todo | Should |
