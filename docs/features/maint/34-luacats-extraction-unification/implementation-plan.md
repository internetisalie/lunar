---
id: "MAINT-34-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "MAINT-34"
folders:
  - "[[features/maint/34-luacats-extraction-unification/requirements|requirements]]"
---

# MAINT-34: Implementation Plan

Sequenced from [`design.md`](design.md). Baseline is `main` @ `440dfe0f` (2336 pass / 0 fail /
1 ignored). Every phase leaves the full suite green via
`tooling/gce-builder/gce-builder.sh run "test --rerun --no-build-cache"` — the blast radius covers
stub serialization and every type-resolution consumer, so the **full** suite is mandatory and an
isolated `--tests` pattern is never the gate (isolated-tests-masks-full-suite lesson).

Phase 1 comes first deliberately: the harness must be able to **fail** on TC-2 before anything is
refactored, otherwise the refactor has no witness.

## Phases

### Phase 0: De-risk [Must]
- **Goal**: Answer DR-01 and DR-02 before they can distort the refactor's scope.
- **Tasks**:
  - [ ] DR-01 — probe whether `resolveType("Base<string, number>")` yields a class once the name
        arrives intact. Record the answer in `risks-and-gaps.md`; file a follow-up if it is "no".
  - [ ] DR-02 — probe quick-doc for `---@field [string] number`. Confirm or drop the "Unknown"
        claim in `design.md` §4.4.
- **Verification**: both answers written down; no production code changed.

### Phase 1: Parity harness [Must]
- **Goal**: MAINT-34-05 — make the divergence visible and permanently detectable.
- **Tasks**:
  - [ ] Add `LuaCatsStubAstParityTest` with the `ParityCase` table (design §5) covering TC-1…TC-8.
  - [ ] Assert the **branch** each arm actually took before asserting members/supertypes.
  - [ ] Confirm TC-2 **fails on the stub arm** at this commit — that failure is the BUG-402 witness.
        Temporarily `@Ignore` only TC-2 so the phase can land green, with the ignore's message
        naming BUG-402 and MAINT-34-02.
- **Verification**: full suite green with TC-2 ignored; TC-2's failure captured in the phase notes.

### Phase 2: `LuaCatsDeclarations` + `@field` unification [Must]
- **Goal**: MAINT-34-01.
- **Tasks**:
  - [ ] Create `luacats/lang/psi/LuaCatsDeclarations.kt` with `FieldMember`, `fieldMember`,
        `fieldMembers`, `fieldDisplayName` (design §2, §2.1).
  - [ ] Route `LuaLocalVarStubElementType.createStub` (`:37`) through it.
  - [ ] Route `materializeClass`'s AST branch (`:220`) and `declaredParts` (`:290`) through it.
  - [ ] Add direct unit tests for `fieldMember` covering the optional, keyed and scoped forms.
- **Verification**: full suite green; parity TC-1, TC-4, TC-5 green on both arms.

### Phase 3: `@class` parents + stub shape [Must]
- **Goal**: MAINT-34-02 and -06 — closes BUG-402.
- **Tasks**:
  - [ ] Add `parentTypeNames` (design §2.2).
  - [ ] `luacatsExtends: String?` → `luacatsParents: List<String>` across the six sites in
        design §3; update `LuaStubSerializationTest:57,73`.
  - [ ] Delete `LuaTypeManagerImpl.kt:212`'s `split(',')`.
  - [ ] Bump `LuaFileElementType.getStubVersion()` 3 → 4.
  - [ ] Un-`@Ignore` parity TC-2; it must now pass on **both** arms.
- **Verification**: full suite green; TC-2 and TC-3 green both arms; BUG-402 marked done.

### Phase 4: `@param` / `@return` / `@alias` [Should]
- **Goal**: MAINT-34-03 and -04.
- **Tasks**:
  - [ ] Add `paramTypes`, `returnTypeName`, `aliasTarget` (design §2.3).
  - [ ] Route `LuaFuncStubElementType:24`, `LuaLocalFuncStubElementType:23`,
        `funcTypeFromStub:368-371`, `materializeAlias:388` through them.
  - [ ] Extend the parity harness with TC-6 and TC-7.
- **Verification**: full suite green; TC-6, TC-7 green both arms.

### Phase 5: Doc renderer + corpus re-baseline [Could]
- **Goal**: MAINT-34-07, plus the MAINT-33 obligation from Phase 3.
- **Tasks**:
  - [ ] `LuaCatsDocumentationRenderer.buildFieldTag:439-450` → `fieldDisplayName` (only if DR-02
        confirmed a real gap; otherwise this is a pure de-duplication and stays a `Could`).
  - [ ] Re-run the MAINT-33 corpus (`-PwithCorpus`) and re-commit baselines if Phase 3 shifted
        inspection counts.
- **Verification**: full suite green; corpus baselines committed with the delta explained.

## Definition of Done

- Requirements MAINT-34-01…-06 implemented (-07 is `Could` and may be deferred).
- `git grep -n "split(','\)" src/main/kotlin/net/internetisalie/lunar/lang/psi/types/` returns
  nothing for parent types.
- Parity harness green on both arms for TC-1…TC-8, with branch assertions in place.
- Full suite green (`--rerun --no-build-cache`), ktlint clean, doc linters clean.
- BUG-402 closed; roadmap and `requirements.md` statuses updated.
