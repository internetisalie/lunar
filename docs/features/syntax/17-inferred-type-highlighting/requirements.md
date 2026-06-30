---
id: SYNTAX-17
title: "17: Inferred-Type Highlighting"
type: feature
parent_id: SYNTAX
status: "done"
vf_icon: ✅
priority: "low"
folders:
  - "[[features/syntax/requirements|requirements]]"
---

# SYNTAX-17: Inferred-Type Highlighting Requirements

Enhance editor visuals by coloring identifiers based on their inferred value types rather than just their lexical scope.

## Scope

### In Scope
- **Function/Method Calls**: Highlight identifiers in call expressions based on whether they resolve to a function or method.
- **Type-Specific Identifiers**: Color variables based on their primary type (e.g., Table, String, User-defined Class).
- **Member Highlighting**: Differentiate between fields, methods, and constants within table indexing.
- **Integration with TYPE Epic**: Leverage the `LuaTypeManager` for resolution.

### Out of Scope
- Basic scope highlighting (handled by `SYNTAX-02`).
- Inlay hints (handled by `SYNTAX-07`).

## Requirements Table

| ID | Requirement | Priority | Description |
| :--- | :--- | :---: | :--- |
| `SYNTAX-17-01` | **Call Site Highlighting** | **M** | Color identifiers in `f()` or `obj:method()` based on their resolved function type. |
| `SYNTAX-17-02` | **Class/Enum Highlighting** | **S** | Highlight identifiers that refer to a `@class` or `@enum` definition. |
| `SYNTAX-17-03` | **Table Field vs. Method** | **S** | Differentiate between fields (data) and methods (functions) in table indexing. |
| `SYNTAX-17-04` | **Performance Throttling** | **M** | Ensure type-based highlighting is calculated on a lower-priority background pass to avoid UI lag. |

## Test Cases

Verified via `myFixture.doHighlighting()` asserting a `HighlightInfo` at the identifier range
carries the expected `forcedTextAttributesKey` (the keys defined in design §2.2).

| ID | Input | Expected Output |
| :--- | :--- | :--- |
| `TC-01` | `local x = function() end; x()` | the `x` in `x()` carries `INFERRED_LOCAL_CALL`. |
| `TC-02` | `---@class MyClass; local o = MyClass()` | `MyClass` in `MyClass()` carries `INFERRED_CLASS`. |
| `TC-03` | `local t = {}; t.data = 1; function t:func() end; t.data; t:func()` | the member `data` carries `INFERRED_FIELD`; the member `func` carries `INFERRED_METHOD`. |
| `TC-04` | `print("hi")` | `print` carries `INFERRED_GLOBAL_CALL` (resolves to a non-local function). |
| `TC-05` | indexing in progress (dumb mode) | `classify` returns null for all identifiers (no forced attributes). |

## Implementation status

Implemented by `LuaInferredTypeAnnotator` (`lang/syntax/`). 17-01 (call sites), 17-02 (class refs)
and 17-04 (dumb-mode/background pass) are Full. 17-03 (field vs method) is Partial: data fields and
assignment-registered methods (`t.func = function`) are distinguished, but colon-declared methods
(`function t:func()`) are not class members in the type engine (see the type-engine idioms in the
agent guide), so they are colored best-effort. TC-05 is verified by invoking the annotator directly
in dumb mode (calling `doHighlighting` inside a forced dumb block deadlocks).
