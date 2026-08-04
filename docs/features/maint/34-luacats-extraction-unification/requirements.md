---
id: "MAINT-34"
title: "34: LuaCATS Extraction Unification (stub ↔ AST parity)"
type: "feature"
parent_id: "MAINT"
status: "planned"
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

**In scope** — the duplicated PSI→string extraction and its consumers:

- `@field` name + type (3 copies: `LuaLocalVarStubElementType.kt:37`, `LuaTypeManagerImpl.kt:220`,
  `LuaTypeManagerImpl.kt:290`).
- `@class` parent types (3 copies: `LuaLocalVarStubElementType.kt:26` + `LuaTypeManagerImpl.kt:212`,
  `LuaTypeManagerImpl.kt:231`, `LuaTypeManagerImpl.kt:300`).
- `@param` / `@return` (3 copies: `LuaFuncStubElementType.kt:24`, `LuaLocalFuncStubElementType.kt:23`,
  `LuaTypeManagerImpl.kt:368`).
- `@alias` target (2 copies: `LuaLocalVarStubElementType.kt:30`, `LuaTypeManagerImpl.kt:388`).
- A parity harness that asserts both paths agree, permanently.

**Out of scope**:

- Stubbing the LuaCATS comment itself. That is the "correct but heavy" fix noted in the agent
  guide (lazy-parseable element + the `IElementType` registry size limit) and would subsume this
  feature; it is not undertaken here.
- Making `Base<string, number>` *resolve* as a supertype. MAINT-34-02 only guarantees the parent
  name arrives intact; whether the type engine can then resolve a parameterized parent is a
  separate question, measured by DR-01 and deliberately not fixed here.
- Any behavioural change to what the tags *mean*. This is a refactor plus two defect fixes that
  fall out of it (BUG-402, and closing the class of defect BUG-401 belonged to).

## Requirements

| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :---: | :---: | :--- |
| MAINT-34-01 | Shared `@field` extraction | **M** | Not Implemented | One function returns the member name (optional marker stripped) and type string (widened with `nil` when optional). All three consumers call it. |
| MAINT-34-02 | Shared `@class` parent extraction | **M** | Not Implemented | One function returns parents as a `List<String>` from `parentTypes.argTypeList`. The stub stores the **list**, never a joined string; `materializeClass`'s `split(',')` is deleted. Fixes BUG-402. |
| MAINT-34-03 | Shared `@param` / `@return` extraction | **S** | Not Implemented | One function each; both func-stub builders and `funcTypeFromStub`'s AST fallback call them. |
| MAINT-34-04 | Shared `@alias` target extraction | **S** | Not Implemented | One function; stub builder and `materializeAlias` call it. |
| MAINT-34-05 | Stub↔AST parity harness | **M** | Not Implemented | A test that builds each fixture **both** ways (`addFileToProject` → stub, `configureByText` → AST) and asserts identical members and supertypes. Table-driven over the tag corpus in Test Cases. |
| MAINT-34-06 | Stub version bump | **M** | Not Implemented | `LuaFileElementType.getStubVersion()` 3 → 4, because MAINT-34-02 changes the serialized shape of the parents field. |
| MAINT-34-07 | Doc renderer uses the shared accessors | **C** | Not Implemented | `LuaCatsDocumentationRenderer.buildFieldTag` calls a `displayName` variant from the same component, so its deliberate difference (docs *should* show `beta?`) sits next to the engine's rule instead of being a fourth private copy. |

## Test Cases

The corpus below is used **twice** by MAINT-34-05 — once stub-backed, once AST-backed — and both
runs must produce the identical result. Cases marked † are the ones that have already regressed.

| TC | Input | Expected members | Expected supertypes |
| :-- | :-- | :-- | :-- |
| TC-1 † | `---@class C`<br>`---@field a string`<br>`---@field b? number` | `{a, b}`, `b` typed `(number) \| nil` | `[]` |
| TC-2 † | `---@class C : Base<string, number>` | `{}` | `["Base<string, number>"]` (**exactly 1**) |
| TC-3 | `---@class C : A, B` | `{}` | `["A", "B"]` |
| TC-4 | `---@class C`<br>`---@field [string] number` | `{[string]}` | `[]` |
| TC-5 | `---@class C`<br>`---@field private p string`<br>`---@field public q number` | `{p, q}` | `[]` |
| TC-6 | `---@alias Handler fun(x: string): number` | — | alias target `fun(x: string): number` |
| TC-7 | `---@param x string`<br>`---@return boolean`<br>`function f(x) end` | param `x`→`string` | return `boolean` |
| TC-8 | `---@class C : Base`<br>`---@field own number`<br>with `---@class Base`/`---@field inherited string` | `{own, inherited}` | `["Base"]` |

TC-2 is the acceptance test for BUG-402: it fails on `main` today on the stub side only.

## Definition of Done

- All seven requirements implemented; MAINT-34-05's harness green in both directions.
- Full suite green via `tooling/gce-builder/gce-builder.sh run "test --rerun --no-build-cache"` —
  never an isolated `--tests` pattern (isolated-tests-masks-full-suite lesson).
- BUG-402 closed with TC-2 as its regression test.
- MAINT-33 corpus baselines re-measured and re-committed if MAINT-34-02 shifts inspection counts.
