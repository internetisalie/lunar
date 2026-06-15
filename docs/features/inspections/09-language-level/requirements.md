---
id: INSP-09
title: Language Level Compliance Requirements
type: feature
parent_id: INSP
status: planned
---

# Language Level Compliance Requirements

## Requirements Table
| ID | Requirement | Priority | Status | Description |
|---|---|---|---|---|
| INSP-09-01 | Attribute Checking | Must | planned | Warn if `<const>` or `<close>` is used below Lua 5.4 |
| INSP-09-02 | Bitwise Operators | Must | planned | Warn if `&`, `|`, `~`, `<<`, `>>` are used below Lua 5.3 |
| INSP-09-03 | Goto Statement | Must | planned | Warn if `goto` is used below Lua 5.2 |

## Test Cases
### Test Case 1: Attributes
**Requirement:** INSP-09-01
**Input:** `local x <const> = 1` in a Lua 5.1 project.
**Action:** Run inspection.
**Expected Output:** Warning on `<const>`.

### Test Case 2: Bitwise
**Requirement:** INSP-09-02
**Input:** `local x = 1 & 2` in a Lua 5.1 project.
**Action:** Run inspection.
**Expected Output:** Warning on `&`.

