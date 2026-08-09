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

The mechanism is as described: `typeCache` (`LuaTypeManagerImpl:34-44`) and the per-file snapshot
(`LuaTypes:215-222`) both depend on project-wide `PsiModificationTracker`, so an edit anywhere
invalidates the *library's* snapshot. The NFR's per-keystroke clause was right that repeated typing
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

`LuaFuncStubElementType:69-75` sinks a receiver key only when the name contains `'.'`, while
`memberNameOf:466` matches `receiver.` **and** `receiver:`. So the swap would silently drop every
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

**And `renderElement` is not per visible row.** `BaseCompletionLookupArranger.java:187` calls it for
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
be independent of *candidate count*, and it scales **40x** between a 3-member and a 3 600-member
receiver measured cold in separate files. Separate files matter: the per-file snapshot is memoized,
so two receivers in one file would have made the second look free for reasons unrelated to its size.

**Caveat on the two cold figures.** 746 ms and 1 641 ms are the same receiver in different fixtures
(one library file vs two), so they are not comparable to each other; each is only comparable to its
own pair. Both are cold and both are over budget, which is all either is used for.

**This is COMP-09-08's mechanism.** The gate asserts cold time-to-first against the budget and is red
today by construction, which is the mutation proof the plan asks for — no need to break the code to
show the test can fail, because it fails now.

## 2. Consequences for the plan

Corrections forced by Step 9 review, beyond §1.7/§1.8:

| Claim | Status |
| :-- | :-- |
| "per-keystroke, 76 % repaid" (§1.2) | **UNRELIABLE.** The harness verdict flipped on re-run. The *mechanism* is sound by reading (`LuaTypes.kt:214-222` deps include project-wide `PsiModificationTracker.MODIFICATION_COUNT`) but it is no longer a measured claim. Needs a repeated-run harness before being cited |
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
(`LuaType.kt:21-27`), and `sourceElement` is **load-bearing** — `materializeClass:256-262` records
that `LuaOverrideLineMarkerProvider` uses it as a gutter navigation target, with the warning that
"the parity harness compares names and types only, so it would not catch that". An index of names
cannot produce a `LuaTypeMember`.

So enumeration splits by consumer, and only one of them can be served without PSI:

| consumer | needs | can an index answer it? |
| :-- | :-- | :-- |
| **completion** (`crossFileGlobalMembers`) | member name; whether it is a function (the `isColon` filter, `LuaCompletionContributor.kt:384`); an icon (`LuaMemberLookup.kt:19-23`) | **yes** — name + kind, no PSI |
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

### 4.3 Indexer algorithm — three sources, as implemented

1. **`function R.m()` / `function R:m()`** — `node.findChildByType(FUNC_NAME)?.text`, the same source
   `LuaFuncStubElementType.createStub:24` uses, so the two agree by construction. `Kind.FUNCTION`,
   separator from the character found.
2. **`R.f = value`, at any depth** — a member assignment inside a function still declares a member.
   Deliberately unlike `LuaGlobalAssignmentIndex`, which is top-level-only because a nested *bare*
   assignment may target an enclosing local. Rejects `R[i]` (keyed suffix), `f().x` (call suffix)
   and `R.a.b` (more than one suffix), matching `LuaImplicitFields.singleFieldSuffixName`.
3. **`---@field` on a `---@class R` comment** — via `LuaCatsDeclarations.fieldMembers`. Sources 3
   and 4 of COMP-09-03 were previously undesigned; this closes the `@field` half.

`Kind` for source 2 (§4.9 D3): `R.f = function() end` is `FUNCTION` — the RHS is syntactically a
`LuaFuncDef`. `R.f = someOtherFn` cannot be classified without resolution and is recorded `FIELD`.
Measured on the DR-09b fixture: `assignedFn=FUNCTION/DOT`, `aliasedFn=FIELD/DOT`. **This is a real
residual gap, not a solved one** — it is bounded to indirectly-assigned functions, and COMP-09-08's
gate must include a case for it so the cost is visible rather than assumed away.

For source 3, `Kind` comes from the declared type text starting `fun(`. `---@field onClose fun(self:
wxFrame): nil` measured as `FUNCTION/DOT`.

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

### 4.5 Consumer 1 — completion. REWRITTEN FROM DR-14 after a third failed review of this section

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

`declaringFileFor` is `getContainingFiles(LuaGlobalAssignmentIndex.KEY, receiver, scope)` filtered by
`exclude?.virtualFile != vf`, taking the first. `membersInFile` is `processValues(KEY, receiver,
inFile, …)` — the platform's own per-file restriction, so no ordering assumption is needed.

**`exclude` is `context.containingFile?.originalFile`, not `containingFile` (BL-8).**
`doResolveGlobal:143` reads `val here = context.containingFile?.originalFile`, and during completion
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

**Measured against today's global door (DR-14), the door this call site actually serves:**

| receiver | today, global door | `membersOfGlobal` | |
| :-- | :-- | :-- | :-- |
| `wx` (bare global + `@class`) | `[wxFileExists, wxID_ANY]` | `[wxFileExists, wxID_ANY]` | **exact** |
| `Shapes` (`a.b.c` shape) | `[deep, direct, nested, plain]` | `[direct, nested, plain]` | `deep` dropped — **BUG-430, deliberate** (§4.4a) |
| `wxFrame` (`@class` on a `local`) | **unresolvable** | `[]` | **exact** — see below |
| `AllColon` (`@class` on a `local`) | **unresolvable** | `[]` | **exact** |
| `Derived` (`@class D : Base` on a bare global) | `[ownFn]` | `[ownField, ownFn]` | **superset by one** — see §4.5a |

`wxFrame` and `AllColon` returning `[]` is not a regression: `crossFileGlobalMembers` resolves a
**bare name through `resolveGlobal`** (`LuaCompletionContributor.kt:133-139`), and a `@class` on a
`local` is not a global. Today it returns `emptyMap()` for these; so does the index rule. Those
receivers reach members through the *type graph* in the contributor's `else` branch, which this
feature does not touch. An earlier draft would have used the union here, which DR-14 shows returns
their full member list — i.e. it would have invented members at a call site that offers none today.

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

It lives in the **contributor**, which already has the anchor and already handles both element types
(§4.5b):

```kotlin
// LuaCompletionContributor, replacing crossFileGlobalMembers' single return
val membership = LuaReceiverMemberIndex.globalMembership(nameRef.text, nameRef.project,
                                                         nameRef.containingFile?.originalFile)
if (membership.authoritative) {
    emitIndexed(membership.members)                       // §4.5b's index arm — syntactic filter
} else {
    emitGraph(LuaGraphType.materialize(                   // §4.5b's else arm, VERBATIM
        LuaTypeManager.getInstance(nameRef.project).resolveGlobal(nameRef.text, nameRef) ?: return,
        nameRef).getMembers())
}
```

So no `VariableNode → LuaReceiverMember` conversion is ever needed, the semantic `isColon` filter and
the type text are preserved on the fallback path, and the two arms §4.5b already specifies are
exactly the two arms this needs.

**The latency fork, named rather than routed around.** A non-authoritative receiver pays the full
graph build (§1.9: 746 ms cold). That is a **two-tier contract**, and `non-functional.md` must say so
— see §4.12 — rather than the gate quietly picking a fast-path fixture. COMP-09-08 asserts the budget
on an authoritative receiver **and** records the non-authoritative cost, and the checklist asks a
human whether the slow tier is noticeable.

**Residual, stated**: a receiver bound through `require` or a call keeps today's cost and today's
membership. The feature makes syntactically-declared members fast — which is every generated
definition library, TARGET-10 included — and changes nothing else.

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

**And it settles B5.** COMP-09-03 scopes in "inherited members". DR-14 measured that today's
**completion door does not inherit at all** — `Derived.` offers `[ownFn]`, not `Base`'s members —
while the `@class` door does (`LuaGraphType.kt:179` `putAll(superType.getMembers())`). So a flat
index list is not a regression on this path. Inheritance is served by the `@class` door, which §4.6
leaves on the graph. COMP-09-03's inherited-members clause is therefore satisfied **by not changing
that path**, and requirements.md now says so instead of implying a supertype walk that no section
designs.

### 4.5b The emit loop — B3, the fork the previous draft glossed

"The emit loop keeps its shape" was wrong. `LuaCompletionContributor.kt:374-388` is **shared** by two
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

### 4.12 The latency contract becomes two-tier — the premise this feature moves

`non-functional.md` states one flat time-to-first budget for every receiver. §4.5c does not meet it
for a non-authoritative receiver and cannot: that path is today's graph build by construction.

An earlier draft handled this by having COMP-09-08's gate "assert against a receiver on the fast
path" — i.e. by choosing a fixture that passes. That is a constraint worked around by fixture
selection rather than examined, which is exactly what the premise axis exists to catch, and Step 9
round five caught it.

**The premise is therefore moved, not dodged.** `non-functional.md` gains a two-tier statement:

> Time-to-first-result is **< 100 ms for a receiver whose members are syntactically declared** — the
> case every generated definition library falls into. A receiver bound through `require`, a call, or
> any expression the index cannot see through resolves through the type graph and is **not** covered
> by that budget; its cost is whatever the declaring file's graph build costs. Narrowing that tier is
> COMP-09's DR-07 follow-up, not this feature.

COMP-09-08 gates tier 1 and **records** tier 2 rather than silently excluding it, and checklist
scenario 1.5 asks a human whether tier 2 is noticeable in practice.

### 4.6 Consumer 2 — materialization (`addMethodsOf`)

Signature unchanged; only how candidate keys are found changes. Note this door uses `allScope`
directly (BUG-399, `:441`) — it is not the global-resolution door and does not inherit §4.5's
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

`collectMethodMembers` and `materializeUnhostedClass:328` drop their `getAllKeys` argument. Four
behaviours preserved verbatim, each a test:

| rule | where it lives today | how it survives |
| :-- | :-- | :-- |
| `allScope`, not projectScope (BUG-399) | `:441` | same scope passed to `membersIn` |
| first-wins within a receiver | `:445` | unchanged `containsKey` guard |
| file confinement for a local-declared class (BUG-398) | `:456` | unchanged `scan.onlyIn` filter |
| nested qualifiers are not members | `memberNameOf:467` | derived at index time (§4.3) |

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
   (`:118-121`), `Stringable` (`:126`), `Lengthable` — and **there is no aggregate**. The union must
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

### 4.8 Registration and reindex boundaries

```xml
<fileBasedIndex implementation="net.internetisalie.lunar.lang.indexing.LuaReceiverMemberIndex"/>
```

Already added beside the five existing entries (`plugin.xml:668`) for DR-09; nothing consumes it yet.
No new service, no EP.

| index | today | after |
| :-- | :-- | :-- |
| `LuaReceiverMemberIndex` | 1 (DR-09) | 1 |
| `LuaMemberFieldIndex` | 1 | unchanged — not modified |
| `LuaGlobalAssignmentIndex` | 2 | unchanged |
| stub format (`LuaFileElementType.getStubVersion`) | 4 | **unchanged** |

The stub row is the payoff, and Step 9 confirmed the argument: the sink stores the whole `FUNC_NAME`,
so `C:m` is itself a key. Deriving receivers in a *new* `FileBasedIndex` leaves the dot-only stub sink
alone — DR-06's asymmetry is sidestepped rather than fixed in place, at no stub-format cost.

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
  | `resolveGlobal` | **null** — guarded at `LuaTypeManagerImpl:129` |
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
| 2 | time-to-first for a 3-member receiver and a 3 600-member receiver are within a stated factor | **red** — 40x |

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
that band is independence; outside it is not. Today's 40x is far outside any plausible floor, which
is why assertion 2 is red now.

**Assertion 1 measures the receiver on the FAST path (§4.5c).** A receiver that falls back pays the
graph build by design, so a gate that happened to pick one would certify a budget the fallback does
not meet. The gate fixture declares its members syntactically; the fallback's cost is recorded
separately, not asserted against the budget.

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

### 4.11 Still not designed

- ~~COMP-09-08, COMP-09-09~~ — **both designed** (§4.10a, §4.10b) off DR-02a and DR-11. Every
  requirement now has a design.
- **Narrowing cache invalidation** (§3.3 / DR-07) — independent, possibly a smaller win.
- **Removing the four caches** — re-measure each; a follow-up.
- **`LuaImplicitFields:76` / `LuaTypesVisitor:1349` / `catsClassTags` (`LuaTypeManagerImpl:347`)** —
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
| COMP-09-08 latency enforced | §4.10a — gate shipped (`MemberEnumerationLatencyGateTest`), both assertions shown red armed | §1.9 — cold time-to-first **746 ms** vs a 100 ms budget; the armed gate reported **1 054 ms** and a 64x member-count ratio against a 2x noise floor on the builder. DR-02a and **DR-17 both done**: assertion 2's factor is derived per run, not picked |
| COMP-09-09 work bound | §4.10b — four assertions off a `processValues` callback count; `filesVisited` pinned at 1 **and** 2 | §4.10b (DR-11) — `entriesTraversed` is 50 for a 50-member receiver and **unchanged** by 4 000 unrelated keys; §4.0's 0 ms vs 2 ms is the timing corroboration |
