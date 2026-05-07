# Specification: NAV-06 Hierarchy View

This document defines the requirements for the Type Hierarchy tool window.

## 1. Functional Requirements

| ID | Feature | Expected Behavior | Priority | Status |
| :--- | :--- | :--- | :---: | :--- |
| `NAV-06-01` | **Type Hierarchy (Subclasses)** | Given a LuaCATS `@class`, show all classes that inherit from it. | **C** | Not Implemented |
| `NAV-06-02` | **Type Hierarchy (Supertypes)** | Given a LuaCATS `@class`, show its parent class(es). | **C** | Not Implemented |
| `NAV-06-03` | **Method Hierarchy** | Given a method, show its definition in the hierarchy chain (where it is defined, overridden). | **C** | Not Implemented |

## 2. Technical Details
- Requires implementation of `HierarchyProvider`.
- Depends heavily on the type inference engine (`TYPE` requirements) and stub indexing to resolve inheritance chains across the project.
