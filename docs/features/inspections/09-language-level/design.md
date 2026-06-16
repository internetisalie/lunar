---
id: "INSP-09-DESIGN"
title: "Technical Design"
type: "design"
status: "planned"
parent_id: "INSP-09"
folders:
  - "[[features/inspections/09-language-level/requirements|requirements]]"
---

# Technical Design: INSP-09 — Language-Level Compliance

## 1. Architecture Overview

### Current State
`net.internetisalie.lunar.lang.syntax.LuaLanguageLevelAnnotator` (an `Annotator`, registered
in `plugin.xml:124-126`) already flags every construct in scope at `ERROR` severity, with
quick fixes from `LuaLanguageLevelQuickFixes.kt`. A 49-test suite,
`src/test/kotlin/net/internetisalie/lunar/lang/syntax/LuaLanguageLevelAnnotatorTest.kt`,
exercises it via `myFixture.doHighlighting(HighlightSeverity.ERROR)`.

### Prior Art in This Repo — REPLACE, do not duplicate
INSP-09 **replaces** `LuaLanguageLevelAnnotator`. Shipping the inspection while leaving the
annotator registered would double-report every construct. The decision is: **migrate every
annotator check into a `LocalInspectionTool`, delete the annotator and its `<annotator>`
registration, reuse the existing quick-fix classes unchanged, and migrate the annotator's
test file to drive the inspection.** No new construct families are added (the annotator's
coverage is already exhaustive for what is PSI-detectable). The pattern mirrors
`LuaGlobalCreationInspection` (`src/main/kotlin/net/internetisalie/lunar/analysis/inspections/LuaGlobalCreationInspection.kt`),
which reads `LuaProjectSettings.getInstance(...).state.languageLevel` the same way (line 69-70).

#### Annotator-check → INSP-09 mapping (every existing check accounted for)
| Annotator check (`LuaLanguageLevelAnnotator.kt`) | PSI / text matched | Min level | INSP-09 home (visitor override) | Req | Quick fixes carried over |
|--------------------------------------------------|--------------------|-----------|---------------------------------|-----|--------------------------|
| `checkLua52Features` → `is LuaGotoStatement` (line 44) | `LuaGotoStatement` | 5.2 | `visitGotoStatement` | 09-03 | `UpgradeLanguageLevelFix(LUA52)`, `RemoveGotoFix` |
| `checkLua52Features` → `is LuaLabel` (line 52) | `LuaLabel` | 5.2 | `visitLabel` | 09-04 | `UpgradeLanguageLevelFix(LUA52)`, `RemoveLabelFix` |
| `checkLua53Features` → `is LuaBinOp`, `operator == "//"` (line 71) | `LuaBinOp` text `//` | 5.3 | `visitBinOp` | 09-05 | `UpgradeLanguageLevelFix(LUA53)`, `ReplaceIntegerDivisionFix` |
| `checkLua53Features` → `is LuaBinOp`, `isBitwiseOperator` (line 79) | `LuaBinOp` text `& \| ~ << >>` | 5.3 | `visitBinOp` | 09-02 | `UpgradeLanguageLevelFix(LUA53)` |
| `checkLua53Features` → `is LuaUnOp`, `operator == "~"` (line 89-91) | `LuaUnOp` text `~` | 5.3 | `visitUnOp` | 09-06 | `UpgradeLanguageLevelFix(LUA53)` |
| `checkLua54Features` → `is LuaAttrib` (line 106) | `LuaAttrib` | 5.4 | `visitAttrib` | 09-01 | `UpgradeLanguageLevelFix(LUA54)` |

Every annotator branch maps to exactly one inspection visitor override; nothing is dropped or
duplicated.

### Target State
One `LocalInspectionTool` visiting the six node kinds above; the annotator deleted; the four
quick-fix classes retained (moved or imported); the test file migrated to the inspection.

## 2. Core Components

### 2.1 `net.internetisalie.lunar.analysis.inspections.LuaLanguageLevelInspection`
- **Responsibility**: Visit version-constrained PSI nodes; register an `ERROR` problem when
  the project level is below the construct's minimum, attaching the carried-over quick fixes.
- **Base class**: `com.intellij.codeInspection.LocalInspectionTool`.
- **Threading**: platform inspection read action (no extra threading).
- **Collaborators**: `LuaProjectSettings`, `LuaLanguageLevel`, `LuaTokenTypes`, the
  `LuaLanguageLevelQuickFixes.kt` classes.
- **Key API** (signatures the implementer writes verbatim):
  ```kotlin
  class LuaLanguageLevelInspection : LocalInspectionTool() {
      override fun getShortName(): String = "LuaLanguageLevel"
      override fun getGroupDisplayName(): String = "Lua"
      override fun getDisplayName(): String = "Language level compliance"
      override fun isEnabledByDefault(): Boolean = true
      override fun getDefaultLevel(): HighlightDisplayLevel = HighlightDisplayLevel.ERROR

      override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
          object : LuaVisitor() {
              override fun visitGotoStatement(o: LuaGotoStatement) { ... }   // 5.2  → 09-03
              override fun visitLabel(o: LuaLabel) { ... }                   // 5.2  → 09-04
              override fun visitBinOp(o: LuaBinOp) { ... }                   // 5.3  → 09-02, 09-05
              override fun visitUnOp(o: LuaUnOp) { ... }                     // 5.3  → 09-06
              override fun visitAttrib(o: LuaAttrib) { ... }                 // 5.4  → 09-01
          }
  }
  ```
  All visit-method names and parameter types are verified against
  `src/main/gen/net/internetisalie/lunar/lang/psi/LuaVisitor.java` (`visitGotoStatement`:115,
  `visitLabel`:128, `visitBinOp`:30, `visitUnOp`:197, `visitAttrib`:22).

> **PSI note (grounding):** there is no bitwise-operator node. Operators are children of
> `LuaBinOp` / `LuaUnOp` (`src/main/gen/.../lang/psi/LuaBinOp.java`, `LuaUnOp.java`); the
> visitor inspects the operator node's `text`. The annotator proves `LuaBinOp.text` / `LuaUnOp.text`
> equals the operator string (`LuaLanguageLevelAnnotator.kt:69,90`).

### 2.2 Quick fixes — reuse, do not re-implement
`UpgradeLanguageLevelFix`, `RemoveGotoFix`, `RemoveLabelFix`, `ReplaceIntegerDivisionFix`
(all in `LuaLanguageLevelQuickFixes.kt`) extend `BaseIntentionAction`, which is **not** a
`LocalQuickFix`. `ProblemsHolder.registerProblem(...)` accepts `LocalQuickFix` varargs, but an
intention can be wrapped with `com.intellij.codeInspection.IntentionWrapper(intention)` (a
`LocalQuickFix`). The implementer passes
`IntentionWrapper(UpgradeLanguageLevelFix(LuaLanguageLevel.LUA53))` etc. to `registerProblem`.
The quick-fix classes themselves are unchanged; only their call site moves from
`annotation.withFix(...)` to `holder.registerProblem(element, message, IntentionWrapper(...))`.

## 3. Algorithms

### 3.1 Reading the level
```kotlin
val level = LuaProjectSettings.getInstance(o.project).state.languageLevel
```
Verified accessor: `LuaProjectSettings.getInstance(Project)` returns the project service
(`LuaProjectSettings.kt:147-149`); `state.languageLevel: LuaLanguageLevel` (`LuaProjectSettings.kt:45`,
default `LUA54`). Existing caller: `LuaGlobalCreationInspection.kt:69-70`.

### 3.2 Per-node evaluation (mirrors the annotator exactly)
Each visitor override:
1. Reads `level` (§3.1).
2. Gates on `level < LuaLanguageLevel.LUA5x` (`LuaLanguageLevel` is an enum, ordinal-ascending,
   so `<` works — `LuaLanguageLevel.kt:19-24`; the annotator uses this idiom at lines 28/31/34).
3. For `LuaBinOp`/`LuaUnOp`, matches the operator by `o.text` against the literal strings
   (`//`, `&`, `|`, `~`, `<<`, `>>`); `^` (power) and `/` (division) are never matched.
4. Calls `holder.registerProblem(o, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, *fixes)`.

Messages reuse the annotator wording (for test-string parity), e.g.:
- goto: `"Goto statements are a Lua 5.2+ feature (project configured for $level)"`
- label: `"Labels are a Lua 5.2+ feature (project configured for $level)"`
- `//`: `"Integer division (//) is a Lua 5.3+ feature (project configured for $level)"`
- bitwise binary: `"<Name> is a Lua 5.3+ feature (project configured for $level)"` where
  `<Name>` is `Bitwise AND operator (&)` / `Bitwise OR operator (|)` / `Bitwise NOT operator (~)`
  / `Left shift operator (<<)` / `Right shift operator (>>)` (`LuaLanguageLevelAnnotator.kt:132-140`)
- unary `~`: `"Bitwise NOT operator (~) is a Lua 5.3+ feature (project configured for $level)"`
- attribute: `"Variable attributes are a Lua 5.4 feature (project configured for $level)"`

### 3.3 Operator-name helper
Port `isBitwiseOperator` and `getBitwiseOperatorName` (`LuaLanguageLevelAnnotator.kt:126-140`)
into the inspection as private functions verbatim. `~` is in the bitwise set; `^` is excluded.

## 4. External Data & Parsing
*None.* No CLI/file/text input.

## 5. Data Flow

### Example 1: Unsupported bitwise AND
1. Source `local x = 1 & 2` in a LUA51 project.
2. `visitBinOp` fires on the `&` `LuaBinOp`; `o.text == "&"`.
3. `level == LUA51`; `LUA51 < LUA53` → true.
4. `registerProblem(o, "Bitwise AND operator (&) is a Lua 5.3+ feature (project configured for Lua 5.1)", GENERIC_ERROR_OR_WARNING, IntentionWrapper(UpgradeLanguageLevelFix(LUA53)))`.

### Example 2: Integer division quick fix
1. `local x = 10 // 3` in LUA52; `visitBinOp` sees `o.text == "//"`.
2. Problem registered with `IntentionWrapper(UpgradeLanguageLevelFix(LUA53))` and
   `IntentionWrapper(ReplaceIntegerDivisionFix())`.
3. User invokes "Replace // with / and math.floor()" → text becomes `local x = math.floor(10 / 3)`.

## 6. Edge Cases
- **Mixed operators**: `visitBinOp` only flags the bitwise/`//` operator texts; `+ - * / ^ .. == < ..`
  are ignored (Req 09-09).
- **`~` overload**: binary xor via `visitBinOp`, unary not via `visitUnOp`; one warning each.
- **`^`**: exponentiation, never flagged at any level (annotator test `regularExponentiationAllowedInAllVersions`).
- **Strings / comments**: `&`, `goto`, `//` inside a `STRING` or comment token are not `LuaBinOp`/`LuaGotoStatement`
  nodes, so they never fire (annotator tests `stringContaining*`).
- **Default level**: `LUA54` → no warnings on unconfigured projects.
- **Multiple attributes**: `local x <const>, y <close> = ...` → two `LuaAttrib` nodes → two warnings.
- **No double-reporting**: the annotator is deleted and deregistered in the same change (§7).

## 7. Integration Points

### 7.1 Add the inspection (`src/main/resources/META-INF/plugin.xml`, near the existing
`<localInspection>` block at lines ~137-190)
```xml
<localInspection
        language="Lua"
        shortName="LuaLanguageLevel"
        displayName="Language level compliance"
        groupName="Lua"
        enabledByDefault="true"
        level="ERROR"
        implementationClass="net.internetisalie.lunar.analysis.inspections.LuaLanguageLevelInspection"/>
```
(Models the `LuaGlobalCreation` entry verbatim — `plugin.xml:162-169`.)

### 7.2 Remove the annotator registration
Delete this exact block at `plugin.xml:124-126`:
```xml
<annotator
        language="Lua"
        implementationClass="net.internetisalie.lunar.lang.syntax.LuaLanguageLevelAnnotator"/>
```

### 7.3 Delete the annotator class
Delete `src/main/kotlin/net/internetisalie/lunar/lang/syntax/LuaLanguageLevelAnnotator.kt`.
**Keep** `LuaLanguageLevelQuickFixes.kt` (the inspection reuses all four classes). If
`LuaLanguageLevelInspection` imports the helpers `isBitwiseOperator`/`getBitwiseOperatorName`,
copy those two private functions into the inspection (they are private to the annotator).

### 7.4 Migrate the test
Replace `LuaLanguageLevelAnnotatorTest.kt` with
`src/test/kotlin/net/internetisalie/lunar/analysis/inspections/LuaLanguageLevelInspectionTest.kt`
that calls `myFixture.enableInspections(LuaLanguageLevelInspection())` in `setUp()` and keeps
the same `doHighlighting(HighlightSeverity.ERROR)` assertions (the messages are unchanged).
Delete the old annotator test so coverage is not double-counted and the deleted class has no
dangling test. See `implementation-plan.md` Phase 4.

## 8. Requirement Coverage
| Requirement | Priority | Implemented by (section) |
|-------------|----------|--------------------------|
| INSP-09-01 (attributes) | M | §2.1 `visitAttrib`, §3.2 |
| INSP-09-02 (bitwise binary) | M | §2.1 `visitBinOp`, §3.2/§3.3 |
| INSP-09-03 (goto) | M | §2.1 `visitGotoStatement`, §3.2 |
| INSP-09-04 (label) | M | §2.1 `visitLabel`, §3.2 |
| INSP-09-05 (integer division) | M | §2.1 `visitBinOp`, §3.2 |
| INSP-09-06 (unary bitwise-not) | M | §2.1 `visitUnOp`, §3.2 |
| INSP-09-07 (quick fixes) | M | §2.2, §3.2 |
| INSP-09-08 (no double-report) | M | §1, §7.2, §7.3 |
| INSP-09-09 (no false positives) | M | §3.2 step 3, §6 |

## 9. Alternatives Considered
- **Keep the annotator, add only net-new checks.** Rejected: the annotator's coverage is
  already complete for PSI-detectable constructs, so this would add nothing while leaving the
  feature un-toggleable; and any new check duplicates an annotator branch.
- **Make the quick fixes native `LocalQuickFix` rewrites instead of wrapping intentions.**
  Rejected for scope: `IntentionWrapper` reuses the proven fix logic with zero behavior change.

## 10. Open Questions
_None — replace-vs-extend is settled (replace, §1), operator detection is grounded in `LuaBinOp`/`LuaUnOp` text matching (§3.2), the quick-fix adaptation via `IntentionWrapper` is specified (§2.2), and the `\z`/hex-float deferral lives in [risks-and-gaps.md](risks-and-gaps.md)._
