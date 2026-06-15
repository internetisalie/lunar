---
id: TYPE-08
title: Flow Sensitive Typing Requirements
type: feature
parent_id: TYPE
status: planned
---

# Flow Sensitive Typing Requirements

## Requirements Table
| ID | Requirement | Priority | Status | Description |
|---|---|---|---|---|
| TYPE-08-01 | Type Guard Refinement | Must | planned | Refine types based on `type(x) == "string"` or `type(x) ~= "nil"` conditionals. |

## Test Cases

### Test Case 1: Narrowing
**Requirement:** TYPE-08-01
**Input:**
```lua
---@type string|number
local x
if type(x) == "string" then
    -- infer x inside here
end
```
**Action:** Inspect inferred type inside the `if` block.
**Expected Output:** Type is narrowed to `string`.
