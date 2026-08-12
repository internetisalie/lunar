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
> exhaustive is measured now, not argued — and time-to-first is **not independent of member count**
> (41 ms for 3 members, inside the budget; 1 641 ms for 3 600, far outside it — the `40x` this line
> once quoted is retired as a ratio of two harness figures, per DR-08), which
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
        (design §4.1) and `LuaTypeManagerImpl.materializeClass` (`LuaTypeManagerImpl.kt:341`, comment
        at `:357-361`) warns the parity harness cannot see it.
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
        `time-to-first scales 64x with member count against a measured noise floor of 2x` — quoted as the
        harness's own printed line, **not** as a figure this plan cites; the ratio form was retired by
        DR-08 and the assertion replaced by a count (design §4.10a-bis). Assertion
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

## Phase 1: `LuaReceiverMemberIndex` — **DONE 2026-08-09**

- **Goal**: the index exists and is correct; nothing consumes it yet.
- **Unblocked** — DR-10 done.
- **Starting point**: DR-09's prototype is already in `src/main` and registered. Phase 1 is
  *finishing* it, not writing it — the class, externalizer, three-source indexer and registration
  exist and are measured (design §4.0). What is missing is below.
- **Tasks**:
  - [x] Add indexer **source 4** — table-literal fields of a bare `R = { a = 1 }` (design §4.5c).
  - [x] Add the **binding-opacity sentinel** and `globalMembership(...): Membership` — design §4.5c.
        Emptiness is **not** a test for authority: `OM = require(x)` plus `function OM.extra()` leaves
        the index non-empty and incomplete, which is how remedies 1 and 2 both failed.
  - [x] The fallback lives in the **contributor**, not the index (design §4.5c, BLOCKER 1): the index
        companion has no `PsiElement` anchor, `resolveGlobal`/`materialize` both need one, and
        `getMembers()` returns `Map<String, VariableNode>`. Routing it through §4.5b's existing
        `else` arm needs no conversion and preserves the semantic filter and the type text.
  - [x] Gate on `CompNineDr19Test`'s five shapes — `wx`, `M` (literal+syntactic), `Config` (literal),
        `assert` (opaque), `OM` (opaque+syntactic). Two prior remedies passed a smaller set.
        *Gated in `LuaReceiverMemberBindingShapeTest`; the opaque receiver is renamed `Busted` there
        so the fixture does not shadow the Lua builtin `assert`.*
  - [x] Extend `LuaReceiverMemberWork` counting to **both** entry points — `membersInFile` currently
        does not touch it, so §4.10b assertion 4 and TC 9 have no instrument (BL-5).
  - [x] Pass `context.containingFile?.originalFile` as `exclude`, not `containingFile` (design §4.5,
        BL-8), and test it — DR-14 never exercised that path.
  - [x] Land the **two** entry points design §4.5 measured, and **delete the name `membersOf`** so it
        cannot be reached for by accident:
        - `membersOfGlobal(receiver, project, exclude)` — completion. Selection comes from
          `LuaGlobalAssignmentIndex` (the same index `typeOfGlobalIn` uses — **not** this one),
          `projectScope` then `allScope`, first file, context excluded; then `processValues(KEY,
          receiver, inFile, …)` for that file. Prototyped and measured in DR-14.
        - `membersIn(receiver, project, scope)` — materialization. The union, unchanged.
  - [x] Re-run `CompNineDr14Test` and gate on it: `membersOfGlobal` vs the **global** door and
        `membersIn` vs the **`@class`** door, per receiver, never `resolveGlobal(r) ?: resolveType(r)`
        (TC 7a). Expected divergences are exactly two, both declared: `Shapes` loses `deep` (BUG-430)
        and `Derived` gains `ownField` (design §4.5a).
  - [x] Add `ProgressManager.checkCanceled()` to the value-processing loop, and make `membersOfGlobal` /
        `membersIn` **return empty when `DumbService.isDumb`** (and **not** take §4.5c's fallback while
        dumb — `resolveGlobal` is guarded and would return null anyway) — design §4.9. Without it the
        prototype throws `IndexNotReadyException` where today's completion quietly offers `[]`
        (DR-10, measured). Test it in dumb mode; nothing in the suite does that today, which is how
        BUG-432 survived.
  - [x] Tests: dot member, colon member, all-colon receiver, nested qualifier (`a.b.c` → **no**
        entry), `@field`, `= function() end` vs `= someFn` (design §4.3's bounded D3 gap), same
        receiver in two files (first-file for `membersOfGlobal`, union for `membersIn`), and externalizer
        round-trip incl. empty and non-ASCII.
  - [x] Delete `CompNineDr09Test`/`CompNineDr09bTest` once their cases are covered by real tests —
        they are throwaway harnesses, and design §4.0's figures are already recorded.
- **Exit**: index tests green; `LuaMemberFieldIndexTest.testDeepQualifiedKeyPresent` still green
  (that index is untouched); full suite green.
- **Exit met.** `ktlintCheck test --rerun --no-build-cache` on gce-builder: **BUILD SUCCESSFUL,
  2 541 tests, 0 failures, 1 skipped** (baseline 2 526 − **6** deleted + **21** added = 2 541), ktlint
  0 violations. The deleted six are `CompNineDr09Test`'s five (`testDr09aExternalizerRoundTrip`,
  `testDr09bMembershipVersusGolden`, `testDr09d2UnionVersusFirstDeclaringFile`,
  `testDr09d1ScopePrecedence`, `testDr09cMembersOfTiming`) **plus**
  `CompNineDr09bTest.testWhereDeepComesFrom`; the added 21 are `LuaReceiverMemberIndexTest` 11,
  `LuaReceiverMemberBindingShapeTest` 4, `LuaReceiverMemberDoorParityTest` 3,
  `LuaReceiverMemberDumbModeTest` 3. The other four `CompNineDr*` files in the commit tree are
  rename-only: method counts before/after are 2/2, 2/2, 4/4, 3/3.
  *(This line first read “2 538 … five deleted, 17 added”. The commit message and the handoff carried
  the right figure; only this document — the one Phase 2 reads — was wrong, which is the
  corrected-in-one-artefact-not-the-others failure this feature has now filed against itself three
  times. Re-derived from the builder's JUnit XML and from `git show 6b480de4^:<file>` method counts,
  not copied.)*
  `src/test/resources/comp09/member-enumeration.golden` is
  **byte-unchanged** (`a8c580ccc7a9528c0fde41527d870c48`), which is the point: nothing consumes the
  index yet, so a moved golden would have meant enumeration semantics changed in the wrong phase.
  `getVersion()` was **1** as Phase 1 shipped it — the indexer's output shape did not change there.
  *(Corrected 2026-08-12: this line, and design §4.8's table, both said the version stays 1
  permanently. It does not. **Phase 3's remediation bumped it to 2** when the input filter widened
  from `file.extension == "lua"` to every registration `LuaFileType` carries — `.rockspec`,
  `.luacheckrc` and `.busted` — which changes the index's **content**, not just its consumers. The
  boundary was crossed deliberately: without the bump a machine that had already indexed keeps its
  `.lua`-only entries and the filter fix is invisible there. Phase 5's benchmarks stay comparable
  regardless, because every COMP-09 benchmark fixture builds its library tree per run and indexes
  cold, so no warm figure spans the boundary.)*

### What Phase 1 found that the plan did not say

- **Most of the task list was already in `src/main`.** Source 4, `OPAQUE_BINDING`, `Membership`,
  `globalMembership`, `membersOfGlobal`/`membersInFile` and the `ThreadLocal` counter all landed with
  DR-19 and Phase 0. Phase 1's real content was the *entry-point split*, the platform obligations,
  and turning print-only harnesses into gates.
- **`membersByFile` was deleted with `CompNineDr09Test`.** It was DR-09's D2 probe and had no other
  caller; design §4.5's table names exactly two entry points, so leaving a third reachable would
  re-create the hazard `membersOf`'s retirement exists to prevent. D2's case is re-homed as **two**
  assertions: `LuaReceiverMemberIndexTest.testTheTwoDoorsDisagreeAboutASecondDeclaringFile` for the
  two-global-declaring-files shape, and
  `LuaReceiverMemberIndexTest.testAFileLocalReceiverIsNotASelectableDeclaringFile` for the
  file-local contamination shape — Risk 1.1's measured firing shape, `[real]` from the completion
  door against `[alsoPrivate, privateToThisFile, real]` from the union.
  *(For one commit the second half was credited to
  `CompNineDr14Test.testDr14LocalReceiverIsNotSelectable`. That method has **no assertion** — 17
  `println`s, ending in a printed VERDICT — so the claim was false and the shape this feature's whole
  §4.5 rewrite exists for was covered nowhere. Coverage was not regressed, because the deleted
  `testDr09d2UnionVersusFirstDeclaringFile` asserted nothing either; but the claim was the evidence
  offered for “delete them once their cases are covered by real tests”, in the same commit whose text
  says printing is not a gate. The assertion now exists and is mutation-proved.)*
- **`membersIn` filters the sentinel too.** §4.5c only requires it of `globalMembership`, but the
  marker is index-internal and Phase 3 materializes `membersIn`'s names directly, so leaving it in
  would offer a member whose name is a NUL byte. Gated by
  `LuaReceiverMemberBindingShapeTest.testTheUnionDoorDoesNotLeakTheOpacitySentinelEither`.
- **`membershipOver` read the first candidate file twice** — once inside the opacity `any`, once for
  membership. Harmless until the counter reached that door, at which point §4.10b's assertion 4 reads
  **2**, not 1. Each candidate is now read once. Mutation-proved: restoring the double read gives
  `the completion door reads exactly one declaring file expected:<1> but was:<2>`.
- **`ProgressManager.checkCanceled()` cannot be gated *by asserting the throw*.** It is in both
  callbacks as §4.9 requires, but a test asserting the throw **passes with the line deleted**: probed
  on gce-builder, `FileBasedIndex.processValues` under a cancelled indicator throws
  `ProcessCanceledException` *before invoking any callback* (the raw probe's callback never ran; the
  counter showed `reset()` had happened and `files == 0`) — grounded afterwards in
  `FileBasedIndexImpl:893`, which calls `checkCanceled()` inside `ensureUpToDate` before the value
  iterator is opened. That candidate test was written, measured and **deleted**.
  *(Measured on the way: `ProgressManager.runProcess` calls `indicator.start()`, which **clears** the
  cancelled flag — the probe's first version was green for that reason alone.)*
  **Phase 1 then over-generalised this to "cannot be gated" full stop, and that was wrong for one of
  the two calls.** What is observable is not the throw but the probe: the platform checks *after*
  each callback (`FileBasedIndexEx:424`, `:456`) and the plugin checks *before*, so a
  `CoreProgressManager.CheckCanceledHook` recording `LuaReceiverMemberWork.files` at every check sees
  two probes straddle one `recordVisit`. Gated in
  `LuaReceiverMemberCancellationTest.testTheUnionDoorProbesCancellationBeforeEachCallbackAndNotOnlyAfterIt`
  and mutation-proved: deleting `membersIn`'s `checkCanceled()` turns `[1, 1, 2, 2, 3]` into
  `[1, 2, 3]` and the test goes red. **`membersInFile`'s call at `:510` remains ungated**, measured
  rather than assumed — `membershipOver` reads one file per `processValues` call, so each call brings
  its own run of platform probes at an unchanged file count and produces the same repeat with or
  without the plugin's line. That second test was written, mutation-tested, found to survive and
  removed. Recorded in the code beside both calls.
- **`Membership` moved off the companion** onto the class, so Phase 2 can write
  `LuaReceiverMemberIndex.Membership` rather than `…Companion.Membership`.
- **`Indexer.map` was ~90 executable lines**, three times the engineering contract's limit, and mixed
  raw PSI traversal with orchestration. Split into one helper per source with the logic unchanged —
  which the byte-unchanged golden and the untouched `getVersion()` both attest.

### Phase 1 remediation — the review gate that failed was gate 8, traceability

Phase 1's code passed gates 1–7 and is unchanged by this. What failed was the evidence layer: two
checked-in artefacts asserted things that were not true, and one status marker was stale. All of it
is corrected above rather than argued with; the substantive item is the first.

- [x] **A false verification claim became a real assertion.** The plan and the test KDoc both said
      `CompNineDr14Test.testDr14LocalReceiverIsNotSelectable` held D2's disjoint-set half. It holds
      nothing — `grep -c assert` is 0 against 17 `println`s. Risk 1.1's *measured firing shape* (a
      global `wx` plus an unrelated file-local `wx`) was therefore asserted nowhere, which is the one
      shape design §4.5 was rewritten around. Now
      `LuaReceiverMemberIndexTest.testAFileLocalReceiverIsNotASelectableDeclaringFile`, pinning
      `[real]` from the completion door and the contaminated `[alsoPrivate, privateToThisFile, real]`
      from the union. Mutation-proved by pointing the completion door at the union.
- [x] **The exit gate's arithmetic re-derived**, not copied: 2 526 − 6 + 21 = **2 541**.
- [x] **Task-summary row for Phase 1 set to done.** The whole table was re-checked; phases 2–5 are
      genuinely `todo` and every one of their checkboxes is unchecked.
- [x] **§4.9's cancellation claim narrowed to what was measured**, and the half that *is* gateable
      is now gated — see the `checkCanceled` bullet above. `requirements.md` follows.
- [x] **`membersOf` renamed out of the test helpers** (`unionMembersOf`). The name is retired
      precisely so it cannot be reached for by accident, and it had reappeared in this phase's own
      primary evidence file next to `globalNamesOf`, where no reader could tell which door an
      assertion was on.
- [x] **Design §4.10b assertion 4's justification corrected.** "Reads exactly one file **by
      construction**" is false under DR-19c: `membershipOver` reads every candidate to decide
      opacity, so the flipped gate is fixture-dependent. Phase 3 uses assertion 4 as its D2-leak
      detector, so the overstatement was load-bearing. `design.md` and `requirements.md` now say what
      DR-19c actually guarantees.
- **Gate.** `ktlintCheck test --rerun --no-build-cache` on gce-builder: **BUILD SUCCESSFUL,
  2 543 tests, 0 failures, 1 skipped** (2 541 + 2 added), ktlint 0 violations. Golden still
  `a8c580ccc7a9528c0fde41527d870c48`; `getVersion()` still 1; no `src/main` behaviour changed.

## Phases 2–5 were RE-CUT on 2026-08-12, not resumed

> **Phase 2 as previously written was executed to plan, measured, and aborted (`ABORT_REPLAN`).** Its
> change site — `crossFileGlobalMembers`, `LuaCompletionContributor.kt:133-139` — sits behind
> `type == LuaGraphType.Undefined`, a guard that never opens for a receiver with members, and is
> downstream of the `LuaTypesSnapshot.forFile` build that is 88–97 % of the cost. The record is in
> [risks-and-gaps.md](risks-and-gaps.md), "BLOCKER (Phase 2, 2026-08-09)".
>
> **What unblocked the re-cut** is TYPE-11 shipping `done` (2026-08-12): the 334 ms recurring
> per-keystroke cost is gone, and what COMP-09 now exists to remove is the **cold `buildSnapshot` of
> the library file — 844–955 ms** for a 123 KiB / 3 600-member library, once per session (TYPE-11
> DR-08).
>
> **The re-cut is prototyped, not proposed.** The abort happened because DR-14 validated
> `membersOfGlobal` against `resolveGlobal` directly rather than through the contributor. DR-21/DR-22
> therefore built the new site end to end, ran it through `myFixture.completeBasic()`, ran the **whole
> 2 639-test suite armed**, and reverted it. Design §1.10 carries the pasted output; every task below
> cites a run rather than a reading. **Phase 1's index needed no change** — the probe shows it answers
> exactly as designed on every receiver.

### Scope decision: the remaining work stays inside COMP-09

Recorded because the re-cut is large enough to ask. **Recommendation: keep it in COMP-09.** Reasons,
in order of weight:

1. **The change site is the consumer COMP-09-01 always named.** The requirement is "'members of X' is
   answered by an index lookup, not a key scan", and Phase 1 built the index with the explicit note
   that "nothing consumes it yet". Splitting the consumer out would leave COMP-09 shipping a
   registered index that nothing uses — which is the state the abort left, and it is not a shippable
   unit of value.
2. **Six of nine requirements and every acceptance criterion are already written against it.**
   COMP-09-07/-08/-09 are all asserted at this door; the golden, the latency gate and the work-bound
   gate are checked in and inverted against it. A new ID would inherit all three artifacts or
   duplicate them.
3. **What changed is a call site, not a thesis.** The index, the selection rule, the opacity sentinel
   and the two-door split all survived the abort intact — the probe confirms the index "answers
   exactly as designed on every receiver". Only §4.5's *premise about which function is the door* was
   wrong.
4. **What genuinely is new gets a requirement, not a feature.** Rule S is the one invented rule, and
   it is COMP-09-10 with its own tests and mutation proof.

**What would reverse this**: if Phase 5's DR-23/DR-24 turn out to need a cached bound-name set with
its own invalidation axis, that is a second mechanism and belongs in its own feature. It is not
needed to ship Phases 2–4. *(Minting or retiring a roadmap ID is the supervisor's call, not this
document's.)*

## Phase 2: The change site — hoist above the snapshot build — **DONE 2026-08-12**

> **Landed, and every declared expectation held.** The golden's diff is exactly the four rows the table
> below declares — `4` insertions, `1` deletion (`git diff --numstat`) — and its hash moves
> `5479f471e3524a924f425581acf737f6` → **`38c7586ecfddd17bdb79785b3b3a9f31`**.
>
> **The colon-mover disagreement is settled, and design §4.5a was right.** Exactly **one** colon row
> moved (`Base|completion:|onClose`); DR-21's summary implying two is corrected against a checked-in
> artifact. **Zero `global`, `class` or `shape` rows moved** — verified by diffing those rows alone,
> which is COMP-09-06's structural guarantee showing up as a measurement.
>
> Measured at the new site: cold time-to-first over **five distinct wide receivers, each in its own
> file** = `[12046, 13162, 14604, 17580, 20540] µs`, **median 14 604 µs** against a 100 ms budget
> (490 995 µs unarmed, design §1.10.2). Assertion 2's count form: `narrow=3->3 wide=3600->3600` across
> 4 000 unrelated indexed members. Both Rule S mutation proofs reddened **exactly one** test each, both
> with `expected:<[]> but was:<[fromLibrary]>`.
>
> **Two `Then` columns in requirements.md were corrected by measurement, neither a behaviour change.**
> TC 10d's loop forms offer `[end]`, not `[]` — the keyword provider fires on the bare `psiElement()`
> pattern inside an open `do` block, and DR-21 had recorded the same. TC 10j offers
> `[else, elseif, end]`, not `[fromLocal]`: `:462` **is** transitively covered (no `fromLibrary`, which
> is the verdict the row itself names), and the missing `fromLocal` is a pre-existing property of the
> narrowing path on an arm that declines. See risks-and-gaps' Phase 2 findings.

- **Goal**: `wx.<caret>` answered from the index **before** `LuaTypesSnapshot.forFile` runs;
  COMP-09-08 assertion 1 goes green. Measured on the prototype: cold time-to-first **491 ms → 7.4 ms**,
  both medians of five cold samples (design §1.10.2).
- **Tasks**:
  - [x] **TASK 0 — DONE 2026-08-12 — extend the golden to a colon door FIRST, in its own commit, with
        no production change.** DR-27, decided rather than deferred; see risks-and-gaps' DR-27 row for
        the full reasoning. `MemberEnumerationGoldenTest.completionRows` (`MemberEnumerationGoldenTest.kt:99-102`)
        emits only `completionsFor("$receiverName.<caret>\n")`. Add the `:` caret beside it, under a
        distinct label so the two carets never collide:

        ```kotlin
        private fun completionRows(receiverName: String): List<String> =
            caretRows(receiverName, ".", "completion") + caretRows(receiverName, ":", "completion:")

        private fun caretRows(receiverName: String, separator: String, label: String): List<String> {
            val offered = completionsFor("$receiverName$separator<caret>\n").sorted()
            return offered.ifEmpty { listOf("<none>") }.map { "$receiverName|$label|$it" }
        }
        ```

        `HEADER` is written into the golden itself (`MemberEnumerationGoldenTest.kt:143-153`), so
        **append** a legend line immediately after the existing
        `# <receiver>|completion|<lookupString>   what \`R.<caret>\` offers` line —
        `# <receiver>|completion:|<lookupString>  what \`R:<caret>\` offers` — and **do not reword
        the existing one**, so the header change is also an addition.
        **Exit for this task alone**: the golden's diff is a **pure addition** — `git diff` on
        `src/test/resources/comp09/member-enumeration.golden` shows only `+` lines and **zero** `-`
        lines, and `LC_ALL=C cut -d'|' -f2 member-enumeration.golden | sort -u` goes from
        `shape | global | class | completion` to that plus `completion:`. **`LC_ALL=C` is
        required, not decoration**: under the default `en_US.UTF-8` collation `sort -u` folds
        `completion` and `completion:` into one entry, so the check silently reports no change. Record the new hash beside
        `a8c580ccc7a9528c0fde41527d870c48`. If any existing line moves, **stop**: the extension was
        supposed to be behaviour-free and something else changed.
        **Done**: `git diff --numstat` on the golden reports `14  0` (14 insertions, 0 deletions);
        `LC_ALL=C cut -d'|' -f2 | sort -u` (excluding `#` header lines, which contain unrelated `|`
        prose) goes from `{class, completion, global, shape, Derived:*}` to that plus `completion:`,
        losing nothing. Old hash (md5) `a8c580ccc7a9528c0fde41527d870c48` → new hash
        `5479f471e3524a924f425581acf737f6`. No existing row moved — every colon-door value was
        obtained from the unarmed suite run (`test --tests *MemberEnumerationGoldenTest*`, captured
        from the test's own printed banner between `===== COMP-09 GOLDEN =====` markers) and
        transcribed verbatim, never hand-computed.

        While in this file, fix its stale KDoc anchor: `MemberEnumerationGoldenTest.kt:30` still
        cites `LuaTypeManagerImpl:256-262`, which is now unrelated code. The correct anchor is
        `LuaTypeManagerImpl.materializeClass` (`LuaTypeManagerImpl.kt:341`), with the warning
        comment at `:357-361`. This task's no-production-change rule covers `src/main`; a `src/test`
        KDoc correction is compatible with it.

        **Why before Phase 2 and not during it, or in Phase 5.** During Phase 2 the re-record would
        carry the new caret *and* the arm's behaviour delta in one diff, which is exactly what the
        every-line-declared exit criterion forbids — that objection is correct and is why the
        deferral existed. It does not apply to a run on unarmed code, where the delta is zero.
        Deferring to Phase 5 leaves two real gaps: after Phase 2 **nothing pins the colon door for
        ten of the eleven receivers** (only E7 asserts `Base:`), and Phase 4's "re-check `v:` at the
        Phase 2 site" task has no baseline to re-check against. It also settles a disagreement
        between two artifacts: DR-21's row reports *nine of eleven* colon receivers byte-identical —
        **two** movers — while design §4.5a declares exactly **one** (`Base:`, with `Derived:`
        explicitly unchanged), and **no colon-door output is pasted anywhere in design §1.10**. The
        recorded baseline decides it. If a second mover appears, add it to the diff table below as a
        declared expectation; if none does, DR-21's count is corrected against a checked-in artifact.
  - [x] **Add `net.internetisalie.lunar.lang.psi.LuaLocalBindingScan`** — design §4.14. One public
        method, `fun binds(file: PsiFile, name: String): Boolean`, implemented as a single
        `PsiTreeUtil.processElements(file) { … }` pass with an early exit over the seven clauses in
        §4.14's table. **Do not substitute `LuaFileBindingsIndex`**: it walks file-scope statements
        only (`LuaFileBindingsIndex.extractBindings`, `LuaFileBindingsIndex.kt:350`, whose walk is
        `file.getBlockList().forEach { block -> block.statementList… }` at `:355-357`), so it misses
        parameters, for-loop variables and nested locals —
        the member-inventing direction. §4.14 states why.
  - [x] **Add the hoist to `LuaCompletionContributor`** — design §4.13, verbatim. One call inserted
        after `findReceiverExpr` (`:361` today) and before `val snapshot = LuaTypesSnapshot.forFile(...)`:
        ```kotlin
        if (addIndexedGlobalMembers(receiverExpr, parameters, isColon, result)) return
        ```
        plus the two private companion functions §4.13 gives in full. **Order matters and is measured**:
        `globalMembership` first, `LuaLocalBindingScan.binds` only if `found && authoritative`. The
        reverse order costs 10 875–21 501 µs per completion on a 4 002-line file and buys nothing —
        routing is identical either way (design §1.10.6).
  - [x] **Change nothing below the insertion point.** `crossFileGlobalMembers`, the
        `type == LuaGraphType.Undefined` guard and the shared emit loop stay byte-for-byte as they are.
        Design §4.5b's branch split is **withdrawn** — it existed only because the old site replaced a
        function in place. The non-authoritative case is `return false`, not a re-implemented graph arm.
  - [x] Add `LuaMemberLookup.create(member: LuaReceiverMember): LookupElement` —
        `net.internetisalie.lunar.lang.completion.LuaMemberLookup`, icon `AllIcons.Nodes.Method` for
        `Kind.FUNCTION` else `AllIcons.Nodes.Field`, **no type text** (design §4.13).
  - [x] **Re-record the golden, declaring every move.** The armed suite moves exactly two tests, and
        the golden's diff on the **dot** caret is three receivers on the `completion` door only —
        every `global` and `class` row is byte-identical (design §1.10.5). The golden's checked-in
        hash moves from task 0's hash to whatever this re-record produces; put both in the commit
        message.

        | golden row | today | after | requirement |
        | :-- | :-- | :-- | :-- |
        | `Shapes\|completion` | `deep, direct, nested, plain` | `direct, nested, plain` | BUG-430, §4.4a, TC 3 |
        | `Base\|completion` | `Show, inheritedFn` | `Show, inheritedField, inheritedFn, onClose` | §4.5a, **TC 7d** |
        | `Derived\|completion` | `Show, ownFn` | `Show, ownField, ownFn` | §4.5a, TC 7c |
        | `Base\|completion:` | `Show, inheritedFn` | `Show, inheritedFn, onClose` | §4.5a, **TC 7e** — the colon rows exist because of task 0 |

        **The fourth row is new and depends on task 0 having run.** Before task 0 the golden had no
        colon door at all — `completionRows` emitted only the `.` caret — which is why an earlier
        revision listed a `Base:` row the file could not produce (Step 9 blocker B3) and a later one
        struck it entirely. Task 0 gives the file a colon door on unarmed code, so `Base|completion:`
        is now a row the golden *can* carry and Phase 2 declares it here as well as gating it in E7.

        ⚠ **The design predicts exactly ONE colon mover and DR-21's summary implies two** ("9 of 11
        colon receivers byte-identical"; risks-and-gaps' DR-21 row), with no colon output pasted
        anywhere to adjudicate. If the re-record moves a second colon row, that is the disagreement
        resolving — **name it here, decide in writing whether it is expected, and only then
        re-record**. Do not treat an undeclared colon movement as noise; that is precisely the
        failure this exit criterion exists to catch. `global` and `class` rows must still be
        byte-identical either way.
  - [x] **Write seven expectation tests** — new class
        `net.internetisalie.lunar.definitions.MemberEnumerationExpectationTest : LibraryRootTestCase()`,
        offered set read through the inherited `completionsFor(...)` (which recovers the
        auto-inserted single match — BUG-431). E2/E3/E5–E7 call
        `registerLibraryRoot(Comp09GoldenFixture.files())`; **E1 and E4 each register their own
        single-file library root** — `registerLibraryRoot(mapOf("sources.lua" to SOURCES))` and
        `mapOf("residue.lua" to RESIDUE)` — and the consumer is the `consumer.lua`
        `completionsFor(...)` writes into the *project*, which is the second file. *(An earlier
        revision called these "two-file roots"; the root holds one file, the project holds the
        other.)* The two library files are given **in full** below, not as bullet lists: an earlier
        revision listed E1's `---@class`/`---@field` block *after* the declarations, which no fixture
        in `Comp09GoldenFixture` does and which a weak implementer cannot resolve without inventing.

        > **These are WRITTEN, not re-used.** An earlier revision said "re-use verbatim the five the
        > aborted Phase 2 already wrote". **They cannot be located**: `d5af3231` is docs-only ("no
        > production change kept; the tree is byte-identical to `0e182b1c` under `src/`"), so the
        > tests were reverted with the code and appear in no commit; `Sources.lua` and `Residue.lua`
        > do not exist in the tree either. What survives is risks-and-gaps' table of the five
        > *expectations* and their observed failures on today's code, which is the mutation proof —
        > so the five are reconstructed from that record below and specified in full, with their
        > fixtures, and DR-21's two `Base` findings added as E6/E7.
        >
        > Same correction for the harnesses: `CompNineDr18Test`, `CompNineDr21Test` and
        > `CompNineDr22Test` — cited for every figure in design §1.10 and §4.8a — were throwaway,
        > run and reverted (`git log --all --diff-filter=A` finds no add commit for any of them).
        > **The pasted output in design §1.10 / §4.8a is the evidence; the harnesses are not
        > re-runnable, and nothing in this plan may instruct re-using them.**

        **E1's library file, verbatim** (`SOURCES`, modelled on `Comp09GoldenFixture.BASE`'s layout —
        `---@meta`, then the `---@class` + `---@field` block **immediately above** the declaration it
        annotates, then the members, then `return`):

        ```lua
        ---@meta

        ---@class Sources
        ---@field fromFieldTag string
        Sources = {}

        Sources.assigned = 1

        function Sources.fromFunc() end

        function Sources:fromMethod() end

        return Sources
        ```

        **E4's library file, verbatim** (`RESIDUE`):

        ```lua
        ---@meta

        Residue = {}

        local function impl() end

        Residue.aliased = impl

        Residue.direct = function() end

        return Residue
        ```

        | # | fixture | assertion | today (the recorded red) | after |
        | :-- | :-- | :-- | :-- | :-- |
        | E1 | `sources.lua` above | every indexer source reaches `Sources.` | `.` = `{assigned, fromFunc, fromMethod}` — `fromFieldTag` **absent** (the aborted Phase 2's recorded red) | `.` = `{assigned, fromFieldTag, fromFunc, fromMethod}`; `:` = `{fromFunc, fromMethod}`, unchanged — TC 5. **See the derivation note below: `fromMethod` IS in the dot set.** |
        | E2 | golden | `Derived.` offers `ownField` | `{Show, ownFn}` | `{Show, ownField, ownFn}` — TC 7c |
        | E3 | golden | `Shapes.` no longer offers `deep` | `{deep, direct, nested, plain}` | `{direct, nested, plain}` — TC 3, BUG-430 |
        | E4 | `residue.lua` above | an **indirectly** assigned function is `Kind.FIELD` and so vanishes at `:` | `.` = `{aliased, direct}`; `:` = `{aliased, direct}` — `aliased` **offered at `:`** | `.` = `{aliased, direct}`, unchanged; `:` = `{direct}` only — design §4.3's D3 residual, made visible rather than assumed away |
| E5 | golden | the index arm renders **no type text** | `wxFileExists=fun(filename)` | `wx.`'s elements carry `typeText == null`. Read it off a `LookupElementPresentation`, never off the lookup string. **Two spellings exist and both are real** — use either, but write one of them out rather than naming a bare method: the static factory `LookupElementPresentation.renderElement(element)` (`LookupElementPresentation.java:258`, which is what `LookupImpl.addItem` itself uses) returns a filled presentation; the instance form `element.renderElement(presentation)` (`LookupElement.java:130`) fills one you supply, and is the shape this repo already uses at `LuaRedisCommandCompletionTest.kt:73-74`. Concretely: `val presentation = LookupElementPresentation(); element.renderElement(presentation); assertNull(presentation.typeText)`. Design §4.13; §1.7 measured this as absence rather than cost |
        | E6 | golden | `Base.` gains its own `@field`s | `{Show, inheritedFn}` | `{Show, inheritedField, inheritedFn, onClose}` — TC 7d, DR-21's finding; §4.5a had declared `Derived` only |
        | E7 | golden | `Base:` gains `onClose` | `{Show, inheritedFn}` | `{Show, inheritedFn, onClose}` — **TC 7e**; `---@field onClose fun(): nil` indexes `Kind.FUNCTION` (§4.3's `startsWith("fun(")`) and survives the syntactic colon filter. After task 0 the golden *also* carries this as a `Base\|completion:` row; **E7 is still the named gate for TC 7e**, and risks-and-gaps' DR-27 row says so too |

        **Derivation note — the expected sets are read off `emitIndexed`, not off intuition, and E1's
        was wrong until this revision.** Design §4.13's `emitIndexed` filters on **one** predicate:
        `if (isColon && member.kind != Kind.FUNCTION) continue`. There is **no separator filter**. So
        at a `.` caret *nothing* is filtered — a `Separator.COLON` member is emitted — and at a `:`
        caret the only test is `Kind`. Two independent confirmations that this is today's behaviour
        too, so the dot set is a preservation rather than a change:

        - the checked-in golden has `Base|completion|Show` (line 78) while `Comp09GoldenFixture.BASE`
          declares `function Base:Show()` — a colon-declared method offered at the dot caret;
        - design §1.10.5's armed measurement has `Base.` = `[Show, inheritedField, inheritedFn,
          onClose]`, `Show` included.

        E1 therefore expects `fromMethod` **in** its dot set. An earlier revision wrote
        `{assigned, fromFunc, fromFieldTag}`, which is both a regression against today's behaviour and
        red against a correct implementation of §4.13. Per-member derivation for E1:
        `assigned` → source 2, RHS `1` → `FIELD/DOT`; `fromFunc` → source 1 → `FUNCTION/DOT`;
        `fromMethod` → source 1 → `FUNCTION/COLON`; `fromFieldTag` → source 3, type text `string`
        (does not start `fun(`) → `FIELD/DOT`. Dot keeps all four; colon keeps the two `FUNCTION`s.
        E2/E3/E6/E7 were re-derived the same way against §1.10.5's pasted armed output and are
        unchanged; E4's was re-derived against §4.3's D3 rule and gains an explicit today-column for
        its dot caret, which was previously blank.

        Assert with `assertEquals(expected, found.toSet())`, never `assertTrue(contains)`: half the
        value here is that nothing *extra* is offered, and a containment assertion cannot see a
        superset — which is the D2 failure mode this feature has hit three times. **No eighth test is
        needed for the no-invention guard**: the golden already pins `wx|completion` at exactly
        `{wxFileExists, wxID_ANY}` and `wxFrame`/`AllColon`/`luassert` at `<none>`, and Phase 2's exit
        requires those rows to be byte-identical.
  - [x] **Write TC 7f and TC 7f-bis — the `@field` superset on the bundled stubs, and its control.**
        New class
        `net.internetisalie.lunar.definitions.MemberEnumerationRedisTargetTest : IndexedBasePlatformTestCase()`.
        Set the target exactly the way `RedisAmbientTypingTest.setRedisTarget` does — **`Target`'s
        second parameter is a `VersionEntry`, not a `String`**, so
        `Target(LuaPlatform.REDIS, "7+")` does not compile:

        ```kotlin
        private fun setTarget(platform: LuaPlatform, label: String) {
            val version = requireNotNull(PlatformVersionRegistry.findVersion(platform, label))
            EdtTestUtil.runInEdtAndWait<RuntimeException> {
                LuaProjectSettings.getInstance(project).setTargetAndNotify(Target(platform, version))
                PlatformLibraryIndex.reload()
            }
        }
        ```

        **Restore STANDARD 5.4 in `tearDown`** — the light project is shared across the module run
        and a leaked Redis target breaks the later `lang.indexing`/`lang.types` tests.

        **TC 7f — Redis 7+.** `runtime/redis/redis-7/redis.lua` is `---@class redis` + ten
        `---@field` constants + a bare `redis = {}` + **thirteen** `function redis.*`, with the ten
        constants **never assigned**. Measured (design §1.10.8a, `=== PROBE TARGET Redis 7+ ===`):
        `redis.` today = the thirteen functions; after Phase 2 = those plus `LOG_DEBUG, LOG_NOTICE,
        LOG_VERBOSE, LOG_WARNING, REDIS_VERSION, REDIS_VERSION_NUM, REPL_ALL, REPL_AOF, REPL_NONE,
        REPL_REPLICA`. `redis:` **unchanged** at the thirteen — the ten are `Kind.FIELD` (their
        `@field` type text is `number`/`string`, not `fun(`) and §4.13's syntactic filter drops them.

        **TC 7f-bis — Valkey 8, which is the control.** `runtime/valkey/valkey-8/redis.lua` has the
        same shape with **twelve** functions and moves the same way. `runtime/valkey/valkey-8/server.lua`
        carries the **same ten `---@field` declarations** *and* writes `server.LOG_DEBUG = 0`-style
        assignments for all ten — so `server.` and `server:` are measured **unchanged** (21 and 11
        members). Assert the non-movement: it is what shows the mechanism is `@field`-**only**
        declaration rather than `@field` as such, and it is why a reviewer cannot dismiss TC 7f as
        "any `@class` stub moves".

        Assert every door as an **exact set** (`assertEquals`, never `contains`).
        ⚠ **Do not write "the thirteen functions" for any other target.** The count is per version —
        10 / 11 / 13 / 12 / 12 on Redis 5, Redis 6, Redis 7+, Valkey 7.2, Valkey 8 — so the two tests
        above name their targets and spell their sets. **Blast radius: five `redis` receivers move**
        (all five Redis/Valkey targets), `server` does not; TC 7f and TC 7f-bis gate the two targets
        whose function sets differ, which covers the family without one test per version.
        **This is also why "exactly two tests move" is a STANDARD-target statement**: DR-21/DR-22
        never set a Redis target, so the armed-suite count says nothing about these.
  - [x] **Write TC 10h and TC 10i/10j** — the three Rule S clauses the mutation proof cannot reach.
        TC 10h goes in `MemberEnumerationRedisTargetTest` (it needs the Redis target): `KEYS.<caret>`
        and `ARGV.<caret>` offer `[]` at both doors, matching today, which is what pins §4.14's
        deliberate exclusion of the `seedAmbientGlobals` declare site (`LuaTypesVisitor.kt:1360`) —
        the one site live on no other target. TC 10i (`name == "self"`) and TC 10j (`:462` type-guard
        narrowing) go with the other Rule S tests; design §4.14 gives both fixtures.
  - [x] **Write the Rule S tests** — TC 10a–10j, one per binding form, each asserting the offered set
        is identical to today's. The prototype's ten scenarios and their output are in design §1.10.4;
        `localVarShadow` is `LuaGlobalMemberCompletionTest.aLocalShadowsTheCrossFileGlobal`'s exact
        fixture and that test must stay green untouched.
  - [x] **Mutation-prove Rule S — twice**, because one clause deletion reaches one clause. (a) Delete
        the `LuaParList` clause from `LuaLocalBindingScan` and TC 10c
        (`local function f(Shadow) Shadow.<caret> end`) must go red, offering `fromLibrary`.
        (b) Delete the `name == "self"` early return and TC 10i must go red. A Rule S test suite that
        survives clause deletion is not testing Rule S; a proof that only ever deletes the same clause
        is not testing the other six.
  - [x] **Replace COMP-09-08 assertion 2 with a count** — design §4.10a-bis. The timing form flips
        verdict between two runs of the same prototype (1x met / 3x not met), which is DR-08's rule
        firing. Assert `LuaReceiverMemberWork.entries` instead: `narrowMembers` for the narrow
        receiver, `wideMembers` for the wide one, neither moving when unrelated indexed content is
        added. Keep the timings as printed records beside assertion 1.
  - [x] Flip `MemberEnumerationLatencyGateTest.BUDGET_ENFORCED` to `true`. It is the phase's exit
        criterion; the inverted gate is currently the thing that goes red when the budget is met.
  - [x] Re-measure cold time-to-first: **five distinct wide receivers, each in its own file**, median
        of five. A single receiver cannot be re-measured cold and produced 13 783 / 35 416 / 49 403 µs
        across three runs of the same code (design §1.10.2).
- **Exit**:
  - **Task 0 landed in its own commit**, golden diff pure-addition (zero `-` lines), no `src/main`
    change in it, new hash recorded.
  - COMP-09-08 assertion 1 **green with `BUDGET_ENFORCED = true`**; assertion 2 green in its count form.
  - The golden is re-recorded and **every diff line is named in the four-row table above** — or, if a
    second colon mover appears, in that table extended and justified in writing first; no `global` or
    `class` row moved.
  - E1–E7 green in `MemberEnumerationExpectationTest`; TC 7f, TC 7f-bis and TC 10h green in
    `MemberEnumerationRedisTargetTest`; TC 10a–10j green.
  - `LuaGlobalMemberCompletionTest` (all six) and `LuaLibraryMemberCompletionTest` green **untouched**.
  - Both Rule S mutation proofs (the `LuaParList` clause and the `self` clause) are recorded in the
    commit message.
  - Full suite `test --rerun --no-build-cache` green.
  - **Corpus baselines unmoved** — `test -PwithCorpus --rerun --no-build-cache`, all four. The
    requirement→phase table assigns COMP-09-06 to Phase 2 and this is what discharges it there. It is
    **not** structurally unnecessary just because §4.13 leaves `forFile` untouched: that argument is
    about the *checker's* inputs, and it is an argument, whereas COMP-09-06's own acceptance says "if
    any baseline moves, enumeration has become a type source: **stop**". A phase that changes what
    completion offers and asserts the checker is unaffected without running the checker is asserting
    the thing under test. The sweep is opt-in and silent when skipped (engineering contract §5), so
    verify `LuaCorpusSweepTest` / `LuaTortureCorpusTest` / `LuaInspectionParityTest` appear in
    `build/test-results/test/` **with fresh timestamps** — `--rerun` does not clear that directory.

## Phase 3: Materialization consumer — **DONE 2026-08-12**

> **BUG-430 is open and Phase 3 proceeds anyway.** The both-directions golden diff runs straight into
> it, so the expected result is stated here rather than discovered: BUG-430 is a **global-door**
> defect (`a.b.c = v` flattens `c` onto `a`), and Phase 3 changes only the **`@class` door**
> (`addMethodsOf`). Design §4.4a establishes that the `@class` door is already correct on `Shapes` —
> `Shapes|class` has no `deep` today — so Phase 3's golden diff must be **empty on the `class` rows**
> and BUG-430 cannot be triggered by it. The one `Shapes|completion` line that does move was already
> moved by Phase 2. **If a `class` row moves in Phase 3, that is not BUG-430 and it is not expected:
> stop.** Gating Phase 3 on BUG-430 would be gating it on an engine-scale change it does not touch.

- **Goal**: both `getAllKeys` scans gone; COMP-09-09's `membersIn` assertions go green.
- **Tasks**:
  - [x] Rewrite `addMethodsOf` — design §4.6; drop the `allKeys` parameter. **Done**: the signature
        is now `(scan: MethodScan, membersMap: MutableMap<String, LuaTypeMember>)` — two arguments,
        the direction the contract's parameter cap wants. The `LuaFuncDecl` lookup is extracted to a
        `declaredMethod(scan, member, scope)` helper, which is where BUG-398's `onlyIn` filter now
        lives.
  - [x] Update both call sites — `collectMethodMembers` (`LuaTypeManagerImpl:519,520,523`) and `materializeUnhostedClass` (`:389`, whose `addMethodsOf` call is `:427`). **All four anchors verified by grep before editing** and all were accurate. `LuaTypeManagerImpl` now contains **zero** `getAllKeys` calls.
  - [x] A test per row of design §4.6's preservation table: `allScope` (BUG-399), first-wins,
        `onlyIn` confinement (BUG-398), nested qualifiers. **`MemberEnumerationMaterializationTest`**,
        on `LibraryRootTestCase` because the BUG-399 row is structurally invisible to a light fixture.
        **Each row is mutation-proven**, and three of the four reddened *exactly* their own test:
        removing the `containsKey` guard reddened only first-wins; replacing `declaredMethod`'s
        filter with a bare `firstOrNull()` reddened only the confinement test; deleting the
        nested-qualifier rule from the **index**'s `split` reddened only the nested-qualifier test —
        which also demonstrates that row is still enforced after moving out of this file. The
        `allScope → projectScope` mutation reddens all four, since every fixture is library-rooted.
  - [x] **Diff the golden in both directions.** A superset is the failure mode (see the hard gate).
        Expect **zero** movement on `class` rows; anything else stops the phase. **Both directions
        empty** (`comm -23` and `comm -13` under `LC_ALL=C`, against the render captured from the
        run itself, not just a re-hash of the untouched file); all 27 `class` rows identical; the
        file's hash is still `38c7586ecfddd17bdb79785b3b3a9f31`.
  - [x] Use §4.10b assertion 4 as the D2-leak detector, read as *"one candidate in, one file read"* —
        a count **above** the candidate count is the leak, a count equal to it is not (requirements.md
        COMP-09-09's acceptance note). **Measured through `resolveType`**, so it observes the
        consumer rather than the index: `entries=50, files=1` for a 50-member receiver, unmoved by
        4 000 unrelated indexed members. `files == 1` equals the candidate count; it is not above it.
  - [x] **BUG-433 is RESOLVED by this phase** (supervisor question). Option 1, with option 2 applied
        belt-and-braces: the unbounded walk is *gone*, not relocated — `getAllKeys` no longer appears
        anywhere in `LuaTypeManagerImpl`, and the two surviving `getAllKeys` calls in `src/main`
        (`LuaHierarchyUtil`, `GlobalSymbolRankingService`) are pre-existing sites this phase never
        touched. The successor loop is bounded by the receiver's own members (measured 50, not 4 145)
        and got a `ProgressManager.checkCanceled()` anyway, while the traversal that replaced the
        scan — `processValues` inside `membersIn` — has carried one since Phase 1. Closing the
        roadmap row is the supervisor's call.
- **Exit**: COMP-09-09 green on both doors; golden byte-identical to Phase 2's re-record; **all four
  corpus baselines unmoved** (`test -PwithCorpus --rerun --no-build-cache`) — if any moves,
  enumeration has become a type source: stop and revert (COMP-09-06).
- **One guard re-earned, not re-baselined.** `LuaTypeSourceRecorderCoverageTest` went red, which is
  the behaviour it is built for: `.getAllKeys(` fell `2 → 0` while a **new kind** of cross-file read
  (`.membersIn(`) arrived that none of its five counted members would have seen. It is registered as
  a sixth door rather than the counts merely being lowered — otherwise the removals would have
  read as a tidy count drop while an unrecognised door came in unremarked. The accounting: the new
  door owes no extra `reportFile`, because `membersIn` yields candidate *names* and a name becomes
  type information only once `declaredMethod` finds a `LuaFuncDecl`, whose `containingFile` is
  reported exactly as the scan reported it.

## Phase 4: `@class` metamethods (COMP-09-05)

- **Goal**: close COMP-04-DR-01 and BUG-426's Known limitation.
- **Tasks**:
  - [x] Contribute `@class`-declared metamethod names to `LuaGraphType.Table.metamethods` — design
        §4.7, **as well as** leaving them in `localMembers`.
  - [x] TC 6: `---@class V` with `__add`; `V() + V()` reports nothing.
  - [x] TC 6a: the offered sets are **unchanged** — `v.` still offers `__add`, `v:` still does not.
        **Settled by DR-12**, which ended a contradiction this plan carried through two reviews: `v.`
        offers `[__add, len, x]` and `v:` offers `[len]` on today's code, so design §4.7 was right and
        this plan and the checklist were both wrong to expect `__add` absent. Assert the measured
        behaviour, not the intent `LuaGraphType.kt:50-52` describes.
  - [x] **Re-check TC 6a at the Phase 2 site.** `V` in TC 6a is a `@class` on a **global**, so after
        Phase 2 the `v.`/`v:` carets may route through the index arm, where the `isColon` filter is
        *syntactic* rather than semantic (design §4.5b's surviving analysis). Assert the offered sets
        directly; do not assume DR-12's pre-Phase-2 measurement still describes them.
  - [x] Close BUG-426's limitation section or restate what remains.
- **Exit**: TC 6 green; TC 6a re-measured post-Phase-2; corpus baselines unmoved.

### Phase 4 outcome (2026-08-12)

- **The change is one branch.** `LuaGraphType.fromLuaType`'s `is LuaClassType ->` arm moved into a
  private `classTable` helper (the arm was inline in an already over-long `when`), which computes
  `type.getMembers()` **once** and passes
  `metamethods = declaredMembers.keys.filterTo(mutableSetOf()) { it in ALL_METAMETHODS }` to the
  `Table` constructor. `localMembers` is populated from the same map, unchanged, so nothing is moved
  out of it — "as well as", per design §4.7. `ALL_METAMETHODS` is the union of the three `Trait`
  subobjects' sets, formed once as a `private val`, because `Trait` is sealed with no aggregate.
- **TC 6a re-measured at this head and it did NOT move.** `v.` = `[__add, len, x]`, `v:` = `[len]`,
  for the class declared on a local *and* on a global — identical to DR-12's pre-Phase-2 figures. The
  receiver-name carets on the global form are `Vec.` = `[__add, len, x]`, `Vec:` = `[__add, len]`;
  `Vec:` keeping `__add` is the index arm's syntactic `isColon` filter (TC 7e's known divergence),
  not a metamethod-visibility change, and the instance carets TC 6a is about are unaffected.
- **TC 6's fixture as the requirements wrote it could not fail** — `local a, b = V(), V()` reports
  nothing on the *pre-change* code, because `V()` infers `Undefined`. Corrected in requirements and
  asserted with `---@type V` operands, which report `V is not assignable to number` before and
  nothing after.
- **Mutation-proved four ways.** Dropping the contribution is CAUGHT by five tests; weakening
  `implementsOperator` to `metamethods.isNotEmpty()` is CAUGHT by the per-trait test; **loosening the
  name filter to `startsWith("__")`, and dropping the filter entirely, both SURVIVE every
  operator-level test** — `implementsOperator` re-tests the name against the trait's own set — and
  both are **CAUGHT** by a direct read of `Table.metamethods` off `LuaGraphType.materialize`
  (`testOnlyKnownMetamethodNamesReachTheMetamethodSet`).
- **CORRECTED 2026-08-12 (Phase 4 remediation).** The bullet above previously recorded the third
  mutation as proof the filter "is not independently observable", and the test pinning it was
  deleted. The survival is real; the conclusion was not — it holds only *through*
  `implementsOperator`, and a second door observes the filter exactly. Two further corrections landed
  with it: the control's rationale (it does **not** catch `metamethods = declaredMembers.keys`;
  measured, all nine assignability tests pass under that mutation) and `ALL_METAMETHODS` becoming
  `by lazy` to break a class-initialization cycle the new test exposed. See risks-and-gaps,
  "Phase 4 findings".

## Phase 5: Re-measure and decide the deferrals

- **Goal**: establish what is left, rather than assuming it is done.
- **Tasks**:
  - [x] Re-measure both doors, medians of ≥5, against the 100 ms NFR — **five distinct wide receivers
        in five files**, never one receiver re-measured. **Door 1 (completion) 12 225 µs — MET. Door 2
        (`@class`) 269 459 µs direct / 322 692 µs through `completeBasic()` — MISSED, 3× the budget,
        deferred as BUG-438 / DR-29.** Design §1.11.1.
  - [x] *(design §4.10 says Phase 4 measures these; it is Phase 5 — this plan is authoritative.)*
  - [x] **Settle DR-23** — Rule S's residual cost on the "consumer file binds a name that is also a
        project-wide global" shape, at 4 000+ lines. **Confirmed at 10.9–14.7 ms (560 µs when the
        binding is early enough to exit on); it STAYS as written.** The cached bound-name set is
        rejected on a mechanism, not a cost: Rule S reads the per-session completion **copy**, so a
        cache on it can never hit, and one on `originalFile` invalidates on every keystroke in exactly
        the file DR-23 is about. Design §1.11.2.
  - [x] **Settle DR-24** — a file whose *only* receivers are opaque still pays one snapshot build.
        **Measured with no tier-1 receiver anywhere before it: median 43.7 / 36.7 ms across two runs,
        inside budget.** The worst sample is the first — the shared library's snapshot build,
        *(single — unrepeatable by construction)*, 99.5 ms one run and 260.3 ms the other — recorded
        as §4.12's stated watch item. Design §1.11.3.
  - [x] **Settle DR-25** — `LuaReceiverMemberIndex.Indexer.map`'s repeated `findChildrenOfType`
        passes. **Re-measured rather than reusing DR-18, and the naive re-measure was wrong**: the
        cost follows the measurement *position*, because the first tree toucher pays the file's AST
        expansion. Expansion-free the three indexers are **67 / 20 / 6 ms**, reproducing DR-18's
        61 / 20 / 6 — DR-18 confirmed. `map` has **five** call sites over three types, three of them
        the same `LuaAssignmentStatement` walk; one shared walk costs the same as one pass, so ~40 ms
        of the 67 ms is redundant. **Worth doing; deferred to BUG-437.** Design §1.11.4.
  - [x] Measure whether COMP-09-02's remaining sites are now under budget or still need work — DR-16's
        outstanding half. **`catsClassTags` 11.0 ms + `LuaImplicitFields` 17.8 ms = 29 ms of the
        `@class` door's 269 ms, so they are not its bottleneck; `seedAmbientGlobals` walks a 7-line
        file on five targets and none on the default.** Anchors re-grepped first, and the task list's
        "`catsClassTags`" and "`LuaTypeManagerImpl:436`" are **the same site** — three sites, not
        four; `LuaMemberFieldNavigation:32` is navigation and out of scope. Design §1.11.5.
  - [x] Re-measure each of the four caches (design §2) and either remove as redundant or record why
        it stays. **All four measured, all four KEEP, none recommended for removal.** Design §1.11.6.
  - [x] Decide DR-07 (narrowing invalidation) on the numbers. **Closed: nothing remains.** The
        post-edit case is TYPE-11's; the cold completion door is 12.2 ms and no invalidation beats a
        path that never builds the snapshot; and the one door still over budget misses on a **first
        build**, which narrowing cannot make cheaper. Design §1.11.7.
  - [ ] Run [human-verification-checklists.md](human-verification-checklists.md) — **NOT run in
        Phase 5, deliberately.** See "The live checklist" below: 15 of its 18 scenarios are discharged
        by automated real-flow tests, and whether the remaining 3 warrant a live GoLand pass is the
        supervisor's call, not the implementor's.
- **Exit**: every acceptance criterion ticked or explicitly deferred with a reason; every DR row in
  risks-and-gaps is `done` or `deferred with a named owner`.

### Phase 5 outcome (2026-08-12) — measurement only, no production change

Every figure lives in design §1.11 with its raw output pasted. `git diff -- src/main` is empty: the
probe (`CompNinePhase5Probe`) and a temporary five-way split inside
`LuaReceiverMemberIndex.Indexer.map` were made under the `temporary-edits` snapshot loop, verified to
have applied with `scratch_changed`, and restored with `scratch_end` — never `git checkout`. **The
corpus sweep was deliberately not re-run**: it gates production changes and there are none.

**Three findings that reversed a premise rather than confirming one.**

1. **Both doors is not one answer.** The completion door meets NFR-1 with an order of magnitude to
   spare; the `@class` door misses it by 3×. Nothing in the feature had ever measured door 2 cold as
   a median of five, so "COMP-09 meets its latency target" was true only of the door the gate
   asserts. Now stated per door, and the miss has an owner.
2. **A timing harness can be defeated by its own ordering.** Timing `map` first reported 256 ms;
   timing it third reported 67 ms, for identical code. `FileContentImpl` expands the AST on first
   *tree access*, not on `getPsiFile()`, so whichever indexer runs first pays the whole parse.
   Reordering — twice, once inside `map` and once across the three extensions — is what established
   it. DR-18 had already avoided this trap by measuring warm, so its numbers survive; the naive
   re-measure would have libelled its own index by 4×.
3. **DR-23's remedy was unavailable, not merely unnecessary.** The proposed cached bound-name set
   cannot be built at all against a per-session completion copy. That is a stronger close than "the
   cost is acceptable", and it came from reading what the call site already documents rather than
   from the timing.

### The live checklist — what it would cover, and what is already discharged

Not run, per the Phase 5 brief. Recorded so the decision can be made on evidence.

**Already discharged by automated real-flow tests** (all through `completeBasic()` against a real
`SyntheticLibrary` root, which is the blind spot `LibraryRootTestCase` exists to remove):

| Scenario | Discharged by |
| :-- | :-- |
| 1.2 the costly keystroke, 1.3 large project / unrelated content, 1.4 small and huge both cold | `MemberEnumerationLatencyGateTest` — cold time-to-first over five distinct wide receivers, plus the `entries` count across 4 000 added unrelated members |
| 1.5 the `require`-bound receiver | the gate's recorded tier 2, plus Phase 5 DR-24 (five opaque receivers, five files) |
| 1.6 Rule S's residual on a big shadowing file | Phase 5 DR-23's medians, plus `MemberEnumerationShadowingTest` |
| 1.7 shadowing by every binding form | `MemberEnumerationShadowingTest` TC 10a–10j, both clause deletions mutation-proved |
| 1.8 Redis ambient globals + `@class` stub, 1.9 Valkey `server.` must not move | `MemberEnumerationRedisTargetTest` on real `Target(REDIS, 7+)` / `Target(VALKEY, 8)` |
| 2.1 colon-declared methods | `MemberEnumerationGoldenTest`'s colon rows (DR-27) + `MemberEnumerationExpectationTest` |
| 2.2 nested qualifiers stop being members | the golden + `CompNineDr12Test` (BUG-430, declared) |
| 2.5 a `@field` that did not complete before | E2/E6/E7, TC 7c, TC 7f/7f-bis |
| 3.1 a `@class`-declared `__add` | `MemberEnumerationMetamethodTest`, 11 tests, exact offered sets |
| 3b.1/3b.2 during and after indexing | `CompNineDr10Test` + the `found = false` decline path (DR-10) |
| 4.1 the checker's view is unchanged | the golden + the corpus sweep's `LuaInspectionParityTest` |

**Not discharged — a human is the only instrument**, and the checklist says so itself for two of the
three:

- **1.1 "First `wx.` in a session"** — *perceived* latency against a real 230 KiB TARGET-10 library
  in a running IDE. The automated figure is a synthetic fixture; perception is not a fixture result.
- **2.3 type text absent on the cross-file path** — a deliberate trade (design §4.5); the checklist
  calls it "the one judgement no automated test can make", and its outcome decides whether the index
  value should carry a type string.
- **2.4 go-to-declaration and the override gutter marker** — `materializeClass`'s parity harness
  compares names and types only, so the checklist marks this "checkable *only* by hand".

**Recommendation, for the supervisor to accept or reject:** run a live pass covering exactly 1.1, 2.3
and 2.4. The other fourteen would re-check by hand what a gate already asserts on every run.

## Requirement → phase coverage

| Requirement | Phase |
| :-- | :-- |
| COMP-09-01 receiver-keyed enumeration | 1, 2, 3 |
| COMP-09-02 no full-file walk | 3, 5 *(remaining sites measured, not assumed)* |
| COMP-09-03 all sources, dot and colon | 1 |
| ~~COMP-09-04 / 04b~~ | withdrawn (design §1.7) |
| COMP-09-05 `@class` metamethods | 4 |
| COMP-09-06 no new type source | 0 (golden), 2 (completion door + corpus gate), 3 (class door + corpus gate) |
| COMP-09-07 behaviour-preserving | 0, 2, 3 |
| COMP-09-08 latency enforced | 0 (red), 2 (green — assertion 1 wall-clock, assertion 2 as a count) |
| COMP-09-09 work bound enforced | 0 (red), 2 (`globalMembership` door), 3 (`membersIn` door) |
| COMP-09-10 the shadowing rule *(new)* | 2 (TC 10a–10j, both mutation proofs) |

## Task summary

| Phase | Status | Priority |
| :-- | :-- | :-- |
| 0: Golden file and instrument | **done** (2026-08-09) | Must |
| 1: `LuaReceiverMemberIndex` | **done** (2026-08-09, remediated 2026-08-09) | Must |
| 2: The change site — hoist above the snapshot build | **done** (2026-08-12; re-cut that day from a measured prototype, superseding the aborted "Completion consumer") | Must |
| 3: Materialization consumer | **done** (2026-08-12, remediated 2026-08-12) | Must |
| 4: `@class` metamethods | **done** (2026-08-12) | Should |
| 5: Re-measure and decide deferrals | **done** (2026-08-12) — measurement only, no production change; the live checklist is deliberately not run and is the supervisor's call | Must |
