---
id: "ROCKS-12-DESIGN"
title: "Technical Design"
type: "design"
parent_id: "ROCKS-12"
folders:
  - "[[features/rocks/12-project-view-roots/requirements|requirements]]"
---

# Technical Design: ROCKS-12 — Project-View Roots & Marking

## 1. Architecture Overview

### Current State

- Only two `<additionalLibraryRootsProvider>` are registered (`plugin.xml:366-367`):
  `PlatformLibraryProvider` and `LuaLibraryProvider`.
- `PlatformLibraryProvider.getExternalLibraries` (`project/PlatformLibraryProvider.kt:54-71`)
  surfaces source-path roots as an `ExternalLibraries` ("Search Trees") `SyntheticLibrary`, but
  filters to **absolute** paths (`:61`) that are **outside** the project base (`:62`,
  `!it.startsWith(projectBasePath)`). So in-project rock source roots and `lua_modules` are
  excluded and shown as plain folders.
- `LuaLibraryProvider` (`lang/library/LuaLibraryProvider.kt:13-44`) roots only the runtime API
  stubs ("Lua External API Stubs"), never `lua_modules`.
- No `ProjectViewNodeDecorator`, `IconProvider`, or `FileIconProvider` is registered (grep of
  `plugin.xml` finds none).

Net: nothing roots the installed-dependency tree (so it indexes as first-party source), and nothing
marks the first-party source roots.

### Prior Art in This Repo

- `net.internetisalie.lunar.project.PlatformLibraryProvider` (`PlatformLibraryProvider.kt:41`) —
  an existing `AdditionalLibraryRootsProvider`; Piece A **mirrors** its `SyntheticLibrary`
  +`ItemPresentation` shape but is a **new, separate** provider (it surfaces the in-project
  installed tree that `PlatformLibraryProvider` deliberately filters out). Not extended/replaced.
- `net.internetisalie.lunar.lang.library.LuaLibraryProvider` (`LuaLibraryProvider.kt:13`) — the
  template for `getRootsToWatch` + a nested `SyntheticLibrary`; Piece A mirrors it. Not modified.
- `net.internetisalie.lunar.rocks.LuaRocksTreeLocator.treeRoot` (`rocks/LuaRocksTreeLocator.kt:33`)
  — reused to locate the project-local tree.
- `net.internetisalie.lunar.lang.path.PathConfiguration.getProjectSourcePathPatterns`
  (`lang/path/SourcePathPattern.kt:19`) — reused by Piece B for the first-party source-root set.
- `ProjectViewNodeDecorator` / `FileIconProvider` — searched `plugin.xml`; **none found** for this
  plugin, so Piece B introduces the first one.

### Target State

```
External Libraries
├── Lua x.y                     (PlatformLibraryProvider.PlatformLibrary — existing)
├── Lua External API Stubs      (LuaLibraryProvider.LuaLibrary — existing)
├── Search Trees                (PlatformLibraryProvider.ExternalLibraries — existing, OUT-of-project)
└── Installed Rocks             (LuaRocksLibraryProvider.InstalledRocksLibrary — NEW, Piece A)

Project view
└── <base>/src   ◀── badged "rock source root"   (LuaRockSourceRootDecorator — NEW, Piece B)
```

Piece A: a new `AdditionalLibraryRootsProvider` surfaces `lua_modules/share/lua/<X.Y>` and
`lua_modules/lib/lua/<X.Y>` as one `SyntheticLibrary`. Piece B: a `ProjectViewNodeDecorator` badges
the in-project first-party source-root folders.

## 2. Core Components

### 2.1 `net.internetisalie.lunar.rocks.library.LuaRocksLibraryProvider`

- **Responsibility**: surface the project-local installed LuaRocks tree's Lua + C module
  directories as a single `SyntheticLibrary` under External Libraries (Piece A).
- **Threading**: read access (platform-invoked); only `VfsUtil.findFile` lookups, no blocking I/O.
- **Collaborators**: `LuaRocksTreeLocator.treeRoot` (existing), `LuaProjectSettings.getInstance`
  (existing), `LuaLanguageLevel.version` (existing), `VfsUtil.findFile`, `LuaIcons.FILE` (existing).
- **Key API**:
  ```kotlin
  class LuaRocksLibraryProvider : AdditionalLibraryRootsProvider() {
      override fun getAdditionalProjectLibraries(project: Project): Collection<SyntheticLibrary> {
          val roots = installedRoots(project)
          if (roots.isEmpty()) return emptyList()
          return listOf(InstalledRocksLibrary(roots))
      }

      override fun getRootsToWatch(project: Project): Collection<VirtualFile> =
          installedRoots(project)

      private fun installedRoots(project: Project): List<VirtualFile> { /* §3.1 */ }

      class InstalledRocksLibrary(private val roots: List<VirtualFile>) :
          SyntheticLibrary(), ItemPresentation {
          override fun getSourceRoots(): Collection<VirtualFile> = roots
          override fun getPresentableText(): String = "Installed Rocks"
          override fun getLocationString(): String = "lua_modules"
          override fun getIcon(unused: Boolean): Icon = LuaIcons.FILE
          override fun hashCode(): Int = roots.hashCode()
          override fun equals(other: Any?): Boolean =
              other is InstalledRocksLibrary && other.roots == roots
      }
  }
  ```
  Base-class signatures verified: `AdditionalLibraryRootsProvider.getAdditionalProjectLibraries`
  (`projectModel-api/.../AdditionalLibraryRootsProvider.java:34`), `getRootsToWatch` (`:51`).

### 2.2 `net.internetisalie.lunar.rocks.view.LuaRockSourceRootDecorator`

- **Responsibility**: badge the in-project first-party `build.modules`-derived source-root folders
  in the Project view (Piece B); presentation only, no model change.
- **Threading**: read access (platform-invoked during node presentation); only path comparison and
  VFS reads, no I/O.
- **Collaborators**: `PathConfiguration.getProjectSourcePathPatterns` (existing,
  `SourcePathPattern.kt:19`), `SourcePathPattern.leadingPath` (existing, `:29`),
  `com.intellij.ide.projectView.ProjectViewNode`, `com.intellij.ide.projectView.PresentationData`.
- **Key API**:
  ```kotlin
  class LuaRockSourceRootDecorator : ProjectViewNodeDecorator {
      override fun decorate(node: ProjectViewNode<*>, data: PresentationData) {
          val project = node.project ?: return
          val dir = node.virtualFile ?: return
          if (!dir.isDirectory || !dir.isValid) return
          val base = project.basePath ?: return
          val roots = sourceRootDirs(project, base)            // §3.2 (Set<String>)
          if (dir.path.trimEnd('/') in roots) {
              data.addText(" rock source root", SimpleTextAttributes.GRAYED_ATTRIBUTES)
          }
      }

      private fun sourceRootDirs(project: Project, base: String): Set<String> { /* §3.2 */ }
  }
  ```
  Interface verified: `com.intellij.ide.projectView.ProjectViewNodeDecorator.decorate(node:
  ProjectViewNode<*>, data: PresentationData)` (`ProjectViewNodeDecorator.kt:15,22`). Real platform
  example using `addText` on a node: `ScratchFileServiceImpl$FilePresentation.decorate`
  (`ScratchFileServiceImpl.java:331,356`).

## 3. Algorithms

### 3.1 Installed-tree root resolution (`LuaRocksLibraryProvider.installedRoots`)

- **Input → Output**: `Project` → `List<VirtualFile>` (0–2 directory roots).
- **Steps**:
  1. `val tree: Path = LuaRocksTreeLocator.treeRoot(project) ?: return emptyList()`.
  2. `val version: String = LuaProjectSettings.getInstance(project).state.getTarget()`
     `.getImplicitLanguageLevel().version`  // e.g. `"5.4"`.
  3. `val candidates = listOf(`
     `tree.resolve("share").resolve("lua").resolve(version),`
     `tree.resolve("lib").resolve("lua").resolve(version))`.
  4. `return candidates.mapNotNull { VfsUtil.findFile(it, true) }.filter { it.isDirectory }`.
- **Rules / edge handling**: no tree → empty. Neither `share/lua/<X.Y>` nor `lib/lua/<X.Y>` exists
  → `mapNotNull` yields empty → empty list (no library, ROCKS-12-02). The `<X.Y>` always resolves
  because `getImplicitLanguageLevel()` has a `LUA54` default (`Target.kt:49`) and
  `LuaProjectSettings.getInstance(project).state.getTarget()` returns `Target.default()`
  (Standard 5.4) when unset.
- **Complexity**: O(1) — two VFS lookups, no tree walk.

### 3.2 First-party source-root directory set (`LuaRockSourceRootDecorator.sourceRootDirs`)

- **Input → Output**: `(Project, base: String)` → `Set<String>` of normalised in-project directory
  paths.
- **Steps**:
  1. `val patterns = PathConfiguration.getProjectSourcePathPatterns(project)`.
  2. For each pattern: `val lead = pattern.leadingPath` (the literal portion before `?`).
  3. `val p = Paths.get(lead)`; keep only `p.isAbsolute && p.startsWith(base)` — the in-project
     subset (the complement of what `PlatformLibraryProvider.getExternalLibraries` keeps).
  4. Normalise each kept leading path: drop a trailing `/` so it names a directory
     (`lead.trimEnd('/')`); collect into a `Set<String>`.
  5. Return the set.
- **Rules / edge handling**: `leadingPath` for a pattern like `/proj/src/?.lua` is `/proj/src/`
  (the directory). A pattern with no `?` yields the whole spec as `leadingPath` — still compared as
  a directory string; harmless. Out-of-project and non-absolute leads are dropped (they belong to
  `PlatformLibraryProvider`'s External-Libraries path, not first-party marking). Empty patterns →
  empty set → nothing badged. `lua_modules` and `thirdparty/` are not produced by
  `getProjectSourcePathPatterns` as source roots, so they are never in the set (ROCKS-12-09).
- **Complexity**: O(n) over the pattern count per node decoration; cheap string work, no I/O.

## 4. External Data & Parsing

This feature consumes no external CLI/text output. The installed-tree layout is read purely by
directory structure (`share/lua/<X.Y>`, `lib/lua/<X.Y>`); source roots come from the ROCKS-05
`PathConfiguration` chokepoint, already parsed upstream. No new parser is introduced.

## 5. Data Flow

### Example 1: Installed-rock library (Piece A)

Project base has `lua_modules/share/lua/5.4/luassert/init.lua`, target Standard 5.4.
`LuaRocksLibraryProvider.getAdditionalProjectLibraries` → `installedRoots` → tree=`<base>/lua_modules`,
version=`5.4` → finds `share/lua/5.4` (and `lib/lua/5.4` if present) → returns one
`InstalledRocksLibrary`. The platform lists "Installed Rocks" under External Libraries; files there
report `isInLibrary == true` and drop out of the project-source scope.

### Example 2: Empty tree (Piece A)

Project base has neither `lua_modules` nor `.luarocks`. `treeRoot` → `null` → `installedRoots` →
empty → provider contributes no library.

### Example 3: First-party source-root marking (Piece B)

ROCKS-05 derived an in-project source root `<base>/src/` into
`getProjectSourcePathPatterns`. The Project view renders the `src` folder; `decorate` resolves
`node.virtualFile.path == <base>/src`, finds it in `sourceRootDirs`, and appends the grayed
" rock source root" suffix. `thirdparty/` and `lua_modules` are not in the set → unmarked.

## 6. Edge Cases

- **No target set** — `getTarget()` returns Standard 5.4 default; `<X.Y>` = `5.4`.
- **`.luarocks` tree instead of `lua_modules`** — `treeRoot` already prefers `lua_modules` then
  `.luarocks`; resolution uses whichever exists.
- **Only `lib/luarocks/` present (metadata, no `share/lua`)** — `share/lua/<X.Y>` and
  `lib/lua/<X.Y>` absent → empty list → no library (ROCKS-12-02, TC #4).
- **C-only install** — only `lib/lua/<X.Y>` exists → single-root library.
- **Source root equals project base** (`leadingPath == <base>/`) — included if absolute and inside
  base; badges the base node. Acceptable (it is a genuine source root); no special-casing.
- **Roots-change rescan** — adding/removing installed rocks invalidates VFS; the platform reloads
  additional-library roots on a roots change. The plugin already performs
  `ProjectRootManagerEx.makeRootsChange(...)` on relevant settings changes via
  `PlatformLibraryIndex.reload` (`PlatformLibraryProvider.kt:133-142`); no new trigger is added by
  ROCKS-12 (a `luarocks install` produces VFS events the platform watches via `getRootsToWatch`).

## 7. Integration Points

```xml
<!-- src/main/resources/META-INF/plugin.xml, in <extensions defaultExtensionNs="com.intellij"> -->
<!-- Piece A: register next to the existing two providers at lines 366-367 -->
<additionalLibraryRootsProvider
    implementation="net.internetisalie.lunar.rocks.library.LuaRocksLibraryProvider"/>

<!-- Piece B -->
<projectViewNodeDecorator
    implementation="net.internetisalie.lunar.rocks.view.LuaRockSourceRootDecorator"/>
```

EP names verified against the platform:
- `additionalLibraryRootsProvider` — used at `plugin.xml:366-367` (existing).
- `projectViewNodeDecorator` — EP defined in
  `intellij-community/platform/platform-resources/src/META-INF/LangExtensionPoints.xml:606`
  (`interface="com.intellij.ide.projectView.ProjectViewNodeDecorator"`); real usage at
  `LangExtensions.xml:410`.

## Cross-Feature Contract (consumed, not redefined)

- **ROCKS-09** — `net.internetisalie.lunar.rocks.LuaRockspecDiscoveryService.discoverRockspecPaths():
  List<DiscoveredRockspec>` (instance via `getInstance(project)`), `DiscoveredRockspec(rockspec:
  java.nio.file.Path, packageName: String?)` (verified
  `09-workspace-discovery/design.md:80-116`). ROCKS-12 does NOT re-scan rockspecs; the first-party
  roots reach it transitively through ROCKS-05's `PathConfiguration` chokepoint (which is itself
  backed by discovery). ROCKS-12 needs no direct discovery call for Piece B, but the contract is
  named here as the upstream source of the source-root set.
- **ROCKS-05** — `net.internetisalie.lunar.lang.path.PathConfiguration.getProjectSourcePathPatterns(
  project): List<SourcePathPattern>` (verified `SourcePathPattern.kt:19`) is the single source-path
  chokepoint; ROCKS-05 appends its `build.modules`-derived in-project roots here. ROCKS-12 Piece B
  consumes those leading paths and does NOT re-derive `build.modules`.

## 8. Requirement Coverage

| Requirement | Priority | Implemented by (section) |
|-------------|----------|--------------------------|
| ROCKS-12-01 | M | §2.1, §3.1 |
| ROCKS-12-02 | M | §3.1 (empty path), §6 |
| ROCKS-12-03 | M | §2.1 (SyntheticLibrary source roots), §5 Ex.1 |
| ROCKS-12-04 | M | §3.1 step 2 |
| ROCKS-12-05 | S | §2.1 `getRootsToWatch` |
| ROCKS-12-06 | M | §7 (Piece A registration) |
| ROCKS-12-07 | S | §2.2, §3.2 |
| ROCKS-12-08 | S | §7 (Piece B registration) |
| ROCKS-12-09 | S | §3.2 (in-project filter excludes vendored/installed) |

## 9. Alternatives Considered

**Piece B mechanism** — three candidates per the prompt:

- **(a) `ProjectViewNodeDecorator`** — CHOSEN. Non-destructive, presentation-only (a suffix label
  via `data.addText`), no workspace/model change, dynamic (recomputes from
  `getProjectSourcePathPatterns`, so it tracks ROCKS-05 changes without persistence). A real
  platform example exists (`ScratchFileServiceImpl$FilePresentation`). Lowest risk, fits the
  "visual mark" requirement exactly.
- **(b) `FileIconProvider`/`IconProvider`** — viable for a folder-icon swap, but it can only change
  the icon, not add a label, and competes with `PsiBasedFileIconProvider` ordering; less expressive
  than (a) for conveying "this is a rock source root". Rejected as strictly weaker than (a).
- **(c) Workspace/content-entry `SourceFolder` marking** — gives true source-root semantics but
  mutates and persists the module model (`.iml`/workspace), risks conflicts with the IDE's own
  module config and with `additionalLibraryRootsProvider` scoping, and is heavy/destructive. The
  requirement is a non-destructive visual mark, so (c) is over-scoped; deferred (risks-and-gaps).

**Piece A** — folding the installed tree into the existing
`PlatformLibraryProvider.ExternalLibraries` was rejected because that provider intentionally
filters to out-of-project paths (`PlatformLibraryProvider.kt:62`); a separate provider mirroring
the established pattern is cleaner and keeps the two concerns independent.

## 10. Open Questions

_None — feature has cleared the planning bar._
