---
id: "TARGET-07-DESIGN"
title: "Technical Design"
type: "design"
parent_id: "TARGET-07"
folders:
  - "[[features/target/07-lua-5.5-stdlib-stubs/requirements|requirements]]"
---

# Technical Design: TARGET-07 — Lua 5.5 Standard-Library Stubs

## 1. Architecture Overview

### Current State
The plugin bundles standard-library API stubs for Lua 5.1–5.4 under
`src/main/resources/runtime/standard/lua-5.{1,2,3,4}/`, each a directory of LuaCATS-annotated
`.lua` "meta" files consumed as a `SyntheticLibrary`. The 5.4 set has 10 files
(`builtin, coroutine, debug, io, math, os, package, string, table, utf8`) — verified
`ls runtime/standard/lua-5.4/`.

Lua 5.5 language support (SYNTAX-09) shipped the `LUA55` level and the `global` keyword but
**no** 5.5 stub directory. Consequently a project on the `Standard 5.5` target resolves against
whatever the resolver returns for `runtime/standard/lua-5.5/` — which does not exist on disk,
so `RuntimeLibraryProvider.getLibraryRoot` returns `null` and 5.5 users currently get **no**
stdlib completion/type-inference at all (not even the 5.4 subset).

### Prior Art in This Repo
Searched `src/main/kotlin` for the runtime-stub resolution path and existing stub sets. Found —
this feature **extends** the data these components read; it adds **no** new component:

- **`net.internetisalie.lunar.platform.target.RuntimeLibraryProvider`**
  (`RuntimeLibraryProvider.kt:16`) — resolves `Target.getLibraryRootPath()` → a classpath
  resource dir → `VirtualFile`; `getLibraryFiles` filters `.lua`. Unchanged; the new dir is
  found by path.
- **`net.internetisalie.lunar.lang.library.LuaLibraryProvider`**
  (`LuaLibraryProvider.kt:13`) — `AdditionalLibraryRootsProvider` that injects the resolved
  root as a `SyntheticLibrary` from `LuaProjectSettings…getTarget()`. Unchanged.
- **`net.internetisalie.lunar.platform.target.PlatformVersionRegistry`**
  (`PlatformVersionRegistry.kt:14`) — already registers
  `VersionEntry("5.5", "lua-5.5", luacheckStd = "lua54")` (`PlatformVersionRegistry.kt:21`), so
  the `lua-5.5` path segment is a first-class registered target **today**. Unchanged.
- **`net.internetisalie.lunar.platform.target.Target`** (`Target.kt:16`) — `getImplicitLanguageLevel()`
  maps `STANDARD 5.5 → LuaLanguageLevel.LUA55` (`Target.kt:44`) and
  `getLibraryRootPath()` composes `runtime/standard/lua-5.5` (`Target.kt:67`). Unchanged.
- **`net.internetisalie.lunar.project.PlatformLibraryIndex`** (`PlatformLibraryProvider.kt:105`) —
  `object` whose `reload()` (`PlatformLibraryProvider.kt:134`) forces a `StubIndex` rebuild after
  a target change; used by tests. Unchanged.
- **Existing 5.4 stub set** (`runtime/standard/lua-5.4/*.lua`) — the **baseline** that the new
  5.5 set is copied from and then diffed. This design **extends** (copies + patches) the
  existing set; it does not replace it.

No existing component resolves 5.5 stubs by any mechanism other than the target path, and no
5.5 stub directory exists to duplicate. Conclusion: **purely additive resource files + a
`THIRD-PARTY.md` attribution edit. Zero production Kotlin.**

### Target State
`runtime/standard/lua-5.5/` exists with 10 files mirroring 5.4 plus the §1-delta patches.
`Target(STANDARD, "5.5")` → `getLibraryRootPath()` = `runtime/standard/lua-5.5` →
`RuntimeLibraryProvider.getLibraryRoot` returns the new dir → `LuaLibraryProvider` injects it
as the synthetic library → `LuaGlobalDeclarationIndex` indexes each `function <ns>.<name>` decl
→ references resolve. Version gating is a free consequence: 5.5-only symbols live only in the
5.5 dir, so they are indexed only when the 5.5 root is the active library root.

Component sketch (all pre-existing; arrow = data flow, no code change):
`LuaProjectSettings.getTarget()` → `Target.getLibraryRootPath()` →
`RuntimeLibraryProvider.getLibraryRoot()` → `LuaLibraryProvider` (`SyntheticLibrary`) →
stub index → `LuaNameRef.reference.multiResolve`.

## 2. Core Components

No new classes. This feature creates **resource files** and edits one **doc file**. The
components that consume them already exist and are enumerated in §1 (Prior Art) with
`file:line` evidence and their exact roles.

### 2.1 Resource files to create (10)
- **Path**: `src/main/resources/runtime/standard/lua-5.5/{builtin,coroutine,debug,io,math,os,package,string,table,utf8}.lua`
- **Provenance**: byte-copy of the same-named file from `runtime/standard/lua-5.4/`, then apply
  the §1 delta to `builtin.lua` and `table.lua` only.
- **Consumed by**: `RuntimeLibraryProvider.getLibraryFiles` / `LuaLibraryProvider` (§1). No
  registration entry is needed — resource dirs are discovered by path, not declared.

### 2.2 File to edit (1)
- **Path**: `THIRD-PARTY.md` (repo root) — extend the existing "Lua standard-library API
  stubs (Lua.org, PUC-Rio)" entry (`THIRD-PARTY.md:116-126`). See §7.

## 3. Algorithms

This feature introduces **no runtime algorithm** — the stubs are static data and resolution is
performed by the existing, unchanged stub-index/reference machinery. The only procedural logic
is the **build-time construction** of the 5.5 set, which is fully mechanical and specified here
so the implementer makes no choices:

### 3.1 Constructing `lua-5.5/` from `lua-5.4/`
- **Input → Output**: `runtime/standard/lua-5.4/` (10 files) → `runtime/standard/lua-5.5/`
  (10 files).
- **Steps**:
  1. Copy the entire `lua-5.4/` directory to `lua-5.5/` verbatim (preserves every file,
     including the MIT header on each). Concretely:
     `cp -r src/main/resources/runtime/standard/lua-5.4 src/main/resources/runtime/standard/lua-5.5`.
  2. In `lua-5.5/builtin.lua`: replace the doc line `---Lua version string (e.g., "Lua 5.4")`
     with `---Lua version string (e.g., "Lua 5.5")`, and the line `_VERSION = "Lua 5.4"` with
     `_VERSION = "Lua 5.5"`. (These are the only two lines mentioning `5.4` in that file;
     verified `grep -n 5.4 lua-5.4/builtin.lua` → lines 86–87.)
  3. In `lua-5.5/table.lua`: append the `table.create` declaration block (verbatim from
     requirements §TARGET-07-02) after the existing `table.move` block (currently the last
     declaration, `lua-5.4/table.lua:75-84`), before the trailing blank line.
  4. Leave the other 8 files untouched.
- **Rules / edge handling**: Do **not** search-and-replace `5.4` across the whole file set —
  the MIT header's copyright year range `1994–2025` and any doc prose must not change; only the
  two `builtin.lua` lines in step 2 are edited. `math.lua`, `os.lua`, etc. contain no `5.4`
  literal that should change.
- **Complexity / bounds**: O(files) copy; two single-line edits + one appended block. No
  runtime cost.

## 4. External Data & Parsing
Not applicable — this feature consumes no CLI/network/unstructured input at runtime. The stub
files are authored data in the repo's own LuaCATS `---@meta` format, parsed by the existing
Lua parser/PSI like any other `.lua` file. (Stated per the template's requirement.)

## 5. Data Flow

### Example 1: `table.create` resolves under a 5.5 target (TARGET-07-06, TC 1)
1. Project target = `Target(STANDARD, VersionEntry("5.5","lua-5.5",...))`.
2. `LuaLibraryProvider.getAdditionalProjectLibraries` → `RuntimeLibraryProvider.getLibraryRoot`
   → classpath resource `runtime/standard/lua-5.5` → `SyntheticLibrary` over that dir.
3. Stub index ingests `function table.create(...)` from `lua-5.5/table.lua` into
   `LuaGlobalDeclarationIndex` under key `table.create` (same path that indexes `redis.call` —
   see `ValkeyStubResourceTest` header).
4. Editor file `table.create(4)`; the `create` leaf's `LuaNameRef.reference.multiResolve(false)`
   returns the stub decl → `.size >= 1`.

### Example 2: `table.create` does NOT resolve under a 5.4 target (TARGET-07-07, TC 2)
1. Target = `Standard 5.4` → root `runtime/standard/lua-5.4`, which has no `table.create`.
2. `LuaGlobalDeclarationIndex` has no `table.create` key → `multiResolve` returns `.size == 0`.
   Gating is structural, not a version check in code.

### Example 3: `table.insert` still resolves under 5.5 (TARGET-07-08, TC 3)
The copied `lua-5.5/table.lua` retains `function table.insert(...)`, so it indexes and resolves
exactly as at 5.4 — proving the copy dropped nothing.

## 6. Edge Cases
- **`table.move` double-add**: `table.move` already exists in the copied `table.lua`
  (`lua-5.4/table.lua:84`). Do **not** add it again; it is not a 5.5 delta.
- **`global.lua` name collision**: the Redis/Valkey stub sets use a file literally named
  `global.lua` for ambient globals; the *standard* set never has one. The Lua 5.5 `global`
  keyword is a language feature (SYNTAX-09), not a stdlib table — do not create a `global.lua`
  in `lua-5.5/`.
- **Stale index in tests**: after switching target, tests must call
  `PlatformLibraryIndex.reload()` on the EDT (as `ValkeyStubResourceTest.setValkey8Target` does)
  or resolution is non-deterministic across test ordering.
- **`luacheckStd` mismatch**: 5.5's registry entry still maps to `lua54` (no upstream `lua55`
  std yet). Out of scope here; tracked in risks-and-gaps §2.2. Not a defect of these stubs.
- **Header drift**: a whole-file `5.4`→`5.5` replace would corrupt the copyright range and doc
  prose; §3.1 forbids it and pins the exact two `builtin.lua` lines.

## 7. Integration Points
No `plugin.xml` change. The consuming extension point already exists and is registered for the
existing 5.1–5.4 stubs; the new dir plugs into it by path with no declaration:

```xml
<!-- plugin.xml (EXISTING — shown for grounding; NOT modified by TARGET-07) -->
<!-- <extensions defaultExtensionNs="com.intellij">
       <additionalLibraryRootsProvider
           implementation="net.internetisalie.lunar.lang.library.LuaLibraryProvider"/>
     </extensions> -->
```

`THIRD-PARTY.md` edit (TARGET-07-05), amending the existing MIT "Lua standard-library API
stubs" entry (`THIRD-PARTY.md:116-126`):
- **Usage line**: change `Lua 5.1-5.4` → `Lua 5.1-5.5`.
- **Files line**: change the glob to
  `src/main/resources/runtime/standard/lua-5.{1,2,3,4,5}/*.lua` and the count from `39 files`
  to `49 files` (39 + 10 new), keeping the same parenthetical file-name list plus a note that
  5.5 adds `table.create`.

## 8. Requirement Coverage

| Requirement | Priority | Implemented by (section) |
|-------------|----------|--------------------------|
| TARGET-07-01 | M | §3.1 step 1 (copy), §2.1 |
| TARGET-07-02 | M | §3.1 step 3, requirements §TARGET-07-02 block |
| TARGET-07-03 | M | §3.1 step 2 |
| TARGET-07-04 | M | §3.1 step 1 (header preserved by copy) |
| TARGET-07-05 | M | §7 (THIRD-PARTY.md edit) |
| TARGET-07-06 | M | §1 resolver chain, §5 Example 1 |
| TARGET-07-07 | M | §5 Example 2 (structural gating) |
| TARGET-07-08 | S | §3.1 step 1 + step 4, §5 Example 3 |

## 9. Alternatives Considered
- **Symlink `lua-5.5/` → `lua-5.4/`**: rejected — build packaging follows real files, symlinks
  are fragile across OSes and Gradle resource copying, and the two files that differ can't be
  symlinked anyway.
- **Single shared dir + a code-level delta overlay for 5.5**: rejected — adds production Kotlin
  and a divergence from the flat one-dir-per-version convention every other version uses; higher
  risk than 10 static files for a "Could" feature.
- **Add `luacheckStd = "lua55"`**: rejected/deferred — upstream luacheck has no `lua55` std;
  changing it would break linting. Kept at `lua54` (risks §2.2).

## 10. Open Questions

_None — feature has cleared the planning bar._
