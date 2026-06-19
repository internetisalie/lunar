---
id: "SYNTAX-09-DESIGN"
title: "Technical Design"
type: "design"
status: "todo"
parent_id: "SYNTAX-09"
folders:
  - "[[features/syntax/09-lua-55/requirements|requirements]]"
---

# Technical Design: SYNTAX-09 — Lua 5.5 Support

## 1. Architecture Overview

### Prior Art in This Repo
- **Lexing**: Comments are handled in `src/main/kotlin/net/internetisalie/lunar/lang/lexer/lua.flex`. The rule `--+` transitions to `XSHORTCOMMENT`.
- **Parsing**: Variables are parsed in `src/main/kotlin/net/internetisalie/lunar/lang/psi/lua.bnf` (e.g., `localVarDecl ::= LOCAL attNameList ['=' exprList]`).
- **Inspections**: Backward compatibility warnings are handled in `src/main/kotlin/net/internetisalie/lunar/analysis/inspections/LuaLanguageLevelInspection.kt`.
- **Language Level Enum**: Defined in `src/main/kotlin/net/internetisalie/lunar/lang/LuaLanguageLevel.kt`.

### Target State
We will *extend* the existing parser and lexer to introduce the `global` keyword. We will *extend* `LuaLanguageLevelInspection` to cover the new AST nodes. **Note:** the `//` token is integer division (`INTDIV`), not a comment — Lua 5.5 does not change comment syntax. See [research.md](research.md).

## 2. Core Components

### 2.1 `LuaLanguageLevel`
- **Responsibility**: Define the LUA55 constant.
- **Key API**:
  Add `LUA55(5, 5)` to the `LuaLanguageLevel` enum. Update `Target.kt`'s `getImplicitLanguageLevel()` to map `version.label == "5.5"` to `LUA55`. Ensure `PlatformVersionRegistry.kt` uses `LUA55` for version `5.5` instead of `LUA54`.

### 2.2 Lexer (`lua.flex`)
- **Responsibility**: Tokenize the `global` keyword.
- **Key API**:
  Add `"global" { return GLOBAL; }` under Keywords.

### 2.3 Parser (`lua.bnf`)
- **Responsibility**: Define the grammar for global variable declarations.
- **Key API**:
  Add `GLOBAL = 'global'` to the keywords list.
  Add `globalVarDecl` to the `statement` choices.
  Define:
  ```bnf
  globalVarDecl ::= GLOBAL attNameList ['=' exprList] {
      implements = [
          "net.internetisalie.lunar.lang.psi.LuaCommentOwner"
      ]
      methods=[
          getComment getDocComment getCatsComment
      ]
  }
  ```
  This automatically generates the `LuaGlobalVarDecl` PSI class via Grammar-Kit.

### 2.4 `LuaLanguageLevelInspection`
- **Responsibility**: Warn when Lua 5.5 syntax is used in < 5.5 language levels.
- **Key API**:
  Extend `buildVisitor()` to handle `LuaGlobalVarDecl`:
  ```kotlin
  override fun visitGlobalVarDecl(o: LuaGlobalVarDecl) {
      super.visitGlobalVarDecl(o)
      if (level(o) < LuaLanguageLevel.LUA55) {
          holder.registerProblem(o.firstChild, "Global variable declarations are available in Lua 5.5+",
              UpgradeLanguageLevelFix(LuaLanguageLevel.LUA55))
      }
  }
  ```


### 2.5 `LuaFile.kt` and `LuaBlockExt.kt`
- **Responsibility**: Expose `global a = 5` variables to the symbol resolution engine.
- **Key API**:
  Modify `processDeclarations` in `LuaFile.kt` and `LuaBlockExt.kt`. When iterating over `children` (before the scope breaks), check:
  ```kotlin
  if (child is LuaGlobalVarDecl) {
      if (!processor.execute(child, state)) return false
  }
  ```
  Ensure this matches how `LuaAssignmentStatement` with global bindings is handled so that `LuaNameReference.multiResolve()` correctly finds the new `LuaGlobalVarDecl` PSI nodes.

## 3. Algorithms
No complex algorithms are required. Standard recursive descent parsing via Grammar-Kit, and simple AST node visitation for the inspection.

## 4. External Data & Parsing
N/A. This modifies internal IDE PSI structures.

## 5. Requirement Coverage

| Requirement | Priority | Implemented by (section) |
|-------------|----------|--------------------------|
| SYNTAX-09-01 | M | §2.1 |
| SYNTAX-09-02 | M | §2.2, §2.3, §2.5 |
| SYNTAX-09-04 | M | §2.4 |

## 6. Open Questions
- What is the exact syntax for named vararg tables? (Needs reference manual deep-dive.)
- Does `global` have block-scoping semantics or is it always file-scoped?
- What are the full token pipeline requirements (`LuaTokenTypes.java` entry, `LuaLexer.kt` mapping, `LuaStubElementTypes` factory)?
- Does `LuaGlobalVarDecl` require a `StubElementType` for cross-file resolution?
