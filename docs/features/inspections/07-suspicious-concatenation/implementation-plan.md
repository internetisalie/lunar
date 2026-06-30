---
id: "INSP-07-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "INSP-07"
folders:
  - "[[features/inspections/07-suspicious-concatenation/design|design]]"
  - "[[features/inspections/07-suspicious-concatenation/requirements|requirements]]"
---

# Implementation Plan: INSP-07 — Suspicious Concatenation

## Phase 1: Inspection class [Must]
- **Tasks**:
  - Create `net.internetisalie.lunar.analysis.inspections.LuaSuspiciousConcatenationInspection`
    extending `LocalInspectionTool` (design §2.1). Override `getShortName`,
    `getGroupDisplayName`, `getDisplayName`, `isEnabledByDefault`, `getDefaultLevel`
    (WARNING) and `buildVisitor`.
  - In `buildVisitor`, obtain `LuaTypesSnapshot.forFile(holder.file)` once, then return a
    `LuaVisitor` overriding `visitBinOpExpr` that guards on `o.binOp.text == ".."` and checks
    `o.left` and `o.right` (design §2.1, §3.1).
  - Implement `isConcatenable(type: LuaGraphType): Boolean` as the exhaustive `when` from
    design §3.2 (string/number/any/undefined/generic → true; nil/boolean/table/function/array
    → false; union → `any { isConcatenable(it) }`).
  - Implement `checkOperand` to register a problem on the operand `LuaExpr` with the message
    from design §3.1 using `LuaGraphType.displayName()`.
- **Verification**: covered by Phase 3 (tests must pass before this phase is considered done).

## Phase 2: Registration [Must]
- **Tasks**: add the `<localInspection>` element from design §7 to
  `src/main/resources/META-INF/plugin.xml` in the existing inspection block (~lines 137–191),
  with `shortName="LuaSuspiciousConcatenation"`, `displayName="Suspicious concatenation"`,
  `groupName="Lua"`, `level="WARNING"`, `language="Lua"`, and the FQ `implementationClass`.
- **Verification**: plugin loads; the inspection appears under Settings → Editor → Inspections
  → Lua. Covered functionally by Phase 3 `doHighlighting()`.

## Phase 3: Tests [Must]
- **Tasks**: create `LuaSuspiciousConcatenationInspectionTest` (a `BasePlatformTestCase`,
  mirroring `src/test/kotlin/net/internetisalie/lunar/lang/insight/ShadowingVariableInspectionTest.kt`):
  `enableInspections(LuaSuspiciousConcatenationInspection())` in `setUp`, a helper that
  `configureByText("test.lua", ...)` + `doHighlighting()` filtered by
  `description?.startsWith("Suspicious concatenation")`. Implement all six cases from
  `requirements.md` (table/boolean operand warns; string/number/unknown/`string|nil` do not;
  `boolean|nil` warns).
- **Verification**: `./gradlew test --tests "*SuspiciousConcatenation*"` green; then full
  `./gradlew test` shows no regression vs. the Wave baseline; `./gradlew ktlintFormat`.

## Requirement → Phase Coverage
| Requirement | Priority | Phase(s) |
|-------------|----------|----------|
| INSP-07-01 | M | Phase 1 (`isConcatenable` concrete kinds), Phase 3 (TC1, TC2, TC6) |
| INSP-07-02 | M | Phase 1 (union rule), Phase 3 (TC5, TC6) |
| INSP-07-03 | M | Phase 1 (`Any`/`Undefined`/`Generic`/string/number), Phase 3 (TC3, TC4) |
| INSP-07-04 | S | Phase 1 (`registerProblem` on operand), Phase 3 (TC1, TC2) |
