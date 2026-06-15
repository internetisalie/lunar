---
id: INTENT-03
title: Name Suggestion Requirements
type: feature
parent_id: REFACT/INTENT
status: planned
---

# Name Suggestion Requirements

## Requirements Table
| ID | Requirement | Priority | Status | Description |
|---|---|---|---|---|
| INTENT-03-01 | Contextual Suggestion | Must | planned | Suggest variable names based on the RHS expression during Introduce Variable refactoring. |
| INTENT-03-02 | Function Call Parsing | Must | planned | Strip 'get' and 'set' prefixes from function calls (e.g. `getUser()` suggests `user`). |

## Test Cases

### Test Case 1: Function Call Suggestion
**Requirement:** INTENT-03-01, INTENT-03-02
**Input:** `getUser()`
**Action:** Highlight `getUser()` and trigger "Introduce Variable" refactoring.
**Expected Output:** The suggestion list includes `user`.
