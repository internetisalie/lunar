---
id: EDITOR-03
title: "03: TODO / FIXME Indexing"
type: feature
parent_id: EDITOR
status: "planned"
priority: "high"
folders:
  - "[[features/editor/requirements|requirements]]"
---
# Specification: EDITOR-03 TODO / FIXME Indexing

Surface `TODO`/`FIXME` (and user-configured patterns) inside Lua comments in the TODO tool window,
gutter stripe, and the "Show TODOs" scopes. Nearly free given the existing lexer already classifies
comment tokens.

## 1. Functional Requirements

| ID | Feature | Expected Behavior | Priority | Status |
| :--- | :--- | :--- | :---: | :--- |
| `EDITOR-03-01` | **Comment token set** | The `IndexPatternBuilder` reports Lua line/block comment token types and comment start length so the platform scans only comment text. | **M** | Not Implemented |
| `EDITOR-03-02` | **TODO tool window** | Default and custom TODO patterns match inside Lua comments and appear in the TODO tool window grouped by file. | **M** | Not Implemented |
| `EDITOR-03-03` | **Editor affordances** | Matched TODOs render with the TODO text attribute in-editor and as gutter/error-stripe marks. | **S** | Not Implemented |
| `EDITOR-03-04` | **LuaDoc/LuaCATS comments** | Patterns are also matched inside doc-style comments (`---`, `--[[ ]]`, LuaCATS blocks). | **S** | Not Implemented |

## 2. Technical Details
- EP: `com.intellij.indexPatternBuilder` (`IndexPatternBuilder`) returning the comment
  `TokenSet`, an owning `Lexer`, and comment-prefix length.
- Comment token types already exist in `LuaTokenTypes`; no lexer changes expected.
- Reference: `intellij-community` `*IndexPatternBuilder` (e.g. `PlatformIndexPatternBuilder`, JSON).
