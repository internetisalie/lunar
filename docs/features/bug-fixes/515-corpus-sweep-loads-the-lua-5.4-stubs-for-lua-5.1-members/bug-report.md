---
id: "BUG-515"
title: "The corpus sweep and the inspection-parity test load the lua-5.4 stdlib stubs for members pinned to Lua 5.1"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-515: the corpus measures Lua 5.1 members against the Lua 5.4 standard library

Found while delivering [[BUG-513]]. Its fix to the `lua-5.1` stubs did not move any corpus baseline,
and this is the reason.

## 1. Reproduction

`LuaCorpusSweepTest.sweepAndRatchet` (`LuaCorpusSweepTest.kt:119`) and
`LuaInspectionParityTest` (`LuaInspectionParityTest.kt:69`) pin the language level from the
manifest, and do nothing else:

```kotlin
LuaProjectSettings.getInstance(myFixture.project).state.languageLevel = entry.luaLevel
```

All four members of `tooling/corpus/corpus.json` are `"luaLevel": "LUA51"`. As a probe, this line
was added after `measure(...)` in `sweepAndRatchet`, temporarily:

```kotlin
println("[probe:513] $name target=${LuaProjectSettings.getInstance(myFixture.project).state.getTarget()} " +
    "root=${PlatformLibraryIndex.getPlatformLibraryFolder(myFixture.project)?.path}")
```

Run: `test --rerun --no-build-cache --tests '*Corpus*' -PwithCorpus`. It was executed 2026-10-15 on
`09b902db` (BUG-513's first commit), on the libvirt builder:

```
[probe:513] luacheck target=Target(platform=Standard, version=5.4) root=…/lunar-0.18.0.jar!/runtime/standard/lua-5.4
[probe:513] luarocks target=Target(platform=Standard, version=5.4) root=…/lunar-0.18.0.jar!/runtime/standard/lua-5.4
[probe:513] penlight target=Target(platform=Standard, version=5.4) root=…/lunar-0.18.0.jar!/runtime/standard/lua-5.4
[probe:513] zerobrane target=Target(platform=Standard, version=5.4) root=…/lunar-0.18.0.jar!/runtime/standard/lua-5.4
```

`LuaInspectionParityTest` was not probed, but it uses the same assignment.

## 2. Expected vs Actual
- **Expected**: a member pinned to `LUA51` is swept with the Standard 5.1 target, so its stdlib
  names resolve against `runtime/standard/lua-5.1/`. MAINT-33's design says the pin does this
  (`docs/features/maint/33-corpus-sweep/design.md:333-334`): "the level selects which stdlib
  globals are known, so `LuaUndeclaredVariable` moves with it too".
- **Actual**: the pin moves the parser and `LuaLanguageLevelInspection`, which read `languageLevel`.
  The stdlib root does not move. `LuaProjectSettings.State.getTarget()` (`LuaProjectSettings.kt:125`)
  derives a target from `languageLevel` only while `target == null`. In the shared light project a
  target already exists, so the root stays on `lua-5.4`. The corpus therefore cannot see any change
  to the `lua-5.1` stubs, including BUG-513's.

## 3. Expected baseline movement once fixed (not executed)

Once the members load `lua-5.1`, the baselines will move, at least as follows:
- **`rawlen`.** `lua-5.4` declares it and Lua 5.1 does not. luacheck's `spec/samples/globals.lua`
  and `spec/samples/compat.lua` read it as a global, so expect new `LuaUndeclaredVariable` hits.
- **`newproxy`.** `lua-5.1` declares it, as deprecated, since BUG-513. `lua-5.4` does not. luacheck's
  `spec/projects/default_stds/**` files read it. Expect the undeclared hits for it to go, if it is
  reported today, and expect `LuaDeprecatedApi` hits to appear.
- **`package.config`.** `lua-5.1` declares it since BUG-513, without `@deprecated`. luarocks
  (`src/luarocks/util.lua:459,488`, `dir.lua:15`) and penlight (`lua/pl/compat.lua:29`) read it.
  Expect no deprecation hit and no undeclared hit.
- **Others.** Anything else that differs between the two roots will also move: `string.gmatch`
  versus 5.4-only names, `unpack`, `setfenv`, `module` and so on.

Each movement needs attributing at re-record time.

## 4. Fix direction (for whoever plans it)

Set the target as well as the level, the same way the stdlib tests do. For example,
`setTargetAndNotify(Target(STANDARD, findVersion(STANDARD, <label for entry.luaLevel>)))` followed by
`PlatformLibraryIndex.reload()`. Then re-record every baseline and attribute it.
`LuaInspectionParityTest` needs the same change. Not fixed here.
