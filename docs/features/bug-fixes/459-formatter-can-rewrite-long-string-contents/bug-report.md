---
id: "BUG-459"
title: "Reformatting may shift the interior lines of a long string, editing the value of a literal"
type: "bug"
parent_id: "BUG"
status: "cancelled"
priority: "low"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-459: a formatter that changes what the program means

Found 2026-08-22 by the [[FORMAT-01]] retroactive-requirements agent as a prediction from the
platform chain. **REFUTED LIVE the same day — the formatter does not touch a long string's
contents.** Kept rather than deleted because §4's measurement is the useful part, and because the
next reader of that platform chain will reach the same prediction and should find this instead.

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

## 4. Live verification, 2026-08-22 — refuted, twice, with a control

Sandbox GoLand on the builder VM. Two probes, each deliberately mis-indented so the formatter had to
do visible work — **without that control an unchanged file proves nothing**, and the first attempt
here did exactly that: the fixture was already well-formatted, the reformat was a no-op, and the
byte-identical result was meaningless. (A second false result came from fixtures created over an
SSH root login: the IDE could not write them and showed "Failed to change read-only status". Both
near-misses are why the control matters.)

**Probe 1 — indent increases.** Code at columns 0/8/2/6 was reformatted to column 4 throughout.
Long string interior before and after, byte for byte:

```
SELECT *
  FROM t
 WHERE id = 1
```

**Probe 2 — indent decreases sharply, nested.** Code at columns 20/28 was reformatted to 4/8. Long
string interior before and after, byte for byte:

```
line one
    line two indented
```

In both directions, and across a 20-column shift, **the literal is unchanged**.

## 4a. What this does not establish

The measurement is decisive about the behaviour and silent about the mechanism. §3's chain predicted
`shiftIndentInsideRange` would fire, and it evidently does not — but this run did not determine
*why*: whether the wrapping model differs from the prediction, whether the leaf condition is not
met, or whether something else intervenes. Anyone changing `lang/format/` should re-run these two
probes rather than assume the protection is structural, because nothing here proves it is.

## 5. What is still worth doing

No fix. Two smaller things survive this report and should be carried into [[FORMAT-01]] rather than
kept open here:

- **The suite contains no `[[` at all.** That was true before this bug and is still true. A
  regression test asserting the exact bytes of a long string across a reformat would convert
  today's measured behaviour into a guarantee — currently it is neither guaranteed nor tested.
- Making the protection explicit (read-only `Spacing` for `STRING`, or implementing
  `FormattingModelWithShiftIndentInsideDocumentRange`) would turn an unexplained absence of a bug
  into a stated invariant. Optional, and only worth it alongside the test.
