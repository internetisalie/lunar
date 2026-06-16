---
id: "FORMAT"
title: "FORMAT: Formatting"
type: "epic"
status: "done"
priority: "medium"
folders:
  - "[[features]]"
---
# Formatting Requirements (`FORMAT`)

Lunar provides a highly configurable code formatter to ensure consistency across Lua projects.

| ID | Requirement | Priority | Description |
| :--- | :--- | :---: | :--- |
| `FORMAT-01` | **Basic Indentation** | **M** | Correctly indent blocks (`function`, `if`, `while`, etc.) and align table fields. |
| `FORMAT-02` | **Configurable Code Style** | **M** | Provide a settings UI to configure tab size, use of tabs/spaces, and placement of spaces. |
| `FORMAT-03` | **Blank Line Management** | **S** | Automatically insert or remove blank lines between functions and at the end of files. |
| `FORMAT-04` | **Expression Wrapping** | **S** | Wrap long expressions, function arguments, and table constructors based on a configurable right margin. |
| `FORMAT-05` | **Alignment Logic** | **S** | Align consecutive assignments and table field keys for better readability. |
| `FORMAT-06` | **Comment Formatting** | **C** | Wrap and align long comments based on code style settings. |
| `FORMAT-07` | **Stylua Compatibility** | **S** | Review and optionally align formatting rules with `stylua`. |

---

## Detailed Implementation Status

### FORMAT-01: Basic Indentation
- **Status**: **Implemented** (`LuaFormattingModelBuilder`)

### FORMAT-02: Configurable Code Style
- **Status**: **Implemented** (`LuaCodeStyleSettingsProvider`, `LuaLanguageCodeStyleSettingsProvider`)

### FORMAT-03: Blank Line Management
- **Status**: **Implemented** — settings-driven function separation + keep-max in `LuaSpacingBuilder`;
  `LuaTrailingNewlinePostProcessor` for the EOF newline (whole-file reformat only).

### FORMAT-04: Expression Wrapping
- **Status**: **Implemented** — `WRAP_ARGUMENTS`/`WRAP_TABLE_CONSTRUCTOR` settings drive shared
  `Wrap`s on argument `EXPR_LIST` / table `FIELD_LIST` items in `LuaFormatBlock`.

### FORMAT-05: Alignment Logic
- **Status**: **Implemented** — `ALIGN_CONSECUTIVE_ASSIGNMENTS`/`ALIGN_TABLE_FIELDS` thread a shared
  `Alignment` onto each statement/field `=` (ASSIGN) in `LuaFormatBlock` (default off).

### FORMAT-06: Comment Formatting
- **Status**: **Implemented** — leading-space via the platform commenter option;
  `LuaCommentWrapPostProcessor` hard-wraps long `--` lines when `WRAP_LONG_COMMENTS` is on
  (LuaCATS doc comments untouched).

