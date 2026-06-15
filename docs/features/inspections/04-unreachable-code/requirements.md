---
id: INSP-04
title: Unreachable Code Requirements
type: feature
parent_id: INSP
status: planned
---

# Unreachable Code Requirements

## Requirements Table

| ID | Requirement | Priority | Status | Description |
|---|---|---|---|---|
| INSP-04-01 | CFG-based Reachability | Must | planned | Use a Control Flow Graph (CFG) to determine if a statement is reachable. |
| INSP-04-02 | Unreachable Statement Highlighting | Must | planned | Statements that have `Reachability.UNREACHABLE` in the CFG must be highlighted with `WEAK_WARNING`. |
| INSP-04-03 | Remove Unreachable Code Quick Fix | Must | planned | Provide a quick fix to delete the unreachable code safely. |

## Test Cases

### Test Case 1: Simple Return
**Requirement:** INSP-04-02
**Input:**
```lua
function test()
    return 1
    print("unreachable")
end
```
**Action:** Run inspection `LuaUnreachableCodeInspection`.
**Expected Output:** `print("unreachable")` is highlighted with WEAK_WARNING.

### Test Case 2: Dead Branch
**Requirement:** INSP-04-01
**Input:**
```lua
function test()
    while true do break end
    print("reachable")
end
```
**Action:** Run inspection `LuaUnreachableCodeInspection`.
**Expected Output:** No warning on `print("reachable")` because it is reachable after the break.
