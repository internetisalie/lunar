---
id: NAVIGATION-01
title: "01: Go to Definition"
type: feature
parent_id: NAV
status: "done"
priority: "medium"
folders:
  - "[[features/navigation/requirements|requirements]]"
---
# Specification: NAV-01 Go to Definition

This document outlines the requirements for Go to Definition.

## 1. Functional Requirements

| ID | Feature | Expected Behavior | Priority | Status |
| :--- | :--- | :--- | :---: | :--- |
| `NAV-01-01` | **Local Symbols** | Navigate to the declaration of local variables and functions. | **M** | Full |
| `NAV-01-02` | **Global Symbols** | Navigate to the declaration of global functions and variables, resolving across files. | **M** | Full |
| `NAV-01-03` | **Table Fields** | Navigate to the definition of a specific table field. | **S** | Full |

## 2. Technical Details
- Uses `PsiReference.resolve()` returning the target element.
- Supported by lazy `PsiScopeProcessor`-based resolution (`LuaScopeProcessor` via `processDeclarations()`) for local resolution and `StubIndex` for global resolution.
