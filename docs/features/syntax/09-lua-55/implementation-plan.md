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

## Phase 1: Language Level & Token Pipeline [Must]

**Goal**: Define LUA55 and make `global` lex as a keyword.

1. Add `LUA55(5, 5)` to `LuaLanguageLevel.kt`.
2. Update `Target.kt:43` to map `"5.5"` to `LUA55`.
3. Add `LUA55` migration branch in `LuaProjectSettings.kt:69-81`.
4. Update `LuaInterpreter.kt:109-116` leveler: `version.startsWith("5.5") -> LuaLanguageLevel.LUA55`.
5. Add `IElementType GLOBAL = new LuaElementType("global")` to `LuaTokenTypes.java`.
6. Add `"global" { return GLOBAL; }` between `"function"` and `"goto"` in `lua.flex`.
7. Add `LuaTokenTypes.GLOBAL to LuaElementTypes.GLOBAL` between `FUNCTION` and `GOTO` in `LuaLexer.kt`.
8. Rebuild lexer: `./gradlew generateLuaLexer`.
9. Add lexer test in `TestLuaLexerExhaustive` for `global` token.

## Phase 2: Parser & PSI Generation [Must]

**Goal**: Generate `LuaGlobalVarDecl`, `LuaGlobalFuncDecl`, `LuaGlobalModeDecl` nodes.

1. Add `GLOBAL = 'global'` keyword to `lua.bnf`.
2. Define `globalVarDecl`, `globalFuncDecl`, `globalModeDecl` grammar rules (see design §2.3).
3. Run `./gradlew generateLuaParser`.
4. Add parser tests in `TestLuaParsingExhaustive`:
   - `global x = 10` → `LuaGlobalVarDecl`, no `PsiErrorElement`.
   - `global <const> *` → `LuaGlobalModeDecl`, no parse error.
   - `global function f() return 1 end` → `LuaGlobalFuncDecl`.

## Phase 3: Scope Resolution [Must]

**Goal**: Make `LuaGlobalVarDecl` visible to `LuaNameReference`.

1. Consolidate `LuaFile.processDeclarations` three child-loops into single-pass `when` (see design §2.4a).
2. Add `LuaGlobalVarDecl` branch in `LuaBlockExt.processDeclarations` `when` (see design §2.4b).
3. **Unit test**: `global x = 10; print(x)` — verify `print(x)` resolves to `LuaGlobalVarDecl`.

## Phase 4: Inspection [Must]

**Goal**: Warn when `global` is used at language level < LUA55.

1. Add `visitGlobalVarDecl` to `LuaLanguageLevelInspection` using `register()` helper.
2. Add inspection test in `LuaLanguageLevelInspectionTest`:
   - At `LUA54`: warning on `global x = 10`.
   - At `LUA55`: no warning.
   - Quick fix upgrades level to LUA55.

## Phase 5: Verification [Must]

**Goal**: Manual verification via `human-verification-checklists.md`.
