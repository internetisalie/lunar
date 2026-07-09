---
id: "EDITOR-01-DESIGN"
title: "Technical Design"
type: "design"
parent_id: "EDITOR-01"
folders:
  - "[[features/editor/01-smart-typing/requirements|requirements]]"
---

# Technical Design: EDITOR-01 — Smart Typing

TDD for auto-close / auto-skip / auto-unpair of paired delimiters and keyword-block
auto-close in Lua files. Honors `docs/engineering-contract.md`: all edits run on the EDT
inside the platform's typed-action write context (no explicit `WriteCommandAction` needed —
see §6), no I/O on the EDT, ≤30 logic lines/method, ≤3 args, `val`-first, no `!!`, no
wildcard imports, declarative `plugin.xml` registration.

## 1. Architecture Overview

### Current State
Lunar registers `LuaPairedBraceMatcher`
(`src/main/kotlin/net/internetisalie/lunar/lang/syntax/LuaPairedBraceMatcher.kt:9`) under
`<lang.braceMatcher>` (`plugin.xml:343`). Because a brace matcher is registered, the IntelliJ
Platform **already** provides, for the paren/bracket/curly pairs, auto-close, auto-skip-over
closer, and matching-closer deletion on Backspace — driven by
`com.intellij.codeInsight.editorActions.TypedHandler` and `BackspaceHandler`
(`intellij-community/platform/lang-impl/.../BackspaceHandler.java:114` gates bracket-pair
delete on `CodeInsightSettings.AUTOINSERT_PAIR_BRACKET`; `:135` gates quote-pair delete on
`AUTOINSERT_PAIR_QUOTE`). What is **missing**:

1. **No `QuoteHandler`** is registered, so `"`/`'` do not auto-close, auto-skip, or
   backspace-unpair (`grep -rl QuoteHandler src/main/kotlin` → empty). This is the primary gap.
2. **No `TypedHandlerDelegate`** exists to suppress bracket auto-close inside strings/comments
   with Lua-aware token context, or to coordinate keyword-block completion on keystroke.
3. Keyword-block `end`/`until` auto-close exists **only on Enter**, via `LuaEnterHandler`
   (`src/main/kotlin/net/internetisalie/lunar/lang/completion/LuaEnterHandler.kt:20`) backed by
   the pair table `LuaBlockPairs`
   (`src/main/kotlin/net/internetisalie/lunar/lang/syntax/LuaBlockPairs.kt:15`). There is **no**
   completion-accept path (accepting `do`/`then`/`function`/`for`/`while`/`repeat` from the
   lookup does not scaffold the terminator), and no user-facing on/off toggle.

### Prior Art in This Repo
Searched `src/main/kotlin` for `TypedHandlerDelegate`, `QuoteHandler`,
`BackspaceHandlerDelegate` (all absent), and for existing block/pair infrastructure:

- **`LuaPairedBraceMatcher`** (`.../syntax/LuaPairedBraceMatcher.kt:9`) — the pair table for
  `()`/`[]`/`{}` and keyword spans. **EXTENDED, not replaced**: bracket auto-close/skip/delete
  ride on it unchanged; this feature adds no bracket-specific code beyond context suppression.
- **`LuaBlockPairs`** (`.../syntax/LuaBlockPairs.kt:15`) — `object` holding
  `terminatorByOpener: Map<IElementType, IElementType>` and `insertTextFor` /
  `terminatorForOwner`. This is the reusable keyword-pair table EDITOR-08 (Smart Enter) will
  also consume (epic requirements.md:50). **EXTENDED, not replaced**: EDITOR-01-05's
  completion-accept path calls into it; §2.4 adds the `while`/`for` opener mapping it currently
  lacks (those reach `do`/`then`, which are already present, so no change is needed to the map —
  see §3.4).
- **`LuaEnterHandler`** / **`LuaEnterBetweenBlockHandler`**
  (`.../completion/LuaEnterHandler.kt`, `LuaEnterBetweenBlockHandler.kt`) — the Enter-key
  keyword-completion (COMP-08). **REUSED, not replaced**: EDITOR-01-05 fires on
  completion-accept (a different trigger); the balance check that prevents a redundant second
  terminator is shared conceptually and re-implemented against `LuaBlockPairs` (§3.4). The two
  paths must not both insert a terminator for one action — they cannot, because they fire on
  different editor actions (Enter vs. Tab/Enter-accept of a lookup item).
- **`LuaSyntax.CommentTokens` / `StringLiteralTokens`**
  (`.../syntax/LuaSyntax.kt:35,42`) — the token sets used for context detection (§3.1).
- **`LuaApplicationSettings`** (`.../settings/LuaApplicationSettings.kt:33`) — app-level
  `PersistentStateComponent` pattern this feature mirrors for the Smart Keys toggle (§2.5).

No component already does quote pairing or keyword completion-accept scaffolding.

### Target State
Four new classes plus one settings state/configurable pair:

```
LuaQuoteHandler         (QuoteHandler + MultiCharQuoteHandler)  → -01-03, -01-04 quotes
LuaTypedHandler         (TypedHandlerDelegate)                  → -01-01/-02 suppression, -01-05 keystroke
LuaKeywordBlockCloser   (shared logic object)                  → -01-05 core, reused by keystroke + completion
LuaEditorOptions        (app PersistentStateComponent)         → -01-05 toggle state
LuaEditorOptionsConfigurable (editorSmartKeysConfigurable)     → -01-05 UI checkbox
```
Bracket auto-close/skip/unpair (`-01-01`, `-01-02`, and the bracket half of `-01-04`) require
**no new insertion code** — they are already delivered by the platform over the existing
`LuaPairedBraceMatcher`; `LuaTypedHandler` only adds Lua-aware suppression inside
strings/comments. The keyword completion-accept path is wired through the existing
`LuaCompletionContributor` by attaching an `InsertHandler` to the block-keyword lookup
elements (§2.3).

## 2. Core Components

### 2.1 net.internetisalie.lunar.lang.editor.LuaQuoteHandler
- **Responsibility**: auto-close `"`/`'`, skip over an existing closer, and (with the platform
  `BackspaceHandler`) delete the pair on Backspace — realizes `-01-03` and the quote half of
  `-01-04`.
- **Threading**: EDT, inside the platform typed/backspace action (read-only lexer access via
  the passed `HighlighterIterator`; no PSI commit, no I/O).
- **Collaborators**: extends
  `com.intellij.codeInsight.editorActions.SimpleTokenSetQuoteHandler`
  (verified `intellij-community/platform/lang-impl/.../SimpleTokenSetQuoteHandler.java:12`),
  implements `com.intellij.codeInsight.editorActions.MultiCharQuoteHandler`. Seeded with
  `LuaSyntax.StringLiteralTokens` (`= TokenSet.create(LuaElementTypes.STRING)`,
  `LuaSyntax.kt:42`). Models JSON's `JsonQuoteHandler`
  (`intellij-community/json/.../JsonQuoteHandler.java`).
- **Key API**:
  ```kotlin
  class LuaQuoteHandler :
      SimpleTokenSetQuoteHandler(LuaSyntax.StringLiteralTokens),
      MultiCharQuoteHandler {
      // super provides isOpeningQuote / isClosingQuote / isInsideLiteral / hasNonClosedLiteral
      override fun getClosingQuote(iterator: HighlighterIterator, offset: Int): CharSequence?
  }
  ```

### 2.2 net.internetisalie.lunar.lang.editor.LuaTypedHandler
- **Responsibility**: (a) suppress bracket auto-close inside string/comment context
  (`-01-01`); (b) on typing the trailing char of a block keyword (space after `do`/`then`/…),
  delegate to `LuaKeywordBlockCloser` when the Smart Keys toggle is on (`-01-05` keystroke path).
- **Threading**: EDT; PSI access wrapped in the platform-provided read context of the typed
  action. Commits the document via `PsiDocumentManager.commitDocument` before token lookup
  (mirrors `LuaEnterHandler.kt:35`).
- **Collaborators**: `LuaKeywordBlockCloser` (§2.3), `LuaEditorOptions` (§2.5),
  `LuaSyntax.CommentTokens`/`StringLiteralTokens`, `LuaFile`.
- **Key API**:
  ```kotlin
  class LuaTypedHandler : TypedHandlerDelegate() {
      override fun beforeCharTyped(
          c: Char, project: Project, editor: Editor, file: PsiFile, fileType: FileType
      ): Result   // returns STOP to veto bracket auto-close in string/comment context
      override fun charTyped(c: Char, project: Project, editor: Editor, file: PsiFile): Result
  }
  ```
  Signatures verified against
  `intellij-community/platform/lang-api/.../TypedHandlerDelegate.java:57,65`.

### 2.3 net.internetisalie.lunar.lang.editor.LuaKeywordBlockCloser
- **Responsibility**: the single reusable implementation of "given a Lua block-opener leaf at
  `offset-1`, insert its terminator on the next line if the block is not already balanced, and
  leave the caret on the body line". Called from both the keystroke path (§2.2) and the
  completion `InsertHandler` (§2.6). Realizes the core of `-01-05`.
- **Threading**: EDT, inside the caller's write-capable typed/completion context.
- **Collaborators**: `LuaBlockPairs` (`terminatorByOpener`, `insertTextFor`,
  `terminatorForOwner`), `LuaBlockParent`, `LuaTableConstructor`, `PsiTreeUtil`,
  `CodeStyleManager`.
- **Key API**:
  ```kotlin
  object LuaKeywordBlockCloser {
      /** true if a terminator was inserted. Caller must have committed the document. */
      fun closeIfNeeded(editor: Editor, file: PsiFile, openerEndOffset: Int): Boolean
  }
  ```

### 2.4 Reuse of net.internetisalie.lunar.lang.syntax.LuaBlockPairs
- No new class. `LuaBlockPairs.terminatorByOpener` (`LuaBlockPairs.kt:17`) already maps
  `THEN→END`, `DO→END`, `FUNCTION→END`, `REPEAT→UNTIL`, `LCURLY→RCURLY`. `while`/`for` blocks
  terminate at their `do`/`then` opener, which is already keyed, so no map change is required
  (verified against `LuaEnterHandlerTest.testEnterAfterWhileDo` /
  `testEnterAfterNumericForDo`). `LuaKeywordBlockCloser` consumes this table read-only. This
  section documents the shared-table decision called out for EDITOR-08 in the epic
  (requirements.md:50).

### 2.5 net.internetisalie.lunar.settings.LuaEditorOptions
- **Responsibility**: app-level persisted flag `autoCloseKeywordBlocks: Boolean = true` backing
  the Smart Keys toggle (`-01-05`, on by default).
- **Threading**: any; a light `@Service(Service.Level.APP)` state holder.
- **Collaborators**: none. Mirrors `LuaApplicationSettings.kt:33` and Kotlin's
  `KotlinEditorOptions` (`intellij-community/plugins/kotlin/.../KotlinEditorOptions.java:22`).
- **Key API**:
  ```kotlin
  @Service(Service.Level.APP)
  @State(name = "LuaEditorOptions", storages = [Storage("lunar.editor.xml")],
         category = SettingsCategory.CODE)
  class LuaEditorOptions : PersistentStateComponent<LuaEditorOptions.State> {
      class State { var autoCloseKeywordBlocks: Boolean = true }
      var autoCloseKeywordBlocks: Boolean            // delegates to state
      companion object { val instance: LuaEditorOptions get() = … getService(...) }
  }
  ```

### 2.6 net.internetisalie.lunar.lang.editor.LuaEditorOptionsConfigurable
- **Responsibility**: contributes one checkbox — "Insert matching `end`/`until` for Lua block
  keywords" — into **Settings > Editor > General > Smart Keys**, gating `-01-05`.
- **Threading**: EDT (Swing).
- **Collaborators**: extends
  `com.intellij.openapi.options.BeanConfigurable<LuaEditorOptions>`; uses
  `com.intellij.application.options.editor.CheckboxDescriptor`. Registered via the
  `com.intellij.editorSmartKeysConfigurable` EP (bean
  `EditorSmartKeysConfigurableEP extends ConfigurableEP<UnnamedConfigurable>`, verified
  `intellij-community/platform/lang-impl/.../EditorSmartKeysConfigurableEP.java:17`; example
  registration `intellij-community/plugins/kotlin/.../kotlin-core.xml:385`).
- **Key API**:
  ```kotlin
  class LuaEditorOptionsConfigurable :
      BeanConfigurable<LuaEditorOptions>(LuaEditorOptions.instance, "Lua") {
      init { checkBox("Insert matching end/until for Lua block keywords",
                      { options.autoCloseKeywordBlocks }, { options.autoCloseKeywordBlocks = it }) }
  }
  ```
- **The completion `InsertHandler`** for block keywords is not a separate class: it is a lambda
  attached in `LuaCompletionContributor.addKeywords`
  (`src/main/kotlin/net/internetisalie/lunar/lang/LuaCompletionContributor.kt:38`) that, for the
  block-opener keywords, commits the document and calls
  `LuaKeywordBlockCloser.closeIfNeeded(...)` when `LuaEditorOptions.autoCloseKeywordBlocks` is
  true (§3.5).

## 3. Algorithms

### 3.1 String/comment context suppression (`-01-01`)
- **Input → Output**: `(char c, editor, file, offset = caret)` → `Result` (`STOP` suppresses
  the platform's bracket auto-close; `CONTINUE` lets it run).
- **Steps** (in `LuaTypedHandler.beforeCharTyped`):
  1. If `file` is not `LuaFile` or `c` not in `{ '(', '[', '{' }`, return `CONTINUE`.
  2. `PsiDocumentManager.getInstance(project).commitDocument(editor.document)`.
  3. `offset = editor.caretModel.offset`; if `offset == 0`, return `CONTINUE`.
  4. `leaf = file.findElementAt(offset - 1)`; if null, return `CONTINUE`.
  5. `type = leaf.node.elementType`. If `LuaSyntax.CommentTokens.contains(type)` **or**
     `LuaSyntax.StringLiteralTokens.contains(type)`, return `STOP`. Else `CONTINUE`.
- **Rules / edge handling**: use `findElementAt(offset - 1)` — never offset math on raw text
  (contract + AGENTS "completion context" note). A caret exactly on a string's closing quote is
  outside the STRING token span at `offset-1` only when the string is empty; the platform's own
  bracket insertion is harmless there, so `CONTINUE` is acceptable.
- **Complexity**: O(1) PSI leaf lookup.

### 3.2 Quote auto-close & skip (`-01-03`)
Delegated to the platform. The platform `TypedHandler.handleQuote` uses the registered
`QuoteHandler`; with `SimpleTokenSetQuoteHandler` seeded on `STRING`:
- **Open**: `isOpeningQuote(iterator, offset)` returns true when the caret is at the start of a
  `STRING` token → platform inserts the closer and positions caret between (base-class logic,
  `SimpleTokenSetQuoteHandler.java:37`).
- **Skip**: `isClosingQuote(iterator, offset)` returns true when `offset == tokenEnd - 1` inside
  a `STRING` token → platform steps over instead of inserting (`:24`).
- **Suppress unbalanced/mid-word** (`-01-03` "unbalanced/mid-word cases suppressed"): override
  `getClosingQuote(iterator, offset)` (MultiCharQuoteHandler) to return `null` when the char
  immediately before `offset` is an identifier char (`Character.isLetterOrDigit(prev) || prev ==
  '_'`) — this stops `it's` from auto-closing after `it`. Steps:
  1. If `offset == 0` return `null`.
  2. Let `prev = editor.document.charsSequence[offset - 1]`.
  3. If `prev.isLetterOrDigit() || prev == '_'` return `null`.
  4. Otherwise return the typed quote char as a 1-char `CharSequence`.
- **Rules / edge handling**: return `null` (not empty) to signal "do not auto-close"; matches
  JSON's null-return contract (`JsonQuoteHandler.java:26`).

### 3.3 Quote & bracket backspace-unpair (`-01-04`)
No new code beyond §2.1. The platform `BackspaceHandler.handleBackspace`
(`intellij-community/.../BackspaceHandler.java:91-135`) deletes the matching closer for a
freshly-typed empty pair: for brackets via the `LuaPairedBraceMatcher` (gated on
`AUTOINSERT_PAIR_BRACKET`), for quotes via `LuaQuoteHandler.isClosingQuote` (gated on
`AUTOINSERT_PAIR_QUOTE`). Both settings default on. No `BackspaceHandlerDelegate` is required;
registering the `QuoteHandler` is sufficient to unlock the quote half.

### 3.4 Keyword-block terminator insertion (`LuaKeywordBlockCloser.closeIfNeeded`)
- **Input → Output**: `(editor, file: LuaFile, openerEndOffset)` → `Boolean` (inserted?).
  `openerEndOffset` is the offset just past the opener leaf whose type is a `LuaBlockPairs`
  opener (`do`/`then`/`function`/`repeat`/`{`).
- **Steps** (mirrors `LuaEnterHandler.completeBlock`, `LuaEnterHandler.kt:44`):
  1. `opener = file.findElementAt(openerEndOffset - 1)`; if null return false.
  2. `terminatorType = LuaBlockPairs.terminatorByOpener[opener.node.elementType]`; if null
     return false.
  3. `parentClass = if (terminatorType == RCURLY) LuaTableConstructor else LuaBlockParent`.
  4. `owner = PsiTreeUtil.getParentOfType(opener, parentClass, false)`.
  5. **Balance check**: if `owner != null && owner.node.findChildByType(terminatorType) !=
     null` → already balanced → return false (no insert; prevents the redundant-`end` bug fixed
     in COMP-08-02).
  6. `insertText = LuaBlockPairs.insertTextFor[terminatorType]`; if null return false.
  7. `editor.document.insertString(openerEndOffset, "\n" + insertText)`.
  8. `CodeStyleManager.getInstance(file.project).adjustLineIndent(...)` on the two new lines
     (as `LuaEnterHandler.reindentBody`, `:94`); leave the caret on the (empty) body line.
  9. Return true.
- **Rules / edge handling**: caller commits the document before calling. Only fires when the
  opener leaf is the immediate token at `openerEndOffset-1`, so typing `do` inside a comment/
  string yields a `SHORTCOMMENT`/`STRING` leaf (not a `DO` element) and step 2 returns false.
- **Complexity**: O(1) leaf + parent lookup.

### 3.5 Keystroke vs. completion trigger for `-01-05`
Two entry points, both gated on `LuaEditorOptions.instance.autoCloseKeywordBlocks`:
- **Completion-accept** (primary): in `LuaCompletionContributor.addKeywords`, block-opener
  keywords (`do`, `then` via `if`, `function`, `for`, `while`, `repeat` — the members of
  `STATEMENT_KEYWORDS` that map through `LuaBlockPairs`) get an `InsertHandler` that, after the
  keyword text is inserted, commits the document and calls
  `LuaKeywordBlockCloser.closeIfNeeded(editor, file, context.tailOffset)`. `for`/`while` scaffold
  the terminator only once their `do`/`then` opener leaf exists; on bare keyword accept the
  balance/opener check (§3.4 step 2) simply returns false and no terminator is inserted until the
  `do`/`then` is typed — which then routes through the keystroke path.
- **Keystroke** (`LuaTypedHandler.charTyped`): when `c == ' '` (or the char completing a
  keyword), find the leaf at `offset-1`; if its type is in `LuaBlockPairs.terminatorByOpener`
  keys, call `LuaKeywordBlockCloser.closeIfNeeded(editor, file, leaf.textRange.endOffset)`.
- **Coordination with Enter**: `LuaEnterHandler` fires on the Enter action, never on Tab/Space
  or completion-accept, so no double insertion is possible. If a user accepts `do` (terminator
  inserted) then presses Enter on the body line, `LuaEnterHandler.completeBlock` step "already
  balanced" (`:54`) sees the existing `end` and inserts nothing.

## 4. External Data & Parsing
None. This feature consumes only editor keystrokes and the in-memory PSI/lexer token stream;
there is no CLI, file, or network input to parse.

## 5. Data Flow

### Example 1: Type `(` after `print` (`-01-01`)
`print` typed → user types `(` → platform `TypedHandler` asks
`LuaTypedHandler.beforeCharTyped('(')` → leaf at `offset-1` is `IDENTIFIER` (not comment/string)
→ `CONTINUE` → platform consults `LuaPairedBraceMatcher`, inserts `)`, caret between → document
becomes `print(<caret>)`.

### Example 2: Type `"` at value position (`-01-03`)
`local s = ` then `"` → platform `handleQuote` → `LuaQuoteHandler.getClosingQuote` sees `prev ==
' '` (not identifier) → returns `"` → platform inserts closer → `local s = "<caret>"`. Typing a
second `"` → `isClosingQuote` true (caret at `end-1`) → platform skips → `local s = ""<caret>`.

### Example 3: Accept `function` from completion (`-01-05`)
Caret at statement start, lookup shows `function`, user presses Enter/Tab → keyword text
inserted → `InsertHandler` commits doc, calls `closeIfNeeded(editor, file, tailOffset)` → opener
leaf `FUNCTION`, no existing `END` in owner → inserts `\nend`, reindents → caret on body line
above `end`. Toggle off → `InsertHandler` returns early, no `end`.

## 6. Edge Cases
- **Toggle off**: both `-01-05` entry points check `LuaEditorOptions.autoCloseKeywordBlocks`
  first; brackets/quotes are unaffected (unconditional per epic decision).
- **Inside string/comment**: `(`/`{`/`[` suppressed by §3.1; keyword scaffolding suppressed
  because the leaf is a comment/string token, not a keyword element (§3.4 step 2).
- **Mid-word quote** (`don't`): `getClosingQuote` returns `null` (§3.2), no auto-close.
- **Already-balanced block**: balance check (§3.4 step 5) — no redundant terminator; covered by
  the COMP-08-02 regression pattern.
- **Non-Lua file**: every handler early-returns `Result.CONTINUE` / `false` unless
  `file is LuaFile`.
- **Write context**: all insertions happen inside the platform's typed-action / completion
  command, which is already a write action; do not wrap in a second `WriteCommandAction`
  (double-command would corrupt undo). Document edits use `editor.document.insertString`, exactly
  as `LuaEnterHandler.kt:60`.

## 7. Integration Points

```xml
<!-- plugin.xml — under the existing <extensions defaultExtensionNs="com.intellij"> block -->
<lang.quoteHandler
    language="Lua"
    implementationClass="net.internetisalie.lunar.lang.editor.LuaQuoteHandler"/>
<typedHandler
    implementation="net.internetisalie.lunar.lang.editor.LuaTypedHandler"/>
<editorSmartKeysConfigurable
    instance="net.internetisalie.lunar.lang.editor.LuaEditorOptionsConfigurable"/>
```
- `LuaEditorOptions` is a `@Service(Service.Level.APP)` — registered by annotation, no
  `plugin.xml` line needed.
- No `<backspaceHandlerDelegate>` is registered: pair-delete is delivered by the platform
  `BackspaceHandler` over the existing brace matcher and the new quote handler (§3.3).
- `LuaCompletionContributor` (already registered, `plugin.xml` `completion.contributor`
  language="Lua") is edited in place to attach the block-keyword `InsertHandler` — no new
  registration.
- Existing `LuaPairedBraceMatcher` (`plugin.xml:343`) and the three `enterHandlerDelegate`
  registrations are untouched.

## 8. Requirement Coverage

| Requirement | Priority | Implemented by (section) |
|-------------|----------|--------------------------|
| EDITOR-01-01 Auto-close brackets | M | §1 (platform + brace matcher), §2.2/§3.1 (suppression) |
| EDITOR-01-02 Auto-skip closer | M | §1 (platform brace matcher) |
| EDITOR-01-03 Quote pairing | M | §2.1, §3.2 |
| EDITOR-01-04 Backspace unpairing | S | §2.1, §3.3 (platform `BackspaceHandler`) |
| EDITOR-01-05 Keyword block auto-close | M | §2.2, §2.3, §2.5, §2.6, §3.4, §3.5 |

## 9. Alternatives Considered
- **A custom `BackspaceHandlerDelegate` for pair-delete** — rejected: the platform
  `BackspaceHandler` already deletes bracket/quote pairs via the brace matcher + quote handler
  (§3.3); a delegate would duplicate platform logic and risk double-deletion.
- **A dedicated `LuaBlockKeywordInsertHandler` class** — rejected in favor of a lambda in
  `LuaCompletionContributor` calling the shared `LuaKeywordBlockCloser`, to keep the pair logic
  single-sourced (contract: no duplicated orchestration) and reusable by EDITOR-08.
- **Gating brackets/quotes behind the toggle** — rejected per the confirmed epic decision
  (requirements.md:59): only keyword auto-close is toggleable; brackets/quotes are unconditional
  per platform norm.

## 10. Open Questions

_None — feature has cleared the planning bar._
