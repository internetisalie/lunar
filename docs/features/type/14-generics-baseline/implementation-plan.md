---
id: "TYPE-14-PLAN"
title: "14: Implementation plan"
type: "plan"
parent_id: "TYPE-14"
folders:
  - "[[features/type/requirements|requirements]]"
---

# TYPE-14 Implementation Plan

Six phases, ordered so that each is independently committable, leaves the suite green, and ends
with a gate that can fail. Phase 1 is the smallest possible change that removes every false
diagnostic the DR-01 probe measured; it goes first and alone because it is also the phase most
likely to be wanted on its own if the rest slips. Every **Mutation** line names an edit to make,
run the named test against, observe red, and revert before committing — the requirement's test
case column says which fixture reaches it.

Build and test only through the builder:
`tooling/gce-builder/gce-builder.sh run "test --rerun --no-build-cache"`; the corpus lane is
`run "test --rerun --no-build-cache -PwithCorpus"`. Parser regeneration is local:
`.claude/skills/generate-parser/scripts/generate.sh` (Phase 4 only).

## Phase 1 — Generic-aware parsing and the safety net [Must]

**Goal:** every position of a type expression on a `@generic`-bearing comment produces a generic
placeholder, and a placeholder that reaches the checker never reports. No instantiation change yet.

1. `LuaCatsDeclarations.genericParameters(comment)` (design §4.2). Replace the two private copies in
   `LuaTypeGraphBridge` (three inject methods) and `LuaCatsDeclaredType.genericNames` with it —
   keeping `LuaCatsDeclaredType`'s `+ "self"` at its call site (design §4.2, §2's `self` row).
2. `LuaGenericType(name, bound, capture)` and `LuaGraphType.Generic(name, bound, capture)` (§3.1,
   §3.2), including `getMembers()` through the bound and `isAssignableTo`'s unconstrained-true arm.
   `fromLuaType` and `graphTypeToLuaType` map both fields (§4.11).
3. `TypeParser.parse(text, context, typeParameters)` (§4.1): thread the map through every private
   parser; the `namedType`, bare `typeParam` and new `parameterName` branches per §5.2.
4. `LuaTypeGraphBridge.resolveTypeWithGenerics` → §4.5 verbatim (the token fast path keeps today's
   null-skips for strings that mention no parameter); delete the `TODO` at line 43 and the
   duplicated "source and sink" branches.
5. `LuaTypeGraph.checkCompatibility` / `isCompatible`: the `Generic` arms of §5.7 (both the
   unbound and the bounded rules — the bounded rules are inert until Phase 3 produces a bound, but
   writing them once avoids revisiting the checker).

**Verification:**
- New `src/test/kotlin/net/internetisalie/lunar/lang/types/Type14GenericParsingTest.kt`
  (`IndexedBasePlatformTestCase`): requirements cases 1, 2, 3, 4.
  **Mutation** (case 1): drop `typeParameters` from the `TypeParser.parse` call in
  `resolveTypeWithGenerics` → `Array(Undefined)`. **Mutation** (case 2): move the generic check
  after `createTypeReference` → `Array(Table("T"))`. **Mutation** (case 3): delete the
  `checkCompatibility` arm → `string is not assignable to T` returns. **Mutation** (case 4):
  delete the `isCompatible` arm → the union-distribution error returns.
- The DR-01 fixtures B, C, D, F, G and I, each asserting `getErrors()` contains no message with a
  generic name — the whole "false error" column of requirements "What the engine does today".
- `UnionAndGenericTest`, `TestLuaTypeEngineSafety`, `LuaUnionDistributionTest`,
  `LuaTypeInlayHintsTest` unmodified and green (the TYPE-05 baseline is untouched).
- `LuaAnnotatedClassDiagnosticsTest` green; **corpus lane** run and re-baselined — the delta must
  contain only removed diagnostics whose message names a generic parameter (TYPE-14-17). A delta
  with any *added* diagnostic fails the phase.

## Phase 2 — Recursive instantiation, methods, and the declared path [Must]

**Goal:** binding through structure, substitution through structure, at both call paths.

1. `net.internetisalie.lunar.lang.psi.types.LuaGenericInstantiation` (§4.4): `containsGeneric`,
   `projectionsOf`, `projectAt` (§5.3, §5.4 — reuse `arrayElementType` for `Elem`), `instantiate`
   (§5.5) and `substitute` (§5.6, with the memoized `Table` materialization). Move
   `instantiationCache` here; delete `LuaTypeGraph.instantiateGeneric` / `doInstantiateGeneric`.
   Change `arrayElementType`, `positionsAreUnambiguous` and `carriesGraphNodes` in
   `LuaTypesVisitor` from `private` to `internal` and call them; do not copy them.
2. `LuaTypesVisitor.visitFuncCall` (§4.6): the same-file branch drops its `ValueNode` guard and
   reads the template through `functionTypeOf(calleeNode)` (first `Function` arm of a union); the
   declared branch gains the `containsGeneric` → `apply` route (§5.8), reusing
   `positionsAreUnambiguous` and `carriesGraphNodes`.
3. `LuaTypesVisitor` holds one `LuaGenericInstantiation(graph)`.

**Verification:**
- New `Type14InstantiationTest.kt`: cases 5, 6, 7, 8, 9, 10, 11.
  **Mutation** (5): stop descending into `Array` in `projectionsOf` → `undefined`.
  **Mutation** (6): return `type` unchanged for `Array` in `substitute` → `undefined[]`.
  **Mutation** (7): skip `Arg(i)` paths → `undefined`.
  **Mutation** (8): key the memo on `template` alone → the second call reads a union.
  **Mutation** (9): restore the `calleeNode is ValueNode` guard → `r` is `undefined`.
  **Mutation** (10): skip instantiation on the declared branch → both results `undefined`.
  **Mutation** (11): wire `use(callDemand)` against the seed's signature → the seed's parameter
  write becomes a union of the arguments.
- `ExpectedCallbackResolverTest`, `LuaColonCallInferenceWithdrawalTest`, `CrossFileInferenceTest`,
  `LuaTypeGraphRootResolutionBudgetTest` unmodified and green — the last one is the BUG-473 meter;
  a lazy node that mints during resolution would move it.
- Full suite and corpus lane green; the corpus delta contains only removed diagnostics (case 25).

## Phase 3 — Constraints, the bound in the body, and backtick capture [Should]

**Goal:** `T : Base` is checked at the call and usable in the body; `` `T` `` binds a named class.

1. `genericParameters` bound text → `TypeParser` (design §5.1 step 2) in `LuaTypeGraphBridge`'s
   map construction; `LuaGenericInstantiation.instantiate` step 1's declared demand on the
   binding node (§5.5).
2. Capture: `TypeParser`'s `parameterName` branch already lands in Phase 1; add `captures` to
   `Instantiated` and the literal-binding rule (§5.11) at both call paths.

**Verification:**
- New `Type14ConstraintTest.kt`: cases 19, 20, 21.
  **Mutation** (19): drop the declared-demand edge on the binding node → no error on `f(1)`.
  **Mutation** (20): return from the `Generic` arm without recursing into `bound` → `m` is
  `undefined`. **Mutation** (21): bind from the argument's type instead of its literal text → `w`
  is `string`.
- Completion on `p.` inside case 20's body offers `k` (a `LuaCompletionContributor` fixture, the
  existing `myFixture.completeBasic()` idiom).

## Phase 4 — The grammar: type arguments are full types [Must]

**Goal:** nested type arguments parse to structure; the bare-name shape is byte-identical.

1. `luacats.bnf` per design §6; run `generate.sh`; commit `src/main/gen` with the `.bnf`.
2. Update the one consumer §6 lists (`TypeParser.kt:86` → a document-order children walk). Make
   that edit **before** running `generate.sh`: the script compiles Kotlin first, and the walk
   compiles against both accessor sets. The two test files are not touched.
3. Re-run `generate.sh` over the committed tree and confirm `git diff src/main/gen` is empty.

**Verification:**
- `LuaCatsParserTest` gains cases 13 and 14's shapes (PSI dump assertions).
  **Mutation** (13): revert the rule → the argument list collapses and a `LuaCatsDescription`
  appears. **Mutation** (14): delete `private` from the rule → the tests risks-and-gaps "DR-02
  result, run a" lists go red (measured).
- `LuaCatsTypeDeclarationsTest`, `LuaCatsTypeReferenceTest`, `LuaCatsTypeRenameTest`,
  `LuaCatsLazyCommentTest`, `LuaCatsDocumentationRendererSectionsTest` green — REFACT-08's
  contracts and the renderer.
- Requirements case 7 (`table<K, V>` binding) now also passes with a nested argument
  (`table<string, Widget|nil>` bound to `V`).

## Phase 5 — Generic classes and parameterized parents [Must]

**Goal:** `Box<T>` is keyed `Box`, `Box<string>` is a real instantiation, `Kid : Base<string>`
inherits with substitution. Closes BUG-420.

1. `LuaCatsDeclarations.classHeadName` / `classTypeParameterNames` (§4.2, §5.10).
2. Stub fields and serialization (§3.5): `LuaFuncStub`/`LuaLocalFuncStub.luacatsGenericParams`,
   `LuaLocalVarStub.luacatsTypeParameters`, head-name `luacatsClassName`; `getStubVersion` → 5;
   `LuaCatsTypeNameIndex` key and version, **and** every site in design §5.10's re-matcher group
   (`LuaTypeManagerImpl.catsClassTags`, `LuaCatsTypeNavigation.processElements`,
   `LuaDocumentationTargetProvider`'s class lookup, `LuaCatsDocumentationRenderer.resolveBareClassComment`)
   compared by head, **and** every site in its AST-reader group (`LuaHierarchyUtil.className`,
   `LuaImportNameResolver.classNameOf`, `LuaTypeGraphBridge.injectTypeAnnotation`) reading
   `classHeadName`, **and** `LuaReceiverMemberIndex.indexClassFields`' key with its index version
   and the `classDeclarationLeaf` KDoc sentence it falsifies (§5.10's last group). Land the key and every group in one commit — DR-03 measured what the key
   alone does.
3. `LuaClassType` fields and the head-name assignability clause (§3.4);
   `isNominallyCompatible`'s `sameClass` (§4.7).
4. `LuaGenericSubstitution` (§4.3, §5.9); `LuaParameterizedType` delegation (§3.6);
   `fromLuaType`'s `LuaParameterizedType` arm (§4.11).
5. `LuaTypeManagerImpl`: `resolveParameterized` branch in `doResolveType`, `catsClassTags` head
   filter, `DeclaredParts.typeParameterNames`, `typeOrReference`, class parameters threaded into
   `funcTypeFromStub` (§4.8).
6. Close BUG-420: set its `bug-report.md` status to `done` with a pointer to this feature, and
   delete its roadmap row (the roadmap convention).

**Verification:**
- New `Type14GenericClassTest.kt`: cases 15, 16, 17, 18, 22.
  **Mutation** (15): read `argType.text` in `createStub` → key `Box<T>`. **Mutation** (16): return
  the base's members unsubstituted from `instantiate` → `v` is `undefined`. **Mutation** (17): drop
  the head comparison from `isNominallyCompatible` → `Box<string> is not assignable to Box`.
  **Mutation** (18): remove the parameterized branch from `doResolveType` → `[own]`.
  **Mutation** (22): treat `Table(className)` as a leaf in `substitute` → `Box<T>`.
- `LuaGotoContributorTest` gains a parameterized-class fixture: case 27. **Mutation**: leave
  `LuaCatsTypeNavigation.kt:48`'s raw-text match → `processElements("Box")` yields nothing.
- `LuaCatsStubAstParityTest` + generic function and generic class fixtures (cases 12 and 26,
  TYPE-14-06; case 12's `resolveGlobal` assertions live in the same file).
  **Mutation** (12): drop `luacatsGenericParams` from `LuaFuncStubImpl` serialization → the stub
  rendering loses `<T>` while the AST rendering keeps it.
- `MemberEnumerationGoldenTest` and the existing `LuaGotoContributorTest` methods green.
- Full suite and corpus lane green; the corpus delta removes diagnostics, and any addition is
  attributed in the re-baseline commit to a generic class that newly resolves (TYPE-14-17 b) — an
  unattributed addition fails the phase.

## Phase 6 — Callback-slot binding, display, docs [Could / Should]

**Goal:** the #1883 shape; inlay hints; the changelog.

1. `seedLambdaParams(lambda, expected, bindings)` and the call-site ordering (§5.12) — Could.
2. `LuaTypeInlayHintsTest` case 23 — Should (no code expected: `displayName` already renders the
   instantiated `className`; the test pins it).
3. `CHANGELOG.md` entry via the `changelog` skill; `docs/features/type/requirements.md` status
   block (TYPE-05's row 04 already points here).

**Verification:**
- `Type14InstantiationTest` case 24. **Mutation**: run `propagateExpectedLambdaParams` before the
  instantiation is built → `a` is `undefined`.
- `LuaTypeInlayHintsTest` case 23 (Phase 6 only; the class-level assertions are case 22 in Phase 5).
- [human-verification-checklists.md](human-verification-checklists.md) run in GoLand
  (`verify-in-ide`), results recorded there.

## Requirement → Phase Coverage

| Requirement | Priority | Delivered in |
|-------------|----------|--------------|
| TYPE-14-01 | M | Phase 1 |
| TYPE-14-02 | M | Phase 1 |
| TYPE-14-03 | M | Phase 2 |
| TYPE-14-04 | M | Phase 2 |
| TYPE-14-05 | M | Phase 2 |
| TYPE-14-06 | M | Phase 5 |
| TYPE-14-07 | M | Phase 4 |
| TYPE-14-08 | M | Phase 5 |
| TYPE-14-09 | M | Phase 5 |
| TYPE-14-10 | M | Phase 5 |
| TYPE-14-11 | S | Phase 3 |
| TYPE-14-12 | S | Phase 3 |
| TYPE-14-13 | S | Phase 3 |
| TYPE-14-14 | S | Phase 5 |
| TYPE-14-15 | S | Phase 6 |
| TYPE-14-16 | C | Phase 6 |
| TYPE-14-17 | M | every phase's gate; corpus lane at Phases 1, 2 and 5 |

## Verification Tasks

- [ ] `Type14GenericParsingTest` — cases 1–4 (Phase 1)
- [ ] DR-01 fixture regression file (`Type14FalseErrorRegressionTest`) — the "false error" rows (Phase 1)
- [ ] `Type14InstantiationTest` — cases 5–11, 24 (Phases 2, 6)
- [ ] `Type14ConstraintTest` — cases 19–21 (Phase 3)
- [ ] `LuaCatsParserTest` additions — cases 13, 14 (Phase 4)
- [ ] `Type14GenericClassTest` — cases 15–18, 22 (Phase 5)
- [ ] `LuaGotoContributorTest` addition — case 27 (Phase 5)
- [ ] `LuaCatsStubAstParityTest` additions — cases 12, 26 (Phase 5)
- [ ] `LuaTypeInlayHintsTest` addition — case 23 (Phase 6)
- [ ] Corpus lane re-baseline — case 25 (removed-only delta at Phases 1 and 2; attributed additions admitted at Phase 5)
- [ ] Run [human-verification-checklists.md](human-verification-checklists.md) (Phase 6)

## Task Summary

| Phase | Status | Priority |
|-------|--------|----------|
| Phase 1: Generic-aware parsing and the safety net | todo | Must |
| Phase 2: Recursive instantiation, methods, declared path | todo | Must |
| Phase 3: Constraints, bound in body, backtick capture | todo | Should |
| Phase 4: Grammar — type arguments are full types | todo | Must |
| Phase 5: Generic classes and parameterized parents | todo | Must |
| Phase 6: Callback-slot binding, display, docs | todo | Could / Should |
