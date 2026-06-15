---
id: COMP-06-REQ
title: Postfix Templates Requirements
type: requirements
parent_id: COMP-06
status: planned
---

# Postfix Templates Requirements

## Requirements Table
| ID | Requirement | Priority | Status | Description |
|---|---|---|---|---|
| COMP-06-01 | `.if` Template | Must | planned | Convert `expr.if` into `if expr then end`. |
| COMP-06-02 | `.not` Template | Must | planned | Convert `expr.not` into `not expr`. |

## Test Cases

### Test Case 1: .if template
**Requirement:** COMP-06-01
**Input:** `isValid.if`
**Action:** Press Tab.
**Expected Output:** `if isValid then end`
