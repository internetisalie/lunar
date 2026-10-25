---
id: "BUG-513"
title: "The Lua 5.1 stdlib stubs disagree with Lua 5.1's API: documented members are missing, nonexistent members are declared, and a `local` parameter hides most of the debug library"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-513: the `lua-5.1` stdlib stubs do not match Lua 5.1

Found while planning [[TARGET-12]], which copies `runtime/standard/lua-5.1/` byte for byte into the
LuaJIT roots and depends on this fix.

## 1. Reproduction

Light fixture, target `STANDARD 5.1`. A file contains one `print(<name>)` per line, and
`multiResolve` is run on the last name segment.

Missing members resolve to nothing:
```
string.match -> 0   string.gmatch -> 0   os.clock -> 0   math.deg -> 0
debug.getupvalue -> 0   package.preload -> 0   table.getn -> 0
```
Controls resolve: `string.format -> 1`, `os.time -> 1` and `debug.getinfo -> 1`. A name Lua 5.1 does
not have resolves too: `os.getwd -> 1`. (`debug.getfunc -> 0` is because of the parse error below,
not because the declaration is absent.)

`debug.lua:40` and `:48` use the reserved word `local` as a parameter name:
`function debug.getlocal(level, local) end`. LuaJIT `loadfile` reports
`debug.lua:40: <name> or '...' expected near 'local'`, and Lunar reports a `PsiErrorElement` at line 40.
After that point, `debug.setlocal`, `getfunc`, `sethook`, `gethook`, `setfenv`, `getfenv` and
`traceback` never resolve under STANDARD 5.1. All other names the stubs declare do resolve.

Executed 2026-10-15 on `84fa67b0`, in TARGET-12's probes: `TARGET-12-00-DR-14`, and run `r18`.

## 2. Expected vs Actual
- **Expected**: the stubs declare exactly this target set: today's declared names, minus set D,
  plus set A, plus the six compatibility names that §4 step 3 adds (all sets are in §3). Each of those
  names resolves under a Lua 5.1 target, and nothing outside the set is declared.
  - Names that are not in Lua 5.1 must not resolve, `debug.getfunc` and `os.getwd` included.
  - The stub files are valid Lua.
- **Actual**: the expected set differs from the stubs in three ways:
  - Set A is missing.
  - The extras in set D are declared.
  - `local` is used as a parameter name, which hides seven `debug.*` declarations.

## 3. The expected set (ground truth)

The ground truth is the "Lua functions" index of the Lua 5.1 reference manual (§5,
`https://www.lua.org/manual/5.1/`). It was compared with an executed dump of PUC Lua 5.1.5, built from
`lua-5.1.5.tar.gz` (sha256 `2640fc56…695333`), and with the names the stubs declare. The stubs'
`File:x` methods are compared as the manual's `file:x`. The table below is the script's output
(`three_way.py`), with four entries dropped from set B:
- `arg`, a global the standalone interpreter sets for a script, not a library member;
- `file:__gc`, `file:__index` and `file:__tostring`, the file handle's metamethods, which the manual
  does not list as functions.

| Set | Names |
| :-- | :-- |
| **A**: in the manual, missing from the stubs | `debug.debug`, `debug.getmetatable`, `debug.getregistry`, `debug.getupvalue`, `debug.setmetatable`, `debug.setupvalue`, `file:setvbuf`, `math.deg`, `math.rad`, `os.clock`, `os.difftime`, `os.setlocale`, `os.tmpname`, `package.loaders`, `package.preload`, `string.gmatch`, `string.match` |
| **B**: in 5.1.5 but not in the manual (compatibility functions), missing from the stubs | `gcinfo`, `math.mod`, `newproxy`, `package.config`, `string.gfind`, `table.foreach`, `table.foreachi`, `table.getn`, `table.setn` |
| **D**: declared by the stubs, in neither the manual nor 5.1.5 | `debug.getfunc`, `dostring`, `file:iseof`, `io.setvbuf`, `os.getwd`, `os.tmpfile`, `package.preloaded`, `package.searchers`, `rawlen` |
| **E**: in the manual but not in the 5.1.5 build | none |

The same names were then checked against the executed dumps of LuaJIT 2.0 (`acf8fcbd`) and 2.1
(`2a8e017e`). This matters because TARGET-12 copies the fixed files into both LuaJIT roots.
- Every name in A is present in both LuaJIT builds.
- Every name in D is absent from both.
- In B, `gcinfo`, `newproxy`, `package.config`, `table.foreach`, `table.foreachi` and `table.getn`
  are present in 5.1.5 and in both LuaJIT builds.
- `string.gfind` and `math.mod` are absent from LuaJIT 2.1, and `table.setn` from both LuaJIT builds.

## 4. Fix strategy
1. In `debug.lua`, rename the `local` parameter of `getlocal` and `setlocal` to `index`, in the
   `@param` tags and in the declarations.
2. **Add set A**, with the signatures the manual gives, in the sibling block style:
   - in `debug.lua`: `debug.debug`, `debug.getmetatable`, `debug.getregistry`, `debug.getupvalue`,
     `debug.setmetatable`, `debug.setupvalue`;
   - in `io.lua`: `File:setvbuf`;
   - in `math.lua`: `math.deg`, `math.rad`;
   - in `os.lua`: `os.clock`, `os.difftime`, `os.setlocale`, `os.tmpname`;
   - in `package.lua`: `package.loaders`, `package.preload`;
   - in `string.lua`: `string.gmatch`, `string.match`.
3. **Add the compatibility names common to 5.1.5, LuaJIT 2.0 and LuaJIT 2.1**:
   - with `---@deprecated`, because later Lua versions removed them: in `builtin.lua`, `gcinfo` and
     `newproxy`; in `table.lua`, `table.foreach`, `table.foreachi` and `table.getn`;
   - without `---@deprecated`: `package.config` in `package.lua`. No Lua version deprecates it. Lua
     5.2 documented it, and code reads it for the directory separator (`package.config:sub(1, 1)`).

   Leave out `string.gfind`, `math.mod` and `table.setn`:
   - **5.1 side.** None of the three is in the 5.1 manual, which is this report's ground truth.
     - `string.gfind` and `math.mod` do work in a default 5.1.5 build: `LUA_COMPAT_GFIND` and
       `LUA_COMPAT_MOD` are on (`luaconf.h:358`, `:343`), and executed 5.1.5 prints both as
       functions.
     - Consequence: a STANDARD 5.1 user who calls either one gets no navigation or completion for
       it. The library tables (`string`, `math`) stay declared, so the call itself should not be
       reported as undeclared. That is read from `LuaUndeclaredVariableInspection.isMemberName`,
       which skips member names, and was not executed.
     - This is acceptable because both are deprecated, undocumented aliases: `string.gmatch` and
       `math.fmod` are the documented names.
     - `table.setn` exists in 5.1.5 but raises an error. `lauxlib.h:23` defines `luaL_setn` as a
       no-op macro, so `ltablib.c:83` takes the `luaL_error(… "setn" " is obsolete")` branch.
       Executed: `pcall(table.setn, {}, 3)` returns `false 'setn' is obsolete`.
   - **LuaJIT side.** Declaring them would put names that LuaJIT 2.1 lacks, and `table.setn` also
     2.0, into TARGET-12's byte-exact copies.
   - **The alternative**, kept for whoever plans this fix: keep `string.gfind` and `math.mod` in
     `lua-5.1/`, and have TARGET-12 remove them from the 2.1 copy with an extra edit. This report
     chooses the manual-only rule because it is simpler and both names are deprecated.
4. **Remove set D.** `io.setvbuf` becomes `File:setvbuf`, which is step 2. `package.searchers` and
   `package.preloaded` become `package.loaders` and `package.preload`, also step 2. Delete
   `debug.getfunc`, `dostring`, `File:iseof`, `os.getwd`, `os.tmpfile` and `rawlen`.
5. **Rewrite the non-standard LuaCATS forms** to the forms Lunar parses, measured in TARGET-12's
   run `r19`:
   - `---@return ...` becomes `---@return any ...` (`builtin.lua:207`, `:266`; `coroutine.lua:60`;
     `io.lua:102`);
   - `---@type fun[]` becomes `---@type function[]` (`package.lua:37`);
   - `---@overload fun(opt: '"stop"'): nil` becomes `---@overload fun(opt: "stop"): nil`
     (`builtin.lua:133`).

   The other two forms are left alone; see §5.
6. **Leave alone** the replace anchors that TARGET-12's design §5 edits (E1 to E5): the `load` and
   `loadfile` blocks, `math.log`, `string.rep` and `os.exit`. TARGET-12 stops if one of them no
   longer matches.

**Test.** There are three checks.
1. **Names.** A test sets STANDARD 5.1. For every non-method name in A and in the step-3 list, it
   writes `print(<name>)` and runs `multiResolve` on the last segment, expecting exactly one target.
   For every non-method name in D, it expects none.
2. **File methods.** `print(<name>)` cannot reach these: `File` is a file-local class
   (`io.lua:25-26`), so `print(File.setvbuf)` resolves to nothing whether or not the method exists.
   Two checks cover them instead:
   - **Resolution through a typed receiver**: `io.stdout:setvbuf("no")`, where `io.stdout` is
     declared `---@type File` (`io.lua:152-153`), with `multiResolve` on `setvbuf`. Colon-call
     resolution through a `---@type` receiver has not been executed here, so whoever plans this fix
     runs it first. If it does not resolve, they use only the text check below for both methods.
   - **A text check that can fail both ways**: in `io.lua`'s text, `function File:setvbuf(` occurs
     once and `function File:iseof(` does not occur. Re-adding `File:iseof` turns the second
     assertion red.
3. **Parse.** A test parses `debug.lua` and expects no `PsiErrorElement`.

The mutation is to revert any one step: a name or text assertion in the relevant list then fails.

## 5. Out of scope: Lunar parser gaps found on the way
Two of the stubs' forms are rejected by Lunar's LuaCATS parser even in their standard spelling
(run `r19`):
- a string-literal union in `@return`. The current `'"nil"'|…` errors, and so does `"nil"|"boolean"|…`
  (`builtin.lua:92`; `coroutine.lua:48`; `io.lua:144`);
- a vararg parameter inside a `fun()` type. `fun(...any)` errors, and so does `fun(...: any)`
  (`string.lua:66`).

Neither form hides any declaration (`TARGET-12-00-DR-14`). They are parser gaps, not stub defects,
and are filed as BUG-514.

## 6. Context
- Only `lua-5.1/debug.lua` uses `local` as a parameter name (grep over `runtime/`).
- BUG-509's report records a throw while indexing this file. That throw came from a `scopeCrawlUp`
  prototype that was never shipped. On `84fa67b0`, parsing and indexing the file give the parse error
  above and do not throw.
- Consumer: [[TARGET-12]]. Its copies are byte-exact rebuilds of the fixed `lua-5.1/`, and its TC 11
  fails if the two diverge.

## Resolution

Fixed in the nine `src/main/resources/runtime/standard/lua-5.1/*.lua` files, following §4 steps 1–6.
The tests are in `src/test/kotlin/net/internetisalie/lunar/platform/target/Lua51StdlibStubsTest.kt`.
Measured on the libvirt builder, on parent `74792f6a` plus this diff.

**Departures from §4 as first written.**
- **Step 3, `package.config`.** Corrected in review. No Lua version deprecates it, so it is
  declared without `---@deprecated`. §4 step 3 above now says so.
- **Step 5, `collectgarbage`.** Lunar reports one error per LuaCATS comment, so rewriting only the
  `"stop"` overload at `builtin.lua:133` moved the error to the `"restart"` overload on the next line.
  All of that block's overloads are rewritten to the `fun(opt: "<x>")` form. Review also asked for
  their content to match 5.1, which the old stub got wrong. The changes were checked against the
  manual's §5.1 and `lbaselib.c` `luaB_collectgarbage`, and executed on PUC 5.1.5, LuaJIT 2.0 and
  LuaJIT 2.1:
  - `opt` is optional. `luaL_checkoption(L, 1, "collect", …)` makes `"collect"` the default, so
    there is a `fun(): integer` overload.
  - `"stop"`, `"restart"` and `"collect"` return 0, `lua_pushnumber(L, res)`. Before, they returned
    `nil`.
  - `"count"` returns one number, the Kbytes in use. Before, it returned `integer, integer`.
  - `"step"` returns a boolean. Its `arg` is optional (`luaL_optint`), so there are overloads with
    and without it.
  - `"setpause"` and `"setstepmul"` return the previous value.

  Executed: `count=number:30.97…`, `step=boolean:false`, `setpause=number:200`, `stop=number:0`
  and `collectgarbage()=0`, each a single value, on all three interpreters.
- **Test check 2, typed receiver.** It was run first, with a control. On the unfixed tree,
  `io.stdout:close()` resolves to nothing, although `File:close` is declared, so a colon call
  through the `---@type File` receiver cannot tell a declared method from a missing one. Only the
  text check is used for the `File` methods. This is filed as [[BUG-516]].

**Deprecation.** `testOnlyRemovedCompatibilityFunctionsWarnAsDeprecated` runs
`LuaDeprecatedApiInspection` under STANDARD 5.1 over the six compatibility names. Exactly `gcinfo`,
`newproxy`, `foreach`, `foreachi` and `getn` warn, and `package.config` does not.
- With the tag still on `package.config` (the first commit's `package.lua`), the inspection part
  still passed. A probe found that `---@deprecated` on a field assignment never warns at a use: not
  for `pkg.cfg = ""`, not for `local t = {}; t.x = 1`, and in the same run `function pkg.f()` did
  warn. So the first commit's tag could not have produced a false warning in today's Lunar.
- The test therefore also checks that `package.config`'s comment block in the stub text has no
  `---@deprecated`. Re-adding the tag turns it red: `package.config is not deprecated`.

**Red, then green.** On the unfixed tree, every test failed for the reason §1 states:

| Test | Unfixed result |
| :-- | :-- |
| `testManualFunctionsMissingFromTheStubsResolve` | all 16 set-A names `-> 0` |
| `testCompatibilityFunctionsResolve` | all 6 step-3 names `-> 0` |
| `testNamesOutsideLua51DoNotResolve` | `dostring`, `io.setvbuf`, `os.getwd`, `os.tmpfile`, `package.preloaded`, `package.searchers` and `rawlen` `-> 1` |
| `testDebugFunctionsAfterGetlocalResolve` | `debug.setlocal`, `sethook`, `gethook`, `setfenv`, `getfenv` and `traceback` `-> 0` |
| `testIoStubDeclaresSetvbufMethodAndNotIseof` | `File:setvbuf` occurs 0 times |
| `testDebugStubParsesWithoutErrors` | one error at line 40 |
| `testOnlyRemovedCompatibilityFunctionsWarnAsDeprecated` | added in review, and red on the first commit's `package.lua`: `package.config is not deprecated` |

After the fix, all seven pass.

**Mutations.** Each was run on the fixed tree, and each test went red, naming only the mutated name:
- re-adding `os.getwd`;
- dropping `string.match`;
- dropping `table.getn`;
- re-adding `File:iseof`;
- restoring the `local` parameter. This failed the parse test (line 40), the debug-names test, and
  the set-A test, whose six new `debug.*` names follow `getlocal`;
- dropping `File:setvbuf`.

**Ground truth after the fix.** `three_way.py`, run on the fixed files, reports set A empty and set D
empty. The only undeclared 5.1.5 names left are `string.gfind`, `math.mod` and `table.setn`, which §4
step 3 excludes, plus `arg` and the three file metamethods. PUC 5.1.5, LuaJIT 2.0 and LuaJIT 2.1 each
`loadfile` all nine files without error. The remaining Lunar parse errors are BUG-514's two forms
(`builtin.lua:92`, `coroutine.lua:48`, `io.lua:141` and `string.lua:73`).

**TARGET-12 anchors.** Replaying design §5 on the fixed files, E1–E5 each match exactly once, and
none of the names that E6–E9 append is already declared.

**Gates.**
- Full suite (`test --rerun --no-build-cache`): 3226 tests, 0 failures, 0 errors, 4 skipped.
- `ktlintCheck`: green, after `ktlintFormat`.
- Corpus and parity (`test --rerun --no-build-cache --tests '*Corpus*' --tests '*InspectionParity*'
  -PwithCorpus`): **not applicable to this fix**. The final run was green, with 6 tests and no
  IMPROVED or REGRESSED line. An earlier run of the same tree printed one IMPROVED line, zerobrane
  `LuaSuspiciousConcatenation` 21 → 19. This is the intermittent movement recorded as BUG-501's
  DR-08, whose cause is unestablished. A repeat run gave 21, and so did a run with the first commit's
  stubs. The sweep and
  `LuaInspectionParityTest` never load these files. They set only `languageLevel = LUA51`, and the
  project target stays STANDARD 5.4. A probe printed `target=Target(platform=Standard, version=5.4)`
  and the `lua-5.4` library root for all four corpus members. So the gate cannot see any change to
  `lua-5.1`. This is filed as [[BUG-515]].
