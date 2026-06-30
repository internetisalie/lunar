---
id: "INSP-03-DESIGN"
title: "Technical Design"
type: "design"
parent_id: "INSP-03"
folders:
  - "[[features/inspections/03-type-mismatch/requirements|requirements]]"
---

# Technical Design: INSP-03 — Type Mismatch

## 1. Architecture Overview

### Current State — INSP-03 IS ALREADY IMPLEMENTED
Type-mismatch detection is centralised in the inference engine and exposed by **two existing,
registered inspections**. There is no missing engine logic and no third inspection to build.

**Engine (single source of truth).** `LuaTypeGraph.checkCompatibility(...)`
(`src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaTypeGraph.kt:275`) compares a value
type flowing into a use-constraint and appends an `ElementError`
(`LuaTypeGraph.kt:595`) for each mismatch. It is invoked during constraint solving at
`LuaTypeGraph.kt:100` and `LuaTypeGraph.kt:261`. Concretely it emits:
- **Scalar / structural mismatch** → `"<actual> is not assignable to <use>"` (`LuaTypeGraph.kt:293`, `:361`).
- **`nil` into non-nil** → `"nil value is not assignable to <use>"` (`LuaTypeGraph.kt:357`).
- **Union miss with closest-match reason** (TYPE-09) → `LuaTypeGraph.kt:324-330`.
- **Arity** → `"Too few/Too many arguments…"` (`LuaTypeGraph.kt:477-479`).
- **Missing `@field`** → `"Missing required field '<k>'"` (`LuaTypeGraph.kt:519`).

How the *declared* type becomes a use-constraint: `LuaTypesVisitor.visitLocalVarDecl`
(`LuaTypesVisitor.kt:306`) declares the var node, flows the RHS into it via
`graph.flowList(rhsNodes, varNodes)` (`:321`), then calls
`LuaTypeGraphBridge.injectTypeAnnotation(cats, …)` for the `@type`/`@class` comment (`:327`).
`injectTypeAnnotation` adds a **use node** carrying the declared graph type
(`LuaTypeGraphBridge.kt:86-87`), so the RHS value is checked against it. `@param` annotations are
injected the same way (`LuaTypeGraphBridge.kt:91+`), giving argument checking; `@return` is modelled
on function nodes, giving return checking. Union distribution in `checkCompatibility`
(`LuaTypeGraph.kt:290-332`) is the TYPE-09 dependency and is already in place.

**Presentation (two inspections partition the error list).** Both call
`LuaTypesSnapshot.forFile(holder.file).getErrors()` (the concrete `LuaTypes` impl is
`LuaTypesSnapshot`, `LuaTypes.kt:40`; `getErrors()` at `LuaTypes.kt:74`) and split on whether the
error element is return-related:
- `net.internetisalie.lunar.analysis.LuaTypeAssignabilityInspection` — reports **non-return**
  errors (assignments, arguments, table-literal field errors). Plugin.xml `plugin.xml:137-143`.
- `net.internetisalie.lunar.analysis.LuaReturnTypeMismatchInspection` — reports **return**
  errors (`LuaFinalStatement` / `LuaFuncDef` / `LuaFuncDecl` / `LuaLocalFuncDecl` elements).
  Plugin.xml `plugin.xml:145-151`.

### Prior Art in This Repo — extend-vs-replace verdict (per scenario)
| Scenario | Existing coverage (file:line) | Verdict |
|----------|-------------------------------|---------|
| **Assignment** (INSP-03-01) | engine `checkCompatibility` (`LuaTypeGraph.kt:293/361`) + `LuaTypeAssignabilityInspection` (`plugin.xml:137`) | **ALREADY SATISFIED.** No code. Add the missing scalar real-flow test only. |
| **Union** (INSP-03-02) | TYPE-09 distribution `LuaTypeGraph.kt:290-332`; closest-match proven by `LuaTypeAssignabilityInspectionTest.testUnionClosestMatchDiagnosticOnRealCode` | **ALREADY SATISFIED.** Add the union *pass* real-flow test (currently only the *fail* path is proven through the inspection). |
| **Return** (INSP-03-03) | `LuaReturnTypeMismatchInspection` (`plugin.xml:145`), proven by `LuaReturnTypeMismatchInspectionTest` | **ALREADY SATISFIED.** No code, no new test. |
| **Argument** (INSP-03-04) | `@param` injection `LuaTypeGraphBridge.kt:91+`; arity/field proven by `testArityTooFewReported` / `testMissingRequiredFieldReported` | **ALREADY SATISFIED.** No code, no new test. |

**This design therefore REPLACES the original "new `LuaTypeMismatchInspection`" plan with a
no-new-inspection reconciliation.** Adding a third inspection would duplicate the two above and
double-report; explicitly rejected.

### Target State
Unchanged production architecture. INSP-03 is closed by: (a) aligning docs to the shipped
two-inspection design and engine message format, and (b) adding two regression tests to
`LuaTypeAssignabilityInspectionTest` for the two currently-unproven cases. No `plugin.xml`
change, no new Kotlin class.

## 2. Core Components

### 2.1 `net.internetisalie.lunar.analysis.LuaTypeAssignabilityInspection` (EXISTING — unchanged)
- **Responsibility**: report all non-return engine errors for the file.
- **Threading**: standard `LocalInspectionTool.buildVisitor` read context.
- **Collaborators**: `LuaTypesSnapshot.forFile(file)` (`LuaTypes.kt:136`), `getErrors()`
  (`LuaTypes.kt:74`), `ProblemsHolder.registerProblem`.
- **Key API** (already present):
  ```kotlin
  class LuaTypeAssignabilityInspection : LocalInspectionTool() {
      override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor
  }
  ```

### 2.2 `net.internetisalie.lunar.analysis.LuaReturnTypeMismatchInspection` (EXISTING — unchanged)
- **Responsibility**: report return-related engine errors for the file. Same shape as §2.1, with
  the inverse `isReturnRelated` filter.

### 2.3 `net.internetisalie.lunar.analysis.LuaTypeAssignabilityInspectionTest` (EXISTING — extend)
- **Responsibility**: real-flow tests. Add two methods (design §3.3) for the unproven cases. No
  new test class.

> No new production class. The original §2.1 `LuaTypeMismatchInspection` is **withdrawn** — it
> would duplicate §2.1/§2.2.

## 3. Algorithms

### 3.1 Assignability check (ENGINE — already implemented, documented for grounding)
- **Input → Output**: `(valueType: LuaGraphType, useType: LuaGraphType, valueElement, useElement)` →
  appends `ElementError` or nothing. (`LuaTypeGraph.checkCompatibility`, `LuaTypeGraph.kt:275`.)
- **Steps** (verbatim from source):
  1. If `valueType == Any || useType == Any` → return (`:282`).
  2. If `valueType == Undefined || useType == Undefined` → return (`:283`). *(This is the `any`/`unknown`
     false-positive suppressor referenced by requirements.)*
  3. If `valueType == useType` → return (`:284`).
  4. Union on the value side: must be assignable to the use as a whole, else error (`:290-303`).
  5. Union on the use side: assignable iff any member matches; else closest-match error
     (`:305-332`).
  6. Primitive→table (metatable methods) allowed (`:334-337`); function/table/array structural
     checks (`:339-354`); `nil`→non-nil error (`:356-359`); otherwise scalar error (`:361`).
- **Note (graph vs Layer-1 type):** the inspections do **not** call `graphTypeToLuaType` /
  `isAssignableTo` — assignability is computed inside the engine on `LuaGraphType`. The
  `graphTypeToLuaType(): LuaType` (`LuaTypes.kt:76`) → `LuaType.isAssignableTo(...)` path exists but
  is used by other features (inlay hints), not by these inspections.

### 3.2 Error → inspection partition (ENGINE consumers — already implemented)
For each `ElementError e` in `getErrors()`: `isReturnRelated = e.element is LuaFinalStatement ||
e.element.parent is LuaFinalStatement || e.element is LuaFuncDef || e.element is LuaFuncDecl ||
e.element is LuaLocalFuncDecl`. `LuaReturnTypeMismatchInspection` registers when true;
`LuaTypeAssignabilityInspection` registers when false. Severity maps `ErrorSeverity`
(`LuaTypeGraph.kt:593`) → `ProblemHighlightType`.

### 3.3 New regression tests (the only delta)
Add to `LuaTypeAssignabilityInspectionTest` (mirrors its existing `descriptions(text)` helper:
`configureByText("test.lua", text)` then `doHighlighting().mapNotNull { it.description }`):
1. `testScalarTypeMismatchReported` (TC1): input `---@type string\nlocal x = 42`; assert some
   description contains `not assignable` AND mentions `number` and `string`.
2. `testUnionMemberMatchNotReported` (TC2): input `---@type string|number\nlocal x = 42`; assert
   **no** description contains `not assignable`.

## 4. External Data & Parsing
*None.* The feature consumes only PSI and the engine's in-memory error list; no external/CLI/file
input.

## 5. Data Flow

### Example 1: scalar mismatch (TC1)
`---@type string` / `local x = 42` → `visitLocalVarDecl` flows the `42` value node into the var
and injects a `string` use node (`LuaTypeGraphBridge.kt:86`) → solver runs `checkCompatibility(Number,
String, 42, …)` → scalar branch (`LuaTypeGraph.kt:361`) appends
`ElementError(42, "number is not assignable to string")` → `LuaTypeAssignabilityInspection`
sees a non-return error → `registerProblem` on `42`.

### Example 2: union pass (TC2)
`---@type string|number` / `local x = 42` → use node is `Union(String, Number)` →
`checkCompatibility` union-on-use branch finds `Number` compatible (`LuaTypeGraph.kt:308-322`) →
**no** error appended → no problem shown.

## 6. Edge Cases
- **`any`/`unknown` operands** → no warning (§3.1 steps 1–2).
- **Missing RHS** (`---@type string` / `local x`) → no value flows in, so `checkCompatibility`
  never runs for that var → no warning.
- **Arity/expansion mismatch** → engine emits arity diagnostics independently (`:477-479`); not
  conflated with element-wise assignability.
- **Anonymous-function returns** (`LuaFuncDef` with no own cats comment) → out of scope; the
  engine only models `@return` where the host decl carries it.

## 7. Integration Points
No new registration. The two existing entries are reused verbatim:
```xml
<!-- src/main/resources/META-INF/plugin.xml:137 -->
<localInspection language="Lua" displayName="Type assignability" groupName="Lua"
                 enabledByDefault="true" level="ERROR"
                 implementationClass="net.internetisalie.lunar.analysis.LuaTypeAssignabilityInspection"/>
<!-- src/main/resources/META-INF/plugin.xml:145 -->
<localInspection language="Lua" displayName="Return type mismatch" groupName="Lua"
                 enabledByDefault="true" level="ERROR"
                 implementationClass="net.internetisalie.lunar.analysis.LuaReturnTypeMismatchInspection"/>
```
*(Note: neither existing entry declares a `shortName`; the platform derives one from the class
name. This design keeps them as-is.)*

## 8. Requirement Coverage
| Requirement | Priority | Implemented by (section / file:line) |
|-------------|----------|--------------------------------------|
| INSP-03-01 | M | §3.1 + `LuaTypeAssignabilityInspection` (`plugin.xml:137`); new TC1 test §3.3 |
| INSP-03-02 | M | §3.1 union branch (`LuaTypeGraph.kt:290-332`, TYPE-09); new TC2 test §3.3 |
| INSP-03-03 | S | §3.2 + `LuaReturnTypeMismatchInspection` (`plugin.xml:145`); existing tests |
| INSP-03-04 | S | `@param` injection (`LuaTypeGraphBridge.kt:91+`) + arity (`LuaTypeGraph.kt:477`); existing tests |

## 9. Alternatives Considered
- **Build a new `LuaTypeMismatchInspection`** (the original design): rejected — duplicates
  `LuaTypeAssignabilityInspection` + `LuaReturnTypeMismatchInspection` and would double-report.
- **Add `shortName` attributes / split argument checking into its own inspection**: deferred as
  cosmetic; no requirement needs it.

## 10. Open Questions
_None — feature has cleared the planning bar. The former union-distribution question is resolved by TYPE-09 (DONE); see design §1 and risks-and-gaps.md._
