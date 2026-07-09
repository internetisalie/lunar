---
id: "EDITOR-02-RISKS"
title: "Risks & Gaps"
type: "risk"
parent_id: "EDITOR-02"
folders:
  - "[[features/editor/02-spellchecking/requirements|requirements]]"
---

# EDITOR-02: Risks & Gaps

## Critical Risks

### Risk 1.1: Identifier noise on short / domain Lua names
- **Impact**: Lua code is dense in short locals (`i`, `db`, `fn`, `ctx`, `idx`) and vendored
  library names (`cjson`, `lfs`, `luv`). Over-aggressive identifier spellchecking would bury the
  editor in false-positive squiggles and get the feature disabled by users.
- **Likelihood**: medium.
- **Mitigation**: (a) identifier checking is scoped to **declarations only** (design §3.3), not
  every reference; (b) suppression list covers stdlib globals + LuaCATS types + keywords
  (§2.4); (c) `IdentifierSplitter` already skips sub-3-letter tokens. Measured in DR-02 before
  promoting EDITOR-02-03 past `Should`.

### Risk 1.2: String-offset drift on escapes / long brackets breaks typo ranges
- **Impact**: if delimiter-strip / escape-decode offsets are wrong, the `<TYPO>` highlight lands
  on the wrong columns (or throws on malformed input), making the string feature worse than absent.
- **Likelihood**: medium.
- **Mitigation**: design §3.2 pins the exact `prefixLen` per string form and reuses the proven
  `EscapeSequenceTokenizer.processTextWithOffsets` + `CodeInsightUtilCore.parseStringCharacters`
  path (same as `PropertiesSpellcheckingStrategy`). TC-3 asserts the long-bracket offset directly.
  Retired by DR-01.

## Design Gaps

### Gap 2.1: Lua-specific string escapes not modelled by `parseStringCharacters`
- **Question**: `CodeInsightUtilCore.parseStringCharacters` understands C/Java-style escapes but
  not Lua's `\ddd` decimal, `\xHH`, `\u{XXX}`, or `\z` (skip-whitespace). Do we need a Lua decoder?
- **Options / leaning**: Lean **no custom decoder for v1** — an unmodelled escape leaves the raw
  bytes in the decoded text, at worst yielding one benign extra/misaligned token, never a crash
  (design §3.2 states graceful degradation). A Lua-aware decoder is future work only if DR-01 shows
  real misalignment on common strings.
- **Resolved by**: DR-01 below; fold the outcome (custom decoder yes/no) back into design §3.2.

### Gap 2.2: Spellcheck inspection class used in tests
- **Question**: the platform spellcheck inspection moved to Grazie
  (`com.intellij.grazie.spellcheck.GrazieSpellCheckingInspection`); the older
  `com.intellij.spellchecker.inspections.SpellCheckingInspection` is retained but no longer the
  active inspection. Which class does the GoLand 2026.1 test fixture expose for
  `myFixture.enableInspections(...)`?
- **Options / leaning**: reference JSON/Properties/YAML tests in `intellij-community` all use
  `GrazieSpellCheckingInspection`; lean on that. If it is not on the test classpath, fall back to
  the legacy `SpellCheckingInspection`.
- **Resolved by**: DR-03 below; fix the class name in the plan's Verification Tasks once confirmed.

## Technical Debt & Future Work
- **TBD: Lua escape decoder** — see Gap 2.1; only if DR-01 warrants.
- **TBD: user-configurable suppression list** — additional project dictionary words are already
  handled by the platform's save-to-dictionary fix; a Lua-specific settings panel is out of scope.
- **TBD: reference-site spellchecking** — deliberately excluded (declaration-only, design §3.3).

## Pre-Implementation De-risking Tasks

| ID | Action | Resolves | Status |
|----|--------|----------|--------|
| EDITOR-00-DR-01 | Prototype `LuaStringTokenizer` against strings with `\n`, `\t`, `\65`, `\u{1F600}`, and `[==[…]==]`; assert typo offsets land correctly (or document misalignment). | Risk 1.2, Gap 2.1 | todo |
| EDITOR-00-DR-02 | Run identifier spellcheck over a real Lua project (e.g. `~/Documents/src/lua/test`) and count false positives on short/vendored names; confirm suppression list adequacy. | Risk 1.1 | todo |
| EDITOR-00-DR-03 | Confirm which spellcheck inspection class (`GrazieSpellCheckingInspection` vs `SpellCheckingInspection`) is available in the GoLand 2026.1 test fixture; lock the Verification Tasks class name. | Gap 2.2 | todo |

## Test Case Gaps
- No test yet for a comment typo *inside* a `--[[ long comment ]]` (LONGCOMMENT) — add alongside TC-1.
- No test for a snake_case identifier split (`recieve_buffer` → `recieve`) — add alongside TC-4.

## See Also
- Requirements: [requirements.md](requirements.md)
- Design: [design.md](design.md)
