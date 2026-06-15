---
id: INTENT-01-REQ
title: String Conversion Requirements
type: requirements
parent_id: INTENT-01
status: planned
---

# String Conversion Requirements

## Requirements Table
| ID | Requirement | Priority | Status | Description |
|---|---|---|---|---|
| INTENT-01-01 | Toggle Quote Type | Must | planned | Provide an intention to cycle between single quotes (`'...'`), double quotes (`"..."`), and multi-line (`[[...]]`) string literals. |

## Test Cases

### Test Case 1: Single to Double
**Requirement:** INTENT-01-01
**Input:** `local s = 'hello'`
**Action:** Invoke "Convert string quotes".
**Expected Output:** `local s = "hello"`
