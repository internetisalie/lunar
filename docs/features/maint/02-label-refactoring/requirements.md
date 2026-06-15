---
id: MAINT-02-REQ
title: Label Refactoring Requirements
type: requirements
parent_id: MAINT-02
status: planned
---

# Label Refactoring Requirements

## Requirements Table
| ID | Requirement | Priority | Status | Description |
|---|---|---|---|---|
| MAINT-02-01 | Rename Label | Must | planned | Allow users to rename a `::label::` declaration, updating all `goto label` references in scope. |

## Test Cases

### Test Case 1: Rename Label
**Requirement:** MAINT-02-01
**Input:** `::myLabel:: goto myLabel`
**Action:** Rename `myLabel` to `newLabel`.
**Expected Output:** `::newLabel:: goto newLabel`
