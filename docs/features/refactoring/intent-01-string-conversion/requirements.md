---
id: INTENT-01
title: String Conversion Requirements
type: feature
parent_id: REFACT/INTENT
status: done
folders:
  - "[[features]]"
---

# String Conversion Requirements

An Alt+Enter intention that cycles a Lua string literal between the three Lua string
forms: single-quoted (`'…'`) → double-quoted (`"…"`) → long bracket (`[[…]]`) → back to
single-quoted. The intention is offered whenever the caret is inside a string literal and
rewrites the literal in place, adjusting escaping/unescaping so the *runtime value* of the
string is preserved across the conversion.

## Requirements Table

| ID | Requirement | Priority | Status | Description |
|---|---|---|---|---|
| INTENT-01-01 | Cycle Quote Type | Must | Full | Provide an intention to cycle a string literal between single quotes (`'…'`), double quotes (`"…"`), and long brackets (`[[…]]`), preserving the runtime string value (escaping/unescaping as needed). |
| INTENT-01-02 | Caret-in-string applicability | Must | Full | The intention is offered only when the caret is inside a string literal (`LuaTerminalExpr` whose child leaf is the `STRING` token), and not elsewhere. |
| INTENT-01-03 | Long-string `]]` guard | Should | Full | When the next form in the cycle is the long-bracket form and the content contains the closing sequence `]]` (or the chosen level's closer), skip directly to the following form rather than producing a broken literal, OR raise the bracket level so a safe closer exists. |

## Test Cases

### Test Case 1: Single → Double
**Requirement:** INTENT-01-01
**Input:** `local s = 'hello'<caret>` (caret inside the string)
**Action:** Invoke "Convert to double-quoted string".
**Expected Output:** `local s = "hello"`

### Test Case 2: Double → Long bracket
**Requirement:** INTENT-01-01
**Input:** `local s = "hello"<caret>`
**Action:** Invoke "Convert to long-bracket string".
**Expected Output:** `local s = [[hello]]`

### Test Case 3: Long bracket → Single
**Requirement:** INTENT-01-01
**Input:** `local s = [[hello]]<caret>`
**Action:** Invoke "Convert to single-quoted string".
**Expected Output:** `local s = 'hello'`

### Test Case 4: Escaping round-trip — single → double re-escapes delimiters
**Requirement:** INTENT-01-01
**Input:** `local s = 'a"b'<caret>` (a literal double-quote inside a single-quoted string)
**Action:** Invoke "Convert to double-quoted string".
**Expected Output:** `local s = "a\"b"`

### Test Case 5: Escaping round-trip — double → single re-escapes delimiters
**Requirement:** INTENT-01-01
**Input:** `local s = "a\"b"<caret>`
**Action:** Invoke "Convert to single-quoted string".
**Expected Output:** `local s = 'a"b'`
(The `\"` is no longer the active delimiter, so it is unescaped to a bare `"`; the round-trip
of Test Case 4 then Test Case 5 returns the original `'a"b'`.)

### Test Case 6: Escaping — quote-in-content when converting to that quote
**Requirement:** INTENT-01-01
**Input:** `local s = "it's"<caret>`
**Action:** Invoke "Convert to single-quoted string".
**Expected Output:** `local s = 'it\'s'`

### Test Case 7: Escapes vanish crossing into long bracket
**Requirement:** INTENT-01-01
**Input:** `local s = "tab\there"<caret>` (an actual tab via `\t`)
**Action:** Invoke "Convert to long-bracket string".
**Expected Output:** `local s = [[tab` + (literal TAB) + `here]]`
(Long strings do no escape processing, so the runtime value — a real tab character — is
written literally into the bracket content; the `\t` escape sequence is *resolved*, not copied.)

### Test Case 8 (INTENT-01-03): Long-bracket guard on `]]` content
**Requirement:** INTENT-01-03
**Input:** `local s = "a]]b"<caret>`
**Action:** Invoke the cycle action that would target the long-bracket form.
**Expected Output:** the intention raises the bracket level to a safe closer:
`local s = [==[a]]b]==]` (the content has no `]==]`, so level-1 brackets are safe).

### Test Case 9 (INTENT-01-02): Not offered outside a string
**Requirement:** INTENT-01-02
**Input:** `local s<caret> = 1`
**Action:** Query available intentions.
**Expected Output:** "Convert to …-quoted string" intentions are NOT present.

## See Also
- Design: [design.md](design.md)
- Implementation plan: [implementation-plan.md](implementation-plan.md)
- Risks & gaps: [risks-and-gaps.md](risks-and-gaps.md)
