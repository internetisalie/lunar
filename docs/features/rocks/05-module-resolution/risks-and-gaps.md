---
id: "ROCKS-05-RISKS"
title: "Risks & Gaps"
type: "risk"
parent_id: "ROCKS-05"
folders:
  - "[[features/rocks/05-module-resolution/requirements|requirements]]"
---

# ROCKS-05: Risks & Gaps

## Critical Risks

### Risk 1.1: Bridge `build.modules` export (verification DR)
- **Impact**: derivation depends on the bridge actually emitting `build` in its JSON. `build` is
  in the export field list ([rockspec.lua:27](../../../../src/main/resources/lua/rockspec.lua))
  and serialised via `require("lunar.export").json`, but the exact nesting of `build.modules`
  (string vs array entry values) is only asserted on the Kotlin side once
  `RockspecBridge.parse()` handles it (design section 4.1).
- **Likelihood**: low — `build` is already exported; only the Kotlin parse is new.
- **Mitigation**: DR-01 verifies the real JSON shape against a fixture rockspec before Phase 2;
  `RockspecBridgeTest` (TC #1-#3) locks it as a regression.

### Risk 1.2: `RockspecBridge.read` cost / cold-cache on EDT
- **Impact**: `read` spawns a Lua subprocess per rockspec; a 10-rock project pays N spawns on the
  first `derivedPatterns()` compute. If first access is on the EDT it could block.
- **Likelihood**: medium.
- **Mitigation**: results are cached behind `CachedValuesManager` (one compute until a rockspec
  changes). The provider computes off-EDT (resolution/indexing/run construction are off-EDT) and,
  on an EDT cold hit, returns empty + schedules a background prime (design section 6, DR-02) — same
  shape as `GlobalSymbolRankingService`. Shares the bridge-cache TBD with ROCKS-09 risks-and-gaps.

### Risk 1.3 (R2): LUA_INIT collision avoided by setting LUA_PATH directly
- **Impact**: the debug path already injects `ENV_LUA_INIT` for the mobdebug preloader
  ([LuaRunConfiguration.kt:259](../../../../src/main/kotlin/net/internetisalie/lunar/run/LuaRunConfiguration.kt)).
  Injecting rockspec paths through `LUA_INIT` would clobber that preloader.
- **Likelihood**: would be high if `LUA_INIT` were used.
- **Mitigation**: ROCKS-05 sets `LUA_PATH` / `LUA_CPATH` **directly** in the `else` branch and
  never touches `ENV_LUA_INIT` / `ENV_LUNAR_LUA_PATH_TEMPLATE` (design section 2.4, Alternatives).

## Design Gaps

### Gap 2.1: Non-builtin build types and `copy_directories`
- **Question**: `build.type = "make"`/`"cmake"` rocks, and `build.copy_directories` (assets), do
  not expose a clean `build.modules` source map; how are their sources rooted?
- **Options / leaning**: out of scope for v1 — non-builtin -> no derived roots (requirements
  Out of Scope); the user's own source-path settings still apply. `copy_directories` deferred.
- **Resolved by**: decision recorded — **deferred** (Technical Debt). Not a blocker: the Musts
  (01-04) are fully specified for builtin module tables.

### Gap 2.2: C-module CPATH built-tree assumptions
- **Question**: `luaCPath` assumes the built tree is at `LuaRocksTreeLocator.treeRoot` and the
  module lands at `lib/lua/<X.Y>/?.so`. Is `.so` the right suffix on all targets?
- **Options / leaning**: `.so` is correct on Linux/macOS (Lunar's container/test targets);
  Windows `.dll` is out of v1 scope. `hasCModules` is only set for builtin entries, so non-builtin
  C is not mapped.
- **Resolved by**: DR-03 confirms the built-tree layout on a C-module rock; Windows `.dll`
  deferred (Technical Debt).

## Technical Debt & Future Work
- **TBD: Cross-rock bridge result cache** — shared `RockspecData` cache keyed on file mod-stamp,
  shared with ROCKS-09's same TBD, to cut repeated subprocess spawns.
- **TBD: `copy_directories` / non-builtin source roots** — see Gap 2.1.
- **TBD: Windows `.dll` C-module CPATH** — see Gap 2.2.

## Cross-Feature Sequencing Dependency

ROCKS-05 depends on ROCKS-09's `LuaRockspecDiscoveryService.discoverRockspecPaths(project):
List<DiscoveredRockspec>` (`DiscoveredRockspec(rockspec: java.nio.file.Path, packageName:
String?)`, **paths only**, no `buildModules`). This is a **stated, locked contract**
(ROCKS-09 risks-and-gaps ROCKS-09-00-03, marked *resolved*), not an open reconciliation risk.
ROCKS-05 builds **no** scanner. Until ROCKS-09 lands, a clearly-labelled **TEST-ONLY** stub of the
discovery seam substitutes in unit tests (never the production path); production always calls
`LuaRockspecDiscoveryService`.

## Pre-Implementation De-risking Tasks

| ID | Action | Resolves | Status |
|----|--------|----------|--------|
| ROCKS-05-00-DR-01 | Run the bridge over a fixture rockspec with `build.modules` (Lua + C entries); confirm the JSON nesting matches design section 4.1. | Risk 1.1 | done |
| ROCKS-05-00-DR-02 | Spike: confirm `derivedPatterns()` computes off-EDT and the EDT cold-cache prime (background `ReadAction.nonBlocking`) returns warm on the next access without `SlowOperationsException`. | Risk 1.2 | done |
| ROCKS-05-00-DR-03 | On a C-module rock, confirm the built tree exposes `<treeRoot>/lib/lua/<X.Y>/<name>.so` so `LUA_CPATH = "<treeRoot>/lib/lua/<X.Y>/?.so;;"` binds at runtime. | Gap 2.2 | done |

## Test Case Gaps
- Mixed Lua+C rock in one rockspec (Lua -> LUA_PATH, C -> LUA_CPATH simultaneously): covered in
  spirit by TC #1+#8 but no single combined fixture; add in Phase 5 e2e.
- Per-config `sourcePath` set (the `if` branch): explicitly untouched by ROCKS-05; no new TC, but
  a guard test asserting the `if` branch is unchanged is advisable.

## See Also
- Requirements: [requirements.md](requirements.md)
- Design: [design.md](design.md)
- ROCKS-09 contract: [../09-workspace-discovery/risks-and-gaps.md](../09-workspace-discovery/risks-and-gaps.md)
