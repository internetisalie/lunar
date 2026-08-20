---
id: "BUG-438"
title: "The `@class` completion door misses the 100 ms time-to-first budget — 323 ms at 3 600 members"
type: "bug"
parent_id: "BUG"
status: "planned"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-438: the `@class` door still misses NFR-1

*Source: COMP-09 Phase 5, [[COMP-09-00-DR-29]] — the first cold medians-of-five figure this door has
ever had. Full evidence in
[COMP-09 design §1.11.1](../../completion/09-member-enumeration/design.md).*

## 1. Reproduction

1. A library file declaring `---@class Klass` with 3 600 members (3 400 `---@type` fields +
   200 `function Klass.f$i()`), registered as a library source root.
2. In a consumer file:

   ```lua
   ---@type Klass
   local v
   v.<caret>
   ```
3. Invoke completion, cold (first time in the session for that class).

## 2. Expected vs actual

- **Expected**: time-to-first-result under **100 ms**
  ([`non-functional.md`](../../non-functional.md) NFR-1, flat, as COMP-09 re-affirmed when it
  withdrew the proposed "tier 2" exemption).
- **Actual**, five distinct receivers in five files, median of five:

  ```
  door2 through completeBasic()  = [286301, 304478, 322692, 364852, 702997] median = 322 692 us
  door2 direct (resolveType + materialize + getMembers)
                                 = [212233, 258183, 269459, 270466, 285922] median = 269 459 us
  ```

  **3× the budget.** A second run gave 257 149 µs for the direct form, so the direction is stable.

## 3. What this is NOT

- **Not a regression.** No comparable cold medians-of-five figure existed for this door before, so
  nothing is being compared. COMP-09 makes no improvement claim about it either.
- **Not COMP-09-08's gate failing.** That assertion is, and always was, on the **completion**
  (`resolveGlobal`) door, which measures **12 225 µs** on the same run — an order of magnitude inside
  the budget.
- **Not the remaining COMP-09-02 walk sites.** Measured: `catsClassTags` **11.0 ms** and
  `LuaImplicitFields.collect` **17.8 ms**, together 29 ms of the 269 ms. Converting them does not
  bring the door inside budget.
- **Not an invalidation problem.** The cost is a *first* build, so narrowing cache invalidation
  (TYPE-11's subject) cannot touch it — COMP-09 DR-07, design §1.11.7.

## 4. Where the cost is, as far as it has been located

COMP-09 design §1.6 predicted this residual before Phase 1 and it held: the `@class` door needs
member **types** (`funcTypeFromStub`), not only names, so an index of names does not serve it. Its
cold cost is dominated by the declaring file's AST parse — measured here as the 522 ms cold first
sample of an otherwise 11 ms `catsClassTags` — plus per-member type construction.

Attribution beyond that is **not** claimed. COMP-09 §1.6 left ~430 ms of an earlier cold measurement
explicitly unattributed rather than assigning it by elimination, and the same restraint applies here.

## 5a. BUCKETED 2026-08-20 — parse dominates, `collectMethodMembers` is second

§5's precondition, done. Nothing was fixed: this report still has no Root Cause and no Fix Strategy,
and `/implement-bug` stopped at that. Instrumentation was temporary (`temporary-edits`, reverted; the
tree is clean) and the harness was removed after the run rather than left in the suite, where a
~2-minute non-gating test would tax every gate. It is reproducible from the shape below.

Five receivers in five files, one `---@class Klass$i` per file with 3 400 `---@field` + 200
`function Klass$i.fn$f()`, resolved cold once each via `resolveType`, member count asserted at 3 600:

```
B438-BUCKET Klass0 parse=164026us fields=17518us implicit=14845us methods=76121us   total=341246us
B438-BUCKET Klass1 parse=143007us fields=18819us implicit=18469us methods=86492us   total=270314us
B438-BUCKET Klass2 parse= 88112us fields=11554us implicit=11332us methods=46916us   total=163286us
B438-BUCKET Klass3 parse= 80564us fields= 6664us implicit=10000us methods=39252us   total=139250us
B438-BUCKET Klass4 parse= 92635us fields= 8656us implicit= 8080us methods=42371us   total=155484us
```

| bucket | median | share of the median total |
| :-- | --: | --: |
| declaring file's AST parse | 92 635 µs | ~57 % |
| `collectMethodMembers` / `funcTypeFromStub` | 46 916 µs | ~29 % |
| `hostedParts` member loop | 11 554 µs | ~7 % |
| `LuaImplicitFields.collect` | 11 332 µs | ~7 % |
| **total** | **163 286 µs** | buckets sum to 157 914 — ~3 % unattributed (stub-index lookup + `LuaClassType` construction) |

**This confirms COMP-09 design §1.6's prediction**: the door needs member *types*, so it pays the
declaring file's parse, and that parse is the majority of the cost. `collectMethodMembers` is the
clear second and was not previously separated from it.

### Two caveats, stated rather than buried

- **The five samples are not five independent cold samples.** They descend monotonically apart from
  the last (341 → 270 → 163 → 139 → 155 ms): the first two carry JVM/index warm-up on top of the
  per-class cold cost. The median is therefore an over-estimate of steady-state per-class cost and an
  under-estimate of true first-in-session cost. **DR-29's shape has the same confound** — this is a
  property of "five receivers in one JVM", not of this run.
- **The `parse=` bucket is instrument-influenced.** It was measured by forcing
  `decls.forEach { it.containingFile.node }` at the top of `materializeClass` and timing it, which
  *causes* the de-stub rather than only observing it. The attribution is supported by the total
  (163 ms here vs DR-29's 269 ms — *lower*, so the forced parse is not additive work), but a run that
  proves the un-instrumented path de-stubs anyway has **not** been done. Do that before building a
  fix on this bucket.

**No ratio here is quotable against DR-29's figures** — different fixture, different machine state,
COMP-09's standing rule. The buckets are internally comparable; the totals are not comparable across
harnesses.

### What this points a fix at, without proposing one

Parse avoidance is where the majority of the cost is, and `funcTypeFromStub` already names the
stub-based path — so the question for `plan-bug` is what forces the de-stub and whether the member
*types* this door needs can come from stubs alone. `collectMethodMembers` at ~29 % is worth its own
look and is a smaller, more self-contained target. Neither is a change to make from this report as it
stands.

## 6. Root cause — GROUNDED 2026-08-20

§5a's caveat said the `parse=` bucket was instrument-influenced and told the next attempt to prove
the un-instrumented path de-stubs before building on it. Done, with `PsiFileImpl.isContentsLoaded`,
which observes without forcing. Staged through `materializeClass`:

```
B438-DESTUB K0 00entry                 =[false]
B438-DESTUB K0 01afterReportFile       =[false]
B438-DESTUB K0 02afterFirstHostedParts =[false]
B438-DESTUB K0 03afterAllHostedParts   =[false]   <- stub-only to here
B438-DESTUB K0 04afterImplicitFields   =[true]    <- the AST is forced HERE
B438-DESTUB K0 05afterMethods          =[true]
```

**`LuaImplicitFields.collect` is what de-stubs**, at `LuaImplicitFields.kt:75`:

```kotlin
val assignments = PsiTreeUtil.findChildrenOfType(file, LuaAssignmentStatement::class.java)
```

`findChildrenOfType` over a `PsiFile` loads the AST and then visits every node in it. `hostedParts`
reads the stub and never parses; `collectMethodMembers` runs after the file is already loaded.

### This corrects §5a's own attribution

§5a reported `parse=92 635 µs` and `implicit=11 332 µs` as separate buckets. They are not separable
that way: the ~92 ms it attributed to "parse" **is `LuaImplicitFields`' de-stub**, paid early because
the instrumentation forced `.node` at the top. The honest split is:

| | median | share |
| :-- | --: | --: |
| `LuaImplicitFields.collect`, including the de-stub it forces | ~104 ms | **~63 %** |
| `collectMethodMembers` / `funcTypeFromStub` | ~47 ms | ~29 % |
| `hostedParts` member loop (stub-only) | ~12 ms | ~7 % |

"The parse dominates" and "`LuaImplicitFields` dominates because it parses" are different claims with
different fixes. The second is the true one.

## 7. Fix strategy

**Guard the walk; do not replace it.** `LuaImplicitFields` needs the RHS *type* (`lightInferType`),
and `LuaReceiverMemberIndex` deliberately stores no type (COMP-09 §4.13, sized in DR-19/§4.2), so it
cannot serve this collection outright. What the index *can* answer without parsing is the cheaper
question: **does this file assign any implicit field to these receivers at all?** When it does not,
the walk has nothing to find and the de-stub is pure waste.

1. **Ask the index first.** For each declaring file and the receiver set, consult
   `LuaReceiverMemberIndex` for a `FIELD`-kind member. If none, skip that file — no AST, no walk.
2. **Otherwise walk exactly as today.** Behaviour must be byte-identical when implicit fields exist;
   this is a skip, not a reimplementation.
3. **Re-bucket** with the staged `isContentsLoaded` probe above, on §5a's five-receiver shape, to
   confirm `04afterImplicitFields` stays `[false]` for a file with no implicit assignments.

### The measurement that must come FIRST, because it may kill this

**The win is shape-dependent, and the two libraries measured differ completely:**

| file | implicit `R.x = …` assignments | `---@field` |
| :-- | --: | --: |
| love2d `love.lua` | **0** | 10 |
| openresty `ngx.lua` | **78** | 28 |

So love2d-shaped libraries skip the parse entirely and openresty-shaped ones do not skip it at all.
**Measure the shipped catalog before committing to this**: if most entries look like openresty, step
1 buys nothing on real material and the effort belongs on `collectMethodMembers` (~29 %) or on a
different approach to the walk itself. `n=2` is not a basis — the same error [[BUG-440]] made.

### Explicitly not proposed

- Putting a type into `LuaReceiverMemberIndex`'s value. COMP-09 §4.13 declared its absence and
  DR-19/§4.2 sized the value deliberately; reopening that is a COMP-09 change, not a bug fix.
- Caching. The cost is a *first* build (§3, DR-07), so invalidation cannot touch it.

## 8. Test strategy

| test | asserts |
| :-- | :-- |
| a file with `---@field` only and no `R.x = …` leaves `isContentsLoaded` **false** after materialization | the guard fires — the observable is the de-stub, not a duration |
| a file WITH `R.x = …` still yields the same member set and types as today | the skip is a skip, not a behaviour change |
| **control**: a class whose implicit field shadows an `@field` keeps today's precedence | a guard that skipped too eagerly would silently drop members |

Prefer the `isContentsLoaded` assertion to a timing threshold — it is a state, not a duration, and
COMP-09's standing rule prefers a count gate to a timing one wherever a count will do. Mutation-prove
by removing the guard: the first test must go red and the second must not.

The corpus is the gate for the member sets. `Missing required field` is absent from all four
baselines; if it appears, exactness was touched inadvertently.

## 5. What a fix has to establish first

Bucket the door on the same five-receivers-in-five-files shape, medians of five, before proposing
anything: parse vs stub construction vs `funcTypeFromStub` vs `LuaImplicitFields`. COMP-09's standing
rule applies — **no ratio between two figures of one harness is quotable**, and a count gate is
preferred to a timing threshold wherever a count will do.
