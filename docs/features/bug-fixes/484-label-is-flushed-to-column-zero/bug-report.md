---
id: "BUG-484"
title: "A `::label::` inside a nested block is flushed to column 0"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-484: A `::label::` inside a nested block is flushed to column 0

Found 2026-10-10 by [[BUG-461]], whose build-time guard and `@Test` additions made previously
uncollected or vacuous tests actually run. Measured on `e762dc74`.

## Reproduction

```lua
if restart then
  ::start::
  goto start
end
```

reformatted with defaults.

## Expected vs actual

- **Expected**: `::start::` at the block indent (4 columns), like the `goto` below it.
- **Actual**: column 0. `LABEL` is given `Indent.getAbsoluteLabelIndent()`, indenting from the leftmost
  column by `LABEL_INDENT_SIZE` (default 0). This is `FORMAT-01-22` (Not Implemented).
  `TestLuaFormatBlock` `testLabel PINS BUG-484 …` asserts the defective output, so fixing it turns that
  test red by design.

## Fix strategy

Use `Indent.getNormalIndent()` or `Indent.getLabelIndent()` (relative) for `LABEL`; flip the pinned test
to the correct expectation and rename it.
