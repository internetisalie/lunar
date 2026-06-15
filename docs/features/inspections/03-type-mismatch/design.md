---
id: "INSP-03-DESIGN"
title: "Technical Design"
type: "design"
status: "planned"
parent_id: "INSP-03"
folders:
  - "[[features/inspections/03-type-mismatch/requirements|requirements]]"
---

# Technical Design: INSP-03 — Type Mismatch

## 1. Architecture Overview
### Target State
A local inspection that hooks into the type engine. It evaluates variable initializations, assignments, and return statements by comparing the *expected* type (from a LuaCATS `@type`/`@return` annotation or a known declaration) against the *actual* type (inferred from the RHS expression) using the file's `LuaTypes` snapshot and `LuaType.isAssignableTo`.

> **Type-engine note (binding):** the inference snapshot returns **`LuaGraphType`** values, while assignability is defined on the Layer-1 **`LuaType`**. The two are distinct — every inferred graph type MUST be converted with `snapshot.graphTypeToLuaType(...)` before calling `isAssignableTo`. See the type-engine lessons in `.agents/AGENTS.md`.

## 2. Core Components
### 2.1 `net.internetisalie.lunar.analysis.inspections.LuaTypeMismatchInspection`
- **Responsibility**: Provides the visitor to inspect `LuaLocalVarDecl`, `LuaAssignmentStatement`, and `LuaFinalStatement` (Lua's `return`) for type discrepancies.
- **Threading**: Read action (standard `LocalInspectionTool`).
- **Collaborators**: `LuaTypes` (per-file inference snapshot, obtained via `LuaTypesVisitor.getTypes(element)` / `LuaTypes.forFile(file)`), `LuaTypeManager` (for resolving an annotation's type *name* to a `LuaType`), `ProblemsHolder`.
- **Key API**:
  ```kotlin
  class LuaTypeMismatchInspection : LocalInspectionTool() {
      override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
          return object : LuaVisitor() {
              override fun visitLocalVarDecl(o: LuaLocalVarDecl) { ... }
              override fun visitAssignmentStatement(o: LuaAssignmentStatement) { ... }
              override fun visitFinalStatement(o: LuaFinalStatement) { ... }
          }
      }
  }
  ```

## 3. Algorithms
### 3.1 Assignability check (shared helper)
- **Input -> Output**: `(expected: LuaType, actualExpr: LuaExpr, snapshot: LuaTypes)` -> registers a problem or nothing.
- **Steps**:
  1. `val actual = snapshot.graphTypeToLuaType(snapshot.getValueType(actualExpr))`.
  2. If `expected` or `actual` is unknown/any/undefined, return (no warning) — avoid false positives on un-inferable expressions.
  3. If `!actual.isAssignableTo(expected)`, register a problem on `actualExpr`.

### 3.2 `visitLocalVarDecl` (INSP-03-01 / -02)
1. Read the attached annotation via `o.catsComment ?: return` (`getCatsComment()` on the `LuaCatsCommentOwner`).
2. `val typeName = catsComment.typeTagList.firstOrNull()?.argType?.text ?: return`.
3. Resolve the declared type: `val expected = LuaTypeManager.getInstance(o.project).resolveType(typeName, o) ?: return`.
4. For the RHS, take `o.exprList?.exprList` and pair it positionally with the declared names; if there is no initializer (`local x`), do nothing (see §6).
5. Apply §3.1 to each `(expected, rhsExpr)`. Message: `"Type mismatch: expected '<expected>', got '<actual>'"`.

> Union handling (INSP-03-02) is **not** special-cased here: `LuaType.isAssignableTo` already distributes unions (a member is assignable to `string|number` iff it is assignable to one variant). This relies on Wave-5 `TYPE-09` union-distribution being in place.

### 3.3 `visitAssignmentStatement` (INSP-03-01)
1. Vars: `o.varList.varList`; values: `o.exprList?.exprList ?: return`.
2. Zip the two lists up to the shorter length (`vars.zip(exprs)`).
3. For each `(varExpr, valueExpr)`: expected `= snapshot.graphTypeToLuaType(snapshot.getValueType(varExpr))`; then apply §3.1 with `valueExpr`.

### 3.4 `visitFinalStatement` (INSP-03-03, *Should*)
1. Enclosing function: `PsiTreeUtil.getParentOfType(o, LuaFuncDecl::class.java, LuaLocalFuncDecl::class.java) ?: return`. Only these two expose `getCatsComment()`; an anonymous **`LuaFuncDef`** has no cats comment of its own (its `@return` rides the enclosing `local`/assignment), so the Should-tier scope covers named/local functions only and skips anonymous-function returns.
2. Declared return type(s): from the function's `catsComment?.returnTagList` (each tag's `argType?.text`), resolved via `LuaTypeManager.resolveType`. For an AST-backed decl in the file being edited, `LuaFuncStub.luacatsReturnType` is null, so read the cats comment directly.
3. Returned expressions: `o.exprList?.exprList ?: return` (`LuaFinalStatement.getExprList()` yields a `LuaExprList`).
4. Zip declared-types to returned-exprs and apply §3.1. Message: `"Return type mismatch: expected '<expected>', got '<actual>'"`.

## 4. External Data & Parsing
*None.*

## 5. Data Flow
### Example 1: Local Assignment
1. Source: `---@type string` / `local x = 42`.
2. `visitLocalVarDecl` reads the `@type` tag string `"string"` and resolves it to `LuaPrimitiveType.STRING`.
3. Actual: `snapshot.getValueType(42)` → number graph type → `graphTypeToLuaType` → `LuaPrimitiveType.NUMBER`.
4. `NUMBER.isAssignableTo(STRING)` is false.
5. Problem on `42`: `"Type mismatch: expected 'string', got 'number'"`.

## 6. Edge Cases
- **Graph vs. Layer-1 type**: always convert via `graphTypeToLuaType` before `isAssignableTo` (see §1 note).
- **Unknown / any**: if either side resolves to an unknown/any/undefined type, do not warn.
- **Missing RHS**: a typed declaration with no initializer (`---@type string` / `local x`) produces no warning.
- **Arity mismatch**: when var/expr counts differ (multiple returns, `f()` expansion), only positionally-paired elements are checked; do not warn on the unpaired tail.

## 7. Integration Points
```xml
<extensions defaultExtensionNs="com.intellij">
    <localInspection language="Lua" groupName="Lua"
                     displayName="Type mismatch"
                     shortName="LuaTypeMismatch"
                     enabledByDefault="true" level="WARNING"
                     implementationClass="net.internetisalie.lunar.analysis.inspections.LuaTypeMismatchInspection"/>
</extensions>
```

## 8. Requirement Coverage
| Requirement | Priority | Implemented by (section) |
|-------------|----------|--------------------------|
| INSP-03-01 | M | §2.1, §3.1, §3.2, §3.3 |
| INSP-03-02 | M | §3.2 (union distribution via `isAssignableTo`) |
| INSP-03-03 | S | §3.4 |

## 9. Alternatives Considered
Using annotators instead of inspections. Rejected because type-mismatch checking is computationally heavier and fits the on-the-fly inspection batching model better.

## 10. Open Questions
- Confirm `LuaType.isAssignableTo` distributes unions as assumed in §3.2 once Wave-5 `TYPE-09` lands; if not, INSP-03-02 needs an explicit union walk.
