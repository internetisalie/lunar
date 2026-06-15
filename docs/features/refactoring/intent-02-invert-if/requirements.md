---
id: INTENT-02-REQ
title: Invert If Requirements
type: requirements
parent_id: INTENT-02
status: planned
---

# Invert If Requirements

## Requirements Table
| ID | Requirement | Priority | Status | Description |
|---|---|---|---|---|
| INTENT-02-01 | Invert Condition | Must | planned | Invert the condition of an `if` statement (e.g. `==` to `~=`). |
| INTENT-02-02 | Swap Branches | Must | planned | Swap the `then` and `else` blocks of an `if` statement. |

## Test Cases

### Test Case 1: Simple Invert
**Requirement:** INTENT-02-01, INTENT-02-02
**Input:**
```lua
if x == 1 then
    foo()
else
    bar()
end
```
**Action:** Invoke "Invert 'if' statement".
**Expected Output:**
```lua
if x ~= 1 then
    bar()
else
    foo()
end
```
