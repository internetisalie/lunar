---
id: INSP-07-REQ
title: Suspicious Concatenation Requirements
type: requirements
parent_id: INSP-07
status: planned
---

# Suspicious Concatenation Requirements

## Requirements Table
| ID | Requirement | Priority | Status | Description |
|---|---|---|---|---|
| INSP-07-01 | Type Checking | Must | planned | Warn when concatenating a type that is not a string, number, or coercible type. |
| INSP-07-02 | Union Handling | Must | planned | Warn if any variant in a Union type is non-coercible. |

## Test Cases
### Test Case 1
**Requirement:** INSP-07-01
**Input:** `local t = {}; local s = "hello " .. t`
**Action:** Run inspection.
**Expected Output:** Warning on `t`.
