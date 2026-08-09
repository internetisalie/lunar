---
id: "COMP-09"
title: "09: Member Enumeration"
type: "feature"
status: "in_progress"
priority: "high"
parent_id: "COMP"
folders:
  - "[[features/completion/requirements|requirements]]"
---

# COMP-09: Member Enumeration

## Overview

Lunar can answer **"given a name, where is it declared?"** from an index. It cannot answer
**"given a receiver or type, what are its members?"** — that question is served, everywhere it is
asked, by scanning every key in an index or walking a whole file's PSI. Extends
[COMP-04](../04-type-inferred-completion/requirements.md), which owns type-inferred member
completion and already reaches into the type engine to do it.

The gap is invisible at small scale and was correct for every input that existed: the largest
bundled stub is a few KiB, the whole `runtime/` tree is 424 KiB across 78 files, and the largest
fetched definition library is a 97 KiB tarball. [TARGET-10](../../target/10-wxlua-definition-libraries/requirements.md)
generates a 230 KiB declaring file and the cost becomes 12.9 s to first completion (BUG-429).

## Why this is a capability, not a defect

It has been solved **locally, repeatedly, by unrelated changes** — the signature of a missing
capability rather than a run of bugs. Every row below is a separate workaround already in the tree:

| Workaround | Where | What it papers over |
| :-- | :-- | :-- |
| Cached `getAllKeys` (§2.5.5) | `GlobalSymbolRankingService:51-72` | "was scanned on every completion invocation (twice per session)" — its own comment |
| `typeCache` `CachedValue` | `LuaTypeManagerImpl:34` | re-materializing a class, including its full key scan |
| Two more `CachedValue`s | `LuaTypeManagerImpl:47,59` | further enumeration results |
| Per-file `CachedValuesManager` (MAINT-30-02) | `LuaTypes:204-215` | rebuilding the per-file type graph |
| Receiver keys added to the stub sink | `LuaFuncStubElementType:69-75` | "also index the base 'cjson' to allow basic resolution of the module/table global" — the right direction, added for one case, then not used by the scan that needed it |
| `resolveGlobal` index-backed | BUG-395 | the *lookup*; its members were left eager |
| Two-phase project/all scope | BUG-427 | index ordering, not enumeration — but it made the path two queries |

Four caches, one half-built index direction, and no single answer to the question.

## Scope

### In Scope

- One indexed answer to "members of X", covering **all four declaration sources** (§ Detailed
  Specifications): dotted assignments, dotted function declarations, `@class` fields and inherited
  members, and metamethods.
- Replacing the brute-force scans and file walks listed in COMP-09-01/02 with it.
- ~~**Incremental yield**~~ — **withdrawn 2026-08-07** (COMP-09-04, design §1.7) and now confirmed by
  measurement: DR-02a put the gap between the first element and the last at **31 ms of 777 ms**, so
  there is no long tail to stream. Time-to-first stops equalling time-to-exhaustive by making *both*
  fast, not by splitting them.
- Closing **COMP-04-DR-01** and BUG-426's "Known limitation" — `@class`-declared metamethods.

### Out of Scope

- **Navigation.** Lookup-by-qualified-name is already complete between
  `LuaGlobalDeclarationIndex` (which covers `function wx.wxFrame()`) and `LuaMemberFieldNavigation`
  (which covers `wx.wxField = v`), both run by `LuaNameReference:95-111`. This feature consumes
  NAV-12's index; it does not fix a navigation defect, and no navigation regression test should be
  written expecting one.
- **Legitimate full-key enumeration** — Go-to-Symbol, Search Everywhere and hierarchy genuinely need
  every key. `LuaCatsTypeNavigation.processNames`, `LuaGotoSymbolContributor`,
  `LuaDocSearchEverywhereContributor` and `LuaHierarchyUtil` are correct as they stand.
- **Widening what the checker sees.** BUG-395 tried wiring cross-file globals into `visitNameRef`
  and reverted it: "handing the checker types it never had turns previously-unchecked calls into
  checked ones and regressed four suites at once" (BUG-397). Enumeration must not become a new type
  source. This is the hardest constraint in the feature.
- Removing the existing caches. They stay until measurement says they are redundant.

## Functional Requirements

| ID | Requirement | Priority | Description |
|----|-------------|----------|-------------|
| COMP-09-01 | **Receiver-keyed member enumeration** | M | "Members of `X`" is answered by an index lookup, not a key scan. |
| COMP-09-02 | **No full-file walk on the member path** | M | Enumeration narrowed to files must not then walk each file's whole PSI. **Scoped to the sites design §4.3/§4.6 replace.** The `catsClassTags` / `LuaImplicitFields` / `LuaTypesVisitor:1349` walks are excluded because **§4.3 and §4.6 do not touch them and they are not on this feature's measured critical path** — *not* because they were shown to be cheap. An earlier revision justified this with "22 ms of a 949 ms cold path"; that is **retracted** (§1.8 re-measured the denominator at 383 ms, the numerator is single-shot, and it was taken on the wrong door for one of the three sites). Their cost is **DR-16** (design §4.11). |
| COMP-09-03 | **All four declaration sources, dot *and* colon** | M | Dotted assignments, function declarations in **both** `ns.f` and `C:m` form, `@class` fields, metamethods. **Inherited members stay on the `@class` door** and are not indexed: DR-14 measured that today's *completion* door does not inherit at all (`Derived.` offers `[ownFn]`, not `Base`'s members), so a flat index list is not a regression there; `LuaGraphType.kt:179` continues to merge supertypes for materialization (design §4.5a). The colon form is called out because it is the one the existing receiver key omits (DR-06). |
| ~~COMP-09-04~~ | ~~**Incremental yield**~~ | — | **WITHDRAWN 2026-08-07.** It names the wrong mechanism — see COMP-09-04b and design §1.7. |
| ~~COMP-09-04b~~ | ~~**Lazy member-type rendering**~~ | — | **WITHDRAWN 2026-08-07 after Step 9 review.** Measured: 4 ms (median of 5) for 3 700 members' types + `displayName()`. Presentation was never on the critical path, and `renderElement` is *not* per-visible-row — that is `getExpensiveRenderer`. Design §1.7. |
| COMP-09-05 | **`@class`-declared metamethods** | S | A `---@class` declaring `__add` makes its instances arithmetic-capable — closes COMP-04-DR-01 / BUG-426. |
| COMP-09-06 | **No new type source** | M | The checker sees exactly what it sees today. Corpus baselines must not move. |
| COMP-09-07 | **Behaviour-preserving against the `@class` door** | M | Same members, same types, same completions as today on every existing fixture — **except** where the two doors disagree, which DR-09 measured and BUG-430 records. `resolveType` → `materializeClass` is the door preserved; the global door's flattening of `a.b.c = v` is deliberately **not** reproduced. An unqualified "same as today" is unsatisfiable: two goldens exist for one receiver and one of them is a defect. Every golden entry names its door. |
| COMP-09-08 | **The latency target is enforced** | M | A failing-first test asserts time-to-first-element against the `non-functional.md` budget, and runs in the routine loop — not behind `-PwithPerf`. |
| COMP-09-09 | **The work bound is enforced** | M | **Entries traversed** per enumeration is instrumented and asserted proportional to matching entries; adding unrelated indexed content must not increase it. (An earlier revision restated this as "bound stub loads" on the strength of a mismeasured `getAllKeys` figure — reverted, design §2.) |

## Detailed Specifications

> **De-risking complete 2026-08-07 — see [design.md §1](design.md). Two claims below were refuted by
> measurement and are corrected there: the critical path is `resolveGlobal` →
> `LuaTypesSnapshot.forFile` (9 568 ms), not `materialize` (10 ms); and the receiver-key swap is a
> correctness regression, not a simplification, because the sink is dot-only.**

### COMP-09-01: the sites to replace

| Site | Shape |
| :-- | :-- |
| `LuaTypeManagerImpl:328` (`materializeUnhostedClass`) | `getAllKeys(LuaGlobalDeclarationIndex)` → `addMethodsOf`; the `@class` path |
| `LuaTypeManagerImpl:421,424` (`collectMethodMembers`) | same, fetched **per class materialized** |
| `LuaCompletionContributor:133-139` (`crossFileGlobalMembers`) | `materialize(global).getMembers()` — full graph before the first element |

`LuaGlobalDeclarationIndex` is **already receiver-keyed** (`LuaFuncStubElementType:69-75` sinks both
the qualified name and `substringBefore('.')`), so the first two are brute-forcing a query the index
already answers **for the dot form only**. Measured (DR-06): `getElements(KEY, "ColonHost")` returns
`[ColonHost.staticDot]` — the colon-declared `ColonHost:dotless` and `ColonHost:scale` are absent,
because `LuaFuncStubElementType:69-75` sinks a receiver key only when the name contains `'.'` while
`memberNameOf:466` matches both separators. Swapping the scan as-is **drops every colon-declared
method**, and `function C:m()` is the dominant idiomatic form. Worse, the `ColonHost` receiver key
exists only because one member happens to use the dot form — a wholly colon-declared class has no
receiver key at all.

So this is **not** a strict simplification and **not** the first increment. It requires `indexStub`
to also sink `substringBefore(':')` — a stub index format change, hence a version bump and a full
reindex, which is also a second boundary no benchmark may cross. And per DR-02 these scans sit inside
a 10 ms region, so they are a **work-bound** fix (COMP-09-09), not a latency one.

### COMP-09-02: the sites narrowed-then-walked

| Site | Shape |
| :-- | :-- |
| `LuaTypeManagerImpl:347` (`catsClassTags`) | right files via `getContainingFiles`, then `findChildrenOfType(LuaCatsClassTag)` over each |
| `LuaMemberFieldNavigation:32` | right files, then `findChildrenOfType(LuaAssignmentStatement)` |
| `LuaImplicitFields:76` | `findChildrenOfType(LuaAssignmentStatement)` over each supplied file |
| `LuaTypesVisitor:1349` | `findChildrenOfType(LuaAssignmentStatement)` over every stub `global.lua` |

The last is on the definition-library path and runs during graph construction.

### COMP-09-03: the four sources, and why an index over one is not enough

`LuaMemberFieldIndex` covers dotted **assignments** only (`LuaAssignmentStatement`, `:68-72`), so
`wx.wxID_ANY = nil` is indexed and `function wx.wxFrame() end` is not. The function form is covered
by `LuaGlobalDeclarationIndex`. `@class` fields are in neither. Metamethods are in no index at all
and have no cross-file path — only the per-file graph from `setmetatable`.

## Test Cases

| # | Requirement | Given | When | Then |
|---|---|---|---|---|
| 1 | COMP-09-01 | A 230 KiB library root declaring ~3 400 `wx.*` members, receiver **authoritative** (§4.5c) | `wx.<caret>`, measure **time to first element**, median of ≥5 cold | **< 100 ms** (`non-functional.md`, tier 1 — design §4.12). Today: **746 ms** (design §1.9). *(An earlier revision said "the budget set by DR-02"; DR-02 is superseded and set no budget.)* |
| 2 | — | *(withdrawn with COMP-09-04b — the premise it tested was false)* | | |
| 2a | NFR-1 | A 3-member receiver and a 3 600-member receiver, **each in its own file**, both cold | Compare time-to-first-element | Comparable. Today 41 ms vs 1 641 ms — 40x (design §1.9), which the NFR's independence clause forbids. The narrow-vs-broad *prefix* pair once cited here is withdrawn (design §1.8) |
| 3 | COMP-09-07 | Every existing definition/completion fixture, plus the DR-01 golden | Run the suite | Identical member sets and types to today, recorded for **both** `resolveGlobal` and `resolveType` per receiver — `wx` answers differently through each (design §1.4), so a one-door golden would miss a change to the other |
| 3a | COMP-09-07 | The `AllColon` fixture — a `@class` whose every member is colon-declared | Enumerate | 2 members. Today's scan returns them; the proposed receiver-key swap returns 0 (design §1.3/§1.4). This is the regression guard |
| 4 | COMP-09-06 | All four corpus members | Re-baseline | `LuaTypeAssignability` / `LuaReturnTypeMismatch` unchanged |
| 5 | COMP-09-03 | Library declaring `wx.K = nil`, `function wx.F() end`, `---@class C` with a field, **and `function C:m()`** | `wx.<caret>`, `C` instance caret | All four forms enumerate. Without the colon case a fix passes every other TC while losing class methods (DR-06) |
| 5a | COMP-09-03 | A `---@class D` whose members are **all** colon-declared — no dot member anywhere | `D` instance caret | Members enumerate. Today `D` has no receiver key at all, so this is the sharpest form of DR-06 |
| 6 | COMP-09-05 | `---@class V` declaring `__add`; `local a, b = V(), V()` | `a + b` | No diagnostic (closes COMP-04-DR-01) |
| 9 | COMP-09-09 | The TC 1 fixture, then the same fixture plus an unrelated 200 KiB library | Instrument entries traversed for `wx.<caret>` in both — the **completion** door, so the counter must cover `membersOfGlobal`, not only `membersIn` (design §4.10b) | The count is unchanged — enumeration must not visit the added library's entries. Fails today: `getAllKeys` visits every key |
| 8 | COMP-09-08 | The TC 1 fixture, on today's code, on a receiver that takes the **fast** path (§4.5c) | Run the new latency test | It **fails**, reporting **746 ms** time-to-**first** against a 100 ms budget (design §1.9) — mutation-proving the gate before the fix lands. *(An earlier revision said ~12 900 ms; that is time-to-**exhaustive**, which is not what this gate measures.)* |
| 7 | COMP-09-02 | The `wx` fixture | Enumerate through `membersIn`, counting `processValues` callbacks | `entriesTraversed >= membersIn(R).size` (and `==` when no name repeats across declaring files — design §4.10b assertion 1) and does not move when 4 000 unrelated keys are added (design §4.10b). **Scoped**: the `catsClassTags` / `LuaImplicitFields` / `LuaTypesVisitor` walks are excluded — they are out of scope because §4.3/§4.6 do not touch them, not because they were measured cheap — the old 22 ms/949 ms justification is retracted, and their cost is DR-16 (design §4.11) |
| 7a | COMP-09-07 | `wx`, `wxFrame`, `AllColon`, `Shapes` | Enumerate through **both** doors, never `resolveGlobal(r) ?: resolveType(r)` | The golden records each door separately. Two of the four resolve through only one door, so a collapsed golden is silently door-dependent (design §4.4a) |
| 7b | COMP-09-01 | A `@class` on a **local** (`---@class wxFrame` / `local wxFrame = {}`) | `membersOfGlobal("wxFrame", …)` | `[]` — matching today's `crossFileGlobalMembers`, which returns `emptyMap()` because a local is not a global. The union would return its full member list, inventing members at a call site that offers none (design §4.5) |
| 7c | COMP-09-03 | `---@class Derived : Base` / `---@field ownField number` / `Derived = {}` | `Derived.<caret>` | `ownField` **is** offered. This is a deliberate new member on the completion path (design §4.5a) — today it is absent — and must be an expectation, not a silent diff |
| 6b | COMP-09-05 | `---@class D : Base` where `Base` declares `__add` | `D() + D()` | No diagnostic. `getMembers()` merges supertypes, so no separate supertype walk is needed (design §4.7) |

## Acceptance Criteria

- [ ] COMP-09-01/02 — every site in the two tables above is converted or explicitly justified.
- [ ] COMP-09-03 — TC 5 passes for all four sources.
- [ ] ~~COMP-09-04b~~ — withdrawn; nothing to verify.
- [ ] COMP-09-05 — TC 6; COMP-04-DR-01 and BUG-426's limitation are closed or re-scoped in writing.
- [ ] COMP-09-06 — TC 4. **If any baseline moves, enumeration has become a type source: stop.**
- [ ] COMP-09-07 — TC 3.
- [ ] COMP-09-08 — the latency assertion exists, fails before the fix, passes after, and runs
      without `-PwithPerf`.
- [ ] COMP-09-09 — the work bound is instrumented and asserted (TC 9); adding unrelated indexed
      content does not change entries traversed.
- [ ] Each cache in the "Why this is a capability" table is re-measured and either removed as
      redundant or kept with a stated reason.

## Non-Functional Requirements

**The target already exists and this feature is 129× outside it.**
[`non-functional.md`](../../non-functional.md) — amended alongside this feature — sets
**time-to-first-result under 100 ms, independent of index size**, and deliberately sets *no* budget
for the exhaustive set, because that scales with **index entries traversed**.

Measured today: **12 902 ms** against a 230 KiB library root, ~~25 352 ms~~ *(withdrawn — single-shot,
and time-to-exhaustive; see below)* against a 530 KiB single
file, **297 ms** against 40 KiB of constants. That entries rather than results dominate is measured,
not assumed. ⚠ **The narrow-vs-broad pair once quoted here (18 429 ms vs 25 352 ms) is WITHDRAWN**
— single-shot, time-to-*exhaustive*, and a 38 % gap inside this harness family's demonstrated ±60 %
spread (design §1.8). The claim now rests on design §1.9's cold narrow-vs-wide pair (41 ms for 3
members, 1 641 ms for 3 600) and §4.10b's entry counts, both of which are about **entries**.

**⚠ Every number above is time-to-EXHAUSTIVE, and time-to-first-result has never been measured.**
`myFixture.completeBasic()` returns only when completion finishes, so the Phase 0 harness could not
observe a first element. Today the two are almost certainly equal — the eager `materialize` precedes
any `addElement` — but "almost certainly" is not a measurement, and the *binding* target is the one
with no data behind it. DR-02 must therefore build a harness that can observe the first element (a
`CompletionResultSet` wrapper or a `LookupListener`), not reuse the Phase 0 timings.

`GlobalSymbolCompletionPerformanceTest`'s KDoc adds per-scale targets (*"<250ms at 1000 symbols"*,
*"<400ms at 5000 symbols"*).

- **NFR-1 — time to first element < 100 ms** against the COMP-09 TC 1 fixture, independent of index
  size. Not a new budget: the amended existing one, applied to the case that breaks it.
- **NFR-2 — exhaustive work is proportional to entries matching the receiver, not to index size.**
  Verified by instrumenting entries traversed, not by a clock. This is the target today's code
  violates outright, and the one that does not vary with the machine.
- **NFR-2b — exhaustive latency carries no fixed figure, deliberately.** 12.9 s to the *complete* set
  is acceptable; 12.9 s to the *first* result is the defect. A "fix" that speeds the exhaustive set
  while leaving first-result behind it has not addressed this feature.
- **NFR-2c — cancellable inside the traversal, and off the EDT.** Typing `wx.wxF` restarts enumeration
  five times; cancellation makes that survivable, not free. *Incremental* is retained here as a
  property the implementation must not **break**, not a feature it must add — COMP-09-04 is withdrawn
  (design §1.7).
- **NFR-2d — the non-incremental consumers bound the real cost.** The checker, the corpus sweep and
  documentation rendering need the complete set, so for them exhaustive time *is* user-facing.
  NFR-2's work bound is what protects them; NFR-2b's absence of a latency figure is not permission.
- **NFR-3 — the target becomes enforced, not documented.** See COMP-09-08.
- **NFR-4 — threading unchanged**; enumeration runs where it runs today
  (`docs/engineering-contract.md` §1).

### Why 12.9 s was never caught, and what that requires of this feature

Every timing assertion in the performance suite is `assertTrue(result.phase1Time > 0, "Benchmark
should record a time > 0")`, with comments that say so — *"Informational: capture target scale
data"*. It is an honest benchmark harness, not a gate, and it is excluded from the routine loop
(`-PwithPerf`). So no stated target has ever been enforceable, and a 129× miss was invisible.

**Fixing the enumeration without fixing that leaves the next 129× regression equally invisible.**
Hence COMP-09-08 below.

### The spec hole — name it, do not exploit it

The 100 ms target is qualified *"for projects up to 50k lines"*. A definition library is not project
lines, so the NFR does not literally bind for library trees — which is how a 530 KiB root can be
both catastrophic and technically in-spec. `non-functional.md` must be amended to cover **indexed
library content**, not only project content. Leaving the qualifier in place and declaring compliance
would be true and useless.

## Status against the planning bar

**Six Step 9 reviews were run. Every blocker was verified against the code before being acted on**,
and three of them were verified by *measurement* rather than by reading, which is how two accepted
remedies turned out to be wrong.

### The arc, because the failure mode changed each round

| Round | What it found | Character |
| :-- | :-- | :-- |
| 1–2 | §4 written from reading; D1/D2/D3 | design was fiction |
| 3 | 9 blockers. §4.5 read off the **tail** of a call chain whose head names a different index | design was fiction, subtler |
| 4 | 9 blockers. **Every golden fixture shared one binding shape** (`R = {}` + separate members) — the one shape in which the index and the graph agree | the *evidence* was too narrow |
| 5 | 6 blockers. Round 4's remedy fails on `M = { VERSION } + function M.f()`, and its harness asserted `today == today` | the *remedy* was wrong and its test could not fail |
| 6 | 5 blockers, **all internal-consistency** — corrections landed in `design.md` and not in the four documents an implementer reads | the design is sound; propagation was not |

### Round 5 → 6, and the design that survived

BL-2 (round 4): the index sees a member only if it is written syntactically against the receiver
name, so `Config = { host, port }` returned `[]` where today's global door returns both.

Three remedies, **two measured wrong before the third worked** (DR-19, design §4.5c):

1. *fall back when the index is empty* — loses `VERSION` from `M = { VERSION } + function M.f()`,
   because source 1 makes the index non-empty. Its harness computed
   `if (indexed.isNotEmpty()) indexed else today` and asserted `== today`: for the receivers it
   certified, `today == today`.
2. *also index table-literal fields* — fixes that, not `OM = require(x) + function OM.extra()`.
3. **a binding-opacity sentinel** — the index says whether it is authoritative. All five binding
   shapes match today, including both counterexamples.

Round 6's five blockers were then all propagation: `requirements.md` still carried remedy 1 as the
fix and a retracted "22 ms of 949 ms" justification; `implementation-plan.md` Phase 2 still asked for
a signature that cannot express the non-authoritative branch; `design.md` §4.10b assertion 4 had a
clause that goes red on a correct implementation; and the sentinel's scope was written "top level"
where the measured code is any-depth. All closed.

### The lesson worth carrying into Phase 0

**A golden is only as good as the binding shapes in it.** Three DR rounds each missed a defect
because their fixtures varied *member style* (dot, colon, `@field`) while holding *binding shape*
fixed. Phase 0 now carries a standing item listing the eight shapes the golden must contain, and
adding a shape is the cheap move — adding another member style is not.

### Open, tracked, not blocking

DR-16 (re-measure the descoped walks — the scope decision does not depend on the answer), DR-17
(assertion 2's factor, with a stated derivation rule), DR-18 (a second whole-project index's build
cost — committed to, but it should not stay `todo` past Phase 1).

## Dependencies

- **COMP-04** (`done`) — extends it; owns `LuaMemberLookup` and the `setmetatable` modelling.
- **NAV-12** (`done`) — `LuaMemberFieldIndex` is consumed, not fixed.
- **BUG-395/397/427** — the reverted-experiment constraint (COMP-09-06) comes from here.
- **BUG-429** (`superseded`) — absorbed here, not a predecessor. Its diagnosis is the reference for
  the critical path, the four reasons enumeration was never index-backed, and the `getAllKeys` audit;
  its fix sites are COMP-09-01/02. It is deliberately *not* landed separately — see its supersession
  note and COMP-09-DR-05.
- **BUG-426** — its Known limitation becomes COMP-09-05.
- **TARGET-10** — first consumer at scale; its release 2 gates on this.

## See Also

- Design: [design.md](design.md)
- Risks: [risks-and-gaps.md](risks-and-gaps.md)
