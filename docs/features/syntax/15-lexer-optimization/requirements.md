---
id: SYNTAX-15
title: Lexer Optimization Requirements
type: feature
parent_id: SYNTAX
status: done
---

# Lexer Optimization Requirements

## Requirements Table
| ID | Requirement | Priority | Status | Description |
|---|---|---|---|---|
| SYNTAX-15-01 | Optimize Strings | Must | **Full** | Optimize lexing of string literals and long comments in JFlex to prevent deep recursion. |

## Implementation Note (2026-06-16)

**Already satisfied by the existing lexer — no change required.** `lua.flex` already lexes long
strings and long comments with **exclusive JFlex states** (`%x XLONGSTRING`, `%x XLONGSTRING_BEGIN`,
`%x XLONGCOMMENT`, `%x XSHORTCOMMENT`, `%x XSTRINGQ`, `%x XSTRINGA`) scanned character-by-character,
not via a single recursive/backtracking regex. This is exactly the state-based scanning the design
proposed, so there is no deep-recursion (stack-overflow) risk on large literals. Per the project
contract ("profile before optimizing"; don't churn the core lexer without a measured bottleneck),
this item is closed as already-met rather than reworked.
