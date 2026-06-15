---
id: FORMAT-03
title: "03: Blank Line Management"
type: feature
status: "planned"
priority: "medium"
parent_id: FORMAT
folders: ["[[features/formatting/requirements|requirements]]"]
---

# FORMAT-03: Blank Line Management

Insert/remove blank lines around functions and at end-of-file, honoring the standard
*Blank Lines* code-style settings.

## Scope
- **In Scope**: configurable blank lines around function definitions; capping consecutive blank
  lines (keep-max); ensuring a single trailing newline at EOF.
- **Out of Scope**: blank lines inside expressions/tables (FORMAT-04/05); comment reflow (FORMAT-06).

## Requirements Table

| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :---: | :--- | :--- |
| `FORMAT-03-01` | **Blank lines around functions** | **S** | Partial | Enforce `BLANK_LINES_AROUND_METHOD` between function definitions (currently hard-coded to 1 via `STANZA_SPACING`). |
| `FORMAT-03-02` | **Keep-max blank lines** | **S** | Not Implemented | Collapse runs of blank lines to at most `KEEP_BLANK_LINES_IN_CODE`. |
| `FORMAT-03-03` | **Trailing newline** | **S** | Not Implemented | Ensure exactly one newline at end of file on reformat. |

## Test Cases

### TC-FORMAT-03-01: Function separation
- **Input**: two `function` defs with no blank line between them; `BLANK_LINES_AROUND_METHOD = 1`.
- **Action**: `CodeStyleManager.reformat`.
- **Output**: exactly one blank line between the functions.

### TC-FORMAT-03-02: Keep-max
- **Input**: 4 consecutive blank lines inside a block; `KEEP_BLANK_LINES_IN_CODE = 1`.
- **Action**: reformat.
- **Output**: collapsed to 1 blank line.

### TC-FORMAT-03-03: Trailing newline
- **Input**: a file ending without a newline (or with several).
- **Action**: reformat.
- **Output**: file ends with exactly one `\n`.
