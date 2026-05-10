# Specification: DOC-06-06 Platform Symbol Documentation

This document defines the requirements for providing Quick Documentation and symbol resolution for platform and standard library symbols in the Lunar editor.

## 1. Scope

Currently, resolving symbols through Quick Documentation (`Ctrl+Q`) or navigating within LuaCATS type annotations may fail to find targets that are defined in platform libraries (e.g., standard Lua libraries in `src/main/resources/platform/`). This feature extends the documentation indexing and lookup scope to include these platform libraries.

## 2. Syntax/Behavior

- **Quick Documentation Trigger**: When a user triggers Quick Documentation on a standard library symbol (e.g., `math.abs`), the documentation popup should correctly resolve and display the LuaCATS-based documentation from the corresponding platform file.
- **Type Tag Resolution**: When a user hovers over or triggers Quick Documentation on a built-in type within a LuaCATS tag (e.g., `string` in `@param s string`), it should resolve to the `@class string` definition in the platform library (`builtin.lua` or similar).

## 3. Requirements

| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :---: | :---: | :--- |
| `DOC-06-06-01` | **Global Search Scope Integration** | **M** | **Implemented** | Updated `LuaDocumentationTargetProvider` to use `GlobalSearchScope.allScope(project)` instead of `projectScope` when querying the StubIndex. |
| `DOC-06-06-02` | **Alias Index Integration** | **M** | **Implemented** | `LuaDocumentationTargetProvider` queries `LuaAliasIndex` in addition to `LuaClassNameIndex` to support types defined as aliases in platform libraries. |
| `DOC-06-06-03` | **Global Function Index Integration** | **M** | **Implemented** | `LuaDocumentationTargetProvider` queries `LuaGlobalDeclarationIndex` for global functions not covered by reference resolution. |

## 4. Test Cases

| Case | Action | Expected Output |
| :--- | :--- | :--- |
| **TC-01** | Trigger Quick Documentation on `math.abs` call. | Displays documentation from `math.lua` with type `number -> number`. |
| **TC-02** | Trigger Quick Documentation on `string` type in `@param s string`. | Displays documentation for the built-in `string` class from `builtin.lua`. |
| **TC-03** | Trigger Quick Documentation on `assert` call. | Displays documentation for the `assert` function from `global.lua`. |
