---
id: MAINT-11
title: "MAINT-11: Test Coverage - Structure View"
type: feature
parent_id: MAINT
status: todo
priority: medium
folders:
  - "[[features/maint/requirements|requirements]]"
---

# MAINT-11: Test Coverage - Structure View

## Overview
Increase test coverage for the Lua Structure Outline panel hierarchy. This ensures all statements, functions, variables, parameters, and labels present cleanly as navigated nodes.

## Scope
* **In Scope**:
  * Unit tests verifying `LuaStructureViewFactory` creates the structure model.
  * Unit tests verifying `LuaStructureViewModel` filters and sorts children (e.g. alphabetical order).
  * Unit tests verifying `LuaFileStructureViewTreeElement` maps file-level definitions.
  * Unit tests verifying child tree elements for global functions, local functions, parameters, variables, returns, and labels.
  * Unit tests verifying `TreeElementUtils` routes elements correctly.
* **Out of Scope**:
  * Testing graphical Swing components of the Structure tool window.

## Functional Requirements
| ID | Requirement | Priority | Status | Description |
|---|---|---|---|---|
| MAINT-11-01 | **Structure View Presentation** | Must | planned | Verify that `LuaStructureViewFactory` creates a valid structure view panel and `LuaStructureViewModel` sorts children. |
| MAINT-11-02 | **File & Class Outline Mapping** | Must | planned | Verify that `LuaFileStructureViewTreeElement` lists top-level statements and global assignments as outline nodes. |
| MAINT-11-03 | **Nested Function Trees** | Must | planned | Verify that both global and local functions list their parameter lists and internal block statements as nested nodes. |
| MAINT-11-04 | **Leaf Variables & Labels** | Must | planned | Verify that local variables, returns, and goto labels render as leaf nodes with appropriate outline icons. |
| MAINT-11-05 | **Routing Utilities** | Must | planned | Verify that `TreeElementUtils` maps PSI elements to their target tree elements without unhandled type exceptions. |

## Acceptance Criteria
* **AC-11-01**: A test case asserts that a Lua file structure model contains sorted child nodes matching its declarations.
* **AC-11-02**: A test case asserts that a top-level global variable `myVar = 1` yields a node in `LuaFileStructureViewTreeElement`.
* **AC-11-03**: A test case asserts that `local function test(a)` yields a local function node containing parameter `a` as a child node.
* **AC-11-04**: A test case asserts that a block return statement `return true` and a label `::done::` produce corresponding structure view tree nodes.
* **AC-11-05**: A test case asserts that `TreeElementUtils.create` successfully returns the correct node subclass for all primary statement types.
