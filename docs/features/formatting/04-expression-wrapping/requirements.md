---
id: FORMAT-04
title: "04: Expression Wrapping"
type: feature
status: "done"
vf_icon: ✅
priority: "medium"
parent_id: FORMAT
folders: ["[[features/formatting/requirements|requirements]]"]
---

# FORMAT-04: Expression Wrapping

Wrap long call-argument lists and table constructors at the configured right margin.

## Scope
- **In Scope**: wrapping `ARGS` (call arguments), `TABLE_CONSTRUCTOR` fields, and `EXPR_LIST`
  when the line exceeds the right margin, with a "chop down if long" policy.
- **Out of Scope**: alignment (FORMAT-05), comments (FORMAT-06).

## Requirements Table

| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :---: | :--- | :--- |
| `FORMAT-04-01` | **Wrap arguments** | **S** | Full | Chop down call arguments onto separate lines when the call exceeds the right margin. |
| `FORMAT-04-02` | **Wrap table constructors** | **S** | Full | Place table fields on separate lines when the constructor exceeds the right margin. |
| `FORMAT-04-03` | **Configurable** | **S** | Full | Expose wrap policy (Do not wrap / Wrap if long / Chop down if long) per construct in settings. |

## Test Cases

### TC-FORMAT-04-01: Wrap arguments
- **Input**: `f(aaaa, bbbb, cccc, dddd, …)` exceeding `RIGHT_MARGIN`; policy "Chop down if long".
- **Action**: `CodeStyleManager.reformat`.
- **Output**: each argument on its own continuation-indented line.

### TC-FORMAT-04-02: Wrap table constructor
- **Input**: `{ a = 1, b = 2, …long… }` exceeding the margin.
- **Action**: reformat.
- **Output**: each field on its own line inside the braces.

### TC-FORMAT-04-03: Short stays inline
- **Input**: `f(1, 2)` (well under the margin); policy "Wrap if long".
- **Action**: reformat.
- **Output**: unchanged (single line).
