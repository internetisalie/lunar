---
id: TARGET-12
parent_id: TARGET
type: feature
folders:
  - "[[features/target/requirements|requirements]]"
title: "TARGET-12: LuaJIT Runtime Stubs"
status: "done"
priority: "medium"
---

# TARGET-12: LuaJIT Runtime Stubs

## Overview

The `LuaJIT` platform (`LuaPlatform.LUAJIT`, versions 2.0 and 2.1) exists in the target model but
bundles no library root. A LuaJIT project therefore gets neither the Lua 5.1 standard library nor
LuaJIT's own API: `jit` and `bit` are reported undeclared, and `string.format` resolves to nothing
(DR-01).

This feature ships `runtime/luajit/luajit-2.0/` and `runtime/luajit/luajit-2.1/`: the Lua 5.1 stub
set plus LuaJIT's `jit`, `bit`, `ffi` and extension modules, as part of the platform rather than as
a catalog definition library. Alongside the resources there is one production change: a
module-resolution rule that keeps the first nested runtime files from capturing bare `require`s
(design §1.1).

## Scope

### In Scope
- A per-version root carrying the stdlib stub files listed in design §3, with LuaJIT's
  library-level 5.2/5.3 extensions applied.
- LuaJIT-specific stubs: the `jit` and `bit` globals, the `ffi` module, and the dotted modules
  `jit.util`, `jit.profile`, `string.buffer`, `table.new` and `table.clear`, each in the versions
  that have it (DR-02).
- The runtime-root module rule (design §1.1).
- Attribution (`THIRD-PARTY.md` and per-file notices).
- Test isolation for the existing tests that relied on LuaJIT having no root (design §7.5).

Split check: the stdlib copy alone is shippable, because today a LuaJIT target has no stdlib at all
(DR-01). The LuaJIT stubs are not shippable alone, because a root without the stdlib would lose
`string.*`. At this size, splitting the copy out buys nothing. What is split out is the `lua-5.1`
defect, as [[BUG-513]], which ships first.

### Out of Scope
- **OpenResty/NGX stubs.** `runtime/ngx/` is still an empty skeleton. OpenResty's LuaJIT fork adds
  APIs upstream lacks, such as `jit.prngstate` (absent from both upstream inventories, DR-02). A
  future NGX stub feature should start from `luajit-2.1/`. [[BUG-405]] (luacheck std) is unrelated.
- **LuaJIT's tool modules**: `jit.bc`, `jit.v`, `jit.dump`, `jit.p`, `jit.zone`, `jit.vmdef`,
  `jit.bcsave` and `jit.dis_*`. These are the implementation modules behind the `luajit -jv`,
  `-jdump`, `-jp` and `-b` command-line options, with no documented Lua API (DR-07). A `require` of
  one stays unresolved.
- **LuaJIT syntax**: `goto` and `\u{}` at the `LUA51` level, and the "LuaJIT 3.0 backported"
  operators that `extensions.html` lists. These are parser and language-level work.
- **Type flow through `require`** of these modules. Measured not to flow (DR-04); resolution and
  navigation do work.
- **Fixing the `lua-5.1` stubs.** [[BUG-513]] brings `lua-5.1/` in line with the Lua 5.1 manual
  (§3–§4 of that report) and lands first. TARGET-12's copies are byte-exact rebuilds of the fixed
  files (TC 11), so TARGET-12 ships no known stub defect. What the copies still trip over is Lunar's
  LuaCATS parser. Two valid forms, a string-literal union in `@return` and a vararg inside
  `fun()`, produce `PsiErrorElement`s. Those are parser gaps, not stub defects ([[BUG-514]]), and they
  hide no declaration (DR-14).
- **[[BUG-500]]**, where a bare `require` matches a file in any project or library directory. Only
  runtime-root files are covered here (design §1.1).
- **`LUAJIT_ENABLE_LUA52COMPAT`-only features** (`table.pack`, `table.unpack`, `rawlen`, …), which a
  default build does not have (DR-02).
- **[[MAINT-39]]'s sweep wiring.** Design §8 records what that wiring has to call.

## Functional Requirements

| ID | Requirement | Priority | Status | Description |
|----|-------------|----------|--------|-------------|
| TARGET-12-01 | **Stdlib under LuaJIT** | M | Full | Each stdlib file in `luajit-<v>/` is byte-identical to `lua-5.1/<f>` as fixed by BUG-513, with design §5's edits applied for that version. Stdlib members resolve under a LuaJIT target. |
| TARGET-12-02 | **`jit` global** | M | Full | Under a LuaJIT target, `jit` and its members resolve. The declared member set is exactly the version's set in design §4.1. |
| TARGET-12-03 | **`bit` global** | M | Full | Under a LuaJIT target, `bit` and every function in design §4.2 resolve. |
| TARGET-12-04 | **`ffi` module, not a global** | M | Full | `require("ffi")` resolves to the version's `ffi.lua`, and `ffi.*` members resolve. A bare `ffi` with no `require` stays undeclared. |
| TARGET-12-05 | **Dotted modules, version-gated** | M | Full | `require("jit.util")` resolves under both versions. `require("jit.profile")`, `require("string.buffer")`, `require("table.new")` and `require("table.clear")` resolve under 2.1 only. |
| TARGET-12-06 | **Platform gating** | M | Full | Under a `STANDARD` target, `jit` and `bit` are undeclared, as before this feature. |
| TARGET-12-07 | **5.2-compat signatures** | S | Full | Both versions carry the signatures LuaJIT enables unconditionally (design §5, E1–E7). |
| TARGET-12-08 | **5.3-compat additions (2.1)** | S | Full | `luajit-2.1/` declares `table.move` and `coroutine.isyieldable`; `luajit-2.0/` declares neither (E8, E9). |
| TARGET-12-09 | **Clean parse** | M | Full | Every LuaJIT-specific file parses with zero `PsiErrorElement`s. |
| TARGET-12-10 | **Attribution** | M | Full | Every LuaJIT-specific file starts with the exact design §6 header. `THIRD-PARTY.md` carries each of the design §6 edits. |
| TARGET-12-11 | **Test isolation** | M | Full | With the roots present, the tests changed in design §7.5 pass, and so does the full suite. Restoring STANDARD 5.4 in their `tearDown`s is checked by inspection only: DR-15 shows that a leaked target breaks no later class today, so no test can observe a missing restore. |
| TARGET-12-12 | **`jit.` completion** | S | Full | `jit.<caret>` offers `version`, `on` and `status` under LuaJIT 2.1. |
| TARGET-12-13 | **Runtime-root module rule** | M | Full | Inside the active runtime root, a module resolves only to the file at its relative path, `<a/b>.lua` or `<a/b>/init.lua`. A bare `require("util")` does not resolve to `luajit-2.1/jit/util.lua`, and a project `util.lua` wins over it. |

## Test Cases

"Fixture A" is this file content:
`local v = jit.version\nlocal b = bit.band(1, 2)\nprint(string.format("%d", b), table.concat({}), v)`.
"Undeclared" means the descriptions that start with `Undeclared variable` from `doHighlighting()`
with `LuaUndeclaredVariableInspection` enabled. Every fixture case uses the design §7.1 helper.
Expected values are those measured in DR-11, DR-12 and DR-13.

| # | Req | Given | When | Then | Red under (mutation reachable from this fixture) |
|---|-----|-------|------|------|-----|
| 1 | -02, -03 | LuaJIT 2.1, Fixture A | highlight | Undeclared = `[]` | Delete `luajit-2.1/jit.lua` → `'jit'` reported. Deleting only `jit = {}` is not enough, because bare `jit` also resolves to the `function jit.*` declarations (DR-03) |
| 2 | -02, -03 | LuaJIT 2.0, Fixture A | highlight | Undeclared = `[]` | Delete `luajit-2.0/bit.lua` → `'bit'` reported |
| 3 | -06 | STANDARD 5.1, Fixture A | highlight | Undeclared = `['jit', 'bit']` in that order | Copy `jit.lua` into `lua-5.1/` → `'jit'` missing |
| 4 | -01 | LuaJIT 2.1, Fixture A | `multiResolve` on `format` | Exactly one target, whose file path ends with `luajit/luajit-2.1/string.lua` | Delete `function string.format` from the 2.1 copy → zero targets |
| 5 | -02 | LuaJIT 2.1, Fixture A | `multiResolve` on `version` | Exactly one target, in `luajit-2.1/jit.lua` | Delete the `jit.version =` line → zero targets |
| 6 | -04 | LuaJIT 2.1, `local ffi = require("ffi")\nprint(ffi.new)` | `LuaRequireReference.resolve()` on the argument, and `multiResolve` on `new` | The path ends with `luajit-2.1/ffi.lua`, and `new` has one target in that file | Rename `function ffi.new` to `ffi.new2` → `new` has zero targets |
| 7 | -04 | LuaJIT 2.1, `print(ffi.new)` | highlight | Undeclared = `['ffi']` | Change `local ffi = {}` to `ffi = {}` in `ffi.lua` → `[]` |
| 8 | -05 | LuaJIT 2.1, `require("jit.util")`, `require("jit.profile")`, `require("string.buffer")`, `require("table.new")` and `require("table.clear")`, one per line | `resolve()` each | Paths end with `luajit-2.1/` plus `jit/util.lua`, `jit/profile.lua`, `string/buffer.lua`, `table/new.lua` and `table/clear.lua` | Move `table/new.lua` to `table.new.lua` → null (DR-05) |
| 9 | -05 | LuaJIT 2.0, same lines | `resolve()` each | `jit.util` resolves to `luajit-2.0/jit/util.lua`, and the rest to `null` | Copy `luajit-2.1/table/` into `luajit-2.0/` → non-null |
| 10 | -02…-05 | The resources (no fixture) | `declaredNames` (design §7.2) of each LuaJIT-specific file in design §3, for each version that has it | Equals that file's set in design §4 | Add `jit.security` to `luajit-2.0/jit.lua` → mismatch |
| 11 | -01, -07, -08 | The resources | `rebuild(v, f)` (design §7.3) for every stdlib copy and both versions | Equals the copy's text byte for byte | Change any character of a copy, or of `lua-5.1/<f>` alone → that pair fails; delete E8 from `luajit-2.1/table.lua` → fails |
| 12 | -09 | The text of each LuaJIT-specific file, opened with `configureByText` | `PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java)` | Empty | Add the line `---@return ...` directly above `function ffi.cdef` → one error, since that LuaCATS form is reported as `<type> expected` (DR-09) |
| 13 | -10 | The resources | Compare the first lines of each LuaJIT-specific file with `HEADER` (design §6) | Equal, line for line | Delete any MIT permission or warranty line from any file → that file fails |
| 14 | -11 | The design §7.5 edits | Run `RuntimeLibraryProviderTest`, `LuaSettingsNotificationTest` and `LuaRocksInterpreterInitializerTest`, then the full suite | All pass (DR-15) | Revert the `RuntimeLibraryProviderTest.kt:95` edit with the roots present → that test fails (DR-15) |
| 15 | -12 | LuaJIT 2.1, `jit.<caret>` | `completeBasic()` | Lookup strings include `version`, `on` and `status` | Delete `function jit.on` → `on` missing |
| 16 | -01, -07, -08 | LuaJIT 2.1, `print(debug.traceback(), debug.upvalueid(print, 1), table.move, coroutine.isyieldable)` | `multiResolve` on `traceback`, `upvalueid`, `move`, `isyieldable` | Each has exactly one target in the matching `luajit-2.1/` file. Under LuaJIT 2.0, `move` and `isyieldable` have none | Remove E7 from `luajit-2.1/debug.lua` → `upvalueid` has none. Under unfixed BUG-513, `traceback` has none (DR-14) |
| 17 | -10 | `THIRD-PARTY.md` | Read it | Contains `### LuaJIT API stubs (Mike Pall)`, the `runtime/luajit/luajit-2.{0,1}/{builtin,…}.lua` files line and the `**MIT (LuaJIT)**` line, and the `**MIT (Lua)**` line names `runtime/luajit/` | Delete the LuaJIT section → fails |
| 18 | -13 | LuaJIT 2.1, a `require` of each of `util`, `new`, `clear`, `buffer`, `profile`, `jit.util` and `string` (design §7.4), and then a project `util.lua` | `resolve()` each | `null` for the first five, `…/jit/util.lua` and `…/luajit-2.1/string.lua` for the other two, and `/src/util.lua` once the project file exists | Remove the `runtimeRootVerdict` call → the first five resolve into the nested stub files (DR-13, before the fix), and the project-`util.lua` case can resolve to `jit/util.lua` instead, depending on index order |
| 19 | -13 | A temp directory `vroot/` holding `pkg/init.lua`, `jit/util.lua` and `top.lua`, plus `elsewhere/util.lua` outside it (`myFixture.tempDirFixture`) | `runtimeRootVerdict(candidate, root, module)` (design §7.4) | `pkg/init.lua` gives `true` for `pkg` and `pkg.init`, and `false` for `init`. `jit/util.lua` gives `false` for `util` and `true` for `jit.util`. `top.lua` gives `true` for `top`. `elsewhere/util.lua` gives `null`, and so does a `null` root (DR-16) | Delete the `\|\| relativePath == "$modulePath/init.lua"` arm → the `pkg` case gives `false`. This is predicted, not executed: r18 ran only the unmutated verdicts. Phase 3's mutation proof runs it |

## Acceptance Criteria
- [x] Every test case above passes on the full suite (`run "test --rerun --no-build-cache"`).
- [x] TC 1, TC 7, TC 8, TC 18 and TC 19 go red under their named mutations (implementation-plan Phase 3).
- [x] `runtime/luajit/.gitkeep` is deleted, since the directory is no longer empty.

## Non-Functional Requirements
- The design §1.1 rule runs on the caller's read path with no VFS refresh (design §9).
- Tests drive settings and roots on the EDT and read PSI inside `runReadAction` (engineering
  contract §1, §5). They use light fixtures.

## Dependencies
- [[BUG-513]]: brings `lua-5.1/` in line with the Lua 5.1 API first, so the byte copies ship no
  known stub defect.
- [[TARGET-04]]: `RuntimeLibraryProvider`/`LuaLibraryProvider` serve the root (done).
- Consumer: [[MAINT-39]] (design §8).

## See Also
- Design: [design.md](design.md) · Plan: [implementation-plan.md](implementation-plan.md) ·
  Risks and DR evidence: [risks-and-gaps.md](risks-and-gaps.md)
