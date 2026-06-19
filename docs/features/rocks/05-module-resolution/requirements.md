---
id: "ROCKS-05"
title: "05: Rockspec Module Resolution"
type: "feature"
status: "todo"
priority: "high"
parent_id: "ROCKS"
folders:
  - "[[features/rocks/requirements|requirements]]"
---

# ROCKS-05: Rockspec Module Resolution

## Overview

When a rockspec file declares `build.modules` (mapping Lua module names to source file paths
relative to the rockspec), the IDE should derive source-path patterns from those mappings so
that `require()` resolution, cross-file completion, auto-import, and indexing work without
manual `LUA_PATH` configuration. This closes the gap between LuaRocks project structure and
IDE module discovery.

## Scope

### In Scope
- Scanning all `.rockspec` files in the project tree (not just the root-level one).
- Parsing the `build.modules` table from each rockspec via the existing `RockspecBridge`.
- Deriving `SourcePathPattern` entries from the module→source-path mappings.
- Merging rockspec-derived patterns into `PathConfiguration.getProjectSourcePathPatterns()`.
- Caching derived patterns with proper invalidation on rockspec file changes.

### Out of Scope
- Parsing `build.type = "make"` or `"cmake"` Makefiles to discover source paths (only `builtin` module tables).
- Modifying the runtime `LUA_PATH` env var in run configurations (that's bootstrapped by `setup.lua` per ROCKS-01-03).
- Reading `build.modules` from installed rocks in `lua_modules/` (deferred; those are already on `package.path` via `setup.lua`).
- Rockspec editing or code insight within `.rockspec` files themselves.

## Functional Requirements

| ID | Requirement | Priority | Description |
|----|-------------|----------|-------------|
| ROCKS-05-01 | **Rockspec Discovery** | M | Scan the project tree for all `.rockspec` files (not limited to the root); support both single-rock and workspace layouts (e.g. `rocks/<name>/<name>-1.0-1.rockspec`). |
| ROCKS-05-02 | **Build Modules Parsing** | M | Extend `RockspecBridge` to extract `build.modules` as a `Map<String, String>` (module name → relative source path) from the bridge JSON output. |
| ROCKS-05-03 | **Pattern Derivation** | M | Derive `SourcePathPattern` entries from module→path mappings by computing the common `leadingPath` and `suffix` relative to each rockspec's parent directory. Deduplicate equivalent patterns. |
| ROCKS-05-04 | **Path Integration** | M | Merge rockspec-derived patterns into `PathConfiguration.getProjectSourcePathPatterns()` so all consumers (require resolution, completion, indexing, auto-import, library roots) inherit them without individual changes. |
| ROCKS-05-05 | **Invalidation** | S | Cache derived patterns with `CachedValuesManager` keyed on rockspec file modification stamps. Invalidate when any `.rockspec` in the project tree is added, removed, or modified. |
| ROCKS-05-06 | **Multi-Pattern Support** | S | Handle rockspecs where modules map to different source directories (e.g. `src/` for library modules, `bin/` for executables) by deriving multiple distinct patterns per rockspec. |

## Detailed Specifications

### ROCKS-05-01: Rockspec Discovery

**Current state**: `LuaRocksTreeLocator.projectRockspec()` (at [LuaRocksTreeLocator.kt:L40](file:///home/mini/Documents/src/lua/lunar/src/main/kotlin/net/internetisalie/lunar/rocks/LuaRocksTreeLocator.kt#L40)) only finds a single `.rockspec` in the project root directory (most-recently-modified). It does not scan subdirectories.

**Required behavior**: A new method scans the project tree recursively for all `.rockspec` files. Must handle:
- Root-level rockspec: `<project>/mylib-1.0-1.rockspec`
- Workspace sub-rockspecs: `<project>/rocks/adt/adt-1.0-1.rockspec`
- Depth limit of 3 to avoid traversing `lua_modules/` tree (those are installed rocks, not source rocks).
- Exclude `lua_modules/` and `.luarocks/` directories.

### ROCKS-05-02: Build Modules Parsing

**Current state**: `RockspecBridge.parse()` (at [RockspecBridge.kt:L49](file:///home/mini/Documents/src/lua/lunar/src/main/kotlin/net/internetisalie/lunar/rocks/RockspecBridge.kt#L49)) reads only `package`, `version`, `dependencies` from the bridge JSON. The bridge Lua script already exports `build` (at [rockspec.lua:L27](file:///home/mini/Documents/src/lua/lunar/src/main/resources/lua/rockspec.lua#L27)), so the data is available in JSON but ignored by the Kotlin side.

**Required behavior**: Parse `build.modules` from the JSON object. The `build` field in rockspec format is:
```lua
build = {
   type = "builtin",
   modules = {
      ["adt.orderedmap"] = "lua/adt/orderedmap.lua",
      ["adt.binaryheap"] = "lua/adt/binaryheap.lua",
      ["adt.pair"]       = "lua/adt/pair.lua",
   },
}
```

The bridge serializes this as:
```json
{
  "build": {
    "type": "builtin",
    "modules": {
      "adt.orderedmap": "lua/adt/orderedmap.lua",
      "adt.binaryheap": "lua/adt/binaryheap.lua",
      "adt.pair": "lua/adt/pair.lua"
    }
  }
}
```

Edge cases:
- `build` is absent → empty map.
- `build.modules` is absent (e.g. `build.type = "make"`) → empty map.
- Module value is a table (C modules: `{ "src/foo.c", "src/bar.c" }`) → skip (not a Lua source path).

### ROCKS-05-03: Pattern Derivation

Given a module mapping `"adt.orderedmap" → "lua/adt/orderedmap.lua"`:
1. Convert module name dots to slashes: `"adt/orderedmap"`.
2. The source path is `"lua/adt/orderedmap.lua"`.
3. Strip the module-slash form from the source path to find `leadingPath = "lua/"` and `suffix = ".lua"`.
4. Prepend the rockspec's parent directory: `leadingPath = "<rockspec_dir>/lua/"`.
5. Emit pattern: `<rockspec_dir>/lua/?.lua`.

For `init.lua` modules (e.g. `"mymod" → "lua/mymod/init.lua"`):
1. Module slash form: `"mymod"`.
2. Source path: `"lua/mymod/init.lua"`.
3. Strip module form: `leadingPath = "lua/"`, suffix = `"/init.lua"`.
4. Emit pattern: `<rockspec_dir>/lua/?/init.lua`.

### ROCKS-05-04: Path Integration

`PathConfiguration.getProjectSourcePathPatterns()` (at [SourcePathPattern.kt:L19](file:///home/mini/Documents/src/lua/lunar/src/main/kotlin/net/internetisalie/lunar/lang/path/SourcePathPattern.kt#L19)) is the single chokepoint. Rockspec-derived patterns are appended after user-configured patterns (user patterns take priority for resolution ordering).

Automatic downstream beneficiaries (no code changes):
- `LuaRequireReference.resolve()` ([LuaRequireReference.kt:L21](file:///home/mini/Documents/src/lua/lunar/src/main/kotlin/net/internetisalie/lunar/lang/LuaRequireReference.kt#L21))
- `LuaCrossFileCompletionProvider` ([LuaCrossFileCompletionProvider.kt:L56](file:///home/mini/Documents/src/lua/lunar/src/main/kotlin/net/internetisalie/lunar/lang/completion/LuaCrossFileCompletionProvider.kt#L56))
- `LuaModulePathResolver` ([LuaModulePathResolver.kt:L21](file:///home/mini/Documents/src/lua/lunar/src/main/kotlin/net/internetisalie/lunar/lang/path/LuaModulePathResolver.kt#L21))
- `LuaFileBindingsIndex` `RequiredFilesQuery` ([LuaFileBindingsIndex.kt:L180](file:///home/mini/Documents/src/lua/lunar/src/main/kotlin/net/internetisalie/lunar/lang/indexing/LuaFileBindingsIndex.kt#L180))
- `PlatformLibraryProvider.getExternalLibraries()` ([PlatformLibraryProvider.kt:L57](file:///home/mini/Documents/src/lua/lunar/src/main/kotlin/net/internetisalie/lunar/project/PlatformLibraryProvider.kt#L57))

## Behavior Rules

1. **Pattern precedence**: User-configured source paths (from project settings) come first; rockspec-derived patterns are appended. This means user paths "win" for ambiguous module names.
2. **Deduplication**: If two rockspecs produce the same pattern (same `leadingPath` + `suffix`), emit it only once.
3. **No runtime effect**: This feature only affects IDE-side resolution (indexing, completion, navigation). It does NOT modify the `LUA_PATH` environment variable in run configurations.
4. **Graceful degradation**: If `RockspecBridge.read()` fails for a rockspec (e.g. Lua interpreter not configured), skip it silently (warn in log). Other rockspecs still contribute their patterns.

## Test Cases

| # | Requirement | Given (input) | When (action) | Then (expected) |
|---|-------------|---------------|---------------|-----------------|
| 1 | ROCKS-05-02 | Rockspec JSON with `build.modules = {"adt.orderedmap": "lua/adt/orderedmap.lua"}` | `RockspecBridge.parse()` is called | `buildModules` map contains `"adt.orderedmap" → "lua/adt/orderedmap.lua"` |
| 2 | ROCKS-05-02 | Rockspec JSON with no `build` field | `RockspecBridge.parse()` is called | `buildModules` is empty map |
| 3 | ROCKS-05-02 | Rockspec JSON with `build.modules` containing a C module `{"foo": ["src/foo.c"]}` | `RockspecBridge.parse()` is called | C module entry is skipped; `buildModules` is empty |
| 4 | ROCKS-05-03 | Module `"adt.orderedmap"`, source `"lua/adt/orderedmap.lua"`, rockspec dir `/proj/rocks/adt/` | Pattern derivation algorithm | `SourcePathPattern("/proj/rocks/adt/lua/?.lua")` |
| 5 | ROCKS-05-03 | Module `"mymod"`, source `"lua/mymod/init.lua"`, rockspec dir `/proj/` | Pattern derivation algorithm | `SourcePathPattern("/proj/lua/?/init.lua")` |
| 6 | ROCKS-05-04 | Project with rockspec containing `build.modules` pointing to `lua/?.lua` | `require("adt.orderedmap")` in editor, Ctrl+click | Navigates to `rocks/adt/lua/adt/orderedmap.lua` |
| 7 | ROCKS-05-01 | Project with `rocks/adt/adt-1.0-1.rockspec` and `rocks/cmd/cmd-1.0-1.rockspec` | Rockspec discovery | Both rockspecs found; patterns derived from both |
| 8 | ROCKS-05-05 | Rockspec file is edited to add a new module | Pattern cache | New pattern appears in `getProjectSourcePathPatterns()` after re-indexing |

## Acceptance Criteria
- [ ] `require("adt.orderedmap")` Ctrl+click navigates to the correct source file in a workspace layout (TC #6).
- [ ] Cross-file completion suggests symbols from rockspec-mapped source files.
- [ ] Auto-import inserts the correct `require()` path for symbols in rockspec-mapped files.
- [ ] Rockspec source directories appear under External Libraries in the project tree.
- [ ] Editing a rockspec invalidates the cached patterns.

## Non-Functional Requirements
- Pattern derivation must not block the EDT. `RockspecBridge.read()` spawns a Lua subprocess — it must run on a background thread.
- Pattern cache invalidation must be lightweight (file modification stamp check, not full re-parse on every access).

## Dependencies
- Requires a configured Lua interpreter for `RockspecBridge.read()` (existing dependency from ROCKS-03).
- `RockspecBridge` and `LuaRocksTreeLocator` (existing infrastructure from ROCKS-03).

## See Also
- Design: [design.md](design.md)
- Plan: [implementation-plan.md](implementation-plan.md)
- Risks: [risks-and-gaps.md](risks-and-gaps.md)
