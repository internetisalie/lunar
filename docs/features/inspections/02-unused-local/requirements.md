---
id: INSPECTIONS-02
title: Unused Local Variable Requirements
type: feature
parent_id: INSP
status: done
---

# Unused Local Variable Requirements

## Requirements Table
| ID | Requirement | Priority | Status | Description |
|---|---|---|---|---|
| INSP-02-01 | Unused Variable Detection | Must | done | Detect local variables with no read accesses in the CFG. |
| INSP-02-02 | Remove Quick Fix | Must | done | Provide quick fix to safely remove the unused variable. |

## Test Cases
### Test Case 1
**Requirement:** INSP-02-01
**Input:** `local function test() local x = 1 end`
**Action:** Run inspection.
**Expected Output:** Warning on `x`.
