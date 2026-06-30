---
id: INTENT-01-PLAN
title: String Conversion Implementation Plan
type: plan
parent_id: INTENT-01
folders:
  - "[[features/refactoring/intent-01-string-conversion/requirements|requirements]]"
  - "[[features/refactoring/intent-01-string-conversion/design|design]]"
---

# Implementation Plan: String Quote Conversion Intention

Implements [design.md](design.md). All paths are relative to the repo root.

## Phase 0: Pre-implementation de-risking [Must]

Run the de-risking tasks in [risks-and-gaps.md](risks-and-gaps.md) first
(`INTENT-01-00-DR-01`, `-DR-02`, `-DR-03`). They confirm the two non-obvious grounding facts
(long strings merge to a single `STRING` leaf; `extractLuaString` decodes all three forms) and
the `]]`-guard level algorithm before code is written.

## Phase 1: Encode helper [Must]

- **Tasks:**
  1. Add `encodeLuaString(value: String, target: Form): String` to
     `src/main/kotlin/net/internetisalie/lunar/lang/syntax/LuaLiterals.kt`, plus the
     `Form` enum (`SINGLE`, `DOUBLE`, `LONG`, `UNKNOWN`) and `longBracketLevel(value): Int`
     (design §3.2–3.3). Reuse the existing `SIMPLE_ESCAPES` map for control-char escaping;
     make it `internal` if `encodeLuaString` is a separate file (it can live in the same file).
  2. Keep each function ≤30 logic lines / ≤3 args per the engineering contract.
- **Verification:** unit tests on the pure helper (no fixture needed) — see Phase 3 `LuaLiteralsTest`.

## Phase 2: Intention action + registration [Must]

- **Tasks:**
  1. Create
     `src/main/kotlin/net/internetisalie/lunar/lang/insight/LuaStringConversionIntention.kt`
     extending `com.intellij.codeInsight.intention.impl.BaseIntentionAction`, with
     `getFamilyName()` = `"Convert string quotes"`, `getText()` returning the stored
     `actionText`, `isAvailable(project, editor, file)` (design §3.5) and
     `invoke(project, editor, file)` (design §3.4). Add private `stringLeafFor`, `currentForm`,
     `nextForm`, `textForNext` helpers.
  2. Register in `src/main/resources/META-INF/plugin.xml` — add the `<intentionAction>` block
     (design §4.1) inside the `com.intellij` `<extensions>` element, next to the existing
     `LuaGenerateDocIntention` registration (line ~355).
  3. Create the description resources (design §4.2):
     - `src/main/resources/intentionDescriptions/LuaStringConversionIntention/description.html`
     - `src/main/resources/intentionDescriptions/LuaStringConversionIntention/before.template.lua`
     - `src/main/resources/intentionDescriptions/LuaStringConversionIntention/after.template.lua`
- **Verification:** `./gradlew build` (plugin verification fails if the description resource dir
  is missing) + Phase 3 fixture tests.

## Phase 3: Tests [Must]

- **Tasks:**
  1. `src/test/kotlin/net/internetisalie/lunar/lang/syntax/LuaLiteralsTest.kt` — pure-unit tests
     for `encodeLuaString` and `longBracketLevel` (no platform fixture): assert
     `encodeLuaString("a\"b", DOUBLE) == "\"a\\\"b\""`, the `it's`→`'it\'s'` case, and
     `longBracketLevel("a]]b") == 1`.
  2. `src/test/kotlin/net/internetisalie/lunar/lang/insight/LuaStringConversionIntentionTest.kt`
     extending `com.intellij.testFramework.fixtures.BasePlatformTestCase`, following the verified
     repo idiom (`src/test/kotlin/net/internetisalie/lunar/lang/insight/LuaGlobalCreationInspectionTest.kt:48-51`):
     ```
     myFixture.configureByText("test.lua", "local s = 'hello'<caret>")
     val intention = myFixture.findSingleIntention("Convert to double-quoted string")
     myFixture.launchAction(intention)
     myFixture.checkResult("local s = \"hello\"")
     ```
     One test per requirements test case:
     - TC1 single→double, TC2 double→long, TC3 long→single (the cycle).
     - TC4 `'a"b'`→`"a\"b"`, TC5 `"a\"b"`→`'a"b'` (round-trip pair), TC6 `"it's"`→`'it\'s'`.
     - TC7 `"tab\there"`→`[[tab<TAB>here]]` (escape resolved into literal long content).
     - TC8 `"a]]b"`→ long form: assert the result is balanced and re-decodes to `a]]b` (do not
       hard-code a fixed level string; assert via `extractLuaString(result) == "a]]b"` to keep
       the test robust to the minimal-level choice).
     - TC9 `local s<caret> = 1`: `assertEmpty(myFixture.filterAvailableIntentions("Convert to single-quoted string"))`
       (or `assertNull(myFixture.getAvailableIntention("…"))`) — intention not offered.
- **Verification:** `./gradlew test --tests "*LuaStringConversionIntentionTest"` and
  `--tests "*LuaLiteralsTest"` pass; full `./gradlew test` shows no regression vs. baseline.

## Phase 4: Polish & contract checks [Should]

- **Tasks:**
  1. `./gradlew ktlintFormat ktlintCheck` on the new files (match IntelliJ-formatter style;
     do not mass-reformat existing files).
  2. Update `CHANGELOG.md` with the user-facing intention (per CLAUDE.md contribution guidelines).
  3. Set `status: planned → in-progress → done` in the four feature docs' front-matter as work
     proceeds; regenerate `docs/status.md` via the `summary` skill at the end.
- **Verification:** `ktlintCheck` clean on new files; `./gradlew build` green.

## Definition of Done

- All 9 requirements test cases pass.
- `./gradlew build` (incl. plugin verification) and `./gradlew test` green (no regression).
- `<intentionAction>` registered and its description-resource dir present.
- Each `Must` requirement (INTENT-01-01/02/03) has a passing test.

## See Also
- Requirements: [requirements.md](requirements.md)
- Design: [design.md](design.md)
- Risks & gaps: [risks-and-gaps.md](risks-and-gaps.md)
