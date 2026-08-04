---
id: "MAINT-34-RISKS"
title: "Risks and Gaps"
type: "risk"
parent_id: "MAINT-34"
folders:
  - "[[features/maint/34-luacats-extraction-unification/requirements|requirements]]"
---

# MAINT-34: Risks and Gaps

## De-risking Tasks

### MAINT-34-00-DR-01 — Does a parameterized parent resolve once the name is intact? [Must]

MAINT-34-02 guarantees `Base<string, number>` reaches the type engine as **one** supertype name
instead of two fragments. It does not guarantee the engine can then *resolve* it:
`LuaTypeReference("Base<string, number>")` ultimately looks the name up in `LuaClassNameIndex`,
which is keyed on the plain class name `Base`, so resolution may still fail — correctly-shaped but
still unresolved.

**The repo already contains a working answer pattern**, and DR-01 must start there rather than
treating this as unexplored: `LuaCatsDocumentationRenderer.parentClassName:531-532` resolves a
parent by stripping the generic arguments first —

```kotlin
private fun parentClassName(argType: LuaCatsArgType): String =
    argType.text.substringBefore('<').trim()
```

— which is how quick-doc's inherited-field walk handles parameterized parents today. If the type
engine needs the same treatment, the shape of the fix is already known.

**Action**: probe `resolveType("Base<string, number>", context)` and check whether inherited
members appear on a subclass. **If "no"**, MAINT-34-02 remains correct and worth shipping (the stub
stops emitting garbage names, and the two paths agree) but a follow-up is needed for generic parent
resolution — file it, with `parentClassName` as the reference, rather than widening this feature.

**Why it does not block**: MAINT-34-02's payoff is stated honestly in `requirements.md` — for the
common non-generic parent it repairs inheritance outright; for a generic parent it converts two
nonsense names into one correct name that may or may not resolve. Neither claim depends on DR-01's
answer.

### MAINT-34-00-DR-02 — Is the doc renderer's "Unknown" real? [Should]

`LuaCatsDocumentationRenderer.buildFieldTag:441` reads `fieldDescriptor.argName?.text ?: "Unknown"`.
For a key-descriptor field (`---@field [string] number`) the grammar puts the descriptor in
`argType`, not `argName` (`luacats.bnf:112`), so quick-doc should render the literal word
"Unknown". **This is inferred from reading the code and has not been measured.**

**Action**: render quick-doc for a class with a keyed field and look. Confirm or drop the claim in
`design.md` §4.4 before Phase 5 acts on it.

**Why it does not block**: MAINT-34-07 is a `Could`, and the de-duplication is worth doing whether
or not the rendering gap is real.

## Risks

| ID | Risk | Likelihood | Impact | Mitigation |
| :-- | :-- | :--: | :--: | :-- |
| R1 | **Stub version bump churn** — `getStubVersion` 3 → 4 discards every on-disk stub, forcing a full re-index on first open after upgrade. | Certain | Low | Inherent and correct: the serialized shape genuinely changes. One-time cost, already paid twice this cycle (2 → 3 for BUG-401). |
| R2 | **The parity harness rots into testing one branch twice.** The fixture→branch mapping is an implementation detail of the test framework, not a contract; if it changes, both arms could silently become AST. | Medium | **High** — this is exactly how BUG-401 shipped with two green tests | The harness asserts `decl.stub != null` / `== null` per arm before asserting anything else (design §5). A framework change then fails loudly instead of quietly halving coverage. |
| R3 | **Corpus baseline shift.** Correcting parent extraction changes which supertypes resolve on real projects, moving MAINT-33 inspection counts. | Medium | Low | Phase 5 re-runs `-PwithCorpus` and re-commits baselines with the delta explained. A *reduction* in unresolved-member hits is the expected direction. |
| R4 | **`sourceElement` asymmetry IS load-bearing.** The stub arm points members at the host decl, the AST arm at the `@field` tag. Confirmed consumer: `LuaOverrideLineMarkerProvider.kt:64` uses `superMembers.mapNotNull { it.sourceElement }` as **gutter navigation targets**, and `:72-75` derives `isAbstractMember` from it. | Medium | **High** — a silent regression of override navigation | `FieldMember` carries the originating `tag` (design §2), and both call sites use `member.tag ?: decl`, reproducing today's behaviour exactly on each path. The parity assertions cover names and types only and would **not** catch this, so design §5.5 adds a dedicated `sourceElement` check. |
| R5 | **Scope creep into stubbing the LuaCATS comment.** The "real" fix is to stub the comment itself, which would subsume this work. | Medium | Medium | Explicitly out of scope (requirements). That change is gated on a lazy-parseable element and the `IElementType` registry size limit, and would unlock Find Usages / Rename on types — a feature, not a refactor. MAINT-34 is deliberately the cheap half. |

## Gaps

- **The fork itself survives.** After MAINT-34 the stub and AST arms still exist; they just cannot
  disagree about what a tag *means*. Only comment-stubbing (R5) removes the fork.
- **Only `LuaLocalVarDecl`, `LuaFuncDecl` and `LuaLocalFuncDecl` are stubbed at all.** Anything
  hanging off another host stays on the un-hosted `LuaCatsTypeNameIndex` path added for BUG-400 —
  a third consumer this feature routes through the shared extractor but does not eliminate.
- **The graph layer keeps its own tag readers.** `LuaTypeGraphBridge.kt:114`/`:164` and
  `LuaTypesVisitor.kt:917`/`:938` read `@param`/`@return` for `LuaGraphType` with different
  semantics; they are named out of scope in `requirements.md` rather than folded in, because
  unifying them would change behaviour, not just structure.
- **`configureByText`-only coverage is repo-wide.** MAINT-34-05 fixes it for LuaCATS class
  materialization. Every other stub-backed behaviour keeps the same blind spot; a broader audit of
  which tests actually reach stub code is worth its own MAINT story.
