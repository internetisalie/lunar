---
id: COMP-06-DESIGN
title: Postfix Templates Design
type: design
parent_id: COMP-06
status: done
folders:
  - "[[features/completion/06-postfix-templates/requirements|requirements]]"
---

# Technical Design: COMP-06 — Postfix Templates

Postfix completion lets a user type an expression followed by `.if` / `.for` / `.var` (etc.)
and have it rewritten into the corresponding statement or expression, with the original
expression spliced in. Implemented on the platform `PostfixTemplateProvider` extension point.
This design covers the full surveyed set **COMP-06-01…11** (Must: `.if`, `.not`, `.var`,
`.for`, `.forp`, `.fori`; Should: `.ifnot`, `.nil`, `.notnil`, `.return`, `.print`).

## 1. Architecture Overview

### Current State (what is actually built)
- `net.internetisalie.lunar.lang.completion.postfix.LuaPostfixTemplateProvider`
  (`completion/postfix/LuaPostfixTemplateProvider.kt:8`) implements
  `com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider` and returns a
  one-element set from `getTemplates()`.
- `net.internetisalie.lunar.lang.completion.postfix.LuaIfPostfixTemplate`
  (`LuaIfPostfixTemplate.kt:12`) implements `.if`, extending
  `com.intellij.codeInsight.template.postfix.templates.StringBasedPostfixTemplate`. It carries a
  private inner `Selector : PostfixTemplateExpressionSelectorBase` (`LuaIfPostfixTemplate.kt:26`).
- Registered at `plugin.xml:189`.
- Covered by `src/test/kotlin/.../completion/postfix/LuaPostfixTemplateTest.kt` (one test).

**Gap vs requirements:** only `.if` (COMP-06-01) is built. The other ten templates
(COMP-06-02…11) are unbuilt; this design specifies all of them.

### Prior Art in This Repo
- Searched `src/main` for `PostfixTemplate` / `postfix` — the only postfix code is this
  feature's own `completion/postfix/` package. No other component rewrites
  expression-into-statement.
- For `.var` (COMP-06-03) there IS a relevant existing component: the Introduce Variable
  refactoring `net.internetisalie.lunar.refactoring.LuaIntroduceVariableHandler`
  (`refactoring/LuaIntroduceVariableHandler.kt:37`), a `RefactoringActionHandler` that extracts
  a `LuaExpr` into `local <name> = <expr>` and offers an inline-rename tab stop. The `.var`
  template **delegates to it** rather than re-implementing local extraction (§2.4). It also
  exposes `LuaElementFactory.createFile(project, text)` used for PSI construction.
- `LuaExpr` is the generated PSI super-interface for all Lua expressions
  (`src/main/gen/net/internetisalie/lunar/lang/psi/LuaExpr.java`). All selectors key on it.

This design **extends** `LuaPostfixTemplateProvider` (adds ten templates + extracts a shared
selector); it does not replace anything.

### Target State
`LuaPostfixTemplateProvider.getTemplates()` returns eleven templates. Ten are
`StringBasedPostfixTemplate` subclasses keyed on a single shared `LuaExpr` selector
(extracted to `LuaExprSelector`, §3.1). One — `.var` — is a `StringBasedPostfixTemplate`
emitting `local $name$ = $expr$` with an editable `$name$` tab stop (§2.4); see §9 for why
delegating to the refactoring handler was rejected in favor of the string form.

```
LuaPostfixTemplateProvider.getTemplates()
 ├─ LuaIfPostfixTemplate       (.if,    built)   ─┐
 ├─ LuaNotPostfixTemplate      (.not)            │
 ├─ LuaVarPostfixTemplate      (.var)            │ all share
 ├─ LuaForPostfixTemplate      (.for)            │ LuaExprSelector
 ├─ LuaForPairsPostfixTemplate (.forp)           │ (§3.1)
 ├─ LuaForIpairsPostfixTemplate(.fori)           │
 ├─ LuaIfNotPostfixTemplate    (.ifnot)          │
 ├─ LuaNilPostfixTemplate      (.nil)            │
 ├─ LuaNotNilPostfixTemplate   (.notnil)         │
 ├─ LuaReturnPostfixTemplate   (.return)         │
 └─ LuaPrintPostfixTemplate    (.print)         ─┘
```

## 2. Core Components

### 2.1 `net.internetisalie.lunar.lang.completion.postfix.LuaPostfixTemplateProvider`
- **Responsibility**: expose Lunar's postfix templates to the platform.
- **Threading**: platform-invoked on the EDT during completion; no I/O.
- **Collaborators**: all eleven template classes.
- **Key API** (edit the existing `templates` set):
  ```kotlin
  class LuaPostfixTemplateProvider : PostfixTemplateProvider {
      private val templates = setOf<PostfixTemplate>(
          LuaIfPostfixTemplate(this), LuaNotPostfixTemplate(this), LuaVarPostfixTemplate(this),
          LuaForPostfixTemplate(this), LuaForPairsPostfixTemplate(this), LuaForIpairsPostfixTemplate(this),
          LuaIfNotPostfixTemplate(this), LuaNilPostfixTemplate(this), LuaNotNilPostfixTemplate(this),
          LuaReturnPostfixTemplate(this), LuaPrintPostfixTemplate(this),
      )
      override fun getTemplates(): Set<PostfixTemplate> = templates
      override fun isTerminalSymbol(currentChar: Char): Boolean = currentChar == '.' || currentChar == '!'
      override fun preExpand(file: PsiFile, editor: Editor) {}
      override fun afterExpand(file: PsiFile, editor: Editor) {}
      override fun preCheck(copyFile: PsiFile, realEditor: Editor, currentOffset: Int): PsiFile = copyFile
  }
  ```

### 2.2 `net.internetisalie.lunar.lang.completion.postfix.LuaExprSelector` (extract — shared)
- **Responsibility**: select the `LuaExpr` ancestors of the caret position, outermost-first.
- **Threading**: EDT, called by the platform during template applicability check.
- **Source**: the `Selector` inner class currently private in `LuaIfPostfixTemplate.kt:26`.
  Promote it to a top-level `internal class LuaExprSelector` in its own file and have every
  template (including `LuaIfPostfixTemplate`) pass `LuaExprSelector()` as the selector arg.
- **Key API** (unchanged behavior; see §3.1 for the algorithm):
  ```kotlin
  internal class LuaExprSelector :
      PostfixTemplateExpressionSelectorBase(Condition { it is LuaExpr }) {
      override fun getExpressions(context: PsiElement, document: Document, offset: Int): List<PsiElement>
      override fun getNonFilteredExpressions(context: PsiElement, document: Document, offset: Int): List<PsiElement>
  }
  ```

### 2.3 The string-template family (COMP-06-01, 02, 04–11)
Every one of these is a `StringBasedPostfixTemplate(presentableName, example, LuaExprSelector(), provider)`
subclass with the same two overrides — only the `getTemplateString` body differs:

```kotlin
class LuaForPostfixTemplate(provider: PostfixTemplateProvider? = null) : StringBasedPostfixTemplate(
    "for", "for i = 1, expr do ... end", LuaExprSelector(), provider,
) {
    override fun getTemplateString(element: PsiElement): String = "for i = 1, \$expr\$ do\n    \$END\$\nend"
    override fun getElementToRemove(expr: PsiElement): PsiElement = expr
}
```

`getElementToRemove` returns `expr` (the matched `LuaExpr`) for **all** of them — the platform
deletes that range and the template's `$expr$` variable is bound to its text. The template
strings (using platform variables `$expr$` for the captured expression text and `$END$` for the
final caret) are:

| Trigger | ID | `getTemplateString` (Kotlin string literal) | Base presentable / example | Source |
|---|---|---|---|---|
| `.if`     | 01 | `"if \$expr\$ then\n    \$END\$\nend"`             | `if`, `if expr then ... end`         | EmmyLua `LuaIfPostfixTemplate` |
| `.not`    | 02 | `"not \$expr\$\$END\$"`                            | `not`, `not expr`                    | EmmyLua `not` |
| `.for`    | 04 | `"for i = 1, \$expr\$ do\n    \$END\$\nend"`       | `for`, `for i = 1, expr do ... end`  | EmmyLua `LuaForAPostfixTemplate` |
| `.forp`   | 05 | `"for k, v in pairs(\$expr\$) do\n    \$END\$\nend"`| `forp`, `for k, v in pairs(expr) ...`| EmmyLua `LuaForPPostfixTemplate` |
| `.fori`   | 06 | `"for i, v in ipairs(\$expr\$) do\n    \$END\$\nend"`| `fori`, `for i, v in ipairs(expr) ..`| EmmyLua `LuaForIPostfixTemplate` |
| `.ifnot`  | 07 | `"if not \$expr\$ then\n    \$END\$\nend"`         | `ifnot`, `if not expr then ... end`  | EmmyLua `LuaIfNotPostfixTemplate` |
| `.nil`    | 08 | `"if \$expr\$ == nil then\n    \$END\$\nend"`      | `nil`, `if expr == nil then ... end` | EmmyLua `LuaCheckNilPostfixTemplate` |
| `.notnil` | 09 | `"if \$expr\$ ~= nil then\n    \$END\$\nend"`      | `notnil`, `if expr ~= nil then ...`  | EmmyLua `LuaCheckIfNotNilPostfixTemplate` |
| `.return` | 10 | `"return \$expr\$\$END\$"`                         | `return`, `return expr`              | EmmyLua `LuaReturnPostfixTemplate` |
| `.print`  | 11 | `"print(\$expr\$)\$END\$"`                         | `print`, `print(expr)`               | EmmyLua `LuaPrintPostfixTemplate` |

Notes:
- `\$expr\$` is the literal four-character platform variable `$expr$` after Kotlin `$` escaping;
  do not interpolate it. `StringBasedPostfixTemplate` auto-registers a variable named `expr` and
  binds it to the removed element's text (this is the existing `.if` mechanism — see
  `LuaIfPostfixTemplate.kt:18`).
- The inline forms (`.not`, `.return`, `.print`) place `$END$` immediately after the rewritten
  expression so the caret lands at end-of-line. The block forms place `$END$` on the body line.
- Body indentation is applied by the formatter in the real IDE; the headless template harness
  (`setTemplateTesting`) does not reformat, so assertions in tests show the body line unindented
  (this is the documented `.if` behavior — see `LuaPostfixTemplateTest.kt:18`).

### 2.4 `net.internetisalie.lunar.lang.completion.postfix.LuaVarPostfixTemplate` (COMP-06-03)
- **Responsibility**: rewrite `<expr>.var` → `local <name> = <expr>` with `<name>` an editable
  tab stop.
- **Decision**: implement as a `StringBasedPostfixTemplate` emitting an editable `$name$`
  variable — **not** by delegating to `LuaIntroduceVariableHandler`. Rationale in §9; the
  refactoring handler is driven by `editor`/selection state and runs its own write command +
  inline-rename `TemplateBuilderImpl`, which is awkward to invoke from inside the platform's
  postfix-template expansion (the template framework already owns the editor template session).
  The string form reuses the exact same plumbing as the other ten templates.
- **Base**: `StringBasedPostfixTemplate("var", "local name = expr", LuaExprSelector(), provider)`.
- **Key API**:
  ```kotlin
  override fun getTemplateString(element: PsiElement): String = "local \$name\$ = \$expr\$\$END\$"
  override fun getElementToRemove(expr: PsiElement): PsiElement = expr
  override fun setVariables(template: Template, element: PsiElement) {
      super.setVariables(template, element)
      // Editable name tab stop seeded from the expression; user can Tab to accept/edit.
      template.addVariable("name", TextExpression("value"), TextExpression("value"), true)
  }
  ```
  `template` is `com.intellij.codeInsight.template.Template`; `TextExpression` is
  `com.intellij.codeInsight.template.impl.TextExpression`. `setVariables` is the protected hook
  on `StringBasedPostfixTemplate` for registering non-`expr` variables. The first occurrence of
  `$name$` in the template string becomes the active tab stop; `$expr$` is filled by the base.
- **Name seed**: a literal `"value"` placeholder is sufficient for the Must bar (the user edits
  it). Smarter seeding (callee/property name like `LuaIntroduceVariableHandler.baseNameFor`,
  `refactoring/LuaIntroduceVariableHandler.kt:144`) is deferred — see risks-and-gaps Gap 2.1.

## 3. Algorithms

### 3.1 Expression selection (`LuaExprSelector`, shared by all templates)
- **Input → Output**: `(context: PsiElement, document: Document, offset: Int)` →
  `List<PsiElement>` of candidate `LuaExpr` ancestors, **outermost-first**.
- **Steps** (verbatim from the built `Selector.getExpressions`, `LuaIfPostfixTemplate.kt:27`):
  1. `current = PsiTreeUtil.getNonStrictParentOfType(context, LuaExpr::class.java)`.
  2. While `current != null`: append `current`; then
     `current = PsiTreeUtil.getParentOfType(current, LuaExpr::class.java)`.
  3. Return the accumulated list `.asReversed()` so the outermost `LuaExpr` is first.
  4. `getNonFilteredExpressions` delegates to the same routine.
- **Rule / why**: the platform applies the first candidate. Outermost-first makes `x > 5.for`
  capture the whole boolean `x > 5`, not the operand `5`. Identical reasoning applies to every
  template — none want the innermost operand.
- **Edge handling**: no enclosing `LuaExpr` → empty list → template does not apply (the platform
  moves on / inserts a literal).

### 3.2 Variable binding in `StringBasedPostfixTemplate`
- `$expr$` is bound automatically: the base class registers a variable `expr` whose value is the
  text of `getElementToRemove(...)` (the matched `LuaExpr`). No override needed beyond
  `getTemplateString`/`getElementToRemove` for the §2.3 family.
- `.var` additionally registers `name` via `setVariables` (§2.4); ordering of tab stops follows
  first-appearance in the template string (`$name$` before `$END$`).

## 4. External Data & Parsing
None — this feature consumes no CLI/file/network input; it operates purely on PSI and the
template framework.

## 5. Data Flow

### Example 1: `.for` on a name (COMP-06-04, TC 4)
`count.for` 〈Tab〉 → `LuaExprSelector` returns `[count]` → platform applies `count`,
`getElementToRemove` removes the `count` `LuaExpr` and binds `$expr$ = "count"` →
`getTemplateString` emits `for i = 1, $expr$ do\n    $END$\nend` → result
`for i = 1, count do\n    <caret>\nend`.

### Example 2: `.forp` on a name (COMP-06-05, TC 5)
`tbl.forp` 〈Tab〉 → `for k, v in pairs(tbl) do\n    <caret>\nend`.

### Example 3: `.var` (COMP-06-03, TC 3)
`getUser().var` 〈Tab〉 → selector returns `[getUser()]` → `$expr$ = "getUser()"`, `$name$`
seeded `"value"` as an editable tab stop → result `local value = getUser()` with `value`
selected for inline edit; the user types a name and Tab → caret to `$END$`.

### Example 4: `.not` (COMP-06-02, TC 2)
`ready.not` 〈Tab〉 → `not ready<caret>`.

## 6. Edge Cases
- Caret not after a `LuaExpr` → no candidates → no expansion (§3.1 edge handling).
- Terminal symbols: `isTerminalSymbol` returns true for `.` and `!`, so the platform treats both
  as postfix triggers (unchanged from the built provider).
- `.not` vs `.ifnot`: `.not` produces the bare expression `not expr` (usable mid-expression);
  `.ifnot` produces a full `if not expr then … end` guard. Distinct triggers, distinct templates.
- `.nil` / `.notnil` are the Lua analogs of the platform's class-language `.null` / `.notnull`;
  they are explicit `== nil` / `~= nil` comparisons (Lua has no implicit nullness).
- Multi-return truncation: none of the shipped templates wrap a call in parentheses, so no
  multi-return is silently truncated. (`.par`, which would, is parked in the backlog.)

## 7. Integration Points
No new registration is required: all eleven templates are returned by the already-registered
provider. The existing block stands:
```xml
<!-- plugin.xml:189 (extensions defaultExtensionNs="com.intellij") -->
<codeInsight.template.postfixTemplateProvider
    language="Lua"
    implementationClass="net.internetisalie.lunar.lang.completion.postfix.LuaPostfixTemplateProvider"/>
```
Each new template class is wired solely by adding it to `LuaPostfixTemplateProvider.templates`
(§2.1).

## 8. Requirement Coverage

| Requirement | Priority | Implemented by (section) | Status |
|-------------|----------|--------------------------|--------|
| COMP-06-01 `.if`     | M | §2.3 table, §3.1 | **Built** (`LuaIfPostfixTemplate.kt`); retarget to `LuaExprSelector` |
| COMP-06-02 `.not`    | M | §2.3 table, §3.1 | To build |
| COMP-06-03 `.var`    | M | §2.4, §3.1, §3.2 | To build |
| COMP-06-04 `.for`    | M | §2.3 table, §3.1 | To build |
| COMP-06-05 `.forp`   | M | §2.3 table, §3.1 | To build |
| COMP-06-06 `.fori`   | M | §2.3 table, §3.1 | To build |
| COMP-06-07 `.ifnot`  | S | §2.3 table, §3.1 | To build |
| COMP-06-08 `.nil`    | S | §2.3 table, §3.1 | To build |
| COMP-06-09 `.notnil` | S | §2.3 table, §3.1 | To build |
| COMP-06-10 `.return` | S | §2.3 table, §3.1 | To build |
| COMP-06-11 `.print`  | S | §2.3 table, §3.1 | To build |

(Backlog items `.par`/`.tostring`/`.tonumber`/`.inc`/`.dec`/`.while`/`.assert` are out of scope
for this design — see requirements.md Backlog.)

## 9. Alternatives Considered
- **One multi-template class vs. one class per template**: the platform's
  `StringBasedPostfixTemplate` is one-template-per-class; per-class matches the framework and
  keeps each selector/template-string trivial. Chosen: per-class.
- **`.var` via `LuaIntroduceVariableHandler` vs. a string template**: the refactoring handler is
  the "richer" path (smart name suggestion, occurrence replacement) but is driven by editor
  selection/caret state and runs its own `WriteCommandAction` + inline-rename session, which
  collides with the postfix framework's own template session and is hard to invoke cleanly from
  `afterExpand`/expansion. A `StringBasedPostfixTemplate` with an editable `$name$` tab stop
  (§2.4) meets the requirement (`local name = expr`, editable name) with the same plumbing as the
  other ten templates. Chosen: string template. Smart name seeding via the handler's
  `baseNameFor` logic is deferred (risks-and-gaps Gap 2.1).
- **Shared selector extraction vs. duplicating the inner class**: extracting to
  `internal LuaExprSelector` avoids ten copies of identical traversal code. Chosen: extract.

## 10. Open Questions

_None — feature has cleared the planning bar._
