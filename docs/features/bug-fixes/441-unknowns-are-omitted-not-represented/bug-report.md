---
id: "BUG-441"
title: "An unknown write vanishes instead of widening, so the model lies about its own completeness"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "low"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-441: unknowns are omitted, not represented

Carved out of [[BUG-419]] on 2026-08-14, unchanged in substance. BUG-419 shipped the two defects that
carried its measured value (defect 3, the declared-demand gate; defect 4, the same gate for arity) and
closed. These two did not ship, deliberately, and are tracked here so a measured-zero item stops
holding a report open.

**Priority is `low` on the numbers and `low` is not the same as wrong** — see "Why this is still
worth doing".

## The two defects, as BUG-419 stated them

### 1. Unknown writes are omitted, so the model lies about its own completeness

```lua
local v = wx.thing        -- unresolved stem → contributes NO write node at all
if cond then v = "s" end  -- contributes a string write
count(v)                  -- @param n number
```

`v`'s reaching definitions become `{string}` — the unknown write *vanished* rather than widening the
set. The certainty rule then counts one non-declared write, calls the flow **certain**, and errors,
when the honest answer is "v may be `wx.thing`, which is unknown". Node-lessness is stronger erasure
than `Undefined`: identical for direct absorption, but for union formation and certainty counting,
**absence ≠ unknown**.

### 2. A union carrying an `Undefined` arm is not gradual

`local v = wx.thing or "s"` → `Undefined | string`. Bare `Undefined` absorbs every check
(`checkCompatibility` early-returns), but as a *union arm* it does not: the informative-arms filter
removes only `Nil`, the string arm mismatches a `number` demand, and the engine errors. `Undefined`
means "could be anything" — in a union it must be gradual, exactly as `Any` already is (BUG-397
Phase 1).

## They are ONE change, and the order is forced

BUG-419's probe measured defect 2's exposure at **zero across all four corpus members**, and the
reason is structural rather than lucky: `VariableElement.resolveWrite`'s `flatten` drops `Undefined`
(`else if (type != Undefined)`) *before* a union can form, so "union carrying an `Undefined` arm" is
currently **unreachable**.

**So fixing (1) alone would CREATE the exposure that measures zero today**, converting silent erasure
into new false positives. (2) is dead code until (1) lands, and (1) must not land without it.

## Why this is still worth doing

The corpus payoff is near zero — with the declared-demand gate in place only 3 emissions corpus-wide
were affected — and that is exactly why it was deferred twice. The case for it is correctness, and it
is the same case in both directions:

- **It is criterion 1 of BUG-419's own emission rule.** *Unknown-free provenance* is unimplemented, so
  the engine's stated licence to speak is still broader than what it can actually justify. What
  currently keeps that honest is the declared-demand gate, not the provenance rule.
- **An unknown write dropped can make a flow look certain when it is not.** That is a live wrong
  answer, independent of how often the corpus happens to hit it.

## What a fix has to reckon with

- **The corpus is the gate, and it will move things the probe cannot predict.** BUG-419's arity fix
  raised three other inspections' counts by un-burying findings hidden inside ERROR ranges (the
  BUG-417 effect). Making unknowns viral changes union shapes, which reaches inlays and completion,
  not only diagnostics. Re-baseline once and attribute each movement.
- **Do not size this from a graph-level probe.** Four separate incomparabilities between graph-level
  emission counts and inspection-level baselines are recorded in BUG-419 and BUG-424. Measure at the
  inspection level, by dumping the residual.
- **`Undefined` reaching `resolveRead` is a known trap.** BUG-423/424/426 all hit the shape where a
  demand-side type projected into a read position and changed every inferred hint. The projection
  belongs at the presentation boundary.

## Verification owed

BUG-419's original list, for these two only:

- A fixture for defect 1 showing the omitted write **defeating certainty**, and one for defect 2
  showing the `Undefined`-arm union erroring. Red before, green after, mutation-proved.
- Corpus re-baselined once, with each movement attributed.
- The BUG-417 parity criterion re-run — inspection independence must survive it.
- A declared-contract violation with certain evidence still errors.
