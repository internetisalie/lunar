---
id: EDITOR-03
title: "03: TODO / FIXME Indexing"
type: feature
parent_id: EDITOR
status: "done"
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
| `EDITOR-03-01` | **Comment token set** | The `IndexPatternBuilder` reports Lua line/block comment token types and comment start length so the platform scans only comment text. | **M** | Full |
| `EDITOR-03-02` | **TODO tool window** | Default and custom TODO patterns match inside Lua comments and appear in the TODO tool window grouped by file. | **M** | Full |
| `EDITOR-03-03` | **Editor affordances** | Matched TODOs render with the TODO text attribute in-editor and as gutter/error-stripe marks. | **S** | Full |
| `EDITOR-03-04` | **LuaDoc/LuaCATS comments** | Patterns are also matched inside doc-style comments (`---`, `--[[ ]]`, LuaCATS blocks). | **S** | Full |

> **Implementation note (2026-07-10) — the two-part TODO wiring:** `findTodoItems` only runs the
> range searcher when the persisted per-file TODO *count* is non-zero. The platform's default count
> path iterates the **layered editor highlighter**, which re-lexes `---` into inner LuaCats tokens and
> so never counts the lazy `LUACATS_COMMENT`. To make single-line `---` doc comments work, EDITOR-03
> registers **two** extensions: (1) `LuaTodoIndexPatternBuilder` (`indexPatternBuilder`) matches the
> comment *range* with per-marker deltas (`--`=2, `---`=3, `--[[`=4, `--[==[`=6); and (2)
> `LuaTodoIndexer` (`todoIndexer`) supplies the *count* using a **non-layered** `LuaLexer` filter
> lexer that counts all comment kinds including `LUACATS_COMMENT`. Both are needed. `SHEBANG` is
> excluded from the comment set (design §6).

## 2. Technical Details
- EP: `com.intellij.indexPatternBuilder` (`IndexPatternBuilder`) returning the comment
  `TokenSet`, an owning `Lexer`, and comment-prefix length.
- Comment token types already exist in `LuaTokenTypes`; no lexer changes expected.
- Reference: `intellij-community` `*IndexPatternBuilder` (e.g. `PlatformIndexPatternBuilder`, JSON).
