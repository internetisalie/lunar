---
id: "BUG-413"
title: "Generated doc comments infer wrong @param types (any name containing x or y becomes number)"
type: "bug"
parent_id: "BUG"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-413: Doc generator infers wrong `@param` types

*Source: codebase review [`docs/review.md`](../../../review.md) finding **#75** (P1, second pass
2026-08-06). Confirmed by executing the branch table.*

## 1. Reproduction

1. Write `function process(list, body, key) end`.
2. Type `---` above it to trigger doc-comment generation (`LuaDocGenerator`).

Generated:

```lua
--- description
--- @param list boolean description
--- @param body number description
--- @param key number description
```

## 2. Expected vs Actual Behavior

- **Expected**: `list` → `any[]`; `body` and `key` → `any` (no signal in the name).
- **Actual**: `lang/insight/LuaDocGenerator.kt:134-145` matches **unanchored substrings** in a
  `when` chain, so two independent faults compound:

  1. The `number` arm includes the bare alternatives `"x"` and `"y"`. Any identifier containing
     either letter is typed `number` — measured: `body`, `key`, `proxy`, `syntax`.
  2. The `boolean` arm (`"is"`, `"has"`, `"can"`, `"enabled"`) is tested **before** the `any[]`
     arm, and `list` contains `is` — so `list` can never reach its own rule.

Measured across the table as written (`items` → `any[]` and `opts` → `table` are correct; the
rest are not):

| name | inferred | expected |
|------|----------|----------|
| `list` | `boolean` | `any[]` |
| `body` | `number` | `any` |
| `key` | `number` | `any` |
| `proxy` | `number` | `any` |
| `syntax` | `number` | `any` |

## 3. Notes

Every generated `---@param` line is affected, and a wrong declared type is worse than none: it
feeds the type engine and the assignability inspection, so the user gets false diagnostics on
code the generator itself wrote.

Fix direction: split the identifier into words (camelCase / `snake_case`) and match whole words or
suffixes rather than substrings; drop the single-letter `x`/`y` alternatives entirely (they only
make sense as an exact match for coordinate parameters); order the arms longest-pattern-first so
`list` cannot be captured by `is`.

Test as a table-driven case over the name→type mapping including every name in the table above —
the current implementation passes any test that only checks `count`, `name` and `opts`.
