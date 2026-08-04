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
  - [x] Create `net.internetisalie.lunar.definitions.LuaDefinitionCatalog` + `LuaDefinitionEntry` — realizes design §2.1.
  - [x] Create `net.internetisalie.lunar.definitions.LuaDefinitionCatalogLoader` (parse-once cache, explicit field validation, `LuaProvisionException` on corruption) — realizes design §2.2, §3.1.
  - [x] Add bundled resource `src/main/resources/definitions/lunar-definitions-catalog.json` with the v1 curated set (data from DR-01/DR-02) — realizes design §4.1.
- **Exit criteria**: `LuaDefinitionCatalogLoader.load()` returns the catalog with a `love2d` entry; a JSON missing `sha256` throws `LuaProvisionException` (TC 1, 2).

### Phase 2: Per-project enable list [Must]
- **Goal**: persist and read the enabled ids.
- **Tasks**:
  - [x] Add `enabledDefinitionLibraries: MutableList<String>` to `LuaProjectSettings.State`; add `setEnabledDefinitionLibrariesAndRefresh(ids)` to `LuaProjectSettings` (calls `PlatformLibraryIndex.reload()` + `PsiManager.dropResolveCaches()` via `invokeLater`) — realizes design §2.5, §3.3.
- **Exit criteria**: setting the list and re-reading it round-trips through `lunar.xml` in a `BasePlatformTestCase` (TC 3).

### Phase 3: Fetcher (download + extract + cache) [Must]
- **Goal**: resolve/fetch an entry's on-disk cache off-EDT.
- **Tasks**:
  - [x] Create `net.internetisalie.lunar.definitions.LuaDefinitionLibraryFetcher` (`cacheDir`/`isCached`/`ensureCached`), injecting `LuaArtifactDownloader` + `cacheRoot`; reuse `LuaArchiveExtractor`; error → balloon on `notification.group.lunar.tools` — realizes design §2.3, §3.2.
- **Exit criteria**: a pre-seeded cache dir is returned with zero downloader calls (TC 4); a throwing downloader yields `null` + no cache dir (TC 8).
- **Amended 2026-08-03 — the balloon is NOT this phase's.** This task text said "error → balloon on
  `notification.group.lunar.tools`", contradicting design §3.2 (review N3), which deliberately
  reassigns user-facing reporting to the *caller* so the fetcher stays a pure, testable function.
  The design wins: `ensureCached` returns `null` and logs; Phase 5's `apply()` balloons.
  **TARGET-08-07 is therefore Partial after Phase 3** — its detection half is done, its
  notification half lands with the settings UI.
- **Amended — seam, not a concrete downloader.** The task text named a `LuaArtifactDownloader`
  constructor injection (design §2.3 orders it `downloader, cacheRoot`). Implemented instead as
  `LuaDefinitionLibraryFetcher(cacheRoot, source: LuaDefinitionArchiveSource)`: injecting the
  concrete class would have required opening a final, security-relevant shared class purely to be
  mocked. The seam keeps it final and the production path still routes through it.

### Phase 4: Library-root provider + registration [Must]
- **Goal**: expose enabled+cached trees as `SyntheticLibrary` roots so `@meta` defs are indexed.
- **Tasks**:
  - [x] Create `net.internetisalie.lunar.definitions.LuaDefinitionLibraryProvider` (`AdditionalLibraryRootsProvider`, `getAdditionalProjectLibraries`, `getRootsToWatch`, inner `DefinitionLibrary : SyntheticLibrary`) — realizes design §2.4, §3.5.
  - [x] Register `<additionalLibraryRootsProvider>` in `plugin.xml` — realizes design §7.
- **Exit criteria**: with `busted` enabled + a pre-seeded `@meta` file, the provider returns one `SyntheticLibrary` over the cache dir (TC 5), and no enabled libraries → empty (TC 7b); enable-list change triggers `reload()` + `dropResolveCaches()` (TC 7). **TC 6 is not met** — see the amendment below.
- **Amended 2026-08-03 — TC 6 is NOT met; TARGET-08-04 is Partial, not Full.** DR-03 proved a
  registered root is indexed and a project-file reference *resolves* into it. Completion does not
  work, for two **pre-existing** reasons outside this feature, which an earlier revision of this
  note wrongly merged into one:
  - **BUG-395 blocks TC 6.** TC 6 is `assert.` — *member* completion. That caret is served only by
    `LuaCompletionContributor.kt:249-283`, reading `LuaTypesSnapshot…getMembers()`; it never
    consults `GlobalSymbolRankingService`, and the member path is structurally file-local
    (`LuaTypesVisitor.visitNameRef:761`). `string.` does not complete either.
  - **BUG-394** is the separate bare-identifier case: `GlobalSymbolRankingService` searches
    `GlobalSearchScope.projectScope` (`:110`, `:180`), which excludes library files. Real, and
    confirmed to affect `print` too — but **widening that scope would not have made TC 6 pass**.
  Both are carved out rather than fixed here: each changes completion behaviour project-wide.
- **Amended 2026-08-04 — BUG-395 and BUG-398 are fixed; TC 6 re-run live and still fails, now on
  BUG-399.** What the VNC run established, in order:
  - The member path works in a real IDE. `table.` completes `concat/insert/move/pack/remove/sort/
    unpack` with signatures, where before the fix it completed nothing at all.
  - The library roots are registered and indexed: both appear under *External Libraries ▸ Lua
    Definition Libraries*, `luassert.lua` is findable in Search Everywhere, and **Ctrl+B on `assert`
    offers two declarations** — the stdlib `function assert(v, message)` and busted's
    `assert = require("luassert")`. So `resolveGlobal` reaches the library.
  - It breaks one step later: `require("luassert")` in a project file is red-squiggled unresolved
    (`require("main")` beside it is not), and `local la = require("luassert")` then `la.` completes
    nothing. `doResolveModule` falls back to `ANY`, `globalTypeIn` discards `Any`, `assert.` gets
    nothing. Filed as **BUG-399**, with a second blocker behind it: `doResolveType` /
    `collectMethodMembers` are `projectScope`-bound, so a library `---@class` cannot materialize
    even once the module resolves.
  - Chase BUG-399 on the DR-03 harness, not over VNC — a 5-minute screenshot round-trip is the wrong
    loop for a scope question that a light fixture with a real registered root can answer directly.
- **Amended 2026-08-04 (same day) — BUG-399 fixed; TC 6 PASSES live. TARGET-08-04 is Full.**
  Moving to the harness was the right call and immediately corrected two misreadings from the VNC
  pass: `require` into a library root **does** resolve (the harness test passes first try), and the
  red squiggle / Ctrl+B miss were not evidence — Ctrl+B missed on a `require("main")` that resolves
  fine, so that signal was worthless. The real defect was the second one, read out of the code
  rather than the screen: `doResolveType` and `collectMethodMembers` were `projectScope`-bound, so a
  library `---@class` never materialized. Both now use `allScope`. Re-verified live: `assert.`
  completes `is_true`, `are_equal`, `are_same`, `add_formatter` and the rest of luassert's API,
  with signatures, and the session log shows no plugin stack traces and no slow-op violations.
  **The standing lesson:** a light fixture's project is entirely in project scope, so it cannot
  observe a projectScope-vs-allScope defect at all. Any feature whose value depends on library
  content needs a registered-root test (`LuaLibraryModuleResolutionTest`) — three green unit suites
  said this worked when it did not.

### Phase 5: Settings UI [Should]
- **Goal**: enable/disable + attribution UI.
- **Tasks**:
  - [x] Create `net.internetisalie.lunar.definitions.ui.LuaDefinitionLibrariesConfigurable` (+ panel) with a per-row checkbox, status, license, attribution `HyperlinkLabel`; `apply()` persists + dispatches fetch off-EDT via `newProjectBackgroundTask` — realizes design §2.6, §3.4.
  - [x] Register `<projectConfigurable>` under `LuaProjectConfigurable` in `plugin.xml` — realizes design §7.
- **Exit criteria**: the settings page lists the catalog with checkboxes, status, license, and a working attribution link (TC 9); toggling a checkbox fetches off-EDT and refreshes roots.
- **Amended 2026-08-03 — logic lives outside Swing.** All page behaviour is in
  `LuaDefinitionLibraryEnabler` (rows, apply, fetch dispatch, failure reporting), unit-tested
  headlessly; the `Configurable` only builds components and reads checkbox state. Balloons go
  through design §3.2's `LuaProvisionNotifier` seam rather than `NotificationGroupManager` directly,
  which is what makes them assertable — `testApplyBalloonsOnFailure` was mutation-checked (removing
  the report call turns it red).
- **STILL OUTSTANDING for a `done` status** — neither is code:
  1. **No live verification.** `human-verification-checklists.md` is entirely `⬜` and this is an
     inherently visual requirement (the roadmap's DoD gate wants a real-flow run for anything
     surfacing through a platform extension point). Run the `verify-in-ide` skill.
  2. **The online-fetch DR spike has not been run** — one end-to-end download → verify → extract →
     register → resolve against the real network. Every automated test here pre-seeds the cache or
     injects a throwing source, deliberately, so nothing has exercised the live path once.

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
