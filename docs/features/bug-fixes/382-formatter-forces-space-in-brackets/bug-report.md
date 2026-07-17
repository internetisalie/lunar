---
id: "BUG-382"
title: "Reformat forces spaces inside index brackets (`t[ 1 ]`); the Space-within-brackets setting is unreachable"
type: "bug"
parent_id: "BUG"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-382: Reformat forces spaces inside index brackets

*Source: codebase review [`docs/review.md`](../../../review.md) finding **#23** (verified in
source 2026-07-02 and still present 2026-07-17).*

## 1. Reproduction

1. In a Lua file, write `local x = t[1]`.
2. Run **Code → Reformat Code**.

## 2. Expected vs Actual Behavior

- **Expected**: `t[1]` unchanged by default; the *Spaces → Within → Brackets* code-style setting
  (`SPACE_WITHIN_BRACKETS`) controls whether `t[ 1 ]` is produced.
- **Actual**: reformat always produces `t[ 1 ]` — `LuaFormatBlock.kt:300-304` has a rule
  commented "No spacing inside brackets" that returns `SINGLE_SPACING`; the
  `SPACE_WITHIN_BRACKETS` branch below it is unreachable.

## 3. Notes

Fix per the review: return `NO_SPACING` (or defer to the `spacingBuilder` `withinPair` rule) so
the setting becomes authoritative. Add a formatter test asserting both setting states.
