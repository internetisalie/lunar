---
id: "EDITOR-04-DESIGN"
title: "Technical Design"
type: "design"
parent_id: "EDITOR-04"
folders:
  - "[[features/editor/04-word-selection/requirements|requirements]]"
---

# Technical Design: EDITOR-04 — Smart Word Selection

## 1. Architecture Overview

### Current State
Lunar registers no `com.intellij.extendWordSelectionHandler`. `Edit | Extend Selection`
(Ctrl+W) therefore falls back to the platform default: `SelectWordHandler` walks the PSI
parent chain and adds the generic word/lexeme range plus each ancestor's `getTextRange()`.
For Lua this means the caret in `f(a, b)` jumps `a` → the whole `f(a, b)` call in one step,
skipping the useful "select all arguments" intermediate; a caret in `"hello"` selects the
literal *including* the quotes with no content-only step; a caret in a comment selects the
whole `-- text` line including the `--` marker; and a caret in a function body has no
"statements without the `function`/`end` shell" step.

### Prior Art in This Repo
Searched `src/main` for existing selection/word handlers and reusable helpers:
- `grep -rn "extendWordSelectionHandler\|ExtendWordSelectionHandler\|AbstractWordSelectioner"
  src/` → **no hits**. This is a greenfield EP for Lunar; nothing to replace.
- **Reusable string helpers** (extended, not duplicated):
  `net.internetisalie.lunar.lang.syntax.LuaLiterals.getLuaStringDelimiterLength(str: String): Int`
  (`syntax/LuaLiterals.kt:156`) returns the leading-delimiter length for `"`, `'`, and
  `[[`/`[=[`… long-bracket strings; the string-interior handler reuses it to compute the
  content range. `LuaStringForm` (`LuaLiterals.kt:108`) and `LuaStringConversionIntention`
  (`insight/LuaStringConversionIntention.kt:42` `stringLeafFor`) confirm the STRING-leaf idiom.
- **Long-comment delimiter idiom** (referenced, logic re-expressed here):
  `LuaLongCommentAnnotator` (`syntax/LuaAnnotators.kt:96`) computes the `--[==[` level by
  counting `=` after `--[`; the comment-interior handler uses the same rule for `LONGCOMMENT`.
- **PSI accessors** used below all exist in generated PSI (`src/main/gen/.../lang/psi/`):
  `LuaArgs.getExprList()/getTableConstructor()/getString()`, `LuaExprList.getExprList()`,
  `LuaFieldList.getFieldList()/getFieldSepList()`, `LuaTableConstructor.getFieldList()`,
  `LuaBlock.getStatementList()`, `LuaFuncCall`, `LuaIndexExpr`, `LuaStatement`, `LuaFuncDecl`,
  `LuaFuncDef`, `LuaTerminalExpr.getString()`.
- Element/token types: `LuaElementTypes.STRING` (`gen/.../psi/LuaElementTypes.java:123`),
  `SHORTCOMMENT` (`:122`), `LONGCOMMENT` (`:100`); token sets
  `LuaSyntax.StringLiteralTokens` / `CommentTokens` (`syntax/LuaSyntax.kt:35,42`).

### Target State
Register **four** dedicated `ExtendWordSelectionHandlerBase` subclasses under
`com.intellij.extendWordSelectionHandler`, each narrowly `canSelect`-gated on one construct
and each ≤30 logic lines. The platform default keeps providing the leaf-word and PSI-ancestor
ranges (identifier → call/index expr → statement → block-with-shell → function); our handlers
*insert intermediate steps* the default cannot infer:

```
caret in identifier   → [word]                        (platform default)
caret in string       → [content] → [full literal]    (LuaStringInteriorSelectioner)
caret in comment      → [text]    → [full comment]     (LuaCommentInteriorSelectioner)
caret in a list item  → [item] → [all items] → [list+brackets]  (LuaArgumentListSelectioner)
caret in a func body  → [statements] → [function…end] (platform ancestor)  (LuaBlockSelectioner)
```

Composition with the platform default (which unions all handlers' ranges and sorts them by
size) yields the ladder `EDITOR-04-01` requires: identifier → argument → call/index expr →
statement → block → enclosing function.

## 2. Core Components

All four classes live in the code-insight editor package
`net.internetisalie.lunar.lang.insight` (alongside `LuaFoldingBuilder`,
`LuaBreadcrumbsProvider`, `LuaStringConversionIntention`), extend
`com.intellij.codeInsight.editorActions.ExtendWordSelectionHandlerBase`, and are stateless
(no `Project`/`Editor`/`PsiFile` fields — contract §4 memory rule). Each is instantiated once
by the platform via the EP.

**Threading (all four):** the `canSelect`/`select` methods are invoked by the platform inside
a read action on the EDT during action processing; they only read PSI text ranges and return
`List<TextRange>`. No I/O, no write, no background work — no extra wrapping required.

**EP contract:** `boolean canSelect(PsiElement e)` and
`List<TextRange> select(PsiElement e, CharSequence editorText, int cursorOffset, Editor editor)`
(verified: `platform/lang-api/.../ExtendWordSelectionHandler.java:39,45`). `e` is the **leaf**
PSI element under the caret; handlers inspect `e` and/or `e.parent`.

### 2.1 net.internetisalie.lunar.lang.insight.LuaStringInteriorSelectioner
- **Responsibility**: inside a Lua `STRING` leaf, offer two steps — content only (delimiters
  stripped) then the full literal (`EDITOR-04-02`).
- **Threading**: EDT read action (platform-driven).
- **Collaborators**: `LuaElementTypes.STRING`,
  `LuaLiterals.getLuaStringDelimiterLength(String)`, `com.intellij.openapi.util.TextRange`.
- **Key API**:
  ```kotlin
  class LuaStringInteriorSelectioner : ExtendWordSelectionHandlerBase() {
      override fun canSelect(e: PsiElement): Boolean =
          e.node.elementType == LuaElementTypes.STRING
      override fun select(e: PsiElement, editorText: CharSequence, cursorOffset: Int, editor: Editor): List<TextRange>?
      // returns listOf(contentRange, e.textRange); see §3.1
  }
  ```

### 2.2 net.internetisalie.lunar.lang.insight.LuaCommentInteriorSelectioner
- **Responsibility**: inside a `SHORTCOMMENT` or `LONGCOMMENT` leaf, offer the comment text
  without the `--` / `--[==[ … ]==]` markers, then the full comment (`EDITOR-04-04`).
- **Threading**: EDT read action.
- **Collaborators**: `LuaElementTypes.SHORTCOMMENT`, `LuaElementTypes.LONGCOMMENT`, `TextRange`.
- **Key API**:
  ```kotlin
  class LuaCommentInteriorSelectioner : ExtendWordSelectionHandlerBase() {
      override fun canSelect(e: PsiElement): Boolean {
          val type = e.node.elementType
          return type == LuaElementTypes.SHORTCOMMENT || type == LuaElementTypes.LONGCOMMENT
      }
      override fun select(e: PsiElement, editorText: CharSequence, cursorOffset: Int, editor: Editor): List<TextRange>?
      // returns listOf(textRange, e.textRange); see §3.2
  }
  ```
  Note: `LUACATS_COMMENT` (`---@…`) is deliberately excluded — it has its own tag PSI and is
  out of scope for EDITOR-04 (see risks-and-gaps §Technical Debt).

### 2.3 net.internetisalie.lunar.lang.insight.LuaArgumentListSelectioner
- **Responsibility**: when the caret is inside an argument/expression/field list, add the
  intermediate "all items (no brackets)" step between a single item and the bracketed list
  (`EDITOR-04-03`). Handles call-arg lists (`LuaArgs`/`LuaExprList`) and table constructors
  (`LuaFieldList`).
- **Threading**: EDT read action.
- **Collaborators**: `LuaArgs`, `LuaExprList.getExprList()`, `LuaFieldList.getFieldList()`,
  `LuaTableConstructor`, `com.intellij.psi.util.PsiTreeUtil`, `TextRange`.
- **Key API**:
  ```kotlin
  class LuaArgumentListSelectioner : ExtendWordSelectionHandlerBase() {
      override fun canSelect(e: PsiElement): Boolean  // e is LuaExprList or LuaFieldList
      override fun select(e: PsiElement, editorText: CharSequence, cursorOffset: Int, editor: Editor): List<TextRange>?
      // returns the span from first-item start to last-item end; see §3.3
  }
  ```

### 2.4 net.internetisalie.lunar.lang.insight.LuaBlockSelectioner
- **Responsibility**: when the caret is inside a `LuaBlock`, add the "body statements only"
  step (first statement start .. last statement end) so the ladder offers body-without-shell
  before the enclosing `function … end` / `do … end` / `if … end` (`EDITOR-04-01`, block rung).
- **Threading**: EDT read action.
- **Collaborators**: `LuaBlock.getStatementList()`, `TextRange`.
- **Key API**:
  ```kotlin
  class LuaBlockSelectioner : ExtendWordSelectionHandlerBase() {
      override fun canSelect(e: PsiElement): Boolean = e is LuaBlock
      override fun select(e: PsiElement, editorText: CharSequence, cursorOffset: Int, editor: Editor): List<TextRange>?
      // returns listOf(TextRange(firstStmt.start, lastStmt.end)); see §3.4
  }
  ```

The identifier, call/index-expr, statement, and enclosing-function rungs of `EDITOR-04-01`
need **no** custom handler: the platform `SelectWordHandler` already adds `getTextRange()` for
each PSI ancestor (`LuaNameRef`/`LuaTerminalExpr` → `LuaFuncCall`/`LuaIndexExpr` →
`LuaStatement` → `LuaBlock` → `LuaFuncDecl`/`LuaFuncDef`), and the base class adds the leaf
word. Our four handlers only inject the intermediate ranges the default cannot compute.

## 3. Algorithms

Ranges returned by every handler are absolute document offsets (`TextRange`). The platform
merges all handlers' ranges, deduplicates, sorts by length, and steps through them on each
Ctrl+W (shrink = reverse). Handlers must return `null` (or the platform-provided base ranges)
when they cannot contribute, never throw.

### 3.1 String interior range
- **Input → Output**: `STRING` leaf `e` → `List<TextRange>` `[content, fullLiteral]`.
- **Steps**:
  1. `val raw = e.text`; `val start = e.textRange.startOffset`.
  2. `val delim = LuaLiterals.getLuaStringDelimiterLength(raw)` — 1 for `"`/`'`, ≥2 for
     `[[`/`[=[`… , 0 if unterminated/degenerate.
  3. If `delim == 0` or `raw.length < 2 * delim` → return `null` (let the default word ranges
     apply; e.g. an unterminated string).
  4. `contentStart = start + delim`; `contentEnd = e.textRange.endOffset - delim`.
  5. If `contentEnd < contentStart` → `contentEnd = contentStart` (empty content, e.g. `""`).
  6. Return `listOf(TextRange(contentStart, contentEnd), e.textRange)`.
- **Rules / edge handling**: long strings with a leading newline after `[[` are *not*
  specially trimmed here (we select the raw content between delimiters, matching what the user
  sees); unterminated strings fall through to the platform default.
- **Complexity**: O(delimiter length).

### 3.2 Comment interior range
- **Input → Output**: `SHORTCOMMENT`|`LONGCOMMENT` leaf `e` → `[text, fullComment]`.
- **Steps**:
  1. `val raw = e.text`; `val start = e.textRange.startOffset`; `val end = e.textRange.endOffset`.
  2. **Short comment** (`SHORTCOMMENT`, text begins `--`): `prefix = 2`. Skip any spaces
     immediately after `--`: advance `prefix` while `raw[prefix]` is `' '` or `'\t'` and
     `prefix < raw.length`. `textStart = start + prefix`; `textEnd = end`.
  3. **Long comment** (`LONGCOMMENT`, text begins `--[`): compute level by counting `=`
     after `--[` (`var level = 0; while (raw[level + 3] == '=') level++`) — same rule as
     `LuaLongCommentAnnotator` (`syntax/LuaAnnotators.kt:103`). Opening marker length =
     `level + 4` (`--[` + `level` `=` + `[`); closing marker length = `level + 2` (`]` +
     `level` `=` + `]`). `textStart = start + level + 4`; `textEnd = end - (level + 2)`.
  4. If the computed `textStart >= textEnd` → return `null` (empty/degenerate comment).
  5. Return `listOf(TextRange(textStart, textEnd), e.textRange)`.
- **Rules / edge handling**: a bare `--` with no text → step 4 returns `null`. A short comment
  that does not start with `--` (should not occur) → return `null`. Guard the long-comment `=`
  scan with a bounds check (`level + 3 < raw.length`) to avoid `IndexOutOfBounds` on a
  truncated `--[`.

### 3.3 Argument / field list range
- **Input → Output**: `LuaExprList` or `LuaFieldList` `e` → `[allItems]` (single-element list),
  where `allItems = TextRange(firstItem.start, lastItem.end)`.
- **`canSelect`**: `e is LuaExprList || e is LuaFieldList`.
- **Steps**:
  1. Collect item elements: for `LuaExprList` → `e.exprList` (`List<LuaExpr>`); for
     `LuaFieldList` → `e.fieldList` (`List<LuaField>`).
  2. If the list is empty → return `null`.
  3. `firstItem = items.first()`; `lastItem = items.last()`.
  4. Return `listOf(TextRange(firstItem.textRange.startOffset, lastItem.textRange.endOffset))`.
- **Rules / edge handling**: the *bracketed* list (`(…)` for a call, `{…}` for a table) is
  the parent's `getTextRange()`, added by the platform default — we only add the un-bracketed
  span. The single-item range is the item PSI's own `getTextRange()`, also added by the
  default. So this handler contributes exactly the missing "all items, no brackets" rung.
- **Complexity**: O(1) (first/last only).

### 3.4 Block body range
- **Input → Output**: `LuaBlock` `e` → `[bodyStatements]`.
- **Steps**:
  1. `val statements = e.statementList` (`List<LuaStatement>`).
  2. If empty → return `null`.
  3. Return `listOf(TextRange(statements.first().textRange.startOffset,
     statements.last().textRange.endOffset))`.
- **Rules / edge handling**: the enclosing `function … end` (or `do`/`if`/`while` shell) is the
  `LuaBlock`'s parent range, added by the platform default. This handler adds only the
  shell-free body rung.
- **Complexity**: O(1).

### 3.5 `canSelect` dispatch precondition (all handlers)
Every `select` re-checks its own `canSelect` guard implicitly by construction (the leaf's
element type / PSI class). The platform calls `select` only for handlers whose `canSelect`
returned true, but handlers still return `null` on any degenerate input (empty content, empty
list, unterminated literal) rather than an invalid `TextRange`.

## 4. External Data & Parsing
Not applicable. This feature consumes no CLI output, files, or network responses — only the
in-memory PSI tree and the editor document text already loaded by the platform.

## 5. Data Flow

### Example 1: argument in a call — `print(x, y)` with caret inside `x`
1. User presses Ctrl+W. Platform locates the leaf `x` (`LuaNameRef`/identifier).
2. Base word range = `x` (offsets of `x`). Platform ancestor ranges add `LuaExpr` (`x`),
   `LuaExprList` (`x, y`) parent, `LuaArgs` (`(x, y)`), `LuaFuncCall` (`print(x, y)`),
   `LuaExprStatement`, `LuaBlock`, file.
3. `LuaArgumentListSelectioner.canSelect(LuaExprList)` → true; `select` returns
   `TextRange` covering `x, y` (start of `x` .. end of `y`) — the un-bracketed list step.
4. Merged, size-sorted ladder: `x` → `x, y` → `(x, y)` → `print(x, y)` → statement → block.
   Requirement `EDITOR-04-03` satisfied (item → all items → list+brackets).

### Example 2: caret inside `"hello"`
1. Leaf under caret is the `STRING` token `"hello"`.
2. `LuaStringInteriorSelectioner.canSelect` → true; `select` returns
   `[TextRange(hello), TextRange("hello")]` (content, full literal) via §3.1.
3. Ladder: `hello` → `"hello"` → surrounding expr/arg/statement (`EDITOR-04-02`).

### Example 3: caret inside a function body statement
1. Leaf is a token inside `function f() local a = 1; return a end`.
2. Platform ancestors add the statement, the `LuaBlock`, and the `LuaFuncDecl`.
3. `LuaBlockSelectioner.canSelect(LuaBlock)` → true; `select` returns the body span
   (`local a = 1; return a`) via §3.4.
4. Ladder: statement → body-statements → `function f() … end` (`EDITOR-04-01`, block rung).

## 6. Edge Cases
- **Unterminated string** (`"abc`): `getLuaStringDelimiterLength` returns 1 but
  `raw.length < 2*delim` may hold for `"`; §3.1 step 3/5 clamps to empty content and still
  returns the literal range — no crash.
- **Empty string `""` / empty long `[[]]`**: content range is empty (`contentStart ==
  contentEnd`); the platform tolerates a zero-length range and just uses the literal step.
- **Bare `--` comment**: §3.2 step 4 returns `null`.
- **Long comment across many lines** (`--[[ … ]]`): §3.2 long-comment branch strips both
  multi-char markers; caret anywhere inside still selects interior text.
- **Single-argument call `f(a)`**: `LuaArgumentListSelectioner` returns the `a` span, which
  coincides with the item's own range — harmless duplicate, the platform dedups.
- **Method call `obj:m(a, b)`**: the arg list is still a `LuaExprList` inside `LuaArgs`;
  handler behaves identically.
- **Table constructor `{1, 2, 3}`**: `LuaFieldList` path (§3.3) adds `1, 2, 3` (no braces)
  between the single field and `{…}`.
- **Caret in whitespace between tokens**: the leaf is whitespace; none of the four
  `canSelect` guards match → platform default only; no regression.

## 7. Integration Points

Register the four handlers declaratively in the existing
`<extensions defaultExtensionNs="com.intellij">` block of
`src/main/resources/META-INF/plugin.xml` (the same block that already holds
`enterHandlerDelegate`, `lang.braceMatcher`, etc.). The EP is **not** language-scoped — it has
no `language` attribute (verified against `platform-resources/.../EditorExtensionPoints.xml:17`
and the platform's own registrations in `LangExtensions.xml:738-744`); handlers self-filter
via `canSelect`.

```xml
<!-- plugin.xml — inside <extensions defaultExtensionNs="com.intellij"> -->
<extendWordSelectionHandler
    implementation="net.internetisalie.lunar.lang.insight.LuaStringInteriorSelectioner"/>
<extendWordSelectionHandler
    implementation="net.internetisalie.lunar.lang.insight.LuaCommentInteriorSelectioner"/>
<extendWordSelectionHandler
    implementation="net.internetisalie.lunar.lang.insight.LuaArgumentListSelectioner"/>
<extendWordSelectionHandler
    implementation="net.internetisalie.lunar.lang.insight.LuaBlockSelectioner"/>
```

No new settings, indexes, or services. No interaction with EDITOR-01/-05/-08 (this feature
adds only read-only range providers).

## 8. Requirement Coverage

| Requirement | Priority | Implemented by (section) |
|-------------|----------|--------------------------|
| EDITOR-04-01 Construct ladder | M | Platform default ancestor ranges + §2.4/§3.4 (block rung); composition in §1/§5 |
| EDITOR-04-02 String interior | S | §2.1, §3.1 |
| EDITOR-04-03 Argument/field lists | S | §2.3, §3.3 |
| EDITOR-04-04 Comment interior | C | §2.2, §3.2 |

## 9. Alternatives Considered
- **One monolithic handler** covering all constructs: rejected — violates contract §3 (≤30
  logic lines/function) and the requirements' explicit "small dedicated handlers per construct"
  directive. Four narrow `canSelect` guards keep each `select` trivial.
- **Extending `AbstractWordSelectioner`** (which force-adds word/lexeme ranges) instead of
  `ExtendWordSelectionHandlerBase`: rejected for the list/block handlers — they should add only
  their structural range, not re-add the word range (the platform's own `WordSelectioner`
  already covers that). `ExtendWordSelectionHandlerBase` gives us `select` returning just our
  ranges.
- **Custom `SelectWordUtil.addWordHonoringEscapeSequences` for strings** (as JSON does):
  rejected as over-engineered — Lua smart selection does not need per-escape-sequence
  sub-steps; content-vs-delimiters is the requirement. Reuse `getLuaStringDelimiterLength`.

## 10. Open Questions

_None — feature has cleared the planning bar._
