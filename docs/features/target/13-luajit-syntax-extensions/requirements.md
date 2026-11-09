---
id: "TARGET-13"
title: "13: LuaJIT 2.1 Syntax Extensions"
type: "feature"
status: "todo"
priority: "medium"
parent_id: "TARGET"
folders:
  - "[[features/target/requirements|requirements]]"
---

# TARGET-13: LuaJIT 2.1 Syntax Extensions

## Overview
LuaJIT 3.0 is unreleased, but LuaJIT 2.1 ships twelve of its syntax extensions as backports
(`doc/extensions.html`, "Backported Syntax Extensions from LuaJIT 3.0", added in LuaJIT `a2ce8114`,
2026-07-20). Lunar maps a `LuaJIT` target to `LuaLanguageLevel.LUA51` (`Target.kt:45`) and parses it as
standard Lua 5.1, so code using any of these forms gets parse errors. This feature accepts them, for
the LuaJIT target only. Parent: [[features/target/requirements|TARGET]]; builds on TARGET-12.

## Scope

### In Scope
- The twelve backported extensions, as listed in `extensions.html` (TARGET-13-01 to -12).
- `goto` and `\u{}` at the LuaJIT target's level, deferred from TARGET-12 (TARGET-13-13).
- Type-engine meaning for the forms that produce values (TARGET-13-14).

### Out of Scope
- LuaJIT 3.0 forms that were not backported.
- Accepting these forms on PUC Lua or Redis targets.
- MAINT-55's `luals-syntax` conformance case (`x.upper()?.field`) stays a deviation until this ships.

## Functional Requirements

| ID | Requirement | Priority | Description |
|----|-------------|----------|-------------|
| TARGET-13-01 | **Bit operators** | M | Unary `~`; binary `& \| ~ << >> ~>>` parse as operators on a LuaJIT target. |
| TARGET-13-02 | **Customary operators** | M | `!`, `&&`, `\|\|`, `!=` parse as `not`, `and`, `or`, `~=`. |
| TARGET-13-03 | **Ternary** | M | `c ? a : b` parses as a conditional expression. |
| TARGET-13-04 | **Safe navigation** | M | `a?.b`, and the forms LuaJIT defines for calls and indexing, parse. |
| TARGET-13-05 | **nil-coalescing** | M | `a ?? b` parses. |
| TARGET-13-06 | **Compound assignment** | M | `+= -= *= /= %= &= \|= ~= <<= >>= ~>>= ..=` parse as assignment statements. |
| TARGET-13-07 | **`continue`** | M | `continue` parses as a statement inside a loop. |
| TARGET-13-08 | **`const`** | M | A `const` declaration parses. |
| TARGET-13-09 | **Short function expression** | M | The short form parses as a function expression. |
| TARGET-13-10 | **Number underscores** | M | `1_000_000` lexes as one number. |
| TARGET-13-11 | **Semicolons anywhere** | S | Empty statements are accepted wherever LuaJIT accepts them. |
| TARGET-13-12 | **Line number directive** | C | The directive lexes without an error. |
| TARGET-13-13 | **`goto` and `\u{}`** | S | Both are accepted on the LuaJIT target. |
| TARGET-13-14 | **Type meaning** | S | `a?.b` and `a ?? b` remove `a`'s nil; `c ? x : y` is the union of `x` and `y`. |
| TARGET-13-15 | **Target-gated** | M | On every non-LuaJIT target each form is still an error, as today. |

## Detailed Specifications
The exact grammar of each form is not yet established and is the first planning task (see Risks):
the summary list in `extensions.html` names the forms, and LuaJIT's lexer and parser
(`src/lj_lex.c`, `src/lj_parse.c` at or after `a2ce8114`) and `LuaJIT/LuaJIT#1475` define them.
Requirements -04, -08, -09, -11 and -12 in particular cannot be specified from the list alone.

## Behavior Rules
- Gating is by target, never by file: one project, one target (TARGET-01).
- A form accepted under TARGET-13 highlights with the token class of its standard equivalent
  (`!=` as `~=`, `&&` as `and`).

## Test Cases

| # | Requirement | Given (input) | When (action) | Then (expected) |
|---|-------------|---------------|---------------|-----------------|
| 1 | TARGET-13-01 | `local x = a & b ~>> 2` on LuaJIT 2.1 | parse | no `PsiErrorElement` |
| 2 | TARGET-13-02 | `if !a && b != c then end` on LuaJIT 2.1 | parse | no `PsiErrorElement` |
| 3 | TARGET-13-03 | `local v = c ? 1 : 2` on LuaJIT 2.1 | parse | no `PsiErrorElement` |
| 4 | TARGET-13-04 | `local s = x?.field` on LuaJIT 2.1 | parse | no `PsiErrorElement` |
| 5 | TARGET-13-05 | `local s = x ?? "d"` on LuaJIT 2.1 | parse | no `PsiErrorElement` |
| 6 | TARGET-13-06 | `n += 1; s ..= "x"` on LuaJIT 2.1 | parse | no `PsiErrorElement` |
| 7 | TARGET-13-07 | `for i = 1, 3 do if i == 2 then continue end end` on LuaJIT 2.1 | parse | no `PsiErrorElement` |
| 8 | TARGET-13-10 | `local n = 1_000_000` on LuaJIT 2.1 | lex | one number token |
| 9 | TARGET-13-15 | case 4's input on Lua 5.4 | parse | a `PsiErrorElement`, as today |
| 10 | TARGET-13-14 | `---@type string?` then `local s = x ?? "d"` on LuaJIT 2.1 | infer `s` | `string` |

Cases for -08 and -09 are written once their grammar is established.

## Acceptance Criteria
- [ ] Every `M` requirement has a passing parser test on LuaJIT 2.1 and a negative test on Lua 5.4.
- [ ] TARGET-13-14's inference cases pass.
- [ ] MAINT-55's `luals-syntax` deviation is withdrawn and its case is rescored.

## Non-Functional Requirements
- Lexer and parser regeneration through `.claude/skills/generate-parser/scripts/generate.sh`.
- No parse-time cost on non-LuaJIT targets.

## Dependencies
- TARGET-12 (LuaJIT target and stubs, done).
- The lexer stack (`LuaLexer` merging adapters) and `lua.bnf`; see AGENTS.md "The lexer is a stack of
  merging adapters".

## Risks
- **Grammar unknown.** Read `lj_parse.c` before designing; do not design from the summary list.
- **Version split.** The backports arrived in a rolling 2.1, so older 2.1 builds reject them. Decide
  whether the target needs a finer version than "2.1".
- **Lexer conflicts.** `~` is unary bit-not, binary xor and the start of `~=`; `!` and `?` are new
  characters; `~=` as compound xor-assign collides with standard Lua's not-equal.
