---
id: "BUG-482"
title: "A multi-line argument list is joined onto the call line instead of continuation-indented"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-482: A multi-line argument list is joined onto the call line instead of continuation-indented

Found 2026-10-10 by [[BUG-461]], whose build-time guard and `@Test` additions made previously
uncollected or vacuous tests actually run. Measured on `e762dc74`.

## Reproduction

Reformat (`CodeStyleManager.reformatText`) with `CONTINUATION_INDENT_SIZE = 4`:

```lua
print(
a,
b,
c
)
```

## Expected vs actual

- **Expected** (`TestLuaFormatBlock.testArgs`, now `@Test` + `@Disabled("BUG-482 …")`): each argument on
  its own line, continuation-indented.
- **Actual**: `print(a,` / `      b,` / `      c)` — the line break after `(` and before `)` is removed and the
  remaining arguments are aligned under the first. `FORMAT-01-17` describes this as "arguments land at 8
  columns"; the measured output is different from that description as well, so the requirement text
  needs correcting alongside the fix.

Re-measured on a clean, correctly locked run (GCE builder, `@Disabled` removed temporarily,
document text printed after `reformatText`; default code style, plus `CONTINUATION_INDENT_SIZE = 4`
as the test sets):

```
actual:                 expected (testArgs):
print(a,                print(
      b,                    a,
      c)                    b,
                            c
                        )
```

The `FORMAT-01-17` row's "8 columns" is therefore not what happens: the arguments are not on
continuation lines at all.

## Fix strategy

Decide the intended shape first (keep user line breaks after `(` vs align under the first argument,
cf. `FORMAT-01-16` for parameter lists), then fix `LuaFormatBlock`/`LuaSpacingBuilder`. Un-ignore `testArgs`.
