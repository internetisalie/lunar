---
id: "MAINT-25-RISKS"
title: "Risks & Gaps"
type: "risk"
parent_id: "MAINT-25"
folders:
  - "[[features/maint/25-type-graph-immutability/requirements|requirements]]"
---

# MAINT-25: Risks & Gaps

Surfaces the correctness/perf risks of making `LuaGraphType.Table` immutable across the shared
inference engine, and the small set of implementation-note unknowns the design flagged as
de-risking tasks. All line citations re-verified on `main` @ `0566cfbc`.

## Critical Risks

### Risk 1.1: Silent inference regression across the shared engine
- **Impact**: `LuaGraphType.Table` feeds every type-aware IDE surface (inlay hints, completion,
  hierarchy, annotators, assignability inspections). A dropped member or altered equality would
  regress inference project-wide without a compile error.
- **Likelihood**: medium
- **Mitigation**: the change is behavior-preserving (immutable-by-copy replaces in-place mutation;
  the only intended delta is removing the cross-file leak). Gate on the REDIS-04 §3.1c-style
  regression contract in implementation-plan Phase 5 — the full `lang/types/*` suite plus consumer
  suites, run cache-defeated (`--rerun-tasks --no-build-cache`) against the 2123/0/1 baseline.

### Risk 1.2: `handleSetMetatable` copy changes receiver-augmentation semantics
- **Impact**: the pre-change code mutated the `tType` instance in place, which — because that
  instance aliased the receiver `t`'s narrowing value — *incidentally* augmented `t` itself, not
  just the call result. Copy-on-augment (design §3.1) publishes the augmented copy only on the
  result node, so any consumer that relied on the receiver binding being augmented could change.
- **Likelihood**: low
- **Mitigation**: TC-05 asserts the **result**-member behavior COMP-04-08 actually tests
  (`LuaTypeInferredCompletionTest`); DR-04 confirms no test/consumer depends on the receiver `t`
  being retroactively augmented. If one does, re-publish the augmented copy on the receiver's node
  too (still immutable — a second `graph.value` edge, not a mutation).

### Risk 1.3: `fromLuaType` cycle-registration ordering
- **Impact**: `fromLuaType` registers a placeholder in `visited` **before** recursing so a cyclic
  `LuaType` sees it (`LuaGraphType.kt:157,172`). An immutable `Table` cannot be filled in place, so
  a naive "build members first, construct last" loses the placeholder and could re-enter recursion
  on a genuinely cyclic Layer-1 `LuaType`.
- **Likelihood**: low
- **Mitigation**: Layer-1 `LuaType` cycles are broken by `LuaTypeReference`/`LuaAliasType`
  indirection (`LuaGraphType.kt:149-150`). DR-03 empirically confirms no bare-`Table` `LuaType`
  cycle survives the existing safety tests; if one does, keep `fromLuaType`'s `Table` backed by a
  locally-mutable map registered by reference (the map carries the cycle), exactly as
  `graphTypeToLuaType` does in design §3.2.

## Design Gaps

### Gap 2.1: `LuaClassType`/`LuaTableLiteralType` may defensively copy their member map
- **Question**: design §3.2's cycle guard fills the placeholder's member map **after** registering
  it in `visited`, relying on `LuaClassType(...)` / `LuaTableLiteralType(...)` **retaining** (not
  copying) the passed `LinkedHashMap` so the cycle-back reference observes the growing map.
- **Options / leaning**: (a) if retained by reference → the §3.2 fill-after-register pattern works
  as written; (b) if copied → register the placeholder as an intermediate holder and swap, or fill
  the map before construction and accept that a self-cycle resolves to the placeholder's empty-map
  first pass (still finite). Leaning (a) pending DR-01.
- **Resolved by**: DR-01 (verify the constructors in `LuaStructuredTypes.kt` /
  `LuaComplexTypes.kt`); fold the confirmed mechanism back into design §3.2.

### Gap 2.2: direct-child cats-comment scan completeness (perf item)
- **Question**: design §3.5 replaces the O(n²) deep `PsiTreeUtil.findChildrenOfType(element,
  LuaCatsComment)` (`LuaRecursiveVisitor.kt:22`) with a **direct-child** scan. Does any
  `LuaCatsComment` attach more than one PSI level below a visited element (so a direct-child scan
  would miss it)?
- **Options / leaning**: LuaCATS comments attach directly under statements in practice; leaning
  "direct-child is sufficient". If DR-02 finds a nested case, keep the deep scan but memoize it per
  element (or drop MAINT-25-05's cats-scan item — it is a Could).
- **Resolved by**: DR-02; fold the answer into design §3.5.

## Technical Debt & Future Work
- **TBD: `VariableElement.write`/`read` memoization** — both are computed properties re-traversing
  the up/down sets per access (`LuaTypeNodes.kt:84-85`, review §2.5.5). They are read **during** the
  `checkTypes` fixed-point loop (`LuaTypeGraph.kt:222`) while edges are still being added, so a naive
  `by lazy` would freeze a stale value. Safe memoization needs a build-complete flag; deferred out of
  MAINT-25 to keep the immutability change atomic and low-risk.
- **TBD: `PlatformLibraryProvider.getRootsToWatch`** — unlike `LuaRocksLibraryProvider`, this
  provider has no `getRootsToWatch` override (design §7). After `refreshIfNeeded = false`, a
  not-yet-refreshed root resolves on the next roots recomputation. Adding a watch override would
  tighten freshness but is out of scope (behavioral change to library-root watching).

## Pre-Implementation De-risking Tasks

| ID | Action | Resolves | Status |
|----|--------|----------|--------|
| MAINT-25-00-DR-01 | Read `LuaStructuredTypes.kt` / `LuaComplexTypes.kt` and confirm whether `LuaClassType` / `LuaTableLiteralType` retain the passed member map by reference (fill-after-register) or defensively copy it | Gap 2.1 | done — resolved by Phase 2 (`be98b5df`): cycle-guarded `graphTypeToLuaType` green (TC-03), full suite unregressed |
| MAINT-25-00-DR-02 | Grep/inspect real PSI (a LuaCATS-annotated fixture) to confirm `LuaCatsComment` never attaches more than one level below a visited element, validating the direct-child scan | Gap 2.2 | done — PSI probe confirmed every `LuaCatsComment` parents to a real, visited container (LuaFile/LuaBlock/LuaFuncDecl/LuaDoStatement), never nested >1 level; direct-child scan is complete (see `LuaRecursiveVisitorCatsScanTest`) |
| MAINT-25-00-DR-03 | Run `TestLuaTypeEngineSafety` + `UnionAndGenericTest` after the immutable-`fromLuaType` change to confirm no bare-`Table` Layer-1 `LuaType` cycle regresses | Risk 1.3 | done — resolved by Phase 1 (`be24eb87`) + the full cache-defeated regression (2128/0/0/1) |
| MAINT-25-00-DR-04 | Grep type/consumer tests for reliance on `setmetatable`'s **receiver** (`t`) being augmented (vs the call result) to confirm copy-on-augment preserves observed behavior | Risk 1.2 | done — resolved by Phase 1 (`be24eb87`): `LuaTypeInferredCompletionTest` (setmetatable result members, TC-05) stays green |

## Test Case Gaps
- Live-IDE verification of "no cross-file member leak after a `type(x)=="table"` narrowing across
  two open files" is covered by human-verification-checklists.md, not by the unit TCs (which build
  two separate snapshots rather than two open editors).
- Perf (MAINT-25-05) has no automated regression assertion — the O(n²)→O(n) cats-scan and
  `nodes`-copy removal are validated by the full-suite build staying green and manual inspection,
  not a benchmark (a benchmark harness exists — `LuaUnionDistributionBenchmarkTest` — but is not
  wired for the recursive-visitor path; deferred).

## See Also
- Requirements: [requirements.md](requirements.md)
- Design: [design.md](design.md)
