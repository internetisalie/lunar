---
id: "BUG-516"
title: "A stdlib file method does not resolve through a receiver typed File, so io.stdout:close() finds no declaration"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "low"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-516: `io.stdout:close()` resolves to nothing

Found while delivering [[BUG-513]], as the control for its typed-receiver check.

## 1. Reproduction

Light fixture (`IndexedBasePlatformTestCase`). The target is set to Standard 5.1 with
`setTargetAndNotify` followed by `PlatformLibraryIndex.reload()`. The fixture is the following
source:

```lua
io.stdout:close()
io.stdout:setvbuf("no")
```

`myFixture.file.findReferenceAt(offset of "close")` is a `PsiPolyVariantReference`.
`multiResolve(false).size` returns **0**. This was executed 2026-10-15 on `74792f6a` plus the
BUG-513 test, on the libvirt builder:

```
junit.framework.AssertionFailedError: control: io.stdout:close expected:<1> but was:<0>
```

`lua-5.1/io.lua` declares `---@class File` / `local File = {}` and `function File:close() end`, and
types `io.stdout` with `---@type File`. `lua-5.4/io.lua` has the same shape. The 5.4 case was not
executed, and is expected to behave the same way.

## 2. Expected vs Actual
- **Expected**: `close` in `io.stdout:close()` resolves to `function File:close` in the target's
  `io.lua`. Go to Declaration works on every method of a file handle, and so does the same call
  through any `---@type File` variable.
- **Actual**: no target. A declared method and a missing one look the same at a call site, so
  [[BUG-513]] had to check `File:setvbuf` by stub text instead.

## 3. Context
- AGENTS.md documents the mechanism: "References resolve by qualified name from receiver TEXT, not
  type". `LuaNameReference` looks up `io.stdout:close` by text. The method is declared as
  `File:close`, on a file-local class.
- No open bug covers this. BUG-439 and BUG-440 cover other surfaces and are `done`.
- Not fixed here.
