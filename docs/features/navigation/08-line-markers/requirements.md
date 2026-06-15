---
id: NAVIGATION-08
title: "08: Line Markers"
type: feature
parent_id: NAV
status: "done"
priority: "medium"
folders:
  - "[[features/navigation/requirements|requirements]]"
---
# Specification: NAV-08 Line Markers

This document defines the requirements for gutter line markers.

## 1. Functional Requirements

| ID | Feature | Expected Behavior | Priority | Status |
| :--- | :--- | :--- | :---: | :--- |
| `NAV-08-01` | **Recursive Call Marker** | Display an icon (`AllIcons.Gutter.RecursiveMethod`) on function calls that recursively call their enclosing function. | **S** | Full |
| `NAV-08-02` | **Tail Call Marker** | Display an icon (`AllIcons.Actions.Forward`) on the `return` keyword for tail calls (e.g., `return f()`). | **S** | Full |

## 2. Technical Details
- Implemented via `LineMarkerProvider`.
- Must distinguish between the `return` statement and the actual function call identifier to allow both markers to appear on recursive tail calls.
