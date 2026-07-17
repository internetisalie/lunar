---
id: "MAINT-29-DESIGN"
title: "Technical Design"
type: "design"
parent_id: "MAINT-29"
folders:
  - "[[features/maint/29-controlflow-inspections/requirements|requirements]]"
---

# Technical Design: MAINT-29 — Control-Flow & Inspection Accuracy

This design repairs seven grounded defects from [`docs/review.md`](../../../review.md) (#8, #9,
#32, #33, #34, #68, #69) across four files: the control-flow-graph builder, three inspections,
and one shared quick-fix file. All symbols below were verified against the repo at commit
`8b1e4586`; every `file:line` citation is current as of 2026-07-17.

## 1. Architecture Overview

### Current State
- **#8 `ReplaceIntegerDivisionFix`** (`lang/syntax/LuaLanguageLevelQuickFixes.kt:78-110`)
  reads `editor.caretModel.offset`, walks *up* from `file.findElementAt(offset)` to the first
  element whose `text.contains("//")`, then does `text.replace("//", "/")` and wraps in
  `math.floor(...)` via `document.replaceString`. Because `LuaLanguageLevelInspection` registers
  the problem **on the `//` operator leaf** (`LuaLanguageLevelInspection.kt:70-74`, element `o`
  is a `LuaBinOp`), `findElementAt` lands on that leaf; its text is exactly `//`, so the first
  match is the operator itself and the result is `math.floor(/)` — garbage.
- **#9 `LuaMakeLocalQuickFix`** (`LuaGlobalCreationInspection.kt:93-107`) does
  `"local " + assignStat.text` on the whole `LuaAssignmentStatement`. For `x, t.f = 1, 2` this
  yields `local x, t.f = 1, 2` — invalid Lua (locals cannot have suffixed targets). The
  inspection registers the fix on **every** unqualified target (loop body at
  `LuaGlobalCreationInspection.kt:38-70`), including targets in multi-target/suffixed
  assignments.
- **#32 `LuaControlFlowBuilder`** (`analysis/controlflow/LuaControlFlowBuilder.kt`) has three
  edge-construction defects:
  - (a) `builder.controlFlow.instructions.lastOrNull()` (lines 110, 126, 150, 197, 224) is used
    as the branch/loop fall-through point. `lastOrNull()` is the *last instruction ever added*,
    not the *live control point* — after a nested `return`/`break` the platform sets
    `prevInstruction = null` via `flowAbrupted()`
    (`intellij-community/.../ControlFlowBuilder.java:148-150`), but `lastOrNull()` still returns
    the abrupted instruction, so a spurious edge is drawn out of a dead branch.
  - (b) In `visitIfStatement` (lines 90-141) pending edges are added with scope `ifStatement`
    but the per-branch `flowAbrupted()` ordering lets a branch-final compound statement's own
    pending edge leak forward to the next `elseif` condition node.
  - (c) `labelInstructions` (line 16) is a **flat** `Map<String, Instruction>`. Two sibling
    loops that each contain `::continue::` overwrite each other; a `goto continue` resolves to
    whichever label was visited last, cross-wiring the two loops.
- **#33** `if`/`while`/`repeat` **conditions** get a graph node (`startNode(expr)` at lines 101,
  144, 173) but the condition expression is **never** `accept`-ed, so no `LuaReadWriteInstruction`
  (READ) is emitted for names used in a condition.
- **#34 `LuaUnusedLocalInspection.classify`** (`analysis/inspections/LuaUnusedLocalInspection.kt:88-108`)
  routes any `LuaNameRef` that is not a declaration into `usages` (line 106 `else -> usages.add`).
  An assignment target like `x` in `x = 1` is a `LuaNameRef` whose parent is a `LuaVar`, so it is
  counted as a usage — a local that is only ever *assigned* is deemed used (false negative vs.
  the documented "declared but never read").
- **#69** `collectUsedDeclarations` (`LuaUnusedLocalInspection.kt:123-135`) calls
  `usage.reference?.resolve()` (line 131). `LuaNameReference` is a `PsiPolyVariantReference`
  (`lang/LuaNameReference.kt:32-33`); `PsiReferenceBase.resolve()` returns `null` when
  `multiResolve` yields more than one result, so an ambiguous usage is silently dropped, risking
  a false "unused".
- **#68 `LuaSuspiciousConcatenationInspection.isConcatenable`**
  (`analysis/inspections/LuaSuspiciousConcatenationInspection.kt:71-79`) returns `false` for
  **every** `LuaGraphType.Table`, so a class that defines a `__concat` metamethod is a guaranteed
  false positive.

### Prior Art in This Repo
Searched `src/main/kotlin` for existing components that already do each job:
- **Write-target exclusion (#34)** — `LuaUndeclaredVariableInspection.isSimpleWriteTarget`
  (`analysis/inspections/LuaUndeclaredVariableInspection.kt:80-81`) already exists and is the
  named model in the review. It is `private` there and takes a `LuaVar`
  (`luaVar.varSuffixList.isEmpty() && luaVar.parent is LuaVarList`). This design **reuses its
  exact predicate** (adapted from `LuaVar` to the `LuaNameRef`'s `LuaVar` parent), not a new one.
- **CFG consumers (#32/#33 blast radius)** — `grep -rln "LuaControlFlow"` yields exactly four
  files: `LuaControlFlowBuilder.kt`, `LuaControlFlow.kt`, `ControlFlowCache.kt`, and the *only
  consumer* `LuaUnreachableCodeInspection.kt` (`analysis/inspections/LuaUnreachableCodeInspection.kt:44-99`,
  uses `ControlFlowCache.getControlFlow` + `LuaControlFlow.isReachable`). No other inspection reads
  the CFG. This design **extends** the existing builder in place; no new CFG type is created.
- **Poly-variant resolution (#69)** — the sibling `LuaGlobalCreationInspection` already uses the
  correct idiom (`reference.multiResolve(false)` at `LuaGlobalCreationInspection.kt:47-48`). #69
  aligns `LuaUnusedLocalInspection` with that existing pattern.
- **Quick-fix rebuild via factory (#8/#9)** — `LuaElementFactory.createFile(project, text)`
  (`lang/psi/LuaElementFactory.kt:41-44`) is the established PSI-rebuild entry point already used
  by `LuaMakeLocalQuickFix` (line 101). #8 adopts the same factory pattern.

No component is duplicated; every fix edits an existing class.

### Target State
Four files edited in place. The CFG builder switches from `instructions.lastOrNull()` to the
platform's `prevInstruction` control point, scopes pending edges correctly, and replaces the flat
label map with a block-scoped resolver. `ReplaceIntegerDivisionFix` and `LuaMakeLocalQuickFix`
navigate real PSI (`LuaBinOpExpr`, `LuaAssignmentStatement`) and rebuild valid Lua via
`LuaElementFactory`. The three inspection predicates are corrected. `LuaUnreachableCodeInspection`
remains the sole CFG consumer and its existing test suite
(`LuaUnreachableCodeInspectionTest`, `LuaControlFlowTest`) is the regression bar.

## 2. Core Components

No new classes are created. Each subsection below is an **edited** existing symbol.

### 2.1 `net.internetisalie.lunar.lang.syntax.ReplaceIntegerDivisionFix` (#8)
- **Responsibility**: Rewrite `l // r` to `math.floor(l / r)` from the operands, not by string
  surgery on the operator leaf.
- **Threading**: EDT inside the platform write action (`startInWriteAction() = true`, unchanged).
  **No** nested `WriteCommandAction` — MAINT-31 removed those; grep confirms none remains
  (§Edge Cases). The `applyFix`/`invoke` body runs inside the platform-supplied write action.
- **Collaborators**: `LuaBinOpExpr.getLeft(): LuaExpr` / `getRight(): LuaExpr?` /
  `getBinOp(): LuaBinOp` (`gen/.../LuaBinOpExpr.java:11-20`); `LuaElementFactory.createExpression`
  (`lang/psi/LuaElementFactory.kt:32-35`); `LuaElementTypes.INTDIV`
  (`gen/.../LuaElementTypes.java:95`, the `//` token).
- **Key API** (signature unchanged; body rewritten):
  ```kotlin
  override fun invoke(project: Project, editor: Editor, file: PsiFile) {
      val offset = editor.caretModel.offset
      val element = file.findElementAt(offset) ?: return
      val binOp = PsiTreeUtil.getParentOfType(element, LuaBinOpExpr::class.java, false) ?: return
      // realizes §3.1
  }
  override fun startInWriteAction(): Boolean = true
  ```

### 2.2 `net.internetisalie.lunar.analysis.inspections.LuaMakeLocalQuickFix` (#9)
- **Responsibility**: Convert a single simple-target global assignment to a `local`; **do not
  offer** the fix at all for multi-target or suffixed assignments.
- **Threading**: platform write action (`LocalQuickFix.applyFix`, unchanged). No nested
  `WriteCommandAction`.
- **Collaborators**: `LuaAssignmentStatement.getVarList().getVarList(): List<LuaVar>`
  (`gen/.../LuaVarList.java:11`); `LuaVar.getVarSuffixList()` (`gen/.../LuaVar.java`);
  `LuaElementFactory.createFile` + `LuaLocalVarDecl` (already used, lines 101-102).
- **Key API**:
  ```kotlin
  class LuaMakeLocalQuickFix : LocalQuickFix {
      override fun getFamilyName(): String = "Make Local"
      override fun applyFix(project: Project, descriptor: ProblemDescriptor)  // realizes §3.2
  }
  ```
  Availability is enforced by the **registration guard** in
  `LuaGlobalCreationInspection.buildVisitor` (§7), not inside `applyFix` — see §3.2 for the
  bail-vs-split decision.

### 2.3 `net.internetisalie.lunar.analysis.controlflow.LuaControlFlowBuilder` (#32, #33)
- **Responsibility**: Build a `LuaControlFlow` whose fall-through, pending, and label edges are
  correct, and which emits READ instructions for names in conditions.
- **Threading**: read action (invoked via `ControlFlowCache.getControlFlow` inside inspection
  `checkFile`, which the platform runs under a read action).
- **Collaborators**: platform `ControlFlowBuilder.prevInstruction` (public field,
  `intellij-community/.../ControlFlowBuilder.java:35`), `addEdge`/`addPendingEdge`/`flowAbrupted`
  (same file, lines 111, 160, 148); `LuaBlock.getStatementList()` (`gen/.../LuaBlock.java`);
  `LuaLabel.getLabelName()` / `LuaGotoStatement.getLabelRef()` (`gen/.../LuaLabel.java`,
  `gen/.../LuaGotoStatement.java`).
- **Key API** (private additions; existing `build`/`visit*` signatures unchanged):
  ```kotlin
  // replaces the flat `labelInstructions: MutableMap<String, Instruction>` (line 16)
  private data class LabelKey(val name: String, val block: LuaBlock)
  private val labelInstructions = mutableMapOf<LabelKey, Instruction>()
  private fun currentControlPoint(): Instruction? = builder.prevInstruction   // realizes §3.3
  private fun resolveGoto(record: GotoRecord): Instruction?                   // realizes §3.5
  ```

### 2.4 `net.internetisalie.lunar.analysis.inspections.LuaUnusedLocalInspection` (#34, #69)
- **Responsibility**: Count only *read* usages; resolve poly-variant references without dropping
  ambiguous ones.
- **Threading**: read action (`checkFile`).
- **Collaborators**: `LuaVar` / `LuaVarList` (`gen/.../LuaVar.java`, `gen/.../LuaVarList.java`);
  `LuaNameRef.getReference()` → `LuaNameReference` (`lang/LuaNameReference.kt:32`,
  `PsiPolyVariantReference`).
- **Key API** (edits inside two existing private methods):
  ```kotlin
  private fun classify(...)                  // add write-target guard before line 106 else-branch — §3.6
  private fun collectUsedDeclarations(...)    // multiResolve(false) instead of resolve() — §3.7
  private fun isSimpleWriteTarget(nameRef: LuaNameRef): Boolean  // new private helper — §3.6
  ```

### 2.5 `net.internetisalie.lunar.analysis.inspections.LuaSuspiciousConcatenationInspection` (#68)
- **Responsibility**: Treat a `Table` whose class declares `__concat` as concatenable.
- **Threading**: read action (`buildVisitor` under `LuaTypesSnapshot`).
- **Collaborators**: `LuaGraphType.Table` (`lang/psi/types/LuaGraphType.kt`) — see §3.8 for the
  `__concat` field lookup it uses.
- **Key API** (edit inside existing `isConcatenable`, lines 71-79):
  ```kotlin
  private fun isConcatenable(type: LuaGraphType): Boolean  // Table branch consults __concat — §3.8
  ```

## 3. Algorithms

### 3.1 #8 — Rebuild `math.floor(left / right)` from operands
- **Input → Output**: caret offset inside a `//` `LuaBinOpExpr` → the whole binOp replaced by a
  `math.floor(<left> / <right>)` expression, valid at any language level.
- **Steps**:
  1. `element = file.findElementAt(offset)`; return if null.
  2. `binOp = PsiTreeUtil.getParentOfType(element, LuaBinOpExpr::class.java, false)`; return if
     null.
  3. Guard: if the operator token child is not `//` return — `binOp.binOp` is the `LuaBinOp`
     CONTAINER (its own `node.elementType` is `BIN_OP`, `LuaElementTypes.java:16`, never `INTDIV`);
     the check must read the token child: `binOp.binOp.firstChild.node.elementType ==
     LuaElementTypes.INTDIV` (equivalently `binOp.binOp.text == "//"`). Return when it is not (not an integer
     division — availability re-check, keeps the fix safe if invoked out of context).
  4. `left = binOp.left` (non-null per `getLeft(): @NotNull LuaExpr`); `right = binOp.right`;
     return if `right == null` (malformed `a //` with no RHS).
  5. `newText = "math.floor(" + left.text + " / " + right.text + ")"`.
  6. `replacement = LuaElementFactory.createExpression(project, newText)`; return if null.
  7. `binOp.replace(replacement)`.
- **Rules / edge handling**: operands are copied verbatim by `.text`, preserving nested
  parentheses and precedence (`math.floor((a+b) / c)` when the source was `(a+b) // c`). No
  document string surgery. `isAvailable` stays `true` (the inspection only offers the fix on a
  real `//` operator; step 3 is a defensive re-check).
- **Complexity**: O(1) PSI navigation.

### 3.2 #9 — Make-local: don't-offer on multi-target / suffixed assignments
- **Decision: bail by not offering the fix** (not split). Rationale:
  - *Splitting* `x, t.f = 1, 2` into `local x = 1; t.f = 2` changes evaluation semantics: Lua
    evaluates all RHS expressions **before** any assignment, so a naïve textual split reorders
    side effects and mis-handles a target/value count mismatch (`x, y = f()`). Getting split
    right requires temp-variable spilling — out of scope and risky.
  - *Offer-and-noop* is a bad UX (a lightbulb that does nothing). **Don't-offer** is correct: the
    problem is still reported (the diagnostic and `LuaAddToGlobalsQuickFix` remain), only the
    unsafe `LuaMakeLocalQuickFix` is withheld.
- **Availability predicate** (evaluated in the inspection's registration loop, §7):
  a target `nameRef` is *make-local-eligible* iff **both**:
  1. its enclosing `LuaAssignmentStatement.varList.varList.size == 1` (single target), and
  2. that single `LuaVar.varSuffixList.isEmpty()` (no `.f` / `[k]` suffix).
- **Steps** (registration loop, replacing the current unconditional `LuaMakeLocalQuickFix()` at
  `LuaGlobalCreationInspection.kt:64`):
  1. Compute `eligible = assignStat.varList.varList.size == 1 && variable.varSuffixList.isEmpty()`
     (`variable` is the current loop `LuaVar`, already in scope at line 40).
  2. Build the fix array: always include `LuaAddToGlobalsQuickFix(name)`; prepend
     `LuaMakeLocalQuickFix()` **only if** `eligible`.
- **Rules / edge handling**: `x = 1` (single simple target) → both fixes; `x, y = 1, 2` and
  `x, t.f = 1, 2` → only "Add to globals". `applyFix` keeps its existing single-target body
  (lines 96-105) and additionally returns early if
  `assignStat.varList.varList.size != 1` (defensive, in case another caller registers it).

### 3.3 #32(a) — Correct fall-through point via `prevInstruction`
- **Rule**: replace every `builder.controlFlow.instructions.lastOrNull()` (lines 110, 126, 150,
  197, 224) used as a *fall-through source* with `builder.prevInstruction`.
- **Why**: `prevInstruction` is the platform's live "next edge origin"; `addNode` sets it and
  `flowAbrupted()` nulls it after `return`/`break`/`goto`
  (`ControlFlowBuilder.java:124-150`). `instructions.lastOrNull()` ignores abruption and returns
  the last-added instruction regardless, drawing a spurious edge out of a dead branch.
- **Steps** (per branch/loop end, e.g. `visitIfStatement` lines 110-113):
  1. `val fallThrough = builder.prevInstruction`  *(replaces `instructions.lastOrNull()`)*.
  2. `if (!isAbrupted && fallThrough != null) builder.addPendingEdge(ifStatement, fallThrough)`
     — for loops (`while`/`for`) keep the existing `addEdge(fallThrough, loopInst)` /
     `addEdge(fallThrough, condInst)` back-edge, still guarded by `!isAbrupted`.
- **Edge handling**: when `isAbrupted` is true the branch abrupted (every path
  returned/broke/goto'd) — no fall-through edge is added, exactly the intended behavior. The
  `isAbrupted` local is already tracked per branch (lines 106-108, 122-124).

### 3.4 #32(b) — Per-branch pending scoping in `visitIfStatement`
- **Rule**: after computing each branch's fall-through pending edge, call
  `builder.flowAbrupted()` **before** starting the next branch's condition node so the branch's
  live control point does not chain into the next `elseif` condition.
- **Steps** (per iteration of the `blockList.indices` loop, lines 98-133):
  1. Start the condition/else node (existing lines 101 / 117).
  2. Wire `prevCondInstruction → condInst` for the false-branch edge (existing 102-104 / 118-120).
  3. `isAbrupted = false; blockList[i].accept(this)` (existing 106-107 / 122-123).
  4. Add the per-branch fall-through pending edge scoped to `ifStatement` (§3.3 step 2).
  5. **`builder.flowAbrupted()`** — nulls `prevInstruction` so the *next* iteration's
     `startNode(expr)` does not inherit this branch's body as a predecessor (this is already
     present at lines 114/130; the fix is to ensure it runs **after** the pending edge is added
     and **before** the next condition — reorder so the pending-edge computation reads the branch
     body's `prevInstruction`, then abrupt).
- **Edge handling**: the false edge from a condition to the next condition is drawn explicitly via
  `prevCondInstruction` (lines 102-104); the *true* edge from a condition into its body is the
  natural `addNode` chain. The trailing "no else" merge (lines 135-138) stays: if the last branch
  was a condition (no `else`), its false-exit is a pending edge to the if-statement scope.

### 3.5 #32(c) — Block-scoped label resolution (Lua 5.4 §3.3.4)
- **Lua rule** (manual §3.3.4): "A label is visible in the entire block where it is defined,
  except inside nested functions. A goto may jump to any visible label as long as it does not
  enter into the scope of a local." Practically: a `goto L` resolves to a `::L::` in the **same
  block** or any **enclosing** block, never in a sibling block.
- **Data model**: replace the flat `Map<String, Instruction>` (line 16) with
  `Map<LabelKey, Instruction>` where `LabelKey(name: String, block: LuaBlock)` and `block` is the
  `LuaLabel`'s enclosing `LuaBlock` (`PsiTreeUtil.getParentOfType(label, LuaBlock::class.java)`).
  Record in `visitLabel` (line 266-271):
  `labelInstructions[LabelKey(label.labelName.text, enclosingBlock)] = labelInst`.
- **Resolution algorithm** `resolveGoto(record)` (called from `build`, replacing lines 58-63):
  - **Input → Output**: a `GotoRecord(gotoInst, targetName)` and the goto's PSI element → the
    matching label `Instruction` or `null`.
  - **Steps**:
    1. `var block = PsiTreeUtil.getParentOfType(record.gotoElement, LuaBlock::class.java)`
       (add `gotoElement: PsiElement` to `GotoRecord`, captured in `visitGotoStatement`).
    2. While `block != null`: if `labelInstructions[LabelKey(record.targetName, block)]` exists,
       return it.
    3. Ascend: `block = PsiTreeUtil.getParentOfType(block, LuaBlock::class.java)` (strict parent).
    4. Return `null` when no enclosing block defines the label.
- **Edge handling**: two sibling `for` loops each with `::continue::` now key on distinct
  `LuaBlock`s, so `goto continue` in loop A never resolves to loop B's label. A `goto` to an
  outer-block label still resolves via the ascent. Cycles are impossible (strict-parent ascent
  terminates at the file root).

### 3.6 #34 — Exclude simple write targets from usages
- **Rule**: in `classify` (lines 88-108), before the `else -> usages.add(nameRef)` fallthrough
  (line 106), skip a `nameRef` that is a simple write target.
- **Helper** (mirrors `LuaUndeclaredVariableInspection.isSimpleWriteTarget:80-81`, adapted from a
  `LuaVar` to a `LuaNameRef`):
  ```kotlin
  private fun isSimpleWriteTarget(nameRef: LuaNameRef): Boolean {
      val luaVar = nameRef.parent as? LuaVar ?: return false
      return luaVar.varSuffixList.isEmpty() && luaVar.parent is LuaVarList
  }
  ```
- **Steps**: add `parent is LuaVar && isSimpleWriteTarget(nameRef) -> { /* skip: write, not read */ }`
  as a `when` branch in `classify` **before** the `else` (so an assignment target is neither a
  declaration nor a usage).
- **Edge handling**: `x = 1` where `x` is a local → not a usage → the local is flagged unused if
  never read elsewhere. A read `print(x)` → `x`'s parent is not `LuaVar` → still a usage. A
  suffixed target `t.f = 1` → `varSuffixList` non-empty → the helper returns false, so it is not
  skipped (correct: `t` there is a read of `t`). Global compound `x = x + 1` → the RHS `x` is a
  read (parent not `LuaVar`) and counts; the LHS `x` is a write and is skipped — so a local
  written only by `x = x + 1` with no other read is flagged (matches "never read").

### 3.7 #69 — Poly-variant resolution keeps ambiguous usages
- **Rule**: in `collectUsedDeclarations` (line 131) replace
  `val resolved = usage.reference?.resolve() ?: continue` /
  `if (resolved in declLeaves) used.add(resolved)` with an iteration over `multiResolve`:
  ```kotlin
  val ref = usage.reference as? PsiPolyVariantReference ?: continue
  for (result in ref.multiResolve(false)) {
      val target = result.element ?: continue
      if (target in declLeaves) used.add(target)
  }
  ```
- **Steps**: for each usage whose name is a declared name (line 130 guard unchanged), collect
  **all** resolve targets; mark each declared leaf among them used.
- **Edge handling**: a name that resolves ambiguously to two shadowed locals now marks *both* used
  (no false "unused"). A non-poly reference (`as?` fails) is skipped exactly as `resolve()==null`
  was. Complexity is unchanged (one `multiResolve` per usage, same as one `resolve`).

### 3.8 #68 — `__concat` metamethod respect
- **Rule**: in `isConcatenable` (lines 71-79) the `is LuaGraphType.Table -> false` case becomes:
  a `Table` is concatenable iff it declares a `__concat` field (metamethod).
- **Lookup**: `LuaGraphType.Table` carries a `fields` map (the same structure the type engine
  materializes class members into — see the `.agents/AGENTS.md` type-engine notes on
  `materializeClass`/`fields`). A table is concatenable iff `"__concat" in table.fields`.
- **Steps**:
  ```kotlin
  is LuaGraphType.Table -> type.localMembers.containsKey("__concat")
  // grounded alternative (DR-01 picks by evidence): resolveMember("__concat") != null via the
  // materialized LuaClassType path (LuaComplexTypes.kt:105) when nominal members matter
  ```
- **Edge handling**: a plain table literal `{}` (no `__concat`) → still flagged (unchanged, true
  positive). A `---@class Vec` with `Vec.__concat = function(a,b) ... end` → not flagged. `Array`
  and `Function` branches are unchanged (still non-concatenable). `Union` keeps its existing
  `any { isConcatenable(it) }` recursion, which now transitively respects `__concat`.
- **De-risking**: the exact `Table.fields` accessor name/type is confirmed at implementation time
  against `LuaGraphType.kt` — tracked as **DR-01** (§risks). If `fields` does not carry
  metamethods for a `@class`, the fallback is to resolve the class via `LuaTypeManager` and check
  its members; DR-01 pins which.

## 4. External Data & Parsing
None. This feature consumes no CLI output, files, or network responses — it operates purely on
PSI, the CFG, and the type graph. (Stated per template requirement.)

## 5. Data Flow

### Example 1: `//` quick fix on `local n = 7 // 2` at Lua 5.1 (#8)
`LuaLanguageLevelInspection.visitBinOp` registers the problem on the `//` `LuaBinOp` leaf
(`LuaLanguageLevelInspection.kt:70`) with an `IntentionWrapper(ReplaceIntegerDivisionFix())`.
User invokes the fix → `invoke` reads caret offset (on `//`), `getParentOfType(..., LuaBinOpExpr)`
finds `7 // 2`, `left="7"`, `right="2"`, rebuilds `math.floor(7 / 2)` via `createExpression`,
`binOp.replace(...)`. Result buffer: `local n = math.floor(7 / 2)`.

### Example 2: unreachable-code accuracy on nested return (#32a, sole consumer)
`function f(x) if x then return 1 else return 2 end; print("x") end`.
`LuaUnreachableCodeInspection` → `ControlFlowCache.getControlFlow(func)` → repaired builder: both
branches abrupt, `prevInstruction` is null at the if-statement's merge, so **no** fall-through
edge reaches `print("x")` → `isReachable(printInst) == false` → "Unreachable code" reported. (The
existing `LuaControlFlowTest.testSimpleBranchingReachability` is the regression assertion.)

### Example 3: assigned-only local (#34)
`local total; total = total + 1` (no other read). `classify`: the `total` declaration comes from
`local total` (`LuaAttName` branch). The write `total = ...` LHS `total` → `isSimpleWriteTarget`
true → skipped. The RHS `total` → read → usage. So `total` *is* used here. But
`local flag; flag = true` (RHS is a literal) → the only `flag` name-ref besides the decl is the
write LHS → skipped → `flag` flagged "Unused local variable 'flag'".

## 6. Edge Cases
- **WriteCommandAction moot (requirements §Note)**: MAINT-31 (`status: done`,
  `docs/features/maint/31-dead-code-sweep/requirements.md`) already removed the redundant nested
  `WriteCommandAction` wrappers. `grep -rn WriteCommandAction` across all four target files
  returns **zero** hits — the note is **moot**; no work remains. The design must not re-introduce
  a nested write action (intention-preview hazard): `applyFix`/`invoke` run inside the
  platform-supplied write action already.
- **#8 malformed `a //`** (no RHS): `binOp.right == null` → fix returns without mutating (safe).
- **#8 chained `a // b // c`**: `getParentOfType` finds the innermost `LuaBinOpExpr` containing
  the caret; the fix rewrites exactly that operator, leaving outer structure intact.
- **#9 count mismatch `x = f()`** (single target, function RHS): still single simple target →
  make-local offered → `local x = f()` is valid. Only multi-target/suffixed are withheld.
- **#32 `repeat`/`until` label (#32c)**: `::continue::` inside a `repeat` block keys on that
  block; the `until` condition (visited at line 173, now `accept`-ed per #33) can reference the
  loop's locals, which are in scope per Lua's repeat semantics — no label change needed there.
- **#33 condition with no names** (`if true then`): `expr.accept(this)` emits no
  `LuaReadWriteInstruction` (the literal is not a `LuaNameRef`) — harmless.
- **#68 `Table` with a non-function `__concat` field**: still treated as concatenable (we only
  check presence, matching Lua's "has the metamethod" runtime rule; validating the field is a
  function is out of scope).

## 7. Integration Points
**No `plugin.xml` changes.** All four inspections and the quick fix are already registered.
Confirmed present in `src/main/resources/META-INF/`:
- `LuaGlobalCreation` / `LuaUnusedLocal` / `LuaSuspiciousConcatenation` / `LuaUnreachableCode` /
  `LuaLanguageLevel` inspections are registered under `<localInspection>` (existing INSP-04/05/07/09
  work). This feature only edits their `.kt` bodies.

The only registration-adjacent change is the **fix array** built in
`LuaGlobalCreationInspection.buildVisitor` (§3.2): the `LuaMakeLocalQuickFix()` element is added
to the `registerProblem` varargs conditionally instead of unconditionally
(`LuaGlobalCreationInspection.kt:60-66`).

## 8. Requirement Coverage

| Requirement | Priority | Implemented by (section) |
|-------------|----------|--------------------------|
| MAINT-29-01 | M | §2.1, §3.1 (#8); §2.2, §3.2 (#9) |
| MAINT-29-02 | M | §2.3, §3.3, §3.4, §3.5 (#32); §3.3/§2.3 condition `accept` (#33) |
| MAINT-29-03 | S | §2.4, §3.6 (#34); §3.7 (#69) |
| MAINT-29-04 | C | §2.5, §3.8 (#68) |

## 9. Alternatives Considered
- **#9 split instead of bail**: rejected — correct splitting needs RHS-order-preserving temp
  spilling (Lua evaluates all RHS before assigning); high risk for a "make local" convenience fix.
- **#8 document `replaceString` with a smarter range**: rejected — string surgery cannot preserve
  operand parenthesization/precedence reliably; PSI rebuild via `LuaElementFactory` is the repo's
  established, safe idiom (used by `LuaMakeLocalQuickFix`).
- **#32 rewrite the builder to a fresh CFG framework**: rejected — the platform
  `ControlFlowBuilder` already exposes `prevInstruction`/pending/abrupt primitives; the defects are
  misuse, not missing capability. Minimal, in-place edits keep the sole consumer's tests as the bar.
- **#33 emit condition instructions in the consumer**: rejected — conditions belong in the CFG
  (dataflow correctness), not re-derived per consumer.

## 10. Open Questions
None. (The single implementation-time confirmation — the `__concat` membership accessor, #68 — is DR-01 in risks-and-gaps.md.)
