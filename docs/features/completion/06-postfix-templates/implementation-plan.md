---
id: COMP-06-PLAN
title: Postfix Templates Plan
type: plan
status: planned
parent_id: COMP-06
folders:
  - "[[features/completion/06-postfix-templates/requirements|requirements]]"
---

# COMP-06: Implementation Plan

Sequence design.md into shippable, verifiable phases. Each phase leaves the build green and the
suite passing. All new classes live in
`src/main/kotlin/net/internetisalie/lunar/lang/completion/postfix/`; all tests extend the
existing `LuaPostfixTemplateTest` (`BasePlatformTestCase`,
`src/test/kotlin/.../completion/postfix/LuaPostfixTemplateTest.kt`) using the `.if` test's
`configureByText` + `myFixture.type("\t")` + `checkResult` pattern.

## Phases

### Phase 0: Extract shared selector [Must]
- **Goal**: a single reusable `LuaExprSelector` so every template keys on the same logic.
- **Tasks**:
  - [ ] Create `LuaExprSelector.kt` — promote the private `Selector` inner class from
    `LuaIfPostfixTemplate.kt:26` to top-level `internal class LuaExprSelector` (design §2.2/§3.1),
    keeping `getExpressions`/`getNonFilteredExpressions` byte-for-byte.
  - [ ] Edit `LuaIfPostfixTemplate.kt` to drop its inner `Selector` and pass `LuaExprSelector()`.
- **Exit criteria**: existing `testIfPostfixTemplate` still passes; no behavior change.

### Phase 1: Must string templates — `.not`, `.for`, `.forp`, `.fori` [Must]
- **Goal**: ship the four remaining Must string templates.
- **Tasks**:
  - [ ] Create `LuaNotPostfixTemplate` (design §2.3 row `.not`) — realizes COMP-06-02.
  - [ ] Create `LuaForPostfixTemplate` (design §2.3 row `.for`) — realizes COMP-06-04.
  - [ ] Create `LuaForPairsPostfixTemplate` (`.forp`, design §2.3) — realizes COMP-06-05.
  - [ ] Create `LuaForIpairsPostfixTemplate` (`.fori`, design §2.3) — realizes COMP-06-06.
  - [ ] Add all four to `LuaPostfixTemplateProvider.templates` (design §2.1).
- **Exit criteria**: TC 2, TC 4, TC 5, TC 6 pass as unit tests; `ktlintCheck` shows no new
  violations in `completion/postfix/`.

### Phase 2: `.var` editable-name template [Must]
- **Goal**: `expr.var` → `local <name> = <expr>` with an editable `$name$` tab stop.
- **Tasks**:
  - [ ] Create `LuaVarPostfixTemplate` (design §2.4) — `getTemplateString` emits
    `local $name$ = $expr$$END$`; override `setVariables` to register the editable `name`
    variable (`TextExpression("value")`). Realizes COMP-06-03.
  - [ ] Add `LuaVarPostfixTemplate` to `LuaPostfixTemplateProvider.templates`.
- **Exit criteria**: TC 3 passes — headless test asserts `local value = getUser()` is produced
  (assert the committed text; the inline-edit tab stop is driven by the template harness).

### Phase 3: Should set — `.ifnot`, `.nil`, `.notnil`, `.return`, `.print` [Should]
- **Goal**: ship the five Should string templates.
- **Tasks**:
  - [ ] Create `LuaIfNotPostfixTemplate` (`.ifnot`, design §2.3) — realizes COMP-06-07.
  - [ ] Create `LuaNilPostfixTemplate` (`.nil`, design §2.3) — realizes COMP-06-08.
  - [ ] Create `LuaNotNilPostfixTemplate` (`.notnil`, design §2.3) — realizes COMP-06-09.
  - [ ] Create `LuaReturnPostfixTemplate` (`.return`, design §2.3) — realizes COMP-06-10.
  - [ ] Create `LuaPrintPostfixTemplate` (`.print`, design §2.3) — realizes COMP-06-11.
  - [ ] Add all five to `LuaPostfixTemplateProvider.templates` (design §2.1).
- **Exit criteria**: unit tests (Verification below) pass for all five triggers.

## Requirement → Phase Coverage

| Requirement | Priority | Delivered in |
|-------------|----------|--------------|
| COMP-06-01 `.if`     | M | Built; retargeted to `LuaExprSelector` in Phase 0 |
| COMP-06-02 `.not`    | M | Phase 1 |
| COMP-06-03 `.var`    | M | Phase 2 |
| COMP-06-04 `.for`    | M | Phase 1 |
| COMP-06-05 `.forp`   | M | Phase 1 |
| COMP-06-06 `.fori`   | M | Phase 1 |
| COMP-06-07 `.ifnot`  | S | Phase 3 |
| COMP-06-08 `.nil`    | S | Phase 3 |
| COMP-06-09 `.notnil` | S | Phase 3 |
| COMP-06-10 `.return` | S | Phase 3 |
| COMP-06-11 `.print`  | S | Phase 3 |

## Verification Tasks
- [ ] Extend `LuaPostfixTemplateTest` with one test per new trigger, mirroring
  `testIfPostfixTemplate` (configure `name.<trigger><caret>`, `type("\t")`, `checkResult`):
  covers TC 2 (`.not`), TC 3 (`.var`), TC 4 (`.for`), TC 5 (`.forp`), TC 6 (`.fori`), plus
  `.ifnot`/`.nil`/`.notnil`/`.return`/`.print` (assert the rewritten text per design §2.3 table;
  body line unindented per the headless-harness note in design §2.3).
- [ ] Confirm `LuaPostfixTemplateProvider.getTemplates()` returns 11 templates (guard test).
- [ ] `./gradlew test --tests "*Postfix*"` green; `./gradlew ktlintCheck` no new violations.

## Task Summary

| Phase | Status | Priority |
|-------|--------|----------|
| Phase 0: Extract shared selector | todo | Must |
| Phase 1: Must string templates (`.not`/`.for`/`.forp`/`.fori`) | todo | Must |
| Phase 2: `.var` editable-name template | todo | Must |
| Phase 3: Should set (`.ifnot`/`.nil`/`.notnil`/`.return`/`.print`) | todo | Should |
