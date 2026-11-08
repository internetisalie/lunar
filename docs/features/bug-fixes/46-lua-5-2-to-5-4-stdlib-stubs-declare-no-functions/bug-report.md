---
id: "BUG-46"
title: "The bundled Lua 5.2–5.4 stdlib stubs for math, string, os, debug, coroutine and utf8 declare no functions"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-46: The bundled Lua 5.2–5.4 stdlib stubs for math, string, os, debug, coroutine and utf8 declare no functions

Found while fixing [[BUG-38]].

## 1. Reproduction
`src/main/resources/runtime/standard/lua-5.{2,3,4}/{math,string,os,debug,coroutine}.lua` (and `utf8.lua`
for 5.3/5.4) are 27 lines each: the licence header and an empty `---@class` table. `lua-5.1/math.lua`
is 180 lines and declares 28 functions. On language level 5.4, `math.floor()` resolves to no signature,
so its argument count and types cannot be checked. This was observed in a BUG-38 probe.

## 2. Expected vs Actual Behavior
- **Expected**: each version's stub declares that version's library functions with `---@param` /
  `---@return` (optional parameters marked `?`, varargs and `@overload`s where the manual has them).
- **Actual**: no function is declared for these modules on 5.2–5.4.

## 3. Context / Environment
- **Lua Version**: 5.2, 5.3, 5.4.
- **Other Notes**: not yet measured: what completion, hover and inlay hints show for `string.*` /
  `math.*` on these levels, and whether any fallback supplies them. The 5.5 and LuaJIT stubs were not
  inspected. Stub precision (optionals, overloads) is a prerequisite for counting arguments to stdlib
  callees (BUG-38).

## 4. Intended Approach
- Carry the 5.1 stubs (checked against the 5.1 API in BUG-513) forward to 5.2, 5.3 and 5.4, keeping their
  style, and apply each version's changes from the reference manual. Examples: `load(chunk, chunkname?,
  mode?, env?)` and `os.exit(code?, close?)` from 5.2; `math.tointeger` from 5.3; `math.random`'s 5.4
  changes.
- Use LuaLS's `meta/template` definitions (MIT) only to cross-check signatures, optionals and overloads,
  not as copied text.
- Before the stubs count toward stdlib arity (the scope deferred from [[BUG-38]]), separate calls valid
  only in some language versions from calls that run but break the declared signature
  (for example `error()` with no message).

## 5. Measurements
Measured at `53ec4288` with a throwaway light-fixture probe (libvirt builder): for each target, the
resolve targets of `string.format`, `math.floor` and `os.time`, the Quick Doc target of `math.floor`, and
the completion list after `math.`.

| Target | Library root | `format` / `floor` / `time` resolve to | Quick Doc `math.floor` | `math.` completion |
|--------|--------------|-----------------------------------------|------------------------|--------------------|
| Standard 5.1 | `runtime/standard/lua-5.1` | `lua-5.1/{string,math,os}.lua` | rendered | 30 items |
| Standard 5.2 | `runtime/standard/lua-5.2` | nothing | none | 0 items |
| Standard 5.3 | `runtime/standard/lua-5.3` | nothing | none | 0 items |
| Standard 5.4 | `runtime/standard/lua-5.4` | nothing | none | 0 items |
| Standard 5.5 | none (`getLibraryRoot` returns null) | nothing | none | 0 items |
| LuaJIT 2.1 | `runtime/luajit/luajit-2.1` | `luajit-2.1/{string,math,os}.lua` | rendered | 30 items |

- **(a) 5.2–5.4:** the functions are missing. No layering exists: `RuntimeLibraryProvider` returns one
  root per target and `LuaLibraryProvider` registers only that root, so the 5.1 stubs are never
  underneath. Completion, Quick Doc and resolution are all empty for these modules, so no argument or
  return type of a call into them is checked. `lua-5.2/bit32.lua` is empty in the same way.
- **(b) 5.5:** there is no `runtime/standard/lua-5.5` directory, so 5.5 has no stdlib at all — not only
  these modules but `print`, `io`, `table` and the rest. [[TARGET-07]] (status `planned`) is the
  feature that ships it, by copying the 5.4 set and applying the 5.5 delta. That is a different gap from
  this one and is left to TARGET-07; once this fix lands, its copy of `lua-5.4/` carries these
  declarations.
- **(c) LuaJIT:** no gap. Every `luajit-2.0`/`luajit-2.1` module declares its functions (2.1: `math` 28,
  `string` 14, `os` 11, `debug` 16, `coroutine` 7).

## 6. Root Cause
`src/main/resources/runtime/standard/lua-5.{2,3,4}/{math,string,os,debug,coroutine}.lua`,
`lua-5.{3,4}/utf8.lua` and `lua-5.2/bit32.lua` contain only the licence header, `---@meta` and an empty
`---@class <module>` table. The other modules of those levels (`builtin`, `io`, `table`, `package`)
were populated; these were not. With no member declarations, the module class has no members.

## 7. Fix Strategy
Resource-only. Declare in each file exactly the functions and constants that version's reference
manual documents (§6 of the 5.2.4, 5.3.6 and 5.4.7 manuals), in the 5.1 stubs' style, with the
version's signatures:
- **5.2:** `math.log(x, base?)`, `math.max/min(x, ...)`; `string.rep(s, n, sep?)`; `os.execute(command?)`
  returning status, `"exit"|"signal"` and code; `os.exit(code?, close?)`; `os.time(table?)`;
  `debug.getinfo/getlocal/sethook/setlocal/traceback` with the optional leading `thread`;
  `debug.getuservalue/setuservalue/upvalueid/upvaluejoin`; `coroutine.running()` returning a boolean
  too; `coroutine.wrap(f)`; the `bit32` library. Not declared: `math.log10` (deprecated, not in the
  manual), `string.gfind`, `math.mod`, `debug.getfenv/setfenv`.
- **5.3:** adds `math.tointeger/type/ult/maxinteger/mininteger`, `math.atan(y, x?)`,
  `string.pack/packsize/unpack`, `string.dump(f, strip?)`, `coroutine.isyieldable()` and `utf8`; drops
  `math.atan2/cosh/sinh/tanh/pow/frexp/ldexp` (deprecated and absent from the manual). `os.difftime`
  takes two required integers.
- **5.4:** adds `coroutine.close`, `coroutine.isyieldable(co?)`, `string.gmatch(s, pattern, init?)`,
  `math.randomseed(x?, y?)` returning both seed parts, the `n` user-value index on
  `debug.getuservalue/setuservalue`, and `lax?` on `utf8.codes/codepoint/len`. `debug.setcstacklimit` is
  not declared (not in the 5.4.7 manual).

- **Basic library (`builtin.lua`):** `getfenv` and `setfenv` are removed from 5.2 and 5.3 (absent from
  both manuals; 5.2 removed them), and 5.4 gains `warn(msg, ...)`. `module` stays `---@deprecated` on 5.2
  and 5.3 as before.

The engine type-checks a call against the **primary** signature only (overloads are not modelled), so
each primary signature is the widest of its forms — every argument that may be omitted is `?`, and a
leading optional `thread` is typed as the union of both forms' first argument — and the `@overload`s
carry the individual forms for parameter info. A parameter is optional where the C implementation
defaults it (`luaL_opt*`), even if the manual's bracket shows otherwise. `os.date` keeps the 5.1 stub's
`string` result, with the `"*t"` form as an overload, so a `string|table` union does not reach callers
that format dates. `string.sub`'s `i` is required, as `luaL_checkinteger` demands.
LuaLS `meta/template` was used only to cross-check optionals.

Stdlib arity stays uncounted, as [[BUG-38]] left it; §4's separation is still the prerequisite for that.

## 8. Test Strategy
`LuaStandardLibraryStubsTest` (light `IndexedBasePlatformTestCase`, switching the project target):
on 5.2, 5.3 and 5.4 `string.format`, `math.floor` and `os.time` resolve into that level's stub; per level,
version-specific functions resolve with their rendered signatures (5.2 `debug.upvalueid`,
`bit32.extract`, `os.exit`; 5.3 `math.tointeger`, `utf8.offset`, `string.dump`; 5.4 `coroutine.close`,
`math.randomseed`, `utf8.len`), and functions the level does not have do not resolve (5.2
`debug.setfenv`, `string.gfind`, `math.tointeger`; 5.3 `math.pow`, `math.atan2`, `coroutine.close`; 5.4
`math.pow`, `math.log10`, `string.gfind`); `getfenv`/`setfenv` do not resolve on 5.2 and 5.3, and `warn` resolves into
`lua-5.4/builtin.lua`. A script check confirmed that each level's declared names are
exactly the manual's documented names and that every file passes that version's `luac -p`.

## 9. Verification
- `LuaStandardLibraryStubsTest`: 4/4 green; mutation-proved by restoring `lua-5.3/math.lua` to its
  empty `53ec4288` form, which turns the 5.3 test and the common-functions test red.
- Full suite `test --rerun --no-build-cache` (libvirt): 3654 tests, 0 failures, 4 skipped. `ktlintCheck` green.
- **Corpus sweep** (`test -PwithCorpus --rerun --no-build-cache`) against a `53ec4288` control: no finding
  added or removed in any member. An earlier run of this fix reported two extra zerobrane hits
  (*Suspicious concatenation: operand of type 'nil'*, `src/editor/menu_project.lua:96`, `:136`). They are
  [[BUG-541]]'s flicker and not caused by the stubs: a probe after the sweep printed the operand
  `ide.config.default.interpreter` as `nil` with the `53ec4288` stubs too, and a light-fixture repro with no
  stdlib involvement reproduces it. The false `nil` is [[BUG-47]]; the zerobrane baseline keeps the high
  reading, with a comment naming both.
- **Type goldens** regenerated for four member files (`luacheck-cache`, `luarocks-zip`, `penlight-Date`,
  `zerobrane-commands`). Every change is a stdlib call that now has its declared type: `undefined` →
  `nil | string` (`os.getenv`), `undefined` → `number`/`string`, `nil` → `nil | number` (`string.find`),
  inferred `fun(p)` shapes → the declared parameter names, and subtree hashes that follow. One new
  `HYPOTHESIS` in `luarocks-zip` (`table.insert(out, string.char(byte))`): the engine checks the
  two-argument call against `table.insert`'s three-argument primary signature, so `string` meets
  `pos?: integer`. A hypothesis is not an inspection finding (BUG-419).
- `TypeAuditHarnessTest.testTC08…` used `string.upper('a')` as an unresolved callee, true only while this
  bug existed; its fixture now calls the undeclared `noSuchHelper('a')`.
- Lua 5.5 has no stdlib root at all; shipping it is [[TARGET-07]]'s job.
