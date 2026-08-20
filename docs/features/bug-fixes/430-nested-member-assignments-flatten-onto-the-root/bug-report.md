---
id: "BUG-430"
title: "`a.b.c = v` makes `c` a member of `a` and leaves `a.b` empty, and only on the global door"
type: "bug"
parent_id: "BUG"
status: "done"
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

## Re-grounded 2026-08-14 — the exactness framing above is probably unnecessary

Probed on `fcd52465` with `Foo = {}; Foo.bar = {}; Foo.bar.baz = 1; Foo.direct = 2`:

```
indexExpr '.bar'   typeOf={ ... }  members=[]
Foo  (graph)                       members=[bar, baz, direct]
Foo  (graphTypeToLuaType)          members=[bar, baz, direct]
Foo.bar via graphTypeToLuaType = { }  members=[]
```

Two things follow, and they shrink this report.

**1. `Foo.bar` is empty because nothing ever constrains it — not because a write was lost.**
`baz` is on **`Foo`** (the hoist), so no `baz` demand was ever recorded against `bar`'s node on
*either* side. The Layer 3 story — "an exact table literal does not accrete later member writes" —
describes a mechanism that is real but has not yet been reached: there is no competing member to
accrete. Layer 1 is upstream of it.

**2. `isExact` has exactly ONE semantic consumer, and it never fires on the corpus.**
`grep` gives four plumbing sites (propagation of the flag) and one decision:
`LuaTypeGraph.kt:735`, `val isRequired = use.isExact && !isMethodOnClass`, gating the
`Missing required field '<key>'` diagnostic. That message is **absent from every corpus baseline**
(all four members). So the claim above that changing exactness "affects every table in every corpus"
is wrong: it affects one diagnostic the corpus never produces, plus three unit tests.

### The hypothesis this leaves — UNPROVEN, and it is task 1

Fix the hoist so `.baz` anchors on `bar` rather than on `Foo`, and `baz` becomes a **demand** on
`bar`'s member node. Enumeration would then still miss it, because `graphTypeToLuaType`
(`LuaTypes.kt:170`) reads `node.write` per member, while `typeOf` (`LuaTypes.kt:76-85`) already
merges a table's read-side members into its write-side ones. **That asymmetry is the candidate
second half of the fix** — and it plausibly explains why the reverted attempt "fixed defect 1 and
could not fix defect 2": with the hoist corrected, the member exists on the read side and the
enumeration path is the one place that does not look there.

**Do not build on this paragraph.** It is a reading of two functions plus one probe of the
*unfixed* state. Prove it by fixing the hoist alone and re-running the probe: if `.bar`'s node then
carries a `baz` demand, the second half is an enumeration change and this is a plan-bug-sized fix. If
it does not, the exactness route is back and the `plan-feature` recommendation stands.

## Re-measured 2026-08-14 (implement-bug attempt) — the bug MOVED, and the plan above is stale

Running the parked reproduction as the first step, exactly as the strategy below says, refuted the
strategy below. **Only two of the four are red, and defect 1 is gone:**

```
testGrandchildIsNotAMemberOfTheRoot   PASS   <- the hoist is no longer visible in completion
testRootKeepsItsOwnMembers           PASS
testAFlatTableKeepsItsMembers        PASS   <- the new control
testIntermediateTableCarriesItsMembers  FAIL — `Foo.bar.` offered: []
testPlainTableChainWithNoClassAnnotation FAIL — `Plain.mid.` offered: []
```

Note the failures are **`[]`, not "missing `baz`"** — a two-segment receiver offers *nothing at all*.
That is a different defect from "the intermediate table is empty", and COMP-09 is why: its index arm
now answers `Foo.` and correctly excludes `baz`, so the hoist still exists in the type graph (probed
above, `Foo` members `[bar, baz, direct]`) but no longer reaches the user. **Defect 1 is now a
type-graph-only defect with no user-visible symptom**; defect 2 is the whole live bug.

### The live root cause — two gaps, both in COMP-09's path, neither in the type engine

**G-1. The index never records a multi-segment receiver.**
`LuaReceiverMemberIndex.dottedTarget:368` is `target.varSuffixList.singleOrNull() ?: return null`, so
`Foo.bar.baz = 1` (suffixes `.bar`, `.baz`) contributes to **no** key — not `Foo`, which is correct,
and not `Foo.bar`, which is the bug. The KDoc calls this "the nested-qualifier rule, preserved from
`LuaTypeManagerImpl.memberNameOf`: a member has exactly one separator, so `a.b.c` contributes nothing
to `a`". True of `a`; false of `a.b`, for which `baz` is exactly a one-separator member.

**G-2. The completion arm only asks about bare names.**
`LuaCompletionContributor.addIndexedGlobalMembers:181` does `bareNameOf(receiverExpr) ?: return false`,
which is null for the index expression `Foo.bar`, so the arm declines and the graph arm answers
empty. Fixing G-1 alone changes nothing user-visible, because nothing would query the new key.

### Why this is not the small fix it looks like

Both gaps are in the enumeration path COMP-09 shipped **yesterday**, and that path carries explicit
constraints: design §4.5's first-declaring-file rule exists because DR-09 measured a flat
`membersOf(receiver, allScope)` returning a superset, and COMP-09-06's acceptance is "if any baseline
moves, stop". Keying the index on multi-segment receivers also grows the key space, which DR-19/§4.2
sized deliberately. This wants its own measured pass, and it is **the same area as [[BUG-439]]** —
which is about the sibling-*file* half of the same enumeration rule and is still unsettled. **Fix
them together or the second will re-open the first.**

Sizing stands at **M**, but the work moved out of the type engine and into COMP-09's index.

## FIXED 2026-08-20 — G-1 and G-2, and the PSI was not what G-2 assumed

The parked reproduction went into the source set first, as the strategy says, and reproduced the
re-measured state exactly: **2 of 5 red**, `Foo.bar.` and `Plain.mid.` both offering `[]`.

**[[BUG-439]] landing first is what made this safe.** This report says "the same area as BUG-439 …
Fix them together or the second will re-open the first". BUG-439 shipped the same morning
(`dc712238`), so the constraint was satisfiable rather than blocking, and all 35 `ReceiverMember` /
`CrossFileMemberEnumeration` / `MemberEnumeration` tests stayed green through this change.

- **G-1** — `dottedTarget` now keys a nested assignment under its real receiver: `Foo.bar.baz = 1`
  contributes `baz` to `Foo.bar` and, still, nothing to `Foo`. The rule it replaces was defended as
  "a member has exactly one separator, so `a.b.c` contributes nothing to `a`" — true of `a`, false of
  `a.b`. `getVersion` 3 → 4: new keys are new content.
- **G-2** — the completion arm reconstructs the dotted receiver. **The first cut of this was wrong,
  and only a probe found it.** It assumed `receiverExpr` at `Foo.bar.<caret>` was a `LuaVar` spanning
  `Foo.bar`; it is a bare `LuaNameRef` holding **only the trailing segment**:

  ```
  B430-PROBE receiverExpr=LuaNameRefImpl text='bar' dotted=bar
  B430-PROBE asking index for 'bar'   found=false
  ```

  So the arm was querying the index for `bar`, finding nothing, and declining to the graph arm —
  which answers empty. `qualifiedPrefixOf` walks up to the enclosing var and back down its suffixes,
  stopping at the one holding that name ref, which also excludes completion's dummy-identifier
  suffix by construction rather than by name.

### Corpus: no movement, and that is the expected answer here

4/4 green, **zero `IMPROVED` lines**. This fix adds keys without changing what any existing receiver
offers, so no inspection count can move. The key-space growth DR-19/§4.2 sized deliberately did not
disturb a measured figure. (`CorpusMetrics.kt:283-284`: MORE hits is the regression — read the
`IMPROVED` lines, a green ratchet means "nothing got worse".)

### Mutation proof — 2/2 CAUGHT, each without taking the control

| mutation | red |
| :-- | :-- |
| G-1 → restore `singleOrNull` | `testIntermediateTableCarriesItsMembers`, `testPlainTableChainWithNoClassAnnotation` |
| G-2 → stop reconstructing the prefix | the same two |

Both leave `testAFlatTableKeepsItsMembers` (the control) and `testGrandchildIsNotAMemberOfTheRoot`
green, which is what this report requires: a fix that emptied every table would pass the first two
assertions and fail the control. **Neither gap is sufficient alone** — each mutation alone restores
the whole defect, confirming the report's "fixing G-1 alone changes nothing user-visible".

### Still open: defect 1 in the type graph

The hoist is *not* fixed. `Foo` still carries `baz` in the type graph (probed at re-grounding), it
simply no longer reaches the user because COMP-09's index arm answers `Foo.` and correctly excludes
it. That is a type-graph-only defect with no user-visible symptom, and it is deliberately left: the
strategy's step 1 targets `LuaTypesVisitor.visitIndexExpr`, which this fix does not touch. Re-file it
if a consumer of the graph door starts to care.

## Fix strategy — SUPERSEDED by the section above for defect 2; kept for defect 1

Sequenced so the cheap measurement comes before the expensive commitment.

1. **Fix the hoist.** `LuaTypesVisitor.visitIndexExpr:1134` computes
   `firstNode(unwrapExpression(varElement.firstChild))` — the root `nameRef`, for *every* suffix.
   Anchor each suffix on its predecessor's member node instead. Previously measured to take `Foo.`
   from `[bar, baz, direct, qux]` to `[bar, direct]`, which is the correct set. The site's own
   comment already documents the hazard for the declaration-typed branch; the graph path was left
   with it.
2. **Re-run the probe** (task 1 above). This decides the rest of the strategy and costs one run.
3. **If `baz` lands as a demand on `bar`**: make member enumeration consult the read side, i.e. give
   `graphTypeToLuaType`'s per-member lookup the same write+read merge `typeOf` already performs.
   Scope it to the member-enumeration path; do **not** change `typeOf`, which is already correct.
4. **Only if it does not**: return to the exactness route — and note it is far cheaper than this
   report assumed, because `isExact` gates one diagnostic that no corpus member produces.

**Out of scope**: the dot/colon and cross-file selection rules ([[BUG-439]]), and any widening of
`membersOf(receiver, allScope)` — DR-09 measured that returning a superset, and COMP-09-06 forbids it.

## Test strategy

`LuaNestedMemberAssignmentTest.kt.txt` is parked beside this report: four completion-level tests,
three red on today's code. Move it into the source set as the first step of the fix, not the last —
it is already written and already proven to fail for the stated reason.

Add, because the parked set predates the re-grounding:

| test | asserts |
| :-- | :-- |
| `Foo.` offers `[bar, direct]` | the hoist is gone — `baz` must **not** appear on the root |
| `Foo.bar.` offers `[baz]` | the intermediate is populated — the half the reverted attempt could not deliver |
| a **control**: `Bar = {}; Bar.only = 1` still offers `[only]` | a fix that empties every table passes both tests above |

Mutation-proof each: reverting the hoist anchor must take the first red, and reverting the
enumeration change must take the second red **without** taking the first.

The corpus is the gate. Expect movement in completion and inlays as well as diagnostics; re-baseline
once and attribute each movement. `Missing required field` is absent from all four baselines today —
if it appears, the exactness route was touched inadvertently.
