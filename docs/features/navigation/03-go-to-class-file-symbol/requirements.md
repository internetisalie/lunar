---
id: "NAV-03"
title: "03: Go to Class/File/Symbol"
type: "feature"
parent_id: "NAV"
status: "done"
priority: "medium"
folders:
  - "[[features/navigation/requirements|requirements]]"
---

# Specification: NAV-03 Go to Class/File/Symbol

This document outlines the requirements for quick navigation via "Search Everywhere" or dedicated Go to Class / Symbol actions.

## 1. Functional Requirements

| ID | Feature | Expected Behavior | Priority | Status |
| :--- | :--- | :--- | :---: | :--- |
| `NAV-03-01` | **Go to Class** | Search and navigate to LuaCATS `@class` definitions. | **M** | Not Implemented |
| `NAV-03-02` | **Go to Symbol** | Search and navigate to any named global function or global variable. | **M** | Not Implemented |
| `NAV-03-03` | **Go to File** | Support standard file navigation for `.lua` files (typically handled by platform, but ensure extensions are registered). | **S** | Full |
| `NAV-03-04` | **Go to Alias** | Search and navigate to LuaCATS `@alias` definitions. | **S** | Not Implemented |

## 2. Technical Details
- Requires implementation of `ChooseByNameContributor` and `GotoClassContributor` / `GotoSymbolContributor`.
- Must be backed by `StubIndex` (e.g., `LuaClassNameIndex`, `LuaGlobalDeclarationIndex`) to ensure fast, project-wide lookup without parsing files.
- Ensure appropriate icons (Classes vs Functions vs Variables) are displayed in the popup.

## 3. Test Cases

### TC-NAV-03-01: Go to Class (NAV-03-01)
- **Input**: a file with `---@class MyClass\nlocal MyClass = {}`.
- **Action**: `LuaGotoClassContributor.processNames` / `processElementsWithName("MyClass")`.
- **Output**: `"MyClass"` is among the names; the element is the `@class` `LuaLocalVarDecl`.

### TC-NAV-03-02: Go to Symbol (NAV-03-02)
- **Input**: `function GlobalFn() end`.
- **Action**: `LuaGotoSymbolContributor.processElementsWithName("GlobalFn")`.
- **Output**: the `LuaFuncDecl` for `GlobalFn` is returned.

### TC-NAV-03-04: Go to Alias (NAV-03-04)
- **Input**: `---@alias MyAlias string`.
- **Action**: `LuaGotoClassContributor.processElementsWithName("MyAlias")`.
- **Output**: the alias declaration is returned.
