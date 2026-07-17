---
id: "BUG-361"
title: "`global` is lexed as a keyword, so it is not a valid identifier/field in Lua < 5.5"
type: "bug"
parent_id: "BUG"
priority: "high"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-361: `global` is lexed as a keyword, so it is not a valid identifier/field in Lua < 5.5

## 1. Reproduction

With the project language level set to any of Lua 5.1–5.4:

```lua
local global = 1            -- 'global' as a local name
print(global)

local t = { global = 2 }    -- 'global' as a field key
return t.global             -- 'global' as a member access

local function f(global)    -- 'global' as a parameter
  return global
end
```

Every use of `global` above is ordinary, valid Lua ≤ 5.4, but Lunar tokenizes `global` as the
`GLOBAL` keyword and the parser tries to read a `globalVarDecl` / `globalFuncDecl`, producing parse
errors and a broken PSI tree instead of an identifier.

## 2. Expected vs Actual Behavior

- **Expected**: `global` is a normal identifier under Lua 5.1–5.4 and must lex/parse as `IDENTIFIER`
  in name, field-key, member-access, and parameter positions. Even under **Lua 5.5** the reference
  implementation keeps `global` as a **contextual** keyword (not reserved) specifically to preserve
  backward compatibility — so it should only be treated as a keyword when it *leads a global
  declaration statement*, never when used as a plain name.
- **Actual**: `global` is unconditionally consumed as the `GLOBAL` keyword regardless of language
  level, so any identifier/field named `global` fails to parse.

## 3. Context / Environment

- **Confidence**: high — root-caused in the lexer.
- **Root cause**: [`lua.flex`](../../../../src/main/kotlin/net/internetisalie/lunar/lang/lexer/lua.flex)
  line 74 — `"global" { return GLOBAL; }` — is an unconditional hard-keyword rule. The only
  level-awareness lives *post-parse* in
  [`LuaLanguageLevelInspection`](../../../../src/main/kotlin/net/internetisalie/lunar/analysis/inspections/LuaLanguageLevelInspection.kt)
  ("Global … declarations are only available in Lua 5.5+"), which is too late — the lexer has already
  claimed the token.
- **Relevant files**:
  - `src/main/kotlin/net/internetisalie/lunar/lang/lexer/lua.flex` (the `GLOBAL` rule).
  - `src/main/kotlin/net/internetisalie/lunar/lang/psi/lua.bnf` (`globalVarDecl` / `globalFuncDecl` /
    `globalModeDecl` consume `GLOBAL`).

## 4. Other Notes

- Fix direction (needs a `plan-bug` pass): make `global` a **soft/contextual keyword** — lex it as
  `IDENTIFIER` and only reinterpret it as the global-declaration lead-in when (a) the language level
  is 5.5 **and** (b) it appears in statement-leading position. This is a lexer/parser change and
  therefore requires a `generate-parser` regen (`.flex` + `.bnf` → `src/main/gen`) plus the full
  build/test gate.
- Introduced by SYNTAX-09 (Lua 5.5 `global` declarations); the language-level enforcement was added
  as an inspection rather than gating the token itself.
- Watch for the analogous risk with any other 5.5 contextual keyword.

## 5. Resolution

Fixed by making `global` a **soft/contextual keyword** at the lexer + parser boundary:

- **Lexer** (`lua.flex`): dropped the unconditional `"global" { return GLOBAL; }` rule, so `global`
  now lexes as an ordinary `IDENTIFIER` at every language level.
- **Parser** (`lua.bnf` + new `LuaParserUtil.globalKeyword`): the three declaration rules
  (`globalVarDecl` / `globalFuncDecl` / `globalModeDecl`) now start with the external soft-keyword
  rule `<<globalKeyword>>` instead of the hard `GLOBAL` token. `globalKeyword` remaps the leading
  `IDENTIFIER("global")` to `GLOBAL` **only** when a one-token lookahead confirms a declaration
  follows (`IDENTIFIER` / `function` / `*` / attribute `<`); otherwise it fails without touching the
  builder, so `global.x = 1`, `global()`, `local global`, `t.global` and a `global` parameter all
  parse as plain identifiers. The three global rules are ordered ahead of `assignmentStatement` /
  `exprStatement` so a genuine `global x = 10` is recognised before the assignment fallback.
- **Highlighting** (`LuaGlobalKeywordAnnotator`): the lexer no longer paints `global` as a keyword;
  a new annotator colours the leading `GLOBAL` leaf of a declaration node with the keyword attribute,
  so only the 5.5 declaration keyword is highlighted — identifier/field uses are not.
- **Language level** (`LuaLanguageLevelInspection`): unchanged. Because the parser still produces the
  same `LuaGlobalVarDecl` / `LuaGlobalFuncDecl` / `LuaGlobalModeDecl` nodes, a *real* global
  declaration under Lua 5.1–5.4 is still flagged; a mere identifier named `global` never is.

Verified by `LuaGlobalSoftKeywordTest` (identifier/field/parameter/call uses parse clean pre-5.5;
declarations parse under 5.5; keyword highlighting only on the declaration), the extended
`TestLuaParsingExhaustive.testGlobalAsSoftKeyword`, and the unchanged SYNTAX-09
`LuaLanguageLevelInspectionTest` global cases.
