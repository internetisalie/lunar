---
id: STATUS-DETAIL
title: Feature Implementation Status (Source-Verified)
type: spec
priority: high
folders:
  - "[[features]]"
---

# Lunar Feature Implementation Status

> Source-code-verified status of every epic and feature tracked in `docs/features/`.
> Generated **2026-06-14** by auditing `src/main/kotlin/`, `src/main/resources/META-INF/plugin.xml`,
> and `src/main/resources/`. Requirements docs were used only as a feature *inventory*;
> all status assessments are based on whether working code exists.
> **Updated 2026-06-16** for Wave 10: the TOOL and ROCKS epics are now fully implemented
> (`src/main/kotlin/.../tool/` and `.../rocks/`, registered in `plugin.xml`).
> **Reopened 2026-06-21:** ROCKS is **in_progress**, not done — ROCKS-05 (Rockspec Module
> Resolution) has no code, and a gap review flagged unbuilt work (server/registry config in
> settings, broader run-config/task-panel scope, the empty ROCKS-06/07 slots). Shipped ROCKS
> features (01–04, 08) remain source-verified `done`.
> **Updated 2026-06-23:** added **ROCKS-13** (Rockspec Editor Support). Only highlighting ships today
> (`.rockspec` added to the `Lua` `fileType` `extensions` in `plugin.xml`).
> **Updated 2026-06-24:** added the **SCHEMA** epic (Lua JSON-Schema engine + rockspec/luacheckrc/busted
> providers). **ROCKS-13 is superseded by SCHEMA-02** and reset to `todo`; its standalone hand-rolled
> design is replaced by the platform-engine route (a Lua `JsonLikePsiWalker`).

**Status vocabulary:**

| Status | Meaning |
|--------|---------|
| **done** | Fully implemented — code exists, registered, functional |
| **in progress** | Work has started — partial implementation or infrastructure exists |
| **planned** | Has full feature breakdown such that a non-frontier model could implement |
| **todo** | Not planned yet — no spec and no code |

---

## Progress by Epic

| Epic                                                        |   Done | In Progress | Planned |   Todo |   Total | Completion      |
| :---------------------------------------------------------- | -----: | ----------: | ------: | -----: | ------: | :-------------- |
| [**SYNTAX**](#syntax--syntax--editor)                       |     17 |           0 |       0 |      0 |      17 | ██████████ 100% |
| [**COMP**](#comp--code-completion)                          |      8 |           0 |       0 |      0 |       8 | ██████████ 100% |
| [**TYPE**](#type--type-system)                              |      9 |           0 |       0 |      0 |       9 | ██████████ 100% |
| [**NAV**](#nav--code-navigation)                            |     10 |           0 |       0 |      0 |      10 | ██████████ 100% |
| [**REFACT/INTENT**](#refactintent--refactoring--intentions) |      9 |           0 |       0 |      0 |       9 | ██████████ 100% |
| [**DEBUG/RUN**](#debugrun--debugging--execution)            |     11 |           0 |       0 |      0 |      11 | ██████████ 100% |
| [**INSP**](#insp--inspections--diagnostics)                 |      9 |           0 |       0 |      0 |       9 | ██████████ 100% |
| [**ANALYSIS**](#analysis--static-analysis-luacheck)         |      5 |           0 |       0 |      0 |       5 | ██████████ 100% |
| [**FORMAT**](#format--formatting)                           |      7 |           0 |       0 |      0 |       7 | ██████████ 100% |
| [**DOC**](#doc--documentation--luacats)                     |      8 |           0 |       0 |      0 |       8 | ██████████ 100% |
| [**TOOL**](#tool--tool-inventory-management)                |      4 |           0 |       0 |      0 |       4 | ██████████ 100% |
| [**ROCKS**](#rocks--luarocks-integration)                   |      8 |           0 |       3 |      1 |      12 | ███████░░░ 66%  |
| [**SCHEMA**](#schema--schema-driven-data-files)             |      0 |           0 |       1 |      3 |       4 | ░░░░░░░░░░  0%  |
| [**MAINT**](#maint--maintenance--internal-refactoring)      |      3 |           1 |       0 |      6 |      10 | ███░░░░░░░ 30%  |
| [**TARGET**](#target--runtime-environment-configuration)    |      7 |           0 |       0 |      0 |       7 | ██████████ 100% |
| [**BUG**](#bug--bug-fixes--stability)                       |     12 |           0 |       0 |      3 |      15 | ████████░░ 80%  |
| **Total**                                                   | **124** |       **1** |   **7** | **13** | **145** | **86%**         |

> [!NOTE]
> NAV-11 (Bindings Caching) was cancelled/retired as part of MAINT-04 and is excluded from
> the NAV count. The NAV epic has 11 tracked features but only 10 active ones.

---

## SYNTAX — Syntax & Editor

| ID | Feature | Status | Notes |
|:---|:--------|:-------|:------|
| SYNTAX-01 | Lua 5.4 Support (Variable Attributes) | done | Lexer, parser, `LuaAttribNameAnnotator` |
| SYNTAX-02 | Semantic Highlighting | done | Highlight keys for global/local/param/upvalue/shadowed; color settings page |
| SYNTAX-03 | Code Folding | done | `LuaFoldingBuilder` — functions, blocks, tables, comments |
| SYNTAX-04 | Brace Matching | done | `LuaPairedBraceMatcher` — 7 pairs incl. keyword pairs; `LuaCodeBlockSupportHandler` |
| SYNTAX-05 | Method Separators | done | `LuaMethodSeparatorProvider` |
| SYNTAX-06 | Breadcrumbs | done | `LuaBreadcrumbsProvider` |
| SYNTAX-07 | Inlay Hints | done | Type hints, parameter name hints, method chain hints — 3 providers |
| SYNTAX-08 | String Escape Processing | done | `LuaLiterals.kt` — all escape forms including `\u{...}` |
| SYNTAX-09 | Lua 5.5 Support | done | Parsing, scoping, and compliance inspection for global keywords and modes |
| SYNTAX-10 | Enter Handler for Comments | done | `LuaEnterHandlerDelegate` — auto-continuation of `---`, doc template generation |
| SYNTAX-11 | Numeric Literal Validation | done | `LuaNumeralAnnotator` — decimal/hex exponent validation, int/float semantic coloring |
| SYNTAX-12 | Label & Goto Scope Resolution | done | `LuaLabelReference` + `LuaLabelReferenceContributor` |
| SYNTAX-13 | Standalone Expression Annotator | done | `LuaStandaloneExpressionAnnotator` — flags non-call expressions as statements |
| SYNTAX-14 | Vararg Context Annotator | done | `LuaVarargAnnotator` — validates `...` usage in scope |
| SYNTAX-15 | Lexer Pushback Optimization | done | Already satisfied (lexer is state-based) |
| SYNTAX-16 | Language Level Enforcement | done | `LuaLanguageLevelAnnotator` — 5.2/5.3/5.4 feature detection + quick fixes |
| SYNTAX-17 | Inferred-Type Highlighting | done | `LuaInferredTypeAnnotator` — call-site, class ref, field/method distinction |

---

## COMP — Code Completion

| ID | Feature | Status | Notes |
|:---|:--------|:-------|:------|
| COMP-01 | Keyword Completion | done | `LuaCompletionContributor` — context-aware, language-level-aware |
| COMP-02 | Basic Symbol Completion | done | Local/global/param symbols via scope processor + stub index |
| COMP-03 | Cross-file Completion | done | `require()` resolution, global ranking, auto-import, and recursive/transitive resolution (`resolveAndAddSymbols` with `visited` cycle guard) all done |
| COMP-04 | Type-Inferred Completion | done | Dot/colon member completion via `LuaTypesVisitor` + `LuaGraphType.getMembers()` |
| COMP-05 | Parameter Name Hints | done | `LuaParameterInlayHintsProvider` + `LuaParameterInfoHandler` |
| COMP-06 | Postfix Templates | done | 11 `StringBasedPostfixTemplate`s in `lang/completion/postfix/` (`.if`/`.not`/`.var`/`.for`/`.forp`/`.fori`/`.ifnot`/`.nil`/`.notnil`/`.return`/`.print`) on `LuaPostfixTemplateProvider`, shared `LuaExprSelector` |
| COMP-07 | Live Templates | done | 16 templates in `liveTemplates/lua.xml` (insertion + 4 surround); `LuaCodeContextType`/`LuaSurroundContextType` (+ `LuaCodeContextPredicate`) suppress strings/comments/numbers |
| COMP-08 | Auto-complete Enhancement | done | `LuaEnterHandler` balance check (no redundant `end`) + full opener coverage incl. table `{}` + reformat; `LuaEnterBetweenBlockHandler` between-pair indent; shared `LuaBlockPairs` |

---

## TYPE — Type System

| ID | Feature | Status | Notes |
|:---|:--------|:-------|:------|
| TYPE-01 | Basic Type Inference | done | `LuaTypeGraph` (cubic biunification), `LuaTypesVisitor`, `LuaGraphType` |
| TYPE-02 | Class/Table Definitions | done | `@class`, `@alias`, `@field`, inheritance, implicit field discovery |
| TYPE-03 | Function Signature Matching | done | Arity, optional, vararg validation via `checkFunctionCompatibility()` |
| TYPE-04 | Union Types | done | `LuaGraphType.Union`, `LuaTypeAlgebra` canonicalization, member merging |
| TYPE-05 | Generics Support | done | `@generic` tag, `LuaGraphType.Generic`, let-polymorphism |
| TYPE-06 | Return Type Checking | done | `LuaReturnTypeMismatchInspection` — multi-return, arity check |
| TYPE-07 | External API Stubs | done | Resolves require calls to standard library and user-defined LuaCATS stub files |
| TYPE-08 | Flow-Sensitive Analysis | done | `tryParseTypeofGuard`, `tryParseNilGuard`, block-local scope injection |
| TYPE-09 | Union Distribution Logic | done | Distributive checking of union types (OR-distribution and AND-distribution) implemented |

---

## NAV — Code Navigation

| ID | Feature | Status | Notes |
|:---|:--------|:-------|:------|
| NAV-01 | Go to Definition | done | `LuaNameReference` via `PsiScopeProcessor` + `LuaGlobalDeclarationIndex` |
| NAV-02 | Find Usages | done | `LuaFindUsagesProvider`, `LuaNameReferenceSearcher`, read/write classification |
| NAV-03 | Go to Class/File/Symbol | done | `LuaGotoClassContributor`, `LuaGotoSymbolContributor`, `LuaCatsTypeNavigation` |
| NAV-04 | Structure View | done | File, global/local functions, params, local vars, labels, returns |
| NAV-05 | Method Override Markers | done | `LuaOverrideLineMarkerProvider` — gutter icons + navigation |
| NAV-06 | Hierarchy View | done | Type hierarchy (sub+super); method hierarchy not yet |
| NAV-07 | Reference Contributors | done | Label references; `require()` string resolution not yet |
| NAV-08 | Line Markers | done | Recursive call + tail call markers via `LuaLineMarkerProvider` |
| NAV-09 | Return Highlighter | done | `LuaReturnHighlightUsagesHandlerFactory` — highlight returns + function keyword |
| NAV-10 | Access Detector | done | `LuaReadWriteAccessDetector` + `LuaReadWriteUsageTypeProvider` |

> NAV-11 (Bindings Caching) was cancelled and retired as part of MAINT-04.

---

## REFACT/INTENT — Refactoring & Intentions

| ID | Feature | Status | Notes |
|:---|:--------|:-------|:------|
| REFACT-01 | Rename Refactoring | done | Platform rename via `PsiNamedElement.setName()` |
| REFACT-02 | Introduce Variable | done | `LuaIntroduceVariableHandler` — extract, replace-all, inline rename |
| REFACT-03 | Safe Delete | done | `LuaSafeDeleteProcessor` — usage search + conflict prompt |
| REFACT-04 | Label Refactoring | done | Rename + find-usages for labels via consolidated providers |
| REFACT-05 | Name Validator | done | `LuaNamesValidator` — rejects Lua keywords and non-identifier names in Rename |
| REFACT-06 | Create from Usage | done | `LuaCreateLocalVariableIntention` + `LuaCreateFunctionIntention` (shared `LuaUndeclaredNames` helper) |
| INTENT-01 | String Style Conversion | done | `LuaStringConversionIntention` |
| INTENT-02 | Invert If Statement | done | `LuaInvertIfIntention` + `LuaConditionInverter` |
| INTENT-03 | Name Suggestion | done | `LuaNameSuggestionProvider` + shared `LuaNameDeriver` (prefix-strip), reused by `LuaIntroduceVariableHandler` |

---

## DEBUG/RUN — Debugging & Execution

| ID | Feature | Status | Notes |
|:---|:--------|:-------|:------|
| DEBUG-01 | Line Breakpoints | done | `LuaLineBreakpointType` + `LuaLineBreakpointHandler` |
| DEBUG-02 | Stack Frames & Variables | done | `LuaRemoteStack`, `LuaStackFrame`, `LuaDebugVariable` |
| DEBUG-03 | Step Over/Into/Out | done | `LuaDebuggerController` + `LuaDebugConnection` STEP/OVER/OUT |
| DEBUG-04 | Expression Evaluation | done | `LuaDebuggerEvaluator` — expression + statement mode |
| DEBUG-05 | Remote Debugging | done | `LuaDebugConnection` — TCP/Mobdebug on port 8172 |
| DEBUG-06 | Debug Target Configuration | done | `LuaRunConfiguration` — interpreter, script, env vars, working dir |
| DEBUG-07 | Lazy Remote Stack Evaluation | done | `LuaRemoteStack` lazy properties |
| RUN-01 | Lua Interpreter SDK | done | `LuaInterpreter`, `LuaInterpreterService`, `LuaInterpreterFamily` |
| RUN-02 | Run Configurations | done | `LuaRunConfiguration` + `LuaRunConfigurationType` |
| RUN-03 | Interactive Console (REPL) | done | `run/console/` pkg: `LuaConsoleRunner`/`View`/`ExecuteHandler` |
| RUN-04 | Run Configuration Validation | done | Interpreter + script validation in `getState()` |
| RUN-05 | Test Runner Integration | done | `run/test/` pkg + `coverage/` pkg, Test runner and Coverage rendering |

---

## INSP — Inspections & Diagnostics

| ID | Feature | Status | Notes |
|:---|:--------|:-------|:------|
| INSP-01 | Undeclared Variable | done | `LuaUndeclaredVariableInspection` — multiResolve, standard globals, allowlist, quick fix |
| INSP-02 | Unused Local/Parameter | done | NAV-02 (usages) |
| INSP-03 | Type Mismatch | done | `LuaTypeAssignabilityInspection` + `LuaReturnTypeMismatchInspection` |
| INSP-04 | Unreachable Code | done | `LuaUnreachableCodeInspection` |
| INSP-05 | Global Creation Warning | done | `LuaGlobalCreationInspection` |
| INSP-06 | Shadowing Check | done | `LuaShadowingVariableInspection` |
| INSP-07 | Suspicious Concatenation | done | `LuaSuspiciousConcatenationInspection` |
| INSP-08 | Deprecated API Usage | done | `LuaDeprecatedApiInspection` |
| INSP-09 | Language Level Compliance | done | `LuaLanguageLevelInspection` |

---

## ANALYSIS — Static Analysis (Luacheck)

| ID | Feature | Status | Notes |
|:---|:--------|:-------|:------|
| ANALYSIS-01 | Luacheck Integration | done | `LuaCheckInvoker` — invokes binary, parses output |
| ANALYSIS-02 | Settings Panel | done | `LuaCheckSettingsPanel` — binary path, arguments |
| ANALYSIS-03 | External Annotator | done | `LuaCheckAnnotator` — inline WARNING annotations |
| ANALYSIS-04 | Output Parsing | done | `LuaCheckModel` + `LuaCheckNodes` — full tree model |
| ANALYSIS-05 | Custom Rules (.luacheckrc) | done | Luacheck's built-in discovery; `workDirectory` set correctly |

---

## FORMAT — Formatting

| ID | Feature | Status | Notes |
|:---|:--------|:-------|:------|
| FORMAT-01 | Basic Indentation | done | `LuaFormatBlock` — blocks, labels, tables, expressions, var lists |
| FORMAT-02 | Configurable Code Style | done | `LuaCodeStyleSettings` + provider; indent, spacing, wrapping options |
| FORMAT-03 | Blank Line Management | done | LuaFormatBlock spacing + LuaTrailingNewlinePostProcessor |
| FORMAT-04 | Expression Wrapping | done | Wrap logic implemented |
| FORMAT-05 | Alignment Logic | done | Alignment logic implemented |
| FORMAT-06 | Comment Formatting | done | `LuaCommentWrapPostProcessor` |
| FORMAT-07 | Stylua Compatibility | done | `StyluaFormattingService` and `StyluaFormattingTask` |

---

## DOC — Documentation & LuaCATS

| ID | Feature | Status | Notes |
|:---|:--------|:-------|:------|
| DOC-01 | Quick Documentation (Ctrl+Q) | done | `LuaDocumentationTargetProvider` + `LuaDocumentationRenderer` |
| DOC-02 | LuaCATS Syntax Highlighting | done | `LuaCatsAnnotator` — all tag types |
| DOC-03 | External URL Links | done | GFM autolinks, `@see` tag, stdlib URL linking |
| DOC-04 | Documentation Generation | done | `LuaDocGenerator` + `LuaGenerateDocIntention` + enter-handler auto-gen |
| DOC-05 | Markdown Support | done | `org.intellij.markdown` rendering + Lua code highlighting |
| DOC-06 | Documentation Indexing | done | 6 indexes + type map + platform symbol documentation implemented |
| DOC-07 | Parameter Info | done | `LuaParameterInfoHandler` — signature help during function calls |
| DOC-08 | Comprehensive LuaCATS Parsing | done | 19 tag types, complex type system, multi-line enum |

---

## TOOL — Tool Inventory Management

| ID | Feature | Status | Notes |
|:---|:--------|:-------|:------|
| TOOL-00 | De-risking & Technical Spikes | done | Terminal PATH-injection corrected to `ShellExecOptionsCustomizer`/`prependEntryToPATH`; `tool/terminal/LuaShellExecOptionsCustomizer` + `lunar-terminal.xml`. Remaining exploratory spikes = Future Work |
| TOOL-01 | Core Tool Registry & Discovery | done | `tool/LuaToolManager` (`@Service` APP) + `LuaTool` + `LuaToolValidator`/`LuaToolDiscoveryService`; registered in `plugin.xml` |
| TOOL-02 | Project Binding & Environment | done | `tool/LuaTerminalEnvironmentService` + cmdline PATH patch in `command/LuaCommandLine.kt`; reuses `LuaSettingsChangedListener.TOPIC` |
| TOOL-03 | UI/UX & Health Monitoring | done | `tool/ui/LuaToolsConfigurable` + `tool/health/*` monitor/checker/editor-banner; 2 health fields on `LuaTool` |

---

## ROCKS — LuaRocks Integration

| ID | Feature | Status | Notes |
|:---|:--------|:-------|:------|
| ROCKS-01 | Project Initialization & Setup | done | `rocks/init/LuaRocksProjectGenerator` (`directoryProjectGenerator`) + templates/scaffolder |
| ROCKS-02 | Package Browser | done | `rocks/browser/*` tool window "LuaRocks Packages"; porcelain search/list/show parse + TTL cache |
| ROCKS-03 | Dependency Resolution | done | `rocks/LuaRocksDependencyResolver` + `deps/*`; bridge Lua scripts packaged as resources via `LuaRocksBridgeFiles` |
| ROCKS-04 | Task Execution & Run Configs | done | `rocks/run/*` — `LuaRocksSettings` (shared) + `LuaRocksRunConfiguration` |
| ROCKS-05 | Rockspec Module Resolution | done | `RockspecModuleDerivation` + `RockspecSourcePathProvider` (caches paths to `PathConfiguration`) + `RockspecRunPathProvider` (LUA_PATH/CPATH unions) |
| ROCKS-06 | Project LuaRocks Environment | done | `LuaRocksEnvironment` (server resolution, `withServer`), `LuaRocksApiKeyStore`, UI project overrides |
| ROCKS-08 | Publishing & Lifecycle | done | `rocks/publish/*` — `PublishRockAction` (`Lua.Rocks.Publish`), `luarocks upload --api-key=`, key in PasswordSafe |
| ROCKS-09 | Multi-Rock Workspace Discovery | done | `LuaRockspecDiscoveryService` (index-backed, cached scanner), `LuaRocksDependencyResolver` forest traversal |
| ROCKS-10 | Workspace Build Orchestration | done | Topo-sort discovered rocks via dependency graph; `luarocks make` in dependency order |
| ROCKS-11 | Makefile Task Integration | done | Enrich scaffolded Makefile (lint/format/coverage targets); optional Makefile-plugin integration |
| ROCKS-12 | Project-View Roots & Marking | done | `lua_modules` installed-rock tree as External Libraries (SyntheticLibrary) + first-party source-root marking (ProjectViewNodeDecorator) |

---

## SCHEMA — Schema-Driven Data Files

> No code yet — epic planned 2026-06-24. The engine adapts the platform JSON-Schema engine to Lua
> (`JsonLikePsiWalker`) so Lua *data* files get validation/completion/docs; providers are declarative.

| ID | Feature | Status | Notes |
|:---|:--------|:-------|:------|
| SCHEMA-01 | Lua JSON-Schema Engine | done | `lang/schema/*`: Lua `JsonLikePsiWalker` + adapters + walker-factory/enabler + `language="Lua"` compliance inspection; depends on `com.intellij.modules.json` |
| SCHEMA-02 | Rockspec Schema Provider | todo | `.rockspec` → bundled rockspec v3.0/v3.1 schema; supersedes ROCKS-13 |
| SCHEMA-03 | Luacheckrc Schema Provider | todo | `.luacheckrc` → new bundled luacheck-config schema (second consumer; proves generality) |
| SCHEMA-04 | Busted Config Schema Provider | todo | `.busted` (shape-B `return {table}`) → bundled busted-config schema |

---

## MAINT — Maintenance & Internal Refactoring

| ID | Feature | Status | Notes |
|:---|:--------|:-------|:------|
| MAINT-01 | Kotlin Conversion | in progress | 4 Java files remain (LuaPsiUtils, LuaTokenType, LuaCatsElementType, LuaPluginDisposable) |
| MAINT-02 | Label Refactoring | done | Lazy PsiScopeProcessor-based resolution, PsiNameIdentifierOwner implementation, and rename binding |
| MAINT-03 | Deprecation Cleanup | todo | Deprecated APIs still used (DataContext, FileChooserDescriptorFactory) |
| MAINT-04 | Refactor Symbol Resolution | done | `LuaScopeProcessor` + `processDeclarations` across block/file/function/for |
| MAINT-05 | Type Engine Cleanup | done | `LuaTypesVisitor` simplified, used across hints/annotators |
| MAINT-06 | LuaCATS Literal Highlighting | todo | No literal type color formatting |
| MAINT-07 | Interpreter Search Path Globs | todo | No glob expansion in `PathConfiguration` |
| MAINT-08 | LuaCheck UI Grouping | todo | Flat problem reporting, no hierarchical grouping |
| MAINT-14 | Scope Reduction (Luau) | done | No Luau references in codebase, no `LuaPlatform.LUAU` |
| MAINT-15 | Remove Legacy Annotators | todo | `LuaLocalBindingsAnnotator` still exists and is registered (empty body) |

---

## TARGET — Runtime Environment Configuration

| ID | Feature | Status | Notes |
|:---|:--------|:-------|:------|
| TARGET-00 | Preparatory Activities | done | Design docs, implementation plan, risks, verification checklists |
| TARGET-01 | Target Data Model | done | `Target`, `VersionEntry`, `PlatformVersionRegistry` (6 platforms, 13 versions) |
| TARGET-02 | Implicit Language Level | done | `Target.getImplicitLanguageLevel()` maps all platform/version combos |
| TARGET-03 | UI Contextual Versions | done | `LuaProjectSettingsPanel` — dynamic platform→version→language level |
| TARGET-04 | Library Root Resolution | done | `PlatformLibraryProvider` as `additionalLibraryRootsProvider` |
| TARGET-05 | Luacheck Integration | done | `LuaCheckCommandLine` passes `--std` from `target.getLuacheckStd()` |
| TARGET-06 | Migration | done | `LuaProjectSettings.State.migrateFromLegacySettings()` |

---

## BUG — Bug Fixes & Stability

| ID | Feature | Status | Notes |
|:---|:--------|:-------|:------|
| BUG-01 | Recursive Local Resolution | done | Fixed via `PsiScopeProcessor` refactor (MAINT-04) |
| BUG-132 | Duplicate Problems Reporting | done | Deduplicate Luacheck warnings that appear on the same line with the same message |
| BUG-133 | Union Inlay Hints (OR) | done | Show parameter hints when resolving a call on a union type |
| BUG-134 | @return Comma Parsing | done | Support comma-separated return types in @return tag |
| BUG-135 | Stdlib Inlay Hints | done | Suppress parameter inlay hints for core standard library functions |
| BUG-272 | Local Var Navigation | done | Fixed via PsiScopeProcessor lazy resolution |
| BUG-349 | Flaky Inlay Hint Tests | done | Fix intermittent failures in inlay hint tests caused by state pollution and cache staling |
| BUG-353 | package.path Member Resolution | done | Resolves package.path and package.cpath base/member segments correctly |
| BUG-354 | Multiline Comment Collision | done | Regular comments starting with dashes do not trigger LuaCATS parser errors |
| BUG-355 | EmmyLua @-description Parse | done | Supports description after @return/@param types |
| BUG-356 | Boolean Concat flag | done | Concatenating a boolean is flagged as suspicious concatenation |
| BUG-357 | LuaCATS `fun()` Param Names | cancelled | Non-reproducible — names extracted correctly at parser, graph round-trip, and call-site hints (resolved by BUG-133). Regression guards added; no production change |
| BUG-358 | Reformat Read-Only Exception | todo | TransactionGuard write-unsafe context exception when reformating a read-only file |
| BUG-359 | package.path Nil Assignment | todo | False positive 'nil value is not assignable to string' on package.path concat |
| BUG-360 | Container Writable Status | todo | Fix file writable status due to container/host UID GID mismatch |
| BUG-BASELINE-TESTS | Pre-existing Baseline Failures | done | Stale-assertion / logic regressions in TargetTest, BraceMatchingTest, RunConfigTest |

> [!NOTE]
> BUG-357 was cancelled (cannot-reproduce / already resolved by BUG-133) and is excluded
> from the BUG epic counts above, following the same convention as NAV-11.
