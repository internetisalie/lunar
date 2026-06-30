---
id: "INSP-07-DESIGN"
title: "Technical Design"
type: "design"
parent_id: "INSP-07"
folders:
  - "[[features/inspections/07-suspicious-concatenation/requirements|requirements]]"
---

# Technical Design: INSP-07 — Suspicious Concatenation

## 1. Architecture Overview

### Target State
A `LocalInspectionTool` that visits every binary `..` expression and, for each of its two
operands, reads the inferred type from the file's `LuaTypes` snapshot and reports a warning
when the operand cannot be concatenated in Lua (only `string`/`number` coerce). It reuses the
exact type-engine idiom of INSP-03 Type Mismatch and `LuaTypeAssignabilityInspection`.

> **Type-engine note (binding):** the inference snapshot exposes inferred types as
> **`LuaGraphType`** values via `LuaTypes.getValueType(...)`. The concatenable predicate is
> defined **directly on `LuaGraphType`** (its sealed variants), so no `graphTypeToLuaType`
> conversion or `isAssignableTo` call is needed for the core check — concatenability is a
> fixed, per-kind rule, not assignability. A `@class` local infers as a
> `LuaGraphType.Union` (per `.agents/AGENTS.md`), which §3.2 handles explicitly.

### Grounded API surface (verified `file:line`)
- `LuaTypes.getValueType(element: PsiElement): LuaGraphType` —
  `src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaTypes.kt:21`.
- `LuaTypesSnapshot.forFile(file: PsiFile): LuaTypes` — same file, `:136`. Obtain the snapshot
  once in `buildVisitor` via `LuaTypesSnapshot.forFile(holder.file)`, exactly as
  `LuaTypeAssignabilityInspection` does
  (`src/main/kotlin/net/internetisalie/lunar/analysis/LuaTypeAssignabilityInspection.kt:18`).
- `LuaGraphType` sealed variants — `src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaGraphType.kt:12`:
  `Any` (`:15`), `Undefined` (`:18`), `Nil` (`:20`), `Boolean` (`:21`), `Number` (`:22`),
  `String` (`:23`), `Function` (`:25`), `Table` (`:37`), `Union(val types: ...)` (`:44`),
  `Array` (`:54`), `Generic(val name: ...)` (`:58`).
- `LuaGraphType.Union.types` — list of member `LuaGraphType`s (`LuaGraphType.kt:44`).
- `LuaGraphType.displayName(): String` — `LuaGraphType.kt:63` (Union → `" | "`-joined, `:72`);
  used only to format the warning message.
- Concat PSI node: `LuaBinOpExpr` with `getLeft(): LuaExpr`, `getRight(): LuaExpr?`,
  `getBinOp(): LuaBinOp` — `src/main/gen/net/internetisalie/lunar/lang/psi/LuaBinOpExpr.java:8`.
  All `..`/`+`/`*`/… binary expressions share elementType `binOpExpr`
  (`src/main/kotlin/net/internetisalie/lunar/lang/psi/lua.bnf:235`), so the visitor override
  is `visitBinOpExpr`.
- Operator discrimination: `LuaBinOp.text == ".."` — the same idiom used by
  `LuaLanguageLevelAnnotator` (`src/main/kotlin/net/internetisalie/lunar/lang/syntax/LuaLanguageLevelAnnotator.kt:68-69`,
  `val operator = element.text`). The concat operator literal is `'..'`
  (`lua.bnf:33`, `CONCAT = '..'`).
- Base visitor: `LuaVisitor` (`src/main/gen/net/internetisalie/lunar/lang/psi/LuaVisitor.java`,
  `visitBinOpExpr` defined at `:34`).
- **Use-site operand binding (why the operand carries the variable's type):** an operand such
  as the `t`/`maybe`/`flag` reference inside `"x" .. t` is a `LuaNameRef`. The engine's
  `LuaTypesVisitor.visitNameRef` (`src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaTypesVisitor.kt:555-558`)
  binds **every** name reference — declaration *and* use site — to the same variable node via
  `scope.lookup(o.text)` and records it in `elementNodes[o]`. Because the *same* node backs the
  declaration and all later uses, `types.getValueType(operand)` on a use-site ref returns the
  variable's inferred/declared type. (`getValueType` reading a name ref's bound node is exercised
  by `TestLuaTypeEnginePhase1:459/474/527` and `CrossFileInferenceTest:83-84`.) This is what makes
  the inspection work without any manual reference resolution.
- **Declared `@type` is unioned with the literal write, not replaced.** A
  `---@type string|nil; local maybe = nil` declaration yields a `LuaGraphType.Union` for the
  variable node — the annotation is merged with the inferred initializer type, then canonicalized
  (deduped/sorted) by `LuaTypeAlgebra.canonicalize` (`LuaTypeAlgebra.kt:45`, members sorted by
  `displayName()`). Confirmed by `LuaTypeAssignabilityTest.testArrayTypeInlayHintPreservesInferredType:50`
  (`---@type string[]` local infers `"{ ... } | string[]"`). Consequence for messages: union member
  order in the label is alphabetical by `displayName()` (e.g. `boolean | nil`, `nil | string`).

## 2. Core Components

### 2.1 `net.internetisalie.lunar.analysis.inspections.LuaSuspiciousConcatenationInspection`
- **Responsibility**: visitor over `LuaBinOpExpr` nodes; for each `..` expression, check both
  operands and register a problem per non-concatenable operand.
- **Threading**: standard `LocalInspectionTool` read action (no I/O, no EDT blocking).
- **Base class**: `LocalInspectionTool`.
- **Collaborators**: `LuaTypes` snapshot (`LuaTypesSnapshot.forFile`), `ProblemsHolder`.
- **Key API**:
  ```kotlin
  package net.internetisalie.lunar.analysis.inspections

  import com.intellij.codeHighlighting.HighlightDisplayLevel
  import com.intellij.codeInspection.LocalInspectionTool
  import com.intellij.codeInspection.ProblemHighlightType
  import com.intellij.codeInspection.ProblemsHolder
  import com.intellij.psi.PsiElementVisitor
  import net.internetisalie.lunar.lang.psi.LuaBinOpExpr
  import net.internetisalie.lunar.lang.psi.LuaExpr
  import net.internetisalie.lunar.lang.psi.LuaVisitor
  import net.internetisalie.lunar.lang.psi.types.LuaGraphType
  import net.internetisalie.lunar.lang.psi.types.LuaTypes
  import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot

  class LuaSuspiciousConcatenationInspection : LocalInspectionTool() {
      override fun getShortName(): String = "LuaSuspiciousConcatenation"
      override fun getGroupDisplayName(): String = "Lua"
      override fun getDisplayName(): String = "Suspicious concatenation"
      override fun isEnabledByDefault(): Boolean = true
      override fun getDefaultLevel(): HighlightDisplayLevel = HighlightDisplayLevel.WARNING

      override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
          val types = LuaTypesSnapshot.forFile(holder.file)
          return object : LuaVisitor() {
              override fun visitBinOpExpr(o: LuaBinOpExpr) {
                  if (o.binOp.text != "..") return
                  checkOperand(o.left, types, holder)
                  o.right?.let { checkOperand(it, types, holder) }
              }
          }
      }

      private fun checkOperand(operand: LuaExpr, types: LuaTypes, holder: ProblemsHolder) { /* §3.1 */ }

      private fun isConcatenable(type: LuaGraphType): Boolean { /* §3.2 */ }
  }
  ```

## 3. Algorithms

### 3.1 `checkOperand` — per-operand check (INSP-07-01, -04)
- **Input → Output**: `(operand: LuaExpr, types: LuaTypes, holder: ProblemsHolder)` →
  registers at most one problem on `operand`.
- **Steps**:
  1. `val graphType = types.getValueType(operand)`.
  2. If `isConcatenable(graphType)` (§3.2) → return (no warning).
  3. Otherwise register a problem on `operand` (the operand range, satisfying INSP-07-04):
     ```kotlin
     holder.registerProblem(
         operand,
         "Suspicious concatenation: operand of type '${graphType.displayName()}' cannot be concatenated",
         ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
     )
     ```
     The type label uses `LuaGraphType.displayName()`
     (`src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaGraphType.kt:63`); for a
     `Union` this joins the member names with `" | "` (`LuaGraphType.kt:72`), yielding e.g.
     `boolean | nil`. The predicate itself stays on the graph type — `displayName()` is used
     only to format the message.

### 3.2 `isConcatenable` — the concatenability predicate (INSP-07-02, -03)
Defined over the `LuaGraphType` sealed variants (`LuaGraphType.kt:12-58`). Returns `true`
(do **not** warn) for any kind that could legally be a `string`/`number`, or that is
un-inferable, to keep false positives off:

```kotlin
private fun isConcatenable(type: LuaGraphType): Boolean = when (type) {
    LuaGraphType.String, LuaGraphType.Number -> true        // Lua coerces these
    LuaGraphType.Any, LuaGraphType.Undefined -> true        // any / unknown — never warn (INSP-07-03)
    is LuaGraphType.Generic -> true                          // unresolved type parameter — never warn
    LuaGraphType.Nil, LuaGraphType.Boolean,
    is LuaGraphType.Table, is LuaGraphType.Function,
    is LuaGraphType.Array -> false                           // concrete non-concatenable (INSP-07-01)
    is LuaGraphType.Union -> type.types.any { isConcatenable(it) } // concatenable if ANY member is (INSP-07-02)
}
```

- **Union rule (INSP-07-02)**: a union is concatenable iff **at least one** member is
  concatenable; equivalently, the inspection warns only when **every** member is
  non-concatenable. This is the conservative direction and is the reason a `string|nil`
  operand (Test Case 5) is **not** flagged while a `boolean|nil` operand (Test Case 6) **is**.
- **`@class`-local caveat**: per `.agents/AGENTS.md`, a `---@class` local infers as a
  `LuaGraphType.Union` of `Table(className=null)` and `Table(className="X")`. Both members are
  `is LuaGraphType.Table` → non-concatenable → the union is non-concatenable, which is correct
  (concatenating a class instance is a runtime error). No special-casing needed; the recursive
  union rule covers it.
- The `when` is **exhaustive** over the sealed hierarchy (no `else`), so adding a future
  `LuaGraphType` variant is a compile error that forces an explicit concatenability decision.

### 3.3 Chained concatenation (`a .. b .. c`)
The parser nests right-associatively (`lua.bnf:235`, `rightAssociative=true`): `a .. (b .. c)`
is an outer `LuaBinOpExpr(left=a, right=LuaBinOpExpr(left=b, right=c))`. `visitBinOpExpr` fires
for **each** `LuaBinOpExpr`. The inner expression itself is an operand of the outer one, but
its inferred type is `String` (the result of `..`), so §3.2 returns `true` for it — no double
or spurious warning. Each leaf operand is checked exactly once by the `LuaBinOpExpr` it is a
direct child of.

## 4. External Data & Parsing
*None.* No files, network, or serialized formats are read; all input is in-memory PSI plus the
cached type snapshot.

## 5. Data Flow

### Example: `local s = "hello " .. t` where `t = {}`
1. `visitBinOpExpr` fires on the `..` expression; `binOp.text == ".."`.
2. Left operand `"hello "`: `getValueType` → `LuaGraphType.String` → `isConcatenable` true → skip.
3. Right operand `t`: `getValueType` → `LuaGraphType.Table(className=null)` → `isConcatenable` false.
4. `registerProblem(t, "Suspicious concatenation: operand of type '{ ... }' cannot be concatenated", GENERIC_ERROR_OR_WARNING)`
   — the type label is `Table(className=null).displayName()` = `{ ... }` (`LuaGraphType.kt:70`),
   **not** the word `table`; a class-typed table would render its `className`.

## 6. Edge Cases
- **Predicate on the graph type, not Layer-1**: concatenability is a fixed per-kind rule, so
  the check stays on `LuaGraphType`. The message label is the graph type's own
  `displayName()` (`LuaGraphType.kt:63`) — **no** `graphTypeToLuaType` conversion is used
  anywhere in this inspection.
- **Un-inferable operand**: `Any`/`Undefined`/`Generic` → never warn (INSP-07-03, Test Case 4).
- **Missing right operand**: `LuaBinOpExpr.getRight()` is `@Nullable`; guard with `o.right?.let`
  to avoid an NPE on a syntactically incomplete `a ..`.
- **Result of an inner `..`**: infers as `String`, so chained concatenations never produce a
  spurious warning on the nested expression (§3.3).
- **Union containing `any`/`unknown`**: `Any`/`Undefined` is a concatenable member, so the
  whole union is treated as concatenable — no warning (keeps false positives off).

## 7. Integration Points
Registered alongside the other inspections in `src/main/resources/META-INF/plugin.xml`
(`<extensions defaultExtensionNs="com.intellij">`, near the existing `<localInspection>` block
at lines ~137–191), matching the inline-attribute house style (no resource bundle; the
`displayName`/`groupName` attributes mirror the literal `getDisplayName()`/`getGroupDisplayName()`
overrides on the class):

```xml
<localInspection
        language="Lua"
        shortName="LuaSuspiciousConcatenation"
        displayName="Suspicious concatenation"
        groupName="Lua"
        enabledByDefault="true"
        level="WARNING"
        implementationClass="net.internetisalie.lunar.analysis.inspections.LuaSuspiciousConcatenationInspection"/>
```

## 8. Requirement Coverage
| Requirement | Priority | Implemented by (section) |
|-------------|----------|--------------------------|
| INSP-07-01 | M | §2.1, §3.1, §3.2 (concrete non-concatenable kinds) |
| INSP-07-02 | M | §3.2 (recursive union rule) |
| INSP-07-03 | M | §3.2 (`Any`/`Undefined`/`Generic`/`String`/`Number` → concatenable) |
| INSP-07-04 | S | §3.1 (`registerProblem` on the operand `LuaExpr`) |

## 9. Alternatives Considered
- **Predicate on Layer-1 `LuaType` via `isAssignableTo(STRING|NUMBER)`**: rejected — there is no
  pre-built `string|number` union singleton to test against, the conversion is unnecessary work
  per node, and `isAssignableTo` semantics (assignability) differ subtly from "is a coercible
  concat operand". The fixed per-kind rule on `LuaGraphType` is exact and exhaustive.
- **Annotator instead of inspection**: rejected — the feature is specified as a
  `<localInspection>` and benefits from inspection batching/severity configuration like its
  INSP siblings.

## 10. Open Questions
*None.*
