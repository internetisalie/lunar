---
id: FORMAT-01
title: "01: Basic Indentation"
type: feature
status: "done"
vf_icon: ✅
priority: "medium"
parent_id: FORMAT
folders: ["[[features/formatting/requirements|requirements]]"]
---

# 01: Basic Indentation

Put every line of a Lua file at the column its enclosing constructs imply — on **Reformat Code**, on
**Auto-Indent Lines**, and on **Enter** — and leave literal text alone while doing it.

## How these requirements were derived

**Not from Lunar's formatter.** A specification read off its own implementation cannot fail, which is
the defect that left [[DEBUG-07]] marked shipped for months (see [[BUG-450]] §4). The rows below were
enumerated from three sources outside this repo, and only *then* checked against
`lang/format/LuaFormatBlock.kt`:

1. **The Lua language.** Every production in the reference grammar that opens a body is one row:
   `do…end`, `while…do…end`, `repeat…until`, `if/elseif/else…end`, numeric and generic `for…do…end`,
   the four function forms (statement, method, `local`, anonymous), and the table constructor. Every
   production that spans lines *without* opening a body is also one row, because each needs a
   deliberate answer: parenthesised expressions, argument and parameter lists, call/method chains,
   binary-operator continuations, `::label::`, the `#!` first line, and the two literal forms —
   long strings `[[…]]` and long comments `--[[…]]` — whose contents must **not** move, because
   moving them changes the program's data.
2. **The IntelliJ formatter contract.** `Block`, `Indent`, `Alignment`, `SpacingBuilder`,
   `FormattingModel` and `LineIndentProvider` in
   `~/Documents/src/lua/intellij-community/platform/code-style-api`. Two of that contract's clauses
   generate rows on their own: `FormattingModel.shiftIndentInsideRange` — *"indents every line except
   for the first in the specified text range representing a multiline block"* — is the mechanism by
   which a formatter corrupts a long string, and `Block.getChildAttributes(newChildIndex)` is the
   single method that decides what Enter does.
3. **Lua community convention** — 2-space indentation is near-universal in hand-written Lua and in
   the `.editorconfig` files shipped with Lua projects. This repo's own `.editorconfig` says nothing
   about `*.lua` (it covers `*.kt`/`*.kts` only), so the default comes from the platform, not from us.

Statuses marked **(unverified)** are read off source — plugin source, platform source, or an existing
assertion's *absence* — and could not be settled without executing the formatter, which this pass did
not do. They are not claims that the behaviour is broken; they are claims that nothing establishes it.
**Eight rows therefore carry no status keyword at all.** That is deliberate: entering `Full` or
`Not Implemented` there would be the same defect this document was written to correct — asserting an
outcome from reading rather than from running.

Registration and settings UI are [[FORMAT-02]]'s; wrapping is [[FORMAT-04]]'s; `=` alignment is
[[FORMAT-05]]'s; delegating the whole job to StyLua is [[FORMAT-07]]'s. This feature owns *where the
line starts*.

## Requirements & Status

| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :---: | :---: | :--- |
| `FORMAT-01-01` | **`do … end` body indented one level** | **M** | **Full** | `LuaFormatBlock.getIndent` maps `LuaElementTypes.BLOCK` → `Indent.getNormalIndent()`. Because every block-opening rule in `lua.bnf` wraps its body in exactly one `block` node, this single mapping serves rows `-01`…`-10`. Asserted end-to-end by `TestLuaFormatBlock.testDoBlock`. |
| `FORMAT-01-02` | **`while … do … end` body** | **M** | **Full** | `TestLuaFormatBlock.testWhileBlock`. |
| `FORMAT-01-03` | **`repeat … until` body, `until` dedented** | **M** | **Full** | `repeatStatement ::= REPEAT block UNTIL expr`; `UNTIL` is a direct child of the statement, so it falls to the `else -> getNoneIndent()` arm and returns to the `repeat` column. `TestLuaFormatBlock.testRepeatBlock`. |
| `FORMAT-01-04` | **`if … then` body** | **M** | **Full** | `TestLuaFormatBlock.testIfBlock`. |
| `FORMAT-01-05` | **`elseif`/`else` dedent to `if`; every arm's body indented** | **M** | **Full** | The grammar is **flat**, not nested — `ifStatement ::= IF expr THEN block {ELSEIF expr THEN block}* [ELSE block] END` — so an N-arm chain cannot accumulate depth the way a desugared-to-nested-`if` grammar would. `TestLuaFormatBlock.testIfBlock` asserts a two-`elseif`-plus-`else` chain that includes an **empty** `elseif` arm, which is the case that would expose a stray indent. |
| `FORMAT-01-06` | **Numeric `for … do … end` body** | **M** | **Full** | `TestLuaFormatBlock.testNumericForBlock`. |
| `FORMAT-01-07` | **Generic `for … in … do … end` body** | **M** | **Full** | `TestLuaFormatBlock.testGenericForBlock`. |
| `FORMAT-01-08` | **`function name(…) … end` body** | **M** | **Full** | `TestLuaFormatBlock.testFunctionBlock`. |
| `FORMAT-01-09` | **`function T:m()`, `function a.b.c()` and `local function` bodies** | **M** | **Full** *(unverified)* | `funcBody ::= '(' [parList] ')' block END` is a **private** rule, so it is inlined into `funcDecl`, `localFuncDecl` and `globalFuncDecl` alike and every form reaches the same `block` node. No test names any of these forms; the claim rests on grammar identity with `-08`, not on an assertion. |
| `FORMAT-01-10` | **Anonymous `function() … end` as an expression or argument** | **M** | **Full** *(unverified)* | `funcDef ::= FUNCTION funcBody` — same inlined body. `TestLuaSpacingBuilder.testAnonymousFunctionHeader` exercises an anonymous function but asserts **spacing**, not columns. The interesting case, a callback indented inside a multi-line argument list, depends on `-17` and is untested. |
| `FORMAT-01-11` | **Table-constructor fields one level in; `}` back to the opener column** | **M** | **Full** | `FIELD → Indent.getNormalIndent()` plus `TABLE_CONSTRUCTOR`'s `getChildIndent()`. `TestLuaFormattingWave7.testAlignTableFields` asserts the exact text `local t = {\n    a   = 1,\n    bb  = 2,\n    ccc = 3,\n}\n`, which pins both halves of this row. |
| `FORMAT-01-12` | **Nesting accumulates exactly one level per enclosing block** | **M** | **Full** *(unverified beyond depth 1)* | Structurally implied by `-01`, since each nested `block` contributes one `getNormalIndent()`. **Every** asserted case in the suite is depth 1: a function inside a loop inside an `if` is asserted nowhere. |
| `FORMAT-01-13` | **A closing keyword returns to its opener's column** | **M** | **Full** | `END`, `UNTIL`, `ELSE`, `ELSEIF` and `RCURLY` are all direct children of the construct they close, so they take the `else -> getNoneIndent()` arm and inherit the opener's position. Covered incidentally by `-01`…`-08` and `-11`. |
| `FORMAT-01-14` | **A long string's contents are never re-indented** | **M** | **Partial** *(unverified — likely defect)* | Half of this is safe by construction: `LuaLexer`'s merging adapter collapses `[[`/body/`]]` into a single `LuaElementTypes.STRING` leaf, so the formatter has no whitespace *inside* it to rewrite. The other half is not. `FormatProcessorUtils.replaceWhiteSpace` calls `FormattingModel.shiftIndentInsideRange` for any leaf that both contains line feeds **and** is preceded by whitespace containing a line feed; `CodeFormatterFacade` wraps the plugin's model in `DocumentBasedFormattingModel`, whose implementation of that method is real (it bypasses the no-op the plugin's own `FormattingModelProvider.createFormattingModelForPsiFile` → `PsiBasedFormattingModel` would have supplied). Lunar implements neither `FormattingModelWithShiftIndentInsideDocumentRange` nor a read-only `Spacing` for `STRING`. A long string that **starts on its own line** — a table field, a wrapped argument — is therefore expected to have its interior lines shifted, silently changing the value of a string literal. No test in the repo contains a `[[` at all. |
| `FORMAT-01-15` | **A long comment's contents are never re-indented** | **M** | **Partial** *(unverified — likely defect)* | Identical mechanism to `-14` with `LONGCOMMENT` in place of `STRING`. Lower blast radius (comment text, not program data) but the same absent guard and the same absent test. |
| `FORMAT-01-16` | **Multi-line parameter list aligned under the opening paren** | **S** | **Full** | A shared `Alignment` is created for `NAME_LIST`/`EXPR_LIST` when `listHasMultipleItems` holds. `TestLuaFormatBlock.testFunctionBlock` asserts the exact columns of a three-parameter list broken across three lines. |
| `FORMAT-01-17` | **Multi-line argument list `f(⏎ a,⏎ b⏎)`** | **M** | **Not Implemented** | `EXPR_LIST → Indent.getContinuationIndent()`, and nothing overrides `CONTINUATION_INDENT_SIZE`, so arguments land at the platform default of **8** columns. The one test that would have caught this, `TestLuaFormatBlock.testArgs`, sets `CONTINUATION_INDENT_SIZE = 4` and expects 4 — and **does not run**: it carries `@Ignore` but no `@Test`, and `BaseDocumentTest` is a plain `open class`, not a JUnit3 `TestCase`, so no engine collects it by name. Its sibling `TestLuaFormatBlock.testLocalVarDecl` is disabled the same way and covers the same ground for a multi-name `local a, b, c = 1, 2, 3`. Multi-line call arguments are the single most common wrapped construct in Lua and are asserted nowhere. |
| `FORMAT-01-18` | **Table constructor nested inside an argument list** | **S** | *(unverified)* | Two competing indents meet here — `FIELD`'s `getNormalIndent()` and `EXPR_LIST`'s `getContinuationIndent()` — and which one the platform anchors to depends on which ancestor starts the line, per `Indent`'s class javadoc. Neither `LuaFormatBlock` nor any test states the intended result for `f({⏎ a = 1,⏎})`. |
| `FORMAT-01-19` | **Parenthesised expression continued across lines** | **S** | **Not Implemented** | `LPAREN`/`RPAREN` take `getNoneIndent()`. The `getChildIndent()` branch that would have supplied a continuation indent is keyed on `LuaElementTypes.EXPR` — **a type no node ever carries**. `expr` is a Grammar-Kit alternatives rule with `{extends=expr}`; `EXPR` appears in the generated `LuaParser.java` only inside the `create_token_set_` extends-set declaration, and `LuaElementTypes.Factory` has no `type == EXPR` branch, so such a node cannot even be instantiated. That branch is dead code. |
| `FORMAT-01-20` | **Chained calls / method chains continued across lines** | **S** | **Not Implemented** | `INDEX_EXPR` and `METHOD_EXPR` appear in `LuaSpacingBuilder` (to keep `a . b` tight) but not in `getIndent`, so `obj⏎:method()` gets no continuation indent and is pulled to the statement column. |
| `FORMAT-01-21` | **Binary-operator continuation lines** | **S** | **Not Implemented** | `BIN_OP_EXPR` and its variants fall to `else -> getNoneIndent()`. A condition broken over `and`/`or` lines up with the `if`, which is exactly the ambiguity `Indent`'s javadoc uses as its worked example. |
| `FORMAT-01-22` | **`::label::` indents with its enclosing block** | **S** | **Not Implemented** | `LABEL → Indent.getAbsoluteLabelIndent()`, documented as indenting *"from the leftmost column in the document"* by `LABEL_INDENT_SIZE`, which defaults to `0`. Every `::label::` is therefore flushed to column 0 no matter how deeply nested. `TestLuaFormatBlock.testLabel` **asserts this outcome**, so the suite is currently a guard against fixing it. StyLua and hand-written Lua keep a label at the block indent; `Indent.getNormalIndent()` or `Indent.getLabelIndent()` (relative, not absolute) is the intended shape. |
| `FORMAT-01-23` | **A `#!` first line stays at column 0** | **C** | *(unverified)* | `SHEBANG` is a real token in `lua.flex`/`lua.bnf`, but neither `getIndent` nor any test mentions it. Its parent is the file element, whose children get `getNoneIndent()`, so column 0 is the likely outcome — likely, not established. |
| `FORMAT-01-24` | **Indent width and tab/space choice come from `IndentOptions`** | **M** | **Partial** | Width is honoured: `Indent.getNormalIndent()` resolves against `INDENT_SIZE`, and `TestLuaFormatBlock.testDoBlock` observes 4 columns, the platform default. `USE_TAB_CHARACTER` is set by **no** test in the repo, and neither is any non-default `INDENT_SIZE` — the only test that touches `IndentOptions` at all is the disabled `testArgs`. Whether a tab-indenting Lua project gets tabs is unestablished. |
| `FORMAT-01-25` | **The default matches Lua convention (2 spaces)** | **S** | **Not Implemented** | `LuaLanguageCodeStyleSettingsProvider` overrides neither `getDefaultCommonSettings()` nor `customizeDefaults`, so Lua inherits `CodeStyleDefaults.DEFAULT_INDENT_SIZE = 4` and `DEFAULT_CONTINUATION_INDENT_SIZE = 8`. The 8 is directly observable: `TestLuaFormatBlock.testVarList` asserts a continued `VAR_LIST` at eight columns. Out of the box Lunar reformats Lua like Java. Overriding the default is a few lines and is a deliberate decision, not an oversight to fix silently — it changes output for every existing user who never opened the settings page. |
| `FORMAT-01-26` | **Enter after a block opener indents the new line one level** | **M** | *(unverified)* | This is decided by `Block.getChildAttributes(newChildIndex)`, which `LuaFormatBlock` **does not override**. `AbstractBlock`'s default discards `newChildIndex` and returns `ChildAttributes(getChildIndent(), …)`, so Enter cannot distinguish *after `then`* from *before `end`* — one answer serves every caret position in a construct. `getChildIndent()` returns `getNoneIndent()` for `BLOCK` and for the `else` arm, i.e. for every block-opening statement type. Compounding it, `buildChildren` **skips `block` nodes with no children**, so in `if x then⏎⏎end` there is no block for the caret to land inside at all. |
| `FORMAT-01-27` | **Enter before `end` opens a body line and leaves `end` in place** | **M** | *(unverified)* | Same index-blind path as `-26`. `LuaEnterHandlerTest.testEnterBetweenThenAndEndSameLine` proves the *split* happens, and its own comment records that the harness "does not reindent the body line to nested depth", so it asserts structure rather than a column. Nothing asserts the column. |
| `FORMAT-01-28` | **Enter inside a long string or long comment leaves the line alone** | **S** | *(unverified)* | The complement of `-14`/`-15` on the typing path. No `LineIndentProvider` and no `EnterHandlerDelegate` guard exists for literal context. |
| `FORMAT-01-29` | **A `LineIndentProvider` gives Enter a fast, document-only answer** | **C** | **Not Implemented** | No `lineIndentProvider` extension is registered in `src/main/resources/META-INF/plugin.xml`, and no class in `src/main` implements the interface. Every Enter therefore forces a document commit and a full formatter-model build, which the platform's own javadoc names as the expensive path this EP exists to avoid. |
| `FORMAT-01-30` | **Reformatting an already-formatted file is a no-op** | **S** | *(unverified)* | Idempotence is the cheapest possible guard on an indenter and the one most likely to catch `-14`, `-17` and `-18` at once — a second pass that moves text proves the first pass disagreed with itself. No test in the repo reformats twice. |

## Verification

`TestLuaFormatBlock` is the only class that asserts indent **columns**: `testDoBlock`,
`testWhileBlock`, `testRepeatBlock`, `testIfBlock`, `testNumericForBlock`, `testGenericForBlock`,
`testFunctionBlock`, `testVarList` and `testLabel` cover `-01`…`-08`, `-13`, `-16`, `-22` and `-25`
by exact-text comparison after `CodeStyleManager.reformatText`.
`TestLuaFormattingWave7.testAlignTableFields` pins `-11` the same way, as a side effect of an
alignment test. `TestLuaSpacingBuilder` asserts line breaks and spaces, not columns, and so verifies
none of these rows.

`net.internetisalie.lunar.lang.completion.LuaEnterHandlerTest` is the only Enter suite that touches
Lua code, and it belongs to [[COMP-08]] — it counts inserted `end`/`until`/`}` terminators. Two of
its methods carry an explicit comment that the in-process harness does not reindent to nested depth,
so they were written to assert structure instead. `net.internetisalie.lunar.lang.format.LuaEnterHandlerTest`
is entirely about `---` doc-comment continuation. **`-26` through `-28` are covered by nothing.**

**`-14`, `-15`, `-17`, `-19`, `-20`, `-22`, `-25` and `-29` were found by writing this table** and
are recorded nowhere else in the repo — no bug report, no roadmap row. Three deserve separating from
the rest, because they are not missing polish:

- **`-22` is a wrong behaviour with a passing test.** `testLabel` asserts that `::start::` inside an
  `if` body sits at column 0. Fixing the indent breaks a green test, which is how this survived.
- **`-17` is a defect wearing a disabled test.** `testArgs` was written against the correct
  expectation, annotated `@Ignore`, and left without a `@Test` — so it neither runs nor reports as
  skipped, and the gap reads as covered from a file listing. `testLocalVarDecl` is the same pattern
  in the same file; a repo-wide sweep for it finds exactly four such methods, two of them here.
- **`-14` is a correctness bug, not a formatting one.** Everything else here misplaces a line;
  `shiftIndentInsideRange` on a `[[…]]` leaf edits the contents of a string literal.
