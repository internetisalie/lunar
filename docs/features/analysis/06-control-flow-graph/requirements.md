---
id: ANALYSIS-06
title: Control Flow Graph (CFG) Requirements
type: feature
parent_id: ANALYSIS
status: done
vf_icon: ✅
folders:
  - "[[features/analysis/requirements|requirements]]"
---

# Control Flow Graph (CFG) Requirements

## Requirements Table

| ID | Requirement | Priority | Status | Description |
|---|---|---|---|---|
| ANALYSIS-06-01 | Control Flow Builder | Must | Full | Build a standard `ControlFlow` graph from a `LuaBlock` or `LuaFunctionDecl` by traversing statements and handling branches, loops, returns, and breaks. |
| ANALYSIS-06-02 | Read/Write Instructions | Must | Full | Emit `ReadWriteInstruction` nodes for local variable reads, writes, and implicit global assignments. |
| ANALYSIS-06-03 | Reachability Analysis | Must | Full | Provide a mechanism to query whether an `Instruction` is reachable from the entry node. |
| ANALYSIS-06-04 | Control Flow Caching | Must | Full | Cache the CFG per `ScopeOwner` using `CachedValuesManager` to ensure performance during highlighting passes. |

## Test Cases

### Test Case 1: Simple Branching
**Requirement:** ANALYSIS-06-01, ANALYSIS-06-03
**Input:**
```lua
function test(x)
    if x then return 1 else return 2 end
    print("unreachable")
end
```
**Action:** Request CFG for the `test` function and query reachability for `print`.
**Expected Output:** CFG contains `IfStatement`, two `Return` nodes terminating the path. `print` node returns `Reachability.UNREACHABLE`.

### Test Case 2: Read/Write Accesses
**Requirement:** ANALYSIS-06-02
**Input:**
```lua
local a = 1
print(a)
```
**Action:** Request CFG. Filter instructions.
**Expected Output:** Contains a `WRITE` instruction for `a` and a `READ` instruction for `a` in the correct execution order.
