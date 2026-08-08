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

### Risk 1.3: Incremental yield conflicts with memoized results

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
- **Resolved by**: DR-02.

### Gap 2.3: Whether metamethods belong here

- **Question**: COMP-09-05 is the only source with no index and no cross-file path. It may be a
  feature of its own.
- **Options / leaning**: keep it if the enumeration API absorbs it cheaply; split to COMP-10 if it
  needs its own model. Decide **after** DR-03 fixes the index shape, not before.
- **Resolved by**: DR-03's outcome.

## Pre-Implementation De-risking Tasks

| ID | Action | Resolves | Status |
| :-- | :-- | :-- | :-- |
| COMP-09-00-DR-01 | Record today's exact enumeration result — member set, types, order — for a library global, a `@class`, and a local-declared class, as a golden file | Risk 1.1, 1.2 | todo — **blocks everything** |
| COMP-09-00-DR-02a | Build a harness that observes the **first** lookup element — `completeBasic()` returns only when completion finishes, so no time-to-first-result figure exists for any fixture, including the ones this plan quotes | NFR-1 (unmeasured), TC 2 | todo — **blocks NFR-1** |
| COMP-09-00-DR-02 | Instrument the four buckets (`resolveGlobal`, `:328` scan, `:421` scan, remaining materialize) and measure each **against the existing 100 ms target** — the budget is not ours to set | COMP-09 NFR, Gap 2.2 | todo |
| COMP-09-00-DR-03 | Prototype the index shape against the wx tree; decide name-only vs name+kind | Gap 2.1, 2.3 | todo |
| COMP-09-00-DR-04 | Establish whether incremental yield and the existing memoization can coexist, or whether only completion yields incrementally | Risk 1.3 | todo |
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
- **TBD: `LuaImportNameResolver`'s whole-file walks** (`:44,79,82`) enumerate *local* declarations,
  a different question with the same shape. Out of scope; recorded so the next audit finds it.

## See Also

- Requirements: [requirements.md](requirements.md)
