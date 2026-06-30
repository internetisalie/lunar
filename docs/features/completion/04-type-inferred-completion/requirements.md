---
id: COMP-04
title: "04: Type-Inferred Completion"
type: feature
parent_id: COMP
status: "done"
vf_icon: ✅
priority: "high"
folders:
  - "[[features/completion/requirements|requirements]]"
---

# COMP-04: Type-Inferred Completion Requirements

Suggest members of a table or class based on its inferred type (via LuaCATS or assignment).

## Scope

### In Scope
- **Member Completion**: Suggest fields and methods when completing after a dot (`.`) or colon (`:`).
- **Class Members**: Suggest members defined in `@class` via `@field` or inheritance.
- **Table Literals**: Suggest members of tables defined via literal assignments (e.g., `local t = { x = 1 }; t.|`).
- **Union Type Members**: Support completion for union types (e.g., `string | myClass`).
- **Inheritance Support**: Suggest members from base classes.
- **Visibility Awareness**: Distinguish between "fields" and "methods" (suggesting colon `:` for methods).

### Out of Scope
- Keyword completion (covered by `COMP-01`).
- Basic symbol completion (covered by `COMP-02`).
- Cross-file symbol resolution (covered by `COMP-03`), though once a cross-file symbol is resolved to a type, its members are in scope.

## Requirements Table

| ID | Requirement | Priority | Description |
| :--- | :--- | :---: | :--- |
| `COMP-04-01` | **Dot Member Completion** | **M** | Suggest fields and functions when completing after a dot (`.`). |
| `COMP-04-02` | **Colon Method Completion** | **M** | Suggest only functions (methods) when completing after a colon (`:`). |
| `COMP-04-03` | **Inherited Member Support** | **M** | Include members from parent classes in the suggestion list. |
| `COMP-04-04` | **Literal Table Completion** | **M** | Suggest members of anonymous tables defined via literal syntax. |
| `COMP-04-05` | **LuaCATS Member Support** | **M** | Suggest members defined via `@field` tags in LuaCATS annotations. |
| `COMP-04-06` | **Union Type Completion** | **S** | Suggest members common to all types in a union, or all possible members if appropriate. |
| `COMP-04-07` | **Auto-Insert Colon** | **S** | (Optional) Switch dot to colon or vice-versa based on member type. |
| `COMP-04-08` | **Metatable/Index Support** | **M** | Resolve members through `__index` in metatables. |
| `COMP-04-09` | **self Context Awareness** | **M** | Correctly type `self` in methods for completion. |
| `COMP-04-10` | **Overload Support** | **S** | Display all available overloads in completion list. |

## Test Cases

| ID | Action | Expected Output |
| :--- | :--- | :--- |
| `TC-01` | `local t = { name = "Lua" }; t.` | `name` appears in the completion list. |
| `TC-02` | `---@class A @field x number; ---@class B : A; ---@type B; local b; b.` | `x` appears in the completion list. |
| `TC-03` | `---@class C; function C:test() end; ---@type C; local c; c:` | `test` appears in the completion list. |
| `TC-04` | `---@type string|number; local v; v.` | Suggest members of both `string` and `number` (or intersection). |
| `TC-05` | `local t = setmetatable({}, { __index = { x = 1 } }); t.` | `x` appears in the completion list. |
| `TC-06` | `function MyClass:method() self.` | Members of `MyClass` appear in the completion list. |

## Deferred (Future Work)

- **COMP-04-06 Union "partial" marking (S):** Future Work — union receivers list all members; per-branch "(partial)" tagging is not yet implemented.
- **COMP-04-10 Overload expansion (S):** Future Work — a single lookup element is shown per member; multi-signature overload sets are not split into separate entries.
- **Visibility filtering (S):** Future Work — all public/non-public members are offered; visibility-based filtering is not yet applied.
- **Method-definition member registration:** Future Work — a method written as `function C:test() end` is not merged into `@type C`'s member set, so TC-03 is verified via `---@field test function` instead. Same implicit-member-registration capability tracked by **TYPE-02** (implicit fields); once TYPE-02 lands, the `function C:m()` colon-completion path can be covered directly.
- **`@field x fun(...)` member typing:** known LuaCATS gap — `fun(...)` field annotations do not yet resolve to a `Function` type (use `---@field x function` meanwhile); tracked separately from COMP-04.
