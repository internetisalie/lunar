---
id: "COMP-09-DESIGN"
title: "Technical Design"
type: "design"
parent_id: "COMP-09"
folders:
  - "[[features/completion/09-member-enumeration/requirements|requirements]]"
---

# Technical Design: COMP-09 — Member Enumeration

## 1. What the de-risking measured, and what it overturned

Run 2026-08-07 on gce-builder (`CompNineDrSpikeTest`). Two results contradict what BUG-429 and an
earlier revision of COMP-09-01 asserted. Both assertions were read off call shapes; neither survived
being run.

### 1.1 The critical path is NOT `materialize` (DR-02)

```
resolveGlobal   9 568 ms      <- 99.9 %
materialize        10 ms
getMembers          0 ms      (3 700 members)
```

BUG-429 stated that `LuaGraphType.materialize(global).getMembers()` "builds the complete type graph
for every member … before the loop yields its first element". **It does not.** Materialization and
enumeration together are 10 ms. The entire cost is `LuaTypeManager.resolveGlobal`, and inside it:

```
resolveGlobal → doResolveGlobal → typeOfGlobalIn(scope) → globalTypeIn(file)
                                                          → LuaTypesSnapshot.forFile(declaringFile)
```

`LuaTypesSnapshot.forFile` builds the **entire per-file type graph for the 242 KiB declaring
library file** in order to answer one question: what type does it give the global `wx`.

**This reframes the capability.** The missing thing is not only "enumerate members from an index" —
enumeration is already fast. It is *"answer a symbol's type without building its declaring file's
whole graph"*. The two `getAllKeys` scans are real (§1.3) but they are inside the 10 ms, and fixing
them would not have moved the headline number at all.

### 1.2 An unrelated edit costs 200 ms+ — REMEASURED TWICE (DR-02c, medians of 5)

The original version of this section reported cold 800 / warm 0 / after-one-keystroke 608 ms and
called it "76 % repaid". Every figure was single-shot, Step 9 re-ran the harness and got the
*opposite* verdict, and DR-02c was reopened. Redone with medians of 5, and with **time-to-first**
(the quantity the NFR names) rather than time-to-exhaustive:

| | time-to-first |
| :-- | --: |
| cold | **1 152 ms** |
| warm, no PSI change | **1.0 ms** |
| after one keystroke **in the consumer file** | **264 ms** (samples 217 / 221 / 264 / 271 / 599) |

**The old headline was wrong twice.** The "76 % repaid" is withdrawn — this run put it at 22 %, and
the re-run below moved it again to 48 %, so the repaid *fraction* is withdrawn as a quantity too and
nothing in this plan may cite one. And the per-keystroke-vs-per-session framing is a false dichotomy
— it is neither. An unrelated edit does not repay the full build, but it takes completion from ~1 ms
to 264 ms, which is **over budget by more than a factor of two under both runs** (223 ms on the
re-run). Both halves of the old wording flattered the code in one direction and the problem in the
other.

The mechanism is as described **for the code this run measured, which TYPE-11 has since changed**:
`typeCache` (`LuaTypeManagerImpl.typeCache`, `LuaTypeManagerImpl.kt:55-65`) and the per-file
snapshot's dependency array (`LuaTypes.dependenciesFor`, `LuaTypes.kt:281-289`) both carried
project-wide `PsiModificationTracker.MODIFICATION_COUNT`, so an edit anywhere invalidated the
*library's* snapshot. **That is no longer the mechanism for a pinned library file** — see §2's
corrected row and the re-plan preamble. The NFR's per-keystroke clause was right that repeated typing
pays repeatedly; it is wrong that the mechanism is cancellation and restart, and wrong that each
keystroke pays the *whole* cost.

**Phase 0 re-ran it (2026-08-09), and the RATIO is not stable either.** Same harness, same medians of
five, different machine load:

| | recorded above | Phase 0 re-run |
| :-- | --: | --: |
| cold | 1 152 ms | **463 ms** |
| warm | 1.0 ms | **0.9 ms** |
| after one keystroke in the consumer | 264 ms | **223 ms** (183/197/223/232/235) |
| "repaid" | 22 % | **48 %** |

So the **direction survives and the precision does not**. What is stable across both runs, and is all
this section is allowed to claim: cold is multiple hundreds of milliseconds and far over budget; warm
is ~1 ms; and an unrelated keystroke costs 200 ms or more, which is over budget on its own. What is
*not* stable is the repaid fraction — and note the harness's own printed verdict switches on
`afterEdit > cold/2`, which at 223 ms against a 231 ms half-cold is decided by 8 ms of noise. **The
per-keystroke/per-session dichotomy is not merely false, it is unmeasurable by this harness**, which
is the sharper form of what §1.2a already concluded. DR-07's decision is unaffected: narrowing cannot
touch the cold figure under either run.

### 1.2a §3.3 / DR-07 DECIDED — narrowing invalidation is a complement, not an alternative

The open question was whether narrowing cache invalidation would beat indexing, which would make this
whole feature the more expensive of two options. The figures above answer it — **directionally, which
is the only form the answer is allowed to take**:

- narrowing invalidation could at best take the post-edit case down to the warm figure: roughly
  **200 ms to ~1 ms** (264 → 1.0 first run, 223 → 0.9 on the Phase 0 re-run). That is a real win and
  worth having.
- it cannot touch **cold**, which is *hundreds* of milliseconds — 1 152 ms first run, 463 ms on the
  Phase 0 re-run, 392 ms in the Phase 0 review's independent run — and is over the 100 ms budget
  under every one of them. Cold is the first completion of every session, the moment a user is most
  likely to notice.
- it also does nothing about time-to-first tracking member count (§1.9), because that is present cold
  and warm alike. That factor is now **derived inside the gate on every run** against the harness's
  own noise floor (DR-17) rather than quoted as a constant.

**No ratio between two of these figures is quotable**, and an earlier draft of this section quoted
three: `264 ms`, `1 152 ms`, and "the budget is missed by 11.5x". §1.2's own re-run moved two of them
by more than a factor of two, and the per-keystroke/per-session dichotomy turns on a margin —
`afterEdit` against `cold/2` — smaller than this harness's run-to-run spread. That is DR-08's standing
consequence applied here, not a new caveat.

**Decision: index, and keep narrowing as a separate follow-up** — unchanged, because it never rested
on the precision. Cold is the first completion of every session and narrowing invalidation cannot
touch it *by construction*: there is no prior state to keep. They fix disjoint halves; only indexing
fixes the half that is over budget on a clean IDE start. DR-07 closes here rather than staying a "TBD
under Technical Debt with no task", which is what Step 9 flagged it as.

### 1.3 The receiver key is dot-only (DR-06)

Raised in review against COMP-09-01's claim that swapping `collectMethodMembers`' scan for
`getElements(KEY, receiver)` is "a strict simplification". Measured:

```
keys under ColonHost : [ColonHost, ColonHost.staticDot, ColonHost:dotless, ColonHost:scale]
getElements(KEY, "ColonHost") -> [ColonHost.staticDot]        <- dot form only
getElements(KEY, "wx")        -> [wx.wxFileExists, wx.wxFrame]
```

`LuaFuncStubElementType.indexStub` (`LuaFuncStubElementType.kt:69-75`) sinks a receiver key only when
the name contains `'.'`, while `LuaTypeManagerImpl.memberNameOf` (`LuaTypeManagerImpl.kt:562`) matches
`receiver.` **and** `receiver:`. So the swap would silently drop every
colon-declared method — a correctness regression, not a simplification. `function C:m()` is the
dominant idiomatic form for class methods.

**And one step further than the review went:** the `ColonHost` receiver key exists *only because
`ColonHost.staticDot` happens to use the dot form*. A class whose members are all colon-declared has
**no receiver key at all**. The keying is incidental to member style, not a property of the class.

### 1.4 DR-01, done properly — and it makes DR-06's risk concrete

Redone across both entry points, with colon methods in the fixture:

| receiver | `resolveGlobal` | `resolveType` | golden members |
| :-- | :-- | :-- | :-- |
| `wx` (`---@class wx` + `wx = {}`) | `LuaTableLiteralType` | `LuaClassType` | `wxFileExists, wxFrame, wxID_ANY, wxID_OK` |
| `wxFrame` (`local` + `@class`) | **null** | `LuaClassType` | `GetTitle, Show, staticCount` |
| `AllColon` (all members colon-declared) | **null** | `LuaClassType` | `alpha, beta` |

Three things fall out.

**Today's scan DOES enumerate colon methods.** `wxFrame:Show`, `wxFrame:GetTitle`, `AllColon:alpha`
and `AllColon:beta` are all present, because `memberNameOf` matches `receiver:`. So DR-06's risk is
no longer hypothetical: **`AllColon` would go from 2 members to 0** under the proposed swap, and it
is now golden-file-verified rather than argued.

**A receiver can resolve through *both* entry points, with different types.** `wx` is
`LuaTableLiteralType` via `resolveGlobal` and `LuaClassType` via `resolveType`, because it carries
both `---@class wx` and `wx = {}`. So COMP-09-07's golden file must record **both** answers per
receiver; capturing one would let a change alter the other undetected. The first DR-01 attempt took
`viaGlobal ?: viaType` and would have done exactly that.

**`resolveGlobal` is not the only door.** COMP-09-01's site list is written against the
`resolveGlobal` path; the `resolveType` path reaches enumeration through `materializeClass` /
`materializeUnhostedClass` — which is where the two `getAllKeys` scans live. Two doors, one room.

## 1.5 §3.1 ANSWERED — the graph build is the cost, and names can bypass it

**(b) `LuaTypesSnapshot.forFile` carries essentially the whole path.**

```
LuaTypesSnapshot.forFile(library)          823 ms   (123 KiB root)
resolveGlobal, snapshot now warm            53 ms
```

So the answer to "can a global's type be answered without `forFile`?" is: **not today** — the type
*is* the graph. But that is the wrong question, which (c) shows.

**(c) Member NAMES can be had from the existing indexes without any graph — and the bottleneck is
not where I assumed.**

```
getElements(KEY,"wx")                 200 names   296 ms
LuaMemberFieldIndex full key scan   25 335 keys    44 ms      <-- the "expensive" scan
index-only name enumeration, total              340 ms
compare: resolveGlobal                        9 568 ms
```

⚠ **WITHDRAWN — see §2.** The figures once quoted here (`getAllKeys` 44 ms / `getElements` ~1.5 ms
each) compared two different index subsystems and printed a filtered match count rather than a key
total. Neither is evidence for anything. Candidate C — 43 ms for a 17 234-key scan *plus* 500
`getElements` — is the only figure from this run that holds, and it says the scan is not the
dominant term. COMP-09-09's bound is **entries traversed**, per `non-functional.md`.

**Which gives the design its answer** — and note the 340 ms total above is the sum of two withdrawn
components, so it is quoted as the *shape* of the result, not as a figure. The load-bearing evidence
is §4.0's `membersOf` at **2 ms for 3 600 members** against `resolveGlobal`'s **13 655 ms** on the
same fixture, measured on the built index. Index-backed name lookup beats the graph by three orders
of magnitude and still loses if it loads stubs, so names must come from the index *value*, not from
the elements:

> A receiver-keyed index whose **value is the member name**, so enumeration is a key lookup returning
> strings with zero stub deserialisation and zero graph construction.

`LuaMemberFieldIndex` already has a value field, currently `""` (`:71`) — "The value is unused;
navigation re-resolves the field identifier on demand". That is the field to use, and it is why the
fix is an index *shape* change rather than a new subsystem.

Types then arrive separately, and only where needed: the checker keeps `forFile`, and completion
renders type text lazily per visible row or not at all.

## 1.6 §3.2 ANSWERED — the `@class` door has a different bottleneck, and one fix still covers both

```
resolveType("Big0")  cold                       949 ms   (500 members)
resolveType("Big1")  same file, warm            167 ms   (500 members)
A  forFile(big.lua), measured AFTER the above  1674 ms   <-- still COLD
B  catsClassTags-shaped walk, AST warm            22 ms
C  getAllKeys(17 234) + getElements per match     66 ms
D  first AST walk of an untouched 253 KiB file   352 ms   (~333 ms of it parse)
```

**The `@class` door never builds the type graph.** Candidate A was measured *after* two
`resolveType` calls and was still 1 674 ms — so `forFile` had not been invoked. This door is
therefore *not* the §1.1 defect, and §3.2's premise ("if the two have different bottlenecks, one fix
does not cover both") was right about the first half.

**Its cold cost is file-level one-time work, not enumeration.** 949 ms cold against 167 ms once the
file is warm. An equivalent untouched file costs 352 ms just to parse and walk (D), so the parse is
the largest identified component. ~430 ms of the cold cost remains unattributed — probably stub
construction and the first `getContainingFiles` touch. **Recorded as unattributed rather than
assigned**, since attribution by elimination is what produced the two errors in §1.1 and §1.5.

**Its marginal cost is ~167 ms per class** for 500 members — already over the 100 ms budget on its
own, before any file-level cost. About half is B + C (88 ms); the rest is `funcTypeFromStub` per
method plus `LuaImplicitFields`.

**But one fix still covers both doors**, because the index-value enumeration of §1.5 needs neither
PSI nor stubs:

| door | what it pays today | what an index value removes |
| :-- | :-- | :-- |
| `resolveGlobal` | `forFile` graph build, 823–1 674 ms | all of it (§1.5) |
| `resolveType` | AST parse 352 ms + walk 22 ms + scan/stub loads 66 ms + per-method stub reads | the parse, the walk and the stub loads |

So the answer to §3.2 is: **different bottleneck, same remedy.** That is a better outcome than the
question anticipated, and it is why the fix is one index change rather than two subsystems.

**Caveat that survives.** The `@class` door needs member *types* (`funcTypeFromStub`), not only names.
An index of names serves completion; the checker still needs types and therefore still pays. Which is
exactly §1.5's split, arrived at independently from the other door.

## 1.7 DECISION (REVISED after adversarial review) — no incremental yield, no lazy rendering

An earlier revision withdrew COMP-09-04 (incremental yield) and replaced it with COMP-09-04b (lazy
type rendering), on the claim that eager type text would cost 3 600 × 1.5 ms ≈ 5.4 s and that
`renderElement` is called per visible row. **Both halves were wrong.** Step 9 review caught them; both
are now measured.

**There is no per-element type cost.** Median of 5, 3 700 members — `memberNode.write` plus
`displayName()` plus the `isColon`/`is Function` filter that `LuaCompletionContributor:384` applies:

```
per-member type + displayName, 3700 members:  median 4 ms  (min 4, max 11)
```

`memberNode.write` comes from the already-materialized graph (`materialize` 6–10 ms, `getMembers`
0 ms) and `LuaGraphType.displayName()` (`LuaGraphType.kt:149-173`) is a pure structural `when` with no
PSI, index or stub access. The "1.5 ms per element" was `StubIndex.getElements` — stub
deserialisation, a different operation — and the same design's candidate C did 500 `getElements` in
43 ms (0.09 ms each), contradicting it in the same document.

**And `renderElement` is not per visible row.** `BaseCompletionLookupArranger.addElement`
(`BaseCompletionLookupArranger.java:186-189`) calls it for
every element added, `LookupImpl.java:410` again on `addItem`, and `LookupElement.java:122-131`'s
javadoc says it "is called before the item can be shown … should be relatively fast … If there are
heavy computations involved, consider … moving into `getExpensiveRenderer()`". The per-visible-row API
is `getExpensiveRenderer` / `LookupElementBuilder.withExpensiveRenderer`, which these artifacts never
named.

**So both requirements are withdrawn, and nothing replaces them.** Presentation was never on the
critical path; the whole cost is *reaching* the members (§1.1, §1.6). Deleting COMP-09-04b removes the
only requirement that touched the completion contributor or `LuaMemberLookup`, which makes this
feature purely an indexing change.

NFR-2c keeps "incremental" as a property not to break. If a future measurement shows enumeration
itself slow, COMP-09-04 returns — and its mechanism would then be `getExpensiveRenderer`, not
`renderElement`.

## 1.8 Measurement discipline — the figures in §1.2/§1.6 were single-shot

Step 9 re-ran all three harnesses. `resolveType` cold came back 383 ms against the 949 ms recorded
(−60 %), and **§1.2's harness printed the opposite verdict** — `afterEdit > cold/2` evaluated false,
so it reported "once per session" where the recorded run reported "per-keystroke". Single
`measureTimeMillis` calls with no warm-up: every ratio derived from a *pair* of them sat inside its own
noise floor.

Re-measured with medians of 5:

```
classDoor warm-file + cold-class, 500 members:  median 120 ms  (min 110, max 154)
classDoor cold-file + cold-class:               1174 ms  (single — unrepeatable by construction)
per-member type + displayName, 3700 members:    median   4 ms  (min 4, max 11)
```

120 ms is still over the 100 ms budget, so §1.6's conclusion survives on better numbers. **The
per-keystroke claim does not** — see §2's correction table. Any figure quoted from here on is a median
of ≥5 or is marked single-shot.

## 1.9 NFR-1 MEASURED at last (DR-02a) — time-to-first is 746 ms and scales with member count

`completeBasic()` returns only when completion has finished, so until now **no time-to-first-result
figure existed for any fixture in this plan**, including the ones §1.1–§1.7 quote. DR-02a built the
observer: a `CompletionContributor` registered `LoadingOrder.FIRST` for Lua which calls
`runRemainingContributors` and timestamps each result as the platform produces it. The numbers come
from a real `completeBasic()` run, not from calling the contributor by hand with a synthesised
parameter object.

| | |
| :-- | --: |
| **cold time-to-first**, 3 600 members | **746 ms** |
| cold time-to-exhaustive | 777 ms |
| **gap first → exhaustive** | **31 ms (4 %)** |
| warm time-to-first, median of 5 of the *same* completion | 1.1 ms |
| cold time-to-first, **3-member** receiver in its own file | 41 ms |
| cold time-to-first, **3 600-member** receiver in its own file | 1 641 ms |

Three things follow, and two of them are new.

**First == exhaustive is now measured, not structural.** DR-02 argued it from the shape of the emit
loop; the gap is 31 ms against a 746 ms total. That is what withdrawing COMP-09-04 (incremental
yield) rested on, and it now rests on a measurement instead of a reading — which matters, given the
record.

**NFR-1 is missed by 7.5x**, and this is the first time the target has been compared against the
quantity it actually names. §1.6's 167 ms was time-to-exhaustive on a warm file.

**The NFR's independence clause is violated outright.** `non-functional.md` requires time-to-first to
be independent of *candidate count*, and it is not: the 3-member receiver is **inside** the 100 ms
budget (41 ms) and the 3 600-member one is **more than an order of magnitude over** it (1 641 ms),
measured cold in separate files. ⚠ **The `40x` this paragraph used to quote is retired** — it is a
ratio between two figures of this harness, which DR-08's standing rule forbids citing; it survives
only as a **pre-Phase-0 record**, not as evidence. What is load-bearing is that one side of the pair
meets the budget and the other misses it, which no plausible spread turns around. Separate files
matter: the per-file snapshot is memoized, so two receivers in one file would have made the second
look free for reasons unrelated to its size.

**Caveat on the two cold figures.** 746 ms and 1 641 ms are the same receiver in different fixtures
(one library file vs two), so they are not comparable to each other; each is only comparable to its
own pair. Both are cold and both are over budget, which is all either is used for.

**This is COMP-09-08's mechanism.** The gate asserts cold time-to-first against the budget and is red
today by construction, which is the mutation proof the plan asks for — no need to break the code to
show the test can fail, because it fails now.

## 1.10 The re-plan, PROTOTYPED END TO END (DR-21/DR-22, 2026-08-12)

Phase 2 was executed to plan, measured and aborted. This section replaces the readings that abort
refuted with a run. **Every figure and every routing verdict below comes from
`myFixture.completeBasic()` through the real contributor**, on gce-builder, against a prototype that
was built, armed, run over the whole 2 639-test suite, and then reverted (`git diff -- src/` empty).
The abort happened because DR-14 validated `membersOfGlobal` against `resolveGlobal` *directly* and
never through the contributor; nothing below is allowed to repeat that.

### 1.10.1 The site: above the snapshot build, gated by Rule S

The prototype inserts one call at `LuaCompletionContributor.kt:361`, immediately after
`findReceiverExpr` and **before** `LuaTypesSnapshot.forFile`, and returns early when the index arm is
taken. Everything below that line — the `forFile` build, the `Undefined` guard, `crossFileGlobalMembers`,
the emit loop — is left byte-for-byte alone.

### 1.10.2 Tier 1 latency: 491 ms → 7.4 ms, medians of five COLD samples

Five wide receivers (3 600 members each), **each in its own library file**, so no sample rides
another's snapshot build. Same JVM, same fixture, same run; the only difference is whether the hoist
is armed:

```
COMP09-DR22 UNARMED wide cold samples(us)=[415645, 474316, 490995, 560463, 711323] median=490995 p95=711323
COMP09-DR22 ARMED   wide cold samples(us)=[  6761,   6988,   7414,   7603,   8984] median=7414   p95=8984
COMP09-DR22 UNARMED narrow cold samples(us)=[7831, 8641, 8850, 9305, 10325] median=8850 p95=10325
COMP09-DR22 ARMED   narrow cold samples(us)=[4586, 5512, 6214, 6769,  7220] median=6214 p95=7220
```

**491 ms → 7.4 ms** on the quantity NFR-1 names, both medians of five cold samples. This is the
first COMP-09 figure for which "cold" and "median of ≥5" are both true at once: previous cold numbers
were single and labelled unrepeatable, because they re-used one receiver.

The armed gate agrees from a different harness. `MemberEnumerationLatencyGateTest` was run unchanged
inside the full suite with the prototype armed, and its **inverted** assertion 1 went red — which is
the gate saying the budget is met:

```
COMP-09-08 tier 1 (syntactic, 3600 members) cold time-to-first = CompletionTimeline(firstUs=13783, lastUs=31881, count=3600)
COMP-09-08 tier 2 (require-bound) cold time-to-first = CompletionTimeline(firstUs=24936, lastUs=25482, count=1)  [RECORDED, not asserted]
junit.framework.AssertionFailedError: COMP-09-08 is now MET — cold time-to-first for `wx.` was 13 ms
against a 100 ms budget. Flip BUDGET_ENFORCED to true; that is Phase 2's exit criterion
```

**Per DR-08's standing rule no ratio between two of these figures is quotable** — the same wide
receiver reported 13 783 µs in the gate run, 35 416 µs and 49 403 µs in two DR-21 runs and a 7 414 µs
median across five distinct receivers. The claim that survives all four is directional and
budget-relative: **every armed measurement is under 100 ms; every unarmed one is over 400 ms.**

### 1.10.3 Tier 2 collapses as a distinction — §4.12 is re-derived

The abort's third owed item. Tier 2 is the `require`-bound receiver the index cannot see through,
which §4.12 excluded from the budget on the grounds that it "pays today's graph build by
construction". At the hoisted site it does not:

| | tier 2 (`Opaque = require(…)`) cold time-to-first |
| :-- | --: |
| at the aborted site (risks-and-gaps, `forFile` alone) | 143 206 µs |
| hoisted, UNARMED (DR-21, two runs) | 130 080 µs / 120 962 µs |
| hoisted, ARMED (DR-21, two runs) | **60 784 µs / 46 259 µs** |
| hoisted, ARMED (gate run) | **24 936 µs** |

Tier 2 lands **under the 100 ms budget in all three armed runs**, without any tier-2-specific work.
It improves because the receivers *around* it stop dragging the consumer file's snapshot through
`freeGlobalSeed` → `resolveGlobal` → the library's `buildSnapshot`. §4.12's two-tier contract is
therefore **not the distinction that matters** and is withdrawn — see the rewritten §4.12.

### 1.10.4 Rule S holds — ten scenarios, and the whole suite

The abort names this as the thing replanning must not invent implicitly. Rule S is stated in §4.14
and was run against every binding form the grammar has. `today` is the unarmed contributor, `hoisted`
is the armed one, same fixture, same run:

```
COMP09-DR21 shadow localVarShadow:   today=[fromLocal]      hoisted=[fromLocal]      same=true
COMP09-DR21 shadow localFuncShadow:  today=[]               hoisted=[]               same=true
COMP09-DR21 shadow paramShadow:      today=[]               hoisted=[]               same=true
COMP09-DR21 shadow genericForShadow: today=[end]            hoisted=[end]            same=true
COMP09-DR21 shadow numericForShadow: today=[end]            hoisted=[end]            same=true
COMP09-DR21 shadow selfAssignShadow: today=[fromThisFile]   hoisted=[fromThisFile]   same=true
COMP09-DR21 shadow funcDeclShadow:   today=[here]           hoisted=[here]           same=true
COMP09-DR21 shadow unbound:          today=[fromLibrary]    hoisted=[fromLibrary]    same=true
COMP09-DR21 shadow unboundLib:       today=[helper,version] hoisted=[helper,version] same=true
COMP09-DR21 shadow stdlibTable:      today=[concat, insert, move, pack, remove, sort, unpack] hoisted=[same] same=true
COMP09-DR21 require-bound assert:    today=[register, unregister] hoisted=[register, unregister] same=true
```

`localVarShadow` is `LuaGlobalMemberCompletionTest.aLocalShadowsTheCrossFileGlobal`'s exact fixture,
and that test — plus `stdlibGlobalCompletesItsMembers`, `projectGlobalDeclaredInAnotherFileCompletesItsMembers`,
`globalBoundToARequiredModuleCompletesThatModulesMembers`, `aMemberCaretOffersNoCrossFileGlobals` and
the whole of `LuaLibraryMemberCompletionTest` — **passed armed** in the full-suite run below.

### 1.10.5 The full suite armed: exactly two tests move, and both are the declared ones

`test --rerun --no-build-cache` on gce-builder with the prototype armed — **run twice, once per
ordering (§1.10.6), with the same verdict both times**:

```
run 1 (Rule S first):     2639 tests completed, 2 failed, 1 skipped
run 2 (index first):      2644 tests completed, 2 failed, 1 skipped     (+5 DR-22 tests)
MemberEnumerationGoldenTest > testGoldenIsUnchanged FAILED
MemberEnumerationLatencyGateTest > testColdTimeToFirstIsWithinTheBudget FAILED
```

Run 2's gate output, for the record — the ordering does not change the verdict, and tier 2 is again
inside the budget:

```
COMP-09-08 tier 1 (syntactic, 3600 members) cold time-to-first = CompletionTimeline(firstUs=8998, lastUs=15663, count=3600)
COMP-09-08 tier 2 (require-bound) cold time-to-first = CompletionTimeline(firstUs=33212, lastUs=33846, count=1)  [RECORDED, not asserted]
COMP-09-08 narrow cold samples (us) = [2438, 2672, 3297, 3479, 3855]  p50=3297 p95=3855
COMP-09-08 noise floor factor = 2x; wide (3600 members) = 13363us = 4x
junit.framework.AssertionFailedError: COMP-09-08 is now MET — cold time-to-first for `wx.` was 8 ms against a 100 ms budget
```

The second is the inverted gate reporting the budget is met (§1.10.2). The first is the golden, and
its diff is **three receivers on the `completion` door only** — every `global` and `class` door row is
byte-identical, which is COMP-09-06's structural guarantee showing up as a measurement:

**Three golden rows move, and all three are `completion|<member>` rows at the `.` caret.** The golden
has **no colon door at all** — `MemberEnumerationGoldenTest.completionRows` emits only
`completionsFor("$receiverName.<caret>")`, so a `Base:` row is not a thing this file can carry (see
DR-27, and implementation-plan Phase 2's note). The colon finding is real and is recorded in the
separate table below, **not** as a golden diff:

| golden row (`.` caret) | golden today | golden armed | verdict |
| :-- | :-- | :-- | :-- |
| `Shapes\|completion` | `deep, direct, nested, plain` | `direct, nested, plain` | **declared** — BUG-430, §4.4a |
| `Base\|completion` | `Show, inheritedFn` | `Show, inheritedField, inheritedFn, onClose` | **new, declared here** — §4.5a, `@field` on the receiver's own `@class` |
| `Derived\|completion` | `Show, ownFn` | `Show, ownField, ownFn` | **declared** — §4.5a, TC 7c |

Measured at the **colon** caret in the same DR-21 run, outside the golden (E7 / TC 7e is its gate):

| caret | today | armed | verdict |
| :-- | :-- | :-- | :-- |
| `Base:` | `Show, inheritedFn` | `Show, inheritedFn, onClose` | **new, declared here** — §4.5a, and see §4.14's kind note |

The `Base` findings are a finding, not a restatement: §4.5a declared the superset for `Derived` only.
`---@field onClose fun(): nil` indexes as `Kind.FUNCTION` (§4.3's `startsWith("fun(")` rule), so it
survives the colon filter and appears at `Base:` too.

Everything else in the golden is unchanged, including all eleven `global` rows, all eleven `class`
rows, and eight of eleven `completion` rows:

```
COMP09-DR21 dot wx:       today=[wxFileExists, wxID_ANY] hoisted=[wxFileExists, wxID_ANY] same=true
COMP09-DR21 dot Config:   today=[host, port]             hoisted=[host, port]             same=true
COMP09-DR21 dot M:        today=[DEBUG, VERSION, f]      hoisted=[DEBUG, VERSION, f]      same=true
COMP09-DR21 dot Busted:   today=[unregister]             hoisted=[unregister]             same=true
COMP09-DR21 dot OM:       today=[unregister]             hoisted=[unregister]             same=true
COMP09-DR21 dot luassert: today=[]                       hoisted=[]                       same=true
COMP09-DR21 dot wxFrame:  today=[]                       hoisted=[]                       same=true
COMP09-DR21 dot AllColon: today=[]                       hoisted=[]                       same=true
```

`Busted` and `OM` are the opacity sentinel working end to end: both are `require`-bound, both are
non-authoritative, both fall through to the graph and answer exactly as today.

### 1.10.6 The routing question costs O(file) — so it is asked SECOND

Rule S is a PSI walk; its cost tracks file size, and it would run on **every** member completion:

```
COMP09-DR21 ruleS small  lines=4:    hit(us)=[17,17,25,35,107]              median=25     miss=[16,17,18,24,54] median=18
COMP09-DR21 ruleS large  lines=4002: hit(us)=[14196,14249,16005,16443,16853] median=16005 miss=[8791,9381,10016,10634,12537] median=10016
```

10–16 ms per keystroke on a 4 000-line file is not acceptable overhead on a path whose warm cost is
~1 ms today. The fix is **ordering, and it is free**: ask the index first — a key lookup — and run
Rule S only when the index could actually answer. Measured on a 4 002-line consumer whose receiver
`t` is a file-local table, five completions each way:

```
COMP09-DR22 ordering indexFirst=true  … ruleS.us per completion = [-1, -1, -1, -1, -1]   (-1 = not run)
COMP09-DR22 ordering indexFirst=false … ruleS.us per completion = [21501, 14963, 10875, 13368, 12884]
COMP09-P1 receiver=t binds=false ruleSRan=false ruleS.us=-1 index.us=425 entries=0 files=0 found=false
```

Routing is identical under both orderings (DR-21's whole table was re-taken under `indexFirst=true`
and is byte-identical), so this is a pure cost decision with a measured 11–21 ms per-keystroke saving
on large files. §4.13 specifies the ordering.

### 1.10.7 The cost split at the site, and the work bound

Five wide receivers, cost attributed inside the contributor:

```
COMP09-DR22 split Wide0: ruleS.us=26 index.us=3576 entries=3600 files=1
COMP09-DR22 split Wide1: ruleS.us=23 index.us=2126 entries=3600 files=1
COMP09-DR22 split Wide2: ruleS.us=12 index.us=1010 entries=3600 files=1
COMP09-DR22 split Wide3: ruleS.us=14 index.us=1085 entries=3600 files=1
COMP09-DR22 split Wide4: ruleS.us=17 index.us=1691 entries=3600 files=1
```

The index read (1.0–3.6 ms for 3 600 entries) is the only member-linear term left, against a ~6 ms
fixed floor that is the platform's own completion machinery. And the work bound holds at the new
site, which is the count COMP-09-09 asks for:

```
COMP09-DR22 workbound before: entries=3600 files=1
COMP09-DR22 workbound after:  entries=3600 files=1     (+ an unrelated 4 000-member library)
```

### 1.10.8 The ambient-seed site, MEASURED (DR-26, 2026-08-12) — and the target it is live on

DR-21/DR-22 ran entirely on the **STANDARD 5.4** target, so neither of them touched
`LuaTypesVisitor.seedAmbientGlobals` (`:1360`) — the one `LuaScope.declare` site §4.14 deliberately
leaves outside Rule S. That site reads only files named `global.lua` in the active target's bundled
root (`LuaTypesVisitor.kt:1345`, `.filter { it.name == "global.lua" }`), and **there is no
`global.lua` under any `runtime/standard/lua-5.x` directory** — only under `runtime/redis/redis-{5,6,7}`
and `runtime/valkey/valkey-{7.2,8}`:

```
$ find src/main/resources/runtime -name global.lua
src/main/resources/runtime/valkey/valkey-7.2/global.lua
src/main/resources/runtime/valkey/valkey-8/global.lua
src/main/resources/runtime/redis/redis-7/global.lua
src/main/resources/runtime/redis/redis-5/global.lua
src/main/resources/runtime/redis/redis-6/global.lua
```

So the excluded site is dead on the default target (`Target.getLibraryRootPath():66` maps Standard
Lua 5.4 to `runtime/standard/lua-5.4`) and live only on Redis/Valkey, where `global.lua` declares
`KEYS` and `ARGV` bare. **It was measured there rather than argued about.** A probe set a real
`Target(REDIS, 7+)` / `Target(VALKEY, 8)` via `LuaProjectSettings.setTargetAndNotify` +
`PlatformLibraryIndex.reload()`, ran `completeBasic()` at both doors, and asked the index the same
question the §4.13 site asks (gce-builder, 2026-08-12):

```
=== PROBE TARGET redis-7 ===
PROBE KEYS  | today.dot=[] | today.colon=[] | globalAssignmentCandidates=[global.lua] | found=true authoritative=true indexMembers=[]
PROBE ARGV  | today.dot=[] | today.colon=[] | globalAssignmentCandidates=[global.lua] | found=true authoritative=true indexMembers=[]
=== PROBE TARGET valkey-8 ===
PROBE KEYS  | today.dot=[] | today.colon=[] | globalAssignmentCandidates=[global.lua] | found=true authoritative=true indexMembers=[]
PROBE ARGV  | today.dot=[] | today.colon=[] | globalAssignmentCandidates=[global.lua] | found=true authoritative=true indexMembers=[]
=== PROBE TARGET standard lua-5.4 (no global.lua exists here) ===
PROBE table | today.dot=[concat, insert, move, pack, remove, sort, unpack] | globalAssignmentCandidates=[table.lua] | found=true authoritative=true indexMembers=[concat, insert, remove, sort, unpack, pack, move]
```

**Three results, and each closes something that was previously asserted from reading:**

1. **The excluded site is behaviour-neutral on the only target it fires on.** `KEYS`/`ARGV` are bound
   `KEYS = {}` with a `---@type string[]`; an empty table literal contributes no members to §4.3's
   source 4 and is not opaque under source 5, so the index answers `found=true, authoritative=true,
   members=[]` — **byte-identical to the `[]` completion offers today at both doors**. The omission is
   therefore measured, not assumed, and TC 10h pins it on a real Redis target.
2. **`table` is not bound by `seedAmbientGlobals` at all** — its `LuaGlobalAssignmentIndex` candidate
   is `table.lua`, not `global.lua`, which is what §4.5's `freeGlobalSeed` path already said. Earlier
   revisions of §4.14 and of TC 10g attributed it to ambient seeding; that attribution is corrected
   below and in requirements.md.
3. **A third instance of §4.5a's `@field` superset, on a bundled stub — `redis.lua`.** The DR-26 run
   *stated* this without pasting a `redis` line, so the claim rested on a summary rather than on an
   artifact. **§1.10.8a is that measurement, taken properly**, and it also settles how many receivers
   are affected — which DR-26 never asked.

### 1.10.8a The `@field` superset on the bundled stubs, MEASURED across every target it exists on (DR-28, 2026-08-12)

DR-26 asserted "today's `redis.` offers the thirteen functions only, while the index arm adds the ten
`@field` constants" and pasted no `redis` line, so **the sentence had no evidence in this document**.
It also scoped the finding to `runtime/redis/redis-7/redis.lua` alone. The same shape — `---@class X`
+ ten `---@field` constants + a bare `X = {}` + `function X.*`, **with no constant assignments** —
exists in **five** bundled stubs, and a sixth file (`server.lua`, on both Valkey targets) has the same
ten `@field`s but *does* carry `server.LOG_DEBUG = 0`-style assignments, so whether it moves is a
different question. Grepped:

```
$ for f in $(find src/main/resources/runtime -name redis.lua -o -name server.lua | sort); do
    printf '%-56s @class=%s @field=%s bare=%s constAssign=%s functions=%s\n' $f \
      $(grep -c '^---@class' $f) $(grep -c '^---@field' $f) \
      $(grep -cE '^(redis|server) = \{\}' $f) $(grep -cE '^(redis|server)\.[A-Z_]+ *=' $f) \
      $(grep -cE '^function (redis|server)\.' $f); done
src/main/resources/runtime/redis/redis-5/redis.lua        @class=1 @field=10 bare=1 constAssign=0 functions=10
src/main/resources/runtime/redis/redis-6/redis.lua        @class=1 @field=10 bare=1 constAssign=0 functions=11
src/main/resources/runtime/redis/redis-7/redis.lua        @class=1 @field=10 bare=1 constAssign=0 functions=13
src/main/resources/runtime/valkey/valkey-7.2/redis.lua    @class=1 @field=10 bare=1 constAssign=0 functions=12
src/main/resources/runtime/valkey/valkey-7.2/server.lua   @class=1 @field=10 bare=1 constAssign=10 functions=11
src/main/resources/runtime/valkey/valkey-8/redis.lua      @class=1 @field=10 bare=1 constAssign=0 functions=12
src/main/resources/runtime/valkey/valkey-8/server.lua     @class=1 @field=10 bare=1 constAssign=10 functions=11
```

A throwaway `CompNineDr28ProbeTest` (`IndexedBasePlatformTestCase`, real
`setTargetAndNotify(Target(platform, PlatformVersionRegistry.findVersion(platform, label)))` +
`PlatformLibraryIndex.reload()`) then ran `completeBasic()` at **both** doors and asked
`LuaReceiverMemberIndex.globalMembership` the same question §4.13's site asks, on **all five
Redis/Valkey targets**. `indexColon` is the index arm's answer after §4.13's syntactic `isColon`
filter (`kind == FUNCTION`). Run on gce-builder, 2026-08-12,
`test --tests *CompNineDr28Probe* --rerun --no-build-cache`; the probe was **reverted** and this
pasted output is the evidence:

```
=== PROBE TARGET Redis 5 ===
PROBE redis | today.dot=[breakpoint, call, debug, error_reply, log, pcall, replicate_commands, set_repl, sha1hex, status_reply]
PROBE redis | today.colon=[breakpoint, call, debug, error_reply, log, pcall, replicate_commands, set_repl, sha1hex, status_reply]
PROBE redis | candidates=[redis.lua] found=true authoritative=true
PROBE redis | indexDot=[LOG_DEBUG, LOG_NOTICE, LOG_VERBOSE, LOG_WARNING, REDIS_VERSION, REDIS_VERSION_NUM, REPL_ALL, REPL_AOF, REPL_NONE, REPL_REPLICA, breakpoint, call, debug, error_reply, log, pcall, replicate_commands, set_repl, sha1hex, status_reply]
PROBE redis | indexColon=[breakpoint, call, debug, error_reply, log, pcall, replicate_commands, set_repl, sha1hex, status_reply]
=== PROBE TARGET Redis 6 ===
PROBE redis | today.dot=[breakpoint, call, debug, error_reply, log, pcall, replicate_commands, set_repl, setresp, sha1hex, status_reply]
PROBE redis | today.colon=[breakpoint, call, debug, error_reply, log, pcall, replicate_commands, set_repl, setresp, sha1hex, status_reply]
PROBE redis | candidates=[redis.lua] found=true authoritative=true
PROBE redis | indexDot=[LOG_DEBUG, LOG_NOTICE, LOG_VERBOSE, LOG_WARNING, REDIS_VERSION, REDIS_VERSION_NUM, REPL_ALL, REPL_AOF, REPL_NONE, REPL_REPLICA, breakpoint, call, debug, error_reply, log, pcall, replicate_commands, set_repl, setresp, sha1hex, status_reply]
PROBE redis | indexColon=[breakpoint, call, debug, error_reply, log, pcall, replicate_commands, set_repl, setresp, sha1hex, status_reply]
=== PROBE TARGET Redis 7+ ===
PROBE redis | today.dot=[acl_check_cmd, breakpoint, call, debug, error_reply, log, pcall, register_function, replicate_commands, set_repl, setresp, sha1hex, status_reply]
PROBE redis | today.colon=[acl_check_cmd, breakpoint, call, debug, error_reply, log, pcall, register_function, replicate_commands, set_repl, setresp, sha1hex, status_reply]
PROBE redis | candidates=[redis.lua] found=true authoritative=true
PROBE redis | indexDot=[LOG_DEBUG, LOG_NOTICE, LOG_VERBOSE, LOG_WARNING, REDIS_VERSION, REDIS_VERSION_NUM, REPL_ALL, REPL_AOF, REPL_NONE, REPL_REPLICA, acl_check_cmd, breakpoint, call, debug, error_reply, log, pcall, register_function, replicate_commands, set_repl, setresp, sha1hex, status_reply]
PROBE redis | indexColon=[acl_check_cmd, breakpoint, call, debug, error_reply, log, pcall, register_function, replicate_commands, set_repl, setresp, sha1hex, status_reply]
=== PROBE TARGET Valkey 7.2 ===
PROBE redis | today.dot=[acl_check_cmd, breakpoint, call, debug, error_reply, log, pcall, replicate_commands, set_repl, setresp, sha1hex, status_reply]
PROBE redis | today.colon=[acl_check_cmd, breakpoint, call, debug, error_reply, log, pcall, replicate_commands, set_repl, setresp, sha1hex, status_reply]
PROBE redis | candidates=[redis.lua] found=true authoritative=true
PROBE redis | indexDot=[LOG_DEBUG, LOG_NOTICE, LOG_VERBOSE, LOG_WARNING, REDIS_VERSION, REDIS_VERSION_NUM, REPL_ALL, REPL_AOF, REPL_NONE, REPL_REPLICA, acl_check_cmd, breakpoint, call, debug, error_reply, log, pcall, replicate_commands, set_repl, setresp, sha1hex, status_reply]
PROBE redis | indexColon=[acl_check_cmd, breakpoint, call, debug, error_reply, log, pcall, replicate_commands, set_repl, setresp, sha1hex, status_reply]
PROBE server | today.dot=[LOG_DEBUG, LOG_NOTICE, LOG_VERBOSE, LOG_WARNING, REDIS_VERSION, REDIS_VERSION_NUM, REPL_ALL, REPL_AOF, REPL_NONE, REPL_REPLICA, acl_check_cmd, breakpoint, call, debug, error_reply, log, pcall, set_repl, setresp, sha1hex, status_reply]
PROBE server | today.colon=[acl_check_cmd, breakpoint, call, debug, error_reply, log, pcall, set_repl, setresp, sha1hex, status_reply]
PROBE server | candidates=[server.lua] found=true authoritative=true
PROBE server | indexDot=[LOG_DEBUG, LOG_NOTICE, LOG_VERBOSE, LOG_WARNING, REDIS_VERSION, REDIS_VERSION_NUM, REPL_ALL, REPL_AOF, REPL_NONE, REPL_REPLICA, acl_check_cmd, breakpoint, call, debug, error_reply, log, pcall, set_repl, setresp, sha1hex, status_reply]
PROBE server | indexColon=[acl_check_cmd, breakpoint, call, debug, error_reply, log, pcall, set_repl, setresp, sha1hex, status_reply]
=== PROBE TARGET Valkey 8 ===
PROBE redis | today.dot=[acl_check_cmd, breakpoint, call, debug, error_reply, log, pcall, replicate_commands, set_repl, setresp, sha1hex, status_reply]
PROBE redis | today.colon=[acl_check_cmd, breakpoint, call, debug, error_reply, log, pcall, replicate_commands, set_repl, setresp, sha1hex, status_reply]
PROBE redis | candidates=[redis.lua] found=true authoritative=true
PROBE redis | indexDot=[LOG_DEBUG, LOG_NOTICE, LOG_VERBOSE, LOG_WARNING, REDIS_VERSION, REDIS_VERSION_NUM, REPL_ALL, REPL_AOF, REPL_NONE, REPL_REPLICA, acl_check_cmd, breakpoint, call, debug, error_reply, log, pcall, replicate_commands, set_repl, setresp, sha1hex, status_reply]
PROBE redis | indexColon=[acl_check_cmd, breakpoint, call, debug, error_reply, log, pcall, replicate_commands, set_repl, setresp, sha1hex, status_reply]
PROBE server | today.dot=[LOG_DEBUG, LOG_NOTICE, LOG_VERBOSE, LOG_WARNING, REDIS_VERSION, REDIS_VERSION_NUM, REPL_ALL, REPL_AOF, REPL_NONE, REPL_REPLICA, acl_check_cmd, breakpoint, call, debug, error_reply, log, pcall, set_repl, setresp, sha1hex, status_reply]
PROBE server | today.colon=[acl_check_cmd, breakpoint, call, debug, error_reply, log, pcall, set_repl, setresp, sha1hex, status_reply]
PROBE server | candidates=[server.lua] found=true authoritative=true
PROBE server | indexDot=[LOG_DEBUG, LOG_NOTICE, LOG_VERBOSE, LOG_WARNING, REDIS_VERSION, REDIS_VERSION_NUM, REPL_ALL, REPL_AOF, REPL_NONE, REPL_REPLICA, acl_check_cmd, breakpoint, call, debug, error_reply, log, pcall, set_repl, setresp, sha1hex, status_reply]
PROBE server | indexColon=[acl_check_cmd, breakpoint, call, debug, error_reply, log, pcall, set_repl, setresp, sha1hex, status_reply]
```

**Five findings, and two of them change what TC 7f may say.**

| # | finding |
| :-- | :-- |
| a | **The `@field` superset is confirmed and is not redis-7's alone.** `redis.` gains the same ten constants on **all five** targets — Redis 5, Redis 6, Redis 7+, Valkey 7.2, Valkey 8. DR-26's scoping to one file was an artifact of only probing one target. |
| b | **The function count is a per-version property, so "the thirteen functions" is a Redis-7 statement.** Measured: **10** on Redis 5, **11** on Redis 6, **13** on Redis 7+, **12** on both Valkey targets. A TC written as "thirteen functions" against any other target is wrong by construction, which is why TC 7f now names its target and gives the set. |
| c | **`redis:` does not move on any target.** `today.colon == indexColon` in all five blocks: every member of `redis.lua` other than the ten constants is a function, and the ten index as `Kind.FIELD` (their `@field` type text is `number`/`string`, not `fun(`), so §4.13's syntactic filter drops exactly the ten the dot door gains. This is the mirror image of `Base:` — where `---@field onClose fun(): nil` *does* start `fun(` and *does* survive. |
| d | **`server` does NOT move, and this had to be measured rather than reasoned.** Its ten `server.LOG_DEBUG = 0`-style assignments make the constants visible to today's global door **and** to §4.3 source 2, so `today.dot == indexDot` exactly (21 members) and `today.colon == indexColon` exactly (11). The bare-`@field` shape is what causes the divergence; the same `@field`s beside real assignments cause none. `server` therefore needs no expectation and gets none. |
| e | **`---@class server : redis` does not inherit on the completion door**, either today or armed — `server.` offers none of `redis`'s `replicate_commands`/`register_function`. That is §4.5a's B5 paragraph holding on a bundled stub, and it is *why* d is a clean equality rather than a near-one. |

**Blast radius, stated:** five receivers move (`redis` on redis-5, redis-6, redis-7, valkey-7.2,
valkey-8), each gaining the same ten constants at the `.` caret only; one receiver (`server`, on both
Valkey targets) was in the candidate set and is measured **unchanged**. TC 7f gates Redis 7+ as the
representative and TC 7f-bis gates Valkey 8 — the two targets whose function sets differ — plus a
`server` non-movement assertion, so the family is covered without one test per version.

The probe was a throwaway; it was reverted, and the numbers above are its pasted output. What ships
from it is TC 10h and TC 7f.

## 2. Consequences for the plan

Corrections forced by Step 9 review, beyond §1.7/§1.8:

| Claim | Status |
| :-- | :-- |
| "per-keystroke, 76 % repaid" (§1.2) | **UNRELIABLE, and its mechanism is now OBSOLETE as well.** The harness verdict flipped on re-run, so the figure was withdrawn. The *mechanism* was then stated here as "sound by reading — the per-file snapshot's deps include project-wide `PsiModificationTracker.MODIFICATION_COUNT`". **TYPE-11 falsified that in the present tense.** `LuaTypes.forFile` no longer passes `MODIFICATION_COUNT` unconditionally: it passes `churnDependencyFor(psiFile, sourceFrame)` (`LuaTypes.kt:334-343`), which *decides* — `LuaLibraryProvenance.getInstance(project).generationTracker()` when `isPinnable(psiFile, sourceFrame)` (`LuaTypes.kt:308`) holds, `MODIFICATION_COUNT` otherwise — and a pinned library snapshot therefore does **not** depend on project-wide churn any more. §1.2's numbers stand as a record of the pre-TYPE-11 code; the sentence describing why they came out that way must be read in the past tense, and `risks-and-gaps.md`'s Technical Debt entry says the same. Nothing in this plan may cite the repaid fraction or the mechanism as current |
| "`getAllKeys` is cheap — 44 ms / 25 335 keys" (§1.5) | **WITHDRAWN as stated.** That was `FileBasedIndex.getAllKeys(LuaMemberFieldIndex)`, a different subsystem from the `StubIndex.getAllKeys(LuaGlobalDeclarationIndex)` scans it was used to exonerate; and the printed number was the *filtered* match count, not the key total, polluted by cross-test index accumulation. Candidate C (43 ms for a 17 234-key scan + 500 `getElements`) is the figure that actually supports the conclusion |
| "bound stub loads, not key visits" (COMP-09-09) | **Reverted.** Candidate C shows 500 `getElements` inside 43 ms. The bound should be on *entries traversed*, as `non-functional.md` states |
| "one index change covers both doors" (§1.6) | **Not yet supported** — see §4's rewrite. `LuaGlobalDeclarationIndex` is a `StringStubIndexExtension` with **no value field**, so function-declaration names cannot come from "an index value" |

| Was | Now |
| :-- | :-- |
| "Replace the two `getAllKeys` scans" is the first increment | Those scans are inside a 10 ms region. Real, but not the headline. Demoted to a work-bound fix (COMP-09-09), not a latency one |
| Fix direction is index-backed member enumeration | Fix direction is **avoiding the declaring file's whole-graph build** to answer one symbol's type. Enumeration is already fast |
| Swapping the scan is a strict simplification | It is a correctness regression until `indexStub` also sinks `substringBefore(':')` — a **stub index format change**: version bump, full reindex |
| Per-keystroke concern justified by cancel/restart | Justified by measured cache invalidation. ⚠ The "76 % repaid" figure once here is **withdrawn — and so is the replacement**: §1.2 remeasured it at 22 %, the Phase 0 re-run at 48 %, so the repaid fraction is not a quotable quantity of this harness (DR-08) |

## 3. Questions this design could not answer when it was written — all now closed

Each needed a measurement that had not been taken, and inventing answers here is what produced the
errors §1.1 and §1.3 record. **None remains open**; each is closed by a named section, and anything
still genuinely undecided lives in `risks-and-gaps.md` as a tracked task, per the DoD.

1. ~~Can a global's type be answered without `forFile`?~~ **Answered, §1.5.** Not the type — but the
   member *names* can, from an index value, which is what completion needs.
2. ~~Is the `@class` path dominated by `forFile` or by the `getElements`-per-key loop?~~
   **Answered, §1.6.** Neither — it never calls `forFile`, and its cold cost is dominated by the
   declaring file's AST parse. Different bottleneck, same remedy.
3. ~~Does narrowing invalidation help more than indexing?~~ **Decided, §1.2a (DR-07).** No — it is a
   complement. Narrowing takes the post-edit case from ~200 ms to ~1 ms but cannot touch cold, which
   is hundreds of milliseconds (392 / 463 / 1 152 ms across three runs), is every session's first
   completion, and is over budget under all three — nor the member-count scaling. The argument is
   **directional**: §1.8's spread means no ratio between two of these figures is quotable. Index now,
   narrow separately.
4. ~~Where does incremental yield apply?~~ **Decided, §1.7 — nowhere**, and confirmed by measurement
   in §1.9: the gap between the first element and the last is 31 ms of 777 ms. COMP-09-04 withdrawn;
   COMP-09-04b withdrawn in turn (§1.7).

## 4. Architecture — rewritten from a measured prototype (DR-09)

Two earlier revisions of this section were written from reading the code and each failed a Step 9
review (§4.9, retained below as the record). This one describes a prototype that exists, was
registered, indexed a real library root, and was measured: `LuaReceiverMemberIndex` plus
`CompNineDr09Test` / `CompNineDr09bTest`. Every claim below cites a printed figure, and the two
places the prototype disagreed with today's engine are stated as findings rather than smoothed over.

### 4.0 What DR-09 measured

*(`membersOf` below is DR-09's prototype name; Phase 1 retires it for `membersOfGlobal` /
`membersIn` — §4.5. The figures are unaffected: it is the same key lookup.)*

Fixture for timing: one 238 KiB `---@meta` library — 3 400 `---@type number` constants, 200 dot
functions, 300 classes × 8 colon methods — the BUG-429 shape.

| measurement | result |
| :-- | :-- |
| `membersOf("wx")`, 3 600 members, median of 5 | **2 ms** (samples 13/2/2/3/2 — the 13 is the cold first call) |
| `membersOf("wxG7")`, 8 members, median of 5 | **0 ms** |
| `resolveGlobal("wx")`, same fixture, same run, cold | **13 655 ms** |
| externalizer round-trip, 4 members incl. non-ASCII | **exact**, 55 bytes; empty list 4 bytes |
| membership vs today's golden, 4 receivers | 3 exact; 1 differs by BUG-430 (§4.4a) |

The narrow/wide pair is the important one: 8 members costs 0 ms on the same index in the same
project as 3 600 members costing 2 ms. That is COMP-09-09's work bound demonstrated, not asserted —
cost tracks members of the receiver, not index size.

### 4.1 The constraint that shapes it: two consumers, different needs

`addMethodsOf` produces `LuaTypeMember(name, type, visibility, description, sourceElement)`
(`LuaType.kt:21-27`), and `sourceElement` is **load-bearing** — `LuaTypeManagerImpl.materializeClass`
(`LuaTypeManagerImpl.kt:341`, comment at `:357-361`) records
that `LuaOverrideLineMarkerProvider` uses it as a gutter navigation target, with the warning that
"the parity harness compares names and types only, so it would not catch that". An index of names
cannot produce a `LuaTypeMember`.

So enumeration splits by consumer, and only one of them can be served without PSI. **The completion
row names a *need*, not a change site**: the function `crossFileGlobalMembers` is left byte-for-byte
unchanged by this feature (§4.5's withdrawn premise, §4.13's rule 3), and it appears here only
because it is where that need is visible in today's code.

| consumer | needs | can an index answer it? |
| :-- | :-- | :-- |
| **completion** (the member provider — §4.13's hoisted site, **not** `crossFileGlobalMembers`) | member name; whether it is a function (the `isColon` filter, `LuaCompletionContributor.kt:384`); an icon (`LuaMemberLookup.kt:19-23`) | **yes** — name + kind, no PSI |
| **type materialization** (`addMethodsOf`) | full `LuaTypeMember` incl. `sourceElement` | **no** — still needs the `LuaFuncDecl` |

The win differs accordingly:

- completion skips the graph build entirely — measured 2 ms against 13 655 ms on one fixture;
- materialization keeps `getElements` per member but stops **scanning every key to find them**.

### 4.2 `net.internetisalie.lunar.lang.indexing.LuaReceiverMemberIndex`

Exists, registered, measured. `FileBasedIndexExtension<String, List<LuaReceiverMember>>`, key =
receiver name, value = the members that receiver declares **in one file**.

```kotlin
data class LuaReceiverMember(val name: String, val kind: Kind, val separator: Separator) {
    enum class Kind { FUNCTION, FIELD }
    enum class Separator { DOT, COLON }
}
```

A `FileBasedIndex` value is per (key, file), so key `wx` carries one list per declaring file and
`membersOf` combines them. That is what `LuaMemberFieldIndex`'s `<String, String>` shape could not
do — the **value type** is the fix, not the key. Collection-valued precedent in this repo is
`LuaFileBindingsIndex` (`:34-77`), whose externalizer this one is modelled on.

**Wire format** (§4.9's "no wire format specified" defect, now closed and round-trip-proved):

```
writeInt(count)
per member: writeUTF(name), writeByte(kind.ordinal), writeByte(separator.ordinal)
```

Ordinals are safe **only** because `getVersion()` gates the format; reordering either enum is a
version bump. Measured: 55 bytes for 4 members including a non-ASCII name, 4 bytes for an empty
list, restored `EXACT`.

### 4.3 Indexer algorithm — FIVE sources, as implemented

> **This heading said "three sources" through Phase 1 and was stale.** Sources 4 and 5 landed with
> DR-19 (§4.5c remedies 2 and 3) and were documented only there, so every cross-reference that says
> "§4.3's source 4" / "source 5" (§1.10.8, §4.14, requirements TC 10h) pointed at a section that
> listed three. All five are below; nothing about the code changed, only what this section records.

`LuaReceiverMemberIndex.Indexer.map` calls one helper per source
(`LuaReceiverMemberIndex.kt:204-208`) and merges their output into one `MemberSink` per receiver:

| # | source | helper | line |
| :-- | :-- | :-- | --: |
| 1 | `function R.m()` / `function R:m()` | `indexFunctionDeclarations` | `:216` |
| 2 | `R.f = value`, at any depth | `indexMemberAssignments` | `:237` |
| 3 | `---@field` on a `---@class R` comment | `indexClassFields` | `:293` |
| 4 | `R = { a = 1 }` — table-literal fields | `indexTableLiteralFields` | `:253` |
| 5 | `R = <non-table-constructor>` — the opacity sentinel | `indexOpaqueBindings` | `:275` |

*(The call order in `map` is 1, 2, 4, 5, 3 — the numbering here is COMP-09-03's, not the call
order's, and nothing depends on the order because each helper only appends.)*

1. **`function R.m()` / `function R:m()`** — `node.findChildByType(FUNC_NAME)?.text`, the same source
   `LuaFuncStubElementType.createStub` (`LuaFuncStubElementType.kt:24`) uses, so the two agree by
   construction. `Kind.FUNCTION`, separator from the character found.
2. **`R.f = value`, at any depth** — a member assignment inside a function still declares a member.
   Deliberately unlike `LuaGlobalAssignmentIndex`, which is top-level-only because a nested *bare*
   assignment may target an enclosing local. Rejects `R[i]` (keyed suffix), `f().x` (call suffix)
   and `R.a.b` (more than one suffix), matching `LuaImplicitFields.singleFieldSuffixName`.
3. **`---@field` on a `---@class R` comment** — via `LuaCatsDeclarations.fieldMembers`. The receiver
   key is the tag's own `argType.text.trim()` (`LuaReceiverMemberIndex.kt:300`), so a **bare**
   `---@class R` + `---@field` block with no host declaration still indexes; it is the *global*
   candidacy (§4.5's `LuaGlobalAssignmentIndex` question) that needs the bare `R = …`, not this
   source. `Kind` comes from the declared type text starting `fun(` (`:304`); `---@field onClose
   fun(self: wxFrame): nil` measured as `FUNCTION/DOT`. Separator is always `DOT`, so a
   function-shaped `@field` reaches the `:` caret through `Kind`, never through separator — which is
   §1.10.5's `Base:` row and TC 7e.
4. **`R = { a = 1 }`** — the table-literal fields of a **bare** binding (§4.5c remedy 2, DR-19).
   Without it the index is blind to `Config = { host = …, port = … }` and, worse, *partially* blind
   to `M = { VERSION = "1" }` + `function M.f()`, where source 1 makes the index non-empty so an
   "empty means fall back" rule never fires. `Kind` from the field's own RHS by the source-2 rule.
5. **`R = <expr>` where `<expr>` is not a table constructor, at any depth** — emits the sentinel
   `LuaReceiverMember.OPAQUE_BINDING` (`LuaReceiverMemberIndex.kt:106`) instead of a member (§4.5c
   remedy 3, DR-19/DR-19c). Any depth is deliberate: a missed sentinel marks a receiver
   authoritative when it is not. The sentinel is filtered out of both entry points' results
   (`membersIn` at `:410`, `membershipOver` at `:488`) so it can never be offered.

`Kind` for source 2 (§4.9 D3): `R.f = function() end` is `FUNCTION` — the RHS is syntactically a
`LuaFuncDef`. `R.f = someOtherFn` cannot be classified without resolution and is recorded `FIELD`.
Measured on the DR-09b fixture: `assignedFn=FUNCTION/DOT`, `aliasedFn=FIELD/DOT`. **This is a real
residual gap, not a solved one** — it is bounded to indirectly-assigned functions, and Phase 2's
expectation test E4 pins it so the cost is visible rather than assumed away.

### 4.4 Membership vs today — measured, and the two divergences

`CompNineDr09Test.testDr09bMembershipVersusGolden` compares `membersOf` against
`materialize(resolve(...)).getMembers().keys` per receiver:

| receiver | golden | indexed | verdict |
| :-- | :-- | :-- | :-- |
| `wx` (globals, constants, dot functions) | 4 | 4 | **exact** |
| `wxFrame` (colon methods + `@field`) | 5 | 5 | **exact** |
| `AllColon` (every member colon-declared) | 2 | 2 | **exact** — the shape DR-06 showed has *no* receiver key today |
| `Shapes` (nested + assigned + keyed) | 5 | 4 | differs by `deep`, which is **BUG-430** (§4.4a) |

`AllColon` matching is the load-bearing result: it is the case COMP-09-01's original "strict
simplification" would have silently emptied (§1.3), and the new index gets it right because the
receiver key is derived at index time rather than inherited from the dot-only stub sink.

### 4.4a The `deep` divergence is a defect in the engine, not the index — BUG-430

The single mismatch was traced (`CompNineDr09bTest`) and it is not the prototype's:

```
Shapes.nested = {} ; Shapes.nested.deep = 1 ; Shapes.nested.alsoDeep = "s"

resolveGlobal("Shapes")  members = [alsoDeep, deep, direct, nested]   <- grandchildren hoisted
                         members["nested"].localMembers = {}          <- and the real parent is empty
resolveType("Shapes")    members = [direct, nested]                   <- correct
```

So the two doors disagree on the same receiver in the same file, and the global door is wrong twice
over: every nested member is offered at the one path where it does not exist (`Shapes.deep`) and
withheld from the one where it does (`Shapes.nested.deep`). `isExact=true` on the empty `nested` node
compounds it — the table is asserted complete while being demonstrably not. The same shape with no
`---@class` at all behaves identically (`Plain.mid.leaf` → `[leaf, mid]`), so this is the global
door's rule, not an interaction with the annotation.

Neither `memberNameOf` nor `LuaImplicitFields.singleFieldSuffixName` produces this — both reject
`base.x.y` explicitly. The flattening is `LuaTypesVisitor`/`LuaTypesSnapshot`'s member-write walk
recording the last suffix against the root receiver instead of descending. That is why two rounds of
reading missed it: both readings were correct about the code they read. Filed as **BUG-430**.

**This changes what COMP-09-07 can mean.** "Behaviour-preserving" is not well-defined while two
goldens exist for one receiver and one of them is a bug. **Each consumer preserves the door it
actually serves** — completion the global door (§4.5), materialization the `@class` door (§4.6) —
and neither reproduces the flattening.

**Per-door golden, measured (DR-14).** An earlier version of this section claimed the prototype
"matches the `@class` door exactly on all four receivers". That was never measured: the DR-09b
harness took `resolveGlobal(r) ?: resolveType(r)`, the exact collapse §1.4 forbids, so for `wx` and
`Shapes` it compared against the *global* door while the text said `@class`. Step 9 caught it. Redone
per door, separately:

| receiver | global door | `@class` door | |
| :-- | :-- | :-- | :-- |
| `wx` | `[wxFileExists, wxID_ANY]` | `[wxFileExists, wxID_ANY]` | agree |
| `wxFrame` | **unresolvable** | `[Show, staticCount, title]` | only one door answers |
| `AllColon` | **unresolvable** | `[alpha]` | only one door answers |
| `Shapes` | `[deep, direct, nested, plain]` | `[direct, nested, plain]` | **disagree — `deep`** |

Two receivers resolve through only one door, which is why a `?:` golden is not merely imprecise but
silently door-dependent. COMP-09-07 requires every golden entry to name its door; the harness that
produced the previous table did not, and the claim it supported has been replaced rather than
re-worded.

**Phase 0 checked this in, and it found one more thing (2026-08-09).**
`src/test/resources/comp09/member-enumeration.golden` records eleven receivers across all eight
binding shapes, per door, plus what `R.<caret>` offers and the `sourceElement` the override marker
navigates to. Three observations that were not in the tables above:

- **The doors disagree about member TYPES, not only membership.** `wx.wxID_ANY` is `nil | number`
  through the global door and `nil` through the `@class` door, from one `---@type number` annotation.
  §1.4 recorded that the two doors give the *receiver* different types; that they give the same
  *member* different types is new, and it is why the golden records `member:type` rather than a name
  list. COMP-09-06's "no baseline may move" is asserted against the door each consumer serves.
- **`OM.extra` is absent from the global door today.** `OM = require("luassert")` +
  `function OM.extra()` enumerates `[unregister]` — the syntactically declared member is lost.
  Consistent with DR-19's row, now pinned rather than argued, and it is the residual §4.5c's
  sentinel deliberately preserves rather than fixes.
- **An `@field` function signature materializes as `undefined`** on the `@class` door
  (`Base.onClose:undefined` from `---@field onClose fun(): nil`). Out of COMP-09's scope; recorded so
  a later reader does not mistake it for something this feature caused.

### 4.5 Consumer 1 — completion. THE SELECTION RULE ONLY; the call site is §4.13

> **PREMISE CORRECTED 2026-08-12 (the abort's owed item 2).** Every earlier revision of this section
> rested on "`crossFileGlobalMembers` (`LuaCompletionContributor.kt:133-139`) is the completion door".
> **It is not.** That function sits behind `if (type == LuaGraphType.Undefined)` at
> `LuaCompletionContributor.kt:375-381`, and `LuaTypesVisitor.visitNameRef` →
> `freeGlobalSeed` (`LuaTypesVisitor.kt:1301-1330`) already resolves a free cross-file global through
> `LuaTypeManager.resolveGlobal`, so the in-file snapshot hands back a populated `Table` and the guard
> never opens for a receiver that has members. Probed across every cross-file completion test in the
> repo, the branch is entered by exactly three receivers — `luassert`, `wxFrame`, `AllColon` — all of
> which offer `<none>` (risks-and-gaps, "BLOCKER (Phase 2)").
>
> **What survives, unchanged, is everything below about SELECTION**: `membersOfGlobal` /
> `globalMembership`, the `LuaGlobalAssignmentIndex` candidate set, the scope-precedence chain, the
> `exclude` rule and the opacity sentinel. DR-21 re-took the comparison table below **through
> `completeBasic()`** and it holds row for row (§1.10.5). The `membersIn` union for §4.6 is likewise
> untouched.
>
> **What is replaced is the call site.** The table below is captioned "the door this call site
> actually serves" in earlier revisions; that caption is **withdrawn**. Read it as *"measured against
> today's global door, which is the door the hoisted site of §4.13 serves"* — because §4.13 asks the
> index before the snapshot exists, at which point there is no in-file type to displace and
> `resolveGlobal`'s answer is precisely what the index is standing in for.

Two earlier versions of this section were wrong; so was the third. Step 9 blocker B2 found that the
rule "mirror `typeOfGlobalIn`'s `.firstNotNullOfOrNull`" was read off the **tail** of a call chain
whose **head** says something else:

```kotlin
private fun typeOfGlobalIn(scope, name, exclude): LuaType? =
    FileBasedIndex.getInstance()
        .getContainingFiles(LuaGlobalAssignmentIndex.KEY, name, scope)   // <- NOT this index
        .asSequence()
        .mapNotNull { PsiManager.getInstance(project).findFile(it) as? LuaFile }
        .filter { it != exclude }                                        // <- no parameter for this
        .firstNotNullOfOrNull { globalTypeIn(it, name) }                 // <- skips typeless files
```

`LuaGlobalAssignmentIndex` keys **bare top-level globals** (`LuaGlobalAssignmentIndex.kt:27-28`);
`LuaReceiverMemberIndex` keys receivers that own members. Different candidate sets, so "the first
declaring file" denotes a different file in each. And "first" is undefined over an unordered
`getContainingFiles` result.

**The rule, implemented as `membersOfGlobal` and measured (DR-14).** Selection is not reinvented: it
asks `LuaGlobalAssignmentIndex` the same question `typeOfGlobalIn` asks, honours `exclude`, and only
then reads member values for the chosen file.

```kotlin
fun membersOfGlobal(receiver: String, project: Project, exclude: PsiFile?): List<LuaReceiverMember> {
    declaringFileFor(receiver, project, GlobalSearchScope.projectScope(project), exclude)
        ?.let { return membersInFile(receiver, project, it) }
    declaringFileFor(receiver, project, GlobalSearchScope.allScope(project), exclude)
        ?.let { return membersInFile(receiver, project, it) }
    return emptyList()
}
```

The candidate-set helper is **`candidates`** (`LuaReceiverMemberIndex.kt:492`) —
`getContainingFiles(LuaGlobalAssignmentIndex.KEY, receiver, scope)` filtered by
`exclude?.virtualFile != vf`. *(The sketch above names it `declaringFileFor`, which is the shape the
rule was first written as and **is not a symbol in the tree**; the landed helper returns the whole
candidate list, because DR-19c makes authority a property of the receiver rather than of the first
file — see §4.5c. Read `declaringFileFor(…)` in the sketch as `candidates(…).firstOrNull()`.)*
`membersInFile` (`LuaReceiverMemberIndex.kt:503`) is `processValues(KEY, receiver,
inFile, …)` — the platform's own per-file restriction, so no ordering assumption is needed.

**`exclude` is `context.containingFile?.originalFile`, not `containingFile` (BL-8).**
`LuaTypeManagerImpl.doResolveGlobal` (`LuaTypeManagerImpl.kt:214`) reads
`val here = context.containingFile?.originalFile` (`:218`), and during completion
the PSI file is a **copy** — so passing `containingFile` makes the exclusion silently never fire, and
nothing would catch it because the excluded file is usually not a declarer anyway. The prototype
compares `exclude?.virtualFile != vf` where `typeOfGlobalIn` compares `PsiFile != exclude`; these
agree once `originalFile` is used, because a copy shares its original's `VirtualFile`. **DR-14 never
exercised this path** — it passed `myFixture.file`, which declares none of the receivers — so it is
specified here and gated by a test rather than claimed as measured.

**One divergence remains and is deliberate**: `globalTypeIn` skips a file that declares the name with
no useful type, which requires the graph build this feature exists to avoid. `membersOfGlobal` takes
the first *declaring* file instead of the first *typed* one. DR-14 measured no divergence from this
on the golden receivers; it is recorded as a residual, not as solved.

**Measured against today's global door (DR-14), which is the door the §4.13 site serves** — the
caption "the door this call site actually serves" is withdrawn, see the correction above. Re-taken
through `completeBasic()` by DR-21 (§1.10.5) rather than against `resolveGlobal` directly:

| receiver | today, global door | `membersOfGlobal` | |
| :-- | :-- | :-- | :-- |
| `wx` (bare global + `@class`) | `[wxFileExists, wxID_ANY]` | `[wxFileExists, wxID_ANY]` | **exact** |
| `Shapes` (`a.b.c` shape) | `[deep, direct, nested, plain]` | `[direct, nested, plain]` | `deep` dropped — **BUG-430, deliberate** (§4.4a) |
| `wxFrame` (`@class` on a `local`) | **unresolvable** | `[]` | **exact** — see below |
| `AllColon` (`@class` on a `local`) | **unresolvable** | `[]` | **exact** |
| `Derived` (`@class D : Base` on a bare global) | `[ownFn]` | `[ownField, ownFn]` | **superset by one** — see §4.5a |

`wxFrame` and `AllColon` returning `[]` is not a regression, and at the §4.13 site they never reach
the index arm at all: a `@class` on a `local` is not a bare global, so `LuaGlobalAssignmentIndex`
offers no candidate, `globalMembership` answers `found = false`, and the site falls through to the
snapshot exactly as today. DR-21 confirms it end to end — `dot wxFrame: today=[] hoisted=[]`,
`dot AllColon: today=[] hoisted=[]`, `dot luassert: today=[] hoisted=[]`. Those receivers reach
members through the *type graph*, which this feature does not touch. An earlier draft would have used
the union here, which DR-14 shows returns their full member list — i.e. it would have invented
members at a call site that offers none today.

**The union is still right for §4.6**, where `addMethodsOf` genuinely wants every declaring file
(BUG-399). Two entry points, two selection rules, each measured against the door it serves:

| entry point | consumer | selection | verified against |
| :-- | :-- | :-- | :-- |
| `membersOfGlobal(receiver, project, exclude)` | completion (§4.5) | `LuaGlobalAssignmentIndex`, scope precedence, first file, exclude context | the **global** door |
| `membersIn(receiver, project, scope)` | materialization (§4.6) | union over all files in `scope` | the **`@class`** door — DR-14: union == class door on all four receivers |

Collapsing these into one `membersOf` is how D1, D2 and B2 all happened. The prototype's union
`membersOf` is renamed `membersIn` in Phase 1 and no method named `membersOf` survives, so the name
cannot be reached for by accident.

### 4.5c When the index is authoritative — three remedies, two of them wrong, all measured

This is the section rounds four and five both broke, and the sequence is worth keeping because each
wrong answer looked right.

**The problem (BL-2, DR-15).** The index's sources see a member only if it is written syntactically
against the receiver name. `Config = { host = …, port = … }` produced **no entries at all** where
today's global door returns `[host, port]`.

**Remedy 1 — "fall back when the index is empty". WRONG, and its test could not fail.** Round five
found the counterexample in the canonical Lua module idiom:

```lua
M = { VERSION = "1.0" }
function M.f() end          -- source 1 emits M -> [f], so the index is NOT empty
```

The fallback never fires and `VERSION` is silently lost. Worse, DR-15's supporting harness computed
`withFallback = if (indexed.isNotEmpty()) indexed else today` and then asserted `withFallback ==
today` — for the two receivers it was cited for, `indexed` is empty, so it asserted `today == today`.
A test that cannot fail, certifying a rule that was wrong.

**Remedy 2 — index the table literal (source 4).** `R = { a = 1 }` now emits `a`. Measured (DR-19):
`M` and `Config` both match today exactly. But it is still not sufficient, because opacity and
syntactic extension can coexist: `OM = require("luassert")` plus `function OM.extra() end` leaves the
index non-empty and incomplete. Emptiness is not a test for authority.

**Remedy 3 — the receiver carries a binding-opacity marker. This is the design.**

At index time, a bare `R = <expr>` — **at any depth**, not only top level — whose `<expr>` is **not**
a table constructor emits a sentinel `LuaReceiverMember.OPAQUE_BINDING`.

Any depth is deliberate and is what DR-19 measured. `LuaGlobalAssignmentIndex` is top-level-only
because a nested bare assignment may target an enclosing local, and mis-indexing one there would
invent a global. Here the failure is inverted: a missed sentinel marks a receiver authoritative when
it is not, and silently drops whatever only the graph knows — `wx = {}` … `function init() wx =
createNamespace() end` is the shape. Marking too many receivers opaque costs latency; marking too few
costs members. The query returns membership *and* whether it is
authoritative:

```kotlin
data class Membership(val members: List<LuaReceiverMember>, val authoritative: Boolean, val found: Boolean)

fun globalMembership(receiver: String, project: Project, exclude: PsiFile?): Membership
```

`authoritative = false` iff **any** declaring file in the chosen scope emitted the sentinel — not
merely the one selection happened to pick. The sentinel is filtered out of `members`, so it is never
offered; membership still comes from the first candidate, preserving `typeOfGlobalIn`.

**Why authority is a receiver property, not a per-file one (DR-19c).** Step 9 round six filed
"'first' is undefined over an unordered `getContainingFiles` result" as *non-blocking*, on the
grounds that today's `typeOfGlobalIn` is equally non-deterministic. Adding an assertion to DR-19's
harness — which until then only printed — showed it is blocking: two runs of the same fixture
disagreed about whether `assert` was authoritative, because a different declaring file was picked.

Today's non-determinism only decides *which* type you get. A non-deterministic **authority** flag
decides whether members are dropped, and the two errors are not symmetric: a wrong
`authoritative = true` silently loses members, a wrong `false` only costs latency. Computing opacity
over every candidate makes the flag stable and biases the residual error toward the survivable side.

This is also the round where a harness that could only print was upgraded to one that can fail — and
it failed immediately, on something two reviews had read past.

**Measured (DR-19), five shapes including the two that broke remedies 1 and 2:**

| receiver | shape | today | index | authoritative | effective | |
| :-- | :-- | :-- | :-- | :-- | :-- | :-- |
| `wx` | `wx = {}` + `function wx.works()` | `[works]` | `[works]` | true | `[works]` | MATCH |
| `M` | literal **+** syntactic | `[DEBUG, VERSION, f]` | `[DEBUG, VERSION, f]` | true | same | MATCH |
| `Config` | pure literal | `[host, port]` | `[host, port]` | true | same | MATCH |
| `assert` | `= require(…)` | `[unregister]` | `[]` | **false** | `[unregister]` | MATCH |
| `OM` | `= require(…)` **+** syntactic | `[unregister]` | `[extra]` | **false** | `[unregister]` | MATCH |

`OM` is the row that matters: the index has something, and is still not trusted.

**Where the fallback lives — BLOCKER 1.** Not in `LuaReceiverMemberIndex`. Its companion has no
`PsiElement` anchor (only the *excluded* `PsiFile`), `resolveGlobal(String, PsiElement)` and
`materialize(LuaType, PsiElement)` both require one, and `getMembers()` returns
`Map<String, VariableNode>` rather than `List<LuaReceiverMember>` — so the fallback cannot be written
inside a method with that signature at all. It would also invert the layering (indexing → type
engine) and re-enter `resolveGlobal`'s `resolvingGlobals` cycle-breaker.

It lives in the **contributor**, which already has the anchor and already handles both element types.

**And "the fallback" is not a branch to write — it is `return false`.** This is the one thing §4.13
simplifies about §4.5c. At the hoisted site the non-authoritative case does not need `emitGraph`, a
`resolveGlobal` call, or a `materialize`: the site simply declines, and control falls through to the
`LuaTypesSnapshot.forFile` path that already exists and is left byte-for-byte unchanged. The earlier
shape above duplicated the graph arm because it was replacing `crossFileGlobalMembers` *in place*,
where declining was not an option. See §4.13 for the code.

DR-21 proves the decline path end to end: `Busted` (`= require(x)`) and `OM` (`= require(x)` +
`function OM.extra()`) are both non-authoritative, both fall through, and both answer
`[unregister]` armed and unarmed (§1.10.5).

**Residual, stated**: a receiver bound through `require` or a call keeps today's *membership*. It no
longer keeps today's *cost* — DR-21 measured the `Opaque` receiver at 46–61 ms armed against
121–130 ms unarmed at the same site, because the receivers around it stop dragging the consumer file's
snapshot through the library graph build (§1.10.3). That is what withdraws the two-tier contract; see
the rewritten §4.12.

### 4.5a A membership superset the reviews did not find: `@field` on the completion door

DR-14's `Derived` row is a finding neither Step 9 review raised, and it is Risk 1.1 in a new place.

```
---@class Derived : Base
---@field ownField number
Derived = {}
function Derived.ownFn() end
```

```
today, global door          [ownFn]                     completion `Derived.` offers [ownFn]
today, @class door          [inheritedField, inheritedFn, ownField, ownFn]
membersOfGlobal             [ownField, ownFn]           <- ownField is NEW on this path
```

§4.3's source 3 indexes `@field` tags, so the index knows `ownField`; today's completion path does
not, because `resolveGlobal` returns a `LuaTableLiteralType` built from the assignment and never
reads the `@class` comment. **This is a deliberate behaviour change, declared here rather than
discovered**: `Derived.ownField` is a member the user declared and should be offered.

It does **not** threaten COMP-09-06 — the checker's inputs are unchanged (§4.1 keeps materialization
on `forFile`), so no corpus baseline can move. It **does** need a test asserting the new member is
offered, and a checklist scenario, both added.

**EXTENDED 2026-08-12 (DR-21) — the superset is not `Derived`-only, and it reaches the colon caret.**
Running the golden through `completeBasic()` armed shows the same mechanism on **`Base`**, which no
earlier revision named, and on the `:` caret, which none considered:

| caret | today | hoisted | the new members |
| :-- | :-- | :-- | :-- |
| `Derived.` | `[Show, ownFn]` | `[Show, ownField, ownFn]` | `ownField` — declared above |
| `Base.` | `[Show, inheritedFn]` | `[Show, inheritedField, inheritedFn, onClose]` | `inheritedField`, `onClose` |
| `Base:` | `[Show, inheritedFn]` | `[Show, inheritedFn, onClose]` | `onClose` |
| `Derived:` | `[Show, ownFn]` | `[Show, ownFn]` | none |

`Base` gains two because its own `---@class Base` carries `---@field inheritedField string` and
`---@field onClose fun(): nil`; `Derived` gains only `ownField` because inherited `@field`s belong to
`Base`'s key, not `Derived`'s — the flat index list, behaving exactly as §4.5a's B5 paragraph says it
should. `onClose` survives the **colon** filter because §4.3's source 3 classifies a `@field` whose
type text starts `fun(` as `Kind.FUNCTION`, which is the same classification the `@class` door
already applies (`Base|class|onClose:undefined` is a *function-shaped* member with an unresolved
signature — §4.4's residual list already records that). All four rows are **declared expectations**,
each with a test in Phase 2's list, not silent diffs.

**EXTENDED AGAIN 2026-08-12 (DR-28) — the same mechanism on five BUNDLED stubs, and one that does
not move.** §1.10.8a's pasted probe. The shape is `---@class X` + ten `---@field` constants + a bare
`X = {}`, with the constants **never assigned**; the ten therefore exist only in the `@class`
comment, which the global door does not read and §4.3 source 3 does.

| target | caret | today | hoisted | the new members |
| :-- | :-- | :-- | :-- | :-- |
| Redis 5 | `redis.` | 10 functions | + the ten constants | `LOG_DEBUG … REPL_REPLICA` |
| Redis 6 | `redis.` | 11 functions | + the ten constants | same ten |
| Redis 7+ | `redis.` | 13 functions | + the ten constants | same ten |
| Valkey 7.2 | `redis.` | 12 functions | + the ten constants | same ten |
| Valkey 8 | `redis.` | 12 functions | + the ten constants | same ten |
| all five | `redis:` | the functions | **unchanged** | none — the ten are `Kind.FIELD` |
| Valkey 7.2 / 8 | `server.` | 21 members | **unchanged** | none — `server.LOG_DEBUG = 0` is assigned, so source 2 already had them |
| Valkey 7.2 / 8 | `server:` | 11 functions | **unchanged** | none |

The `server` rows are the control that makes the mechanism legible: identical `@field` block, identical
`@class`, and **no movement**, because the constants are also written as assignments. It is the
`@field`-only declaration that moves, not `@field` as such. TC 7f (Redis 7+) and TC 7f-bis (Valkey 8,
plus `server`'s non-movement) are the gates; the function *count* differs per version, so neither may
be written as "the thirteen functions" without naming its target.

**And it settles B5.** COMP-09-03 scopes in "inherited members". DR-14 measured that today's
**completion door does not inherit at all** — `Derived.` offers `[ownFn]`, not `Base`'s members —
while the `@class` door does (`LuaGraphType.kt:179` `putAll(superType.getMembers())`). So a flat
index list is not a regression on this path. Inheritance is served by the `@class` door, which §4.6
leaves on the graph. COMP-09-03's inherited-members clause is therefore satisfied **by not changing
that path**, and requirements.md now says so instead of implying a supertype walk that no section
designs.

### 4.5b The emit loop — SUPERSEDED by §4.13; retained as the record

> **This section's problem no longer exists.** It solved "how do two branches share one emit loop
> when only one of them has `VariableNode`s". At the §4.13 site the index arm emits and returns
> *before* the shared loop is reached, so the loop below is **not touched at all** — no split, no
> `else`-branch copy, no risk of the in-file path changing shape. `LuaMemberLookup.create(LuaReceiverMember)`
> is still needed (§4.13); the fork is not. Kept because the analysis of the two `isColon` filters
> below is still binding on the new arm.

"The emit loop keeps its shape" was wrong. `LuaCompletionContributor.kt:375-388` is **shared** by two
branches producing different element types:

```kotlin
val members = if (type == LuaGraphType.Undefined) crossFileGlobalMembers(receiverExpr)  // Map<String, VariableNode>
              else type.getMembers()                                                     // Map<String, VariableNode>
for ((name, memberNode) in members) {
    val memberType = memberNode.write
    if (isColon && memberType !is LuaGraphType.Function) continue      // SEMANTIC filter
    result.addElement(PrioritizedLookupElement.withPriority(LuaMemberLookup.create(name, memberType), 100.0))
}
```

Changing `crossFileGlobalMembers` to `List<LuaReceiverMember>` breaks the `else` branch, which still
needs `memberNode.write` for both the type text and the semantic `isColon` filter. **The two branches
split; they do not share a loop.**

```kotlin
if (type == LuaGraphType.Undefined) {
    for (m in crossFileGlobalMembers(receiverExpr)) {                  // List<LuaReceiverMember>
        if (isColon && m.kind != Kind.FUNCTION) continue               // SYNTACTIC filter (D3)
        result.addElement(PrioritizedLookupElement.withPriority(LuaMemberLookup.create(m), 100.0))
    }
} else {
    for ((name, memberNode) in type.getMembers()) {                    // unchanged, verbatim
        val memberType = memberNode.write
        if (isColon && memberType !is LuaGraphType.Function) continue
        result.addElement(PrioritizedLookupElement.withPriority(LuaMemberLookup.create(name, memberType), 100.0))
    }
}
```

The `else` branch is untouched, which is what `implementation-plan.md`'s "golden unchanged for the
in-file path" exit criterion requires. `LuaMemberLookup` gains an overload
`create(member: LuaReceiverMember): LookupElement` — icon from `kind`, **no type text**, because the
index has no type (§1.7 measured type rendering at 4 ms, so this is absence, not cost).

The two `isColon` filters are deliberately not the same test: semantic in the `else` branch,
syntactic in the index branch. §4.3's D3 residue (`wx.f = someFn` records FIELD) lives exactly here
and is gated by a test rather than assumed away.

### 4.12 The two-tier latency contract — WITHDRAWN 2026-08-12, re-derived from a measurement

**This section previously proposed amending `non-functional.md` to exempt "tier 2" — a receiver the
index cannot see through — from the 100 ms time-to-first budget. That amendment must NOT be made.**
It was the abort's owed item 3, and the answer is that the tiers are not the distinction that matters.

**The reasoning that produced the tiers.** §4.5c's fallback pays the graph build "by construction", so
a receiver bound through `require` could not meet the budget however good the index arm was. Step 9
round five had already caught an earlier draft dodging this by pointing the gate at a fixture that
passes, and the tier statement was the honest repair for that dodge. It was never measured.

**Why it is wrong.** It attributes the cost to *the receiver being enumerated*. The cost is actually
the **consumer file's whole snapshot build**, which runs before any receiver is looked at and drags in
every free global in the file through `LuaTypesVisitor.freeGlobalSeed` → `resolveGlobal` → the
library's `buildSnapshot`. A tier-2 receiver sitting in a file next to tier-1 receivers pays for
*their* libraries, not for its own opacity. Measured at the §4.13 site, DR-21, two runs each:

| | tier 2 (`Opaque = require("luassert")`) cold time-to-first |
| :-- | --: |
| aborted site — `forFile` alone, from the abort's own table | 143 206 µs |
| §4.13 site, UNARMED | 130 080 µs / 120 962 µs |
| §4.13 site, ARMED | **60 784 µs / 46 259 µs**; gate run **24 936 µs** |

Every armed measurement is inside the 100 ms budget, with **no tier-2-specific work whatsoever** —
the index arm is never taken for `Opaque`; it is `found = true, authoritative = false` and falls
through to exactly today's code. The improvement is entirely the tier-1 receivers around it no longer
forcing the library graph.

**What replaces it.** Nothing. `non-functional.md` keeps its single flat time-to-first budget, and the
only amendment it still needs is the one requirements.md's "spec hole" section already names — that
the budget covers **indexed library content**, not only "projects up to 50k lines". COMP-09-08 asserts
the budget on tier 1 and continues to **record** tier 2, but as a watch item rather than an exemption:
if a future run puts tier 2 over 100 ms, that is a defect to file, not a contract to widen.

**The residual, honestly.** A file whose *only* receivers are opaque still pays one snapshot build,
because nothing hoists it. That case is not measured here and is not claimed fixed — it is
[[DR-24]]. What is measured is that opacity alone does not put a receiver over budget in any fixture
this feature has.

### 4.13 THE CHANGE SITE — above the snapshot build (replaces §4.5b's in-place rewrite)

This is the abort's owed item 1. Prototyped end to end and measured in §1.10 before being written.

**Where.** `LuaCompletionContributor.kt`, the member-completion provider registered under
`psiElement().afterLeaf(".", ":")` (`:346-350` today). The insertion point is immediately after
`findReceiverExpr` and **before** `val snapshot = LuaTypesSnapshot.forFile(...)`:

```kotlin
val receiver = PsiTreeUtil.prevVisibleLeaf(prevLeaf) ?: return
val receiverExpr = findReceiverExpr(receiver) ?: return

// COMP-09 §4.13 — answer from the index before the type graph is built.
if (addIndexedGlobalMembers(receiverExpr, parameters, isColon, result)) return

val snapshot = LuaTypesSnapshot.forFile(receiverExpr.containingFile)   // unchanged from here down
```

**Everything below that line is untouched** — the `forFile` build, the `type == LuaGraphType.Undefined`
guard, `crossFileGlobalMembers`, the shared emit loop. That is why §4.5b's branch split is no longer
needed and why the golden's `global` and `class` door rows cannot move (§1.10.5).

**The new function**, on `LuaCompletionContributor`'s companion beside `crossFileGlobalMembers`:

```kotlin
/** True when the index answered and the caller must not build the snapshot. */
private fun addIndexedGlobalMembers(
    receiverExpr: PsiElement,
    parameters: CompletionParameters,
    isColon: Boolean,
    result: CompletionResultSet,
): Boolean {
    val nameRef = bareNameOf(receiverExpr) ?: return false
    val membership =
        LuaReceiverMemberIndex.globalMembership(nameRef.text, nameRef.project, parameters.originalFile)
    if (!membership.found || !membership.authoritative) return false          // §4.5c sentinel
    if (LuaLocalBindingScan.binds(receiverExpr.containingFile, nameRef.text)) return false  // Rule S, §4.14
    emitIndexed(membership.members, isColon, result)
    return true
}

private fun emitIndexed(
    members: List<LuaReceiverMember>,
    isColon: Boolean,
    result: CompletionResultSet,
) {
    for (member in members) {
        if (isColon && member.kind != LuaReceiverMember.Kind.FUNCTION) continue   // SYNTACTIC filter (D3)
        result.addElement(PrioritizedLookupElement.withPriority(LuaMemberLookup.create(member), 100.0))
    }
}
```

`bareNameOf` already exists (`LuaCompletionContributor.kt:149-153`) and is reused verbatim.
`LuaMemberLookup.create(member: LuaReceiverMember): LookupElement` is the one new overload — icon
`AllIcons.Nodes.Method` for `Kind.FUNCTION` else `AllIcons.Nodes.Field`, **no type text**, because the
index carries none (§1.7 measured type rendering at 4 ms, so this is absence, not cost).

**Four rules, each with its measurement:**

1. **Decline by falling through, never by re-implementing the graph arm.** `return false` and the
   existing path runs. §4.5c's `emitGraph` branch is deleted from the design; it existed only because
   the old site replaced a function in place.
2. **The index is asked FIRST; Rule S runs only if the index could answer.** Not a routing choice —
   routing is identical either way (DR-21's whole table re-taken, byte-identical) — a cost one. Rule S
   is O(file) and would otherwise run on every keystroke of every member completion:
   `indexFirst=false` costs 10 875–21 501 µs per completion on a 4 002-line file, `indexFirst=true`
   costs nothing there because `found = false` short-circuits it (§1.10.6).
3. **`exclude` is `parameters.originalFile`, not `containingFile`** — BL-8, unchanged from §4.5.
   During completion `receiverExpr.containingFile` is a **copy** with its own `VirtualFile`, so the
   plain form matches no candidate and the exclusion silently never fires. Note that under Rule S the
   exclusion is *belt and braces*: a consumer file that declares the receiver bare is bound by Rule S
   and never reaches the query. Keep it anyway — the two rules are independent and the cost is nil.
4. **Rule S reads the COPY, `exclude` reads the ORIGINAL.** `receiverExpr.containingFile` is the
   in-memory completion copy and is what the user is actually typing in; `parameters.originalFile` is
   the file on disk that a `VirtualFile` comparison can match. Using the same one for both is wrong in
   opposite directions.

**Threading.** No change. The provider already runs on the completion thread inside the platform's own
read action; `globalMembership` is `FileBasedIndex` reads plus `ProgressManager.checkCanceled()` at
each `processValues` callback (already present, §4.9), and Rule S is a PSI walk. Nothing is added to
the EDT and no background dispatch is introduced. `globalMembership` already returns
`Membership(emptyList(), authoritative = true, found = false)` while dumb (§4.9, DR-10), so the site
declines during indexing and today's behaviour is preserved verbatim.

**Contract conformance.** No `Project`/`Editor`/`PsiFile` is retained — every value is a local inside
one `addCompletions` call. `addIndexedGlobalMembers` takes four arguments; the ≤3-argument tripwire is
waived the same way the enclosing `addCompletions(parameters, context, result)` is, and `emitIndexed`
is split out to keep each function under 30 logic lines.

### 4.14 Rule S — the shadowing rule, stated

The abort's owed item 1 names this explicitly: the obvious repair "reorders in-file-versus-global
precedence, which `LuaGlobalMemberCompletionTest.aLocalShadowsTheCrossFileGlobal` pins … inventing one
to make the gate green is the rogue workaround the abort protocol forbids." So it is stated here,
derived from the code that owns the precedence, and gated by named tests.

> **Rule S.** The index arm may be taken only if the consumer file contains **no binding occurrence of
> the receiver name anywhere in it**. Any binding occurrence, at any depth, routes to today's path.

**It is derived, not invented.** Today's precedence lives in `LuaTypesVisitor.visitNameRef`
(`:1301-1313`): `scope.lookup(o.text)` first, and only a name the file's scope chain does **not** bind
falls through to `freeGlobalSeed` → `resolveGlobal`. Rule S is that predicate with the *lexical
position* refinement dropped. `LuaScope.declare` (`LuaScope.kt:20`) is called from exactly **ten**
places in `LuaTypesVisitor.kt` — `:346`, `:462`, `:539`, `:555`, `:725`, `:748`, `:776`, `:1360`,
`:1414`, `:1425` — and Rule S covers every one that can
bind a name the consumer file owns. **The reproduction command matters**: `git grep -n
'scope.declare('` returns only **eight** of the ten, because `:1414` and `:1425` call
`funcScope.declare(` — the `self` and parameter sites, i.e. exactly the two clauses this section
then singles out as the ones the mutation proof reaches least. Use

```
git grep -nE '[Ss]cope\.declare\(' -- src/main   # 10
```

*(An earlier revision cited the eight-hit form as the source of a ten-site list, which is the kind of
citation that reads as verified and is not.)* The table below is exhaustive over those ten:

| `scope.declare` site | construct | Rule S clause |
| :-- | :-- | :-- |
| `LuaTypesVisitor.kt:539` | generic `for k, v in …` | `LuaGenericForStatement.nameList.nameRefList` |
| `:555` | numeric `for i = …` | `LuaNumericForStatement.identifier` |
| `:725` | `local a, b = …` | `LuaLocalVarDecl.attNameList` |
| `:748` | `local function f` | `LuaLocalFuncDecl`'s `NAME_REF` |
| `:776` | `function R.m()` binding a fresh `R` | `LuaFuncDecl`'s `FUNC_NAME` leading `NAME_REF` |
| `:1414`, `:1425` | `self` and parameters | `LuaParList.nameList.nameRefList`; `name == "self"` — **TC 10c** and **TC 10i** respectively |
| `:346` (`declareFileGlobals`) | file-scope `R = …` and `function R.m()` | `LuaAssignmentStatement.varList` bare targets; the `LuaFuncDecl` clause above |
| `:462` | type-guard narrowing | re-declares an already-bound name — **TC 10j** proves the transitivity rather than asserting it |
| `:1360` (`seedAmbientGlobals`) | a **stub file's** globals, not the consumer's — live on Redis/Valkey only (`KEYS`, `ARGV`) | deliberately **not** covered — measured behaviour-neutral, TC 10h; see below |

**Why over-approximating is the safe direction, and the asymmetry is the whole argument.** Rule S drops
lexical position, so `do local Shadow = {} end` followed by `Shadow.<caret>` reports "bound" where
`scope.lookup` at the caret would report unbound. That routes to today's path, which answers exactly as
today — a **latency** cost and nothing else. The opposite error, missing a binding, sends a shadowed
receiver to the index arm and **invents members the user did not declare**. This is the same asymmetry
§4.5c's opacity sentinel is built on (`a wrong true silently drops members, a wrong false only costs
latency`), pointed the other way, and it is why Rule S enumerates binding forms exhaustively rather
than trying to be precise.

**`seedAmbientGlobals` is deliberately excluded, and DR-26 measured the target it is live on.**

> **PREMISE CORRECTED 2026-08-12.** Earlier revisions of this paragraph said the site "declares the
> target's bundled `global.lua` globals into the consumer's root scope, so a bundled stub global like
> `table` is bound — which is exactly why the aborted site's `Undefined` guard never opened for it."
> **Both halves are false and the repo refutes them.** `seedAmbientGlobals` reads only files *named*
> `global.lua` (`LuaTypesVisitor.kt:1345`), and no `global.lua` exists under any
> `runtime/standard/lua-5.x` root — only under `runtime/redis/*` and `runtime/valkey/*` (§1.10.8).
> `table` is bound by **`freeGlobalSeed` (`LuaTypesVisitor.kt:1322`) → `resolveGlobal`**, off
> `runtime/standard/lua-5.4/table.lua:27` (`table = {}`), which is also what §4.5 says and what
> closed the `Undefined` guard. The `Undefined`-guard attribution belongs to `freeGlobalSeed`, not to
> ambient seeding.

What is actually true, and measured (§1.10.8):

- **The site is dead on the default target.** Standard Lua 5.4 maps to `runtime/standard/lua-5.4`
  (`Target.getLibraryRootPath():66`), which has no `global.lua`, so `:1360` declares nothing there.
  Every figure DR-21/DR-22 took was on that target, which is precisely why they could not exercise it.
- **On Redis/Valkey it declares exactly `KEYS` and `ARGV`**, and Rule S sends both to the index arm.
  Measured on real `Target(REDIS, 7+)` and `Target(VALKEY, 8)` projects: today's completion offers
  `[]` at both doors, and the index answers `found=true, authoritative=true, members=[]` — identical.
  `KEYS = {}` is an empty table literal, so §4.3's source 4 records no member and source 5 records no
  opacity sentinel. **TC 10h is the gate**, and it must set a Redis target rather than assume one.
- **`table` is gated by `LuaGlobalMemberCompletionTest.stdlibGlobalCompletesItsMembers`** — but as a
  `freeGlobalSeed` receiver, which is what TC 10g now says. `stdlibTable: today=[concat, insert, move,
  pack, remove, sort, unpack] hoisted=[same] same=true` (§1.10.4), and the index reads the same
  `table.lua` the seed resolves through, which is why they agree.

The exclusion stands: Rule S asks about the **consumer file**, and `:1360` declares another file's
globals into the consumer's scope. Including it would mean routing every Redis script's `KEYS`/`ARGV`
back through the snapshot for no behaviour difference. That is now a measured omission with a named
test and a named target, not an unexamined one.

**Prior art, named and rejected.** `LuaFileBindingsIndex` (`lang/indexing/LuaFileBindingsIndex.kt:34`)
already indexes "what names does this file bind", and reaching for it is the obvious move. It is
**not usable for Rule S** and must not be substituted: `LuaFileBindingsIndex.extractBindings`
(`LuaFileBindingsIndex.kt:350`) walks
`file.getBlockList().forEach { block -> block.statementList… }` (`:355-357`) — **file-scope
statements only** — so it sees no parameter, no
for-loop variable and no nested `local`. Using it would under-approximate, which is the member-inventing
direction. It is also stale for the completion *copy*, which is not the file the index built from. Rule
S is therefore a new, tiny PSI predicate, and this design says so rather than duplicating the index by
accident.

**Class and signature.**

```kotlin
package net.internetisalie.lunar.lang.psi

object LuaLocalBindingScan {
    /** True if [file] binds [name] anywhere in it, over-approximating `LuaScope.lookup`. */
    fun binds(file: PsiFile, name: String): Boolean
}
```

Implementation is one `PsiTreeUtil.processElements(file) { … }` pass with an early exit, type-testing
each element against the seven clauses in the table above. `name == "self"` returns true immediately —
`self` is bound by every method body and is never a global.

**The two clauses the mutation proof does not reach get their own tests (BL, 2026-08-12).** The
mutation proof deletes the `LuaParList` clause and watches TC 10c go red; that covers `:1425` and
nothing else. Two rows of the table above were previously carried on prose alone, and prose is what
this feature has been wrong about five times:

- **`name == "self"` (`:1414`)** — the one clause with no PSI shape behind it, so no clause deletion
  can express it. **TC 10i** is its gate: a library declaring `self = {}` + `function self.fromLibrary()`
  against a consumer that writes `self.<caret>` inside `function C:m()`. The clause is what stops the
  index arm offering `fromLibrary` on a receiver that is the method's own instance. Mutation-prove it
  the same way: delete the `name == "self"` early return and TC 10i must go red.
- **`:462` (type-guard narrowing)** — "covered transitively" is a claim about the code, so **TC 10j**
  measures it instead of asserting it: `if type(Shadow) == "table" then Shadow.<caret> end`, where
  `:462` re-declares `Shadow` under the narrowing. Whichever declaration bound it first is what Rule
  S must see; the test asserts the offered set is identical to today's. If it is not, `:462` is not
  transitively covered and Rule S needs an eighth clause — which is a finding, not a failure.

**Cost, measured (§1.10.6).** 18–30 µs on a small file; 8–16 ms on a 4 002-line file. Rule 2 of §4.13
keeps it off the path entirely for a receiver the index does not know, which is every purely in-file
receiver. The case it still runs on is "the consumer file binds a name that is *also* a project-wide
global" — the `Shadow` shape — where it costs up to ~16 ms on a very large file. That is a stated
residual with a tracked de-risking task ([[DR-23]]), not an unexamined cost: it is bounded above by
the `forFile` build it stands in front of (DR-20: 124 ms on the same 4 001-line file), so it can never
make a completion slower than the path it replaces on the arm where it is decisive.

### 4.6 Consumer 2 — materialization (`addMethodsOf`)

Signature unchanged; only how candidate keys are found changes. Note this door uses `allScope`
directly (BUG-399, `:541`) — it is not the global-resolution door and does not inherit §4.5's
precedence rule.

```kotlin
private fun addMethodsOf(scan: MethodScan, membersMap: MutableMap<String, LuaTypeMember>) {
    val scope = GlobalSearchScope.allScope(project)
    for (member in LuaReceiverMemberIndex.membersIn(scan.receiver, project, scope)) {
        if (member.kind != Kind.FUNCTION) continue
        if (membersMap.containsKey(member.name)) continue                       // first-wins, preserved
        val key = "${scan.receiver}${if (member.separator == COLON) ":" else "."}${member.name}"
        val decls = StubIndex.getElements(LuaGlobalDeclarationIndex.KEY, key, project, scope, LuaFuncDecl::class.java)
        val decl = decls.firstOrNull { scan.onlyIn == null || it.containingFile == scan.onlyIn } ?: continue
        membersMap[member.name] = LuaTypeMember(member.name, funcTypeFromStub(scan.className, decl), sourceElement = decl)
    }
}
```

`collectMethodMembers` (`:514`) and `materializeUnhostedClass` (`:389`, called at `:330`) drop their `getAllKeys` argument. Four
behaviours preserved verbatim, each a test:

| rule | where it lives today | how it survives |
| :-- | :-- | :-- |
| `allScope`, not projectScope (BUG-399) | `:541` | same scope passed to `membersIn` |
| first-wins within a receiver | `:544` | unchanged `containsKey` guard |
| file confinement for a local-declared class (BUG-398) | `:554` | unchanged `scan.onlyIn` filter |
| nested qualifiers are not members | `memberNameOf:562` | derived at index time (§4.3) |

Two entry points, deliberately named apart: **`membersOfGlobal`** (completion — scope precedence,
first file, index-or-fallback) and **`membersIn`** (materialization — an explicit scope, all files).
Collapsing them is how D1, D2 and B2 all happened, and the name `membersOf` is retired so it cannot
be reached for again.

### 4.7 COMP-09-05 — `@class`-declared metamethods

Change site: `LuaGraphType.fromLuaType`'s `is LuaClassType ->` branch (`LuaGraphType.kt:267-277`),
which today constructs `Table(type.name, members, supers, isExact = true)` and passes no
`metamethods`. Any member whose name is a known metamethod contributes to `metamethods` **as well
as** remaining a member.

**Specified, not narrated (B9).** Three verified facts constrain how:

1. `Trait` is a `sealed class` whose subobjects each hold their own `metamethods` set — `Numberable`
   (`:118-119`), `Stringable` (`:126`), `Lengthable` — and **there is no aggregate**. The union must
   be formed explicitly, once, as a `private val`:
   `ALL_METAMETHODS = setOf(Trait.Numberable, Trait.Stringable, Trait.Lengthable).flatMapTo(mutableSetOf()) { it.metamethods }`.
2. `Table.metamethods` is an immutable `val`, and the `Table` is constructed **before** `members` is
   populated — it goes into `visited[type]` first to break cycles, and the map is mutated afterwards.
   So the set cannot be read off the populated map; it must come from `type.getMembers()` *before*
   the constructor call:
   `val metas = type.getMembers().keys.filterTo(mutableSetOf()) { it in ALL_METAMETHODS }`.
3. Supertype metamethods need **no** separate walk: `getMembers()` already merges supertypes
   (`LuaGraphType.kt:179`), so `@class D : Base` inherits `Base`'s `__add` through the same
   expression. TC 6b asserts it rather than leaving it to be discovered.

"As well as" is deliberate, and **DR-12 measured why**:

```
v.  offers [__add, len, x]      <- a @class-declared __add already completes today
v:  offers [len]                <- and the colon filter already excludes it
```

So keeping it in `localMembers` is behaviour-preserving and only the operator check gains it. This
was contested across two reviews — the plan and the checklist both expected `__add` absent — and the
measurement settles it in the design's favour.

It also shows `LuaGraphType.kt:50-52` describes an intent the code does not implement on this path:
metamethods are held separately because putting them in `localMembers` "would make `t.__add` complete
on the instance, which is not what Lua exposes", and for a `@class`-declared metamethod it always
has. COMP-09-05 preserves the **behaviour**, not the intent. That is the conservative call and the
right one here, but the gap is real and is recorded rather than quietly inherited.

**Shipped 2026-08-12 (Phase 4), to this design, with two amendments.**

- The `is LuaClassType ->` arm moved into a private `classTable` helper rather than gaining two
  inline lines: the `when` it lived in was already well past the contract's function-length limit.
  The helper also calls `type.getMembers()` **once** and reuses it for both the metamethod filter and
  the member map, where the literal reading of fact 2 above would have walked the hierarchy twice.
- Fact 1's `ALL_METAMETHODS` is right as specified but **buys nothing observable**, which mutation
  testing established rather than argument: loosening it to `startsWith("__")` survives, because
  `LuaTypeGraph.implementsOperator` re-tests the name against the trait's own set. It is kept
  (`metamethods` is part of `Table`'s data-class equality, a memoization key) with no test claiming
  to prove it. See risks-and-gaps, "Phase 4 findings".

DR-12's measurement was **re-taken at the Phase 4 head**, not carried forward, because Phase 2 moved
global receivers onto the index arm in between: `v.` = `[__add, len, x]` and `v:` = `[len]` for the
class declared on a local *and* on a global, i.e. unchanged. The receiver-name carets on the global
form are `Vec.` = `[__add, len, x]` and `Vec:` = `[__add, len]`; `Vec:` keeping `__add` is §4.13's
syntactic `isColon` filter (the field's type text starts `fun(`, so §4.3 indexes it `Kind.FUNCTION`)
— the divergence TC 7e already declares, not a metamethod-visibility change.

### 4.8 Registration and reindex boundaries

```xml
<fileBasedIndex implementation="net.internetisalie.lunar.lang.indexing.LuaReceiverMemberIndex"/>
```

Already added beside the five existing entries (`plugin.xml:670`) for DR-09; nothing consumes it yet.
No new service, no EP. **Phases 2–5 add no registration of any kind** — `LuaLocalBindingScan` is a
plain Kotlin `object`, and the change site is inside a contributor `plugin.xml` already registers at
`:368-370`:

```xml
<completion.contributor
        language="Lua"
        implementationClass="net.internetisalie.lunar.lang.LuaCompletionContributor"/>
```

| index | today | after |
| :-- | :-- | :-- |
| `LuaReceiverMemberIndex` | 1 (DR-09) | **2 (shipped)** — see below |
| `LuaMemberFieldIndex` | 1 | unchanged — not modified |
| `LuaGlobalAssignmentIndex` | 2 | unchanged |
| stub format (`LuaFileElementType.getStubVersion`) | 4 | **unchanged** |

The stub row is the payoff, and Step 9 confirmed the argument: the sink stores the whole `FUNC_NAME`,
so `C:m` is itself a key. Deriving receivers in a *new* `FileBasedIndex` leaves the dot-only stub sink
alone — DR-06's asymmetry is sidestepped rather than fixed in place, at no stub-format cost.

**The `LuaReceiverMemberIndex` row says `1 → 1` in every revision of this design before 2026-08-12,
and that is not what shipped.** Phase 3's remediation bumped it to **2**, deliberately: the input
filter widened from `file.extension == "lua"` to every registration `LuaFileType` carries
(`extensions="lua;rockspec"` *and* `fileNames=".luacheckrc;.busted"`), so the index's **content**
changed, not merely its consumers. Without the bump a machine that had already indexed keeps its
`.lua`-only entries and the fix is invisible there — a persisted-state bug that no test on a fresh
fixture can see. So the boundary was crossed on purpose, and the version is 2.

**Impact on Phase 5's benchmarks is nil.** The rule below is about comparing a *warm* measurement
across a format change; every COMP-09 benchmark fixture builds its library tree per run and indexes
cold, so there is no warm figure on either side of the boundary to be invalidated.

Every version bump forces a full reindex on first run, and **no benchmark may cross one**.

### 4.9 Platform obligations

Previously absent entirely, which `non-functional.md:26-30` and the engineering contract make
binding:

- **Read action.** `membersOfGlobal`/`membersIn` touch `FileBasedIndex` and must be called under one.
  Completion contributors already run under a read action; `addMethodsOf` does too. No new
  requirement, but stated so a future caller does not assume otherwise.
- **`ProgressManager.checkCanceled()`** inside the value-processing loop. The 3 600-member call is
  2 ms, so this is not about the loop being long — it is that a `processValues` callback that never
  yields cannot be cancelled if a pathological receiver appears.
- **`DumbService` — MEASURED (DR-10), and the obligation is real.**

  | door, while dumb | today |
  | :-- | :-- |
  | `resolveGlobal` | **null** — guarded at `LuaTypeManagerImpl:188` |
  | `materialize(resolveGlobal(...))` | **null**, following from the above |
  | completion, `wx.<caret>` | **`[]`** — empty, no throw, no error |
  | `LuaReceiverMemberIndex.membersOf` (the prototype) | **throws `IndexNotReadyException`** |
  | `resolveType` | **logs an IDE internal error and rethrows** — no guard at all (**BUG-432**) |

  So §4.9's requirement was right and the prototype does not meet it. **The rule: `globalMembership` returns
  `Membership(emptyList(), authoritative = true, found = false)` when `DumbService.isDumb`, and
  `membersIn` returns empty.** `authoritative = true` is deliberate: it stops the contributor taking
  §4.5c's fallback into `resolveGlobal`, which is itself dumb-guarded and would return null anyway.
  The user sees `[]`, which is what DR-10 measured today, which reproduces the `[]` the user sees
  today rather than the exception the prototype would introduce.

  The behaviour being matched is `resolveGlobal`'s, deliberately — **not** `resolveType`'s, which
  DR-10 found is itself defective (BUG-432: an `IndexNotReadyException` is control flow, and logging
  it via `Logger.error` shows the user a crash report while indexing). Matching that would propagate
  a bug into new code, so this is one place COMP-09 does not preserve today's behaviour.

### 4.10 The record: what the two Step 9 reviews found

Kept because the pattern is the point. §4 was written twice from reading and failed twice; every
figure in §4.0–§4.5 above comes from a run.

- **D1** — flat `allScope` reverted BUG-427's precedence. **Confirmed** and fixed (§4.5).
- **D2** — `membersOf` union was a membership superset. **Confirmed by measurement** and fixed by
  first-file-only (§4.5).
- **D3** — `Kind` syntactic where the filter is semantic. **Partly real**: direct
  `= function() end` is classifiable, indirect assignment is not (§4.3), and the residue is now a
  bounded, gated gap.
- No `membersOf` algorithm, no wire format, `@field` undesigned, no read-action/cancel/dumb-mode
  statement — all closed above.
- Confirmed correct by both reviews: the no-stub-bump argument, the separator round-trip, the four
  preserved materialization behaviours, and the collection-valued index's viability.

### 4.10a COMP-09-08 — the latency gate, now that DR-02a supplies a mechanism

Previously "NOT DESIGNED — no test class, no first-element harness". The harness exists (§1.9).

**Mechanism.** A `CompletionContributor` registered `LoadingOrder.FIRST` for Lua whose
`fillCompletionVariants` calls `result.runRemainingContributors(parameters) { … }`, timestamping the
first result and the last, then passing each through unchanged. It observes a real `completeBasic()`
run and adds nothing to production code — the probe lives in the test source set and is registered on
`testRootDisposable`.

**Assertions**, all against a **cold** receiver, all medians of ≥5 where the quantity is stable:

| # | assertion | today |
| :-- | :-- | :-- |
| 1 | cold time-to-first < the `non-functional.md` budget | **red** — 746 ms vs 100 ms |
| 2 | time-to-first for a 3-member receiver and a 3 600-member receiver are within a stated factor | **red** — narrow 41 ms inside the budget, wide 1 641 ms far outside it. *(The `40x` once printed here is a ratio of two harness figures and is retired as a quotable quantity per DR-08 — §4.10a-bis replaces this assertion with a **count** for exactly that reason.)* |

**Mutation proof comes free**, which is the unusual part: the plan requires COMP-09-08 to be shown
failing before the fix lands, and both assertions fail on today's code as written. Nothing has to be
deliberately broken to prove the test can fail — §1.9 *is* the failing run. What must still be
mutation-proved is the opposite direction, once the fix lands: re-introducing the graph build on the
completion path must turn assertion 1 red again, or it is passing for an unrelated reason.

**Cold is the whole difficulty.** Warm time-to-first is 1.1 ms, so a gate that does not force a cold
state passes trivially and forever. "Cold" here means the declaring file's `LuaTypesSnapshot` has not
been built — §1.2 established a single keystroke anywhere is enough to get back to it, so the gate
must reproduce that rather than rely on fixture ordering, and each measured receiver needs its own
file (§1.9).

**It runs in the routine loop**, not behind `-PwithPerf` — COMP-09-08's acceptance criterion says so
explicitly, and Risk 1.5 records what the alternative bought last time.

**Assertion 2's factor — tracked, with a derivation rule (BL-4).** "Independent" needs a number.
It is **DR-17**, and it is not left to whatever the post-fix run happens to produce: the factor is
`ceil(p95 / p50)` over ≥5 cold samples of the *narrow* receiver alone — i.e. the harness's own noise
floor, measured on a case where member count cannot be the variable. A wide-vs-narrow ratio inside
that band is independence; outside it is not. Today's pair sits on opposite sides of the budget
itself — 41 ms narrow, 1 641 ms wide — which no plausible floor absorbs, and that is why assertion 2
is red now. *(This paragraph used to say "today's 40x is far outside any plausible floor". The `40x`
is retired as a quotable quantity, per DR-08; it is a pre-Phase-0 record. §4.10a-bis then retires the
whole timing form of assertion 2, because its **verdict** — not just its precision — flips between
runs of identical code.)*

**Assertion 1 measures the receiver on the FAST path (§4.5c).** A receiver that falls back pays the
graph build by design, so a gate that happened to pick one would certify a budget the fallback does
not meet. The gate fixture declares its members syntactically; the fallback's cost is recorded
separately, not asserted against the budget. *(§4.12 is withdrawn, so "recorded separately" is now a
watch item rather than an exemption: tier 2 measured 25–61 ms armed, inside the budget.)*

#### 4.10a-bis Assertion 2 must become a COUNT — re-derived 2026-08-12 (DR-22)

**Assertion 1 is settled by the prototype: it goes green at the §4.13 site** (§1.10.2 — the inverted
gate went red reporting `13 ms against a 100 ms budget`, and five cold samples across five distinct
wide receivers median **7 414 µs**). Nothing about assertion 1 changes.

**Assertion 2 is not settled, and its derivation is now broken by its own success.** DR-17 derives the
independence factor as `ceil(p95 / p50)` over five cold *narrow* receivers — the harness's own noise
floor. That worked while the narrow receivers were served by the graph, because their cost was
dominated by real work. Armed, the narrow receivers are **also served by the index**, so their cost is
almost entirely the platform's fixed completion overhead, and `p95/p50` measures scheduler jitter on a
~6 ms constant. Two armed runs of the same code disagree:

| run | narrow p50 | narrow p95 | derived floor | wide | ratio | assertion 2 |
| :-- | --: | --: | --: | --: | --: | :-- |
| DR-22, five distinct wide receivers | 6 214 µs | 7 220 µs | 2x | 7 414 µs | **1x** | **met** |
| full-suite gate run 1, one wide receiver | 2 744 µs | 3 362 µs | 2x | 8 705 µs | **3x** | **not met** |
| full-suite gate run 2, one wide receiver | 3 297 µs | 3 855 µs | 2x | 13 363 µs | **4x** | **not met** |

All three runs are of the same prototype, and assertion 1 was comfortably met in every one (7.4 ms /
13 ms / 9 ms against 100 ms). The verdict on assertion 2 flips on which narrow samples the machine
happened to give, which is exactly the failure mode DR-08's standing rule names: *gate on a **count**,
never a timing threshold, wherever a count will do*.

**A count will do here, and it already exists.** The quantity assertion 2 is trying to express is "work
does not track member count". §4.10b's `LuaReceiverMemberWork.entries` measures precisely that, at the
traversal site, and DR-22 measured it at the hoisted site:

```
COMP09-DR22 workbound before: entries=3600 files=1
COMP09-DR22 workbound after:  entries=3600 files=1     (+ an unrelated 4 000-member library)
```

**So assertion 2 is replaced, not tuned.** Phase 2 rewrites it as: *the number of index entries
traversed to answer the narrow receiver is `narrowMembers`, and to answer the wide receiver is
`wideMembers`, and neither moves when unrelated indexed content is added.* That is a count, is
machine-independent, and cannot flip on jitter. The timing figures stay in the gate as **printed
records** next to assertion 1, so a regression in the constant overhead is still visible to a reader —
but nothing is asserted on a ratio of two timings again.

**The one thing the count cannot say** is that a per-entry cost did not explode. Assertion 1 covers
that: 3 600 entries under a 100 ms wall budget bounds the per-entry cost at 27 µs, and the measured
index read is 1 010–3 576 µs for those 3 600 entries (§1.10.7), two orders inside it.

### 4.10b COMP-09-09 — the work bound, from DR-11

The last requirement without a design. COMP-09-09 asks that "**entries traversed** per enumeration is
instrumented and asserted proportional to matching entries; adding unrelated indexed content must not
increase it". §4.10a's timing probe cannot answer it — a duration is not a count, and an
implementation that scanned the whole key space *quickly* would pass a latency gate while failing
this one outright.

**Mechanism.** There is no platform metric for "entries traversed", but there is an exact place to
count: `FileBasedIndex.processValues` calls back once per (key, file) pair it visits, so counting
callbacks **is** the traversal count, taken where the traversal happens. DR-11 added the counter to
the prototype and measured:

| | entries traversed | files visited | members found | stub keys in the index |
| :-- | --: | --: | --: | --: |
| quiet project, `Target` | 50 | 1 | 50 | 4 145 |
| **noisy** (+40 receivers x 100 members), `Target` | **50** | **1** | 50 | 4 145 |
| noisy, `Noise7` | 100 | 1 | 100 | 4 145 |

`entriesTraversed == membersFound` exactly, and it does **not** move when unrelated indexed content
is added — which is the requirement, verbatim. The `getAllKeys` column is the contrast: the scan
COMP-09-01 removes would visit 4 145 keys to answer a 50-member question.

**Assertions:**

| # | assertion | asserted on | why it is not tautological |
| :-- | :-- | :-- | :-- |
| 1 | `entriesTraversed >= membersIn(R).size`, and `== ` it when no name repeats across files | `membersIn` (§4.6) | a scan-based implementation traverses the key space and fails |
| 2 | `entriesTraversed` for R is **unchanged** between a quiet and a noisy project | both entry points | this is the one that goes red if anyone reintroduces a scan |
| 3 | `filesVisited ==` the number of files declaring R | `membersIn` | catches an enumeration that widened its scope (D2's failure mode, in count form) |
| 4 | `filesVisited ==` the number of **candidate** files the selection rule chose (1 for a receiver bare-bound in one file in the chosen scope), else 0 | `globalMembership` (§4.5c) | the completion door reads only the files `LuaGlobalAssignmentIndex` offered it; more means the selection rule leaked |

*(An earlier revision added "or 0 on the §4.5c fallback". That is impossible: the fallback is reached only when a declaring file **was** found and emitted the sentinel, so `membersInFile` has already visited at least one file. Written that way the gate goes red on a correct implementation.)*

*(**Corrected after the Phase 1 review.** Assertion 4 said "reads exactly one file **by construction**", justified by the fallback argument above. §4.5c's DR-19c rule invalidates that justification: authority is a property of the receiver, so `membershipOver` reads **every** candidate to decide opacity and only takes membership from the first. A receiver bare-bound in two files in the chosen scope therefore visits two, correctly. `MemberEnumerationWorkBoundGateTest`'s `quietFixture()` bare-binds `Target` in one file only, so the `== 1` it asserts is a property of that fixture; the test KDoc says "**here**" and this row now says so too. This matters at Phase 3, which uses assertion 4 as its D2-leak detector — read as a construction guarantee it would not distinguish a genuine scope leak from the ordinary multi-candidate case.)*

**The counter must be extended to `membersOfGlobal` (BL-5).** DR-11's prototype counts only inside
the union entry point; `membersInFile` runs `processValues` without touching it, so assertion 4 was
specified against an instrument that does not observe the door it names. Phase 1 adds counting to
both entry points — one line at each `processValues` callback — and TC 9, which measures
`wx.<caret>`, is the *completion* door and therefore depends on it.

**Corrected after Step 9 (B6).** DR-11 measured these against the prototype's *union*, and assertions
1 and 3 as first written are invalidated by §4.5's first-file rule — `filesVisited` is 1 by
construction there, and `membersOf` de-dupes with `putIfAbsent` while `entries` sums `members.size`,
so equality breaks whenever a name repeats across files. Each assertion now names the entry point it
holds for, and assertion 1 states the inequality that is actually true. A gate written against the
wrong one of two entry points is a gate that passes for the wrong reason.

**Shipping shape — not the spike's.** DR-11 used a `@Volatile` global object, which is fine for a
single-threaded test and wrong in production: two concurrent completions would overwrite each other's
counts, and a global mutable static on a hot path is exactly the kind of thing that survives into
shipped code because it is cheap. The counter ships as a **`ThreadLocal`**, reset by the caller,
read on the same thread that ran the enumeration. Completion runs one session per thread, so the
numbers stay attributable.

**Caveat DR-11 exposed, worth keeping.** `StubIndex.getAllKeys(KEY, project)` returned 4 145 in
*both* arms, including the "quiet" one that declares 54 — index storage is shared across test methods
in one JVM and `getAllKeys` is not really project-scoped. That is another reason the gate counts
callbacks rather than comparing key totals: the key total is not a reliable number in a test, and a
gate built on it would be measuring the fixture.

### 4.8a What the second index costs to build — DR-18 ANSWERED (2026-08-12)

§4.8 chose "a new `FileBasedIndex` rather than a stub-format bump" on the grounds that "a new index is
additive and its cost is confined to first build". That cost was asserted nowhere and measured
nowhere through Phase 1, and the premises table flagged it as the feature's largest unmeasured
premise: the thesis is that **the first completion of a session** matters, which is exactly when
indexing runs. Measured on gce-builder, 2026-08-12, by a throwaway `CompNineDr18Test` that was
**run and reverted** — it is in no commit, so the pasted output below is the evidence and nothing
may instruct re-running it.

The indexer is invoked directly — `LuaReceiverMemberIndex().indexer.map(FileContentImpl.createByFile(vf, project))`
— alongside the two existing **whole-project** indexes on identical input, so the comparison is like
for like. Two figures per index, because they answer different questions: **warm** has the PSI already
built, which is the *marginal* cost of a second index (the platform builds one `FileContent` per file
and every extension shares its PSI); **cold** builds the PSI inside the timed region and is dominated
by the parse, which is shared and therefore not attributable to this index.

```
COMP09-DR18 library is 123 KiB
COMP09-DR18 library 123 KiB | LuaReceiverMemberIndex:     cold=845414us (single) warm(us)=[59665, 59950, 60614, 71963, 401740] median=60614
COMP09-DR18 library 123 KiB | LuaMemberFieldIndex:        cold=340841us (single) warm(us)=[14497, 18225, 19595, 21913, 297750] median=19595
COMP09-DR18 library 123 KiB | LuaGlobalAssignmentIndex:   cold=103932us (single) warm(us)=[ 4131,  5008,  5806,  7483,  78707] median=5806

COMP09-DR18 ordinary file is 15 KiB
COMP09-DR18 ordinary 15 KiB | LuaReceiverMemberIndex:     warm(us)=[7922, 8038, 8171, 8290, 31986] median=8171
COMP09-DR18 ordinary 15 KiB | LuaMemberFieldIndex:        warm(us)=[2006, 3189, 3654, 3662, 21986] median=3654
COMP09-DR18 ordinary 15 KiB | LuaGlobalAssignmentIndex:   warm(us)=[ 381,  382,  399,  431,  23854] median=399

COMP09-DR18 tree is 78 files, 207 KiB
COMP09-DR18 tree warm total | LuaReceiverMemberIndex:   runs(us)=[323969, 337550, 354739, 356354, 409560] median=354739
COMP09-DR18 tree warm total | LuaMemberFieldIndex:      runs(us)=[207360, 218935, 229713, 233276, 243147] median=229713
COMP09-DR18 tree warm total | LuaGlobalAssignmentIndex: runs(us)=[ 72369,  78102,  80440,  81746,  83066] median= 80440
```

**Verdict: the premise holds, and the number is small next to what it removes.**

- The marginal cost of the second index on the **123 KiB library the whole feature is about** is
  **61 ms, once**, written to disk and reused across sessions. The cold `buildSnapshot` it removes is
  **844–955 ms, every session** (TYPE-11 DR-08). One is a one-off write; the other is a per-session
  read. They are not the same kind of cost and the plan should not net them off — but the direction is
  unambiguous under any accounting.
- Over a **78-file / 207 KiB tree** the new index adds **355 ms** to an existing **310 ms**
  (`LuaMemberFieldIndex` 230 ms + `LuaGlobalAssignmentIndex` 80 ms) of Lua-specific indexer time. That
  roughly doubles the *Lua indexer* component of a first scan. It does not double indexing: the parse
  (`cold − warm`, ~785 ms on the library file alone) is shared and dwarfs all three.
- It is the **most expensive of the three**, which is expected — it is the only one with five sources
  and four full `findChildrenOfType` passes (§4.3). Whether those passes can share one traversal is a
  cheap follow-up, not a blocker: [[DR-25]].
- The three `cold` figures are **not comparable to each other** — they ran in sequence against the same
  `VirtualFile`, so later ones benefit from platform-level warming. Each is labelled
  `(single — unrepeatable by construction)` and none is used for a comparison. Only the warm medians
  are compared, and each warm list's outlier (401 740 / 297 750 / 78 707 µs) is the first, JIT-warming
  sample — which is why the median of five is the reported figure and the raw list is printed beside it.

### 4.11 Still not designed

- ~~COMP-09-08, COMP-09-09~~ — **both designed** (§4.10a, §4.10b) off DR-02a and DR-11. Every
  requirement now has a design. *(Assertion 2 is re-derived as a count — §4.10a-bis.)*
- ~~What a second whole-project index costs to build~~ — **measured, §4.8a (DR-18).**
- **Narrowing cache invalidation** (§3.3 / DR-07) — independent, possibly a smaller win.
- **Removing the four caches** — re-measure each; a follow-up.
- **`LuaImplicitFields:76` / `LuaTypesVisitor:1349` / `catsClassTags` (`LuaTypeManagerImpl:436`)** —
  COMP-09-02's other three sites. **Out of scope — but on a weaker basis than an earlier draft
  claimed, and the weakness is stated (BL-3).**

  The earlier justification was "22 ms of a 949 ms cold path". Three things are wrong with it and
  none was caught until the fourth review: the **949 ms denominator is disavowed by §1.8**, which
  re-measured the same path at 383 ms (−60 %) and put the warm-file median at 120 ms — against which
  22 ms is 18 %, not 2 %; the **22 ms numerator is single-shot**, violating this plan's own
  medians-of-≥5 rule (DR-08 was open when this was written and is **done at Phase 0** — every
  surviving harness now medians five runs); and `LuaTypesVisitor:1349` is on the
  **`resolveGlobal`/`forFile` door**, not the `@class` door candidate B was measured on, so the
  figure does not even apply to one of the three sites.

  They stay out of scope because §4.3 and §4.6 do not touch them and nothing in this feature's
  measured critical path runs through them — **not** because they were shown to be cheap. That is a
  scoping decision, and COMP-09-02's acceptance is scoped to match. Re-measuring them properly is
  **DR-16**, tracked rather than asserted away.

## 5. Requirement coverage

Rewritten after DR-09. Every "covered" row now cites a printed figure from a prototype run rather
than a call-shape argument; the rows that are still not designed say so.

| Requirement | Covered by | Evidence |
| :-- | :-- | :-- |
| COMP-09-01 receiver-keyed enumeration | §4.2 — implemented and registered | §4.0 — `membersOf("wx")` 2 ms median for 3 600 members vs `resolveGlobal` 13 655 ms, same fixture, same run |
| COMP-09-02 no full-file walk | §4.3 | §1.6 — the walk and the parse are the `@class` door's cost; §4.0 — the index path pays neither |
| COMP-09-03 all sources, dot **and** colon | §4.3 covers **3 of 4** — funcs, assignments, `@field`; metamethods are COMP-09-05 | §4.4 — `AllColon` (every member colon-declared) enumerates 2 of 2, the case a naive swap emptied |
| ~~COMP-09-04~~ incremental yield | **withdrawn** | §1.7 — no tail to stream |
| ~~COMP-09-04b~~ lazy type rendering | **withdrawn** | §1.7 — measured 4 ms for 3 700 members; presentation was never the cost, and `renderElement` is not per-visible-row |
| COMP-09-05 `@class` metamethods | §4.7 — change site named, current behaviour measured | DR-12 — `v.` offers `[__add, len, x]`, `v:` offers `[len]`, so the change is operator-only and the offered sets must not move |
| COMP-09-06 no new type source | §4.1 | the split — names for completion, `forFile` retained for the checker — keeps the checker's inputs unchanged |
| COMP-09-07 behaviour-preserving | §4.4 measured; §4.4a **redefines the bar** | 3 of 4 receivers exact; the 4th is BUG-430, where the two doors disagree and the global one is wrong. COMP-09 preserves the `@class` door and deliberately does not reproduce the global door's flattening |
| COMP-09-08 latency enforced | §4.13 — the change site; §4.10a — gate shipped (`MemberEnumerationLatencyGateTest`); §4.10a-bis — assertion 2 re-derived as a **count** | §1.10.2 — the prototype at the §4.13 site takes cold time-to-first from **491 ms to 7.4 ms**, both medians of five cold samples, and the inverted gate went red reporting *"COMP-09-08 is now MET — 13 ms against a 100 ms budget"*. Assertion 2's timing form flips verdict between two runs of the same code (§4.10a-bis), which is why it becomes a count |
| COMP-09-09 work bound | §4.10b — four assertions off a `processValues` callback count; `filesVisited` pinned at 1 **and** 2 | §4.10b (DR-11) — `entriesTraversed` is 50 for a 50-member receiver and **unchanged** by 4 000 unrelated keys; §1.10.7 measures it **at the §4.13 site**: `entries=3600 files=1` before and after adding an unrelated 4 000-member library |
| COMP-09-10 the shadowing rule | §4.14 — Rule S stated, derived from `LuaTypesVisitor.visitNameRef`'s `scope.lookup` and exhaustive over the **ten** `LuaScope.declare` sites; TC 10a–10j; two mutation proofs | §1.10.4 — ten binding-form scenarios, all `same=true`, including `aLocalShadowsTheCrossFileGlobal`'s exact fixture. §1.10.8 (DR-26) — the one deliberately excluded site (`:1360`, `seedAmbientGlobals`) measured on the only targets it fires on: `KEYS`/`ARGV` answer `[]` today and `found=true, authoritative=true, members=[]` from the index, identical |
| **The change site** (COMP-09-01's consumer) | §4.13 + §4.14 (Rule S) | §1.10 — prototyped end to end through `completeBasic()`, run over the full 2 639-test suite armed: **exactly two tests move**, both declared (§1.10.5). Ten shadowing scenarios identical (§1.10.4) |
| **Index build cost** (the §4.8 premise) | §4.8a | DR-18 — **61 ms marginal, once**, on the 123 KiB library whose per-session graph build is 844–955 ms |
