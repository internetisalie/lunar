---
id: "MAINT-28-RISKS"
title: "Risks & Gaps"
type: "risk"
parent_id: "MAINT-28"
folders:
  - "[[features/maint/28-completion-correctness/requirements|requirements]]"
---

# MAINT-28: Risks & Gaps

## Critical Risks

### Risk 1.1: originalFile switch breaks the in-file scope walk
- **Impact**: if the #24 change accidentally routes the `addSymbolCompletions` scope walk or the
  member-completion snapshot to `parameters.originalFile`, local/param completion and member
  completion break — the caret's live PSI lives in the **copy**, not the original.
- **Likelihood**: medium (the two concerns are one word apart at the call sites).
- **Mitigation**: the design §3.6 table classifies every call site; only the cross-file provider's
  index/proximity path moves. TC-39 (in-scope locals still offered) and the existing member
  completion tests are the regression fence. Enforced by DR-01.

### Risk 1.2: #40 exclusion over-broadens and drops legitimate globals
- **Impact**: if `funcNamePropertyList.isNotEmpty()` also matches a case we want offered, real
  project globals disappear from completion.
- **Likelihood**: low — a bare `function name()` has an empty property list and no method node
  (verified against `LuaFuncName.java:11-14`); only `Class:m` / `M.fn` / `a.b.c` are excluded, which
  is exactly the #40 target.
- **Mitigation**: TC-40 asserts a bare global is still offered alongside the receiver exclusion.

### Risk 1.3: #25 corrected bound mis-fires on nested blocks
- **Impact**: `DefaultForceIndent` on the wrong Enter (e.g. after the terminator) would double-indent.
- **Likelihood**: low — `getParentOfType(leaf, LuaBlockParent…, strict=false)` returns the nearest
  enclosing owner, whose `terminator` is its own; the `(openerStart+1)..terminatorStart` range is
  bounded to that owner's span.
- **Mitigation**: TC-25 plus a negative case (Enter *after* `end` → `Continue`).

## Design Gaps

_None open._ Every decision the design left implicit at intake was resolved and folded into
`design.md`:

- The originalFile-vs-copy line per call site → §3.6 table (was the intake's "critical tension").
- Which of the three #39 guards survive → §3.3 (keep `:216`; delete `:241` + IDENTIFIER provider).
- The #40 skip predicate → §3.2 (`funcNameMethod`/`funcNamePropertyList`, grounded PSI, not
  string-parsing the stub key).
- The #62 prefix source → §3.5 (`result.prefixMatcher.prefix`, not `prevLeaf` text).
- Where the §2.5.5 key cache lives → §3.4 (`CachedValue` fields on the existing
  `GlobalSymbolRankingService` `@Service`, `MODIFICATION_COUNT`-invalidated).
- Whether the member-snapshot claim is real → §3.4 re-verification: **partly moot**, the snapshot
  is already UserData-memoized per file-text; no member-provider change.

## Technical Debt & Future Work

- **TBD: converge `LuaBlockPairs` `if`-pair inconsistency** — `LuaPairedBraceMatcher` keys `if`→`end`
  while the Enter handler keys `then`→`end` (`LuaBlockPairs.kt:12-13`). Out of scope for MAINT-28;
  noted for a future EDITOR pass.
- **TBD: the member-provider `checkTypes` 5 s wall-clock bound** — the underlying O(n²)
  `findChildrenOfType(LuaCatsComment)` snapshot cost (`docs/review.md:245-246`) is a **type-engine**
  concern, out of this feature's scope (consume the engine only). Tracked separately in the review's
  §2.5.5 type-engine items.

## Pre-Implementation De-risking Tasks

| ID | Action | Resolves | Status |
|----|--------|----------|--------|
| MAINT-28-00-DR-01 | Before Phase 1, add TC-39 (in-scope locals still offered) and confirm the existing member-completion tests are green on `main` (ff1dfbd5) — establishes the regression fence for the originalFile switch. | Risk 1.1 | done — TC-39/TC-39b added; member/PSI/type-inferred completion suites green through every phase; full suite 2191/0/1. |
| MAINT-28-00-DR-02 | Confirm `LuaFuncName.getFuncNameMethod()` is non-null for `function Class:m()` and `getFuncNamePropertyList()` non-empty for `function M.fn()` via a one-off `configureByText` + PSI dump, before relying on it in §3.2. | Risk 1.2 | done — `LuaFuncName.java` confirms `@Nullable getFuncNameMethod()` / `@NotNull List getFuncNamePropertyList()`; TC-40 asserts the receiver exclusion end-to-end. |
| MAINT-28-00-DR-03 | Confirm `CachedValue` on a `@Service(Level.PROJECT)` field survives across completion invocations (value recomputed only on `MODIFICATION_COUNT` bump) by asserting a call counter — mirrors `LuaRockspecDiscoveryService`. | §2.5.5 correctness | done — TC-25p asserts `assertSame` List identity across reads and `assertNotSame` after a PSI edit. |

## Test Case Gaps

- No test currently exercises the enter-between-blocks handler in real-flow; TC-25 introduces it.
- No test asserts the `getAllKeys` caching; TC-25p introduces a counter/identity assertion.

## See Also
- Requirements: [requirements.md](requirements.md)
- Design: [design.md](design.md)
