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

## Root cause — GROUNDED 2026-08-08, and it is bigger than this report's Scope assumed

An implement-bug attempt was made and **reverted**. It fixed defect 1 and could not fix defect 2, and
this report's own Scope section says they must not be separated. What it established:

### Layer 1 — the hoist (defect 1). Found, fixed, reverted with the rest.

`LuaTypesVisitor.visitIndexExpr` anchors every suffix on the **bare receiver**:

```kotlin
val varElement = PsiTreeUtil.getParentOfType(o, LuaVar::class.java)
val receiverNode = firstNode(unwrapExpression(varElement.firstChild))   // <- root nameRef, always
```

`varElement.firstChild` is `Foo` whether `o` is `.bar` or `.baz`, so `Foo.bar.baz = 1` constrains
`Foo` with a member `baz`. **The site already documents this**, in a comment guarding the
*declaration-typed* branch: "The graph path below anchors EVERY suffix on the bare receiver, so
letting a later suffix of a declaration-typed chain fall through would resolve `A.b.c` as `A.c`."
The hazard was known and guarded for annotated receivers; the ordinary graph path was left with it.

Anchoring each suffix on its predecessor removes the hoist — measured: `Foo.` went from
`[bar, baz, direct, qux]` to `[bar, direct]`.

### Layer 2 — one member node per occurrence. Tried, not sufficient.

`Foo.bar = {}` and `Foo.bar.baz = 1` each contain their own `.bar` index expression, and
`graph.variable(o)` mints a fresh node per occurrence, so the receiver carries two unrelated `bar`
members. Interning them per `(receiverNode, name)` did **not** make `Foo.bar.` non-empty.

### Layer 3 — the actual blocker: an exact table literal does not accrete later member writes.

`graph.addEdge(receiverNode, graph.use(o, tableConstraint))` records the member as a **use** — a
demand — not a write. Cross-file enumeration goes `resolveGlobal` → `LuaTypesSnapshot` → the member's
**written** type, and `Foo.bar = {}` writes `Table(localMembers = {}, isExact = true)`
(`LuaGraphType.kt:259`). The report's own opening measurement already showed this and it was read as a
symptom: `members["nested"] = Table(className=null, localMembers={}, superTypes=[], isExact=true)`.

So the fix is not a suffix-anchoring correction. It is: **a table whose literal is exact must still
widen when a later statement writes a new member into it.** That is core type-engine semantics —
exactness and accretion — and changing it affects every table in every corpus, not just nested
chains.

### Consequence for planning

This is **not an implement-bug-sized change**, and the "Scope" section above was written before the
root cause was known. It needs a `plan-feature`-grade pass with corpus measurement up front, because
the plausible fixes (drop `isExact` for a table that is later extended; make member constraints
contribute to the write side; unify member nodes across statements) each change assignability for
code that has nothing to do with this bug.

The reproduction is parked beside this report as `LuaNestedMemberAssignmentTest.kt.txt` — four
completion-level tests, three of which are red on today's code with exactly the output above. It is
**not** in the test source set, because a red suite is a broken gate for everything else.
