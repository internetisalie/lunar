---
id: "SYNTAX-09"
title: "Lua 5.5 Support"
type: "feature"
status: "planned"
priority: "low"
parent_id: "SYNTAX"
folders:
  - "[[features/syntax/requirements|requirements]]"
---

# SYNTAX-09: Lua 5.5 Support

## Overview
Lua 5.5 (released December 22, 2025) introduces the \`global\` keyword for explicit global variable declarations, the \`<const>\` and \`<close>\` attributes on variable declarations, read-only for-loop variables, and \`global\` scoping that replaces the implicit \`global *\` default. This feature implements parser/lexer support for the new syntax, updates \`LuaLanguageLevel\` to include \`LUA55\`, and extends \`LuaLanguageLevelInspection\` to warn against Lua 5.5 features when the project language level is below 5.5. See [research.md](research.md) for findings from the Lua 5.5 Reference Manual.

## Scope

### In Scope
- Lexer: tokenize \`global\` keyword (BNF §9) — \`//\` is already tokenized as \`INTDIV\`, no change needed.
- Parser: \`global attnamelist ['=' explist]\`, \`global function Name funcbody\`, \`global [attrib] '*'\` (BNF §9: three stat forms).
- Attributes: \`<const>\` and \`<close>\` on variable declarations (\`attrib ::= '<' Name '>'\` — BNF §3.3.7).
- File-scoped resolution: \`LuaFile.processDeclarations\` and \`LuaBlockExt.processDeclarations\` expose \`LuaGlobalVarDecl\` entries.
- \`LuaLanguageLevel.LUA55\` enum value and \`Target.kt\` / \`LuaProjectSettings\` integration.
- \`LuaLanguageLevelInspection\`: flag \`global\` declarations when \`LanguageLevel < LUA55\`.

### Out of Scope
- \`//\` comment syntax — Lua 5.5 does **not** introduce this; \`//\` remains integer division.
- Named vararg tables — deferred pending syntax confirmation from the reference manual.
- \`table.create\` stdlib integration — no parser changes, can be added to platform stubs separately.
- **Cross-file stub indexing**: \`LuaGlobalVarDecl\` is file-scoped only (no \`StubElementType\`). \`LuaGlobalDeclarationIndex\` is typed for \`LuaFuncDecl\` — giving globals a separate index is deferred to a future enhancement.
- Runtime semantic verification (compact arrays, incremental GC, external strings).

## Functional Requirements

| ID | Requirement | Priority | Description |
|----|-------------|----------|-------------|
| SYNTAX-09-01 | **Language Level Integration** | M | Add \`LuaLanguageLevel.LUA55(5, 5)\`, wire into \`Target.kt\` and \`LuaInterpreterFamily\`. |
| SYNTAX-09-02 | **\`global\` Keyword Parsing** | M | Parse \`global x\`, \`global x = 10\`, \`global function f() end\`, and \`global <const> *\` as PSI nodes without errors. |
| SYNTAX-09-03 | **Scope Resolution** | M | \`LuaFile.processDeclarations\` and \`LuaBlockExt.processDeclarations\` expose \`LuaGlobalVarDecl\` entries so \`LuaNameReference\` resolves them. |
| SYNTAX-09-04 | **Language Level Inspection** | M | \`LuaLanguageLevelInspection\` warns on \`global\` usage when \`LuaLanguageLevel < LUA55\`. |

## Test Cases

| # | Requirement | Given (input) | When (action) | Then (expected) |
|---|-------------|---------------|---------------|-----------------|
| 1 | SYNTAX-09-01 | \`LuaLanguageLevel\` enum | Unit test enumerates \`LuaLanguageLevel.entries\` | \`LUA55\` is present with \`major=5, minor=5\` |
| 2 | SYNTAX-09-02 | Lua file: \`global x = 10\` | \`TestLuaParsingExhaustive\` | AST contains \`LuaGlobalVarDecl\` with one \`LuaAttName\` node; no \`PsiErrorElement\` |
| 3 | SYNTAX-09-02 | Lua file: \`global function f() return 1 end\` | \`TestLuaParsingExhaustive\` | AST contains \`LuaGlobalFuncDecl\` wrapping a function body |
| 4 | SYNTAX-09-02 | Lua file: \`global <const> *\` | \`TestLuaParsingExhaustive\` | No parse error |
| 5 | SYNTAX-09-03 | Lua file: \`global x = 10; print(x)\` | \`LuaNameReference.multiResolve()\` on \`x\` in \`print(x)\` | Resolves to the \`LuaGlobalVarDecl\` |
| 6 | SYNTAX-09-04 | \`global x = 10\` with \`LUA54\` | \`LuaLanguageLevelInspection\` runs | Warning: "Global declarations are available in Lua 5.5+"; quick fix upgrades to LUA55 |

## Acceptance Criteria
- [ ] \`LuaLanguageLevel.LUA55\` exists and \`PlatformVersionRegistry\` maps 5.5 to it.
- [ ] \`lua.flex\` lexes \`global\` as the \`GLOBAL\` token.
- [ ] \`lua.bnf\` parses all three \`global\` statement forms.
- [ ] \`processDeclarations\` in \`LuaFile\` and \`LuaBlockExt\` exposes \`LuaGlobalVarDecl\`.
- [ ] \`LuaLanguageLevelInspection\` warns on 5.5 features in < 5.5 contexts.

## See Also
- Research: [research.md](research.md)
- Design: [design.md](design.md)
- Plan: [implementation-plan.md](implementation-plan.md)
