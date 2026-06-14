---
id: "NAV-06"
title: "06: Hierarchy View"
type: "feature"
parent_id: "NAV"
status: "done"
priority: "medium"
folders:
  - "[[features/navigation/requirements|requirements]]"
---

# Specification: NAV-06 Hierarchy View

This document defines the requirements for the Type Hierarchy tool window.

## 1. Functional Requirements

| ID | Feature | Expected Behavior | Priority | Status |
| :--- | :--- | :--- | :---: | :--- |
| `NAV-06-01` | **Type Hierarchy (Subclasses)** | Given a LuaCATS `@class`, show all classes that inherit from it. | **C** | Full |
| `NAV-06-02` | **Type Hierarchy (Supertypes)** | Given a LuaCATS `@class`, show its parent class(es). | **C** | Full |
| `NAV-06-03` | **Method Hierarchy** | Given a method, show its definition in the hierarchy chain (where it is defined, overridden). | **C** | Future Work |

> NAV-06-03 (method hierarchy) is deferred: it needs a separate `MethodHierarchyProvider`/browser
> and EP. The super-direction primitive `LuaOverrideLineMarkerProvider.findSuperMembers` is already
> available to build it on.

## 2. Technical Details
- Requires implementation of `HierarchyProvider`.
- Depends heavily on the type inference engine (`TYPE` requirements) and stub indexing to resolve inheritance chains across the project.

## 3. Test Cases

### TC-NAV-06-02: Supertypes (NAV-06-02)
- **Input**: `---@class Base` and `---@class Derived : Base`; caret on `Derived`.
- **Action**: build the supertype hierarchy structure.
- **Output**: the supertype tree contains `Base`.

### TC-NAV-06-01: Subtypes (NAV-06-01)
- **Input**: same as above; caret on `Base`.
- **Action**: build the subtype hierarchy structure (scan `LuaClassNameIndex`).
- **Output**: the subtype tree contains `Derived`.
