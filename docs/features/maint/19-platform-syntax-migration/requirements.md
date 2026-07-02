---
id: "MAINT-19"
title: "MAINT-19: platform.syntax Migration (Kotlin lexer/parser)"
type: "feature"
status: "planned"
priority: "low"
parent_id: "MAINT"
folders:
  - "[[features/maint/requirements|requirements]]"
---

# MAINT-19: platform.syntax Migration (Kotlin lexer/parser)

## Overview

This feature is the carve-out of the two token-constant holders deferred from **MAINT-01**
(Kotlin Conversion): the hand-written Java interfaces
`src/main/java/net/internetisalie/lunar/lang/lexer/LuaTokenTypes.java` (153 lines) and
`src/main/java/net/internetisalie/lunar/luacats/lang/lexer/LuaCatsTokenTypes.java` (34 lines).

MAINT-01 could not convert them because the JFlex-generated lexers inherit their constants by
`%implements`-ing the interface and referencing the constants **bare** (unqualified), a contract
that no idiomatic Kotlin shape preserves without editing the `.flex` sources and regenerating the
lexers:

- `src/main/gen/net/internetisalie/lunar/lang/lexer/_LuaLexer.java` — `implements FlexLexer, LuaTokenTypes`; body uses e.g. `return NUMBER;` (`_LuaLexer.java:750`), `return IDENTIFIER;` (`:780`), `return WHILE;` (`:1046`) — bare constant names.
- `src/main/gen/net/internetisalie/lunar/luacats/lang/lexer/_LuaCatsLexer.java` — `implements FlexLexer, LuaCatsTokenTypes` (declared in `luacats.flex:24`).
- `.flex` sources: `src/main/kotlin/net/internetisalie/lunar/lang/lexer/lua.flex:18` (`%implements FlexLexer, LuaTokenTypes`) and `src/main/kotlin/net/internetisalie/lunar/luacats/lang/lexer/luacats.flex:24` (`%implements FlexLexer, LuaCatsTokenTypes`).

### Scoping decision (grounded, binding)

The roadmap title says "platform.syntax Migration". A grounding pass against the reference repo
`~/Documents/src/lua/intellij-community` establishes that a **full** migration to
`com.intellij.platform.syntax` (lexers emitting `%type SyntaxElementType`, parsers built on
`SyntaxGeneratedParserRuntime` / `SyntaxTreeBuilder`, and a `LanguageSyntaxDefinition` extension)
is **infeasible with Lunar's current build tooling** and is therefore **explicitly out of scope**
for this feature. Evidence:

- The `platform.syntax` API surface exists (281 files; e.g.
  `intellij-community/platform/syntax/syntax-api/src/com/intellij/platform/syntax/SyntaxElementType.kt`,
  `.../lexer/Lexer.kt`, `.../parser/SyntaxTreeBuilder`, `.../psi/LanguageSyntaxDefinition.kt`).
- **Only JetBrains' own core languages have adopted it** — JSON
  (`intellij-community/json/syntax/src/com/intellij/json/syntax/JsonLanguageDefinition.kt`), Java
  (`java/java-syntax/...`), XML (`xml/xml-syntax/...`). No third-party-style plugin has.
- JSON's migrated lexer uses `%type SyntaxElementType` and its parser is `JsonSyntaxParser` built on
  `SyntaxGeneratedParserRuntime` (`_JsonLexer.flex:%type SyntaxElementType`;
  `JsonLanguageDefinition.kt` overriding `parse(elementType, runtime: SyntaxGeneratedParserRuntime)`).
  Producing that output requires a **JetBrains-internal JFlex skeleton + Grammar-Kit generator** that
  emit `SyntaxElementType`/`SyntaxGeneratedParserRuntime` code.
- Lunar uses **classic Grammar-Kit** (`org.intellij.grammar.Main`, options
  `elementTypeHolderClass=".../LuaElementTypes"` / `psiPackage=".../lang.psi"` at `lua.bnf:80,85`) and
  **classic JFlex** (`%type IElementType`, `lua.flex`). CLAUDE.md records the grammar-kit Gradle plugin
  is **not** wired into the build (Kotlin circular-dep issues); regeneration is a manual IDE step
  (`.claude/skills/generate-parser/SKILL.md` documents the classic CLI path only). No syntax-emitting
  generator is available.

Therefore MAINT-19 delivers the **achievable Kotlin-native goal**: make the two token holders
Kotlin-native (`.kt`) while preserving the exact `IElementType` constants and their bare consumption
from the generated lexers, by editing the two `.flex` files to import a Kotlin holder statically and
regenerating the lexers via the documented manual handoff. The full `platform.syntax` port is
recorded as **future work** in `risks-and-gaps.md` (DR task `MAINT-19-00-1`), not attempted here.

## Scope

### In Scope
- Convert `LuaTokenTypes.java` → `LuaTokenTypes.kt` (Kotlin, same package, same constant names/values).
- Convert `LuaCatsTokenTypes.java` → `LuaCatsTokenTypes.kt` (same).
- Edit `lua.flex` and `luacats.flex` so the generated lexers reference the Kotlin holder's constants
  bare (Java static import of the Kotlin holder's members).
- Regenerate `_LuaLexer.java` and `_LuaCatsLexer.java` via the manual human-in-the-loop IDE step and
  commit the regenerated `src/main/gen/` output.
- Delete the two `.java` interface files after conversion.
- Verify byte-for-byte token behavior is unchanged (existing lexer tests stay green).

### Out of Scope
- **Full `com.intellij.platform.syntax` migration** — no `%type SyntaxElementType`, no
  `SyntaxGeneratedParserRuntime` parser, no `LanguageSyntaxDefinition` extension. Deferred to future
  work (`risks-and-gaps.md` DR `MAINT-19-00-1`) because the required generator tooling is unavailable.
- Any change to `lua.bnf` / `luacats.bnf` or the Grammar-Kit parser output.
- Any change to `LuaParserDefinition.kt`, PSI classes, or `plugin.xml`.
- Any token-set / grammar behavior change (this is a pure representation change).

## Requirements

| ID | Requirement | Priority | Status | Description |
|----|-------------|----------|--------|-------------|
| MAINT-19-01 | `LuaTokenTypes` is a Kotlin file | Must | Not Implemented | `src/main/java/.../lang/lexer/LuaTokenTypes.java` replaced by `src/main/kotlin/.../lang/lexer/LuaTokenTypes.kt`; same package, all constants preserved with identical names, `IElementType` values, and debug names. Old `.java` deleted. |
| MAINT-19-02 | `LuaCatsTokenTypes` is a Kotlin file | Must | Not Implemented | `src/main/java/.../luacats/lang/lexer/LuaCatsTokenTypes.java` replaced by `src/main/kotlin/.../luacats/lang/lexer/LuaCatsTokenTypes.kt`; same package, all constants + the `LUACATS_TOKENS` `TokenSet` preserved. Old `.java` deleted. |
| MAINT-19-03 | `.flex` sources import the Kotlin holder | Must | Not Implemented | `lua.flex` and `luacats.flex` no longer `%implements` the token interface; instead they `import static <holder>.*` (or fully-qualify) so generated lexers reference constants. Bare-name usage in the generated body is preserved. |
| MAINT-19-04 | Generated lexers regenerated & committed | Must | Not Implemented | `_LuaLexer.java` / `_LuaCatsLexer.java` regenerated from the edited `.flex` via the manual IDE JFlex step; committed under `src/main/gen/`. They compile against the Kotlin holders. |
| MAINT-19-05 | Token behavior is unchanged | Must | Not Implemented | Lexing any Lua/LuaCATS input produces the identical `IElementType` sequence as before (same constant instances). Existing lexer tests pass unmodified. |
| MAINT-19-06 | Kotlin call sites unchanged | Must | Not Implemented | All Kotlin consumers that reference `LuaTokenTypes.X` / `LuaCatsTokenTypes.X` compile with **zero source edits** (the Kotlin `@JvmField object` holder preserves qualified `LuaTokenTypes.X` member access). The complete consumer set is: `LuaRequireReferenceContributor.kt`, `LuaCompletionContributor.kt`, `LuaParserDefinition.kt`, `LuaSyntaxHighlighter.kt`, `LuaSyntax.kt`, `LuaCodeContextPredicate.kt`, `LuaDocGenerator.kt`, `LuaLexer.kt` (all reference `LuaTokenTypes`), plus `LuaCatsAnnotator.kt`, `LuaCatsLexer.kt`, and again `LuaSyntax.kt` (reference `LuaCatsTokenTypes`). |
| MAINT-19-07 | Full platform.syntax port assessed & deferred | Should | Not Implemented | `risks-and-gaps.md` records the feasibility finding and the DR task to revisit when a syntax-emitting generator becomes available in Lunar's build. |

## Test Cases

All lexer TCs run against the existing exhaustive suites
`src/test/kotlin/net/internetisalie/lunar/lang/lexer/TestLuaLexerExhaustive.kt`,
`.../TestLuaLexer.kt`, and `.../luacats/lang/lexer/TestLuaCatsLexer.kt`, which must pass **unmodified**.

| TC | Requirement | Input | Action | Expected Output |
|----|-------------|-------|--------|-----------------|
| TC-01 | MAINT-19-01 | `find src/main/java -name LuaTokenTypes.java` | after impl | no results; `src/main/kotlin/.../lang/lexer/LuaTokenTypes.kt` exists |
| TC-02 | MAINT-19-02 | `find src/main/java -name LuaCatsTokenTypes.java` | after impl | no results; `src/main/kotlin/.../luacats/lang/lexer/LuaCatsTokenTypes.kt` exists |
| TC-03 | MAINT-19-03 | `grep '%implements' lua.flex luacats.flex` | after impl | neither line names `LuaTokenTypes`/`LuaCatsTokenTypes` (only `FlexLexer`) |
| TC-04 | MAINT-19-04 | edited `lua.flex` / `luacats.flex` | run JFlex generator in IDE | `_LuaLexer.java` / `_LuaCatsLexer.java` regenerated, compile, no `LuaTokenTypes` Java interface referenced |
| TC-05 | MAINT-19-05 | `"local x = 1 + 2 while true do end"` | lex via `LuaLexer` | token sequence identical to pre-change baseline (assert in `TestLuaLexerExhaustive`) |
| TC-06 | MAINT-19-05 | `[[ multi\nline\nstring ]]` and `--[[ long comment ]]` | lex via `LuaLexer` | `STRING` / `LONGCOMMENT` merged tokens identical to baseline |
| TC-07 | MAINT-19-05 | `---@class Foo` and `---@field x number` | lex via `LuaCatsLexer` | `LCATS_DASHES`/`LCATS_TAG`/`LCATS_NAME`/... sequence identical to baseline |
| TC-08 | MAINT-19-06 | full module compile | `gce-builder run build` | compiles; `git diff --name-only` shows **none** of the 10 consumers modified: `LuaRequireReferenceContributor.kt`, `LuaCompletionContributor.kt`, `LuaParserDefinition.kt`, `LuaSyntaxHighlighter.kt`, `LuaSyntax.kt`, `LuaCodeContextPredicate.kt`, `LuaDocGenerator.kt`, `LuaLexer.kt`, `LuaCatsAnnotator.kt`, `LuaCatsLexer.kt` |
| TC-09 | MAINT-19-05 | full unit suite | `gce-builder run test` | 0 new failures vs. baseline; lexer test classes unchanged |
