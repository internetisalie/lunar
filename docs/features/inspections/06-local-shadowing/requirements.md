---
id: INSP-06-REQ
title: Local Shadowing Requirements
type: requirements
parent_id: INSP-06
status: done
---

# Local Shadowing Requirements

## Requirements Table

| ID | Requirement | Priority | Status | Description |
|---|---|---|---|---|
| INSP-06-01 | Shadowing Detection | Must | planned | Detect when a local variable shares the same name as a variable in an outer scope. |
| INSP-06-02 | Rename Quick Fix | Must | planned | Provide a rename quick fix. |

## Test Cases
### Test Case 1
**Requirement:** INSP-06-01
**Input:** `local x = 1; function test() local x = 2 end`
**Action:** Run inspection.
**Expected Output:** Warning on the inner `x`.
