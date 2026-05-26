---
id: "COMP-04-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "COMP-04"
status: "planned"
priority: "high"
folders:
  - "[[features/completion/04-type-inferred-completion/requirements|requirements]]"
---

# COMP-04: Type-Inferred Completion Implementation Plan

## Phases

### Phase 1: Member Enumeration [Must]
- Enhance `LuaType` and its implementations (`LuaClassType`, `LuaTableLiteralType`, `LuaPrimitiveType`) with `getMembers()`.
- Implement recursive member collection for inheritance with **circular reference protection**.
- **Member Caching**: Implement lazy-loading or internal caching of member lists to ensure performance during completion.
- **Implicit `self` injection**: Ensure methods have a correctly typed `self` variable in their scope.
- **Metatable Member Discovery**: Implement `__index` resolution logic in `LuaTableLiteralType` and `LuaClassType`.
- **Verification**: Unit tests verifying that `getMembers()` returns the expected symbols for various types, including inherited and metatable members.

### Phase 2: Completion Provider [Must]
- Implement `LuaMemberCompletionProvider`.
- Integrate with `LuaTypesSnapshot` to get the receiver's type.
- Handle dot (`.`) and colon (`:`) filtering based on member call strategy.
- Implement visibility checks for `PRIVATE`/`PROTECTED` members.
- **Verification**: Integration tests for table literal, basic `@class`, and visibility-restricted completion.

### Phase 3: Advanced Type Support [Should]
- Support completion for `LuaUnionType` using the **Union of Members** approach (suggesting all possible members).
- Support completion for `LuaAliasType` (enums/aliases).
- **Generic Substitution**: Implement type substitution for `LuaParameterizedType`.
- Handle specialized types like `string` and `table` methods from the platform library.
- **Verification**: Tests for `string:sub()`, union type members, and generic type specialization.

### Phase 4: UI & Polish [Could]
- Add type information to the tail text of lookup elements.
- Implement specialized icons for different member kinds.
- **Verification**: Manual UI check.

## Verification Tasks

- [ ] [Must] Implement `LuaTypeMemberTests`.
- [ ] [Must] Implement `LuaMemberCompletionTests`.
- [ ] [Should] Verify completion in nested indexing (e.g., `a.b.c`).
- [ ] [Must] Profile completion performance on large class hierarchies.
