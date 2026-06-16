---
id: "INSP-09"
title: "09: Language-Level Compliance Inspection"
type: "feature"
status: "done"
priority: "high"
parent_id: "INSP"
folders:
  - "[[features/inspections/requirements|requirements]]"
---

# INSP-09: Language-Level Compliance Inspection

## Overview
Flags syntax that the project's configured Lua language level (5.1–5.4) does not support —
goto/labels (5.2), bitwise operators and integer division (5.3), and variable attributes
(5.4) — as a user-controllable `<localInspection>` with quick fixes. This **replaces** the
existing `LuaLanguageLevelAnnotator`, migrating its checks and quick fixes into the
inspection model so users get an enable/disable toggle and configurable severity. Parent
epic: [[features/inspections/requirements|INSP]].

## Scope

### In Scope
- A single `LocalInspectionTool` that reads the project language level and warns on
  constructs newer than that level.
- All five construct families the annotator already covers: goto, label, bitwise binary
  operators (`&` `|` `~` `<<` `>>`), unary bitwise-not (`~`), integer division (`//`), and
  variable attributes (`<const>` / `<close>`).
- The four existing quick fixes carried over as `LocalQuickFix`: upgrade language level,
  remove goto, remove label, replace integer division.
- Removal of the `LuaLanguageLevelAnnotator` `<annotator>` registration and migration of its
  test coverage to the inspection (so no construct is double-reported).

### Out of Scope
- `\z` line-continuation string escapes (5.2+) and hexadecimal float literals: the lexer
  does not preserve a distinct token for these (escapes live inside a single `STRING`
  token), so they are not detectable at the PSI level without lexer changes. Deferred — see
  [risks-and-gaps.md](risks-and-gaps.md).
- New language levels or settings UI changes (handled by the `settings/` package).

## Functional Requirements

| ID | Requirement | Priority | Description |
|----|-------------|----------|-------------|
| INSP-09-01 | **Attribute checking** | M | Warn on a `LuaAttrib` (`<const>` / `<close>`) when level < 5.4 |
| INSP-09-02 | **Bitwise binary operators** | M | Warn on a `LuaBinOp` whose text is `&` `|` `~` `<<` `>>` when level < 5.3 |
| INSP-09-03 | **Goto statement** | M | Warn on a `LuaGotoStatement` when level < 5.2 |
| INSP-09-04 | **Label** | M | Warn on a `LuaLabel` (`::name::`) when level < 5.2 |
| INSP-09-05 | **Integer division** | M | Warn on a `LuaBinOp` whose text is `//` when level < 5.3 |
| INSP-09-06 | **Unary bitwise-not** | M | Warn on a `LuaUnOp` whose text is `~` when level < 5.3 |
| INSP-09-07 | **Quick fixes** | M | Each problem offers the upgrade-level fix; goto/label/`//` also offer their removal/replace fix |
| INSP-09-08 | **No double-reporting** | M | The `LuaLanguageLevelAnnotator` `<annotator>` registration is removed in the same change |
| INSP-09-09 | **No false positives** | M | Arithmetic/relational/logical operators, `^` (power), `/`, `..`, comments and string literals never flagged at any level |

## Detailed Specifications

### INSP-09-02 / INSP-09-05 / INSP-09-06: Operator detection
`&`, `|`, `~`, `<<`, `>>` and `//` are not dedicated PSI node types — they are children of
`LuaBinOp` (binary) and `LuaUnOp` (unary). The dedicated tokens are
`AMP`/`PIPE`/`NEG`/`BSL`/`BSR`/`INTDIV` (`LuaTokenTypes.java:112-117`, `DIV /` at line 84).
Detection matches the operator's `text` exactly (the proven annotator approach), or
equivalently `firstChild.elementType` against those tokens. `~` is overloaded: as a binary
op it is bitwise-xor (5.3), as a unary op it is bitwise-not (5.3); both are flagged. `^` is
exponentiation (all versions) and must never be flagged.

### INSP-09-01: Attribute detection
A variable attribute parses to a `LuaAttrib` node (`local x <const> = 1` /
`local x <close> = io.open(...)`). One warning per `LuaAttrib`, so
`local x <const>, y <close> = ...` yields two warnings.

## Behavior Rules
- Level is read per element via `LuaProjectSettings.getInstance(element.project).state.languageLevel`.
- `LuaLanguageLevel` is `Comparable` (ascending `LUA50 < LUA51 < LUA52 < LUA53 < LUA54`); gates use
  `level < LuaLanguageLevel.LUA5x`.
- Default level is `LUA54` (`LuaProjectSettings.State.languageLevel`), so an unconfigured
  project produces no warnings.
- Severity is `ERROR` (matches the annotator's prior behavior), configurable by the user.

## Test Cases

| # | Requirement | Given (input) | When (action) | Then (expected) |
|---|-------------|---------------|---------------|-----------------|
| 1 | INSP-09-01 | `local x <const> = 1`, level LUA51 | enable inspection + `doHighlighting()` | One ERROR on the `<const>` `LuaAttrib`, message contains "attribute" |
| 2 | INSP-09-01 | `local x <const> = 1`, level LUA54 | `doHighlighting()` | No language-level error |
| 3 | INSP-09-02 | `local x = 1 & 2`, level LUA51 | `doHighlighting()` | One ERROR on the `&` operator, message contains "Bitwise AND" |
| 4 | INSP-09-02 | `local x = 1 & 2`, level LUA53 | `doHighlighting()` | No language-level error |
| 5 | INSP-09-03 | `goto exit`, level LUA51 | `doHighlighting()` | One ERROR on the goto, message contains "Goto" |
| 6 | INSP-09-04 | `::exit::`, level LUA51 | `doHighlighting()` | One ERROR on the label, message contains "Label" |
| 7 | INSP-09-05 | `local x = 10 // 3`, level LUA52 | `doHighlighting()` | One ERROR on the `//`, message contains "Integer division" |
| 8 | INSP-09-06 | `local x = ~5`, level LUA52 | `doHighlighting()` | One ERROR on the `~`, message contains "Bitwise NOT" |
| 9 | INSP-09-07 | `goto exit`, level LUA51 | `findSingleIntention("Remove goto statement")` + `launchAction` + `checkResult("")` | The goto statement is deleted |
| 10 | INSP-09-07 | `local x = 10 // 3`, level LUA52 | `findSingleIntention("Replace // with / and math.floor()")` + `launchAction` | Text becomes `local x = math.floor(10 / 3)` |
| 11 | INSP-09-07 | `goto exit`, level LUA51 | `findSingleIntention("Upgrade project to Lua 5.2")` + `launchAction` + re-highlight | After fix the project level is LUA52 and no error remains |
| 12 | INSP-09-09 | `local s = "1 & 2"` and `-- goto`, level LUA51 | `doHighlighting()` | No language-level error (string/comment content ignored) |
| 13 | INSP-09-09 | `local x = 2 ^ 3` and `local y = 10 / 3`, level LUA51 | `doHighlighting()` | No language-level error (power and division allowed) |

## Acceptance Criteria
- [ ] INSP-09-01..06 each produce an ERROR at the correct sub-level and none at/above their level.
- [ ] INSP-09-07 quick fixes behave as Test Cases 9–11.
- [ ] INSP-09-08 the annotator registration is gone and no construct is reported twice.
- [ ] INSP-09-09 no false positives on arithmetic/`^`/`/`/strings/comments.

## Non-Functional Requirements
- Runs in the platform read action (inspection visitor); no EDT blocking, no I/O.
- No retained refs to `Project`/`PsiElement` beyond the visit; level read fresh per element.

## Dependencies
- `LuaProjectSettings` (settings) and `LuaLanguageLevel` (lang) — both exist.
- No INSP-09 dependency on other inspection features.

## See Also
- Design: [design.md](design.md)
- Plan: [implementation-plan.md](implementation-plan.md)
- Risks: [risks-and-gaps.md](risks-and-gaps.md)
