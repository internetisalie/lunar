---
id: "TYPE-13"
title: "13: A structurally-resolved member carries no declaration"
type: "feature"
status: "planned"
priority: "medium"
parent_id: "TYPE"
folders:
  - "[[features/type/requirements|requirements]]"
---

# TYPE-13: A structurally-resolved member carries no declaration

## Overview

A colon call `obj:m()` can be resolved to the `function Obj:m()` that declares it **only when the
receiver carries a LuaCATS annotation**. `LuaGraphType.Table.className`
([LuaGraphType.kt:58](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaGraphType.kt))
is minted from a `---@class` tag and nothing else — `LuaLocalVarStubElementType` reads
`classTag?.argType?.text`
([LuaLocalVarStubElementType.kt:32](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/stubs/impl/LuaLocalVarStubElementType.kt))
and `LuaTypesVisitor.mergedTableOf` propagates it unchanged
([LuaTypesVisitor.kt:175](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaTypesVisitor.kt)).
A plain table, a global table, `setmetatable` OO and an un-annotated module therefore all present
`className = null`, and every consumer that keys on a class name misses them.

This feature makes a receiver's member resolvable **without** requiring a class name. It is the
enabling half of [[REFACT-09]]; it has consumers beyond rename (navigation, completion, the
SYNTAX-17 annotator, NAV-05/06) that today all share the same annotated-only reach.

## Why this exists as its own feature

`REFACT-01-08` shipped the dotted form and left the colon form refused by name
([LuaRenameProcessor.kt:111](../../../../src/main/kotlin/net/internetisalie/lunar/refactoring/rename/LuaRenameProcessor.kt),
`LuaDeclarationKind.METHOD_FUNCTION` → message `refactoring.rename.colonMethod`).
`REFACT-01-00-DR-03` measured why lifting that refusal is not one more step, and its finding is this
feature's problem statement, not REFACT-01's:

| receiver shape | nominal route reaches it | lifting the refusal today |
| :-- | :-- | :-- |
| `---@class` / `---@type` annotated, cross-file included | yes | renames correctly |
| plain table | no | **silently half-renames** |
| global table | no | **silently half-renames** |
| `setmetatable` OO | no | **silently half-renames** |
| second segment of `B:m1():m2()` | no | not renamed |

Corpus scale: **809 colon-method declarations across 734 files, 0 of which carry any `---@` tag.**
The annotated path is the exception in real Lua, not the rule.

## DR-01 result — executed, and it inverts DR-03's conclusion

`TYPE-13-00-DR-01` ran on the gce builder (`Type13Dr01StructuralReachProbe`, one fixture per
receiver shape, one colon call each). DR-03 measured the **nominal** route only —
`LuaTypeManager.resolveType(className).resolveMember(name)` — and concluded that un-annotated
receivers need class inference that does not exist. **That conclusion is route-specific and does not
hold for the structural route.**

| fixture | `className` | `resolveMember` | member node's PSI element | is that a declaration? |
| :-- | :-- | :-- | :-- | :-- |
| A `---@class` control | `Builder` | MISS (structural) / **HIT** (nominal) | `LuaFile@0` | no — nominal route carries `LuaFuncDeclImpl@37` |
| B plain local table | null | **HIT** | `LuaFuncNameMethodImpl@23` (`:m`) | **yes** — parent `LuaFuncNameImpl`, inside `function t:m()` |
| C global table | null | **HIT** | `LuaFuncNameMethodImpl@21` (`:m`) | **yes** |
| D `setmetatable` OO | null | **HIT** | `LuaMethodExprImpl@97` (`:m`) | **NO — that is the call site** |
| E chained receiver `x:m1():m2()`, resolving `m1` on `x` | `B` | MISS (structural) / **HIT** (nominal) | `LuaFile@0` | no — nominal carries `LuaFuncDeclImpl@38` |

The findings, in order of how much they change the feature:

**1. The un-annotated shapes already resolve.** B and C return a member whose graph node is the
`:m` of the declaration itself. Reaching the `LuaFuncDecl` from there is a `PsiTreeUtil` parent walk.
No class name is minted and none is needed. This feature is therefore **not** receiver
classification.

**2. What is dropped is the declaration, and the drop site is one expression.**
[LuaTypes.kt:178 and :185](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaTypes.kt)
build every structural member as `LuaTypeMember(name, graphTypeToLuaType(node.write, visited))` —
`sourceElement` takes its `null` default, although `node` is a `VariableNode` and
[`TypeNode.element: PsiElement`](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaTypeNodes.kt)
(`LuaTypeNodes.kt:12`) is right there. Every structural HIT above carries `sourceElement=null` for
that reason alone.

**3. Fixture D is why this feature cannot be that one expression.** `setmetatable` OO returns a HIT
whose element is the **call site**, not the declaration. Populating `sourceElement` from the winning
node would hand a renamer the call site and let it rename that one occurrence while reporting
success — **DR-03's half-rename, reintroduced by the fix for it.**

The nominal route stays as it is for A and E: it already carries a real `LuaFuncDecl`, and
`tableToLuaType`'s "graph members win" line overwrites nominal members with graph ones, so the
ordering there needs care rather than replacement.

## DR-05 result — which node the merges drop, and where the declaration still is

`TYPE-13-00-DR-05` was run to answer the question DR-01 left open: *when the winning node is not the
declaration, is the declaration reachable at all?* Two independent merges decide which node a
consumer sees, and either can drop the declaration:

- `LuaTypesSnapshot.typeOf`
  ([LuaTypes.kt:87-88](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaTypes.kt))
  merges `write.localMembers` then `read.localMembers` — **the read wins**.
- `LuaGraphType.Table.getMembers()`
  ([LuaGraphType.kt:200](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaGraphType.kt))
  merges `superTypes` then `localMembers` — **the local wins**.

### DR-05a — the shapes, measured end to end against a working prototype

The table below was produced by building design §3.2–§4.5 as a throwaway prototype at `3e151d4c`
(mint-site `declaresMember`, `LuaMemberDeclarations`, the rewritten `tableToLuaType`) and printing,
for each fixture, the winning member node, the node `declaringNodeOf` returns, and what
`declarationOf` derives from it. Every value below is transcribed from that run; nothing is
inferred.

**Shapes where a declaration is reported.** Design §4.2 records the measured size of
`declaringNodeOf`'s visited set for the fixtures where it was instrumented.

| fixture | winning member node | `declaresMember` of the winner | `declaringNodeOf` | `declarationOf` |
| :-- | :-- | :-- | :-- | :-- |
| `local t = {}` ; `function t:m() end` ; `t:m()` | `LuaFuncNameMethodImpl@23` | true | `LuaFuncNameMethodImpl@23` | `LuaFuncDeclImpl@13` |
| `Obj = {}` ; `function Obj:m() end` ; `Obj:m()` | `LuaFuncNameMethodImpl@21` | true | `LuaFuncNameMethodImpl@21` | `LuaFuncDeclImpl@9` |
| `local t = {}` ; `t.m = function() end` ; `t.m()` | `LuaIndexExprImpl@14` | true | `LuaIndexExprImpl@14` | `LuaAssignmentStatementImpl@13` |
| `local t = { m = function() end }` ; `t.m()` | `LuaIndexExprImpl@34` (the read) | false | `LuaFieldImpl@12` | `LuaFieldImpl@12` |
| `Class.__index = Class` ; `function Class:m() end` ; `o = setmetatable({}, Class)` ; `o:m()` | `LuaMethodExprImpl@97` (the call site) | false | `LuaFuncNameMethodImpl@53` | `LuaFuncDeclImpl@39` |
| `A.__index = B` ; `B.__index = A` ; `function B:m() end` ; `o:m()` | `LuaMethodExprImpl@104` | false | `LuaFuncNameMethodImpl@64` | `LuaFuncDeclImpl@54` |
| `---@class Builder` ; `function Builder:setName(n) end` ; `local b = Builder` ; `b:setName("x")` | `LuaFile@0` (a scratch-graph node — see below) | false | `LuaFuncNameMethodImpl@53` | superseded by the nominal element, design §4.3 |

**Shapes where no declaration is reported.** In each the winning node's `upSet` is empty, so the
walk returns null on its first step and the member resolves with `sourceElement = null`.

| fixture | member asked for | winning member node | why no declaration |
| :-- | :-- | :-- | :-- |
| `local t = {}` ; `t:m()` | `m` on `t` | `LuaMethodExprImpl@14` | nothing declares `m`; correct |
| `local t = {}` ; `print(t.m)` ; `t.m = function() end` | `m` on `t` | `LuaIndexExprImpl@20` (the read) | the read precedes the assignment — Gap 2.4 |
| `local M = require('mod')` ; `M:m()` | `m` on `M` | `LuaMethodExprImpl@27` | cross-file — `TYPE-13-08` |
| `local function make() local t={} function t:m() end return t end` ; `local o = make()` ; `o:m()` | `m` on `o` | `LuaMethodExprImpl@91` | the factory's return does not carry the member node — Gap 2.7 |
| `function C:b() end` ; `function C:a() self:b() end` | `b` on `self` | `LuaMethodExprImpl@51` | the injected `self` node carries no member edges — Gap 2.7 |
| `local t = { inner = { m = f } }` ; `t.inner.m()` | `m` on `t` | `LuaIndexExprImpl@52` | the suffix is anchored on the bare receiver — Gap 2.8 |
| `local t = {}` ; `t.a = {}` ; `t.a.m = function() end` ; `t.a.m()` | `m` on `t` | `LuaIndexExprImpl@25` | same anchoring; the mint-site predicate refuses it — Gap 2.8 |
| `local t = {}` ; `t().m = function() end` | `m` on `t` | `LuaIndexExprImpl@16` | the sole suffix carries a **call** step, so the member is on the call's result, not on `t`; the mint-site predicate refuses it — Gap 2.8 (measured at `137a2a5a`, with `Cfg = {}` ; `Cfg().m = …`, a parameter receiver and `t()().m = …` behaving identically) |

`upSet` reach is **not a rule and not a property anyone has characterised** — it is an emergent
consequence of biunification edge propagation. The tables above are the enumeration this
feature is designed against; each row is pinned by a fixture in `Type13ProvenanceTest` /
`Type13DeclarationLookupTest` (implementation plan Phases 1–2). A shape not in either table is
**unmeasured**, and the `declaresMember` default plus the empty-`upSet` outcome make an unmeasured
shape report *no declaration*, which is the refusing direction.

### DR-05b — the consequences that fix the design

**1. The declaration, where one is reported at all, is at `upSet` depth 1.** No merge is touched;
`declaringNodeOf` recovers it from the winning node.

**2. The node the engine mints already knows what it is.** Every member node is created at one of
the sites listed in `design.md` §3.2, and each site knows at mint time whether it is binding a
declaration or a use. Classifying a node afterwards from its PSI class is a second, weaker copy of
information the engine already has, and it is wrong for `LuaIndexExpr`, which is a declaration or a
use depending on its position.

### DR-05c — the annotated receiver's graph member is not an inert scratch node

For the `---@class Builder` fixture the graph member for `setName` is a `VariableElement` anchored
on `LuaFile@0` — the anchor `LuaGraphType.memberNodeFor`
([LuaGraphType.kt:391](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaGraphType.kt))
takes from `graph.firstNodeElement()`. Measured `upSet` closure of that node:

```
up[0] LuaFile@0                    VariableElement  declaresMember=false
up[1] LuaFile@0                    ValueElement
up[1] LuaFuncNameMethodImpl@53     VariableElement  declaresMember=true
up[1] LuaFuncDeclImpl@37           ValueElement
up[1] LuaFuncDeclImpl@37           VariableElement
up[1] LuaFuncDeclImpl@37           ValueElement
up[1] LuaMethodExprImpl@88         VariableElement  declaresMember=false
```

So `declaringNodeOf` on the annotated fixture returns `LuaFuncNameMethodImpl@53`, **not null**. A
guard conditioned on "the graph member has no declaration" never fires here, and the graph route
would replace the nominal route's `LuaFuncDeclImpl@37` with the coarser `:setName` name node. Design
§4.3's preservation rule is therefore unconditional on the nominal side: **where a nominal member is
already present, its element wins and the graph route supplies the type only.**

## Scope

### In Scope
- A single resolution entry point that maps (receiver expression, member name) to a
  `LuaTypeMember` carrying its **declaration** — or reporting explicitly that it has none — for the
  receiver shapes enumerated in DR-05a.
- A **mint-site declaration mark** on the graph node (`VariableNode.declaresMember`) so a member
  minted by a use site is never offered as a declaration.
- Cycle safety and a bounded walk over `upSet`.

### Out of Scope
- Renaming the colon form — that is [[REFACT-09]], which depends on this.
- **The completeness verdict** — "is *every* call site of this declaration findable?" is a stronger
  claim than resolving one, and it is `TYPE-13-00-DR-02`'s question, not this feature's. This
  feature delivers *resolved with a declaration* / *resolved without one*; there is no third state
  and no design section for one.
- The member-demand clique and its cost curve — that is [[TYPE-12]]; this feature must not
  regress it, and its verification gate says so.
- Making `LuaUnionType.resolveMember` resolve a name that only some arms carry (Gap 2.3).
- Widening `upSet` reach to the factory, `self` and nested-constructor shapes (Gap 2.7), or fixing
  the bare-receiver anchoring of a multi-suffix chain (Gap 2.8). Both are merge/visitor changes.
- Dynamic receivers (`_G["x"]`, a receiver assigned in a loop from a table of constructors) —
  out of reach for static resolution, consistent with `REFACT-01-19`/`-20`.

## Functional Requirements

| ID | Requirement | Priority | Status | Description |
|----|-------------|----------|--------|-------------|
| `TYPE-13-01` | **A structural member carries its declaration** | M | Not Implemented | `LuaTypeMember.sourceElement` is populated from the declaring node's `element` for every structurally-resolved member that has one, in both branches of `tableToLuaType`. |
| `TYPE-13-02` | **The declaration mark is established at the mint site** | M | Not Implemented | `VariableNode.declaresMember` is set where the member node is created and is never inferred from `TypeNode.element`'s PSI class. A member's answer to *do you have a declaration?* is `sourceElement != null`; there is no separate discriminator field. |
| `TYPE-13-03` | **A declaration-bearing member resolves to the declaration** | M | Not Implemented | For `local t = {} ; function t:m() end`, `LuaMemberDeclarations.declarationOf` returns the `LuaFuncDecl`, reached from the `LuaFuncNameMethod` node element. Same for a global table. |
| `TYPE-13-04` | **A member with no declaring node is never offered as a declaration** | M | Not Implemented | Where no declaring node is reachable, `sourceElement` is null and `declarationOf` returns null while `resolveMember` still returns the member — *no declaration* stays distinct from *no such member*. This includes a suffix mis-anchored on the bare head of its `var`, by an index step (`t.a.m = f`) or by a call step (`t().m = f`) — neither may declare `t.m`. |
| `TYPE-13-05` | **`setmetatable` OO resolves to its declaration** | M | Not Implemented | The declaration node lost by the `typeOf`/`getMembers` merges is recovered through the winning node's `upSet`; `o:m()` yields `function Class:m()`, never the `:m` of the call site. |
| `TYPE-13-06` | **The nominal route is not regressed** | M | Not Implemented | An annotated receiver still resolves through `LuaTypeManager.resolveType` with its existing `sourceElement`; `tableToLuaType`'s "graph members win" must not replace or blank a nominal member's declaration, on either the `@field` or the colon-method form. |
| `TYPE-13-07` | **Cycle and depth safety** | M | Not Implemented | `declaringNodeOf` visits each node at most once and stops at a node budget; a mutually-referential `upSet` terminates and a chain longer than the budget reports no declaration. |
| `TYPE-13-08` | **Cross-file receivers report honestly** | S | Not Implemented | A receiver from `require` of an un-annotated module either resolves with a declaration or resolves with none. Measured today: no declaration. |
| `TYPE-13-09` | **Chained receivers** | C | Future Work | The second segment of `B:m1():m2()`. DR-01 measured only the chain's first segment (`m1` on `x`), where the structural route returns `LuaFile@0` and the nominal route carries the declaration; the second segment is unmeasured and `AGENTS.md` records that `visitFuncCall` models `nameAndArgsList.firstOrNull()` only. |
| `TYPE-13-10` | **No inference change** | M | Not Implemented | Every diagnostic the engine reports before, it reports after — message-for-message, pinned by `LuaAnnotatedClassDiagnosticsTest`. |
| `TYPE-13-11` | **No cost regression** | M | Not Implemented | `LuaTypeGraphRootResolutionBudgetTest` passes at its committed values, extended to cover the conversion path it does not reach today; the corpus ratchet reports no `Corpus regression:` failure. |

## Behavior Rules

- **Failure is explicit and typed by nullability.** `TYPE-13-04` exists because the measured defect
  in this area is not wrong answers, it is *silent* wrong answers. A member with no declaration
  carries `sourceElement == null` and `declarationOf(member) == null`, and is still returned by
  `resolveMember` — *no declaration* and *no such member* are different answers.
- **The declaration mark is established where the node is minted, never inferred later.** A node
  whose mint site did not claim a declaration is not a declaration, and a mint site added later
  defaults to not claiming one. The default is the refusing direction.
- **A mis-anchored member is not a declaration.** The graph anchors every suffix of a `var` on that
  `var`'s bare head, so `t.a.m = f` and `t().m = f` both mint a member `m` **on `t`** while declaring
  a member of something else. The mint-site predicate must therefore refuse an index expression
  unless **no navigation step stands between the head and it** — design §3.3's property (P). The
  grammar gives a `var` exactly two step kinds, an index step and a call step
  (`varSuffix ::= nameAndArgs* indexExpr`), so the predicate must test **both**: counting suffixes
  alone leaves the call step inside the sole suffix untested, and `t().m = f` is one suffix.
- **Annotated receivers keep their current path, and their element.** This feature adds reach; it
  does not replace `LuaTypeManager.resolveType` for receivers that already work, and where a
  nominal member exists its `sourceElement` is authoritative over anything the graph route finds.

## Test Cases

Every row names the mutation that turns it red. Design §3.2–§4.5 was built as a throwaway prototype
at `3e151d4c` and run on the gce builder, so the "Then" column is transcribed output rather than
expectation, and the offsets are the ones that run printed on each row's own fixture. The rows
differ in how their falsifier was established, and each says which:

- **Mutation executed, and the changed value is quoted in the row** — rows marked **observed**. The
  edit was applied to the prototype, the fixture re-run, and the differing value recorded.
- **Falsifier established by measurement rather than by applying the edit** — rows 1, 11 and 12.
  Row 1's mutation is simply the pre-feature code, whose output (`sourceElement = null`) is measured
  at `3e151d4c` without the prototype. Rows 11 and 12 pin the walk's bounds and are argued from a
  direct measurement of the un-guarded and over-long cases, quoted in the rows.
- **Deferred to the phase that creates the gate** — row 15, whose budget Phase 4 sets and whose
  mutation Phase 4 runs.
- **Asserts an unchanged route, so its falsifier lives in another row** — row 8, falsified by row 9.

| # | Requirement | Given | When | Then | Mutation that turns it red (executed) |
|---|-------------|-------|------|------|---------------------------|
| 1 | `TYPE-13-01` | `local t = {}` ; `function t:m() end` ; `t:m()` | `resolveMember("m")` on the receiver's `LuaType` | `sourceElement` is `LuaFuncNameMethod@23` | restore the two-argument `LuaTypeMember(name, type)` at `LuaTypes.kt:178`/`:185` → `sourceElement` is null, which is what it is before this feature |
| 2 | `TYPE-13-02` | as #1, plus `local t = {}` ; `t:m()` and `local t = {}` ; `print(t.m)` | read `declaresMember` on the member node reached from the receiver's value type | `true` for `function t:m()`, `function t.m()`, `t.m = f` and a constructor field; `false` for a bare `t:m()` and for `print(t.m)` | pass `declaresMember = true` unconditionally at `LuaTypesVisitor.kt:1198` → the `print(t.m)` row reads `true`. And: drop `declaresMember = true` at the `funcNameMethod` mint → the `function t:m()` row reads `false` |
| 3 | `TYPE-13-03` | as #1 | `LuaMemberDeclarations.declarationOf(member)` | the `LuaFuncDecl` at offset 13 | delete the `LuaFuncNameMethod`/`LuaFuncNameProperty` branch of `declarationOf` → **observed** `LuaFuncNameMethodImpl@23` returned instead of `LuaFuncDeclImpl@13` |
| 4 | `TYPE-13-03` | `Obj = {}` ; `function Obj:m() end` ; `Obj:m()` | as #3, resolving from **both** the write-target `Obj` at offset 0 and the call receiver `Obj` at offset 30 | both yield the `LuaFuncDecl` at offset 9 | make `declareFileGlobals` ([LuaTypesVisitor.kt:358](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaTypesVisitor.kt)) a no-op → **observed** at `137a2a5a`: the `Obj@0` handle's value type collapses to `Undefined`/`unknown` and `resolveMember("m")` **misses entirely** (`resolveMember=MISS src=null declarationOf=null`), where unmutated it is `HIT src=LuaFuncNameMethod@21 declarationOf=LuaFuncDecl@9`. #3's local fixture is unchanged, and so is this fixture's *call receiver* `Obj@30`, which still returns `LuaFuncDecl@9`. This is the one mutation measured to separate the global shape from the local one; the candidates tried that did **not** separate them are recorded in risks Gap 2.9 |
| 5 | `TYPE-13-05` | `local Class = {}` ; `Class.__index = Class` ; `function Class:m() end` ; `local o = setmetatable({}, Class)` ; `o:m()` | as #3 | the `LuaFuncDecl` at offset 39 — **not** the `LuaMethodExpr` at 97 | drop `declaresMember = true` at the `funcNameMethod` mint → **observed** `declaringNodeOf` returns null and `sourceElement` is null |
| 6 | `TYPE-13-04` | `local t = {}` ; `t:m()` | `resolveMember("m")` then `declarationOf` | member is non-null, `sourceElement == null`, `declarationOf == null` | make `declaringNodeOf` return the start node when the walk finds nothing → **observed** `sourceElement = LuaMethodExprImpl@14` and `declarationOf = LuaMethodExprImpl@14` |
| 7 | `TYPE-13-04` | `local t = {}` ; `t.a = {}` ; `t.a.m = function() end` ; `t.a.m()` | `resolveMember("m")` on `t`, then `declarationOf` | member is non-null, `sourceElement == null`; and `resolveMember("a")` on `t` still yields `LuaIndexExpr@14` → `LuaAssignmentStatement@13` | drop the `singleOrNull` (single-`varSuffix`) clause from `isAssignmentTarget` (design §3.3) → **observed** `m` on `t` reports `sourceElement = LuaIndexExprImpl@25` and `declarationOf = LuaAssignmentStatementImpl@22`, i.e. the statement that declares `t.a.m` offered as the declaration of `t.m` |
| 8 | `TYPE-13-06` | `---@class Builder` ; `local Builder = {}` ; `function Builder:setName(n) end` ; `local b = Builder` ; `b:setName("x")` | `LuaTypeManagerImpl(project).resolveType("Builder", file)!!.resolveMember("setName")` | `sourceElement` is the `LuaFuncDecl` at offset 37 | this row asserts the pre-existing nominal route is untouched; its falsifier is #9, which is the route this feature changes |
| 9 | `TYPE-13-06` | as #8 | take the `LuaClassType` arm of the receiver's `LuaUnionType` and `resolveMember("setName")` | `sourceElement` is the `LuaFuncDecl` at offset 37 and `declarationOf` returns it | delete the nominal-preservation guard in `tableToLuaType` (design §4.3) → **observed** `sourceElement = LuaFuncNameMethodImpl@53`, the coarser name node, replacing the nominal `LuaFuncDeclImpl@37` |
| 10 | `TYPE-13-06` | `---@class Box` ; `---@field lid string` ; `local Box = {}` ; `function Box:open() end` ; `print(Box.lid)` ; `Box:open()` | as #9, for member `lid` | `sourceElement` is `LuaCatsFieldTag@17` | delete the same guard → **observed** `sourceElement = null`: the graph member for `lid` has no declaring node, so the nominal `@field` tag is blanked. #9 and #10 are the two halves of the guard — a nominal element that loses to a *worse* one, and a nominal element that loses to *nothing* |
| 11 | `TYPE-13-07` | a hand-built `LuaTypeGraph` with two `VariableNode`s, `declaresMember = false`, each in the other's `upSet` | `declaringNodeOf(a)` | returns null, and the call returns | remove `if (!visited.add(candidate)) continue` from `declaringNodeOf` → the frontier alternates forever and the test does not return. Corroborated at `137a2a5a` on the `A.__index = B` fixture, where the same closure repeats without bound: an un-guarded walk from the winning `LuaMethodExpr@104` reached a 5 000-step probe cap with its frontier still non-empty, having visited **5 distinct nodes** (`steps=5000 cap=5000 frontierStillNonEmpty=true distinctNodes=5`) |
| 12 | `TYPE-13-07` | a hand-built chain of 70 non-declaring `VariableNode`s ending in one with `declaresMember = true` | `declaringNodeOf(head)` | returns null — the budget is exhausted, and exhaustion refuses | raise `MAX_VISITED` above the chain length → the declaring node is returned. A companion chain of 8 returns it under both, which is what makes this row about the budget and not about the walk |
| 13 | `TYPE-13-07` | `local A = {}` ; `local B = {}` ; `A.__index = B` ; `B.__index = A` ; `function B:m() end` ; `local o = setmetatable({}, A)` ; `o:m()` | as #3 | terminates; the `LuaFuncDecl` at offset 54 | drop `declaresMember = true` at the `funcNameMethod` mint → **observed** `declaringNodeOf` returns null. Note this row is *reach* coverage through a cyclic supertype graph; the walk returns at depth 1 and so does **not** exercise the de-duplication guard — #11 does |
| 14 | `TYPE-13-08` | `mod.lua` = `local M = {}` ; `function M:m() end` ; `return M` — and `local M = require('mod')` ; `M:m()` | as #6 | member is non-null, `sourceElement == null`, `declarationOf == null`. **Executed** at `137a2a5a` on this fixture: `M@25 resolveMember=HIT src=null declarationOf=null` | as #6 — the start-node fallback makes `declarationOf` non-null here too |
| 15 | `TYPE-13-11` | the 80-call-site `annotatedCallSiteFixture()` already in `LuaTypeGraphRootResolutionBudgetTest` | build the snapshot, then convert the receiver's value type with `graphTypeToLuaType` | `RootAccessor.WRITE`/`READ` counts stay within the budgets Phase 4 measures and commits | have `declaringNodeOf` read `node.write` (instead of walking `upSet`) at each step → every member opens a walk root and the `WRITE` count exceeds its budget. Executed in Phase 4, against the budget that phase sets |
| 16 | `TYPE-13-04` | `local t = {}` ; `t().m = function() end` — plus `Cfg = {}` ; `Cfg().m = function() end`, `function g(p)` ; `  p().m = function() end` ; `  return p.m` ; `end`, and `local t = {}` ; `t()().m = function() end` | `resolveMember("m")` on the head receiver (`t` / `Cfg` / `p`), then `declarationOf` | every row: member is non-null, `sourceElement == null`, `declarationOf == null`. And the control `local t = {}` ; `t.m = function() end` still reports `sourceElement = LuaIndexExpr@14`, `declarationOf = LuaAssignmentStatement@13` | drop the `soleSuffix.nameAndArgsList.isEmpty()` clause from `isAssignmentTarget` (design §3.3) → **observed** at `137a2a5a`, from these fixtures: `t.m` reports `src=LuaIndexExpr@16 declarationOf=LuaAssignmentStatement@13`; `Cfg.m` `src=LuaIndexExpr@14 declarationOf=LuaAssignmentStatement@9`; `p.m` `src=LuaIndexExpr@19 declarationOf=LuaAssignmentStatement@16`; `t()().m` `src=LuaIndexExpr@18 declarationOf=LuaAssignmentStatement@13` — the statement that declares a member of the **call's result** offered as the declaration of `t.m`. The control row is unmoved by the mutation, which is what makes this row about the call step rather than about the predicate as a whole |
| 17 | `TYPE-13-06` | `---@class A` ; `local A = {}` ; `function A:m() end` ; `---@class B` ; `local B = {}` ; `function B:m() end` ; `---@type A\|B` ; `local u` ; `u:m()` | on the call receiver's `LuaNameRef` at offset 109, convert `getValueType(ref)` with `graphTypeToLuaType` — it is a `LuaUnionType` of `LuaClassType:A` and `LuaClassType:B` — then `resolveMember("m")`, and separately `getMembers()["m"]` | both report `sourceElement` = the `LuaFuncDecl` at offset 25, the `function A:m()` of the first arm | restore the two-argument `LuaTypeMember` in **both** `LuaUnionType.resolveMember` and `getMembers` (design §4.4), leaving §4.3 in place → **observed** at `3dd0aa34` `resolveMember('m') src=null` and `getMembers()['m'] src=null`, while the arms still report `LuaFuncDeclImpl@25` and `LuaFuncDeclImpl@69` — the union is the only thing that drops the field |

Case 17 is the only row that reaches `LuaUnionType` itself. Every detail below is load-bearing
rather than incidental, and each was measured at `3dd0aa34` on that fixture:

- **The handle is the `LuaNameRef` inside the receiver, not `LuaFuncCall.varOrExp`.** `varOrExp@109`
  types as `Undefined` and misses; the `LuaNameRef` at the same offset types as the union. This is
  the handle rule design §4.2's walk and the DR-01 probe already work under.
- **The first arm is `A` deterministically**, so `firstNotNullOfOrNull` has one answer:
  `LuaTypeAlgebra.canonicalize` sorts arms by `displayName()`
  ([LuaTypeAlgebra.kt:58-61](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaTypeAlgebra.kt)),
  and `graphTypeToLuaType`'s `map { … }.toSet()` preserves that order.
- **§4.3 is a precondition of this row, not a co-defendant.** Reverting §4.3 while keeping §4.4
  was executed too, and the arms themselves then report `arm[0] LuaClassType:A src=null`,
  `arm[1] LuaClassType:B src=null` — so the row would pass vacuously with §4.3 missing. The
  mutation therefore reverts §4.4 alone and leaves §4.3 standing, and the arm values quoted in the
  row are what separates the two sections.
- **The fixture must be alone in the project.** `LuaTypeManager.resolveType` searches the stub
  index project-wide, so a second fixture file declaring `A`/`B` resolves an arm to the *other*
  file's declaration. Measured: with sibling fixtures present, arm `B` reported an offset belonging
  to a different file while arm `A` coincidentally agreed.

Which receiver shapes reach a `LuaUnionType` at all, which of them `resolveMember` resolves, and
which are carried by `getMembers` alone, are enumerated with the measurement in risks Gap 2.3 — so
this row's fixture is not re-derived by the next reader. This row is not the only shape that can
falsify design §4.4; it is the one this feature asserts.

`TYPE-13-10` has **no fixture-level falsifying mutation, deliberately.** It is a no-change
requirement, and §4.3 computes a member's `type` from the same expression as today
(`graphTypeToLuaType(node.write, visited)`) — a diagnostic can only move if an implementer deviates
from that. Its gate is the existing `LuaAnnotatedClassDiagnosticsTest` plus the corpus ratchet, not
a new unit test, and this row records that as an argument rather than filling it with an assertion
that cannot fail. The measurements that support it: on the `setmetatable` fixture, where the declaration
node and the winning node differ, both carry the **identical** `write` object
(`Function(params=[], returns=[VariableElement@527c3886])`, `writeIdentity=1272757576` for both) and
both convert to `fun(): unknown`; and the prototype of §3.2–§4.5 ran the full unit suite on the
builder — **2 891 tests over 463 classes, 0 failures, 0 errors** at `3e151d4c` — with
`LuaAnnotatedClassDiagnosticsTest`, `MemberEnumerationGoldenTest`, `LuaCatsStubAstParityTest` and
`LuaTypeGraphRootResolutionBudgetTest` unmodified.

## Acceptance Criteria

- [ ] `TYPE-13-00-DR-01` and `TYPE-13-00-DR-05` have run and their results are recorded above.
- [ ] Every `M` requirement has an executed test case with a named, reachable mutation — except
      `TYPE-13-10`, whose argument is recorded in "Test Cases".
- [ ] `LuaAnnotatedClassDiagnosticsTest` passes unchanged (`TYPE-13-10`).
- [ ] The corpus ratchet is run with `-PwithCorpus` and does not fail with `Corpus regression:`
      ([CorpusGuards.kt:53-54](../../../../src/test/kotlin/net/internetisalie/lunar/corpus/CorpusGuards.kt);
      a regression line is formatted `<key>: baseline <N> → observed <M>` by `CorpusMetrics.describe`).
      Any `[corpus] IMPROVED (…)` line is printed, not failed, and means the baseline is re-recorded
      (`TYPE-13-10`, `TYPE-13-11`).
- [ ] `LuaTypeGraphRootResolutionBudgetTest` passes at its committed values, and its new
      post-conversion method passes at the budget measured when it lands (`TYPE-13-11`).

## Non-Functional Requirements

- **Cost.** [[TYPE-12]] records that growth is still ×5.9 per doubling after BUG-473's two phases.
  This feature must not add a per-member graph walk that opens a resolution root: `declaringNodeOf`
  reads `upSet` edges only and never touches `write`, `read` or `declaredDemand`.
- **Threading.** `declaringNodeOf` reads graph nodes and no PSI, so it inherits the caller's context.
  `declarationOf` is a `PsiTreeUtil` parent walk and requires a read action; it performs no I/O and
  takes no lock. [[BUG-473]] DR-7 established that the type engine runs off-EDT on the daemon path.
  The refactoring path is [[REFACT-09]]'s to establish — this feature adds no threading requirement
  beyond "call `declarationOf` inside a read action".

## De-risking

| ID | Question | Blocks | Status |
|----|----------|--------|--------|
| `TYPE-13-00-DR-01` | Does the **structural** route reach the plain-table, global-table and `setmetatable` shapes without any `className`? | the whole design | **done — see "DR-01 result".** B and C reach a real declaration node; D reaches the CALL SITE, which is the trap; A and E keep the nominal route. |
| `TYPE-13-00-DR-05` | When the winning node is not the declaration, is the declaration reachable at all — and from where? | design §4 | **done — see "DR-05 result".** Reachable at `upSet` depth 1 for the shapes in DR-05a's first table; the second table enumerates the measured shapes that report none. |
| `TYPE-13-00-DR-02` | What does a **complete** usage set mean operationally, and can it be computed without a whole-project scan? | [[REFACT-09]]'s refusal predicate | todo — does not block this feature's design, only REFACT-09's |

## Dependencies

- Enables [[REFACT-09]] (colon-method rename); `TYPE-13-00-DR-02` owns the completeness half of
  REFACT-09's refusal predicate.
- Must not regress [[TYPE-12]] or [[BUG-473]]'s committed budgets.
