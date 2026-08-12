---
id: "BUG-438"
title: "The `@class` completion door misses the 100 ms time-to-first budget — 323 ms at 3 600 members"
type: "bug"
parent_id: "BUG"
status: "todo"
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

## 5. What a fix has to establish first

Bucket the door on the same five-receivers-in-five-files shape, medians of five, before proposing
anything: parse vs stub construction vs `funcTypeFromStub` vs `LuaImplicitFields`. COMP-09's standing
rule applies — **no ratio between two figures of one harness is quotable**, and a count gate is
preferred to a timing threshold wherever a count will do.
