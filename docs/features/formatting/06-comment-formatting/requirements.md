---
id: FORMAT-06
title: "06: Comment Formatting"
type: feature
status: "done"
priority: "medium"
parent_id: FORMAT
folders: ["[[features/formatting/requirements|requirements]]"]
---

# FORMAT-06: Comment Formatting

Normalize line comments (leading space) and optionally hard-wrap long comments at the right
margin.

## Scope
- **In Scope**: ensure a space after `--` on reformat; optional hard-wrap of long line comments
  at the right margin (preserving the `--` prefix).
- **Out of Scope**: LuaCATS doc-comment (`---@`) reflow (kept verbatim); alignment of trailing
  comments (FORMAT-05-DR-01).

## Requirements Table

| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :---: | :--- | :--- |
| `FORMAT-06-01` | **Space after `--`** | **C** | Full | On reformat, ensure a single space after `--` for line comments (driven by `LINE_COMMENT_ADD_SPACE_ON_REFORMAT`). |
| `FORMAT-06-02` | **Wrap long comments** | **C** | Full | Hard-wrap a line comment exceeding the right margin onto continuation `--` lines (opt-in). |

## Test Cases

### TC-FORMAT-06-01: Space after dashes
- **Input**: `--no space comment`; `LINE_COMMENT_ADD_SPACE_ON_REFORMAT = true`.
- **Action**: `CodeStyleManager.reformat`.
- **Output**: `-- no space comment`.

### TC-FORMAT-06-02: Wrap long comment
- **Input**: a `-- …` line longer than `RIGHT_MARGIN`; wrap option on.
- **Action**: reformat.
- **Output**: split into multiple `-- ` lines each within the margin, words preserved.

### TC-FORMAT-06-03: Doc comment preserved
- **Input**: a `---@param x number` LuaCATS comment.
- **Action**: reformat.
- **Output**: unchanged (doc comments are not reflowed).
