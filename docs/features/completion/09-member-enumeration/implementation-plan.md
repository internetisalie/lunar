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
> an **engine defect** (design §4.4a): the global and `@class` doors disagree about `a.b.c = v`, and the global door is wrong
> twice. COMP-09 preserves the **`@class` door** and deliberately does not reproduce the flattening;
> COMP-09-07's bar is redefined accordingly (design §4.4a) and Phase 0's golden must record which
> door each receiver is measured through.
>
> **DR-10 still blocks Phase 1**: dumb-mode behaviour is asserted in design §4.9 and measured nowhere.

> **Two standing rules.** (1) Any figure quoted in a doc or a commit is a **median of ≥5**; design
> §1.8 records a −60 % single-shot spread and one flipped verdict. (2) No benchmark may cross a
> reindex boundary (design §4.8).

## Phase 0: Golden file and instrument

- **Goal**: record today's behaviour before touching it, and make the gates able to fail.
- **Tasks**:
  - [ ] Promote `CompNineDr01Test`'s enumeration dump into a checked-in golden covering, for **both**
        `resolveGlobal` and `resolveType` per receiver (design §1.4 — `wx` answers differently through
        each): a namespace global, a `@class` with dot *and* colon members, and an all-colon `@class`.
        **Label each receiver with the door it is measured through** — design §4.4a: for `a.b.c = v`
        the two doors disagree and the global door's answer is wrong, so an unlabelled golden would
        certify a bug as the contract.
  - [ ] Add the `LuaOverrideLineMarkerProvider` case to the golden — `sourceElement` is load-bearing
        (design §4.1) and `materializeClass:256-262` warns the parity harness cannot see it.
  - [ ] Write **COMP-09-08**'s latency assertion (time-to-first-element vs the 100 ms NFR) and
        **COMP-09-09**'s entries-traversed assertion. Both must **fail** on today's code — mutation-prove
        them now, not after (TC 8, TC 9).
  - [ ] Convert the three throwaway harnesses to medians of ≥5 and delete the single-shot variants.
- **Exit**: golden checked in; two gates red; DR-02c re-run with medians and its verdict recorded.

## Phase 1: `LuaReceiverMemberIndex`

- **Goal**: the index exists and is correct; nothing consumes it yet.
- **Blocked on DR-10** (dumb-mode behaviour).
- **Starting point**: DR-09's prototype is already in `src/main` and registered. Phase 1 is
  *finishing* it, not writing it — the class, externalizer, three-source indexer and registration
  exist and are measured (design §4.0). What is missing is below.
- **Tasks**:
  - [ ] Replace the prototype's `membersOf` union with the measured rule — design §4.5: try
        `projectScope`, fall back to `allScope`, and within the chosen scope take **the first
        declaring file only**. Keep the union available as `membersIn(receiver, scope)` for
        materialization (design §4.6), which genuinely wants all files.
  - [ ] Re-run `CompNineDr09Test.testDr09b` and `testDr09d2` with that rule. Membership matching on
        all four receivers is the **gate**; D2's superset is fixed on paper until it does.
  - [ ] Add `ProgressManager.checkCanceled()` to the value-processing loop and the dumb-mode
        behaviour DR-10 establishes — design §4.9.
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
  - [ ] Rewrite `crossFileGlobalMembers` — design §4.5. D1 and D2 are **decided and measured**; the
        rule lands in Phase 1, so this phase consumes it rather than deciding it.
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
  - [ ] **Settle the contradiction first.** Design §4.7 says a `@class`-declared `__add` is *already*
        in `localMembers` and so already completes; this plan and the checklist both expect it **not**
        to. `LuaGraphType.fromLuaType:267-277` copies every member into `localMembers`, which favours
        the design — but none of the three was executed. Run it, then assert whichever is true.
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
| 0: Golden file and instrument | todo | Must |
| 1: `LuaReceiverMemberIndex` | todo | Must |
| 2: Completion consumer | todo | Must |
| 3: Materialization consumer | todo | Must |
| 4: `@class` metamethods | todo | Should |
| 5: Re-measure and decide deferrals | todo | Must |
