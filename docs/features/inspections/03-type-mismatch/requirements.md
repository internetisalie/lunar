---
id: INSP-03
title: Type Mismatch Requirements
type: feature
parent_id: INSP
status: planned
---

# Type Mismatch Requirements

## Requirements Table
| ID | Requirement | Priority | Status | Description |
|---|---|---|---|---|
| INSP-03-01 | Type Compatibility Check | Must | planned | Verify value assigned matches the declared variable type |
| INSP-03-02 | Union Type Check | Must | planned | Verify value assigned matches at least one variant of a union |
| INSP-03-03 | Return Type Check | Should | planned | Verify return values match the function's `@return` type |

## Test Cases
### Test Case 1: Simple Mismatch
**Requirement:** INSP-03-01
**Input:**
```lua
---@type string
local x = 42
```
**Action:** Run LuaTypeMismatchInspection.
**Expected Output:** Warning on `42` that `number` cannot be assigned to `string`.

### Test Case 2: Union Match
**Requirement:** INSP-03-02
**Input:**
```lua
---@type string|number
local x = 42
```
**Action:** Run LuaTypeMismatchInspection.
**Expected Output:** No warning.

