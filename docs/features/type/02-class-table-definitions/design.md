---
id: TYPE-02-DESIGN
title: "Technical Design"
type: design
parent_id: TYPE-02
priority: "high"
folders:
  - "[[features/type/02-class-table-definitions/requirements|requirements]]"
---

# Technical Design: TYPE-02 Class/Table Definitions

## 1. Architecture Overview

### Current State (most of this feature is already implemented)
`net.internetisalie.lunar.lang.psi.types.LuaTypeManagerImpl` already materializes structured
types:
- `resolveType(name, context)` (`:52`) resolves a name to a class then an alias, cached.
- `materializeClass(name, decls)` (`:135`) builds a `LuaClassType(name, superTypes, members)`:
  `@field`s from `stub.luacatsFields` (or the live `LuaCatsComment.getFieldTagList()`), with
  optional `name?` → `T | nil`; `@extends` / `: Parent` from `stub.luacatsExtends` /
  `getClassTagList().parentTypes` → `superTypes` (inheritance).
- `materializeAlias(name, decl)` builds the alias's target type.
- Indexes `LuaClassNameIndex` (`StubIndexKey<String, LuaLocalVarDecl>`) and `LuaAliasIndex`
  provide cross-file resolution. `LuaClassType.resolveMember` walks `superTypes`.

So **TYPE-02-01 (class), -02 (alias), -03 (inheritance), -04 (@field) are implemented (Full)**.
The remaining work is **TYPE-02-05 Implicit Field Discovery** (`ClassName.field = val` /
`self.field = val`), which is not modeled.

### Target State
Augment class materialization to also include **implicit fields** assigned to a class-typed
table, so `local p = {} ---@type Player; p.hp = 100` and `function Player:init() self.hp = 0 end`
contribute an `hp` member to `Player`.

## 2. Core Components

### 2.1 `LuaTypeManagerImpl.materializeClass` (modify) — TYPE-02-05
- After building `membersMap` from `@field`s, call `collectImplicitFields(name, decls, membersMap)`
  (§3.1) to add assignment-derived members (without overwriting explicit `@field`s).

### 2.2 `net.internetisalie.lunar.lang.psi.types.LuaImplicitFields` (new helper)
- **Key API**:
  ```kotlin
  object LuaImplicitFields {
      // adds <field> -> LuaTypeMember for `<className>.<field> = …` and `self.<field> = …`
      fun collect(className: String, classDecls: Collection<LuaLocalVarDecl>,
                  into: MutableMap<String, LuaTypeMember>)
  }
  ```

## 3. Algorithms

### 3.1 Implicit field collection (`LuaImplicitFields.collect`) — TYPE-02-05
- **Scope**: the containing files of the class's `classDecls` (where the class is defined;
  cross-file implicit fields are `TYPE-02-DR-01`).
- **Steps** for each `LuaAssignmentStatement` in those files:
  1. For each LHS `LuaVar` with exactly one `varSuffix` that is a `.field` index:
     - **Direct**: if the var's base `nameRef.text == className` → field name = the suffix name.
     - **Self**: if the base is `self` **and** the assignment is inside a method
       `function <className>:m()` / `function <className>.m()` (the enclosing `LuaFuncDecl`'s
       receiver name == `className`) → field name = the suffix name.
  2. If `into` does not already contain the field (explicit `@field` wins), add
     `into[field] = LuaTypeMember(field, lightInferType(rhs), sourceElement = theVar)`.
  - `lightInferType(rhs)`: **light syntactic RHS inference only** — map the RHS expression KIND
    directly to a type with NO graph / `resolveType` call: number literal → `NUMBER`, string
    literal → `STRING`, `true`/`false` → `BOOLEAN`, `nil` → `NIL`, table constructor → `TABLE`,
    function expr (`LuaFuncDef`) → `FUNCTION`; anything else → `ANY`. **Do NOT** use
    `LuaTypesVisitor.getTypes(...)` / the full type graph here: `collect` runs inside
    `materializeClass`, which runs inside `resolveType` during graph-building, so a graph/resolve
    call would re-enter `resolveType(sameClass)` (uncached mid-materialization → recursion) and
    `getTypes(sameFile)` mid-build. Precise RHS inference is deferred (`TYPE-02-DR-03`). A
    `resolvingTypes` ThreadLocal guard in `resolveType` (mirroring `resolvingModules`) defends
    against any transitive re-entry: after the cache check, `if (name in resolvingTypes) return null`,
    bracketing `doResolveType` with add/remove in a `finally`.
- **Edge handling**: multi-assignment `a.x, a.y = 1, 2` pairs each LHS var with its positional
  RHS; if RHS is shorter, extra fields get `ANY`.

## 4. External Data & Parsing
None — LuaCATS PSI + assignment PSI + the type graph.

## 5. Data Flow

### Example: explicit inheritance (requirements §5.1) — already works
`@class Player : Entity` + `@field name string` → `resolveType("Player")` →
`LuaClassType("Player", [Entity], {name})`; `resolveMember("id")` walks to `Entity` → `number`.

### Example: implicit field (TYPE-02-05)
```lua
---@class Player
local Player = {}
function Player:heal() self.hp = self.hp + 1 end
Player.maxHp = 100
```
`collect` adds `hp` (from `self.hp = …` in a `Player` method) and `maxHp` (from `Player.maxHp =`)
to `Player`'s members.

## 6. Edge Cases

| Case | Handling |
| :--- | :--- |
| Explicit `@field` and implicit assignment of the same name | explicit wins (not overwritten). |
| `self.x` outside a class method | no class context → skipped. |
| `ClassName.x.y = …` (nested) | only single-suffix `ClassName.field` handled in v1; nested deferred. |
| Cross-file implicit fields | only the class's defining files scanned in v1 (`TYPE-02-DR-01`). |

## 7. Integration Points
- No new `plugin.xml` registration — edits the existing `LuaTypeManagerImpl` (a
  `projectService`) and adds a helper. `LuaClassNameIndex`/`LuaAliasIndex` already registered.
- Implicit-field collection runs inside the cached `resolveType`/`materializeClass` path
  (cache keyed per `LuaTypeManagerImpl.typeCache`).

## 8. Requirement Coverage

| Requirement | Priority | Implemented by (section) |
|-------------|----------|--------------------------|
| TYPE-02-01 Class parsing | M | §1 (existing `materializeClass`) |
| TYPE-02-02 Alias parsing | M | §1 (existing `materializeAlias`) |
| TYPE-02-03 Inheritance | M | §1 (`superTypes` + `resolveMember`) |
| TYPE-02-04 @field | M | §1 (existing field collection) |
| TYPE-02-05 Implicit fields | S | §2.2, §3.1 |

## 9. Alternatives Considered
- **Implicit fields in `materializeClass` vs in `LuaTypesVisitor`**: collecting at
  materialization keeps the class type complete for all consumers (completion, hover,
  find-usages) and reuses the existing cache, rather than only affecting flow at one site.
- **Cross-file scan vs defining-file scan**: defining-file scan bounds cost; a project-wide
  implicit-field index is the optimization (`TYPE-02-DR-01`).

## 10. Open Questions

_None — feature has cleared the planning bar._
