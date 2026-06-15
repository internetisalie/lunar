---
id: REFACT-06
title: Create from Usage Requirements
type: feature
parent_id: REFACT/INTENT
status: planned
---

# Create from Usage Requirements

## Requirements Table
| ID | Requirement | Priority | Status | Description |
|---|---|---|---|---|
| REFACT-06-01 | Create Local Variable | Must | planned | Provide an intention action to create a local variable declaration when an undeclared identifier is used. |
| REFACT-06-02 | Create Function | Must | planned | Provide an intention action to create a function declaration when an undeclared identifier is called. |

## Test Cases

### Test Case 1: Create Local Variable
**Requirement:** REFACT-06-01
**Input:** `x = 1`
**Action:** Invoke intention "Create local variable 'x'".
**Expected Output:** Code becomes `local x = 1`.

### Test Case 2: Create Function
**Requirement:** REFACT-06-02
**Input:** `myFunc(1, 2)`
**Action:** Invoke intention "Create function 'myFunc'".
**Expected Output:** A new function `local function myFunc(arg1, arg2) end` is created.
