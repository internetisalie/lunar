---
id: INTENT-03-PLAN
title: Name Suggestion Implementation Plan
type: plan
parent_id: INTENT-03
status: planned
---

# Implementation Plan

All class/PSI names below are grep-verified in [design.md](design.md) §1–§6. Resolve the
de-risking tasks in [risks-and-gaps.md](risks-and-gaps.md) BEFORE Phase 1 (especially
INTENT-03-00-DR-01, the IntroduceVariable↔provider wiring, and INTENT-03-00-DR-02, the Rename
element→RHS walk).

## Phase 0: De-risking [Must]
- Run INTENT-03-00-DR-01 and INTENT-03-00-DR-02 (see risks doc). Fold outcomes into
  design.md §4 (element resolution) before writing code.

## Phase 1: Shared derivation helper [Must]
- **Tasks**:
  1. Create `net.internetisalie.lunar.refactoring.rename.LuaNameDeriver` with
     `fun baseName(expr: LuaExpr): String?` implementing design.md §3 (raw-name extraction
     for `LuaFuncCall` plain/dotted/method, `LuaIndexExpr`, `LuaNameRef`; prefix strip with
     list `["create","build","find","load","make","get","set","new"]` and the uppercase rule).
  2. Refactor `LuaIntroduceVariableHandler.baseNameFor` (`LuaIntroduceVariableHandler.kt:144`)
     to delegate to `LuaNameDeriver.baseName(expr)` with the existing `LuaBinOpExpr -> "result"`
     / `else -> "value"` fallbacks. Keep `uniquify` (`:160-170`) untouched.
- **Verification — `LuaNameDeriverTest` (`src/test/kotlin/.../refactoring/rename/`)**:
  Unit-test `baseName` directly with `myFixture.configureByText("t.lua", ...)` to build PSI,
  then `runReadAction` to locate the `LuaExpr` and assert the returned string:
  - `getUser()` → `user`; `compute()` → `compute`; `obj:getName()` → `name`;
    `db.getUser()` → `user`; `cfg.timeout` (as `LuaIndexExpr` initializer) → `timeout`;
    `settings()` → `settings`; `getter()` → `getter`.

## Phase 2: NameSuggestionProvider [Must]
- **Tasks**:
  1. Create `LuaNameSuggestionProvider : NameSuggestionProvider` (design.md §2 signature),
     resolving the target expr per design.md §4 and calling `LuaNameDeriver.baseName`; add the
     candidate to `result` and return `SuggestedNameInfo.NULL_INFO`, else `null`.
  2. Register `<nameSuggestionProvider implementation="net.internetisalie.lunar.refactoring.rename.LuaNameSuggestionProvider"/>`
     in `plugin.xml` near lines 206–210.
- **Verification — `LuaNameSuggestionProviderTest` (`src/test/kotlin/.../refactoring/rename/`)**:
  Call `getSuggestedNames(element, context, mutableSetOf())` directly (no UI needed — the EP
  method is pure PSI). Assert the result set contains the expected name for TC2–TC6
  (requirements.md). For the Rename element shape, configure `local x = getUser()` and pass the
  `local` declaration's name element as `element` (shape confirmed by DR-02).

## Phase 3: Introduce Variable integration test [Must]
- **Verification — extend `LuaIntroduceVariableTest`
  (`src/test/kotlin/net/internetisalie/lunar/refactoring/LuaIntroduceVariableTest.kt`)**:
  - Keep `testCalleeNameSuggestion` (`:60-63`, `compute` unchanged).
  - Add `testPrefixStrippedSuggestion`: `introduceSelected("print(<selection>getUser()</selection>)")`
    then `myFixture.checkResult("local user = getUser()\nprint(user)")` (TC1).
  - Add a method-call case: selecting `obj:getName()` introduces `local name = obj:getName()`.

## Test Approach Summary
- `LuaNameDeriver` / `LuaNameSuggestionProvider`: **unit-testable directly** — `baseName` and
  `getSuggestedNames` are pure PSI functions; build PSI with `BasePlatformTestCase` +
  `myFixture.configureByText`, no need to drive the Rename or Introduce UI.
- End-to-end Introduce Variable behavior: driven through the existing handler test harness
  (`handler.invoke(...)` under unit-test mode), which already runs headlessly.

## See Also
- Requirements: [requirements.md](requirements.md)
- Design: [design.md](design.md)
- Risks & Gaps: [risks-and-gaps.md](risks-and-gaps.md)
</content>
