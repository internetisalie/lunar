---
id: INSP-05
title: "05: Global Creation"
type: feature
parent_id: INSP
status: done
vf_icon: ✅
folders:
  - "[[features/inspections/requirements|requirements]]"
---
# Global Creation Requirements

## Requirements Table
| ID | Requirement | Priority | Status | Description |
|---|---|---|---|---|
| INSP-05-01 | Implicit Global | Must | done | Detect assignments to undeclared variables. |
| INSP-05-02 | Make Local Fix | Must | done | Provide `Make Local` quick fix. |

## Test Cases
### Test Case 1
**Requirement:** INSP-05-01
**Input:** `myGlobal = 1`
**Action:** Run inspection.
**Expected Output:** Warning on `myGlobal`.
