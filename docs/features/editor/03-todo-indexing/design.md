---
id: "EDITOR-03-DESIGN"
title: "Technical Design"
type: "design"
parent_id: "EDITOR-03"
folders:
  - "[[features/editor/03-todo-indexing/requirements|requirements]]"
---

# Technical Design: EDITOR-03 — TODO / FIXME Indexing

## 1. Architecture Overview

### Current State
Lunar has no `com.intellij.indexPatternBuilder` registration (verified: `grep -rn
"indexPatternBuilder" src/ plugin.xml` returns only `IndexPatternProvider` *import* usage in
`lang/indexing/LuaFileBindingsIndex.kt:10,89`, which merely listens to the
`INDEX_PATTERNS_CHANGED` topic to invalidate a caches — it does **not** build TODO patterns).
Consequently `TODO:`/`FIXME:` inside Lua comments never appear in the TODO tool window, gutter,
or error stripe. The lexer already classifies every comment kind, so this is purely a missing
declarative registration plus one small class.

### Prior Art in This Repo
Searched `src/main` for existing TODO / index-pattern machinery:
- `grep -rn "IndexPatternBuilder\|indexPatternBuilder\|IndexPatternSearch\|TodoPattern\|PsiTodoSearchHelper" src/main`
  → only hit is `LuaFileBindingsIndex.kt` using `IndexPatternProvider.INDEX_PATTERNS_CHANGED`
  as a cache-invalidation topic. **No** `IndexPatternBuilder` exists. → This design **creates a
  new** builder; nothing to extend or replace.
- The canonical comment `TokenSet` already exists: `net.internetisalie.lunar.lang.syntax.LuaSyntax.CommentTokens`
  (`lang/syntax/LuaSyntax.kt:35-40`), used by `LuaParserDefinition.getCommentTokens()`
  (`lang/LuaParserDefinition.kt:37-39`). This design **reuses the token members** but defines its
  own, tighter `TokenSet` (excluding `SHEBANG`, see §6) rather than reusing `CommentTokens` verbatim.
- The parser-facing lexer is `net.internetisalie.lunar.lang.lexer.LuaLexer` (`lang/lexer/LuaLexer.kt:10`),
  instantiated by `LuaParserDefinition.createLexer` (`lang/LuaParserDefinition.kt:25-27`). This design
  reuses `LuaLexer` as the indexing lexer.

### Target State
A single stateless extension class `LuaTodoIndexPatternBuilder` implementing
`com.intellij.psi.impl.search.IndexPatternBuilder`, registered under
`<indexPatternBuilder>`. It hands the platform three things per the EP contract:
its indexing `Lexer` (`LuaLexer`), the Lua comment `TokenSet`, and per-token start/end deltas
(the length of the comment marker) so the platform's `LexerBasedTodoIndexer` scans only the
comment *body*. The platform then does everything else — matching the user's `TodoConfiguration`
patterns, populating the TODO tool window grouped by file, and painting the gutter/error-stripe
marks with the platform TODO `TextAttributesKey`. **No `TextAttributesKey` or lexer changes are
needed** (see §6, §7).

Component sketch:
```
LexerBasedTodoIndexer (platform)
   └─ asks each IndexPatternBuilder EP → LuaTodoIndexPatternBuilder
         ├─ getIndexingLexer(file)      → LuaLexer()      [reused]
         ├─ getCommentTokenSet(file)    → COMMENT_TOKENS  [new, LuaSyntax members]
         └─ getCommentStartDelta/EndDelta(type[,text]) → marker length
   → matches TodoConfiguration.getTodoPatterns() against comment body text
   → TODO tool window / gutter / error stripe / IndexPatternSearch  (all platform-provided)
```

> **⚠ Note (implementation 2026-07-10):** §2.1's single-`IndexPatternBuilder` sketch is incomplete.
> `findTodoItems` only runs the range searcher when the persisted TODO *count* is non-zero, and the
> default count path (the **layered editor highlighter**) re-lexes `---` into inner LuaCats tokens and
> never counts the lazy `LUACATS_COMMENT`. Shipped design therefore uses **two** extensions: the
> `IndexPatternBuilder` (range match, `COMMENT_TOKENS = { SHORTCOMMENT, LONGCOMMENT, LUACATS_COMMENT }`,
> deltas as §3.1) **plus** a `com.intellij.todoIndexer` (`LuaTodoIndexer` + `LuaTodoFilterLexer` over a
> **non-layered** `LuaLexer`) that supplies the count including `LUACATS_COMMENT`. With both, single-
> line `---` doc comments work (EDITOR-03-04 Full). See `risks-and-gaps.md` Risk 1.2 / DR-01.

## 2. Core Components

### 2.1 `net.internetisalie.lunar.lang.todo.LuaTodoIndexPatternBuilder`
- **Responsibility**: Tell the platform TODO indexer which Lua tokens are comments, which lexer
  owns them, and how many prefix/suffix marker characters to skip. One class, no state.
- **Threading**: None of its own. The platform calls these methods from the indexing thread
  (background, under the indexing framework's own read access). The class holds **no** references
  to `Project`/`Editor`/`PsiFile`/`VirtualFile` fields (contract §4 HEAVY OBJECT RETENTION) —
  `PsiFile` arrives as a method parameter and is not retained.
- **Collaborators** (all verified real):
  - `net.internetisalie.lunar.lang.psi.LuaFile` (`lang/psi/LuaFile.kt:14`) — file-type guard.
  - `net.internetisalie.lunar.lang.lexer.LuaLexer` (`lang/lexer/LuaLexer.kt:10`) — indexing lexer.
  - `net.internetisalie.lunar.lang.psi.LuaElementTypes.SHORTCOMMENT` / `.LONGCOMMENT`
    (`src/main/gen/.../lang/psi/LuaElementTypes.java:122,100`).
  - `net.internetisalie.lunar.lang.psi.LuaLazyElementTypes.LUACATS_COMMENT`
    (`lang/psi/LuaBaseElements.kt:116-120`).
- **Key API**:
  ```kotlin
  package net.internetisalie.lunar.lang.todo

  import com.intellij.lexer.Lexer
  import com.intellij.psi.PsiFile
  import com.intellij.psi.impl.search.IndexPatternBuilder
  import com.intellij.psi.tree.IElementType
  import com.intellij.psi.tree.TokenSet

  class LuaTodoIndexPatternBuilder : IndexPatternBuilder {
      override fun getIndexingLexer(file: PsiFile): Lexer? =
          if (file is LuaFile) LuaLexer() else null

      override fun getCommentTokenSet(file: PsiFile): TokenSet? =
          if (file is LuaFile) COMMENT_TOKENS else null

      override fun getCommentStartDelta(tokenType: IElementType): Int =
          fixedStartDelta(tokenType)                       // §3.1

      override fun getCommentStartDelta(tokenType: IElementType, tokenText: CharSequence): Int =
          longBracketStartDelta(tokenType, tokenText)      // §3.1

      override fun getCommentEndDelta(tokenType: IElementType): Int =
          endDelta(tokenType)                              // §3.1

      private companion object {
          val COMMENT_TOKENS: TokenSet = TokenSet.create(
              LuaElementTypes.SHORTCOMMENT,
              LuaElementTypes.LONGCOMMENT,
              LuaLazyElementTypes.LUACATS_COMMENT,
          )
      }
  }
  ```
  The three `private` helpers (`fixedStartDelta`, `longBracketStartDelta`, `endDelta`) each stay
  ≤30 logic lines and ≤3 args (contract §3). `LUACATS_COMMENT` is a `var` in `LuaLazyElementTypes`
  (`lang/psi/LuaBaseElements.kt:120`) — read it, never reassign it.

## 3. Algorithms

### 3.1 Comment-marker delta computation
The platform's `LexerBasedTodoIndexer` matches `TodoConfiguration` regexes against the token text
**minus** `startDelta` leading chars and `endDelta` trailing chars, so patterns don't spuriously
key off the `--`/`--[[` marker. Deltas must equal the marker length for each Lua comment kind.

- **Input → Output**: `(IElementType[, CharSequence]) → Int` (character count to skip).
- **Fixed-prefix tokens** (`fixedStartDelta`):
  1. `SHORTCOMMENT` → text always begins `--` → **return 2**.
  2. `LUACATS_COMMENT` → text always begins `---` (the lexer only classifies a `SHORTCOMMENT`
     as `LUACATS_COMMENT` when every line starts with `---`, per `LuaLexer.getTokenType`
     `lang/lexer/LuaLexer.kt:97-106`) → **return 3**.
  3. anything else → **return 0**.
- **Long comment** (`longBracketStartDelta`, uses the text overload): a `LONGCOMMENT` token text is
  `--` + `[` + *k* `=` + `[` … `]` *k* `=` `]`, where *k* ≥ 0 is the bracket level (`--[[`, `--[==[`).
  Compute the opening-bracket length from the text so `--[==[` (delta 6) works as well as `--[[`
  (delta 4):
  1. If `tokenType != LONGCOMMENT` delegate to `fixedStartDelta(tokenType)`.
  2. Let `s = tokenText`. Assert `s` starts with `--[`; if not (defensive), return 2.
  3. Starting at index 3 (`--[`), count consecutive `=` characters → *k*.
  4. If `s[3 + k] == '['` → **return `4 + k`** (`--` = 2, `[` `=`×k `[` = `2 + k`).
  5. Otherwise (malformed / unterminated) → **return 2** (skip just `--`, never negative/out-of-range).
- **End delta** (`endDelta`):
  1. `LONGCOMMENT` → closing bracket is `]` `=`×k `]`. Recompute *k* from the **opening** run is not
     available in the arg-less overload, so use the symmetric fixed form: the platform only needs a
     non-negative end delta to keep the closing bracket out of the match. Return **2** for
     `LONGCOMMENT` (skips the minimal `]]`; a longer `]==]` tail is harmless because a TODO pattern
     never legitimately matches the trailing bracket run). All other tokens → **0**.
- **Rules / edge handling**: never return a negative delta or one exceeding `tokenText.length`;
  the malformed/unterminated branches above cap at the safe minimum. Empty/blank comment bodies
  simply yield no pattern match (platform-handled).
- **Complexity**: O(k) over the leading `=` run (bounded by comment length); effectively O(1).

**Why the text-aware overload:** the JetBrains reference builders that have a fixed one-char marker
(`PyIndexPatternBuilder`, `TomlTodoIndexPatternBuilder`) return a constant. Lua's long comment has a
**variable-length** opening bracket, so we override the
`getCommentStartDelta(IElementType, CharSequence)` overload (present on the EP interface,
`platform/indexing-impl/.../IndexPatternBuilder.java:33-35`) to read the actual level.

## 4. External Data & Parsing
Not applicable. This feature consumes no CLI output, files, or network responses. The only "input"
is the user's TODO pattern list, which the **platform** owns via `TodoConfiguration.getTodoPatterns()`
(Settings ▸ Editor ▸ TODO); this builder never parses patterns itself.

## 5. Data Flow

### Example 1: line comment `-- TODO: refactor`
1. Indexer lexes the file with `LuaLexer`; the `-- TODO: refactor` token is `SHORTCOMMENT`.
2. `getCommentTokenSet` contains `SHORTCOMMENT` → the indexer processes it.
3. `getCommentStartDelta(SHORTCOMMENT)` = 2, `getCommentEndDelta` = 0 → body scanned = ` TODO: refactor`.
4. Default `\bTODO\b.*` pattern matches → a `TodoItem` is recorded; it surfaces in the TODO tool
   window (grouped by file), gutter, error stripe, and `PsiTodoSearchHelper.findTodoItems`.

### Example 2: block comment `--[[ FIXME see #12 ]]`
1. `LuaLexer` merges the long comment into one `LONGCOMMENT` token (`LongCommentMergingLexerAdapter`,
   `lang/lexer/LuaLexer.kt:147-169`).
2. `longBracketStartDelta`: k=0, `s[3]=='['` → delta 4; `endDelta` = 2 → body = ` FIXME see #12 `.
3. Default `\bFIXME\b.*` pattern matches → `TodoItem` recorded.

### Example 3: TODO inside a string literal (negative)
1. `local s = "TODO not a comment"` → the `"…"` is a `STRING` token (`LuaElementTypes.STRING`),
   **not** in `COMMENT_TOKENS`.
2. The indexer skips it → **no** `TodoItem`. (This is the DoD negative case.)

## 6. Edge Cases
- **SHEBANG** (`#!`): `LuaSyntax.CommentTokens` includes `LuaElementTypes.SHEBANG`
  (`lang/syntax/LuaSyntax.kt:38`), but we **exclude** it from `COMMENT_TOKENS`. A shebang is a
  single first-line directive, not a comment users write TODOs in; excluding it matches JetBrains
  language behavior and avoids odd delta math (`#!` marker). If included later it is a one-line
  addition; deferred as a non-goal (see risks TBD).
- **`--[==[` leveled long comment**: handled by §3.1 step 3–4 via the text overload.
- **Unterminated long comment** (`--[[ TODO` with no close): §3.1 step 5 caps startDelta at 2; the
  platform still matches inside the surviving body. No exception thrown.
- **`---` LuaCATS block**: classified as `LUACATS_COMMENT`; delta 3; TODOs inside doc comments are
  matched (requirement `EDITOR-03-04`).
- **`----` (four dashes)**: still all-lines-start-`---` → `LUACATS_COMMENT`, delta 3; the extra dash
  becomes body char, harmless (a leading `-` never breaks `\bTODO\b`).
- **Custom user pattern** (e.g. `\bHACK\b.*`): matched automatically — the builder is pattern-agnostic;
  it only defines comment scanning (requirement `EDITOR-03-02`).

## 7. Integration Points
Single declarative registration in the existing `<extensions defaultExtensionNs="com.intellij">`
block of `src/main/resources/META-INF/plugin.xml` (block opens at line 61):

```xml
<!-- plugin.xml, inside <extensions defaultExtensionNs="com.intellij"> -->
<!-- EDITOR-03: TODO / FIXME indexing in Lua comments -->
<indexPatternBuilder
    implementation="net.internetisalie.lunar.lang.todo.LuaTodoIndexPatternBuilder"/>
```

- **Extension point**: `com.intellij.indexPatternBuilder`
  (`platform/indexing-impl/.../IndexPatternBuilder.java:16`, `EP_NAME =
  "com.intellij.indexPatternBuilder"`). No `id`/`order`/`group` attributes required (the EP has none).
- **No new package registration**: `net.internetisalie.lunar.lang.todo` is a new source package; no
  extra config needed.
- **`TextAttributesKey`**: none. TODO highlighting uses the platform's built-in TODO attributes
  (`CodeInsightColors.TODO_DEFAULT_ATTRIBUTES` via `TodoAttributesUtil`), applied by the platform
  once a `TodoItem` exists. Requirement `EDITOR-03-03` needs **no** wiring on our side.
- **No lexer/parser changes**: `LuaLexer`, `LuaTokenTypes`, `LuaElementTypes` are untouched.

## 8. Requirement Coverage

| Requirement | Priority | Implemented by (section) |
|-------------|----------|--------------------------|
| EDITOR-03-01 (comment token set + prefix length) | M | §2.1 (`COMMENT_TOKENS`, `getIndexingLexer`), §3.1 (deltas) |
| EDITOR-03-02 (TODO tool window, default + custom) | M | §2.1, §5 Ex.1; platform-driven via §7 registration |
| EDITOR-03-03 (editor gutter / error-stripe / attributes) | S | §7 (platform TODO attributes, no wiring), §5 Ex.1 |
| EDITOR-03-04 (LuaDoc/LuaCATS + block comments) | S | §2.1 (`LONGCOMMENT`, `LUACATS_COMMENT`), §3.1, §5 Ex.2 |

## 9. Alternatives Considered
- **Reuse `LuaSyntax.CommentTokens` verbatim** as the token set: rejected because it includes
  `SHEBANG` (§6) and would force shebang delta handling for zero user value. A dedicated tighter set
  is clearer and closes the door on odd matches.
- **Constant delta for `LONGCOMMENT`** (like Python's fixed `3`): rejected — Lua long brackets are
  variable-length (`--[==[`), so a constant would mis-slice `--[==[` bodies. The text overload (§3.1)
  is the minimal correct approach and is exactly why the platform provides that overload.
- **A custom `TodoIndexer`/`DataIndexer`**: rejected as vastly heavier than the EP; the
  `IndexPatternBuilder` EP + platform `LexerBasedTodoIndexer` is the sanctioned lexer-based path (the
  interface Javadoc points at `LexerBasedTodoIndexer`).

## 10. Open Questions

_None — feature has cleared the planning bar._
