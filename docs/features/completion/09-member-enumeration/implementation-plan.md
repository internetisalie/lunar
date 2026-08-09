---
id: "COMP-09-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "COMP-09"
folders:
  - "[[features/completion/09-member-enumeration/requirements|requirements]]"
---

# COMP-09: Implementation Plan

> **Hard gate before Phase 1.** DR-01's golden file must exist and be checked in. The natural
> implementation returns a **superset** — the eager path carries scope and file-confinement rules
> (`MethodScan.onlyIn`, BUG-398) that an index key does not — and a superset silently makes
> enumeration a new type source, which is BUG-395's reverted experiment (BUG-397, four suites).
> Nothing in Phase 1 may land before the golden file records what enumeration returns today.

> **DR-09 is done (2026-08-08) and §4 has been rewritten from it** — design §4.0–§4.11. The
> prototype exists (`LuaReceiverMemberIndex`, registered, nothing consumes it) and was measured:
> `membersOf` **2 ms** for 3 600 members against `resolveGlobal`'s **13 655 ms** on the same fixture,
> **0 ms** for an 8-member receiver, exact externalizer round-trip, and exact membership on 3 of the
> 4 golden receivers.
>
> **Two DR-09 findings change this plan.** (1) D1 and D2 were both *confirmed by measurement*, so
> §4.5's scope rule is now first-declaring-file within `projectScope`-then-`allScope` — Phase 1 must
> implement that, not the union the prototype currently has. (2) The one membership mismatch is
> **BUG-430**: the global and `@class` doors disagree about `a.b.c = v`, and the global door is wrong
> twice. COMP-09 preserves the **`@class` door** and deliberately does not reproduce the flattening;
> COMP-09-07's bar is redefined accordingly (design §4.4a) and Phase 0's golden must record which
> door each receiver is measured through.
>
> **DR-10 and DR-02a are both done (2026-08-08).** Nothing blocks Phase 1 now.
>
> DR-10 (design §4.9): while dumb, `resolveGlobal` returns null, completion offers `[]` and nothing
> throws — but the prototype's `membersOf` **throws `IndexNotReadyException`**, so Phase 1 must make
> it return empty. It matches `resolveGlobal`'s behaviour deliberately and **not** `resolveType`'s,
> which DR-10 found is itself defective (**BUG-432** — it logs an IDE internal error during
> indexing).
>
> DR-02a (design §1.9) built the first-element observer NFR-1 was blocked on: cold time-to-first is
> **746 ms** against a 100 ms budget, the gap to exhaustive is **31 ms (4 %)** — so first ==
> exhaustive is measured now, not argued — and time-to-first scales **40x** with member count, which
> violates the NFR's independence clause. **COMP-09-08 is designed off it (§4.10a) and both its
> assertions are red today**, so the mutation proof Phase 0 asks for is already in hand.
>
> DR-11 (design §4.10b) supplies COMP-09-09's instrument, the last requirement without one:
> `processValues` callbacks counted at the traversal site. `entriesTraversed` is 50 for a 50-member
> receiver and **unchanged** by 4 000 unrelated stub keys.
>
> DR-12 settled two questions this plan had carried, unexecuted, through two reviews — see Phase 4's
> TC 6a and Phase 0's golden note. Both were resolved **against** what this plan said.

> **Two standing rules.** (1) Any figure quoted in a doc or a commit is a **median of ≥5**; design
> §1.8 records a −60 % single-shot spread and one flipped verdict. (2) No benchmark may cross a
> reindex boundary (design §4.8).

## Phase 0: Golden file and instrument — **DONE 2026-08-09**

- **Goal**: record today's behaviour before touching it, and make the gates able to fail.
- **Tasks**:
  - [x] Promote `CompNineDr01Test`'s enumeration dump into a checked-in golden covering, for **both**
        `resolveGlobal` and `resolveType` per receiver (design §1.4 — `wx` answers differently through
        each): a namespace global, a `@class` with dot *and* colon members, and an all-colon `@class`.
        **Label each receiver with the door it is measured through** — design §4.4a: for `a.b.c = v`
        the two doors disagree and the global door's answer is BUG-430, so an unlabelled golden would
        certify a bug as the contract.
        → `src/test/resources/comp09/member-enumeration.golden`, written by
        `MemberEnumerationGoldenTest`. Each row is `<receiver>|<door>|<member>:<type>`; the two doors
        are never collapsed. A **third** door is recorded that the task did not ask for — what
        `R.<caret>` actually offers — because Phase 2's exit diffs completion membership and today's
        completion path takes the *first* declaring file only, so Phase 3's materialization diff
        cannot cover it.
  - [x] **Standing item — the golden must carry every binding shape, not every member style.** Three
        DR rounds each missed a defect because their fixtures shared one shape. The golden carries,
        at minimum: `R = {}` + syntactic members; `R = { a = 1 }` (literal); `R = { a = 1 }` +
        syntactic (mixed); `R = require(x)` (opaque); `R = require(x)` + syntactic (opaque + mixed);
        a `@class` on a **local**; an all-colon `@class`; and `a.b.c = v`. Adding a *member style* is
        cheap and finds little; adding a *binding shape* is what found BL-2 and BLOCKER 2.
        → All eight are in `Comp09GoldenFixture`, one receiver per file. The opaque receiver is named
        `Busted`, not `assert`: a golden that shares a receiver name with a bundled stdlib stub would
        record whichever declaring file the unordered `getContainingFiles` returned.
  - [x] Add the `LuaOverrideLineMarkerProvider` case to the golden — `sourceElement` is load-bearing
        (design §4.1) and `materializeClass:256-262` warns the parity harness cannot see it.
        → Four `override|…` rows recording the **source PSI class and file** per super member, so the
        Implement-vs-Override distinction is pinned: `Derived:Show` → `LuaFuncDeclImpl`,
        `Derived:onClose` → `LuaCatsFieldTagImpl`, `Derived:ownFn` → `<none>`.
  - [x] Promote `CompNineDr02aTest`'s probe into **COMP-09-08**'s gate — design §4.10a. Both
        assertions (cold time-to-first vs budget; narrow-vs-wide receiver) are **already red**
        (§1.9), so the "must fail first" obligation is met by construction; what still needs proving
        is the reverse direction after Phase 2, and assertion 2's factor is not yet chosen.
        → Probe promoted to `FirstElementProbe` / `TimedCompletionTestCase`;
        `MemberEnumerationLatencyGateTest` holds both assertions. **Armed and run red on today's
        code**: `cold time-to-first for wx. was 1054 ms against a 100 ms budget`, and
        `time-to-first scales 64x with member count against a measured noise floor of 2x`. Assertion
        2's factor is **derived at run time by DR-17's stated rule** — `ceil(p95/p50)` over five cold
        3-member receivers, each in its own file — rather than picked; it measured 2x
        (10 018/10 142/10 392/10 620/11 604 us).
  - [x] Promote `CompNineDr11Test`'s counter into **COMP-09-09**'s gate — design §4.10b. Three
        assertions: `entriesTraversed >= membersIn(R).size`; `entriesTraversed` for R **unchanged**
        between a quiet and a noisy project; `filesVisited ==` the declaring-file count. Ship the
        counter as a **`ThreadLocal`**, not DR-11's `@Volatile` global — two concurrent completions
        would overwrite each other's counts.
        → `MemberEnumerationWorkBoundGateTest`; `LuaReceiverMemberWork` is now a `ThreadLocal` pair
        behind `record(...)`. `CompNineDr11Test` is deleted: it only printed, and printing is not a
        gate. Measured: `quiet=Traversal(members=50, entries=50, files=2)`,
        `noisy=Traversal(members=50, entries=50, files=2)`.
        **Correction to this plan** — assertions 1–3 are **green** today, not red: they measure the
        DR-09 prototype, which is already index-backed. Only assertion 4 is red, and for the reason
        BL-5 names — `the completion door reads exactly one declaring file expected:<1> but was:<0>`,
        because `membersInFile` never touches the counter. The redness COMP-09-09 is *about* lives at
        the consumers, and Phase 3 is where they stop scanning.
  - [x] Do **not** build the gate on `getAllKeys` totals. DR-11 measured 4 145 keys in both the quiet
        and the noisy arm: index storage is shared across test methods in one JVM and `getAllKeys` is
        not really project-scoped, so a gate on that number measures the fixture.
        → No `getAllKeys` call survives in either gate.
  - [x] Convert the three throwaway harnesses to medians of ≥5 and delete the single-shot variants.
        → `CompNineDrSpikeTest` (§1.1 buckets), `CompNineDr01Test` (§3.1) and `CompNineSection32Test`
        (§3.2 candidates B and C) now median five runs through a shared `Medians` helper. Deleted:
        `CompNineDrSpikeTest.testDr02cInvalidationOnUnrelatedEdit` (single-shot DR-02c, superseded by
        `CompNineDr13Test`) and both single-door DR-01 golden dumps. A quantity that is unrepeatable
        by construction — a cold snapshot build is warm the second time — is now **labelled**
        `(single — unrepeatable by construction)` instead of being averaged with warm samples.
- **Exit**: golden checked in ✅; the two gates exist and were each **shown red with the real
  assertion armed** ✅ (output above and in the class KDocs); DR-02c re-run with medians ✅.
- **Deviation, and the reason.** Both gates ship with a one-line direction switch —
  `BUDGET_ENFORCED` / `COMPLETION_DOOR_INSTRUMENTED`, both `false` — under which each asserts the
  **miss** rather than the target: the same measured quantity against the same budget, inverted. The
  plan's "two gates red" cannot mean *leave a red test on main for three phases* — the repo is
  trunk-based and the non-regression gate is a green suite — and a gate that is merely absent until
  Phase 2 is the `-PwithPerf` mistake again. Inverted, the assertion goes red the moment the
  requirement is met, so Phase 2 and Phase 1 respectively **cannot land without flipping their
  switch**, and flipping it is already each phase's exit criterion. ⚠ This paragraph also claimed
  "inverted, the assertion still cannot pass vacuously" — **that was false for assertion 2 and is
  retracted**; see Phase 0 remediation below, which supplies the liveness guard that makes it true.
  The
  must-fail-first obligation is discharged by the armed run recorded above, not by the shipped
  direction.

### Phase 0 remediation — **DONE 2026-08-09**

The Phase 0 review passed and filed five non-blocking defects. Four of them become live hazards the
moment Phase 1 and Phase 2 start, so they were closed before either did. No Phase 1 work is included.

- [x] **The inverted gate COULD pass vacuously — the paragraph above was wrong, and this is the fix.**
      `MemberEnumerationLatencyGateTest` assertion 2 had no probe-liveness guard. `timeToFirstUs`
      returns -1 when the probe saw no completion result, a -1 `wideUs` makes `ratio` 0, `0 <= factor`
      is `met`, so a **dead harness reported the requirement MET** — inverted-red today, and
      **silently green the instant Phase 2 flips `BUDGET_ENFORCED`**. That is the DR-15 defect
      (a harness asserting `today == today`) in the gate that exists to prevent it. Fixed with a
      plain `assertTrue` on `narrowUs.isNotEmpty() && narrowUs.all { it >= 0 } && wideUs >= 0`,
      deliberately **outside** `assertGate`, so it fails in both switch positions.
      → **Mutation-proved**, `timeToFirstUs` returning -1 for the wide receiver and two of five
      narrow ones: without the guard at `BUDGET_ENFORCED = true`, **BUILD SUCCESSFUL**; with it, red
      at `false` (`narrow=[-1, -1, 15191, 37526, 228760] wide=-1us`) and red at `true`
      (`narrow=[-1, -1, 19124, 40984, 212584] wide=-1us`). Note the sample lists: a **whole**-probe
      break was already caught by the pre-existing `floor > 0` check, so the uncovered case was a
      **partly** dead probe — which is the shape a real regression takes, since `wx.` offering
      nothing is indistinguishable from `wx.` being instant.
- [x] **Figures corrected in every artifact at once, not one.** §1.2a and §3 still quoted
      `264 ms` / `1 152 ms` / "missed by 11.5x" after §1.2's own re-run contradicted them
      (463 / 0.9 / 223 ms; the review's independent run got 392 ms cold). The **DR-07 conclusion does
      not change** — narrowing is a complement, not an alternative — but the honest version is
      directional: the per-keystroke/per-session dichotomy turns on a margin smaller than this
      harness's run-to-run spread, so **no ratio between two of its figures is quotable** (DR-08's
      standing consequence), and what survives is that cold is the first completion of every session
      and narrowing cannot touch it by construction. Corrected in `design.md` §1.2, §1.2a, §3 and
      the §3.x "Was/Now" row, and in `risks-and-gaps.md`'s DR-07 and DR-02c rows — every hit of
      every changed figure was grepped, this being exactly the one-doc-corrected pattern Step 9
      round six was about.
- [x] Stale DR status lines refreshed: DR-08 is `done` (not "partly done"), DR-17 is `done by
      construction` (assertion 2's factor is no longer "the open item"), DR-16 is `partly done`.
      `design.md` §4.11 + coverage table, `requirements.md`'s "Open, tracked, not blocking".
- [x] `MemberEnumerationWorkBoundGateTest` read the `ThreadLocal` counter **outside** the
      `runReadAction` that populated it — correct today only because `runReadAction` runs on the
      caller's thread. Captured inside and asserted outside. Left alone it would report 0 forever and
      silently the moment Phase 1 moves any of this to a pooled read, and 0 is exactly what the
      un-instrumented branch asserts, so the gate would keep passing while measuring another thread.
- [x] DR-11's single-declaring-file `filesVisited == 1` case, lost when the fixture moved to two
      declaring files, is restored on the noise receiver. `filesVisited` is now pinned at **1 and 2**
      — a constant 2 satisfied every other assertion in that test.
- **Accepted, not fixed (defect 5).** The golden's byte-exactness is coupled to
  `Comp09GoldenFixture.receivers` ordering. Real, but inert: it can only fire when someone edits the
  fixture, and Phase 1's whole verification method is diffing against this golden. Changing the
  golden's determinism immediately before Phase 1 diffs against it trades a live gate for a cosmetic
  one. Revisit after Phase 1's diff is taken.

## Phase 1: `LuaReceiverMemberIndex`

- **Goal**: the index exists and is correct; nothing consumes it yet.
- **Unblocked** — DR-10 done.
- **Starting point**: DR-09's prototype is already in `src/main` and registered. Phase 1 is
  *finishing* it, not writing it — the class, externalizer, three-source indexer and registration
  exist and are measured (design §4.0). What is missing is below.
- **Tasks**:
  - [ ] Add indexer **source 4** — table-literal fields of a bare `R = { a = 1 }` (design §4.5c).
  - [ ] Add the **binding-opacity sentinel** and `globalMembership(...): Membership` — design §4.5c.
        Emptiness is **not** a test for authority: `OM = require(x)` plus `function OM.extra()` leaves
        the index non-empty and incomplete, which is how remedies 1 and 2 both failed.
  - [ ] The fallback lives in the **contributor**, not the index (design §4.5c, BLOCKER 1): the index
        companion has no `PsiElement` anchor, `resolveGlobal`/`materialize` both need one, and
        `getMembers()` returns `Map<String, VariableNode>`. Routing it through §4.5b's existing
        `else` arm needs no conversion and preserves the semantic filter and the type text.
  - [ ] Gate on `CompNineDr19Test`'s five shapes — `wx`, `M` (literal+syntactic), `Config` (literal),
        `assert` (opaque), `OM` (opaque+syntactic). Two prior remedies passed a smaller set.
  - [ ] Extend `LuaReceiverMemberWork` counting to **both** entry points — `membersInFile` currently
        does not touch it, so §4.10b assertion 4 and TC 9 have no instrument (BL-5).
  - [ ] Pass `context.containingFile?.originalFile` as `exclude`, not `containingFile` (design §4.5,
        BL-8), and test it — DR-14 never exercised that path.
  - [ ] Land the **two** entry points design §4.5 measured, and **delete the name `membersOf`** so it
        cannot be reached for by accident:
        - `membersOfGlobal(receiver, project, exclude)` — completion. Selection comes from
          `LuaGlobalAssignmentIndex` (the same index `typeOfGlobalIn` uses — **not** this one),
          `projectScope` then `allScope`, first file, context excluded; then `processValues(KEY,
          receiver, inFile, …)` for that file. Prototyped and measured in DR-14.
        - `membersIn(receiver, project, scope)` — materialization. The union, unchanged.
  - [ ] Re-run `CompNineDr14Test` and gate on it: `membersOfGlobal` vs the **global** door and
        `membersIn` vs the **`@class`** door, per receiver, never `resolveGlobal(r) ?: resolveType(r)`
        (TC 7a). Expected divergences are exactly two, both declared: `Shapes` loses `deep` (BUG-430)
        and `Derived` gains `ownField` (design §4.5a).
  - [ ] Add `ProgressManager.checkCanceled()` to the value-processing loop, and make `membersOfGlobal` /
        `membersIn` **return empty when `DumbService.isDumb`** (and **not** take §4.5c's fallback while
        dumb — `resolveGlobal` is guarded and would return null anyway) — design §4.9. Without it the
        prototype throws `IndexNotReadyException` where today's completion quietly offers `[]`
        (DR-10, measured). Test it in dumb mode; nothing in the suite does that today, which is how
        BUG-432 survived.
  - [ ] Tests: dot member, colon member, all-colon receiver, nested qualifier (`a.b.c` → **no**
        entry), `@field`, `= function() end` vs `= someFn` (design §4.3's bounded D3 gap), same
        receiver in two files (first-file for `membersOf`, union for `membersIn`), and externalizer
        round-trip incl. empty and non-ASCII.
  - [ ] Delete `CompNineDr09Test`/`CompNineDr09bTest` once their cases are covered by real tests —
        they are throwaway harnesses, and design §4.0's figures are already recorded.
- **Exit**: index tests green; `LuaMemberFieldIndexTest.testDeepQualifiedKeyPresent` still green
  (that index is untouched); full suite green.

## Phase 2: Completion consumer

- **Goal**: `wx.<caret>` served from the index; COMP-09-08 goes green.
- **Tasks**:
  - [ ] **Implement the §4.5c fork here, in the contributor** — this is Phase 2's work, not
        Phase 1's, because Phase 1's goal is "the index exists and nothing consumes it yet":
        ```kotlin
        val membership = LuaReceiverMemberIndex.globalMembership(
            nameRef.text, nameRef.project, nameRef.containingFile?.originalFile)
        if (membership.authoritative) emitIndexed(membership.members)   // §4.5b index arm
        else emitGraph(...)                                            // §4.5b else arm, VERBATIM
        ```
        `crossFileGlobalMembers` is **replaced** by this fork rather than re-typed. An earlier
        revision said "rewrite it to return `List<LuaReceiverMember>`"; that cannot express the
        non-authoritative branch, which yields `Map<String, VariableNode>` from the graph — the same
        signature impossibility Step 9 raised against putting the fallback in the index.
  - [ ] **Split the emit loop** — design §4.5b. It is shared by two branches today and the `else`
        branch still needs `memberNode.write` for its type text and its *semantic* `isColon` filter,
        so the branches separate and the `else` branch is copied **verbatim**. "The emit loop keeps
        its shape" was Step 9 blocker B3.
  - [ ] Add `LuaMemberLookup.create(LuaReceiverMember)` — icon from `kind`, **no type text** on this
        path (design §4.5). D3 is bounded, not closed: `wx.f = function() end` is FUNCTION, but
        `wx.f = someFn` is FIELD and vanishes from `wx:` completion. Add that case to the gate so the
        residue stays visible.
  - [ ] Amend TC 3 to expect absent type text on the cross-file path. This is a **visible behaviour
        change** and must be an expectation, not a silent diff.
  - [ ] Re-measure time-to-first-element, medians of ≥5.
- **Exit**: COMP-09-08 green; golden unchanged for the in-file path; **completion membership diffed
  against the golden in both directions** (design §4.9 D2 — today's path takes the *first* declaring
  file only, so a union is a superset and Phase 3's diff does not cover this consumer); TC 6 and TC 7
  green. *(An earlier revision cited TC 7a and TC 14 here; neither exists in `requirements.md`.)*

## Phase 3: Materialization consumer

- **Goal**: both `getAllKeys` scans gone; COMP-09-09 goes green.
- **Tasks**:
  - [ ] Rewrite `addMethodsOf` — design §4.6; drop the `allKeys` parameter.
  - [ ] Update both call sites — `collectMethodMembers:421,424` and `materializeUnhostedClass:328`.
  - [ ] A test per row of design §4.6's preservation table: `allScope` (BUG-399), first-wins,
        `onlyIn` confinement (BUG-398), nested qualifiers.
  - [ ] **Diff the golden in both directions.** A superset is the failure mode (see the hard gate).
- **Exit**: COMP-09-09 green; golden byte-identical; **all four corpus baselines unmoved** — if any
  moves, enumeration has become a type source: stop and revert (COMP-09-06).

## Phase 4: `@class` metamethods (COMP-09-05)

- **Goal**: close COMP-04-DR-01 and BUG-426's Known limitation.
- **Tasks**:
  - [ ] Contribute `@class`-declared metamethod names to `LuaGraphType.Table.metamethods` — design
        §4.7, **as well as** leaving them in `localMembers`.
  - [ ] TC 6: `---@class V` with `__add`; `V() + V()` reports nothing.
  - [ ] TC 6a: the offered sets are **unchanged** — `v.` still offers `__add`, `v:` still does not.
        **Settled by DR-12**, which ended a contradiction this plan carried through two reviews: `v.`
        offers `[__add, len, x]` and `v:` offers `[len]` on today's code, so design §4.7 was right and
        this plan and the checklist were both wrong to expect `__add` absent. Assert the measured
        behaviour, not the intent `LuaGraphType.kt:50-52` describes.
  - [ ] Close BUG-426's limitation section or restate what remains.
- **Exit**: TC 6 green; corpus baselines unmoved.

## Phase 5: Re-measure and decide the deferrals

- **Goal**: establish what is left, rather than assuming it is done.
- **Tasks**:
  - [ ] Re-measure both doors, medians of ≥5, against the 100 ms NFR.
  - [ ] *(design §4.10 says Phase 4 measures these; it is Phase 5 — this plan is authoritative.)*
  - [ ] Measure whether COMP-09-02's remaining sites (`catsClassTags:347`,
        `LuaImplicitFields:76`, `LuaTypesVisitor:1349`) are now under budget or still need work.
  - [ ] Re-measure each of the four caches (design §2) and either remove as redundant or record why
        it stays.
  - [ ] Decide DR-07 (narrowing invalidation) on the numbers.
  - [ ] Run [human-verification-checklists.md](human-verification-checklists.md).
- **Exit**: every acceptance criterion ticked or explicitly deferred with a reason.

## Requirement → phase coverage

| Requirement | Phase |
| :-- | :-- |
| COMP-09-01 receiver-keyed enumeration | 1, 2, 3 |
| COMP-09-02 no full-file walk | 3, 5 *(remaining sites measured, not assumed)* |
| COMP-09-03 all sources, dot and colon | 1 |
| ~~COMP-09-04 / 04b~~ | withdrawn (design §1.7) |
| COMP-09-05 `@class` metamethods | 4 |
| COMP-09-06 no new type source | 0 (golden), 3 (gate) |
| COMP-09-07 behaviour-preserving | 0, 3 |
| COMP-09-08 latency enforced | 0 (red), 2 (green) |
| COMP-09-09 work bound enforced | 0 (red), 3 (green) |

## Task summary

| Phase | Status | Priority |
| :-- | :-- | :-- |
| 0: Golden file and instrument | **done** (2026-08-09) | Must |
| 1: `LuaReceiverMemberIndex` | todo | Must |
| 2: Completion consumer | todo | Must |
| 3: Materialization consumer | todo | Must |
| 4: `@class` metamethods | todo | Should |
| 5: Re-measure and decide deferrals | todo | Must |
