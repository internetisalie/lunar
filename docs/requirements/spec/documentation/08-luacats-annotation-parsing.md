# Specification: DOC-08 Comprehensive LuaCATS Parsing

This document specifies the requirements for exhaustive support of the LuaCATS annotation system, ensuring compatibility with the official [Lua Language Server (sumneko.lua)](https://luals.github.io/wiki/annotations/) specification.

## 1. Objective

Provide a robust, error-tolerant parser for all LuaCATS tags and type structures to enable accurate static analysis, type inference, and quick documentation.

## 2. Requirements

### 2.1 Full Tag Coverage
The parser must support all tags defined in the LuaCATS specification, including but not limited to:
- **Foundational**: `@class`, `@alias`, `@type`, `@param`, `@return`, `@field`, `@generic`.
- **Advanced**: `@overload`, `@operator`, `@cast`, `@as`.
- **Meta/Behavioral**: `@async`, `@nodiscard`, `@diagnostic`, `@meta`, `@module`, `@version`, `@deprecated`, `@see`, `@source`.

### 2.2 Advanced Type System
Full support for complex type compositions:
- **Recursive Types**: Support for self-referential structures.
- **Generic Captures**: Syntax using backticks (e.g., `` `T` ``) to capture literal string values as types.
- **Literal Unions**: Mixing string/number literals with types (e.g., `"auto" | "manual" | number`).
- **Function Literals**: Anonymous function signatures with named/unnamed parameters and return types.
- **Dictionaries & Tuples**: `{ [K]: V }`, `table<K, V>`, and `[T1, T2]`.

### 2.3 Structural Features
- **Multi-line Enums/Aliases**: Support for the `---|` syntax for defining enum values across multiple lines.
- **Class Merging/Inheritance**: Parsing multiple parent classes (`---@class A : B, C`).
- **Visibility & Scope**: Handling `public`, `protected`, `private`, and `package` modifiers on fields.

### 2.4 Diagnostic Support
- Support for `@diagnostic` with states (`disable-next-line`, `enable`, etc.) and specific diagnostic names.

## 3. Implementation Plan

### Phase 1: Grammar Refinement
- Update `luacats.bnf` to include missing tags (`@operator`, `@cast`, etc.).
- Implement multi-line support for enums and aliases.
- Refine `type` rule to handle backtick captures and literal unions more robustly.

### Phase 2: Lexer Updates
- Ensure `luacats.flex` can tokenize all keywords and symbols (e.g., `+`, `-` for `@cast`, backticks for generics).

### Phase 3: Validation Suite
- Create `LuaCatsParserTest` inheriting from `ParsingTestCase`.
- Implement exhaustive test cases covering every tag and type combination described in the LuaCATS wiki.

## 4. Verification

| ID | Requirement | Status | Verification Method |
| :--- | :--- | :---: | :--- |
| `DOC-08-01` | **Full Tag Support** | **Not Implemented** | `LuaCatsParserTest` |
| `DOC-08-02` | **Complex Types** | **Partial** | `LuaCatsParserTest` |
| `DOC-08-03` | **Multi-line Support** | **Not Implemented** | `LuaCatsParserTest` |
| `DOC-08-04` | **Parser Validation** | **Not Implemented** | New test suite in `luacats.lang.parser` |
