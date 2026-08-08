---
id: "COMP-09-RISKS"
title: "Risks & Gaps"
type: "risk"
parent_id: "COMP-09"
folders:
  - "[[features/completion/09-member-enumeration/requirements|requirements]]"
---

# COMP-09: Risks & Gaps

## Premises examined

| Constraint treated as fixed | Verdict |
| :-- | :-- |
| "This is a performance problem" | **Removed.** It is a missing question. Enumeration is not slow because the code is inefficient; it is slow because no index answers "members of X" and every caller improvises. Framing it as perf produces a fifth cache. |
| "The indexes need replacing" | **Removed.** `LuaGlobalDeclarationIndex` already sinks receiver keys (`LuaFuncStubElementType:69-75`). Two of the three worst sites are brute-forcing a query it already answers. Most of COMP-09-01 is deletion. |
| "Navigation is broken too" | **Removed, and it is a trap.** Lookup-by-qualified-name is complete between two indexes (`LuaNameReference:95-111`). A navigation test written here would pass before the change. |
| "The existing caches should go" | **Not assumed.** They may be load-bearing for reasons unrelated to enumeration. Each is re-measured (acceptance criterion), not deleted on principle. |
| "Metamethods are part of this" | **Chosen, and arguable.** They are the same missing direction, but the only source with *no* index and *no* cross-file path — a bigger build than the rest. Splitting to COMP-10 is a live option (Gap 2.3). |
| "The type engine is where enumeration lives" | **Genuinely fixed** for now — COMP-04 already reaches into `LuaTypesVisitor` in three places, so this is precedent, not a new straddle. |

## Critical Risks

### Risk 1.1: Enumeration becomes a new type source

- **Impact**: the failure BUG-395 already hit and reverted — "handing the checker types it never
  had turns previously-unchecked calls into checked ones and regressed four suites at once"
  (BUG-397). An index that returns *more* members than the eager path did will do exactly this,
  silently, and TARGET-10 multiplies the blast radius to ~10 000 wxLua contracts.
- **Likelihood**: **high**. The natural implementation — "index everything, look it up" — produces a
  superset by construction, because the eager path is filtered by scope and file confinement
  (`MethodScan.onlyIn`) that an index key does not carry.
- **Mitigation**: COMP-09-06 as a hard gate — all four corpus baselines re-run, and **any** movement
  stops the change. Not "investigate the movement": stop. Plus DR-01, which pins the exact
  membership of today's result *before* anything is replaced, so the comparison is against a
  recorded set rather than a memory.

### Risk 1.2: The scope and file-confinement semantics are lost in translation

- **Impact**: subtly wrong members. `addMethodsOf` carries `MethodScan(className, receiver, onlyIn)`
  — a match on the class name is honoured project-wide, a match on a declaring local's name is
  confined to that local's file (BUG-398's rule). `catsClassTags` filters
  `argType?.text?.trim() == name` after narrowing. These are behavioural rules living inside the
  scan, and a key lookup does not reproduce them for free.
- **Likelihood**: high — this is the most likely way COMP-09-07 breaks.
- **Mitigation**: enumerate the rules explicitly in the design before writing code; each becomes a
  test at the enumeration boundary, not only end-to-end.

### Risk 1.3: ~~Incremental yield conflicts with memoized results~~ — dissolved by §1.7

Withdrawing COMP-09-04 removes this risk: nothing yields partially, so no cache has to hold a partial
result. DR-04 is correspondingly withdrawn. Retained below as the reasoning, because it returns
verbatim if COMP-09-04 is reinstated.

### Risk 1.3 (superseded): Incremental yield conflicts with memoized results

- **Impact**: today's callers get a complete `Map<String, …>` and several memoize it (`typeCache`,
  `LuaTypes` per-file cache). An enumeration that yields incrementally either has to complete before
  it can be cached — losing the benefit — or the caches must hold partial results, which is a
  correctness hazard.
- **Likelihood**: medium, and it is a design fork rather than a bug.
- **Mitigation**: DR-04. The likely answer is that only the *completion* consumer needs incremental
  yield and materialization keeps its complete-then-cache contract — but that must be established,
  not assumed, because it decides the API shape.

### Risk 1.4: This is a large change to the least-covered seam

- **Impact**: member enumeration is reached by completion, type materialization, the checker and
  documentation. Regressions surface as absent completions, which are hard to notice.
- **Mitigation**: `LibraryRootTestCase` exists precisely for this (it registers a real
  `SyntheticLibrary` root because "a projectScope-vs-allScope defect is structurally invisible to an
  ordinary `BasePlatformTestCase`" — the blind spot that let BUG-395/398 ship green). Every new
  enumeration test belongs there, not in a light fixture.

## Design Gaps

### Gap 2.1: What the index's value should be

- **Question**: member *names* only (cheap, stub-level, forces a second step for types), or names
  plus enough to render a lookup element without touching PSI?
- **Options**: name-only keeps the index small and the invalidation simple, but every element then
  needs a type from somewhere; name+kind+type-text is a bigger index and a stale-data risk.
- **Resolved by**: DR-03.

### Gap 2.2: Whether one member's type can be resolved without materializing its receiver

- **Question**: decides whether incremental yield can carry types lazily (`renderElement` is called
  per *visible row*, so ~15 of them) or whether early rows show no type at all.
- **Options / leaning**: unknown. Inherited from BUG-429, still unestablished. Do not assume.
- **Resolved by**: NOT DR-02 — Step 9 caught that DR-02 is `done` while never testing this, so the
  tracker showed a closed resolver for an open question. Needs its own task; and note §1.7's
  withdrawal of COMP-09-04b means completion no longer *needs* per-member types lazily, so this gap
  now bears only on the checker.

### Gap 2.5: ~~Whether COMP-09-04 should be withdrawn~~ — DECIDED, withdrawn (design §1.7)

- **Question**: §1.5 and §1.6 put member *names* at key-lookup speed with no stub or PSI load. If the
  exhaustive set arrives in milliseconds there is no long tail to stream, and COMP-09-04 —
  the only requirement touching the completion contributor rather than the index — solves a problem
  that no longer exists.
- **Options / leaning**: withdraw it, keeping the NFR's incremental/cancellable clause as a property
  the implementation must not *break* rather than a feature it must add. Leaning withdraw; it is the
  most invasive part of the feature and the least supported by measurement.
- **Decided 2026-08-07**: withdrawn, replaced by COMP-09-04b (lazy type rendering). Names become fast,
  types stay slow *per element*, and `renderElement` is called per visible row — so the remaining
  problem is presentation cost, not discovery cost. Design §1.7 records the full reasoning.
- **What would reverse it**: Phase 1 measuring a value-carrying index lookup as slow. That is the only
  input that reinstates COMP-09-04, and it is flagged in §1.7 as an unmeasured premise rather than
  buried.

### Gap 2.3: Whether metamethods belong here

- **Question**: COMP-09-05 is the only source with no index and no cross-file path. It may be a
  feature of its own.
- **Options / leaning**: keep it if the enumeration API absorbs it cheaply; split to COMP-10 if it
  needs its own model. Decide **after** DR-03 fixes the index shape, not before.
- **Resolved by**: DR-03's outcome.

## Pre-Implementation De-risking Tasks

| ID | Action | Resolves | Status |
| :-- | :-- | :-- | :-- |
| COMP-09-00-DR-01 | Golden enumeration across both entry points | Risk 1.1, 1.2 | **done** — design §1.4. `wx` resolves through *both* doors with different types; `AllColon` (all-colon members) enumerates 2 today and would return 0 under the proposed swap. Golden must record both doors per receiver |
| COMP-09-00-DR-03.2 | §3.2: is the `@class` door dominated by `forFile` or the key loop? | design §3.2 | **done** — design §1.6. Neither: it never calls `forFile` (A measured 1 674 ms cold *after* two resolveType calls). Cold cost is the declaring file's AST parse (352 ms measured on an untouched equivalent); marginal cost 167 ms/class, already over budget. Different bottleneck, **same remedy** |
| COMP-09-00-DR-03.1 | §3.1: can a type be answered without `forFile`? | design §3.1 | **done** — design §1.5. Not the type; but member *names* can, from an index **value**, at key-lookup speed. `getAllKeys` over 25 335 keys is 44 ms — cheap; `getElements` is 1.5 ms per element and is the real cost |
| COMP-09-00-DR-06 | Does `getElements(KEY, receiver)` cover the colon form? | COMP-09-01's premise | **done — NO.** Dot-only; `getElements(KEY, "ColonHost")` → `[ColonHost.staticDot]`. See design §1.3 |
| COMP-09-00-DR-02 | Bucket the cost | critical path | **done.** `resolveGlobal` 9 568 ms / `materialize` 10 ms / `getMembers` 0 ms. The hot path is `LuaTypesSnapshot.forFile` on the declaring file, not enumeration. See design §1.1 |
| COMP-09-00-DR-02c | Per-keystroke or per-session? | severity | **REOPENED.** Step 9 re-ran the same harness and it printed the *opposite* verdict (214 ms vs a 244 ms threshold → "once per session"). Single-shot timing. The mechanism is sound by reading (`LuaTypes.kt:214-222`) but the claim is not measured. Redo with medians of ≥5 |
| COMP-09-00-DR-02a | Build a harness that observes the **first** lookup element — `completeBasic()` returns only when completion finishes, so no time-to-first-result figure exists for any fixture, including the ones this plan quotes | NFR-1 (unmeasured), TC 2 | todo — **blocks NFR-1** |
| COMP-09-00-DR-02 | Instrument the four buckets (`resolveGlobal`, `:328` scan, `:421` scan, remaining materialize) and measure each **against the existing 100 ms target** — the budget is not ours to set | COMP-09 NFR, Gap 2.2 | todo |
| COMP-09-00-DR-03 | Prototype the index shape against the wx tree; decide name-only vs name+kind | Gap 2.1, 2.3 | todo |
| COMP-09-00-DR-04 | ~~Incremental yield vs memoization~~ | Risk 1.3 | **withdrawn** — COMP-09-04 withdrawn (design §1.7), so nothing yields partially and no cache holds a partial result |
| COMP-09-00-DR-07 | §3.3: would narrowing cache invalidation beat indexing? Previously only a "TBD" under Technical Debt with no task — the DoD requires every open question be tracked | design §3.3 | todo |
| COMP-09-00-DR-08 | Re-measure every quoted figure with medians of ≥5 before it is cited anywhere. Step 9 showed −60 % run-to-run spread and one flipped verdict | design §1.8 | **partly done** — class door and per-member cost re-measured; §1.2 and the narrow-vs-broad pair still owed |
| COMP-09-00-DR-05 | ~~Land BUG-429's two-site fix first~~ — **withdrawn.** It cannot precede DR-01: replacing the scans changes enumeration, and DR-01 exists to record what enumeration returns *before* that happens. The scan replacement is COMP-09-01's first increment, after DR-01, not a shortcut around it | — | withdrawn |

### Risk 1.5: The performance suite cannot fail, so the next regression is equally invisible

- **Impact**: `GlobalSymbolCompletionPerformanceTest` asserts `phase1Time > 0` and is excluded from
  the routine loop. A 129× miss against `non-functional.md:13` went unnoticed until a feature
  happened to trip over it. Landing COMP-09 without COMP-09-08 restores exactly that condition.
- **Likelihood**: certain, absent COMP-09-08 — it is the current state.
- **Mitigation**: COMP-09-08, mutation-proved (TC 8): the assertion must be shown to fail on today's
  code before the fix lands, or it is another test that cannot fail.

## Technical Debt & Future Work

- **TBD: the four caches.** If enumeration becomes cheap, `typeCache`, the two sibling
  `CachedValue`s, `LuaTypes`' per-file cache and `GlobalSymbolRankingService`'s may each be
  redundant. Removing a cache is a separate, measurable change — not a side effect of this one.
- **TBD: narrowing cache invalidation** may beat indexing. If the library snapshot survived edits to
  unrelated files, the cost would be once per session per file rather than per keystroke (design
  §1.2). That could be a smaller change than a new index — and it is not obviously safe, which is why
  it is a question (design §3.3) and not a plan.
- **TBD: `LuaImportNameResolver`'s whole-file walks** (`:44,79,82`) enumerate *local* declarations,
  a different question with the same shape. Out of scope; recorded so the next audit finds it.

## See Also

- Requirements: [requirements.md](requirements.md)
