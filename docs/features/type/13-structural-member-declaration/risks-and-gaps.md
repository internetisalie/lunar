---
id: "TYPE-13-RISKS"
title: "13: Risks and gaps"
type: "risk"
parent_id: "TYPE-13"
folders:
  - "[[features/type/requirements|requirements]]"
---

# TYPE-13 Risks and Gaps

## Risk 1.1 — A mint-site parameter reaches the whole engine [Low]

Design §3.2 adds a defaulted parameter to `LuaTypeGraph.variable`, which every node-creating site in
`LuaTypesVisitor` calls. The parameter is defaulted, and no production path reads it until Phase 3
wires `declaringNodeOf` into `tableToLuaType`, so Phase 1 is behaviour-neutral by construction — but
"by construction" has been wrong here before: BUG-473's Phase 2 measured that the fastest candidate
member-map change silently deleted the `---@param` violation on every method call while every suite
stayed green.

- **Mitigation:** `LuaAnnotatedClassDiagnosticsTest` pins the diagnostic multiset and runs at the end
  of Phase 1, which is the phase that touches the snapshot path it exercises. The corpus ratchet is
  the second gate at Phase 3. Neither is optional.
- **Measured, not argued:** the whole of design §3.2–§4.5 was built as a throwaway prototype at
  `3e151d4c` and the full unit suite run on the builder — 2 891 tests over 463 classes, 0 failures,
  0 errors — with `LuaAnnotatedClassDiagnosticsTest`, `LuaTypeGraphRootResolutionBudgetTest`,
  `MemberEnumerationGoldenTest` and `LuaCatsStubAstParityTest` unmodified. The corpus lane is opt-in
  and was **not** run against the prototype; Phase 3 owns it.
- **Why this is Low, and what would make it High:** the obvious place to fix a merge that drops the
  declaration is `LuaGraphType.Table.getMembers()` — and its consumers include the checker at
  [LuaTypeGraph.kt:763 and :850](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaTypeGraph.kt)
  and `handleSetMetatable`'s helpers
  ([LuaTypesVisitor.kt:117](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaTypesVisitor.kt)),
  which is the widest blast radius in the engine. DR-05 makes going there unnecessary: the
  declaration is reachable from the winning node's `upSet`, so no merge is touched and the checker
  sees the identical member map. An implementer who reaches for `getMembers()` instead has changed
  the risk class of this feature and should stop.

## Risk 1.2 — The `type` expression is the one thing that must not move [Medium]

Design §4.3's member construction is the only place inference could change, and it changes only by
an implementer substituting `declaringNode.write` for `node.write`. Nothing in the fixtures would
catch that substitution on its own: measured on the `setmetatable` fixture, the declaring node and
the winning node carry the **same** `write` object
(`Function(params=[], returns=[VariableElement@527c3886])`, `writeIdentity=1272757576` for both), and
both convert to `fun(): unknown`. Two nodes indistinguishable by type are exactly the case a unit
test cannot separate.

- **Mitigation:** the corpus ratchet at Phase 3, plus the requirement that §4.3's `type` argument be
  transcribed verbatim. This is why `requirements.md` records `TYPE-13-10` as having no
  fixture-level falsifying mutation rather than inventing one.

## Risk 1.3 — `LuaTypeMember` is a `data class` whose `sourceElement` starts moving [Closed]

Structural members' `sourceElement` goes from null to non-null, which changes `equals`/`hashCode`.
(The member-level discriminator that would have changed them further was removed — design §2.)

- **Measured at `3e151d4c`:** `grep -rn "LuaTypeMember" src/main/kotlin src/test/kotlin` finds no
  site that compares members by value — every use constructs, destructures, or reads one field.
  `copy` is called only by design §4.3 itself. Every reader of `sourceElement`, in `src/main` **and**
  `src/test`, and why none of them moves, is enumerated in design §5.1; the prototype suite run
  confirms it.
- **No action.** Recorded so a later reviewer does not have to re-derive it.

## Gap 2.1 — "Complete usage set" is undefined

`REFACT-09` needs to know that *every* call site of a resolved declaration is findable, which is a
stronger claim than resolving one. A declaration handle does not answer it, and this feature does not
deliver a "resolved, possibly incomplete" verdict — `requirements.md`'s Out of Scope says so and
there is no design section for one.

- **Owner:** `TYPE-13-00-DR-02`. It does not block this feature's design, only `REFACT-09`'s.

## Gap 2.2 — `setmetatable` OO: settled, not a gap

DR-01 showed the `setmetatable` merge returning the **call-site** node and did not show whether a
declaration node for `m` exists in that table at all. DR-05 answered it: it does.

Measured for `local Class = {}` ; `Class.__index = Class` ; `function Class:m() end` ;
`local o = setmetatable({}, Class)` ; `o:m()`, the receiver `o`'s value type carries **both** nodes:

```
WRITE: Table className=null keys=[] supers=1
WRITE.SUPER[0]: Table className=null keys=[__index, m]
    LOCAL['m'] = LuaFuncNameMethodImpl@53':m'
READ:  Table className=null keys=[m]
    LOCAL['m'] = LuaMethodExprImpl@97':m'
MERGED(getValueType): LOCAL['m'] = LuaMethodExprImpl@97   (supers=1, still carrying @53)
WINNER (getMembers) = LuaMethodExprImpl@97':m'
upSet closure of winner:
    up[0] LuaMethodExprImpl@97':m'
    up[1] LuaFuncNameMethodImpl@53':m'
    up[1] LuaFuncDeclImpl@39'function Class:m'
```

So there is a declaration to find, it is reachable from the winner at `upSet` depth 1, and design
§4.2 finds it without changing either merge — measured end to end against the prototype:
`declaringNodeOf` returns `LuaFuncNameMethodImpl@53` and `declarationOf` returns
`LuaFuncDeclImpl@39`. There is no branch in the plan for "no declaration node exists"; that case was
measured not to arise, and `handleSetMetatable`
([LuaTypesVisitor.kt:117](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaTypesVisitor.kt))
is not touched.

## Gap 2.3 — A union does not resolve a name only some arms carry

`AGENTS.md` records that a `---@class` local infers as a **union**, and DR-01 measured it:
`---@class Builder ; local Builder = {} ; local b = Builder` gives `b` the type
`Union[Table(className=null, localKeys=[]), Table(className=Builder, localKeys=[setName])]`, which
converts to `LuaUnionType("{  } | Builder")`. `LuaUnionType.resolveMember`
([LuaComplexTypes.kt:8-17](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaComplexTypes.kt))
returns null unless **every** arm resolves the name, and the bare `LuaTableLiteralType` arm does not
carry `setName` — so `b.resolveMember("setName")` is a MISS today and stays one after this feature.

- **`getMembers` does not share the all-arms rule.**
  [LuaComplexTypes.kt:19-29](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaComplexTypes.kt)
  *unions* the arms' member maps instead of intersecting them, so a name carried by a single arm is
  PRESENT in `getMembers()` on a union whose `resolveMember` misses it. The two methods are separate
  entry points into §4.4 with different reach, and conflating them understates which shapes exercise
  it — including the `---@class` local above, whose `getMembers()["setName"]` is PRESENT.
- **Consequence for this feature:** a `---@class`-local receiver is served by the nominal route
  (`LuaTypeManager.resolveType`, measured HIT with `sourceElement = LuaFuncDeclImpl@37`) or by the
  union's `LuaClassType` arm directly — which is why every `TYPE-13-06` fixture built on that shape
  resolves through those rather than through `LuaUnionType.resolveMember`. It is also why the
  Phase 4 budget method asserts on an arm rather than on the union.

**Which receiver shapes reach `LuaUnionType`, measured.** Executed at `09bd4b6f`, each fixture
**alone in its own project** (one `@Test` per shape: `LuaTypeManager.resolveType` searches the stub
index project-wide, so a sibling file declaring `A`/`B` binds an arm to the other file's
declaration). `A`/`B` are `---@class` locals in the same file, the handle is the call receiver's
`LuaNameRef`, and the member asked for is `m` (`setName` on the last row):

| receiver shape | value type of the receiver | `resolveMember` | `getMembers()[m]` |
| :-- | :-- | :-- | :-- |
| `---@alias U A\|B` then `---@type U` | `Undefined` → `LuaPrimitiveType'unknown'` | MISS | ABSENT |
| `---@param p A\|B` | `LuaUnionType'A \| B'` | **HIT** | **PRESENT** |
| `---@return A\|B` on a local function, result assigned | `LuaUnionType'A \| B'` | **HIT** | **PRESENT** |
| two `---@return` tags, result assigned | `LuaClassType'A'` — no union | HIT, not via a union | PRESENT, not via a union |
| `---@type A\|string` | `LuaUnionType'A \| string'` | MISS — the `string` arm | **PRESENT** |
| `---@type A\|A` | `LuaClassType'A'` — the union collapses | HIT, not via a union | PRESENT, not via a union |
| two-branch `if` assigning two annotated class locals | `LuaUnionType'{  } \| A \| B'` | MISS — the `{  }` arm | **PRESENT** |
| `---@type A\|B` where only `A` declares `m` | `LuaUnionType'A \| B'` | MISS — the `B` arm | **PRESENT** |
| `---@type A\|B` where both declare `m` (requirements case 17) | `LuaUnionType'A \| B'` | **HIT** | **PRESENT** |
| `---@class Builder` local (requirements case 9), member `setName` | `LuaUnionType'{  } \| Builder'` | MISS — the `{  }` arm | **PRESENT** |

Only the `---@alias` row types the receiver as `Undefined` and misses before any union is reached.
The two rows marked *not via a union* type as a bare `LuaClassType`: the `A|A` union collapses to a
single arm and the two `---@return` tags do not compose into one, so neither enters
`LuaUnionType` at all. Every other row does.

**Which of them can falsify §4.4, measured.** Executed at `09bd4b6f` with §4.3's
nominal-preservation guard applied, first with §4.4 applied and then with §4.4 alone reverted:

- **Through `resolveMember`:** the `---@param`, `---@return A|B` and case-17 rows — each reports
  `src=LuaFuncDeclImpl@25` with §4.4 and `src=null` without it.
- **Through `getMembers` as well:** those three, plus `---@type A|string`, the two-branch `if`,
  `---@type A|B` with only `A` declaring, and the `---@class Builder` local — the last reporting
  `getMembers()['setName'] src=LuaFuncDeclImpl@37` with §4.4 and `src=null` without it.
- **Not at all:** the `---@alias` row (no union), and the `A|A` / two-`---@return` rows, which keep
  `src=LuaFuncDeclImpl@25` under the §4.4 revert — that invariance is what shows they bypass it.

Nothing downstream moves: the extra reach is additional coverage §4.4 already has, not a shape any
requirement or acceptance row asserts differently. No requirements row's stated mutation becomes
reachable or unreachable — case 9 asserts on the union's `LuaClassType` **arm** (its mutation is
§4.3's guard, which the union is not on the path of) and case 17 already asserts both entry points.

- **Not this feature's to fix.** Changing the all-arms rule changes what the engine resolves, which
  is [[TYPE-09]]'s subject (union distribution logic). No roadmap row is minted here: the behaviour
  is pre-existing, documented in `AGENTS.md`, and every consumer already routes around it.

## Gap 2.4 — A read that textually precedes its assignment reports no declaration

Measured: for `local t = {}` ; `print(t.m)` ; `t.m = function() end`, the winning member node is the
**read**'s `LuaIndexExprImpl@20`, its `upSet` is empty, and `declaringNodeOf` returns null — so the
member resolves with `sourceElement = null` and no declaration, even though the file declares `t.m`.
Reversing the two lines makes the assignment target `LuaIndexExprImpl@14` the winner and the
declaration is found (`LuaAssignmentStatementImpl@13`).

- **This is the safe direction**, not a correctness defect: a consumer that mutates code refuses,
  exactly as it does for a genuinely undeclared member.
- **Action:** Phase 2 asserts this fixture as *no declaration*, so the limitation is pinned by a test
  rather than found by a user. Widening it would mean reaching across the separate use-tables the
  reads raise on `t`, which is a merge change and is out of scope by design §8.

## Gap 2.5 — The gate tests do not reach the code Phase 3 changes

`LuaTypeGraphRootResolutionBudgetTest` and `LuaAnnotatedClassDiagnosticsTest` both stop at
`LuaTypesSnapshot.forFile`. Verified at `3e151d4c`:
`grep -n "graphTypeToLuaType\|resolveMember\|getValueType"` over both files returns nothing, and
`tableToLuaType` is reached only from `LuaTypesSnapshot.graphTypeToLuaType`. They are real gates for
Phase 1 (the mint sites are in the snapshot path) and assert **nothing** about Phase 3.

- **Action:** Phase 4 adds the post-conversion budget method. Until it lands, "the budget test passes"
  must not be read as evidence about the conversion path.
- **Closed by Phase 4** —
  `conversionPathStaysWithinItsRootResolutionBudget` in `LuaTypeGraphRootResolutionBudgetTest` now
  calls `graphTypeToLuaType` and reaches `LuaMemberDeclarations.declaringNodeOf`. Measured:
  `WRITE` = 574, `READ` = 648 (budgets 620 / 700, ~8% headroom). See Gap 2.10 for what this new gate
  can and cannot catch: `requirements.md` case 15's prescribed mutation does not redden it on the
  80-call-site fixture, for a structural reason rather than a headroom one.

## Gap 2.10 — Case 15's mutation cannot redden the fixture it was written against

`requirements.md` case 15 prescribes: have `declaringNodeOf` read `node.write` at each step instead
of relying on `declaresMember` to short-circuit, expecting the `WRITE` budget to be exceeded.
Executed, on the annotated-call-site fixture, under three variants — (a) add an unconditional
`candidate.write` read ahead of the existing `declaresMember` check; (b) remove the `declaresMember`
short-circuit entirely and read `write` unconditionally at each step; (c) as (b), with the BFS left
free to walk the full `upSet` fan-out to the `MAX_VISITED = 64` cap. **All three reproduced
`WRITE` = 574 exactly — the mutation does not redden.**

- **Root cause, confirmed with a temporary visited-count probe (not committed):** on this fixture,
  `Table.localMembers["setName"]` **is** the `declaresMember = true` node itself — `declaringNodeOf`
  returns it at BFS step 0 without ever touching `upSet`. Variant (c) showed `upSet` from that same
  node fans out to 64+ nodes, but every one of them already has its `write` resolved in the
  `RootMemo` from the ordinary type-check pass `LuaTypesSnapshot.forFile` performs before the test
  ever calls `graphTypeToLuaType` — so a duplicate read of an already-memoized
  `(node, graph-revision)` key costs nothing, by the exact mechanism BUG-473's fix relies on.
- **Consequence:** the new budget method is a real gate for a regression that makes `declaringNodeOf`
  reach **previously-unresolved** nodes (a widened traversal into a colder part of the graph), but it
  cannot detect a regression that only re-reads nodes the initial type-check pass already warmed —
  which is exactly the shape case 15's literal mutation takes on this fixture. This is not a
  headroom problem; a tighter budget would not have caught it either, since the count genuinely does
  not move.
- **Action:** no roadmap row minted. A fixture whose `declaresMember` member sits `upSet` hops away
  from nodes the initial full-file pass never visits would be needed to exercise this mutation; none
  of TYPE-13's existing fixtures has that shape, and building one is not required by any `Must`
  requirement — `TYPE-13-11`'s budget still catches the class of regression BUG-473 itself was.

## Gap 2.6 — The DR-05 measurements live only in this document

`Type13Dr01StructuralReachProbe`
([src/test/kotlin/…/lang/types/Type13Dr01StructuralReachProbe.kt](../../../../src/test/kotlin/net/internetisalie/lunar/lang/types/Type13Dr01StructuralReachProbe.kt))
is committed and **does** assert: its `assertTrue`s pin DR-01's findings, including the TYPE-13-05
tripwire that fixture D's member node is still the call-site `LuaMethodExprImpl`. DR-05's probe and
the design prototype it drove were both throwaway and were deleted, so DR-05a's tables in
`requirements.md` are pinned to `3e151d4c` and nothing in the tree re-checks them.

- **Action:** Phase 1's `Type13ProvenanceTest` and Phase 2's `Type13DeclarationLookupTest` assert
  DR-05a's element classes and offsets directly, which converts the measurement into a gate. Until
  they land, treat DR-05a as a dated measurement rather than an invariant.

## Gap 2.7 — Ordinary un-annotated OO shapes that report no declaration

`upSet` reach is an emergent consequence of biunification edge propagation, not a characterised
property, so it must be read as the enumeration DR-05a gives rather than as a rule. The shapes
below are ones a reader might expect to be reachable and which are **not**, each measured at
`3e151d4c` with an empty `upSet` on the winning node:

| shape | member asked for | winning node | `upSet` |
| :-- | :-- | :-- | :-- |
| `local function make() local t={} function t:m() end return t end` ; `local o = make()` ; `o:m()` | `m` on `o` | `LuaMethodExprImpl@91` | empty |
| `local C = {}` ; `function C:b() end` ; `function C:a() self:b() end` | `b` on `self` | `LuaMethodExprImpl@51` | empty |
| `local t = { inner = { m = function() end } }` ; `t.inner.m()` | `m` on `t` | `LuaIndexExprImpl@52` | empty |

- **Why this matters more than the count suggests:** the factory-returned table and the `self`
  receiver are the dominant un-annotated OO forms in real Lua, and they were the implicit
  justification for treating Risk 1.1 as Low. That justification stands on DR-05a's *first* table —
  the shapes that do reach — and not on any general claim about reach.
- **No `Must` is falsified.** Each reports *no declaration*, which is the refusing direction, and a
  consumer that mutates code refuses. `REFACT-09` will therefore refuse a colon rename on a
  factory-built or `self`-receiver method, exactly as it does today.
- **Action:** Phase 2 asserts each of them as *no declaration*, so a later widening is a visible diff
  rather than a silent behaviour change. Widening reach for them means giving a call's return value
  the callee's member nodes, or giving the injected `self` node the receiver's — both are merge or
  visitor changes and out of scope by design §8. No roadmap row is minted: nothing regresses, and
  the work belongs with whichever consumer first needs those shapes.

## Gap 2.8 — Every suffix is anchored on the `var`'s bare head, and this feature only refuses them

`LuaTypesVisitor.kt:1189-1194` says in its own comment that the graph path anchors **every** suffix
of a `var` on that `var`'s bare head. So `t.a.m = function() end` mints a member `m` **on `t`**
(measured at `3e151d4c`: `t` resolves `m` to `LuaIndexExprImpl@25`), and so does
`t().m = function() end` (measured at `137a2a5a`: `LuaIndexExprImpl@16`). `M.sub.fn = function() end`
and `factory().fn = function() end` are both ordinary module shapes, so neither is a corner case.

- **What this feature does:** design §3.3's predicate enforces property (P) — no navigation step
  between the head and the index expression — so both members resolve with `sourceElement = null`
  and `declarationOf` returns null. Executed with the predicate reduced to its left-hand-side clause
  alone, `t.a.m`'s member reports `sourceElement = LuaIndexExprImpl@25` /
  `declarationOf = LuaAssignmentStatementImpl@22` (`requirements.md` case 7); executed with the
  call-step clause alone removed, `t().m`'s reports `LuaIndexExprImpl@16` /
  `LuaAssignmentStatementImpl@13` (case 16). Each is a statement declaring a member of something
  else offered as the declaration of `t.m`, which is the half-rename this feature exists to prevent.
- **What this feature does not do:** the *reach* defect is untouched — `t` still carries a bogus
  member `m` in both shapes, and a consumer enumerating `t`'s members still sees it. That is
  pre-existing behaviour of the graph path and changing it means changing where suffixes anchor,
  which is a visitor change with the member-map blast radius Risk 1.1 describes.
- **Why the predicate is stated as a property rather than as a shape list.** A check derived from
  one observed bad shape tests one step kind and leaves the other open: a suffix *count* bounds the
  index steps and is silent about the call steps `varSuffix ::= nameAndArgs* indexExpr` allows inside
  a single suffix. Property (P) plus the grammar's step alphabet — index and call — is what makes the predicate
  closed — a future shape is a sequence of those two kinds and is already refused. Any later edit to
  `isAssignmentTarget` must re-derive from (P), not add a clause per shape.
- **Action:** pinned by a Phase 1 assertion (the `declaresMember` value on every index expression in
  both fixtures) and a Phase 2 assertion (no declaration). No roadmap row: nothing regresses, and
  the anchoring belongs to whichever feature next needs `t.a.m` to type correctly.

## Gap 2.9 — The global receiver shape has exactly one measured discriminator

`requirements.md` case 4 pins the global-table shape. Its falsifying mutation must separate it from
case 3's local-table fixture, and the shapes share almost the whole path: each reaches the
declaration through `scope.lookup` → a shared `VariableNode` → the `funcNameMethod` mint. Executed at
`3e151d4c`:

| candidate mutation | effect on the global fixture | discriminating? |
| :-- | :-- | :-- |
| `globalNode` mints a fresh node per call instead of memoising (`LuaTypesVisitor.kt:41`) | still resolves; `scope.declare` already shares the node | no |
| `declareFileGlobals` a no-op, asserting on the **call receiver** | still resolves through the `:823` fallback's shared node | no |
| `declareFileGlobals` a no-op, asserting on the **write-target `Obj` at offset 0** | that handle's value type collapses to `Undefined`/`unknown`, so `resolveMember("m")` **misses** — no member, no `sourceElement`, no `declarationOf` (`resolveMember=MISS`); unmutated the same handle is `HIT src=LuaFuncNameMethod@21 declarationOf=LuaFuncDecl@9` | **yes** |

- **Re-executed at `137a2a5a`** against the prototype, with the mutation applied and removed:
  mutated `CASE4 Obj@0 … resolveMember=MISS src=null declarationOf=null` /
  `CASE4 Obj@30 … resolveMember=HIT src=LuaFuncNameMethodImpl@21 declarationOf=LuaFuncDeclImpl@9`;
  unmutated both handles report the `HIT` line.
- **Consequence:** case 4 must resolve from *both* handles, and only the offset-0 assertion carries
  the falsifier. A test written against the call receiver alone would be a duplicate of case 3
  wearing a different fixture. The mutated `Obj@0` handle stops resolving `m` **at all**, so case 4's
  assertion must be that `declarationOf` from `Obj@0` *is* the `LuaFuncDecl` — a `MISS` fails that;
  an assertion phrased as "`declarationOf` is null-or-the-decl" would survive the mutation.
- **Recorded because it is easy to lose:** an implementer simplifying case 4 to "same as case 3, but
  global" deletes the only coverage the global shape has.

## Gap 2.11 — TYPE-13-08 stays refused: `moduleType` carries zero members before `fromLuaType` ever runs

`implementation-plan.md` Phase 5 asks whether a `require`d module's declaration can be made to reach
the receiver's node **without changing a merge**. Executed with a temporary probe (`temporary-edits`,
reverted; no production or test file carries this code), on
`Type13DeclarationLookupTest`'s own case-14 fixture — `mod.lua` = `local M = {} ; function M:m() end ;
return M`, `a.lua` = `local M = require('mod') ; M:m()`:

```
moduleType class=LuaTableLiteralType value={  }
member m sourceElement=null class=null
```

`LuaTypeManagerImpl.getModuleType`
([LuaTypeManagerImpl.kt:272-284](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaTypeManagerImpl.kt))
takes the stub fast path first (`psiFile.stub?.exportedTypeString`), which is null here — this
fixture's `return M` has no `---@type`/`---@class` tag for `extractExportedType`
([LuaFileElementType.kt:33-90](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/LuaFileElementType.kt))
to read, so it falls through to `LuaTypesSnapshot.forFile(psiFile).getFileReturnType()`. That accessor
([LuaTypesVisitor.kt:337-346](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaTypesVisitor.kt))
reads `rootReturnNodes.firstOrNull()?.write` **directly** — it does not go through
`LuaTypesSnapshot.typeOf`'s write/read merge that `getValueType` uses, and it is that merge (not `.write`
alone) that carries `m` on every DR-05a fixture that resolves it. So the member the require boundary
would need to carry across the graph is not on the `LuaType` `resolveModule` returns, before `putGraphMember`,
`fromLuaType` or `LuaGraphType.memberNodeFor` ([LuaGraphType.kt:385-396](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaGraphType.kt))
ever run.

- **Why `memberNodeFor` alone cannot fix this.** `memberNodeFor` is the drop point *if* `moduleType` had
  a member to give it — it mints a fresh `VariableNode` per member from `graph.firstNodeElement()`
  (this file's anchor) and takes only `member.type`, discarding `LuaTypeMember.sourceElement`
  entirely; plumbing `sourceElement` through would be a mint-site change, not a merge change, and
  would be in scope by the letter of the Phase 5 constraint. But it is moot here: there is no
  `sourceElement` arriving at `fromLuaType`'s `LuaTableLiteralType` branch
  ([LuaGraphType.kt:282-290](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaGraphType.kt))
  to plumb, because `moduleType.getMembers()` is already empty by the time it gets there.
- **Why the demand-node route (the thing that actually makes case 14 `HIT`) cannot reach a
  declaration either.** `M:m()` in `a.lua` independently mints its own member-demand node for `m`
  on `M` (`LuaTypesVisitor.kt:927`-area, left at the `declaresMember` default by design), and it is
  *this* node — not anything from `require` — that wins `LuaGraphType.Table.getMembers()`'s
  local-over-super merge and is what `Type13DeclarationLookupTest.crossFileRequireReportsNoDeclaration`
  finds as `resolveMember=HIT`. Its `upSet` is empty by construction (DR-05a already records this
  shape), and reaching into that merge to prefer a require-supplied node over the local demand is the
  one exact case `implementation-plan.md`'s out-of-scope list and design §8 forbid
  (`LuaGraphType.Table.getMembers()`'s supertype merge).
- **Root cause is one layer under TYPE-13, not inside it.** `getFileReturnType`'s `.write`-only read is
  a pre-existing gap in the require/module-export path — nothing this feature's Phases 1-4 touch — and
  fixing it (routing it through `typeOf`'s merge, or something equivalent) is a change to how a file's
  exported type is computed generally, well outside a "cross-file declaration reach" feature and with
  its own cost/correctness profile (every `require` caller project-wide, not just colon calls).
- **Action:** the assertion is **not flipped**. `TYPE-13-08`'s existing behaviour — *resolves to a
  member, reports no declaration* — is correct and is now backed by an executed measurement of why,
  not just by the DR-05a table. No roadmap row minted for the `getFileReturnType` gap: it is not a
  regression, and TYPE-13-08's `Should` priority does not license reaching into module-export
  computation to close it.

## Gap 2.12 — TYPE-13-09's second segment: `visitFuncCall` reports the wrong VALUE, not merely no declaration

`implementation-plan.md` Phase 5 asks for a measurement of the chain's **second** segment (DR-01
measured only the first). Executed with a temporary probe (reverted), on the annotated chain
`---@class B ; local B = {} ; ---@return B ; function B:m1() end ; function B:m2() end ; local x = B ;
x:m1():m2()` — the same shape as `requirements.md` DR-01 fixture E, extended with the trailing
`:m2()` call:

```
nameAndArgsCount=2
chainValue(x:m1():m2()) = Union(Table(className=B, localMembers={m1, m2}), Table(className=B, localMembers={m1, m2}))
chainLuaType = B
m2 via resolveMember on the chain's OWN reported value = HIT, sourceElement=FUNC_DECL
```

`x:m1():m2()` parses as **one** `LuaFuncCall` PSI node with `varOrExp = x` and a two-element
`nameAndArgsList` (`funcCall ::= varOrExp nameAndArgs+`, `lua.bnf:297`) — there is no second PSI node
for the `:m2()` step. `visitFuncCall` reads only `o.nameAndArgsList.firstOrNull()`
([LuaTypesVisitor.kt:893](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaTypesVisitor.kt))
and, because `m1` is declared-typed (`---@return B`), takes the declared-type branch, seeds
`elementNodes[o]` from `m1`'s declared return (`B`) and **returns** — `:m2()` is never visited, so
`elementNodes[o]` is never touched again.

- **The consequence is stronger than "no declaration": the whole chain expression reports the wrong
  type.** `getValueType(theWholeFuncCall)` returns `B` — the type of `x:m1()`, not of `x:m1():m2()`.
  Asking `resolveMember("m2")` on that value *does* hit, but only because `B` happens to declare `m2`
  as a member of itself; the hit is a member of the **receiver**, not a return value of calling it, and
  a differently-typed `m2` (or a `B` with no `m2` at all) would silently report `m1`'s own member set
  in its place. This is a coincidence of the fixture, not a resolution path a consumer can rely on.
- **There is no PSI handle for "the value of `x:m1()`" to resolve `m2` against**, because chained
  segments share one `LuaFuncCall` node — `implementation-plan.md`'s routing note ("the nominal route
  already carries the declaration") is only true for the **first** segment; there is nothing analogous
  for the second, and building one means minting a node per intermediate `nameAndArgs` step inside
  `visitFuncCall` — the exact widening `implementation-plan.md` and `AGENTS.md` both say not to do
  here.
- **Action:** `TYPE-13-09` stays `Future Work`. No fix attempted, per the plan's explicit instruction.
  A future implementer picking this up should route through `visitFuncCall`'s per-segment modeling
  (TYPE-12's neighbourhood, per the plan) rather than trying to patch this feature's declaration
  lookup around it — the gap is upstream of any node this feature's `declaringNodeOf` ever sees.

## De-risking actions

| ID | Question | Blocks | Status |
| :-- | :-- | :-- | :-- |
| `TYPE-13-00-DR-01` | Does the structural route reach the un-annotated shapes? | the design | **done** — see `requirements.md`; probe committed and asserting |
| `TYPE-13-00-DR-05` | When a merge drops the declaration node, is it reachable, and from where? | design §4 | **done** — see `requirements.md` DR-05a/b/c; two enumerated tables, measured against a working prototype |
| `TYPE-13-00-DR-02` | What is a complete usage set, and is it computable without a project scan? | [[REFACT-09]] | todo |
