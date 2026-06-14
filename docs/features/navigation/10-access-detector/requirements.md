---
id: "NAV-10"
title: "10: Access Detector"
type: "feature"
parent_id: "NAV"
status: "done"
priority: "medium"
folders:
  - "[[features/navigation/requirements|requirements]]"
---

# Specification: NAV-10 Access Detector

This document defines the requirements for detecting how variables are accessed (Read vs. Write).

## 1. Functional Requirements

| ID | Feature | Expected Behavior | Priority | Status |
| :--- | :--- | :--- | :---: | :--- |
| `NAV-10-01` | **Write Detection** | Identify if a variable reference is on the Left-Hand Side (LHS) of an assignment statement. | **S** | Full |
| `NAV-10-02` | **Read Detection** | Identify if a variable reference is on the Right-Hand Side (RHS) of an assignment, in an expression, or passed as an argument. | **S** | Full |
| `NAV-10-03` | **Highlighting Colors** | Apply different highlight colors for Read vs. Write usages when a variable is selected. | **S** | Full |
| `NAV-10-04` | **Find Usages Filter** | Allow "Find Usages" dialog to filter by Read or Write access. | **S** | Full |

## 2. Technical Details
- Requires implementing a `ReadWriteAccessDetector`.
- Needs to accurately analyze `LuaAssignmentStatement` structures (matching LHS var list to RHS expr list).

## 3. Test Cases

### TC-NAV-10-01: Write detection (NAV-10-01)
- **Input**: `x = 1` (global) and `local y = 2`.
- **Action**: `getExpressionAccess` on the `x` ref; `isDeclarationWriteAccess` on the `y` binding.
- **Output**: both `Access.Write`.

### TC-NAV-10-02: Read detection (NAV-10-02)
- **Input**: `print(x); t.k = 1`.
- **Action**: `getExpressionAccess` on `x` and on `t`.
- **Output**: both `Access.Read` (`t` is the index base of `t.k = 1`).

### TC-NAV-10-03: Highlight colors (NAV-10-03)
- **Input**: `x = 1; print(x)`; caret on `x`.
- **Action**: identifier highlight-usages.
- **Output**: `x = 1` uses the Write color, `print(x)` the Read color.
