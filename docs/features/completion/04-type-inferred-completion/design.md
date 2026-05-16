---
folders:
  - "[[features/completion/04-type-inferred-completion/requirements|requirements]]"
title: "Technical Design"
type: design
---

# COMP-04: Type-Inferred Completion Design

## Architecture

The type-inferred completion system integrates the IntelliJ completion engine with the Lunar type engine (`LuaTypeManager`).

### Components

1.  **`LuaMemberCompletionProvider`**:
    *   Triggered in `LuaIndexExpr` contexts (after `.` or `:`).
    *   Uses the flow-sensitive type graph to determine the type of the receiver.
2.  **`LuaType` Member Enumeration**:
    *   Enhance `LuaType` interface to support `getMembers()` which returns a collection of `LuaMember` objects.
    *   Recursive implementation for `LuaClassType` to include superclass members.

### Logic Flow

1.  **Context Identification**: The `CompletionContributor` identifies the cursor is after a `.` or `:` in a `LuaIndexExpr`.
2.  **Receiver Analysis**:
    *   Extract the "receiver" expression (the part before the separator).
    *   Call `LuaTypesSnapshot.forFile(file)` to get the current type graph state.
    *   Use `snapshot.getValueType(receiver)` to fetch the `LuaGraphType`.
3.  **Type Conversion**:
    *   Convert `LuaGraphType` to a high-level `LuaType` via `snapshot.graphTypeToLuaType()`.
4.  **Member Collection**:
    *   Call `type.getMembers()`.
    *   **Visibility Enforcement**: Filter out members marked `PRIVATE` or `PROTECTED` if the current PSI element is outside the allowed scope.
    *   **Call Strategy Filtering**:
        *   If the separator is a colon (`:`), filter for members that are functions/methods (preferring those defined with `:`).
        *   If the separator is a dot (`.`), show all members but prioritize fields or static functions.
5.  **Lookup Element Creation**:
    *   Wrap each member in a `LookupElement`.
    *   Include full signature or type information in the tail text.
    *   Use appropriate icons (Field vs. Method).

### Metatable and `__index` Resolution

Resolution of members must follow the Lua `__index` rules:
1.  Check the table's own members.
2.  If not found, check the metatable's `__index` field.
3.  If `__index` is a table, recursively search that table.
4.  If `__index` is a function, return the inferred return type of that function (if known).

### Generic Substitution

For `LuaParameterizedType`, `getMembers()` must perform type substitution:
- If a class `List<T>` has a member with type `T`, then for `List<string>`, the member type must be substituted with `string` before being returned to the completion provider.

### Handling Union Types

- When completing a union type `A | B`:
    - **Union Approach (Preferred UX)**: Suggest all members from both `A` and `B`.
    - **Indication**: Mark members that are not present in all types of the union with a "Partial" or "Optional" hint in the tail text.
    - **Type Recovery**: If a member exists in only some types, its completion type should ideally be unioned with `nil` to reflect its potential absence.

### Performance Strategy

- **Member Caching**: Implement lazy-loading or internal caching of member lists within `LuaType` implementations to avoid expensive re-enumeration during completion.
- **Snapshot Caching**: `LuaTypesSnapshot` is already designed to be cached per PSI change.
- **Index Usage**: Use `StubIndex` to avoid full file analysis of external classes when resolving members.
