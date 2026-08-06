---
id: "BUG-419"
title: "The type engine reports incompatibility it cannot know: unknowns are omitted, not represented, and inferred demands are checked like contracts"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-419: Incompatibility claims from an incomplete model

An assignability verdict requires knowing **both** sides. The engine currently issues verdicts where
one side — or the model that produced it — is acknowledged to be incomplete. The result is a
structural false-positive source that survives every per-rule fix (BUG-416 removed 72 % of one
member's errors; the residual 997 remain unvalidated), because the defect is in *when the engine is
entitled to speak*, not in any particular rule.

Supersedes the severity-policy follow-up flagged in BUG-417's report: severity was the symptom axis.

## The epistemic situation, measured

zerobrane, post-BUG-416/417 baselines: **1 945 identifiers the engine admits it cannot resolve**,
co-located with **997 assignability errors asserted with ERROR severity**. A file whose name-model
is ~84 % unknown does not support certainty claims about the flows those names touch.

## Three specific defects

### 1. Unknown writes are omitted, so the model lies about its own completeness

```lua
local v = wx.thing        -- unresolved stem → contributes NO write node at all
if cond then v = "s" end  -- contributes a string write
count(v)                  -- @param n number
```

`v`'s reaching definitions become `{string}` — the unknown write *vanished* rather than widening
the set. BUG-416's certainty rule then counts one non-declared write, calls the flow **certain**,
and errors — when the honest answer is "v may be `wx.thing`, which is unknown". Node-lessness is
stronger erasure than `Undefined`: identical for direct absorption, but for union formation and
certainty counting, **absence ≠ unknown**. (Constructed example — the shape is verified in code
paths, the frequency is not; see the probe.)

### 2. A union carrying an `Undefined` arm is not gradual

`local v = wx.thing or "s"` → `Undefined | string`. Bare `Undefined` absorbs every check
(`checkCompatibility` early-returns), but as a *union arm* it does not: the informative-arms filter
removes only `Nil`, the string arm mismatches a `number` demand, and the engine errors. `Undefined`
means "could be anything" — in a union it must be gradual, exactly as `Any` already is (BUG-397
Phase 1).

### 3. Inferred demands are checked as if they were contracts

`frame:SetStatusText(...)` synthesizes a `Table{SetStatusText}` **demand from usage**. When an
inferred value conflicts with an inferred demand, both sides are the engine's own guesses; the
conflict is evidence the model is incomplete, not that the code is wrong. Reporting it as an error
is the engine arguing with itself and blaming the user. Only a **user-declared** expectation — an
annotation, a stub signature, a declared global — is a contract whose violation is a diagnostic.
(This is also LuaLS's observable behaviour: its type errors key off annotations.)

## The emission rule

Report incompatibility only when **all three** hold:

1. **Unknown-free provenance** — no unresolved stem, no `Undefined`, anywhere in the value's
   derivation. Unknowns are *viral and represented*: an unresolved stem contributes an explicit
   `Undefined` write wherever its value flows, so unions widen and certainty counting sees it.
2. **Declared demand** — the expectation traces to something a user wrote. `UseNode` gains the
   `declaredOrigin` flag `ValueNode` already has (BUG-416 built half of this machinery); annotation
   and stub injection sites mark it, usage-synthesized constraints do not.
3. **Certain flow** — BUG-416's rule, now made honest by (1).

Inferred-vs-inferred conflicts stop being user-facing diagnostics. Their proper use is as fuel for
an "annotate this" intention — a hypothesis presented as a suggestion, not an error.

This subsumes the severity question: with the rule in place, what remains *is* contract violation
with certain evidence, for which ERROR severity is defensible.

## Probe first — the prediction is a prediction

Three of three bug reports this week carried premises that measurement refuted, and the two code
examples above are constructed, not observed. Before implementing, classify the residual
assignability errors (997 zerobrane / 478 luarocks / 376 luacheck / 317 penlight) by three flags:

- (a) any unknown in the value's provenance,
- (b) demand provenance (declared vs usage-synthesized),
- (c) flow certainty.

**Prediction, on record**: the majority are inferred-demand cases (b), and the corpus impact of (1)
alone is small because most residual stems are stub-resolved (`io.open`-style). If the probe says
otherwise, the fix order changes — follow the numbers, not this paragraph.

## Verification

- Fixtures for each defect, red before / green after, mutation-proved (defect 1's fixture must show
  the omitted write defeating certainty; defect 2's must show the `Undefined`-arm union erroring).
- Corpus re-baselined once, with the movement attributed per flag from the probe.
- The BUG-417 parity criterion re-run: inspection independence must survive the change.
- A declared-contract violation with certain evidence (`--- @param n number` + literal string)
  still errors — the rule must not swallow real signal.
