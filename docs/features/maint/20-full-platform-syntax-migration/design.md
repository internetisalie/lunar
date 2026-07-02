---
id: "MAINT-20-DESIGN"
title: "Technical Design"
type: "design"
priority: "low"
parent_id: "MAINT-20"
folders:
  - "[[features/maint/20-full-platform-syntax-migration/requirements|requirements]]"
---

# Technical Design: MAINT-20 — Full platform.syntax Migration

> **Gating note.** This design is *conditionally* complete: its concrete shape is
> pinned by the JSON reference adopter (grep-verified below), but two facts about
> **Lunar's own build** — that JFlex can emit Kotlin from `lua.flex`, and that a
> grammar-kit build supporting `parser-api="syntax"` is reachable in this checkout —
> are only reproducible via the DR spikes in `risks-and-gaps.md`. Those spikes
> (DR-01, DR-02) MUST pass before this feature leaves `todo`. The design describes the
> end state the spikes validate; it does not assume tooling not yet demonstrated here.

## 1. Architecture Overview

### Current State
Lunar uses the **classic** parse stack:
- `LuaLexer` (`src/main/kotlin/net/internetisalie/lunar/lang/lexer/LuaLexer.kt`) wraps a
  Java `_LuaLexer` (JFlex `%type IElementType`) in a `MergingLexerAdapter` chain.
- `LuaParser` (Grammar-Kit classic, `org.intellij.grammar.Main`, options at
  `src/main/kotlin/net/internetisalie/lunar/lang/psi/lua.bnf:80,85`).
- `LuaParserDefinition` (`.../lang/LuaParserDefinition.kt`) returns `LuaLexer()` /
  `LuaParser()` and `FILE = LuaFileElementType()`.
- `LuaFileElementType : IStubFileElementType<LuaFileStub>`
  (`.../lang/psi/LuaFileElementType.kt`) — **stub-based**.
- Token/node holders: `LuaTokenTypes` (Kotlin `@JvmField object`, MAINT-19),
  `LuaElementTypes` (Grammar-Kit generated). LuaCATS analogues in the `luacats` package.

It is insufficient only in that it does not use `com.intellij.platform.syntax` — the roadmap's
stated target and the JetBrains direction of travel for core languages.

### Prior Art in This Repo
Grounding searches performed:
- **MAINT-19** (`docs/features/maint/19-platform-syntax-migration/`) — the predecessor. It made
  the token holders Kotlin (`LuaTokenTypes.kt`) and rewired `.flex` to `import static`, and
  documented (with evidence) why the *full* syntax port was out of its scope. This feature
  **extends** that work; it does not redo it.
- **No existing platform.syntax adopter in Lunar** — `grep -rn 'SyntaxElementType\|LanguageSyntaxDefinition\|SyntaxGeneratedParserRuntime' src/main` returns nothing. This is greenfield within Lunar.
- **Classic parse components to be replaced/re-wired** (all grep-confirmed): `LuaParserDefinition.kt`,
  `LuaFileElementType.kt`, `lua.bnf`, `luacats.bnf`, `lua.flex`, `luacats.flex`, `_LuaLexer.java`,
  `_LuaCatsLexer.java`, `LuaParser` (generated).

### Target State
Mirror `intellij-community/json/syntax`:
```
lua.flex (%type SyntaxElementType) ──JFlex(kotlin skel)──▶ _LuaLexer.kt ──▶ FlexAdapter ──▶ LuaLexer (merging chain)
lua.bnf  (parser-api="syntax")     ──Grammar-Kit────────▶ LuaSyntaxParser (SyntaxGeneratedParserRuntime)
                                     └────────────────────▶ LuaSyntaxElementTypes (object of SyntaxElementType)
                                     └────────────────────▶ LuaElementTypeConverterFactory (SyntaxElementType↔IElementType)
LuaLanguageDefinition : LanguageSyntaxDefinition, GrammarKitLanguageDefinition
LuaFileElementType : IStubFileElementType  (doParseContents drives the syntax parse; stubs preserved)
LuaParserDefinition  (createLexer → syntax lexer; createParser → throws; FILE unchanged shape)
```
Registered in `plugin.xml` under `com.intellij.syntax.syntaxDefinition` and
`com.intellij.syntax.elementTypeConverter`.

## 2. Core Components

All existing platform types below are verified in the reference repo
`~/Documents/src/lua/intellij-community` (packages under `com.intellij.platform.syntax*`) and are
present at runtime in Lunar's 261 dependencies (`syntax-psi-261`, `syntax-util-261` jars in the
Gradle cache).

### 2.1 `net.internetisalie.lunar.lang.syntax.LuaSyntaxElementTypes` (NEW, generated)
- **Responsibility**: hold one `SyntaxElementType(name)` per Lua node type (Grammar-Kit
  `syntaxElementTypeHolderClass`).
- **Threading**: static init.
- **Collaborators**: `com.intellij.platform.syntax.SyntaxElementType` (verified:
  `platform/syntax/syntax-api/src/com/intellij/platform/syntax/SyntaxElementType.kt`).
- **Key API** (mirrors `JsonSyntaxElementTypes.kt`, generated):
  ```kotlin
  object LuaSyntaxElementTypes {
    val WHILE = SyntaxElementType("while")   // name == classic LuaTokenTypes.WHILE debug name
    // …one per token AND node…
  }
  ```

### 2.2 `net.internetisalie.lunar.luacats.lang.syntax.LuaCatsSyntaxElementTypes` (NEW, generated)
- As 2.1 for the LuaCATS token/node set (`luacats.bnf`).

### 2.3 `net.internetisalie.lunar.lang.syntax._LuaLexer` (NEW, generated Kotlin)
- **Responsibility**: JFlex-generated Kotlin lexer, `%type SyntaxElementType`, `implements FlexLexer`
  (the *syntax* `FlexLexer`: `platform/syntax/syntax-util/src/com/intellij/platform/syntax/util/lexer/FlexLexer.kt`).
- **Generation**: JFlex with `--skel <path>/tools/lexer/idea-flex-kotlin.skeleton` (verified present).
- Replaces `src/main/gen/.../lang/lexer/_LuaLexer.java`.

### 2.4 `net.internetisalie.lunar.lang.lexer.LuaLexer` (EDIT)
- **Responsibility**: unchanged public role — the merging lexer used by `LuaParserDefinition` and
  highlighting. Its innermost `FlexAdapter(_LuaLexer(null))` becomes the **syntax** `FlexAdapter`
  (`com.intellij.platform.syntax.util.lexer.FlexAdapter`, verified) wrapping the Kotlin `_LuaLexer`.
- **Collaborators**: `MergingLexerAdapter`, `LongStringMergingLexerAdapter`,
  `LongCommentMergingLexerAdapter`, `MultiLineMergingLexerAdapter` (all Lunar classes, grep-confirmed
  in `LuaLexer.kt`). These operate on classic `IElementType`; see §3.2 for how they bridge.

### 2.5 `net.internetisalie.lunar.lang.parser.LuaSyntaxParser` (NEW, generated)
- **Responsibility**: Grammar-Kit `parser-api="syntax"` parser.
- **Key API** (mirrors `json/syntax/gen/.../JsonSyntaxParser.kt`, verified):
  ```kotlin
  object LuaSyntaxParser {
    fun parse(t: SyntaxElementType, s: SyntaxGeneratedParserRuntime)
  }
  ```
- Replaces classic `LuaParser`. `SyntaxGeneratedParserRuntime` verified:
  `platform/syntax/syntax-util/src/com/intellij/platform/syntax/util/runtime/SyntaxGeneratedParserRuntime.kt`.

### 2.6 `net.internetisalie.lunar.lang.syntax.LuaLanguageDefinition` (NEW, hand-written)
- **Responsibility**: bind lexer + parser + brace pairs + comments for the platform.syntax machinery.
- **Threading**: methods invoked by platform parse machinery (parser thread).
- **Collaborators**: `LuaSyntaxParser`, `LuaLexer`/syntax `FlexAdapter`, `LuaSyntaxElementTypes`.
- **Key API** (mirrors `JsonLanguageDefinition.kt`, verified):
  ```kotlin
  @ApiStatus.NonExtendable
  open class LuaLanguageDefinition : LanguageSyntaxDefinition, GrammarKitLanguageDefinition {
    override fun createLexer(): Lexer = LuaLexer()               // com.intellij.platform.syntax.lexer.Lexer
    override fun parse(builder: SyntaxTreeBuilder) = throw UnsupportedOperationException("generated Parser")
    override fun parse(elementType: SyntaxElementType, runtime: SyntaxGeneratedParserRuntime) =
        LuaSyntaxParser.parse(elementType, runtime)
    override fun getPairedBraces(): Collection<BracePair> = luaPairedBraces
    override val comments: SyntaxElementTypeSet get() = luaComments
  }
  ```
  Verified base types: `LanguageSyntaxDefinition`
  (`platform/syntax/syntax-api/src/com/intellij/platform/syntax/LanguageSyntaxDefinition.kt`),
  `GrammarKitLanguageDefinition` / `BracePair` / `SyntaxGeneratedParserRuntime`
  (`platform/syntax/syntax-util/src/com/intellij/platform/syntax/util/runtime/`),
  `SyntaxElementTypeSet` / `syntaxElementTypeSetOf` (`syntax-api`).

### 2.7 `net.internetisalie.lunar.lang.psi.LuaElementTypeConverterFactory` (NEW, generated)
- **Responsibility**: provide the `SyntaxElementType`→`IElementType` converter so the classic PSI
  tree is built with the *same* `LuaElementTypes`/`LuaTokenTypes` singletons.
- **Key API** (mirrors `json/gen/.../JsonElementTypeConverterFactory.java`, verified):
  ```java
  public class LuaElementTypeConverterFactory implements ElementTypeConverterFactory {
    public ElementTypeConverter getElementTypeConverter() {
      return ElementTypeConverterKt.elementTypeConverterOf(
        new Pair<>(LuaSyntaxElementTypes.INSTANCE.getWHILE(), LuaTokenTypes.WHILE), …);
    }
  }
  ```
  Verified: `ElementTypeConverterFactory` / `ElementTypeConverter` / `ElementTypeConverterKt`
  (`platform/syntax/syntax-psi/src/com/intellij/platform/syntax/psi/ElementTypeConverter*.kt`).

### 2.8 `net.internetisalie.lunar.lang.psi.LuaFileElementType` (EDIT)
- **Responsibility**: stays `IStubFileElementType<LuaFileStub>` (stubs preserved); overrides
  `doParseContents` to drive the syntax parse (see §3.1). `getBuilder`/`serialize`/`deserialize`
  unchanged; `getStubVersion` bumped 2→3.
- **Collaborators**: `PsiSyntaxBuilderFactory`, `ElementTypeConverters`, `LanguageSyntaxDefinitions`,
  `createSyntaxGeneratedParserRuntime`, `registerParse` — all verified in
  `platform/syntax/syntax-psi/src/com/intellij/platform/syntax/psi/` and used by
  `SyntaxGrammarKitFileElementType.kt`.

### 2.9 `net.internetisalie.lunar.lang.LuaParserDefinition` (EDIT)
- `createLexer(project)` → `LuaLexer()` (now syntax-backed).
- `createParser(project)` → `throw UnsupportedOperationException(...)` (JSON contract — parsing is
  driven by `LuaFileElementType.doParseContents`).
- `getFileNodeType()` → `FILE` (unchanged). `createElement` unchanged.

## 3. Algorithms

### 3.1 File parse bridge (`LuaFileElementType.doParseContents`)
Ported verbatim from `SyntaxGrammarKitFileElementType.doParseContents` (verified source), adapted
to Lunar's names:
- **Input → Output**: `(chameleon: ASTNode, psi: PsiElement)` → `ASTNode?` (root's first child).
- **Steps**:
  1. `val factory = PsiSyntaxBuilderFactory.getInstance()`.
  2. `val elementType = chameleon.elementType`.
  3. `val def = LanguageSyntaxDefinitions.INSTANCE.forLanguage(LuaLanguage) as? GrammarKitLanguageDefinition ?: error(...)`.
  4. `val lexer = def.createLexer()`.
  5. `val builder = factory.createBuilder(chameleon, lexer, LuaLanguage, chameleon.chars)`.
  6. `val converter = ElementTypeConverters.getConverter(LuaLanguage)`.
  7. `val converted = converter.convert(elementType) ?: error(...)`.
  8. `val runtime = createSyntaxGeneratedParserRuntime(LuaLanguage, builder.syntaxTreeBuilder, state = null)`.
  9. `val root = registerParse(builder, LuaLanguage) { def.parse(converted, runtime); builder.treeBuilt }`.
  10. `return root.firstChildNode`.
- **Edge handling**: null converter/converted → `IllegalStateException` with the offending type
  (as JSON). Empty file → platform builder yields an empty file node (same as classic path).

### 3.2 Merging-lexer bridge (preserve `LuaLexer` chain)
The `MergingLexerAdapter` stack operates on classic `IElementType`. Two options; **chosen: A**.
- **A (converter-in-adapter, chosen)**: keep the syntax `FlexAdapter(_LuaLexer)` innermost; wrap it
  so the tokens it exposes to the merging adapters are the classic `IElementType`s via the
  `ElementTypeConverter`. Rationale: the merging adapters' `TokenSet.create(LuaTokenTypes.STRING, …)`
  arguments (grep-confirmed in `LuaLexer.kt`) are classic token sets; converting at the flex-adapter
  boundary keeps that code untouched. The syntax `FlexAdapter` already returns tokens compatible with
  the platform lexer contract; the conversion point is where `LuaLexer` currently constructs
  `FlexAdapter(_LuaLexer(null))`.
- **Rule**: exactly one conversion, at the innermost boundary; the merging adapters and their
  `TokenSet`s are unchanged, preserving MAINT-20-10 equivalence.
- **DR-03 validates** that the platform `FlexAdapter` + converter yields the identical merged token
  stream as the current Java `FlexAdapter`.

### 3.3 Debug-name parity (converter correctness)
- **Input → Output**: classic constant → `SyntaxElementType` name string.
- **Rule**: for every generated `SyntaxElementType("X")`, `"X"` MUST equal the classic constant's
  `toString()` (e.g. `LuaTokenTypes.WHILE.toString()` is `"while"` per the current Kotlin holder;
  `LuaElementType`/`LuaCatsElementType.toString()` return their `name`/`debugName`). The generator
  derives names from the same `.bnf`/`.flex` token names, so parity is structural; DR-02's diff
  gate enforces it.

## 4. External Data & Parsing
This feature consumes **no external/CLI/network input**. Its only "external" inputs are the
generator outputs (`.kt`/`.java` from JFlex/Grammar-Kit), which are compiled, not parsed at runtime.
The generation commands and their exact flags are specified in the implementation plan (§ toolchain)
and validated by DR-01/DR-02.

## 5. Data Flow

### Example 1: Parsing `local x = 1`
1. Platform requests parse of the Lua file → `LuaFileElementType.doParseContents` (§3.1).
2. `LuaLanguageDefinition.createLexer()` → `LuaLexer` (syntax `FlexAdapter` + merging chain).
3. `LuaSyntaxParser.parse(converted, runtime)` builds the syntax tree.
4. `PsiSyntaxBuilder` + `ElementTypeConverter` materialize the classic AST using
   `LuaElementTypes`/`LuaTokenTypes` singletons.
5. `LuaParserDefinition.createElement` builds PSI (unchanged). Stub builder runs as before.
   Result: identical PSI to the classic path (MAINT-20-09).

## 6. Edge Cases
- **LuaCATS inside Lua** — LuaCATS is not a separate `ParserDefinition`; its nodes carry
  `LuaCatsElementType` (grep-confirmed: `LuaParserDefinition.createElement` dispatches on
  `LuaCatsElementType`). The converter and syntax holders must therefore include the LuaCATS
  token/node set too (MAINT-20-02/03), and `luacats.bnf` must be regenerated in the same syntax mode.
- **Stub version** — bumping `getStubVersion` (2→3) forces stub-cache rebuild so no stale
  classic-parsed stubs survive the switch.
- **Lazy/collapse element types** — `LuaLazyElementTypes` (referenced in `LuaLexer.kt`) must have
  matching `SyntaxElementType`s or be excluded from the syntax grammar identically to today; DR-02's
  tree-diff gate catches any divergence.

## 7. Integration Points

```xml
<!-- src/main/resources/META-INF/plugin.xml, under <extensions defaultExtensionNs="com.intellij"> -->
<syntax.syntaxDefinition   language="Lua"
    implementationClass="net.internetisalie.lunar.lang.syntax.LuaLanguageDefinition"/>
<syntax.elementTypeConverter language="Lua"
    implementationClass="net.internetisalie.lunar.lang.psi.LuaElementTypeConverterFactory"/>
<!-- existing, UNCHANGED: -->
<lang.parserDefinition language="Lua"
    implementationClass="net.internetisalie.lunar.lang.LuaParserDefinition"/>
```
Extension points verified in the reference repo:
`platform/syntax/syntax-psi/resources/intellij.platform.syntax.psi.xml` declares
`syntax.elementTypeConverter` and `syntax.syntaxDefinition`
(`beanClass="com.intellij.lang.LanguageExtensionPoint"`). DR-04 confirms these EPs are *registered
and available* in Lunar's 261 platform before wiring.

`lua.bnf` option additions (mirroring `json.bnf`, verified):
`generate=[parser-api="syntax"]`,
`syntaxElementTypeHolderClass="net.internetisalie.lunar.lang.syntax.LuaSyntaxElementTypes"`,
`elementTypeConverterFactoryClass="net.internetisalie.lunar.lang.psi.LuaElementTypeConverterFactory"`.
Existing `elementTypeHolderClass`/`psiPackage` (`lua.bnf:80,85`) are retained.

## 8. Requirement Coverage

| Requirement | Priority | Implemented by (section) |
|-------------|----------|--------------------------|
| MAINT-20-01 | M | §2.3, §2.4, plan toolchain |
| MAINT-20-02 | M | §2.2, §6 (LuaCATS), plan toolchain |
| MAINT-20-03 | M | §2.1, §2.2, §3.3 |
| MAINT-20-04 | M | §2.5, §7 (bnf options) |
| MAINT-20-05 | M | §2.6, §7 |
| MAINT-20-06 | M | §2.7, §3.3 |
| MAINT-20-07 | M | §2.8, §3.1 |
| MAINT-20-08 | M | §2.9 |
| MAINT-20-09 | M | §3.1, §3.2, §5 (equivalence via converter) |
| MAINT-20-10 | M | §2.4, §3.2 |
| MAINT-20-11 | S | §2.7 (classic holders retained as converter target) |
| MAINT-20-12 | S | plan toolchain / generate-parser skill update |

## 9. Alternatives Considered
- **Reuse `SyntaxGrammarKitFileElementType` directly** (as JSON). Rejected: it is a plain
  `IFileElementType`; Lunar needs `IStubFileElementType` for stub indexing. We inline its
  `doParseContents` into Lunar's stub subclass instead (§3.1).
- **Drop the classic `IElementType` holders** and key everything on `SyntaxElementType`. Rejected:
  ~all of Lunar's PSI/index/highlight code is `IElementType`-keyed; the converter lets the whole
  downstream stay unchanged (MAINT-20-11). Retaining classic holders is the minimal-risk path.
- **Keep the classic parser, migrate only the lexer.** Rejected: that is essentially MAINT-19's
  achievable scope already shipped; MAINT-20's mandate is the *full* port.

## 10. Open Questions
None. All unknowns are tracked as pre-implementation de-risking spikes DR-01…DR-04 in `risks-and-gaps.md`; they gate execution (Phase 1+ work does not begin until the spikes clear).
