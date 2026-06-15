---
id: INTENT-01
title: String Conversion Requirements
type: feature
parent_id: REFACT/INTENT
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
