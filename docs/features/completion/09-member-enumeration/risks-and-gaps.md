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
| "This is a performance problem" | **Removed at mint; PARTLY REINSTATED by measurement.** The reframing still holds — the fix is a missing index, not a fifth cache — but the feature is now also gated by a latency budget (NFR-1, COMP-09-08, design §1.9). Both are true; the original wording implied only the first. |
| "The indexes need replacing" | **REFUTED by DR-06/DR-09 — this row was stale for three reviews.** `LuaGlobalDeclarationIndex`'s receiver key is **dot-only**, and a wholly colon-declared class has no key at all (design §1.3), so swapping the scan for it is a correctness regression rather than a simplification (§2). COMP-09-01 is a **new** `FileBasedIndex` (§4.2) — net addition, not deletion. |
| "The stub format must not be bumped" | **The largest architectural premise, and it was unnamed until the fourth review.** It is what forces a second index rather than one line in `LuaFuncStubElementType.indexStub`. It is **not** immovable — §4.8 already accepts reindex boundaries. It is *chosen*: a stub bump reindexes every Lua file for every user, where a new index is additive and its cost is confined to first build. That cost is **not measured**, which is [[DR-18]]. |
| "Navigation is broken too" | **Removed, and it is a trap.** Lookup-by-qualified-name is complete between two indexes (`LuaNameReference:95-111`). A navigation test written here would pass before the change. |
| "One flat < 100 ms time-to-first budget applies to every receiver" | **REMOVED — the feature moves it (design §4.12).** It is not achievable for a receiver the index cannot see through, and an earlier draft handled that by pointing the gate at a fixture that passes. `non-functional.md` now states a two-tier contract, and COMP-09-08 gates tier 1 while *recording* tier 2. |
| "A second whole-project `FileBasedIndex` is the right shape" | **Chosen, cost unmeasured — DR-18.** Its `InputFilter` accepts every `.lua` file. Precedent exists (`LuaMemberFieldIndex`, `LuaGlobalAssignmentIndex` are both whole-project), but the feature's thesis is that session-start latency matters, which is exactly when index build runs. Should not stay `todo` past Phase 1. |
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
- **STATUS (DR-09, 2026-08-08): this risk fired, exactly as written, and was caught by measurement.**
  A flat `membersOf(receiver, allScope)` union returned `[alsoPrivate, privateToThisFile, real]`
  against a golden of `[real]` — the extras came from an unrelated file-local `wx`. The design's
  answer is first-declaring-file-only within a scope-precedence chain (design §4.5), which reproduces
  today's `typeOfGlobalIn`. The risk stays open until the consumers stop scanning (Phase 3) and the
  four corpus baselines are re-run, because until then the superset is fixed only at the index.
- **STATUS (Phase 1 remediation, 2026-08-09): the firing shape above is now an assertion.**
  `testDr09b`, named here as the gate, was a print-only harness and was deleted in Phase 1; its
  membership half is re-homed in `LuaReceiverMemberDoorParityTest` (per-door, all four receivers) and
  **this bullet's exact shape** — a global `wx` plus an unrelated file-local `wx` — in
  `LuaReceiverMemberIndexTest.testAFileLocalReceiverIsNotASelectableDeclaringFile`, which pins
  `[real]` from the completion door against `[alsoPrivate, privateToThisFile, real]` from the union.
  For one commit that half was credited to `CompNineDr14Test.testDr14LocalReceiverIsNotSelectable`,
  which contains no assertion at all.

### BLOCKER (Phase 2, 2026-08-09): the change site design §4.5 specifies is unreachable, and it is downstream of the cost

**Phase 2 was executed to the plan, measured, and stopped. `ABORT_REPLAN`.** No production change was
kept: the tree is back at Phase 1's `0e182b1c`. This section records the measurement so replanning
starts from a run rather than a reading.

**The plan's Phase 2 is:** rewrite `crossFileGlobalMembers` (`LuaCompletionContributor.kt:133-139`) to
consume `globalMembership`, per design §4.5b/§4.5c. That was done exactly as specified — the §4.5c
fork in the contributor, the §4.5b emit-loop split, `LuaMemberLookup.create(LuaReceiverMember)`. The
result was **zero behaviour change**: the golden stayed byte-identical
(`a8c580ccc7a9528c0fde41527d870c48`) and all five new expectation tests failed, each reporting
*today's* answer.

**The reason, probed rather than read.** The fork is guarded by
`if (type == LuaGraphType.Undefined)` at `LuaCompletionContributor.kt:370-378`, where `type` comes
from the in-file `LuaTypesSnapshot`. That snapshot **already resolves a cross-file global to a fully
populated `Table`**, so the guard does not open. Instrumenting the branch and running every
cross-file completion test in the repo:

| receiver | fixture | `type` at the guard | `crossFileGlobalMembers` reached |
| :-- | :-- | :-- | :-- |
| `wx`, `Config`, `M`, `Busted`, `OM`, `Shapes`, `Base`, `Derived` | the golden fixture | `Table(...)` with its members | **no** |
| `table` (bundled stdlib), `Lib` (project file), `assert` (`require`-bound), `Shadow` | `LuaGlobalMemberCompletionTest` | `Table(...)` / `Union(...)` | **no** |
| `wx` | `LuaLibraryMemberCompletionTest`, library root | `Table(...)` | **no** |
| `luassert`, `wxFrame`, `AllColon` | the golden fixture | `Undefined` | **yes — and all three return `<none>`** |

So the branch is reached by exactly the three receivers whose `@class` sits on a **local**, i.e. the
ones that are not globals and have nothing to offer. It is dead for every receiver COMP-09 exists to
serve. Design §4.5's comparison table is headed "measured against today's global door, **the door
this call site actually serves**" — the call site does not serve that door for any receiver with
members, and DR-14 validated `membersOfGlobal` against `resolveGlobal` directly rather than through
the contributor, so nothing in the de-risking would have caught it.

**And the guard is downstream of the cost.** `LuaTypesSnapshot.forFile(receiverExpr.containingFile)`
runs *unconditionally*, before either arm. Timed inside the COMP-09-08 fixture on gce-builder
(us, two cold runs of the 3 600-member receiver):

| | `forFile` | `getValueType` | gate's total cold time-to-first |
| :-- | --: | --: | --: |
| `wx`, run 1 | 1 462 142 | 27 | 1 661 395 |
| `wx`, run 2 | 853 725 | 4 | 878 303 |
| `Opaque` (tier 2) | 143 206 | 2 | 170 936 |

The graph build is **88–97 %** of cold time-to-first and the member lookup the fork replaces is a
rounding error. **COMP-09-08 therefore cannot be flipped by any change at this site**, however
correct the index arm is: by the time the fork is evaluated the budget is already spent. The gate ran
unchanged and still reports the miss (`wide = 878 303 us = 46x` against a derived floor of `2x`).

**Why this was not worked around.** The obvious repair — consult the index *before*
`LuaTypesSnapshot.forFile` for a bare-name receiver — is a new rule, not an implementation detail: it
reorders in-file-versus-global precedence, which is what
`LuaGlobalMemberCompletionTest.aLocalShadowsTheCrossFileGlobal` pins (a file-local `Shadow` must beat
the global `Shadow`), and design §4.5/§4.5b contain no such rule. Inventing one to make the gate green
is the rogue workaround the abort protocol forbids.

**What replanning owes.** (1) A change site above the snapshot build, with a stated shadowing rule.
(2) A restatement of §4.5's premise that `crossFileGlobalMembers` is the completion door — it is not.
(3) A re-derivation of §4.12's two-tier contract: tier 2 (`Opaque`, 143 ms in `forFile` alone) misses
the 100 ms budget through the same snapshot build, so the tiers may not be the distinction that
matters. **Not owed:** any change to `LuaReceiverMemberIndex`, which the probe shows answers exactly
as designed — `Derived` → `[Show, ownFn, ownField]` authoritative, `Shapes` → `[plain, nested,
direct]` authoritative, `wx` → `[wxFileExists, wxID_ANY]` authoritative, `OM` → `[extra]`
**non-authoritative**. Phase 1 is sound; only its consumer is mis-sited.

**The five expectations Phase 2 wrote are correct and should be reused verbatim once the site moves**
— they were run and each failed against today's code, which is the mutation proof the plan asks for:

| expectation | design | observed failure on today's code |
| :-- | :-- | :-- |
| all four indexer sources reach `Sources.` | TC 5 | `fromFieldTag` absent |
| `Derived.` offers `ownField` | §4.5a / TC 7c | `[Show, ownFn]` |
| `Shapes.` no longer offers `deep` | §4.4a / BUG-430 | `[deep, direct, nested, plain]` |
| `Residue.aliased` is a FIELD and vanishes at `Residue:` | §4.3 D3 | offered at `:` |
| the index arm renders no type text | §4.5b / TC 3 | `wxFileExists=fun(filename)` |

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
- **RESOLVED by DR-09 (design §4.2): name + kind + separator, no type text.** The prototype was built
  and measured rather than chosen on paper. Kind is needed by the `isColon` filter and the icon;
  separator is needed to rebuild the stub key in `addMethodsOf` (§4.6); type text is deliberately
  absent, which §4.5 records as a visible behaviour change rather than an omission. Wire format and
  its round-trip are in §4.2.

### Gap 2.2: Whether one member's type can be resolved without materializing its receiver

- **Question**: decides whether incremental yield can carry types lazily (`renderElement` is called
  per *visible row*, so ~15 of them) or whether early rows show no type at all.
- **Options / leaning**: unknown. Inherited from BUG-429, still unestablished. Do not assume.
- **CLOSED as out of scope, not answered.** §1.7 withdrew COMP-09-04b, so completion no longer needs
  per-member types at all — §4.5 sets no type text on the cross-file path. That leaves this bearing
  only on the checker, which §4.1 keeps on `forFile` unchanged. Nothing in COMP-09 now depends on the
  answer, so it is deliberately not resolved here; it returns if anyone reinstates lazy type
  rendering. (The premise it was written on — "`renderElement` is called per visible row, so ~15 of
  them" — is itself false: `BaseCompletionLookupArranger:187` calls it for every element, and
  `getExpensiveRenderer` is the per-visible-row API.)

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
- **RESOLVED: keep it, and it does not touch the index at all.** DR-09 fixed the index shape (§4.2)
  and COMP-09-05 turned out to need nothing from it — the change is one branch in
  `LuaGraphType.fromLuaType`, and DR-12 measured that the completion sets do not move (`v.` offers
  `__add` today, `v:` does not). A COMP-10 split would carry a whole feature's overhead for a
  one-branch change, so it stays.

## Pre-Implementation De-risking Tasks

| ID | Action | Resolves | Status |
| :-- | :-- | :-- | :-- |
| COMP-09-00-DR-01 | Golden enumeration across both entry points | Risk 1.1, 1.2 | **done** — design §1.4. `wx` resolves through *both* doors with different types; `AllColon` (all-colon members) enumerates 2 today and would return 0 under the proposed swap. Golden must record both doors per receiver |
| COMP-09-00-DR-03.2 | §3.2: is the `@class` door dominated by `forFile` or the key loop? | design §3.2 | **done** — design §1.6. Neither: it never calls `forFile` (A measured 1 674 ms cold *after* two resolveType calls). Cold cost is the declaring file's AST parse (352 ms measured on an untouched equivalent); marginal cost 167 ms/class, already over budget. Different bottleneck, **same remedy** |
| COMP-09-00-DR-09 | Prototype `LuaReceiverMemberIndex` and MEASURE it before §4 is rewritten again — membership vs today's golden, `membersOf` timing, externalizer round-trip | design §4.9 D1–D3 | **done** — `LuaReceiverMemberIndex` + `CompNineDr09Test`/`CompNineDr09bTest`. §4 rewritten from the run (design §4.0–§4.11). Results: `membersOf` **2 ms**/3 600 members vs `resolveGlobal` **13 655 ms**, and **0 ms** for an 8-member receiver (the work bound, demonstrated); externalizer round-trip **exact**; membership exact on 3 of 4 receivers. D1 and D2 both **confirmed by measurement** and fixed by first-declaring-file-within-scope-precedence; D3 **partly real** and now bounded. The 4th receiver's mismatch was traced to the engine, not the index, and filed as **BUG-430** |
| COMP-09-00-DR-10 | Establish what `crossFileGlobalMembers` does during indexing today, and match it — §4.9 asserts `DumbService` handling is required but DR-09 measured none of it | design §4.9 | **done** — `CompNineDr10Test`, design §4.9. Dumb: `resolveGlobal` -> null (guarded, `:129`), `materialize` -> null, completion -> `[]` **without throwing**; the prototype's `membersOf` -> **throws `IndexNotReadyException`**, so §4.9's obligation is real and unmet. Phase 1's rule: return empty when dumb. Also found `resolveType` has **no** guard and turns the signal into an IDE internal error — **BUG-432** |
| COMP-09-00-DR-03.1 | §3.1: can a type be answered without `forFile`? | design §3.1 | **done** — design §1.5. Not the type; but member *names* can, from an index **value**, at key-lookup speed.  ⚠ the "`getAllKeys` 44 ms / `getElements` 1.5 ms" figures this row once carried are **WITHDRAWN** (design §1.5, §2): they compared two different index subsystems and printed a filtered match count. The surviving support is design §1.9 and §4.10b |
| COMP-09-00-DR-06 | Does `getElements(KEY, receiver)` cover the colon form? | COMP-09-01's premise | **done — NO.** Dot-only; `getElements(KEY, "ColonHost")` → `[ColonHost.staticDot]`. See design §1.3 |
| COMP-09-00-DR-02 | Bucket the cost | critical path | **done.** `resolveGlobal` 9 568 ms / `materialize` 10 ms / `getMembers` 0 ms. The hot path is `LuaTypesSnapshot.forFile` on the declaring file, not enumeration. See design §1.1 |
| COMP-09-00-DR-02c | Per-keystroke or per-session? | severity | **done, and it overturned §1.2 — then Phase 0 re-ran it and overturned its own precision.** `CompNineDr13Test`, medians of 5, time-to-**first**. First run: cold **1 152 ms**, warm **1.0 ms**, after one unrelated keystroke **264 ms** (22 % repaid). Phase 0 re-run (2026-08-09): cold **463 ms**, warm **0.9 ms**, after one keystroke **223 ms** (48 % repaid). A third, independent run during the Phase 0 review got cold **392 ms** — three runs, three cold figures spanning 3x. **Verdict: the direction is stable and the ratio is not** — cold is hundreds of ms and far over budget, warm is ~1 ms, an unrelated keystroke costs 200 ms+ and is over budget on its own. The harness's printed per-keystroke/per-session verdict turns on `afterEdit > cold/2` and was decided by 8 ms of noise, so that dichotomy is not just false but **unmeasurable by this harness**; nothing in the plan may cite the repaid fraction. Design §1.2 |
| COMP-09-00-DR-02a | Build a harness that observes the **first** lookup element — `completeBasic()` returns only when completion finishes, so no time-to-first-result figure exists for any fixture, including the ones this plan quotes | NFR-1 (unmeasured), TC 2 | **done** — `CompNineDr02aTest`, design §1.9. A contributor registered `LoadingOrder.FIRST` calling `runRemainingContributors` times each result inside a real `completeBasic()`. Cold time-to-first **746 ms** vs the 100 ms budget; gap to exhaustive **31 ms (4 %)**, so first == exhaustive is now measured, not structural; warm **1.1 ms**; and time-to-first scales **40x** with member count (41 ms for 3 members, 1 641 ms for 3 600), which violates the NFR's independence clause outright. This is COMP-09-08's mechanism |
| COMP-09-00-DR-02 | Instrument the four buckets (`resolveGlobal`, `:328` scan, `:421` scan, remaining materialize) and measure each **against the existing 100 ms target** — the budget is not ours to set | COMP-09 NFR, Gap 2.2 | **superseded** — §1.1 bucketed the cost, §1.5/§1.6 measured both doors, and §1.9 is the comparison against the target done properly, on time-to-**first** rather than time-to-exhaustive. Nothing is left that this would add |
| COMP-09-00-DR-03 | Prototype the index shape against the wx tree; decide name-only vs name+kind | Gap 2.1, 2.3 | **done** — superseded by DR-09, which built and measured it. name+kind, with `Kind` from §4.3's three sources |
| COMP-09-00-DR-11 | Instrument "entries traversed" for COMP-09-09 — a timing probe cannot answer a count | COMP-09-09 (undesigned) | **done** — `CompNineDr11Test`, design §4.10b. `processValues` callbacks counted at the traversal site: 50 entries / 1 file for a 50-member receiver, **unchanged** by 4 000 unrelated stub keys. Ships as a `ThreadLocal`, not the spike's `@Volatile` global |
| COMP-09-00-DR-19c | Is `globalMembership`'s authority flag stable, given `getContainingFiles` is unordered? | design §4.5c, round-six NB | **done, and it was NOT non-blocking.** Round six filed the ordering hazard as non-blocking because today's `typeOfGlobalIn` shares it. Adding assertions to DR-19 (previously print-only) made two runs of one fixture disagree about `assert`. Fixed by computing opacity over **every** candidate file, not the one selection picks: a wrong `authoritative = true` drops members, a wrong `false` only costs latency |
| COMP-09-00-DR-19 | Round five BLOCKER 2: DR-15's "fall back when empty" rule loses members on a mixed receiver, and its own harness asserted `today == today` | design §4.5c | **done** — `CompNineDr19Test`. Two remedies measured wrong before the third worked: (1) empty-means-fall-back loses `VERSION` from `M = { VERSION } + function M.f()`; (2) indexing literal fields fixes that but not `OM = require(x) + function OM.extra()`. The design is a **binding-opacity sentinel**: all five shapes MATCH today, including both counterexamples. Also relocated the fallback to the contributor — the index companion cannot call `resolveGlobal` (no `PsiElement` anchor) or return `getMembers()`'s type |
| COMP-09-00-DR-15 | Fourth Step 9 review, BL-2: does the index see members that are not written syntactically against the receiver? | design §4.5c | **done — the deepest finding here.** `CompNineDr15Test`. `Config = { host = …, port = … }` → today `[host, port]`, index **`[]`**. Every DR-14 receiver used `R = {}` with members assigned separately, the one shape that hides it. Remedy measured: fall back to `resolveGlobal` on an empty index result — `matchesToday=true` for all three shapes. The review also predicted the `require` case breaks; **it does not** — `resolveGlobal("assert")` returns nothing today, so that door is not the one those tests use |
| COMP-09-00-DR-16 | Re-measure the `catsClassTags` / `LuaImplicitFields:76` / `LuaTypesVisitor:1349` walks with medians of ≥5, against the **right door** for each | COMP-09-02 scope (design §4.11) | **partly done (Phase 0)** — the medians half is settled and it makes the old scope-out look worse, not better: `CompNineSection32Test` candidate B (the `catsClassTags`-shaped walk) is **29 ms median of 5** (24/25/29/46/52) against a **41 ms** warm-file `@class` door, i.e. roughly *two thirds* of that door's marginal cost, not the 2 % the retracted "22 ms of 949 ms" implied. Candidate C (`getAllKeys` 16 180 keys + `getElements`) is **18 ms**. Still owed: the per-site right-door analysis — `LuaTypesVisitor:1349` is on the `resolveGlobal` door and neither figure applies to it. The scope decision does not change: §4.3/§4.6 do not touch these sites |
| COMP-09-00-DR-17 | Derive COMP-09-08 assertion 2's independence factor rather than picking one | design §4.10a, COMP-09-08 | **done by construction (Phase 0)** — the rule is not applied once and frozen, it is **evaluated inside the gate on every run**: `ceil(p95 / p50)` over five cold 3-member receivers, each in its own file, so member count cannot be the variable. Measured 2x on one run (10 018–11 604 us) and 3x on the next (12 880–41 205 us) — which is exactly why a frozen constant would have been wrong — while the wide-vs-narrow ratio was 64x and 37x. Red under either floor |
| COMP-09-00-DR-18 | Measure what a second whole-project `FileBasedIndex` costs to build — its `InputFilter` accepts every `.lua` file | design §4.8, premises table | todo — the feature's thesis is that the first completion of a session is what matters, which is exactly when indexing runs. The build cost is asserted nowhere and measured nowhere |
| COMP-09-00-DR-14 | Third Step 9 review (B2, B5, B8): §4.5's scope rule was read off the tail of a call chain; the golden collapsed both doors with `?:`; COMP-09-03's inherited-members clause had no design | design §4.5, §4.4a, §4.5a | **done** — `CompNineDr14Test`. `membersOfGlobal` selects via `LuaGlobalAssignmentIndex` and matches the **global** door exactly on `wx`, `wxFrame` (`[]`, as today), `AllColon` (`[]`) and `Shapes` (minus `deep`, BUG-430). Per-door golden printed separately: two of four receivers resolve through only one door. Inherited members are **not** on today's completion door (`Derived.` offers `[ownFn]`), so the flat list is no regression. **New finding:** indexing `@field` adds `Derived.ownField` to completion — a superset the reviews missed, now declared and gated (TC 7c) |
| COMP-09-00-DR-12 | Settle the two checklist questions argued in prose across two reviews: does a `@class` `__add` complete today, and does `Foo.` offer `baz` for `Foo.bar.baz` | checklist 2.2 + 3.1 | **done** — `CompNineDr12Test`. **Both resolved against the plan.** `v.` offers `[__add, len, x]` and `v:` offers `[len]`, so design §4.7 was right; `Foo.` offers `[bar, baz, direct]` and `Foo.bar.` offers `[]`, confirming BUG-430 is user-visible, not an API artefact |
| COMP-09-00-DR-04 | ~~Incremental yield vs memoization~~ | Risk 1.3 | **withdrawn** — COMP-09-04 withdrawn (design §1.7), so nothing yields partially and no cache holds a partial result |
| COMP-09-00-DR-07 | §3.3: would narrowing cache invalidation beat indexing? Previously only a "TBD" under Technical Debt with no task — the DoD requires every open question be tracked | design §3.3 | **done — DECIDED, design §1.2a.** Narrowing takes the post-edit case from ~200 ms to ~1 ms but cannot touch cold — hundreds of ms (392 / 463 / 1 152 across three runs), every session's first completion, over budget under all three — or the member-count scaling. The decision rests on the **direction only**: per DR-08 no ratio between two of this harness's figures is quotable, and the earlier wording here quoted three. Complement, not alternative: index now, narrow separately |
| COMP-09-00-DR-08 | Re-measure every quoted figure with medians of ≥5 before it is cited anywhere. Step 9 showed −60 % run-to-run spread and one flipped verdict | design §1.8 | **done (Phase 0)** — the three remaining single-shot harnesses now median five runs through a shared `Medians` helper: `CompNineDrSpikeTest` (§1.1 buckets), `CompNineDr01Test` (§3.1) and `CompNineSection32Test` (§3.2 candidates B/C). §1.2 was re-run and its ratio disavowed (DR-02c row). The narrow-vs-broad *prefix* pair is withdrawn, not owed. A quantity that is unrepeatable by construction — a cold snapshot build is warm the second time — is now labelled `(single — unrepeatable by construction)` rather than averaged with warm samples, which is the error that once reported "1 ms vs 0 ms". **Standing consequence: the spread is wide enough that no *ratio* between two harness figures is quotable** — DR-02c's repaid fraction moved 22 % → 48 % between two clean medians-of-5 runs |
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
