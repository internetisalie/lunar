---
id: "MAINT-20-RISKS"
title: "Risks & Gaps"
type: "risk"
priority: "low"
parent_id: "MAINT-20"
folders:
  - "[[features/maint/20-full-platform-syntax-migration/requirements|requirements]]"
---

# MAINT-20: Risks & Gaps

This is a large, high-uncertainty architectural migration. The **DR spikes below are genuine
pre-implementation gates**: they must all pass (with the stated success criteria) **before**
MAINT-20 moves from `todo` to `in_progress`. If DR-01 or DR-02 fails, the migration is not
executable in this checkout and the feature stays `todo` with the finding recorded (mirroring how
MAINT-19 scoped the full port out with evidence).

## Critical Risks

### Risk 1.1: Generator toolchain not reproducible in this checkout
- **Impact**: cannot emit `_LuaLexer.kt` (`%type SyntaxElementType`) or `LuaSyntaxParser`
  (`parser-api="syntax"`); the migration is blocked at the source.
- **Likelihood**: medium. The Kotlin JFlex skeleton and grammar-kit `parser-api` option are
  verified to exist in `~/Documents/src/lua/intellij-community`, but Lunar's local JFlex is
  `jflex-1.9.2.jar` and its grammar-kit jar is resolved ad-hoc from the Gradle cache
  (`generate.sh:48`) and was **not present** at planning time (`find … -iname 'grammar-kit*.jar'`
  returned nothing).
- **Mitigation**: DR-01 (JFlex Kotlin skeleton) and DR-02 (grammar-kit `parser-api="syntax"`) prove
  the toolchain before any artifact is committed.

### Risk 1.2: Stub-index breakage from the file-element-type re-wire
- **Impact**: `LuaFileElementType` is `IStubFileElementType`; a botched `doParseContents` re-wire
  could corrupt stubs / indexes (class-name, alias, global) and silently break navigation.
- **Likelihood**: medium.
- **Mitigation**: keep the `IStubFileElementType` subclass and only replace `doParseContents` body
  (design §3.1); bump `getStubVersion` to force rebuild; TC-9 regression on `LuaClassNameIndex`.

### Risk 1.3: Token/tree divergence via the converter
- **Impact**: a missing or mismapped `SyntaxElementType`↔`IElementType` pair yields a different PSI
  tree or `IElementType.toString()` string, breaking string-asserting tests and downstream logic.
- **Likelihood**: medium.
- **Mitigation**: name-parity rule (design §3.3); DR-02 tree-diff gate; full suite unmodified (TC-10).

### Risk 1.4: LuaCATS dual-set coverage
- **Impact**: LuaCATS nodes carry `LuaCatsElementType` and are parsed inside the Lua grammar; if the
  syntax holders/converter omit the LuaCATS set, `---@`-annotated files mis-parse.
- **Likelihood**: medium.
- **Mitigation**: MAINT-20-02/03 explicitly cover the LuaCATS holders; TC-8 asserts LuaCATS lexing.

## Design Gaps

### Gap 2.1: JFlex version that emits the Kotlin skeleton output
- **Question**: does the bundled `jflex-1.9.2.jar` accept `--skel idea-flex-kotlin.skeleton` and
  produce compiling Kotlin, or is a different JFlex build required?
- **Options / leaning**: try 1.9.2 first; JetBrains generates JSON's `_JsonLexer.kt` with their
  internal JFlex — if 1.9.2 fails, source the matching JFlex from the reference repo's build.
- **Resolved by**: DR-01.

### Gap 2.2: Grammar-Kit build supporting `parser-api="syntax"`
- **Question**: which grammar-kit jar (version) that Lunar can resolve produces the syntax parser +
  `syntaxElementTypeHolderClass` + `elementTypeConverterFactoryClass` outputs?
- **Options / leaning**: the version in the 261 Gradle cache may already support it (JSON ships in
  the same platform); if not, pin a newer grammar-kit jar for the manual generate step only.
- **Resolved by**: DR-02.

### Gap 2.3: Are the `syntax.*` EPs registered in Lunar's 261 platform at runtime?
- **Question**: `syntax.syntaxDefinition` / `syntax.elementTypeConverter` are declared in
  `intellij.platform.syntax.psi.xml` in the reference repo — are they loaded in the GoLand 2026.1
  Lunar targets (so `plugin.xml` can reference them without an "unknown extension point" error)?
- **Options / leaning**: `syntax-psi-261` is in Lunar's dependency set, so the module is present;
  confirm the EP is actually contributed at runtime.
- **Resolved by**: DR-04.

## Technical Debt & Future Work
- **TBD: Wire the grammar-kit Gradle plugin** so syntax generation is a Gradle task rather than a
  manual step — deferred because of the Kotlin circular-dependency issue documented in CLAUDE.md.
- **TBD: Migrate the highlighting lexer factory / brace matcher** to consume `SyntaxElementTypeSet`
  from `LuaLanguageDefinition` directly — out of scope; MAINT-20 keeps the classic highlighting path
  via the converter.

## Pre-Implementation De-risking Tasks

| ID | Action | Resolves | Status |
|----|--------|----------|--------|
| MAINT-20-00-DR-01 | **Spike: JFlex Kotlin lexer emission.** Run `jflex-1.9.2.jar --skel ~/Documents/src/lua/intellij-community/tools/lexer/idea-flex-kotlin.skeleton` on a minimal `%type SyntaxElementType` flex derived from `lua.flex` into a scratch dir. **Success criterion**: a Kotlin `_LuaLexer.kt` is produced that compiles against `com.intellij.platform.syntax.util.lexer.FlexLexer` and returns `SyntaxElementType`. If 1.9.2 fails, identify and pin the JFlex build that succeeds. | Risk 1.1, Gap 2.1 | **FAIL** |
| MAINT-20-00-DR-02 | **Spike: Grammar-Kit syntax parser emission.** On a *copy* of `lua.bnf` with `generate=[parser-api="syntax"]` + `syntaxElementTypeHolderClass` + `elementTypeConverterFactoryClass`, run `org.intellij.grammar.Main` using the grammar-kit jar resolved by `generate.sh`. **Success criterion**: it emits `LuaSyntaxParser` (`fun parse(SyntaxElementType, SyntaxGeneratedParserRuntime)`), `LuaSyntaxElementTypes`, and `LuaElementTypeConverterFactory` that compile; the generated node/token names byte-match the classic debug names (diff-gate). If the resolved version lacks `parser-api`, pin the version that supports it. | Risk 1.1/1.3, Gap 2.2 | **BLOCKED** |
| MAINT-20-00-DR-03 | **Spike: merging-lexer bridge equivalence.** Prototype `LuaLexer` with the syntax `FlexAdapter` + `ElementTypeConverter` boundary (design §3.2 option A). **Success criterion**: lexing the exhaustive corpus (`TestLuaLexerExhaustive` inputs) yields a token stream byte-identical to the current Java `FlexAdapter` path, including merged `STRING`/`LONGSTRING`/`LONGCOMMENT`/`SHORTCOMMENT`. | Risk 1.3, MAINT-20-10 | **NOT RUN** (gated on DR-01) |
| MAINT-20-00-DR-04 | **Spike: platform.syntax EP availability at runtime.** In a `runIde` sandbox on GoLand 2026.1, confirm `syntax.syntaxDefinition` and `syntax.elementTypeConverter` are contributed (register a trivial `LuaLanguageDefinition` + no-op converter and verify `LanguageSyntaxDefinitions.INSTANCE.forLanguage(LuaLanguage)` resolves it with no "unknown extension point"/load error). **Success criterion**: EPs resolve and the plugin loads clean. | Risk 1.2, Gap 2.3 | **NOT RUN** (gated on DR-01/02) |

### Phase 0 Outcome (executed 2026-07-02) — GATE FAILED, feature held at `todo`

The hard gate (DR-01 **or** DR-02 failing → STOP, keep `todo`) tripped on **both** toolchain
spikes. The full `com.intellij.platform.syntax` port is **not executable in this checkout**; the
finding mirrors how MAINT-19 scoped the full port out with evidence.

- **DR-01 — FAIL (dispositive).** `jflex-1.9.2.jar --skel …/idea-flex-kotlin.skeleton` on a minimal
  self-contained `%type SyntaxElementType` scratch flex *does* accept `--skel` and *does* apply the
  Kotlin skeleton frame (emits `package`, `private const val`, `override fun getTokenStart()`,
  `var zzBuffer: CharSequence`). **But the generated body is a broken Java/Kotlin hybrid that does
  not compile.** At the skeleton's `--- actions` injection point (skeleton line 302) stock JFlex
  1.9.2 emits the action dispatch as a **Java `switch (…) { case 1: … case 4: break; }`**, plus
  inline Java statements (`int zzNext = …;`, `Character.codePointAt(…)`). These collide with the
  Kotlin skeleton's `else ->` arm, so `kotlinc` (2.0.21, `-cp` = `syntax-261` +
  `syntax-util-261`/`FlexLexer`) fails with `unresolved reference 'case'` / `Expecting '}'`. Stock
  JFlex 1.9.2 does **not** know how to emit the Kotlin `when` the skeleton expects — JetBrains emits
  it with an **internal JFlex fork** that is *not* present in the reference monorepo (only the
  `.skeleton` is), *not* in any local Gradle/Maven cache, and no pinnable public coordinate was
  found. DR-01's fallback ("pin the JFlex build that succeeds") therefore cannot be satisfied here.
- **DR-02 — BLOCKED (independently dispositive).** `generate.sh:48` resolves the grammar-kit jar
  ad-hoc from `~/.gradle/caches/modules-2` — but **grammar-kit is not a declared Lunar dependency**
  (`build.gradle.kts` / `gradle.properties` / `libs.versions.toml` reference none) and **no
  `grammar-kit-*.jar` exists anywhere in the caches**. `org.intellij.grammar.Main` cannot even be
  invoked, so `parser-api="syntax"` support is untestable in this checkout.
- **DR-03 / DR-04 — NOT RUN.** Both presuppose a working syntax lexer/parser (DR-01/DR-02); with the
  emitter unavailable there is nothing to bridge (DR-03) or register (DR-04). Not run — avoids
  burning a gce-builder / `runIde` sandbox on a gate already decided.

**Decision:** MAINT-20 stays `todo` (reverted from `planned`). The classic `%type IElementType`
Grammar-Kit + Java-JFlex toolchain MAINT-19 delivered on remains the supported path. Re-open MAINT-20
only if the JetBrains Kotlin-emitting JFlex fork **and** a `parser-api="syntax"`-capable grammar-kit
jar become resolvable (e.g. published to a reachable repository, or vendored into the repo).

## Test Case Gaps
- **PSI-tree snapshot baseline** — requirements TC-6 needs a captured pre-migration tree snapshot to
  diff against; produce it from `main` HEAD before Phase 1 (it is an input to the equivalence test,
  not a runtime artifact).
- **LuaCATS parser regression** — beyond lexing (TC-8), add a PSI-level check that a `---@class`/
  `---@field` block parses to the identical `LuaCatsElementType` node tree post-migration.

## See Also
- Requirements: [requirements.md](requirements.md)
- Design: [design.md](design.md)
- Predecessor findings: [[features/maint/19-platform-syntax-migration/risks-and-gaps|MAINT-19 Risks & Gaps]]
