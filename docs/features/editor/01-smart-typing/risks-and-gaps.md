---
id: "EDITOR-01-RISKS"
title: "Risks & Gaps"
type: "risk"
parent_id: "EDITOR-01"
folders:
  - "[[features/editor/01-smart-typing/requirements|requirements]]"
---

# EDITOR-01: Risks & Gaps

## Critical Risks

### Risk 1.1: Double terminator insertion (Enter path vs. completion/keystroke path)
- **Impact**: accepting `do` (scaffolds `end`) and then pressing Enter could insert a second
  `end`, producing broken code and a bad first impression.
- **Likelihood**: low.
- **Mitigation**: the two paths fire on different editor actions (Enter action vs.
  Tab/Enter-accept of a lookup / space keystroke), so they cannot both run for one user action.
  Both use the same balance check (`owner.node.findChildByType(terminatorType)`,
  `LuaEnterHandler.kt:54` / design §3.4 step 5): once a terminator exists, neither inserts
  another. Regression-guarded by TC-11 and the pre-existing
  `LuaEnterHandlerTest.testEnterAfterThenAlreadyBalanced`.

### Risk 1.2: In-process fixture editor does not reindent block bodies like the real IDE
- **Impact**: caret-column / indent assertions in real-flow tests may not match the live IDE, as
  already documented for COMP-08 (`LuaEnterHandlerTest.kt:90-104`).
- **Likelihood**: high (known harness quirk).
- **Mitigation**: assert **structural** correctness (terminator count, terminator on its own
  line, caret on the body line) rather than exact indent columns — mirror the COMP-08 test
  strategy. The exact indent is confirmed by the human-verification checklist in the sandbox IDE.

### Risk 1.3: `MultiCharQuoteHandler.getClosingQuote` API surface drift
- **Impact**: if the signature assumed (`getClosingQuote(HighlighterIterator, Int): CharSequence?`)
  differs on the pinned platform, `LuaQuoteHandler` won't compile.
- **Likelihood**: low — verified against `SimpleTokenSetQuoteHandler` /
  `JsonQuoteHandler.getClosingQuote` in `intellij-community` master.
- **Mitigation**: DR-01 confirms against the pinned platform before Phase 1 lands.

## Design Gaps

_None open._ The single confirmed decision (keyword auto-close on-by-default behind a Smart
Keys toggle) is fully specified in design §2.5/§2.6/§7. No unresolved questions remain.

## Technical Debt & Future Work
- **TBD: `for`/`while` bare-keyword scaffolding.** On accepting a bare `for`/`while` from
  completion, the terminator is scaffolded only when the `do`/`then` opener is later typed (design
  §3.5). A future enhancement could scaffold `… do end` immediately from a live template; deferred
  to keep -01-05 leaning on the existing `LuaBlockPairs` opener leaves.
- **TBD: long-string `[[ ]]` pairing.** `LONGSTRING` auto-pairing is out of scope for -01-03
  (only `"`/`'` per the requirement); revisit if users request it.

## Pre-Implementation De-risking Tasks

| ID | Action | Resolves | Status |
|----|--------|----------|--------|
| EDITOR-00-DR-01 | Compile a throwaway `LuaQuoteHandler` stub against the pinned platform to confirm the `MultiCharQuoteHandler.getClosingQuote` signature and `SimpleTokenSetQuoteHandler` ctor. | Risk 1.3 | todo |
| EDITOR-00-DR-02 | In the sandbox IDE, confirm the platform auto-inserts/skips/backspace-deletes bracket pairs over `LuaPairedBraceMatcher` with **no** new code (validates the §3.3 platform-delivery assumption). | Risk 1.1 (scope) | todo |

## Cross-feature Dependency (surfaced during planning)
- **EDITOR-08 (Smart Enter) reuses `LuaBlockPairs`** (epic requirements.md:50). This feature keeps
  the opener→terminator table single-sourced in `LuaBlockPairs`
  (`src/main/kotlin/.../lang/syntax/LuaBlockPairs.kt:15`) and routes all terminator insertion
  through the new `LuaKeywordBlockCloser` object (design §2.3/§2.4). EDITOR-08 should consume the
  same object rather than re-implementing the balance-check + insert logic. No code change to
  `LuaBlockPairs` is required by EDITOR-01.

## Test Case Gaps
- Undo-coalescing of the scaffolded terminator (single Ctrl+Z removes the `end`) is verified only
  manually via the human-verification checklist; the in-process fixture does not model undo
  grouping reliably.

## See Also
- Requirements: [requirements.md](requirements.md)
- Design: [design.md](design.md)
