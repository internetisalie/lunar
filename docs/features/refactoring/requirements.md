---
id: "REFACT/INTENT"
title: "REFACT: Refactoring & Intentions"
type: "epic"
priority: low
status: done
vf_icon: ✅
folders:
  - "[[features]]"
---

# Refactoring & Intentions Requirements (`REFACT`, `INTENT`)

Lunar provides tools to safely restructure code and perform automated transformations.

| ID | Requirement | Priority | Description |
| :--- | :--- | :---: | :--- |
| `REFACT-01` | **Rename Refactoring** | **M** | Safely rename symbols and update all references across the project. |
| `REFACT-02` | **Introduce Variable** | **S** | Extract an expression into a local variable and replace all occurrences. |
| `REFACT-03` | **Safe Delete** | **S** | Verify if a symbol is unused before allowing its deletion. |
| `REFACT-04` | **Label Refactoring** | **C** | Support renaming and refactoring of `goto` labels and their references. |
| `REFACT-05` | **Name Validator** | **S** | Validate names for idiomatic conventions and suggest corrections. |
| `REFACT-06` | **Stubs for Declaring Identifiers** | **C** | Generate `.lua` stub files for declaring external APIs. |
| `INTENT-01` | **String Style Conversion** | **C** | Switch between different string quote styles. |
| `INTENT-02` | **Invert If Statement** | **C** | Automatically flip an `if-else` block and its condition. |
| `INTENT-03` | **Name Suggestion** | **S** | Suggest idiomatic names when creating new variables or functions. |

---

## Detailed Implementation Status

### REFACT-01: Rename Refactoring
- **Status**: **Implemented** (`LuaNameReference.handleElementRename`)

### REFACT-04: Label Refactoring
- **Status**: **Implemented** (`LuaLabelFindUsagesProvider`, `LuaLabelRefactoringSupportProvider`)

