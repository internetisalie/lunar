---
id: "EDITOR-07-DESIGN"
title: "Technical Design"
type: "design"
parent_id: "EDITOR-07"
folders:
  - "[[features/editor/07-move-statement/requirements|requirements]]"
---

# Technical Design: EDITOR-07 — Move Statement / Element

## 1. Architecture Overview

### Current State
Lunar registers no `com.intellij.statementUpDownMover` and no `com.intellij.moveLeftRightHandler`
extension. With no mover registered, `MoveStatementUp`/`MoveStatementDown` (Ctrl+Shift+↑/↓) fall
back to the platform `LineMover` (raw line swap, block-unaware), and `MoveElementLeft`/`MoveElementRight`
(Ctrl+Alt+Shift+←/→) are inert for Lua because no handler returns movable sub-elements. Line-based
moves corrupt Lua block delimiters (`end`, `until`, `then`, `do`) because they ignore PSI structure.

### Prior Art in This Repo
Searched `src/main/resources/META-INF/plugin.xml` (`grep statementUpDownMover|moveLeftRightHandler`
→ no hits) and `src/main/kotlin` (`grep StatementUpDownMover|MoveElementLeftRightHandler` → no hits).
**No existing mover or left/right handler exists** — this is a greenfield feature; nothing is
extended or replaced. It reuses existing generated PSI (`LuaStatement`, `LuaBlock`, `LuaArgs`,
`LuaExprList`, `LuaTableConstructor`, `LuaFieldList`, `LuaNameList`, `LuaLocalVarDecl`) and the
`LuaLanguage` registration (`language="Lua"`, used by 52 existing EPs in `plugin.xml`).

Verified PSI symbols (all in `src/main/gen/net/internetisalie/lunar/lang/psi/`):
- `LuaStatement` (marker interface, `LuaStatement.java`); every statement rule `extends=statement`
  (`lua.bnf:100–118`).
- `LuaBlock` — `getStatementList(): List<LuaStatement>` (`LuaBlock.java:13`).
- `LuaBlockParent` — `getBlockList(): List<LuaBlock>` (`LuaBaseElements.kt:179`); implemented by
  `LuaDoStatement`, `LuaWhileStatement`, `LuaRepeatStatement`, `LuaIfStatement`,
  `LuaNumericForStatement`, `LuaGenericForStatement`, `LuaFuncDecl`, `LuaLocalFuncDecl`,
  `LuaGlobalFuncDecl` (`lua.bnf:125–218`), and `LuaFuncDef` funcBody (`lua.bnf:283–289`).
- Args: `LuaArgs.getExprList(): LuaExprList` (`LuaArgs.java:11`); `LuaExprList.getExprList(): List<LuaExpr>`
  (`LuaExprList.java:11`).
- Table: `LuaTableConstructor.getFieldList(): LuaFieldList` (`LuaTableConstructor.java:11`);
  `LuaFieldList.getFieldList(): List<LuaField>` + `getFieldSepList(): List<LuaFieldSep>`
  (`LuaFieldList.java:11–14`).
- Name lists: `LuaNameList.getNameRefList(): List<LuaNameRef>` (`LuaNameList.java:11`) — used by
  `genericForStatement` and `parList` (`lua.bnf:144,291`); `local`/`global` decls use the private
  `attNameList` rule so `LuaLocalVarDecl.getAttNameList(): List<LuaAttName>` /
  `LuaGlobalVarDecl.getAttNameList()` return the attName children directly (`LuaLocalVarDecl.java:15`,
  `lua.bnf:186,196,220`).

### Target State
Two new Kotlin classes, both registered declaratively:

1. `LuaStatementMover : StatementUpDownMover` — computes source/target `LineRange`s from the
   enclosing statement PSI so a whole statement moves over its sibling, entering/leaving block
   bodies at boundaries, and calls `prohibitMove()` when a structural move is impossible except
   where the platform line move is the correct fallback (then returns `false` so `LineMover` runs).
2. `LuaMoveLeftRightHandler : MoveElementLeftRightHandler` — returns the ordered sibling
   `PsiElement[]` for call-argument lists, table-constructor field lists, and name lists.

```
Ctrl+Shift+↑/↓ ──▶ MoveStatement{Up,Down}Action ──▶ STATEMENT_UP_DOWN_MOVER_EP
                                                         └─▶ LuaStatementMover.checkAvailable(...)
                                                                └─ structural range? set toMove/toMove2
                                                                └─ else return false ▶ LineMover fallback
Ctrl+Alt+Shift+←/→ ─▶ MoveElement{Left,Right}Action ─▶ moveLeftRightHandler(Lua)
                                                         └─▶ LuaMoveLeftRightHandler.getMovableSubElements(el)
```

## 2. Core Components

### 2.1 `net.internetisalie.lunar.lang.editor.LuaStatementMover`
- **Responsibility**: Given the caret/selection line range, find the enclosing movable
  `LuaStatement`, compute its full `LineRange`, find the sibling statement (or block-boundary) to
  swap with, and populate `MoveInfo.toMove` / `MoveInfo.toMove2`.
- **Threading**: Runs on EDT inside the platform's move action, which already holds a read action
  when `checkAvailable` is called; it only *reads* PSI (no write — the platform performs the text
  move using the two `LineRange`s). No pooled work, no I/O.
- **Collaborators**: extends `com.intellij.codeInsight.editorActions.moveUpDown.StatementUpDownMover`;
  uses `LineRange`, `LineMover.checkLineMoverAvailable`, `MoveInfo`; reads `LuaStatement`, `LuaBlock`,
  `LuaBlockParent`, `PsiTreeUtil`, `com.intellij.psi.PsiComment`, `com.intellij.psi.PsiWhiteSpace`.
- **Key API**:
  ```kotlin
  class LuaStatementMover : StatementUpDownMover() {
    override fun checkAvailable(editor: Editor, file: PsiFile, info: MoveInfo, down: Boolean): Boolean
    // private helpers (each ≤30 logic lines, ≤3 args):
    private fun enclosingStatement(file: PsiFile, offset: Int): LuaStatement?
    private fun statementRange(psiStatement: LuaStatement, document: Document): LineRange
    private fun targetStatement(psiStatement: LuaStatement, down: Boolean): LuaStatement?
    private fun blockBoundaryTarget(psiStatement: LuaStatement, down: Boolean): LineRange?
  }
  ```

### 2.2 `net.internetisalie.lunar.lang.editor.LuaMoveLeftRightHandler`
- **Responsibility**: Return the ordered array of movable siblings for the three Lua list
  containers, else `PsiElement.EMPTY_ARRAY`. The platform swaps two adjacent returned elements,
  preserving the separators between them.
- **Threading**: EDT under the platform's read action; pure PSI read, no mutation.
- **Collaborators**: extends `com.intellij.codeInsight.editorActions.moveLeftRight.MoveElementLeftRightHandler`;
  reads `LuaExprList`, `LuaFieldList`, `LuaNameList`, `LuaField`, `LuaExpr`, `LuaNameRef`.
- **Key API**:
  ```kotlin
  class LuaMoveLeftRightHandler : MoveElementLeftRightHandler() {
    override fun getMovableSubElements(element: PsiElement): Array<PsiElement>
  }
  ```

## 3. Algorithms

### 3.1 Statement Up/Down — `checkAvailable` (EDITOR-07-01, -07-02, -07-04)
- **Input → Output**: `(Editor, PsiFile, MoveInfo, down: Boolean)` → `Boolean`; side effect: sets
  `info.toMove` (source `LineRange`) and `info.toMove2` (target `LineRange`, or `null` via
  `prohibitMove()` to block the move).
- **Steps**:
  1. Guard: if `file !is LuaFile` return `false` (let other movers / `LineMover` handle it).
  2. `range = getLineRangeFromSelection(editor)` (inherited helper).
  3. `offset = document.getLineStartOffset(range.startLine)`; if the leaf at `offset` is inside a
     multi-line string literal (`LuaTerminalExpr`/`STRING` token whose text contains `\n`), return
     `false` (defer to line mover — matches Groovy multiline-string guard).
  4. `psiStatement = enclosingStatement(file, offset)`; if `null`, **return `false`** — no structural
     move applies, platform runs `LineMover` (EDITOR-07-04 fallback).
  5. `source = statementRange(psiStatement, document)`; set `info.toMove = source`.
  6. `sibling = targetStatement(psiStatement, down)`.
     - If `sibling != null`: `info.toMove2 = statementRange(sibling, document)`. **Return `true`.**
     - If `sibling == null` (no sibling statement in this direction): the statement is at the edge
       of its block. Compute `boundary = blockBoundaryTarget(psiStatement, down)`:
       - If `boundary != null`, `info.toMove2 = boundary` (move enters the adjacent block body or
         steps out over the enclosing delimiter — EDITOR-07-02). **Return `true`.**
       - Else `info.prohibitMove()` (nowhere to go — first/last statement of the file with no
         adjacent block). **Return `true`** (prohibit is a decided answer, not a fallback).
- **Rules / edge handling**:
  - `enclosingStatement(file, offset)`: `element = file.findElementAt(shiftForward(text, offset, " \t"))`;
    if `element is PsiComment`, advance to `PsiTreeUtil.nextVisibleLeaf(element)`; then
    `PsiTreeUtil.getParentOfType(element, LuaStatement::class.java)`. A statement's **preceding
    attached comment lines** (leading `--`/`---` comments with no blank line between them and the
    statement) are folded into the source range by walking `PsiTreeUtil.prevLeaf` over
    `PsiComment`/whitespace-without-blank-line and extending `statementRange`'s start line up to the
    first such comment (keeps a LuaDoc/`---@`-annotated statement together with its annotation).
  - `targetStatement(psiStatement, down)`: walk `psiStatement.{nextSibling|prevSibling}` skipping
    `PsiWhiteSpace` and `PsiComment` that belong to the *moving* statement; the first sibling that
    `is LuaStatement` is the target. Siblings live under the same `LuaBlock` (or `LuaFile` top level),
    so this never crosses a block delimiter — that is what prevents `end`/`until` corruption.
  - `blockBoundaryTarget(psiStatement, down)`: let `container = psiStatement.parent` (a `LuaBlock`).
    - Moving **down** past the last statement, or **up** past the first: inspect the sibling in that
      direction *outside* the block (i.e. `container.parent` is a `LuaBlockParent`). If the adjacent
      element in the move direction is another `LuaBlockParent`/statement with a body we can enter
      (an `if`/`for`/`while`/`function`/`do` whose body `LuaBlock` is non-empty in the near edge),
      return the `LineRange` of that body's edge line so the statement lands inside the block; else
      return the `LineRange` of the single delimiter line (`end`/`until`) so the statement steps out
      of the block. Re-indentation is handled by the platform (`MoveInfo.indentTarget = true`
      default) plus `afterMove` reformatting (see §3.3).
  - Empty/None path: any `null` from steps 4/6 that is not a boundary → `return false` (line mover).
- **Complexity**: O(depth) PSI walks; no full-file traversal.

### 3.2 Move Element Left/Right — `getMovableSubElements` (EDITOR-07-03)
- **Input → Output**: `PsiElement` → `Array<PsiElement>` (ordered movable siblings, or empty).
- **Steps** (first match wins):
  1. `element is LuaExprList` → `element.exprList.toTypedArray()` (call arguments and RHS of
     assignments / `local … = a, b, c`).
  2. `element is LuaFieldList` → `element.fieldList.toTypedArray()` (table-constructor fields).
  3. `element is LuaNameList` → `element.nameRefList.toTypedArray()` (`for k, v in …`, param lists).
  4. `element is LuaLocalVarDecl` → `element.attNameList.toTypedArray()`;
     `element is LuaGlobalVarDecl` → `element.attNameList.toTypedArray()` (the `local a, b` /
     `global a, b` name side, which has no wrapping `LuaNameList` node — see §1 grounding).
  5. else → `PsiElement.EMPTY_ARRAY`.
- **Rules / edge handling**: The platform's `MoveElementLeftRightAction` locates the innermost
  element from `getMovableSubElements` results whose returned array contains the element under the
  caret, then swaps it with its neighbour, **reusing the existing separators** (commas for all four
  Lua list kinds; the field separator may be `,` or `;` per `LuaFieldSep`, which the platform keeps
  in place because it swaps only the `LuaField` sub-ranges, not the separators). Returning `< 2`
  elements makes the action a no-op. No `!!`, no separator synthesis in our code.
- **Complexity**: O(1) container dispatch + O(n) child list copy.

### 3.3 Re-indentation after a structural move — `afterMove`
- **Input → Output**: `(Editor, PsiFile, MoveInfo, down)` → `Unit`.
- **Steps**: Override `afterMove`; when the move entered/left a block (a flag set on `MoveInfo`
  user-data in `checkAvailable`, key `ENTERED_BLOCK`), call
  `CodeStyleManager.getInstance(project).adjustLineIndent(document, offset)` for the moved line range
  (wrapped by the platform's own write command — `afterMove` is already inside it). For a plain
  sibling swap, rely on `MoveInfo.indentTarget = true` (platform default) — no extra work.
- **Rules**: Only reindent the moved range; never reformat the whole file. If `adjustLineIndent`
  throws, log via `Logger.getInstance(...)` and swallow (no IDE crash — engineering contract §2).

## 4. External Data & Parsing
None. This feature consumes no CLI output, files, or network responses — it operates purely on the
in-memory PSI/`Document`. (Section retained per template; explicitly not applicable.)

## 5. Data Flow

### Example 1: Move a statement over its sibling (EDITOR-07-01)
Input `local a = 1<caret>\nlocal b = 2`, Ctrl+Shift+↓. `enclosingStatement` → `local a = 1`
(`LuaLocalVarDecl`); `statementRange` → lines [0,1); `targetStatement(down)` → `local b = 2`, range
[1,2). `info.toMove=[0,1)`, `info.toMove2=[1,2)`. Platform swaps → `local b = 2\nlocal a = 1`.

### Example 2: Move into a block (EDITOR-07-02)
Input `print(1)<caret>\nif x then\n  print(2)\nend`, Ctrl+Shift+↓. No sibling statement below
`print(1)` at top level in the down direction that is a plain statement adjacent — the next sibling
is the `if` block-parent. `blockBoundaryTarget(down)` returns the first-body-line range of the `if`
body, so `print(1)` moves to become the first statement inside `if x then … end`; `afterMove`
reindents it to the block indent.

### Example 3: Reorder an argument (EDITOR-07-03)
Input `f(a, <caret>b, c)`, Ctrl+Alt+Shift+→. Caret element's parent chain hits `LuaExprList`
`(a, b, c)`; `getMovableSubElements` returns `[a, b, c]`. Platform swaps `b`↔`c` keeping commas →
`f(a, c, b)`.

### Example 4: Line-move fallback (EDITOR-07-04)
Input a caret on a blank line or inside a multi-line string; `checkAvailable` returns `false`;
platform runs `LineMover` for a plain line swap.

## 6. Edge Cases
- **Multi-line string literal** under caret → return `false` (line mover), never split the literal.
- **Comment-only line** → `enclosingStatement` advances past the `PsiComment` to the owning
  statement; a leading `---@`/`--` comment block moves together with its statement (§3.1 rule).
- **First/last statement of file, no adjacent block** → `prohibitMove()` (no-op move, no corruption).
- **`repeat … until` / `if … elseif … else … end`** → siblings are found only *within* one
  `LuaBlock`; the `until expr` / `elseif`/`else` delimiters are not `LuaStatement`s, so
  `targetStatement` never selects them and the delimiter is never swapped away from its block.
- **Single-element list** (`f(a)`, `{x}`, `local a`) → `getMovableSubElements` returns one element;
  action is a no-op (platform requires ≥2).
- **Trailing field separator** (`{a, b,}`) → `LuaFieldList.getFieldList()` still returns `[a, b]`;
  the trailing `LuaFieldSep` is untouched.
- **Assignment `x, y = 1, 2`** → both `LuaVarList` (as `LuaExprList`? no — LHS is `varList`) and RHS
  `LuaExprList` are lists; only the RHS `LuaExprList` and name lists are declared movable in §3.2
  (LHS `varList` reordering is out of scope for this feature — see risks-and-gaps TBD).

## 7. Integration Points

```xml
<!-- src/main/resources/META-INF/plugin.xml, inside <extensions defaultExtensionNs="com.intellij"> -->
<statementUpDownMover
    id="LuaStatementMover"
    implementation="net.internetisalie.lunar.lang.editor.LuaStatementMover"/>
<lang.moveLeftRightHandler
    language="Lua"
    implementationClass="net.internetisalie.lunar.lang.editor.LuaMoveLeftRightHandler"/>
```
- `statementUpDownMover` is an application-level EP (`ExtensionPointName.create("com.intellij.statementUpDownMover")`,
  verified `StatementUpDownMover.java:26`) — registered with `implementation=` and an `id` (no
  `language` attribute; the mover self-guards on `file is LuaFile`).
- `moveLeftRightHandler` is a `LanguageExtension` (`MoveElementLeftRightHandler.java:14`), so it is
  registered as `lang.moveLeftRightHandler` with `language="Lua"` and `implementationClass=`
  (mirrors the 52 existing `language="Lua"` EP registrations in `plugin.xml`).
- New package `net.internetisalie.lunar.lang.editor` (new directory
  `src/main/kotlin/net/internetisalie/lunar/lang/editor/`) — shared home for the EDITOR epic's
  editor-action EPs.
- No settings keys, indexes, or stubs are added.

## 8. Requirement Coverage

| Requirement | Priority | Implemented by (section) |
|-------------|----------|--------------------------|
| EDITOR-07-01 Move statement up/down | M | §2.1, §3.1 (steps 1–6, `targetStatement`) |
| EDITOR-07-02 Enter/leave blocks | S | §2.1, §3.1 (`blockBoundaryTarget`), §3.3 (`afterMove` reindent) |
| EDITOR-07-03 Move element left/right | S | §2.2, §3.2 |
| EDITOR-07-04 Line-move fallback | C | §3.1 step 4 (`return false` → `LineMover`) |

## 9. Alternatives Considered
- **Groovy-style `allRanges` recursion** (`GroovyStatementMover.allRanges`): builds every line range
  in a scope via a `PsiRecursiveElementVisitor`. Rejected as over-engineered for Lua's simpler
  `LuaBlock`/`LuaStatement` model — the direct `targetStatement` + `blockBoundaryTarget` sibling walk
  is easier to specify, ≤30 lines/helper, and covers the same cases (Kotlin/Python movers use the
  same direct approach).
- **Registering the mover with `language="Lua"`**: the EP is application-level (not a
  `LanguageExtension`), so `language=` is not honoured — the mover must self-guard on `file is LuaFile`
  (matches `GroovyStatementMover`'s `file instanceof GroovyFileBase` guard).
- **Reordering assignment LHS (`x, y = …`)**: deferred (risks-and-gaps TBD) to keep separators-and-
  paired-RHS semantics out of scope; only RHS/arg/field/name lists reorder.

## 10. Open Questions

_None — feature has cleared the planning bar._
