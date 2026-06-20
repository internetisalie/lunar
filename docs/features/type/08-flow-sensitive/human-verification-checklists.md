---
id: "TYPE-08-CHECKLIST"
title: "Verification Checklists"
type: "qa"
status: "todo"
parent_id: "TYPE-08"
folders:
  - "[[features/type/08-flow-sensitive/requirements|requirements]]"
---

# Verification Checklists: TYPE-08 — Flow-Sensitive Types

## 1. Inlay Hints / Type Display

### Scenario 1.1: `type()` equality guard narrows correctly
- **Setup**: Create a Lua file with:
  ```lua
  ---@type string|number
  local x
  if type(x) == "string" then
      print(x)  -- hover here
  end
  ```
- **Steps**:
  1. Hover over `x` inside the `then` block.
  2. Hover over `x` outside the block (after `end`).
- **Expected**: Inside → type shows `string`. Outside → type shows `string | number`.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 1.2: `~=` nil guard narrows
- **Setup**:
  ```lua
  ---@type string|nil
  local x
  if x ~= nil then
      print(x)  -- hover here
  end
  ```
- **Steps**: Hover over `x` inside the `then` block.
- **Expected**: Type shows `string` (nil removed).
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 1.3: `else` block gets complement type
- **Setup**:
  ```lua
  ---@type string|number
  local x
  if type(x) == "string" then
      print(x)
  else
      print(x)  -- hover here
  end
  ```
- **Steps**: Hover over `x` inside the `else` block.
- **Expected**: Type shows `number`.
- **Result**: ⬜ Pass / ⬜ Fail

## 2. Code Completion

### Scenario 2.1: Completion respects narrowed type
- **Setup**:
  ```lua
  ---@class Point
  ---@field x number
  ---@field y number
  local Point = {}

  ---@class Vector
  ---@field dx number
  ---@field dy number
  local Vector = {}

  ---@type Point|Vector
  local v
  if type(v) == "table" then  -- guard is weak but narrowing should still work for table
      v.  -- trigger completion here
  end
  ```
- **Steps**: Type `v.` inside the `then` block and trigger completion (Ctrl+Space).
- **Expected**: Field narrowing is limited for `table` guard, but completion infrastructure
  should still function (no crash, no empty results from broken scope).
- **Result**: ⬜ Pass / ⬜ Fail

## 3. Inspections / Error Reporting

### Scenario 3.1: Type mismatch respects narrowing
- **Setup**:
  ```lua
  ---@type string|number
  local x
  if type(x) == "string" then
      x = 42  -- assign number to narrowed string
  end
  ```
- **Steps**: Look for type-mismatch inspection on `x = 42`.
- **Expected**: Warning/error on the assignment (if TYPE-03 inspection is enabled).
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 3.2: No regression on non-guard conditionals
- **Setup**:
  ```lua
  ---@type string|number
  local x
  if x > 5 then
      print(x)  -- hover here
  end
  ```
- **Steps**: Hover over `x` inside the `then` block.
- **Expected**: Type shows `string | number` (un-narrowed — no guard recognized).
- **Result**: ⬜ Pass / ⬜ Fail

## 4. Edge Cases

### Scenario 4.1: `elseif` chain
- **Setup**:
  ```lua
  ---@type string|number|boolean
  local x
  if type(x) == "string" then
      print(x)
  elseif type(x) == "number" then
      print(x)  -- hover here
  else
      print(x)  -- hover here
  end
  ```
- **Steps**: Hover over `x` in each branch.
- **Expected**: `then` → `string`; `elseif` → `number`; `else` → `boolean`.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 4.2: Guard on parameter
- **Setup**:
  ```lua
  ---@param x string|nil
  local function f(x)
      if x == nil then
          print(x)  -- hover here
      else
          print(x)  -- hover here
      end
  end
  ```
- **Steps**: Hover over `x` in each branch.
- **Expected**: `then` → `nil`; `else` → `string`.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 4.3: Non-guard conditional is unchanged
- **Setup**:
  ```lua
  ---@type string|number
  local x
  if true then
      print(x)
  end
  ```
- **Steps**: Hover over `x`.
- **Expected**: Type still `string | number`.
- **Result**: ⬜ Pass / ⬜ Fail