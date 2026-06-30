---
id: REFACT-06-DESIGN
title: Create from Usage Design
type: design
parent_id: REFACT-06
priority: medium
folders:
  - "[[features/refactoring/06-stubs-for-declaring/requirements|requirements]]"
---

# Technical Design: Create from Usage (REFACT-06)

## 1. Architecture Overview

Two standalone intention actions, placed in the package that already hosts the project's only
intention (`net.internetisalie.lunar.lang.insight`, home of
`LuaGenerateDocIntention`):

- `net.internetisalie.lunar.lang.insight.LuaCreateLocalVariableIntention`
- `net.internetisalie.lunar.lang.insight.LuaCreateFunctionIntention`

**Base class:** `com.intellij.codeInsight.intention.impl.BaseIntentionAction`, matching
`LuaGenerateDocIntention`
(`src/main/kotlin/net/internetisalie/lunar/lang/insight/LuaGenerateDocIntention.kt:11`).
`BaseIntentionAction` is preferred over `PsiElementBaseIntentionAction` here because the actions
need `editor`/`file` directly (caret-offset lookup + write via `WriteCommandAction`), exactly as
the existing intention does. Both override:

```kotlin
override fun getFamilyName(): String = "Lua"
override fun getText(): String         // dynamic: "Create local variable 'x'" / "Create function 'myFunc'"
override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean
override fun invoke(project: Project, editor: Editor, file: PsiFile)  // throws IncorrectOperationException allowed
```

`getText()` is computed from the resolved name; store the name/arg-count on a nullable field set
during the last `isAvailable` (the platform calls `isAvailable` before showing `getText`), or
recompute in `getText()` from the caret — recomputing is simplest and matches the stateless style
of `LuaGenerateDocIntention`.

## 2. Grounded PSI Reference

All names below were verified in this repo (generated PSI under
`src/main/gen/net/internetisalie/lunar/lang/psi/`). The skeleton's `LuaCallExpr` and
`LuaNameReference`-as-PSI were **wrong**; corrections are tracked in §6.

| Concept | Type / accessor | Cite |
|---|---|---|
| Function call | `LuaFuncCall` (NOT `LuaCallExpr`) | `LuaFuncCall.java:8` |
| Call callee | `LuaFuncCall.getVarOrExp(): LuaVarOrExp` | `LuaFuncCall.java:14` |
| Call args (list) | `LuaFuncCall.getNameAndArgsList(): List<LuaNameAndArgs>` | `LuaFuncCall.java:11` |
| Args node | `LuaNameAndArgs.getArgs(): LuaArgs` | `LuaNameAndArgs.java:11` |
| Positional args | `LuaArgs.getExprList(): LuaExprList?` → `LuaExprList.getExprList(): List<LuaExpr>` | `LuaArgs.java:11`, `LuaExprList.java:11` |
| Args grammar | `args ::= '(' [exprList] ')' \| tableConstructor \| STRING` | `lua.bnf:253` |
| Name reference (PSI element) | `LuaNameRef` (the identifier node); `LuaNameRef.getIdentifier(): PsiElement` | `LuaNameRef.java:8`, `:11` |
| Name reference (`PsiReference`) | `LuaNameRef.getReference()` → resolved via `LuaNameReference` | inspection use at `LuaUndeclaredVariableInspection.kt:54` |
| Assignment statement | `LuaAssignmentStatement` (NOT `LuaAssignStatement`) | `LuaAssignmentStatement.java:8` |
| Assignment LHS list | `LuaAssignmentStatement.getVarList(): LuaVarList` → `LuaVarList.getVarList(): List<LuaVar>` | `LuaAssignmentStatement.java:14`, `LuaVarList.java:11` |
| Var → name | `LuaVar.getNameRef(): LuaNameRef?`, `LuaVar.getVarSuffixList(): List<LuaVarSuffix>` | `LuaVar.java:14`, `:17` |
| `varOrExp` → var | `LuaVarOrExp.getVar(): LuaVar?` | `LuaVarOrExp.java:11` |
| Statement / block anchors | `LuaStatement`, `LuaBlock` | used at `LuaIntroduceVariableHandler.kt:72`, `:83` |
| Element factory | `LuaElementFactory.createFile` / `createExpression` / `createNewLine` | `LuaElementFactory.kt:41`, `:32`, `:37` |

**Note on `LuaNameReference` vs `LuaNameRef`.** `LuaNameRef` is the *PSI element*; its
`getReference()` returns the `PsiReference` (a `LuaNameReference`, a `PsiPolyVariantReference`).
Use the **element** (`LuaNameRef`) to read text/identify position, and the **reference**
(`ref.reference as? PsiPolyVariantReference`) to test resolution — exactly the split the
inspection makes at `LuaUndeclaredVariableInspection.kt:49–56`.

## 3. Shared "is this name undeclared?" helper

To avoid divergence from the inspection, factor its resolution+exemption logic into a small shared
object (e.g. `LuaUndeclaredNames`) and have **both** the inspection and the two intentions call it.
The inspection's current logic (`LuaUndeclaredVariableInspection.kt`):

- `name == "_"` → not flagged (`:51`).
- `isExemptGlobal(ref, name)` → standard global for the project language level
  (`LuaStandardGlobals.contains`, `:67`), allowlisted (`settings.state.additionalGlobals`, `:69`),
  or underscore-suppressed (`:70`).
- `ref.reference as? PsiPolyVariantReference` then `multiResolve(false).isNotEmpty()` → declared,
  not flagged (`:54–55`).

Extract this into:

```kotlin
object LuaUndeclaredNames {
    /** True iff [ref]'s name resolves to nothing and is not an exempt global. */
    fun isUnresolvedNonGlobal(ref: LuaNameRef): Boolean
}
```

The inspection keeps its `isReadUse` + `LuaInspectionSuppression` gate and delegates the
resolve/exempt decision to `LuaUndeclaredNames`; the intentions call `isUnresolvedNonGlobal`
directly. This is the de-risking subject of REFACT-06-00-DR-01 (see risks doc). If extraction is
deferred, the intentions MUST replicate the **same three checks** verbatim and a test must assert
parity — but extraction is the chosen design.

## 4. Algorithm — REFACT-06-01 Create Local Variable

### 4.1 `isAvailable`
1. `file as? LuaFile ?: return false`.
2. `leaf = file.findElementAt(caretOffset)`; `ref = PsiTreeUtil.getParentOfType(leaf, LuaNameRef::class.java) ?: return false`.
3. **Write-target gate** (mirrors the inspection's `isSimpleWriteTarget`,
   `LuaUndeclaredVariableInspection.kt:94`): the `LuaNameRef`'s parent is a `LuaVar` with
   `varSuffixList.isEmpty()` and that `LuaVar`'s parent is a `LuaVarList`. This is precisely the
   `x = …` case and excludes reads (TC2) and member writes (`a.b = …`).
4. **Undeclared gate:** `LuaUndeclaredNames.isUnresolvedNonGlobal(ref)` — false if it resolves to
   an existing `local` (TC3) or is an exempt global.
5. Capture `name = ref.identifier.text` for `getText()` → `"Create local variable '$name'"`.

### 4.2 `invoke`
Locate the enclosing `LuaAssignmentStatement`:
`PsiTreeUtil.getParentOfType(ref, LuaAssignmentStatement::class.java)`. Rebuild it as a `local`
declaration, reusing `LuaElementFactory` + the introduce-variable insertion idiom
(`LuaIntroduceVariableHandler.kt:106–119`):

```kotlin
WriteCommandAction.runWriteCommandAction(project, getText(), null, {
    val assignment = PsiTreeUtil.getParentOfType(ref, LuaAssignmentStatement::class.java) ?: return@…
    val newText = "local " + assignment.text          // "local x = 1"
    val throwaway = LuaElementFactory.createFile(project, newText)
    val decl = PsiTreeUtil.findChildOfType(throwaway, LuaStatement::class.java) ?: return@…
    assignment.replace(decl)
})
```

Prefixing the statement text with `local ` is valid because the assignment grammar
(`varList = exprList`) becomes a well-formed local declaration (`local nameList = exprList`) for
the single-name simple-target case the gate guarantees. For `x = 1` this yields `local x = 1`
(TC1). The action restricts itself to the single simple-target case, so multi-target LHS
(`a, b = 1, 2`) is excluded by the §4.1 write-target gate (only one `LuaVar`, no suffixes).

## 5. Algorithm — REFACT-06-02 Create Function

### 5.1 `isAvailable`
1. `file as? LuaFile ?: return false`.
2. `leaf = file.findElementAt(caretOffset)`;
   `call = PsiTreeUtil.getParentOfType(leaf, LuaFuncCall::class.java) ?: return false`.
3. **Single-name callee gate:** `val varNode = call.varOrExp.var ?: return false`;
   require `varNode.varSuffixList.isEmpty()` and `val callee = varNode.nameRef ?: return false`.
   This rejects `obj.method(…)` and `a.b.c(…)` (callee has suffixes / no bare `nameRef`), TC7.
   Also require the caret's `LuaNameRef` to be `callee` (so the intention fires on the callee, not
   on an argument identifier).
4. **Undeclared gate:** `LuaUndeclaredNames.isUnresolvedNonGlobal(callee)` — false for TC6
   (callee resolves to the existing `local function f`) and for exempt globals.
5. Capture `name = callee.identifier.text` → `getText()` = `"Create function '$name'"`.

### 5.2 Argument count
Count positional args on the **first** `nameAndArgs` segment (the actual call's arg list):

```kotlin
val argCount = call.nameAndArgsList.firstOrNull()
    ?.args?.exprList?.exprList?.size ?: 0
```

- `myFunc(1, 2)` → `LuaArgs.exprList.exprList.size == 2` → params `arg1, arg2` (TC4).
- `f()` → `LuaArgs.exprList == null` → `0` params (TC5).

(Engine caveat from CLAUDE.md: a chained call `a():b()` is one `LuaFuncCall` with multiple
`nameAndArgs`; we deliberately use only the first segment, which is the call whose callee we
matched.)

### 5.3 Insertion point + generation
Find the enclosing **top-level** statement — the `LuaStatement` ancestor whose parent is a
`LuaBlock` (the same anchor pattern as introduce-variable,
`LuaIntroduceVariableHandler.kt:72`, `:83`):

```kotlin
val anchor = PsiTreeUtil.getParentOfType(call, LuaStatement::class.java) ?: return
val block = anchor.parent as? LuaBlock ?: return
val params = (1..argCount).joinToString(", ") { "arg$it" }
val stub = "local function $name($params)\nend"
WriteCommandAction.runWriteCommandAction(project, getText(), null, {
    val throwaway = LuaElementFactory.createFile(project, stub)
    val decl = PsiTreeUtil.findChildOfType(throwaway, LuaStatement::class.java) ?: return@…
    val inserted = block.addBefore(decl, anchor)
    block.addAfter(LuaElementFactory.createNewLine(project), inserted)
})
```

This places `local function myFunc(arg1, arg2)\nend` immediately before the statement containing
the call, with a blank line, producing TC4/TC5 output. Reusing `addBefore` +
`createNewLine` is identical to the introduce-variable insertion at
`LuaIntroduceVariableHandler.kt:113–114`.

## 6. Skeleton Corrections (this design overrides the original skeleton)

| Skeleton said | Correct | Cite |
|---|---|---|
| `LuaCallExpr` | `LuaFuncCall` | `LuaFuncCall.java:8` |
| caret on a "`LuaNameReference`" (as PSI) | caret on a `LuaNameRef` PSI element; resolution uses its `PsiReference` (`LuaNameReference`) | `LuaNameRef.java:8`; `LuaUndeclaredVariableInspection.kt:54` |
| implements `IntentionAction` (bare) | extends `BaseIntentionAction` | `LuaGenerateDocIntention.kt:11` |
| package `lang.intentions` | package `lang.insight` (existing intention home) | `LuaGenerateDocIntention.kt:1` |
| "insert above the current scope" (vague) | insert before the enclosing top-level `LuaStatement` whose parent is `LuaBlock` | `LuaIntroduceVariableHandler.kt:72`, `:83` |

## 7. Registration & Resources

Each intention needs an `<intentionAction>` plus a `intentionDescriptions/<SimpleClassName>/`
resource folder (verified suffix on disk is `.template.lua`, per
`src/main/resources/intentionDescriptions/LuaGenerateDocIntention/`).

`plugin.xml` (insert next to the existing block at `plugin.xml:355`):

```xml
<intentionAction>
  <className>net.internetisalie.lunar.lang.insight.LuaCreateLocalVariableIntention</className>
  <category>Lua</category>
</intentionAction>
<intentionAction>
  <className>net.internetisalie.lunar.lang.insight.LuaCreateFunctionIntention</className>
  <category>Lua</category>
</intentionAction>
```

Resource files to create:

- `src/main/resources/intentionDescriptions/LuaCreateLocalVariableIntention/description.html`
- `src/main/resources/intentionDescriptions/LuaCreateLocalVariableIntention/before.template.lua`
  → `x = 1`
- `src/main/resources/intentionDescriptions/LuaCreateLocalVariableIntention/after.template.lua`
  → `local x = 1`
- `src/main/resources/intentionDescriptions/LuaCreateFunctionIntention/description.html`
- `src/main/resources/intentionDescriptions/LuaCreateFunctionIntention/before.template.lua`
  → `myFunc(1, 2)`
- `src/main/resources/intentionDescriptions/LuaCreateFunctionIntention/after.template.lua`
  → `local function myFunc(arg1, arg2)\nend\n\nmyFunc(1, 2)`

`description.html` follows the existing `<html><body>…</body></html>` form
(`intentionDescriptions/LuaGenerateDocIntention/description.html`).

## 8. Prior Art / Integration — `LuaUndeclaredVariableInspection`

**File:** `src/main/kotlin/net/internetisalie/lunar/analysis/inspections/LuaUndeclaredVariableInspection.kt`.

### 8.1 What it does
A `LocalInspectionTool` that, on **read-position** `LuaNameRef`s only (`isReadUse`,
`:74–82`), flags names that don't resolve and aren't exempt globals, registering a single
quick fix `LuaAddToGlobalsQuickFix(name)` (`:62`). It explicitly does **not** flag write targets,
declarations, member names, or func-name positions (`isReadUse` returns false for those).

### 8.2 Does it already offer a "create local"/"declare" fix?
**No.** Its only `LocalQuickFix` is `LuaAddToGlobalsQuickFix` (adds the name to the allowlisted
globals setting). There is no create-local or create-function fix today, so REFACT-06 adds new
capability rather than duplicating an existing one.

### 8.3 Standalone intentions vs. inspection quick fixes — decision
**REFACT-06 ships as standalone `IntentionAction`s** (as the skeleton intended), not as
`LocalQuickFix`es on the inspection. Rationale:

- **Create local (06-01)** targets a **write** position, which the inspection deliberately does
  **not** highlight (`isReadUse` excludes write targets). A quick fix can only attach to a
  registered problem, so there would be no highlight to hang it on. A standalone intention (Alt+Enter
  anywhere on the name) is the only viable surface.
- **Create function (06-02)** targets a **read** callee, which the inspection *does* highlight — but
  offering it as a separate intention keeps both actions uniform and avoids reworking the
  inspection's fix list. The two never double-offer because they apply to disjoint positions and
  different generated output ("Add to globals" vs "Create function").

### 8.4 No double-offering / no divergence
- Positions are disjoint: 06-01 only on simple write targets, 06-02 only on bare-name callees,
  the inspection's quick fix only on read uses (which for a callee coexists with 06-02 — both are
  legitimate, distinct user choices).
- **Undeclared-ness is shared, not re-implemented:** both the inspection and the intentions call
  `LuaUndeclaredNames.isUnresolvedNonGlobal` (§3). This guarantees a name the inspection considers
  "declared/exempt" is never offered a create-from-usage action, and vice-versa. The extraction is
  gated by REFACT-06-00-DR-01.

## 9. Open Questions

None. (All design gaps are resolved in `risks-and-gaps.md` and folded back here.)
