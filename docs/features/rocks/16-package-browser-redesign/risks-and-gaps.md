---
id: "ROCKS-16-RISKS"
title: "Risks & Gaps"
type: "risk"
parent_id: "ROCKS-16"
folders:
  - "[[features/rocks/16-package-browser-redesign/requirements|requirements]]"
---

# ROCKS-16: Risks & Gaps

## Critical Risks

### Risk 1.1: `luarocks install --tree <root>` behaves differently than the read-side tree layout
- **Impact**: If `install --tree <root>` writes to a layout that `LuaRocksTreeLocator.installedRocks`
  (which reads `<root>/lib/luarocks/rocks-<X.Y>/…`) does not recognize, installs would still be
  invisible to the plugin — defeating the load-bearing fix (ROCKS-16-02).
- **Likelihood**: low — the scaffolder and `RockspecRunPathProvider` already assume this layout, and
  luarocks' `--tree` is documented to produce exactly `<tree>/lib/luarocks/…`.
- **Mitigation**: DR-01 verifies a real `install --tree` writes a rock that `installedRocks` then
  enumerates, live over VNC, before Phase 4 UI is built.

### Risk 1.2: Threading — CLI capture on a background thread while mutating the model on the EDT
- **Impact**: A slow search overwriting a newer one, or a `capture` accidentally on the EDT →
  `SlowOperationsException`.
- **Likelihood**: medium.
- **Mitigation**: the model's monotonic `requestId` staleness guard (design §6); all `capture`
  calls stay inside `executeOnPooledThread` / `Task.Backgroundable`; `LuaToolExecutionService`
  already soft-asserts a background thread. Unit tests exercise the staleness drop.

### Risk 1.3: Throwing change to `LuaRocksSearchService` breaks non-browser callers
- **Impact**: Making `search`/`installed` throw `BrowserCliError` (design §3.5) could regress a
  caller that relied on the silent-empty contract.
- **Likelihood**: low — grep shows the only current callers are the browser + its tests.
- **Mitigation**: keep `searchOrEmpty`/`installedOrEmpty` wrappers and migrate any caller found to
  the wrapper; DR-02 records the grep result.

## Design Gaps

### Gap 2.1: No zero-query Marketplace catalog — v1 = option (a); popular-list follow-on FEASIBLE via scrape
- **Question**: The Plugins page shows a default Marketplace catalog with no query. `luarocks
  search --all` is huge and slow; luarocks has no first-class "popular/recent" CLI.
- **Decision (owner, 2026-07-16)**: v1 ships option (a) — neutral `Idle` prompt on the Marketplace
  tab; the Installed tab provides zero-query content.
- **Popular-list follow-on — CORRECTED 2026-07-16** (supersedes the earlier "dropped, no API"
  record): luarocks.org exposes **no JSON API** (`?format=json` returns byte-identical HTML;
  `luarocks-site` `applications/api.moon` is upload/auth-only + a public `tool_version`) — BUT the
  popularity **data is published** on two stable, curated pages that are viable to scrape:
  - **`https://luarocks.org/stats/this-week`** — "Top downloaded versions in the past 7 days",
    `<table class="table">`, ~40 rows (e.g. LuaFileSystem 51,572; dkjson 32,931; luassert 32,461).
  - **`https://luarocks.org/stats/dependencies`** — "Top depended-upon modules", `<table
    class="table">`, ~28 rows (e.g. santoku 2,199; LuaSocket 2,040; lua-cjson 1,964).
  Each row carries a `/modules/<author>/<name>` link — a stable per-package key. So the follow-on
  is **feasible via a lightweight HTML scrape** of these two ranking tables (populate the
  Marketplace zero-query view with "Popular / Trending"), *not* impossible for lack of an API. The
  earlier DR-03 note wrongly conflated "no JSON API" with "no usable data" and over-stated the
  scrape fragility — a ~30-row curated ranking table is a far lower-risk scrape than arbitrary
  search-result pages. Caveat: any HTML scrape breaks if luarocks.org changes the markup, so gate
  it behind a graceful "couldn't load popular list" fallback to the neutral prompt.
- **Decision (owner, 2026-07-16)**: build it **in ROCKS-16 as a Could-have** — **ROCKS-16-15**,
  Phase 8 (last, non-gating). Design §3.3a; tests TC-ROCKS-16-15a/b.

### Gap 2.2: Rockspec-dependency-add scope (ROCKS-16-13) — RESOLVED (in scope)
- **Question**: Should install optionally append the rock to the project rockspec's `dependencies`?
- **Decision (owner, 2026-07-16)**: build it **in this feature** (Phase 7); no follow-on split.
  ROCKS-16-13 stays Should priority.

## Technical Debt & Future Work
- **Roadmap placement — RESOLVED**: ROCKS-16 is listed in **Wave 18** of `docs/roadmap.md`
  (owner decision 2026-07-16); the ROCKS epic itself stays `done` (post-hoc redesign, not a reopen).
- **TBD: System/global tree install targets** — v1 targets only the project-local tree
  (`LuaRocksTreeLocator` v1 scope). Multi-tree selection deferred.

## Open Items for Product Owner
All three open items were decided by the product owner on 2026-07-16:
1. **Roadmap placement** → Wave 18 (see Technical Debt above).
2. **ROCKS-16-13** (add-to-rockspec) → in scope, this feature (Gap 2.2).
3. **Zero-query Marketplace catalog** → the neutral prompt is the baseline; the scraped popular list
   (feasible via `/stats/this-week` + `/stats/dependencies`, keyed on `/modules/<author>/<name>`) is
   now **in ROCKS-16 as a Could-have — ROCKS-16-15, Phase 8** (owner, 2026-07-16). No longer a
   separate follow-on (Gap 2.1).

## Epic-table drift (noted, not fixed here)
`docs/features/rocks/requirements.md` has known drift the reviewer should be aware of: ROCKS-14/15
show **Full** but their front-matter is `superseded`; ROCKS-12 shows **Not Implemented** but is
reportedly done. This plan only **adds** the ROCKS-16 row; it deliberately does not rewrite the
drifting rows (out of scope; flagged for a separate table-alignment chore).

## Pre-Implementation De-risking Tasks

| ID | Action | Resolves | Status |
|----|--------|----------|--------|
| ROCKS-16-00-DR-01 | Live: run `luarocks install --tree <proj>/lua_modules inspect`, then confirm `LuaRocksTreeLocator.installedRocks` enumerates it and the library provider shows it (VNC). | Risk 1.1, ROCKS-16-02 | todo |
| ROCKS-16-00-DR-02 | Grep all callers of `LuaRocksSearchService.search`/`installed`; confirm only browser+tests; record the migration list for the wrapper. | Risk 1.3 | todo |
| ROCKS-16-00-DR-03 | Investigate a feasible zero-query Marketplace list (luarocks.org manifest/API); decide option (a) vs (b) for Gap 2.1. | Gap 2.1 | done — no JSON API (`api.moon` upload/auth only), but `/stats/this-week` + `/stats/dependencies` are scrapeable ranked tables → v1 ships option (a); popular-list follow-on is FEASIBLE via scrape, not dropped (corrected 2026-07-16) |
| ROCKS-16-00-DR-04 | Prototype `JBHtmlPane` rendering a `luarocks show` detailed description (HTML sanitation, link handling) in the tool window over VNC. | ROCKS-16-04 | todo |
| ROCKS-16-00-DR-05 | Decide add-to-rockspec scope (build vs defer) with the product owner; if deferred, file the follow-on feature. | Gap 2.2, ROCKS-16-13 | done — in scope, this feature (owner, 2026-07-16) |

## Absorbed codebase-review findings (2026-07-17)

The 2026-07 codebase review ([docs/review.md](../../../review.md); remediation verified
2026-07-17) has four open findings in the code this feature replaces. They are **in scope here**
— the new browser must not reintroduce them; do not file/fix them separately against the old panel:

| Review # | Defect in the old browser | Where it lands here |
|----|----|----|
| #48 | `PackageDetailPanel` async metadata fetch has no staleness guard — a slow response for A overwrites B's details | Detail-pane design: bail in the callback if the selection changed |
| #70 | `LuaRocksSearchCache` keyed on bare query — survives server-setting changes and out-of-band installs | New cache keys on resolved server; invalidate on settings change/install |
| #71b | `runSearch` has no stale-result guard (the Alarm-parent half was fixed as BUG-379) | Same staleness rule as #48 for the results list |
| #64 | `LuaRocksActionHandler` KDoc promises `onDone` on EDT but runs it on the task thread | Fix the contract (or the doc) when the handler is reworked for inline install |

## Test Case Gaps
- **Live install visibility** (Risk 1.1) is not unit-testable (requires a real `luarocks`); covered
  by DR-01 + the human-verification checklist, not an automated TC.
- **`JBHtmlPane` rendering fidelity** is VNC-only (DR-04); no headless assertion.

## See Also
- Requirements: [requirements.md](requirements.md)
- Design: [design.md](design.md)
- Implementation Plan: [implementation-plan.md](implementation-plan.md)
