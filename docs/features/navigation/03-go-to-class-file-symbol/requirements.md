---
id: NAV-03
title: "03: Go to Class/File/Symbol"
type: feature
parent_id: NAV
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
| `NAV-03-01` | **Go to Class** | Search and navigate to LuaCATS `@class` definitions, including bare `@class` comments with no associated `local`. | **M** | Full |
| `NAV-03-02` | **Go to Symbol** | Search and navigate to any named global function or global variable. | **M** | Full |
| `NAV-03-03` | **Go to File** | Support standard file navigation for `.lua` files (typically handled by platform, but ensure extensions are registered). | **S** | Full |
| `NAV-03-04` | **Go to Alias** | Search and navigate to LuaCATS `@alias` definitions, including bare `@alias` comments with no associated `local`. | **S** | Full |

## 2. Technical Details
- Requires implementation of `ChooseByNameContributor` and `GotoClassContributor` / `GotoSymbolContributor`.
- Classes/globals are backed by `StubIndex` (`LuaClassNameIndex`, `LuaGlobalDeclarationIndex`) for fast, project-wide lookup without parsing files.
- LuaCATS types (`@class`/`@alias`) are **not** stub-backed: the comment is unstubbed and stub data is only hoisted onto a host `LuaLocalVarDecl`, so a bare `--- @class Name` / `--- @alias Name type` (the pure type-level form, with no `local`) has no host stub. Both are instead indexed by the file-based `LuaCatsTypeNameIndex` (keyed off the `LuaCatsClassTag` / `LuaCatsAliasTag`); the Goto contributors source types from it (not the stub class/alias indexes), with `LuaCatsTypeNavigation` re-resolving the tag PSI for navigation, navigating to the comment identifier and deriving the icon from the tag kind. The stub `LuaClassNameIndex`/`LuaAliasIndex` are retained for type resolution and documentation.
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
