---
id: REFACT-05-REQ
title: Name Validator Requirements
type: requirements
parent_id: REFACT-05
status: planned
---

# Name Validator Requirements

## Requirements Table
| ID | Requirement | Priority | Status | Description |
|---|---|---|---|---|
| REFACT-05-01 | Keyword Validation | Must | planned | The plugin must prevent users from renaming variables or functions to reserved Lua keywords. |
| REFACT-05-02 | Identifier Validation | Must | planned | The plugin must validate that a proposed name is a valid Lua identifier (e.g. starts with a letter/underscore). |

## Test Cases

### Test Case 1: Keyword Rejection
**Requirement:** REFACT-05-01
**Input:** User invokes Rename refactoring on `local x = 1` and types `local`.
**Action:** Press Enter to confirm rename.
**Expected Output:** The IDE rejects the rename, displaying an error tooltip: `'local' is a reserved keyword`.

### Test Case 2: Invalid Identifier Rejection
**Requirement:** REFACT-05-02
**Input:** User invokes Rename refactoring on `local x = 1` and types `1var`.
**Action:** Press Enter to confirm rename.
**Expected Output:** The IDE rejects the rename, displaying an error tooltip: `'1var' is not a valid identifier`.
