---
id: INTENT-02-DESIGN
title: Invert If Design
type: design
parent_id: INTENT-02
status: planned
---

# Technical Design: Invert If

## 1. Architecture Overview

- **Intention class**: `net.internetisalie.lunar.lang.insight.LuaInvertIfIntention`
- **Base class**: `com.intellij.codeInsight.intention.impl.BaseIntentionAction`
- **Helper (condition inversion)**: `net.internetisalie.lunar.lang.insight.LuaConditionInverter`
  (a top-level object in the same package)

### Convention choice (design fork — resolved)

This repo's existing intention, `LuaGenerateDocIntention`, lives in package
`net.internetisalie.lunar.lang.insight` and extends
`com.intellij.codeInsight.intention.impl.BaseIntentionAction`
(`src/main/kotlin/net/internetisalie/lunar/lang/insight/LuaGenerateDocIntention.kt:1-11`).
The skeleton proposed a *new* `lang.intentions` package + `PsiElementBaseIntentionAction`.

**Decision:** follow the existing convention — package `lang.insight`, base class
`BaseIntentionAction`. Rationale: one intention package, consistent with the only existing
intention, no new package to register/justify. `PsiElementBaseIntentionAction` would force an
`isAvailable(project, editor, element)` shape and is not used anywhere in this codebase. The
class name and the `<className>` in plugin.xml (§5) both use `lang.insight.LuaInvertIfIntention`.

### `BaseIntentionAction` method signatures

```kotlin
package net.internetisalie.lunar.lang.insight

class LuaInvertIfIntention : BaseIntentionAction() {
    override fun getFamilyName(): String = "Lua"
    override fun getText(): String = "Invert 'if' statement"

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean
    override fun invoke(project: Project, editor: Editor, file: PsiFile)
}
```

(`getFamilyName` must equal the directory name under `intentionDescriptions/` only when that
directory is named after the family; IntelliJ resolves the description directory by the
**simple class name** `LuaInvertIfIntention` — see §6.)

## 2. PSI Model (grep-verified against this repo)

| Element | FQ type | Source (file:line) |
|---|---|---|
| `if` statement | `net.internetisalie.lunar.lang.psi.LuaIfStatement` | `src/main/gen/net/internetisalie/lunar/lang/psi/LuaIfStatement.java:8` |
| — implements marker | `net.internetisalie.lunar.lang.psi.LuaBlockParent` | `LuaIfStatement.java:8`; iface at `src/main/kotlin/net/internetisalie/lunar/lang/psi/LuaBaseElements.kt:178` |
| — branch blocks | `getBlockList(): List<LuaBlock>` | `LuaIfStatement.java:11`; `LuaBaseElements.kt:179` |
| — condition expressions | `getExprList(): List<LuaExpr>` | `LuaIfStatement.java:14` |
| block | `net.internetisalie.lunar.lang.psi.LuaBlock` | `src/main/gen/.../psi/LuaBlock.java:10` (`getStatementList(): List<LuaStatement>` at :13) |
| binary expr | `net.internetisalie.lunar.lang.psi.LuaBinOpExpr` | `src/main/gen/.../psi/LuaBinOpExpr.java:8` |
| — operator child | `getBinOp(): LuaBinOp` | `LuaBinOpExpr.java:11` |
| — operands | `getLeft(): LuaExpr` / `getRight(): LuaExpr?` | `LuaBinOpExpr.java:17,20` |
| binary operator | `net.internetisalie.lunar.lang.psi.LuaBinOp` (bare `PsiElement`) | `src/main/gen/.../psi/LuaBinOp.java:8` |
| unary expr | `net.internetisalie.lunar.lang.psi.LuaUnOpExpr` | `src/main/gen/.../psi/LuaUnOpExpr.java:8` (`getExpr(): LuaExpr?` at :11, `getUnOp(): LuaUnOp` at :14) |
| unary operator | `net.internetisalie.lunar.lang.psi.LuaUnOp` (bare `PsiElement`) | `src/main/gen/.../psi/LuaUnOp.java:8` |

Grammar (`src/main/kotlin/net/internetisalie/lunar/lang/psi/lua.bnf:133`):
```
ifStatement ::= IF expr THEN block {ELSEIF expr THEN block}* [ELSE block] END
```
So for a simple `if/else`: `getExprList()` has **one** condition `LuaExpr` (the first/only
`expr`), and `getBlockList()` has **two** `LuaBlock`s — index 0 is the `then` body, index 1 is
the `else` body. An `elseif` adds another `expr` to `getExprList()` and another `LuaBlock` to
`getBlockList()`.

### Operator element-type constants (grep-verified)

All in `src/main/gen/net/internetisalie/lunar/lang/psi/LuaElementTypes.java`:

| Operator | Constant | Source line |
|---|---|---|
| `==` | `LuaElementTypes.EQ` | `:79` |
| `~=` | `LuaElementTypes.NE` | `:103` |
| `<` | `LuaElementTypes.LT` | `:98` |
| `<=` | `LuaElementTypes.LE` | `:94` |
| `>` | `LuaElementTypes.GT` | `:87` |
| `>=` | `LuaElementTypes.GE` | `:84` |
| `and` | `LuaElementTypes.AND` | `:64` |
| `or` | `LuaElementTypes.OR` | `:108` |
| `not` | `LuaElementTypes.NOT` | `:106` |
| `elseif` | `LuaElementTypes.ELSEIF` | `:77` |
| `else` | `LuaElementTypes.ELSE` | `:76` |
| `then` | `LuaElementTypes.THEN` | `:120` |
| `if` | `LuaElementTypes.IF` | `:89` |
| `end` | `LuaElementTypes.END` | `:78` |

> NOTE: the not-equal token constant is `NE` (text `~=`), **not** `NEQ`.

## 3. Condition Inversion Algorithm (`LuaConditionInverter`)

`LuaConditionInverter.invertedText(condition: LuaExpr): String` returns the source text of
the negated condition (used by `invoke` to build a replacement `LuaExpr` via
`LuaElementFactory.createExpression`). Dispatch on the runtime PSI type of `condition`:

### 3.1 Relational `LuaBinOpExpr`

If `condition is LuaBinOpExpr` **and** `getBinOp()`'s child node element type is one of the
six relational tokens below, produce `"<leftText> <flippedOp> <rightText>"` where `leftText`
= `getLeft().text`, `rightText` = `getRight()!!.text` (a relational binop always has a right
operand). The flipped-operator table:

| Source op | Element type | Flipped op | Element type |
|---|---|---|---|
| `==` | `EQ` | `~=` | `NE` |
| `~=` | `NE` | `==` | `EQ` |
| `<`  | `LT` | `>=` | `GE` |
| `<=` | `LE` | `>`  | `GT` |
| `>`  | `GT` | `<=` | `LE` |
| `>=` | `GE` | `<`  | `LT` |

The operator's element type is read via
`condition.binOp.firstChild?.node?.elementType` (the `LuaBinOp` wraps a single leaf token;
compare against `LuaElementTypes.EQ` etc.). If the binop's operator is **not** one of these
six (e.g. `and`, `or`, `+`, `..`), fall through to §3.3 (wrap).

### 3.2 Unary `not X` → `X`

If `condition is LuaUnOpExpr` **and** `condition.unOp.firstChild?.node?.elementType == LuaElementTypes.NOT`,
return `condition.expr!!.text` (unwrap the negation; the operand becomes the new condition).
A unary `-` / `#` (`LuaUnOp` other than `not`) does **not** match and falls through to §3.3.

### 3.3 Fallback — wrap as `not ( … )`

For anything else — a bare name (`LuaNameExpr`/identifier), a function call
(`LuaFuncCall`/`LuaCallExpr`), a literal, or an `and`/`or` `LuaBinOpExpr` — return
`"not (" + condition.text + ")"`. The parentheses are mandatory because `not` binds tighter
than `and`/`or` in Lua; `not a and b` would mean `(not a) and b`.

> **Out of scope (documented deliberately):** De Morgan distribution is NOT performed. The
> implementer must NOT rewrite `a and b` to `not a or not b`. `and`/`or` chains take the
> §3.3 wrap path. See [risks-and-gaps.md](risks-and-gaps.md) Gap 2.1.

### 3.4 Double-negation note (acceptable)

Inverting a wrapped condition twice yields `not (not (X))` rather than `X`. Cleanup of nested
`not (not …)` is out of scope for INTENT-02 (the intention is its own inverse only up to this
textual nesting). Documented in risks; not gated.

## 4. Swap + Replacement Algorithm (`invoke`)

**Approach chosen (design fork — resolved): rebuild the statement via `LuaElementFactory` +
single PSI `replace`, NOT piecemeal document edits.** Rationale: a whole-statement rebuild
keeps the operation atomic, lets the PSI re-indent the result, and avoids fragile offset
math. `LuaElementFactory.createExpression` /`createFile` already exist
(`src/main/kotlin/net/internetisalie/lunar/lang/psi/LuaElementFactory.kt:32,41`).

Steps inside `WriteCommandAction.runWriteCommandAction(project) { … }`:

1. Resolve the target: `val ifStmt = PsiTreeUtil.getParentOfType(file.findElementAt(editor.caretModel.offset), LuaIfStatement::class.java) ?: return`.
2. `val condition = ifStmt.exprList.firstOrNull() ?: return`.
3. `val blocks = ifStmt.blockList` — `val thenBlock = blocks[0]`, `val elseBlock = blocks[1]`.
4. `val newCondText = LuaConditionInverter.invertedText(condition)`.
5. Capture the **body text** of each block, trimmed of surrounding block whitespace but
   keeping inner statement formatting: `val thenBody = thenBlock.text` and
   `val elseBody = elseBlock.text` (block text is the run of statements between `then`/`else`
   and `else`/`end`; it already excludes the keywords).
6. Build replacement source text:
   ```
   if <newCondText> then
   <elseBody>
   else
   <thenBody>
   end
   ```
   where `<elseBody>` / `<thenBody>` are spliced verbatim (they carry their own leading
   newline + indentation from the original block text).
7. `val dummyFile = LuaElementFactory.createFile(project, replacementText)`;
   `val newIf = PsiTreeUtil.findChildOfType(dummyFile, LuaIfStatement::class.java) ?: return`.
8. `ifStmt.replace(newIf)`.
9. Run `CodeStyleManager.getInstance(project).reformat(replaced)` on the inserted node to
   normalise indentation (the spliced bodies keep their relative indentation; reformat fixes
   the outer indentation to match the insertion site).

> Comment preservation: because block bodies are spliced as raw text (step 5), inline and
> full-line comments inside each branch are carried over verbatim. The only formatting risk is
> outer-indentation drift, mitigated by the reformat in step 9 (see risks Risk 1.1).

## 5. Applicability (`isAvailable`)

Return `true` only when **all** hold:
1. `file is LuaFile`.
2. There is a `LuaIfStatement` ancestor of `file.findElementAt(editor.caretModel.offset)`
   (via `PsiTreeUtil.getParentOfType(element, LuaIfStatement::class.java)`).
3. `ifStmt.exprList.size == 1` — exactly one condition (more than one means `elseif`
   branches exist, since each `elseif` contributes an `expr`).
4. `ifStmt.blockList.size == 2` — exactly a `then` body and an `else` body.
5. The statement actually has an `else` keyword: assert presence of an `ELSE` leaf among the
   statement's children (`ifStmt.node.findChildByType(LuaElementTypes.ELSE) != null`). With
   `blockList.size == 2` and `exprList.size == 1` this is implied, but checking the `ELSE`
   leaf guards against any odd recovery tree.
6. No `ELSEIF`: `ifStmt.node.findChildByType(LuaElementTypes.ELSEIF) == null`. (Redundant with
   `exprList.size == 1` but explicit and cheap.)

All PSI reads occur on the EDT inside `isAvailable`; they are pure tree reads (no I/O), which
is permitted for intention availability.

## 6. plugin.xml + Resource Files

### Registration (mirrors `plugin.xml:355-358`)

```xml
<intentionAction>
  <className>net.internetisalie.lunar.lang.insight.LuaInvertIfIntention</className>
  <category>Lua</category>
</intentionAction>
```

### Required description resources

IntelliJ locates these by the **simple class name** `LuaInvertIfIntention`. Mirroring the
on-disk layout of `LuaGenerateDocIntention`
(`src/main/resources/intentionDescriptions/LuaGenerateDocIntention/{description.html,
before.template.lua,after.template.lua}` — verified present), create:

- `src/main/resources/intentionDescriptions/LuaInvertIfIntention/description.html`
  ```html
  <html>
  <body>
  Inverts an <code>if … then … else … end</code> statement: negates the condition and swaps
  the <code>then</code> and <code>else</code> branch bodies, preserving behaviour. Offered
  only when the statement has an <code>else</code> branch and no <code>elseif</code>.
  </body>
  </html>
  ```
- `src/main/resources/intentionDescriptions/LuaInvertIfIntention/before.template.lua`
  ```lua
  if x == 1 then
      foo()
  else
      bar()
  end
  ```
- `src/main/resources/intentionDescriptions/LuaInvertIfIntention/after.template.lua`
  ```lua
  if x ~= 1 then
      bar()
  else
      foo()
  end
  ```

> Note the suffix order is `before.template.lua` / `after.template.lua` (verified on disk for
> `LuaGenerateDocIntention`).

## 7. Prior Art

- **No existing invert-if implementation** in the repo (grep for `Invert`/`InvertIf` across
  `src/main` returns nothing). This is a greenfield intention.
- **Reusable patterns:** `LuaGenerateDocIntention`
  (`src/main/kotlin/net/internetisalie/lunar/lang/insight/LuaGenerateDocIntention.kt`) is the
  template for class shape, `getFamilyName() == "Lua"`, `isAvailable` caret-resolution
  (`file.findElementAt(editor.caretModel.offset)` + `PsiTreeUtil.getParentOfType`), and the
  `intentionDescriptions/<Class>/` resource layout.
- **PSI construction helper:** `LuaElementFactory.createFile` / `createExpression`
  (`LuaElementFactory.kt:41,32`) — reuse for the statement rebuild (§4).
- **No existing condition/operator-inversion helper** exists to reuse (grep for
  `Inverter`/`negate`/`invertCondition` across `src/main` returns nothing); `LuaConditionInverter`
  is new. `LuaBinOp`/`LuaUnOp` are opaque `PsiElement`s with no helper for reading the operator
  token, so the inverter reads the operator leaf's element type directly (§3).

## 8. Open Questions

None.

<!-- The two design forks — package/base-class convention and rebuild-vs-document-edit — are
     resolved in §1 and §4 respectively; the De Morgan and double-negation scope calls are
     recorded in risks-and-gaps.md (Gap 2.1, Gap 2.2). -->


## See Also
- Requirements: [requirements.md](requirements.md)
- Implementation plan: [implementation-plan.md](implementation-plan.md)
- Risks & gaps: [risks-and-gaps.md](risks-and-gaps.md)
