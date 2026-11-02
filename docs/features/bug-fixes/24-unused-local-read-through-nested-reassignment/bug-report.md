---
id: "BUG-24"
title: "A local read after an assignment in a nested block is reported as an unused local"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-24: A local read beside an assignment in a nested block is reported as unused

Found while measuring [[MAINT-53]]'s kind fix, which removed accidental reads that had hidden this defect (`risks-and-gaps.md`, DR-05). It ships ahead of that fix and closes in the same pull request.

## 1. Reproduction

Light fixture (`BasePlatformTestCase`), `myFixture.enableInspections(LuaUnusedLocalInspection())`, `configureByText`, then `doHighlighting()`.

```lua
local x
do
  x = 1
  print(x)
end
```

Two levels of nesting behave the same:

```lua
local x
do
  x = 1
  do
    x = 2
    print(x)
  end
end
```

## 2. Expected vs Actual
- **Expected**: no diagnostic. `x` is read by `print(x)`.
- **Actual**: `Unused local variable 'x'`.
- **Control**: `local x` / `x = 1` with no read still reports `x`, and `local flag` / `flag = true` / `print(flag)` in one block reports nothing.

## 3. Context
- **Corpus effect**: 69 hits fall out of the committed baselines, in `LuaUnusedLocal`: luacheck 7, luarocks 25, Penlight 31, ZeroBrane 3 and lazy.nvim 3. Two are cited in DR-05: luarocks `cmd/make.lua:88` (`version`) and Penlight `lapp.lua:221` (`rest`).

## 4. Root Cause
`LuaUnusedLocalInspection` counts a read only when the name reference resolves to a declaration leaf. A block offers its own declarations before its assignment targets, so in the block that declares the local a read binds to the local. The scope walk reaches an inner block first, though, and a read beside an assignment in a block nested under the local binds to that assignment's name leaf, which is not a declaration leaf. The read is dropped.

## 5. Fix
Follow the read: when it resolves to the name leaf of a simple write target, resolve that write target in turn, until a declaration leaf is reached (a visited set ends cycles such as `x = x`). Design: `docs/features/maint/53-member-resolution-by-receiver-type/design.md`, section 3.1.
