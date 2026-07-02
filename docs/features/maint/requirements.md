---
id: "MAINT"
title: "MAINT: Maintenance & Refactoring"
type: "epic"
priority: low
status: planned
folders:
  - "[[features]]"
---

# Maintenance & Refactoring Requirements (`MAINT`)

Lunar prioritizes codebase health, performance, and alignment with modern IntelliJ Platform standards. The `MAINT` epic covers technical debt reduction, refactoring legacy components, and improving infrastructure.

## Requirements & Implementation Status

| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :---: | :---: | :--- |
| [`MAINT-01`](01-kotlin-conversion/requirements.md) | **Kotlin Conversion** | **M** | **Done** | Convert 4 hand-written legacy Java files (`LuaPsiUtils`, `LuaTokenType`, `LuaCatsElementType`, `LuaPluginDisposable`) to idiomatic Kotlin. The 2 token-constant interfaces are deferred to MAINT-19. |
| `MAINT-02` | **Label Refactoring** | **M** | **Todo** | Refactor Lua `goto` and label handling to use lazy resolution. |
| `MAINT-03` | **Deprecation Cleanup** | **L** | **Todo** | Remove usage of deprecated IntelliJ APIs (e.g. `DataContext`, `FileChooserDescriptorFactory`) and modernize platform integration. |
| [`MAINT-04`](04-refactor-symbol-resolution/requirements.md) | **Refactor Symbol Resolution** | **M** | **Done** | Replace eager `LuaBindingsVisitor` with lazy `PsiScopeProcessor`. |
| `MAINT-05` | **Type Engine Cleanup** | **M** | **Done** | Remove redundant type checks and unused parameters in the type engine. |
| [`MAINT-06`](06-luacats-literal-highlighting/requirements.md) | **LuaCATS Literal Highlighting** | **M** | **Done** | Add color formatting for literal types in LuaCATS tags. |
| [`MAINT-07`](07-interpreter-globs/requirements.md) | **Interpreter Search Path Globs** | **M** | **Done** | Add globbing support for interpreter search paths to improve module resolution. |
| `MAINT-08` | **LuaCheck UI Grouping** | **L** | **Done** | Implement hierarchical grouping for LuaCheck inspection results. |
| `MAINT-14` | **Scope Reduction (Luau)** | **L** | **Done** | Remove Luau support references to focus on standard Lua (5.1-5.4). |
| `MAINT-15` | **Remove Legacy Annotators** | **L** | **Planned** | Remove the three dead no-op annotators (`LuaLocalBindingsAnnotator`, `LuaGotoAnnotator`, `LuaGlobalBindingsAnnotator`) and their `plugin.xml` registrations. |
| [`MAINT-09`](09-psi-stubs/requirements.md) | **Test Coverage: PSI & Stubs** | **M** | **Done** | Increase unit test coverage for PSI walking, scopes, and stub serialization. |
| [`MAINT-10`](10-stub-indexes/requirements.md) | **Test Coverage: Stub Indexes** | **M** | **Done** | Increase unit test coverage for stub and file-based indexes. |
| [`MAINT-11`](11-structure-view/requirements.md) | **Test Coverage: Structure View** | **M** | **Done** | Increase unit test coverage for outline structure view tree nodes. |
| [`MAINT-12`](12-settings-ui/requirements.md) | **Test Coverage: Settings & UI** | **M** | **Done** | Increase unit test coverage for configuration persistence and change listeners. |
| [`MAINT-13`](13-run-debugger/requirements.md) | **Test Coverage: Run & Debugger** | **M** | **Done** | Increase unit test coverage for debugger controllers and interactive REPL. |
| [`MAINT-16`](16-luacats-syntax/requirements.md) | **Test Coverage: LuaCATS Syntax** | **M** | **Done** | Increase unit test coverage for LuaCATS type comments, highlights, and docs. |
| [`MAINT-17`](17-utilities-commandline/requirements.md) | **Test Coverage: Utilities** | **M** | **Done** | Increase unit test coverage for process runner, file, and thread utilities. |
| [`MAINT-18`](18-luacov-reports/requirements.md) | **Test Coverage: LuaCov Reports** | **M** | **Done** | Increase unit test coverage for LuaCov report parsing and layered highlighting. |
| [`MAINT-19`](19-platform-syntax-migration/requirements.md) | **Kotlin-native token holders** | **C** | **Done** | Carve-out from MAINT-01. Convert `LuaTokenTypes`/`LuaCatsTokenTypes` to Kotlin `@JvmField object`s and rewire the `.flex` sources to `import static`, so the JFlex-generated lexers consume Kotlin constants. Full `com.intellij.platform.syntax` migration deferred to MAINT-20. |
| `MAINT-20` | **Full platform.syntax Migration** | **C** | **Todo** | Follow-up to MAINT-19. Migrate the lexer/parser to `com.intellij.platform.syntax` (`%type SyntaxElementType`, a `SyntaxGeneratedParserRuntime` parser, a `LanguageSyntaxDefinition` extension) so the generated lexer/parser output becomes Kotlin-native. Requires vendoring the JetBrains syntax-emitting JFlex skeleton + Grammar-Kit generator and re-wiring the grammar-kit Gradle plugin. Large architectural epic. |

---

## Detailed Implementation Status

### MAINT-14: Scope Reduction (Luau)
- **Status**: **✅ Completed**
- **Action**: All references to Luau support have been removed from the documentation, backlog, and requirements to ensure project focus on standard Lua 5.1-5.4, LuaJIT, and Redis.

### MAINT-04: Refactor Symbol Resolution
- **Status**: **✅ Implemented**
- **Strategy**: PSI Scope Processor (Lazy evaluation)
- **Detailed Specification**: [`04-refactor-symbol-resolution/requirements.md`](04-refactor-symbol-resolution/requirements.md)

### MAINT-05: Type Engine Cleanup
- **Status**: **✅ Implemented**
- **Changes**: Simplified `getValueType` logic and removed dead code in `LuaTypesVisitor`.

### MAINT-19: platform.syntax Migration (Kotlin lexer/parser)
- **Status**: **✅ Done** (carved out of MAINT-01; delivered the Kotlin-native token holders).
- **Why it existed**: MAINT-01 could not convert `LuaTokenTypes.java` / `LuaCatsTokenTypes.java` to
  Kotlin, because the JFlex-generated lexers inherited their token constants by *implementing* the
  interface and referencing them *bare*:
  - `lua.flex:18` → `%implements FlexLexer, LuaTokenTypes`; `_LuaLexer.java` →
    `class _LuaLexer implements FlexLexer, LuaTokenTypes` (uses constants bare: `return NUMBER;`, …)
  - `luacats.flex:24` → `%implements FlexLexer, LuaCatsTokenTypes`; `_LuaCatsLexer.java` likewise.
- **What shipped**: the two holders are now Kotlin `object`s
  (`src/main/kotlin/.../lang/lexer/LuaTokenTypes.kt`, `.../luacats/lang/lexer/LuaCatsTokenTypes.kt`)
  with `@JvmField val` constants (idiom already used in `LuaIcons.kt` / `LuaCodeStyleSettings.kt`).
  A Kotlin `object`'s `@JvmField` members compile to `public static final` fields, so the `.flex`
  headers were rewired from interface-inheritance to a Java `import static <holder>.*` (dropping the
  token interface from `%implements`), and the two lexers were regenerated via headless JFlex 1.9.2.
  Result: zero `IElementType` instance changes, byte-identical debug names and transition tables, and
  all 10 qualified Kotlin consumers compile unchanged. The `.java` interfaces were deleted.
- **Full `com.intellij.platform.syntax` port — deferred (future work).** The roadmap's original
  "platform.syntax" framing (migrate to `SyntaxElementType` holders, a
  `SyntaxGeneratedParserRuntime` parser via Grammar-Kit `parser-api="syntax"`, JFlex Kotlin emission,
  a `LanguageSyntaxDefinition` extension, and re-enabling the grammar-kit Gradle plugin) is **out of
  scope** and tracked as follow-up feature `MAINT-20` (see
  `19-platform-syntax-migration/risks-and-gaps.md`).
  Grounding (in `intellij-community`): only JetBrains core languages (JSON `json/syntax`, Java, XML)
  have adopted it, requiring a JetBrains-internal syntax-emitting JFlex skeleton + Grammar-Kit
  generator that Lunar's classic build (`org.intellij.grammar.Main`, `%type IElementType`) does not
  have. None of the Lua reference plugins (Luanalysis, EmmyLua, EmmyLua2) have migrated either.
