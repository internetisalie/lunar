---
id: ANALYSIS-07
title: "07: Value Constraints over the Control-Flow Graph"
type: feature
parent_id: ANALYSIS
status: todo
vf_icon: ⬜
priority: medium
folders:
  - "[[features/analysis/requirements|requirements]]"
---

# Value Constraints over the Control-Flow Graph (`ANALYSIS-07`)

> **Not planned yet — this is a filing, not a design.** It needs the full `plan-feature` pass
> before any code. Filed 2026-08-20 out of three independent defects that turned out to share one
> shape; the point of the record is that the shape has now been *measured* three times and should
> stop being rediscovered one bug at a time.

## The situation

Lunar has **two flow analyses, and they do not talk to each other.**

| | [[ANALYSIS-06]] — the CFG | The type engine (`LuaTypeGraph`) |
| :-- | :-- | :-- |
| shape | a real control-flow graph over `ScopeOwner`s | a constraint graph over value/use nodes |
| knows | reachability; `ReadWriteInstruction` for every local read, write and implicit global | a rich value domain: biunification, `upSet`/`downSet`, unions, traits |
| lacks | **any value domain** | **any control-flow graph** |
| consumers | exactly one — `LuaUnreachableCodeInspection` | every inspection, inlay, completion and hover |

[[TYPE-08]] considered joining them and declined, on the record (design §9):

> **ControlFlow-based approach**: use `LuaControlFlowBuilder` to annotate the CFG with guard
> information, then query it during type resolution. **Rejected** because the type engine is purely
> tree-walk (no CFG integration), and wiring them together would require a much larger refactor.

That was a reasonable call for TYPE-08's scope — single-pattern type guards via scope injection —
and it is the decision this feature would revisit, with evidence TYPE-08 did not have.

## Why now: three measured defects, one shape

Each of these was diagnosed independently and each turned out to be the engine reasoning about
values with no graph to hang them on.

| defect | what it needed | what it got |
| :-- | :-- | :-- |
| [[BUG-441]] | reaching definitions — "does any *other* definition of this variable reach here unaccountably?" | `currentUpSet.any { … }` over the constraint graph's own approximation. The CFG already models exactly these reads and writes (ANALYSIS-06-02) and was never asked |
| [[BUG-435]] | narrowing inside `if type(x) == "table"` — a value constraint holding on one **subgraph** | TYPE-08's scope injection, which does not reach table members; the guarded variable offers no members at all |
| [[BUG-428]] residual | context sensitivity — `check_cnfg(var, def)` unions **every** call site's `def`, so `list_delim` types as `boolean \| string` where the value is plainly a string | one type per function, call-site-blind. 2 sites survive at the declaration anchor after BUG-441 |

BUG-441 is the sharpest illustration. Its RC-2 computes "is this variable's provenance
accountable?" — a textbook reaching-definitions query — by scanning the constraint graph's upSet,
because that is the only place the information exists in a form the diagnostic path can reach.

## What a solution has to be, and what it must not be

**It must not be a third analysis.** Two uncoordinated flow analyses is the problem being reported;
adding a third makes it worse. The work is to give one of them what the other has, and the direction
is an open question this feature exists to answer:

- **A — teach the CFG a value domain.** Abstract interpretation over `LuaControlFlow`, with the type
  engine's lattice as the domain. Natural home for reachability-conditioned facts.
- **B — give the type engine the CFG.** What TYPE-08 rejected; its cost estimate ("a much larger
  refactor") is unmeasured and should be measured rather than inherited.
- **C — a narrow bridge.** Expose the CFG's `ReadWriteInstruction` stream as a reaching-definitions
  service the type engine may consult at specific decision points (BUG-441's gate being the first),
  without merging the two.

C is the cheapest and the least likely to pay off structurally; A and B are both large. **Sizing this
from a reading would repeat exactly the mistake BUG-441's own report records** — its "one change,
the order is forced" framing survived contact with a runtime probe, and its mechanism did not.

## Non-goals

- Rewriting `LuaUnreachableCodeInspection`, which works. [[INSP-04]]'s deferred `INSP-04-C1`
  (`error()`/`os.exit()` as terminators) and `INSP-04-C2` (`while true` as non-terminating) are
  **CFG-builder** changes that would ride along naturally, but neither justifies this on its own.
- Any new user-visible inspection. This is engine work whose payoff is existing diagnostics being
  right more often.

## Evidence required before planning

The corpus is the only real gate, and this area has a recorded history of graph-level probes
disagreeing with inspection-level baselines (four incomparabilities, [[BUG-419]] and [[BUG-424]]).

- Measure at the **inspection** level with a per-site dump, not by counting graph emissions.
- Re-run the [[BUG-417]] parity criterion; it currently reads exact parity and must not regress.
- The ratchet is **one-directional** — `CorpusGuards` asserts only `regressions.isEmpty()` and
  `println`s improvements. A green corpus run means "nothing got worse", not "nothing changed"; read
  the `IMPROVED` lines. (This cost a false "no movement" claim on BUG-441, 2026-08-20.)

## Requirements Table

| ID | Requirement | Priority | Status | Description |
|---|---|---|---|---|
| ANALYSIS-07-01 | Decide the direction | Must | Not Implemented | Choose A, B or C above **from a measurement**, not from a reading. TYPE-08's "much larger refactor" is an unmeasured estimate and is the first thing to check. |
| ANALYSIS-07-02 | Reaching definitions as a service | Must | Not Implemented | A queryable "what definitions reach this use, and is any of them unaccountable?" built on ANALYSIS-06-02's `ReadWriteInstruction` stream. First consumer: [[BUG-441]]'s `unknownProvenance` gate, which today approximates it over `upSet`. |
| ANALYSIS-07-03 | Constraints scoped to a subgraph | Should | Not Implemented | A value constraint that holds on one branch and not its sibling, reaching **members** and not only the bare type — the [[BUG-435]] shape. |
| ANALYSIS-07-04 | Call-site sensitivity | Could | Not Implemented | A parameter's type at one call site not polluted by every other. [[BUG-428]]'s residual; the largest and least certain of these. |
| ANALYSIS-07-05 | One analysis, not three | Must | Not Implemented | Whatever ships, the CFG and the type engine must end up coupled or one subsumed — not joined by a third flow analysis. |
