---
id: INSP-08-REQ
title: Deprecated API Requirements
type: requirements
parent_id: INSP-08
status: done
---

# Deprecated API Requirements

## Requirements Table
| ID | Requirement | Priority | Status | Description |
|---|---|---|---|---|
| INSP-08-01 | Deprecated Usage | Must | planned | Detect references to symbols marked with `---@deprecated`. |
| INSP-08-02 | Strikethrough | Must | planned | Apply `LIKE_DEPRECATED` highlight. |

## Test Cases
### Test Case 1
**Requirement:** INSP-08-01
**Input:** `---@deprecated
local function old() end
old()`
**Action:** Run inspection.
**Expected Output:** `old()` is struck through.
