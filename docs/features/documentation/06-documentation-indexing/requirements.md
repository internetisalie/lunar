---
id: DOC-06
folders:
  - "[[features/documentation/requirements|requirements]]"
title: "06: Documentation Indexing"
type: feature
parent_id: DOC
status: "done"
vf_icon: ✅
---

# Specification: DOC-06 Documentation Indexing

This document defines the requirements for indexing LuaDoc and LuaCATS comments in the Lunar editor to support fast lookup and analysis.

## 1. Scope

Documentation indexing ensures that type annotations, class definitions, and descriptions are processed and stored in a way that allows for efficient symbol resolution, code completion, and quick documentation display without re-parsing files unnecessarily.

## 2. Indexed Elements

### 2.1 Classes and Aliases
- `@class` names and their inheritance (`@extends`).
- `@alias` names and their underlying types.

### 2.2 Global Types
- Types defined using `@type` for global variables.

### 2.3 Fields
- `@field` definitions within classes or tables.

### 2.4 Metadata
- Descriptions (for full-text search).
- Tags like `@deprecated`, `@since`, etc.

## 3. Editor Requirements

| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :---: | :---: | :--- |
| `DOC-06-01` | **Stub Indexing** | **M** | **Full** | Integrate documentation parsing into the IntelliJ Platform's stub indexing system. |
| `DOC-06-02` | **Type Map Construction** | **M** | **Full** | Build a global map of user-defined types (classes, aliases) for resolution. |
| `DOC-06-03` | **Incremental Updates** | **M** | **Full** | Ensure indices are updated incrementally as files are modified. |
| `DOC-06-04` | **Full-Text Search** | **S** | **Full** | Support searching for symbols based on their documentation descriptions. |
| `DOC-06-05` | **Cross-File Resolution** | **M** | **Full** | Resolve types and classes defined in other files within the same project or libraries. |
| `DOC-06-06` | **Platform Symbol Documentation** | **M** | **Not Implemented** | Lookup and display documentation for symbols from platform/library files (e.g., Lua standard library). |

## 4. Performance Considerations

- **Memory Usage**: Keep the index size manageable by only storing essential information.
- **Background Processing**: Perform indexing in background threads to avoid UI freezes.
- **Cache Invalidation**: Correctly invalidate cached values when comments change.

## 5. Examples

Examples demonstrating LuaCATS indexing and cross-file resolution can be found in the test project:
- `examples/luacats_definitions.lua`: Defines `@class`, `@alias`, and functions with `@param`.
- `examples/luacats_usage.lua`: Uses `@type` and references symbols defined in `luacats_definitions.lua`.
