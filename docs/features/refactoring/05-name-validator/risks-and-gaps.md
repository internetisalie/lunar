---
id: "REFACT-05-RISKS"
title: "Risks & Gaps"
type: "risk"
parent_id: "REFACT-05"
priority: "medium"
folders:
  - "[[features/refactoring/05-name-validator/requirements|requirements]]"
---

# REFACT-05: Risks & Gaps

## Critical Risks

### Risk 1.1: Wrong keyword source omits/adds words
- **Impact**: Deriving the keyword set from `LuaSyntax.KeywordTokens` (`lang/syntax/LuaSyntax.kt:46`)
  would omit `nil`/`true`/`false` (they live in `PredefinedConstantTokens`, `LuaSyntax.kt:79`) and
  falsely include non-reserved `with`/`continue` (`LuaSyntax.kt:53,64`) — letting a rename to
  `nil` through while blocking `with`.
- **Likelihood**: medium (the skeleton pointed at a token set; the original design referenced a
  non-existent `LuaTokenTypes.KEYWORDS`).
- **Mitigation**: Use `LuaKeywords.RESERVED` / `LuaKeywords.isReserved` (`LuaKeywords.kt:8,15`) —
  the authoritative 22-word Lua 5.1–5.4 list. `testAllReservedWordsAreKeywords` pins this.

### Risk 1.2: Identifier regex accepts Unicode / wrong shape
- **Impact**: Using `\w` or Unicode letter classes would accept non-ASCII names the Lua lexer
  rejects, producing invalid code after rename; a wrong anchor (missing `$`) would accept
  `1var ` or `foo bar`.
- **Likelihood**: low.
- **Mitigation**: Fixed, fully-anchored ASCII regex `^[A-Za-z_][A-Za-z0-9_]*$`, matching the
  lexer rule in `LuaLexer.flex`. Covered by `testInvalidIdentifierRejected`.

## Design Gaps

### Gap 2.1: Language-level awareness for `goto` (5.2+ only)
- **Question**: `goto` is reserved only in Lua 5.2+. Should the validator be language-level-aware
  (allow `goto` as an identifier under a project configured for Lua 5.1) or always treat the full
  5.1–5.4 union as reserved?
- **Options / leaning**: (a) union — always reserve `goto`; (b) consult the project's configured
  language level (`settings/`). **Leaning (a), resolved:** use the union. Renaming a symbol to
  `goto` is unsafe the moment the file is run under any 5.2+ interpreter, and the platform passes a
  nullable `Project` (no reliable level in some call sites). `LuaKeywords.RESERVED` already encodes
  the union including `goto`, so no extra logic is needed.
- **Resolved by**: DR-02 — outcome folded into design §2.1 and requirements TC-4. Closed.

## Technical Debt & Future Work
- **TBD: per-language-level validation** — if users complain that Lua-5.1 projects cannot name a
  variable `goto`, revisit Gap 2.1 with a language-level-aware variant. Deferred; the union is the
  safe default and keeps the validator a pure, project-independent function.

## Pre-Implementation De-risking Tasks

| ID | Action | Resolves | Status |
|----|--------|----------|--------|
| REFACT-05-00-DR-01 | Confirm `LuaKeywords.RESERVED` contains exactly the 22 Lua 5.1–5.4 reserved words (incl. `goto`) and nothing else; confirm `isReserved` is case-sensitive string membership | Risk 1.1 | done — verified at `LuaKeywords.kt:8-15` (22 words incl. `goto`; `word in RESERVED`) |
| REFACT-05-00-DR-02 | Decide union vs. language-level keyword handling for `goto` | Gap 2.1 | done — union chosen (see Gap 2.1) |
| REFACT-05-00-DR-03 | Confirm platform `NamesValidator` signatures + `project` nullability so the Kotlin override compiles and tests can pass `null` | test design | done — `NamesValidator.java:22,31`: `name` `@NotNull`, `project` unannotated → `Project?`; tests pass `null` |

## Test Case Gaps
- None outstanding. Requirements TC-1…TC-6 plus the exhaustive `testAllReservedWordsAreKeywords`
  cover both predicates, the keyword/identifier coupling, ASCII boundaries, empty string, and the
  `goto`/`end` edge cases.

## See Also
- Requirements: [requirements.md](requirements.md)
- Design: [design.md](design.md)
- Plan: [implementation-plan.md](implementation-plan.md)
