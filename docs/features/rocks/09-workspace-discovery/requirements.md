---
id: "ROCKS-09"
title: "09: Multi-Rock Workspace Discovery"
type: "feature"
status: "in_progress"
priority: "high"
parent_id: "ROCKS"
folders:
  - "[[features/rocks/requirements|requirements]]"
---

# ROCKS-09: Multi-Rock Workspace Discovery

## Overview

Lunar's LuaRocks intelligence (dependency tree, future module resolution) is currently blind
to real multi-rock projects: rockspec discovery is single-root and non-recursive, so a project
whose rockspecs live under `rocks/<name>/<name>-*.rockspec` (with no root rockspec) resolves
**nothing**. This feature replaces single-root discovery with **recursive multi-rockspec
discovery** that excludes install/build/vendored trees, exposes the discovered rock set as the
canonical project-level source of truth, and makes "multi-rock" a *discovered* property rather
than a scaffolded "workspace" mode. Parent epic: [ROCKS](../requirements.md).

## Scope

### In Scope

- Recursive discovery of **every** source `.rockspec` in the project tree, excluding install,
  build, and vendored directories (`lua_modules/`, `.luarocks/`, `build*/`, `output/`,
  `thirdparty/`).
- A project-level discovery service that returns the discovered rock set (rockspec path +
  parsed package identity), cached and invalidated on VFS/PSI change.
- An optional per-project membership override (include/exclude globs) stored on the existing
  `LuaProjectSettings` state (persisted in `.idea/lunar.xml`); default behaviour requires no
  configuration.
- Updating `LuaRocksDependencyResolver.resolve` to resolve **each** discovered rock (returning
  a forest of roots) instead of a single root rockspec.
- Removal of the orphan `workspace.lua` scaffolding path: delete
  `LuaRocksTemplates.workspaceLua`, `LuaRocksScaffolder.scaffoldWorkspace`, collapse
  `enum RockKind { SINGLE_ROCK, WORKSPACE }`, and drop the corresponding generator-peer UI.
- Exposing the discovered rockspec set for sibling feature ROCKS-05 to consume (its
  `allProjectRockspecs` becomes a thin delegate to this service).

### Out of Scope

- `require()` resolution / source-root derivation itself — that is **ROCKS-05**; ROCKS-09 only
  provides the discovered set ROCKS-05 reads.
- System/global rock trees and manifest parsing — deferred (ROCKS-03-G-01 / ROCKS-03-DR-03).
- The dependency-tree tool-window UI redesign for forests (this feature renders each root; rich
  multi-root grouping is deferred — see risks-and-gaps.md).
- Reintroducing any first-class "workspace" concept or a replacement manifest file.

## Functional Requirements

| ID | Requirement | Priority | Description |
|----|-------------|----------|-------------|
| ROCKS-09-01 | **Recursive Rockspec Discovery** | M | Discover every `.rockspec` under the project base path, at any depth, not just the project root. |
| ROCKS-09-02 | **Install/Build/Vendor Exclusion** | M | Exclude any rockspec whose path contains an excluded directory segment (`lua_modules`, `.luarocks`, `build*`, `output`, `thirdparty`). |
| ROCKS-09-03 | **Discovered Rock Set API** | M | Expose a project-level service returning the discovered rocks (rockspec path + package name) as the canonical source of truth. |
| ROCKS-09-04 | **Cached, Invalidated Discovery** | M | Discovery is cached per project and must not re-walk/re-index on every call; it invalidates on VFS structure / PSI change. |
| ROCKS-09-05 | **Multi-Root Dependency Resolution** | M | `LuaRocksDependencyResolver.resolve` resolves a dependency graph for **each** discovered rock (a forest), independent of a root rockspec. |
| ROCKS-09-06 | **Remove `workspace.lua` Scaffolding** | M | Delete the orphan `workspace.lua` template/scaffolder path and collapse `RockKind` to a single-rock generator. |
| ROCKS-09-07 | **Membership Override** | S | Allow per-project include/exclude glob overrides on `LuaProjectSettings`; default = recursive-minus-excludes. |
| ROCKS-09-08 | **ROCKS-05 Delegation Hook** | S | Provide the discovered rockspec list in the shape ROCKS-05's `allProjectRockspecs` needs, so ROCKS-05 delegates rather than re-walks. |

## Detailed Specifications

### ROCKS-09-01: Recursive Rockspec Discovery

Enumerate all files named `*.rockspec` within `project.basePath` at **any depth** (not capped
at the root directory and not capped at a hand-rolled depth limit — see design §3.1 for why an
index-backed enumeration is preferred over a `nio` walk). The `Kernel/v0` shape — 10 rockspecs
at `rocks/<name>/<name>-1.0-1.rockspec`, **no root rockspec** — must yield all 10.

### ROCKS-09-02: Install/Build/Vendor Exclusion

A discovered rockspec is **excluded** if any path segment between the project base and the file
(case-insensitive) matches the exclusion set:

- `lua_modules`
- `.luarocks`
- any segment matching glob `build*` (e.g. `build`, `build-5.4`)
- `output`
- `thirdparty`

Exclusion is by **directory segment**, not substring (so a rockspec literally named
`build-tools-1.0-1.rockspec` in a non-excluded dir is still discovered; only a `build*/`
**directory** in its path excludes it). See design §3.2 for the exact predicate.

### ROCKS-09-03 / ROCKS-09-04: Discovered Rock Set API + Caching

`LuaRockspecDiscoveryService` (project-level `@Service`) exposes
`discoverRockspecPaths(): List<DiscoveredRockspec>` where `DiscoveredRockspec` carries the rockspec
`Path` and the parsed `packageName`. The result is wrapped in a `CachedValuesManager`
project-level cached value keyed on `PsiModificationTracker.getInstance(project)` (the same
invalidation contract `LuaTypeManagerImpl` uses), so repeated calls in a single edit-cycle do
not re-enumerate.

### ROCKS-09-05: Multi-Root Dependency Resolution

`LuaRocksDependencyResolver.resolveAll(project): List<DependencyNode>` builds one resolved
root per discovered rockspec (each via the existing `RockspecBridge.read` + `expand` machinery,
sharing one installed-rock index). The legacy single-root `resolve(project): DependencyNode?`
becomes the first element of `resolveAll` (or null when empty) to keep the existing
`DependencyTreePanel` caller compiling; the panel is updated to render all roots.

### ROCKS-09-06: Remove `workspace.lua` Scaffolding

`workspace.lua` is written by `LuaRocksScaffolder.scaffoldWorkspace` via
`LuaRocksTemplates.workspaceLua`, but **nothing reads it** at runtime (grep `workspace` across
`rocks/` finds only `init/` scaffolding, the generator-peer UI, and tests). Multi-rock is now a
*discovered* property. Therefore: delete `LuaRocksTemplates.workspaceLua`,
`LuaRocksScaffolder.scaffoldWorkspace`; collapse `enum RockKind { SINGLE_ROCK, WORKSPACE }` to a
single-rock generator; remove the `workspaceName`/`initialRocks` settings fields and the
Workspace radio/fields from `LuaRocksGeneratorPeer`; delete the now-dead workspace tests.

### ROCKS-09-07: Membership Override

Two optional ordered glob lists on `LuaProjectSettings.State`:
`rockspecIncludeGlobs: MutableList<String>` and `rockspecExcludeGlobs: MutableList<String>`
(default empty). When **both empty**, default behaviour (recursive minus built-in excludes)
applies. When non-empty they refine discovery per design §3.3. No new settings file is created;
these ride the existing `.idea/lunar.xml` storage.

### ROCKS-09-08: ROCKS-05 Delegation Hook

The service exposes `discoverRockspecPaths(): List<Path>` (paths only). ROCKS-05's planned
`LuaRocksTreeLocator.allProjectRockspecs(project): List<Path>` becomes a one-line delegate to
this method, so the recursion/exclusion logic lives in exactly one place.

## Behavior Rules

- **Determinism / ordering**: the returned list is sorted by the rockspec's project-relative
  path string (case-insensitive, `/`-normalised) so tree rendering and tests are stable.
- **Duplicate package names**: two discovered rockspecs may declare the same `package`; both are
  retained as distinct `DiscoveredRockspec` entries (keyed by path, not package name).
- **Unparseable rockspec**: a rockspec whose bridge read fails is still *discovered* (its path is
  returned) but contributes no `DependencyNode` root (resolution skips it, logs a warning).
- **Empty result**: a project with zero source rockspecs yields an empty list; callers degrade
  to "no rocks found" (the dependency panel already shows a no-rockspec message).
- **Excluded-only project**: if every rockspec is under an excluded dir, the result is empty.

## Test Cases

| # | Requirement | Given (input) | When (action) | Then (expected) |
|---|-------------|---------------|---------------|-----------------|
| 1 | ROCKS-09-01 | `Kernel/v0`-shaped fixture: 10 rockspecs at `rocks/<name>/<name>-1.0-1.rockspec`, **no root rockspec** | `LuaRockspecDiscoveryService.getInstance(project).discoverRockspecPaths()` | Returns exactly 10 paths, one per `rocks/<name>/` dir |
| 2 | ROCKS-09-02 | Fixture with `foo-1.0-1.rockspec` at root **and** `lua_modules/share/lua/5.4/bar/bar-2.0-1.rockspec` | `discoverRockspecPaths()` | Returns only the root `foo` rockspec; the `lua_modules/` one is **not** present |
| 3 | ROCKS-09-02 | Fixture with rockspecs under `build/`, `build-5.4/`, `output/`, `thirdparty/vendored/`, and `.luarocks/` | `discoverRockspecPaths()` | All five excluded; result empty |
| 4 | ROCKS-09-02 | Fixture with `src/build-tools-1.0-1.rockspec` (a file named `build-…`, not in a `build/` dir) | `discoverRockspecPaths()` | The file **is** discovered (exclusion is by directory segment, not filename) |
| 5 | ROCKS-09-03 | `Kernel/v0`-shaped fixture, bridge stubbed to echo `package` per file | `discoverRockspecPaths()` | Returns 10 `DiscoveredRockspec`, each with the matching `packageName` (`adt`, `channels`, …) |
| 6 | ROCKS-09-04 | Any fixture; call `discoverRockspecPaths()` twice with no edits in between | second call | Returns the same instance / does not re-enumerate (assert via a counting bridge or cached-value identity) |
| 7 | ROCKS-09-05 | `Kernel/v0`-shaped fixture | `LuaRocksDependencyResolver.resolveAll(project)` | Returns 10 `DependencyNode` roots, one per discovered rock (each `isTransitive == false`) |
| 8 | ROCKS-09-05 | Single-rock fixture: one root `foo-scm-1.rockspec` depending on `ghost` (not installed) | `resolveAll(project)` | One root `foo` whose child `ghost` is flagged `MISSING_DEPENDENCY` (parity with TC-ROCKS-03-05) |
| 9 | ROCKS-09-06 | n/a (compile-time) | Build the plugin after removing the workspace path | `LuaRocksTemplates.workspaceLua`, `scaffoldWorkspace`, `RockKind.WORKSPACE` no longer exist; build is green; generator scaffolds a single rock |
| 10 | ROCKS-09-07 | Fixture with `a/a-1.0-1.rockspec` and `vendor/v-1.0-1.rockspec`; `rockspecExcludeGlobs = ["vendor/**"]` | `discoverRockspecPaths()` | Returns only `a/a-1.0-1.rockspec` |
| 11 | ROCKS-09-07 | Fixture as #10 with `rockspecIncludeGlobs = ["a/**"]`, excludes empty | `discoverRockspecPaths()` | Returns only `a/a-1.0-1.rockspec` (include acts as an allow-list when non-empty) |
| 12 | ROCKS-09-08 | `Kernel/v0`-shaped fixture | `LuaRocksTreeLocator.allProjectRockspecs(project)` | Returns the same 10 paths as TC #1 (delegation) |

## Acceptance Criteria

- [ ] ROCKS-09-01/02: recursive discovery with directory-segment exclusion (TC 1–4).
- [ ] ROCKS-09-03/04: project service returns a cached, invalidated discovered set (TC 5–6).
- [ ] ROCKS-09-05: `resolveAll` produces one root per discovered rock; missing deps flagged (TC 7–8).
- [ ] ROCKS-09-06: `workspace.lua` scaffolding fully removed; build green; single-rock generator (TC 9).
- [ ] ROCKS-09-07: include/exclude glob overrides honoured (TC 10–11).
- [ ] ROCKS-09-08: ROCKS-05's `allProjectRockspecs` delegates to this service (TC 12).

## Non-Functional Requirements

- **Threading**: discovery reads VFS/index inside `runReadAction`; it must run off the EDT
  (the dependency panel already calls resolution on a pooled thread). No `SlowOperationsException`.
- **Caching/memory**: cache via `CachedValuesManager` keyed on `PsiModificationTracker`; never
  retain hard refs to `Project`/`VirtualFile` in service fields (hold only `Project`, taken per
  call, mirroring `DependencyTreePanel`). See `docs/engineering-contract.md` §4.
- **Performance**: discovery is O(files-named-`*.rockspec`) via `FilenameIndex`, not an O(all
  files) `nio` tree walk (see design §9 alternatives).
- **Cancellation**: long loops call `ProgressManager.checkCanceled()`.

## Dependencies

- **ROCKS-03** (dependency resolution): consumes/extends `LuaRocksDependencyResolver`,
  `LuaRocksTreeLocator`, `RockspecBridge`, `DependencyNode`, `DependencyTreePanel`.
- **ROCKS-05** (module resolution): consumes the discovered set; its `allProjectRockspecs`
  becomes a delegate (ROCKS-09-08). Coordinated so the recursion lives in one place.
- **ROCKS-01** (project init): the `workspace.lua` scaffolding being removed lives here.

## See Also
- Design: [design.md](design.md)
- Plan: [implementation-plan.md](implementation-plan.md)
- Risks: [risks-and-gaps.md](risks-and-gaps.md)
- Checklists: [human-verification-checklists.md](human-verification-checklists.md)
