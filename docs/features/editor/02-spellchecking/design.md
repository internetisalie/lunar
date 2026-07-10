---
id: "EDITOR-02-DESIGN"
title: "Technical Design"
type: "design"
parent_id: "EDITOR-02"
folders:
  - "[[features/editor/02-spellchecking/requirements|requirements]]"
---

# Technical Design: EDITOR-02 — Spellchecking

## 1. Architecture Overview

### Current State
Lunar registers no `com.intellij.spellchecker.support` extension (verified: `grep -rl
"SpellcheckingStrategy\|spellchecker" src/main` returns nothing; `src/main/resources/META-INF/plugin.xml`
has no `<spellchecker.support>`). With no strategy registered, the platform falls back to the
default file-level behavior, which does **not** understand Lua's comment/string/identifier PSI —
Lua comments and strings are raw `LeafPsiElement`s carrying a `LuaTokenType` element type
(`LuaTokenTypes.SHORTCOMMENT`, `LONGCOMMENT`, `STRING`), **not** `PsiComment`/`PsiLiteral`
composites, so the default strategy's `element instanceof PsiComment` branch never fires for
Lua comments. Result: no Lua-aware spellchecking, which users register as "thin."

### Prior Art in This Repo
Searched `src/main` for existing spellcheck/tokenizer components and reusable name/keyword sources:

- **No existing spellchecker** — `grep -rl "SpellcheckingStrategy\|spellchecker" src/main` → empty.
  This is a greenfield strategy; nothing to extend or replace.
- **`net.internetisalie.lunar.lang.syntax.LuaSyntax`** (`src/main/kotlin/.../lang/syntax/LuaSyntax.kt:35,42,30`)
  already defines the exact `TokenSet`s this feature keys on: `CommentTokens`
  (`SHORTCOMMENT`, `LONGCOMMENT`, `SHEBANG`, `LUACATS_COMMENT`), `StringLiteralTokens` (`STRING`),
  and `IdentifierTokens`. **Reused, not duplicated.**
- **`net.internetisalie.lunar.analysis.inspections.LuaStandardGlobals`**
  (`src/main/kotlin/.../analysis/inspections/LuaStandardGlobals.kt:44`) — deterministic per-level
  allowlist of built-in global names with `contains(name, level): Boolean`. **Reused** as the
  stdlib suppression source for EDITOR-02-05.
- **`net.internetisalie.lunar.lang.LuaKeywords`** (`src/main/kotlin/.../lang/LuaKeywords.kt:15`)
  — `isReserved(word): Boolean`. Reserved words are never `IDENTIFIER` leaves, so keyword
  suppression is structural; `LuaKeywords` is used as a belt-and-suspenders guard.
- **`net.internetisalie.lunar.refactoring.LuaNamesValidator`** + `LuaRefactoringSupportProvider`
  (registered in `plugin.xml:301,308`) — already wire Rename for Lua. The spellchecker's Rename
  typo-fix is produced automatically by the platform base class (see §2.1 / §3.4); **this feature
  adds no new rename code**, it relies on the existing `refactoringSupport`/`namesValidator`.
- **Identifier PSI**: `net.internetisalie.lunar.lang.psi.LuaNameDeclElement : PsiNameIdentifierOwner`
  with `getNameIdentifier()` returning the `IDENTIFIER` child
  (`src/main/kotlin/.../lang/psi/LuaBaseElements.kt:47,52`). Declarations are already
  `PsiNameIdentifierOwner`, so the platform's built-in `PsiIdentifierOwnerTokenizer` path
  (§3.3) works with no extra PSI work.
  > **⚠ Correction (implementation 2026-07-10):** this grounding is **wrong** — `LuaNameDeclElement`
  > is applied by the grammar to **only `labelName`** (`::labels::`); locals/functions/params are all
  > plain `LuaNameRef` (identical to references). The tokenizer instead routes `LuaNameRef` and emits
  > only in declaration-only parents (`LuaAttName`, `LuaLocalFuncDecl`, `LuaNameList`). See
  > `requirements.md` → *Implementation notes* for the covered/excluded set.

### Target State
One new `SpellcheckingStrategy` subclass, `LuaSpellcheckingStrategy`, overrides `getTokenizer`
and dispatches by `element.node.elementType` (a `TokenSet` membership test — the Groovy pattern,
not `instanceof`) to one of three tokenizers:

```
element ──► LuaSpellcheckingStrategy.getTokenizer(element)
   ├─ elementType ∈ LuaSyntax.CommentTokens (non-LuaCATS) ──► TEXT_TOKENIZER (plain-text splitter)
   ├─ elementType == LUACATS_COMMENT / DESCRIPTION / COMMENT ─► TEXT_TOKENIZER over prose ranges only (§3.5)
   ├─ elementType ∈ LuaSyntax.StringLiteralTokens ──────────► LuaStringTokenizer (escape-aware, §3.2)
   ├─ element is LuaNameDeclElement (PsiNameIdentifierOwner)─► LuaIdentifierTokenizer (suppress-aware, §3.3/§3.4)
   └─ otherwise ────────────────────────────────────────────► EMPTY_TOKENIZER  (keywords/ops/numbers)
```

Registration is a single declarative `<spellchecker.support language="Lua" .../>` line
(§7). No `<depends>` addition is required — `com.intellij.spellchecker.support` is a platform
extension point (declared in the bundled spellchecker module, used by Properties/Groovy which
add no `<depends>` for it).

## 2. Core Components

### 2.1 `net.internetisalie.lunar.lang.spellcheck.LuaSpellcheckingStrategy`
- **Responsibility**: route each Lua PSI element to the correct `Tokenizer` (comment / string /
  identifier / empty) by element-type membership.
- **Threading**: called by the platform inside a read action during highlighting (pooled/DHL
  thread); the class holds no state and retains no `Project`/`Editor`/`PsiFile` references.
- **Collaborators**: `com.intellij.spellchecker.tokenizer.SpellcheckingStrategy` (base, verified
  `spellchecker/src/.../tokenizer/SpellcheckingStrategy.java:53`), `LuaSyntax` token sets,
  `LuaStringTokenizer`, `LuaIdentifierTokenizer`, `SpellcheckingStrategy.TEXT_TOKENIZER`,
  `SpellcheckingStrategy.EMPTY_TOKENIZER`.
- **Contract conformance**: implements `com.intellij.openapi.project.DumbAware` (no index access
  in the tokenizers themselves; the stdlib check reads settings state, which is index-free).
- **Key API**:
  ```kotlin
  package net.internetisalie.lunar.lang.spellcheck

  class LuaSpellcheckingStrategy : SpellcheckingStrategy(), DumbAware {
      private val stringTokenizer = LuaStringTokenizer()
      private val identifierTokenizer = LuaIdentifierTokenizer()

      override fun getTokenizer(element: PsiElement): Tokenizer<*> {
          if (isInjectedLanguageFragment(element)) return EMPTY_TOKENIZER
          val type = element.node?.elementType ?: return EMPTY_TOKENIZER
          return when {
              type == LuaLazyElementTypes.LUACATS_COMMENT -> catsCommentTokenizer  // §3.5
              LuaSyntax.CommentTokens.contains(type)      -> TEXT_TOKENIZER
              LuaSyntax.StringLiteralTokens.contains(type) -> stringTokenizer
              element is LuaNameDeclElement               -> identifierTokenizer
              else                                        -> EMPTY_TOKENIZER
          }
      }
  }
  ```
  Note `SHEBANG` is in `CommentTokens` but the base class already skips shebangs; because we
  route `SHEBANG` to `TEXT_TOKENIZER` explicitly, §3.1 adds the shebang guard.

### 2.2 `net.internetisalie.lunar.lang.spellcheck.LuaStringTokenizer`
- **Responsibility**: spellcheck the textual content of a Lua string literal, stripping quotes /
  long-bracket delimiters and decoding escape sequences so a typo range maps back to source
  offsets correctly.
- **Threading**: same read-action context as §2.1; stateless.
- **Collaborators**: `com.intellij.spellchecker.tokenizer.EscapeSequenceTokenizer<PsiElement>`
  (base, verified `spellchecker/src/.../tokenizer/EscapeSequenceTokenizer.java:10`, static
  `processTextWithOffsets` at :13), `com.intellij.spellchecker.inspections.PlainTextSplitter`
  (`inspections/PlainTextSplitter.java:23`), `TokenConsumer`.
- **Key API**:
  ```kotlin
  class LuaStringTokenizer : EscapeSequenceTokenizer<PsiElement>() {
      override fun tokenize(element: PsiElement, consumer: TokenConsumer) { /* §3.2 */ }
  }
  ```

### 2.3 `net.internetisalie.lunar.lang.spellcheck.LuaIdentifierTokenizer`
- **Responsibility**: spellcheck a declaration's name via `IdentifierSplitter`
  (camelCase / snake_case split) **unless** the name is a suppressed token (stdlib global or
  LuaCATS type/keyword), in which case emit nothing.
- **Threading**: same read-action context; stateless. Reads `LuaProjectSettings.getInstance(
  project).state.languageLevel` (index-free service call) for the stdlib check.
- **Collaborators**: `com.intellij.spellchecker.tokenizer.PsiIdentifierOwnerTokenizer` behavior
  (reference: `tokenizer/PsiIdentifierOwnerTokenizer.java`), `IdentifierSplitter`
  (`inspections/IdentifierSplitter.java:19`), `LuaStandardGlobals.contains`,
  `LuaProjectSettings.getInstance` (`settings/LuaProjectSettings.kt:141`),
  `net.internetisalie.lunar.lang.LuaLanguageLevel`.
- **Key API**:
  ```kotlin
  class LuaIdentifierTokenizer : Tokenizer<LuaNameDeclElement>() {
      override fun tokenize(element: LuaNameDeclElement, consumer: TokenConsumer) { /* §3.3, §3.4 */ }
  }
  ```

### 2.4 `net.internetisalie.lunar.lang.spellcheck.LuaSpellcheckSuppressions`
- **Responsibility**: single decision function `isSuppressed(name, project): Boolean` — the
  identifier suppression list for EDITOR-02-05.
- **Threading**: pure/stateless; called inside the read action.
- **Collaborators**: `LuaStandardGlobals`, `LuaKeywords`, `LuaProjectSettings`.
- **Key API**:
  ```kotlin
  object LuaSpellcheckSuppressions {
      // Cats builtin type names (Lua 5.x @type primitives), lowercase.
      private val CATS_TYPES = setOf(
          "nil", "boolean", "number", "string", "userdata", "function",
          "thread", "table", "integer", "any", "self", "lightuserdata", "void", "unknown",
      )
      fun isSuppressed(name: String, project: Project): Boolean
  }
  ```

## 3. Algorithms

### 3.1 Element → tokenizer dispatch (`LuaSpellcheckingStrategy.getTokenizer`)
- **Input → Output**: `PsiElement` → `Tokenizer<*>`.
- **Steps**:
  1. If `isInjectedLanguageFragment(element)` (base helper) → `EMPTY_TOKENIZER`.
  2. `type = element.node?.elementType`; if null → `EMPTY_TOKENIZER`.
  3. If `type == LuaLazyElementTypes.LUACATS_COMMENT` → `catsCommentTokenizer` (§3.5).
  4. If `type == LuaTokenTypes.SHEBANG` (i.e. `element.text.startsWith("#!")`) → `EMPTY_TOKENIZER`.
  5. Else if `LuaSyntax.CommentTokens.contains(type)` → `TEXT_TOKENIZER`.
  6. Else if `LuaSyntax.StringLiteralTokens.contains(type)` → `stringTokenizer` (§3.2).
  7. Else if `element is LuaNameDeclElement` → `identifierTokenizer` (§3.3).
  8. Else → `EMPTY_TOKENIZER`.
- **Rules / edge handling**: order matters — the LuaCATS branch precedes the generic comment
  branch because `LUACATS_COMMENT` is also a member of `CommentTokens`. `type` is compared by
  reference identity (element types are static singletons — see CLAUDE.md), which `TokenSet.contains`
  uses. Keywords/operators/numbers fall through to step 8; they are never `LuaNameDeclElement`
  and their token types are absent from `CommentTokens`/`StringLiteralTokens`.

### 3.2 String content tokenization (`LuaStringTokenizer.tokenize`)
- **Input → Output**: `PsiElement` (a `STRING` leaf) + `TokenConsumer` → tokens fed to consumer.
- **Steps**:
  1. `raw = element.text`.
  2. Compute `(inner, prefixLen)` = `stripDelimiters(raw)` per the table below.
  3. If `inner` contains no backslash **or** the string is a long-bracket string (no escapes):
     `consumer.consumeToken(element, false, prefixLen, TextRange.allOf(inner), PlainTextSplitter.getInstance())`
     — the 6-arg `TokenConsumer.consumeToken(element, word, useRename, offset, range, splitter)`
     overload, `useRename=false` (strings are not renameable), `offset=prefixLen`.
  4. Else (short string with escapes): unescape into a `StringBuilder` and build an
     `offsets: IntArray` mapping each decoded char back to its source column, then delegate to
     `EscapeSequenceTokenizer.processTextWithOffsets(element, consumer, unescaped, offsets, prefixLen)`.
- **`stripDelimiters` rules** (Lua 5.1–5.4 string forms):

  | Source form | Example | `prefixLen` | `inner` |
  |-------------|---------|-------------|---------|
  | Double-quoted | `"helo wrld"` | 1 | `helo wrld` (drop leading `"` and trailing `"`) |
  | Single-quoted | `'helo'` | 1 | `helo` |
  | Long bracket level 0 | `[[helo]]` | 2 | `helo` |
  | Long bracket level N | `[==[helo]==]` | 3+N | `helo` (prefix = `2 + N + 1`) |

  Detect long-bracket by `raw.startsWith("[")` and a `^\[=*\[` match; `N` = count of `=` between
  the two `[`; `prefixLen = 2 + N`. Trailing delimiter length is symmetric and is **not** needed
  because `TextRange.allOf(inner)` is anchored to `inner`, and `offset = prefixLen` shifts it into
  source coordinates.
- **Escape decoding** (only for short strings): reuse
  `com.intellij.codeInsight.CodeInsightUtilCore.parseStringCharacters(text, sb, offsets)` exactly
  as `PropertiesSpellcheckingStrategy` does
  (`properties/.../PropertiesSpellcheckingStrategy.java:86`). Lua-specific escapes it does not
  model (`\u{XXX}`, `\z`, `\ddd`) degrade gracefully: an unrecognized escape leaves the sequence
  as-is, which only risks a benign false token, never a crash. This limitation is tracked as
  Gap 2.1.
- **Edge handling**: empty `inner` → `TextRange.allOf("")` is empty; consumer ignores it. Unclosed
  string (`BAD_CHARACTER` recovery) never reaches here because its element type is not `STRING`.

### 3.3 Identifier tokenization (`LuaIdentifierTokenizer.tokenize`)
- **Input → Output**: `LuaNameDeclElement` + `TokenConsumer` → tokens or nothing.
- **Steps**:
  1. `identifier = element.nameIdentifier ?: return` (the `IDENTIFIER` leaf).
  2. `name = identifier.text`; if `name.isEmpty()` return.
  3. If `LuaSpellcheckSuppressions.isSuppressed(name, element.project)` → return (emit nothing).
  4. Compute `offset = identifier.textRange.startOffset - element.textRange.startOffset`
     (mirrors `PsiIdentifierOwnerTokenizer`); if `offset < 0`, fall back to
     `element = identifier` and `offset = 0`.
  5. `consumer.consumeToken(element, name, true, offset, TextRange.allOf(name),
     IdentifierSplitter.getInstance())` — `useRename=true` so the platform offers the Rename fix.
- **Rules / edge handling**: only *declarations* (`LuaNameDeclElement`) are tokenized, so
  references (uses of stdlib globals like `pairs`) are never checked — they hit the
  `EMPTY_TOKENIZER` fall-through in §3.1 because a `LuaNameRefElement` is not a
  `PsiNameIdentifierOwner`. This is intentional and satisfies EDITOR-02-05 for references for free.

### 3.4 Rename / change-to / save-to-dictionary quick fixes
- **No custom code.** The three quick fixes required by EDITOR-02-04 are produced by the platform
  base class `SpellcheckingStrategy.getDefaultRegularFixes`
  (`tokenizer/SpellcheckingStrategy.java:248`), invoked automatically once a tokenizer reports a
  typo with `useRename`:
  - `useRename=true` **and** the element has a `PsiNamedElement` ancestor →
    `SpellCheckerQuickFixFactory.rename(...)` (uses the registered `LuaRefactoringSupportProvider`
    / `LuaNamesValidator`). Our identifier tokenizer passes `useRename=true` and the reported
    element is the `LuaNameDeclElement` (a `PsiNamedElement`), so Rename is offered.
  - otherwise → `changeToVariants(...)` + `saveTo(...)`. Comments and strings pass
    `useRename=false`, so they get change-to + save-to-dictionary only.
- **Rule**: the feature must **not** override `getRegularFixes`; the defaults already satisfy
  EDITOR-02-04. Passing the correct `useRename` flag per element class (§3.2 false, §3.3 true)
  is the only lever.

### 3.5 LuaCATS comment tokenization (`catsCommentTokenizer`)
- **Input → Output**: the `LUACATS_COMMENT` composite (`LuaCatsLazyCommentImpl`) → prose tokens only.
- **Problem**: routing the whole comment to `TEXT_TOKENIZER` would spellcheck tag names and type
  identifiers (`@class`, `Builder`, `boolean`), producing noise. LuaCATS prose lives in
  `LuaCatsDescription` children (`LuaCatsElementTypes.DESCRIPTION`, verified
  `luacats/lang/psi/LuaCatsElementTypes.java:27`) and plain trailing `LuaCatsElementTypes.COMMENT`
  (:25).
- **Steps** (`catsCommentTokenizer` is a `Tokenizer<LuaCatsComment>`):
  1. For each descendant `d` of the comment where
     `d.node.elementType ∈ { DESCRIPTION, COMMENT }` (via `PsiTreeUtil.findChildrenOfType` on
     `LuaCatsDescription` plus a leaf scan for `COMMENT`):
  2. `consumer.consumeToken(d, false, 0, TextRange.allOf(d.text), PlainTextSplitter.getInstance())`.
  - Tag identifiers, `ARG_TYPE`, `BUILTIN_TYPE`, `CLASS_TAG` names, etc. are **not** visited →
    not spellchecked.
- **Rules / edge handling**: a comment with no description/prose children emits nothing (a bare
  `---@class Foo` is silent, correct). Nested type prose is never entered because we only match
  the two prose element types.

## 4. External Data & Parsing
No external CLI/file/network input. The only "parsing" is delimiter stripping and escape decoding
of in-buffer string text, fully specified in §3.2.

## 5. Data Flow

### Example 1: comment typo (EDITOR-02-01)
Source `-- helo world`. Lexer emits a `SHORTCOMMENT` leaf. Highlighting calls
`getTokenizer(leaf)` → step §3.1.5 (`CommentTokens.contains`) → `TEXT_TOKENIZER` →
`PlainTextSplitter` splits `helo`, `world` → `helo` is unknown → `<TYPO>` range over `helo`;
`useRename=false` → change-to + save-to-dictionary fixes.

### Example 2: identifier typo with Rename (EDITOR-02-03/04)
Source `local recieveBuffer = 1`. `LuaLocalVarDecl` is a `LuaNameDeclElement`;
`getTokenizer` → §3.1.7 → `LuaIdentifierTokenizer`. `isSuppressed("recieveBuffer")` = false →
`IdentifierSplitter` splits `recieve`, `Buffer` → `recieve` unknown → `<TYPO>` over `recieve`,
`useRename=true` → Rename + change-to + save fixes; accepting Rename drives the existing
`LuaRefactoringSupportProvider`.

### Example 3: suppressed stdlib redefinition (EDITOR-02-05)
Source `local pairs = function() end`. Decl name `pairs`; §3.3 step 3
`LuaStandardGlobals.contains("pairs", LUA54)` = true → tokenizer emits nothing → no typo.

## 6. Edge Cases
- **Shebang `#!/usr/bin/lua`**: `SHEBANG` ∈ `CommentTokens` but §3.1 step 4 short-circuits to
  `EMPTY_TOKENIZER`.
- **Long-bracket string `[==[ ... ]==]`**: handled by §3.2 `stripDelimiters` level-N rule; no
  escape decoding (long strings have no escapes).
- **Bare `---@alias Name string`**: no `DESCRIPTION` child → §3.5 emits nothing.
- **camelCase acronyms (`HTTPServer`)**: delegated to `IdentifierSplitter`'s own heuristics; not
  our concern.
- **Very short names (`i`, `db`, `fn`)**: `IdentifierSplitter` does not flag < 3-letter tokens as
  typos (platform behavior); noise risk covered in Risk 1.1.
- **Reference (non-declaration) identifiers**: never tokenized (§3.3 rule).

## 7. Integration Points

```xml
<!-- src/main/resources/META-INF/plugin.xml, inside the existing
     <extensions defaultExtensionNs="com.intellij"> block -->
<spellchecker.support
    language="Lua"
    implementationClass="net.internetisalie.lunar.lang.spellcheck.LuaSpellcheckingStrategy"/>
```

- No `<depends>` entry is added — `com.intellij.spellchecker.support` is a platform EP (Properties
  and Groovy register against it with no plugin dependency).
- No new settings keys, indexes, or actions. Reuses `LuaProjectSettings` (existing),
  `LuaStandardGlobals` (existing), `LuaKeywords` (existing), `LuaSyntax` token sets (existing),
  and the existing `lang.refactoringSupport` / `lang.namesValidator` registrations for Rename.

## 8. Requirement Coverage

| Requirement | Priority | Implemented by (section) |
|-------------|----------|--------------------------|
| EDITOR-02-01 Comment spellcheck | M | §2.1 (§3.1.5), §3.5 (LuaCATS prose), §5 Ex.1 |
| EDITOR-02-02 String literal spellcheck | S | §2.2, §3.2, §6 (long-bracket) |
| EDITOR-02-03 Identifier spellcheck | S | §2.3, §3.3, §5 Ex.2 |
| EDITOR-02-04 Quick fixes | S | §3.4 (default fixes; useRename per §3.2/§3.3), §5 Ex.2 |
| EDITOR-02-05 Suppression | C | §2.4, §3.3 step 3, §3.5, §5 Ex.3 |

## 9. Alternatives Considered
- **`instanceof PsiComment` dispatch** (default base-class path): rejected — Lua comments are raw
  `LuaTokenType` leaves, not `PsiComment` (verified `LuaTokenType.kt`, `LuaElementTypes.java:100,122`),
  so this branch never fires. Element-type membership (the Groovy strategy pattern) is required.
- **Tokenize whole `LUACATS_COMMENT` as plain text**: rejected — spellchecks tag/type identifiers;
  §3.5 restricts to `DESCRIPTION`/`COMMENT` prose.
- **Custom rename/change-to quick-fix classes**: rejected — `getDefaultRegularFixes` already
  produces all three required fixes; adding them would duplicate platform code.
- **Spellcheck references too**: rejected — would re-flag every stdlib call site; declaration-only
  scoping (via `PsiNameIdentifierOwner`) is both correct and simpler.

## 10. Open Questions

_None — feature has cleared the planning bar. Remaining unknowns are tracked as de-risking tasks in risks-and-gaps.md._
