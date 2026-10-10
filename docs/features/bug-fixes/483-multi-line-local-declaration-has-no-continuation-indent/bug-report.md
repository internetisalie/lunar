---
id: "BUG-483"
title: "A multi-line local declaration gets no continuation indent on its names"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-483: A multi-line local declaration gets no continuation indent on its names

Found 2026-10-10 by [[BUG-461]], whose build-time guard and `@Test` additions made previously
uncollected or vacuous tests actually run. Measured on `e762dc74`.

## Reproduction

```lua
local a,
b,
c = 1,
2,
3
```

reformatted with defaults.

## Expected vs actual

- **Expected** (`TestLuaFormatBlock.testLocalVarDecl`, now `@Test` + `@Disabled("BUG-483 …")`): names and
  values continued under the first of each list (`b`, `c` at column 6; `2`, `3` at column 10).
- **Actual**: `b` and `c` stay at column 0 and the values `2`, `3` at column 4.

Re-measured on a clean, correctly locked run (`@Disabled` removed temporarily, document text
printed after `reformatText`), verbatim:

```
actual:                 expected (testLocalVarDecl):
local a,                local a,
b,                            b,
c = 1,                        c = 1,
    2,                            2,
    3                             3
```

## Fix strategy

Give the `VAR_LIST`/`NAME_LIST` continuation lines of a `local` declaration an indent or alignment
consistent with the decision taken in [[BUG-482]]; un-ignore `testLocalVarDecl`.
