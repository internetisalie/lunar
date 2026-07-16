---
id: "REDIS-06-RISKS"
title: "Risks & Gaps"
type: "risk"
status: "planned"
parent_id: "REDIS-06"
folders:
  - "[[features/redis/06-sandbox-quickdoc-refinements/requirements|requirements]]"
---

# REDIS-06: Risks & Gaps

## Critical Risks

### Risk 1.1: A VFS/type-engine resolve in the sandbox gate re-introduces TestLogger errors
- **Impact**: This is the exact reason REDIS-06-01 was deferred on 2026-07-14. An earlier
  attempt resolved the flagged name through the full reference (`LuaNameReference.resolve` /
  `multiResolve`), whose Phase-2 path hits the VFS and the type engine
  (`LuaTypeManagerImpl.resolveModule`), logging a warning that the test harness escalates to a
  `TestLoggerAssertionError`. This is already documented in the codebase at
  `LuaRedisSandboxInspectionTest.kt:134-151` (the `testRequireIsInBlockedAllowlist` note explains
  why `require` is checked via the allowlist directly instead of `doHighlighting`).
- **Likelihood**: high (if the wrong resolution API is used).
- **Mitigation**: the design mandates the **scope-walk-only** path
  (`LuaResolveUtil.scopeCrawlUp` + a local-only `PsiScopeProcessor`, design §3.1), which touches
  only in-tree PSI — no VFS, stub index, or type engine. Implementers MUST NOT call
  `LuaNameReference.resolve()`/`multiResolve()`, `LuaTypeManager*`, `StubIndex`, or
  `PsiManager.findFile` from the gate. Verify against the **full** gce-builder suite (`run test`),
  not an isolated `--tests` pattern — the isolated run can pass while a full-suite JUnit failure
  hides (see the "Isolated --tests masks full suite" project lesson); the TestLogger error only
  surfaces when other suites share the harness state (DR-01).

### Risk 1.2: Over-exemption from matching file-level globals as "locals"
- **Impact**: If the gate reused `LuaScopeProcessor` (which also matches `LuaGlobalVarDecl` /
  `LuaGlobalFuncDecl` / `LuaFuncDecl` / `LuaVar` / assignment vars), a script-defined global
  named `io`/`print` would silently exempt the use, weakening the sandbox check.
- **Likelihood**: medium.
- **Mitigation**: the design uses a **new** `LocalBindingScopeProcessor` that matches ONLY the
  five local kinds (`LuaLocalVarDecl`, `LuaLocalFuncDecl`, `LuaParList`, numeric+generic
  for-vars) and returns `true` (continue, no match) for every global kind (design §3.1). Edge
  case explicitly covered in design §6 ("Global assignment shadow").

## Design Gaps
_None — both fixes are fully specified against grounded, real symbols (design §2, §3)._

## Technical Debt & Future Work
- **TBD: cross-file / true-resolution sandbox gate** — a full resolution (respecting `require`d
  modules that re-export a blocked name) is out of scope precisely because it needs the VFS path
  that triggers Risk 1.1. Deferred; the local-only walk covers the reported false positive.
- **Roadmap note (do not edit roadmap here)**: on completion, REDIS-06 moves from `planned` to
  `done`; the roadmap `Status` column is advisory and can be reconciled separately.

## Pre-Implementation De-risking Tasks

| ID | Action | Resolves | Status |
|----|--------|----------|--------|
| REDIS-00-DR-01 | After Phase 1, run the FULL gce-builder `run test` suite and grep the output for `TestLoggerAssertionError` / `resolveModule` warnings; confirm zero, especially in the Redis suites and any alphabetically-later suite sharing harness state. | Risk 1.1 | todo |

## Test Case Gaps
- The parameter-shadow case (TC 4) and the numeric/generic for-variable shadow are covered by
  the `LuaParList` / for-statement branches in design §3.1 but only TC 4 (param) is in the
  requirements table; a for-variable shadow case may be added opportunistically if cheap.

## See Also
- Requirements: [requirements.md](requirements.md)
- Design: [design.md](design.md)
