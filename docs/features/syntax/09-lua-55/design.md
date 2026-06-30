---
id: "SYNTAX-09-DESIGN"
title: "Technical Design"
type: "design"
parent_id: "SYNTAX-09"
folders:
  - "[[features/syntax/09-lua-55/requirements|requirements]]"
---

# Technical Design: SYNTAX-09 — Lua 5.5 Support

## 1. Architecture Overview

### Prior Art in This Repo
- **Language level**: `lang/LuaLanguageLevel.kt:24` — `LUA54(5, 4)`.
- **Version registry**: `platform/target/PlatformVersionRegistry.kt:21` — has `VersionEntry("5.5", "lua-5.5", luacheckStd = "lua54")`.
- **Target mapping**: `platform/target/Target.kt:43` — maps `"5.5"` → `LUA54` (placeholder).
- **Token types**: `src/main/java/net/internetisalie/lunar/lang/lexer/LuaTokenTypes.java` — `FUNCTION`, `LOCAL`, `GOTO`.
- **Lexer**: `lang/lexer/lua.flex:72-73` — `"function"` and `"goto"` keywords; `"local"` at line 104.
- **Lexer mapping**: `lang/lexer/LuaLexer.kt:49-88` — maps `LuaTokenTypes.KEYWORD → LuaElementTypes.KEYWORD`.
- **Parser**: `lang/psi/lua.bnf:182-192` — `localVarDecl ::= LOCAL attNameList ['=' exprList]`.
- **Scope (file)**: `lang/psi/LuaFile.kt:41-85` — `processDeclarations` iterates children for `LuaFuncDecl`, `LuaAssignmentStatement`.
- **Scope (block)**: `lang/psi/LuaBlockExt.kt:22-66` — `processDeclarations` with `when(statement)` for `LuaLocalVarDecl`, `LuaLocalFuncDecl`, `LuaAssignmentStatement`.
- **Inspection**: `analysis/inspections/LuaLanguageLevelInspection.kt:30` — uses `register(holder, element, message, fix)` helper at line 104.
- **Quick fix**: `lang/syntax/LuaLanguageLevelQuickFixes.kt:17` — `UpgradeLanguageLevelFix`.
- **Migration**: `settings/LuaProjectSettings.kt:69-81` — `migrateFromLegacySettings()` `when` over `LuaLanguageLevel`.
- **Interpreter**: `platform/LuaInterpreter.kt:109-116` — `LuaInterpreterFamily.FAMILIES["Lua"]` leveler lambda maps version strings.

### Target State
We **extend** the existing token pipeline, parser, scope resolution, and inspection. No stub infrastructure is added — `LuaGlobalVarDecl` has no stub (cross-file resolution relies on the existing `_ENV` mechanism; within-file block-level scoping is handled by `LuaBlockExt.processDeclarations`). `LuaGlobalDeclarationIndex` is typed `StringStubIndexExtension<LuaFuncDecl>` and cannot hold `LuaGlobalVarDecl` stubs without a type change; creating a separate index is deferred.

## 2. Core Components

### 2.1 `LuaLanguageLevel` — `lang/LuaLanguageLevel.kt`
- **Responsibility**: Define the LUA55 constant.
- **Key API**:
  Add after line 24 (`LUA54(5, 4);`):
  ```kotlin
  LUA55(5, 5);
  ```
- **Callers to update**:
  - `Target.kt:43`: change `version.label == "5.5"` from `LuaLanguageLevel.LUA54` to `LuaLanguageLevel.LUA55`.
  - `LuaProjectSettings.kt:69-81`: `migrateFromLegacySettings()` `when` — add `LuaLanguageLevel.LUA55 -> "5.5"`.
  - `LuaInterpreter.kt:109-116`: `LuaInterpreterFamily.FAMILIES["Lua"]` leveler lambda — add `version.startsWith("5.5") -> LuaLanguageLevel.LUA55` (before the implicit catch-all else clause).

### 2.2 Token Pipeline — `LuaTokenTypes.java` + `lua.flex` + `LuaLexer.kt`
- **Responsibility**: Tokenize the `global` keyword.

  **2.2a** `LuaTokenTypes.java` — add alongside `GOTO`:
  ```java
  IElementType GLOBAL = new LuaElementType("global");
  ```

  **2.2b** `lua.flex` — add under Keywords between `"function"` (line 72) and `"goto"` (line 73):
  ```
  "global"       { return GLOBAL; }
  ```

  **2.2c** `LuaLexer.kt` — add mapping between `FUNCTION` (line 49) and `GOTO` (line 52):
  ```kotlin
  LuaTokenTypes.GLOBAL to LuaElementTypes.GLOBAL,
  ```

### 2.3 Parser — `lua.bnf`
- **Responsibility**: Define grammar for all three `global` statement forms.

  Add `GLOBAL = 'global'` to the keywords list.

  **2.3a** Variable declaration — add to `statement` alternatives:
  ```bnf
  | globalVarDecl
  ```
  Define (mirrors `localVarDecl` at line 182):
  ```bnf
  globalVarDecl ::= GLOBAL attNameList ['=' exprList] {
      implements = ["net.internetisalie.lunar.lang.psi.LuaCommentOwner"]
      methods=[getComment getDocComment getCatsComment]
      extends=statement
  }
  ```
  No `elementTypeFactory` — no stub. Generates `LuaGlobalVarDecl` as a plain PSI node.

  **2.3b** Global mode — add to `statement` alternatives:
  ```bnf
  | globalModeDecl
  ```
  Define:
  ```bnf
  globalModeDecl ::= GLOBAL [attrib] '*' {
      implements = ["net.internetisalie.lunar.lang.psi.LuaCommentOwner"]
      methods=[getComment getDocComment getCatsComment]
      extends=statement
  }
  ```
  Generates `LuaGlobalModeDecl`.

  **2.3c** Global function — add to `statement` alternatives:
  ```bnf
  | globalFuncDecl
  ```
  Define (mirrors `localFuncDecl`):
  ```bnf
  globalFuncDecl ::= GLOBAL FUNCTION nameRef funcBody {
      implements = [
          "net.internetisalie.lunar.lang.psi.LuaCommentOwner"
          "net.internetisalie.lunar.lang.psi.LuaBlockParent"
      ]
      methods=[
          getComment getDocComment getCatsComment
          getBlockList
      ]
      extends=statement
  }
  ```
  No stub. Generates `LuaGlobalFuncDecl`. Per Lua 5.5 manual §3.4.11, `global function f () body end` is syntactic sugar for `global f; global f = function () body end`.

### 2.4 Scope Resolution
- **Responsibility**: Expose `LuaGlobalVarDecl` and `LuaGlobalFuncDecl` entries to `LuaNameReference`.

  **2.4a** `LuaFile.kt:41-85` — consolidate the three sequential `for (child in children)` loops into one:
  ```kotlin
  for (child in children) {
      if (lastParent != null && child.textOffset >= lastParent.textOffset) break
      when (child) {
          is LuaFuncDecl -> if (!processor.execute(child, state)) return false
          is LuaGlobalFuncDecl -> if (!processor.execute(child, state)) return false
          is LuaGlobalVarDecl -> if (!processor.execute(child, state)) return false
          is LuaAssignmentStatement -> if (!processor.execute(child, state)) return false
      }
  }
  return true
  ```
  The existing three separate loops (lines 50-60, 63-73) are replaced by this single-pass `when`. `LuaGlobalFuncDecl` is included despite the Lua 5.5 manual §3.4.11 describing it as syntactic sugar — the parser generates a distinct `LuaGlobalFuncDecl` node (does not desugar), so its name must be resolvable for `f()` to resolve after `global function f() end`. Block processing (`getBlockList()` → `LuaBlock.processDeclarations`) remains at the end of the method (lines 76-85), unchanged.

  **2.4b** `LuaBlockExt.kt:35-56` — add `when` branches:
  ```kotlin
  is LuaGlobalVarDecl -> {
      if (!processor.execute(statement, state)) return false
  }
  is LuaGlobalFuncDecl -> {
      if (!processor.execute(statement, state)) return false
  }
  ```

### 2.5 `LuaLanguageLevelInspection`
- **Responsibility**: Warn when `global` is used below Lua 5.5.
- **Threading**: EDT (inspection runs on UI thread via `LocalInspectionTool`).

  The existing inspection at `analysis/inspections/LuaLanguageLevelInspection.kt:104` uses a private `register(holder, element, message, vararg fixes)` helper. Match that convention:

  ```kotlin
  override fun visitGlobalVarDecl(o: LuaGlobalVarDecl) {
      super.visitGlobalVarDecl(o)
      if (level(o) < LuaLanguageLevel.LUA55) {
          register(
              holder, o.firstChild,
              "Global variable declarations are only available in Lua 5.5+",
              UpgradeLanguageLevelFix(LuaLanguageLevel.LUA55)
          )
      }
  }

  override fun visitGlobalFuncDecl(o: LuaGlobalFuncDecl) {
      super.visitGlobalFuncDecl(o)
      if (level(o) < LuaLanguageLevel.LUA55) {
          register(
              holder, o.firstChild,
              "Global function declarations are only available in Lua 5.5+",
              UpgradeLanguageLevelFix(LuaLanguageLevel.LUA55)
          )
      }
  }

  override fun visitGlobalModeDecl(o: LuaGlobalModeDecl) {
      super.visitGlobalModeDecl(o)
      if (level(o) < LuaLanguageLevel.LUA55) {
          register(
              holder, o.firstChild,
              "Global mode declarations are only available in Lua 5.5+",
              UpgradeLanguageLevelFix(LuaLanguageLevel.LUA55)
          )
      }
  }
  ```

  The generated `LuaVisitor` (at `src/main/gen/.../LuaVisitor.java`) auto-generates `visitGlobalVarDecl`, `visitGlobalFuncDecl`, and `visitGlobalModeDecl` methods after `./gradlew generateLuaParser`.

  **No `plugin.xml` registration needed** — the inspection is already registered and `LuaVisitor` dispatch is automatic.

### 2.6 `LuaProjectSettings` — Migration
- **Responsibility**: Handle `LUA55` in legacy settings.
- **Key API**: Add to `migrateFromLegacySettings()` `when` block (line ~70):
  ```kotlin
  LuaLanguageLevel.LUA55 -> "5.5"
  ```

## 3. Algorithms
No complex algorithms. Standard token pipeline addition (matching `GOTO`/`LOCAL` patterns), Grammar-Kit BNF rule generation (mirroring `localVarDecl`/`localFuncDecl`), single-pass `processDeclarations` with `when` dispatch, and `LuaVisitor` visitor-pattern for the inspection.

## 4. External Data & Parsing
N/A — this modifies internal IDE PSI structures. No external CLI/text/file input.

## 5. Requirement Coverage

| Requirement | Priority | Implemented by (section) |
|-------------|----------|--------------------------|
| SYNTAX-09-01 | M | §2.1, §2.6 |
| SYNTAX-09-02 | M | §2.2, §2.3 |
| SYNTAX-09-03 | M | §2.4 |
| SYNTAX-09-04 | M | §2.5 |

## 6. Open Questions
_None — all decisions are grounded in the Lua 5.5 Reference Manual BNF (§9) and Lunar's existing infrastructure patterns._
