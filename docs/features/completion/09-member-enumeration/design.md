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

### 1.2 The cost is per-keystroke, not per-session (DR-02b/c)

| | ms |
| :-- | --: |
| cold | 800 |
| warm, no PSI change | **0** |
| after one keystroke **in the consumer file** | **608** |

(Constants-only fixture, hence 800 ms not 9 568 ms; the **ratio** is the finding — 76 % repaid.)

`typeCache` (`LuaTypeManagerImpl:34-44`) and the per-file snapshot (`LuaTypes:215-222`) both depend
on project-wide `PsiModificationTracker`, so an edit anywhere invalidates the *library's* snapshot.
Typing `wx.wxF` therefore pays the full graph build on each character. The NFR's per-keystroke clause
was right, but the mechanism is cache invalidation forcing recomputation — not, as it stated,
cancellation and restart.

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

**`getAllKeys` over 25 335 keys costs 44 ms.** The scan is *cheap*. What costs is
`getElements` — 296 ms for 200 elements, ~1.5 ms each, because each one deserialises a stub. And
`collectMethodMembers` calls `getElements` **per matching key**, so for a 3 600-member receiver that
is seconds. My earlier framing — "`getAllKeys` is the defect" — was wrong in the same way as §1.1:
inferred from the call shape, refuted by running it. COMP-09-09's work bound is still right, but the
work it must bound is **stub loads**, not key visits.

**Which gives the design its answer.** 340 ms is 28× better than 9 568 ms and still over the 100 ms
budget, because it loads stubs. Names must come from the index *value*, not from the elements:

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

## 1.7 DECISION — no incremental yield; lazy type rendering instead

COMP-09-04 asked for incremental yield. **Withdrawn**, because the measurements moved the bottleneck
and the requirement named the mechanism for the *old* one.

| what completion needs | cost after the §4 fix | consequence |
| :-- | :-- | :-- |
| member **names** | ~ms — keyed index lookup, no stub load, no graph | emit the whole set in one batch |
| member **types** | ~1.5 ms each (measured, §1.5) | 3 600 members ⇒ **5.4 s** if computed eagerly |

Two different mechanisms address two different problems:

- **Incremental yield** — the contributor emits elements over time and the popup grows. Solves *names
  are slow to find*. That problem **ceases to exist** once names come from an index value.
- **Lazy rendering** — every element is emitted at once, but each one's presentation is computed on
  demand. `LookupElement.renderElement` is called per **visible row**, so ~15 rows cost ~22 ms rather
  than 5.4 s. Solves *per-element detail is slow*, which is the problem that remains.

So the need COMP-09-04 pointed at is real; its mechanism was wrong. It is replaced by **COMP-09-04b**,
which is also the smaller change: `LuaMemberLookup.create` currently takes `memberType: LuaGraphType`
eagerly and calls `withTypeText(memberType.displayName())`. Lazy rendering is a `LookupElement`
subclass overriding `renderElement` — contained in `LuaMemberLookup`, not in the contributor's control
flow.

**Why lazy rendering is safe here.** Prefix matching keys off the lookup *string*, not the
presentation, and these elements carry a fixed `PrioritizedLookupElement.withPriority(element, 100.0)`
— so neither filtering nor sorting forces a render of the whole set. Had sorting needed type
information, lazy rendering would not work and streaming would be back on the table.

**The one unmeasured premise, and what would reverse this.** "Index value lookup is ~ms" is *inferred*
from `getAllKeys` over 25 335 keys costing 44 ms (§1.5): a keyed lookup returning values should beat a
full scan. It has **not** been measured for a value-carrying index, because that index does not exist
yet. If Phase 1 measures it slow, names become slow to find again and **COMP-09-04 is reinstated**.
That is the single number that reopens this decision; nothing else in §1 does.

## 2. Consequences for the plan

| Was | Now |
| :-- | :-- |
| "Replace the two `getAllKeys` scans" is the first increment | Those scans are inside a 10 ms region. Real, but not the headline. Demoted to a work-bound fix (COMP-09-09), not a latency one |
| Fix direction is index-backed member enumeration | Fix direction is **avoiding the declaring file's whole-graph build** to answer one symbol's type. Enumeration is already fast |
| Swapping the scan is a strict simplification | It is a correctness regression until `indexStub` also sinks `substringBefore(':')` — a **stub index format change**: version bump, full reindex |
| Per-keystroke concern justified by cancel/restart | Justified by measured cache invalidation: 76 % of the cost repaid on an unrelated edit |

## 3. Open questions this design cannot yet answer

Deliberately unresolved — each needs a measurement that has not been taken, and inventing answers
here is what produced §1.1 and §1.3.

1. ~~Can a global's type be answered without `forFile`?~~ **Answered, §1.5.** Not the type — but the
   member *names* can, from an index value, which is what completion needs.
2. ~~Is the `@class` path dominated by `forFile` or by the `getElements`-per-key loop?~~
   **Answered, §1.6.** Neither — it never calls `forFile`, and its cold cost is dominated by the
   declaring file's AST parse. Different bottleneck, same remedy.
3. **Does narrowing invalidation help more than indexing?** If the library snapshot survived edits to
   unrelated files the cost would be once per session per file rather than 76 % repaid per keystroke
   (§1.2). Possibly a smaller change than an index; not obviously safe.
4. ~~Where does incremental yield apply?~~ **Decided, §1.7 — nowhere.** COMP-09-04 withdrawn,
   COMP-09-04b (lazy type rendering) added. Reopens only if a value-carrying index lookup measures
   slow in Phase 1.

## 4. Fix direction, as far as measurement supports it

1. **`LuaMemberFieldIndex` gains a receiver key with the member name as its value**, covering dotted
   assignments. Enumeration becomes a key lookup returning strings.
2. **`LuaFuncStubElementType.indexStub` also sinks `substringBefore(':')`**, so colon-declared
   methods gain a receiver key (§1.3). Stub format change: version bump, full reindex, and a boundary
   no benchmark may cross.
3. **The enumeration callers read names from the index**, and resolve types only where a type is
   actually required — the checker via today's `forFile`, completion lazily or not at all.
4. **The `getElements`-per-key loops** in `collectMethodMembers` / `materializeUnhostedClass` are
   replaced by the same lookup. Bounded by COMP-09-09, restated: bound **stub loads**, not key visits
   (§1.5).

Not yet supported by measurement, and therefore not designed: whether (3) needs incremental yield
(§3.4 — likely withdrawable), and whether narrowing invalidation would beat indexing (§3.3).

## 5. Requirement coverage

Now writable: §3.1 and §3.2 are answered, and both doors resolve to the same remedy.

| Requirement | Covered by | Evidence |
| :-- | :-- | :-- |
| COMP-09-01 receiver-keyed enumeration | §4.1, §4.2 | §1.5 — names from an index value at key-lookup speed; §1.3 — the colon form needs its own key |
| COMP-09-02 no full-file walk | §4.3, §4.4 | §1.6 — the walk and the parse are the `@class` door's cost |
| COMP-09-03 all sources, dot **and** colon | §4.1, §4.2 | §1.3, §1.4 — `AllColon` enumerates 2 today and 0 under a naive swap |
| ~~COMP-09-04~~ incremental yield | **withdrawn** | §1.7 — right need, wrong mechanism |
| COMP-09-04b lazy type rendering | §4.3 + `LuaMemberLookup` | §1.5 — types cost ~1.5 ms each; `renderElement` is per visible row |
| COMP-09-05 `@class` metamethods | not yet designed | COMP-04-DR-01 / BUG-426; Gap 2.3 says decide after the index shape is fixed, which §1.5 now fixes |
| COMP-09-06 no new type source | §4.3 | the split — names for completion, `forFile` retained for the checker — is what keeps the checker's inputs unchanged |
| COMP-09-07 behaviour-preserving | DR-01 golden | §1.4 — both doors per receiver, colon methods included |
| COMP-09-08 latency enforced | requirements NFR-3 | §1.6 shows even the warm-file `@class` path is 167 ms, i.e. over budget without a library involved |
| COMP-09-09 work bound | §4.4 | §1.5 — bound **stub loads**, not key visits: `getAllKeys` is 44 ms for 25 335 keys |
