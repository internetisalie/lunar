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

| ID | Requirement | Priority |
| :--- | :--- | :---: |
| `DOC-06-01` | **Stub Indexing** | **M** | Integrate documentation parsing into the IntelliJ Platform's stub indexing system. |
| `DOC-06-02` | **Type Map Construction** | **M** | Build a global map of user-defined types (classes, aliases) for resolution. |
| `DOC-06-03` | **Incremental Updates** | **M** | Ensure indices are updated incrementally as files are modified. |
| `DOC-06-04` | **Full-Text Search** | **S** | Support searching for symbols based on their documentation descriptions. |
| `DOC-06-05` | **Cross-File Resolution** | **M** | Resolve types and classes defined in other files within the same project or libraries. |

## 4. Performance Considerations

- **Memory Usage**: Keep the index size manageable by only storing essential information.
- **Background Processing**: Perform indexing in background threads to avoid UI freezes.
- **Cache Invalidation**: Correctly invalidate cached values when comments change.
