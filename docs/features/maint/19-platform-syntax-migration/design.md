---
id: "MAINT-19-DESIGN"
title: "MAINT-19: Design — Kotlin-native token holders"
type: "design"
priority: "low"
parent_id: "MAINT-19"
folders:
  - "[[features/maint/19-platform-syntax-migration/requirements|requirements]]"
---

# MAINT-19: Technical Design

## Goal restated

Make `LuaTokenTypes` and `LuaCatsTokenTypes` Kotlin-native without changing a single token
`IElementType` instance, the generated lexers' *bare* constant usage, or any Kotlin call site.
Full `com.intellij.platform.syntax` adoption is **out of scope** (see `requirements.md` §Scoping
and `risks-and-gaps.md` DR `MAINT-19-00-1`).

## The binding constraint (why this is not a trivial rename)

Two consumers reference the constants, with **incompatible** access shapes:

1. **Generated Java lexers** reference them *bare*: `_LuaLexer.java:750` `{ return NUMBER; }`,
   `:780` `{ return IDENTIFIER; }`, `:675` `{ return WRONG; }`. Today this resolves because
   `_LuaLexer implements LuaTokenTypes` (a Java interface) and inherits its constants into scope.
2. **Kotlin code** references them *qualified*: `LuaLexer.kt` uses `LuaTokenTypes.SHORTCOMMENT`,
   `LuaTokenTypes.LONGSTRING`, etc.; `LuaCatsLexer.kt:11-18` uses `LuaCatsTokenTypes.LCATS_DASHES`
   … `LuaCatsTokenTypes.LCATS_CODE`; `LuaCatsAnnotator.kt:9` imports the holder. The **complete**
   qualified-consumer set (from `grep -rln 'LuaTokenTypes' src/main/kotlin/` +
   `grep -rln 'LuaCatsTokenTypes' src/main/kotlin/`, excluding the `.flex` sources) is 10 Kotlin
   files: `LuaRequireReferenceContributor.kt`, `LuaCompletionContributor.kt`,
   `LuaParserDefinition.kt`, `LuaSyntaxHighlighter.kt`, `LuaSyntax.kt`, `LuaCodeContextPredicate.kt`,
   `LuaDocGenerator.kt`, `LuaLexer.kt` (all reference `LuaTokenTypes`), plus `LuaCatsAnnotator.kt`,
   `LuaCatsLexer.kt`, and `LuaSyntax.kt` again (reference `LuaCatsTokenTypes`). All must compile
   **unchanged** — the `@JvmField object` preserves qualified `LuaTokenTypes.X` access, so none of
   these files are edited.

A Kotlin `object` with `@JvmField` constants satisfies **both** shapes simultaneously:

- Kotlin sees `LuaTokenTypes.NUMBER` (member access on the `object`) — unchanged call sites.
- Java sees `NUMBER` as a `public static final IElementType` field on class `LuaTokenTypes`, so a
  Java `import static net.internetisalie.lunar.lang.lexer.LuaTokenTypes.*;` in the `.flex` header
  brings all constants into bare scope — replacing the interface-inheritance mechanism.

`@JvmField` is already used in this repo (`lang/LuaIcons.kt`, `lang/format/LuaCodeStyleSettings.kt`),
so the idiom is established.

## Data model — `LuaTokenTypes.kt` (NEW, replaces the `.java`)

Path: `src/main/kotlin/net/internetisalie/lunar/lang/lexer/LuaTokenTypes.kt`
Package: `net.internetisalie.lunar.lang.lexer` (unchanged).

Shape (every one of the 73 constants — 71 `new LuaElementType(...)` + `TokenType.BAD_CHARACTER` + `TokenType.WHITE_SPACE` — from the existing
`src/main/java/net/internetisalie/lunar/lang/lexer/LuaTokenTypes.java` is ported verbatim; the
snippet below shows the exact translation pattern for the three representative source forms):

```kotlin
package net.internetisalie.lunar.lang.lexer

import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import net.internetisalie.lunar.lang.psi.LuaElementType

object LuaTokenTypes {
    // form 1: reused platform constant   (Java: IElementType WRONG = TokenType.BAD_CHARACTER;)
    @JvmField val WRONG: IElementType = TokenType.BAD_CHARACTER

    // form 2: fresh LuaElementType        (Java: IElementType NUMBER = new LuaElementType("number");)
    @JvmField val NUMBER: IElementType = LuaElementType("number")
    @JvmField val IDENTIFIER: IElementType = LuaElementType("identifier")
    @JvmField val NL_BEFORE_LONGSTRING: IElementType = LuaElementType("newline after longstring start bracket")
    // ... every remaining constant, preserving the EXACT debug-name string argument ...
    @JvmField val WHILE: IElementType = LuaElementType("while")
}
```

Rules for the port (mechanical — no judgement):
- **Order-independent**: copy each `IElementType NAME = <initializer>;` line from the `.java`, in the
  same order, as `@JvmField val NAME: IElementType = <initializer-kotlin>`.
- **Initializer translation**: `new LuaElementType("s")` → `LuaElementType("s")` (Kotlin ctor call;
  `LuaElementType` is already Kotlin at `lang/psi/LuaElementType.kt`); the two reused platform
  constants `TokenType.BAD_CHARACTER` and `TokenType.WHITE_SPACE` are copied verbatim.
- **Debug-name strings copied byte-for-byte** — these strings are asserted by tests
  (`IElementType.toString()` format is load-bearing, per CLAUDE.md) and are the identity of each token.
- **`object`** (not `class`, not top-level `val`s): `object` gives one canonical instance whose
  members compile to `public static final` fields — required so the generated lexer's bare `NUMBER`
  resolves via `import static`.

> **Element-type registry note (CLAUDE.md "Element types must be static singletons"):** an `object`
> initialises its `@JvmField`s exactly once at class-load, identical to the interface's one-time field
> init. No new `IElementType` instances are created per parse — the registry-size invariant is preserved.

## Data model — `LuaCatsTokenTypes.kt` (NEW, replaces the `.java`)

Path: `src/main/kotlin/net/internetisalie/lunar/luacats/lang/lexer/LuaCatsTokenTypes.kt`
Package: `net.internetisalie.lunar.luacats.lang.lexer` (unchanged).

Ports all 11 `LCATS_*` constants plus the `LUACATS_TOKENS` `TokenSet` from
`src/main/java/net/internetisalie/lunar/luacats/lang/lexer/LuaCatsTokenTypes.java`:

```kotlin
package net.internetisalie.lunar.luacats.lang.lexer

import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet

object LuaCatsTokenTypes {
    @JvmField val LCATS_DASHES: IElementType = LuaCatsElementType("LCATS_DASHES")
    @JvmField val LCATS_WHITESPACE: IElementType = LuaCatsElementType("LCATS_WHITESPACE")
    @JvmField val LCATS_TEXT: IElementType = LuaCatsElementType("LCATS_TEXT")
    @JvmField val LCATS_NAME: IElementType = LuaCatsElementType("LCATS_NAME")
    @JvmField val LCATS_STRING: IElementType = LuaCatsElementType("LCATS_STRING")
    @JvmField val LCATS_SYMBOL: IElementType = LuaCatsElementType("LCATS_SYMBOL")
    @JvmField val LCATS_TAG: IElementType = LuaCatsElementType("LCATS_TAG")
    @JvmField val LCATS_KEYWORD: IElementType = LuaCatsElementType("LCATS_KEYWORD")
    @JvmField val LCATS_CODE: IElementType = LuaCatsElementType("LCATS_CODE")
    @JvmField val LCATS_NUMBER: IElementType = LuaCatsElementType("LCATS_NUMBER")
    @JvmField val LCATS_BAD_CHARACTER: IElementType = TokenType.BAD_CHARACTER

    @JvmField val LUACATS_TOKENS: TokenSet = TokenSet.create(
        LCATS_DASHES, LCATS_WHITESPACE, LCATS_TEXT, LCATS_NAME, LCATS_STRING,
        LCATS_SYMBOL, LCATS_TAG, LCATS_KEYWORD, LCATS_CODE, LCATS_NUMBER,
    )
}
```

`LuaCatsElementType` is already Kotlin (`luacats/lang/lexer/LuaCatsElementType.kt`), so `new
LuaCatsElementType("s")` → `LuaCatsElementType("s")`.

## `.flex` source edits

### `lua.flex` (`src/main/kotlin/net/internetisalie/lunar/lang/lexer/lua.flex`)

JFlex copies the header (everything before `%%`) verbatim into the generated Java, and the generated
lexer body already uses bare constant names. Two edits:

1. **Add a Java static import** to the header block (after the existing imports at lines 3-7):
   ```java
   import static net.internetisalie.lunar.lang.lexer.LuaTokenTypes.*;
   ```
2. **Drop the interface from `%implements`** (line 18):
   ```
   %implements FlexLexer
   ```
   (was `%implements FlexLexer, LuaTokenTypes`).

No other line changes: the ruleset still writes `return NUMBER;`, now resolved by the static import.

### `luacats.flex` (`src/main/kotlin/net/internetisalie/lunar/luacats/lang/lexer/luacats.flex`)

1. Add to the header (after the existing imports at lines 18-19):
   ```java
   import static net.internetisalie.lunar.luacats.lang.lexer.LuaCatsTokenTypes.*;
   ```
2. Change line 24 to:
   ```
   %implements FlexLexer
   ```

## Regeneration (manual human-in-the-loop — see implementation-plan)

Per CLAUDE.md, the grammar-kit Gradle plugin is not wired in; the `.flex` → `_*Lexer.java` step is a
**manual IDE action** (JFlex Generator) or the vendored CLI in
`.claude/skills/generate-parser/scripts/generate.sh` (JFlex portion only; `lua.bnf`/`luacats.bnf` are
untouched here). The regenerated `_LuaLexer.java` / `_LuaCatsLexer.java` under `src/main/gen/` are
committed. Expected diff in each generated file: the `implements` clause loses the token interface and
the header gains the `import static` line; the transition-table body is byte-identical.

## Integration points / registration

**No `plugin.xml` change.** The lexer is wired via the existing
`<lang.parserDefinition language="Lua" implementationClass=".../LuaParserDefinition"/>`
(`src/main/resources/META-INF/plugin.xml:105`), and `LuaParserDefinition.createLexer` returns
`LuaLexer()` (`LuaParserDefinition.kt`). Neither is edited. `LuaLexer.kt` wraps
`FlexAdapter(_LuaLexer(null))` — the regenerated `_LuaLexer` keeps that exact constructor/type, so the
adapter chain is untouched.

## Threading / contract conformance

- **Threading:** lexing runs wherever the platform drives it (highlighting pass / parse); this feature
  changes only the *representation* of static constants and introduces **no new runtime code paths**,
  no I/O, no EDT interaction. `object` field init is class-load-time (thread-safe via JVM class-init
  locking).
- **No hard refs** to `Project`/`Editor`/`PsiFile`/`VirtualFile` are introduced (constants are pure
  `IElementType` singletons).
- **No StubIndex / CachedValuesManager** involvement (not a resolution feature).
- **Immutability:** all constants are `val`; `object` is stateless.

## What is explicitly NOT built (grounded scope wall)

- No `%type SyntaxElementType` (would require the JetBrains-internal JFlex skeleton used by
  `json/syntax/.../_JsonLexer.flex`).
- No `com.intellij.platform.syntax.LanguageSyntaxDefinition` extension (JSON's is
  `JsonLanguageDefinition.kt`; adopting it requires a `SyntaxGeneratedParserRuntime` parser Lunar's
  classic Grammar-Kit cannot emit).
- No change to `LuaParserDefinition`, PSI factories, or `lua.bnf`.

## Open Questions

None. Full `platform.syntax` adoption is a tracked de-risking task in `risks-and-gaps.md`.
