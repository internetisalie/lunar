---
id: SYNTAX-12
title: "12: Label & Goto Scope Resolution"
type: feature
parent_id: SYNTAX
status: "done"
vf_icon: ✅
priority: "medium"
folders:
  - "[[features/syntax/requirements|requirements]]"
---
# Specification: SYNTAX-12 Label & Goto Scope Resolution

This document defines the requirements for resolving `goto` statements and verifying their validity according to Lua 5.4 scope rules.

## 1. Scope

This specification applies to `goto` statements and `::label::` declarations.

## 2. Syntax

- **Label**: `::name::`
- **Goto**: `goto name`

## 3. Scope Rules (Lua 5.4 Section 3.3.4)

A `goto` statement transfers the control of execution to a label. For a `goto` to be valid, it must adhere to several scoping constraints.

### 3.1 Label Visibility
A label is visible in the entire block where it is defined, except inside nested blocks where a label with the same name is defined and inside nested functions.
- A `goto` may jump backward or forward in the same block.
- A `goto` may jump out of a nested block to an enclosing block.

### 3.2 Jumping into Local Scope (Forbidden)
A `goto` may **not** jump into the scope of a local variable.
- Jumping forward over a local variable declaration in the same block is invalid.
- Jumping into a nested block is invalid (because the label is either out of scope, or implies jumping into the inner block's local scopes).

**Example of Invalid Scope Jump**:
```lua
goto my_label
local x = 10
::my_label:: -- Error: jumping into the scope of local 'x'
```

**Example of Valid Scope Jump**:
```lua
do
    local x = 10
    goto my_label
end
::my_label:: -- Valid: jumping out of scope is fine
```

## 4. Annotator Behavior

1. **Unresolved Label**: If a `goto name` has no corresponding `::name::` in a visible scope, flag it as an error: `Unresolved label 'name'`.
2. **Invalid Scope Jump**: If a `goto` statement jumps over a local variable declaration into its scope, flag it as an error: `Cannot jump into the scope of local variable`.
3. **Duplicate Labels**: If a block defines the same label name more than once, flag the subsequent definitions as an error: `Duplicate label 'name'`.

## 5. Requirements & Implementation Status

| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :---: | :---: | :--- |
| `SYNTAX-12-01` | **Unresolved Label Detection** | **M** | Implemented | Flag `goto` statements that refer to non-existent labels. |
| `SYNTAX-12-02` | **Invalid Scope Jump Detection** | **M** | Implemented | Flag `goto` statements that jump forward over local declarations. |
| `SYNTAX-12-03` | **Duplicate Label Detection** | **M** | Implemented | Flag duplicate label definitions in the same block. |
| `SYNTAX-12-04` | **Function Boundary Isolation** | **S** | Implemented | Labels are not visible across function boundaries. |
| `SYNTAX-12-05` | **Label Shadowing** | **S** | Implemented | Labels in nested blocks correctly shadow labels in outer blocks. |
