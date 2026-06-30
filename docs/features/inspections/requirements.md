---
id: "INSP"
title: "INSP: Inspections & Diagnostics"
type: "epic"
status: "done"
vf_icon: ✅
priority: "medium"
folders:
  - "[[features]]"
---

# Inspections & Diagnostics Requirements (`INSP`)

Lunar leverages its type engine and PSI tree to provide real-time feedback and catch errors before execution.

| ID | Requirement | Priority | Description |
| :--- | :--- | :---: | :--- |
| [`INSP-01`](01-undeclared-variable/requirements.md) | **Undeclared Variable** | **M** | Highlight variables that are used but never defined. |
| `INSP-02` | **Unused Local/Parameter** | **M** | Warn when a local variable or function parameter is defined but never read. |
| `INSP-03` | **Type Mismatch** | **S** | Warn when assigning a value with a conflicting LuaCATS `@type`. |
| `INSP-04` | **Unreachable Code** | **S** | Detect code following a `return` or `break` that can never be executed. |
| `INSP-05` | **Global Creation Warning** | **S** | Warn when a variable is assigned without `local`. |
| `INSP-06` | **Shadowing Check** | **S** | Warn when a local variable shadows another local in a parent scope. |
| `INSP-07` | **Suspicious Concatenation** | **C** | Warn about string concatenation inside loops. |
| `INSP-08` | **Deprecated API Usage** | **C** | Highlight usage of functions marked with `@deprecated`. |
| `INSP-09` | **Language Level Compliance** | **M** | Warn if using features not supported by the selected Lua version. |

---

## Detailed Implementation Status

### INSP-01: Undeclared Variable
- **Status**: **Planned**
- **Strategy**: Lazy `PsiReference.resolve()` via `LuaNameReference`.
- **Detailed Specification**: [`01-undeclared-variable/requirements.md`](01-undeclared-variable/requirements.md)

### INSP-05: Global Creation Warning
- **Status**: **Partial** (Implemented in `LuaGlobalBindingsAnnotator`)

### INSP-09: Language Level Compliance
- **Status**: **Implemented** (`LuaAttribNameAnnotator` checks for 5.4 attributes)

