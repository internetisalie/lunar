---
id: MAINT-02
title: Label Refactoring Requirements
type: feature
parent_id: MAINT
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
