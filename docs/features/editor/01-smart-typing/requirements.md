---
id: EDITOR-01
title: "01: Smart Typing"
type: feature
parent_id: EDITOR
status: "planned"
priority: "high"
folders:
  - "[[features/editor/requirements|requirements]]"
---
# Specification: EDITOR-01 Smart Typing

Auto-insertion, auto-skip, and auto-deletion of paired delimiters — the single biggest
"feels native" lever Lunar is missing. Lua's keyword-delimited blocks (`do`/`end`,
`then`/`end`, `repeat`/`until`) make block-pair completion especially high-impact.

## 1. Functional Requirements

| ID | Feature | Expected Behavior | Priority | Status |
| :--- | :--- | :--- | :---: | :--- |
| `EDITOR-01-01` | **Auto-close brackets** | Typing `(`, `{`, `[` inserts the matching closer and positions the caret between them (context-aware: not inside strings/comments). | **M** | Not Implemented |
| `EDITOR-01-02` | **Auto-skip closer** | Typing `)`, `}`, `]` over an existing auto-inserted closer moves past it instead of duplicating. | **M** | Not Implemented |
| `EDITOR-01-03` | **Quote pairing** | Typing `"` or `'` inserts the matching quote; typing over the closer skips; unbalanced/mid-word cases suppressed. | **M** | Not Implemented |
| `EDITOR-01-04` | **Backspace unpairing** | Deleting the opener of a freshly auto-inserted empty pair also deletes the closer. | **S** | Not Implemented |
| `EDITOR-01-05` | **Keyword block auto-close** | Completing/typing `do`/`then`/`function`/`for`/`while` scaffolds the matching `end`; `repeat` scaffolds `until`. **On by default**, behind an Editor > General > Smart Keys toggle (JetBrains-language convention). Fires on both keystroke and completion-accept, coordinated with the Enter handler. | **M** | Not Implemented |

## 2. Technical Details
- EPs: `com.intellij.lang.quoteHandler` (`QuoteHandler`/`MultiCharQuoteHandler`),
  `com.intellij.typedHandler` (`TypedHandlerDelegate`), `com.intellij.backspaceHandlerDelegate`.
- Reuses the existing `LuaBraceMatcher` pair table; keyword-pair auto-close coordinates with the
  registered `EnterHandlerDelegate`s.
- **Settings (confirmed decision):** keyword auto-close ships **on by default** with a dedicated
  Smart Keys option (register a `codeInsight.editorActionOptions` / `EditorSmartKeysConfigurable`
  contribution, mirroring how Java exposes "Insert pair `}`" so users can disable it). Bracket/quote
  auto-close (`-01` … `-04`) is unconditional per platform norm.
- Must respect string/comment context — resolve the preceding token via `LuaLexer`/PSI, not offset math.
- Reference: `intellij-community` JSON/Groovy `TypedHandler` + `QuoteHandler`.
