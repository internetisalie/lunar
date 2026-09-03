---
id: "REFACT-08-PLAN"
title: "08: Implementation Plan"
type: "plan"
parent_id: "REFACT-08"
folders:
  - "[[features/refactoring/08-luacats-type-rename/requirements|requirements]]"
---

# REFACT-08: Implementation Plan

The phases are ordered so that each one is observable on its own and the riskiest change lands
first. Phase 1 carries `REFACT-08-14`, whose blast radius is every LuaCATS PSI element and without
which every later phase silently half-applies; it is therefore gated on the **full** suite rather
than on its own tests. Every phase leaves the build green.

Every phase's verification runs on the builder:
`tooling/gce-builder/gce-builder.sh run "test --tests <pattern> --rerun --no-build-cache"`, and the
phase gate adds `--rerun --no-build-cache` over the whole suite. Never `./gradlew` locally.

## Phase 1 — the slot reader, and the reference host [Must]

**Deliverables**

1. `net.internetisalie.lunar.luacats.lang.psi.LuaCatsTypeDeclarations` — `design.md` §2.1, §3.2-§3.4
   and §3.11. This includes `isDeclarationSlotHolder` and `shadowedTypeParameterNames` (which
   together gate `useHolderOf` and `useLeafOf`) and `outOfProjectDeclarationFiles`.
2. `LuaCatsBaseElement.getReferences()` / `getReference()` — `design.md` §2.2.

**Verification**

- `LuaCatsTypeDeclarationsTest` (new): `classDeclarationLeaf` on `@class Widget`, on
  `@class Panel : Widget` (the parent must **not** be the declaration leaf), on `@class Box<T>`
  (null, TC-12); `aliasDeclarationLeaf` on `@alias Handle string` (TC-20); `isDeclarationLeaf`
  false for a `@param` name, a `@field` name, a `@cast` name and a `@generic` parameter — every
  residue class `REFACT-08-00-DR-01` measured under a `LuaCatsArgName` or a
  `LuaCatsFieldNameDescriptor`.
- `declarationLeaves` over two files declaring the same class name returns both (TC-6's precondition),
  and `declarationLeaves(name, …, projectScope)` over the same fixture returns only the project's.
- `isDeclarationSlotHolder` is true for the `Box` **and** the `T` of `---@class Box<T>` and for the
  `T` of `---@generic T`, and false for the `Box` and the `Widget` of `---@type Box<Widget>`, for the
  `Box` of `---@class Panel : Box<Widget>` and for the `Widget` of `---@param p table<string, Widget>`
  (TC-22, TC-23, TC-26, TC-27, TC-28). `design.md` §3.4 quotes the executed verdict for every one of
  those shapes. **Write it as one clause in `LuaCatsTypeDeclarations` and let §2.4's provider ask
  `useLeafOf`** — a second copy in the provider leaves neither copy with a reachable mutation
  (`risks-and-gaps.md`, DR-03 result).
- `shadowedTypeParameterNames` returns `{T}` for the comment of `---@generic T` + `---@param v T` and
  for the comment of `---@class Box<T>` + `---@field item T`, and the empty set for a comment
  declaring no type parameter — so `useHolderOf` is null for the shadowed `T`s and non-null for the
  unshadowed one (TC-27, TC-28).
- **TC-3 — the negative control for §2.2, and it must be written as a test, not read.** With
  `getReferences()` overridden but `getReference()` left inherited, the contributed reference exists
  and every consumer reading `element.reference` sees null: `firstNamedType.reference=null
  getReferences=1 references=0`. That intermediate state is the one a reviewer passes on inspection,
  so assert both halves.
- **Phase gate: the full unit suite** (TC-19). `REFACT-08-14` changes `getReferences()` for every LuaCATS
  element; `risks-and-gaps.md` Risk 1.2 is what this gate discharges. Measured on the DR-02
  prototype at `154f26f3`: with Phase 5's over-broad validator pattern, `2954 tests completed, 1
  failed, 1 skipped` — the failure being
  `LuaNamesValidatorTest.testRenameUtilReachesValidatorForLabel`, which belongs to Phase 5 and not
  to this phase. With the narrowed pattern, **2 960 tests, 0 failures, 0 errors, 1 skipped**; the
  delta against the unmodified suite is the throwaway probe classes' own methods.
  `REFACT-08-00-DR-03` re-ran the same gate over the remediated design and reported **2 949 tests,
  0 failures, 0 errors, 1 skipped**.

## Phase 2 — the reference [Must]

**Deliverables**

1. `net.internetisalie.lunar.lang.LuaCatsTypeReference` — `design.md` §2.3, §3.4, §3.5.
2. `net.internetisalie.lunar.lang.LuaCatsTypeReferenceContributor` — `design.md` §2.4.
3. The `psi.referenceContributor` registration — `design.md` §4.

**Verification**

- `LuaCatsTypeReferenceTest` (new): a `@param p Widget` in one file resolves to the `@class Widget`
  NAME leaf in another (TC-16); `multiResolve` over two declarations returns both; `isReferenceTo`
  is true for a peer declaration leaf and false for a `@param` name of the same text.
- The reference resolves for all three holders: `LuaCatsNamedType`, `LuaCatsTypeParam` (inside
  `table<string, Widget>`) and `LuaCatsGenericType` (the head of `Widget<string>`) — TC-21.
- The provider returns `PsiReference.EMPTY_ARRAY` for the head of `---@class Box<T>` (TC-22) and a
  live reference for the head of `---@class Panel : Box<string>` (TC-23).

## Phase 3 — the searcher and Find Usages [Must]

**Deliverables**

1. `net.internetisalie.lunar.lang.insight.LuaCatsTypeReferenceSearcher` — `design.md` §2.5, §3.5.
2. The `referencesSearch` registration — `design.md` §4.
3. The `LuaCatsTypeDeclarations` clause in `LuaFindUsagesProvider.canFindUsagesFor` and `getType` —
   `design.md` §2.10.

**Verification**

- `LuaCatsTypeReferenceSearchTest` (new): TC-2 — the eleven-slot fixture returns
  `references=11`, `byHolder={NAMED_TYPE=10, TYPE_PARAM=1}`; and TC-18 at real-tree scale, if a
  pinned annotated tree exists by then ([[MAINT-39]]; `risks-and-gaps.md` Gap 2.4).
- TC-17 — `canFindUsagesFor` on a `@class` name is true and `getType` is non-empty.
- A `LocalSearchScope` search returns only that file's uses.
- **The searcher's necessity is asserted, not assumed.** Deleting the `referencesSearch` registration
  must turn TC-2, TC-21, TC-22 and TC-23 to `references=0` and TC-25 to
  `staleWidget=2 newGadget=0` (`risks-and-gaps.md` DR-03 mutation O). `CachesBasedRefSearcher` derives
  a search text only for a `PsiFileSystemItem`, `PsiNamedElement` or `PsiMetaOwner`
  (`CachesBasedRefSearcher.java:26-56`), and a cats `NAME` leaf is none of the three.

## Phase 4 — the rename [Must]

**Deliverables**

1. `net.internetisalie.lunar.refactoring.rename.LuaCatsTypeRenameProcessor` — `design.md` §2.6,
   §3.6, §3.7, §3.9, §3.11. Note the clauses an implementer must not simplify away:
   `substituteElementToRename` step 4's out-of-project refusal, and `renameElement` step 1's
   `projectScope` — and that `executeNonCancelableSection` is reached through
   `ProgressManager.getInstance()`, not statically.
2. The `LuaCatsTypeDeclarations` clause in `LuaTargetElementEvaluator.getNamedElement` —
   `design.md` §2.8.
3. The `renamePsiElementProcessor` registration — `design.md` §4.
4. `refactoring.rename.catsBuiltinType`, `refactoring.rename.catsLibraryType` and
   `refactoring.rename.catsUnresolvedType` in `LuaBundle.properties` — `design.md` §6.

**Verification**

- `LuaCatsTypeRenameTest` (new): TC-1 (declaration caret, eleven slots, zero stale), TC-4 (use
  caret), TC-5 (`findTargetElement`), TC-6 (two declaration sites), TC-7 (host untouched and the
  type re-resolves), TC-10 (a `---@class table` use parses as `LuaCatsBuiltinType`, so no holder
  exists — the precondition TC-11's refusal rests on) and TC-11 (builtin refused), TC-13 (parameterized head offers no target), TC-15
  (undo), TC-20 (`@alias`), TC-21 (the `GenericType` spelling), TC-22 (a parameterized declaration head
  is not a use), TC-23 (a parent-type generic head still is), TC-24 (a name the bundled stubs also
  declare is refused, both files byte-identical) and TC-25 (its control).
- **TC-26, TC-27 and TC-28 — `REFACT-08-17`, the type-parameter exclusion.** Renaming a project
  `---@class T`: the `<T>` of `---@class Box<T>` and the whole of a `---@generic T` comment stay
  byte-identical, while a `---@param w T` in a comment declaring no type parameter is rewritten.
  These three carry no transcribed outcome (`requirements.md` says so beside them) — they are the
  end-to-end half of what `REFACT-08-00-DR-04` measured at predicate level, and they are the first
  rows to write in this phase because mutations T, U and V are the only falsifiers `REFACT-08-17`
  has.
- **TC-24 needs no fixture of its own for the library side.** The plugin's own bundled runtime stub
  declares `---@class File` in `runtime/standard/lua-5.4/io.lua`, and `PlatformLibraryProvider`
  attaches it in a plain `BasePlatformTestCase` — measured in `REFACT-08-00-DR-03` W1, which reports
  `leavesAll=2 leavesProject=1` for `File`. Assert on `outOfProjectDeclarationFiles` being non-empty
  rather than on the jar path, which carries the plugin version.
- TC-15's fixture must call `TestDialogManager.setTestDialog(TestDialog.OK)` before undoing and
  restore `TestDialog.DEFAULT` afterwards: a rename spanning two files makes the platform ask
  `Undo Renaming type Widget to Gadget?`, and the headless default answers by throwing. Measured
  in `REFACT-08-00-DR-02` P13, which then reported
  `renamed=true typesRestoredDoc=true usesRestoredDoc=true`.
- TC-15 must read `editor.document.text` and `FileDocumentManager.getDocument(...).text`, not
  `PsiFile.text`: the same probe read PSI text first and saw `typesRestored=false` on a document
  that had in fact been restored.
- A regression pass over `LuaRenameTest`, `LuaRenameConflictTest` and `LuaCatsParamRenameTest`:
  the two processors are disjoint (`design.md` §1.3) and neither must move.

## Phase 5 — new-name validation [Must]

**Deliverables**

1. `net.internetisalie.lunar.refactoring.rename.LuaCatsTypeNameInputValidator` — `design.md` §2.7,
   §3.8.
2. The `renameInputValidator` registration — `design.md` §4.

**Verification**

- `LuaCatsTypeNameInputValidatorTest` (new): TC-8 — `RenameUtil.isValidName` for `parser.node`,
  `Gadget`, `ffi.cdata*` (true); `table`, `has space` (false), plus the Lua-side control element.
- TC-9 — `parser.object` renames to `parser.node` end to end, both files.
- **The pattern-narrowing regression is a named test, not an incidental**:
  `LuaNamesValidatorTest.testRenameUtilReachesValidatorForLabel` must stay green, and a second
  assertion is added there for a Lua **local** — `RenameUtil.isValidName(project, localLeaf,
  "parser.node")` must be `false`. `design.md` §2.7 records why: an over-broad
  `getPattern()` makes `RenameInputValidatorRegistry` short-circuit
  `LanguageNamesValidation` for **every** element, and the measured symptom was a Lua label
  accepting the reserved word `end`.

## Phase 6 — conflicts [Should]

**Deliverables**

1. `LuaCatsTypeRenameProcessor.findCollisions` — `design.md` §2.9, §3.10.
2. `refactoring.rename.conflict.catsTypeExists` in `LuaBundle.properties`.

**Verification**

- TC-14: renaming `Widget` to a `Gadget` that another file already declares raises
  `ConflictsInTestsException` carrying the message. Measured on the prototype.
- Renaming to a name **nothing** declares raises no conflict — the falsifier for a rule that always
  fires.

## Phase 7 — regression gate and documentation [Must]

**Deliverables**

1. Full-suite run with counts recorded against `154f26f3`.
2. `REFACT-01-16` moved from `Partial` to `Full` in
   `docs/features/refactoring/01-rename-refactoring/requirements.md`, with its delegation note
   replaced by a pointer to this feature.
3. `REFACT-08`'s roadmap row deleted (mint-and-close), and its `status:` set to `done`.
4. `CHANGELOG.md` entry — user-facing.

**Verification**

- `tooling/gce-builder/gce-builder.sh run "test --rerun --no-build-cache"` — 0 failures. This is
  TC-19; record the counts against `154f26f3` as `REFACT-08-15` requires.
- `tooling/gce-builder/gce-builder.sh run ktlintCheck` — clean. Format on the VM and rsync the
  result back if it is not; never `run "ktlintFormat ktlintCheck"` (BUG-445).
- `python3 scripts/lint_docs.py docs` and `python3 scripts/lint_planning.py docs` — 0 errors.
- **`-PwithCorpus` is required for this feature**, not optional: it changes reference resolution and
  indexing, which is the class of change the engineering contract names.
  `tooling/gce-builder/gce-builder.sh run "test -PwithCorpus --rerun --no-build-cache"`, and confirm
  `LuaCorpusSweepTest`, `LuaTortureCorpusTest`, `LuaInspectionParityTest` and
  `LuaAnnotatedFixtureSweepTest` appear in `build/test-results/test/` **with fresh timestamps** —
  `--rerun` does not clear that directory.
- Live verification through the `verify-in-ide` flow, driven by
  [`human-verification-checklists.md`](human-verification-checklists.md). It covers what the unit
  suite provably cannot: the rename dialog's **OK-button enablement** for a dotted new name
  (where `REFACT-08-08` is actually user-visible), the **refusal balloons** — under
  `BasePlatformTestCase` `CommonRefactoringUtil.showErrorHint` throws rather than painting, so no
  automated test has ever seen one — and the single-undo entry's label.

## Estimated sequence

| Phase | Priority | Depends on |
| :-- | :-- | :--- |
| 1 — slot reader + reference host | Must | — |
| 2 — reference + contributor | Must | 1 |
| 3 — searcher + Find Usages | Must | 2 |
| 4 — rename processor + target evaluator | Must | 3 |
| 5 — input validator | Must | 4 |
| 6 — conflicts | Should | 4 |
| 7 — regression gate + docs | Must | 5, 6 |
