---
id: INTENT-02-RISKS
title: Invert If Risks & Gaps
type: risk
parent_id: INTENT-02
---

# INTENT-02: Risks & Gaps

## Critical Risks

### Risk 1.1: Comment / whitespace loss on branch swap
- **Impact**: The swap rebuilds the statement and splices branch bodies as raw text
  ([design.md](design.md) §4). If body capture or the post-replace reformat is wrong, inline
  comments, blank lines, or relative indentation inside a branch could be dropped or shifted,
  silently mangling user code.
- **Likelihood**: medium
- **Mitigation**: Capture each branch via `LuaBlock.text` (raw, verbatim — preserves inner
  comments/newlines), splice without trimming inner content, then run
  `CodeStyleManager.reformat` only on the inserted `LuaIfStatement` node to fix *outer*
  indentation. Add a test with an inline comment in a branch as part of `INTENT-02-00-DR-01`
  before committing to the approach.

### Risk 1.2: Operator-inversion table incompleteness / wrong fallback
- **Impact**: A relational operator missed from the table, or an `and`/`or`/arithmetic binop
  incorrectly treated as relational, produces a semantically wrong condition (a real
  correctness bug, not just cosmetic).
- **Likelihood**: medium
- **Mitigation**: The table in [design.md](design.md) §3.1 is exhaustive over Lua's six
  relational operators (`EQ/NE/LT/LE/GT/GE`, all grep-verified in `LuaElementTypes.java`).
  Anything not in that set — including `and`/`or` (`AND`/`OR`) and arithmetic/concat — must
  take the §3.3 `not ( … )` wrap path by construction (the dispatch checks membership in the
  relational set, not a blocklist). Unit tests TC4 (`and`) and TC5 (`<`) pin both branches.

### Risk 1.3: Nested / parenthesized conditions and operator precedence
- **Impact**: Wrapping `a and b` as `not a and b` (missing parens) would change meaning
  because `not` binds tighter than `and`/`or` in Lua. Also, an already-parenthesized
  condition `(x == 1)` may parse such that the outer node is a parenthesized-expr, not a
  `LuaBinOpExpr`, sending it down the wrap path unexpectedly.
- **Likelihood**: medium
- **Mitigation**: §3.3 always emits explicit parentheses: `not (" + condition.text + ")"`.
  For a parenthesized condition, the wrap path is still correct (`not ((x == 1))` is valid and
  semantically right); de-risking task `INTENT-02-00-DR-02` checks whether `(x == 1)` should
  be unwrapped first for nicer output, but correctness is not at risk either way.

## Design Gaps

### Gap 2.1: De Morgan distribution over `and` / `or` — OUT OF SCOPE (resolved)
- **Question**: Should `a and b` invert to `not a or not b` (De Morgan) instead of
  `not (a and b)`?
- **Options / leaning**: (a) wrap as `not ( … )` [chosen]; (b) distribute via De Morgan.
- **Resolved by**: Decision recorded here and in [design.md](design.md) §3.3: **wrap only.**
  De Morgan distribution is explicitly excluded from INTENT-02 — it adds recursion, operator
  re-association, and edge cases (mixed `and`/`or`, comparison operands) for marginal benefit.
  The implementer must NOT attempt it. Parked as future work below.

### Gap 2.2: Double negation `not (not X)` cleanup — OUT OF SCOPE (resolved)
- **Question**: Inverting a §3.3-wrapped condition twice yields `not (not (X))`. Collapse it?
- **Options / leaning**: (a) leave as-is [chosen]; (b) detect and unwrap one `not`.
- **Resolved by**: Leave as-is for INTENT-02 ([design.md](design.md) §3.4). The §3.2 unwrap
  only fires when the *top-level* node is a `not` unary, so a single round-trip on a relational
  op is clean; only repeated wraps nest. Parked as future work.

## Technical Debt & Future Work
- **TBD: De Morgan distribution** — optional richer inversion for `and`/`or` chains (Gap 2.1).
- **TBD: `not (not X)` collapse** — simplify nested negations on repeated inversion (Gap 2.2).
- **TBD: arithmetic/`#`/`-` unary handling** — currently all take the §3.3 wrap path; no
  special casing planned.

## Pre-Implementation De-risking Tasks

| ID | Action | Resolves | Status |
|----|--------|----------|--------|
| INTENT-02-00-DR-01 | Prototype the §4 rebuild-and-`replace` on an `if/else` whose branches contain inline + full-line comments and nested indentation; confirm comments/indentation survive after `CodeStyleManager.reformat`. Fold the exact body-capture call (`LuaBlock.text` vs node-range substring) back into design §4. | Risk 1.1 | done |
| INTENT-02-00-DR-02 | Spike `LuaConditionInverter` against: `x == 1`, `not ready`, `isValid()`, `a and b`, `x < 10`, `(x == 1)`, `-x`. Confirm each operator leaf is reachable via `binOp.firstChild?.node?.elementType` and matches the §3 table; decide whether to unwrap a leading parenthesized expr (Risk 1.3). | Risk 1.2, Risk 1.3 | done |

## Test Case Gaps
- No coverage yet for a branch containing comments (covered operationally by DR-01; add a
  positive test `test preserves branch comments` if DR-01 confirms the approach is safe).
- No coverage for a parenthesized condition `(x == 1)` — add once DR-02 resolves the unwrap
  question (Risk 1.3).
- Unary `-`/`#` conditions (e.g. `if -x then`) are vanishingly rare and take the §3.3 path;
  no dedicated test planned.

## See Also
- Requirements: [requirements.md](requirements.md)
- Design: [design.md](design.md)
- Implementation plan: [implementation-plan.md](implementation-plan.md)
