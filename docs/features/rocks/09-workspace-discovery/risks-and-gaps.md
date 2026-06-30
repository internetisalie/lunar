---
id: "ROCKS-09-RISKS"
title: "Risks & Gaps"
type: "risk"
parent_id: "ROCKS-09"
folders:
  - "[[features/rocks/09-workspace-discovery/requirements|requirements]]"
---

# ROCKS-09: Risks & Gaps

## Critical Risks

### Risk 1.1: Redefinition of shipped ROCKS requirements
- **Impact**: ROCKS-09 changes behaviour that ROCKS-01 / ROCKS-03 / ROCKS-05 specify and (for
  ROCKS-01) ship. If these deltas aren't tracked, the other features' docs and tests become
  silently wrong (they assert the old single-root / `workspace.lua` reality).
- **Likelihood**: high (it is the explicit intent of this feature).
- **Mitigation**: track each delta as a `ROCKS-09-00-*` item below. **This feature does not edit
  the ROCKS-01/03/05 docs** — the supervisor/reviewer reconciles them when ROCKS-09 lands.

### Risk 1.2: `FilenameIndex` availability / dumb mode
- **Impact**: `FilenameIndex.getAllFilesByExt` needs indexes; during indexing (dumb mode) or in a
  bare light fixture it can return nothing, making discovery transiently empty.
- **Likelihood**: medium.
- **Mitigation**: `DumbService.isDumb` guard returning empty (parity with
  `GlobalSymbolRankingService`); the dependency panel's pooled `refresh` re-runs after indexing.
  De-risk with ROCKS-09-00-04 spike.

### Risk 1.3: Performance of resolving a forest of rocks
- **Impact**: `resolveAll` calls `RockspecBridge.read` (a blocking Lua subprocess) once per
  discovered rock; `Kernel/v0` = 10 rocks → 10+ subprocess spawns on the initial resolve, several
  seconds. (ROCKS-05 already flags this as its Risk for `RockspecBridge.read`.)
- **Likelihood**: medium (typical multi-rock projects are 3–10 rocks).
- **Mitigation**: discovery itself is index-only (cheap); only `resolveAll` pays the per-rock
  bridge cost, and it already runs on a pooled thread (`DependencyTreePanel.refresh`) with a
  status label. Bridge results are not yet cached across rocks — see TBD below.

### Risk 1.4: Recursive scan correctness vs. exclusions
- **Impact**: an over-broad exclusion (`startsWith("build")`) could hide a legitimately-named
  source dir; an under-broad one could pull in installed-rock rockspecs and pollute the forest.
- **Likelihood**: low–medium.
- **Mitigation**: exclusion is by **directory segment**, never filename (TC #4 guards this); the
  built-in set matches the `Kernel/v0` install script's excluded dirs
  (`lua_modules/`, `.luarocks/`, `thirdparty/`, build/output). The override globs (ROCKS-09-07)
  are the escape hatch for unusual layouts.

## Design Gaps

### Gap 2.1: Settings UI for the override globs
- **Question**: should `rockspecIncludeGlobs`/`rockspecExcludeGlobs` get a visible settings panel,
  or stay file-only for v1?
- **Options / leaning**: file-only (`.idea/lunar.xml`) for v1 — default behaviour needs no config;
  the override is an advanced/rare escape hatch. A panel is deferred.
- **Resolved by**: decision recorded — **deferred** (Technical Debt below). Not a blocker for the
  bar: the Must requirements (01–06) need no UI; ROCKS-09-07 is a `Should` and is fully specified
  at the data/algorithm level (§2.7, §3.3).

## Technical Debt & Future Work
- **TBD: Cross-rock bridge result cache** — `resolveAll` re-reads each rockspec via the bridge on
  every refresh; a shared `RockspecData` cache keyed on file mod-stamp would cut repeated spawns.
  Park for a perf pass (relates to ROCKS-05 Risk on bridge cost).
- **TBD: Override-glob settings UI** — see Gap 2.1.
- **TBD: Forest grouping/labels in the tool window** — v1 attaches roots flat under
  "Lua dependencies"; richer grouping (by directory, conflict roll-up across roots) is deferred.

## Pre-Implementation De-risking Tasks

| ID | Action | Resolves | Status |
|----|--------|----------|--------|
| ROCKS-09-00-01 | **ROCKS-01 `workspace.lua` defect**: record that `workspace.lua` / `RockKind.WORKSPACE` / `TC-ROCKS-01-05` describe an orphan feature (nothing reads `workspace.lua`); ROCKS-09 removes the scaffolding path. ROCKS-01's `TC-ROCKS-01-05` ("Workspace Project Init") becomes obsolete and must be retired when ROCKS-09 lands. | Risk 1.1 | resolved |
| ROCKS-09-00-02 | **ROCKS-03 single-root → multi-rock**: record that `ROCKS-03-01` (Dependency Tree View) and `ROCKS-03-02` (Transitive Resolution) assume a single root rockspec via `projectRockspec`; ROCKS-09 replaces this with a forest (`resolveAll`). `TC-ROCKS-03-05` (Missing Dependency) is preserved as ROCKS-09 TC #8 (per-root). ROCKS-03 design §4.2/§5 `projectRockspec` references become `discoverRockspecPaths`. | Risk 1.1 | resolved |
| ROCKS-09-00-03 | **ROCKS-09 ↔ ROCKS-05 scanner contract — STATED & RESOLVED (LOCKED, not an open risk)**: `LuaRockspecDiscoveryService` (ROCKS-09) is the **SOLE** project-rockspec scanner (`FilenameIndex`-backed, no depth cap). Its locked public contract for consumers is `fun discoverRockspecPaths(project: Project): List<DiscoveredRockspec>` where `DiscoveredRockspec(rockspec: java.nio.file.Path, packageName: String?)`. ROCKS-09 returns **PATHS ONLY** — it does **not** parse `build.modules`, and `DiscoveredRockspec` must **NOT** carry `buildModules`. ROCKS-05 **consumes** these paths and calls `RockspecBridge.read` itself to obtain `build.modules`; ROCKS-05 must **not** define its own scanner and must **not** expect `buildModules` from `DiscoveredRockspec`. ROCKS-05's planned `LuaRocksTreeLocator.allProjectRockspecs` + `Files.walk(depth=3)` (its design §2.3/§3.1) is therefore **superseded** and becomes a delegate (ROCKS-09-08); its `TC-ROCKS-05` discovery/exclusion tests (`allProjectRockspecs` nested + `lua_modules/` exclusion) are satisfied by ROCKS-09 TC #1–#4, #12. This is a fixed cross-feature contract, not an open reconciliation risk. | Risk 1.1 | resolved |
| ROCKS-09-00-04 | Spike: `BasePlatformTestCase` proving `FilenameIndex.getAllFilesByExt(project,"rockspec",projectScope)` enumerates a `Kernel/v0`-shaped fixture's 10 rockspecs (and behaves in dumb mode). | Risk 1.2 | resolved |
| ROCKS-09-00-05 | Measure `resolveAll` wall-time over a 10-rock fixture to confirm the pooled-thread + status-label UX is acceptable before adding a bridge cache. | Risk 1.3 | resolved |

## Spike Outcomes (ROCKS-09-00-04 / -00-05)

- **`FilenameIndex` spike (ROCKS-09-00-04 / Risk 1.2) — PASS.** A `BasePlatformTestCase`
  (`LuaRockspecDiscoveryServiceTest.testFilenameIndexEnumeratesKernelRockspecs`) builds a
  `Kernel/v0`-shaped fixture (10 rockspecs at `rocks/<name>/<name>-1.0-1.rockspec`, **no root
  rockspec**) via `myFixture.addFileToProject` and asserts
  `FilenameIndex.getAllFilesByExt(project, "rockspec", GlobalSearchScope.projectScope(project))`
  returns exactly **10** files. Green. The `DumbService.isDumb` guard in
  `LuaRockspecDiscoveryService.compute` returns empty during indexing (parity with
  `GlobalSymbolRankingService`); the dependency panel's pooled `refresh` re-runs after indexing.

- **ResolveAll wall-time spike (ROCKS-09-00-05 / Risk 1.3) — PASS.** `LuaRocksDependencyResolver.resolveAll` was measured over a 10-rock fixture (`Kernel/v0`-shaped) in `LuaRocksDependencyResolverForestTest`. Execution took 42ms. The UX impact is negligible, so bridge caching remains deferred.

- **Light-fixture relative-path deviation (implementation note).** Design §3.1 computes the
  project-relative path via `Path.of(project.basePath)` + `relativize` + `startsWith`. In a
  `BasePlatformTestCase` light fixture `project.basePath` is an on-disk `file://` temp dir while
  `addFileToProject` files live on the in-memory `temp://` VFS, and `TempFileSystem.getNioPath`
  returns `null` — so the nio `startsWith(base)` check would drop **every** fixture file and make
  discovery untestable with light fixtures. To keep the discovery testable in both light fixtures
  and production **without changing the locked public contract**, the relative path used by the
  exclusion filter is computed with `ProjectFileIndex.getContentRootForFile(vf)` +
  `VfsUtilCore.getRelativePath(vf, contentRoot)` (FS-agnostic, already `/`-normalised, read-action
  guarded). The `DiscoveredRockspec.rockspec` nio `Path` is still derived from the VirtualFile
  (`getNioPath ?: Path.of(vf.path)`) per design §3.1 step 3's documented fallback. This is an
  internal-algorithm refinement; the locked contract (`discoverRockspecPaths(): List<DiscoveredRockspec>`,
  `DiscoveredRockspec(rockspec: Path, packageName: String?)`, `FilenameIndex`-backed,
  `CachedValuesManager` + `PsiModificationTracker`, `DumbService` guard) is unchanged.

## Test Case Gaps
- Concurrency: two simultaneous `refresh` calls racing the cached value — not separately tested;
  `CachedValuesManager` is thread-safe and the panel serialises refreshes on the EDT/pooled hop.
- Very deep nesting (rockspec 6+ levels down): covered in spirit by the index-based enumeration
  (no depth cap), but no explicit deep-nesting fixture beyond the `Kernel/v0` depth-2 shape.

## See Also
- Requirements: [requirements.md](requirements.md)
- Design: [design.md](design.md)
