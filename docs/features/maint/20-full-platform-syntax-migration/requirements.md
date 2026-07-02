---
id: "MAINT-20"
title: "MAINT-20: Full platform.syntax Migration (SyntaxElementType lexer/parser)"
type: "feature"
status: "planned"
priority: "low"
parent_id: "MAINT"
folders:
  - "[[features/maint/requirements|requirements]]"
---

# MAINT-20: Full platform.syntax Migration (SyntaxElementType lexer/parser)

## Overview

This feature is the **deferred follow-up to MAINT-19** (done). MAINT-19 made the two token
holders Kotlin-native while keeping the *classic* `IElementType` representation, and explicitly
scoped out the full port to `com.intellij.platform.syntax` because the generator tooling could
not be shown to work in Lunar's build. MAINT-20's job is to **close that tooling gap and then
execute the migration**: emit `SyntaxElementType`-based lexers (`%type SyntaxElementType`),
generate a `SyntaxGeneratedParserRuntime`-based parser, introduce a `LanguageSyntaxDefinition`
plus an `ElementTypeConverter`, and re-wire `LuaParserDefinition` to the platform.syntax parse
path — mirroring JetBrains' own JSON adopter (`intellij-community/json/syntax/`), verified
against the reference repo throughout.

Parent epic: [[features/maint/requirements|MAINT]]. Predecessor:
[[features/maint/19-platform-syntax-migration/requirements|MAINT-19]].

## Scope

### In Scope
- Introduce a **syntax-emitting generation toolchain** into Lunar: a JFlex run using the
  Kotlin skeleton (`tools/lexer/idea-flex-kotlin.skeleton`, sourced from
  `~/Documents/src/lua/intellij-community`) and a Grammar-Kit run with `generate=[parser-api="syntax"]`.
- Migrate `lua.flex` and `luacats.flex` to `%type SyntaxElementType` Kotlin flex files that
  emit `_LuaLexer.kt` / `_LuaCatsLexer.kt` (Kotlin, not Java) referencing a new
  `LuaSyntaxElementTypes` / `LuaCatsSyntaxElementTypes` holder.
- Add `generate=[parser-api="syntax"]` and `syntaxElementTypeHolderClass` /
  `elementTypeConverterFactoryClass` options to `lua.bnf` (and `luacats.bnf`) and generate a
  `LuaSyntaxParser` built on `SyntaxGeneratedParserRuntime`.
- Create `LuaLanguageDefinition : LanguageSyntaxDefinition, GrammarKitLanguageDefinition`
  (mirroring `JsonLanguageDefinition`), an `ElementTypeConverterFactory`
  (`SyntaxElementType`↔`IElementType`), and register both in `plugin.xml` under the
  `com.intellij.syntax.*` extension points.
- Re-wire `LuaFileElementType` to drive the platform.syntax parse path and `LuaParserDefinition`
  to supply the syntax lexer, while preserving Lunar's **stub-indexing** subsystem
  (`IStubFileElementType`).
- Prove byte-for-byte behavioral equivalence: identical PSI tree, identical token stream (via
  the converter), all existing lexer/parser/PSI/stub/index tests green **unmodified**.

### Out of Scope
- Any change to Lua grammar rules, token sets, or the shape of the produced PSI tree (this is a
  pure representation/plumbing change; behavior must be identical).
- Changing the merging-lexer chain semantics (`LuaLexer`'s `MergingLexerAdapter` stack) — it is
  ported, not redesigned.
- Migrating any *other* IntelliJ subsystem (highlighting lexer factory, brace matcher, etc.)
  beyond what the parse path strictly requires.
- Vendoring the toolchain permanently into the Gradle build (the grammar-kit Gradle plugin stays
  unwired per CLAUDE.md); generation remains the manual human-in-the-loop IDE/CLI step. Wiring
  the Gradle plugin is future work (see risks-and-gaps.md).

## Functional Requirements

| ID | Requirement | Priority | Description |
|----|-------------|----------|-------------|
| MAINT-20-01 | **Syntax lexer for Lua** | M | `lua.flex` uses `%type SyntaxElementType`; a Kotlin `_LuaLexer.kt` is generated (via the Kotlin JFlex skeleton) returning `LuaSyntaxElementTypes` members. Old Java `_LuaLexer.java` removed. |
| MAINT-20-02 | **Syntax lexer for LuaCATS** | M | `luacats.flex` uses `%type SyntaxElementType`; Kotlin `_LuaCatsLexer.kt` generated returning `LuaCatsSyntaxElementTypes` members. Old Java `_LuaCatsLexer.java` removed. |
| MAINT-20-03 | **Syntax element-type holders** | M | New generated `LuaSyntaxElementTypes` / `LuaCatsSyntaxElementTypes` Kotlin objects hold one `SyntaxElementType(name)` per token and node, names identical to the classic `LuaTokenTypes` / `LuaElementTypes` debug names. |
| MAINT-20-04 | **Syntax parser** | M | `lua.bnf` carries `generate=[parser-api="syntax"]`; a generated `LuaSyntaxParser` object with `fun parse(t: SyntaxElementType, s: SyntaxGeneratedParserRuntime)` is produced, replacing the classic `LuaParser`. |
| MAINT-20-05 | **LanguageSyntaxDefinition** | M | New `LuaLanguageDefinition : LanguageSyntaxDefinition, GrammarKitLanguageDefinition` overrides `createLexer()`, `parse(elementType, runtime)`, `getPairedBraces()`, `comments`; registered via `<syntax.syntaxDefinition language="Lua" …>`. |
| MAINT-20-06 | **ElementTypeConverter** | M | New generated `LuaElementTypeConverterFactory` maps every `SyntaxElementType`→`IElementType` (token + node) so the classic PSI tree is built unchanged; registered via `<syntax.elementTypeConverter language="Lua" …>`. |
| MAINT-20-07 | **File-element-type re-wire preserves stubs** | M | `LuaFileElementType` continues to extend `IStubFileElementType<LuaFileStub>` (stub indexing intact) while its `doParseContents` drives the platform.syntax parse (lexer→`PsiSyntaxBuilder`→converter→`LuaSyntaxParser`). `getStubVersion` bumped. |
| MAINT-20-08 | **ParserDefinition re-wire** | M | `LuaParserDefinition.createLexer` returns a syntax-backed lexer; `createParser` throws the "should not be called directly" `UnsupportedOperationException` (per JSON), since parsing is driven by the file element type. `createElement` unchanged. |
| MAINT-20-09 | **Behavioral equivalence** | M | For every input, the resulting classic PSI tree and `IElementType` token stream are byte-for-byte identical to pre-migration. All existing lexer, parser, PSI, stub, and index test suites pass **unmodified**. |
| MAINT-20-10 | **Merging-lexer chain preserved** | M | The `MergingLexerAdapter`/`LongStringMergingLexerAdapter`/… stack in `LuaLexer` is preserved (adapted to wrap the new syntax `FlexAdapter`), so `STRING`/`LONGSTRING`/`LONGCOMMENT`/`SHORTCOMMENT` merged tokens are identical to baseline. |
| MAINT-20-11 | **Consumers of classic holders unbroken** | S | The 10 Kotlin consumers of `LuaTokenTypes`/`LuaCatsTokenTypes` (enumerated in MAINT-19-06) still compile: either the classic `IElementType` holders are retained as the converter target, or each consumer is migrated with the converter. No behavior change. |
| MAINT-20-12 | **Toolchain documented & reproducible** | S | The generate-parser skill (`.claude/skills/generate-parser/`) documents the syntax-emitting path (Kotlin skeleton flag + `parser-api="syntax"` option), enabling reproducible regeneration. |

## Detailed Specifications

### MAINT-20-03 / MAINT-20-06: Holder + converter naming parity

The classic representation must survive as the converter's target so the PSI tree, stub keys,
and all downstream `IElementType`-keyed logic are unchanged. Concretely:

- Classic holders **retained**: `net.internetisalie.lunar.lang.psi.LuaElementTypes` (node types),
  `net.internetisalie.lunar.lang.lexer.LuaTokenTypes` (token types), and the LuaCATS equivalents.
- New syntax holders **added**: `LuaSyntaxElementTypes` / `LuaCatsSyntaxElementTypes` under the
  syntax package (mirroring `com.intellij.json.syntax.JsonSyntaxElementTypes`), one
  `SyntaxElementType("NAME")` per classic constant, **`"NAME"` equal to the classic debug name**
  (so `IElementType.toString()` on the converted type is byte-identical — see MAINT-19 R-3).
- The generated `LuaElementTypeConverterFactory` produces an `ElementTypeConverter` via
  `ElementTypeConverterKt.elementTypeConverterOf(Pair(syntax, classic), …)` — one pair per
  constant (token **and** node), exactly as `JsonElementTypeConverterFactory` does.

### MAINT-20-07: File-element-type parse bridge (stub-preserving)

JSON uses `SyntaxGrammarKitFileElementType` (a plain `IFileElementType`). Lunar cannot: its
`LuaFileElementType` is an `IStubFileElementType<LuaFileStub>` and the whole stub-index
subsystem depends on it. Therefore Lunar keeps its own `IStubFileElementType` subclass and
**inlines the JSON bridge's `doParseContents` body** (verified in
`platform/syntax/syntax-psi/src/com/intellij/platform/syntax/psi/SyntaxGrammarKitFileElementType.kt`):
obtain `PsiSyntaxBuilderFactory.getInstance()`, `createBuilder(chameleon, lexer, lang, text)`,
`ElementTypeConverters`-convert the chameleon's element type, `createSyntaxGeneratedParserRuntime`,
`registerParse { LuaLanguageDefinition.parse(convertedElement, runtime); builder.getTreeBuilt() }`,
return `root.firstChildNode`. Stub building (`getBuilder`/`serialize`/`deserialize`) is unchanged.

## Behavior Rules

- **No grammar/token-set change.** Any diff to the produced PSI tree, token stream, or stub keys
  is a defect, not an accepted outcome.
- **Debug-name strings are frozen.** Every `SyntaxElementType` name equals the classic constant's
  debug name; the converter maps back to the *same* classic `IElementType` singleton instance.
- **Static singletons only.** `SyntaxElementType`/`IElementType` constants live in generated
  `object` holders (never per-parse instances) — the platform registry has a hard size limit
  (CLAUDE.md, Lessons Learned).
- **Threading.** Parsing runs where the platform invokes `doParseContents` (parser thread / under
  the platform's parse machinery); no EDT work is added. Stub building is unchanged.

## Test Cases

All existing suites must pass **unmodified**. Named suites verified present:
`src/test/kotlin/net/internetisalie/lunar/lang/lexer/TestLuaLexerExhaustive.kt`,
`.../lang/lexer/TestLuaLexer.kt`,
`.../luacats/lang/lexer/TestLuaCatsLexer.kt` (grep-confirmed on disk).

| # | Requirement | Given (input) | When (action) | Then (expected) |
|---|-------------|---------------|---------------|-----------------|
| 1 | MAINT-20-01 | edited `lua.flex` (`%type SyntaxElementType`) | run JFlex with `--skel idea-flex-kotlin.skeleton` | `_LuaLexer.kt` generated (Kotlin), no `_LuaLexer.java`; `grep '%type' lua.flex` → `SyntaxElementType` |
| 2 | MAINT-20-03 | generated holders | `grep 'SyntaxElementType("WHILE")' LuaSyntaxElementTypes.kt` | present; name string equals classic `LuaTokenTypes.WHILE.toString()` (`"while"` per current holder) |
| 3 | MAINT-20-04 | `lua.bnf` with `generate=[parser-api="syntax"]` | run Grammar-Kit `org.intellij.grammar.Main` | `LuaSyntaxParser` object generated with `fun parse(t: SyntaxElementType, s: SyntaxGeneratedParserRuntime)` |
| 4 | MAINT-20-05 | plugin.xml | plugin loads in sandbox IDE (`runIde`) | `LanguageSyntaxDefinitions.INSTANCE.forLanguage(LuaLanguage)` resolves `LuaLanguageDefinition`; no load error |
| 5 | MAINT-20-06 | `LuaElementTypeConverterFactory` | convert `LuaSyntaxElementTypes.WHILE` | returns the *same instance* as classic `LuaTokenTypes.WHILE` |
| 6 | MAINT-20-09 | `"local x = 1 + 2 while true do end"` | parse via `LuaParserDefinition` | PSI tree structure + node/token `IElementType`s identical to pre-migration baseline (`TestLuaLexerExhaustive` + a PSI-tree snapshot test) |
| 7 | MAINT-20-09 | `[[ multi\nline ]]`, `--[[ long ]]`, `-- short` | lex via `LuaLexer` | merged `STRING`/`LONGSTRING`/`LONGCOMMENT`/`SHORTCOMMENT` tokens identical to baseline (MAINT-20-10) |
| 8 | MAINT-20-02 | `---@class Foo` / `---@field x number` | lex via `LuaCatsLexer` | `LCATS_*` token sequence identical to baseline |
| 9 | MAINT-20-07 | a Lua file declaring a root `return M` with `---@class M` | build stubs / query `LuaClassNameIndex` | stub `exportedTypeString` and class-name index hit identical to baseline; `getExternalId` still `"lunar.file"` |
| 10 | MAINT-20-09 | full unit suite | `gce-builder run test` | 0 new failures vs. baseline; no test source edited |
| 11 | MAINT-20-08 | `LuaParserDefinition.createParser` | call it | throws `UnsupportedOperationException` (JSON contract); parsing still works end-to-end via the file element type |

## Acceptance Criteria
- [ ] MAINT-20-01…06: syntax lexers, holders, parser, definition, converter all generated and present; Java `_Lua*Lexer.java` / classic `LuaParser` removed.
- [ ] MAINT-20-07/08: file element type + parser definition re-wired; stubs and indexes intact.
- [ ] MAINT-20-09/10: full existing suite green **unmodified**; PSI tree + token stream byte-identical.
- [ ] All four pre-implementation de-risking spikes (risks-and-gaps.md DR-01…DR-04) resolved with their success criteria met **before** this feature moves to `in_progress`.

## Non-Functional Requirements
- **Performance**: parse/lex throughput within noise of baseline (platform.syntax is JetBrains' own hot path). No new EDT work.
- **Memory**: no new hard refs to `Project`/`Editor`/`PsiFile`; holders are static singletons.
- **Contract**: honors `docs/engineering-contract.md` (≤30-line methods, no `!!`, no wildcard imports in hand-written code; generated files are exempt from style but must compile).

## Dependencies
- **MAINT-19** (done) — Kotlin token holders and the `.flex` `import static` rewiring this builds on.
- Platform runtime `com.intellij.platform.syntax` — present in Lunar's 2026.1 (261) dependency set (`syntax-psi-261`, `syntax-util-261` in the Gradle cache; verified).
- Reference toolchain in `~/Documents/src/lua/intellij-community` (`tools/lexer/idea-flex-kotlin.skeleton`; grammar-kit `parser-api="syntax"`).

## See Also
- Design: [design.md](design.md)
- Plan: [implementation-plan.md](implementation-plan.md)
- Risks: [risks-and-gaps.md](risks-and-gaps.md)
- Predecessor: [[features/maint/19-platform-syntax-migration/requirements|MAINT-19]]
