---
id: "EDITOR-08-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "EDITOR-08"
folders:
  - "[[features/editor/08-smart-enter/requirements|requirements]]"
---

# EDITOR-08: Implementation Plan

Sequenced phases over the `design.md` architecture. Each phase leaves the build green and is
independently testable via `CodeInsightTestFixture`. All new production code lands under
`src/main/kotlin/net/internetisalie/lunar/lang/smartenter/`. Tests under
`src/test/kotlin/net/internetisalie/lunar/lang/smartenter/`.

**Build gate:** `tooling/gce-builder/gce-builder.sh run test` then
`... run "ktlintFormat ktlintCheck"` (per CLAUDE.md; do not run `./gradlew` locally). PLANNING
DOC — no code is written here.

## Phase 0 — Keyword-pair table handshake  [Must]

- **P0.1** Check whether `net.internetisalie.lunar.lang.syntax.LuaKeywordPairs` exists (EDITOR-01
  shipped it). If present, verify it exposes the shape/rows in `design.md` §2.1; if it diverges,
  reconcile per DR-01.
- **P0.2** If absent, create `lang/syntax/LuaKeywordPairs.kt` with `LuaBlockKeyword` data class,
  the six `blocks` rows, and `byOpener`, using `LuaTokenTypes` constants
  (`lang/lexer/LuaTokenTypes.kt`). This is the shared surface EDITOR-01 also needs.
- **Verification:** unit test `LuaKeywordPairsTest` asserting `byOpener(LuaTokenTypes.IF)` →
  `(IF, END, THEN)` and `byOpener(LuaTokenTypes.REPEAT)` → `(REPEAT, UNTIL, null)`; assert
  `blocks.size == 6`.

## Phase 1 — Processor skeleton + registration  [Must]

- **P1.1** Create `LuaSmartEnterProcessor : SmartEnterProcessorWithFixers` with an empty fixer
  list (temporarily) and `getStatementAtCaret` override (design §3.1).
- **P1.2** Create `LuaSmartEnterUtil` object with the helper signatures (design §3.4); implement
  `keywordPairFor`, `hasChildToken`, `hasCloser`, `leafTokens`, `separatorText`,
  `blockBodyEndOffset`, `bodyCaretOffset`.
- **P1.3** Register `<lang.smartEnterProcessor language="Lua" implementationClass="…LuaSmartEnterProcessor"/>`
  in `plugin.xml` beside the `lang.braceMatcher` entry (design §4).
- **Verification:** `LuaSmartEnterProcessorTest.testRegistration` —
  `SmartEnterProcessors.INSTANCE.forKey(LuaLanguage.INSTANCE)` is non-empty and contains a
  `LuaSmartEnterProcessor`. (Mirror `JsonSmartEnterTest`'s `forKey` lookup.)

## Phase 2 — Block-end fixers (`function`/`if`/`for`/`while`/`do`)  [Must]  (`EDITOR-08-01`)

- **P2.1** `LuaBlockSeparatorFixer` (inserts `then`/`do`; design §3.2).
- **P2.2** `LuaBlockEndFixer` (inserts `end`; design §3.2, non-repeat branch).
- **P2.3** `LuaFunctionParenFixer` (supplies `()` for a param-less `function foo`; design §3.2).
- **P2.4** Wire the three fixers into `LuaSmartEnterProcessor.init` in the order of design §3.1.
- **Verification:** see Test Matrix TC-01..TC-05, TC-08.

## Phase 3 — `repeat … until`  [Should]  (`EDITOR-08-03`)

- **P3.1** Add the `REPEAT` branch to `LuaBlockEndFixer` (insert `"\nuntil "`, register caret at
  the condition slot; design §3.2).
- **Verification:** TC-06.

## Phase 4 — Bracket balancing  [Should]  (`EDITOR-08-02`)

- **P4.1** `LuaMissingBracketFixer` (stack-based single pass over the statement's leaf tokens;
  design §3.2). Insert it **first** in the fixer list.
- **Verification:** TC-07, TC-09.

## Phase 5 — Caret placement  [Should]  (`EDITOR-08-04`)

- **P5.1** `LuaCaretPlacementEnterProcessor : FixEnterProcessor`; register via
  `addEnterProcessors` (design §3.3). Confirm `registerUnresolvedError`-driven placement (missing
  condition / empty parens / `until` tail) and body-line placement for closed blocks.
- **Verification:** caret-offset assertions in every TC (each test asserts final caret offset,
  not just text).

## Phase 6 — Polish & full-suite gate  [Must]

- **P6.1** Run the full unit suite (regression-relative baseline per Wave DoD); confirm 0 new
  failures.
- **P6.2** `ktlintFormat ktlintCheck` on the new files; match surrounding IntelliJ-formatter style.
- **P6.3** DR-02 reformat spot-check (see risks): confirm reformatted output indentation matches
  `LuaFormatBlock` expectations for a nested block.

## Test Matrix (real-flow, `CodeInsightTestFixture`)

Test class `LuaSmartEnterTest : BaseDocumentTest`. Each test: `configureByText` with a `<caret>`,
invoke the action inside a write command
(`WriteCommandAction.runWriteCommandAction(project) { processor.process(project, editor, file) }`
or `myFixture.performEditorAction(IdeActions.ACTION_EDITOR_COMPLETE_STATEMENT)`), then assert
resulting document text **and** `editor.caretModel.offset`. `|` marks the expected caret in the
"after" text below.

| TC | Requirement | Before (`<caret>`) | After (`|` = caret) |
| :--- | :--- | :--- | :--- |
| TC-01 | 08-01 if | `if x<caret>` | `if x then\n  |\nend` |
| TC-02 | 08-01 if-missing-cond | `if <caret>` | `if | then\nend` (caret at condition slot) |
| TC-03 | 08-01 while | `while c<caret>` | `while c do\n  |\nend` |
| TC-04 | 08-01 numeric for | `for i=1,n<caret>` | `for i=1,n do\n  |\nend` |
| TC-05 | 08-01 function | `function foo<caret>` | `function foo(|)\nend` (caret between parens) |
| TC-06 | 08-03 repeat | `repeat<caret>` | `repeat\n  \nuntil |` |
| TC-07 | 08-02 call parens | `print("x"<caret>` | `print("x")|` |
| TC-08 | 08-01 generic for | `for k,v in pairs(t)<caret>` | `for k,v in pairs(t) do\n  |\nend` |
| TC-09 | 08-02 table literal | `local t = { 1, 2<caret>` | `local t = { 1, 2 }|` |
| TC-10 | 08-01 idempotent | `if x then<caret>\nend` | unchanged text; caret to body line |

(Exact whitespace/indent in "after" is whatever `LuaFormatBlock` produces; tests assert the
reformatted result — TC authors capture the actual formatter output once and pin it, per DR-02.)

## Rollback / safety

`SmartEnterProcessorWithFixers.invokeProcessor` already rolls the document back on
`TooManyAttemptsException` (`SmartEnterProcessorWithFixers.java:80`); no extra guard needed. A
fixer that cannot safely act returns early (never throws), so a malformed skeleton degrades to a
plain enter.
