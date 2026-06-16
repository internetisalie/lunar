---
id: INTENT-01-RISKS
title: "Risks & Gaps"
type: risk
status: done
parent_id: INTENT-01
folders:
  - "[[features/refactoring/intent-01-string-conversion/requirements|requirements]]"
---

# INTENT-01: Risks & Gaps

## Critical Risks

### Risk 1.1: Long strings might not be a `LuaTerminalExpr`/`STRING` leaf
- **Impact**: if `[[…]]` surfaced as a distinct PSI element (or distinct token), `isAvailable`
  would miss it, breaking two of the three cycle forms and TC2/TC3/TC7/TC8.
- **Likelihood**: low (verified) — `LongStringMergingLexerAdapter.getMergeFunction()` returns
  `LuaElementTypes.STRING` for the whole `LONGSTRING_BEGIN…LONGSTRING_END` run
  (`src/main/kotlin/net/internetisalie/lunar/lang/lexer/LuaLexer.kt:136`), so it is the same leaf
  shape as quoted strings.
- **Mitigation**: `INTENT-01-00-DR-01` asserts the leaf type for all three forms before coding;
  `currentForm` keys on leaf *text*, not element type, so a single accessor (`LuaTerminalExpr.string`)
  covers all three.

### Risk 1.2: Escape correctness (value not preserved across conversion)
- **Impact**: a wrong escape table corrupts the runtime string — e.g. dropping `\\` ordering,
  double-escaping, or escaping the wrong delimiter. User-visible data loss.
- **Likelihood**: medium — escaping is fiddly (backslash must be escaped first; only the active
  delimiter is escaped; long strings escape nothing).
- **Mitigation**: always **decode→encode** via the existing, tested `extractLuaString`
  (`LuaLiterals.kt:121`) for decode; encode rules pinned in design §3.2; round-trip tests TC4↔TC5
  prove symmetry; pure-unit `LuaLiteralsTest` exercises the encoder in isolation.

### Risk 1.3: Long-bracket `]]`-in-content produces a broken literal
- **Impact**: converting a value containing `]]` (or the chosen level's closer) to long form would
  terminate the literal early, yielding unparseable code.
- **Likelihood**: medium (real Lua code embeds `]]`).
- **Mitigation**: `longBracketLevel` raises the bracket level until no closer sequence is present
  (design §3.3); the LONG target is therefore always representable — no "skip the form" path is
  needed. Covered by TC8. De-risked by `INTENT-01-00-DR-03`.

### Risk 1.4: Long-string leading-newline semantics
- **Impact**: Lua silently swallows the first newline after `[[`. A value that starts with `\n`
  would lose a newline on a `[[`-round-trip, or gain one, unless handled.
- **Likelihood**: low.
- **Mitigation**: `extractLuaString` already strips one leading `\n` on decode
  (`LuaLiterals.kt:137`); the encoder re-adds a `\n` after the opener when the value starts with
  `\n` (design §3.2). Add a test value beginning with a newline if `INTENT-01-00-DR-02` surfaces
  an asymmetry.

## Design Gaps

_All design questions are resolved; design.md "Open Questions" is empty. The two decisions that
were genuine forks are recorded below as resolved._

### Gap 2.1: Package/base-class convention (RESOLVED)
- **Question**: new `lang/intentions/` package + `PsiElementBaseIntentionAction` (skeleton), or
  follow the existing `lang/insight/` + `BaseIntentionAction` intention?
- **Resolved**: follow existing `LuaGenerateDocIntention` — `lang/insight/` package,
  `BaseIntentionAction`, file-based `isAvailable`/`invoke`. Both bases are valid; consistency wins.
  Folded into design §1.

### Gap 2.2: Long-bracket `]]` strategy (RESOLVED)
- **Question**: skip the LONG form when content has `]]`, or raise the bracket level?
- **Resolved**: raise the level (`longBracketLevel`) — LONG is then always representable, simpler
  for the user, no surprising form-skip. Folded into design §3.3 and requirement INTENT-01-03.

## Technical Debt & Future Work
- **TBD: preserve original short-string escapes verbatim where unambiguous** — the decode→encode
  pass *normalizes* escapes (e.g. `\065` → `A`, `\u{41}` → `A`). A future enhancement could
  preserve the author's original escape spelling for unaffected characters. Deferred: value is
  preserved, only spelling changes; not worth the complexity now.
- **TBD: caret-position preservation inside the string after conversion** — current design replaces
  the whole leaf; caret lands at the edit's natural position. Fine-grained caret restoration is
  out of scope.

## Pre-Implementation De-risking Tasks

| ID | Action | Resolves | Status |
|----|--------|----------|--------|
| INTENT-01-00-DR-01 | In a `BasePlatformTestCase` spike, `configureByText` each of `'x'`, `"x"`, `[[x]]`; assert `findElementAt(caret)`'s enclosing `LuaTerminalExpr.string` is non-null and its element type is `LuaElementTypes.STRING` for all three. | Risk 1.1 | done |
| INTENT-01-00-DR-02 | Round-trip spike: for sample values incl. `a"b`, `it's`, a tab, and a leading-newline value, assert `extractLuaString(encodeLuaString(v, form)) == v` for every `form`. | Risk 1.2, 1.4 | done |
| INTENT-01-00-DR-03 | Unit-test `longBracketLevel` against `"hello"`→0, `"a]]b"`→1, `"a]=]b"`→? (a value containing both `]]` and `]=]`); confirm the opener/closer it produces are absent from the content. | Risk 1.3 | done |

## Test Case Gaps
- A value containing BOTH `]]` and `]=]` (needs level ≥2) is not yet a requirements test case;
  add it once `INTENT-01-00-DR-03` confirms the level loop. Covered indirectly by the
  re-decode assertion strategy in implementation-plan Phase 3 TC8.
- Multi-line short string built from `\n` escapes converting to long form (visual newline) —
  add if DR-02 shows any asymmetry.

## See Also
- Requirements: [requirements.md](requirements.md)
- Design: [design.md](design.md)
- Implementation plan: [implementation-plan.md](implementation-plan.md)
