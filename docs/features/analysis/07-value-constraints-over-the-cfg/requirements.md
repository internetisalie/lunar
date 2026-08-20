---
id: ANALYSIS-07
title: "07: Value Constraints over the Control-Flow Graph"
type: feature
parent_id: ANALYSIS
status: planned
vf_icon: 📋
priority: medium
folders:
  - "[[features/analysis/requirements|requirements]]"
---

# Value Constraints over the Control-Flow Graph (`ANALYSIS-07`)

> **Planning state, 2026-08-20.** This feature is **planned only as far as its de-risking spike.**
> [[ANALYSIS-07-DESIGN|design.md]] §2–§4 fully specify Phase 0 — the spike, its harnesses and the
> decision procedure that selects direction **A**, **B** or **C** from its output. §5–§7 (the
> implementations of `-02`/`-03`/`-04`) are **deliberately unwritten**: each is gated on a named DR,
> because a design written before that measurement would be a guess about which of three
> architectures is correct. The feature front-matter therefore stays `todo`, not `planned` — the
> bar is cleared for Phase 0 and for nothing after it. See
> [[ANALYSIS-07-RISKS|risks-and-gaps.md]] for the DR table.

Filed 2026-08-20 out of three independent defects that turned out to share one shape; the point of
the record is that the shape has now been *measured* three times and should stop being rediscovered
one bug at a time.

## The situation

Lunar has **two flow analyses, and they do not talk to each other.**

| | [[ANALYSIS-06]] — the CFG | The type engine (`LuaTypeGraph`) |
| :-- | :-- | :-- |
| shape | a real control-flow graph over `ScopeOwner`s | a constraint graph over value/use nodes |
| knows | reachability; `LuaReadWriteInstruction` for every local read, write and implicit global | a rich value domain: biunification, `upSet`/`downSet`, unions, traits |
| lacks | **any value domain** | **any control-flow graph** |
| consumers | exactly one — `LuaUnreachableCodeInspection` | every inspection, inlay, completion and hover |

The "exactly one consumer" claim is **verified, not repeated**: `grep -rn "analysis.controlflow" src/`
returns four production files (the package itself) plus exactly two importers —
`LuaUnreachableCodeInspection.kt:13-15` and the test `LuaControlFlowTest.kt:5`. No other production
class imports the package.

[[TYPE-08]] considered joining them and declined, on the record (`docs/features/type/08-flow-sensitive/design.md:331-335`):

> **ControlFlow-based approach**: use `LuaControlFlowBuilder` to annotate the CFG with guard
> information, then query it during type resolution. **Rejected** because the type engine is purely
> tree-walk (no CFG integration), and wiring them together would require a much larger refactor.

That was a reasonable call for TYPE-08's scope — single-pattern type guards via scope injection —
and it is the decision this feature would revisit, with evidence TYPE-08 did not have. **The
estimate is unmeasured.** `ANALYSIS-07-01` exists to measure it; nothing in this feature inherits it
and nothing contradicts it without a number.

## Why now: three measured defects, one shape

Each of these was diagnosed independently and each turned out to be the engine reasoning about
values with no graph to hang them on.

| defect | what it needed | what it got |
| :-- | :-- | :-- |
| [[BUG-441]] | reaching definitions — "does any *other* definition of this variable reach here unaccountably?" | `currentUpSet.any { … }` over the constraint graph's own approximation (`LuaTypeGraph.kt:352-353`). The CFG already models exactly these reads and writes (ANALYSIS-06-02) and was never asked |
| [[BUG-435]] | narrowing inside `if type(x) == "table"` — a value constraint holding on one **subgraph** | TYPE-08's scope injection (`LuaTypesVisitor.kt:464-478`), which does not reach table members; the guarded variable offers no members at all |
| [[BUG-428]] residual | context sensitivity — `check_cnfg(var, def)` unions **every** call site's `def`, so `list_delim` types as `boolean \| string` where the value is plainly a string | one type per function, call-site-blind. 2 sites survive at the declaration anchor after BUG-441 |

BUG-441 is the sharpest illustration. Its RC-2 computes "is this variable's provenance
accountable?" — a textbook reaching-definitions query — by scanning the constraint graph's upSet,
because that is the only place the information exists in a form the diagnostic path can reach.

## The measured headroom, and what it means for how this is gated

Read off the **committed corpus baselines** at `99b45f92` (`src/test/resources/corpus/*.baseline`),
i.e. the state left by BUG-441:

| corpus | files | `LuaTypeAssignability` | `LuaReturnTypeMismatch` | `LuaUnreachableCode` |
| :-- | --: | --: | --: | --: |
| luacheck | 132 | 0 *(absent)* | 0 *(absent)* | 1 |
| luarocks | 159 | 5 | 0 *(absent)* | 0 *(absent)* |
| penlight | 56 | 5 | 1 | 2 |
| zerobrane | 72 | 0 *(absent)* | 0 *(absent)* | 2 |
| **total** | **419** | **10** | **1** | **5** |

**Consequence, and it shapes the whole plan:** BUG-441 took the two type inspections from 100 sites
to 11 across 419 files. The corpus can therefore no longer *select* anything for this feature —
there is almost nothing left to move. It remains the **guard** (nothing may get worse), and the
**selector** must be a targeted probe. That is the difference between this plan and a plan that
proposes to "measure it on the corpus".

It also raises a premise this feature must answer before building anything: **if the corpus has 11
type-error sites left, is `ANALYSIS-07-02` worth building at all?** The answer this plan pursues is
that `-02`'s value is *precision*, not further suppression — BUG-441's gate over-approximates
(any unknown **anywhere** in the `upSet`, whether or not it reaches the use), so a true
reaching-definitions query should **restore** checking at sites BUG-441 silenced. That is
measurable, and it is `ANALYSIS-07-00-DR-02`. If it restores nothing, the feature re-scopes — see
the decision procedure's branch **D0** (design **§3.7**).

## What a solution has to be, and what it must not be

**It must not be a third analysis.** Two uncoordinated flow analyses is the problem being reported;
adding a third makes it worse. The work is to give one of them what the other has, and the direction
is an open question this feature exists to answer:

- **A — teach the CFG a value domain.** Abstract interpretation over `LuaControlFlow`, with the type
  engine's lattice as the domain. Natural home for reachability-conditioned facts.
- **B — give the type engine the CFG.** What TYPE-08 rejected; its cost estimate ("a much larger
  refactor") is unmeasured and should be measured rather than inherited.
- **C — a narrow bridge.** Expose the CFG's `LuaReadWriteInstruction` stream as a
  reaching-definitions service the type engine may consult at specific decision points (BUG-441's
  gate being the first), without merging the two.

C is the cheapest and the least likely to pay off structurally; A and B are both large. **Sizing
this from a reading would repeat exactly the mistake BUG-441's own report records**: its "one change,
the order is forced" framing survived contact with a runtime probe, and its mechanism did not.

**That sentence is this document's paraphrase, not a quotation** — flagged because
[[ANALYSIS-07-DESIGN|design.md]] once presented it as BUG-441's own words and it is not
(`grep -rn "survived contact" docs/features/bug-fixes/441-*/bug-report.md` → no match). BUG-441's
actual wording, at
`docs/features/bug-fixes/441-unknowns-are-omitted-not-represented/bug-report.md:84-86`, is:
*"So the order is forced for a different reason than recorded: **(1) is the root** … The "one change"
conclusion survives; the mechanism behind it did not."* — correcting its own heading at `:47`,
"They are ONE change, and the order is forced". The paraphrase says the same thing; it is simply
not a quote, and this plan does not get to invent one.

## In Scope

- A measured selection between A, B and C, recorded with the numbers that selected it (`-01`).
- A reaching-definitions query whose first consumer is BUG-441's `unknownProvenance` gate (`-02`).
- Branch-scoped value constraints that reach members, the [[BUG-435]] shape (`-03`, Should) —
  **only if** `ANALYSIS-07-00-DR-10` shows the defect is not simply a wrong node installed by
  `injectNarrowedBinding`.
- Call-site sensitivity, the [[BUG-428]] residual (`-04`, Could) — **only if**
  `ANALYSIS-07-00-DR-11` confirms the 2 residual sites are that shape.
- Coupling or subsumption of the two analyses, never a third (`-05`).

## Out of Scope (non-goals)

- **Rewriting `LuaUnreachableCodeInspection`, which works.** [[INSP-04]]'s deferred `INSP-04-C1`
  (`error()`/`os.exit()` as terminators) and `INSP-04-C2` (`while true` as non-terminating) are
  **CFG-builder** changes that would ride along naturally, but neither justifies this on its own.
- **Any new user-visible inspection.** This is engine work whose payoff is existing diagnostics
  being right more often. No `plugin.xml` `<localInspection>` is added by this feature — see design
  §7.
- **`displayName()` / presentation changes.** BUG-441 scoped these out and so does this; if a wider
  type reads noisily in an inlay, the answer is a presentation-boundary projection (BUG-424's
  precedent in `LuaTypes.typeOf`), not a weaker graph.
- **Reintroducing `LuaBranchInstruction`.** It was deleted by MAINT-31
  (`docs/features/maint/31-dead-code-sweep/design.md:40-41` — the sentence *"`LuaBranchInstruction`
  is deleted — MAINT-29 can reintroduce it if it uses it for condition nodes."* begins at `:40` and
  ends at `:41`; `:39` is the `DebugCommandKind.EXIT` clause of the same bullet) as never-used, with
  the note that
  MAINT-29 could reintroduce it for condition nodes; MAINT-29 shipped without it. If direction A is
  selected, a condition-carrying instruction is part of A's design and is named there, not
  pre-committed here.

## Requirements Table

| ID | Requirement | Priority | Status | Description |
|---|---|---|---|---|
| ANALYSIS-07-01 | Decide the direction | Must | Not Implemented | Choose A, B or C above **from a measurement**, not from a reading, by executing the Phase 0 spike (design §2–§3) and applying the decision procedure (design **§3.7**) to its output. TYPE-08's "much larger refactor" is an unmeasured estimate and is what `ANALYSIS-07-00-DR-06`…`-09` measure. |
| ANALYSIS-07-02 | Reaching definitions as a service | Must | Not Implemented | A queryable "what definitions reach this use, and is any of them unaccountable?" built on ANALYSIS-06-02's `LuaReadWriteInstruction` stream. First consumer: [[BUG-441]]'s `unknownProvenance` gate (`LuaTypeGraph.kt:352-353`), which today approximates it over `upSet`. **Design deferred** to the outcome of `-01`; **acceptance is not deferred** — see TC-2a…TC-2e. |
| ANALYSIS-07-03 | Constraints scoped to a subgraph | Should | **Cancelled** | A value constraint that holds on one branch and not its sibling, reaching **members** and not only the bare type — the [[BUG-435]] shape. **Gated on `ANALYSIS-07-00-DR-10`**, which may descope it entirely.  **CANCELLED 2026-08-20 by DR-10** — measured as a wrong node at `injectNarrowedBinding:464-478`, not a missing capability; shipped as [[BUG-435]]. See risks-and-gaps' DR-10 entry for the dump. |
| ANALYSIS-07-04 | Call-site sensitivity | Could | Not Implemented | A parameter's type at one call site not polluted by every other. [[BUG-428]]'s residual; the largest and least certain of these. **Gated on `ANALYSIS-07-00-DR-11`**. |
| ANALYSIS-07-05 | One analysis, not three | Must | Not Implemented | Whatever ships, the CFG and the type engine must end up coupled or one subsumed — not joined by a third flow analysis. Enforced by TC-5a, a structural assertion, not by review. |

### Non-functional

| ID | Requirement | Priority | Description |
|---|---|---|---|
| ANALYSIS-07-NFR-1 | Type-graph build cost | Must | Whatever `-02` ships must add **≤ 10 %** to the median cold `LuaTypesSnapshot.forFile` build on the same file, medians of 5. `forFile` is on the completion path and [[COMP-09]]'s NFR-1 budgets time-to-first at 100 ms; a reaching-definitions pass that is not free spends that budget. Baseline and delta are `ANALYSIS-07-00-DR-08`. |
| ANALYSIS-07-NFR-2 | Cancellation | Must | Any new traversal over `LuaControlFlow` or the instruction array must call `ProgressManager.checkCanceled()` at the head of each iteration (engineering contract §2). **Today neither `LuaControlFlowBuilder` nor `ControlFlowCache` contains a single `checkCanceled` call** (`grep -rn "checkCanceled\|ProgressManager" src/main/kotlin/net/internetisalie/lunar/analysis/controlflow/` → no matches); that is tolerable for one inspection over 5 corpus sites and is not tolerable on the `forFile` path. |
| ANALYSIS-07-NFR-3 | Corpus ratchet | Must | **Every gated movement is accounted for, in both directions.** Not "the run is green" — see the direction below, which makes "green" the wrong criterion for `-02`. Phases that change no behaviour (Phase 0, Phase 4) must be `test --rerun --no-build-cache -PwithCorpus` **green with zero `IMPROVED` lines**. Phases that deliberately move a gated count (`-02`, `-03`, `-04`) must instead attribute each moved site in writing and then re-record deliberately with `-PrecordCorpusBaseline` — design **§4.8a** is the four-step procedure and TC-2e is the acceptance case. **The direction, which must be cited from the comparator and not from the printer:** `CorpusGuards.assertRatchet` (`CorpusGuards.kt:49-55`) only shows *that* regressions are asserted and improvements printed; `CorpusBaseline.compare` decides *which is which* — `regressions = gated.filter { it.third > it.second }`, `improvements = gated.filter { it.third < it.second }` (`CorpusMetrics.kt:283-284`) over `Triple(key, baseline, observed)` (`:259`, `:271`, `:276-280`). So **more hits = regression (hard fail, `:52-55`)** and **fewer hits = improvement (`println` only, `:49-51`)**. Two errors have already been paid for here: reading a green ratchet as "no movement" cost one false claim on BUG-441 (2026-08-20, 89 sites unattributed), and asserting the direction backwards cost this document a review round. |
| ANALYSIS-07-NFR-4 | Inspection independence | Must | `LuaInspectionParityTest` must stay green. Its criterion is **exact parity** on the zerobrane member — [[BUG-441]]'s landing note records `1954/1954, 72/72` and *"must not regress"*; the zerobrane baseline's own `files=72` and `inspection.LuaUndeclaredVariable=1952` are consistent with it inside the test's `ANCHOR_TOLERANCE = 25` (`LuaInspectionParityTest.kt:174`). `ANALYSIS-07-00-DR-12` re-records the figure before any change, so the before-number is this tree's and not a quote. |

## Test Cases

Every `Must` has a concrete input → output case below. `-02`'s **design** is deferred; its
**behaviour** is not, and these cases are writable today because "which definitions reach this use"
has one right answer independent of which architecture computes it.

### TC-1a — `-01` produced a decision, not an opinion

- **Input**: the Phase 0 spike, executed per design §2–§3.
- **Action**: apply design **§3.7**'s ordered decision rules to the recorded outputs. (§4.2 and §4.3
  are the *coverage* and *join* census **output formats**; an earlier revision of this case named
  them as the procedure and as its record.)
- **Expected output**: exactly one of `D0`/`D1`/`D2`/`D3`/`D4` fires — or **`DX`**, in which case
  the case is **not** satisfied: DX is design §3.7's probe-invalid gate, not an outcome, and Phase 0
  may not exit while it fires. `design.md` **§4.9** names the fired rule, quotes the numbers that
  selected it, and `risks-and-gaps.md`'s DR table carries each DR's pasted output. A selection
  recorded without the quoted numbers fails this case.

### TC-1b — TYPE-08 §9's estimate is settled either way

- **Input**: `ANALYSIS-07-00-DR-06`…`-09` outputs.
- **Expected output**: `design.md` **§4.9** — the block whose two literal forms are printed there —
  states, with numbers, whether "a much larger refactor" is **confirmed** or **refuted**, and
  `docs/features/type/08-flow-sensitive/design.md` §9 gains a one-line cross-reference to that
  finding. Silence on it fails this case. (§4.4 is the *order-comparison* output format; it supplies
  the `lcs=` figure §4.9 quotes, and is not where the verdict is written.)

### TC-2a — the over-approximation's false negative disappears (`-02`, the headline)

The killed-unknown case. BUG-441's gate reads the whole `upSet`, so an unknown that is
**overwritten before the use** still suppresses.

```lua
---@param n number
local function count(n) end
local d = wx.thing   -- unknown, but dead by line 4
d = "s"              -- unconditional kill
count(d)             -- <- must ERROR: only `"s"` reaches
```

- **Expected today** (to be confirmed by `ANALYSIS-07-00-DR-01`, and this case is void if it is
  not): no `not assignable to number` error — the unknown suppresses a use it cannot reach.
- **Expected after `-02`**: exactly one ERROR-tier diagnostic containing
  `not assignable to number`, anchored on the `d` inside `count(d)`.
- **Home**: `net.internetisalie.lunar.lang.types.LuaReachingDefinitionsTest`, alongside the existing
  `LuaUnknownProvenanceTest` (`src/test/kotlin/net/internetisalie/lunar/lang/types/LuaUnknownProvenanceTest.kt`),
  which it **extends, not replaces** — that file's four cases must all stay green.

### TC-2b — the control: a live unknown still suppresses

```lua
---@param n number
local function count(n) end
local d = wx.thing
if cond then d = "s" end   -- the unknown SURVIVES on the else path
count(d)
```

- **Expected output**: **no** ERROR-tier `not assignable to number`. This is
  `LuaUnknownProvenanceTest.testAnUnknownWriteDefeatsCertainty` verbatim
  (`LuaUnknownProvenanceTest.kt:29-45`) and it must remain green.
- **Why it carries weight**: TC-2a and TC-2b differ by one character of control flow. A `-02` that
  makes TC-2a error by weakening the gate breaks TC-2b; one that keeps TC-2b green by leaving the
  gate alone fails TC-2a. Only a real reaching-definitions answer passes both. **Mutation-proof it**:
  forcing the query to return "all definitions" must turn TC-2a red, and forcing it to return "the
  nearest definition" must turn TC-2b red.

### TC-2c — shadowing is not conflated

```lua
---@param n number
local function count(n) end
local x = 1
do local x = "s" end   -- a DIFFERENT binding
count(x)               -- <- must NOT error: only `1` reaches this `x`
```

- **Expected output**: no diagnostic mentioning `string`.
- **Why**: `LuaControlFlowBuilder` keys every instruction on `nameRef.text` — **11** construction
  sites (`grep -c "LuaReadWriteInstruction(" LuaControlFlowBuilder.kt` → `11`, at
  `:38, 46, 54, 220, 246, 309, 319, 330, 337, 342, 361`) — and performs **no** scope resolution:
  `grep -n "scope\|Scope" LuaControlFlowBuilder.kt` returns **exactly one line**,
  `31:    fun build(owner: ScopeOwner): ControlFlow {`. An earlier revision of this bullet also
  claimed the `goto`-label resolver as a match; `resolveGoto` (`:86-93`) contains **no** `scope`/
  `Scope` occurrence — it keys on `LabelKey(targetName, block)` over enclosing `LuaBlock`s, which is
  block nesting, not name binding. A by-name query would see the inner `"s"` as a definition of the
  outer `x`.
  This case is what forces `-02` to resolve bindings rather than names. Incidence is already
  measured and is **not re-derived**: `inspection.LuaShadowingVariable` across the four committed
  baselines totals **320 sites over 419 files** (luacheck 10, luarocks 26, penlight 65,
  zerobrane 219). That inspection fires on a *declaration* whose name is already bound in an
  enclosing scope (`LuaShadowingVariableInspection.kt:64-80` — `ShadowingResolveProcessor` +
  `LuaResolveUtil.scopeCrawlUp`), so 320 is a
  **lower bound** on the sites a by-name query would conflate — every read of either binding is
  affected too, and none of those is counted. It is not a corner.

### TC-2d — a closure write is not lost

```lua
---@param n number
local function count(n) end
local d = 1
local function f() d = "s" end   -- write lives in a DIFFERENT CFG
f()
count(d)                          -- <- must NOT error at ERROR tier
```

- **Expected output**: no ERROR-tier `not assignable to number` (`d`'s definitions include the
  closure's `"s"`, so the flow is not one certain definition).
- **Why**: the file-level CFG does not descend into function bodies —
  `LuaControlFlowBuilder.visitFuncDecl`/`visitLocalFuncDecl` emit only a WRITE for the *name*
  (`:335-343`) and `visitFuncDef` is an empty body (`:345-347`); `LuaUnreachableCodeInspection`
  compensates by enumerating owners separately (`LuaUnreachableCodeInspection.kt:64-70`). A
  reaching-definitions query that consults only the enclosing owner's CFG **loses the upvalue
  write** and would turn this into a false ERROR. This case is the guard against `-02` shipping a
  regression the corpus (11 sites) is too small to catch.

### TC-2e — nothing BUG-441 *correctly* suppressed comes back, while `-02`'s restorations do

**Two populations, and an earlier revision of this case collapsed them into one cap.** BUG-441's gate
suppressed 89 corpus sites; `-02` exists to bring back the subset a reaching-definitions answer proves
were suppressed *wrongly*. "Nothing comes back" and "`-02` works" are therefore not the same
assertion, and this case has to separate them.

**(a) The unit floor — sites BUG-441 correctly suppressed must NOT return.**

- **Input**: the whole of `LuaUnknownProvenanceTest` (4 cases) plus
  `FreeGlobalMemberTypingTest.testChainedReadsStayIsolatedAndUntyped`.
- **Expected output**: all green, **unchanged** — no assertion edited, no case deleted. This half has
  no tolerance.
- Plus TC-2b's own control (`local d = wx.thing` / `if cond then d = "s" end` / `count(d)`), which
  must still emit **no** ERROR: it is the canonical *correctly*-suppressed shape.

**(b) The corpus — `-02` deliberately EXCEEDS the recorded baseline, which the ratchet classifies as
a regression.**

- `LuaUnreachableCode` = **5** exactly (2 penlight + 2 zerobrane + 1 luacheck, read from the committed
  baselines), unchanged in **both** directions. It is the explicit non-goal, so any movement fails.
- `inspection.LuaTypeAssignability` and `inspection.LuaReturnTypeMismatch` are expected to **RISE**
  from their recorded `5 + 5 = 10` and `1`. **Capping them at ≤ 10 / ≤ 1 would fail this case for
  exactly the condition design §3.7's D0 uses to KEEP `-02` alive** (`RESTORED > 0`) — which is what
  an earlier revision of this bullet did.
- Because more hits is a **regression** (`CorpusMetrics.kt:283-284`, `Triple(key, baseline,
  observed)` → `it.third > it.second`), the sweep **hard-fails** at `CorpusGuards.kt:52-55`. That red
  is the expected state, not a failure of this case, and **there will be no `IMPROVED` line to
  attribute** — `-02` may only restore.
- **What this case actually requires**, per design §4.8a: every restored `file:line` in the §4.8 dump
  diff attributed in writing, one line of prose each; **zero** `IMPROVED` lines and zero
  `LuaUnreachableCode` movement (either is the `NEW_SUPPRESSED` defect and fires §3.7's `DX`); then a
  deliberate re-record with `-PrecordCorpusBaseline`, committed alongside the change.
- **The expected post-`-02` counts are the re-recorded baseline itself, not a threshold written
  here.** They are `10 + RESTORED_assignability` and `1 + RESTORED_returnmismatch`, both taken from
  DR-02's measured dump. A figure written into this document would either cap the feature or invent a
  number; the acceptance is *"the new baseline is the old one plus exactly the attributed sites, and
  nothing else moved"* — checkable line-by-line against the dump diff and against the two
  `.baseline` files' own `git diff`.

### TC-3a — `-03`, the BUG-435 shape (Should)

```lua
local Shadow = { fromLocal = 1 }

if type(Shadow) == "table" then
    Shadow.<caret>
end
```

- **Expected today** (measured, [[BUG-435]] at `fb79c038`): `[else, elseif, end]` — three keywords,
  zero members.
- **Expected after `-03`**: the offered set contains `fromLocal`. Narrowing a value to `table` must
  never *remove* members.
- **Gate**: void if `ANALYSIS-07-00-DR-10` shows the defect is a wrong node installed at
  `LuaTypesVisitor.kt:475-478`, in which case it is a one-site bug fix and `-03` is `cancelled` with
  the finding recorded.

### TC-4a — `-04`, the BUG-428 residual (Could)

- **Input**: the two sites BUG-428's 2026-08-20 re-measurement records as surviving, both anchored
  on a **function-declaration line**. Read from the fetched corpus at `99b45f92`, not from prose:

  | site | the line, verbatim |
  | :-- | :-- |
  | `penlight/lua/pl/config.lua:131` | `    local function check_cnfg (var,def)` |
  | `penlight/lua/pl/stringx.lua:231` | `local function _find_all(s,sub,first,last,allow_overlap)` |

  **`local val = cnfg[var]` is `config.lua:132`, one line below the anchor.** An earlier revision of
  this case glossed `:131` as that statement and then, in the same sentence, called the anchor "the
  function-declaration line" — it contradicted itself and disagreed with
  [[ANALYSIS-07-RISKS|risks-and-gaps.md]]'s DR-11, which was right. The anchor being the *declaration*
  and not the *use* is the whole point of DR-11: it is what makes BUG-428's "same call-site-union
  imprecision, reported against the parameter itself" checkable rather than assumed. Verified with
  `awk 'NR>=131 && NR<=132' test/corpus/penlight/lua/pl/config.lua`.
- **Expected after `-04`**: both sites gone, and penlight's `LuaTypeAssignability` drops from 5
  toward 3 with the delta attributed per site.
- **Gate**: void if `ANALYSIS-07-00-DR-11` shows they are not call-site union imprecision.

### TC-5a — one analysis, not three (structural)

- **Input**: the shipped tree after whichever phase lands.
- **Action**: three checks, **each with the path filter its sibling has**, each run against a
  measured expected set rather than against "nothing suspicious". All three outputs below were
  produced at `99b45f92` and are this case's before-state.

  **(a) Importers of either control-flow package, outside the CFG package.**

  ```
  grep -rn "net\.internetisalie\.lunar\.analysis\.controlflow\|com\.intellij\.codeInsight\.controlflow" \
      src/main/kotlin/ | grep -v "^src/main/kotlin/net/internetisalie/lunar/analysis/controlflow/"
  ```

  Today, exactly four lines, all in one file:

  ```
  …/analysis/inspections/LuaUnreachableCodeInspection.kt:4:import com.intellij.codeInsight.controlflow.Instruction
  …/analysis/inspections/LuaUnreachableCodeInspection.kt:13:import …analysis.controlflow.ControlFlowCache
  …/analysis/inspections/LuaUnreachableCodeInspection.kt:14:import …analysis.controlflow.LuaControlFlow
  …/analysis/inspections/LuaUnreachableCodeInspection.kt:15:import …analysis.controlflow.ScopeOwner
  ```

  **(b) Named, top-level Lua traversals — the conjunct that catches "a new visitor that walks
  statements", which a name-based grep cannot.**

  ```
  grep -rnE "^(open |abstract |internal |private |sealed )*class [A-Za-z]+[^:]*: Lua(Recursive)?Visitor\(\)" \
      src/main/kotlin/
  ```

  Today, exactly **three**, and the inventory is the assertion:

  ```
  …/analysis/controlflow/LuaControlFlowBuilder.kt:10:class LuaControlFlowBuilder : LuaVisitor() {
  …/lang/psi/LuaRecursiveVisitor.kt:6:open class LuaRecursiveVisitor : LuaVisitor() {
  …/lang/psi/types/LuaTypesVisitor.kt:21:class LuaTypesVisitor : LuaRecursiveVisitor() {
  ```

  Plus one that the regex does not reach because its supertype sits on a later line —
  `LuaFoldingVisitor` (`lang/insight/LuaFoldingBuilder.kt:113-115`), a folding-descriptor collector.
  **Four allowed entries total**, each recorded in the test with the reason it is not a flow
  analysis. Anonymous `object : LuaVisitor()` bodies inside `buildVisitor` are deliberately **not**
  matched. **14** exist today. **Both counts name the pattern that produced them, because a supertype
  clause can be ktlint-wrapped onto a later line and a single-line grep then undercounts** — which is
  exactly what happens to the fourth named entry above. Anonymous:
  `grep -rc "object : Lua\(Recursive\)\?Visitor()" src/main/kotlin/`, summed over the files with a
  non-zero count → `14`. Total supertype sites:
  `grep -rn ": Lua\(Recursive\)\?Visitor()" src/main/kotlin/ | wc -l` → `18`; `18 - 4` named
  entries = the same `14`, which is the cross-check. Thirteen are per-element
  inspection visitors returned from `buildVisitor`; the fourteenth is
  `lang/insight/LuaDocGenerator.kt:100`. Pinning them would make this case fire on every new
  inspection. **A definition-computing traversal cannot
  be one of those** — it must hold state across statements, which means a named class with fields,
  which is what (b) enumerates.

  **(c) Flow-named declarations outside the two sanctioned packages.**

  ```
  grep -rnE "^[[:space:]]*(class|object|interface) [A-Za-z]*(ControlFlow|DataFlow|FlowAnalysis|ReachingDef)" \
      src/main/kotlin/ \
    | grep -v "^src/main/kotlin/net/internetisalie/lunar/analysis/controlflow/" \
    | grep -v "^src/main/kotlin/net/internetisalie/lunar/lang/psi/types/"
  ```

  Today: **empty**.
- **Expected output**: (a) every importer is `LuaUnreachableCodeInspection` **or** a component named
  in the selected direction's §5/§6; (b) the named-traversal inventory is the four above **plus** at
  most components named in §5/§6 — a new entry with no design section fails; (c) still empty.
  **A third traversal fails this case even if it is correct.** An earlier revision of this case ran
  `grep -rln "class .*ControlFlow\|class .*DataFlow\|class .*FlowAnalysis"` with **no path filter**
  and no inventory, so it could neither exclude the sanctioned packages nor detect the very case the
  bullet describes — a new statement-walking visitor with an innocuous name.
- **Home**: `net.internetisalie.lunar.analysis.LuaSingleFlowAnalysisTest`, a source-scanning test in
  the manner of the existing structural assertions (`LuaReceiverMemberIndexTest.testEveryFileTypeRegistrationIsIndexed`
  is the precedent for asserting a repo-wide structural property rather than reviewing for it).

## See Also

- Design: [design.md](design.md) — Phase 0 spike + decision procedure; §5–§7 gated
- Plan: [implementation-plan.md](implementation-plan.md)
- Risks & DR table: [risks-and-gaps.md](risks-and-gaps.md)
- [[ANALYSIS-06]] — the CFG this builds on · [[TYPE-08]] §9 — the recorded rejection
- [[BUG-441]] · [[BUG-435]] · [[BUG-428]] — the three measured defects
