---
id: "MAINT-34"
title: "34: LuaCATS Extraction Unification (stub ↔ AST parity)"
type: "feature"
parent_id: "MAINT"
status: "in_progress"
priority: "medium"
folders:
  - "[[features/maint/requirements|requirements]]"
---

# MAINT-34: LuaCATS Extraction Unification (stub ↔ AST parity)

Every LuaCATS tag that feeds the type engine is read **twice, by two different pieces of code**:
once at stub-build time (`*StubElementType.createStub`) and once at materialization time from live
PSI (`LuaTypeManagerImpl`). The two are copy-paste siblings, not one function with two callers, so
they drift — and because *which* one runs depends only on whether the file's AST happens to be
loaded, drift shows up as a class resolving differently from one caret position to the next.

This feature does not remove the stub/AST fork — a stub is a serialized snapshot, so a fork must
exist. It reduces the fork to a **data-source choice** by extracting the PSI→(name, type-string)
logic into one shared, tested component that both sides call.

## Evidence: this has already drifted three times

Measured on `main` @ `440dfe0f` with a throwaway probe (`ProbeCatsDivergenceTest`, run on the
builder 2026-08-04, not committed):

| # | Tag form | Stub path | AST / un-hosted path | Status |
| :-- | :-- | :-- | :-- | :-- |
| 1 | `---@field optional? number` | member keyed **`optional?`** — unreachable | member keyed `optional` | **BUG-401**, fixed `440dfe0f` |
| 2 | `---@class Kid : Base<string, number>` | **2** supertypes: `Base<string`, `number>` | **1** supertype: `Base<string, number>` | **BUG-402**, open — fixed here by MAINT-34-02 |
| 3 | `---@class` with no stubbed host | invisible to `LuaClassNameIndex` | resolved via `LuaCatsTypeNameIndex` | **BUG-400**, fixed `3869bc9c` by adding a **third** implementation |

Probe output for #2, verbatim:

```
PROBE genericParent:    branches=[STUB] extends=Base<string, number>
PROBE genericParent:    superCount=2 supers=<Base<string> | <number>>
PROBE unhostedGeneric:  superCount=1 supers=<Base<string, number>>
```

The stub stores `parentTypes.text` — a flattened string — and `materializeClass` re-splits it on
`','` (`LuaTypeManagerImpl.kt:212`), which cuts a parameterized parent in half. The AST branch
(`:231`) and the un-hosted branch (`declaredParts`, `:300`) both walk `parentTypes.argTypeList`,
which the grammar already split correctly (`luacats.bnf:92`).

BUG-400's fix is the clearest warning: closing it required writing the extraction a **third**
time rather than reusing either existing copy.

## Evidence: the drift is invisible to the current test style

Also measured by the same probe, and the reason BUG-401 survived:

| Fixture call | Branch actually exercised |
| :-- | :-- |
| `myFixture.configureByText(...)` | **AST** (`decl.stub == null`) |
| `myFixture.addFileToProject(...)` | **STUB** (`decl.stub != null`) |

Reverting the BUG-401 fix and re-running its own regression tests: `testOptionalFieldCompletes`
**failed**, but `testOptionalFieldIsNamedWithoutTheMarker` and `testOptionalFieldTypeAdmitsNil`
**passed with the bug present** — both use `configureByText`, so neither ever reached the code
that was broken. Any test written the ordinary way tests the AST branch only.

This generalizes beyond BUG-401: `configureByText` is the dominant fixture idiom in this repo, so
**the stub branch is close to untested across the board**.

## Scope

**In scope** — the duplicated *nominal-layer* PSI→string extraction and its consumers. Counts below
are exhaustive for the nominal layer and were re-verified by grep, not estimated:

- `@field` name + type — **3 copies**: `LuaLocalVarStubElementType.kt:37`,
  `LuaTypeManagerImpl.kt:220`, `LuaTypeManagerImpl.kt:290`.
- `@class` parent types — **4 copies**: `LuaLocalVarStubElementType.kt:26` (+ its re-split at
  `LuaTypeManagerImpl.kt:212`), `LuaTypeManagerImpl.kt:231`, `LuaTypeManagerImpl.kt:300`, and
  `LuaCatsDocumentationRenderer.parentClassNames:534-535` (which additionally strips generics via
  `parentClassName:531-532`). The renderer's copy is folded in under MAINT-34-07 as a *display*
  variant, not deleted — stripping `<…>` for doc lookup is deliberate.
- `@param` / `@return` — **3 copies in the nominal layer**: `LuaFuncStubElementType.kt:24`,
  `LuaLocalFuncStubElementType.kt:23` (byte-identical to each other), `LuaTypeManagerImpl.kt:368`.
- `@alias` target — **2 copies**: `LuaLocalVarStubElementType.kt:30`, `LuaTypeManagerImpl.kt:388`.
- A parity harness that asserts both paths agree, permanently.

**Out of scope**:

- **The graph layer's own `@param`/`@return` readers** — `LuaTypeGraphBridge.kt:114` and `:164`,
  `LuaTypesVisitor.kt:917` and `:938`. These read the same tags but for `LuaGraphType`, with
  different semantics (positional parameter binding, multi-value returns), so they are *not*
  copies of the nominal rule and folding them in would change behaviour. Named here so their
  existence is a recorded decision rather than an oversight.
- Stubbing the LuaCATS comment itself. That is the "correct but heavy" fix noted in the agent
  guide (lazy-parseable element + the `IElementType` registry size limit) and would subsume this
  feature; it is not undertaken here.
- Making `Base<string, number>` *resolve* as a supertype — see MAINT-34-02's honest payoff
  statement below and DR-01.
- Any behavioural change to what the tags *mean*, including `LuaTypeMember.sourceElement`, which
  this feature preserves exactly (design §2, §4.1).

## Requirements

| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :---: | :---: | :--- |
| MAINT-34-01 | Shared `@field` extraction | **M** | Full | One function returns the member name (optional marker stripped) and type string (widened with `nil` when optional). All three consumers call it. |
| MAINT-34-02 | Shared `@class` parent extraction | **M** | Full | One function returns parents as a `List<String>` from `parentTypes.argTypeList`. The stub stores the **list**, never a joined string; `materializeClass`'s `split(',')` is deleted. Fixes BUG-402. **Payoff, stated honestly:** this guarantees the parent *name* arrives whole and that the two paths agree. It does **not** by itself make `Base<string, number>` resolve — `LuaClassNameIndex` is keyed on the plain class name. Where the parent is non-generic (the overwhelming majority) inheritance is repaired outright; where it is generic, the fix converts two nonsense names into one correct-but-possibly-unresolved name. See DR-01. |
| MAINT-34-03 | Shared `@param` / `@return` extraction | **S** | Full | One function each; both func-stub builders and `funcTypeFromStub`'s AST fallback call them. |
| MAINT-34-04 | Shared `@alias` target extraction | **S** | Full | One function; stub builder and `materializeAlias` call it. |
| MAINT-34-05 | Stub↔AST parity harness | **M** | Full | A test that materializes each case **both** ways (`addFileToProject` → stub arm, `configureByText` → AST arm) with **arm-distinct class names**, asserts the branch each arm actually took, then asserts identical members and supertypes. One test method per case. See Test Cases for the substitution rule and why arm-distinct names are mandatory. |
| MAINT-34-06 | Stub version bump | **M** | Full | `LuaFileElementType.getStubVersion()` 3 → 4, because MAINT-34-02 changes the serialized shape of the parents field. |
| MAINT-34-07 | Doc renderer uses the shared accessors | **C** | Full | `LuaCatsDocumentationRenderer.buildFieldTag:439-450` calls a `fieldDisplayName` variant, and `parentClassNames:534-535` is rebuilt on `parentTypeNames` + a `simpleParentName` display variant. Both renderer behaviours are **kept** (docs should show `beta?`, and should strip `<…>` for parent lookup); the point is that these deliberate differences live beside the engine's rule instead of being private copies that drift. |

## Test Cases

Each case below is materialized **twice** by MAINT-34-05 — once stub-backed, once AST-backed —
and both runs must produce the identical result. Cases marked † have already regressed.

**Every identifier that must differ between the two arms is written with a trailing `__`.** The
harness replaces each `__` with `<arm><caseIndex>` (`S1`/`A1`, `S2`/`A2`, …), so the stub arm and
the AST arm declare *different* classes. This is mandatory, not cosmetic: `doResolveType`
(`LuaTypeManagerImpl.kt:181-185`) searches `allScope` and hands **every** matching declaration to
`materializeClass`, so two same-named fixtures in one project are merged into a single blended
result and neither arm exists any more. `typeCache` is keyed on the name alone (`:34`, `:185`),
which would additionally make the second resolution a cache hit and the comparison a tautology.

**Every class case carries a host declaration** (`local C__ = {}`). Without a stubbed host there is
no `LuaLocalVarDecl`, no `LuaClassNameIndex` entry, and resolution falls through to
`materializeUnhostedClass` (`:253`) — a third path that is neither arm.

| TC | Source (`__` → arm suffix) | Expected `getMembers().keys` | Expected supertype names | Extra |
| :-- | :-- | :-- | :-- | :-- |
| TC-1 † | `---@class C__`<br>`---@field a string`<br>`---@field b? number`<br>`local C__ = {}` | `{a, b}` | `[]` | member `b`'s type name contains `nil` |
| TC-2 † | `---@class C__ : Base<string, number>`<br>`local C__ = {}` | `{}` | `["Base<string, number>"]` — **size exactly 1** | — |
| TC-3 | `---@class C__ : A__, B__`<br>`local C__ = {}` | `{}` | `["A__", "B__"]` (substituted) | parents intentionally undeclared; only the *names* are asserted |
| TC-4 | `---@class C__`<br>`---@field [string] number`<br>`local C__ = {}` | `{[string]}` | `[]` | — |
| TC-5 | `---@class C__`<br>`---@field private p string`<br>`---@field public q number`<br>`local C__ = {}` | `{p, q}` | `[]` | the scope keyword must not leak into either name |
| TC-7 | `---@class C__`<br>`local C__ = {}`<br><br>`---@param x string`<br>`---@return boolean`<br>`function C__.f(x) end` | `{f}` | `[]` | `f`'s type is a `LuaFunctionType`; parameter `x` typed `string`, return typed `boolean` |
| TC-8 | `---@class Base__`<br>`---@field inherited string`<br>`local Base__ = {}`<br><br>`---@class C__ : Base__`<br>`---@field own number`<br>`local C__ = {}` | `{own, inherited}` | `["Base__"]` (substituted) | parent declared in the **same file** as the child, in both arms |

TC-7 is expressed as a class member deliberately: it exercises `@param`/`@return` through
`funcTypeFromStub` (`:361`), which is the nominal-layer consumer this feature unifies, and so needs
no second case model.

Two cases sit outside the table because they are not class materialization:

| TC | Source (`__` → arm suffix) | Expectation |
| :-- | :-- | :-- |
| TC-6 | `---@alias Handler__ fun(x: string): number`<br>`local Handler__ = nil` | `resolveType` yields a `LuaAliasType` whose target type name is `fun(x: string): number`, identically in both arms |
| TC-9 | — | `LuaParserDefinition.FILE.stubVersion == 4` — the acceptance check for MAINT-34-06. Assert through the **singleton**; instantiating `LuaFileElementType()` in a test violates the "element types must be static singletons" rule (the `IElementType` registry has a hard size limit) |
| TC-10 | Two files, each `---@meta` + `---@class Shared__ : P__` with **no** host declaration | The un-hosted path (`materializeUnhostedClass`) yields **2** supertype references named `P__` — duplicates accumulate, matching today. Not a parity case: with no host there is no stub arm |

TC-2 is the acceptance test for BUG-402: on `main` today it fails on the stub arm only, producing
2 supertypes instead of 1.

## Definition of Done

- All seven requirements implemented; MAINT-34-05's harness green in both arms for every case,
  with the per-arm branch assertions in place.
- `LuaTypeMember.sourceElement` unchanged: still the `@field` tag on the AST path and the host
  declaration on the stub path (verified by a test over `LuaOverrideLineMarkerProvider`'s
  navigation targets, which consume it at `LuaOverrideLineMarkerProvider.kt:64`).
- Full suite green via `tooling/gce-builder/gce-builder.sh run "test --rerun --no-build-cache"` —
  never an isolated `--tests` pattern (isolated-tests-masks-full-suite lesson).
- BUG-402 closed with TC-2 as its regression test.
- MAINT-33 corpus baselines re-measured and re-committed if MAINT-34-02 shifts inspection counts.
