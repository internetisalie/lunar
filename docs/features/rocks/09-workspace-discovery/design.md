---
id: "ROCKS-09-DESIGN"
title: "Technical Design"
type: "design"
status: "done"
parent_id: "ROCKS-09"
folders:
  - "[[features/rocks/09-workspace-discovery/requirements|requirements]]"
---

# Technical Design: ROCKS-09 — Multi-Rock Workspace Discovery

## 1. Architecture Overview

### Current State

- `LuaRocksTreeLocator.projectRockspec(project)`
  ([LuaRocksTreeLocator.kt:40-46](../../../../src/main/kotlin/net/internetisalie/lunar/rocks/LuaRocksTreeLocator.kt))
  lists only `base.listDirectoryEntries()` (project root) and returns the single
  most-recently-modified `*.rockspec`. Subdirectories are never scanned.
- `LuaRocksDependencyResolver.resolve(project)`
  ([LuaRocksDependencyResolver.kt:15-25](../../../../src/main/kotlin/net/internetisalie/lunar/rocks/LuaRocksDependencyResolver.kt))
  calls `projectRockspec` → for a `rocks/<name>/<name>.rockspec` project it gets `null` →
  resolves nothing.
- `DependencyTreePanel.refresh`
  ([DependencyTreePanel.kt:74-88](../../../../src/main/kotlin/net/internetisalie/lunar/rocks/ui/DependencyTreePanel.kt))
  calls `resolve(project)` on a pooled thread and renders one root.
- `LuaRocksTemplates.workspaceLua` ([LuaRocksTemplates.kt:93-105](../../../../src/main/kotlin/net/internetisalie/lunar/rocks/init/LuaRocksTemplates.kt))
  and `LuaRocksScaffolder.scaffoldWorkspace` ([LuaRocksScaffolder.kt:70-85](../../../../src/main/kotlin/net/internetisalie/lunar/rocks/init/LuaRocksScaffolder.kt))
  generate a `workspace.lua` that **no runtime code reads** (verified: grep `workspace` across
  `rocks/` yields only `init/` scaffolding, the `LuaRocksGeneratorPeer` UI, and tests).
- `enum RockKind { SINGLE_ROCK, WORKSPACE }` + `workspaceName`/`initialRocks` fields
  ([LuaRocksProjectSettings.kt:3,14-15](../../../../src/main/kotlin/net/internetisalie/lunar/rocks/init/LuaRocksProjectSettings.kt));
  the WORKSPACE branch is only reachable from the generator-peer Workspace radio
  ([LuaRocksGeneratorPeer.kt:31,96](../../../../src/main/kotlin/net/internetisalie/lunar/rocks/init/LuaRocksGeneratorPeer.kt)).

### Prior Art in This Repo

- **`LuaRocksTreeLocator`** — the existing locator. This design **extends** it (delegates the
  recursive scan into a new service; keeps `treeRoot`/`installedRocks` unchanged) and **replaces**
  `projectRockspec`'s single-root role with the new discovery service.
- **`LuaRocksDependencyResolver`** — **extended** with `resolveAll`; `resolve` is kept as a thin
  back-compat shim.
- **ROCKS-05 `allProjectRockspecs`** — *planned but not yet implemented* (verified: no
  `allProjectRockspecs` symbol exists in `src/`; it appears only in
  `docs/features/rocks/05-module-resolution/`). To avoid two recursive scanners, ROCKS-09 owns
  the scan and ROCKS-05's method becomes a **delegate** (ROCKS-09-08). This design does **not**
  duplicate ROCKS-05; it supersedes the recursion ROCKS-05 sketched.
- **Enumeration API** — `FilenameIndex.getAllFilesByExt(project, ext, scope)` exists
  ([FilenameIndex.java:217-240](../../../../../intellij-community/platform/indexing-api/src/com/intellij/psi/search/FilenameIndex.java)),
  and `FilenameIndex.getVirtualFilesByName(...)` is already used in Lunar
  ([LuaRequireReference.kt:37](../../../../src/main/kotlin/net/internetisalie/lunar/lang/LuaRequireReference.kt),
  [LuaTypeManagerImpl.kt:116](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaTypeManagerImpl.kt)).
- **Project-level cached service pattern** — `LuaTypeManagerImpl`
  ([LuaTypeManagerImpl.kt:28-50](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaTypeManagerImpl.kt))
  and `GlobalSymbolRankingService` (`@Service(Service.Level.PROJECT)`,
  [GlobalSymbolRankingService.kt:30](../../../../src/main/kotlin/net/internetisalie/lunar/lang/completion/GlobalSymbolRankingService.kt))
  are the templates we follow.

### Target State

A new project service `LuaRockspecDiscoveryService` owns recursive, exclusion-aware,
**cached** rockspec discovery. `LuaRocksTreeLocator` and `LuaRocksDependencyResolver` consume it;
ROCKS-05 will too. Discovery is index-backed (`FilenameIndex`), never a raw `nio` tree walk.

```
DependencyTreePanel ─► LuaRocksDependencyResolver.resolveAll
                                    │
LuaRocksTreeLocator.allProjectRockspecs ─► LuaRockspecDiscoveryService.discoverRockspecPaths
ROCKS-05 allProjectRockspecs (delegate) ┘            │  (CachedValue, PsiModificationTracker)
                                                     ▼
                                  FilenameIndex.getAllFilesByExt(project,"rockspec",projectScope)
                                                     │  filter: ExclusionFilter (§3.2/§3.3)
                                                     ▼
                                          List<DiscoveredRockspec>
```

## 2. Core Components

### 2.1 `net.internetisalie.lunar.rocks.LuaRockspecDiscoveryService`

- **Responsibility**: recursively discover and cache the project's source rockspecs, applying
  the built-in exclusions and any per-project override globs.
- **Threading**: read-only; callers must be off-EDT. Index access is wrapped in
  `runReadAction { }`. Holds only `Project` (constructor-injected), never `VirtualFile`.
- **Collaborators**: `FilenameIndex.getAllFilesByExt` (enumeration),
  `GlobalSearchScope.projectScope(project)`, `LuaProjectSettings.getInstance(project).state`
  (override globs), `CachedValuesManager`, `PsiModificationTracker`.
- **Key API**:
  ```kotlin
  @Service(Service.Level.PROJECT)
  class LuaRockspecDiscoveryService(private val project: Project) {
      /** Cached, sorted; one entry per discovered source rockspec (path + parsed identity).
       *  The SOLE project-rockspec scanner — ROCKS-05 and ROCKS-10 consume this. */
      fun discoverRockspecPaths(): List<DiscoveredRockspec>

      companion object {
          fun getInstance(project: Project): LuaRockspecDiscoveryService =
              project.getService(LuaRockspecDiscoveryService::class.java)
      }
  }

  /** A discovered source rockspec and its parsed identity. */
  data class DiscoveredRockspec(
      val rockspec: Path,         // java.nio.file.Path to the .rockspec file
      val packageName: String?,   // null when the bridge read fails (still discovered)
  )
  ```
- **Public contract for consumers (ROCKS-05) — LOCKED**: `LuaRockspecDiscoveryService` is the
  **sole** project-rockspec scanner (`FilenameIndex`-backed, no depth cap). Its public consumer
  contract is exactly:
  ```kotlin
  // instance method on the @Service(PROJECT); obtain via getInstance(project)
  fun discoverRockspecPaths(): List<DiscoveredRockspec>
  // DiscoveredRockspec(rockspec: java.nio.file.Path, packageName: String?)
  ```
  This is the **single** discovery method (no `List<Path>` overload — that would be a same-name
  return-type clash). It returns one `DiscoveredRockspec` per rockspec, carrying the rockspec
  `Path` plus its `packageName`. ROCKS-09 does **not** parse `build.modules`, and
  `DiscoveredRockspec` must **NOT** carry a `buildModules` field. Consumers take the `.rockspec`
  paths and call `RockspecBridge.read` themselves for `build.modules` (ROCKS-05) or dependencies
  (ROCKS-10), and read `.packageName` for identity. ROCKS-05/10 must not define their own scanner —
  they consume this one.
- **Caching field** (mirrors `LuaTypeManagerImpl`):
  ```kotlin
  private val pathCache: CachedValue<List<Path>> =
      CachedValuesManager.getManager(project).createCachedValue({
          CachedValueProvider.Result.create(
              computePaths(),                        // §3.1 + §3.2/§3.3
              PsiModificationTracker.getInstance(project),
          )
      }, /* trackValue = */ false)
  ```
  `discoverRockspecPaths()` maps `pathCache.value` through `RockspecBridge.read` for the
  `packageName` (also memoised behind the same tracker via a second `CachedValue<List<DiscoveredRockspec>>`).

### 2.2 `net.internetisalie.lunar.rocks.RockspecExclusionFilter`

- **Responsibility**: decide whether a project-relative rockspec path is included.
- **Threading**: pure function, no platform access.
- **Collaborators**: none (operates on `Path` strings); override globs come from settings.
- **Key API**:
  ```kotlin
  object RockspecExclusionFilter {
      val BUILT_IN_EXCLUDED_SEGMENTS = listOf("lua_modules", ".luarocks", "output", "thirdparty")
      val BUILD_SEGMENT_GLOB = "build*"   // matched per-segment, case-insensitive

      fun isIncluded(relativePath: String, includeGlobs: List<String>, excludeGlobs: List<String>): Boolean
  }
  ```

### 2.3 Modification: `net.internetisalie.lunar.rocks.LuaRocksTreeLocator`

- **Responsibility**: keep `treeRoot`/`installedRocks`; **remove** `projectRockspec`; add the
  ROCKS-05 delegate.
- **Key change** at [LuaRocksTreeLocator.kt:40-46](../../../../src/main/kotlin/net/internetisalie/lunar/rocks/LuaRocksTreeLocator.kt):
  ```kotlin
  // DELETE projectRockspec(...)
  fun allProjectRockspecs(project: Project): List<Path> =     // ROCKS-09-08 (also ROCKS-05's API)
      LuaRockspecDiscoveryService.getInstance(project).discoverRockspecPaths()
  ```
- **Public contract for consumers (ROCKS-05) — single scanner, LOCKED**: the old single-root
  `LuaRocksTreeLocator.projectRockspec` is **REPLACED**, and the ROCKS-05-planned
  `allProjectRockspecs` is **REPLACED BY (delegates to)** `LuaRockspecDiscoveryService` — there is
  exactly **one** project-rockspec scanner. The locked consumer entry point is
  `LuaRockspecDiscoveryService.discoverRockspecPaths(project): List<DiscoveredRockspec>`
  (`DiscoveredRockspec(rockspec: java.nio.file.Path, packageName: String?)`), which returns
  **PATHS ONLY**. ROCKS-05 must not build its own scanner and must not expect `build.modules` /
  `buildModules` from `DiscoveredRockspec`; it reads `build.modules` itself via `RockspecBridge.read`.

### 2.4 Modification: `net.internetisalie.lunar.rocks.LuaRocksDependencyResolver`

- **Responsibility**: resolve one `DependencyNode` per discovered rock; back-compat `resolve`.
- **Threading**: background only (each `RockspecBridge.read` blocks — unchanged).
- **Key change** at [LuaRocksDependencyResolver.kt:15-27](../../../../src/main/kotlin/net/internetisalie/lunar/rocks/LuaRocksDependencyResolver.kt):
  ```kotlin
  fun resolveAll(project: Project): List<DependencyNode> {
      val installed = LuaRocksTreeLocator.installedRocks(project).groupBy { it.packageName.lowercase() }
      return LuaRockspecDiscoveryService.getInstance(project).discoverRockspecPaths()
          .mapNotNull { resolveOne(project, it, installed) }
  }

  // Back-compat for the existing DependencyTreePanel caller and TC parity.
  fun resolve(project: Project): DependencyNode? = resolveAll(project).firstOrNull()

  private fun resolveOne(project: Project, rockspec: Path, installed: Map<String, List<InstalledRock>>): DependencyNode? {
      val rootData = RockspecBridge.read(project, rockspec) ?: return null
      val context = ResolutionContext(project, installed)
      val root = DependencyNode(rootData.packageName, isTransitive = false)
      context.seen[rootData.packageName.lowercase()] = root
      expand(root, rootData.dependencies, context,
             visiting = setOf(rootData.packageName.lowercase()), parentIsRoot = true)
      return root
  }
  ```
  `expand`/`childFor`/`recurseInto`/`ResolutionContext` are unchanged; only the entry point
  and the installed-index hoist move. **Note**: `ResolutionContext.seen` must be per-root (one
  fresh context per `resolveOne`) so two rocks depending on the same package each get their own
  subtree.

### 2.5 Modification: `net.internetisalie.lunar.rocks.ui.DependencyTreePanel`

- **Responsibility**: render a forest (one Swing child node per resolved root).
- **Key change** at [DependencyTreePanel.kt:74-99](../../../../src/main/kotlin/net/internetisalie/lunar/rocks/ui/DependencyTreePanel.kt):
  call `resolveAll(project)`; hold `resolvedRoots: List<DependencyNode>`; in `rebuildTree`,
  attach each root as a child of the synthetic `"Lua dependencies"` Swing root (the panel
  already has a synthetic root and an `addChildren` recursion — iterate roots instead of one).
  `VersionConflictEngine.annotate` is called per root.

### 2.6 Modifications: scaffolding removal (ROCKS-09-06)

- `LuaRocksTemplates`: **delete** `workspaceLua` ([LuaRocksTemplates.kt:93-105](../../../../src/main/kotlin/net/internetisalie/lunar/rocks/init/LuaRocksTemplates.kt)).
- `LuaRocksScaffolder`: **delete** `scaffoldWorkspace` ([LuaRocksScaffolder.kt:70-85](../../../../src/main/kotlin/net/internetisalie/lunar/rocks/init/LuaRocksScaffolder.kt))
  and collapse `scaffold` to call `scaffoldSingleRock` unconditionally
  ([LuaRocksScaffolder.kt:20-25](../../../../src/main/kotlin/net/internetisalie/lunar/rocks/init/LuaRocksScaffolder.kt)).
- `LuaRocksProjectSettings`: **delete** `enum class RockKind`, the `kind` field, and the
  `workspaceName`/`initialRocks` fields ([LuaRocksProjectSettings.kt:3,8,14-15](../../../../src/main/kotlin/net/internetisalie/lunar/rocks/init/LuaRocksProjectSettings.kt)).
  `enum class RockType { LIBRARY, APPLICATION }` is **kept**.
- `LuaRocksGeneratorPeer`: remove the `singleRockButton`/`workspaceButton` `ButtonGroup`, the
  `workspaceNameField`/`initialRocksField`, the "Project kind" section, and the kind/workspace
  wiring in `getSettings` ([LuaRocksGeneratorPeer.kt:30-31,40-41,46-49,65-67,78-79,96,108-109](../../../../src/main/kotlin/net/internetisalie/lunar/rocks/init/LuaRocksGeneratorPeer.kt)).
- Tests: delete the two `workspaceLua` tests
  ([LuaRocksTemplatesTest.kt:103-113](../../../../src/test/kotlin/net/internetisalie/lunar/rocks/init/LuaRocksTemplatesTest.kt))
  and the WORKSPACE scaffolder test
  ([LuaRocksScaffolderTest.kt:140-144](../../../../src/test/kotlin/net/internetisalie/lunar/rocks/init/LuaRocksScaffolderTest.kt)).

### 2.7 Modification: `net.internetisalie.lunar.settings.LuaProjectSettings.State` (ROCKS-09-07)

Add two glob lists to `State` ([LuaProjectSettings.kt:44-63](../../../../src/main/kotlin/net/internetisalie/lunar/settings/LuaProjectSettings.kt)),
following the existing `additionalGlobals: MutableList<String>` precedent (xmlb-serialisable):
```kotlin
var rockspecIncludeGlobs: MutableList<String> = mutableListOf()
var rockspecExcludeGlobs: MutableList<String> = mutableListOf()
```
No new file; persisted in the existing `lunar.xml` storage. No UI panel is required for the
default behaviour; a settings-UI field is deferred (risks-and-gaps.md).

## 3. Algorithms

### 3.1 Recursive discovery (`computePaths`)

- **Input → Output**: `Project` → `List<Path>` (sorted, included only).
- **Steps**:
  1. `val basePath = project.basePath ?: return emptyList()`; `val base = Path.of(basePath)`.
  2. Inside `runReadAction`:
     `val scope = GlobalSearchScope.projectScope(project)`
     `val files: Collection<VirtualFile> = FilenameIndex.getAllFilesByExt(project, "rockspec", scope)`.
  3. For each `vf` (call `ProgressManager.checkCanceled()` each iteration):
     - `val path = vf.fileSystem.getNioPath(vf) ?: vf.toNioPath()` (fallback: `Path.of(vf.path)`).
     - Skip if `!path.startsWith(base)` (out-of-project link).
     - `val rel = base.relativize(path).toString().replace('\\','/')`.
     - Keep if `RockspecExclusionFilter.isIncluded(rel, include, exclude)` (§3.2/§3.3) where
       `include`/`exclude` come from `LuaProjectSettings.getInstance(project).state`.
  4. Sort the kept paths by `base.relativize(it).toString().replace('\\','/').lowercase()`.
- **Rules / edge handling**: null base path → empty; index returns case-insensitively-matched
  names (`getAllFilesByExt` already lowercases the extension compare). No depth cap — the index
  enumeration is already bounded to project files.
- **Complexity**: O(k) where k = number of `*.rockspec` files in the project (index lookup),
  **not** O(all project files). No filesystem walk.

### 3.2 Built-in exclusion predicate (`RockspecExclusionFilter.isIncluded`, default path)

When `includeGlobs` and `excludeGlobs` are both empty:
- **Input**: `relativePath` (project-relative, `/`-normalised, e.g. `rocks/adt/adt-1.0-1.rockspec`).
- **Steps**:
  1. `val segments = relativePath.split('/').dropLast(1)` (directory segments only; the filename
     is never tested against the exclusion set — this is what makes TC #4 pass).
  2. For each `seg` (compared `lowercase()`):
     - excluded if `seg in {"lua_modules", ".luarocks", "output", "thirdparty"}`, **or**
     - excluded if `seg.startsWith("build")` (covers `build`, `build-5.4`, `builddir`).
  3. Included iff no segment is excluded.
- **Rationale for `startsWith("build")`**: matches the `build*/` glob from requirements without a
  full glob engine for the built-in case; documented edge (a dir literally named `build` or
  `build-anything` is excluded — acceptable, these are LuaRocks output dirs).

### 3.3 Override predicate (`RockspecExclusionFilter.isIncluded`, override path) — ROCKS-09-07

When either glob list is non-empty, **built-in exclusions are ANDed with the overrides**:
- **Steps**:
  1. Compute `builtinIncluded` per §3.2.
  2. `val matcher = { glob: String -> FileSystems.getDefault().getPathMatcher("glob:$glob")
        .matches(Path.of(relativePath)) }`.
  3. If `excludeGlobs` is non-empty and any `matcher(g)` for `g in excludeGlobs` → **excluded**.
  4. If `includeGlobs` is non-empty: included iff some `matcher(g)` for `g in includeGlobs`
     matches (include acts as an allow-list); if `includeGlobs` is empty, fall through.
  5. Final: `builtinIncluded && notExcludedByGlob && (includeGlobsEmpty || matchesAnInclude)`.
- **Edge handling**: globs are matched against the **project-relative, `/`-normalised** string
  (so `vendor/**` and `a/**` behave as in TC #10/#11). Invalid glob → log warn, treat that glob
  as non-matching (does not throw).

### 3.4 Multi-root resolution (`resolveAll`) — see §2.4

One fresh `ResolutionContext` per discovered rockspec so `seen`/`visiting` are per-root;
`installed` index is computed once and shared (read-only). Order of roots follows §3.1 sort.

## 4. External Data & Parsing

No new external format. Package identity for `DiscoveredRockspec.packageName` and dependency
data both come from the **existing** `RockspecBridge.read(project, path): RockspecData?`
([RockspecBridge.kt:31-46](../../../../src/main/kotlin/net/internetisalie/lunar/rocks/RockspecBridge.kt)),
which runs the bundled `rockspec.lua` bridge and parses its JSON (`package`, `version`,
`dependencies`). ROCKS-09 adds no parsing; it only changes *which* rockspecs are fed in.

## 5. Data Flow

### Example 1: Kernel/v0 (no root rockspec) → dependency forest

1. `DependencyTreePanel.refresh` (pooled thread) → `LuaRocksDependencyResolver.resolveAll`.
2. `resolveAll` → `LuaRockspecDiscoveryService.discoverRockspecPaths` →
   `FilenameIndex.getAllFilesByExt(project,"rockspec",projectScope)` returns 10 VFs under
   `rocks/<name>/`; none under an excluded dir → all 10 kept, sorted.
3. `resolveAll` maps each path through `resolveOne` (fresh context each) → 10 roots
   (`adt`, `channels`, `cmd`, `meteor`, `pipe`, `platform`, `ramdisk`, `runtime`, `ssdpd`,
   `utils`), each `isTransitive == false`.
4. `VersionConflictEngine.annotate` per root; panel attaches 10 roots under "Lua dependencies".

### Example 2: project with a vendored rockspec

1. `discoverRockspecPaths` enumerates `foo-scm-1.rockspec` (root) and
   `thirdparty/dkjson/dkjson-2.5-1.rockspec`.
2. `RockspecExclusionFilter.isIncluded("thirdparty/dkjson/dkjson-2.5-1.rockspec", …)` →
   segment `thirdparty` excluded → dropped. Only `foo` is resolved.

## 6. Edge Cases

- **No `project.basePath`** (default/light test project) → empty list; callers show "no rocks".
- **Rockspec outside project root via symlink** → `!path.startsWith(base)` drops it.
- **Bridge fails for one rockspec** → still in `discoverRockspecPaths`; `resolveOne` returns null
  → that root is omitted from `resolveAll` (warning logged), others unaffected.
- **Duplicate package names across rocks** → both retained (keyed by path); the per-root context
  keeps their subtrees independent.
- **Windows paths** → relativised string is `\\`→`/` normalised before filtering/sorting.
- **Dumb mode** → `FilenameIndex` requires indexes; guard with `DumbService.isDumb(project)` →
  return empty (parity with `GlobalSymbolRankingService`), and let the panel's refresh retry.

## 7. Integration Points

`LuaRockspecDiscoveryService` is a project-level light service; project light services
(`@Service(Service.Level.PROJECT)`) do **not** require a `plugin.xml` entry — they are
instantiated on `project.getService(...)`, exactly like `GlobalSymbolRankingService`
([GlobalSymbolRankingService.kt:30](../../../../src/main/kotlin/net/internetisalie/lunar/lang/completion/GlobalSymbolRankingService.kt))
which has no `plugin.xml` registration. No new extension points, actions, or listeners are
registered. The `RockType`-only generator continues to use its existing
`projectTemplatesFactory`/generator registration (unchanged). The settings additions (§2.7) are
serialised by the existing `LuaProjectSettings` `@State` storage (`lunar.xml`) — no new
`<applicationService>`/`<projectService>` tag.

_No `plugin.xml` change is required for this feature._

## 8. Requirement Coverage

| Requirement | Priority | Implemented by (section) |
|-------------|----------|--------------------------|
| ROCKS-09-01 Recursive Discovery | M | §2.1, §3.1 |
| ROCKS-09-02 Exclusion | M | §2.2, §3.2 |
| ROCKS-09-03 Discovered Set API | M | §2.1 |
| ROCKS-09-04 Cached/Invalidated | M | §2.1 (CachedValue + PsiModificationTracker) |
| ROCKS-09-05 Multi-Root Resolution | M | §2.4, §2.5, §3.4 |
| ROCKS-09-06 Remove `workspace.lua` | M | §2.6 |
| ROCKS-09-07 Membership Override | S | §2.2, §2.7, §3.3 |
| ROCKS-09-08 ROCKS-05 Delegation | S | §2.3 |

## 9. Alternatives Considered

- **Raw `nio` `Files.walk(base, depth)`** (as ROCKS-05's draft §3.1 sketched): rejected.
  It bypasses the index (re-walks the disk on every call, harder to cache, sees ignored/excluded
  dirs, blocks on I/O), and would need a hand-tuned depth cap (`Kernel/v0` is depth 2 but other
  layouts vary). `FilenameIndex.getAllFilesByExt` is index-backed, project-scoped, and already
  used in Lunar — strictly better on perf and correctness. The shared service means ROCKS-05's
  walk is replaced, not duplicated.
- **`FileTypeIndex`**: rejected — `.rockspec` is **not** registered as a distinct `FileType` in
  this repo (no `RockspecFileType`; verified by grep), so `FileTypeIndex` would not key on it.
- **VFS `BulkFileListener` hand-rolled cache**: viable but heavier; the
  `CachedValuesManager` + `PsiModificationTracker` approach (proven in `LuaTypeManagerImpl`)
  already invalidates on structural VFS/PSI change and is the repo idiom. Chosen for consistency.
- **Keep `workspace.lua` as the discovery input**: rejected — it is orphan (nothing reads it),
  and tying discovery to a generated manifest would miss imported/cloned multi-rock projects
  (e.g. `Kernel/v0`, which has no such file).

## 10. Open Questions

_None — feature has cleared the planning bar._
