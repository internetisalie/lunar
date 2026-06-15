---
id: "INSP-09-DESIGN"
title: "Technical Design"
type: "design"
status: "planned"
parent_id: "INSP-09"
folders:
  - "[[features/inspections/09-language-level/requirements|requirements]]"
---

# Technical Design: INSP-09 — Language Level Compliance

## 1. Architecture Overview
### Target State
A local inspection that queries the project's configured Lua language level and flags syntax that the level does not support (attributes, bitwise operators, `goto`).

> **This REPLACES an existing annotator.** `net.internetisalie.lunar.lang.syntax.LuaLanguageLevelAnnotator` (registered in `plugin.xml`) already flags these exact constructs — goto/label (5.2), bitwise `& | ~ << >>` and integer division `//` (5.3), attributes (5.4) — at ERROR severity, **with quick fixes** (`UpgradeLanguageLevelFix`, `RemoveGotoFix`, `RemoveLabelFix`, …). Shipping this inspection without removing the annotator would **double-report** every affected construct. Migrating to an inspection is the motivation (user enable/disable + severity control); see §9. The quick fixes MUST be carried over (a `LocalQuickFix` per problem), not dropped — that is a hard requirement of the migration, not optional polish.

## 2. Core Components
### 2.1 `net.internetisalie.lunar.analysis.inspections.LuaLanguageLevelInspection`
- **Responsibility**: Visits AST nodes that have version constraints and warns when the project level is too low.
- **Threading**: Read action.
- **Collaborators**: `LuaProjectSettings`, `LuaLanguageLevel`.
- **Key API**:
  ```kotlin
  class LuaLanguageLevelInspection : LocalInspectionTool() {
      override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
          return object : LuaVisitor() {
              override fun visitAttribName(o: LuaAttribName) { ... }       // <const> / <close>  (5.4)
              override fun visitBinOpExpr(o: LuaBinOpExpr) { ... }          // & | ~ << >>        (5.3)
              override fun visitUnOpExpr(o: LuaUnOpExpr) { ... }            // unary ~            (5.3)
              override fun visitGotoStatement(o: LuaGotoStatement) { ... }  // goto               (5.2)
          }
      }
  }
  ```

> **PSI note:** there is no dedicated bitwise-op node. Bitwise operators are ordinary binary
> expressions (`LuaBinOpExpr`, operator via `getBinOp()`) plus unary bitwise-not (`LuaUnOpExpr`).
> The visitor inspects the operator's text rather than a node type.

## 3. Algorithms
### 3.1 Reading the level
```kotlin
val level: LuaLanguageLevel = LuaProjectSettings.getInstance(element.project).state.languageLevel
```
This is the canonical accessor used by `LuaGlobalCreationInspection` / `LuaUndeclaredVariableInspection`. `LuaLanguageLevel` is `Comparable` (declared ascending `LUA50, LUA51, LUA52, LUA53, LUA54`), so the existing annotator's idiom `level < LuaLanguageLevel.LUA53` is the preferred gate; reuse it for parity. (It also exposes `major`/`minor` ints if an explicit check is wanted.)

### 3.2 Node evaluation
Mirror the construct→version mapping already in `LuaLanguageLevelAnnotator`, and attach the same quick fixes:
- **visitAttribName** (attributes — 5.4): if `level < LuaLanguageLevel.LUA54`, register `"Attributes are not supported in Lua ${level.version}"`.
- **visitBinOpExpr** (bitwise & integer-division — 5.3): if `level < LuaLanguageLevel.LUA53` and the operator is bitwise/`//`, register `"Bitwise operators are not supported in Lua ${level.version}"`. Match the operator by **token element type** — the dedicated tokens `AMP &`, `PIPE |`, `NEG ~`, `BSL <<`, `BSR >>` (`LuaTokenTypes`) — rather than raw text, to avoid ambiguity with arithmetic operators.
- **visitUnOpExpr** (bitwise-not — 5.3): if `level < LuaLanguageLevel.LUA53` and the operator token is `NEG ~`, register the same message. (`~` is overloaded: binary xor vs. unary not; `^` is power, not xor — handle as the existing annotator does.)
- **visitGotoStatement** / label (goto — 5.2): if `level < LuaLanguageLevel.LUA52`, register `"Goto statements are not supported in Lua ${level.version}"`.

Each `registerProblem` must include the corresponding `LocalQuickFix` ported from the annotator (`UpgradeLanguageLevelFix`, `RemoveGotoFix`, `RemoveLabelFix`).

## 4. External Data & Parsing
*None.*

## 5. Data Flow
### Example 1: Unsupported Bitwise
1. Source `1 & 2` in a Lua 5.1 project.
2. `visitBinOpExpr` sees operator text `&`.
3. `LuaProjectSettings...state.languageLevel` → `LUA51` (`minor == 1`).
4. `1 < 3`, so register an `ERROR`-level problem on `&`: `"Bitwise operators are not supported in Lua 5.1"`.

## 6. Edge Cases
- **Mixed operators**: `visitBinOpExpr` must filter to the bitwise/`//` operator tokens; arithmetic/relational binary expressions are never flagged.
- **Unary `~`**: handled by `visitUnOpExpr` separately from binary `~` (xor); `^` is power, not bitwise.
- **Level defaulting**: `state.languageLevel` defaults to `LUA54`, so no spurious warnings on an unconfigured project.
- **No double-reporting**: the existing `LuaLanguageLevelAnnotator` must be removed in the same change (see §1, §7).

## 7. Integration Points
Register the inspection **and remove the old annotator** in the same `plugin.xml` change:
```xml
<extensions defaultExtensionNs="com.intellij">
    <!-- ADD: -->
    <localInspection language="Lua" groupName="Lua"
                     displayName="Language level compliance"
                     shortName="LuaLanguageLevel"
                     enabledByDefault="true" level="ERROR"
                     implementationClass="net.internetisalie.lunar.analysis.inspections.LuaLanguageLevelInspection"/>
    <!-- REMOVE: the existing <annotator ... LuaLanguageLevelAnnotator/> registration -->
</extensions>
```
Delete `LuaLanguageLevelAnnotator.kt` (or reduce it to the shared quick-fix classes that the inspection reuses).

## 8. Requirement Coverage
| Requirement | Priority | Implemented by (section) |
|-------------|----------|--------------------------|
| INSP-09-01 | M | §2.1, §3.2 |
| INSP-09-02 | M | §2.1, §3.2 |
| INSP-09-03 | M | §2.1, §3.2 |

## 9. Alternatives Considered
Keep using annotators. Rejected because the language level is project-level configuration that fits the inspection model and gives users an enable/disable toggle and severity control.

## 10. Open Questions
_None — both prior questions are resolved: the existing `LuaLanguageLevelAnnotator` is the component being replaced (§1, §7), and operator matching uses the dedicated `LuaTokenTypes` tokens `AMP/PIPE/NEG/BSL/BSR` (§3.2)._
