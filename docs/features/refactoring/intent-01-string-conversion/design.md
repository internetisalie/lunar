---
id: INTENT-01-DESIGN
title: String Conversion Design
type: design
parent_id: INTENT-01
status: planned
folders:
  - "[[features/refactoring/intent-01-string-conversion/requirements|requirements]]"
---

# Technical Design: String Quote Conversion Intention

An Alt+Enter intention that cycles the string literal under the caret through the three Lua
string forms while preserving the runtime value:

```
'…'  →  "…"  →  [[…]]  →  '…'  (and around again)
```

## 1. Architecture Overview

### Class & package

- **FQ class:** `net.internetisalie.lunar.lang.insight.LuaStringConversionIntention`
  - **Convention decision:** the repo's *only* existing intention,
    `net.internetisalie.lunar.lang.insight.LuaGenerateDocIntention`
    (`src/main/kotlin/net/internetisalie/lunar/lang/insight/LuaGenerateDocIntention.kt:11`),
    lives in `lang/insight/`. We follow that convention rather than inventing a new
    `lang/intentions/` package (which the skeleton named but which does **not** exist on disk).
- **Base class:** `com.intellij.codeInsight.intention.impl.BaseIntentionAction`
  - Verified by example: `LuaGenerateDocIntention` extends `BaseIntentionAction` and overrides
    `getFamilyName()`, `getText()`, `isAvailable(project, editor, file)`, `invoke(project, editor, file)`
    (`LuaGenerateDocIntention.kt:11-49`).
  - **Why not `PsiElementBaseIntentionAction`?** That class
    (`intellij-community/platform/lang-api/src/com/intellij/codeInsight/intention/PsiElementBaseIntentionAction.java:21`)
    is also valid — it extends `BaseIntentionAction` and dispatches to element-based
    `isAvailable(project, editor, element)` / `invoke(project, editor, element)` overloads. We
    deliberately mirror the existing repo intention (`BaseIntentionAction`, file-based signatures)
    for consistency; the file-based form reads the caret element itself, exactly as the rest of
    this design does. Either base would work; this is a style choice, not a correctness one.

### Action text shown in the Alt+Enter menu

`getText()` returns the *target* form so the menu reads naturally, and `getFamilyName()`
groups them:

- Family name: `"Convert string quotes"` (returned by `getFamilyName()`).
- Action text (`getText()`), chosen at `isAvailable` time from the current form's *next* step:
  - current `'…'` → `"Convert to double-quoted string"`
  - current `"…"` → `"Convert to long-bracket string"`
  - current `[[…]]` → `"Convert to single-quoted string"`

The chosen text is stored in a `var actionText` field set during `isAvailable` and returned by
`getText()` (the IntelliJ intention contract calls `isAvailable` before `getText`).

## 2. Grounded PSI Facts (the basis for all algorithms)

All verified against this repo (not EmmyLua):

1. **A string literal is a `LuaTerminalExpr` whose child is the `STRING` leaf.**
   - Grammar: `terminalExpr ::= NIL | FALSE | TRUE | NUMBER | STRING | ELLIPSIS`
     (`src/main/kotlin/net/internetisalie/lunar/lang/psi/lua.bnf:225`).
   - PSI accessor: `LuaTerminalExpr.getString(): PsiElement?` returns the `STRING` leaf or null
     (`src/main/gen/net/internetisalie/lunar/lang/psi/LuaTerminalExpr.java:14`).
   - Element type: `LuaElementTypes.STRING`
     (`src/main/gen/net/internetisalie/lunar/lang/psi/LuaElementTypes.java:119`).
   - **The skeleton's `LuaLiteralExpr` does not exist and is wrong — corrected to `LuaTerminalExpr`.**

2. **Long strings `[[…]]` ALSO surface as a single `STRING` leaf under a `LuaTerminalExpr`.**
   This is the non-obvious, load-bearing fact for applicability. The flex lexer emits the raw
   tokens `LONGSTRING_BEGIN` / `LONGSTRING` / `LONGSTRING_END`
   (`src/main/java/net/internetisalie/lunar/lang/lexer/LuaTokenTypes.java:75,77,78`), but
   `LongStringMergingLexerAdapter.getMergeFunction()` merges that whole run and **returns
   `LuaElementTypes.STRING`** (`src/main/kotlin/net/internetisalie/lunar/lang/lexer/LuaLexer.kt:112-138`,
   esp. line 136 `return@MergeFunction LuaElementTypes.STRING`). Short strings are likewise
   emitted as many `STRING` tokens by the flex lexer (`lua.flex:95-163`) and merged by the
   outer `MergingLexerAdapter` over `TokenSet.create(… LuaTokenTypes.STRING …)`
   (`LuaLexer.kt:21-26`). The parser's `terminalExpr` consumes a single `STRING` token
   (`src/main/gen/net/internetisalie/lunar/lang/parser/LuaParser.java:1283-1292`).
   - **Consequence:** both `'…'`, `"…"`, and `[[…]]` are the same PSI shape — a `LuaTerminalExpr`
     with a non-null `getString()`. The current *form* is determined by inspecting the leaf's
     **text**, not by a distinct element type.

3. **PSI construction / replacement.** `LuaElementFactory`
   (`src/main/kotlin/net/internetisalie/lunar/lang/psi/LuaElementFactory.kt:11`) builds throwaway
   PSI from text via `createFile(project, text)` (line 41) and
   `createExpression(project, value)` (line 32, wraps as `local _ = <value>`). **This design uses
   a document-level text replacement, not PSI surgery** — see §3.4 — because we are replacing a
   single leaf's verbatim text and a document edit is simpler and avoids re-parsing concerns. The
   factory is noted only as the alternative.

4. **Escape parsing helpers already exist** in
   `src/main/kotlin/net/internetisalie/lunar/lang/syntax/LuaLiterals.kt`:
   - `extractLuaString(str): String` (line 121) — given the **full** literal text incl.
     delimiters, returns the decoded runtime value. Handles `'…'`/`"…"` (unescaping simple,
     `\xHH`, `\u{…}`, `\ddd` escapes via the private `unescapeLuaString`, lines 16-106) **and**
     long brackets `[[…]]`/`[==[…]==]` (no escape processing, strips a leading newline, lines
     130-138).
   - `getLuaStringDelimiterLength(str): Int` (line 108) — returns 1 for `'`/`"`, or
     `level + 2` for `[==[`-style brackets, or 0.
   - **We REUSE `extractLuaString` for the unescape/decode half.** `unescapeLuaString` is
     private; `extractLuaString` is the public entry and is sufficient (it dispatches on the
     delimiter). The *encode* (re-escape into a new delimiter) half does not yet exist and is
     added by this feature (see §3.2).

## 3. Core Algorithm

### 3.1 Determine current form from the leaf text

Let `raw` = `terminalExpr.string.text` (the full literal incl. delimiters).

```
fun currentForm(raw): Form =
  when {
    raw.startsWith("'")            -> Form.SINGLE
    raw.startsWith("\"")           -> Form.DOUBLE
    getLuaStringDelimiterLength(raw) >= 2 && raw.startsWith("[") -> Form.LONG
    else                           -> Form.UNKNOWN   // defensive; intention not offered
  }
```

Cycle: `SINGLE → DOUBLE → LONG → SINGLE`.

### 3.2 Decode then re-encode (the escaping algorithm)

The conversion is always **decode the old literal to its runtime value, then encode that value
into the target delimiter.** This guarantees value preservation and is symmetric.

**Decode** (old literal → value string): `val value = extractLuaString(raw)`
(`LuaLiterals.kt:121`). This already:
- unescapes `\'`, `\"`, `\\`, `\n`, `\t`, …, `\xHH`, `\u{…}`, `\ddd` for short strings;
- returns long-string content verbatim (no escape processing), stripping one leading newline.

**Encode** (value → target literal). New helper added in `LuaLiterals.kt`,
`fun encodeLuaString(value: String, target: Form): String`:

- **Form.SINGLE → `'…'`:** wrap in `'`. Inside the content:
  - replace each `\` with `\\` (do this FIRST);
  - replace each `'` with `\'`;
  - replace control chars that have a named escape (`\n`, `\r`, `\t`, …) with that escape
    (reuse the inverse of `SIMPLE_ESCAPES`, `LuaLiterals.kt:3-14`);
  - leave `"` **unescaped** (it is not the delimiter).
- **Form.DOUBLE → `"…"`:** identical, but escape `"` (not `'`); leave `'` unescaped.
- **Form.LONG → `[[…]]` / `[=…=[…]=…=]`:** **no escaping at all** — the content is written
  literally. Choose the bracket level (see §3.3). Prepend a single `\n` after the opener iff the
  content itself starts with `\n` (Lua swallows the first newline of a long string, so a leading
  newline must be doubled to survive; this matches the strip in `extractLuaString`, line 137).

**Concrete transforms (match the requirements' test cases):**
- `'a"b'` → decode → `a"b` → encode DOUBLE → `"a\"b"` (TC4).
- `"a\"b"` → decode → `a"b` → encode SINGLE → `'a"b'` (TC5; round-trips TC4).
- `"it's"` → decode → `it's` → encode SINGLE → `'it\'s'` (TC6).
- `"tab\there"` → decode → `tab<TAB>here` → encode LONG → `[[tab<TAB>here]]` (TC7).

### 3.3 Long-bracket level selection & the `]]`-in-content guard (INTENT-01-03)

Long strings do no escape processing, so a closer sequence appearing in the content would
terminate the literal early. Algorithm `fun longBracketLevel(value): Int`:

```
level = 0
while value.contains("]" + "=".repeat(level) + "]"):
    level += 1
return level
```

The opener is `"[" + "=".repeat(level) + "["` and the closer is `"]" + "=".repeat(level) + "]"`.
- `value = "hello"` → level 0 → `[[hello]]`.
- `value = "a]]b"` → level 0 closer `]]` is present → level 1 → opener `[=[`, closer `]=]`?
  `]=]` not present either, but the content `a]]b` contains `]]` not `]=]`, so level 1 is safe:
  `[=[a]]b]=]`. (Requirement TC8 illustrates the *principle* with `[==[…]==]`; the algorithm
  picks the **minimal safe level** — `[=[a]]b]=]` is the actual minimal output. The test asserts
  the produced literal is balanced and decodes back to `a]]b`, not a fixed level string — see
  the implementation-plan note.)
- A value containing a bare `]` is fine at level 0 (only the full closer matters).

This means the LONG target is **always representable** — we never skip the form; INTENT-01-03's
"skip" fallback is unnecessary because raising the level always yields a safe closer. The
requirement's "OR raise the bracket level" branch is the one chosen; record this in
requirements/risks as the resolved decision.

> **Edge note:** a value containing a literal newline is fine in a long string. A value that
> *cannot* be expressed at all in a short string does not exist (short strings can escape any
> char), so SINGLE/DOUBLE targets are always representable too.

### 3.4 Applying the edit

In `invoke(project, editor, file)`:

1. `val element = file.findElementAt(editor.caretModel.offset) ?: return`
2. Walk up to the `STRING` leaf: if `element`'s type is `LuaElementTypes.STRING` use it, else
   `PsiTreeUtil.getParentOfType(element, LuaTerminalExpr::class.java)?.string ?: return`.
   (Caret may be on the leaf itself or just inside its `LuaTerminalExpr`.)
3. Compute `newText = encodeLuaString(extractLuaString(leaf.text), nextForm)`.
4. Replace the leaf's text range in the document:
   `editor.document.replaceString(leaf.textRange.startOffset, leaf.textRange.endOffset, newText)`
   inside the implicit write action the platform wraps `invoke` in. (Intentions run on the EDT
   under a write command; no explicit `WriteCommandAction` needed — mirrors how
   `LuaGenerateDocIntention.invoke` edits via the document/template directly.)
5. `startInWriteAction()` returns `true` (default for `BaseIntentionAction`), so the document
   edit is legal.

### 3.5 `isAvailable`

```
override fun isAvailable(project, editor, file): Boolean {
  if (file !is LuaFile) return false
  val element = file.findElementAt(editor.caretModel.offset) ?: return false
  val leaf = stringLeafFor(element) ?: return false       // §3.4 step 2
  val form = currentForm(leaf.text)
  if (form == Form.UNKNOWN) return false
  actionText = textForNext(form)                          // §1 menu text
  return true
}
```

`stringLeafFor` returns the leaf iff its element type is `LuaElementTypes.STRING` (directly or
via the enclosing `LuaTerminalExpr.string`). This satisfies INTENT-01-02 (TC9: caret on
`local s` is not inside a string → null → not offered).

## 4. Integration Points (plugin.xml + resources)

### 4.1 `plugin.xml` registration

Add an `<intentionAction>` next to the existing one
(`src/main/resources/META-INF/plugin.xml:355-358`), inside the same `<extensions defaultExtensionNs="com.intellij">` block:

```xml
<intentionAction>
  <className>net.internetisalie.lunar.lang.insight.LuaStringConversionIntention</className>
  <category>Lua</category>
</intentionAction>
```

(The skeleton's `<className>net.internetisalie.lunar.lang.intentions.LuaStringConversionIntention</className>`
is corrected to the `lang.insight` package — see §1.)

### 4.2 Required intention description resources

Registering an `<intentionAction>` **requires** a description resource directory named after the
**simple class name** (verified by the existing intention: the dir
`src/main/resources/intentionDescriptions/LuaGenerateDocIntention/` holds `description.html`,
`before.template.lua`, `after.template.lua`). Create:

- `src/main/resources/intentionDescriptions/LuaStringConversionIntention/description.html`
  — short HTML (mirror the existing one's `<html><body>…</body></html>` shape,
  `intentionDescriptions/LuaGenerateDocIntention/description.html`):
  ```html
  <html>
  <body>
  Cycles the string literal under the caret between single quotes, double quotes, and
  long-bracket form, preserving the string value (escaping or unescaping as needed).
  </body>
  </html>
  ```
- `src/main/resources/intentionDescriptions/LuaStringConversionIntention/before.template.lua`
  — `local s = 'hello'`
- `src/main/resources/intentionDescriptions/LuaStringConversionIntention/after.template.lua`
  — `local s = "hello"`

  > Filename note: the existing resource files are named `before.template.lua` /
  > `after.template.lua` (NOT `before.lua.template`); match that exactly.

## 5. Prior Art in This Repo

- **Intention scaffolding:** `LuaGenerateDocIntention`
  (`lang/insight/LuaGenerateDocIntention.kt`) — copy its structure (base class, override set,
  `findElementAt` + `getParentOfType`, document edits) and its resource-dir convention.
- **Escape decode:** `LuaLiterals.extractLuaString` / `unescapeLuaString` / the `SIMPLE_ESCAPES`
  map (`lang/syntax/LuaLiterals.kt`) — reused for decode; the inverse (encode) is added here.
- **No existing string-conversion / quote-toggle code exists.** Confirmed by grep: no class or
  function references "string conversion", "quote", or a quote-cycling intention in
  `src/main` (only the lexer/escape helpers above). This feature is greenfield apart from those
  helpers.

## 6. Requirement Coverage

| Requirement | Covered by |
|---|---|
| INTENT-01-01 Cycle Quote Type | §3.1–3.3 decode/encode + cycle |
| INTENT-01-02 Caret-in-string applicability | §3.5 `isAvailable` / `stringLeafFor` |
| INTENT-01-03 Long-string `]]` guard | §3.3 minimal-safe-level selection |

## 7. Open Questions

_None._
