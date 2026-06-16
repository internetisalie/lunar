---
id: "INSP-09-PLAN"
title: "Implementation Plan"
type: "plan"
status: "done"
parent_id: "INSP-09"
folders:
  - "[[features/inspections/09-language-level/requirements|requirements]]"
---

# INSP-09: Implementation Plan

Replace `LuaLanguageLevelAnnotator` with a user-controllable `LocalInspectionTool`. Reuse the
four existing quick fixes; migrate the existing test suite. No production behavior is added
beyond toggle/severity control — the construct coverage is unchanged.

## Phases

### Phase 1: Inspection skeleton [Must]
- Create `src/main/kotlin/net/internetisalie/lunar/analysis/inspections/LuaLanguageLevelInspection.kt`
  extending `LocalInspectionTool` with `getShortName()="LuaLanguageLevel"`, `getGroupDisplayName()="Lua"`,
  `getDisplayName()="Language level compliance"`, `getDefaultLevel()=HighlightDisplayLevel.ERROR`
  (design §2.1). Empty `buildVisitor` returning a `LuaVisitor`.
- Wire level read: `LuaProjectSettings.getInstance(o.project).state.languageLevel` (design §3.1).
- Register the `<localInspection>` in `plugin.xml` per design §7.1.

### Phase 2: Implement checks [Must]
- Override `visitGotoStatement` (5.2, 09-03), `visitLabel` (5.2, 09-04), `visitBinOp`
  (5.3 — `//` 09-05 and bitwise `& | ~ << >>` 09-02), `visitUnOp` (5.3, unary `~` 09-06),
  `visitAttrib` (5.4, 09-01). Gate each on `level < LuaLanguageLevel.LUA5x` (design §3.2).
- Port the private `isBitwiseOperator` / `getBitwiseOperatorName` helpers from the annotator
  (design §3.3); match `LuaBinOp`/`LuaUnOp` by `.text`.
- Use the exact annotator messages (design §3.2) for test-string parity.

### Phase 3: Quick fixes [Must]
- For each problem, pass the carried-over fixes to `holder.registerProblem(...)` wrapped in
  `com.intellij.codeInspection.IntentionWrapper` (design §2.2): always
  `UpgradeLanguageLevelFix(<level>)`; plus `RemoveGotoFix` (goto), `RemoveLabelFix` (label),
  `ReplaceIntegerDivisionFix` (`//`). Keep `LuaLanguageLevelQuickFixes.kt` unchanged.

### Phase 4: Replace the annotator + migrate tests [Must]
- Delete the `<annotator ... LuaLanguageLevelAnnotator/>` block at `plugin.xml:124-126` (design §7.2).
- Delete `LuaLanguageLevelAnnotator.kt` (design §7.3); keep `LuaLanguageLevelQuickFixes.kt`.
- Replace `src/test/kotlin/.../lang/syntax/LuaLanguageLevelAnnotatorTest.kt` with
  `src/test/kotlin/.../analysis/inspections/LuaLanguageLevelInspectionTest.kt` using
  `myFixture.enableInspections(LuaLanguageLevelInspection())` and the same
  `doHighlighting(HighlightSeverity.ERROR)` assertions (design §7.4). Add the three quick-fix
  tests (requirements Test Cases 9–11) using `findSingleIntention` + `launchAction` + `checkResult`.

## Requirement → Phase Coverage
| Requirement | Phase |
|-------------|-------|
| INSP-09-01 attributes | 2 |
| INSP-09-02 bitwise binary | 2 |
| INSP-09-03 goto | 2 |
| INSP-09-04 label | 2 |
| INSP-09-05 integer division | 2 |
| INSP-09-06 unary bitwise-not | 2 |
| INSP-09-07 quick fixes | 3 |
| INSP-09-08 no double-report | 4 |
| INSP-09-09 no false positives | 2 (verified in tests) |

## Verification Tasks
- **Unit**: new `LuaLanguageLevelInspectionTest` covers requirements Test Cases 1–13 (highlighting
  ranges per level + the three quick-fix outcomes). Reuse the migrated annotator test bodies.
- **Build**: `./gradlew test --tests "*LuaLanguageLevelInspection*"`, then full `./gradlew test`
  to confirm no other suite referenced the deleted annotator/test.
- **Lint**: `./gradlew ktlintFormat` on the new file.
- **Manual**: per [human-verification-checklists.md](human-verification-checklists.md).

## Task Summary
Five overrides in one new inspection class, one `plugin.xml` add + one `plugin.xml` removal,
one class deletion, one test-file migration with three added quick-fix tests. No new quick-fix
code. Estimated 3–5 hours.

## See Also
- Requirements: [requirements.md](requirements.md)
- Design: [design.md](design.md)
- Risks: [risks-and-gaps.md](risks-and-gaps.md)
