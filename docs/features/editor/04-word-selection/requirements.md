---
id: EDITOR-04
title: "04: Smart Word Selection"
type: feature
parent_id: EDITOR
status: "planned"
priority: "medium"
folders:
  - "[[features/editor/requirements|requirements]]"
---
# Specification: EDITOR-04 Smart Word Selection

Make Ctrl+W (Extend Selection) / Ctrl+Shift+W (Shrink) grow along meaningful Lua constructs instead
of raw word/line boundaries. Small, self-contained, and satisfying to use.

## 1. Functional Requirements

| ID | Feature | Expected Behavior | Priority | Status |
| :--- | :--- | :--- | :---: | :--- |
| `EDITOR-04-01` | **Construct ladder** | Extending selection climbs identifier → argument → call/index expr → statement → block → enclosing function. | **M** | Not Implemented |
| `EDITOR-04-02` | **String interior** | Inside a string literal, one step selects the content without the quotes/long-bracket delimiters, the next includes them. | **S** | Not Implemented |
| `EDITOR-04-03` | **Argument/field lists** | A step selects a single list item, the next the whole comma-separated list within its brackets. | **S** | Not Implemented |
| `EDITOR-04-04` | **Comment interior** | Inside a comment, a step selects the comment text without the leading `--`/long-bracket markers. | **C** | Not Implemented |

## 2. Technical Details
- EP: `com.intellij.extendWordSelectionHandler` (`ExtendWordSelectionHandler` /
  `AbstractWordSelectioner`); return candidate `TextRange`s for the element under caret.
- Provide small dedicated handlers per construct rather than one monolith (≤30-line functions).
- Reference: `intellij-community` `*SelectionHandler` / `*Selectioner` implementations.
