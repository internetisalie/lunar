---
id: FORMAT-05
title: "05: Alignment Logic"
type: feature
status: "done"
vf_icon: ✅
priority: "medium"
parent_id: FORMAT
folders: ["[[features/formatting/requirements|requirements]]"]
---

# FORMAT-05: Alignment Logic

Align the `=` of consecutive assignments and the keys/values of table fields for readability.

## Scope
- **In Scope**: aligning the assignment operator across a run of consecutive
  `LuaAssignmentStatement`/`LuaLocalVarDecl`; aligning `=` (and optionally values) across
  `FIELD`s of a table constructor.
- **Out of Scope**: wrapping (FORMAT-04), comments (FORMAT-06).

## Requirements Table

| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :---: | :--- | :--- |
| `FORMAT-05-01` | **Align consecutive assignments** | **S** | Full | Align the `=` across a contiguous run of assignment/local statements. |
| `FORMAT-05-02` | **Align table fields** | **S** | Full | Align the `=` (key→value) across the fields of a table constructor. |
| `FORMAT-05-03` | **Configurable** | **S** | Full | Each alignment is toggled by a code-style setting (default off). |

## Test Cases

### TC-FORMAT-05-01: Assignment alignment
- **Input** (setting on):
  ```lua
  x = 1
  abc = 2
  ```
- **Action**: `CodeStyleManager.reformat`.
- **Output**: the two `=` are column-aligned (`x   = 1` / `abc = 2`).

### TC-FORMAT-05-02: Table field alignment
- **Input** (setting on): `{ a = 1, bbb = 2 }` written one field per line.
- **Action**: reformat.
- **Output**: the field `=` are column-aligned.

### TC-FORMAT-05-03: Disabled
- **Input**: same as TC-FORMAT-05-01 with the setting off.
- **Action**: reformat.
- **Output**: no extra alignment padding.
