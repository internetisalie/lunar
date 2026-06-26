---
id: "ROCKS-12-PLAN"
title: "Implementation Plan"
type: "plan"
status: "planned"
parent_id: "ROCKS-12"
folders:
  - "[[features/rocks/12-project-view-roots/requirements|requirements]]"
---

# ROCKS-12: Implementation Plan

## Phases

### Phase 1: Installed-rock library provider (Piece A) [Must]
- **Goal**: surface the project-local installed LuaRocks tree under External Libraries and exclude
  it from first-party indexing.
- **Tasks**:
  - [x] Create `net.internetisalie.lunar.rocks.library.LuaRocksLibraryProvider` extending
    `AdditionalLibraryRootsProvider` — realizes design §2.1.
  - [x] Implement `installedRoots(project): List<VirtualFile>` using `LuaRocksTreeLocator.treeRoot`,
    `LuaProjectSettings...getImplicitLanguageLevel().version`, and `VfsUtil.findFile` for
    `share/lua/<X.Y>` + `lib/lua/<X.Y>` — realizes design §3.1.
  - [x] Add the nested `InstalledRocksLibrary : SyntheticLibrary, ItemPresentation` (source roots =
    resolved dirs; presentable text "Installed Rocks") — realizes design §2.1.
  - [x] Override `getRootsToWatch` to return `installedRoots(project)` — realizes ROCKS-12-05.
  - [x] Register a third `<additionalLibraryRootsProvider>` in `plugin.xml` next to lines 366-367 —
    realizes design §7 / ROCKS-12-06.
- **Exit criteria**: TC #1, #2, #3, #4, #5, #6 pass; build green; "Installed Rocks" node appears for
  a project with a populated `lua_modules` tree.

### Phase 2: First-party source-root marking (Piece B) [Should]
- **Goal**: badge in-project first-party `build.modules`-derived source-root folders in the Project
  view without mutating the model.
- **Tasks**:
  - [ ] Create `net.internetisalie.lunar.rocks.view.LuaRockSourceRootDecorator` implementing
    `com.intellij.ide.projectView.ProjectViewNodeDecorator` — realizes design §2.2.
  - [ ] Implement `sourceRootDirs(project, base): Set<String>` filtering
    `PathConfiguration.getProjectSourcePathPatterns` to the absolute in-project leading paths —
    realizes design §3.2.
  - [ ] In `decorate`, append a grayed " rock source root" suffix via `data.addText` when the node's
    directory path is in the set — realizes design §2.2 / ROCKS-12-07.
  - [ ] Register `<projectViewNodeDecorator>` in `plugin.xml` — realizes design §7 / ROCKS-12-08.
- **Exit criteria**: TC #7, #8 pass; `thirdparty/` and `lua_modules` remain unmarked; build green.

## Requirement → Phase Coverage

| Requirement | Priority | Delivered in |
|-------------|----------|--------------|
| ROCKS-12-01 | M | Phase 1 |
| ROCKS-12-02 | M | Phase 1 |
| ROCKS-12-03 | M | Phase 1 |
| ROCKS-12-04 | M | Phase 1 |
| ROCKS-12-05 | S | Phase 1 |
| ROCKS-12-06 | M | Phase 1 |
| ROCKS-12-07 | S | Phase 2 |
| ROCKS-12-08 | S | Phase 2 |
| ROCKS-12-09 | S | Phase 2 |

## Verification Tasks

- [x] Add `LuaRocksLibraryProviderTest` (`BasePlatformTestCase`): build a fixture tree with
  `lua_modules/share/lua/5.4/...` and assert the returned `SyntheticLibrary` source roots — covers
  TC #1, #5, #6.
- [x] Test the empty/missing-tree paths return an empty contribution — covers TC #3, #4.
- [x] Test `ProjectFileIndex.isInLibrary` is `true` for a file under the installed tree — covers
  TC #2.
- [ ] Add `LuaRockSourceRootDecoratorTest`: stub `getProjectSourcePathPatterns` to an in-project
  root and assert `decorate` appends the suffix for that folder but not for `thirdparty/` —
  covers TC #7, #8.
- [ ] Run `human-verification-checklists.md` in the containerized IDE.

## Task Summary

| Phase | Status | Priority |
|-------|--------|----------|
| Phase 1: Installed-rock library provider | done | Must |
| Phase 2: First-party source-root marking | todo | Should |
