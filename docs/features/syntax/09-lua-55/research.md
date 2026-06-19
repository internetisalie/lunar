---
id: "SYNTAX-09-RESEARCH"
title: "Research: Lua 5.5 Language Changes"
type: "spec"
status: "done"
parent_id: "SYNTAX-09"
folders:
  - "[[features/syntax/09-lua-55/requirements|requirements]]"
---

# Research: Lua 5.5 Language Changes

## Overview
This research investigates the actual syntax and semantic changes introduced in Lua 5.5
(released December 22, 2025) to determine which features require parser/lexer support in
Lunar and to correct earlier design assumptions.

## Findings / Key Components

### 1. `//` Comments Do Not Exist
The earlier SYNTAX-09 design assumed Lua 5.5 introduces `//` single-line comments. **This
is false.** Lua 5.5 retains `--` as the only comment syntax. The `//` token remains the
integer division (floor division) operator, unchanged since Lua 5.3.

**Source:** [Lua 5.5 Reference Manual](https://www.lua.org/manual/5.5/) — the "Changes
since Lua 5.4" section makes no mention of comment syntax changes. The lexer grammar in the
reference manual confirms `--` comments only.

### 2. Global Variable Declarations (`global`)
Lua 5.5 introduces explicit declarations for global variables. This is the primary syntax
change requiring parser support.

**Impact on Lunar:**
- New keyword `global` must be added to the lexer.
- New grammar rule for global variable declarations in `lua.bnf`.
- The `//` integer division operator (`INTDIV`) is **not affected** — no lexer conflict exists.

### 3. Read-Only For-Loop Variables
For-loop variables are now read-only in Lua 5.5. Assigning to them is a compile error.

**Impact on Lunar:**
- This is a semantic constraint, not a syntax change. The parser already handles for-loops.
- Requires a new inspection or extension of `LuaLanguageLevelInspection` to flag assignments
  to for-loop variables when `LuaLanguageLevel >= LUA55`.

### 4. Named Vararg Tables
New support for named vararg tables.

**Impact on Lunar:** Requires further investigation of the exact syntax before designing
parser support.

### 5. `table.create`
A new standard library function. No parser changes needed — it's a regular function call.

**Impact on Lunar:**
- Add `table.create` to the Lua 5.5 stdlib definitions (platform library stubs).

### 6. Other Changes (No Parser Impact)
- More compact arrays (runtime optimization — no IDE impact)
- Incremental major GC (runtime — no IDE impact)
- External strings (C API — no IDE impact)
- `luaL_openselectedlibs`, `luaL_makeseed` (C API — no IDE impact)
- Float decimal printing (runtime — no IDE impact)

## Prior Art & References
- [Lua 5.5 Reference Manual](https://www.lua.org/manual/5.5/)
- [Lua 5.5 Readme / Changelog](https://www.lua.org/manual/5.5/readme.html)
- Existing `PlatformVersionRegistry.kt` already has a `5.5` version entry (with `luacheckStd = "lua54"`)
- Existing `Target.kt` maps `version.label == "5.5"` to `LuaLanguageLevel.LUA54` (placeholder)

## Recommendations
1. **Drop `SYNTAX-09-03` (`//` comments) entirely.** It was based on a hallucination. Lua 5.5
   does not change comment syntax.
2. **Focus the design on**: `global` keyword parsing, `LuaLanguageLevel.LUA55` integration,
   read-only for-loop variable inspection, and stdlib stub updates.
3. **De-risk named vararg tables** before designing — the exact syntax needs to be confirmed
   from the reference manual.
4. **Address the reviewer's feedback** on the token pipeline (`LuaTokenTypes.java`,
   `LuaLexer.kt` mapping), stub infrastructure for `LuaGlobalVarDecl`, `LuaStubElementTypes`
   factory entry, `processDeclarations` scoping semantics, and correct element type references.

## Common Pitfalls
- Do not assume Lua 5.5 introduces C-style `//` comments — this is a common misconception.
- The `//` token is and remains integer division.

## Open Questions
- What is the exact syntax for named vararg tables? (Needs reference manual deep-dive.)
- Does `global` have block-scoping semantics or is it always file-scoped?
