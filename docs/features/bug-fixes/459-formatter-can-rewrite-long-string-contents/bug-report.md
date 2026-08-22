---
id: "BUG-459"
title: "Reformatting may shift the interior lines of a long string, editing the value of a literal"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "high"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-459: a formatter that changes what the program means

Found 2026-08-22 by the [[FORMAT-01]] retroactive-requirements agent. **Predicted from the platform
chain, not observed** — §4 must run first. Filed at high priority despite that, because the
predicted failure silently changes data rather than layout.

## 1. Reproduction (predicted)

1. Write a multi-line long string that starts on its own line and whose interior lines are indented
   differently from the surrounding code:

   ```lua
   local sql = [[
   SELECT *
     FROM t
   ]]
   ```
2. Reformat the file.

## 2. Expected vs actual

- **Expected**: the contents of `[[ ... ]]` are untouched. The value of a string literal is data.
- **Actual (predicted)**: the interior lines are re-indented along with the code, changing the
  string's value — and therefore the SQL, the message, or whatever the literal holds.

## 3. Root cause

`FormatProcessorUtils.replaceWhiteSpace` calls `FormattingModel.shiftIndentInsideRange` for any leaf
that contains line feeds and follows whitespace containing one — which a long string on its own line
does.

The usual protection is that `FormattingModelProvider` yields a `PsiBasedFormattingModel`, whose
`shiftIndentInsideRange` is a no-op. **That protection is bypassed**: `CodeFormatterFacade` wraps the
plugin's model in a `DocumentBasedFormattingModel`, whose implementation is real.

Lunar implements neither `FormattingModelWithShiftIndentInsideDocumentRange` nor a read-only
`Spacing` for `STRING`, so nothing stops the shift.

**No test in the repo contains a `[[` at all**, which is why this has never been observed either
way.

## 4. Settle it before fixing

Reformat a file containing an indented long string in a sandbox IDE and diff the literal's bytes.
Three outcomes: the value changes (fix urgently), the value is preserved by some path not identified
here (close, and record why), or the formatter declines to touch the construct (close).

## 5. Fix strategy sketch

If confirmed: give `STRING` and `LONGCOMMENT` read-only spacing so the formatter treats them as
atomic, and consider implementing `FormattingModelWithShiftIndentInsideDocumentRange` to make the
no-op explicit rather than incidental.

## 6. Test strategy

A regression test must assert the **exact bytes** of the literal across a reformat. Add long strings
to the formatter fixture corpus generally — the absence of a single `[[` in the suite is itself the
finding here.
