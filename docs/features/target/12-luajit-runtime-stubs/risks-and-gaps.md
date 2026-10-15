---
id: TARGET-12-RISKS
parent_id: TARGET-12
type: risk
folders:
  - "[[features/target/12-luajit-runtime-stubs/requirements|requirements]]"
title: "Risks & Gaps"
---

# TARGET-12: Risks & Gaps

## Load-bearing premises (DR plan)

| Premise | Kind | DR | Verdict |
| :-- | :-- | :-- | :-- |
| A LuaJIT target gets no stdlib stubs today | runtime | DR-01 | confirmed |
| 2.0 and 2.1 expose different APIs; `ffi` is not a global | runtime | DR-02 | confirmed |
| Stub globals in the plugin jar resolve in a light fixture | runtime | DR-03, DR-06 | confirmed |
| `require` gives the module's types | runtime | DR-04 | **refuted**, now out of scope |
| A dotted `require` finds a flat `a.b.lua` | runtime | DR-05 | **refuted**, so the nested layout is used |
| Neovim or LuaLS ships usable LuaJIT stubs | read + runtime | DR-07, DR-08 | **refuted** |
| The `lua-5.1` stubs parse cleanly and match Lua 5.1's API | runtime | DR-09, DR-14, DR-16 | **refuted**, and filed as [[BUG-513]], which lands first |
| The resolver rule's `init.lua` arm behaves as specified | runtime | DR-16 | confirmed |
| The design §4 stubs match the executed inventory and behave as the test cases expect | runtime | DR-10, DR-11 | confirmed |
| One refresh of the root makes a reused sandbox's listing whole | runtime | DR-12 | **refuted**, so the helper loads the tree, then refreshes |
| Nested runtime files affect only dotted `require`s | runtime | DR-13 | **refuted**, so design §1.1 adds a rule |
| Only the tests that are named depend on LuaJIT having no root | runtime | DR-15 | confirmed |

## Pre-Implementation De-risking Tasks

All were executed on 2026-10-15 at `84fa67b0` (DR-12 to DR-15 at `5949b290`, whose source is the same)
with `builder-run libvirt test --rerun …`. Probe code and raw output are in private scratch
(`~/.cache/claude-scratch/lunar/target-12/`, `results/<run>/`). The tree was restored to HEAD after
every run.

| ID | Action | Observed (verbatim excerpts) | Status |
| :-- | :-- | :-- | :-- |
| TARGET-12-00-DR-01 | Light fixture with `jit`/`bit`/`string`/`table` under STANDARD 5.1, LuaJIT 2.0, LuaJIT 2.1, Redis 7+ and STANDARD 5.4, at HEAD | `jit21 … root=null` · `jit21 undeclared=[… 'jit', … 'bit', …]` · `jit21 resolve format@128 -> []` · `std51 resolve format@128 -> [string.lua@3745]`. `string`, `table` and `print` are never reported (`LuaStandardGlobals`) | done |
| TARGET-12-00-DR-02 | Built LuaJIT `v2.0` (`acf8fcbd`) and `v2.1` (`2a8e017e`) from source, then dumped `_G` and every library table, and `require`d a fixed list of modules (`ffi`, `jit.util`, `jit.profile`, `string.buffer`, `table.new`, `table.clear`). The tool modules in LuaJIT's `src/jit/` were not dumped (requirements §Out of Scope) | 2.1 only: `jit.security`, `table.move`, `coroutine.isyieldable`, `require(ffi).typeinfo`, `require(jit.profile)`, `require(string.buffer)`, `require(table.new)`, `require(table.clear)`. 2.0 only: `jit.util` (as a field), `string.gfind`, `math.mod`. Both: `global ffi nil`, `math.log base 3`, `string.rep sep a,a,a`. `jit.prngstate` is in neither | done |
| TARGET-12-00-DR-03 | Prototype `luajit-2.1/` (the stdlib copies, `jit.lua`, the `redis-7` `bit.lua` and `ffi.lua`) | `jit21 undeclared=[]` (without `KEYS`) · `resolve jit@74 -> [jit.lua…, jit.lua…, jit.lua@59]` · `band@100 -> [bit.lua@565]` · `format@128 -> [string.lua@3745]` · `new@193 -> [ffi.lua@237]` · `type jit.version = string`. Control: the non-LuaJIT arms' output is byte-identical to DR-01's | done |
| TARGET-12-00-DR-04 | Types through `require`, in the same prototype | `type ffi.new = undefined` · `type new_tab = any`, even though `require "ffi" -> …/luajit-2.1/ffi.lua` resolves. The cause is not established | done; out of scope |
| TARGET-12-00-DR-05 | Dotted `require`: a flat `table.new.lua` compared with nested `table/new.lua` and `jit/util.lua` | Flat: `require "table.new" -> null`. Nested, after a refresh: `require "table.new" -> runtime/luajit/luajit-2.1/table/new.lua`, `"jit.util" -> …/jit/util.lua` | done |
| TARGET-12-00-DR-06 | Control: do stub globals resolve in a light fixture? (`RedisAmbientTypingTest.kt:76-85` says not) | `redis7 resolve KEYS@207 -> [global.lua@70]` · `redis7 resolve bit@96 -> [bit.lua…]`. That comment is stale | done |
| TARGET-12-00-DR-07 | Neovim `nvim-linux-x86_64.tar.gz` `share/nvim/runtime/lua/jit/` | `bc`, `bcsave`, `dump`, `p`, `v`, `vmdef`, `zone`, `dis_*`. Headers read "Copyright (C) 2005-2026 Mike Pall … See Copyright Notice in luajit.h". `LuaCATS tag lines across nvim jit/*.lua: 0`. These are LuaJIT's implementation modules | done |
| TARGET-12-00-DR-08 | LuaLS `meta/template` at `b144c872` | `jit.lua` opens `---#if not JIT then DISABLE() end` and `---@version JIT`. `ffi.lua` uses `ffi.cdata*` names. `jit.lua` lacks `attach` and `security`. Not adopted | done |
| TARGET-12-00-DR-09 | Parse of each `lua-5.1` stub: LuaJIT `loadfile` and Lunar `PsiErrorElement`s | LuaJIT: `debug.lua:40: <name> or '...' expected near 'local'`. Lunar: `debug [40]`, `builtin [92, 133, 207:<type> expected, got '...', 266]`, `coroutine [48, 60]`, `io [102, 144]`, `package [37]` and `string [66]`. `math`, `os` and `table` are clean | done; filed as BUG-513 |
| TARGET-12-00-DR-10 | `declaredNames` (design §7.2 regexes) of the assembled design §4 stubs compared with DR-02's inventory | `ALL-OK`, every LuaJIT-specific file `OK` for both versions | done |
| TARGET-12-00-DR-11 | The full design §3–§6 layout, with `debug.lua` already fixed as BUG-513 will fix it, run through the probe helper | `LuaJIT-2.1 fixtureA undeclared=[]` · `LuaJIT-2.0 fixtureA undeclared=[]` · `Standard-5.1 … ['jit', 'bit']`. `2.1 debug resolve=[traceback->[…/luajit-2.1/debug.lua], upvalueid->[…], move->[…/table.lua], isyieldable->[…/coroutine.lua]]` · `2.0 … move->[], isyieldable->[]`. `2.0 dotted=["jit.util"->…/jit/util.lua, "jit.profile"->null, …]`. `bare ffi undeclared=['ffi']`. `completion jit.=[arch, attach, flush, off, on, opt, os, security, status, version, version_num]`. Its parse pass reached 15 of the 17 `luajit-2.1` files; DR-12 covers all of them | done |
| TARGET-12-00-DR-12 | Refresh sufficiency, run in sequence on one sandbox (`results/r9…r13`). Seed a partial layout (no `jit/profile.lua`, `table/clear.lua` or `string/`) with the load-then-refresh helper, then run the full layout with the one-refresh helper (r11). Re-seed, then run the full layout with the load-then-refresh helper (r13) | r9, one refresh over the stale cache: `jit/profile.lua` and `table/clear.lua` are listed although absent from the jar. r10/r12 seed, load-then-refresh: `count=14`, the true partial listing. **r11 control**, one refresh: `count=15`, missing `jit/profile.lua` and `table/clear.lua`, which reproduces the 15-of-17 gap. **r13**, load-then-refresh: `count=17`, and every LuaJIT-specific file in both versions shows `errors=[]`, including `jit/profile.lua`, `string/buffer.lua` and `table/clear.lua` | done |
| TARGET-12-00-DR-13 | Bare `require` of nested stub names under LuaJIT 2.1, LuaJIT 2.0 and STANDARD 5.1, then a project `util.lua`; before (r13) and after (r14) the design §1.1 rule | Before: `"util"->…/luajit-2.1/jit/util.lua, "new"->…/table/new.lua, "clear"->…/table/clear.lua, "buffer"->…/string/buffer.lua, "profile"->…/jit/profile.lua`. After: those five give `null`, and `"jit.util"`, `"jit.profile"`, `"table.new"`, `"string.buffer"`, `"string"`, `"ffi"` and `"jit"` resolve as before. `Standard-5.1` is identical before and after. `withProjectUtil ["util"->/src/util.lua]` both times | done |
| TARGET-12-00-DR-14 | Resolve every name each `lua-5.1` file declares, as `print(<name>)` with `multiResolve` on the last segment, under STANDARD 5.1 and under the prototype LuaJIT 2.1 (r13) | `sweep Standard-5.1 debug unresolved=[debug.setlocal(0), debug.getfunc(0), debug.sethook(0), debug.gethook(0), debug.setfenv(0), debug.getfenv(0), debug.traceback(0)]`. Every other file is `unresolved=[]`, so the LuaCATS parse errors hide nothing. With the fixed `debug.lua` copy, `LuaJIT-2.1 debug unresolved=[]` | done |
| TARGET-12-00-DR-15 | Full suite (`test --rerun --no-build-cache`). r15: the prototype roots only. r16: the roots plus design §1.1, with the §7.5 changes using the never-bundled target in both repointed tests. r17: as r16, but with Tarantool 2.10 in `LuaSettingsNotificationTest` | r15: `3207 tests completed, 2 failed, 4 skipped`. The failures are `RuntimeLibraryProviderTest > testGetLibraryFilesForFuturePlatformReturnsEmpty()` and `LuaSettingsNotificationTest > testGetSupportLibrariesEmptyContractHolds`; nothing else moved. r16: `3207 tests completed, 1 failed`, the failure being `LuaSettingsNotificationTest > testGetSupportLibrariesEmptyContractHolds` (`AssertionFailedError`): the settings round-trip turns the unregistered label into `STANDARD 5.1`. r17: `BUILD SUCCESSFUL`, with the JUnit XML at 3207 tests, 0 failures, 0 errors and 4 skipped | done |
| TARGET-12-00-DR-16 | Five checks. (a) The Lua 5.1 manual's "Lua functions" index compared with an executed dump of PUC Lua 5.1.5 (built from `lua-5.1.5.tar.gz`, sha256 `2640fc56…`) and with the names the `lua-5.1` stubs declare (`three-way.out`). (b) BUG-513's add and remove sets checked against the LuaJIT 2.0 and 2.1 dumps (`luajit-vs-fix.out`). (c) Run r18 under STANDARD 5.1. (d) Run r18 calling `runtimeRootVerdict` over a temp directory. (e) Run r19 parsing alternative LuaCATS forms | (a) Missing documented names: `debug.debug … string.match` (BUG-513 §3 A). Declared but in neither source: `debug.getfunc dostring file:iseof io.setvbuf os.getwd os.tmpfile package.preloaded package.searchers rawlen`. (b) Every A name is in both LuaJIT builds and every D name in neither. `string.gfind` and `math.mod` are `compat-absent` in v2.1, and `table.setn` in both. (c) `string.match -> 0`, `os.clock -> 0`, `math.deg -> 0`, `debug.getupvalue -> 0`, `package.preload -> 0`, `table.getn -> 0`, `string.format -> 1`, `os.getwd -> 1`. (d) `vroot/pkg/init.lua 'pkg' -> true`, `'init' -> false`, `'pkg.init' -> true`; `vroot/jit/util.lua 'util' -> false`, `'jit.util' -> true`; `vroot/top.lua 'top' -> true`; `elsewhere/util.lua 'util' -> null`; `root=false 'top' -> null`. (e) `new-return-any-dots errors=[]`, `new-function-array errors=[]`, `new-overload-literal errors=[]`, `new-literal errors=[<type> expected, got '"']`, `new-fun-varargs errors=[')' or NAME expected, got '.']` | done |

## Critical Risks

### Risk 1.1: The stdlib copies drift from `lua-5.1/`
- **Impact**: a later fix to `lua-5.1` does not reach LuaJIT projects.
- **Likelihood**: medium; BUG-513 is one such fix.
- **Mitigation**: TC 11 rebuilds each copy from `lua-5.1/` plus E1–E9 and compares bytes. Any
  one-sided change fails, so the copies are resynced by whoever changes `lua-5.1/`. BUG-513 lands
  before TARGET-12, so TARGET-12 starts in sync.

### Risk 1.2: The §1.1 rule changes resolution for some existing runtime file
- **Impact**: a `require` that resolved into a runtime root before stops resolving.
- **Likelihood**: low. Every existing root is flat, and a top-level file is accepted for its own
  module name. The candidates it newly rejects are nested files and dotted names whose path tail
  happened to match the root's directory name (DR-13, DR-15).
- **Mitigation**: TC 18 and TC 19. [[BUG-500]] is untouched, so behaviour outside runtime roots is
  unchanged.

### Risk 1.3: A stale VFS listing on a reused sandbox
- **Impact**: tests on a builder that ran before the files existed see a wrong set of files (DR-12).
- **Likelihood**: high on the libvirt builder and low in CI. Whether an installed IDE can keep a
  stale listing after a plugin update is not established.
- **Mitigation**: the design §7.1 helper loads the tree, then refreshes it.

## Technical Debt & Future Work
- **NGX/OpenResty stubs** should start from `luajit-2.1/` and add the fork's APIs
  (`jit.prngstate`, `table.isempty`, …).
- **Type flow through `require`** for platform modules (DR-04).
- **LuaJIT syntax**: `goto`/`\u{}` at `LUA51`, and the 3.0 backports in `extensions.html`.
- **LuaJIT tool modules** (`jit.v`, `jit.dump`, …), if a user ever needs them resolved.
- **Lunar LuaCATS parser gaps**: a string-literal union in `@return`, and a vararg inside `fun()`
  (DR-16 (e)), filed as [[BUG-514]].

## See Also
- [requirements.md](requirements.md) · [design.md](design.md) · [implementation-plan.md](implementation-plan.md)
