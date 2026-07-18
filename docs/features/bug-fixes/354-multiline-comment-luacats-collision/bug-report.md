---
id: "BUG-354"
title: "Multi-line regular comments starting with dashes cause LuaCATS syntax errors"
type: "bug"
parent_id: "BUG"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-354: Multi-line regular comments starting with --- cause LuaCATS syntax errors

## 1. Reproduction

Open a Lua file in the plugin environment and paste the following snippet:

```lua
----------------------------------------------------------------------------
-- Scheduling
--
-- Tasks ready to be run are placed on a stack and it's possible to
-- starve a coroutine.
----------------------------------------------------------------------------
```

1. Look at the second line (`-- Scheduling`).
2. Observe syntax highlighting / annotations on that line.

## 2. Expected vs Actual Behavior

- **Expected**: The entire block is parsed as a regular Lua comment (since the subsequent lines are prefixed with `--` rather than the LuaCATS prefix `---`). No parsing or syntax errors are reported.
- **Actual**: The second line (`-- Scheduling`) has a red underline / syntax error stating `<description>, ... expected, found '-'`.

## 3. Context / Environment

- **Relevant Files**:
  - `src/main/kotlin/net/internetisalie/lunar/lang/lexer/LuaLexer.kt` (classifies merged `SHORTCOMMENT` tokens as `LUACATS_COMMENT`)
  - `src/main/kotlin/net/internetisalie/lunar/luacats/lang/parser/luacats.bnf` (defines the structure of `DASHES`-prefixed comment lines)

- **Other Notes (Diagnostic clues)**:
  - **Comment Merging**: `MultiLineMergingLexerAdapter` merges multiple consecutive single-line comments separated by newlines into a single `SHORTCOMMENT` token.
  - **LuaCATS Classification**: In `LuaLexer.getTokenType()`, if a merged `SHORTCOMMENT` starts with `---` (three dashes), the entire merged comment block is classified as `LuaLazyElementTypes.LUACATS_COMMENT`.
  - **Backwards Compatibility Break**: A decorative header starting with `-------` starts with `---`, so the entire merged block (including subsequent lines starting with only `--`) gets classified as a LuaCATS comment. The LuaCATS parser expects `DASHES` (`---+`) at the start of each line, failing on `-- Scheduling` which only starts with `--`.
  - **Proposed Fix**: The check `text.startsWith("---")` in `LuaLexer.getTokenType()` should be updated to ensure that *every* line in the merged comment token starts with the LuaCATS `---` prefix. If any line in the block starts with only `--` (two dashes), the entire block should fall back to a regular Lua comment.

## Resolution

**Fixed** — `fe91301f` fix(lexer): ensure all lines in a merged comment start with `---` for LuaCATS (marked done in `418d68d8`).
