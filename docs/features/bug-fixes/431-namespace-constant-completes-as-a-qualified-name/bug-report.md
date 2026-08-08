---
id: "BUG-431"
title: "A library namespace constant appeared to complete as the qualified `wx.wxID_ANY` — the harness read a line where a name goes, and the suite was red for it from the day the spike landed"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "high"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-431: namespace constants complete as qualified names, and main is red

> **Resolved — the headline is refuted.** Member completion offers bare names from either source and
> always did; `wx.wxID_ANY` was the test harness returning a document line. The suite was red from
> `e5b802a0`, the commit that introduced the assertion. Everything above this line is the report as
> filed; the measurements are under [Fixed](#fixed-2026-08-08--the-completion-was-right-the-harness-read-a-line-where-a-name-goes).

Found while gating COMP-09 DR-09. **The full unit suite is failing on `main` today** — this is not
introduced by any pending work; it was verified by stashing every uncommitted change and re-running
the whole suite on a clean tree.

## Measured (2026-08-08, gce-builder, full suite)

```
clean tree      2494 tests completed, 1 failed, 1 skipped
with DR-09      2500 tests completed, 1 failed, 1 skipped     <- same single failure
```

```
TargetTenDrSpikeTest > testDr06SingleFileLayout FAILED
  junit.framework.AssertionFailedError:
    single-file layout must resolve a namespace constant. Found: [wx.wxID_ANY]
  at TargetTenDrSpikeTest.kt:161
```

The fixture is one registered library file:

```lua
---@meta

---@class wx
wx = {}

---@type number
wx.wxID_ANY = nil
```

Completion for `wx.wxID_<caret>` returns the list `[wx.wxID_ANY]` — the **qualified** name as the
lookup string. The assertion is `constant.contains("wxID_ANY")`, i.e. exact list-element membership,
which the qualified form fails. In the same run the method half of the same test passes:
`f:<caret>` on a `wxFrame` returns `Show`, bare and correct.

## The two questions, in order

1. **Is the completion behaviour itself wrong?** A lookup string of `wx.wxID_ANY` offered at
   `wx.<caret>` inserts `wx.wx.wxID_ANY`. If that is what a running IDE does, this is a user-visible
   defect in member completion and the priority above is right. It has not been checked live — the
   evidence so far is a test fixture only, and `LibraryRootTestCase` exists precisely because
   library-root behaviour differs from project-file behaviour.
2. **If the qualified form is correct**, the assertion is over-strict. Note that
   `TargetTenDrSpikeTest` contains **both** forms for equivalent observations —
   `constant.contains("wxID_ANY")` at `:163` and the looser
   `constant.any { it.contains("wxID_ANY") }` at `:281` — so the harness disagrees with itself about
   what the right answer is, and one of the two was written to pass against whatever was observed.

   A third possibility to rule out before choosing between 1 and 2: the answer may **differ by
   source**. Both assertions run against a library root; a project-file declaration goes through a
   different resolution path. If bare is correct there and qualified here, then `:281`'s looser form
   is right by accident rather than by design, and the fix is neither the test nor the contributor
   but the divergence.

## Regression or committed red?

`TargetTenDrSpikeTest` was added in `e5b802a0` ("feat(target): TARGET-10 Phase 0 — DR verdicts
measured"). **BUG-423, BUG-424 and BUG-425 landed afterwards.** Whether one of those changed member
completion's lookup strings — making this a regression — or whether the spike was committed red has
not been determined and should be, before deciding which of the two questions above to act on.

## Why it matters beyond the one test

A red full suite is a broken gate for everything else: the next real regression lands against a
baseline that is already failing, and "1 failed" stops being a signal. Per the standing rule that a
feature's gate is regression-relative, this must be resolved rather than carried.

## Fixed (2026-08-08) — the completion was right; the harness read a line where a name goes

Every question above is answered by measurement, and all three possible answers were wrong, because
the premise was: **`wx.wxID_ANY` was never a lookup string.** `completionsFor`'s fallback returns
`document.substringBefore('\n').trim()` — the whole first line — and the line after a single perfect
match auto-inserts still holds the receiver. The list `[wx.wxID_ANY]` is one element long because it
is one *line* long.

A probe against the same fixture, reading the elements themselves:

```
library  wx.<caret>        elements=[wxFrame, wxID_ANY, wxID_OK]     <- bare, and once each
library  wx.wxID_<caret>   elements=[wxID_ANY, wxID_OK]              (two constants seeded)
library  wx.wxID_<caret>   elements=null  document=wx.wxID_ANY       (one constant — auto-inserted)
project  wx.<caret>        elements=[wxFrame, wxID_ANY, wxID_OK]     <- identical by source
```

So: no qualified lookup string exists (`LuaMemberLookup.create` is the only construction site and
keys off the bare suffix name), insertion is `wx.` + `wxID_ANY` and never `wx.wx.wxID_ANY`, and
library and project sources agree. The asymmetry with the passing `f:<caret>` half is the *prefix*,
not the receiver: an empty prefix lets the keyword provider contribute elements too, so nothing
auto-inserts and the real list is returned.

**Committed red, not a regression.** The spike was re-run at `e5b802a0` itself, the commit that
introduced it, with the probe alongside: `testDr06SingleFileLayout` fails there with the identical
`Found: [wx.wxID_ANY]`, and all four probe lines are byte-identical to today's. BUG-423/424/425
changed nothing here. The DR-06 verdict recorded in TARGET-10's
[risks-and-gaps.md](../../target/10-wxlua-definition-libraries/risks-and-gaps.md) — "one
self-contained file per namespace *does* resolve" — was read off the `println`, and stands: the
auto-insert of `wxID_ANY` is itself proof the constant resolved.

### The fix

`completionsFor` moves to `LibraryRootTestCase`, where both copies of it should have been, and its
fallback now recovers the identifier **ending at the caret** rather than the line. `TargetTenDrSpike`
and `LuaLibraryGlobalCompletion` drop their private copies; the report's "harness disagrees with
itself" observation is settled by tightening DR-06c's `any { it.contains(…) }` to the same exact
membership the other assertions use, which is meaningful now that the elements are names.

The spike is a throwaway, so the behaviour it accidentally raised is pinned where it will outlive it:
`LuaLibraryMemberCompletionTest` asserts the offered set exactly (bare, no duplicates — a duplicate
is what would suppress the auto-insert), the inserted document text, and the same for a project-file
declaration. Mutation-proofed: qualifying the lookup string in `LuaMemberLookup` turns all three red,
and the insertion assertion fails with exactly the `wx.wx.wxID_ANY` this report predicted, while
restoring the line-wide fallback turns the DR-06 assertion red again.

## Gating notes

`--rerun --no-build-cache` is required; without it `:test` is served FROM-CACHE and reports a pass
having run nothing. Gate on the **full** suite — an isolated `--tests *TargetTen*` run can pass while
the full suite fails.
