---
id: "EDITOR-05-RISKS"
title: "Risks & Gaps"
type: "risk"
parent_id: "EDITOR-05"
folders:
  - "[[features/editor/05-surround-with/requirements|requirements]]"
---

# EDITOR-05: Risks & Gaps

## Critical Risks

### Risk 1.1: Reformat range does not normalize wrapped-body indentation
- **Impact**: Body appears double-indented or flush-left after wrapping, failing the "re-indented"
  requirement and the `checkResult` assertions.
- **Likelihood**: medium
- **Mitigation**: `LuaInvertIfIntention` already relies on `CodeStyleManager.reformat` producing
  correct Lua indentation over a rebuilt `if` (verified live in its tests). `replaceStatements`
  reuses the same `CodeStyleManager` via `reformatRange` scoped to the inserted node. DR-01
  prototypes the exact reformat call on a two-statement body before Phase 1 hardens.

### Risk 1.2: Caret-marker offset mapping drifts after reformat
- **Impact**: Caret lands one column off (e.g. inside `then` instead of the condition) because
  reformat inserts/removes whitespace between the strip-time index and the final document.
- **Likelihood**: medium
- **Mitigation**: The marker always sits on the wrapper's fixed leading text (before `§BODY§` and
  before the reindented body), so reformat only touches text *after* the caret site for
  condition-templates and the body-start for body-templates. §3.1 step 7 recomputes the offset from
  the post-reformat inserted range start rather than caching a pre-reformat offset. DR-02 verifies
  caret placement for one condition-template and one body-template.

## Design Gaps
_No open design decisions._ Every template shape, ordering, caret site, and the empty/partial-selection
behavior is pinned in design §2–§3. The two items below are deliberately deferred scope, not
unresolved design.

## Technical Debt & Future Work
- **TBD: IIFE-invoked `function` variant** — requirement -03 says "optionally IIFE-invoked". Shipped as
  plain `function() … end`. `(function() … end)()` deferred; would be an 8th surrounder reusing the same
  base with no new helpers.
- **TBD: capturing `pcall` return values** — `LuaPcallSurrounder` wraps the body but does not bind
  `local ok, err = pcall(...)`. Deferred; the -05 requirement only asks to "capture the protected body".
- **TBD: semantic soundness of wrapping `return`/`break`** — wrapping such statements in `function`/`do`
  can change control-flow semantics. Matches JetBrains-language surround behavior (syntactic only); not
  guarded. Noted for a future inspection, not this feature.

## Pre-Implementation De-risking Tasks

| ID | Action | Resolves | Status |
|----|--------|----------|--------|
| EDITOR-00-DR-01 | Prototype `LuaElementFactory.createFile` + `CodeStyleManager.reformatRange` on a rebuilt `if`-wrapped two-statement body; confirm indentation matches expected `checkResult`. | Risk 1.1 | todo |
| EDITOR-00-DR-02 | Verify caret `TextRange` lands in the condition (`if`) and at body start (`do`) after reformat, using `SurroundWithHandler.invoke` in a `BasePlatformTestCase`. | Risk 1.2 | todo |

## Cross-feature dependency
- **EDITOR-06 (Unwrap) reuses `LuaBlockStructureUtil`.** This feature introduces that shared
  utility (design §2.1) with the block-locating / statement-span / re-indent helpers the epic
  requirements.md (line 49) calls out as a soft coupling. EDITOR-06's Unwrap will call
  `enclosingBlock`, `statementsInRange`/`statementsText`, and a hoist-and-reindent counterpart to
  `replaceStatements`. The utility is designed as a stateless `object` with no `Project`/`Editor`
  retention specifically so EDITOR-06 can depend on it without a blocking edge. If EDITOR-06 needs an
  additional helper (e.g. "hoist a block's body into its parent"), it should be added to
  `LuaBlockStructureUtil`, not duplicated.

## Test Case Gaps
- No test yet for surrounding a selection that spans an inner block boundary (design §6 "spans nested
  blocks"); add as a negative case alongside the partial-selection negative test.

## See Also
- Requirements: [requirements.md](requirements.md)
- Design: [design.md](design.md)
