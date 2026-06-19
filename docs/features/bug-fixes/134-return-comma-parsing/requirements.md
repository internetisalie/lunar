---
id: "BUG-134"
title: "@return Comma Parsing"
type: "feature"
status: "planned"
priority: "high"
parent_id: "BUG"
folders:
  - "[[features/bug-fixes/requirements|requirements]]"
---

# BUG-134: @return Comma Parsing

## Overview
The LuaCATS `@return` tag currently fails to parse when multiple return types are separated by a comma on a single line (e.g., `---@return number count, string error`). This causes the parser to generate a `PsiErrorElement`, breaking type inference and inlay hints for subsequent values. This feature fixes the LuaCATS grammar to support standard comma-separated multiple return types.

## Scope

### In Scope
- Modifying the `luacats.bnf` grammar to support a comma-separated list of types within a single `@return` tag.
- Refactoring existing Kotlin code that calls `getArgType()` on `LuaCatsReturnTag` to handle the new `LuaCatsReturnTypeDescriptor` list.
- Properly preserving the positional indices of multiple return types (e.g. mapping the second type to the second returned value).

### Out of Scope
- Changing the stub index format to store multiple return types (the stub will continue to store only the first return type to maintain compatibility).

## Functional Requirements

| ID | Requirement | Priority | Description |
|----|-------------|----------|-------------|
| BUG-134-01 | **Parse Multiple Returns** | M | The `@return` tag must successfully parse a comma-separated list of types without producing an error element. |
| BUG-134-02 | **Parse Optional Names and Descriptions** | M | Each comma-separated return type may optionally include a parameter name and/or a description. |
| BUG-134-03 | **Positional Inference** | M | The type engine must accurately assign positional return types extracted from a single `@return` tag. |

## Detailed Specifications

### BUG-134-01: Parse Multiple Returns
Given a function annotated with `---@return string, number`, the parser should identify two distinct return types (`string` and `number`) grouped under a single `LuaCatsReturnTag`.

## Test Cases

| # | Requirement | Given (input) | When (action) | Then (expected) |
|---|-------------|---------------|---------------|-----------------|
| 1 | BUG-134-01 | `---@return string id, boolean status` | Parser processes the LuaCATS comment | AST contains `LuaCatsReturnTag` with two `LuaCatsReturnTypeDescriptor` elements |
| 2 | BUG-134-03 | `local a, b = myFunc()` where `myFunc` has `---@return number, string` | User checks inferred types of `a` and `b` | `a` is inferred as `number`, `b` is inferred as `string` |

## Acceptance Criteria
- [ ] `luacats.bnf` parses comma-separated return tags successfully.
- [ ] No `PsiErrorElement` is generated when typing a comma inside a `@return` tag.
- [ ] Inferred types respect the positional return values parsed from the comma-separated list.
- [ ] Existing functionality relying on `@return` (like MethodChain inlay hints and stub indexing) continues to work.

## See Also
- Design: [design.md](design.md)
- Plan: [implementation-plan.md](implementation-plan.md)
