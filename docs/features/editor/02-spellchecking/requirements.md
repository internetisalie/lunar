---
id: EDITOR-02
title: "02: Spellchecking"
type: feature
parent_id: EDITOR
status: "todo"
priority: "high"
folders:
  - "[[features/editor/requirements|requirements]]"
---
# Specification: EDITOR-02 Spellchecking

Wire Lua comments, string literals, and identifiers into the platform spellchecker. Cheap given
the existing lexer, and its absence is something users unconsciously register as "thin."

## 1. Functional Requirements

| ID | Feature | Expected Behavior | Priority | Status |
| :--- | :--- | :--- | :---: | :--- |
| `EDITOR-02-01` | **Comment spellcheck** | Words inside line/block comments (and LuaDoc/LuaCATS prose) are spell-checked as plain text. | **M** | Not Implemented |
| `EDITOR-02-02` | **String literal spellcheck** | Words inside string literals are spell-checked (escapes and long-bracket strings handled). | **S** | Not Implemented |
| `EDITOR-02-03` | **Identifier spellcheck** | Declarations are tokenized (camelCase/snake_case split) and spell-checked; typo highlighting on names. | **S** | Not Implemented |
| `EDITOR-02-04` | **Quick fixes** | "Typo" quick-fixes: change-to suggestions, Save-to-dictionary, and Rename integration for identifiers. | **S** | Not Implemented |
| `EDITOR-02-05` | **Suppression** | Keywords, known stdlib names, and LuaCATS type tokens are excluded from identifier spellcheck. | **C** | Not Implemented |

## 2. Technical Details
- EP: `com.intellij.spellchecker.support` (`SpellcheckingStrategy` + `Tokenizer` per PSI element type).
- Use `TokenizerBase`/`Splitter` variants: plain-text tokenizer for comments/strings, identifier
  splitter for declaration names; return `EMPTY_TOKENIZER` for keywords/operators/numbers.
- Identifier rename quick-fix leverages the existing `refactoringSupport` / `namesValidator`.
- Reference: `intellij-community` `*SpellcheckingStrategy` (e.g. Properties, JSON, Groovy).
