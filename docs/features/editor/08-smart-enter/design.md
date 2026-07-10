---
id: "EDITOR-08-DESIGN"
title: "Technical Design"
type: "design"
parent_id: "EDITOR-08"
folders:
  - "[[features/editor/08-smart-enter/requirements|requirements]]"
---

# Technical Design: EDITOR-08 — Smart Enter (Complete Statement)

## 1. Overview

Ctrl+Shift+Enter (`EditorCompleteStatement`) completes a half-written Lua block or call.
We implement a `SmartEnterProcessorWithFixers` subclass registered on the Lua language,
with one small `Fixer` per construct plus one `FixEnterProcessor` that places the caret.

The processor never parses text with offset math for structural decisions: it drives off the
committed Lua PSI (including the `ERROR_ELEMENT`s the parser produces for the unbalanced
skeleton) and mutates the document with `Document.insertString`, then lets the base class
reformat via the existing formatter. All invocation happens inside the platform's write
action (the `EditorCompleteStatement` action wraps `SmartEnterProcessor.process` in a
`WriteCommandAction` — see `SmartEnterAction`), so fixers must not open their own write
command; they run on the EDT within that command.

### Grounding — verified symbols

Every platform symbol below was verified against `~/Documents/src/lua/intellij-community`;
every Lunar symbol against this repo.

| Symbol | Kind | Evidence |
| :--- | :--- | :--- |
| `com.intellij.lang.SmartEnterProcessorWithFixers` | base class (abstract) | `platform/lang-impl/src/com/intellij/lang/SmartEnterProcessorWithFixers.java:29` |
| `SmartEnterProcessorWithFixers.Fixer<P>` | abstract inner, `apply(editor, processor, element)` | same file `:233` |
| `SmartEnterProcessorWithFixers.FixEnterProcessor` | abstract inner, `doEnter(atCaret, file, editor, modified): boolean` | same file `:237` |
| `addFixers(vararg)` / `addEnterProcessors(vararg)` | registration hooks | same file `:220`, `:211` |
| `getStatementAtCaret(editor, file)` | protected, returns leaf near caret | `platform/lang-api/.../SmartEnterProcessor.java:48` |
| `registerUnresolvedError(int)` | records caret target | `SmartEnterProcessorWithFixers.java:245` |
| `reformat(PsiElement)` | reformats element range via `CodeStyleManager` | `SmartEnterProcessor.java:34` |
| EP `lang.smartEnterProcessor` | `LanguageExtensionPoint`, dynamic | `platform/platform-resources/src/META-INF/EditorExtensionPoints.xml:60` |
| Action id `EditorCompleteStatement` | `IdeActions.ACTION_EDITOR_COMPLETE_STATEMENT` | `platform/ide-core/.../IdeActions.java:60` |
| `LuaIfStatement` / `getExprList()` / `getBlockList()` | Lua PSI | `src/main/gen/net/internetisalie/lunar/lang/psi/LuaIfStatement.java` |
| `LuaWhileStatement.getExpr()` / `getBlock()` | Lua PSI | `.../LuaWhileStatement.java` |
| `LuaRepeatStatement.getExpr()` / `getBlock()` | Lua PSI | `.../LuaRepeatStatement.java` |
| `LuaNumericForStatement` / `LuaGenericForStatement` | Lua PSI | `.../LuaNumericForStatement.java`, `.../LuaGenericForStatement.java` |
| `LuaFuncDecl` / `LuaLocalFuncDecl` / `LuaFuncName` / `LuaParList` | Lua PSI | `.../LuaFuncDecl.java`, grammar `lua.bnf:162,174` |
| `LuaBlockParent { getBlockList() }` | marker for block-owning statements | `src/main/kotlin/.../lang/psi/LuaBaseElements.kt:179` |
| `LuaTokenTypes.{IF,THEN,DO,END,FUNCTION,REPEAT,UNTIL,LPAREN,RPAREN,LCURLY,RCURLY,LBRACK,RBRACK,IN}` | tokens | `src/main/kotlin/.../lang/lexer/LuaTokenTypes.kt:70–133` |
| `LuaFile` | file PSI root | used across `lang/format/LuaEnterHandlerDelegate.kt` |
| `LuaFormattingModelBuilder` (drives `LuaFormatBlock`) | existing formatter | `plugin.xml:319`, `src/main/kotlin/.../lang/format/LuaFormatBlock.kt` |

**Prior art check.** No `SmartEnterProcessor` exists in the repo (`find src -iname '*SmartEnter*'`
is empty) — this feature is net-new, not a duplication. The existing
`LuaEnterHandlerDelegate` / `completion/LuaEnterHandler` handle the *plain* Enter key and are a
**different** EP (`enterHandlerDelegate`, `plugin.xml:272,285,286`); Smart Enter does not touch
them. The `LuaPairedBraceMatcher` (`plugin.xml:343`) pair table is the runtime source we mirror
into the keyword-pair contract below.

## 2. Keyword-pair-table contract (dependency on EDITOR-01)

EDITOR-08 needs a single canonical table mapping an opener keyword/bracket token to its closer
and to the intermediate keyword a valid skeleton requires. EDITOR-01 (`EDITOR-01-05`) introduces
this table for keyword auto-close. **This is a shared-code soft dependency, not a blocking edge**
(see epic `requirements.md` §"Execution order & dependencies").

### 2.1 The keyword-block table: extend the existing `LuaBlockPairs`

**Epic reconciliation (2026-07-09).** The single source of truth for keyword-block pairs is the
**already-existing** `net.internetisalie.lunar.lang.syntax.LuaBlockPairs`
(`LuaBlockPairs.kt:15`, from COMP-08), which EDITOR-01 (`EDITOR-01-05`) also reuses via
`LuaKeywordBlockCloser`. EDITOR-08 does **not** introduce a parallel `LuaKeywordPairs` — that was a
planning-time divergence, now retired.

The grounding finding that drives this: `LuaBlockPairs` today keys on the **separator/terminator
leaf**, not the opener keyword —

```kotlin
terminatorByOpener = { THEN→END, DO→END, FUNCTION→END, REPEAT→UNTIL, LCURLY→RCURLY }  // keyed by THEN/DO leaves
insertTextFor      = { END→"end", UNTIL→"until", RCURLY→"}" }
```

That is enough for EDITOR-01 (auto-close fires *after* the user has typed `then`/`do`) but **not**
for EDITOR-08's separator fixer, which must supply `then`/`do` for a bare `if x` / `while c` /
`for …` skeleton where no separator leaf exists yet. So EDITOR-08 **extends `LuaBlockPairs`** with
two opener-keyword-keyed maps (additive; existing maps untouched):

```kotlin
// added to object LuaBlockPairs — keyed by the OPENER keyword leaf (IF/WHILE/FOR/FUNCTION/DO/REPEAT)
val separatorByOpenerKeyword: Map<IElementType, IElementType> =
    mapOf(IF to THEN, WHILE to DO, FOR to DO)            // FUNCTION/DO/REPEAT: no separator
val terminatorByOpenerKeyword: Map<IElementType, IElementType> =
    mapOf(IF to END, WHILE to END, FOR to END, FUNCTION to END, DO to END, REPEAT to UNTIL)
```

Keyed on `LuaElementTypes` (as the existing `LuaBlockPairs` maps are), covering both
`LuaNumericForStatement` and `LuaGenericForStatement` via the shared `FOR` leaf.

### 2.2 Ownership & ordering

`LuaBlockPairs` already exists, so there is no "create the file" handshake: the two opener-keyed
maps are an additive extension EDITOR-08 makes (EDITOR-01 does not need them — it defers `for`/`while`
scaffolding and works terminator-side only). For plain `end`/`until` insertion EDITOR-08 **may**
reuse EDITOR-01's `LuaKeywordBlockCloser`; but Smart Enter's fixers insert at computed offsets and
drive the caret through `registerUnresolvedError`, a different idiom than the Enter-handler closer,
so the block-end fixer may instead insert directly off the table. The **mandatory** shared artifact
is the table (`LuaBlockPairs`), not the insertion helper. See `risks-and-gaps.md` DR-01.

## 3. Components (files to create)

All under `src/main/kotlin/net/internetisalie/lunar/lang/smartenter/`.

### 3.1 `LuaSmartEnterProcessor`

`net.internetisalie.lunar.lang.smartenter.LuaSmartEnterProcessor : SmartEnterProcessorWithFixers()`

```kotlin
class LuaSmartEnterProcessor : SmartEnterProcessorWithFixers() {
    init {
        addFixers(
            LuaMissingBracketFixer(),      // EDITOR-08-02  (runs first: balances (){}[] )
            LuaBlockSeparatorFixer(),      // EDITOR-08-01  (inserts then/do)
            LuaBlockEndFixer(),            // EDITOR-08-01/03 (inserts end / until)
            LuaFunctionParenFixer(),       // EDITOR-08-01  (balances function param parens)
        )
        addEnterProcessors(LuaCaretPlacementEnterProcessor())  // EDITOR-08-04
    }

    override fun getStatementAtCaret(editor: Editor, psiFile: PsiFile): PsiElement? { … }
    override fun doNotStepInto(element: PsiElement): Boolean =
        element is LuaBlock || element is LuaStatement
}
```

- **`getStatementAtCaret`** (≤30 lines): call `super.getStatementAtCaret`; if it returns a
  `PsiWhiteSpace` or `null`, return `null` (→ base does a plain enter). Otherwise walk up with
  `PsiTreeUtil.getParentOfType(leaf, LuaBlockParent::class.java, false)` to find the enclosing
  block-owning statement; if none, return the leaf's enclosing `LuaStatement`. This mirrors
  `PySmartEnterProcessor.getStatementAtCaret` (`PySmartEnterProcessor.java:198`).
- **Threading:** no explicit read/write action inside — the platform action supplies the write
  command; PSI reads happen synchronously on the EDT within it. Fixers use only
  `Document.insertString` / `Editor.getCaretModel`, never a nested `WriteCommandAction`.

### 3.2 Fixers

Each `Fixer` overrides `apply(editor, processor, element)`; each stays ≤30 logic lines and takes
the 3 fixed args. Shared PSI-inspection helpers live in `LuaSmartEnterUtil` (§3.4) so fixers do
not mix raw traversal with orchestration (contract §3).

#### `LuaBlockEndFixer` (EDITOR-08-01, -03) — inserts the closer

Algorithm:
1. `val opener = openerKeywordFor(element) ?: return` — maps the PSI element type to its opener
   leaf: `LuaIfStatement`→`IF`, `LuaFuncDecl`/`LuaLocalFuncDecl`→`FUNCTION`, `LuaWhileStatement`→`WHILE`,
   `LuaNumericForStatement`/`LuaGenericForStatement`→`FOR`, `LuaDoStatement`→`DO`,
   `LuaRepeatStatement`→`REPEAT`. Return early for any other element.
   `val closer = LuaBlockPairs.terminatorByOpenerKeyword[opener] ?: return`.
2. `if (LuaSmartEnterUtil.hasCloser(element, closer)) return` — a closer already present as a
   direct child token means the block is already balanced; do nothing. `hasCloser` scans the
   element's own leaf children (not descendants) for a leaf whose `elementType == pair.closer`.
3. Compute insertion offset: `val insertAt = LuaSmartEnterUtil.blockBodyEndOffset(element)` — the
   offset just past the block body (end of `getBlockList().lastOrNull()`, else end of the
   separator token, else end of the header). See §3.4.
4. For `REPEAT` (`closer == UNTIL`): `document.insertString(insertAt, "\nuntil ")` and
   `processor.registerUnresolvedError(insertAt + "\nuntil ".length)` (caret lands after `until `,
   at the condition slot).
   For all others: `document.insertString(insertAt, "\nend")`. Caret placement for these is left
   to the separator fixer / caret processor (they land the caret in the body, not on `end`).

#### `LuaBlockSeparatorFixer` (EDITOR-08-01) — inserts `then` / `do`

Runs before the caret is finalized; ensures the mandatory intermediate keyword exists.
1. Map `element` → opener leaf via `openerKeywordFor` as above;
   `val sep = LuaBlockPairs.separatorByOpenerKeyword[opener] ?: return` (function/do/repeat are
   absent from the map → no separator → nothing to do).
2. `if (LuaSmartEnterUtil.hasChildToken(element, sep)) return`.
3. Determine the offset after which the separator belongs:
   - `LuaIfStatement` / `LuaWhileStatement`: after `getExprList()`/`getExpr()` — i.e.
     `condition?.textRange?.endOffset`. If the condition is missing (`if <caret>`), insert the
     separator right after the opener keyword and `registerUnresolvedError` at the gap so the
     caret returns to the empty condition (mirrors `PyConditionalStatementPartFixer.java:50`).
   - `LuaNumericForStatement`: after the last `getExprList()` element (the `1,n[,step]` range).
   - `LuaGenericForStatement`: after `getExprList()` (the iterator expressions following `in`).
4. `document.insertString(offset, " $separatorText")` where `separatorText` is `then` or `do`
   (from `pair.separator`'s presentable text; hard-coded map `THEN→"then"`, `DO→"do"` in
   `LuaSmartEnterUtil` to avoid depending on `IElementType.toString()` formatting).

#### `LuaMissingBracketFixer` (EDITOR-08-02) — balances `(` `{` `[`

Balances unclosed brackets **on the statement being completed**, so
`local t = { 1, 2` → `local t = { 1, 2 }` and `print("x"` → `print("x")`.
1. Restrict to the current statement's text range (`element.textRange`); collect its leaf tokens
   via `LuaSmartEnterUtil.leafTokens(element)`.
2. Single left-to-right pass maintaining a stack of opener tokens
   (`LPAREN`/`LCURLY`/`LBRACK`). On a matching closer, pop; on a non-matching closer, register an
   unresolved error at that offset and stop (malformed — let the user fix). Skip tokens inside
   `STRING`/`LONGSTRING`/comment tokens (they are single leaves, so naturally skipped).
3. For each opener left on the stack at end of pass (in reverse — innermost last opened, closed
   first), append its closer at the statement's end offset:
   insert `")"`, `"}"`, or `"]"` at `element.textRange.endOffset`. Because closers are appended at
   the same end offset in stack order (LIFO), the resulting order is correct nesting.

#### `LuaFunctionParenFixer` (EDITOR-08-01) — function parameter parens

For `function foo` / `function foo(` (a `LuaFuncDecl`/`LuaLocalFuncDecl`/`LuaFuncDef` whose
`getParList()`/param parens are incomplete):
1. If the decl has no `(` child token after the func name → insert `"()"` right after the
   `LuaFuncName` (or name ref) end offset, and `registerUnresolvedError` between the parens.
2. If there is a `(` but no matching `)` before end-of-header → this is handled by
   `LuaMissingBracketFixer`; this fixer only supplies the *pair* when neither paren exists.

### 3.3 `LuaCaretPlacementEnterProcessor` (EDITOR-08-04)

`... : SmartEnterProcessorWithFixers.FixEnterProcessor()`

`doEnter(atCaret, file, editor, modified): Boolean` returns `true` when it positions the caret
(so the base class stops). Placement rule, in priority order:
1. If `processor.registerUnresolvedError` recorded an offset (missing condition / empty parens /
   after `until `), the base class already moved the caret there in `doEnter`
   (`SmartEnterProcessorWithFixers.java:163`) — this processor returns `false` for that case and
   lets the base handle it. (We surface the "most likely edit" through `registerUnresolvedError`,
   which is the platform-canonical mechanism.)
2. Otherwise, for a freshly-closed block (`LuaBlockParent` with a now-present closer), move the
   caret to the **start of the (empty) body line**: compute the offset after the separator token
   (`then`/`do`) or after the header, insert a newline + indentation is done by reformat, then
   `editor.caretModel.moveToOffset(bodyLineOffset)` and return `true`. In practice this is
   achieved by inserting `"\n"` between the separator and `end` in `LuaBlockEndFixer` (the
   `"\nend"` already creates the body line) and landing the caret on that blank line here.
3. Return `false` if nothing applies (base falls back to `plainEnter`).

Keep `doEnter` ≤30 lines; delegate offset computation to `LuaSmartEnterUtil.bodyCaretOffset`.

### 3.4 `LuaSmartEnterUtil` (PSI-traversal helpers)

`net.internetisalie.lunar.lang.smartenter.LuaSmartEnterUtil` — an `object` holding the raw-PSI
inspection helpers, so fixers hold only orchestration (contract §3, "orchestration symmetry"):

```kotlin
object LuaSmartEnterUtil {
    fun openerKeywordFor(element: PsiElement): IElementType?      // PSI type → opener leaf (IF/WHILE/FOR/…)
    fun hasChildToken(element: PsiElement, token: IElementType): Boolean   // direct leaf child scan
    fun hasCloser(element: PsiElement, closer: IElementType): Boolean
    fun blockBodyEndOffset(element: PsiElement): Int
    fun leafTokens(element: PsiElement): List<PsiElement>          // PsiTreeUtil leaf collection
    fun separatorText(token: IElementType): String                // THEN→"then", DO→"do"
    fun bodyCaretOffset(element: PsiElement): Int
}
```

- `openerKeywordFor` maps by PSI class (`when (element) { is LuaIfStatement -> IF; is LuaWhileStatement -> WHILE; … }`);
  fixers then look up `LuaBlockPairs.separatorByOpenerKeyword[..]` / `.terminatorByOpenerKeyword[..]`.
- `hasChildToken`/`hasCloser` iterate `element.node.getChildren(null)` and compare `elementType`
  — no recursion, so partial/`ERROR_ELEMENT`-laden trees are handled by presence, not shape.
- `blockBodyEndOffset(element)`: `(element as? LuaBlockParent)?.getBlockList()?.lastOrNull()
  ?.textRange?.endOffset` ?: the separator token end ?: `element.textRange.endOffset`.

## 4. plugin.xml registration

Add one line inside the existing `<extensions defaultExtensionNs="com.intellij">` block
(sibling of the `lang.braceMatcher` entry at `plugin.xml:343`):

```xml
<lang.smartEnterProcessor
        language="Lua"
        implementationClass="net.internetisalie.lunar.lang.smartenter.LuaSmartEnterProcessor" />
```

No new action, keymap, or settings entry is required — the platform already binds
`EditorCompleteStatement` to Ctrl+Shift+Enter and dispatches to registered
`lang.smartEnterProcessor` extensions for the file's language.

## 5. Threading & contract conformance

- **Write context:** the `EditorCompleteStatement` action invokes `process` inside a
  `WriteCommandAction` (platform `SmartEnterAction`); fixers therefore mutate the document
  directly and must **not** wrap their own write command (would nest). Verified: `JsonSmartEnterTest`
  drives the processor inside `WriteCommandAction.runWriteCommandAction` (`JsonSmartEnterTest.java:17`).
- **Read context:** all PSI access is synchronous on the EDT within that write command; the base
  class calls `commit(editor)` to re-parse between attempts (`SmartEnterProcessorWithFixers.java:100`).
- **No hard refs:** processor and fixers are stateless singletons; they receive `Editor`/`PsiFile`
  per call and retain nothing (contract §4 HEAVY OBJECT RETENTION).
- **Method size / args:** one fixer per construct + `LuaSmartEnterUtil` keeps every function
  ≤30 logic lines and ≤3 args (contract §3). `getStatementAtCaret` and each `apply` are single-
  responsibility.
- **Reformat:** the base `doEnter` calls `reformat(atCaret)` (`SmartEnterProcessorWithFixers.java:165,172`)
  which routes through `CodeStyleManager` → the registered `LuaFormattingModelBuilder`
  (`plugin.xml:319`). We do not indent by hand.

## 6. Requirement → design map

| Requirement | Design section |
| :--- | :--- |
| `EDITOR-08-01` Close block keywords | §3.2 `LuaBlockEndFixer` + `LuaBlockSeparatorFixer` + `LuaFunctionParenFixer` |
| `EDITOR-08-02` Close brackets | §3.2 `LuaMissingBracketFixer` |
| `EDITOR-08-03` `repeat … until` | §3.2 `LuaBlockEndFixer` (REPEAT branch) |
| `EDITOR-08-04` Caret placement | §3.3 `LuaCaretPlacementEnterProcessor` + `registerUnresolvedError` usage |
| EP registration | §4 |

## 7. Open Questions

None. (Deferred handshakes — EDITOR-01 ordering, reformat-fidelity spot check — are tracked as
de-risking tasks DR-01/DR-02 in `risks-and-gaps.md`.)
