---
id: "EDITOR-06-DESIGN"
title: "Technical Design"
type: "design"
parent_id: "EDITOR-06"
folders:
  - "[[features/editor/06-unwrap-remove/requirements|requirements]]"
---

# Technical Design: EDITOR-06 — Unwrap / Remove

Implements the `Code | Unwrap/Remove` action (Ctrl+Shift+Delete) for Lua block
constructs via the `com.intellij.lang.unwrapDescriptor` extension point. The platform
supplies the whole UX (option picker, live preview highlight, write-command wrapping,
caret restore) once we provide one `UnwrapDescriptor` and a set of `Unwrapper`
implementations. This is the inverse of EDITOR-05 (Surround With).

## 1. Architecture Overview

### Current State
No `unwrapDescriptor` is registered for Lua. `grep -n "unwrapDescriptor" src/main/resources/META-INF/plugin.xml` → no match. Ctrl+Shift+Delete currently does nothing in a `.lua` file.

### Prior Art in This Repo

Searched `src/main/kotlin` for existing unwrap / block-hoist / if-branch manipulation:

- **`net.internetisalie.lunar.lang.insight.LuaInvertIfIntention`** (`src/main/kotlin/net/internetisalie/lunar/lang/insight/LuaInvertIfIntention.kt:15`) — the closest prior art. It resolves a `LuaIfStatement` from the caret via `PsiTreeUtil.getParentOfType`, reads `getExprList()` / `getBlockList()`, detects the `else`/`elseif` branches via `node.findChildByType(LuaElementTypes.ELSE)` / `LuaElementTypes.ELSEIF`, rebuilds text, and `replace()`s inside a `WriteCommandAction`, then `CodeStyleManager.getInstance(project).reformat(replaced)`. This design **reuses those exact idioms** (branch detection, `LuaElementFactory.createFile` + `PsiTreeUtil.findChildOfType`, reformat) but does **not** modify or replace `LuaInvertIfIntention`; the two are distinct actions.
- **`LuaElementFactory`** (`src/main/kotlin/net/internetisalie/lunar/lang/psi/LuaElementFactory.kt:11`) — the sanctioned PSI-from-text builder (`createFile(project, text): LuaFile`). Reused; **not** extended.
- **`LuaBlockExt.kt`** (`src/main/kotlin/net/internetisalie/lunar/lang/psi/LuaBlockExt.kt`) — `LuaBlock.processDeclarations` scope-walk. Not a structural-edit helper; not reused here.
- No `Surrounder`/`SurroundDescriptor` or block-structure helper exists yet: EDITOR-05 has only `requirements.md` (no `design.md`, no source). `grep -rl "Surround" src/main/kotlin` → only live-template context classes (`LuaSurroundContextType`, unrelated). **Consequence: the EDITOR-05 "shared block-structure PSI helpers" do not exist.** This design therefore ships its own self-contained helper (`LuaBlockStructure`, §2.4) and specifies the contract EDITOR-05 should later converge on (see risks-and-gaps §Gap 2.1). This is a soft dependency, not a blocking edge.

### Target State

A single `LuaUnwrapDescriptor extends UnwrapDescriptorBase` returns an array of five
`Unwrapper`s. Four are "unwrap block" unwrappers (one shared class parameterised by
construct is impractical because applicability differs; see §2.3) and one is the
`else`/`elseif` collapse. Each `Unwrapper` extends a small Lua base
(`LuaUnwrapper extends AbstractUnwrapper<LuaUnwrapper.Context>`) that reuses the
platform's `AbstractContext.extract(...)` machinery to hoist statements and highlight
the affected range. All statement-span / block-locating logic lives in the stateless
`LuaBlockStructure` helper object.

Component sketch:

```
UnwrapHandler (platform, Ctrl+Shift+Delete)
   └─ LuaUnwrapDescriptor : UnwrapDescriptorBase
        └─ createUnwrappers(): Unwrapper[] = {
             LuaBlockUnwrapper(IF), LuaBlockUnwrapper(WHILE),
             LuaBlockUnwrapper(FOR), LuaBlockUnwrapper(DO),
             LuaBlockUnwrapper(FUNCTION),      // §2.3 handles all "unwrap block"
             LuaElseBranchRemover,             // §2.5 collapse else/elseif
             LuaRemoveConstructUnwrapper       // §2.6 remove whole construct
           }
        each extends LuaUnwrapper : AbstractUnwrapper<Context>  // §2.2
                                     └─ uses LuaBlockStructure   // §2.4
```

## 2. Core Components

### 2.1 `net.internetisalie.lunar.lang.insight.unwrap.LuaUnwrapDescriptor`
- **Responsibility**: Register the Lua unwrapper set with the platform; the base class collects applicable unwrappers up the PSI parent chain from the caret.
- **Threading**: Instantiated by the platform; `createUnwrappers()` runs on EDT under the platform's read access. No I/O.
- **Collaborators**: extends `com.intellij.codeInsight.unwrap.UnwrapDescriptorBase` (real: `platform/lang-impl/.../UnwrapDescriptorBase.java`), which implements `collectUnwrappers` / `findTargetElement` / `showOptionsDialog=true` / `shouldTryToRestoreCaretPosition=true`. We override only `createUnwrappers()`.
- **Key API**:
  ```kotlin
  class LuaUnwrapDescriptor : UnwrapDescriptorBase() {
      override fun createUnwrappers(): Array<Unwrapper> = arrayOf(
          LuaBlockUnwrapper(LuaConstruct.IF),
          LuaBlockUnwrapper(LuaConstruct.WHILE),
          LuaBlockUnwrapper(LuaConstruct.FOR),
          LuaBlockUnwrapper(LuaConstruct.DO),
          LuaBlockUnwrapper(LuaConstruct.FUNCTION),
          LuaElseBranchRemover(),
          LuaRemoveConstructUnwrapper(),
      )
  }
  ```

### 2.2 `net.internetisalie.lunar.lang.insight.unwrap.LuaUnwrapper`
- **Responsibility**: Lua-specific base that supplies the `Context` (whitespace predicate) so subclasses only implement `isApplicableTo` + `doUnwrap`.
- **Threading**: `collectAffectedElements` (preview) runs read-only; `unwrap` runs inside the platform's `WriteCommandAction` (`UnwrapHandler.startInWriteAction()` → the handler wraps the effective unwrap in a write command — verified in `UnwrapHandler.java`). Subclasses must not open their own write command.
- **Collaborators**: extends `com.intellij.codeInsight.unwrap.AbstractUnwrapper<LuaUnwrapper.Context>` (real: `platform/lang-impl/.../AbstractUnwrapper.java`). Uses `com.intellij.psi.PsiWhiteSpace`.
- **Key API**:
  ```kotlin
  abstract class LuaUnwrapper(description: @Nls String) :
      AbstractUnwrapper<LuaUnwrapper.Context>(description) {
      override fun createContext(): Context = Context()
      class Context : AbstractContext() {
          override fun isWhiteSpace(element: PsiElement): Boolean =
              element is PsiWhiteSpace
          // extractBlockBody(block, from) delegates to platform extract(first,last,from)
          fun extractBlockBody(block: LuaBlock, from: PsiElement) {
              val stmts = block.statementList
              val first = stmts.firstOrNull() ?: return
              val last = stmts.last()
              extract(first, last, from)   // protected AbstractContext.extract
          }
      }
  }
  ```
  `AbstractContext.extract(first, last, from)` (verified, `AbstractUnwrapper.java:72`) trims whitespace, and — only when `isEffective` — `addRangeBefore(first, last, from.parent, from)` to hoist the body before the construct, recording extracted elements for the preview range. This is exactly the Groovy/Java hoist path.

### 2.3 `net.internetisalie.lunar.lang.insight.unwrap.LuaBlockUnwrapper`
- **Responsibility**: "Unwrap block" for a single construct kind — remove keyword+`end`, hoist the (single) body block to the parent. Implements EDITOR-06-01.
- **Threading**: as §2.2.
- **Collaborators**: `LuaConstruct` enum (§2.7), `LuaBlockStructure` (§2.4), PSI types `LuaIfStatement`, `LuaWhileStatement`, `LuaNumericForStatement`, `LuaGenericForStatement`, `LuaDoStatement`, `LuaFuncDecl`, `LuaLocalFuncDecl`, `LuaGlobalFuncDecl`, `LuaFuncDef`, all implementing `net.internetisalie.lunar.lang.psi.LuaBlockParent` (`getBlockList(): List<LuaBlock>` — real, `LuaBaseElements.kt:179`).
- **Key API**:
  ```kotlin
  class LuaBlockUnwrapper(private val construct: LuaConstruct) :
      LuaUnwrapper(construct.unwrapDescription) {
      override fun isApplicableTo(e: PsiElement): Boolean =
          construct.matches(e) && !LuaBlockStructure.isElseBranchOwner(e).not().let { false }
      override fun doUnwrap(element: PsiElement, context: Context) {
          val body = LuaBlockStructure.primaryBody(element) ?: return
          context.extractBlockBody(body, element)   // hoist
          context.delete(element)                    // remove keyword..end
      }
  }
  ```
  Applicability: `construct.matches(e)` is an `is`-check against the construct's PSI type(s) (§2.7). For `IF`, applicability additionally requires that `e` has **no** `elseif`/`else` branch (a plain `if…then…end`); a multi-branch `if` is not "unwrappable to one body" — matching Java's `JavaIfUnwrapper` which also refuses `else`-blocks. Multi-branch collapse is EDITOR-06-02 via §2.5. `primaryBody` returns the sole body (`then`-block for `if`, the single block for the others).

### 2.4 `net.internetisalie.lunar.lang.insight.unwrap.LuaBlockStructure`
- **Responsibility**: Stateless PSI helper — the "block-structure helpers" contract. Locate the primary body of a block construct, enumerate `if` branches, and detect branch keywords. This is the self-contained implementation of the helper EDITOR-05 will later share (see risks §Gap 2.1).
- **Threading**: pure PSI reads; caller supplies the read context.
- **Collaborators**: `LuaBlockParent`, `LuaBlock`, `LuaIfStatement`, `LuaElementTypes` (`IF`/`ELSEIF`/`ELSE`/`THEN`/`END`, real — used by `LuaInvertIfIntention.kt:24`).
- **Key API**:
  ```kotlin
  object LuaBlockStructure {
      fun primaryBody(construct: PsiElement): LuaBlock?          // §3.1
      fun ifBranches(ifStmt: LuaIfStatement): List<LuaIfBranch>  // §3.2
      fun hasElseOrElseIf(ifStmt: LuaIfStatement): Boolean
      fun blockParent(e: PsiElement): LuaBlockParent?
  }
  data class LuaIfBranch(val condition: LuaExpr?, val body: LuaBlock, val keywordType: IElementType)
  ```

### 2.5 `net.internetisalie.lunar.lang.insight.unwrap.LuaElseBranchRemover`
- **Responsibility**: Collapse an `if/else` (or `elseif`) — implements EDITOR-06-02. Applicable to a `LuaIfStatement` that has `else`/`elseif`; offers "Remove else branch" keeping the preceding body, i.e. drops the last branch and its keyword.
- **Threading**: as §2.2.
- **Collaborators**: `LuaBlockStructure.ifBranches`, `LuaConditionInverter`? — no; text rebuild via `LuaElementFactory.createFile` + `PsiTreeUtil.findChildOfType(dummy, LuaIfStatement::class.java)`, mirroring `LuaInvertIfIntention.kt:41-45`.
- **Key API**:
  ```kotlin
  class LuaElseBranchRemover : LuaUnwrapper("Remove 'else' branch") {
      override fun isApplicableTo(e: PsiElement): Boolean =
          e is LuaIfStatement && LuaBlockStructure.hasElseOrElseIf(e)
      override fun doUnwrap(element: PsiElement, context: Context) { /* §3.3 */ }
  }
  ```

### 2.6 `net.internetisalie.lunar.lang.insight.unwrap.LuaRemoveConstructUnwrapper`
- **Responsibility**: Delete the whole construct including its body — implements EDITOR-06-03.
- **Threading**: as §2.2.
- **Collaborators**: `LuaBlockStructure.blockParent`.
- **Key API**:
  ```kotlin
  class LuaRemoveConstructUnwrapper : LuaUnwrapper("Remove enclosing block") {
      override fun isApplicableTo(e: PsiElement): Boolean =
          LuaBlockStructure.blockParent(e) === e   // e is itself a block construct
      override fun doUnwrap(element: PsiElement, context: Context) {
          context.delete(element)   // no body extraction → whole construct removed
      }
  }
  ```

### 2.7 `net.internetisalie.lunar.lang.insight.unwrap.LuaConstruct`
- **Responsibility**: Enum mapping construct kind → applicable PSI type(s), description string, and primary-body accessor.
- **Key API**:
  ```kotlin
  enum class LuaConstruct(val unwrapDescription: String) {
      IF("Unwrap 'if'"), WHILE("Unwrap 'while'"), FOR("Unwrap 'for'"),
      DO("Unwrap 'do'"), FUNCTION("Unwrap 'function'");
      fun matches(e: PsiElement): Boolean = when (this) {
          IF       -> e is LuaIfStatement
          WHILE    -> e is LuaWhileStatement
          FOR      -> e is LuaNumericForStatement || e is LuaGenericForStatement
          DO       -> e is LuaDoStatement
          FUNCTION -> e is LuaFuncDecl || e is LuaLocalFuncDecl ||
                      e is LuaGlobalFuncDecl || e is LuaFuncDef
      }
  }
  ```

## 3. Algorithms

### 3.1 `primaryBody(construct)` — locate the single hoistable body
- **Input → Output**: `PsiElement` (a `LuaBlockParent`) → `LuaBlock?`
- **Steps**:
  1. If `construct !is LuaBlockParent`, return `null`.
  2. If `construct is LuaIfStatement`, return `construct.getBlockList().firstOrNull()` (the `then`-block; ordering of `getBlockList()` follows source order — `then`, then each `elseif`, then `else`, per the grammar `IF expr THEN block {ELSEIF expr THEN block}* [ELSE block] END`, `lua.bnf:137`). Only the first is the primary body; callers must have already excluded multi-branch `if` (§2.3).
  3. Otherwise return `construct.getBlockList().firstOrNull()` — for `while`/`for`/`do`/`function`/`funcDef` there is exactly one block.
- **Edge handling**: empty body block (no statements) → `firstOrNull()` chain in `extractBlockBody` returns without extracting; `context.delete(element)` still removes the construct, leaving an empty parent line that reformat collapses. Never throws.

### 3.2 `ifBranches(ifStmt)` — enumerate branches
- **Input → Output**: `LuaIfStatement` → `List<LuaIfBranch>` in source order.
- **Steps**:
  1. Read `blocks = ifStmt.getBlockList()` and `exprs = ifStmt.getExprList()` (both real generated accessors — `LuaIfStatement.java:11,14`).
  2. Walk `ifStmt.node.getChildren(null)` in order; each keyword node of type `LuaElementTypes.IF` / `ELSEIF` opens a conditional branch (pairs with the next `expr` from `exprs`), `ELSE` opens an unconditional branch (no expr). Associate each keyword with the next `LuaBlock` child.
  3. Emit `LuaIfBranch(condition, body, keywordType)` per keyword→block pairing.
- **Rules**: For an `if` with N conditions and an optional `else`, `blocks.size == exprs.size` (no else) or `exprs.size + 1` (with else) — this invariant is what `LuaInvertIfIntention.kt:22-23` relies on. `hasElseOrElseIf` = `node.findChildByType(ELSE) != null || node.findChildByType(ELSEIF) != null`.

### 3.3 `LuaElseBranchRemover.doUnwrap` — collapse the else/elseif tail
- **Input → Output**: mutate a `LuaIfStatement` so its last branch is dropped; extracted elements = the removed branch (for preview).
- **Steps** (preview vs effective governed by `context.isEffective`, per `AbstractContext`):
  1. `branches = LuaBlockStructure.ifBranches(ifStmt)`; if `branches.size < 2`, return (nothing to collapse).
  2. Compute the rebuilt `if` **text** by dropping the last branch: keep every branch except the last, terminated by `end`. Concretely, take `ifStmt.text`, and via `ifBranches` find the TextRange from the last branch's keyword (`ELSEIF`/`ELSE` node) through just before the trailing `END`, and delete that span from the text; the remainder is the new source.
  3. `dummy = LuaElementFactory.createFile(project, rebuiltText)`; `newIf = PsiTreeUtil.findChildOfType(dummy, LuaIfStatement::class.java) ?: return`.
  4. Record the removed branch's body block in `context.addElementToExtract(lastBranch.body)` so the preview highlights the branch being removed.
  5. If `context.isEffective`: `val replaced = ifStmt.replace(newIf); CodeStyleManager.getInstance(project).reformat(replaced)`. (`replace`/reformat identical to `LuaInvertIfIntention.kt:44-45`; runs inside the platform write command.)
- **Edge handling**: a plain `if…else…end` → dropping the `else` yields `if cond then <then-body> end`. A three-way `if…elseif…else…end` → drops only the final `else`, leaving the `elseif`. Removing an `elseif` similarly drops that trailing conditional branch.

### 3.4 Preview range
No custom algorithm: the platform derives the preview highlight from the `PsiElement`s reported by `Unwrapper.collectAffectedElements` (real: `Unwrapper.java:40`). `AbstractUnwrapper.collectAffectedElements` runs `doUnwrap` with `isEffective=false`, collecting into `toExtract`; for §2.3 the extracted body statements become the highlight, for §2.5 the removed branch, for §2.6 the whole construct (via `delete` recording — see note). This satisfies EDITOR-06-04 for free.

> Note for EDITOR-06-04 on the "remove construct" option: `AbstractContext.delete` records nothing into `myElementsToExtract`. To make the whole-construct removal preview highlight the construct, `LuaRemoveConstructUnwrapper.doUnwrap` calls `context.addElementToExtract(element)` **before** `context.delete(element)`.

## 4. External Data & Parsing
None. The feature consumes only in-memory PSI produced by our own parser; no CLI, file, or network input.

## 5. Data Flow

### Example 1: Unwrap `if` (EDITOR-06-01)
Input `if x then\n  a()\n  b()\nend<caret>`. `UnwrapHandler.invoke` → `LuaUnwrapDescriptor.collectUnwrappers` walks parents from the caret leaf, finds the `LuaIfStatement`, `LuaBlockUnwrapper(IF).isApplicableTo` true (no else). User picks "Unwrap 'if'". Platform opens a write command; `doUnwrap` calls `extractBlockBody(thenBlock, ifStmt)` → `addRangeBefore(a(), b(), ifStmt.parent, ifStmt)` hoisting both statements before the `if`, then `delete(ifStmt)`. Reformat (implicit via extracted range + platform) yields `a()\nb()`.

### Example 2: Collapse else (EDITOR-06-02)
Input `if x then\n  a()\nelse\n  b()<caret>\nend`. Applicable unwrappers include `LuaElseBranchRemover` (has else) and `LuaBlockUnwrapper(IF)` is **not** applicable (has else). Picking "Remove 'else' branch" → §3.3 rebuilds `if x then\n  a()\nend`, `replace`, reformat.

### Example 3: Remove while (EDITOR-06-03)
Input `while c do\n  work()<caret>\nend`. `LuaRemoveConstructUnwrapper.isApplicableTo(whileStmt)` true. Picking "Remove enclosing block" → `addElementToExtract(whileStmt)` (preview) then `delete(whileStmt)`; document loses the whole loop.

## 6. Edge Cases
- **Caret on a nested construct**: `collectUnwrappers` walks the whole parent chain, so an inner `do` and an outer `function` both appear as options (platform behaviour). No extra code.
- **Empty body**: §3.1 edge handling — construct removed cleanly, reformat collapses blank line.
- **`if` with `elseif` but no `else`**: `LuaBlockUnwrapper(IF)` not applicable; `LuaElseBranchRemover` applicable (drops the trailing `elseif`).
- **Anonymous `funcDef` as an expression** (e.g. `local f = function() body end`): unwrapping hoists `body` into the parent statement position, which can be syntactically invalid Lua. Handled by restricting `FUNCTION` applicability to statement-context function decls **only** when the parent is a `LuaBlock`; `LuaFuncDef` (expression form) is matched but `primaryBody`+hoist may produce non-statement text — this is flagged as a known limitation (risks §Gap 2.2), mirroring that Java also allows semantically-odd unwraps and relies on the user + undo.
- **`repeat…until`**: intentionally out of scope (requirements list only `if`/`while`/`for`/`do`/`function`); `LuaRepeatStatement` is not in `LuaConstruct`. No option offered.
- **Scoping on hoist**: locals declared inside the body become visible in the outer scope after hoisting. Lua semantics tolerate this (a wider scope is not an error), but shadowing/collision can change behaviour. Preserved as-is (no renaming); called out in risks §Risk 1.1.

## 7. Integration Points

```xml
<!-- src/main/resources/META-INF/plugin.xml, inside <extensions defaultExtensionNs="com.intellij"> -->
<lang.unwrapDescriptor
        language="Lua"
        implementationClass="net.internetisalie.lunar.lang.insight.unwrap.LuaUnwrapDescriptor"/>
```

Beanclass is `com.intellij.lang.LanguageExtensionPoint` (verified: `LangExtensionPoints.xml:128`, `dynamic="true"`), so the `language`/`implementationClass` attribute pair is correct — same shape as Groovy's `plugins/groovy/resources/META-INF/plugin.xml:681`. No new action, group, or ID: Ctrl+Shift+Delete is already bound to the platform `UnwrapAction`. No settings keys, no index.

## 8. Requirement Coverage

| Requirement | Priority | Implemented by (section) |
|-------------|----------|--------------------------|
| EDITOR-06-01 Unwrap block | M | §2.3, §2.4, §3.1, §2.2 (`extract` hoist) |
| EDITOR-06-02 Unwrap else/elseif | S | §2.5, §3.2, §3.3 |
| EDITOR-06-03 Remove construct | S | §2.6 |
| EDITOR-06-04 Preview highlight | S | §3.4 (platform `collectAffectedElements`) + §2.6 note |

## 9. Alternatives Considered
- **One generic `LuaUnwrapper` switching on type inside `doUnwrap`** — rejected: `isApplicableTo` differs per construct and the platform expects one `Unwrapper` per offered option/description; a single class would collapse the picker into one label.
- **Manual `WriteCommandAction` + text rebuild for the block hoist (like `LuaInvertIfIntention`)** — rejected for §2.3: the platform `AbstractContext.extract`/`addRangeBefore` path gives correct preview highlighting and undo for free. Text-rebuild is retained only for the `else`-collapse (§3.3) where a structural `replace` is simplest.
- **Reusing an EDITOR-05 helper** — impossible: EDITOR-05 is unimplemented. We ship `LuaBlockStructure` and define the convergence contract in risks.

## 10. Open Questions

_None — feature has cleared the planning bar. The EDITOR-05 helper-convergence and the `funcDef` hoist limitation are tracked as de-risking items in risks-and-gaps.md, not open design decisions._
