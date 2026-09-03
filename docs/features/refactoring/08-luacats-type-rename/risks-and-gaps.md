---
id: "REFACT-08-RISKS"
title: "08: Risks & Gaps"
type: "risk"
parent_id: "REFACT-08"
folders:
  - "[[features/refactoring/08-luacats-type-rename/requirements|requirements]]"
---

# REFACT-08: Risks and Gaps

## Critical Risks

### Risk 1.1 — A rename that reaches most uses but not all [High]

- **Impact.** Every half-apply this subsystem has produced is **silent**:
  `LuaTypeManager.resolveType` returns null, `LuaCatsDeclaredType.isType` flips to false, the
  documented type is rendered as the first word of a description, and no annotator, inspection or
  balloon reports anything (`REFACT-01-00-DR-04` P9). A user discovers it by reading a doc popup.
- **Why it is the top risk here.** Every route tried so far produced exactly that, by a different
  mechanism each time. The index route rewrote the declaration and left 68 of 68 uses
  (`REFACT-01-00-DR-04` P4). The reference route without `design.md` §2.2 rewrote the declaration and
  left 11 of 11 (`REFACT-08-00-DR-02`, first pass). The **write path** did it a third way
  (`REFACT-08-00-DR-03`, mutation R): with the declaration set read over `allScope`, a name a library
  also declares moves in the project and stays in the library, splitting one type in two with no
  exception raised — see Risk 1.4. Three mechanisms, one wrong state, which is what makes the failure
  mode structural rather than incidental.
- **Mitigation**, in order of strength. (a) The acceptance criterion "no fixture ends holding
  both spellings" is asserted by counting, not by eye: every rename test asserts
  `staleSpellings == 0` as well as the new text. (b) The two shapes whose uses provably cannot be
  reached — a builtin-keyword name and a parameterized class head — are **refused**, not attempted
  (`REFACT-08-07`, `-09`), as is every name whose declarations cannot all be *written*
  (`REFACT-08-16`). (c) `REFACT-08-00-DR-01` measured the reach before the design existed, so the
  refusal set is derived from a census rather than from imagination. (d) Every refusal is asserted on
  both sides: a test that asserts a refusal also asserts that every file in the fixture is
  byte-identical, so a refusal cannot pass while a partial write has already happened — which is
  exactly how mutation R2 was caught.

### Risk 1.2 — `LuaCatsBaseElement.getReferences()` changes every LuaCATS element [Medium]

- **Impact.** `design.md` §2.2 is four lines and its blast radius is the whole LuaCATS PSI: every
  element now consults `ReferenceProvidersRegistry` on `getReferences()`, and `getReference()` stops
  being unconditionally null.
- **Likelihood of a surprise.** Measured rather than argued, in two full-suite passes at `154f26f3`
  with the DR-02 prototype in place. The first reported **2 954 tests, 1 failure, 1 skipped**, and
  the one failure was `LuaNamesValidatorTest.testRenameUtilReachesValidatorForLabel` — caused by the
  input validator's over-broad `getPattern()` (`design.md` §2.7), **not** by §2.2. With the narrowed
  pattern the second pass reported **2 960 tests, 0 failures, 0 errors, 1 skipped**; the delta against
  the unmodified suite is the throwaway probe classes' own methods. `REFACT-08-00-DR-03` re-ran the
  same gate over the design as remediated — the §3.11 write-scope refusal, the §3.4
  declaration-head guard and all four registrations — and reported **2 949 tests, 0 failures,
  0 errors, 1 skipped**.
- **Mitigation.** Phase 1's gate is the full suite, not the phase's own tests
  (`implementation-plan.md`). The two other registered contributors were checked by reading their
  patterns and their guards (`design.md` §2.2), and neither can match a LuaCATS element.
- **The corpus lane is green too**, and it is the gate the engineering contract names for a change
  that can move resolution or indexing. With the prototype in place,
  `test -PwithCorpus --rerun --no-build-cache` reported **BUILD SUCCESSFUL in 27m 34s** and
  **2 970 tests, 0 failures, 0 errors, 1 skipped**, with `LuaCorpusSweepTest`,
  `LuaTortureCorpusTest`, `LuaInspectionParityTest` and `LuaAnnotatedFixtureSweepTest` all present
  in `build/test-results/test/` at that run's timestamp.
- **Residual cost.** Every `getReferences()` on a LuaCATS element now runs the registry's pattern
  match, including `LuaRequireReferenceContributor`'s `PlatformPatterns.psiElement()` provider,
  which bails on `LuaRequireReference.moduleStringOf`. This is the same cost `LuaBaseElement`
  already pays for every Lua element.

### Risk 1.4 — The write set is not the resolution set, and the platform does not police it [High]

- **Impact.** `GlobalSearchScope.allScope` includes libraries, and Lunar attaches four
  `AdditionalLibraryRootsProvider`s (`plugin.xml:528-532`). Its own bundled runtime stubs declare
  **non-builtin** type names — `File`, `io`, `os`, `math`, `debug`, `package`, `coroutine`, `utf8`,
  `bit`, `struct`, `cjson`, `redis`, `server`, `void`, `self`, `_G`, ~30 `Rockspec*` — none of which
  `REFACT-08-07`'s builtin refusal covers. Reproduce with the `grep` in `design.md` §3.11.
- **Why the platform does not catch it.** `BaseRefactoringProcessor.ensureElementsWritable` gates on
  `usages[].getElement()` ∪ `getElementsToWrite(descriptor)`
  (`BaseRefactoringProcessor.java:424-438`), and `getElementsToWrite` returns
  `descriptor.getElements()` (`:842-844`) — for a rename, `RenameViewDescriptor`'s
  `myAllRenames.keySet()` (`RenameViewDescriptor.java:26-27`). A declaration leaf discovered inside
  `renameElement` is in neither set, so the writability gate passes and the write is attempted.
- **Measured, and it fails two different ways** (`REFACT-08-00-DR-03`, a project `---@class File`
  against the bundled `runtime/standard/lua-5.4/io.lua`): with the write scope at `allScope`,
  `IncorrectOperationException: Cannot modify a read-only file` **after** the use file was already
  rewritten; with the write scope narrowed but no refusal, no exception at all and one type silently
  split. The quiet one is the worse one.
- **Mitigation.** `design.md` §3.11: resolution keeps `allScope`, the rewrite writes only
  `projectScope`, and where the two differ the rename is **refused** with the offending file named
  (`REFACT-08-16`). TC-24 asserts the refusal and TC-25 is its control.
- **Residual.** A type name any attached library also declares is unrenameable, including the
  project's own declaration of it. §3.11 states that cost explicitly.

### Risk 1.3 — The reach measurement is against an out-of-repo checkout [Medium]

- **Impact.** `REFACT-08-00-DR-01`'s numbers come from `lua-language-server` 3.10.6 at `66141703`,
  a local clone. Nothing in the repository pins it, and a reviewer cannot reproduce the census
  without fetching it.
- **Why it was done anyway.** The alternative was no measurement at all: the pinned corpus carries
  **0** `---@` tags in 734 files and the in-repo annotated fixture is two synthetic files
  ([[MAINT-39]], [[BUG-473]] DR-6). The sibling failure this feature is explicitly avoiding —
  [[REFACT-09]]'s predicate accepting 0 of 941 real declarations — happened because reach was
  measured last.
- **Mitigation.** The census is reproducible from a stated commit with a stated file selection, and
  its headline numbers are cross-checked two ways: a raw text scan (`grep -cE '^\s*---+\s*@class'`
  → 191 against PSI's 188) and an independent `ReferencesSearch` count at scale (DR-02 P15 → 324
  references for `parser.object`, against DR-01's census of 324 uses). Gap 2.4 records what a pinned
  corpus would add.

## Design Gaps

### Gap 2.1 — Should renaming the type also rename its Lua host declaration?

- **Question.** In the common idiom `---@class Widget` above `local Widget = {}` the tag name and the
  local are two symbols with one spelling. `REFACT-01-00-DR-04` left this explicitly unanswered as
  "a requirement, and it is unwritten".
- **Answered: no**, and the answer is measured rather than chosen. `LuaLocalVarStubElementType`
  hoists `className` **from the tag**, not from the local's own name, so the two are independent in
  the engine. DR-02 P8 renamed the tag alone and observed
  `before=Widget afterOld=null afterNew=Gadget` with `local Widget = {}` unchanged — the class
  resolves under the new name and nothing dangles. Coupling them would introduce a *new* silent
  half-apply in the other direction, because `LuaRenameProcessor` renames the local through
  `LuaNameReference` and has no way to refuse when the tag rewrite fails.
- **What it costs the user.** After renaming the type, the tag and the local disagree in spelling.
  That is legal LuaCATS and reads oddly. `REFACT-08-11`'s conflict machinery is the natural place to
  add an *advisory* later; it is not a correctness gap.

### Gap 2.2 — `LuaCatsTypeNameIndex` keys a parameterized class under its whole text

- **Question.** `LuaCatsTypeNameIndex.Indexer.map` keys a `@class` under `argType.text.trim()`,
  which for `---@class Box<T>` is `Box<T>` rather than `Box`. Measured: 2 disagreements over 188
  class tags, both `table<K, V>`.
- **Leaning, and why the feature does not fix it.** Re-keying to the head name changes what
  `LuaTypeManagerImpl.materializeUnhostedClass` can resolve and requires a `getVersion()` bump, so
  it is a type-engine change wearing a rename feature's clothes. `REFACT-08-09` refuses the shape
  instead, and `design.md` §3.2 clause (a) is that refusal.
- **De-risked by.** `REFACT-08-00-DR-01`, which sized it at 2 of 188. File a separate bug if a real
  project is found where parameterized classes are common.

### Gap 2.3 — 87 occurrences sit in comment regions the parser did not structure

- **Question.** `REFACT-08-00-DR-01` measured 82 occurrences of a declared name under `DUMMY_BLOCK`
  and 5 directly under `LAZY_COMMENT` — recovery regions with no `LuaCatsNamedType` to carry a
  reference. They survive a rename.
- **Concentration.** Five files: `guide.lua` 56, `export.lua` 16, `debug.lua` 5, `ffi.lua` 2,
  `template.lua` 2 — i.e. it tracks the 19 of 195 files that hold a `PsiErrorElement`, not the
  dialect.
- **87 is a lower bound.** DR-01 excluded `lua-language-server`'s own `test/` tree, which is where
  its intentionally malformed Lua lives — i.e. exactly the files that generate this residue class. The
  exclusion is right for the parse-error count it was made for and biases *this* number downward, so
  a census over the whole checkout would report more than 87, not fewer.
- **Leaning.** Out of scope and **not** silently so: this is a LuaCATS *parser* gap, and closing it
  is a grammar change, not a rename change. A rename over a file the parser could not structure was
  never going to be complete.
- **De-risked by.** DR-01, which bounded it. If it grows, the honest response is a warning at rename
  time listing the unstructured comment regions in the affected files — not a wider rewrite.

### Gap 2.4 — There is no pinned annotated corpus to regression-test against

- **Question.** `REFACT-08-15` gates on the unit suite, which contains two synthetic annotated files.
  Nothing re-runs DR-01's census after the feature ships.
- **Leaning.** Accept, and depend on [[MAINT-39]] rather than duplicating it. MAINT-39 is `blocked`
  on [[BUG-473]] step 2, and its staging note is explicit that adding real annotated projects today
  produces a sweep that does not finish.
- **De-risked by.** Nothing in this feature. Recorded so a future reader does not read the absence
  as an oversight, and so that MAINT-39, when it lands, knows this feature wants it.

### Gap 2.5 — `LuaCatsGenericType` and `LuaCatsTypeParam` are each both a declaration slot and a use holder — CLOSED by DR-03 and DR-04

- **Question.** `genericType ::= NAME` is the head of `Foo<…>` wherever it appears — in a use
  (`---@type Widget<string>`) and in a parameterized **declaration** (`---@class Box<T>`). If a
  project declared both `---@class Box` and `---@class Box<T>`, would renaming `Box` rewrite the
  parameterized head as though it were a use?
- **Answered: it did, and the design now stops it.** `REFACT-08-00-DR-03` built the co-existence
  fixture the earlier leaning had left unmeasured. Before the guard: `references=2
  files={params.lua=1, uses.lua=1}` and `---@class Box<T>` was rewritten to `---@class Crate<T>` — a
  second type, keyed separately in `LuaCatsTypeNameIndex` under the whole text `Box<T>`, renamed
  without being asked. After the guard (`design.md` §3.4 `isDeclarationSlotHolder`, gating
  `useHolderOf`/`useLeafOf` and hence §2.4's provider): `references=1 files={uses.lua=1}` and
  `params.lua` byte-identical. TC-22 is the row; mutation N is its falsifier.
- **The same hazard exists one node down, and DR-03's guard did not reach it.** `typeParam ::= NAME`
  (`:203`) is reached from `parameterizedName` (`:201`) *and* from `genericTypeParam` (`:117`), so the
  `<T>` of `---@class Box<T>` and the `T` of `---@generic T` are both `LuaCatsTypeParam`
  declarations. A guard requiring `holder is LuaCatsGenericType` declines both. `REFACT-08-00-DR-04`
  measured it and `design.md` §3.4 is the widened predicate; `REFACT-08-17` is the requirement and
  TC-26…TC-28 are its rows.
- **The guard is narrow, and that is measured too.** DR-03 G3 is its control: a generic head in a
  **parent-type** position (`---@class Panel : Box<string>`) is still a use, still gets a reference
  (`references=1 byHolder={GENERIC_TYPE=1}`) and is still rewritten. Only the
  `GenericType → ParameterizedName → ArgType → ClassTag` chain is excluded.
- **What this also settles.** `REFACT-08-04`'s third spelling now has a fixture, an expected output
  and a reachable mutation (TC-21 / mutation M): DR-01 measured `GENERIC_TYPE 0` over the reach corpus
  only because every parameterized head there was `table<K, V>`, whose head is a builtin keyword — not
  because the shape does not occur. DR-03 G1 shows it firing and being rewritten
  (`byHolder={GENERIC_TYPE=1, NAMED_TYPE=1}`).
- **Still open.** If Gap 2.2 is ever closed by re-keying the index to the head name, this guard must
  be revisited with it: the two would then key the same name and the exclusion becomes a merge
  question rather than an aliasing one.

### Gap 2.6 — The new-name regex rejects Unicode letters the lexer accepts

- **Question.** `luacats.flex:70-71` admits `UNICODE_LETTER` in a `NAME`; `design.md` §3.8's regex is
  ASCII-only.
- **Leaning.** Deliberate and symmetric with `LuaNamesValidator`, which makes the identical
  narrowing for Lua identifiers (`^[A-Za-z_][A-Za-z0-9_]*$`). Widening one without the other would
  make the two validators disagree about what a name is.
- **De-risked by.** Nothing; it is a stated narrowing, not an unknown.

### Gap 2.7 — Two shapes in the rename path are reachable only from a non-editor caller

- **Question.** Two branches of `design.md` §3.6 are not on the path an editor caret takes, and both
  were measured that way rather than reasoned about.
  - **`substituteElementToRename`'s use branch (§3.6 steps 6-8).** At a use caret,
    `TargetElementUtilBase.doFindTargetElement` follows the *reference* first and hands the processor
    the **declaration leaf** already, so step 1 fires and steps 6-8 do not. Measured (mutation C of
    `REFACT-08-00-DR-02`): replacing the resolve step with `return null` left DR-02 P4 renaming
    correctly.
  - **`refactoring.rename.catsUnresolvedType` (§3.6 step 8).** Reached only when a caller supplies a use
    element directly.
- **Leaning.** Keep both. They are what makes the processor correct for `PsiElementRenameHandler
  .invoke(element, …)` and for any future in-place route (TBD-1), and deleting a branch because the
  current caller does not take it is how `REFACT-01`'s `declarationLeafOf` defect was created. But
  **they must not be claimed as the mechanism that carries the use caret** — `design.md` §3.6 says
  so, and no test case names them as its falsifier.
- **De-risked by.** Mutation J of DR-02, which deletes `LuaCatsTypeReference.resolve` and is the
  real falsifier for the use caret.

## Technical Debt & Future Work

- **TBD-1: in-place (inline) rename of a type name.** `LuaInplaceRenameHandler` accepts only an
  element whose node type is `LuaElementTypes.IDENTIFIER`, and `MemberInplaceRenameHandler` requires
  a `PsiNameIdentifierOwner`; a LuaCATS NAME leaf is neither. Offering it means either widening
  `LuaInplaceRenameHandler`'s gate to the cats leaf — which re-opens `REFACT-07`'s
  `RenameHandlerRegistry` deletion hazard, since two in-place handlers must never claim one caret —
  or granting `PsiNameIdentifierOwner` to the tag, which is Alternative D's regeneration cost.
  Deferred deliberately; the dialog rename is complete without it.
- **TBD-2: `---@enum` names.** Not renameable because nothing in Lunar treats them as types:
  `LuaCatsEnumTag`'s only reference in `src/main/kotlin` is
  `LuaCatsLazyCommentImpl.getEnumTagList`. If the type engine ever reads them, this feature's
  declaration-slot rule gains a third row and nothing else changes.
- **TBD-3: an advisory when the type's Lua host keeps the old spelling.** Gap 2.1's cosmetic
  residue. Belongs with the conflict machinery, not with the rewrite.

## Pre-Implementation De-risking Tasks

| ID | Action | Resolves | Status |
| :-- | :--- | :--- | :--- |
| `REFACT-08-00-DR-01` | Census the reach over real annotated Lua: how many uses, in how many spellings, how many reachable, and what is left over. | Risk 1.1, Risk 1.3, Gap 2.2, Gap 2.3, the refusal set | **done — see "DR-01 result"** |
| `REFACT-08-00-DR-02` | Prototype the whole design and run it: does a contributor produce references, does `ReferencesSearch` find them cross-file, does the rename leave the type resolving, and what does the full suite say. | Risk 1.1, Risk 1.2, Gap 2.1, Gap 2.7, every test case | **done — see "DR-02 result"** |
| `REFACT-08-00-DR-03` | Interrogate the **write** path with the rigour DR-02 gave the read path: what does the rewrite reach through `allScope`, does `LuaCatsGenericType` ever fire, and does the searcher earn its registration. | Risk 1.1, Risk 1.4, Gap 2.5, `REFACT-08-04`, `REFACT-08-16`, TC-21…TC-25 | **done — see "DR-03 result"** |
| `REFACT-08-00-DR-04` | `LuaCatsTypeParam` is reached from two grammar rules. Which of its occurrences are uses and which are declarations, does DR-03's guard reach both declaration positions, and what does a use bound by a `@generic` parameter resolve to? | Gap 2.5, `REFACT-08-17`, `REFACT-08-04`, TC-26…TC-28, the Out-of-Scope claim about `---@generic` | **done — see "DR-04 result". DR-03's guard reached neither declaration position; the corrected predicate and the shadowing clause are `design.md` §3.4.** |

### DR-01 result (2026-09-02) — the reach, and what bounds it

Run rather than read, on the gce builder over a throwaway `LuaCatsTypeReachProbeTest` and a staged
copy of **`lua-language-server` 3.10.6 (`66141703`)** — the 195 `.lua` files under `meta/`, `script/`
and `tools/` that carry a `---@` tag. Its own `test/` tree is excluded because it holds intentionally
malformed Lua for diagnostics tests. Both the probe and the staged tree were deleted after the run.

The corpus could not be used: **0 of its 734 files carry a `---@` tag**
(`build.gradle.kts:322-326`).

```
[reach] files=195 parseErrorFiles=19 parseErrors=33
[reach] classTagSlots=188 aliasTagSlots=46 distinctDeclaredNames=176
[reach] declaredNamesCollidingWithABuiltinKeyword=11 [any, nil, boolean, number, integer, thread,
        table, string, userdata, lightuserdata, function]
[reach] classTagsWithNoArgType=0
[reach] classSlotShapes={ARG_TYPE=186, PARAMETERIZED_NAME/GENERIC_TYPE=2}
[reach] indexKeyDisagreements=2 sample=[table<K, V> -> table, table<K, V> -> table]
[reach] slotOccurrencesByShape={DECL_ARG_TYPE=166, NAMED_TYPE=1082, TYPE_PARAM=44, DECL_ARG_NAME=46}
[reach] residueByParentType={ARG_NAME=154, RETURN_DESCRIPTION=3, LAZY_COMMENT=5, DUMMY_BLOCK=82,
        DESCRIPTION=50, FIELD_NAME_DESCRIPTOR=16, LITERAL_TYPE=6}
[reach] dummyBlockResidueFiles=[guide.lua:DUMMY_BLOCK=56, export.lua:DUMMY_BLOCK=16,
        debug.lua:DUMMY_BLOCK=5, ffi.lua:DUMMY_BLOCK=2, template.lua:DUMMY_BLOCK=2,
        ffi.lua:LAZY_COMMENT=1, code-lens.lua:LAZY_COMMENT=1, template.lua:LAZY_COMMENT=1]
[reach] usesPerDeclaredName buckets={0=57, 1-5=93, 6-20=16, 21-100=8, >100=2} max=324 sum=1126
[reach] top10=[parser.object=324, uri=178, vm.node=80, fs.path=52, vm.global=40, vm.node.object=28,
        scope=26, parser.state=24, vm.variable=22, dummyfs=21]
[reach] declaredNamesAlsoNamingALuaDeclaration=34
[reach] declaredNamesPlainIdentifier=85 withDotDashStar=91
[reach] narrow word='parser.object' getFilesWithWord[IN_COMMENTS]=46 [IN_CODE]=2 [ANY]=50
[reach] narrow word='uri'           getFilesWithWord[IN_COMMENTS]=46 [IN_CODE]=118 [ANY]=121
[reach] narrow word='vm.node'       getFilesWithWord[IN_COMMENTS]=17 [IN_CODE]=41  [ANY]=48
[reach] narrow word='scope'         getFilesWithWord[IN_COMMENTS]=6  [IN_CODE]=14  [ANY]=15
[reach] narrow word='table<K, V>'   getFilesWithWord[IN_COMMENTS]=5  [IN_CODE]=0   [ANY]=5
```

**Every finding below changed the design.**

1. **The rewrite is 1 126 occurrences over 176 names, and three spellings carry all of them.**
   `NAMED_TYPE` 1 082, `TYPE_PARAM` 44, `GENERIC_TYPE` 0 once the builtin-named classes are removed
   (all 52 `GENERIC_TYPE` hits were `table<…>`). A reference implementation patterned only on
   `LuaCatsNamedType` would miss the 44, which is why `design.md` §2.4 registers three patterns.
2. **11 names are unreachable by construction.** Every one of them is a LuaCATS builtin keyword, and
   `builtinType` is tried before `namedType` in `simpleType`, so their 1 668 use occurrences parse as
   `LuaCatsBuiltinType`. `REFACT-08-07` refuses them.
3. **The word index keys a dotted name as one word.** This was the design's biggest open question —
   91 of the 176 in-scope names contain `.`, `-` or `*`, and `LuaNameReferenceSearcher`'s narrowing
   primitive takes a single word. Measured: `getFilesWithWord("parser.object", IN_COMMENTS, …)`
   returns 46 files, matching `processElementsWithWord`'s 47 and DR-02 P15's 45 files of actual
   references. **`IN_COMMENTS` is also not interchangeable with `IN_CODE`** — for `parser.object` the
   two differ by a factor of 23.
4. **The residue is almost all correct exclusion.** `ARG_NAME` 154 (parameter and `@cast` names that
   happen to match a type name — **not** `@generic` names, which land one node deeper; see DR-04),
   `DESCRIPTION` 50 (prose),
   `FIELD_NAME_DESCRIPTOR` 16, `RETURN_DESCRIPTION` 3, `LITERAL_TYPE` 6 — none is a type use and none
   must be rewritten. `design.md` §3.3's round-trip predicate excludes every one of them by
   construction. The 87 that are *not* correct exclusion are Gap 2.3.

**A cross-check, because a census can agree with itself and still be wrong.** A raw text scan of the
same tree finds 191 `@class` and 46 `@alias` tag openings against PSI's 188 and 46, so the parser
reaches all the alias tags and misses three class tags — consistent with the 19 files carrying a
`PsiErrorElement`, and quantifying the parser's own contribution to Gap 2.3.

### DR-01 addendum — the consumer surface, enumerated

`REFACT-01-00-DR-04` counted "fifteen production files" that consume a LuaCATS type name, from a
grep set that omitted `resolveType(` callers and the LuaCATS PSI plumbing. Re-run here with both
added:

```bash
{ grep -rln "LuaCatsClassTag\|LuaCatsAliasTag\|LuaCatsTypeNameIndex\|LuaClassNameIndex\|LuaAliasIndex\|LuaCatsNamedType\|LuaCatsArgType\|LuaCatsTypeParam\|LuaCatsGenericType" \
       --include=*.kt src/main/kotlin
  grep -rln "resolveType(" --include=*.kt src/main/kotlin
} | sort -u
```

At `154f26f3` that prints **30** paths. One of them, `lang/indexing/LuaMemberFieldIndex.kt`, matches
only inside a KDoc sentence comparing itself to `LuaCatsTypeNameIndex` and reads no type name, and
one, `lang/psi/types/LuaTypeManager.kt`, is the interface declaring `resolveType` rather than a
caller — so **28 files read a LuaCATS type name**. Classified by what this feature must do to each:

| Group | Files | Verdict |
| :--- | :--- | :--- |
| Type resolution by name | `lang/psi/types/LuaTypeManagerImpl.kt`, `LuaTypeReference.kt`, `LuaTypeGraphBridge.kt`, `LuaGraphType.kt`, `LuaTypes.kt` | **may leave** — every one re-derives from file content, and DR-02 P8 measured the round trip: `resolveType("Widget")` null and `resolveType("Gadget")` non-null after the rename |
| Documentation | `lang/doc/LuaDocumentationRenderer.kt`, `LuaDocumentationTargetProvider.kt`, `LuaDocumentationLinkHandler.kt`, `luacats/lang/doc/LuaCatsDocumentationRenderer.kt`, `LuaCatsDeclaredType.kt` | **may leave** — all keyed on the name as written |
| Hierarchy | `lang/hierarchy/LuaHierarchyUtil.kt`, `LuaTypeHierarchyProvider.kt`, `LuaSubTypesHierarchyTreeStructure.kt`, `LuaSuperTypesHierarchyTreeStructure.kt` | **may leave** — the `@class X : Y` edge is a `LuaCatsNamedType`, i.e. one of the three rewritten shapes |
| Inlay hints and markers | `lang/insight/hint/LuaMethodChainInlayHintProvider.kt`, `lang/insight/LuaOverrideLineMarkerProvider.kt` | **may leave** |
| Indexes | `lang/indexing/LuaCatsTypeNameIndex.kt`, `LuaClassNameIndex.kt`, `LuaAliasIndex.kt`, `LuaReceiverMemberIndex.kt` | **may leave** — file-content and stub indexes both reindex on the write. No `getVersion()` is bumped and no key rule changes |
| Navigation and completion | `lang/navigation/LuaCatsTypeNavigation.kt`, `LuaGotoClassContributor.kt`, `lang/completion/GlobalSymbolRankingService.kt`, `lang/LuaNameReference.kt` | **may leave** |
| Stub hoisting | `lang/psi/stubs/impl/LuaLocalVarStubElementType.kt` | **may leave**, and it is what makes Gap 2.1's answer correct: `className` is hoisted from the tag, so the rewritten tag re-hoists under the new name |
| LuaCATS PSI plumbing | `luacats/lang/psi/LuaCatsDeclarations.kt`, `luacats/lang/psi/impl/LuaCatsLazyCommentImpl.kt`, `luacats/lang/syntax/LuaCatsAnnotator.kt` | **may leave** — the annotator highlights `NamedType`/`TypeParam`/`GenericType` unconditionally and never checks whether a type resolves, which is the mechanism behind "no loud failure anywhere" |

**Nothing on this list breaks, and nothing must be updated — *provided the rewrite is complete*.**
That conditional is the whole feature. Under a partial rewrite every row in the first four groups
degrades **silently**, which is what `REFACT-01-00-DR-04` P9 measured and what Risk 1.1 exists to
prevent. The files this feature does edit are a disjoint set, none of which appears above:
`LuaCatsBaseElements.kt`, `LuaTargetElementEvaluator.kt`, `LuaFindUsagesProvider.kt`,
`plugin.xml`, `LuaBundle.properties`.

### DR-02 result (2026-09-02) — the design, built and run

Run rather than read, over a throwaway `LuaCatsTypeRenameProtoTest` plus prototype implementations of
`design.md` §2.1-§2.10 and the four `plugin.xml` registrations of §4. Everything was reverted;
`git status --porcelain` is empty on both trees.

**A `P` number below indexes an observation, not a test method.** One method can report more than
one, so this table's row count and the method count in the harness note beneath it measure different
things and neither is derivable from the other.

| Probe | Question | Measured |
| :-- | :--- | :--- |
| P1 | does `ReferencesSearch` find every use shape cross-file? | `references=11 byFile={uses.lua=11} byHolder={NAMED_TYPE=10, TYPE_PARAM=1}` |
| P2 | is a declaration caret renameable at all? | `findTargetElement=PsiElement(NAME) type=NAME text='Widget'` |
| P3 | what does a rename from the declaration caret leave? | `staleWidgetSpellings=0 newGadgetSpellings=11` |
| P4 | rename from a use caret | `uses.lua` = `--- @param p Gadget`, `types.lua` = `--- @class Gadget` |
| P5 | a dotted name | `--- @class parser.node` / `--- @param p parser.node` / `--- @return parser.node` |
| P6 | `RenameUtil.isValidName` | `parser.node=true Gadget=true table=false 'has space'=false 9bad=true ffi.cdata*=true goto=true`; Lua-side control `M`: `parser.node=false` |
| P7 | a name declared by two tags in two files | all three files on `Gadget`; both declarations moved |
| P8 | does the type still resolve, and is the Lua host touched? | `before=Widget afterOld=null afterNew=Gadget`; `local Widget = {}` unchanged |
| P9 | a builtin-keyword class name | `builtinTypes=[table] namedTypes=[] references=0` |
| P10 | a parameterized class head as a declaration slot | `classDeclarationLeaf=null` |
| P11 | renaming a builtin-keyword class | `REFUSED Cannot perform refactoring.`, both files byte-identical |
| P12 | renaming onto an existing type name | `CONFLICT ConflictsInTestsException: A LuaCATS type named 'Gadget' is already declared in this project` |
| P13 | undo across two files | `renamed=true editorProvided=true typesRestoredDoc=true usesRestoredDoc=true` |
| P14 | Find Usages gate | `canFindUsagesFor=true type='type'` |
| P15 | the search at real-tree scale (DR-01's 195 files) | `name=parser.object references=324 files=45 elapsedMs=1457`, `byHolder={NAMED_TYPE=320, TYPE_PARAM=4}` |
| P16 | a caret on a parameterized class head | no rename target; file byte-identical |
| P17 | an `@alias` declaration and its uses | `aliasDeclarationLeaf=Handle isDeclarationLeaf=true references=2`; rename to `Token` rewrites the tag and both use lines |

**The correction this spike produced, and it is the reason the feature is not a two-file change.**
`REFACT-01-00-DR-04` recorded — marked "read from the constructor, not run" — that a
`psi.referenceContributor language="Lua"` would suffice because cats PSI reports `LuaLanguage`. It is
a legal registration and it is inert. Three passes measured the ladder:

| Pass | `LuaCatsBaseElement` | P1 | P3 |
| :-- | :--- | :-- | :-- |
| 1 | as shipped today | `references=0` | `staleWidgetSpellings=11 newGadgetSpellings=0` |
| 2 | `getReferences()` from the registry | `references=0`, `firstNamedType.reference=null getReferences=1` | `staleWidgetSpellings=11` |
| 3 | `getReferences()` **and** `getReference()` | `references=11` | `staleWidgetSpellings=0 newGadgetSpellings=11` |

Pass 1 is `REFACT-01-00-DR-04`'s index half-apply produced by a different mechanism. Pass 2 is the
one a reviewer would have passed on inspection: the reference exists, and every consumer that reads
`element.reference` sees null.

**Harness facts, each recorded because it cost a run.**

- **`P15` was silently not executed** while its method was named `testP15SearchAtCorpusScale`. The
  routine test task filters `excludeTestsMatching("*Corpus*")` (`build.gradle.kts:315`; `:274` is the
  neighbouring `*Performance*` filter), which
  matches on the fully qualified `Class.method`, so a *method* name containing `Corpus` is excluded
  with no report. The XML reported `tests="15"` for 16 methods. Renaming it made it run.
- **Undo across two files asks for confirmation.** `TestDialogManager.setTestDialog(TestDialog.OK)`
  is required, and the assertion must read `Document` text rather than `PsiFile` text — read from PSI
  the same restored document reported `typesRestored=false`.


### DR-03 result (2026-09-02) — the write path, and the third spelling

Run rather than read, on the gce builder over a throwaway `LuaCatsTypeWriteScopeProbeTest` plus a
rebuilt prototype of `design.md` §2.1-§2.6 and §3.11 and the `plugin.xml` registrations of §4.
Everything was reverted; `git status --porcelain` is empty locally and a file check on the builder
(which is not a git repo) shows no probe or prototype file and no `MUTATION` marker, with
`LuaCatsBaseElements.kt`, `LuaTargetElementEvaluator.kt` and `plugin.xml` back at their original
byte sizes.

DR-03 exists because DR-02 interrogated resolution and search and left the rewrite to inspection.
Its findings are grouped below; the first is a live defect in the design DR-02 produced.

**W — the write scope.** Fixture: a project `---@class File` with two use sites, against the plugin's
own bundled runtime stub, which declares `---@class File` in `runtime/standard/lua-5.4/io.lua`.

```
[b1] W1 name=File indexFilesAll=2 indexFilesProject=1 leavesAll=2 leavesProject=1
        outOfProject=[…/lunar-0.18.0.jar!/runtime/standard/lua-5.4/io.lua]
[b1] W1   leaf name=File file=…/lunar-0.18.0.jar!/runtime/standard/lua-5.4/io.lua writable=false inProject=false
[b1] W1   leaf name=File file=/src/types.lua writable=true inProject=true
[b1] W1 name=os    leavesAll=1 leavesProject=0 outOfProject=[…/runtime/standard/lua-5.4/os.lua]
[b1] W1 name=io    leavesAll=1 leavesProject=0 outOfProject=[…/runtime/standard/lua-5.4/io.lua]
[b1] W1 name=table leavesAll=2 leavesProject=0 outOfProject=[…/table.lua, …/builtin.lua]
[b1] W1 name=Widget leavesAll=0 leavesProject=0 outOfProject=[]
[b1] W3 outcome=REFUSED RefactoringErrorHintException: Cannot perform refactoring.
        catsLibraryType:'File' in […/runtime/standard/lua-5.4/io.lua] typesUnchanged=true usesUnchanged=true
[b1] W4 outcome=RENAMED types=<<<--- @class Gadget / local W = {}>>> staleWidget=0 newGadget=2
```

`File` is not a `BUILTIN_KEYWORDS` member, so `REFACT-08-07` never sees it. `W4` is the control: a
project-only name still renames completely under the same rule. Mutations R and R2 below are the
falsifiers.

**G — the third use spelling.** DR-01 measured `GENERIC_TYPE 0` over the reach corpus, which is a
property of that corpus and not of the shape: every parameterized head in it is `table<K, V>`, whose
head is a builtin keyword. G exercises the shape directly.

```
[b1] G1 references=2 byHolder={GENERIC_TYPE=1, NAMED_TYPE=1}
[b1] G1 outcome=RENAMED usesAfter=<<<--- @type Crate<string> / local a = nil / --- @param p Crate>>>
[b1] G2 references=1 files={uses.lua=1}
[b1] G2 outcome=RENAMED params=<<<--- @class Box<T> / --- @field item T / local P = {}>>>
[b1] G3 references=1 byHolder={GENERIC_TYPE=1}
[b1] G3 outcome=RENAMED uses=<<<--- @class Panel : Crate<string>>>>
```

G1 fires the shape and rewrites it. G2 is Gap 2.5's co-existence case: **before** the §3.4 guard it
read `references=2 files={params.lua=1, uses.lua=1}` and rewrote `---@class Box<T>` to
`---@class Crate<T>`; with the guard, `params.lua` is byte-identical. G3 is the guard's narrowness
control.

**A guard written twice has no falsifier.** The guard was first written into both
`LuaCatsTypeDeclarations` and the reference provider. Removing it from the provider alone left G2's
`params.lua` correct — `handleElementRename` goes through `useLeafOf`, which still declined — and
removing it from `useHolderOf` alone left G2's reference count correct, because the provider still
declined. Neither copy had a reachable mutation. Consolidating it into `useLeafOf`, which §2.4's
provider then *asks*, gives one clause and one falsifier (mutation N).

**O — the searcher.** Removing only the `referencesSearch` registration, everything else in place:
every fixture reports `references=0`, and TC-25's rename reports
`outcome=RENAMED staleWidget=2 newGadget=0` — the declaration moved and both uses were left behind.
The default `CachesBasedRefSearcher` derives a search text only for a `PsiFileSystemItem`,
`PsiNamedElement` or `PsiMetaOwner` (`CachesBasedRefSearcher.java:26-56`), and a bare cats `NAME` leaf
is none of the three. This is the measurement `REFACT-01`'s own risks artifact needed and did not have.

**A grounding defect the compiler found.** `design.md` §3.7 wrote
`ProgressManager.executeNonCancelableSection { … }`. It is an **instance** method
(`ProgressManager.java:155`), so the prototype failed to compile with
`Unresolved reference 'executeNonCancelableSection'` until it was written
`ProgressManager.getInstance().executeNonCancelableSection { … }` — the form `LuaRenameProcessor.kt:268`
already uses. §3.7 now says so.

**Regression gate.** With §3.11's refusal, §3.4's guard and all four registrations in place:
`test --rerun --no-build-cache` → `BUILD SUCCESSFUL in 8m 12s`, **2 949 tests, 0 failures, 0 errors,
1 skipped**.

### DR-03 mutation results

The "Falsifies" mapping for these is derived the same way — see the note under "DR-02 mutation
results"; running that snippet prints `M → TC-21, TC-23`, `N → TC-22`, `O → TC-25`, `R → TC-24` and
`R2 → TC-24`.

| # | Mutation | Observed |
| :-- | :--- | :--- |
| M | the contributor's `LuaCatsGenericType` pattern is dropped | G1 `references=1 byHolder={NAMED_TYPE=1}` and `uses.lua` keeps `--- @type Box<string>`; G3 `references=0` and the parent type stays `Box<string>` |
| N | `isDeclarationSlotHolder` returns false | G2 `references=2 files={params.lua=1, uses.lua=1}` and `params.lua` becomes `--- @class Crate<T>`; G1 and G3 unchanged |
| O | the `referencesSearch` registration of `LuaCatsTypeReferenceSearcher` is removed | G1/G2/G3 all `references=0`; W4 `outcome=RENAMED staleWidget=2 newGadget=0` — every rename half-applies |
| R | `substituteElementToRename`'s out-of-project refusal is deleted (write scope still `projectScope`) | W3 `outcome=RENAMED typesUnchanged=false usesUnchanged=false` — **no exception**, the library's `---@class File` left behind, one type split in two |
| R2 | R, **and** `renameElement`'s scope widened back to `allScope` | W3 `REFUSED RuntimeException: com.intellij.util.IncorrectOperationException: Cannot modify a read-only file '…/lunar-0.18.0.jar!/runtime/standard/lua-5.4/io.lua'` with `typesUnchanged=true usesUnchanged=false` — the throw arrives *after* the use file was rewritten |

### DR-04 result (2026-09-02) — `LuaCatsTypeParam` is a declaration slot too

Run rather than read, on the gce builder over a throwaway `Refact08F1RemediationProbe` and the same
staged `lua-language-server` 3.10.6 (`66141703`) tree DR-01 used. The probe touched no production
file; it was deleted from both trees after the run. It answers three things DR-02 and DR-03 left
open, and it needs none of the design to do it.

**P-A — the slot chains.** `typeParam ::= NAME` (`luacats.bnf:203`) is reached from
`parameterizedName` (`:201`) and from `genericTypeParam` (`:117`). The NAME leaf's parent is
`TYPE_PARAM` in both, so a predicate keyed on the parent cannot tell them apart; the grandparent can.

```
[f1] leaf='Box'    line=1  chain=GENERIC_TYPE > PARAMETERIZED_NAME > ARG_TYPE > CLASS_TAG
[f1] leaf='T'      line=1  chain=TYPE_PARAM > PARAMETERIZED_NAME > ARG_TYPE > CLASS_TAG
[f1] leaf='T'      line=5  chain=TYPE_PARAM > ARG_NAME > GENERIC_TYPE_PARAM > GENERIC_TYPE_PARAMS > GENERIC_TAG
[f1] leaf='Widget' line=10 chain=TYPE_PARAM > PARAMETERIZED_NAME > DISTINCT_TYPE > ARRAY_TYPE > UNION_TYPE > TYPE
[f1] leaf='table'  line=13 chain=GENERIC_TYPE > PARAMETERIZED_NAME > DISTINCT_TYPE > ARRAY_TYPE > UNION_TYPE > TYPE
[f1] leaf='Widget' line=16 chain=TYPE_PARAM > PARAMETERIZED_NAME > DISTINCT_TYPE > ARRAY_TYPE > UNION_TYPE > TYPE
```

**P-B — the guard DR-03 specified reaches neither declaration.** `SPEC` is `design.md` §3.4's
`isDeclarationSlotHolder`; `SHIPPED` is the `holder is LuaCatsGenericType` form the design carried
into review. They disagree on exactly the two `TYPE_PARAM` declarations and agree on every use, which
is what makes TC-26 and TC-27's mutations reachable. The full transcript is quoted in `design.md`
§3.4; the two disagreements are:

```
[f1] holder=TYPE_PARAM text='T' line=1 SPEC=DECLARATION SHIPPED=use   <== DISAGREE   ---@class Box<T>
[f1] holder=TYPE_PARAM text='T' line=5 SPEC=DECLARATION SHIPPED=use   <== DISAGREE   ---@generic T
```

**P-C — the corpus share of the hazard is zero, and that is not a defence.** DR-01's
`TYPE_PARAM=44` was re-derived over the same 195 files, split by the predicate above:

```
[f1] census files=195
[f1] classTagSlots=188 aliasTagSlots=46 distinctClassNames=142 distinctAliasNames=46 union=188
[f1] distinctDeclaredNames=188 collidingWithBuiltinKeyword=11
[f1] slotOccurrencesByParentShape={ARG_NAME=210, ARG_TYPE=186, BUILTIN_TYPE=1668, DESCRIPTION=112,
     DUMMY_BLOCK=223, FIELD_NAME_DESCRIPTOR=16, GENERIC_TYPE=52, LAZY_COMMENT=16, LITERAL_TYPE=6,
     NAMED_TYPE=1082, RETURN_DESCRIPTION=8, TYPE_PARAM=96}
[f1] TYPE_PARAM split={TYPE_PARAM_USE=96}
[f1] TYPE_PARAM(declared-name occurrences) byNameClass={BUILTIN_KEYWORD_NAMED=52, OTHER=44}
[f1] genericTypeParamOccurrences(any name)=33 shapes={NAME > TYPE_PARAM > ARG_NAME=33}
[f1] genericTags=20 distinctGenericParamNames=13 [K, Number, T, T1, T2, T3, T4, T5, T6, T7, T8, T9, V]
[f1] genericParamNamesThatAreDeclaredNames=0
```

Three readings, in order of what they settle:

1. **DR-01's `TYPE_PARAM=44` is entirely uses on this corpus.** No type parameter in
   `lua-language-server` is spelled the same as one of its declared type names
   (`genericParamNamesThatAreDeclaredNames=0`), so the declaration share is zero and DR-01's arithmetic
   stands as a *use* count. **This is plausible-but-unmeasured, not corpus-demonstrated**: the corpus
   supplies no falsifier for the hazard because it never realises it. What makes `REFACT-08-17`
   necessary is the grammar and P-B, not a corpus incident, and TC-26…TC-28 are the fixtures that
   realise it deliberately.
2. **The `44` reconciles exactly.** This census counts 96 `TYPE_PARAM` occurrences of a declared name
   against DR-01's 44, and the difference is the declared names that are also builtin keywords:
   `byNameClass={BUILTIN_KEYWORD_NAMED=52, OTHER=44}`. `typeParam ::= NAME` has no `builtinType`
   alternative, so `string` in `table<string, X>` is a plain `TYPE_PARAM` where the same word in a
   `namedType` position is a `BUILTIN_TYPE`. The `OTHER` bucket is DR-01's number to the unit.
3. **`@generic` names are not in the `ARG_NAME` residue.** Every one of the 33 `@generic` parameter
   occurrences sits under `TYPE_PARAM`, whose parent is the `ArgName` — so the leaf's parent is never
   `ARG_NAME`, and DR-01 finding 4's attribution of them to that bucket is corrected above.

**P-E — comment-scoped shadowing, with its control.** Quoted in `design.md` §3.4. A `NAMED_TYPE`
whose text is a type parameter declared in the *same* `LuaCatsComment` binds to that parameter; one in
a comment that declares none is an ordinary use.

**P-D — mutations R and R2, isolated without a prototype.** Quoted in `design.md` §3.11. This is the
reviewer-reproducibility gap named below, closed for the mechanism and left open for the ordering.

#### DR-04 mutation results

**These three are specified, not executed end-to-end**, and they are tabled here so the invariant the
"DR-02 mutation results" note states — every letter a TC names appears in a mutation table, and every
letter tabled is named by a TC or recorded as a negative — still holds. What *is* executed for each is
its reachability: P-B and P-E show the mutant and the specified predicate disagreeing on that row's
own fixture shape, which is the half the planning bar calls unreachable-mutation risk. The rename
outcome is not transcribed, because the prototype DR-02 and DR-03 used no longer exists.

| # | Mutation | Falsifies | Reachability (executed) | Outcome (specified) |
| :-- | :--- | :-- | :--- | :--- |
| T | `isDeclarationSlotHolder` clause 2 regains a `holder is LuaCatsGenericType` conjunct, so it reaches the head and not the parameters | TC-26 | P-B `holder=TYPE_PARAM text='T' line=1 SPEC=DECLARATION SHIPPED=use <== DISAGREE` | `---@class Box<T>` becomes `---@class Box<Elem>` — a differently-keyed type renamed unasked |
| U | clause 1 of `isDeclarationSlotHolder` is deleted (`holder is LuaCatsTypeParam && holder.parent is LuaCatsArgName`) | TC-27 | P-B, the same `DISAGREE` at line 5 | `---@generic T` becomes `---@generic Elem` — a function-local parameter renamed unasked |
| V | the `shadowedTypeParameterNames` clause is deleted from `useHolderOf` / `useLeafOf` | TC-27; TC-28 is its control, which stays green | P-E `commentShadows=[T] SHADOWED=true` for `---@param v T` and `---@return T`, and `SHADOWED=false` for the unrelated `---@param w T` | `---@param v Elem` / `---@return Elem` under an unchanged `---@generic T` — a generic function silently broken |

#### Open after DR-04

- **DR-01's `distinctDeclaredNames=176` is the POST-exclusion figure, and the reach table subtracted
  the same eleven twice.** Settled by DR-01's own `declaredNamesPlainIdentifier=85`, which
  reproduces on the builtin-excluded set and only there: all 188 declared names give 96 plain
  identifiers, while 188 minus the 11 builtin keywords gives 85 — and DR-01's own `85 + 91 = 176`.
  So `176 = 188 − 11 builtins − 1 parameterized head`, already in scope, and the table's
  `165 = 176 − 11` removed the builtins a second time. The reach table now states 188 declared, 11
  builtin, 1 parameterized head, **176 in scope**. Every other DR-01 bucket reproduces exactly
  (`classTagSlots=188`, `aliasTagSlots=46`, `NAMED_TYPE=1082`, `GENERIC_TYPE=52`, non-builtin
  `TYPE_PARAM=44`), so the disagreement was confined to how one line had already been filtered.

  **The `96`/`85` split, executed rather than asserted** — it is the load-bearing premise of the
  settlement above and DR-01's transcript does not carry it. Re-run against the pinned corpus at
  `3.10.6-6-g66141703`, with the builtin set taken verbatim from `luacats.bnf:206` rather than
  assumed:

  ```
  [census] declared=188  class=142 alias=46 disjoint=True
  [census] builtinColliding=11  [any, boolean, function, integer, lightuserdata, nil, number,
                                 string, table, thread, userdata]
  [census] plainIdentifiers over ALL 188 declared             = 97
  [census] plainIdentifiers over 188-11 builtin-excluded      = 86
  [census] withDotDashStar                                    = 91
  ```

  **This is a text scan, and it sits one name above DR-01's PSI figures (96 / 85) — the same
  text-vs-PSI gap this table already records for `@class` slots (191 raw against 188 reached).**
  The extra name is a plain identifier PSI does not reach: `---@class A` appears only inside
  unclosed long-bracket comments (`script/core/diagnostics/assign-type-mismatch.lua:71`,
  `script/vm/type.lua:581`), so a text scan sees it and the parser does not. Subtracting it gives
  97 → 96 and 86 → 85, DR-01's figures exactly.

  **The settlement does not depend on which reading is used.** Under the PSI reading
  `85 + 91 = 176`; under the text reading `86 + 91 = 177`. Both are the *in-scope* total, and both
  exceed the retracted `165` by more than the eleven builtins that `165` subtracted a second time.
  What the two readings disagree about is one unreachable name, not the direction of the error.

- **Mutations R and R2's *ordering* claim rests on a single run.** P-D reproduces each mechanism
  independently — the read-only throw, and the silent survival of the library declaration — but not
  DR-03's observation that R2 throws only *after* `uses.lua` was rewritten. That sequencing is a
  property of `renameElement`'s step order (§3.7), which no probe short of the feature can exercise.
  It is recorded as resting on one run rather than restated as measured.

### DR-02 mutation results

Every mutation was applied to the prototype and the whole probe class re-run.

**The "Falsifies" column is derived from `requirements.md`, not maintained beside it.** A
cross-reference table maintained by hand is a second source of truth, and this one had drifted in most
of its rows. Re-derive and check it with:

```bash
python3 - <<'EOF'
import re
for ln in open('docs/features/refactoring/08-luacats-type-rename/requirements.md'):
    m = re.match(r'^\|\s*(\d+)\s*\|', ln)
    if m:
        print("TC-" + m.group(1) + ":", re.findall(r'(?i:mutations?)\s+((?:[A-Z]\d?)(?:\s+and\s+[A-Z]\d?)?)', ln))
EOF
```

Each TC row names its own falsifier; this column is the inverse of that mapping, plus every mutation
that names no TC because it is a recorded *negative* — one that leaves its row green, kept so that
nobody re-derives it as a falsifier. Any letter appearing here and
nowhere in `requirements.md`, or a TC naming a letter absent from this table, is a defect.

| # | Mutation | Falsifies | Observed |
| :-- | :--- | :-- | :--- |
| A | `renameElement` rewrites `listOf(element)` instead of `declarationLeaves(...)` | TC-6 | P7 `more=<<<--- @class Widget` — the second declaration left behind |
| B | the searcher narrows with `UsageSearchContext.IN_CODE` | TC-9, TC-18 | P1 `references=0`; P3 `staleWidgetSpellings=11`; P5 use file unchanged; P15 `references=10 files=1` instead of 324/45 |
| C | `substituteElementToRename`'s resolve step (§3.6 step 7) returns null | Gap 2.7 — no TC; it is the mutation that does **not** redden | P4 **still renames** — the use caret is carried by `TargetElementUtilBase`'s reference branch, not by this one |
| D | the `isDeclarationLeaf` clause is removed from `LuaTargetElementEvaluator.getNamedElement` | TC-5 | P2 `findTargetElement=null`; P11 and P12 both degrade to `element not found in file`; five observations of that run go red |
| E | the builtin-keyword line is removed from the input validator | TC-8 | P6 `isValidName('table')=true` |
| F | the builtin refusal is removed from `substituteElementToRename` (§3.6 step 2) | TC-11 | P11 `REFUSED com.intellij.util.IncorrectOperationException: Cannot modify a read-only file '…/lunar/lib/lunar-0.18.0.jar!/runtime/standard/lua-5.4/table.lua'` — the rename proceeds and reaches the **bundled stdlib stub** that declares `---@class table`. A stronger failure than expected, and only because that file is read-only |
| G | the explicit `LuaCatsParameterizedName` guard is removed from `classDeclarationLeaf` | none on its own — TC-12 names it as the mutation that does **not** work; with G3, TC-13 | P10 **still** `classDeclarationLeaf=null` — the guard is dead code, and `design.md` §3.2 drops it |
| G3 | the guard **and** the `firstChildNode` element-type test are both relaxed | TC-12; with G, TC-13 | P10 `classDeclarationLeaf=PARAMETERIZED_NAME` — the type test is the operative clause. P16 still refuses, because the refusal a user meets is §3.3's parent test plus the index key, not this function |
| H | `findCollisions` returns before its lookup | TC-14 | P12 `outcome=RENAMED` — two type declarations merge with no report |
| I | the `LuaCatsTypeDeclarations` clause is removed from `canFindUsagesFor` | TC-17 | P14 `canFindUsagesFor=false`, while `getType` still answers `'type'` and every rename probe stays green — the two gates are independent |
| J | `LuaCatsTypeReference.resolve` returns null | TC-4, TC-16 | `testP4RenameFromAUseCaret FAILED`; 16 tests, 1 failure. This — not mutation C — is the falsifier for the use caret |
| K | `LuaCatsTypeParam` is dropped from the contributor's pattern list | TC-2, TC-18 | P1 `references=10 byHolder={NAMED_TYPE=10}`; P3 `staleWidgetSpellings=1 newGadgetSpellings=10`; P15 `references=320` instead of 324 at real-tree scale |
| L | `aliasDeclarationLeaf` returns null | TC-20 | P17 `aliasDeclarationLeaf=null isDeclarationLeaf=false references=null`, the alias rename refused, every `@class` probe still green |
