---
id: INTENT-02-PLAN
title: Invert If Implementation Plan
type: plan
parent_id: INTENT-02
---

# Implementation Plan

All class/PSI names below are grep-verified in [design.md](design.md) §2.

## Phase 0: De-risking [Should]
Run the de-risking tasks in [risks-and-gaps.md](risks-and-gaps.md)
(`INTENT-02-00-DR-01`, `INTENT-02-00-DR-02`) before/while writing production code. Outcomes
fold back into [design.md](design.md) §4 (swap fidelity) and §3 (operator table).

## Phase 1: Intention Action [Must]

- **Tasks**:
  1. Add `net.internetisalie.lunar.lang.insight.LuaConditionInverter` (object) implementing
     the inversion table from [design.md](design.md) §3:
     `invertedText(condition: LuaExpr): String` with the relational flip (§3.1), the
     `not X` → `X` unwrap (§3.2), and the `not ( … )` wrap fallback (§3.3). Read the operator
     leaf via `binOp.firstChild?.node?.elementType` / `unOp.firstChild?.node?.elementType`
     compared against `LuaElementTypes.{EQ,NE,LT,LE,GT,GE,NOT}`.
  2. Add `net.internetisalie.lunar.lang.insight.LuaInvertIfIntention : BaseIntentionAction`
     with `getFamilyName() = "Lua"`, `getText() = "Invert 'if' statement"`, `isAvailable`
     per [design.md](design.md) §5 (gating on `exprList.size == 1`, `blockList.size == 2`,
     `ELSE` present, no `ELSEIF`), and `invoke` per §4 (rebuild via `LuaElementFactory.createFile`
     + `replace` inside `WriteCommandAction.runWriteCommandAction`, then `CodeStyleManager.reformat`).
  3. Register in `src/main/resources/META-INF/plugin.xml` next to the existing
     `<intentionAction>` (around line 355) with
     `<className>net.internetisalie.lunar.lang.insight.LuaInvertIfIntention</className>` and
     `<category>Lua</category>`.
  4. Add resource files under
     `src/main/resources/intentionDescriptions/LuaInvertIfIntention/`:
     `description.html`, `before.template.lua`, `after.template.lua` (contents in
     [design.md](design.md) §6).
- **Verification**: plugin compiles (`./gradlew compileKotlin`); the intention appears in the
  Alt+Enter menu for a simple `if/else` in a sandbox IDE (`./gradlew runIde`).

## Phase 2: Tests [Must]

- **Test class**: `LuaInvertIfIntentionTest : BasePlatformTestCase`, under
  `src/test/kotlin/net/internetisalie/lunar/lang/insight/LuaInvertIfIntentionTest.kt`.
- **Idiom** (verified against `LuaDocGenerationTest`,
  `src/test/kotlin/net/internetisalie/lunar/lang/doc/LuaDocGenerationTest.kt:149-151`):
  - `myFixture.configureByText(LuaFileType, input)` with a `<caret>` marker in `input`.
  - Positive: `val intention = myFixture.findSingleIntention("Invert 'if' statement")` then
    `myFixture.launchAction(intention)` then `myFixture.checkResult(expected)`.
  - Negative: `assertNull(myFixture.getAvailableIntention("Invert 'if' statement"))` (or
    assert the action is absent from `myFixture.availableIntentions`).
- **Cases** (one test method each; inputs/outputs from [requirements.md](requirements.md)):

  | Test method | Requirement(s) | Case |
  |---|---|---|
  | `test invert relational eq and swap` | 01,02,03 | TC1 (`==` → `~=`) |
  | `test unwrap not condition` | 01 | TC2 (`not ready` → `ready`) |
  | `test wrap non relational condition` | 01 | TC3 (`isValid()` → `not (isValid())`) |
  | `test wrap and chain no de morgan` | 01 | TC4 (`a and b` → `not (a and b)`) |
  | `test flip less than to ge` | 01 | TC5 (`<` → `>=`) |
  | `test elseif not offered` | 03 | TC6 (negative) |
  | `test no else not offered` | 03 | TC7 (negative) |

- **Verification**: `./gradlew test --tests "*LuaInvertIfIntentionTest"` is green.

## Phase 3: Polish & Docs [Should]

- **Tasks**:
  1. `./gradlew ktlintFormat` on the two new Kotlin files; match surrounding IntelliJ-formatter
     style (do not mass-reformat).
  2. Add a `CHANGELOG.md` entry (user-facing: new "Invert 'if' statement" intention).
  3. Flip [requirements.md](requirements.md) row statuses and front-matter `status` from
     `planned` to the implemented state once Phase 2 is green.
- **Verification**: full `./gradlew test` green relative to the pre-existing baseline (per the
  Wave baseline-cleanup convention — no NEW failures); `ktlintCheck` shows no new violations in
  the two new files.

## See Also
- Requirements: [requirements.md](requirements.md)
- Design: [design.md](design.md)
- Risks & gaps: [risks-and-gaps.md](risks-and-gaps.md)
