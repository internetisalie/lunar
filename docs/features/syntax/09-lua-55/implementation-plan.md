---
id: "SYNTAX-09-PLAN"
title: "Implementation Plan"
type: "plan"
status: "planned"
parent_id: "SYNTAX-09"
folders:
  - "[[features/syntax/09-lua-55/requirements|requirements]]"
---

# Implementation Plan: SYNTAX-09 — Lua 5.5 Support

## Phase 1: Core Configuration and Lexing [Must]
**Goal**: Introduce the Lua 5.5 level and parse basic tokens.

1.  Update `LuaLanguageLevel.kt`, `Target.kt`, and `LuaProjectSettings.kt` to include `LUA55`.
2.  Update `lua.flex` to recognize the `global` keyword and `//` comments.
3.  Rebuild lexer.

## Phase 2: Grammar and AST [Must]
**Goal**: Allow `globalVarDecl` to be successfully integrated into the AST.

1.  Update `lua.bnf` to include the `GLOBAL` keyword and `globalVarDecl` statement definition.
2.  Run `generateParser` task to build the `LuaGlobalVarDecl` PSI elements.
3.  Add unit tests in `TestLuaParsingExhaustive` to ensure `global a = 1` and `// comment` parse correctly.

## Phase 3: Diagnostics [Must]
**Goal**: Prevent users from using Lua 5.5 syntax in 5.4 or lower environments.

1.  Modify `LuaLanguageLevelInspection.kt` to flag `LuaGlobalVarDecl` and `//` short comments.
2.  Add unit tests in `LuaLanguageLevelInspectionTest` to assert that warnings trigger at `LUA54` and clear at `LUA55`.

## Phase 4: Verification [Must]
**Goal**: Execute the manual verification checklist.
