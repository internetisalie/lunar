---
id: INSP-01-PLAN
title: "Implementation Plan"
type: plan
parent_id: INSP-01
status: "planned"
priority: "medium"
folders:
  - "[[features/inspections/01-undeclared-variable/requirements|requirements]]"
---

# Implementation Plan: INSP-01 Undeclared Variable

Implements the design in `design.md`. Each phase maps to requirement IDs and is verified by
the test cases in `requirements.md`.

## Phase 1: Inspection Skeleton & Read Classification [Must] — INSP-01-01,02,04,05,06

Goal: flag unresolved reads; never flag declarations or simple write targets.

- [ ] Create package `net.internetisalie.lunar.analysis.inspections`.
- [ ] Add `LuaUndeclaredVariableInspection : LocalInspectionTool` with `getShortName` =
      `"LuaUndeclaredVariable"`, group `"Lua"`, default level `WARNING`, `buildVisitor`
      filtering on `element is LuaNameRef` (§2.1).
- [ ] Implement `isReadUse(ref)` per §3.1 (declaration-site + write-target exclusion).
- [ ] Implement `inspectNameRef` steps 1–2, 7–8, 10 (resolve-null → `registerProblem`
      `GENERIC_ERROR_OR_WARNING`, message `Undeclared variable '<name>'`).
- [ ] Register `<localInspection …>` in `META-INF/plugin.xml` exactly as in design §7.
- [ ] Unit tests: TC-01, TC-02, TC-03, TC-05, TC-06, TC-11.

## Phase 2: Standard-Globals Floor [Must] — INSP-01-03

Goal: built-ins never flagged, independent of platform-library configuration.

- [ ] Add `LuaStandardGlobals` object with the exact base/delta sets from §3.3.
- [ ] Wire `inspectNameRef` step 3–4 (language level from `LuaProjectSettings`, allowlist
      check) and step 6 (underscore suppression).
- [ ] Unit tests: TC-04 (each level 5.1–5.4), TC-10.

## Phase 3: Additional Globals & Suppression [Should] — INSP-01-07,08

Goal: user allowlist and inline comment suppression.

- [ ] Add `additionalGlobals: MutableList<String>` to `LuaProjectSettings.State` + accessor
      (§2.4); confirm round-trip serialization to `lunar.xml`.
- [ ] Wire `inspectNameRef` step 5 (allowlist check).
- [ ] Add `LuaInspectionSuppression` with the two parsers/scope rules from §4 (cached per
      file via `CachedValuesManager`); wire `inspectNameRef` step 9.
- [ ] Add `LuaAddToGlobalsQuickFix` (§2.5) and attach it to the registered problem.
- [ ] Add the "Additional Globals" list editor to the Lua project settings page (§7).
- [ ] Unit tests: TC-07, TC-08, TC-09.

## Verification Tasks

### Unit Tests (`LuaUndeclaredVariableInspectionTest.kt`, `BasePlatformTestCase`)
Use `myFixture.configureByText("a.lua", …)` + `myFixture.checkHighlighting()` with
`<warning descr="Undeclared variable 'x'">x</warning>` markup.

| Test | Covers | Asserts |
| :--- | :--- | :--- |
| `testLocalResolved` | TC-01 | no highlight on resolved local |
| `testUndeclaredGlobal` | TC-02 | one WARNING with exact message |
| `testUsedBeforeLocal` | TC-03 | warning on use, none on declaration |
| `testStandardLibrary` (×4 levels) | TC-04 | no highlight on `print`/`math` |
| `testCrossFileGlobal` | TC-05 | no highlight after indexing `a.lua` |
| `testWriteTargetExcluded` | TC-06 | warning on `existing`, none on `newGlobal` |
| `testFunctionNameHead` | TC-11 | warning on `undeclaredTable`, none on `PlainGlobal` |
| `testAdditionalGlobals` | TC-07 | no highlight after adding `love` to settings |
| `testDiagnosticSuppression` | TC-08 | no highlight under `disable-next-line` |
| `testLuacheckSuppression` | TC-09 | no highlight with trailing `luacheck: ignore` |
| `testUnderscoreSuppressed` | TC-10 | no highlight on `_`-prefixed name |

### Manual Verification
See `human-verification-checklists.md`.
