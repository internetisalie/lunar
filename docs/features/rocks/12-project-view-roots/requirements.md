---
id: "ROCKS-12"
title: "12: Project-View Roots & Marking"
type: "feature"
status: "done"
vf_icon: ✅
priority: "medium"
parent_id: "ROCKS"
folders:
  - "[[features/rocks/requirements|ROCKS]]"
---

# ROCKS-12: Project-View Roots & Marking

## Overview

Today the IntelliJ Project view shows no visual distinction for LuaRocks-related folders: the
installed-dependency tree (`lua_modules`) appears as a plain in-project folder and is indexed as
first-party source, and the first-party rock source roots (the `build.modules`-derived directories)
look identical to ordinary folders. This feature (a) surfaces the installed-rock tree under
**External Libraries** as a `SyntheticLibrary` so installed rocks are browsable and excluded from
first-party indexing, and (b) visually marks the first-party source roots in the Project view. It
consumes the discovery (ROCKS-09) and source-root derivation (ROCKS-05) chokepoints rather than
re-scanning. Parent epic: [[features/rocks/requirements|ROCKS]].

## Scope

### In Scope

- **Piece A** — A new `AdditionalLibraryRootsProvider` that surfaces the project-local installed
  LuaRocks tree (`lua_modules/share/lua/<X.Y>/` for Lua modules and `lua_modules/lib/lua/<X.Y>/` for
  C modules) as a single `SyntheticLibrary` named under **External Libraries**, with
  `getRootsToWatch` covering the same roots. This makes installed rocks browsable AND excludes them
  from the first-party source scope (a `SyntheticLibrary` source root is treated as a library root,
  not project source).
- **Piece B** — A `ProjectViewNodeDecorator` that badges the first-party rock source-root folders
  (the in-project `build.modules`-derived roots, from ROCKS-05) with a presentation marker (a
  suffix label) in the Project view. Non-destructive: presentation only, no workspace/content-entry
  model change.

### Out of Scope

- Re-scanning rockspecs or re-deriving `build.modules` — both are consumed from ROCKS-09 / ROCKS-05.
- System/global LuaRocks trees (project-local trees only, matching `LuaRocksTreeLocator` v1 scope;
  deferred to ROCKS-03-G-01).
- Marking vendored/third-party copies (`thirdparty/`) — only first-party `build.modules` roots are
  marked.
- True source-folder semantics via the workspace/content-entry model (heavier, persisted) — see
  risks-and-gaps; rejected in favour of the non-destructive decorator.
- Manifest parsing of the installed tree — the directory layout under `share/lua` / `lib/lua` is
  used directly.

## Functional Requirements

| ID | Requirement | Priority | Description |
|----|-------------|----------|-------------|
| ROCKS-12-01 | **Installed-rock library provider** | M | A new `AdditionalLibraryRootsProvider` returns a `SyntheticLibrary` whose source roots are the existing `lua_modules/share/lua/<X.Y>/` and `lua_modules/lib/lua/<X.Y>/` directories, surfaced under External Libraries. |
| ROCKS-12-02 | **Empty/missing tree → no library** | M | When no project-local tree exists, or the resolved `share/lua/<X.Y>` and `lib/lua/<X.Y>` directories are both absent, the provider returns an empty collection (no library node). |
| ROCKS-12-03 | **Installed rocks excluded from first-party scope** | M | A `.lua` file under the installed tree resolves as a library file (`isInLibrary`/excluded from project-source scope), not as indexed project source. |
| ROCKS-12-04 | **`<X.Y>` derived from the configured target** | M | The `<X.Y>` segment is derived from `LuaProjectSettings...getTarget().getImplicitLanguageLevel().version`, with the same default (`5.4`) the rest of the plugin uses when no explicit target is set. |
| ROCKS-12-05 | **Roots watched** | S | `getRootsToWatch` returns the same resolved installed-tree directories so VFS changes there trigger refresh. |
| ROCKS-12-06 | **plugin.xml registration (Piece A)** | M | The new provider is registered as a third `<additionalLibraryRootsProvider>` next to the existing two. |
| ROCKS-12-07 | **First-party source-root marking** | S | First-party `build.modules`-derived source-root folders (from ROCKS-05) that are inside the project base are badged in the Project view via a `ProjectViewNodeDecorator`. |
| ROCKS-12-08 | **plugin.xml registration (Piece B)** | S | The decorator is registered under `<projectViewNodeDecorator>`. |
| ROCKS-12-09 | **No vendored marking** | S | `thirdparty/` and the installed `lua_modules` tree are NOT marked by the Piece-B decorator (those come from the library provider, not from first-party source roots). |

## Detailed Specifications

### ROCKS-12-01 / ROCKS-12-04: Installed-rock library root resolution

The provider resolves roots from the existing helpers:

1. `val tree = LuaRocksTreeLocator.treeRoot(project)` — returns the project-local tree
   (`lua_modules` or `.luarocks`) `Path`, or `null` if neither exists.
2. `val version = LuaProjectSettings.getInstance(project).state.getTarget().getImplicitLanguageLevel().version`
   — e.g. `"5.4"` (the `<X.Y>` string). This mirrors how `PlatformLibraryIndex.getPlatformLibrary`
   derives the level from the target.
3. Candidate directories: `tree.resolve("share").resolve("lua").resolve(version)` and
   `tree.resolve("lib").resolve("lua").resolve(version)`.
4. For each candidate, `VfsUtil.findFile(path, true)`; keep the non-null, existing directories.
5. If the kept set is empty → return an empty collection (ROCKS-12-02). Otherwise return one
   `SyntheticLibrary` whose `getSourceRoots()` is the kept directories.

The path layout is confirmed by the scaffolder (`rocks/init/LuaRocksTemplates.kt:43-45`):
`package.path = "lua_modules/share/lua/" .. version .. "/?.lua"` and
`package.cpath = "lua_modules/lib/lua/" .. version .. "/?.so"`.

### ROCKS-12-03: Library exclusion semantics

A `SyntheticLibrary` source root is treated by the platform as a library root, so files under it
report `ProjectFileIndex.isInLibrary == true` and are excluded from project-source-only scopes.
This is the same mechanism `PlatformLibraryProvider.ExternalLibraries` and
`LuaLibraryProvider.LuaLibrary` already rely on; no extra code is needed beyond returning the roots.

### ROCKS-12-07 / ROCKS-12-09: First-party source-root marking

The decorator marks the first-party source-root folders — the in-project subset of
`PathConfiguration.getProjectSourcePathPatterns(project)` leading paths. These are exactly the
ROCKS-05 `build.modules`-derived roots that `PlatformLibraryProvider.getExternalLibraries` filters
OUT (because they are absolute AND inside the project base, failing the
`!it.startsWith(projectBasePath)` test at `PlatformLibraryProvider.kt:62`). The decorator badges a
Project-view folder node when its `VirtualFile` directory equals one of these in-project leading
paths. The installed `lua_modules` tree and `thirdparty/` copies are not in this set, so they are
not marked.

## Behavior Rules

- The library provider runs under read access; all path → VFS resolution uses `VfsUtil.findFile`.
- The provider holds no hard reference to `Project`/`VirtualFile` beyond the `SyntheticLibrary`'s
  own roots (matching the existing two providers).
- Marking is presentation-only and additive (a suffix label), never replacing the node's value or
  model.
- The `<X.Y>` segment always resolves (the target derivation has a `5.4` default), so a project with
  no explicit target still resolves an installed-tree path.

## Test Cases

| # | Requirement | Given (input) | When (action) | Then (expected) |
|---|-------------|---------------|---------------|-----------------|
| 1 | ROCKS-12-01 | A project whose base contains `lua_modules/share/lua/5.4/luassert/init.lua`; target = Standard 5.4 | `getAdditionalProjectLibraries(project)` is called | The returned collection contains one `SyntheticLibrary` whose `getSourceRoots()` includes the `VirtualFile` for `lua_modules/share/lua/5.4` |
| 2 | ROCKS-12-03 | Same project as #1 | Resolve `ProjectFileIndex.getInstance(project).isInLibrary(vf)` for `lua_modules/share/lua/5.4/luassert/init.lua` | Returns `true` (file is library-scoped, excluded from project source) |
| 3 | ROCKS-12-02 | A project whose base has no `lua_modules` and no `.luarocks` directory | `getAdditionalProjectLibraries(project)` is called | The provider contributes no library (its own contribution is an empty collection) |
| 4 | ROCKS-12-02 | A project with `lua_modules/` present but no `share/lua/5.4` and no `lib/lua/5.4` (e.g. only `lib/luarocks/`); target = 5.4 | `getAdditionalProjectLibraries(project)` is called | The provider contributes no library (empty collection) |
| 5 | ROCKS-12-04 | A project with `lua_modules/share/lua/5.1/foo.lua`; target = Standard 5.1 | `getAdditionalProjectLibraries(project)` is called | The `SyntheticLibrary` source roots include `lua_modules/share/lua/5.1`, not `5.4` |
| 6 | ROCKS-12-01 | A project with both `lua_modules/share/lua/5.4/foo.lua` and `lua_modules/lib/lua/5.4/bar.so`; target 5.4 | `getAdditionalProjectLibraries(project)` is called | The single `SyntheticLibrary` source roots include both `share/lua/5.4` and `lib/lua/5.4` |
| 7 | ROCKS-12-07 | A project where `getProjectSourcePathPatterns` yields in-project leading path `<base>/src/`; Project view shows the `src` folder | The `ProjectViewNodeDecorator.decorate` runs for the `src` folder node | The node presentation gains the rock-source-root suffix label |
| 8 | ROCKS-12-09 | A project with a `thirdparty/` folder and a populated `lua_modules` tree | The decorator runs for the `thirdparty` and `lua_modules` folder nodes | Neither node receives the rock-source-root label |

## Acceptance Criteria

- [ ] ROCKS-12-01/02/03/04: the new provider returns the installed-tree `SyntheticLibrary` exactly
  when the resolved `share`/`lib` `<X.Y>` directories exist, and files there are library-scoped.
- [ ] ROCKS-12-06: a third `<additionalLibraryRootsProvider>` is present in `plugin.xml`.
- [ ] ROCKS-12-07/08/09: first-party in-project source roots are badged; vendored/installed trees
  are not.

## Non-Functional Requirements

- **Threading**: `getAdditionalProjectLibraries`, `getRootsToWatch`, and `decorate` run under
  platform read access; do only VFS lookups (`VfsUtil.findFile` / `LocalFileSystem`), no blocking
  I/O, no heavy parse. See `docs/engineering-contract.md` (threading segregation).
- **Memory**: hold no long-lived hard refs to `Project`/`VirtualFile` (engineering-contract §4).
- **Library-set changes**: a roots change must trigger a rescan via
  `ProjectRootManagerEx.makeRootsChange` (already done by `PlatformLibraryIndex.reload`).

## Dependencies

- **ROCKS-09** (`09-workspace-discovery`): `LuaRockspecDiscoveryService.discoverRockspecPaths()`.
- **ROCKS-05** (`05-module-resolution`): `PathConfiguration.getProjectSourcePathPatterns` chokepoint
  and the `build.modules` source-root derivation.
- Existing: `LuaRocksTreeLocator.treeRoot`, `LuaProjectSettings`, `LuaIcons`.

## See Also
- Design: [design.md](design.md)
- Plan: [implementation-plan.md](implementation-plan.md)
- Risks: [risks-and-gaps.md](risks-and-gaps.md)
- Checklists: [human-verification-checklists.md](human-verification-checklists.md)
