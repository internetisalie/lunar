---
id: "ROCKS-05-DESIGN"
title: "Technical Design"
type: "design"
parent_id: "ROCKS-05"
folders:
  - "[[features/rocks/05-module-resolution/requirements|requirements]]"
---

# Technical Design: ROCKS-05 — Rockspec Module Resolution

## 1. Architecture Overview

### Current State

- `PathConfiguration.getProjectSourcePathPatterns(project)`
  ([SourcePathPattern.kt:19](../../../../src/main/kotlin/net/internetisalie/lunar/lang/path/SourcePathPattern.kt))
  builds patterns only from user settings (`expandSourcePath`) or the hard-coded
  `DEFAULT_SOURCE_PATH`. It has no knowledge of rockspec `build.modules`.
- `RockspecBridge.parse()`
  ([RockspecBridge.kt:49](../../../../src/main/kotlin/net/internetisalie/lunar/rocks/RockspecBridge.kt))
  reads only `package`/`version`/`dependencies`; the `build` field, although exported to stdout
  by the bridge ([rockspec.lua:27](../../../../src/main/resources/lua/rockspec.lua)), is dropped.
- Run/debug `LUA_PATH` is injected in the `else` branch of `LuaRunConfiguration`
  ([LuaRunConfiguration.kt:262-272](../../../../src/main/kotlin/net/internetisalie/lunar/run/LuaRunConfiguration.kt))
  and `LuaTestCommandLineState.configureLuaPath`
  ([LuaTestCommandLineState.kt:133-144](../../../../src/main/kotlin/net/internetisalie/lunar/run/test/LuaTestCommandLineState.kt))
  straight from `state.expandSourcePath(project)` — rockspec roots are not unioned in.

### Prior Art in This Repo

- **`PathConfiguration.getProjectSourcePathPatterns`** — the single source-path chokepoint.
  This design **extends** it (appends rockspec-derived patterns); it does **not** add a parallel
  resolver. `LuaRequireReference.resolve()`
  ([LuaRequireReference.kt:21](../../../../src/main/kotlin/net/internetisalie/lunar/lang/LuaRequireReference.kt)),
  `LuaModulePathResolver`
  ([LuaModulePathResolver.kt:18](../../../../src/main/kotlin/net/internetisalie/lunar/lang/path/LuaModulePathResolver.kt)),
  and `LuaFileBindingsIndex`
  ([LuaFileBindingsIndex.kt:180](../../../../src/main/kotlin/net/internetisalie/lunar/lang/indexing/LuaFileBindingsIndex.kt))
  inherit the extension with no change.
- **`RockspecBridge`** — **extended**: `RockspecData` gains build fields, `parse()` populates them.
- **`LuaRockspecDiscoveryService` (ROCKS-09)** — the SOLE project-rockspec scanner
  ([../09-workspace-discovery/design.md](../09-workspace-discovery/design.md) section 2.1).
  ROCKS-05 **consumes** `discoverRockspecPaths(project)`; it does **not** add a scanner.
- **`LuaTypeManagerImpl`** ([LuaTypeManagerImpl.kt:28-50](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaTypeManagerImpl.kt))
  and `GlobalSymbolRankingService` (`@Service(Service.Level.PROJECT)`,
  [GlobalSymbolRankingService.kt:30](../../../../src/main/kotlin/net/internetisalie/lunar/lang/completion/GlobalSymbolRankingService.kt))
  — the project-light-service + `CachedValuesManager`/`PsiModificationTracker` template followed
  by `RockspecSourcePathProvider`.
- **`LuaRocksTreeLocator.treeRoot`** ([LuaRocksTreeLocator.kt:33](../../../../src/main/kotlin/net/internetisalie/lunar/rocks/LuaRocksTreeLocator.kt))
  — reused for the C-module `LUA_CPATH` built-tree root.

### Target State

A pure derivation object `RockspecModuleDerivation` turns one rockspec's `build.modules`
(read via `RockspecBridge.read`) into source-root `SourcePathPattern`s. A project light service
`RockspecSourcePathProvider` consumes ROCKS-09 discovery, calls the bridge per path, derives and
caches the union; `PathConfiguration` appends its output (consumer A). A stateless
`RockspecRunPathProvider` reuses the same provider to build the run/debug `LUA_PATH` prefix and
the C-module `LUA_CPATH` (consumer B).

```
(A) PathConfiguration.getProjectSourcePathPatterns
        |  append
        v
RockspecSourcePathProvider.derivedPatterns()  (CachedValue, PsiModificationTracker)
        |
        +-- LuaRockspecDiscoveryService.discoverRockspecPaths(project)   (ROCKS-09; PATHS ONLY)
        +-- per path: RockspecBridge.read(project, path) -> RockspecData.luaModules
        +-- per rockspec: RockspecModuleDerivation.derive(dir, luaModules) -> List<SourcePathPattern>
        \-- union + dedup by spec

(B) LuaRunConfiguration / LuaTestCommandLineState  (else branch only)
        |
        v
RockspecRunPathProvider.luaPathPrefix(project) + luaCPath(project)
        \-- reuses RockspecSourcePathProvider.derivedPatterns() + LuaRocksTreeLocator.treeRoot
```

## 2. Core Components

### 2.1 `net.internetisalie.lunar.rocks.RockspecModuleDerivation`

- **Responsibility**: pure transform from one rockspec's directory + `build.modules` Lua map to
  deduplicated source-root `SourcePathPattern`s (both `?.lua` and `?/init.lua`).
- **Threading**: pure function, no platform access; safe to call inside the provider's compute.
- **Collaborators**: `SourcePathPattern`
  ([SourcePathPattern.kt:28](../../../../src/main/kotlin/net/internetisalie/lunar/lang/path/SourcePathPattern.kt)).
- **Key API**:
  ```kotlin
  object RockspecModuleDerivation {
      /** dir = rockspec parent dir (absolute, '/'-normalised, no trailing slash). */
      fun derive(dir: String, luaModules: Map<String, String>): List<SourcePathPattern>
  }
  ```
  Implements algorithm section 3.1. Output order is the sorted distinct root list; for each root
  the `?.lua` pattern precedes the `?/init.lua` pattern.

### 2.2 Modification: `net.internetisalie.lunar.lang.path.PathConfiguration` (consumer A)

- **Responsibility**: append rockspec-derived patterns at the existing chokepoint.
- **Key change** at [SourcePathPattern.kt:19-24](../../../../src/main/kotlin/net/internetisalie/lunar/lang/path/SourcePathPattern.kt):
  ```kotlin
  fun getProjectSourcePathPatterns(project: Project): List<SourcePathPattern> {
      val state = LuaProjectSettings.getInstance(project).state
      val luaPath = state.expandSourcePath(project).ifEmpty { DEFAULT_SOURCE_PATH.expandMacros(project) }
      val userPatterns = SourcePathPattern.patternsFromLuaPath(luaPath)
      val rockspecPatterns = RockspecSourcePathProvider.getInstance(project).derivedPatterns()
      return (userPatterns + rockspecPatterns).distinctBy { it.spec }   // user first; dedup
  }
  ```
- **Threading**: callers already invoke this inside read actions / resolution; the provider's
  `derivedPatterns()` returns the cached list and only triggers the (background) bridge compute on
  a cold cache. See section 6 (cold-cache handling).

### 2.3 `net.internetisalie.lunar.rocks.RockspecSourcePathProvider`

- **Responsibility**: project-scoped, cached union of rockspec-derived patterns and the C-module
  flag set; the single seam both consumers read.
- **Threading**: read-only to callers; the compute runs the blocking `RockspecBridge.read` and so
  must be primed off-EDT (section 6). Holds only `Project` (constructor-injected); never a
  `VirtualFile`/`PsiFile`.
- **Collaborators**: `LuaRockspecDiscoveryService.discoverRockspecPaths`
  ([../09-workspace-discovery/design.md](../09-workspace-discovery/design.md) section 2.1),
  `RockspecBridge.read`, `RockspecModuleDerivation`, `CachedValuesManager`,
  `PsiModificationTracker`.
- **Key API**:
  ```kotlin
  @Service(Service.Level.PROJECT)
  class RockspecSourcePathProvider(private val project: Project) {
      /** Cached, deduplicated derived source-root patterns across all project rockspecs. */
      fun derivedPatterns(): List<SourcePathPattern>

      /** Per-rockspec C-module info for the run-side LUA_CPATH (ROCKS-05-05). */
      fun cModuleRockspecs(): List<CModuleRock>

      companion object {
          fun getInstance(project: Project): RockspecSourcePathProvider =
              project.getService(RockspecSourcePathProvider::class.java)
      }
  }

  /** A rockspec that declares at least one builtin C module. */
  data class CModuleRock(val rockspecDir: String, val hasCModules: Boolean)
  ```
- **Caching field** (mirrors `LuaTypeManagerImpl`):
  ```kotlin
  private val cache: CachedValue<List<SourcePathPattern>> =
      CachedValuesManager.getManager(project).createCachedValue({
          CachedValueProvider.Result.create(
              computeDerivedPatterns(),                 // section 3.1 + the discovery+bridge loop
              PsiModificationTracker.getInstance(project),
          )
      }, /* trackValue = */ false)
  ```
  The `PsiModificationTracker` key invalidates on any structural PSI/VFS change, including a
  rockspec edit (ROCKS-05-06). `cModuleRockspecs()` is memoised behind the same tracker.

### 2.4 `net.internetisalie.lunar.rocks.RockspecRunPathProvider` (consumer B)

- **Responsibility**: build the run/debug `LUA_PATH` local-root prefix and the C-module
  `LUA_CPATH` from the cached derivation; edit **only** the `else` (no per-config `sourcePath`)
  branch of the two run states.
- **Threading**: called during process construction (already off-EDT in the run-profile state
  factory). Reads the cached provider; no PSI traversal of its own.
- **Collaborators**: `RockspecSourcePathProvider`, `LuaRocksTreeLocator.treeRoot`
  ([LuaRocksTreeLocator.kt:33](../../../../src/main/kotlin/net/internetisalie/lunar/rocks/LuaRocksTreeLocator.kt)),
  `LuaProjectSettings.getInstance(project).state.languageLevel`
  ([LuaProjectSettings.kt:45](../../../../src/main/kotlin/net/internetisalie/lunar/settings/LuaProjectSettings.kt),
  [LuaLanguageLevel.kt:31](../../../../src/main/kotlin/net/internetisalie/lunar/lang/LuaLanguageLevel.kt)).
- **Key API**:
  ```kotlin
  object RockspecRunPathProvider {
      /** Local roots, '?'-expanded, ending ';' — to PREPEND before the existing project LUA_PATH. */
      fun luaPathPrefix(project: Project): String

      /** "<treeRoot>/lib/lua/<X.Y>/?.so;;" when any builtin C module exists; else null. */
      fun luaCPath(project: Project): String?
  }
  ```
- **Key change (LuaRunConfiguration else branch)** at
  [LuaRunConfiguration.kt:265-272](../../../../src/main/kotlin/net/internetisalie/lunar/run/LuaRunConfiguration.kt):
  ```kotlin
  } else {
      val settingsState = LuaProjectSettings.getInstance(project).state
      val projectPath = settingsState.expandSourcePath(project)
      val prefix = RockspecRunPathProvider.luaPathPrefix(project)   // "<root>/?.lua;...;" or ""
      val union = (prefix + projectPath).trimEnd(';') + ";;"        // local-before-installed
      if (union != ";;") commandLine.withEnvironment("LUA_PATH", union)
      RockspecRunPathProvider.luaCPath(project)?.let { commandLine.withEnvironment("LUA_CPATH", it) }
  }
  ```
  The same edit is applied to `LuaTestCommandLineState.configureLuaPath`'s `else` branch
  ([LuaTestCommandLineState.kt:137-143](../../../../src/main/kotlin/net/internetisalie/lunar/run/test/LuaTestCommandLineState.kt)).
  The per-config `sourcePath` (`if`) branch and the debug preloader injection
  ([LuaRunConfiguration.kt:256-260](../../../../src/main/kotlin/net/internetisalie/lunar/run/LuaRunConfiguration.kt))
  are left untouched.

## 3. Algorithms

### 3.1 `build.modules` -> source root (`RockspecModuleDerivation.derive`)

- **Input -> Output**: `(dir: String, luaModules: Map<String,String>)` -> `List<SourcePathPattern>`.
- **Steps**:
  1. `val roots = LinkedHashSet<String>()`.
  2. For each `(module, source)` in `luaModules` (source `/`-normalised):
     - `val modSlash = module.replace('.', '/')`.
     - Determine `root`:
       - If `source` ends with `"$modSlash/init.lua"`: `root = source.removeSuffix("$modSlash/init.lua")`.
       - Else if `source` ends with `"$modSlash.lua"`: `root = source.removeSuffix("$modSlash.lua")`.
       - Else (mapping not a clean module->path; e.g. flattened name): fall back to
         `root = source.substringBeforeLast('/', "") .let { if (it.isEmpty()) "" else "$it/" }`
         (the source's own directory).
     - Normalise `root` to end with exactly one `/` (or be `""` for the rockspec dir itself).
     - `roots += root`.
  3. For each `root` in `roots` (iteration order = first-seen, then sorted for determinism):
     - `val base = joinDir(dir, root)`  // dir + '/' + root, collapsing the boundary slash
     - emit `SourcePathPattern("$base?.lua")` then `SourcePathPattern("$base?/init.lua")`.
  4. Return the emitted list (caller dedups by `spec` across rockspecs).
- **Worked examples**:
  - `dir="/proj"`, `{"foo.bar":"src/foo/bar.lua"}` -> `modSlash="foo/bar"`, source ends
    `"foo/bar.lua"` -> `root="src/"` -> `"/proj/src/?.lua"`, `"/proj/src/?/init.lua"`.
  - `dir="/proj"`, `{"mymod":"lua/mymod/init.lua"}` -> `modSlash="mymod"`, source ends
    `"mymod/init.lua"` -> `root="lua/"` -> `"/proj/lua/?.lua"`, `"/proj/lua/?/init.lua"`.
  - `dir="/proj/rocks/foo"`, `{"foo":"foo.lua"}` -> `modSlash="foo"`, source ends `"foo.lua"`
    -> `root=""` -> `"/proj/rocks/foo/?.lua"`, `"/proj/rocks/foo/?/init.lua"`.
- **Rules / edge handling**: a module mapping whose source does not end with the module slash
  form takes the directory fallback (step 2 else); C modules are never passed here (they are not
  in `luaModules`). Empty `luaModules` -> empty list.
- **Complexity**: O(m) over module count; no I/O.

### 3.2 Run/debug LUA_PATH union (`RockspecRunPathProvider.luaPathPrefix` + section 2.4 splice)

- **Input -> Output**: `Project` -> `String` prefix; combined with the existing project path in
  the run state.
- **Steps**:
  1. `val patterns = RockspecSourcePathProvider.getInstance(project).derivedPatterns()` (cached
     union across all discovered rockspecs; discovery from ROCKS-09).
  2. `prefix = patterns.joinToString("") { it.spec + ";" }`  // each derived pattern, then ';'
     (so local roots come first, in derivation order).
  3. In the run state: `union = (prefix + expandSourcePath(project)).trimEnd(';') + ";;"`.
     The trailing `;;` is Lua's "append default `package.path`" marker, covering the installed
     tree already on the interpreter's default path.
- **Rules / edge handling**: empty prefix and empty project path -> `union == ";;"`, which the
  splice treats as "set nothing" (keeps current behaviour). Dedup already done in
  `derivedPatterns()`. Local roots ALWAYS precede the project/installed value.

### 3.3 C-module LUA_CPATH (`RockspecRunPathProvider.luaCPath`) — ROCKS-05-05

- **Input -> Output**: `Project` -> `String?`.
- **Steps**:
  1. If `RockspecSourcePathProvider.getInstance(project).cModuleRockspecs().none { it.hasCModules }`
     -> return `null`.
  2. `val tree = LuaRocksTreeLocator.treeRoot(project) ?: return null`.
  3. `val xy = LuaProjectSettings.getInstance(project).state.languageLevel.version`  // e.g. "5.4".
  4. Return `"$tree/lib/lua/$xy/?.so;;"` ('/'-normalised).
- **Rules / edge handling**: C modules are taken from the **built tree**, never source `src/*.c`.
  No built tree -> `null` (no `LUA_CPATH` set). The `.so` suffix is the Unix shared-object name
  LuaRocks installs builtin C modules under (`<tree>/lib/lua/<X.Y>/<name>.so`).

## 4. External Data & Parsing

### 4.1 Bridge `build` JSON object

- **Format**: the bridge already exports `build` in its `require("lunar.export").json` field list
  ([rockspec.lua:27](../../../../src/main/resources/lua/rockspec.lua)); the JSON encoder serialises
  the Lua `build` table as a JSON object. Exact shape consumed by `RockspecBridge.parse()`:
  ```json
  {
    "package": "foo",
    "build": {
      "type": "builtin",
      "modules": {
        "foo.bar":  "src/foo/bar.lua",
        "foo.init": "src/foo/init.lua",
        "cjson":    ["src/cjson.c", "src/strbuf.c"]
      }
    }
  }
  ```
  - `build` may be absent; `build.type` may be absent; `build.modules` may be absent.
  - A `modules` entry value is **either** a JSON string (Lua source path) **or** a JSON array of
    strings (C source files).
- **Parse strategy** (extends `RockspecBridge.parse`,
  [RockspecBridge.kt:49-68](../../../../src/main/kotlin/net/internetisalie/lunar/rocks/RockspecBridge.kt),
  reusing the existing `com.google.gson` `JsonObject` already in scope):
  ```kotlin
  val buildObj = obj.get("build")?.takeIf { it.isJsonObject }?.asJsonObject
  val buildType = buildObj?.get("type")?.takeIf { it.isJsonPrimitive }?.asString
  val modulesObj = buildObj?.get("modules")?.takeIf { it.isJsonObject }?.asJsonObject
  val luaModules = LinkedHashMap<String, String>()
  val cModules = LinkedHashMap<String, List<String>>()
  modulesObj?.entrySet()?.forEach { (name, value) ->
      when {
          value.isJsonPrimitive -> luaModules[name] = value.asString
          value.isJsonArray -> cModules[name] =
              value.asJsonArray.filter { it.isJsonPrimitive }.map { it.asString }
          else -> Unit   // object/null -> ignored
      }
  }
  ```
- **Maps to**: new `RockspecData` fields `buildType: String?`, `luaModules: Map<String,String>`,
  `cModules: Map<String,List<String>>`.
- **Failure handling**: malformed/absent `build` -> all three default to null/empty (TC #2);
  no exception thrown; existing `package`/`version`/`dependencies` parsing unchanged.

## 5. Data Flow

### Example 1: workspace rock, IDE navigation (consumer A)

1. `require("foo.bar")` Ctrl+click -> `LuaRequireReference.resolve` ->
   `PathConfiguration.getProjectSourcePathPatterns(project)`.
2. `RockspecSourcePathProvider.derivedPatterns()` (cache warm) ->
   `discoverRockspecPaths()` returned `/proj/rocks/foo/foo-1.0-1.rockspec` ->
   `RockspecBridge.read` -> `luaModules={"foo.bar":"src/foo/bar.lua"}` ->
   `derive("/proj/rocks/foo", ...)` -> `/proj/rocks/foo/src/?.lua`, `.../?/init.lua`.
3. `interpolate("foo.bar")` on `/proj/rocks/foo/src/?.lua` -> `/proj/rocks/foo/src/foo/bar.lua`;
   reference resolves to that file (TC #6).

### Example 2: 2-rock run from source (consumer B)

1. User Runs a script; no per-config `sourcePath` -> `else` branch.
2. `RockspecRunPathProvider.luaPathPrefix` -> patterns for rock `a` (`<a>/src/?.lua`,
   `<a>/src/?/init.lua`) and `b` (`<b>/lua/?.lua`, `<b>/lua/?/init.lua`) ->
   prefix `"<a>/src/?.lua;<a>/src/?/init.lua;<b>/lua/?.lua;<b>/lua/?/init.lua;"`.
3. `union = prefix + projectPath`, trimmed, `+ ";;"` -> set as `LUA_PATH`; local roots precede
   installed (TC #7).

### Example 3: C-module rock (consumer B, LUA_CPATH)

1. Rock declares `build.modules={"cjson":["src/cjson.c"]}`, `languageLevel=LUA54`, tree
   `/proj/lua_modules`.
2. `luaCPath` -> `/proj/lua_modules/lib/lua/5.4/?.so;;` set as `LUA_CPATH`; `src/cjson.c` never
   enters any path (TC #8).

## 6. Edge Cases

- **No `project.basePath` / no rockspecs**: `derivedPatterns()` -> empty; consumers unchanged.
- **Cold cache on EDT**: `derivedPatterns()` triggers a bridge compute that must not block the
  EDT. The provider computes on first off-EDT access (resolution/indexing/run construction all
  run off-EDT); if first hit is on EDT it returns empty and schedules a background prime via
  `ReadAction.nonBlocking { cache.value }.submit(...)`, then the next access is warm — same
  pattern as `GlobalSymbolRankingService`'s dumb-mode guard. (De-risk DR-01.)
- **Bridge fails for one rockspec**: that rockspec contributes nothing (warn logged); others
  unaffected (Behavior Rule 5).
- **`build.type` non-builtin (`make`/`cmake`)**: `luaModules` typically empty -> no source roots
  (Out of Scope); `cModules` ignored for CPATH only when builtin (section 3.3 step 1 checks
  `hasCModules` which is only set for builtin entries — see risks-and-gaps Gap 2.2).
- **Windows paths**: rockspec dir and source strings are `\\`->`/` normalised before derivation.
- **Duplicate roots across rockspecs**: `distinctBy { it.spec }` in section 2.2 collapses them.

## 7. Integration Points

`RockspecSourcePathProvider` is a project light service (`@Service(Service.Level.PROJECT)`).
Project light services do **not** require a `plugin.xml` entry — they are instantiated on
`project.getService(...)`, exactly like `GlobalSymbolRankingService`
([GlobalSymbolRankingService.kt:30](../../../../src/main/kotlin/net/internetisalie/lunar/lang/completion/GlobalSymbolRankingService.kt)),
which has no `plugin.xml` registration. `RockspecModuleDerivation` and `RockspecRunPathProvider`
are stateless objects (no registration). `RockspecData`'s new fields ride the existing bridge.
No new extension points, actions, listeners, settings keys, or indexes are added.

_No `plugin.xml` change is required for this feature._

## 8. Cross-Feature Contract

**ROCKS-09 owns discovery.** `LuaRockspecDiscoveryService.discoverRockspecPaths(project):
List<DiscoveredRockspec>` ([../09-workspace-discovery/design.md](../09-workspace-discovery/design.md)
section 2.1) is the SOLE project-rockspec scanner (`FilenameIndex`-backed, no depth cap).
`DiscoveredRockspec(rockspec: java.nio.file.Path, packageName: String?)` carries **paths only** —
it has **no** `buildModules` field. ROCKS-05 consumes those paths and calls
`RockspecBridge.read(project, rockspec)` itself to obtain `build.modules`. ROCKS-05 defines no
scanner (`Files.walk` / `allProjectRockspecs` / depth cap / exclusion set). A single-project
**TEST-ONLY** stub of the discovery seam is permitted in unit tests (clearly labelled), never the
production path.

## 9. Requirement Coverage

| Requirement | Priority | Implemented by (section) |
|-------------|----------|--------------------------|
| ROCKS-05-01 Build Modules Parsing | M | section 4.1, section 2.3 (RockspecData fields) |
| ROCKS-05-02 Source-Root Derivation | M | section 2.1, section 3.1 |
| ROCKS-05-03 IDE Path Integration (A) | M | section 2.2 |
| ROCKS-05-04 Run/Debug LUA_PATH Union (B) | M | section 2.4, section 3.2 |
| ROCKS-05-05 C-Module LUA_CPATH | S | section 2.4, section 3.3 |
| ROCKS-05-06 Invalidation | S | section 2.3 (CachedValue + PsiModificationTracker) |

## 10. Alternatives Considered

- **A ROCKS-05-owned `Files.walk`/`allProjectRockspecs` scanner** (the reverted draft): rejected.
  It would duplicate ROCKS-09's `FilenameIndex`-backed `LuaRockspecDiscoveryService`, re-walk the
  disk per call, need a hand-tuned depth cap, and see excluded/installed-rock rockspecs. ROCKS-09
  is the single, index-backed, exclusion-aware scanner; ROCKS-05 consumes
  `discoverRockspecPaths()`.
- **A parallel rockspec require-resolver** (separate from `PathConfiguration`): rejected — it
  would bypass the single chokepoint and force per-caller changes in `LuaRequireReference`,
  `LuaModulePathResolver`, `LuaFileBindingsIndex`. Appending at
  `getProjectSourcePathPatterns` reuses all existing consumers.
- **C modules into `LUA_PATH` from source `src/*.c`**: rejected — C modules load from the built
  `.so` tree, not source; mapping `src/cjson.c` into a Lua path is meaningless. Hence the separate
  `LUA_CPATH` from `LuaRocksTreeLocator.treeRoot` (section 3.3).
- **Setting `LUA_INIT` to inject paths**: rejected — collides with the debug preloader's
  `ENV_LUA_INIT` ([LuaRunConfiguration.kt:259](../../../../src/main/kotlin/net/internetisalie/lunar/run/LuaRunConfiguration.kt));
  we set `LUA_PATH`/`LUA_CPATH` directly (risks-and-gaps R2).

## 11. Open Questions

_None — feature has cleared the planning bar._
