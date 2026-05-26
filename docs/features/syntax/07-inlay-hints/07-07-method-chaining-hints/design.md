---
id: "SYNTAX-07-07-DESIGN"
title: "Technical Design"
type: "design"
parent_id: "SYNTAX-07-07"
status: "planned"
priority: "medium"
folders:
  - "[[features/syntax/07-inlay-hints/07-07-method-chaining-hints/requirements|requirements]]"
---

# Design: SYNTAX-07-07 Method Chaining Hints

## Objective
The goal is to implement a dedicated `InlayHintsProvider` that identifies fluent interface patterns (method chains) in Lua and displays the return type of intermediate calls. This is particularly useful in builders, DSLs, or libraries that use chaining extensively.

## Architecture

### 0. Provider Registration
- **Provider**: `LuaMethodChainInlayHintProvider`
- **Group**: `METHOD_CHAINS_GROUP`
- **ID**: `lua.method.chain.hints`
- **Language**: `Lua`

### 1. Chain Detection
- Traverse the PSI tree looking for `LuaFuncCall` elements.
- Determine if the call is part of a chain by checking its receiver (via `varOrExp`).
- A call warrants a hint if:
    - It is a method call (`:` syntax).
    - Its receiver is also a `LuaFuncCall` OR it has a subsequent `LuaFuncCall` in a chain.
    - The call expression starts on a different line than its receiver's start offset.
- To handle the "Partial single-line" case (TC-09), a hint is shown for any call in a chain that is followed by a newline before the next chained call, or if it is the first call on a new line.

### 2. Type Resolution
- Use `LuaTypesVisitor.getTypes()` or `LuaTypeManager` to resolve the return type of the method call.
- **Self Propagation**: If the resolved return type is explicitly `self` (from LuaCATS), the hint must display the concrete class of the receiver.
- **Generic Instantiation**: Ensure return types are resolved using the instantiated generic context of the call site.
- **Caching**: Use a `CachedValue` for chain detection if performance on large files with thousands of calls becomes an issue.

### 3. Hint Placement & Formatting
- The hint is placed at the end of the `LuaFuncCall` (after the closing parenthesis).
- Use a shared utility `LuaInlayTypeUtil.formatReturnTypes(List<LuaGraphType>)` to generate the comma-separated string, consistent with SYNTAX-07-06.

## Suppression Logic
- **Single-line Suppression**: If both the call and its receiver are on the same line, suppress the hint.
- **Trivial Type Suppression**: Suppress hints for `boolean`, `number`, or `nil` returns unless they are part of a multiple-return value, as these are often obvious from the method name (e.g., `is*`, `has*`, `getAge`).
- **Complexity/Depth Suppression**: 
    - Stop traversing/resolving chains deeper than 10 calls.
    - Truncate or suppress union types with more than 3 members.

## Performance Considerations
- Method chains can be deep. Resolution should be cached or limited in depth to prevent editor hangs.
- Since we use the background declarative inlay API, we are safe from blocking the EDT, but we should still be efficient with CPU usage.
