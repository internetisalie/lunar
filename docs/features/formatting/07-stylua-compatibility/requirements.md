---
id: FORMAT-07
title: Stylua Compatibility Requirements
type: feature
parent_id: FORMAT
status: planned
---

# Stylua Compatibility Requirements

## Requirements Table
| ID | Requirement | Priority | Status | Description |
|---|---|---|---|---|
| FORMAT-07-01 | External Formatter | Must | planned | Allow configuring `stylua` as the default formatter instead of the built-in formatter. |

## Test Cases

### Test Case 1: Format with Stylua
**Requirement:** FORMAT-07-01
**Input:** Unformatted lua code, with `stylua` path configured.
**Action:** Reformat code.
**Expected Output:** Code is formatted according to `stylua` rules.
