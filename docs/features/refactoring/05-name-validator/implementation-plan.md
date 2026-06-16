---
id: REFACT-05-PLAN
title: "Implementation Plan"
type: plan
parent_id: REFACT-05
status: done
priority: "medium"
folders:
  - "[[features/refactoring/05-name-validator/requirements|requirements]]"
---

# Implementation Plan: REFACT-05 Rename Names Validator

Self-contained; depends only on the existing `LuaKeywords` object. No PSI, indexing, or threading
concerns (the validator is a pure string check). See risks-and-gaps for de-risking tasks
`REFACT-05-00-DR-*`, which should be run before Phase 1.

## Phase 1: Validator implementation [Must]

- **Tasks**
  1. Create `src/main/kotlin/net/internetisalie/lunar/refactoring/LuaNamesValidator.kt`
     implementing `com.intellij.lang.refactoring.NamesValidator` (signatures
     `isKeyword(name: String, project: Project?): Boolean`,
     `isIdentifier(name: String, project: Project?): Boolean`).
  2. `isKeyword` → `LuaKeywords.isReserved(name)`.
  3. `isIdentifier` → `IDENTIFIER_PATTERN.matches(name) && !LuaKeywords.isReserved(name)`,
     with `private val IDENTIFIER_PATTERN = Regex("^[A-Za-z_][A-Za-z0-9_]*$")` in a companion
     object (compiled once).
  4. Register in `src/main/resources/META-INF/plugin.xml` next to the
     `<lang.refactoringSupport>` block:
     `<lang.namesValidator language="Lua"
     implementationClass="net.internetisalie.lunar.refactoring.LuaNamesValidator"/>`.
- **Satisfies:** REFACT-05-01, REFACT-05-02.

## Phase 2: Verification [Must]

- **Test class:** `LuaNamesValidatorTest` (`src/test/kotlin/net/internetisalie/lunar/refactoring/`),
  extending `BasePlatformTestCase`. The validator is a pure function, so tests instantiate it
  directly (`val validator = LuaNamesValidator()`) and call `isKeyword` / `isIdentifier` with
  `project = null` (or `myFixture.project`); **no rename UI / no `myFixture.renameElementAtCaret`
  is required**.
- **Test methods (map 1:1 to requirements test cases):**

  | Test method | Asserts | Requirement |
  |-------------|---------|-------------|
  | `testValidIdentifierAccepted` | `isIdentifier("foo") == true`, `isIdentifier("_x1") == true`, `isIdentifier("X") == true`, `isKeyword("foo") == false` | REFACT-05-02 |
  | `testKeywordRejected` | `isKeyword("local") == true`, `isIdentifier("local") == false` | REFACT-05-01 |
  | `testInvalidIdentifierRejected` | `isIdentifier("1var") == false`, `isIdentifier("a-b") == false`, `isIdentifier("") == false`, `isIdentifier("foo bar") == false` | REFACT-05-02 |
  | `testGotoIsKeyword` | `isKeyword("goto") == true`, `isIdentifier("goto") == false` | REFACT-05-01 |
  | `testEndIsKeyword` | `isKeyword("end") == true`, `isIdentifier("end") == false` | REFACT-05-01 |
  | `testNearKeywordIsValidIdentifier` | `isIdentifier("end_") == true`, `isIdentifier("End") == true`, `isKeyword("End") == false` | REFACT-05-02 |
  | `testAllReservedWordsAreKeywords` | every word in `LuaKeywords.RESERVED` returns `isKeyword == true` and `isIdentifier == false` | REFACT-05-01 |

- **Build gate:** `./gradlew test --tests "*LuaNamesValidatorTest*"`, then `ktlintFormat` /
  `ktlintCheck` on the new files. Confirm the EP loads (no plugin.xml schema error) via the
  normal `./gradlew test` plugin-load path.

## Done Criteria

- `LuaNamesValidator` registered and loaded; all `LuaNamesValidatorTest` methods green.
- Renaming a Lua local/function to `local`/`goto`/`1var` is rejected by the platform rename UI
  (manual smoke check in `runIde`, optional — the boolean unit tests are the gate).
