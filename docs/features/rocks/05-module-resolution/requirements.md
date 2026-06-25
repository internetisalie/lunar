---
id: "ROCKS-05"
title: "05: Rockspec Module Resolution"
type: "feature"
status: "done"
priority: "high"
parent_id: "ROCKS"
folders:
  - "[[features/rocks/requirements|requirements]]"
---

# ROCKS-05: Rockspec Module Resolution

## Overview

A rockspec's `build.modules` table maps Lua module names to source files relative to the
rockspec. ROCKS-05 derives source roots from those mappings and feeds them to **two**
consumers of one derivation:

- **(A) IDE require-resolution / indexing** — derived roots are appended at the single
  chokepoint `PathConfiguration.getProjectSourcePathPatterns(project)`
  ([SourcePathPattern.kt:19](../../../../src/main/kotlin/net/internetisalie/lunar/lang/path/SourcePathPattern.kt)),
  so `LuaRequireReference.resolve()`
  ([LuaRequireReference.kt:21](../../../../src/main/kotlin/net/internetisalie/lunar/lang/LuaRequireReference.kt)),
  `LuaModulePathResolver`
  ([LuaModulePathResolver.kt:18](../../../../src/main/kotlin/net/internetisalie/lunar/lang/path/LuaModulePathResolver.kt)),
  and `LuaFileBindingsIndex`
  ([LuaFileBindingsIndex.kt:180](../../../../src/main/kotlin/net/internetisalie/lunar/lang/indexing/LuaFileBindingsIndex.kt))
  all inherit them with no per-caller change.
- **(B) Run/debug `LUA_PATH` union** — derived local roots are unioned into the existing
  `LUA_PATH` injection in `LuaRunConfiguration`
  ([LuaRunConfiguration.kt:262-272](../../../../src/main/kotlin/net/internetisalie/lunar/run/LuaRunConfiguration.kt))
  and `LuaTestCommandLineState`
  ([LuaTestCommandLineState.kt:133-144](../../../../src/main/kotlin/net/internetisalie/lunar/run/test/LuaTestCommandLineState.kt)),
  so running/debugging from source binds the same modules the editor resolves. C modules
  (`build.type` builtin, C sources) contribute `LUA_CPATH` from the **built tree**, not source.

This closes the gap between LuaRocks project structure and IDE module discovery without manual
`LUA_PATH` configuration.

## Scope

### In Scope
- Reading `build.modules` from each project rockspec via the existing `RockspecBridge`.
- Deriving source-root `SourcePathPattern` entries (incl. `?/init.lua`) from module->path
  mappings, per rockspec, with deduplication.
- Appending derived patterns at the single chokepoint
  `PathConfiguration.getProjectSourcePathPatterns(project)` (consumer A).
- Unioning derived local roots into the existing run/debug `LUA_PATH` injection
  (consumer B), **prepending** local roots before the installed tree and the trailing `;;`.
- Deriving `LUA_CPATH` for C-module rocks from the built tree
  (`LuaRocksTreeLocator.treeRoot(project)` -> `lib/lua/<X.Y>`).
- Caching derived patterns with invalidation on rockspec changes.

### Out of Scope
- **ROCKS-05 owns NO scanner.** Project-rockspec **discovery** is owned solely by ROCKS-09
  (`LuaRockspecDiscoveryService.discoverRockspecPaths(project)`,
  [09-workspace-discovery/design.md](../09-workspace-discovery/design.md) section 2.1). ROCKS-05
  must not define `allProjectRockspecs` / `Files.walk` / a depth cap / an exclusion set — it
  **consumes** ROCKS-09's discovery output.
- Parsing `build.type = "make"`/`"cmake"` Makefiles for source paths (only `builtin` module
  tables are read; non-builtin -> no source roots derived).
- `copy_directories` / asset-only build entries (deferred, risks-and-gaps.md).
- Reading `build.modules` from installed rocks under `lua_modules/` (already on
  `package.path` via the installed tree).
- Rockspec editing or code insight within `.rockspec` files themselves.
- Touching the debug preloader env injection (`ENV_LUNAR_LUA_PATH_TEMPLATE` / `ENV_LUA_INIT`)
  at [LuaRunConfiguration.kt:256-260](../../../../src/main/kotlin/net/internetisalie/lunar/run/LuaRunConfiguration.kt).

## Functional Requirements

| ID | Requirement | Priority | Description |
|----|-------------|----------|-------------|
| ROCKS-05-01 | **Build Modules Parsing** | M | Extend `RockspecBridge.parse()` to surface `build.modules` as `Map<String, String>` (module name -> relative Lua source path). C-module entries (array values) are excluded from this map. |
| ROCKS-05-02 | **Source-Root Derivation** | M | From each rockspec's `build.modules`, derive the common source root(s) and emit `SourcePathPattern`s `<rockspecDir>/<root>/?.lua` and `<rockspecDir>/<root>/?/init.lua`, deduplicated across rockspecs. |
| ROCKS-05-03 | **IDE Path Integration (A)** | M | Append derived patterns at `PathConfiguration.getProjectSourcePathPatterns(project)` so require-resolution, completion, indexing and library roots inherit them with no per-caller change. |
| ROCKS-05-04 | **Run/Debug LUA_PATH Union (B)** | M | In the no-per-config-sourcePath (`else`) branch of `LuaRunConfiguration` / `LuaTestCommandLineState`, prepend derived local roots before the existing project `LUA_PATH` and the installed tree, ending with `;;`. |
| ROCKS-05-05 | **C-Module LUA_CPATH** | S | For rocks with C modules (`build.type` builtin with array-valued module entries), set `LUA_CPATH` to the built tree `LuaRocksTreeLocator.treeRoot(project)` -> `lib/lua/<X.Y>/?.so` (X.Y from `languageLevel`), never from source. |
| ROCKS-05-06 | **Invalidation** | S | Cache derived patterns with `CachedValuesManager` keyed to rockspec modifications; invalidate when any project rockspec is added/removed/modified. |

## Detailed Specifications

### ROCKS-05-01: Build Modules Parsing

**Current state**: `RockspecBridge.parse()`
([RockspecBridge.kt:49](../../../../src/main/kotlin/net/internetisalie/lunar/rocks/RockspecBridge.kt))
reads only `package`, `version`, `dependencies`. The bridge Lua script already exports `build`
to stdout ([rockspec.lua:27](../../../../src/main/resources/lua/rockspec.lua) export list, via
`require("lunar.export").json`), so the data is on the wire but ignored by Kotlin.

**Required behavior**: parse the `build` JSON object. The exact shape is specified in
design.md section 4. Outcome:
- `buildType: String?` = `build.type` (e.g. `"builtin"`, `"make"`, `null`).
- `luaModules: Map<String, String>` = entries of `build.modules` whose value is a JSON
  **string** (a Lua source path). Empty when `build` or `build.modules` absent.
- `cModules: Map<String, List<String>>` = entries whose value is a JSON **array** (C sources).

### ROCKS-05-02: Source-Root Derivation

Given a module mapping `"foo.bar" -> "src/foo/bar.lua"` (rockspec dir `/proj/`):
1. Module slash form: `"foo/bar"`.
2. Strip the module slash form from the source path -> `root = "src/"`, `suffix = ".lua"`.
3. Emit `SourcePathPattern("/proj/src/?.lua")` **and** `SourcePathPattern("/proj/src/?/init.lua")`.

For an `init.lua` module `"mymod" -> "lua/mymod/init.lua"`:
1. Module slash form: `"mymod"`.
2. The source path ends with `mymod/init.lua`; strip `mymod` and `/init.lua` -> `root = "lua/"`.
3. Emit `SourcePathPattern("/proj/lua/?.lua")` and `SourcePathPattern("/proj/lua/?/init.lua")`.

Full algorithm (common-prefix, both suffixes, worked examples) is in design.md section 3.1.
Patterns are deduplicated by their `spec` string across all rockspecs.

### ROCKS-05-03: IDE Path Integration (A)

`PathConfiguration.getProjectSourcePathPatterns(project)` is the single chokepoint. Derived
patterns are **appended after** user-configured patterns (user paths win for resolution
ordering). All existing callers inherit them. See design.md section 2.2.

### ROCKS-05-04: Run/Debug LUA_PATH Union (B)

A new `RockspecRunPathProvider` (design.md section 2.4) edits **only** the `else` (no per-config
`sourcePath`) branch of:
- `LuaRunConfiguration` ([LuaRunConfiguration.kt:262-272](../../../../src/main/kotlin/net/internetisalie/lunar/run/LuaRunConfiguration.kt)),
- `LuaTestCommandLineState.configureLuaPath` ([LuaTestCommandLineState.kt:133-144](../../../../src/main/kotlin/net/internetisalie/lunar/run/test/LuaTestCommandLineState.kt)).

Local roots are **prepended** before the existing project `LUA_PATH` value and an installed
tree fallback, ending with the Lua `;;` default-include marker. The debug preloader injection
at [LuaRunConfiguration.kt:256-260](../../../../src/main/kotlin/net/internetisalie/lunar/run/LuaRunConfiguration.kt)
(`ENV_LUNAR_LUA_PATH_TEMPLATE` / `ENV_LUA_INIT`) is **not** touched. Union algorithm: design.md
section 3.2.

### ROCKS-05-05: C-Module LUA_CPATH

When a rockspec's `build.type` is builtin and at least one `build.modules` entry is C
(array-valued, ROCKS-05-01 `cModules`), `RockspecRunPathProvider` sets `LUA_CPATH` to
`<treeRoot>/lib/lua/<X.Y>/?.so;;` where `treeRoot = LuaRocksTreeLocator.treeRoot(project)`
([LuaRocksTreeLocator.kt:33](../../../../src/main/kotlin/net/internetisalie/lunar/rocks/LuaRocksTreeLocator.kt))
and `X.Y = LuaProjectSettings.getInstance(project).state.languageLevel.version`
([LuaLanguageLevel.kt:31](../../../../src/main/kotlin/net/internetisalie/lunar/lang/LuaLanguageLevel.kt)).
C modules are NEVER mapped to source roots. See design.md section 3.3.

### ROCKS-05-06: Invalidation

Derivation is cached on the project light service via `CachedValuesManager` keyed to
`PsiModificationTracker` (mirrors `LuaTypeManagerImpl`), so editing/adding/removing a rockspec
invalidates derived patterns. See design.md section 2.3.

## Behavior Rules

1. **Discovery is ROCKS-09's**: the rockspec path list comes from
   `LuaRockspecDiscoveryService.discoverRockspecPaths(project)`; ROCKS-05 calls
   `RockspecBridge.read(project, path)` on each returned path itself.
2. **Pattern precedence**: user-configured source paths come first; rockspec-derived patterns
   are appended (consumer A).
3. **Local-before-installed**: in `LUA_PATH` (consumer B), derived local roots precede the
   project/installed value and the trailing `;;`.
4. **Deduplication**: identical `SourcePathPattern.spec` strings emitted once.
5. **Graceful degradation**: if `RockspecBridge.read` fails for one rockspec (e.g. no
   interpreter), skip it (warn in log); other rockspecs still contribute.
6. **Threading**: `RockspecBridge.read` spawns a subprocess — never on the EDT; PSI/index reads
   in a read action; no hard refs to `Project`/`PsiFile`/`VirtualFile` retained.

## Test Cases

| # | Requirement | Given (input) | When (action) | Then (expected) |
|---|-------------|---------------|---------------|-----------------|
| 1 | ROCKS-05-01 | Bridge JSON `{"package":"foo","build":{"type":"builtin","modules":{"foo.bar":"src/foo/bar.lua"}}}` | `RockspecBridge.parse()` | `luaModules == {"foo.bar":"src/foo/bar.lua"}`, `cModules` empty, `buildType=="builtin"` |
| 2 | ROCKS-05-01 | Bridge JSON with no `build` field | `RockspecBridge.parse()` | `luaModules` empty, `cModules` empty, `buildType==null` |
| 3 | ROCKS-05-01 | Bridge JSON `build.modules = {"cjson": ["src/cjson.c"]}` | `RockspecBridge.parse()` | C entry -> `cModules == {"cjson":["src/cjson.c"]}`, `luaModules` empty |
| 4 | ROCKS-05-02 | `build.modules={["foo.bar"]="src/foo/bar.lua"}`, rockspec dir `/proj/` | Derivation | patterns include `/proj/src/?.lua` **and** `/proj/src/?/init.lua` |
| 5 | ROCKS-05-02 | Module `"mymod"`, source `"lua/mymod/init.lua"`, dir `/proj/` | Derivation | patterns include `/proj/lua/?.lua` and `/proj/lua/?/init.lua` |
| 6 | ROCKS-05-03 | Project rockspec at `/proj/rocks/foo/foo-1.0-1.rockspec` with `build.modules={["foo.bar"]="src/foo/bar.lua"}` | `getProjectSourcePathPatterns(project)`, then `require("foo.bar")` Ctrl+click | derived `/proj/rocks/foo/src/?.lua` present; navigates to `/proj/rocks/foo/src/foo/bar.lua` |
| 7 | ROCKS-05-04 | 2 rocks: `a` -> root `a/src/`, `b` -> root `b/lua/`; **discovery via a TEST-ONLY stub** of `discoverRockspecPaths()` returning both paths; per-config `sourcePath` empty | Build `LUA_PATH` (else branch) | `LUA_PATH` prepends `<a>/src/?.lua;<a>/src/?/init.lua;<b>/lua/?.lua;<b>/lua/?/init.lua;` **before** the project value, ending `;;`; local roots precede installed |
| 8 | ROCKS-05-05 | Rock with `build.type="builtin"`, `build.modules={"cjson":["src/cjson.c"]}`, `languageLevel=LUA54`, built tree `/proj/lua_modules` | Build env | `LUA_CPATH == "/proj/lua_modules/lib/lua/5.4/?.so;;"`; no `src/` C path in `LUA_PATH` or `LUA_CPATH` |
| 9 | ROCKS-05-06 | A project rockspec is edited to add a module | Re-read after PSI bump | new pattern appears in `getProjectSourcePathPatterns()` |

## Acceptance Criteria
- [ ] `require("foo.bar")` Ctrl+click navigates to the correct rockspec-mapped source (TC #6).
- [ ] Running from source (no per-config `sourcePath`) binds modules via the unioned `LUA_PATH`,
      local-before-installed (TC #7).
- [ ] A C-module rock contributes `LUA_CPATH` from the built tree, not source (TC #8).
- [ ] Editing a rockspec invalidates the cached patterns (TC #9).
- [ ] ROCKS-05 defines no scanner; it consumes `LuaRockspecDiscoveryService.discoverRockspecPaths`.

## Non-Functional Requirements
- Derivation must not block the EDT: `RockspecBridge.read` runs on a background thread; PSI/index
  reads in `runReadAction`.
- Cache invalidation is lightweight (`PsiModificationTracker`-keyed `CachedValue`), not a
  full re-parse per access.
- No hard references to `Project`/`PsiFile`/`VirtualFile` retained in the long-lived service.

## Dependencies
- **ROCKS-09 (Workspace Discovery) — stated contract**: ROCKS-05 consumes
  `LuaRockspecDiscoveryService.discoverRockspecPaths(project): List<DiscoveredRockspec>`,
  where `DiscoveredRockspec(rockspec: java.nio.file.Path, packageName: String?)` carries
  **paths only** (no `buildModules`). ROCKS-05 reads `build.modules` itself via
  `RockspecBridge.read`. Until ROCKS-09 lands, a **TEST-ONLY** stub of the discovery seam may
  substitute in unit tests (clearly labelled, never the production path).
- `RockspecBridge` and `LuaRocksTreeLocator` (existing, from ROCKS-03).
- A configured Lua interpreter for `RockspecBridge.read()`.

## See Also
- Design: [design.md](design.md)
- Plan: [implementation-plan.md](implementation-plan.md)
- Risks: [risks-and-gaps.md](risks-and-gaps.md)
- ROCKS-09 contract: [../09-workspace-discovery/design.md](../09-workspace-discovery/design.md)
