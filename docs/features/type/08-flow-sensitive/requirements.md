---
id: "TYPE-08"
title: "08: Flow-Sensitive Types"
type: "feature"
status: "done"
vf_icon: ✅
priority: "low"
parent_id: TYPE
folders:
  - "[[features/type/requirements|requirements]]"
---

# TYPE-08: Flow-Sensitive Types

Flow-sensitive (path-dependent) type narrowing: when the user writes a type-guard
conditional such as `if type(x) == "string" then … end`, the type engine narrows the
type of `x` within the guarded block.

## Overview

Lunar's type engine (`LuaTypesVisitor` → `LuaTypeGraph` → `LuaTypesSnapshot`) already
infers types from declarations and dataflow. This feature adds *control-flow-aware*
narrowing so that type-guard `if`/`elseif`/`else` branches receive refined types without
the user writing explicit `---@type` annotations. It builds on the existing lexical-scope
machinery (`LuaScope.child()`) — no new `plugin.xml` registrations.

## Scope

### In Scope
- Narrowing through `type(x) == "<typename>"` and `type(x) ~= "<typename>"` guards.
- Narrowing through `x == nil` and `x ~= nil` guards.
- Narrowing in `then`, `elseif`, and `else` blocks.
- Interaction with already-typed locals (e.g. `---@type string|number`).
- Single-variable guards on locals and parameters.

### Out of Scope
- Compound guards (`type(x) == "string" and x ~= nil`). Deferred to `TYPE-08-02` (Could).
- Guards on table fields / upvalues (`type(t.x)`).
- Inter-procedural narrowing (calls inside a guarded block do not save the guard).
- `goto`/`break` label crossing — scope injection respects existing block scope only.

## Functional Requirements

| ID | Requirement | Priority | Description |
|----|-------------|----------|-------------|
| TYPE-08-01 | `type()` equality guard | **M** | `if type(x) == "string" then … end` narrows `x` to `string` in the `then` block; in the `else` block (if any), the original type minus `string`. |
| TYPE-08-02 | `type()` inequality guard | **M** | `if type(x) ~= "nil" then … end` narrows `x` to non-nil (original minus `Nil`) in the `then` block; in the `else` block, `x` is `nil`. |
| TYPE-08-03 | `nil` equality guard | **S** | `if x == nil then … end` narrows `x` to `nil` in the `then` block; in the `else` block, original minus `Nil`. |
| TYPE-08-04 | `nil` inequality guard | **S** | `if x ~= nil then … end` narrows `x` to non-nil in the `then` block; in the `else` block, `x` is `nil`. |
| TYPE-08-05 | `elseif` chain narrowing | **S** | `if type(x) == "string" then … elseif type(x) == "number" then … end` — each `elseif` branch gets the *remaining* type narrowed independently. |
| TYPE-08-06 | Multiple types via `elseif` | **C** | A chain of `type()` guards over all possibilities narrows each branch to its specific type, and the `else` branch to whatever is left. |

## Detailed Specifications

### TYPE-08-01: `type()` equality guard

Given `local x` with union type `string|number`:
```lua
if type(x) == "string" then
    -- x : string (narrowed)
else
    -- x : number (original minus string)
end
```

The type guard is recognized as a `LuaBinOpExpr` with:
- bin-op `==`
- left-hand side: `LuaFuncCall` where `getVarOrExp().text == "type"`
- first argument to `type()` is a `LuaNameRef` whose name matches an in-scope variable
- right-hand side: `LuaTerminalExpr` with `getString() != null`

Mapping of `type()` return values → `LuaGraphType`:
```
"string"  → LuaGraphType.String
"number"  → LuaGraphType.Number
"boolean" → LuaGraphType.Boolean
"nil"     → LuaGraphType.Nil
"table"   → LuaGraphType.Table()
"function"→ LuaGraphType.Function(emptyList(), emptyList())
"thread"  → LuaGraphType.Any
"userdata"→ LuaGraphType.Any
```

### TYPE-08-02: `type()` inequality guard

The same pattern with `~=` reverses the branch targets: the `then` block gets
the original type *without* the matched type; the `else` block gets the matched type.

```lua
if type(x) ~= "nil" then
    -- x : number (original minus nil, for string|number|nil)
else
    -- x : nil
end
```

### TYPE-08-03 / 08-04: `nil` guards

Detected as `LuaBinOpExpr` with bin-op `==` or `~=`, where one side is a `LuaTerminalExpr`
with `NIL` token and the other is a `LuaNameRef` resolving to an in-scope variable.

## Behavior Rules

1. **Scope injection**: the narrowed type is injected as a new `VariableNode` in the child
   scope of the guarded block, shadowing the original binding.
2. **No mutable side effects**: the narrowing is local to the block; the variable's type
   outside the block is unchanged.
3. **Precedence**: if a variable is re-assigned inside the guarded block, the re-assignment
   takes precedence (standard scope rules already handle this).
4. **Recognition failure is silent**: if the condition doesn't match any guard pattern,
   types are un-narrowed — no error, no warning.

## Test Cases

| # | Requirement | Given (input) | When (action) | Then (expected) |
|---|-------------|---------------|---------------|-----------------|
| 1 | TYPE-08-01 | `---@type string\|number; local x; if type(x) == "string" then print(x) end` | Inspect `x` inside `then` block | `x` is `string` |
| 2 | TYPE-08-01 | `---@type string\|number; local x; if type(x) == "string" then print(x) else print(x) end` | Inspect `x` inside `else` block | `x` is `number` |
| 3 | TYPE-08-02 | `---@type string\|number\|nil; local x; if type(x) ~= "nil" then print(x) end` | Inspect `x` inside `then` block | `x` is `string\|number` (nil removed) |
| 4 | TYPE-08-02 | `---@type string\|number\|nil; local x; if type(x) ~= "nil" then print(x) else print(x) end` | Inspect `x` inside `else` block | `x` is `nil` |
| 5 | TYPE-08-03 | `---@type string\|nil; local x; if x == nil then print(x) end` | Inspect `x` inside `then` block | `x` is `nil` |
| 6 | TYPE-08-03 | `---@type string\|nil; local x; if x == nil then print(x) else print(x) end` | Inspect `x` inside `else` block | `x` is `string` |
| 7 | TYPE-08-04 | `---@type string\|nil; local x; if x ~= nil then print(x) end` | Inspect `x` inside `then` block | `x` is `string` (nil removed) |
| 8 | TYPE-08-04 | `---@type string\|nil; local x; if x ~= nil then print(x) else print(x) end` | Inspect `x` inside `else` block | `x` is `nil` |
| 9 | TYPE-08-05 | `---@type string\|number; local x; if type(x) == "string" then … elseif type(x) == "number" then … end` | Inspect `x` in first `elseif` block | `x` is `number` (only `number` remains after `string` was already excluded by the first branch) |
| 10 | TYPE-08-06 | `---@type string\|number\|boolean; local x; if type(x) == "string" then … elseif type(x) == "number" then … else … end` | Inspect `x` in `else` block | `x` is `boolean` (string and number removed by preceding branches) |
| 11 | TYPE-08-01 | `local x = "hello"; if type(x) == "string" then print(x) end` | Inspect `x` in `then` block | `x` is `string` (uninferred `Any` narrowed to `string`) |
| 12 | TYPE-08-01 | `---@type string\|number; local x; if type(x) == "table" then print(x) else print(x) end` | Inspect `x` in `else` block | `x` is `string\|number` (table removed from then) |
| 13 | (no guard) | `---@type string\|number; local x; if x > 5 then print(x) end` | Inspect `x` in `then` block | `x` is `string\|number` (un-narrowed — no type guard recognized) |

## Acceptance Criteria
- [x] All 13 test cases pass as automated tests in `TestFlowSensitiveType`.
- [x] Narrowing does not change the type of `x` when accessed *after* the `if`/`end` block.
- [x] Non-guard conditionals continue to infer types identically (no regression).

## Non-Functional Requirements
- **Threading**: type narrowing runs synchronously inside `LuaTypesSnapshot.forFile()` (read action). No new threads.
- **Performance**: guard detection is a constant-time PSI-node walk on each `if`/`elseif` condition; no additional traversal.
- **Memory**: one extra `VariableNode` + scope binding per guarded variable per branch; negligible.
- **Caching**: narrowing is recomputed with the rest of the snapshot (keyed on file text hash) — no separate cache.

## Dependencies
- **TYPE-01** (Basic Type Inference) — the `LuaTypesVisitor`/`LuaScope`/`LuaGraphType` infrastructure used for injection.
- **TYPE-04** (Union Types) — union-minus operation required for "original type minus guard type."

## See Also
- Design: [design.md](design.md)
- Plan: [implementation-plan.md](implementation-plan.md)
- Risks: [risks-and-gaps.md](risks-and-gaps.md)
- Verification: [human-verification-checklists.md](human-verification-checklists.md)