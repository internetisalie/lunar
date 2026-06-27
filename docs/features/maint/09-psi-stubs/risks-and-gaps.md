---
id: "MAINT-09-RISKS"
title: "Risks & Gaps"
type: "risk"
status: "planned"
priority: "medium"
parent_id: "MAINT-09"
folders:
  - "[[features/maint/09-psi-stubs/requirements|requirements]]"
---

# MAINT-09: Risks & Gaps

Test-only feature, so delivery risk is low. The substantive items are two **requirement/AC
wording mismatches** against the real production APIs — both resolved here as decisions and
baked into `design.md`, so the feature still clears the planning bar.

## Critical Risks

### Risk 1.1: `SerializationManagerEx` is `@ApiStatus.Internal`
- **Impact**: The stub round-trip (design §3.2) uses
  `SerializationManagerEx.getInstanceEx().serialize/deserialize`
  (`platform/indexing-impl/.../psi/stubs/SerializationManagerEx.java:17`,
  `StubTreeSerializer.java:12-14`), which is marked internal and could change across platform
  bumps.
- **Likelihood**: low
- **Mitigation**: It is the standard mechanism the platform itself uses to (de)serialize stub
  trees and is stable across the 2024–2026 line; the test pins the GoLand version via
  `gradle.properties`. If it is ever removed, fall back to asserting hoisting via
  `calcStubTree()` only (design §9 alternative) — the same `serialize`/`deserialize` methods on
  each element type would then need a hand-rolled `AbstractStringEnumerator`.

### Risk 1.2: Headless test environment (fonts)
- **Impact**: `BasePlatformTestCase` tests that initialize an editor color scheme throw
  `RuntimeException: Fontconfig head is null` without `fontconfig` + a font.
- **Likelihood**: low
- **Mitigation**: These five classes use only `myFixture.configureByText` + PSI/stub APIs
  (no inlay/editor-scheme init), and the `tooling/gce-builder` bootstrap installs
  `fontconfig`+`fonts-dejavu-core` regardless (per `.agents/AGENTS.md`). No action needed.

## Design Gaps

### Gap 2.1: AC-09-01 names a "LuaNameRef element"; the factory produces no such thing — RESOLVED
- **Question**: AC-09-01 says `LuaElementFactory.createIdentifier` "produces a valid
  `LuaNameRef` element". The real method (`LuaElementFactory.kt:12-15`) returns a generic
  `PsiElement?` — `labelRef.identifier ?: labelRef.firstChild` — i.e. the identifier *leaf* of
  a `LuaLabelRef`. There is no `createNameRef` and `LuaNameRef` (gen) is never the return type.
- **Options / leaning**: (a) assert the test against the real return (identifier leaf:
  non-null, `text == name`) and assert `createLabelRef` returns a `LuaLabelRef`; (b) change
  production to add a `LuaNameRef`-producing factory (out of scope — test-coverage epic).
- **Resolved by**: DR-01. **Decision: option (a)** — baked into design §2.1 / §6. The
  requirement's intent (factory yields a valid, named PSI element) is met; only the type name
  in the AC prose is loose. No production change.

### Gap 2.2: AC-09-04 names `it.isMethod`; no such accessor exists — RESOLVED
- **Question**: AC-09-04 says "calling `it.isMethod` … resolves to the receiver method
  parameter". There is no `isMethod` accessor on any Lua PSI type. The real method predicate is
  `funcName.funcNameMethod != null` (`LuaScopeProcessor.kt:82`; same idiom at
  `LuaParameterInfoHandler.kt:129`), and implicit `self` resolves to the **receiver
  identifier** `funcName.funcNameMethod.nameRef.identifier` (`LuaScopeProcessor.kt:83`), not a
  synthetic "self parameter" PSI.
- **Options / leaning**: (a) test the observable behaviour — resolve `self` inside a method and
  assert the target is the receiver identifier (`obj`); (b) introduce an `isMethod` helper
  (out of scope).
- **Resolved by**: DR-02. **Decision: option (a)** — baked into design §2.4 / §3 Example 3 /
  §6. No production change.

## Technical Debt & Future Work
- **TBD: stub round-trip via public `StubOutputStream`/`StubInputStream`** — would avoid the
  internal `SerializationManagerEx`, but needs an in-memory `AbstractStringEnumerator`
  (none exists; design §9). Park until/unless the internal API is removed.
- **TBD: align AC prose** — a later docs pass could reword AC-09-01 ("a valid named PSI
  element") and AC-09-04 ("resolves `self` to the method receiver") to match the code. Cosmetic;
  not blocking.

## Pre-Implementation De-risking Tasks

| ID | Action | Resolves | Status |
|----|--------|----------|--------|
| MAINT-00-DR-01 | Confirm `LuaElementFactory.createIdentifier` return type and assert against the real leaf (text == name); confirm `createLabelRef` → `LuaLabelRef`. | Gap 2.1 | done |
| MAINT-00-DR-02 | Confirm implicit-`self` resolves to the receiver identifier via `LuaScopeProcessor.kt:82-83`; no `isMethod` API. | Gap 2.2 | done |
| MAINT-00-DR-03 | Confirm `(LuaFile as PsiFileImpl).calcStubTree()` + `SerializationManagerEx` round-trip is viable under `BasePlatformTestCase` (no index forceRebuild). | Risk 1.1 | done |

## Test Case Gaps
- No coverage planned for `LuaFuncDef` (anonymous function-expression) parameter scoping
  (`LuaFunctionExt.kt:18`) beyond what `LuaFunctionScopeTest` exercises for `LuaFuncDecl`;
  acceptable — the requirement table (MAINT-09-04) names methods and loop variables, not
  anonymous-function params. Add later if needed.
- No coverage for label resolution (`LuaBlockExt.processLabelDeclarations`,
  `LuaBlockExt.kt:83`) — already covered by the existing `LuaLabelResolutionTest`; out of scope
  here to avoid duplication.

## See Also
- Requirements: [requirements.md](requirements.md)
- Design: [design.md](design.md)
- Implementation Plan: [implementation-plan.md](implementation-plan.md)
