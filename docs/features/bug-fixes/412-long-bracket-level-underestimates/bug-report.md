---
id: "BUG-412"
title: "\"Convert to long string\" emits invalid Lua when the value contains a closing long bracket"
type: "bug"
parent_id: "BUG"
priority: "high"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-412: Long-string conversion emits invalid Lua

*Source: codebase review [`docs/review.md`](../../../review.md) finding **#74** (P1, second pass
2026-08-06). Confirmed against PUC Lua.*

## 1. Reproduction

1. In a Lua file, write `local s = "]=]"`.
2. Put the caret on the string and invoke the **Convert to long string** intention
   (`LuaStringConversionIntention`).

Result: `local s = [[]=]]]`.

3. Run `luac -p` on the file (or simply re-open it — the parser reports the same):

```
luac: bad.lua:1: unexpected symbol near ']'
```

`"]==]"` fails identically. `"]]"` happens to work, which is why the defect is easy to miss.

## 2. Expected vs Actual Behavior

- **Expected**: the conversion picks a long-bracket level whose closer does not occur in the
  value — for `]=]` that is level 2, giving `[==[]=]]==]`.
- **Actual**: `lang/syntax/LuaLiterals.kt:133-139`

  ```kotlin
  fun longBracketLevel(value: String): Int {
      var level = 0
      while (value.contains("]" + "=".repeat(level) + "]")) {
          level++
      }
      return level
  }
  ```

  returns the first level **absent** from the value, not the highest level *present* plus one.
  For `]=]` it tests `]]` (absent) and returns 0 immediately, so `encodeLong` (`:141-146`) wraps
  the value in level-0 brackets. The `]]` scanner then closes the string early and the trailing
  `]` is a syntax error.

The intention is registered and enabled by default, so this is a destructive quick fix on
otherwise-valid source — the same class as review findings #8 and #9.

## 3. Notes

Fix direction: scan for the highest `]=*]` sequence actually present in the value and return that
level + 1 (0 when none is present). Note that `getLuaStringDelimiterLength` in the same file is
correct and must not be changed — review #15 / BUG-386 deliberately routed the annotators onto it.

Regression test should be a round-trip property over the pairing rather than a fixed example:
`extractLuaString(encodeLuaString(v, LONG)) == v` for every `v` in a table that includes `]]`,
`]=]`, `]==]`, `]=]=]` and a value with no brackets at all. A single-example test would pass today
for `]]` and prove nothing.
