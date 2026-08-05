---
id: "BUG-411"
title: "Vertical tab and form feed are not treated as whitespace, so valid Lua reports a syntax error"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "low"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-411: Vertical tab and form feed are not treated as whitespace

**Found by**: MAINT-35-06's torture corpus, on its **first run** — the single disagreement across
1 696 fuzzer inputs (`falseReject:output/fuzz_strings/a6cec286…`, whose entire content is one
`0x0B` byte).

## Symptom

A source file containing `\v` (0x0B) or `\f` (0x0C) between tokens reports a syntax error in the
editor. PUC Lua accepts it at **every** supported level.

## Reproduction

```bash
printf 'local a = 1\x0b\x0clocal b = 2\n' | test/luac/5.1.5/luac -p -   # exit 0
```

Measured against all five pinned oracles — 5.1.5, 5.2.4, 5.3.6, 5.4.8, 5.5.1 — **all accept**.
Lunar produces a `PsiErrorElement`.

## Root cause

`lua.flex:36-38` enumerates whitespace as a closed set that omits both characters:

```
w           =   [ \t]+
wnl         =   [ \r\n\t]+
nl          =   \r\n|\n|\r
```

PUC's lexer does not enumerate. `llex.c` dispatches on `isspace()` for the default case, and the C
locale's `isspace()` is `{' ', '\t', '\n', '\v', '\f', '\r'}` — a superset of `wnl` by exactly the
two characters missing here. This is a transcription gap, not a deliberate narrowing: `\r` is
present, so the intent was clearly to cover the C set.

## Fix

Add `\v\f` (JFlex: `\x0B\x0C`) to `w` and `wnl` in `lua.flex`, regenerate with
`.claude/skills/generate-parser/scripts/generate.sh`, and commit `src/main/gen/`.

Do **not** widen this to "any `isspace()`": JFlex's `[:space:]` is Unicode-aware and would swallow
NEL, NBSP and the Unicode line separators, none of which PUC accepts. The oracle disagrees in the
opposite direction there — `0x1A` and `0x00` are both **rejected** by luac (verified), so a blanket
widening would trade one false reject for several false accepts.

## Verification

- Regression test: `local a = 1\x0b\x0clocal b = 2` parses with zero `PsiErrorElement`s.
- The torture ratchet is the real gate: `oracleDisagreements` for `fuzzing-lua` must go **1 → 0**,
  and the baseline is then re-recorded. 82 of the 1 696 inputs contain one of these bytes, so the
  fix is exercised well beyond its one minimized witness.

## Why low priority

Neither character appears in hand-written Lua; this was found by a fuzzer, not by a user. It is
filed rather than fixed inline because the fix requires a lexer regeneration, which belongs in its
own commit — and because MAINT-35's Definition of Done forbids baselining a disagreement without a
filed defect, not fixing every defect it finds.
