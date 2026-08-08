---
id: "BUG-430"
title: "`a.b.c = v` makes `c` a member of `a` and leaves `a.b` empty, and only on the global door"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-430: nested member assignments flatten onto the root, and the two enumeration doors disagree

Found by COMP-09 DR-09 while measuring a prototype receiver-member index against today's enumeration
as a golden. Every receiver matched except one, and the mismatch turned out not to be a defect in the
prototype.

## Measured (2026-08-08, `CompNineDr09bTest`, indexed library fixture)

```lua
---@class Shapes
Shapes = {}

Shapes.nested = {}
Shapes.nested.deep = 1
Shapes.nested.alsoDeep = "s"
Shapes.direct = 2
```

```
resolveGlobal("Shapes") = LuaTableLiteralType
  members = [alsoDeep, deep, direct, nested]
  members["nested"] = Table(className=null, localMembers={}, superTypes=[], isExact=true, …)

resolveType("Shapes")   = LuaClassType
  members = [direct, nested]
```

The same shape without any `---@class`, so that only the global door can answer it:

```lua
Plain = {}
Plain.mid = {}
Plain.mid.leaf = 1
```

```
resolveGlobal("Plain") members = [leaf, mid]   -- `mid` is an empty table
```

## Three defects, all visible above

1. **Grandchildren are hoisted onto the root.** `Shapes.nested.deep = 1` makes `deep` a member of
   `Shapes`, so `Shapes.` completes `deep` — a member that does not exist at that path.
2. **The intermediate table is left empty.** `members["nested"]` has `localMembers={}`, so
   `Shapes.nested.` completes *nothing*, which is where `deep` and `alsoDeep` actually live. The two
   defects are complementary: every nested member is offered at exactly the one path where it is
   wrong and withheld from the one where it is right.
3. **The two enumeration doors disagree on the same receiver in the same file.** The `@class` door
   (`resolveType` → `materializeClass`) returns the correct `[direct, nested]`; the global door
   (`resolveGlobal` → `LuaTypesSnapshot`) returns the flattened set. Which one a caller gets depends
   on whether the receiver happens to carry a `---@class`.

`isExact=true` on the empty `nested` node compounds it: the table is asserted complete while being
demonstrably not.

## Confirmed in completion, not just in the type API (DR-12, 2026-08-08)

The measurements above are of `resolveGlobal`/`resolveType`. A second probe drove real completion
over the same shape, so the user-visible half is no longer inferred:

```lua
Foo = {} ; Foo.bar = {} ; Foo.bar.baz = 1 ; Foo.direct = 2
```

```
Foo.      offers [bar, baz, direct]     <- `baz` is offered where it does not exist
Foo.bar.  offers []                     <- and withheld where it does
```

Both halves of the defect are what a user sees. `Foo.bar.` offering **nothing** is arguably the worse
one: a table with two members completes as empty.

## Why it matters

`Config.db.host = …` is ordinary Lua, and both halves of the result are wrong in a user-visible way.
It also means **"behaviour-preserving" is not a well-defined bar for COMP-09** — there are two
goldens for the same receiver and one of them is this bug — which is why it is filed separately
rather than absorbed. COMP-09 must state which door it preserves; it cannot preserve both.

Not user-reported; found by measurement. Severity is medium rather than high because the flattened
name is an extra completion rather than a false diagnostic, and because the `@class`-annotated case —
which definition libraries all are — takes the correct door.

## Where to look

- `LuaTypesVisitor` / `LuaTypesSnapshot` — the global door, which produces the flattening. The
  member-write walk records the *last* suffix against the *root* receiver rather than descending.
- `LuaImplicitFields.singleFieldSuffixName` — the `@class` door's rule, which rejects `base.x.y`
  explicitly (`varSuffixList.singleOrNull()`) and is the behaviour the other door should match.
- `LuaTypeManagerImpl.memberNameOf:462-468` — the third copy of the rule, agreeing with
  `LuaImplicitFields` and not with the graph.

## Scope

Fixing 1 and 2 together is the real fix and they should not be separated: correcting the hoist
without populating the intermediate would take `deep` from wrong-place to nowhere. Whether the
resulting member set changes the corpus sweep baselines must be measured before the fix lands.
