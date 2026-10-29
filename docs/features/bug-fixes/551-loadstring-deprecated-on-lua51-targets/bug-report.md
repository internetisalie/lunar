---
id: "BUG-551"
title: "`loadstring` is reported as a deprecated API on the Lua 5.1 and LuaJIT 2.1 targets, where it is current"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-551: `loadstring` is flagged *Deprecated API* on Lua 5.1 and LuaJIT 2.1

Found by [[MAINT-39]]'s corpus sweep of `lazy.nvim` (MAINT-39 risks-and-gaps, DR-12).

## 1. Reproduction (executed)

Light fixture (`BasePlatformTestCase`) with `LuaDeprecatedApiInspection` enabled, the project
pinned to each target with `CorpusTarget.pin`, `myFixture.doHighlighting()`. Executed 2026-10-28 on
`5dd675a4` with a throwaway probe test (not committed).

```lua
local f = loadstring("return 1")
print(f)
```
→ Standard 5.1: `1:'loadstring':Deprecated API`; LuaJIT 2.1: `1:'loadstring':Deprecated API`.

## 2. Expected vs Actual
- **Expected**: no warning. `loadstring` is the Lua 5.1 API (deprecated in 5.2 in favour of
  `load`), and LuaJIT 2.1 keeps it.
- **Actual**: *Deprecated API* on every call.

## 3. Context
- **Where the tag is**: both targets' stubs carry `---@deprecated` on `loadstring`
  (`src/main/resources/runtime/standard/lua-5.1/builtin.lua`,
  `src/main/resources/runtime/luajit/luajit-2.1/builtin.lua`, the comment reading
  "deprecated, use load+pcall").
- **Corpus site**: `lua/lazy/core/plugin.lua:308`, one of the seven `inspection.LuaDeprecatedApi`
  hits in `src/test/resources/corpus/lazynvim.baseline`. A fix lowers that count, and the fixing
  change re-records the baseline; other members' baselines may move too.

## 4. Root Cause
Stub data, not the inspection. `loadstring` carries `---@deprecated` in all three stubs that declare
it: `runtime/standard/lua-5.1/builtin.lua`, `runtime/luajit/luajit-2.0/builtin.lua` and
`runtime/luajit/luajit-2.1/builtin.lua` (LuaJIT 2.0 was not probed, but its stub is identical).
Lua 5.1 has no string form of `load`, so `loadstring` is the only way to compile a string chunk there;
5.2 is the release that deprecated it. No later target's stub declares `loadstring`, so there is no
target on which the tag is correct. The description beside it was also wrong: `loadstring` compiles
the chunk and returns it as a function, it does not run it.

## 5. Fix Strategy
Remove `---@deprecated` from `loadstring` in the three stubs and correct its description. The other
5.1-era deprecations in those stubs (`gcinfo`, `newproxy`, `table.foreach`, `table.foreachi`,
`table.getn`) are deprecated on 5.1 itself and stay.

## 6. Test Strategy
`LoadstringNotDeprecatedTest` highlights `loadstring("return 1")` beside `gcinfo()` under Standard 5.1,
LuaJIT 2.0 and LuaJIT 2.1, and asserts that only `gcinfo` is reported. `gcinfo` is the control that
proves the inspection ran. Before the fix all three cases failed with
`expected:<[gcinfo]> but was:<[loadstring, gcinfo]>`.

## 7. Baseline movement (measured)
One re-record (`-PwithCorpus -PrecordCorpusBaseline`). Only `inspection.LuaDeprecatedApi` moved, in
every member:

| Member | Before | After |
| :-- | --: | --: |
| `lazynvim` | 7 | 6 |
| `luacheck` | 8 | 7 |
| `luarocks` | 1 | 0 |
| `penlight` | 2 | 0 |
| `zerobrane` | 6 | 0 |
