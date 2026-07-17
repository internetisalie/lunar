---
id: "BUG-386"
title: "Long-string/long-comment annotators throw StringIndexOutOfBounds on truncated tokens mid-typing"
type: "bug"
parent_id: "BUG"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-386: Long-string/long-comment annotators throw on truncated tokens

> **RESOLVED 2026-07-17 (commit 113b9ef4)**: Replaced raw `text[level+1]`/`text[level+3]` indexing in `LuaLongStringAnnotator` and `LuaLongCommentAnnotator` with the bounds-checked helpers `getLuaStringDelimiterLength` / `getLuaCommentDelimiterLength`; added `LuaLongBracketAnnotatorTest` with truncated-token no-throw assertions and positive highlighting assertions.

*Source: codebase review [`docs/review.md`](../../../review.md) finding **#15** (P1; still
present 2026-07-17).*

## 1. Reproduction

1. In a Lua file, type the start of a long-bracket string or comment and stop mid-delimiter at
   end of file: `[==` or `--[=`.
2. Let the highlighting pass run.

## 2. Expected vs Actual Behavior

- **Expected**: no error; the annotators simply skip delimiters they cannot classify yet.
- **Actual**: `lang/syntax/LuaAnnotators.kt:79,101-103` index the token text
  (`text[level+1]` / `text[level+3]`) without bounds checks — truncated tokens throw
  `StringIndexOutOfBoundsException` inside the annotator (surfaces as a red "highlighting
  errors" indicator / exception balloon while typing).

## 3. Notes

Fix per the review: reuse the existing bounds-checked helpers
`LuaLiterals.getLuaStringDelimiterLength` / `LuaComment.getLuaCommentDelimiterLength` instead of
raw indexing. Isolated annotator fix — kept as a BUG rather than folded into a Wave-19 MAINT
cluster (it touches neither the LuaCATS subsystem nor the inspection stack).
