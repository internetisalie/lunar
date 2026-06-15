---
id: COMP-08-REQ
title: Auto Complete Requirements
type: requirements
parent_id: COMP-08
status: planned
---

# Auto Complete Requirements

## Requirements Table
| ID | Requirement | Priority | Status | Description |
|---|---|---|---|---|
| COMP-08-01 | Block Auto-close | Must | planned | Automatically insert `end` when pressing Enter after a block starter (`then`, `do`, `function`). |

## Test Cases

### Test Case 1: Enter after then
**Requirement:** COMP-08-01
**Input:** `if true then<Caret>`
**Action:** Press Enter.
**Expected Output:**
```lua
if true then
    <Caret>
end
```
