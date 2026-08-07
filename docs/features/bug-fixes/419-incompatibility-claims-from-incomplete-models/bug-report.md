---
id: "BUG-419"
title: "The type engine reports incompatibility it cannot know: unknowns are omitted, not represented, and inferred demands are checked like contracts"
type: "bug"
parent_id: "BUG"
status: "in_progress"
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

## Presentation of the hypothesis tier

Demoted inferred-vs-inferred conflicts get a **spelling-class squiggle**, not silence. The platform
mechanism is proven and available: the spellchecker's typo underline is a *custom severity*
registered through the `SeveritiesProvider` EP (`SpellCheckerSeveritiesProvider.java:18` —
`TYPO = INFORMATION.myVal + 5`, own `TextAttributesKey`, own name/colour in settings); Grazie does
the same for grammar. Lunar registers a `TYPE HYPOTHESIS` severity near that rank: faint dotted
underline, no error-stripe weight, own inspection-popup identity, carries the "annotate this"
intention. By the BUG-417 precedence mechanics it sits below every other severity and therefore
cannot bury another inspection's output; whether ERROR ranges hide it is acceptable for a
hypothesis, and is verified rather than assumed when implemented. The invisible
`ProblemHighlightType.INFORMATION` (fix-only, no squiggle) is the fallback if even that proves
noisy on real corpora.

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

### Probe result, 2026-08-07 — prediction confirmed, and the fix ORDER changes

Throwaway instrumentation on `reportIncompatible` (the funnel all assignability messages reach),
classifying every emission across all four corpus members. Reverted after measuring.

| member | errors | demand declared | demand inferred | value has `Undefined` arm | flow certain | **survives the rule** |
| :-- | --: | --: | --: | --: | --: | --: |
| zerobrane | 4 452 | 3 (0.1 %) | 4 449 (99.9 %) | **0** | 1 468 (33.0 %) | **3** |
| luarocks | 922 | 0 | 922 (100 %) | **0** | 265 (28.7 %) | **0** |
| luacheck | 950 | 0 | 950 (100 %) | **0** | 25 (2.6 %) | **0** |
| penlight | 1 109 | 0 | 1 109 (100 %) | **0** | 188 (17.0 %) | **0** |
| **total** | **7 433** | **3 (0.04 %)** | **7 430** | **0** | — | **3** |

Counts are graph-level `reportIncompatible` emissions, *before* the inspection layer's file-wide-anchor
drop and dedup — so they are not comparable to the 997/478/376/317 baselines. The **ratios** are the
finding.

**1. Defect 3 is the whole thing.** 7 430 of 7 433 emissions check an inferred demand against an
inferred value. The prediction said "the majority"; it is 99.96 %. On un-annotated real-world Lua the
assignability inspection is, essentially in its entirety, the engine arguing with itself.

**2. Defect 2 has ZERO exposure — because defect 1 masks it.** No emission anywhere carried an
`Undefined` union arm, and the reason is structural rather than lucky:
`VariableElement.resolveWrite`'s `flatten` drops `Undefined` (`else if (type != Undefined)`) *before*
a union can form, so "union carrying an `Undefined` arm" is currently **unreachable**. Defect 2 is
real as written, but it is downstream of defect 1.

**This inverts the implementation order.** Fixing (1) in isolation — making unknowns viral and
represented — would *create* the defect-2 exposure that measures zero today, converting silent
erasure into new false positives. (1) and (2) are one change, not two, and (2) is dead code until
(1) lands.

**3. Defect 1 is pervasive but its payoff is now small.** Dropped `Undefined` writes: 6.7 M
(zerobrane), 20.0 M (luarocks), 0.7 M (luacheck), 1.5 M (penlight) — counted per `flatten` call, so
repeated resolutions inflate it and it is an intensity signal, not a site count. Yet with (3) in
place only 3 emissions survive corpus-wide, so (1) can affect at most those 3. It remains a
correctness fix — an unknown write dropped can make a flow look certain when it is not — but its
*corpus* value is now near zero, which is the opposite of how the report weighted it.

### Revised fix order

1. **Defect 3 first, alone.** Gate emission on `UseNode.declaredDemand` (annotation/stub injection
   sites in `LuaTypeGraphBridge` mark it; the usage-synthesized demands in `LuaTypesVisitor` do not).
   This delivers 100 % of the measured benefit and is the smallest change of the three.
2. **Defects 1 + 2 together, afterwards**, on correctness grounds rather than corpus impact, and
   only with (2) in place to catch what (1) makes reachable.
3. Certainty (c) is not a discriminator on its own — it ranges 2.6 %–33 % across members and, with
   (3) gating, applies to a handful of emissions.

### The blast radius — predicted 7 433 → 3, ACTUAL 2 168 → 907

The probe's ratio said the rule would take output from 7 433 to 3, i.e. switch the inspection off for
un-annotated code. **Implemented, the measured result is a 58 % reduction, not ~100 %:**

| member | before | after | |
| :-- | --: | --: | --: |
| zerobrane | 997 | 358 | −64 % |
| luarocks | 478 | 213 | −55 % |
| luacheck | 376 | 201 | −47 % |
| penlight | 317 | 135 | −57 % |
| **total** | **2 168** | **907** | **−58 %** |

Two reasons the probe over-stated it, both worth keeping:

1. **7 433 was graph-level**, before the inspection layer's file-wide-anchor drop and dedup. The
   caveat was recorded and then the ratio was quoted anyway as if it transferred. It does not.
2. **The probe defined "declared" as annotation-only** — and that was wrong, which is the
   substantive correction below.

### Correction to defect 3: not every inferred demand is a guess

The report frames defect 3 around `frame:SetStatusText()` synthesizing a `Table{SetStatusText}`
demand. That generalises too far. `a .. b` demanding a string is **not** the engine inferring
anything — it is a rule of Lua, and `attempt to concatenate a boolean value` is a runtime error no
matter what anybody annotated. There are three kinds of demand, not two:

| demand | declared by | verdict |
| :-- | :-- | :-- |
| `---@param n number` | the user | contract → **ERROR** |
| `a .. b` requires a string | **the language** | contract → **ERROR** |
| `f()` requires `fun()`, `x.k` requires `Table{k}` | the engine's inference | guess → **HYPOTHESIS** |

Caught by two existing tests — `testBooleanConcatMismatchReported` and
`testGenuineNilConcatHighlightedOnce` — which turned out to encode genuine Lua semantics rather than
stale policy. The first implementation demoted both and would have silently stopped reporting a real
runtime error.

So the shipped rule is materially more conservative than the design implied, and better for it:
violations of Lua's own rules and of user annotations still error; only demands the engine invented
from usage are demoted.

## Status — defect 3 SHIPPED, the report is NOT closed

Marked `done` on 2026-08-07 and reopened the same day: two of the four verification items below were
never completed, and later measurement showed the thesis is not met.

**Shipped** (`31d9c761`): defect 3. `UseNode.declaredDemand`, the ERROR/HYPOTHESIS split, the
inspections skipping hypotheses, `LuaTypeHypothesisAnnotator` with the annotate-it intention. Corpus
2 168 → 907. A declared-contract violation still errors, and that criterion caught a real bug
(declared `@param` demoted through every call site).

**Not shipped**: defects 1 and 2, deliberately — the probe showed defect 2 has zero exposure because
defect 1 masks it, so they are one change and neither is urgent once defect 3 gates. That deferral
stands.

**Not done, and the reason it matters**: the BUG-417 parity criterion was never re-run. An attempt
(2026-08-07) produced `undeclaredAlone=0 withTypes=0` — vacuous, because the probe did not reproduce
the sweep's module-root/sourcePath setup and so measured nothing. Recorded as unmet rather than
passed. This change moved most type errors out of ERROR severity, which is precisely the lever
BUG-417 was about, so the criterion is *more* relevant here than usual, not less.

**The thesis is not met.** "The engine may only claim incompatibility it can know" — it still emits
655 LPeg claims it cannot know (BUG-424). Defect 3 classified operator demands as *language
contracts*, which is right in principle, but the language model behind them is wrong about
metamethods. So the ERROR tier is still carrying a large class the engine has no basis for.

Closing this report requires: the parity criterion actually measured, and either BUG-424 landing or
an explicit decision that the 655 are acceptable in the ERROR tier.

## Verification

- Fixtures for each defect, red before / green after, mutation-proved (defect 1's fixture must show
  the omitted write defeating certainty; defect 2's must show the `Undefined`-arm union erroring).
- Corpus re-baselined once, with the movement attributed per flag from the probe.
- The BUG-417 parity criterion re-run: inspection independence must survive the change.
- A declared-contract violation with certain evidence (`--- @param n number` + literal string)
  still errors — the rule must not swallow real signal.
