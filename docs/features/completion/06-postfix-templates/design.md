---
id: COMP-06-DESIGN
title: Postfix Templates Design
type: design
parent_id: COMP-06
status: planned
folders:
  - "[[features/completion/06-postfix-templates/requirements|requirements]]"
---

# Technical Design: COMP-06 — Postfix Templates

Postfix completion lets a user type an expression followed by `.if` (etc.) and have it
rewritten into the corresponding statement, with the expression spliced in. Implemented on the
platform `PostfixTemplateProvider` extension point.

## 1. Architecture Overview

### Current State (what is actually built)
- `net.internetisalie.lunar.lang.completion.postfix.LuaPostfixTemplateProvider`
  (`src/main/kotlin/.../completion/postfix/LuaPostfixTemplateProvider.kt:8`) implements
  `com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider` and returns a
  single template from `getTemplates()`.
- `net.internetisalie.lunar.lang.completion.postfix.LuaIfPostfixTemplate`
  (`LuaIfPostfixTemplate.kt:12`) implements `.if`.
- Registered at `plugin.xml:189`.
- Covered by `src/test/kotlin/.../completion/postfix/LuaPostfixTemplateTest.kt`.

**Gap vs requirements:** `COMP-06-02` (`.not`) is a `Must` but is **NOT implemented** — there is no
`LuaNotPostfixTemplate` and the provider's set holds only `LuaIfPostfixTemplate`. COMP-06 is
therefore **partial**, not done. §2.3 specifies the missing template so it can be built.

### Prior Art in This Repo
Searched `src/main` for `PostfixTemplate*` and `postfix` — the only postfix code is this feature's
own `completion/postfix/` package. No other component generates statement-from-expression
rewrites. This design **extends** the existing `LuaPostfixTemplateProvider` (adds a template);
it does not replace anything.

### Target State
`LuaPostfixTemplateProvider.getTemplates()` returns the full `Must` set — `LuaIfPostfixTemplate`
(built) and `LuaNotPostfixTemplate` (to add) — each a `StringBasedPostfixTemplate` keyed on a
`LuaExpr` selector.

## 2. Core Components

### 2.1 `net.internetisalie.lunar.lang.completion.postfix.LuaPostfixTemplateProvider`
- **Responsibility**: expose Lunar's postfix templates to the platform.
- **Threading**: platform-invoked on the EDT during completion; no I/O.
- **Collaborators**: `LuaIfPostfixTemplate`, `LuaNotPostfixTemplate`.
- **Key API** (as built, `LuaPostfixTemplateProvider.kt`):
  ```kotlin
  class LuaPostfixTemplateProvider : PostfixTemplateProvider {
      override fun getTemplates(): Set<PostfixTemplate>          // {LuaIfPostfixTemplate(), LuaNotPostfixTemplate()}
      override fun isTerminalSymbol(currentChar: Char): Boolean  // '.' || '!'
      override fun preExpand(file: PsiFile, editor: Editor) {}
      override fun afterExpand(file: PsiFile, editor: Editor) {}
      override fun preCheck(copyFile: PsiFile, realEditor: Editor, currentOffset: Int): PsiFile = copyFile
  }
  ```

### 2.2 `net.internetisalie.lunar.lang.completion.postfix.LuaIfPostfixTemplate` (built)
- **Responsibility**: rewrite `<expr>.if` → `if <expr> then … end`.
- **Base**: `com.intellij.codeInsight.template.postfix.templates.StringBasedPostfixTemplate("if", "if expr then ... end", Selector())`.
- **Key API** (`LuaIfPostfixTemplate.kt:18`):
  ```kotlin
  override fun getTemplateString(element: PsiElement): String = "if \$expr\$ then\n    \$END\$\nend"
  override fun getElementToRemove(expr: PsiElement): PsiElement = expr
  ```
- **Selector**: private `Selector : PostfixTemplateExpressionSelectorBase(Condition { it is LuaExpr })`; see §3.1.

### 2.3 `net.internetisalie.lunar.lang.completion.postfix.LuaNotPostfixTemplate` (to add — COMP-06-02)
- **Responsibility**: rewrite `<expr>.not` → `not <expr>`.
- **Base**: `StringBasedPostfixTemplate("not", "not expr", Selector())` — reuse the same `LuaExpr`
  selector strategy as §2.2 (extract `Selector` into a shared `internal` class, or duplicate the
  small inner class).
- **Key API**:
  ```kotlin
  override fun getTemplateString(element: PsiElement): String = "not \$expr\$\$END\$"
  override fun getElementToRemove(expr: PsiElement): PsiElement = expr
  ```
- **Register**: add `LuaNotPostfixTemplate()` to `LuaPostfixTemplateProvider.getTemplates()`.

## 3. Algorithms

### 3.1 Expression selection (which `LuaExpr` the template wraps)
- **Input → Output**: `(context: PsiElement, document, offset)` → `List<PsiElement>` of candidate
  `LuaExpr` ancestors, **outermost-first**.
- **Steps** (as built, `LuaIfPostfixTemplate.kt` `Selector.getExpressions`):
  1. Start at `PsiTreeUtil.getNonStrictParentOfType(context, LuaExpr::class.java)`.
  2. Walk up via `PsiTreeUtil.getParentOfType(current, LuaExpr::class.java)`, collecting each.
  3. Return the list **reversed** (`asReversed()`) so the outermost expression is first.
- **Rule / why**: the platform applies the first candidate. Outermost-first makes `x > 5.if` wrap
  the whole boolean `x > 5`, not just the operand `5`. `getNonFilteredExpressions` returns the
  same list.
- **Edge handling**: no enclosing `LuaExpr` → empty list → template does not apply (Continue).

## 4. External Data & Parsing
None — this feature consumes no CLI/file/text input; it operates purely on PSI.

## 5. Data Flow

### Example 1: `.if` on a comparison
`x > 5.if` 〈invoke〉 → selector returns `[ (x>5), 5 ]` (outermost first) → platform applies `(x>5)`
→ `getTemplateString` emits `if $expr$ then\n    $END$\nend` with `$expr$` = `x > 5` → result
`if x > 5 then\n    <caret>\nend`.

### Example 2: `.not` (after §2.3)
`ready.not` 〈invoke〉 → `not ready<caret>`.

## 6. Edge Cases
- Caret not after a `LuaExpr` → no candidates → no expansion.
- Terminal symbols: `isTerminalSymbol` returns true for `.` and `!` so the platform treats them as
  postfix triggers.

## 7. Integration Points
```xml
<!-- plugin.xml:189 (extensions defaultExtensionNs="com.intellij") -->
<codeInsight.template.postfixTemplateProvider
    language="Lua"
    implementationClass="net.internetisalie.lunar.lang.completion.postfix.LuaPostfixTemplateProvider"/>
```
No new registration needed for `.not` — it is added to the existing provider's template set.

## 8. Requirement Coverage

| Requirement | Priority | Implemented by (section) | Status |
|-------------|----------|--------------------------|--------|
| COMP-06-01 `.if` | M | §2.2, §3.1 | **Built** (`LuaIfPostfixTemplate.kt`) |
| COMP-06-02 `.not` | M | §2.3, §3.1 | **Not yet implemented** — design specifies it |

## 9. Alternatives Considered
- A single multi-template class vs. one class per template: the platform's `StringBasedPostfixTemplate`
  is one-template-per-class; per-class matches the framework and keeps selectors simple.

## 10. Open Questions
_None._ The only outstanding item is implementation of COMP-06-02 (`.not`), which is fully
specified in §2.3 — an implementation task, not an unresolved design decision.
