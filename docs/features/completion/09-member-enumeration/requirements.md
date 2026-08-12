---
id: "COMP-09"
title: "09: Member Enumeration"
type: "feature"
status: "done"
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
| `typeCache` `CachedValue` | `LuaTypeManagerImpl:55` | re-materializing a class, including its full key scan |
| Two more `CachedValue`s | `LuaTypeManagerImpl:67,79` (`moduleCache`, `globalCache`) | further enumeration results |
| Per-file `CachedValuesManager` (MAINT-30-02) | `LuaTypes.forFile` (`LuaTypes.kt:237`), deps from `LuaTypes.dependenciesFor` (`:281-289`) | rebuilding the per-file type graph |
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
| COMP-09-07 | **Behaviour-preserving against the `@class` door** | M | Same members, same types, same completions as today on every existing fixture — **except** where the two doors disagree, which DR-09 measured and BUG-430 records. `resolveType` → `materializeClass` is the door preserved; the global door's flattening of `a.b.c = v` is deliberately **not** reproduced. An unqualified "same as today" is unsatisfiable: two goldens exist for one receiver and one of them is a defect. Every golden entry names its door. **A second declared exception (2026-08-12): a dot/colon member-name collision resolves to the first declaration in file order.** There was no behaviour to preserve — the prior winner came from index traversal state, not from the source — so the requirement is met by *declaring* the replacement rule and pinning its determinism, not by reproducing the old answer. |
| COMP-09-08 | **The latency target is enforced** | M | A failing-first test asserts time-to-first-element against the `non-functional.md` budget, and runs in the routine loop — not behind `-PwithPerf`. |
| COMP-09-09 | **The work bound is enforced** | M | **Entries traversed** per enumeration is instrumented and asserted proportional to matching entries; adding unrelated indexed content must not increase it. (An earlier revision restated this as "bound stub loads" on the strength of a mismeasured `getAllKeys` figure — reverted, design §2.) |
| COMP-09-10 | **The shadowing rule is stated and gated** | M | **NEW 2026-08-12.** The change site (design §4.13) consults the index *before* the in-file type graph exists, which re-opens in-file-versus-global precedence. That precedence must be a **stated rule** (design §4.14, "Rule S"), derived from `LuaTypesVisitor.visitNameRef`'s `scope.lookup` rather than invented, with a test per binding form and a mutation proof. Inventing it implicitly to make a gate green is the rogue workaround the Phase 2 abort protocol forbids. |

## Detailed Specifications

> **De-risking complete 2026-08-07 — see [design.md §1](design.md). Two claims below were refuted by
> measurement and are corrected there: the critical path is `resolveGlobal` →
> `LuaTypesSnapshot.forFile` (9 568 ms), not `materialize` (10 ms); and the receiver-key swap is a
> correctness regression, not a simplification, because the sink is dot-only.**

### COMP-09-01: the sites to replace

| Site | Shape |
| :-- | :-- |
| `LuaTypeManagerImpl:389` (`materializeUnhostedClass`, called at `:330`) | `getAllKeys(LuaGlobalDeclarationIndex)` → `addMethodsOf`; the `@class` path |
| `LuaTypeManagerImpl:514` (`collectMethodMembers`; `getAllKeys` at `:519`, `addMethodsOf` at `:520,:523`) | same, fetched **per class materialized** |
| ~~`LuaCompletionContributor:133-139` (`crossFileGlobalMembers`)~~ **NOT a site to replace** | `materialize(global).getMembers()` — full graph before the first element. **Struck 2026-08-12**: the premise that this is the completion door is *withdrawn* (design §4.5's correction block). It sits behind `if (type == LuaGraphType.Undefined)`, a guard that never opens for a receiver with members, and Phase 2 was executed against it, measured and aborted. The re-plan leaves it **byte-for-byte unchanged** (design §4.13's rule 3, implementation-plan Phase 2 "Change nothing below the insertion point") and adds a new call **above** `LuaTypesSnapshot.forFile` instead. The row is kept struck rather than deleted because two reviews looked for it |

`LuaGlobalDeclarationIndex` is **already receiver-keyed** (`LuaFuncStubElementType:69-75` sinks both
the qualified name and `substringBefore('.')`), so the first two are brute-forcing a query the index
already answers **for the dot form only**. Measured (DR-06): `getElements(KEY, "ColonHost")` returns
`[ColonHost.staticDot]` — the colon-declared `ColonHost:dotless` and `ColonHost:scale` are absent,
because `LuaFuncStubElementType.indexStub` (`LuaFuncStubElementType.kt:69-75`) sinks a receiver key
only when the name contains `'.'` while `LuaTypeManagerImpl.memberNameOf`
(`LuaTypeManagerImpl.kt:562`) matches both separators. Swapping the scan as-is **drops every colon-declared
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
| `LuaTypeManagerImpl:436` (`catsClassTags`, called at `:393`) | right files via `getContainingFiles`, then `findChildrenOfType(LuaCatsClassTag)` over each |
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
| 1 | COMP-09-01 | Five 123 KiB library files, **one wide receiver each** (3 600 members), all authoritative (§4.5c) | `Wide<i>.<caret>` for `i` in 0..4, measure **time to first element**, cold; report the median of the five | **< 100 ms** (`non-functional.md` — flat, not tiered; §4.12's two-tier amendment is **withdrawn**). Prototype measured **7 414 µs** median armed against **490 995 µs** unarmed, same fixture, same run (design §1.10.2). *Five distinct receivers, not one receiver five times — a cold snapshot is warm the second time, so re-measuring one receiver reports memoization.* |
| 2 | — | *(withdrawn with COMP-09-04b — the premise it tested was false)* | | |
| 2a | NFR-1 | A 3-member receiver and a 3 600-member receiver, **each in its own file** | Compare **entries traversed** (`LuaReceiverMemberWork.entries`), not time | `3` and `3600` respectively, and **neither moves** when an unrelated 4 000-member library is added. **Restated as a count 2026-08-12** (design §4.10a-bis): the timing form of this test flips verdict between two runs of the same code — 1x (met) and 3x (not met) against the same derived 2x floor — which is DR-08's standing rule firing. Timings stay as printed records: armed narrow median 6 214 µs, armed wide median 7 414 µs (design §1.10.2) |
| 3 | COMP-09-07 | Every existing definition/completion fixture, plus the DR-01 golden | Run the suite | Identical member sets and types to today, recorded for **both** `resolveGlobal` and `resolveType` per receiver — `wx` answers differently through each (design §1.4), so a one-door golden would miss a change to the other |
| 3a | COMP-09-07 | The `AllColon` fixture — a `@class` whose every member is colon-declared | Enumerate | 2 members. Today's scan returns them; the proposed receiver-key swap returns 0 (design §1.3/§1.4). This is the regression guard |
| 4 | COMP-09-06 | All four corpus members | Re-baseline | `LuaTypeAssignability` / `LuaReturnTypeMismatch` unchanged |
| 5 | COMP-09-03 | Library declaring `wx.K = nil`, `function wx.F() end`, `---@class C` with a field, **and `function C:m()`** | `wx.<caret>`, `C` instance caret | All four forms enumerate. Without the colon case a fix passes every other TC while losing class methods (DR-06) |
| 5a | COMP-09-03 | A `---@class D` whose members are **all** colon-declared — no dot member anywhere | `D` instance caret | Members enumerate. Today `D` has no receiver key at all, so this is the sharpest form of DR-06 |
| 6 | COMP-09-05 | `---@class V` declaring `__add`; ~~`local a, b = V(), V()`~~ → **`---@type V` on each operand** | `a + b` | No diagnostic (closes COMP-04-DR-01). ⚠ **The fixture as written could not fail — CORRECTED 2026-08-12 (Phase 4).** Measured on the pre-change code, `local a, b = V(), V()` reports **nothing**: `V()` calls a class declaring no `__call`, infers `Undefined`, and `Undefined` absorbs every check. A test in that shape passes with no implementation at all — the same trap BUG-424's fixture fell into before BUG-426 landed. The asserted form binds each operand with `---@type V`, which reports `V is not assignable to number` before the change and nothing after. `MemberEnumerationMetamethodTest` |
| 6a | COMP-09-05 | The DR-12 fixture — `---@class Vec` with `---@field __add`, `---@field x` and `function Vec:len()` — declared once on a **local** and once on a **global** | `v.<caret>`, `v:<caret>`, and (global only) `Vec.<caret>`, `Vec:<caret>` | **The offered sets do not move.** `v.` = `[__add, len, x]`, `v:` = `[len]` — identical for both declaration forms and identical to DR-12's pre-Phase-2 measurement, so Phase 2's hoist did not change metamethod visibility. Asserted as **exact** sets: every other gate in this area is superset-shaped and a subset passes them all. `Vec.` = `[__add, len, x]` and `Vec:` = `[__add, len]` are also pinned; `Vec:` keeping `__add` is the index arm's *syntactic* `isColon` filter (the field's type text starts with `fun(`, so §4.3 indexes it `Kind.FUNCTION`), the same divergence TC 7e already fixes for `onClose` — **not** a metamethod-visibility change |
| 9 | COMP-09-09 | The TC 1 fixture, then the same fixture plus an unrelated 200 KiB library | Instrument entries traversed for `wx.<caret>` in both — the **completion** door, so the counter must cover `membersOfGlobal`, not only `membersIn` (design §4.10b) | The count is unchanged — enumeration must not visit the added library's entries. Fails today: `getAllKeys` visits every key |
| 8 | COMP-09-08 | The TC 1 fixture, on today's code, on a receiver that takes the **fast** path (§4.5c) | Run the latency test with `BUDGET_ENFORCED = true` | It **fails**, reporting hundreds of ms time-to-**first** against a 100 ms budget — mutation-proving the gate before the fix lands. Already discharged: shipped inverted at Phase 0, and the armed prototype flipped it to *"COMP-09-08 is now MET — cold time-to-first for `wx.` was 13 ms against a 100 ms budget"* (design §1.10.2), which is the same proof taken from both directions. *(An earlier revision said ~12 900 ms; that is time-to-**exhaustive**, which is not what this gate measures.)* |
| 8a | COMP-09-08 | The TC 1 fixture, `Opaque = require("luassert")` in its own file — the receiver §4.12 wanted to exempt | Measure cold time-to-first at the §4.13 site | **Under 100 ms, and recorded rather than exempted.** Prototype: 24 936 / 46 259 / 60 784 µs armed across three runs against 120 962 / 130 080 µs unarmed (design §1.10.3). It improves with **no tier-2-specific work** because the tier-1 receivers around it stop dragging the consumer file's snapshot through the library build. If a future run puts this over 100 ms, file a defect — do not widen the contract |
| 10a | COMP-09-10 | `shadowed.lua` declares `Shadow = {}` + `function Shadow.fromLibrary()`; consumer is `local Shadow = { fromLocal = 1 }` | `Shadow.<caret>` | `[fromLocal]`, and **not** `fromLibrary`. This is `LuaGlobalMemberCompletionTest.aLocalShadowsTheCrossFileGlobal` verbatim; it must stay green **untouched** |
| 10b | COMP-09-10 | Same library; consumer is `local function Shadow() end` | `Shadow.<caret>` | `[]` — identical to today |
| 10c | COMP-09-10 | Same library; consumer is `local function f(Shadow) … end` | `Shadow.<caret>` inside `f` | `[]` — identical to today. **This is the mutation-proof case**: delete `LuaLocalBindingScan`'s `LuaParList` clause and this must go red offering `fromLibrary` |
| 10d | COMP-09-10 | Same library; consumer is `for Shadow in pairs({}) do … end` and `for Shadow = 1, 2 do … end` | `Shadow.<caret>` inside each | Identical to today for both loop forms. **MEASURED 2026-08-12 (Phase 2):** `[end]` for both, not `[]` — the keyword provider is registered on the bare `psiElement()` pattern and an open `do` block offers `end` at any caret inside it. DR-21 recorded the same `today=[end] hoisted=[end]` (design §1.10.4). No member appears beside it, which is the assertion that matters |
| 10e | COMP-09-10 | Same library; the consumer itself writes `Shadow = { fromThisFile = 1 }`, and separately `function Shadow.here() end` | `Shadow.<caret>` | `[fromThisFile]` / `[here]` — the consumer's own binding wins, identical to today |
| 10f | COMP-09-10 | Same library; consumer binds nothing | `Shadow.<caret>` | `[fromLibrary]` — the index arm, and the only case in 10a–10f that takes it |
| 10g | COMP-09-10 | A **bundled stdlib** receiver on the default STANDARD 5.4 target, bound by `LuaTypesVisitor.freeGlobalSeed` → `resolveGlobal` off `runtime/standard/lua-5.4/table.lua:27` (`table = {}`), which the consumer file itself does not bind. **CORRECTED 2026-08-12**: an earlier revision said `seedAmbientGlobals` binds it. It does not — that function reads only files *named* `global.lua` (`LuaTypesVisitor.kt:1345`) and no `global.lua` exists under any `runtime/standard` root (design §1.10.8), so on this target the site declares nothing at all | `table.<caret>` | `[concat, insert, move, pack, remove, sort, unpack]` — identical to today, **through the index arm** (measured: the `LuaGlobalAssignmentIndex` candidate is `table.lua`, `found=true authoritative=true`, same seven members). This pins that Rule S asks only about the **consumer** file, for a receiver another file declares. It does **not** exercise `seedAmbientGlobals` — that is TC 10h |
| 10h | COMP-09-10 | A project on a real **Redis** target. `Target`'s second parameter is a `VersionEntry`, **not a `String`** — `Target(LuaPlatform.REDIS, "7+")` does not compile. Build it the way `RedisAmbientTypingTest.setRedisTarget` does: `Target(LuaPlatform.REDIS, requireNotNull(PlatformVersionRegistry.findVersion(LuaPlatform.REDIS, "7+")))`, then `LuaProjectSettings.getInstance(project).setTargetAndNotify(target)` + `PlatformLibraryIndex.reload()` inside `EdtTestUtil.runInEdtAndWait`. Its bundled `runtime/redis/redis-7/global.lua` declares `KEYS = {}` and `ARGV = {}`. This is the **only** family of targets on which `seedAmbientGlobals`'s `scope.declare` (`LuaTypesVisitor.kt:1360`) fires — Redis 5/6/7 and Valkey 7.2/8 | `KEYS.<caret>`, `ARGV.<caret>`, and the same two at `:` | `[]` for all four — identical to today. **NEW 2026-08-12.** Design §4.14 deliberately leaves `:1360` outside Rule S, and until now that omission had neither a test nor a measurement while being live on a shipped target. Measured (design §1.10.8): today offers `[]` at both doors and the index answers `found=true, authoritative=true, members=[]`, because `KEYS = {}` is an empty table literal — no member from §4.3 source 4, no opacity sentinel from source 5. Restore STANDARD 5.4 in `tearDown` or the shared light project poisons the later `lang.indexing`/`lang.types` tests |
| 10i | COMP-09-10 | The `Shadow` library; consumer is `function C:m() Shadow.<caret> end` with the receiver renamed to `self` — i.e. a library declaring `self = {}` + `function self.fromLibrary()`, and `self.<caret>` inside a method body | `self.<caret>` | `[]` — identical to today. **NEW 2026-08-12.** Rule S's `name == "self"` clause (design §4.14, `LuaTypesVisitor.kt:1414`) is the one clause with no PSI shape, so the `LuaParList` mutation proof cannot reach it. **Mutation-prove it separately**: delete the `name == "self"` early return and this must go red offering `fromLibrary` |
| 10j | COMP-09-10 | The `Shadow` library; consumer is `local Shadow = { fromLocal = 1 }` followed by `if type(Shadow) == "table" then Shadow.<caret> end` — the type-guard narrowing that re-declares an already-bound name at `LuaTypesVisitor.kt:462` | `Shadow.<caret>` inside the guard | **not** `fromLibrary` — identical to today. **NEW 2026-08-12.** §4.14's table calls `:462` "covered transitively"; this measures that claim instead of asserting it. If it comes back with `fromLibrary`, `:462` is not transitively covered and Rule S needs an eighth clause — a finding, not a failure. **MEASURED 2026-08-12 (Phase 2): `:462` IS covered** — `fromLibrary` is absent, so no eighth clause is needed. ⚠ **This row's `Then` also predicted `[fromLocal]` and that half was wrong**: the observed set is `[else, elseif, end]`, i.e. the three keywords an open `if` offers and **no member at all**. That is a pre-existing property of the narrowing path, not something Phase 2 caused — this arm declines, so everything below the insertion point runs byte-for-byte as before, and TC 10f is the control showing what a taken arm looks like. Recorded as a finding in [risks-and-gaps.md](risks-and-gaps.md) and out of COMP-09's scope |
| 11 | COMP-09-10 | A 4 002-line consumer file whose receiver `t` is a file-local table | `t.<caret>`, instrument whether `LuaLocalBindingScan` ran | It does **not** run — `globalMembership` answers `found = false` and short-circuits it. Ordering is a measured cost decision, not a routing one: the reverse order costs 10 875–21 501 µs per completion here (design §1.10.6) |
| 7 | COMP-09-02 | The `wx` fixture | Enumerate through `membersIn`, counting `processValues` callbacks | `entriesTraversed >= membersIn(R).size` (and `==` when no name repeats across declaring files — design §4.10b assertion 1) and does not move when 4 000 unrelated keys are added (design §4.10b). **Scoped**: the `catsClassTags` / `LuaImplicitFields` / `LuaTypesVisitor` walks are excluded — they are out of scope because §4.3/§4.6 do not touch them, not because they were measured cheap — the old 22 ms/949 ms justification is retracted, and their cost is DR-16 (design §4.11) |
| 7a | COMP-09-07 | `wx`, `wxFrame`, `AllColon`, `Shapes` | Enumerate through **both** doors, never `resolveGlobal(r) ?: resolveType(r)` | The golden records each door separately. Two of the four resolve through only one door, so a collapsed golden is silently door-dependent (design §4.4a) |
| 7b | COMP-09-01 | A `@class` on a **local** (`---@class wxFrame` / `local wxFrame = {}`) | `membersOfGlobal("wxFrame", …)` | `[]` — matching today's `crossFileGlobalMembers`, which returns `emptyMap()` because a local is not a global. The union would return its full member list, inventing members at a call site that offers none (design §4.5) |
| 7c | COMP-09-03 | `---@class Derived : Base` / `---@field ownField number` / `Derived = {}` | `Derived.<caret>` | `[Show, ownField, ownFn]` — `ownField` is a deliberate new member on the completion path (design §4.5a); today `[Show, ownFn]`. Measured armed (design §1.10.5) |
| 7d | COMP-09-03 | `---@class Base` / `---@field inheritedField string` / `---@field onClose fun(): nil` / `Base = {}` | `Base.<caret>` | `[Show, inheritedField, inheritedFn, onClose]`; today `[Show, inheritedFn]`. **NEW 2026-08-12** — §4.5a declared this superset for `Derived` only; DR-21 found the same mechanism on `Base`, which owns the two `@field`s. `Derived` does *not* gain them, because inherited `@field`s belong to `Base`'s index key — the flat list behaving as §4.5a's B5 paragraph says |
| 7e | COMP-09-03 | The same `Base` fixture | `Base:<caret>` | `[Show, inheritedFn, onClose]`; today `[Show, inheritedFn]`. **NEW 2026-08-12** — `---@field onClose fun(): nil` indexes as `Kind.FUNCTION` (design §4.3's `startsWith("fun(")` rule) so it survives the *syntactic* colon filter. `Derived:` is unchanged. This is the one place the index arm's syntactic `isColon` filter and the graph arm's semantic one visibly diverge, and it must be an expectation |
| 7f | COMP-09-03 | A project on a real **Redis 7+** target (build the `Target` as TC 10h specifies — `PlatformVersionRegistry.findVersion`, not a `String`); `runtime/redis/redis-7/redis.lua` is `---@class redis` + ten `---@field` constants + a bare `redis = {}` + **thirteen** `function redis.*`, with the ten constants **never assigned** | `redis.<caret>` and `redis:<caret>` | `redis.` = `[acl_check_cmd, breakpoint, call, debug, error_reply, log, pcall, register_function, replicate_commands, set_repl, setresp, sha1hex, status_reply, LOG_DEBUG, LOG_NOTICE, LOG_VERBOSE, LOG_WARNING, REDIS_VERSION, REDIS_VERSION_NUM, REPL_ALL, REPL_AOF, REPL_NONE, REPL_REPLICA]` — today the thirteen functions only. `redis:` = the same **thirteen functions, unchanged**, because the ten index as `Kind.FIELD` (their `@field` type text is `number`/`string`, not `fun(`) and design §4.13's syntactic `isColon` filter drops them. Assert **both** doors as exact sets. **Evidence: the pasted probe output in design §1.10.8a**, `=== PROBE TARGET Redis 7+ ===` block — `today.dot`, `today.colon`, `indexDot`, `indexColon` are printed there in full and this row restates nothing that is not in it. ⚠ **The function count is per version — 10 / 11 / 13 / 12 / 12 across Redis 5, Redis 6, Redis 7+, Valkey 7.2, Valkey 8** — so "thirteen" binds only to this target and TC 7f may not be reused verbatim on another |
| 7f-bis | COMP-09-03 | A project on a real **Valkey 8** target. `runtime/valkey/valkey-8/redis.lua` has the same shape as 7f with **twelve** functions; `runtime/valkey/valkey-8/server.lua` has the **same ten `---@field` constants** but *also* writes `server.LOG_DEBUG = 0`-style assignments for all ten | `redis.<caret>`, `redis:<caret>`, `server.<caret>`, `server:<caret>` | `redis.` gains the same ten constants on top of its twelve functions; `redis:` unchanged at twelve. **`server.` and `server:` are BOTH unchanged** — `server.` stays at its 21 members (ten constants + eleven functions) and `server:` at eleven, because the assignments already put the constants in front of today's global door *and* design §4.3's source 2. **NEW 2026-08-12 (DR-28).** This is the control that makes 7f legible: identical `@field` block, no movement, so it is `@field`-**only** declaration that moves rather than `@field` as such. It also pins the full blast radius — five `redis` receivers move (Redis 5/6/7+, Valkey 7.2/8), `server` does not — instead of leaving four of them unnamed. Evidence: design §1.10.8a's `=== PROBE TARGET Valkey 8 ===` block. Restore STANDARD 5.4 in `tearDown` |
| 6b | COMP-09-05 | `---@class D : Base` where `Base` declares `__add` | ~~`D() + D()`~~ → `---@type D` on each operand, for TC 6's reason | No diagnostic. `getMembers()` merges supertypes, so no separate supertype walk is needed (design §4.7). Pre-change: `D is not assignable to number` |

## Acceptance Criteria

**DONE 2026-08-12 — all six phases complete. Every box below is ticked.** Three things are
deliberately **not** claimed by that status, each with a tracked owner rather than a silent gap:

1. **The `@class` door misses NFR-1** — 269 ms direct / 323 ms through completion at 3 600 members,
   3× the budget, first measured at Phase 5. It is the residual design §1.6 predicted, it is not a
   regression, and COMP-09-08's gate is and always was on the completion door, which measures
   **12.2 ms**. Owner: **BUG-438** (DR-29).
2. **`LuaReceiverMemberIndex.Indexer.map` walks the file five times** where one walk would do —
   ~40 ms of its 67 ms per-file build cost. One-off, persisted, on no latency path.
   Owner: **BUG-437** (DR-25).
3. **The live IDE checklist has not been run.** 15 of its 18 scenarios are discharged by automated
   real-flow tests; 3 (perceived first-completion latency, the deliberate loss of type text, and the
   override gutter marker) need a human, and whether that pass is warranted is the supervisor's
   decision — see the implementation plan, "The live checklist".

**Phase 1 was done 2026-08-09** — `LuaReceiverMemberIndex` was complete, correct and tested and
**nothing consumed it yet**, so no criterion below was met by it alone. What Phase 1 moved is marked
per line; the consumers are Phases 2 and 3.

- [x] COMP-09-01/02 — every site in the two tables above is converted or explicitly justified.
      **MET 2026-08-12 (Phase 5 closed the COMP-09-02 half, design §1.11.5).** The three remaining
      COMP-09-02 sites are now justified against a **per-site figure on the door each one serves**,
      which is what DR-16 was owed and the only thing that kept this box open:
      `catsClassTags` **11.0 ms** and `LuaImplicitFields.collect` **17.8 ms**, both on the `@class`
      door, together 29 ms of that door's 269 ms — so converting them is neither required by the work
      bound (COMP-09-09 is green at that door) nor sufficient for its latency (BUG-438).
      `LuaTypesVisitor:1349` (`seedAmbientGlobals`) is under budget by construction: it reads only
      files *named* `global.lua`, none exists on the default target, and each of the five that do is
      **7 lines**. Two corrections to the table below, from grep before quoting: `catsClassTags` is
      `LuaTypeManagerImpl:435` called at `:396`, and it is **the same site** as the row named
      "`LuaTypeManagerImpl:436`" — three distinct sites, not four. `LuaMemberFieldNavigation:32` is a
      **navigation** site and is excluded by this document's own Out of Scope section.
      **One is justified rather than converted, and the justification is here rather than only in the
      plan**: `LuaCompletionContributor:133-139` (`crossFileGlobalMembers`) is **not converted and
      must not be**. Its guard `if (type == LuaGraphType.Undefined)` never opens for a receiver with
      members and it is downstream of the cost; converting it was executed, measured and aborted
      (`ABORT_REPLAN`, `d5af3231`). The re-plan's site is a new call **above**
      `LuaTypesSnapshot.forFile` (design §4.13) and this function stays byte-for-byte unchanged, which
      is itself an exit check on Phase 2. **Phase 2 landed the new site and that exit check holds**:
      `crossFileGlobalMembers`, the `Undefined` guard and the shared emit loop are byte-for-byte
      unchanged, and the golden's eleven `global` and eleven `class` rows are byte-identical across
      the re-record. The completion door is converted; `addMethodsOf` is Phase 3.
      **Phase 3 converted `addMethodsOf` (2026-08-12).** Both `StubIndex.getAllKeys` scans are gone
      — `LuaTypeManagerImpl` now contains **zero** `getAllKeys` calls — and the `allKeys` parameter
      is dropped, taking the function to two arguments. Candidates come from
      `LuaReceiverMemberIndex.membersIn` (the materialization door, never `membersOfGlobal`), and
      the `LuaFuncDecl` lookup moved into a `declaredMethod` helper that carries BUG-398's
      confinement. The golden is byte-identical in **both** directions and no `class` row moved.
- [x] COMP-09-03 — **MET at the completion door 2026-08-12 (Phase 2).** TC 5 passes for all four
      sources. **Sources verified at the index** after Phase 1 (`LuaReceiverMemberIndexTest`): dotted
      assignment, `function R.f`/`function R:m` including the all-colon receiver (TC 5a), `@field`,
      and the table literal. TC 5 itself is a completion test and Phase 2 supplies it as **E1**
      (`MemberEnumerationExpectationTest`), which puts all four sources at one receiver and asserts
      both doors as exact sets. TC 7c/7d/7e are E2/E6/E7; TC 7f and TC 7f-bis are green on real
      Redis 7+ / Valkey 8 targets. **The metamethod clause (COMP-09-05) is Phase 4.** The `@class`
      door (`addMethodsOf`) was converted by **Phase 3**, whose four preservation tests
      (`MemberEnumerationMaterializationTest`) pin design §4.6's table row by row; the four index
      sources reach that door only as *candidates*, since a member is admitted there only when a
      `LuaFuncDecl` stub backs it.
- [x] ~~COMP-09-04b~~ — **withdrawn (design §1.7); nothing to verify.** Ticked as an explicit
      deferral-by-withdrawal rather than left open: the box can never be met and an unticked box that
      cannot be ticked is indistinguishable from outstanding work.
- [x] COMP-09-05 — **met (Phase 4, 2026-08-12; coverage remediated the same day).** TC 6 / 6a / 6b
      are `MemberEnumerationMetamethodTest` (**11 tests** — two added by the remediation, which
      overturned Phase 4's finding that the `ALL_METAMETHODS` name filter could not be pinned: it is
      unobservable only *through* `implementsOperator`, and a direct read of `Table.metamethods` off
      `LuaGraphType.materialize` pins it under both the loosened and the removed filter. The
      remediation also corrected the control's rationale to what it measurably catches, and made
      `ALL_METAMETHODS` `by lazy` to break a class-initialization cycle it exposed. See
      risks-and-gaps, "Phase 4 findings"). `LuaGraphType.classTable` contributes a
      `@class`-declared operator metamethod to `Table.metamethods` **as well as** leaving it in
      `localMembers`, so the operator position gains the capability and completion is untouched —
      TC 6a asserts the four offered sets exactly and none moved. BUG-426's Known limitation is
      **closed** for the `@class` half and restated for the other half (assigning `__add` to a table
      never installed as a metatable still confers nothing, which is correct). `COMP-04-DR-01` is
      closed in two parts — BUG-426 answered aliased `mt`, this answers the declared form — with
      `COMP-04-G-01` (a genuinely dynamic `mt`) left open, because neither change addresses it.
- [x] COMP-09-06 — TC 4. **If any baseline moves, enumeration has become a type source: stop.**
      **MET.** No baseline moved at any phase that changed production code: Phase 2 and Phase 3 each
      gated on the corpus sweep with the golden `38c7586ecfddd17bdb79785b3b3a9f31`, and Phase 4's exit
      criterion was "corpus baselines unmoved" and held. Phase 5 changes **no production code** — its
      probes were run under the `temporary-edits` snapshot loop and reverted, `git diff -- src/main`
      empty — so it cannot move a baseline and did not re-run the sweep, by design.
- [x] COMP-09-07 — TC 3. **TC 7a is met** (`LuaReceiverMemberDoorParityTest`): each entry point is
      asserted against the door it serves, never `resolveGlobal(r) ?: resolveType(r)`. The union
      reproduces the `@class` door on all four receivers; the completion door reproduces the global
      door with exactly the two declared divergences — `Shapes` loses `deep` (BUG-430) and `Derived`
      gains `ownField` (§4.5a). TC 7b and 7c are covered there too.
      **Phase 3 broke it once, and declared one exception (2026-08-12).** *(a)* **A defect, fixed.**
      The receiver index accepted only `file.extension == "lua"` while `LuaFileType` is registered for
      `lua;rockspec` plus the file names `.luacheckrc`/`.busted`, so the `@class` door returned a
      subset — measured `[fromBusted, fromLua, fromLuacheckrc, fromRockspec, same]` →
      `[fromLua, same]`. The filter is now derived from the file type and the shape is asserted at
      both index doors and at the `@class` door. *(b)* **A DECLARED exception, closed 2026-08-12.**
      A dot/colon name collision on one receiver (`function R.m` beside `function R:m`) resolves to
      the **first declaration in file order** where the `getAllKeys` scan resolved it by
      key-enumeration order. **Preservation was not achievable here because the prior answer was not
      a function of the input**: measured, five structurally identical receivers were answered two
      dot / three colon by a persistent index enumerator, so there was no rule in the source to
      preserve — see risks-and-gaps, "GAP (Phase 3, 2026-08-12)", for the measurement and the ruling.
      The replacement rule is deterministic, agrees with the global door (which returned the dot
      declaration on all five), and is pinned by
      `MemberEnumerationMaterializationTest.testDotColonCollisionResolvesToTheFirstDeclarationInFileOrder`
      — which asserts *determinism across identical receivers* as well as the value, and states both
      polarities, so a return to order-dependence or a flip to last-wins reddens it (both mutation-proven).
      **COMP-09-07 therefore holds as "behaviour-preserving except where declared", with this and the
      two `@class`/global door divergences above as the whole declared set.**
      **TICKED 2026-08-12.** The one thing outstanding was TC 3 re-run against Phase 4's metamethod
      addition at this door, and Phase 4 did exactly that and measured no movement: `v.` =
      `[__add, len, x]` and `v:` = `[len]` for the class declared on a local *and* on a global,
      identical to DR-12's pre-Phase-2 figures, now permanently gated as exact sets by
      `MemberEnumerationMetamethodTest` (11 tests) rather than printed. The `@class` half of the
      change contributes to `Table.metamethods` **as well as** leaving `localMembers` untouched, which
      is why the offered sets could not move. Nothing about the tie-break was ever outstanding.
- [x] COMP-09-08 — **MET 2026-08-12 (Phase 2).** The latency assertion exists, failed before the fix,
      passes after, and runs without `-PwithPerf`. **Assertion 1 (wall clock, tier 1) is enforced**:
      `MemberEnumerationLatencyGateTest.BUDGET_ENFORCED` is `true`, and the run that flipped it
      reported the inverted gate red at *"COMP-09-08 is now MET — 24 ms against a 100 ms budget"*.
      Re-measured at the new site over **five distinct wide receivers, each in its own file** —
      `[12046, 13162, 14604, 17580, 20540] µs`, **median 14 604 µs** against 490 995 µs unarmed
      (design §1.10.2). **Assertion 2 is replaced by a count** (design §4.10a-bis) — its timing form
      flipped verdict between two runs of the same code, so it is re-expressed as
      `LuaReceiverMemberWork.entries` per receiver: measured `narrow=3->3 wide=3600->3600` across the
      addition of 4 000 unrelated indexed members.
- [x] COMP-09-10 — **MET 2026-08-12 (Phase 2).** Rule S is stated in design §4.14, implemented as
      `net.internetisalie.lunar.lang.psi.LuaLocalBindingScan`, and TC 10a–10j pass
      (`MemberEnumerationShadowingTest`, plus TC 10h in `MemberEnumerationRedisTargetTest`). Both
      mutation proofs are recorded in the commit message and each reddened **exactly one** test with
      the same invented member: deleting the `LuaParList` clause reddened TC 10c
      (`expected:<[]> but was:<[fromLibrary]>`) and deleting the `name == "self"` early return
      reddened TC 10i (identically) — one clause deletion proves one clause, and §4.14's table has
      seven. TC 10h additionally pins the one `LuaScope.declare` site Rule S deliberately excludes
      (`seedAmbientGlobals`, `LuaTypesVisitor.kt:1360`) on the only target family it fires on, and it
      is green on a real `Target(REDIS, 7+)`: `KEYS`/`ARGV` offer `[]` at both doors, as today.
- [x] COMP-09-09 — **MET 2026-08-12 (Phase 3).** The work bound is instrumented and asserted (TC 9);
      adding unrelated indexed content does not change entries traversed. **Both doors are now
      green.** The completion door was met at Phase 1 (assertion 4) and the *materialization* door —
      the half requiring "the consumers stop scanning" — is met here:
      `MemberEnumerationMaterializationTest.testMaterializationWorkDoesNotMoveWhenUnrelatedContentIsAdded`
      measures `LuaReceiverMemberWork` **through `resolveType`**, so it observes what `addMethodsOf`
      asks for rather than what the index can do. Measured `quiet=Traversal(entries=50, files=1)`
      and `noisy=Traversal(entries=50, files=1)` across the addition of 4 000 unrelated indexed
      members (40 receivers × 100) — the bound is the receiver's own 50 declared methods, and it is
      the "one candidate in, one file read" reading of assertion 4: `files == 1` equals the one
      declaring file, not above it. Under the old `getAllKeys` scan the same enumeration visited the
      whole key space. **Guarded against vacuity**: `resolveType` is memoized, so a cached second
      answer would leave the thread-local holding the first arm's numbers; the test asserts
      `noisy.entries > 0` first, which only holds if `membersIn` genuinely ran again. **Both the
      `entries` and the `files` figures carry an absolute anchor** (added in the Phase 3
      remediation): `entries == METHOD_COUNT` and `files == 1`, so a change that moved both arms
      together cannot pass on the quiet-equals-noisy comparison alone.
      **The instrument was complete after Phase 1**: all
      four of design §4.10b's assertions are armed and green in `MemberEnumerationWorkBoundGateTest`
      — assertion 4, the *completion* door, was red until `LuaReceiverMemberWork` reached
      `membersInFile` (BL-5). That left the *materialization* door outstanding until Phase 3, and
      Phase 3 supplied it — see the measurement above.
      **Assertion 4 holds `filesVisited == 1` for its fixture, not by construction.** DR-19c makes
      `membershipOver` read every candidate to decide opacity, so a receiver bare-bound in two files
      in the chosen scope visits two on a *correct* implementation; `quietFixture()` yields exactly
      one candidate, which is what pins the 1. Phase 3 used assertion 4 as its D2-leak detector,
      read as "one candidate in, one file read" — a count above the candidate count is the leak, a
      count equal to it is not — and measured a count equal to it.
- [x] Each cache in the "Why this is a capability" table is re-measured and either removed as
      redundant or kept with a stated reason. **MET 2026-08-12 (Phase 5, design §1.11.6).** All four
      are measured and **all four stay**, each with a figure and a reason:
      `GlobalSymbolRankingService:54,66` — the two `getAllKeys` scans cost **1 176 µs** (2 872 function
      keys) + **123 µs** (3 class keys) uncached, ~1.3 ms per completion invocation, and they answer a
      **ranking** question over key *names* that `LuaReceiverMemberIndex` cannot answer at all, so
      COMP-09 did not make them redundant. `typeCache` (`LuaTypeManagerImpl:58`) — cold-file miss
      **477 ms**, warm-file miss **103 ms** (the marginal per-class cost), hit **9 µs**.
      `globalCache` (`:82`) — cold miss **631 ms**, hit **9 µs**; **`moduleCache` (`:70`) is not
      separately measured** — same structure, same door — and that is stated rather than implied.
      `LuaTypesSnapshot.forFile`'s per-file `CachedValuesManager` (`LuaTypes.kt:237`, deps `:281-289`)
      — hit **13 µs** against a miss of **44 ms** on a 4 002-line consumer and **844–955 ms** on the
      123 KiB library (TYPE-11 DR-08); it is the most valuable cache in the feature and TYPE-11 has
      just made it survive unrelated edits. Anchors were re-grepped: the table's `:55` / `:67,79` are
      `:58` / `:70,82` today.
- [x] **NFR-1, both doors, re-measured — MET at the completion door, MISSED at the `@class` door,
      and the miss is deferred with an owner.** *(Phase 5 task 1, design §1.11.1.)* Five distinct wide
      receivers in five files each, cold, medians of five: the **completion door is 12 225 µs**
      against the 100 ms budget (15 253 µs on a second run — an independent re-confirmation of
      COMP-09-08's gate on a second fixture), and the **`@class` door is 269 459 µs** direct /
      **322 692 µs** driven end to end through `completeBasic()`, **3× the budget**. The `@class`
      miss is **not a regression and no improvement is claimed** — no comparable prior figure exists
      — and it is the residual design §1.6 named in advance. Filed as **BUG-438** / DR-29.

## Non-Functional Requirements

**The target already exists and this feature is orders of magnitude outside it.**
*(An earlier revision said "129× outside it". That is 12 902 ms / 100 ms, and the 12 902 ms is the
single-shot **time-to-exhaustive** figure this very section goes on to withdraw — so the ratio is
retired with its numerator and kept only as a pre-Phase-0 record. The binding comparison is design
§1.9's cold **time-to-first**, 746 ms against a 100 ms budget, and §1.10.2's re-plan pair, 491 ms
unarmed against 7.4 ms armed, both medians of five cold samples.)*
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
  **It stays FLAT.** Design §4.12 previously proposed amending `non-functional.md` to exempt a
  "tier 2" receiver — one the index cannot see through — from this budget. That amendment is
  **withdrawn 2026-08-12** and must not be made: measured at the new change site, the `require`-bound
  receiver lands at 25–61 ms armed against 121–130 ms unarmed, with no tier-2-specific work, because
  the cost was never the receiver's opacity — it was the consumer file's whole snapshot build
  (design §1.10.3, TC 8a). The only amendment `non-functional.md` still needs is the one the "spec
  hole" section below names: that the budget covers **indexed library content**, not just project
  lines.
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
(`-PwithPerf`). So no stated target has ever been enforceable, and a two-orders-of-magnitude miss was
invisible.

**Fixing the enumeration without fixing that leaves the next such regression equally invisible.**
Hence COMP-09-08 below.

### The spec hole — name it, do not exploit it

The 100 ms target is qualified *"for projects up to 50k lines"*. A definition library is not project
lines, so the NFR does not literally bind for library trees — which is how a 530 KiB root can be
both catastrophic and technically in-spec. `non-functional.md` must be amended to cover **indexed
library content**, not only project content. Leaving the qualifier in place and declaring compliance
would be true and useless.

## RE-PLANNED 2026-08-12 — unblocked by TYPE-11, Phases 2–5 re-cut from a prototype

Phases 0 and 1 stand: the golden and both gates are checked in, and `LuaReceiverMemberIndex` is
complete, registered and tested. **Phases 2–5 were re-cut, not resumed** — see
[implementation-plan.md](implementation-plan.md).

**Why the old Phase 2 was aborted.** It was executed exactly to plan, measured, and stopped
(`ABORT_REPLAN`, `d5af3231`, docs only — no production change kept). `crossFileGlobalMembers` sits
behind `if (type == LuaGraphType.Undefined)`, a guard that never opens for a receiver with members
because `LuaTypesVisitor.freeGlobalSeed` already resolves free cross-file globals; and it is
downstream of `LuaTypesSnapshot.forFile`, which was 88–97 % of cold time-to-first. Full record in
[risks-and-gaps.md](risks-and-gaps.md), "BLOCKER (Phase 2, 2026-08-09)".

**What changed.** TYPE-11 shipped `done`. The 334 ms recurring per-keystroke cost DR-20 found is
gone. What remains — and what COMP-09 now exists to remove — is the **cold `buildSnapshot` of the
library file: 844–955 ms** for a 123 KiB / 3 600-member library, once per session, ~690 ms of AST
traversal plus ~170 ms of `checkTypes` (TYPE-11 DR-08). That is the measured target.

**What the re-plan is, and what makes it different from the thing that failed.** The abort happened
because *nothing in the de-risking exercised the real door* — DR-14 validated `membersOfGlobal`
against `resolveGlobal` directly. So the re-plan was **built and run before it was written**
(DR-21/DR-22, design §1.10): the new site was implemented in `LuaCompletionContributor`, armed,
driven through `myFixture.completeBasic()`, run over the whole 2 639-test suite, measured, and
reverted.

| what the abort owed | answer | evidence |
| :-- | :-- | :-- |
| a change site **above** the snapshot build | design §4.13 — one call before `LuaTypesSnapshot.forFile`, early return; nothing below it touched | cold time-to-first **491 ms → 7.4 ms**, medians of five cold samples (§1.10.2) |
| an **explicit shadowing rule** | design §4.14 "Rule S", derived from `LuaTypesVisitor.visitNameRef`'s `scope.lookup` and the **ten** `LuaScope.declare` sites — plus COMP-09-10 and TC 10a–10j | ten scenarios, all `same=true`, and the whole armed suite (§1.10.4/§1.10.5) |
| a **restatement of §4.5's premise** | §4.5's caption "the door this call site actually serves" is **withdrawn**; the *selection* rule survives unchanged and was re-taken through the contributor | §4.5's correction block; DR-21's per-receiver table |
| a **re-derivation of §4.12's two tiers** | **withdrawn.** Tier 2 lands at 25–61 ms armed with no tier-2-specific work; the cost was never the receiver's opacity, it was the consumer file's snapshot | §1.10.3, TC 8a |
| DR-18 **executed** | done — **61 ms marginal, once**, on the 123 KiB library, against 844–955 ms per session | §4.8a |

**Exactly two tests move under the armed prototype**, both declared: the golden (three receivers,
`completion` door — the `.` caret only, since the golden has no colon door) and the inverted latency
gate reporting the budget is met. **That count is scoped to the STANDARD 5.4 target**, which is the
only one DR-21/DR-22 ran on, and it is scoped to the **dot** caret, which is the only one the golden
records.

**Two things sit outside that count and are named rather than left to Phase 2 to discover:**

- **Redis/Valkey.** DR-28 measured every target the `@field`-only stub shape exists on (design
  §1.10.8a). **Five receivers move** — `redis.` on Redis 5, Redis 6, Redis 7+, Valkey 7.2 and
  Valkey 8, each gaining the same ten `@field` constants at the `.` caret and **nothing** at `:`. One
  receiver that shares the shape is measured **unchanged**: `server` on both Valkey targets, because
  its constants are also written as assignments. TC 7f and TC 7f-bis. DR-26's earlier statement of
  this named one file and pasted no `redis` line; DR-28 supersedes it on both counts.
- **The colon caret.** The golden cannot carry it (`completionRows` uses `.` only), and after Phase 2
  nothing would pin it for ten of the eleven receivers. **DR-27 is therefore decided: extend the
  golden to a colon door BEFORE Phase 2**, on today's unarmed code, where the re-record is a pure
  addition with zero behaviour delta — implementation-plan Phase 2 task 0. That also settles an
  internal disagreement: DR-21's summary says nine of eleven colon receivers were byte-identical
  (two movers) while the design declares exactly one (`Base:`), and no colon output is pasted
  anywhere, so the recorded baseline is what decides it.

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

Refreshed after Phase 0, which closed two of these and moved a third:

- **DR-08 — done.** Every surviving harness medians five runs. Its standing consequence outlives it:
  the spread is wide enough that **no ratio between two of this harness's figures is quotable**.
- **DR-17 — done by construction.** Assertion 2's factor is not picked and frozen; the gate evaluates
  `ceil(p95/p50)` over five cold 3-member receivers on every run. It measured 2x on one run and 3x on
  the next, which is the argument for deriving it.
- **DR-16 — partly done.** The medians half is settled (`CompNineSection32Test`: candidate B 29 ms
  against a 41 ms warm-file `@class` door, candidate C 18 ms) and it makes the old scope-out look
  worse, not better. Still owed: the per-site right-door analysis. The scope decision does not depend
  on the answer.
- **DR-18 — DONE 2026-08-12, and the premise holds.** Design §4.8a. The second whole-project index's
  **marginal** build cost (PSI already parsed, which is how the platform actually indexes) is
  **61 ms median of 5** on the 123 KiB library this feature is about, against `LuaMemberFieldIndex`'s
  20 ms and `LuaGlobalAssignmentIndex`'s 6 ms on identical input; **355 ms** over a 78-file / 207 KiB
  tree against those two's 230 ms + 80 ms. One-off, written to disk, reused across sessions —
  against the 844–955 ms cold graph build it removes **every session**. It is the most expensive of
  the three, which is expected from five sources and four `findChildrenOfType` passes; whether those
  can share one traversal is **DR-25**, a follow-up rather than a blocker.
- **DR-21/DR-22 — done 2026-08-12.** The re-planned change site, prototyped end to end through the
  real completion path and run over the full suite armed. Design §1.10.
- **DR-23/DR-24/DR-25 — new, all Phase 5.** Rule S's residual cost on a large file that binds a
  global's name; the all-opaque file that still pays one snapshot build; and whether the indexer's
  four traversals can be one.
- **DR-26 — done 2026-08-12, and it corrected two statements in this document.** The one
  `LuaScope.declare` site Rule S excludes (`seedAmbientGlobals`, `LuaTypesVisitor.kt:1360`) is dead
  on the default target and live only on Redis/Valkey, where it declares `KEYS`/`ARGV`; measured
  there, the index arm and today agree at `[]` (TC 10h). `table` is bound by `freeGlobalSeed`, not
  by ambient seeding — TC 10g's *Given* was wrong and is fixed. It also **reported** a third instance
  of §4.5a's `@field` superset on the bundled `redis` stub — but pasted no `redis` line into any
  artifact, so that half was a summary, not evidence, and it is **superseded by DR-28**.
  Design §1.10.8.
- **DR-28 — done 2026-08-12, and it supersedes DR-26's third result.** DR-26's `redis` claim lived
  only in a hand-off summary; §1.10.8's pasted block contains `KEYS`, `ARGV` and `table` and no
  `redis` line at all. DR-28 re-ran it properly, on **all five** Redis/Valkey targets and at **both**
  doors, and pasted the output into design §1.10.8a. It confirms the superset, and it corrects the
  scope twice: the movement is **five receivers**, not one file, and the function count is a
  **per-version** property (10/11/13/12/12), so "the thirteen functions" binds only to Redis 7+. It
  also measured the receiver that shares the shape and does **not** move — `server` on both Valkey
  targets, whose constants are additionally written as assignments — which is the control that shows
  the mechanism is `@field`-**only** declaration. TC 7f, TC 7f-bis.
- **DR-27 — DECIDED 2026-08-12: do it, and do it BEFORE Phase 2.** The Phase 5 deferral is
  withdrawn. The golden has no colon door (`completionRows` uses the `.` caret only,
  `MemberEnumerationGoldenTest.kt:100`). The deferral weighed "extend during Phase 2" against
  "extend in Phase 5" and never considered the cheap third option: **extend it first, on today's
  unarmed code**, where the diff is a pure addition of colon rows with zero movement on any existing
  line and no production change in the commit. Phase 2's every-line-declared exit criterion is
  untouched by that, and it closes a gap the deferral did not name — after Phase 2 nothing pins the
  colon door for ten of the eleven receivers, and Phase 4's "re-check `v:` at the new site" has no
  baseline to re-check against. It also resolves a live inconsistency: DR-21's row claims nine of
  eleven colon receivers byte-identical (**two** movers) where the design declares **one** (`Base:`),
  with no colon output pasted anywhere. Implementation-plan Phase 2 task 0.
- **Cancellation cannot be gated *by asserting the throw*, and one of its two calls cannot be gated
  at all — both measured, in Phase 1 and its remediation.** §4.9's `ProgressManager.checkCanceled()`
  is present at both `processValues` callbacks. No test can distinguish it *by the throw*: the
  platform calls `checkCanceled()` inside `ensureUpToDate` (`FileBasedIndexImpl:893`) before the value
  iterator is opened, so `processValues` under a cancelled indicator throws before invoking any
  callback and a test asserting the throw passes with the line deleted. That candidate test was
  written, measured and deleted.
  What *is* observable is the probe rather than the throw. The platform checks after each callback
  (`FileBasedIndexEx:424`, `:456`) and the plugin checks before it, so a
  `CoreProgressManager.CheckCanceledHook` sees two probes straddle one `recordVisit`. The **union
  door** is gated on that in
  `LuaReceiverMemberCancellationTest.testTheUnionDoorProbesCancellationBeforeEachCallbackAndNotOnlyAfterIt`
  and mutation-proved (`[1, 1, 2, 2, 3]` → `[1, 2, 3]` red). The **completion door** is not: because
  `membershipOver` reads one file per `processValues` call, each call brings its own run of platform
  probes at an unchanged file count, so the same repeat appears with or without the plugin's line —
  that test was written, mutation-tested, found to survive and removed. `membersInFile:510`'s call is
  therefore required, present, and an accepted evidence gap.

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
