---
id: "EDITOR-05-DESIGN"
title: "Technical Design"
type: "design"
parent_id: "EDITOR-05"
folders:
  - "[[features/editor/05-surround-with/requirements|requirements]]"
---

# Technical Design: EDITOR-05 — Surround With

TDD for the real `Surround With` action (Ctrl+Alt+T) on Lua statement lists: a single
`SurroundDescriptor` whose `getElementsToSurround` returns whole `LuaStatement`s from the
enclosing `LuaBlock`, plus one `Surrounder` per template (`if`, `while`, numeric `for`,
generic `for`, anonymous `function`, bare `do`, `pcall`). Each surrounder rebuilds the wrapped
region from text via `LuaElementFactory.createFile`, replaces the span inside a
`WriteCommandAction`, reformats with `CodeStyleManager`, and returns a `TextRange` to place the
caret (in the condition/header for `if`/`while`/`for`, in the body for `do`/`function`/`pcall`).

## 1. Architecture Overview

### Current State
No `SurroundDescriptor` exists for Lua. `grep -rn "surroundDescriptor" src/main/resources/META-INF/plugin.xml` → no hit.
COMP-07 shipped four **live-template** surrounds (`src/main/resources/liveTemplates/lua.xml` lines 84–103:
`surr_if`, `surr_for`, `surr_do`, `surr_fn`) gated by `LuaSurroundContextType`
(`src/main/kotlin/net/internetisalie/lunar/lang/completion/templates/LuaSurroundContextType.kt:12`).
Those use the `com.intellij.liveTemplateContext` EP and `$SELECTION$` expansion — a **different
extension point** from `com.intellij.lang.surroundDescriptor`. This feature adds the
`SurroundDescriptor` machinery (proper picker + caret placement); it does **not** touch or remove
the live templates.

### Prior Art in This Repo
- **`LuaInvertIfIntention`** (`src/main/kotlin/net/internetisalie/lunar/lang/insight/LuaInvertIfIntention.kt:29-47`)
  — the canonical pattern this design reuses: build replacement text, parse it with
  `LuaElementFactory.createFile(project, replacementText)`, `PsiTreeUtil.findChildOfType` the new
  node, `ifStmt.replace(newIf)`, then `CodeStyleManager.getInstance(project).reformat(replaced)` —
  all inside `WriteCommandAction.runWriteCommandAction(project)`. EDITOR-05 **extends this pattern**;
  it does not replace `LuaInvertIfIntention`.
- **`LuaElementFactory`** (`src/main/kotlin/net/internetisalie/lunar/lang/psi/LuaElementFactory.kt:41`)
  — `createFile(project, text): LuaFile` is the from-text PSI builder. **Reused**, not modified.
- **`LuaBlock`** (`src/main/gen/net/internetisalie/lunar/lang/psi/LuaBlock.java:10`) with
  `getStatementList(): List<LuaStatement>` and **`LuaStatement`**
  (`src/main/gen/net/internetisalie/lunar/lang/psi/LuaStatement.java:8`) — the statement-list PSI the
  descriptor operates on. `LuaBlock` is the parent of every statement (bnf
  `block ::= not_eof {statement}* ...`, `lua.bnf:97`).
- **COMP-07 live templates** (`liveTemplates/lua.xml`) — **complementary, not duplicated** (different EP,
  as the epic requirements.md lines 23–26 state).
- No existing `SurroundDescriptor`, `Surrounder`, or "block structure helper" utility found —
  searched `grep -rn "SurroundDescriptor\|Surrounder\|BlockStructure\|enclosingBlock" src/main`.

### Target State
```
com.intellij.lang.surroundDescriptor (language="Lua")
        └── LuaStatementsSurroundDescriptor            (getElementsToSurround / getSurrounders)
                └── LuaStatementSurrounder (abstract)   (WriteCommandAction + reformat + caret)
                        ├── LuaIfSurrounder
                        ├── LuaWhileSurrounder
                        ├── LuaNumericForSurrounder
                        ├── LuaGenericForSurrounder
                        ├── LuaFunctionSurrounder
                        ├── LuaDoSurrounder
                        └── LuaPcallSurrounder
        uses ── LuaBlockStructure   (shared PSI helpers; EDITOR-06 will reuse)
```

## 2. Core Components

### 2.1 `net.internetisalie.lunar.lang.editor.LuaBlockStructure`
- **Responsibility**: Shared block-structure PSI helpers for statement-list surrounds/unwraps.
  **Canonical shared object** (epic reconciliation, 2026-07-09): this feature contributes the
  range/replace API below; EDITOR-06 (Unwrap) adds `primaryBody`/`ifBranches`/`hasElseOrElseIf`/
  `blockParent` to the **same** `lang.editor.LuaBlockStructure` object. Whichever feature lands
  first creates the file; the second extends it — never a second competing helper.
- **Threading**: pure PSI reads; callers already hold a read action (surround) or a write command.
  Contains no state; a Kotlin `object`. Holds **no** references to `Project`/`Editor`/`PsiFile`.
- **Collaborators**: `LuaBlock`/`LuaStatement` (gen PSI), `PsiTreeUtil`, `LuaElementFactory`,
  `CodeStyleManager`.
- **Key API**:
  ```kotlin
  object LuaBlockStructure {
      // The LuaBlock that directly contains the offset range, or null (offset in a comment/whitespace-only file).
      fun enclosingBlock(file: PsiFile, startOffset: Int, endOffset: Int): LuaBlock?

      // Whole LuaStatements of `block` whose text range is fully covered by [startOffset,endOffset]
      // after trimming leading/trailing whitespace. Empty if the selection splits a statement.
      fun statementsInRange(block: LuaBlock, startOffset: Int, endOffset: Int): List<LuaStatement>

      // Concatenated source text of a contiguous statement run, joined with '\n'.
      fun statementsText(statements: List<LuaStatement>): String

      // Replace the PSI span [first..last] with `replacement`'s children, returning the inserted range.
      // Reindents via CodeStyleManager.reformatRange. Caller supplies an already-parsed replacement stmt.
      fun replaceStatements(first: LuaStatement, last: LuaStatement, replacement: PsiElement): TextRange
  }
  ```

### 2.2 `net.internetisalie.lunar.lang.surround.LuaStatementsSurroundDescriptor`
- **Responsibility**: Entry point registered on `com.intellij.lang.surroundDescriptor`; finds the
  statement run under the selection and exposes the seven surrounders.
- **Threading**: `getElementsToSurround` is invoked by the platform under a read action.
- **Collaborators**: `LuaBlockStructure`, the seven `Surrounder`s.
- **Key API**:
  ```kotlin
  class LuaStatementsSurroundDescriptor : SurroundDescriptor {
      override fun getElementsToSurround(file: PsiFile, startOffset: Int, endOffset: Int): Array<PsiElement>
      override fun getSurrounders(): Array<Surrounder>  // 7 instances, order = §3.4
      override fun isExclusive(): Boolean = false
  }
  ```
  `getElementsToSurround` = `enclosingBlock(...)?.let { statementsInRange(it, start, end) }?.toTypedArray()
  ?: PsiElement.EMPTY_ARRAY`. When no statement is selected (caret with no selection), the platform
  passes `startOffset == endOffset`; `statementsInRange` returns the single statement covering the caret.

### 2.3 `net.internetisalie.lunar.lang.surround.LuaStatementSurrounder` (abstract base)
- **Responsibility**: Shared surround skeleton — validate, build wrapped text, replace, reformat,
  compute caret. Subclasses supply only the template shape.
- **Threading**: `surroundElements` runs under the platform write command (`startInWriteAction()`
  defaults to `true` via `WriteActionAware`); it wraps the mutation in
  `WriteCommandAction.runWriteCommandAction` defensively per the InvertIf precedent and the contract.
- **Collaborators**: `LuaBlockStructure`, `LuaElementFactory`, `CodeStyleManager`.
- **Key API**:
  ```kotlin
  abstract class LuaStatementSurrounder : Surrounder {
      final override fun isApplicable(elements: Array<PsiElement>): Boolean =
          elements.isNotEmpty() && elements.all { it is LuaStatement }

      final override fun surroundElements(project: Project, editor: Editor, elements: Array<PsiElement>): TextRange?

      // Template contract implemented by each subclass:
      protected abstract fun getTemplateDescription(): String     // Surrounder API
      protected abstract fun buildWrappedText(bodyText: String): WrappedTemplate
  }

  // Result of a template build: the full replacement source and the caret marker.
  data class WrappedTemplate(val text: String, val caretMarker: String)
  ```
  See §3.1 for `surroundElements` steps and §3.2 for the caret-marker protocol.

### 2.4 The seven concrete surrounders
Each is a `LuaStatementSurrounder` subclass that only implements `getTemplateDescription()` and
`buildWrappedText(bodyText)`. FQCNs and template shapes (`§CARET§` is the caret marker literal,
`§BODY§` is `bodyText`):

| Class | Description | `buildWrappedText` output (before marker stripping) | Req |
|---|---|---|---|
| `…surround.LuaIfSurrounder` | `if` | `if §CARET§ then\n§BODY§\nend` | -01 |
| `…surround.LuaWhileSurrounder` | `while` | `while §CARET§ do\n§BODY§\nend` | -02 |
| `…surround.LuaNumericForSurrounder` | `for (numeric)` | `for §CARET§ = 1, 10 do\n§BODY§\nend` | -02 |
| `…surround.LuaGenericForSurrounder` | `for (generic)` | `for §CARET§ in pairs(t) do\n§BODY§\nend` | -02 |
| `…surround.LuaFunctionSurrounder` | `function` | `function()\n§CARET§§BODY§\nend` | -03 |
| `…surround.LuaDoSurrounder` | `do` | `do\n§CARET§§BODY§\nend` | -04 |
| `…surround.LuaPcallSurrounder` | `pcall` | `pcall(function()\n§CARET§§BODY§\nend)` | -05 |

For `function`/`do`/`pcall` the caret marker sits at the start of the body line (empty-condition
templates) so the caret lands at the body's first character after reformat.

## 3. Algorithms

### 3.1 `surroundElements` (base skeleton)
- **Input → Output**: `(project, editor, elements: Array<PsiElement>) → TextRange?`
- **Steps**:
  1. `val statements = elements.map { it as LuaStatement }`; if empty → return `null`.
  2. `val first = statements.first()`, `val last = statements.last()`.
  3. `val bodyText = LuaBlockStructure.statementsText(statements)`.
  4. `val wrapped = buildWrappedText(bodyText)` → `WrappedTemplate(text, caretMarker)`.
  5. Locate the caret marker: `val caretIndexInText = wrapped.text.indexOf(wrapped.caretMarker)`;
     `val cleanText = wrapped.text.removeRange(caretIndexInText, caretIndexInText + wrapped.caretMarker.length)`.
  6. Inside `WriteCommandAction.runWriteCommandAction(project)`:
     a. `val dummy = LuaElementFactory.createFile(project, cleanText)`.
     b. `val newStmt = PsiTreeUtil.findChildOfType(dummy, LuaStatement::class.java) ?: return@… null`.
     c. `val insertedRange = LuaBlockStructure.replaceStatements(first, last, newStmt)` (reformats).
  7. Return the caret `TextRange`: recompute the marker's document offset — the caret document offset is
     `insertedRange.startOffset + relativeCaretOffset`, where `relativeCaretOffset` is `caretIndexInText`
     mapped through the same leading text of `cleanText` (marker is on a fixed prefix line, so
     `relativeCaretOffset == caretIndexInText`). Return `TextRange(caretOffset, caretOffset)`.
     (Empty range → caret placed, no selection.)
- **Rules / edge handling**:
  - Empty `elements` → `null` (no-op; platform shows nothing).
  - Reformat is scoped to the inserted range via `CodeStyleManager.reformatRange` so surrounding code
    is untouched.
  - The marker is a private-use sentinel string (`"⁣CARET⁣"`) that cannot appear in Lua
    source, so `indexOf` is unambiguous.
- **Complexity**: O(n) in selected-statement source length; single reparse of the wrapped region.

### 3.2 Caret-marker protocol
- Each template embeds the sentinel marker exactly once at the intended caret site
  (condition for `if`/`while`/`for`; body start for `do`/`function`/`pcall`).
- The base strips the marker (step 5) before parsing so the parsed PSI is valid Lua, then maps the
  marker's pre-strip character index to a post-insert document offset (step 7). Because every template's
  marker lies on the wrapper's fixed leading text (before `§BODY§`), the index is stable across the
  body content.

### 3.3 `LuaBlockStructure.statementsInRange`
- **Input → Output**: `(block, startOffset, endOffset) → List<LuaStatement>`
- **Steps**:
  1. `val stmts = block.statementList`.
  2. If `startOffset == endOffset` (no selection): return the single statement whose text range
     `contains(startOffset)`, or empty.
  3. Otherwise trim: skip leading whitespace/newlines so `startOffset` lands on the first non-blank
     char; likewise pull `endOffset` back off trailing whitespace (mirrors JsonSurroundDescriptor
     lines 34–47).
  4. Return the maximal contiguous run of `stmts` where each statement's range is fully within
     `[trimmedStart, trimmedEnd]`. If the trimmed range partially covers any boundary statement
     (splits it), return **empty** (selection is not a whole-statement run).
- **Rules**: statements must be adjacent siblings of the same `LuaBlock`; a selection spanning two
  different blocks (e.g. across an `end`) yields empty because `enclosingBlock` returns the innermost
  block containing both offsets and the outer statements won't be in its `statementList`.

### 3.4 Surrounder ordering
`getSurrounders()` returns, in picker order: `LuaIfSurrounder`, `LuaWhileSurrounder`,
`LuaNumericForSurrounder`, `LuaGenericForSurrounder`, `LuaFunctionSurrounder`, `LuaDoSurrounder`,
`LuaPcallSurrounder` (Must first, then Should, then Could — matching requirement priorities).

## 4. External Data & Parsing
None. This feature consumes no CLI/file/network input; all input is in-memory PSI and editor
selection offsets. The only "parsing" is re-parsing our own generated Lua text through
`LuaElementFactory.createFile`, which uses the plugin's own parser.

## 5. Data Flow

### Example 1: Surround two statements with `if`
Input (`<sel>` marks selection):
```lua
<sel>foo()
bar()</sel>
```
1. Platform calls `getElementsToSurround(file, start, end)`; `enclosingBlock` → the file's root
   `LuaBlock`; `statementsInRange` → `[foo() exprStmt, bar() exprStmt]`.
2. User picks "if". `LuaIfSurrounder.surroundElements`:
   - `bodyText = "foo()\nbar()"`.
   - `buildWrappedText` → `"if §CARET§ then\nfoo()\nbar()\nend"`; strip marker → `"if  then\nfoo()\nbar()\nend"`.
   - Parse, `replaceStatements` swaps the two statements for the new `LuaIfStatement`, reformats range.
3. Result + caret between `if ` and ` then`:
```lua
if <caret> then
    foo()
    bar()
end
```

### Example 2: Surround with `do`
`bodyText = "x = 1"` → `"do\n§CARET§x = 1\nend"` → strip → `"do\nx = 1\nend"`; caret at body start:
```lua
do
    <caret>x = 1
end
```

## 6. Edge Cases
- **No selection, caret on a statement** → `statementsInRange` returns that one statement (§3.3 step 2).
- **Selection splits a statement** (e.g. mid-expression) → empty array → descriptor not offered.
- **Selection spans nested blocks** → `enclosingBlock` narrows to the innermost common `LuaBlock`;
  outer statements aren't its children → empty.
- **Empty file / whitespace-only selection** → `enclosingBlock` null or empty run → no-op.
- **Body already indented** → `reformatRange` normalizes indentation after wrapping (no double indent),
  same as `LuaInvertIfIntention` relies on `reformat`.
- **`function`/`pcall` wrapping statements that use `return`/`break`** → syntactically wrapped verbatim;
  semantic correctness is the user's call (matches JetBrains-language surround behavior; noted in risks).

## 7. Integration Points

```xml
<!-- src/main/resources/META-INF/plugin.xml, in the existing <extensions defaultExtensionNs="com.intellij"> block,
     alongside the other language="Lua" registrations (e.g. near the annotators, ~line 130). -->
<lang.surroundDescriptor
        language="Lua"
        implementationClass="net.internetisalie.lunar.lang.surround.LuaStatementsSurroundDescriptor"/>
```
No new settings, indexes, actions, or icons. The Ctrl+Alt+T keymap and the template-picker popup are
provided by the platform's `SurroundWithHandler`; registering the one descriptor is sufficient.

## 8. Requirement Coverage

| Requirement | Priority | Implemented by (section) |
|-------------|----------|--------------------------|
| EDITOR-05-01 (`if … end`) | M | §2.2, §2.3, §2.4 (`LuaIfSurrounder`), §3.1 |
| EDITOR-05-02 (`while`/`for`) | S | §2.4 (`LuaWhileSurrounder`, `LuaNumericForSurrounder`, `LuaGenericForSurrounder`), §3.1 |
| EDITOR-05-03 (`function`) | S | §2.4 (`LuaFunctionSurrounder`), §3.1 |
| EDITOR-05-04 (`do … end`) | S | §2.4 (`LuaDoSurrounder`), §3.1 |
| EDITOR-05-05 (`pcall`) | C | §2.4 (`LuaPcallSurrounder`), §3.1 |

## 9. Alternatives Considered
- **PSI-surgery construction** (build the wrapper node and splice statements as `LuaBlock` children,
  as GroovyManyStatementsSurrounder does) — rejected: Lunar's generated PSI has no ergonomic
  block-insertion API, and the text+reparse+reformat path is already proven in `LuaInvertIfIntention`
  and far fewer lines. Keeps each surrounder under the 30-line/method cap.
- **Reusing COMP-07 live templates via a programmatic `TemplateManager`** — rejected: that is the
  wrong EP (no proper picker, no `SurroundDescriptor` semantics) and the epic explicitly wants both to
  coexist.
- **IIFE-invoked `function` variant** (`(function() … end)()`) — deferred; requirement -03 marks it
  "optionally". Shipped as a plain `function() … end`; IIFE tracked in risks as future work.

## 10. Open Questions
_None — feature has cleared the planning bar._
