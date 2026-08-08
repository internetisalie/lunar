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

> **Two standing rules.** (1) Any figure quoted in a doc or a commit is a **median of ≥5**; design
> §1.8 records a −60 % single-shot spread and one flipped verdict. (2) No benchmark may cross a
> reindex boundary (design §4.8).

## Phase 0: Golden file and instrument

- **Goal**: record today's behaviour before touching it, and make the gates able to fail.
- **Tasks**:
  - [ ] Promote `CompNineDr01Test`'s enumeration dump into a checked-in golden covering, for **both**
        `resolveGlobal` and `resolveType` per receiver (design §1.4 — `wx` answers differently through
        each): a namespace global, a `@class` with dot *and* colon members, and an all-colon `@class`.
  - [ ] Add the `LuaOverrideLineMarkerProvider` case to the golden — `sourceElement` is load-bearing
        (design §4.1) and `materializeClass:256-262` warns the parity harness cannot see it.
  - [ ] Write **COMP-09-08**'s latency assertion (time-to-first-element vs the 100 ms NFR) and
        **COMP-09-09**'s entries-traversed assertion. Both must **fail** on today's code — mutation-prove
        them now, not after (TC 8, TC 9).
  - [ ] Convert the three throwaway harnesses to medians of ≥5 and delete the single-shot variants.
- **Exit**: golden checked in; two gates red; DR-02c re-run with medians and its verdict recorded.

## Phase 1: `LuaReceiverMemberIndex`

- **Goal**: the index exists and is correct; nothing consumes it yet.
- **Tasks**:
  - [ ] Create `net.internetisalie.lunar.lang.indexing.LuaReceiverMemberIndex` + `LuaReceiverMember`
        — design §4.2, incl. the `DataExternalizer<List<LuaReceiverMember>>`.
  - [ ] Implement the indexer — design §4.3 (both declaration forms; `FUNC_NAME` text read the same
        way `LuaFuncStubElementType.createStub:24` reads it, so the two agree by construction).
  - [ ] Implement receiver derivation — design §4.4, **first separator, reject nested qualifiers**.
  - [ ] Register in `plugin.xml` beside the five existing `fileBasedIndex` entries — design §4.8.
  - [ ] Tests: dot member, colon member, all-colon receiver, nested qualifier (`a.b.c` → **no**
        entry), same receiver across two files (union), and `getVersion` bump behaviour.
- **Exit**: index tests green; `LuaMemberFieldIndexTest.testDeepQualifiedKeyPresent` still green
  (that index is untouched); full suite green.

## Phase 2: Completion consumer

- **Goal**: `wx.<caret>` served from the index; COMP-09-08 goes green.
- **Tasks**:
  - [ ] Rewrite `crossFileGlobalMembers` — design §4.5.
  - [ ] Add `LuaMemberLookup.create(LuaReceiverMember)` — icon from `kind`, **no type text** on this
        path (design §4.5).
  - [ ] Amend TC 3 to expect absent type text on the cross-file path. This is a **visible behaviour
        change** and must be an expectation, not a silent diff.
  - [ ] Re-measure time-to-first-element, medians of ≥5.
- **Exit**: COMP-09-08 green; golden unchanged for the in-file path; TC 6, 7, 7a, 14 green.

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
  - [ ] Assert `t.__add` still does **not** complete on an instance (`LuaGraphType.kt:50-52`).
  - [ ] Close BUG-426's limitation section or restate what remains.
- **Exit**: TC 6 green; corpus baselines unmoved.

## Phase 5: Re-measure and decide the deferrals

- **Goal**: establish what is left, rather than assuming it is done.
- **Tasks**:
  - [ ] Re-measure both doors, medians of ≥5, against the 100 ms NFR.
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
