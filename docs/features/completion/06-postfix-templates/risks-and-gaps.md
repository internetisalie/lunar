---
id: COMP-06-RISKS
title: Postfix Templates Risks & Gaps
type: risk
status: done
parent_id: COMP-06
folders:
  - "[[features/completion/06-postfix-templates/requirements|requirements]]"
---

# COMP-06: Risks & Gaps

Risks for expanding the postfix set from `.if` to the full COMP-06-01…11. The dominant risk is
the `.var` editable-name template; the string-template family (§2.3) is low-risk, being a direct
clone of the shipped `.if`.

## Critical Risks

### Risk 1.1: `.var` editable name tab stop fails to register or commit
- **Impact**: COMP-06-03 ships without an editable name, or throws during expansion — the Must
  template is broken.
- **Likelihood**: medium — `setVariables` on `StringBasedPostfixTemplate` plus a `$name$`
  template variable is the non-obvious part of the design (design §2.4/§3.2); ordering of
  `$name$` vs the base-registered `expr`/`$END$` tab stops is framework-dependent.
- **Mitigation**: in Phase 2, follow the platform's own string-based templates that register
  extra variables (e.g. JetBrains `StringBasedPostfixTemplate` subclasses with `setVariables`).
  Resolved/validated by COMP-06-00-DR-01.

### Risk 1.2: Selector returns a statement-context expression that breaks block templates
- **Impact**: a `for`/`if` template applied where the captured `LuaExpr` is itself already a
  statement-level construct (e.g. caret inside a larger statement) could produce malformed Lua or
  delete the wrong range.
- **Likelihood**: low — `LuaExprSelector` walks `LuaExpr` ancestors only and the `.if` template
  has shipped on this exact selector without report; but the new block templates (`.for*`) wrap
  the expression in more syntax, widening the blast radius.
- **Mitigation**: COMP-06-00-DR-02 — test each block trigger against a comparison expression
  (`x > 5.for`) and a call (`f().forp`) to confirm outermost-first capture and correct removal.

### Risk 1.3: Template-string indentation differs between headless tests and the real IDE
- **Impact**: block templates assert one indentation in tests but the IDE reformats to another;
  test churn or user-visible mis-indent.
- **Likelihood**: medium — already documented for `.if`: the headless `setTemplateTesting`
  harness does not reformat, so the body line is unindented in `checkResult`
  (`LuaPostfixTemplateTest.kt:18`). All five block templates inherit this.
- **Mitigation**: write `checkResult` expectations with the body line unindented (matching the
  shipped `.if` test); rely on the formatter for real-IDE indentation. Document in each test.

## Design Gaps

### Gap 2.1: `.var` name seeding quality (placeholder vs. smart suggestion)
- **Question**: should `$name$` seed a literal `"value"`, or derive a name from the expression
  (callee/property name) the way `LuaIntroduceVariableHandler.baseNameFor`
  (`refactoring/LuaIntroduceVariableHandler.kt:144`) does?
- **Options / leaning**: ship literal `"value"` for the Must bar (user edits it); defer smart
  seeding. Reusing the handler's `baseNameFor`/`uniquify` would require exposing them as a
  shared util — out of scope for COMP-06.
- **Resolved by**: decided — literal seed (design §2.4). Smart seeding deferred to Future Work
  below; not a blocker for `planned`.

## Technical Debt & Future Work
- **TBD: Smart `.var` name suggestion** — lift `baseNameFor`/`uniquify` from
  `LuaIntroduceVariableHandler` into a shared helper and seed `$name$` from it.
- **TBD: Backlog templates** — `.par`, `.tostring`, `.tonumber`, `.inc`, `.dec`, `.while`,
  `.assert` (requirements.md Backlog); `.par` carries a multi-return-truncation footgun.

## Pre-Implementation De-risking Tasks

| ID | Action | Resolves | Status |
|----|--------|----------|--------|
| COMP-06-00-DR-01 | Prototype `LuaVarPostfixTemplate.setVariables` registering an editable `$name$` tab stop; confirm it expands `getUser().var` → `local value = getUser()` with `value` selected in a headless test. | Risk 1.1 | done |
| COMP-06-00-DR-02 | Test `LuaExprSelector` outermost-first capture for block templates against `x > 5.for` and `f().forp`; confirm correct expression removal/binding. | Risk 1.2 | done |
| COMP-06-00-DR-03 | Capture the headless-vs-IDE indentation for one block template and codify the `checkResult` shape used across all block tests. | Risk 1.3 | done |

## Test Case Gaps
- requirements.md provides TC 1–6 (`.if`, `.not`, `.var`, `.for`, `.forp`, `.fori`). The Should
  set (COMP-06-07…11: `.ifnot`, `.nil`, `.notnil`, `.return`, `.print`) has **no** input→output
  test cases in requirements.md — add them there, or cover via the design §2.3 table in the
  Verification Tasks of the plan. (Should-priority, so the missing TCs do not block the Must bar.)

## See Also
- Requirements: [requirements.md](requirements.md)
- Design: [design.md](design.md)
