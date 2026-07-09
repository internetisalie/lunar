---
id: EDITOR-08
title: "08: Smart Enter (Complete Statement)"
type: feature
parent_id: EDITOR
status: "planned"
priority: "low"
folders:
  - "[[features/editor/requirements|requirements]]"
---
# Specification: EDITOR-08 Smart Enter (Complete Statement)

Ctrl+Shift+Enter completes a half-written statement — supplies the missing `end`/`then`/`do`,
closes brackets, and drops the caret onto the next logical edit point.

## 1. Functional Requirements

| ID | Feature | Expected Behavior | Priority | Status |
| :--- | :--- | :--- | :---: | :--- |
| `EDITOR-08-01` | **Close block keywords** | On `function foo(`/`if x`/`for i=1,n`/`while c`, insert the matching `end` (and `then`/`do`) and place the caret in the body. | **M** | Not Implemented |
| `EDITOR-08-02` | **Close brackets** | Balance unclosed `(`/`{`/`[` on the current statement before completing. | **S** | Not Implemented |
| `EDITOR-08-03` | **`repeat … until`** | Complete `repeat` with an `until <caret>` tail. | **S** | Not Implemented |
| `EDITOR-08-04` | **Caret placement** | After completion, the caret lands at the most likely next edit (condition, body, or new line). | **S** | Not Implemented |

## 2. Technical Details
- EP: `com.intellij.lang.smartEnterProcessor` (`SmartEnterProcessor` / `SmartEnterProcessorWithFixers`
  + per-construct `Fixer`s and an `EnterProcessor`).
- Reuses the keyword-pair table from `EDITOR-01` (soft dependency, shared code — not blocking).
- All edits under `WriteCommandAction`; reformat via the existing formatter.
- Reference: `intellij-community` Java `*SmartEnterProcessor` and its fixer set.
