---
id: "BUG-551"
title: "`loadstring` is reported as a deprecated API on the Lua 5.1 and LuaJIT 2.1 targets, where it is current"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-551: `loadstring` is flagged *Deprecated API* on Lua 5.1 and LuaJIT 2.1

Found by [[MAINT-39]]'s corpus sweep of `lazy.nvim` (MAINT-39 risks-and-gaps, DR-12). A bug
report: reproduction executed, root cause not investigated.

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
