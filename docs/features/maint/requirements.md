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
| [`MAINT-11`](11-structure-view/requirements.md) | **Test Coverage: Structure View** | **M** | **Planned** | Increase unit test coverage for outline structure view tree nodes. |
| [`MAINT-12`](12-settings-ui/requirements.md) | **Test Coverage: Settings & UI** | **M** | **Planned** | Increase unit test coverage for configuration persistence and change listeners. |
| [`MAINT-13`](13-run-debugger/requirements.md) | **Test Coverage: Run & Debugger** | **M** | **Planned** | Increase unit test coverage for debugger controllers and interactive REPL. |
| [`MAINT-16`](16-luacats-syntax/requirements.md) | **Test Coverage: LuaCATS Syntax** | **M** | **Planned** | Increase unit test coverage for LuaCATS type comments, highlights, and docs. |
| [`MAINT-17`](17-utilities-commandline/requirements.md) | **Test Coverage: Utilities** | **M** | **Planned** | Increase unit test coverage for process runner, file, and thread utilities. |
| [`MAINT-18`](18-luacov-reports/requirements.md) | **Test Coverage: LuaCov Reports** | **M** | **Planned** | Increase unit test coverage for LuaCov report parsing and layered highlighting. |
| `MAINT-19` | **platform.syntax Migration (Kotlin lexer/parser)** | **C** | **Todo** | Carve-out from MAINT-01. Migrate the lexer/parser to `com.intellij.platform.syntax` so the token-constant holders (`LuaTokenTypes`/`LuaCatsTokenTypes`) and the JFlex/Grammar-Kit output become Kotlin-native. Large architectural epic; deferred. |

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
- **Status**: **Todo** (backlog — carved out of MAINT-01)
- **Why it exists**: MAINT-01 cannot convert `LuaTokenTypes.java` / `LuaCatsTokenTypes.java` to
  Kotlin, because the JFlex-generated lexers inherit their token constants by *implementing* the
  interface:
  - `lua.flex:17-18` → `%implements FlexLexer, LuaTokenTypes`; `_LuaLexer.java:13` →
    `class _LuaLexer implements FlexLexer, LuaTokenTypes` (uses constants bare: `return WS;`, …)
  - `luacats.flex:24` → `%implements FlexLexer, LuaCatsTokenTypes`; `_LuaCatsLexer.java:25` likewise.

  A Kotlin `object` is a final class Java cannot `implements`; a Kotlin `interface` cannot hold
  initialized `val` constants. So a Kotlin-native lexer/parser requires adopting JetBrains' new
  multiplatform framework.
- **Scope (when planned)**: migrate to `com.intellij.platform.syntax` — `SyntaxElementType` token
  holders + an `IElementType ↔ SyntaxElementType` converter factory; regenerate the lexer with
  JFlex ≥ 1.10.x (Kotlin emission, e.g. `_JsonLexer.kt` in `intellij-community`) and the parser with
  Grammar-Kit `generate=[parser-api="syntax"]` (e.g. `JsonSyntaxParser.kt`); re-enable the
  grammar-kit Gradle plugin (currently disabled); bridge to the existing classic PSI.
- **Grounding**: verified against `intellij-community/json/syntax` (`json.bnf:3`
  `generate=[parser-api="syntax"]`, generated `JsonSyntaxParser.kt`, `_JsonLexer.kt`). None of the
  three Lua reference plugins (Luanalysis, EmmyLua, EmmyLua2) have migrated — all still ship Java
  lexers/parsers — so this is genuinely a large, optional, future epic, not a prerequisite for
  MAINT-01.
