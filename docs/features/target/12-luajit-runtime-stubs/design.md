---
id: TARGET-12-DESIGN
parent_id: TARGET-12
type: design
folders:
  - "[[features/target/12-luajit-runtime-stubs/requirements|requirements]]"
title: "Technical Design"
---

# Technical Design: TARGET-12 — LuaJIT Runtime Stubs

## 1. Architecture

The feature adds resource files under `src/main/resources/runtime/luajit/` and makes one production
change, the runtime-root rule in §1.1. Otherwise the existing machinery serves the files:

- `Target.getLibraryRootPath()` (`platform/target/Target.kt:66`) maps `Target(LUAJIT, "2.0"|"2.1")` to
  `runtime/luajit/luajit-2.0|luajit-2.1`, using the `PlatformVersionRegistry.kt:26-29` entries.
- `RuntimeLibraryProvider.getLibraryRoot` (`RuntimeLibraryProvider.kt:26`) resolves that path with the
  class loader and returns `null` when it is absent, which is the case for LuaJIT today (DR-01).
  `LuaLibraryProvider` and `PlatformLibraryProvider` (`plugin.xml:537-538`, both
  `additionalLibraryRootsProvider`) register the root as a `SyntheticLibrary`. Nothing new is
  registered.
- `PlatformLibraryIndex.getPackageFiles` (`PlatformLibraryProvider.kt:131`) maps every top-level
  `.lua` file to a module of the same base name, and `LuaNameReference` uses that map. Global and
  qualified declarations (`jit = {}`, `function ffi.new`) resolve through the existing indexes (DR-03).
- `require("a.b")` resolves through `resolveModuleCandidates` (`lang/path/LuaModuleFileResolver.kt:26`),
  which matches a file named `b.lua` whose path ends with `a/b.lua`. Dotted modules therefore live
  in subdirectories. A flat `a.b.lua` does not resolve (DR-05). For a name without a dot, the same
  function accepts a `<name>.lua` in any directory, so these are the first runtime files a bare
  `require("util")` could hit. §1.1 confines them (DR-13).
- The language level is unchanged: `LUA51`, from `Target.kt:45`. `print`, `string` and the other
  base names stay declared through `LuaStandardGlobals`, whatever the root contains (DR-01).

**Prior art, extended rather than duplicated.** The stdlib files are copies of
`runtime/standard/lua-5.1/`. `bit.lua` reuses the body of `runtime/redis/redis-7/bit.lua`, which is the
same LuaBitOp API: the redis file declares exactly the `bit.*` functions that DR-02 lists. The layout follows
TARGET-07's whole-root copy. TARGET-08's catalog libraries are not used, because the user decided
that `jit` belongs to the platform.

**Premises examined.**

| Premise | Fixed or chosen | Why |
| :-- | :-- | :-- |
| One library root per target, built from source copies | Chosen to keep | Every consumer assumes one root: `LuaLibraryProvider`, `PlatformLibraryIndex.getPackageFiles`, `LuaTypesVisitor.seedAmbientGlobals` (`:1436`), `LuaLibraryProvenance` (`:73`). Layering a base root under LuaJIT would change each of them. Assembling the roots at `processResources` time from `lua-5.1/` plus overlays would avoid duplicate sources, but it would move stub content into Gradle logic and leave the shipped files out of the source tree, where they could not be grepped or reviewed. The cost of copying is duplicated stdlib files (design §3). TC 11's byte-exact rebuild makes any drift from `lua-5.1/` fail. |
| No subdirectories in a runtime root | Changed | Every existing root is flat. Dotted LuaJIT modules need subdirectories (DR-05), and those collide with bare `require`s (DR-13). §1.1 is the change that makes nesting safe. |
| Separate 2.0 and 2.1 sets | Fixed | The registry has both versions, and the executed inventories differ (DR-02). |
| Hand-authored LuaJIT stubs | Chosen | The LuaLS templates are LuaLS-preprocessed and incomplete, and Neovim's `jit/*.lua` are implementations, not stubs (DR-07, DR-08). |
| Upstream LuaJIT API only | Chosen | OpenResty's fork additions belong to an NGX feature (requirements §Out of Scope). |
| Copy `lua-5.1/` as fixed by [[BUG-513]] | Chosen | BUG-513 aligns `lua-5.1/` with the Lua 5.1 manual, checked against executed 5.1.5 and LuaJIT dumps (DR-16), and it lands first. The copies are byte-exact rebuilds of the fixed files, so TARGET-12 ships no known stub defect. The `PsiErrorElement`s left in the copies come from Lunar parser gaps on valid LuaCATS, not from the stubs ([[BUG-514]]), and they hide no declaration (DR-14). |

### 1.1 Runtime-root module rule

`resolveModuleCandidates` (`lang/path/LuaModuleFileResolver.kt:26`) is changed. A `FilenameIndex`
candidate that lies inside the active runtime root is accepted only when its path relative to that
root is `<module path>.lua` or `<module path>/init.lua`, where `<module path>` is the module name with
`.` replaced by `/`. Every other candidate keeps the current rule, so [[BUG-500]], which is about
project and library files, is untouched.

```kotlin
// in resolveModuleCandidates, replacing the `for (virtualFile in byName)` loop
val runtimeRoot = PlatformLibraryIndex.getPlatformLibraryFolder(project)
for (virtualFile in byName) {
    val path = virtualFile.path
    val accepted =
        runtimeRootVerdict(virtualFile, runtimeRoot, moduleName)
            ?: (path.endsWith(expectedPathPart) || path.endsWith(expectedInitPathPart) || !moduleName.contains('.'))
    if (accepted) {
        (psiManager.findFile(virtualFile) as? LuaFile)?.let { yield(it) }
    }
}

/** True or false for a candidate inside the active runtime root; null when the rule does not apply. */
internal fun runtimeRootVerdict(
    candidate: VirtualFile,
    runtimeRoot: VirtualFile?,
    moduleName: String,
): Boolean? {
    if (runtimeRoot == null || !VfsUtilCore.isAncestor(runtimeRoot, candidate, true)) return null
    val relativePath = VfsUtilCore.getRelativePath(candidate, runtimeRoot) ?: return false
    val modulePath = moduleName.replace('.', '/')
    return relativePath == "$modulePath.lua" || relativePath == "$modulePath/init.lua"
}
```

The new imports are `com.intellij.openapi.vfs.VfsUtilCore` and
`net.internetisalie.lunar.project.PlatformLibraryIndex`. `runtimeRootVerdict` is `internal` so that
TC 19 can call it directly. Both callers of the sequence, `LuaRequireReference.resolve()` and
`LuaTypeManagerImpl.kt:298`, get the rule. Neither of them reached `getPlatformLibraryFolder`
(`PlatformLibraryProvider.kt:117`) before. Each `require` resolution now adds one call: a settings
read, a class-loader `getResource` and a `VfsUtil.findFileByURL`, the same work `getPackageFiles`
does for name references. DR-13 records the behaviour before and after the rule.

The `<module path>/init.lua` arm is unreachable through the shipped roots, which contain no
`init.lua`. TC 19 tests it directly against a temporary directory (DR-16).

[[BUG-500]]'s fix rewrites the same `if`. The two compose: the verdict is taken first through `?:`,
and BUG-500's path-tail check alone would not reject `jit/util.lua` for `util`. Whichever change
lands second merges this line by hand. TC 18's `null` expectations also assume that no `init.lua`
is in `allScope`, which is BUG-500's territory. That holds in the light fixture.

## 2. Data model

There are no Kotlin types. The "model" is the file layout in §3 and the per-file name sets in §4 and §5.

## 3. File layout

`R` is `src/main/resources/runtime/luajit/`. Delete `R/.gitkeep`.

| File (relative to `R/luajit-<v>/`) | 2.0 | 2.1 | Content |
| :-- | :-: | :-: | :-- |
| `builtin.lua` `coroutine.lua` `debug.lua` `io.lua` `math.lua` `os.lua` `package.lua` `string.lua` `table.lua` | ✓ | ✓ | Byte copy of `runtime/standard/lua-5.1/<f>`, taken after BUG-513 has landed, plus the §5 edits for that version |
| `bit.lua` | ✓ | ✓ | §6 header, an empty line, then the bytes of `runtime/redis/redis-7/bit.lua` |
| `jit.lua` | ✓ | ✓ | §6 header + §4.1 |
| `ffi.lua` | ✓ | ✓ | §6 header + §4.3 |
| `jit/util.lua` | ✓ | ✓ | §6 header + §4.4 |
| `jit/profile.lua` | — | ✓ | §6 header + §4.5 |
| `string/buffer.lua` | — | ✓ | §6 header + §4.6 |
| `table/new.lua`, `table/clear.lua` | — | ✓ | §6 header + §4.7 |

"LuaJIT-specific files" means every row except the first.

## 4. LuaJIT-specific stubs

**Block template.** Every declaration is one block, and blocks are separated by one empty line:
```
---<Doc line>
<one annotation per line, in the order given>
<declaration line>
```
A field declaration is `<name> = <placeholder>`, preceded by `---@type <T>`. The placeholder is `""`
for `string`, `0` for `integer`, and `{}` for `table` or a class. A function declaration is
`function <name>(<params>) end`, whose params are the `@param` names in order, with `...` last.
A module file (§4.3–§4.7) starts with `local <m> = {}` and ends with `return <m>`. The Doc lines
below are the exact text that goes after `---`.

### 4.1 `jit.lua` (global)
First block: `---LuaJIT JIT-compiler control` / `---@class jitlib` / `jit = {}`. Then:

| Name | Annotations | Doc |
| :-- | :-- | :-- |
| `jit.version` | `@type string` | LuaJIT version string, e.g. "LuaJIT 2.1.1791988261" |
| `jit.version_num` | `@type integer` | LuaJIT version as a number |
| `jit.os` | `@type string` | Target OS name: "Windows", "Linux", "OSX", "BSD", "POSIX" or "Other" |
| `jit.arch` | `@type string` | Target architecture name, e.g. "x86", "x64", "arm64" |
| `jit.opt` | `@type table` | JIT compiler optimization control |
| `jit.util` (**2.0 only**, placed right after `jit.opt`) | `@type table` | Bytecode and trace introspection (same table as require("jit.util")) |
| `jit.on(func, recursive)` | `@param func? function\|boolean`, `@param recursive? boolean` | Turns the JIT compiler on for the whole VM, or for a function and optionally its children |
| `jit.off(func, recursive)` | as `jit.on` | Turns the JIT compiler off for the whole VM, or for a function and optionally its children |
| `jit.flush(func, recursive)` | `@param func? function\|boolean\|integer`, `@param recursive? boolean` | Flushes the whole trace cache, the code of a function, or one trace |
| `jit.status()` | `@return boolean status`, `@return string ...` | Returns whether the JIT compiler is on, followed by the enabled CPU features and optimizations |
| `jit.attach(handler, event)` | `@param handler function`, `@param event? string` | Attaches a handler to a VM event, or detaches it when event is nil |
| `jit.security(mode)` (**2.1 only**) | `@param mode string`, `@return integer` | Returns the build's security setting for the given mode |
| `jit.opt.start(...)` | `@param ... string\|integer` | Sets JIT compiler optimization flags and parameters |

`declaredNames`, 2.1: `jit`, every name above except `jit.util`. 2.0: `jit`, every name above except `jit.security`.

### 4.2 `bit.lua` (global)
Its body is `redis-7/bit.lua`. `declaredNames` is `bit` plus `bit.` followed by each of `tobit`, `tohex`,
`bnot`, `bor`, `band`, `bxor`, `lshift`, `rshift`, `arshift`, `rol`, `ror` and `bswap`, in both
versions. These are the names the `redis-7` body already declares (DR-10).

### 4.3 `ffi.lua` (module `ffi`, not a global)
The file opens with three tag-only blocks, each a Doc line and one tag with no declaration line:
- `---A C data object` / `---@class ffi.cdata : userdata`
- `---A C type object` / `---@class ffi.ctype : userdata`
- `---A C library namespace` / `---@class ffi.namespace : table`

These are followed by one ordinary block: `---A callback created from a Lua function` /
`---@class ffi.cb : ffi.cdata` / `local cb = {}`.

Then come `function cb:free() end` (Doc: Frees the resources associated with the callback) and
`function cb:set(func) end` (`@param func function`; Doc: Associates a new Lua function with the
callback). After those: `---LuaJIT FFI library, loaded with require("ffi")` / `---@class ffilib` /
`local ffi = {}`. In the table below, `CT` stands for `string|ffi.ctype|ffi.cdata`.

| Name | Annotations | Doc |
| :-- | :-- | :-- |
| `ffi.C` | `@type ffi.namespace` | The default C library namespace |
| `ffi.os` / `ffi.arch` | `@type string` | Target OS name / Target architecture name |
| `ffi.cdef(def)` | `@param def string` | Adds C declarations for types or external symbols |
| `ffi.load(name, global)` | `@param name string`, `@param global? boolean`, `@return ffi.namespace` | Loads a dynamic library and returns its namespace |
| `ffi.new(ct, nelem, ...)` | `@param ct CT`, `@param nelem? integer`, `@param ... any`, `@return ffi.cdata` | Creates a cdata object for the given ctype |
| `ffi.typeof(ct, ...)` | `@param ct CT`, `@param ... any`, `@return ffi.ctype` | Creates a ctype object for the given C type |
| `ffi.cast(ct, init)` | `@param ct CT`, `@param init any`, `@return ffi.cdata` | Converts init to a cdata object of the given ctype |
| `ffi.metatype(ct, metatable)` | `@param ct CT`, `@param metatable table`, `@return ffi.ctype` | Creates a ctype with a metatable for its cdata objects |
| `ffi.gc(cdata, finalizer)` | `@param cdata ffi.cdata`, `@param finalizer function\|nil`, `@return ffi.cdata` | Attaches a finalizer to a cdata object, or removes it when finalizer is nil |
| `ffi.sizeof(ct, nelem)` | `@param ct CT`, `@param nelem? integer`, `@return integer\|nil` | Returns the size of ct in bytes, or nil if the size is unknown |
| `ffi.alignof(ct)` | `@param ct CT`, `@return integer` | Returns the minimum required alignment of ct in bytes |
| `ffi.offsetof(ct, field)` | `@param ct CT`, `@param field string`, `@return integer\|nil ofs`, `@return integer\|nil bpos`, `@return integer\|nil bsize` | Returns the offset in bytes of field relative to the start of ct, plus bit position and size for bit fields |
| `ffi.istype(ct, obj)` | `@param ct CT`, `@param obj any`, `@return boolean` | Returns true if obj has the C type given by ct |
| `ffi.errno(newerr)` | `@param newerr? integer`, `@return integer` | Returns the C errno of the last C call, optionally setting a new value |
| `ffi.string(ptr, len)` | `@param ptr any`, `@param len? integer`, `@return string` | Creates a Lua string from the data pointed to by ptr |
| `ffi.copy(dst, src, len)` | `@param dst any`, `@param src any`, `@param len? integer` | Copies len bytes from src to dst, or a whole Lua string when len is omitted |
| `ffi.fill(dst, len, c)` | `@param dst any`, `@param len integer`, `@param c? integer` | Fills len bytes at dst with the byte value c (default 0) |
| `ffi.abi(param)` | `@param param string`, `@return boolean` | Returns true if param (e.g. "32bit", "le", "fpu") applies to the target ABI |
| `ffi.typeinfo(id)` (**2.1 only**) | `@param id integer`, `@return table\|nil` | Returns a table describing the internal C type with the given id, or nil |

`declaredNames` is `cb:free`, `cb:set` and every name in the table (`ffi.os` and `ffi.arch` are two
names). `ffi.typeinfo` is in 2.1 only.

### 4.4 `jit/util.lua` (module `util`, both versions)
Opening block: `---LuaJIT bytecode and trace introspection, loaded with require("jit.util"); an undocumented internal API` /
`local util = {}`. Then comes one function for each name listed below, each with no Doc line, the annotations `---@param ... any` and
`---@return any`, and the declaration `function util.<n>(...) end`. `<n>` is each of `funcinfo`, `funcbc`,
`funck`, `funcuvname`, `traceinfo`, `traceir`, `tracek`, `tracesnap`, `tracemc`, `traceexitstub` and
`ircalladdr` (DR-02). The API is undocumented, so the stubs declare names, not shapes.

### 4.5 `jit/profile.lua` (module `profile`, 2.1)
Opening block: `---LuaJIT sampling profiler, loaded with require("jit.profile")` / `local profile = {}`. Then:

| Name | Annotations | Doc |
| :-- | :-- | :-- |
| `profile.start(mode, func)` | `@param mode string`, `@param func fun(thread: thread, samples: integer, vmstate: string)` | Starts the profiler; callback receives the thread, the sample count and the VM state |
| `profile.stop()` | — | Stops the profiler |
| `profile.dumpstack(fmt, depth)` | `@overload fun(thread: thread, fmt: string, depth: integer): string`, `@param fmt string`, `@param depth integer`, `@return string` | Returns a string with a stack dump of the given thread, or of the current one |

### 4.6 `string/buffer.lua` (module `buffer`, 2.1)
Opening block: `---A string buffer object` / `---@class string.buffer` / `local buf = {}`. Then come
the methods, each declared as `function buf:<m>(<params>) end`. `SB` stands for `string.buffer`:

| Method | Annotations | Doc |
| :-- | :-- | :-- |
| `reset()` | `@return SB` | Empties the buffer and keeps its allocation |
| `free()` | `@return SB` | Empties the buffer and frees its allocation |
| `put(...)` | `@param ... string\|number\|table`, `@return SB` | Appends strings, numbers or objects with a __tostring metamethod |
| `putf(format, ...)` | `@param format string`, `@param ... any`, `@return SB` | Appends formatted output, as string.format |
| `putcdata(cdata, len)` | `@param cdata ffi.cdata`, `@param len integer`, `@return SB` | Appends len bytes from a cdata pointer |
| `set(str, len)` | `@param str string\|ffi.cdata`, `@param len? integer`, `@return SB` | Replaces the buffer contents with a string, or with len bytes from a cdata pointer |
| `reserve(size)` | `@param size integer`, `@return ffi.cdata ptr`, `@return integer len` | Reserves at least size bytes of write space and returns a pointer to it and its length |
| `commit(used)` | `@param used integer`, `@return SB` | Commits used bytes of previously reserved write space |
| `skip(len)` | `@param len integer`, `@return SB` | Skips (consumes) len bytes |
| `get(...)` | `@param ... integer\|nil`, `@return string ...` | Consumes and returns len bytes, or the whole buffer when len is nil, for each argument |
| `tostring()` | `@return string` | Returns the buffer contents as a string without consuming them |
| `ref()` | `@return ffi.cdata ptr`, `@return integer len` | Returns a pointer to the buffer data and its length |
| `encode(obj)` | `@param obj any`, `@return SB` | Serializes obj and appends it to the buffer |
| `decode()` | `@return any` | Deserializes and consumes one object from the buffer |

Then `---LuaJIT string buffer library, loaded with require("string.buffer")` / `local buffer = {}`, then:

| Name | Annotations | Doc |
| :-- | :-- | :-- |
| `buffer.new(size, options)` | `@overload fun(options: table): string.buffer`, `@param size? integer`, `@param options? table`, `@return string.buffer` | Creates a new buffer, optionally with an initial size and options |
| `buffer.encode(obj)` | `@param obj any`, `@return string` | Serializes obj into a string |
| `buffer.decode(str)` | `@param str string`, `@return any` | Deserializes one object from str |

The file ends with `return buffer`. `declaredNames` is `buf:<m>` for every method, plus `buffer.new`,
`buffer.encode` and `buffer.decode`.

### 4.7 `table/new.lua` and `table/clear.lua` (2.1)
These are function modules: `local function <f>(<params>) end` followed by `return <f>`, with no table.
- `new(narray, nhash)`: `@param narray integer`, `@param nhash integer`, `@return table`. Doc: Creates
  a table pre-sized for narray array elements and nhash hash entries; loaded with require("table.new").
- `clear(tab)`: `@param tab table`. Doc: Clears all keys and values from a table and keeps its
  allocated size; loaded with require("table.clear").

`declaredNames` is `{new}` and `{clear}`.

## 5. Stdlib copies: edits

Each copy starts as the bytes of `lua-5.1/<f>`, taken after [[BUG-513]], and then the edits below
are applied in the order listed:
- A **replace** substitutes the exact text `OLD` with `NEW`. `OLD` must occur exactly once.
- An **append** adds `TEXT` at the end of the file.

All text below is literal, including the trailing newline of each fenced block. The same edit list,
held as the `STDLIB_EDITS` constant, is what TC 11 applies (§7.3). That constant is the executable
copy of this list, and TC 11 checks it against the shipped copies. `ADDED[v][f]` is the set of names an append adds; a replace adds no names.

**E1 `builtin.lua`, both versions, replace.**
`OLD`:
```
---Loads Lua code from reader function
---@param reader fun(): string|nil
---@param chunkname? string
---@return function|nil, string|nil
function load(reader, chunkname) end
```
`NEW`:
```
---Loads a chunk from a string or a reader function; LuaJIT accepts the Lua 5.2 mode and env arguments
---@param chunk string|fun(): string|nil
---@param chunkname? string
---@param mode? string
---@param env? table
---@return function|nil, string|nil
function load(chunk, chunkname, mode, env) end
```

**E2 `builtin.lua`, both versions, replace.**
`OLD`:
```
---Loads Lua code from file
---@overload fun(): function
---@overload fun(filename: string): function|nil
---@param filename? string
---@return function|nil, string|nil
function loadfile(filename) end
```
`NEW`:
```
---Loads a chunk from a file, or from stdin when filename is nil; LuaJIT accepts the Lua 5.2 mode and env arguments
---@param filename? string
---@param mode? string
---@param env? table
---@return function|nil, string|nil
function loadfile(filename, mode, env) end
```

**E3 `math.lua`, both versions, replace.**
`OLD` is `---@param x number` / `---@return number` / `function math.log(x) end`.
`NEW` is `---@param x number` / `---@param base? number` / `---@return number` / `function math.log(x, base) end`.

**E4 `string.lua`, both versions, replace.**
`OLD` is `---@param n integer` / `---@return string` / `function string.rep(s, n) end`.
`NEW` is `---@param n integer` / `---@param sep? string` / `---@return string` / `function string.rep(s, n, sep) end`.

**E5 `os.lua`, both versions, replace.**
`OLD` is `---@param code? boolean|integer` / `function os.exit(code) end`.
`NEW` is `---@param code? boolean|integer` / `---@param close? boolean` / `function os.exit(code, close) end`.

In E3 to E5, `/` separates lines. Each line ends with `\n`.

**E6 `package.lua`, both versions, append.** ADDED: `package.searchpath`.
```

---Searches path for name, replacing each sep in name with rep (default: the directory separator)
---@param name string
---@param path string
---@param sep? string
---@param rep? string
---@return string|nil filename
---@return string|nil errmsg
function package.searchpath(name, path, sep, rep) end
```

**E7 `debug.lua`, both versions, append.** ADDED: `debug.upvalueid`, `debug.upvaluejoin`.
```

---Returns a unique identifier for upvalue n of function f
---@param f function
---@param n integer
---@return lightuserdata
function debug.upvalueid(f, n) end

---Makes upvalue n1 of closure f1 refer to upvalue n2 of closure f2
---@param f1 function
---@param n1 integer
---@param f2 function
---@param n2 integer
function debug.upvaluejoin(f1, n1, f2, n2) end
```

**E8 `table.lua`, 2.1 only, append.** ADDED: `table.move`.
```

---Moves elements a1[f..e] to a2[t..] and returns a2 (a2 defaults to a1)
---@param a1 table
---@param f integer
---@param e integer
---@param t integer
---@param a2? table
---@return table
function table.move(a1, f, e, t, a2) end
```

**E9 `coroutine.lua`, 2.1 only, append.** ADDED: `coroutine.isyieldable`.
```

---Returns true when the running coroutine can yield
---@return boolean
function coroutine.isyieldable() end
```

Every other `ADDED[v][f]` is empty. The appended functions come from DR-02's inventory, and the
signatures follow `extensions.html` ("Extensions from Lua 5.2/5.3"). Under BUG-513's fix, E7's
additions resolve under LuaJIT, and so do the `debug.*` functions that are hidden today (DR-14).
Each `OLD` anchor occurs exactly once in `84fa67b0`'s `lua-5.1` files, and BUG-513's fix strategy
(§4 step 6) leaves them unchanged. No name BUG-513 adds is also added by an E edit. If an anchor no
longer matches when Phase 2 runs, the implementer stops
and reports it (implementation-plan Phase 2).

## 6. Header

Every LuaJIT-specific file begins with exactly these 25 lines, which TC 13 holds as the `HEADER`
constant. Lines 5 to 21 are lines 6 to 22 of LuaJIT's `COPYRIGHT` file (commit `2a8e017e`), each
prefixed with `-- `, or with `--` alone when the source line is empty. The double space after
`NONINFRINGEMENT.` is in the original.
```
-- LuaJIT -- a Just-In-Time Compiler for Lua. https://luajit.org/
--
-- Copyright (C) 2005-2026 Mike Pall. All rights reserved.
--
-- Permission is hereby granted, free of charge, to any person obtaining a copy
-- of this software and associated documentation files (the "Software"), to deal
-- in the Software without restriction, including without limitation the rights
-- to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
-- copies of the Software, and to permit persons to whom the Software is
-- furnished to do so, subject to the following conditions:
--
-- The above copyright notice and this permission notice shall be included in
-- all copies or substantial portions of the Software.
--
-- THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
-- IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
-- FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.  IN NO EVENT SHALL THE
-- AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
-- LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
-- OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
-- THE SOFTWARE.
--
-- API stubs for Lunar, describing the API documented at https://luajit.org/.

---@meta
```
The stdlib copies keep their Lua.org header. `THIRD-PARTY.md` edits (TC 17 checks each one):
- In "Lua standard-library API stubs", add the files line
  `` `src/main/resources/runtime/luajit/luajit-2.{0,1}/{builtin,coroutine,debug,io,math,os,package,string,table}.lua` (copies of `lua-5.1`) ``.
- Under "MIT License", after "MobDebug", add this section:
  ```
  ### LuaJIT API stubs (Mike Pall)

  - **Copyright:** (C) 2005-2026 Mike Pall
  - **License:** MIT
  - **Version:** LuaJIT v2.0 `acf8fcbd`, v2.1 `2a8e017e` (the API inventories the stubs follow)
  - **Usage:** API stubs written from the LuaJIT documentation and the executed API of those builds.
  - **Files:** the LuaJIT-specific files under `src/main/resources/runtime/luajit/` (all except the
    `lua-5.1` copies)
  - **Upstream:** https://luajit.org/
  ```
- Under "License texts", change the **MIT (Lua)** line to end
  `` under `src/main/resources/runtime/standard/` and of each `lua-5.1` copy under `src/main/resources/runtime/luajit/`. ``
  Then add the line
  `` - **MIT (LuaJIT)** — the full notice is preserved at the top of each LuaJIT-specific stub file under `src/main/resources/runtime/luajit/`. ``

## 7. Tests

### 7.1 Classes

All new classes are JUnit4, written like `RedisAmbientTypingTest`: `@RunWith(JUnit4::class)` on the
class and `@Test` on each method, with methods named `testTcNN<Description>`. JUnit4 matters because
a JUnit3-discovered Kotlin class also picks up synthetic `test…$lambda$N` methods, which fail in a
full-suite run.
- `net.internetisalie.lunar.platform.target.LuaJitRuntimeStubsTest : IndexedBasePlatformTestCase`
  (`src/test/kotlin/.../platform/target/`) covers TCs 1–9, 15, 16, 18 and 19. `setUp` calls
  `myFixture.enableInspections(LuaUndeclaredVariableInspection())`, and `tearDown` calls
  `useTarget(LuaPlatform.STANDARD, "5.4")` before `super.tearDown()`, as `RedisAmbientTypingTest` does.
- `net.internetisalie.lunar.platform.target.LuaJitStubResourcesTest : BasePlatformTestCase` covers
  TCs 10–13 and 17. It reads resources with
  `LuaJitStubResourcesTest::class.java.classLoader.getResource("runtime/<dir>/<rel>")?.readText()`,
  reads `THIRD-PARTY.md` as `File(System.getProperty("user.dir"), "THIRD-PARTY.md").readText()`, and
  uses `myFixture.configureByText` for TC 12.

`useTarget(platform: LuaPlatform, label: String)`, the target helper, runs inside
`EdtTestUtil.runInEdtAndWait`:
```kotlin
val version = requireNotNull(PlatformVersionRegistry.findVersion(platform, label))
val settings = LuaProjectSettings.getInstance(project)
settings.setTargetAndNotify(Target(platform, version))
RuntimeLibraryProvider(project).getLibraryRoot(settings.state.getTarget())?.let { root ->
    VfsUtil.collectChildrenRecursively(root)
    VfsUtil.markDirtyAndRefresh(false, true, true, root)
}
PlatformLibraryIndex.reload()
```
Both VFS calls are needed, in that order. A refresh reaches only directories the VFS has loaded, so
a subdirectory left over from an earlier sandbox run keeps its old file list. That list can lack new
files or still hold deleted ones. Loading the tree first and then refreshing it gives the true
listing (DR-12). PSI reads, `multiResolve`, `LuaRequireReference.resolve()` and `findChildrenOfType`
run inside `runReadAction`. `doHighlighting()` and `completeBasic()` are called as the existing
inspection and completion tests call them.

### 7.2 `declaredNames(text: String): Set<String>`
This is a private function in `LuaJitStubResourcesTest`. For each line of `text`, it tests the line
with `R1`, then `R2`, then `R3`, which are anchored at column 0 and not trimmed. Each match adds its
group 1 to the result.
- `R1 = Regex("^function\\s+([A-Za-z_][A-Za-z0-9_.:]*)\\s*\\(")`
- `R2 = Regex("^([A-Za-z_][A-Za-z0-9_.]*)\\s*=")`
- `R3 = Regex("^local\\s+function\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\(")`

`local x = {}` lines match none of them. TC 10 compares `declaredNames` with the §4 sets, which the
test holds as `Map<String, Set<String>>` constants per version.

### 7.3 TC 11 byte-exact rebuild
`STDLIB_EDITS` is a `List<StdlibEdit>`, where
`data class StdlibEdit(val file: String, val versions: Set<String>, val old: String?, val new: String)`.
`old == null` means an append. The list holds E1–E9 in order, with exactly the §5 text.
`rebuild(v, f)` starts from the text of `standard/lua-5.1/<f>.lua`. For each edit with
`file == f` and `v in versions`, it either:
- asserts `text.split(old).size == 2` and then replaces `old` with `new`, or
- appends `new`, for an append.

TC 11 asserts `rebuild(v, f) == text of luajit/luajit-<v>/<f>.lua` for every stdlib copy in §3 and
both versions. Any edit to `lua-5.1/`, or to a copy, that is not mirrored on the other side fails.

### 7.4 Resolver rule tests (TC 18, TC 19)
TC 19 is in `LuaJitRuntimeStubsTest`. It builds this layout with `myFixture.tempDirFixture`
(creating files writes, so this happens outside the read action; the verdict calls run inside one):
- `findOrCreateDir("vroot")` as the root;
- `createFile("vroot/pkg/init.lua", "return {}")`, `createFile("vroot/jit/util.lua", "return {}")`
  and `createFile("vroot/top.lua", "return {}")` inside it;
- `createFile("elsewhere/util.lua", "return {}")` outside it.

It then asserts `runtimeRootVerdict(file, root, module)` for each row of the TC 19 table.

TC 18 is also in `LuaJitRuntimeStubsTest`. It uses `useTarget(LUAJIT, "2.1")` and a file with one
`local _ = require("<m>")` line for each `m` in `util`, `new`, `clear`, `buffer`, `profile`,
`jit.util` and `string`. It asserts the `LuaRequireReference.resolve()` paths: `null` for the first
five, `…/luajit-2.1/jit/util.lua` and `…/luajit-2.1/string.lua` for the other two. A second case adds
`myFixture.addFileToProject("util.lua", "return {}")` and asserts that `require("util")` resolves to
`/src/util.lua`.

### 7.5 Existing tests changed (Phase 1)
- `RuntimeLibraryProviderTest.kt:95`: replace `Target(LuaPlatform.LUAJIT, VersionEntry("2.1", "luajit-2.1"))`
  with `Target(LuaPlatform.STANDARD, VersionEntry("unbundled", "never-bundled"))`. This test passes
  the `Target` straight to `RuntimeLibraryProvider`, so an unregistered entry works, and no release
  will ever bundle `runtime/standard/never-bundled`. Rename the method from
  `testGetLibraryFilesForFuturePlatformReturnsEmpty` to `testGetLibraryFilesForUnbundledTargetReturnsEmpty`,
  and change its message to `"An unbundled target should return an empty list"`.
- `LuaSettingsNotificationTest.kt:74`: replace `Target(LuaPlatform.LUAJIT, VersionEntry("2.0", "luajit-2.0"))`
  with `Target(LuaPlatform.TARANTOOL, VersionEntry("2.10", "tarantool-2.10"))`. The never-bundled
  target cannot be used here. This test goes through the settings, which store only the label, and
  `TargetState.toTarget()` (`LuaProjectSettings.kt:43-47`) falls back to the platform's first
  registered version for an unknown label (DR-15). Tarantool 2.10 is registered and has no bundled
  directory; when Tarantool gets stubs,
  this line must move to the next unbundled registered target.
- In the same class, add a `tearDown` that runs
  `LuaProjectSettings.getInstance(project).setTargetAndNotify(Target.default())` and
  `PlatformLibraryIndex.reload()` inside `EdtTestUtil.runInEdtAndWait`, then calls `super.tearDown()`.
  Today the class leaves its last target in the shared light project.
- `LuaRocksInterpreterInitializerTest.kt:27`: replace `LuaProjectSettings.getInstance(project).state.target = null`
  with the same `EdtTestUtil` block. Today it nulls the target but leaves `languageLevel` at `LUA51`
  and the library roots unreloaded, after `testLuaJitKindMapsToLuaJitTarget` set LuaJIT 2.1.
- `RedisAmbientTypingTest.kt:76-85`: replace the stale `NOTE (fixture limitation …)` paragraph,
  every line from `// NOTE (fixture limitation` through `// human-verification-checklists Scenario 4.1.`,
  with these four comment lines:
  ```
      // The "no undeclared-variable highlight on KEYS/ARGV" half of TC-KEYS-1 rides the
      // reference-resolution path (LuaUndeclaredNames -> multiResolve), not the type path tested
      // here. Bundled stub globals do resolve in a light fixture (TARGET-12-00-DR-06: KEYS ->
      // global.lua under Redis 7+); this class does not assert that half.
  ```

DR-15 lists which tests change expectations, measured with and without these changes.

## 8. Consumers

- **MAINT-39 (corpus sweep)**, if it adds a LuaJIT-targeted member, which is MAINT-39's decision.
  `LuaCorpusSweepTest.kt:119` sets only `state.languageLevel`, so the target, and with it the stub
  root, stays at whatever the shared light project last held. Such a member would need
  `useTarget(LuaPlatform.LUAJIT, "2.1")`, the §7.1 helper, before `measure`, and a teardown that
  restores `STANDARD 5.4`. TARGET-12 only provides the root.
- **NGX.** `Target(NGX, "latest")` resolves to `runtime/ngx/ngx-latest`, which is absent, so it gets
  no root. It does not inherit LuaJIT stubs, and this feature does not change that (requirements
  §Out of Scope).

## 9. Threading and contract
The only production change, §1.1, runs where `resolveModuleCandidates` already runs: inside the
caller's read action, with no VFS refresh (`refresh = false` semantics are kept). It adds one
`getPlatformLibraryFolder` call per `require` resolution (§1.1). `runtimeRootVerdict` has
3 parameters and 4 logic lines; the loop stays under 30 lines.
Tests follow engineering contract §5 (light fixtures, `configureByText`), and their helpers take at
most two arguments.

## Open Questions
None. Every runtime premise was executed (risks-and-gaps, DR table).
