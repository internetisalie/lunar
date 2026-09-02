---
id: "TYPE-13-PLAN"
title: "13: Implementation plan"
type: "plan"
parent_id: "TYPE-13"
folders:
  - "[[features/type/requirements|requirements]]"
---

# TYPE-13 Implementation Plan

The phases below are ordered; each is independently committable and each ends with a gate that can
fail. Every "Mutation" line names an edit to make, run the named test against, observe red, and
revert before committing.

Build and test only through the builder:
`tooling/gce-builder/gce-builder.sh run "test --rerun --no-build-cache"`.

## Phase 1 — Mint-site marking, with no behaviour change [Must]

**Goal:** every member node states whether it binds a declaration; nothing reads it yet.

1. Add `declaresMember` to `VariableNode` and `VariableElement`, and the defaulted parameter to
   `LuaTypeGraph.variable` (design §3.2).
2. Add the private `isAssignmentTarget(o: LuaIndexExpr)` to `LuaTypesVisitor` (design §3.3),
   verbatim — it enforces property (P), *no navigation step between the `var`'s head and the index
   expression*, keeping every clause it has: left-hand side (`parent is LuaVarList`), a **single**
   `varSuffix` (no preceding index step), and that suffix's `nameAndArgsList` **empty** (no
   preceding call step). Dropping either of the two step clauses admits a wrong declaration, and
   between them they cover every step kind `varSuffix ::= nameAndArgs* indexExpr` can produce — do
   not substitute a check derived from one Lua shape.
3. Pass `declaresMember` at each mint site in design §3.2's table: `true` at
   `LuaTypesVisitor.kt:754`, `:832`, `:848`; `isAssignmentTarget(o)` at `:1198`. Leave `:927` and
   `LuaGraphType.kt:391` at the default.

No `LuaTypeMember` change and no new enum: design §2 and §3.1 record why the member-level
discriminator was dropped, and §3.4 records that every nominal `sourceElement` site needs no edit.

**Verification:**
- New `src/test/kotlin/net/internetisalie/lunar/lang/types/Type13ProvenanceTest.kt`, a
  `BasePlatformTestCase`, asserting design §3.2's and §3.3's tables one fixture at a time. For each
  of `function t:m()`, `function t.m()`, `t.m = function() end`, `local t = { m = function() end }`,
  `t:m()` (call only), `print(t.m)`, `t.a.m = function() end` and `t().m = function() end`, the
  member node reached from the receiver's value type has the expected `declaresMember` and the
  expected `element` class and offset — the values recorded in `requirements.md` DR-05a and
  design §3.3.
  This is `requirements.md` case 2, and the first half of cases 7 and 16.
  **Mutation:** pass `true` unconditionally at `LuaTypesVisitor.kt:1198` → the `print(t.m)` row goes
  red. **Mutation:** drop the `singleOrNull` clause from `isAssignmentTarget` → the `t.a.m` row goes
  red. **Mutation:** drop the `nameAndArgsList.isEmpty()` clause → the `t().m` row goes red while the
  `t.m = function() end` control stays green. **Mutation:** drop `declaresMember = true` at `:848` →
  the `function t:m()` row goes red.
- `LuaAnnotatedClassDiagnosticsTest` unchanged (`TYPE-13-10`). This phase touches the snapshot path
  the test exercises, so a failure here means node identity or ordering moved and the phase is wrong.
- `LuaTypeGraphRootResolutionBudgetTest` unchanged (`TYPE-13-11`).
- Full suite green with `--rerun --no-build-cache`.
- `ktlintCheck` (run alone — never paired with `ktlintFormat`, per BUG-445).

## Phase 2 — The declaration lookup [Must]

**Goal:** a declaration dropped by either merge is recoverable, and the walk's bounds are pinned.

1. Add `LuaMemberDeclarations` with `declaringNodeOf` (design §4.2) and `declarationOf`
   (design §4.5).
2. No caller yet; the object is exercised only by its tests.

**Verification:** add `Type13DeclarationLookupTest` covering

- every `requirements.md` test case whose Requirement column is `TYPE-13-03`, `TYPE-13-04`,
  `TYPE-13-05`, `TYPE-13-07` or `TYPE-13-08` — the plain-table, global-table, `setmetatable`,
  demand-only, multi-suffix, call-suffix, cycle and cross-file fixtures — asserting the
  declaration's element class **and offset**, or null. Each row's mutation is named in that table; run each and observe
  red.
- case 4's **two handles**: resolve the global fixture from both the write-target `Obj` at offset 0
  and the call receiver at offset 30, asserting on each that `declarationOf` **is** the `LuaFuncDecl`
  at offset 9. The mutation for that row (no-op `declareFileGlobals`) is visible only on the first
  handle, and it shows up as that handle's member **disappearing** (`resolveMember` misses, its
  value type is `unknown`) — not as a member with a null declaration. Risks Gap 2.9 has the executed
  output.
- cases 11 and 12, which use a **hand-built `LuaTypeGraph`** rather than a Lua fixture:
  `LuaTypeGraph()` is public and `TestLuaTypeEngineSafety` already constructs nodes this way. Case 11
  wires two `declaresMember = false` variables into each other's `upSet` and asserts
  `declaringNodeOf` returns null (a test that hangs when the de-duplication guard is removed).
  Case 12 builds a 70-node non-declaring chain ending in a declaring node and asserts null, plus an
  8-node control that asserts the node is found. Design §4.2 properties 2 and 3 are what these pin;
  no Lua fixture reaches either bound.

Gap 2.4's read-before-assignment fixture (`local t = {}` ; `print(t.m)` ; `t.m = function() end`) and
Gap 2.7's factory / `self` / nested-constructor fixtures are asserted here as **no declaration**, so
each limitation is pinned rather than discovered later.

## Phase 3 — Populate the member [Must]

**Goal:** consumers get a declaration through the ordinary `LuaType` API.

1. Rewrite both `tableToLuaType` branches through the shared `putGraphMember` helper (design §4.3),
   including the nominal-preservation rule.
2. Preserve `sourceElement` in `LuaUnionType.resolveMember` and restructure `getMembers`'
   accumulator (design §4.4) — the existing `MutableMap<String, MutableSet<LuaType>>` holds no
   members and cannot carry the field.

**Verification:**
- Every `requirements.md` test case whose Requirement column is `TYPE-13-01`, `TYPE-13-02` or
  `TYPE-13-06`. Cases 9 and 10 resolve through the union's `LuaClassType` arm and are red before
  this phase and green after — at `3e151d4c` that arm resolves `setName` with `sourceElement = null`.
  Case 17 is the one that covers step 2, and it is the only case that reaches `LuaUnionType`
  itself; it needs step 1 in place to be non-vacuous, so land the two steps together and do not
  treat step 1's cases as covering step 2.
- **Cases 8, 9, 10 and 17 must each be alone in their own fixture project.**
  `LuaTypeManager.resolveType` searches the stub index project-wide, so a second file declaring the
  same class name binds an arm to the *other* file's declaration and the offsets these cases assert
  stop meaning what they say — measured on case 17, where a sibling fixture made arm `B` report an
  offset belonging to a different file while arm `A` coincidentally agreed. Give each case its own
  test method (`BasePlatformTestCase` builds a fresh project per method) and configure exactly one
  file in it. That the class names in use are disjoint today (`Builder` and `Box` against `A` and
  `B`) is coincidence, not a constraint: a later case reusing a name would bind to the wrong file
  with no visible failure.
- `LuaAnnotatedClassDiagnosticsTest` unchanged, and the **corpus ratchet** with `-PwithCorpus`:
  `LuaCorpusSweepTest`, which calls `CorpusGuards.assertRatchet(baselineFile, observed)` at
  [LuaCorpusSweepTest.kt:101](../../../../src/test/kotlin/net/internetisalie/lunar/corpus/LuaCorpusSweepTest.kt),
  must not fail with `Corpus regression:`
  ([CorpusGuards.kt:53-54](../../../../src/test/kotlin/net/internetisalie/lunar/corpus/CorpusGuards.kt)),
  whose message body lists `<key>: baseline <N> → observed <M>` lines from `CorpusMetrics.describe`.
  A printed `[corpus] IMPROVED (…)` line is not a failure; it means re-record with
  `-PrecordCorpusBaseline`. This phase is the one that could move inference if §4.3's `type`
  expression is altered, and the corpus lane is the only thing that would notice.
- Full suite green with `--rerun --no-build-cache`; `ktlintCheck`.

## Phase 4 — The conversion-path cost gate [Must]

**Goal:** `TYPE-13-11` has a gate that actually covers what this feature changed.

`LuaTypeGraphRootResolutionBudgetTest` and `LuaAnnotatedClassDiagnosticsTest` both stop at
`LuaTypesSnapshot.forFile`; neither calls `graphTypeToLuaType`, so neither can see Phase 3
(verified at `3e151d4c`: `grep -n "graphTypeToLuaType\|resolveMember\|getValueType"` over both files
returns nothing). Phases 1–3 must not be called covered by them alone.

1. Add a second test method to `LuaTypeGraphRootResolutionBudgetTest`, using the existing
   `annotatedCallSiteFixture()` (80 call sites). Locate the receiver as the **last** `LuaNameRef`
   whose text is `"b"` — the fixture's final line is `b:setName("a79")`, so that is the last call
   site's receiver:

   ```kotlin
   val receiverRef =
       PsiTreeUtil.findChildrenOfType(myFixture.file, LuaNameRef::class.java)
           .last { it.text == "b" }
   val converted = types.graphTypeToLuaType(types.getValueType(receiverRef))
   assertTrue((converted as LuaUnionType).types.any { it.resolveMember("setName") != null })
   ```

   `.last` matters: `local b = Builder` also contributes a `LuaNameRef` with text `"b"`, and it is
   the *first*. The arm-wise assertion matters too — measured at `3e151d4c`, this receiver converts
   to `LuaUnionType` named `{  } | Builder`, and `converted.resolveMember("setName")` is **null**,
   because `LuaUnionType.resolveMember` requires every arm and the bare `LuaTableLiteralType` arm
   does not carry the name (Gap 2.3). Asserting on the union directly would fail on correct code.
   The assertion is not decoration: it fails if the handle chosen carries no member node, which
   would make the budget numbers meaningless. Then assert `rootResolutionCount(WRITE)` and `(READ)`
   against new budgets.
2. Set those budgets the way the existing ones were set, which that file states: run it, take the
   measured counts, commit them with headroom, and record the measured figure in the KDoc so a later
   drift is visible.

**Verification:** **Mutation** (`requirements.md` case 15): replace the `upSet` walk in
`declaringNodeOf` with a read of `node.write` at each step → every member opens a resolution walk
root and the `WRITE` budget is exceeded. Observe red, revert.

## Phase 5 — Cross-file and chained [Should / Could]

**Goal:** close `TYPE-13-08` and decide `TYPE-13-09`.

1. `TYPE-13-08` is already asserted in Phase 2 as *resolves to a member, reports no declaration* —
   which is what `3e151d4c` does. If the implementer can make a `require`d module's declaration
   reach the receiver's node without changing a merge, do so and flip the assertion; otherwise leave
   it, and record the measurement in `risks-and-gaps.md`.
2. `TYPE-13-09` stays `Future Work`. DR-01 measured only the chain's **first** segment, and
   `AGENTS.md` records that `visitFuncCall` models `nameAndArgsList.firstOrNull()` only. Record the
   measurement for the second segment and stop — **do not** widen `visitFuncCall` here; that is a
   type-engine change with its own cost profile and belongs to [[TYPE-12]]'s neighbourhood.

**Verification:** full suite plus corpus, as Phase 3.

**Outcome (executed):** both questions were measured with a temporary probe (`temporary-edits`,
reverted — no production or test file carries the probe code) and both came back negative, so this
phase lands as documentation only: `risks-and-gaps.md` Gap 2.11 and Gap 2.12.

- `TYPE-13-08` is **not flipped**. `moduleType.getMembers()` is already empty by the time
  `LuaGraphType.fromLuaType`/`memberNodeFor` would run — `LuaTypeManagerImpl.getModuleType`'s AST
  fallback reads `getFileReturnType()`, which is `.write` alone rather than `typeOf`'s write/read
  merge, so there is no `sourceElement` for a mint-site fix to plumb through. The node that actually
  resolves `m` on the receiver (`Type13DeclarationLookupTest.crossFileRequireReportsNoDeclaration`'s
  `HIT`) is a same-file member-demand node the call site mints independently of `require`, and
  preferring a require-supplied node over it would mean changing `LuaGraphType.Table.getMembers()`'s
  local-over-super merge — the one merge this phase, like every phase before it, must not touch.
- `TYPE-13-09` stays `Future Work`, unattempted. The second segment's actual defect is stronger than
  "no declaration": `visitFuncCall` seeds the whole chain's value from the first segment's declared
  return and never visits the second, so `getValueType` on `x:m1():m2()` reports `B` — `m1()`'s
  return type — not `m2()`'s. Fixing it means modeling multiple `nameAndArgs` per `LuaFuncCall`
  inside `visitFuncCall`, which is exactly the widening the plan says belongs to [[TYPE-12]]'s
  neighbourhood, not here.

Since neither measurement changed production code, the corpus lane (`-PwithCorpus`) was not re-run
for this phase — Phase 3's clean run (24m49s) stands, and Phase 4 correctly skipped it for the same
reason. The full suite (`test --rerun --no-build-cache`, no `-PwithCorpus`) was run to confirm no
regression from the doc-only change; its result is recorded in `requirements.md`'s status update.

## Out of scope for every phase

- Changing `LuaTypesSnapshot.typeOf`'s write/read merge or `LuaGraphType.Table.getMembers()`'s
  supertype merge (design §8).
- Changing where a chain's suffixes are anchored (Gap 2.8), or widening `upSet` reach to the
  factory / `self` / nested-constructor shapes (Gap 2.7).
- Making `LuaUnionType.resolveMember` resolve a name only some arms carry (Gap 2.3).
- Removing redundant member demands ([[TYPE-12]]).
- The colon-method rename itself ([[REFACT-09]]).
- Minting a class name for an un-annotated receiver — DR-01 established nothing here needs one.

## Definition of done

- [x] Phases 1–4 complete; Phase 5 complete or its deferral recorded with a measurement.
- [x] Every `M` requirement except `TYPE-13-10` has a test whose named mutation was **executed** and
      observed red; `TYPE-13-10`'s argument stands as recorded in `requirements.md`.
- [x] Full suite green on the builder with `--rerun --no-build-cache`.
- [x] Corpus ratchet green with `-PwithCorpus` — no `Corpus regression:` failure; any
      `[corpus] IMPROVED` line is re-recorded rather than ignored.
- [x] `LuaAnnotatedClassDiagnosticsTest` unchanged; `LuaTypeGraphRootResolutionBudgetTest` unchanged
      in its existing method and passing in its new one.
- [x] `ktlintCheck` green, run on its own.
- [x] `Type13Dr01StructuralReachProbe`'s TYPE-13-05 tripwire — the assertion that fixture D's member
      node is still the call-site `LuaMethodExprImpl` — is **expected to keep passing**: this feature
      does not change which node wins the merge, only which declaration is reported alongside it. If
      it fails, a merge was changed and design §8 was violated.
- [x] `REFACT-01-08` is **not** touched — it flips to `Full` only when [[REFACT-09]] ships.
