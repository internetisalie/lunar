---
id: COMP-07-REQ
title: Live Templates Requirements
type: requirements
parent_id: COMP-07
status: planned
---

# Live Templates Requirements

## Requirements Table
| ID | Requirement | Priority | Status | Description |
|---|---|---|---|---|
| COMP-07-01 | Basic Templates | Must | planned | Provide built-in live templates for common constructs like `func` (function), `forp` (pairs loop), and `fori` (ipairs loop). |

## Test Cases

### Test Case 1: func template
**Requirement:** COMP-07-01
**Input:** Type `func`
**Action:** Press Tab.
**Expected Output:** Expands to `function($PARAM$) $END$ end` with cursors correctly placed.
