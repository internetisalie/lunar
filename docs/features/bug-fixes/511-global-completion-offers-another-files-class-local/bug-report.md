---
id: "BUG-511"
title: "Global completion offers another file's class local as a bare value, with no require"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-511: completion offers `Iter`, `uv` or `cjson` as a bare value

Found in BUG-510's review (`~/.cache/claude-scratch/lunar/bug-510-review/review.md`, F3). Read, not
run.

## 1. Reproduction (predicted)

`cls.lua` = `---@class K` / `local K = {}` / `return K`; in `user.lua`, type `K` at statement start
and invoke basic completion. With a library enabled the same holds for its class locals: Neovim's
`Iter` and `uv`, openresty's `cjson`.

## 2. Expected vs actual

- **Expected:** `K` is not offered as a bare value, because it is a `local` of another file. It may be
  offered only in a form that is valid there, such as a `require` insertion, or as a type inside a
  LuaCATS comment.
- **Actual (by reading):** it is offered as a plain global and inserted bare. Since BUG-510, the
  accepted name is immediately reported as *Undeclared variable*.

## 3. Root cause (read, not executed)

`GlobalSymbolRankingService.collectClassSymbols` (about lines 190–250) collects class names from the
class index as global value candidates, and `LuaCrossFileCompletionProvider.buildGlobalLookupElement`
inserts them with no `require`. The class index holds only `local` hosts
(`LuaLocalVarStubElementType.indexStub`), so every such candidate is a local of another file.

## 4. Fix strategy

Not planned. Plan with `plan-bug`: first run §1 to confirm the lookup, then decide between dropping
class-local candidates from global value completion and offering them with a `require` insertion.
