---
id: "BUG-419"
title: "The type engine reports incompatibility it cannot know: unknowns are omitted, not represented, and inferred demands are checked like contracts"
type: "bug"
parent_id: "BUG"
status: "done"
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

## Status — defects 3 and 4 shipped; 1 and 2 carved out to [[BUG-441]]; CLOSED

Marked `done` on 2026-08-07 and reopened the same day: two of the four verification items below were
never completed, and later measurement showed the thesis is not met.

**Shipped** (`31d9c761`): defect 3. `UseNode.declaredDemand`, the ERROR/HYPOTHESIS split, the
inspections skipping hypotheses, `LuaTypeHypothesisAnnotator` with the annotate-it intention. Corpus
2 168 → 907. A declared-contract violation still errors, and that criterion caught a real bug
(declared `@param` demoted through every call site).

**Not shipped**: defects 1 and 2, deliberately — the probe showed defect 2 has zero exposure because
defect 1 masks it, so they are one change and neither is urgent once defect 3 gates. That deferral
stands.

**Now done** (2026-08-07, second attempt): the two verification gaps above. See
"Verification, completed" below. Neither changed the verdict on defect 3 — the parity criterion
passes and the ERROR path proved to be guarded everywhere but one line — but both turned up
something the report did not know, and one of them is a new bug.

**The thesis is not met.** "The engine may only claim incompatibility it can know" — it still emits
655 LPeg claims it cannot know (BUG-424). Defect 3 classified operator demands as *language
contracts*, which is right in principle, but the language model behind them is wrong about
metamethods. So the ERROR tier is still carrying a large class the engine has no basis for.

Closing this report now requires only the metamethod question: **BUG-424 landing, or an explicit
decision that the 655 are acceptable in the ERROR tier.**

> **Both sentences above are wrong, and the paragraph that follows them is the correction.** The
> thesis was indeed unmet — but not because of LPeg, and the stated closing condition could never
> have closed anything. See "Defect 4" below, which measured the residual instead of reasoning about
> it. Left in place rather than edited away: the mistake is the same one this report has now made
> four times, and deleting the evidence of it is how it keeps recurring.

## Defect 4 — arity is an inferred demand, and it never passed through defect 3's gate

Found 2026-08-14 by dumping the residual rather than characterising it from a previous probe.

### The stated blocker was the wrong number, for four independent reasons

BUG-424 landed (`ef48c172`, status `done`), which by the condition above should have closed this.
It does not, and the 655 could not have settled it either way:

1. **Graph-level vs inspection-level.** Recorded in this report and in BUG-424's own correction.
2. **The probe defined "declared" as annotation-only.** Recorded above.
3. **BUG-424 re-measured its own arm at 27 emissions, not 655.** Recorded there.
4. **NEW — the 655 are not in the swept file set at all.** zerobrane's corpus roots are
   `src, interpreters, api, cfg` — 34+13+8+17 = the `files=72` the baseline records. Scintillua's
   133 LPeg lexers live in `lualibs/lexers`, which is **not swept**. Of the 72 files measured, two
   mention LPeg and both merely *load* a lexer. So the corpus gate is structurally incapable of
   observing that class, and no amount of metamethod work was ever going to move it. The class is
   still real for a user who opens those files; its size is simply not a fact this baseline knows.

### What the residual actually is

Every `LuaTypeAssignability` emission dumped at the inspection level with its file, line, message and
source line — per-member counts matched the baselines exactly (278/144/201/91), so this is the
residual itself and not a proxy:

| | count | share |
| :-- | --: | --: |
| **Arity** — "Too few/many arguments" | **629** | **88 %** |
| Assignability | 85 | 12 % |

`string -> number`, the LPeg shape the blocker rests on: **2 emissions.**

### Why arity escaped

`LuaTypeGraph.checkFunctionCompatibility` calls `addError` **directly**, with a hardcoded
`ErrorSeverity.WARNING`. It never reaches `reportIncompatible`, so defect 3's `declaredDemand`
gate — the entire subject of this report — was never applied to it, and neither was BUG-417's
file-wide anchor guard. The inspection skips `HYPOTHESIS` but reports `WARNING`, so all 629 shipped.

And they are claims the engine cannot know, in the strict sense this report means. **Lua adjusts
arguments to parameters**: missing ones become `nil`, extra ones are discarded (Reference Manual
§3.4.10). `isOptional` is set only by `---@param x?`, so in un-annotated code every parameter is
inferred required. `cfg/tomorrow.lua` alone produced **157** of zerobrane's 246, all against:

```lua
local function H(c, bg) c = c:gsub('#','')
  local bg = bg and H(bg) or {255, 255, 255}   -- the author handles the nil, on the first line
```

called as `H'8e908c'` throughout — the engine contradicting, 157 times, a fact the source states.

### The fix

`LuaGraphType.Function.declaredSignature`, set where a signature is genuinely user-written
(`---@param` tags in `LuaTypesVisitor`, a `fun(...)` literal in `TypeParser`, a stub signature in
`LuaTypeManagerImpl`) and defaulting **false** everywhere else — the safe direction, since a
signature wrongly marked declared produces exactly the false ERROR this removes. `graphTypeToLuaType`
rebuilds a `LuaFunctionType` from an *inferred* graph function and deliberately keeps the default.
Arity then emits `WARNING` against a declared signature and `HYPOTHESIS` against an inferred one.

Arity hypotheses carry no `inferredValueType` and `LuaTypeHypothesisAnnotator` now skips them: the
remedy for an arity guess is a `---@param` on the **declaration**, not the `---@type` that fix
scaffolds at the call statement. Pointing the user at the wrong element is worse than staying silent.

### Measured: 714 → 85 corpus-wide (−88 %)

| member | `LuaTypeAssignability` | `LuaReturnTypeMismatch` |
| :-- | --: | --: |
| zerobrane | 278 → **32** | 16 → 9 |
| luarocks | 144 → **7** | 19 → 6 |
| luacheck | 201 → **0** | 6 → 0 |
| penlight | 91 → **46** | 15 → 6 |
| **total** | **714 → 85** | **56 → 21** |

The 85 is exactly the assignability remainder the dump identified, so the prediction and the
measurement agree — which is the first time in this report's history that has been true, and only
because the prediction came from dumping the residual rather than re-deriving it.

**The return-mismatch drop was not predicted.** `TypeErrorClassification.isReturnRelated()` partitions
the *same* error list between the two inspections, so arity emissions inside a `return` were counted
in the other bucket — outside the dump, which filtered on `LuaTypeAssignability`. Total arity demoted
is ~664, not 629. Recorded because it means the dump was not a complete census of the check.

### Three counts ROSE, and that is the BUG-417 effect, not a regression

luarocks `LuaSuspiciousConcatenation` 115→116; zerobrane `LuaShadowingVariable` 218→219 and
`LuaUndeclaredVariable` 1951→1952. These are other inspections' findings that were **buried inside
arity ERROR ranges** by the platform's severity precedence; removing the range reveals them. The
symbol maps show it on named symbols rather than in aggregate — `symbol.LuaUndeclaredVariable.wx`
1547→1548, `symbol.LuaSuspiciousConcatenation.{ ... }` 63→64 — and `LuaUndeclaredVariable` moves
*toward* the 1954 BUG-417 measured with the type inspection disabled. Inspection independence
improved. Baselines re-recorded.

### Verification

`LuaInferredArityTierTest`, 9 fixtures, both tiers in both directions. Mutation-proved — each
mutation taking exactly the fixtures that should own it red, and nothing else:

| mutation | went red |
| :-- | :-- |
| tier → always `WARNING` | the 4 inferred fixtures |
| tier → always `HYPOTHESIS` | the 3 declared fixtures + the pre-existing `testArityTooFewReported` |
| `LuaTypesVisitor` flag → `false` | the 3 declared fixtures + `testArityTooFewReported` |
| `TypeParser` flag → `false` | `testDeclaredFunctionTypeAnnotationStillWarns`, alone |

**Two things are measured as uncovered and recorded rather than described as tested:**

- **`LuaTypeManagerImpl`'s flag** (the stub / `@class` method path). Mutating it to `false` and
  running the **entire** suite left everything green. It is reasoned, not tested.
- **`FunctionSignatureMatchingTest`'s two arity tests are not tier coverage**, despite looking like
  it. They assert on the message and ignore severity, so they stayed green under *both* tier
  mutations. Anyone reading them as the arity gate would be wrong.

### The BUG-417 parity criterion does not merely survive — it reaches EXACT parity

```
[parity:zerobrane] files=72 withTypes=1954 withoutTypes=1954 baseline=1952
[parity:zerobrane] filesAtExactParity=72/72
```

Against the measurement recorded above for defect 3 — `withTypes=1948 withoutTypes=1954`,
`filesAtExactParity=70/72` — the type inspection now buries **nothing** on zerobrane.

That closes a residual this report had explained away. It read the leftover 6 as "refs inside narrow,
correctly-anchored ERROR ranges: the platform's by-design severity precedence". The anchoring was
indeed correct and the precedence is indeed by design — but the ranges were **arity claims that
should never have been made**, so the 6 were not a platform limit at all. A true statement about the
mechanism was doing duty as an explanation for a defect. Worth remembering: "by-design behaviour"
explains why a symptom *appears*, never why the input to it was justified.

Full suite green; corpus green against the re-recorded baselines; ktlint clean.

## Verification, completed (2026-08-07)

### The BUG-417 parity criterion — PASSES, and is now a test

Re-run properly, as `LuaCorpusInspectionParityTest` (`*Corpus*`, so `-PwithCorpus`) rather than a
throwaway. zerobrane, `LuaUndeclaredVariable` with and without `LuaTypeAssignabilityInspection`:

```
files=72  withTypes=1948  withoutTypes=1954  baseline=1945
filesAtExactParity=70/72
src/editor/commands.lua  withTypes=54  withoutTypes=55
src/util.lua             withTypes=71  withoutTypes=76
```

Identical to BUG-417's post-fix measurement — 1 948 / 1 954, 70 of 72 — so the hypothesis tier did
not disturb inspection independence. The residual 6 are refs inside narrow, correctly-anchored ERROR
ranges: the platform's by-design severity precedence, not the file-wide class BUG-417 removed.

The first attempt's failure mode is designed out rather than remembered. `assertAnchored` requires
the measured total to sit within 25 of the ratchet's recorded baseline, so a probe measuring a
different tree — the `0 vs 0` that read as perfect parity — fails instead of passing vacuously.

### Mutation proof of the ERROR path — one line is measurably unguarded

`LuaDeclaredContractErrorTest` adds fixtures for the sites the single pre-existing fixture did not
cover, and every guard was flipped in turn:

| mutation | went red |
| :-- | :-- |
| tier split → always `HYPOTHESIS` | 9 tests across 4 classes |
| `@param` injection site → `false` | 4 |
| `@type` injection site → `false` | 3 |
| `@return` injection site → `false` | 1 |
| operator sites → `false` | `testBooleanConcatMismatchReported`, `testGenuineNilConcatHighlightedOnce` |
| `VariableElement.declaredDemand` → `false` | the two variable-mediated `@param` fixtures |

**One survived**: replacing `resolveDeclaredDemand`'s *recursion* with a one-hop lookup leaves every
fixture green, including deliberately-constructed two-variable chains. A value propagates all the
way into the parameter variable, so the declared use node is always exactly one hop from wherever
the check lands. The recursion is insurance against a shape that does not occur today; it is
recorded as untested rather than described as covered, and the harness was proved live with a
`false` canary in the same invocation shape.

### And a new bug: BUG-425

The fixtures were extended to the shape TARGET-10 will generate — a contract declared in a
*definitions file* — and it produces no diagnostic at all, not even the arity warning. Out-of-file
signatures never reach the type graph. That is filed separately as **BUG-425**; it also means this
report's "3 declared-demand emissions across all of zerobrane" undercounts for a second reason
beyond un-annotated code.

## Verification (original list)

- Fixtures for each defect, red before / green after, mutation-proved (defect 1's fixture must show
  the omitted write defeating certainty; defect 2's must show the `Undefined`-arm union erroring).
  **Done for defect 3**, above; defects 1 and 2 are not implemented, so their fixtures are not owed.
- Corpus re-baselined once, with the movement attributed per flag from the probe.
- The BUG-417 parity criterion re-run: inspection independence must survive the change.
- A declared-contract violation with certain evidence (`--- @param n number` + literal string)
  still errors — the rule must not swallow real signal.

All four are now met, for defects 3 and 4 together.

## Closing (2026-08-14)

The thesis — *the engine may only claim incompatibility it can know* — is met **for what the corpus
gate can see**. Across the four members the assignability inspection emits 85 findings where it once
emitted 2 168, and every survivor reaches the user through the declared-demand gate.

Two limits on that sentence, both deliberate:

- **Criterion 1 of the emission rule is still unimplemented.** Unknowns are omitted rather than
  represented (defect 1), and a union carrying an `Undefined` arm is not gradual (defect 2). The
  probe measured their combined corpus exposure at zero, and defect 1 masks defect 2 so they are one
  change; that deferral has stood since 2026-08-07 and still stands. Carved out as **[[BUG-441]]**
  rather than left holding this report open — a report that stays open for a measured-zero item is
  how this one accumulated four wrong closing conditions.
- **"What the gate can see" is a real qualifier, not a hedge.** The LPeg class lives in files the
  sweep does not visit, and nothing here establishes its size. That is now a property of the corpus
  manifest, and belongs to whoever widens it.
