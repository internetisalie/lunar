---
id: "REVIEW-2026-07"
title: "Full Codebase Review — July 2026"
type: "guide"
priority: "high"
folders:
  - "[[features]]"
---

# Full Codebase Review — 2026-07-02

Whole-codebase review of `src/main/kotlin` (~32k LOC production Kotlin; `src/main/gen`
excluded) performed as we approach feature-completeness. Six parallel reviewers covered:
lang core (lexer/psi/types/indexing), lang features (completion/insight/format/doc/…),
luacats, run/debug/coverage, analysis, and rocks/tool/platform/settings. Each finding
carries a confidence label from the reviewer; items marked **(verified)** were
independently re-checked against the source during consolidation.

**Overall assessment.** The codebase splits into two strata. Newer, spec-driven code
(type narrowing, auto-import, hierarchy, schema, ROCKS environment resolution, coverage,
`analysis/inspections`) is well-factored and largely contract-compliant. Older plumbing —
the DBGp debugger, luacheck integration, completion-stack internals, settings/interpreter
UI, and the type-graph's mutable core — predates the engineering contract and carries the
bulk of the defects: ~25 confirmed bugs, ~1,000 lines of dead code, and a handful of
systemic patterns worth fixing once rather than per-site.

---

## 1. Bugs (prioritized)

### P1 — Crashes, corruption, EDT freezes, destructive fixes

| # | Location | Issue | Fix |
|---|----------|-------|-----|
| 1 | `lang/psi/types/LuaTypesVisitor.kt:772` | **(verified)** `TYPEOF_MAP` maps `"table"` to a single shared mutable `LuaGraphType.Table()`. TYPE-08 narrowing injects it as a variable's type; `handleSetMetatable` then mutates its `superTypes` — `if type(t) == "table" then setmetatable(t, mt) end` pollutes a JVM-global singleton, leaking members across all files for the IDE session. | Produce fresh instances (`Map<String, () -> LuaGraphType>`) and/or copy-on-mutate in `handleSetMetatable`. |
| 2 | `lang/psi/types/LuaTypes.kt:76-128` | `graphTypeToLuaType` recurses through members with no cycle guard; a self-referential table (`t.self = t`, common module pattern) → `StackOverflowError` in inlay hints/inspections. | Thread a `visited` map through the conversion, as `LuaGraphType.fromLuaType` already does. |
| 3 | `lang/psi/types/LuaTypeManagerImpl.kt:144-147` | `VfsUtil.findFileByIoFile(file, true)` performs a **synchronous VFS refresh under the read lock** (reached from annotator/completion traversal) — forbidden by the platform. | `refreshIfNeeded = false` (as `LuaRequireReference.resolve` correctly does). |
| 4 | `run/LuaDebugProcess.kt:145-163` + `run/LuaDebuggerController.kt:113-116` | If `accept()` fails (user stops session before connect), `waitForConnect` returns normally with `isReady == false`; `registerBreakpoints()`'s `while (!isReady) Thread.sleep(100)` spins forever inside a `@Synchronized` method — any later breakpoint toggle **blocks the EDT permanently**. | Propagate accept failure; replace the poll loop with real readiness sequencing. |
| 5 | `run/LuaDebugConnection.kt:164,172,262,277-278,326,358-367` | Byte/char confusion in DBGp framing: length prefix counts bytes, `readExactly` reads chars from a Reader — any multi-byte UTF-8 payload desyncs the protocol permanently. Writes are UTF-8 but the reader uses platform-default charset. | Read exactly N raw bytes from the InputStream, then decode UTF-8; explicit charset on the reader. |
| 6 | `run/LuaDebugProcess.kt:74-76` | "Run to Cursor" throws `AbstractMethodError` — crash dialog on a standard debugger action. | Implement via temporary SETB + RUN + DELB, or no-op with a status message. |
| 7 | `run/LuaDebugValue.kt:24,114` | Imports `nullIfEmpty` from `com.jetbrains.rd.generator.nova.GenerationSpec` — an rd-gen codegen internal not guaranteed at runtime across IDE distributions; if absent, every non-primitive value presentation throws `NoClassDefFoundError`. | `stringValue.ifEmpty { identityValue ?: "" }`. |
| 8 | `lang/syntax/LuaLanguageLevelQuickFixes.kt:90-105` | `ReplaceIntegerDivisionFix` matches the first element whose text contains `//` — the operator leaf itself — and replaces it with `math.floor(/)`, producing garbage like `a math.floor(/) b`. Destructive quick fix. | Find the enclosing `LuaBinOpExpr` with an INTDIV operator and rebuild `math.floor(left / right)` from operands. |
| 9 | `analysis/inspections/LuaGlobalCreationInspection.kt:82-98` | "Make local" prepends `local ` to the whole statement text; on `x, t.f = 1, 2` this generates syntactically invalid Lua. | Bail (or split) when the assignment has multiple targets or any suffixed target. |
| 10 | `platform/LuaInterpreterService.kt:71` | `possibleResult.family!!` — when `identify()` fails (broken/hung binary), `family` is null → KotlinNPE aborts the entire interpreter re-scan on the first bad candidate. | Use `familyOrUnknown` / null-safe compare; treat mismatch as "not this family". |
| 11 | `rocks/LuaRockspecDiscoveryService.kt:68-79` + `util/LuaProcessUtil.kt:17-25` | Rockspec bridge (`lua` subprocess, 10 s timeout each) runs inside a `CachedValue` evaluated **while holding the read lock**; `capture` blocks `.get()` under the lock. Reachable from `BuildWorkspaceAction.update()` → typing can stall 10 s × #rockspecs. | Enumerate paths under read action; run bridge processes strictly outside the lock; `update()` reads cached data only. |
| 12 | `tool/ui/LuaToolsConfigurable.kt:49-52,89-95` | Auto-Discover/Add run `LuaToolManager.autoDiscover()`/`registerTool()` on the **EDT**; each registration spawns the binary twice with a 10 s timeout — multi-second EDT freezes. | `Task.Backgroundable` + `invokeLater(reset)`, like `recheckAll()` already does. |
| 13 | `run/LuaDebuggerEvaluator.kt:42-66` + `run/LuaDebuggerController.kt:283` | Evaluate paths chain only `.then { }`; on rejection `callback.errorOccurred()` is never called — Evaluate dialog/watches spin forever. The controller's `.onError { log.error(e) }` turns expected user errors into IDE fatal-error reports. | Add `.onError { callback.errorOccurred(...) }`; downgrade controller logging to `warn`. |
| 14 | `lang/psi/types/LuaTypeGraph.kt:236-243` + `LuaTypeManagerImpl.kt:64-66` | `Logger.error` on the designed iteration/time-limit cutoff → throws in tests, "IDE fatal error" popups in production, per snapshot build on large files. `resolveType` also logs `ProcessCanceledException` as an error before rethrowing (PCE must never be logged). | Downgrade cutoffs to `log.warn`; rethrow PCE untouched. |
| 15 | `lang/syntax/LuaAnnotators.kt:79,101-103` | Long-string/long-comment annotators index token text without bounds checks; truncated tokens mid-typing (`[==`, `--[=` at EOF) throw `StringIndexOutOfBoundsException` in the annotator. | Reuse the bounds-checked `LuaLiterals.getLuaStringDelimiterLength` / `LuaComment.getLuaCommentDelimiterLength`. |
| 16 | `run/LuaPosition.kt:43` | `FileUtil.getRelativePath(...)!!` NPEs when a breakpoint file can't be relativized against the working dir (different root/drive), killing breakpoint registration. | Null-check; fall back to absolute path. |
| 17 | `run/LuaRemoteStack.kt:17,128,137` | `checkTable()!!` NPEs on malformed stack payloads; `exprList?.exprList[index]` throws IOOBE for `local a, b = 1`. | `getOrNull` + skip non-table entries. |
| 18 | `run/LuaDebuggerController.kt:58-59,231-242,342` | Breakpoint maps are plain public `HashMap`s written from EDT, read from the connection thread — unsynchronized data race. Companion: `LuaDebugConnection.current`/`running` mutated across threads without `@Volatile`/consistent locking. | Private `ConcurrentHashMap`s; `@Volatile` + `synchronized` mutation. |
| 19 | `luacats/lang/lexer/luacats.flex:72,77` | `CODE`/`STRINGD` negated classes match `\n` — an unclosed backtick/quote greedily consumes across newlines, corrupting lexing/PSI of all following tag lines. | Exclude `\r\n` from both classes. |

### P2 — Broken or silently non-functional features, wrong results

| # | Location | Issue | Fix |
|---|----------|-------|-----|
| 20 | `lang/indexing/LuaFileBindingsIndex.kt:372-399` | Indexer records **every `PsiNamedElement`** in the subtree as a file binding — including name *usages* and nested locals — so cross-file resolution can resolve a global to a mere usage in another file. | Restrict to actual declaration elements at file scope. |
| 21 | `lang/psi/FileUserData.kt:9-21` | Snapshot cache keyed on `text.hashCode()`: stale after identical-text reparse (element-identity keys dangle), wrong on 32-bit collision, and re-hashes the whole document on every access. | Replace with `CachedValuesManager.getCachedValue(file)`. |
| 22 | `lang/lexer/lua.flex:74` + `LuaTokenTypes.kt:136` | `global` lexed as a hard keyword unconditionally; valid Lua 5.1–5.4 using `global` as an identifier gets parse errors. | Lex as IDENTIFIER and handle contextually in the parser, or gate on language level. |
| 23 | `lang/format/LuaFormatBlock.kt:300-304` | **(verified)** Rule commented "No spacing inside brackets" returns `SINGLE_SPACING` — reformat forces `t[ 1 ]`; the `SPACE_WITHIN_BRACKETS` setting below is unreachable. | Return `NO_SPACING` or defer to the `spacingBuilder` `withinPair` rule. |
| 24 | `lang/completion/LuaCrossFileCompletionProvider.kt:42,101-118` + `completion/ProximityCalculator.kt:50-62` | Uses the completion **copy** file instead of `parameters.originalFile`: (a) FileBasedIndex query scoped to the never-indexed copy → require-based cross-file phase silently returns nothing; (b) proximity "same file" (0.9) and "same directory" tiers unreachable. Also `extractRequires` creates a fresh `CachedValue` per call — zero caching. | Use `parameters.originalFile` for index/proximity work; `CachedValuesManager.getCachedValue(file) { … }`. |
| 25 | `lang/completion/LuaEnterBetweenBlockHandler.kt:45` | Guard `offset in (leaf.endOffset + 1) until terminator.startOffset` is unsatisfiable (`leaf = findElementAt(offset - 1)` ⇒ `leaf.endOffset >= offset`); COMP-08-04 never fires. | Compare against the opener leaf's end without the `+ 1`. |
| 26 | `run/LuaRunConfiguration.kt:342-349` | **(verified)** `applyEditorTo` never writes `sourcePathField` back — Source-path edits silently discarded on Apply/OK. | Add the missing assignment. |
| 27 | `run/test/LuaTestCommandLineState.kt:35-43,84-89` | Rerun Failed Tests doubly broken: (a) `createRerunFailedTestsAction` never wired into `execute()` so the button never appears; (b) the filter uses Java `Regex.escape` (`\Q…\E`) + `\|` alternation — busted's `--filter` takes Lua patterns, so it would match nothing anyway. | Wire the restart action; escape Lua magic chars; one `--filter` per failed test. |
| 28 | `analysis/luacheck/LuaCheckCommandLine.kt:42` | `args.distinct()` on the flat token list drops repeated value tokens (e.g. `--ignore 611 --max-line-length 611` loses the second `611`), corrupting the command. | Drop `distinct()` or de-dupe whole flag-value pairs. |
| 29 | `analysis/luacheck/LuaCheckInvoker.kt:35-60` | Output listener uses `reg.find` (first match) and requires trailing `\n`; multi-line chunks yield one problem, unterminated final lines yield none. | Buffer, split on newlines, `findAll` (or parse accumulated output at termination). |
| 30 | `analysis/luacheck/LuaCheckAnnotator.kt:42-45` | Luacheck runs against the on-disk file while offsets index the in-editor document — misplaced ranges for unsaved buffers; `getLineStartOffset` throws IOOBE when disk has more lines than the document (kills the pass). | Clamp against the current document; ideally feed editor text via stdin (`luacheck -`). |
| 31 | `analysis/inspections/LuaInspectionSuppression.kt:87-124` | `closeBlocks` ignores `names`: any `---@diagnostic enable: <unrelated>` closes **all** open disable blocks. | Only close blocks whose names intersect the enable's names. |
| 32 | `analysis/controlflow/LuaControlFlowBuilder.kt:110-227` | Three graph defects: (a) `instructions.lastOrNull()` as fall-through point creates spurious edges out of `return` in mixed-outcome branches; (b) pending edges leak from a branch-final compound statement to the next `elseif` condition; (c) `labelInstructions` is a flat name map — two sibling `::continue::` loops cross-wire gotos. Manifest as unreachable-code false negatives now; will bite any dataflow consumer. | Use `prevInstruction`/pending-edge mechanics; re-scope pendings per branch; resolve labels per Lua block scoping. |
| 33 | `analysis/controlflow/LuaControlFlowBuilder.kt:101,144,173` | `if`/`while`/`repeat` conditions get a node but are never descended into — no READ instructions for names in conditions; any dataflow consumer misses them. | `expr.accept(this)` on conditions. |
| 34 | `analysis/inspections/LuaUnusedLocalInspection.kt:96-107` | Write-position references count as usages — a local only ever *assigned* is considered used (false negative vs. documented "declared but never read"). | Exclude simple write targets, mirroring `LuaUndeclaredVariableInspection.isSimpleWriteTarget`. |
| 35 | `luacats/lang/doc/LuaCatsDocumentationRenderer.kt:112,135,246,266,282,367,386,434` | No HTML escaping of type text passed to `buildTypeLink` — `table<string, integer>`, `fun(x): boolean`, string-literal types break the doc popup HTML and corrupt the `psi_element://` href. | HTML-escape; hyperlink only simple identifiers, render structured types as escaped code. |
| 36 | `luacats/lang/doc/LuaCatsDocumentationRenderer.kt:502-506` (also 112,214,405-407) | `lookupParentComment` uses `parentTypes.text` — the whole list — as an index key: `@class C : A, B` looks up `"A, B"`; generic parents look up `"Base<T>"`. Inherited Fields silently never renders; bare `--- @class Parent` (not in the stub index) is also never found. | Iterate individual `ArgType` children; fall back to `LuaCatsTypeNameIndex` for bare classes. |
| 37 | `luacats/lang/doc/LuaCatsDocumentationRenderer.kt:313-316` | Alias Values section gated on `enumTagList` — the standard LuaCATS union-alias form (`---@alias X` + `---| "'v'"`) has no `@enum`, so values never render. | Gate on `typeOptionList.isNotEmpty()`. |
| 38 | `luacats/lang/psi/impl/LuaCatsLazyCommentImpl.kt:47-49` (all getters) | `PsiTreeUtil.findChildrenOfType` is recursive, diverging from the generated direct-children contract — `getDescriptionList()` also returns descriptions nested inside tags, duplicating tag descriptions in `collectDescriptionText` and skewing `isDocCommentEmpty`. | `getChildrenOfTypeAsList` (with the inner `comment` hop) for `getDescriptionList` at minimum. |
| 39 | `lang/LuaCompletionContributor.kt:187,212` (+ identifier provider ~222-235) | `addSymbolCompletions` called twice under the same guard in one provider, plus a third time via the IDENTIFIER-pattern provider — full scope walk up to 3× per completion, deduped only by lookup-element equality. | Keep one call site; fold the identifier provider in. |
| 40 | `lang/completion/GlobalSymbolRankingService.kt:216-221` | `extractFuncDeclName` takes only the base identifier from `Class:method`/`M.fn` keys — every method declaration surfaces its *receiver* as a standalone global-function candidate (including receivers that are `local` `@class` tables). | Skip decls with a method separator or property list. |
| 41 | Settings event bus (`settings/LuaProjectSettings.kt:134-151`, `project/LuaSettingsChangeListener.kt`, `tool/LuaTerminalEnvironmentService.kt:40-49`) | The `LuaSettingsChangedListener.TOPIC` wiring is entirely dead: `setTargetAndNotify`/`setProjectToolBindingAndNotify`/`setGlobalBinding` have zero callers and `LuaSettingsChangeListener` is never registered. Concrete effect: `cachedToolDirectories` is never invalidated — newly registered tools don't reach terminal/run PATH until project reopen. | Publish the topic from tool/settings mutations and register the listener — or delete the whole mechanism and invalidate directly. |
| 42 | `rocks/browser/LuaRocksActionHandler.kt:33,57`, `LuaRocksMetadataService.kt:30`, `rocks/run/LuaRocksRunConfiguration.kt:208`, `rocks/build/WorkspaceBuildRunner.kt:29` | These resolve the luarocks binary via raw settings, bypassing `LuaRocksEnvironment.resolveExecutable`/`resolveServer` (TOOL-02/ROCKS-06) — search hits the configured registry, but Install runs against luarocks.org, possibly with a different binary. | Route all invocations through `LuaRocksEnvironment` (install/remove get `withServer` too). |
| 43 | `tool/health/LuaToolHealthChecker.kt:51-54` | Health check hardcodes `--version` for every tool; LuaCov has none (the validator knows and uses `--help`) — a validly registered LuaCov fails every health pass. | Reuse `LuaToolValidator.versionFlagFor(type)`. |
| 44 | `settings/LuaApplicationSettingsConfigurable.kt:9-31` + panel/table | No `reset()`/`disposeUIResources()` — Settings Reset/Cancel never reverts; panel/table mutate the **live persisted** `LuaInterpreter` objects before Apply, so Cancel can't undo and `isModified` misreports. | Implement reset/dispose; operate on cloned elements, commit in `apply()`. |
| 45 | `rocks/VersionConflictEngine.kt:51` | Unsatisfiable detection misses equal-version strict cases: `>= 2.0` + `< 2.0` not flagged. | Also flag equal versions unless both bounds are inclusive. |
| 46 | `rocks/RockspecRunPathProvider.kt:19-21` | `luaCPath` hardcodes `?.so` (wrong on Windows: `?.dll`); reads deprecated `state.languageLevel` while `LuaRocksLibraryProvider` reads the target-derived level — two sources of truth. | Extension per `SystemInfo`; target-derived level in both. |
| 47 | `rocks/library/LuaRocksLibraryProvider.kt:33` + `project/PlatformLibraryProvider.kt:63` | `AdditionalLibraryRootsProvider`s call `VfsUtil.findFile(path, refreshIfNeeded = true)` — synchronous refresh under roots-computation read actions (asserts on recent platforms). | `refreshIfNeeded = false`; rely on `getRootsToWatch`. |
| 48 | `rocks/browser/PackageDetailPanel.kt:131-149` | Async metadata fetch has no staleness guard — selecting A (slow) then B lets A's late response overwrite B's details and re-enable the wrong buttons. | Bail in the `invokeLater` if `currentPackage !== pkg`. |
| 49 | `rocks/init/LuaRocksScaffolder.kt:73` | Instantiates a fresh `LuaRunConfigurationType()` instead of `ConfigurationTypeUtil.findConfigurationType` — configuration types must be singletons; template patching may hit a divergent template. | Look up the registered singleton. |
| 50 | `platform/target/Target.kt:76-83` | `Target.default()` documented (and used as fallback) as "Standard Lua 5.4" but resolves to the registry's *first* entry — 5.1. | Explicit `findVersion(STANDARD, "5.4")`. |
| 51 | `refactoring/LuaSafeDeleteProcessor.kt:82-95,148-163` | Multi-name `local a, b = …` elevates to `LuaAttName`, but `identifierLeafFor` has no branch for it — `ReferencesSearch` runs against the wrong node and usages are silently missed (no conflict dialog). | Add an `is LuaAttName` branch. |
| 52 | `run/LuaDebugValueParser.kt:241-249` | Numeric bracket indexing looks up only `table.named`; positional fields live in `table.indexed` — evaluating `t[1]` on `{10, 20}` yields nil. | Integer keys → `indexed[key-1]` first, fall back to `named`. |
| 53 | `run/LuaExecutionStack.kt:34-37` | (a) C-frame check compares `frame.path == "=[C]"` but path is `"[C]"` (file is `"=[C]"`) — never fires; (b) `computeStackFrames` ignores `firstFrameIndex` — duplicated frames on offset requests. | Compare the right field; `drop(firstFrameIndex)`. |
| 54 | `run/test/LuaTestOutputToEventsConverter.kt:295-297` | `findTopLevelJson` treats `'` as a string delimiter; a lone apostrophe in busted output (e.g. `print("doesn't")`) flips the scanner and can swallow the whole JSON report — no test tree. | Only `"` delimits strings in JSON. |
| 55 | `run/LuaDebuggerController.kt:99-120` + `run/LuaDebugProcess.kt:104` | Connect timeout is dead code (blocking `accept()` never reaches the sleep-count loop); combined with a non-cancellable `Backgroundable`, "Connecting to debugger" can hang forever. | `serverSocket.soTimeout` + real deadline; cancellable task. |
| 56 | `run/LuaRunConfiguration.kt:244-247` | No `checkConfiguration()`; empty stored `workingDirectory` (`""`) passed straight to the process instead of the `project.basePath` fallback the test config uses — fresh configs fail at start. | Add validation + basePath fallback. |
| 57 | `luacats/lang/doc/LuaCatsDocumentationRenderer.kt:203-207` | A local annotated only `---@type T` renders as `class T` — wrong keyword, and structured `T` feeds the unescaped link path (see #35). | Render as `local <name> : <type>`. |

### P3 — Minor / edge-case defects

| # | Location | Issue |
|---|----------|-------|
| 58 | `lang/psi/types/LuaTypeGraph.kt:37,390-408` | `compatMemo`/`visited` keyed on `LuaGraphType` while `Table` is a data class over mutable collections — hashCode changes after mutation corrupt the memo. (Resolved structurally by fixing #1 via immutability.) |
| 59 | `run/LuaLineBreakpointType.kt:42` | `getDisplayText` prints the 0-based line ("Line N" off by one). |
| 60 | `analysis/inspections/LuaInspectionSuppression.kt:126-136` | Inline `-- luacheck: ignore` covers `commentLine..commentLine+1`; real luacheck applies it to its own line only — over-suppression of the next line. |
| 61 | `analysis/inspections/LuaStandardGlobals.kt:23-31` | Lua 5.1's global `arg` missing from `DELTA_51` — flagged as undeclared under 5.1/5.0. |
| 62 | `lang/LuaCompletionContributor.kt:191-193` | `hasPrefix` via `prevVisibleLeaf is IDENTIFIER` can't detect a typed prefix (prefix merges into the dummy-identifier token); the keyword gate keys off the wrong condition. Also shadows the outer `prevLeaf`. |
| 63 | `analysis/luacheck/LuaCheckModel.kt:10-14` | `ProblemFile.hashCode()` mixes identity hash with `file.hashCode()` while `equals` compares only `file` — moot once the dead tree model is deleted (§3). |
| 64 | `rocks/browser/LuaRocksActionHandler.kt:28,53` | KDoc promises `onDone` "called on the EDT" but it runs on the task thread; current callers defensively wrap, future callers will trust the doc. |
| 65 | `command/LuaCommandLine.kt:37` + `run/console/LuaConsoleRunner.kt` | REPL cwd is the interpreter's bin directory — relative `require`/`io.open` in the console resolve against the wrong place. Use `project.basePath`. |
| 66 | `luacats/lang/lexer/luacats.flex:68` | `HIGH_ASCII=[\x80-\xff]` under `%unicode` — names with chars above U+00FF (CJK, Cyrillic) abort tag lexing. Use a Unicode letter class. |
| 67 | `luacats/lang/doc/LuaCatsDocumentationRenderer.kt:406-425` | Inheritance rendering is single-level — grandparent fields never appear. Walk the chain with a visited-set (cycle guard). |
| 68 | `analysis/inspections/LuaSuspiciousConcatenationInspection.kt:71-79` | All `Table` types deemed non-concatenable — guaranteed false positive for classes defining `__concat`. |
| 69 | `analysis/inspections/LuaUnusedLocalInspection.kt:131` | `reference?.resolve()` on poly-variant references returns null when ambiguous — usage silently dropped, risking false "unused". Use `multiResolve(false)`. |
| 70 | `rocks/browser/LuaRocksSearchCache.kt:24-32` | Cache key is the bare query — results (incl. `isInstalled` flags) survive server-setting changes and out-of-band installs for 5 min. Key on resolved server; invalidate on settings change. |
| 71 | `rocks/browser/LuaRocksPackageBrowserToolWindowFactory.kt:59,120-156` | `Alarm(POOLED_THREAD)` created without a parent Disposable (leak); `runSearch` has no stale-result guard (older slow query can overwrite a newer one). |
| 72 | `luacats/lang/syntax/LuaCatsAnnotator.kt:23-31` | The `parent is LuaCatsArgType && (class/alias tag)` special case appears unreachable per the bnf, and would mis-highlight an alias *target* if it fired. Verify with a fixture, then drop. |

---

## 2. Engineering-contract & systemic-pattern compliance

### 2.1 Rule 1 — Threading & actions

Violations cluster in pre-contract code; the newer feature code is clean.

- **Process execution under the read lock / on the EDT** is the enabling defect behind
  several P1s: `util/LuaProcessUtil.capture` has no EDT guard and its read-lock branch
  blocks `.get()` non-cancellably while holding the lock (P1 #11); the Tools configurable
  spawns validators on the EDT (P1 #12); `run/console/LuaConsoleAction.kt:19` spawns the
  Lua interpreter synchronously from `actionPerformed`; `coverage/LuaCoverageProgramRunner.kt:50-53`
  does file I/O in `doExecute` on the EDT. **Fix once at the primitive:** give
  `LuaProcessUtil` an EDT assertion and a cancellable read-lock-free path, then migrate
  callers.
- **PSI from process/pooled threads without read actions:** `analysis/luacheck/LuaCheckInvoker.kt:55-57`
  reads `psiFile.name`/`virtualFile.canonicalPath` in the process listener.
- **Synchronous VFS refresh in forbidden contexts:** P1 #3 and P2 #47.
- **Slow work in fast passes:** `lang/insight/LuaLineMarkerProvider.kt:42` resolves
  references in `getLineMarkerInfo` (belongs in `collectSlowLineMarkers`);
  `tool/health/LuaToolHealthMonitor.kt:110-143` does per-tool disk I/O and
  `File.canonicalPath` in the `AsyncFileListener` **prepare phase** for every VFS event
  batch; `rocks/LuaRocksEnvironment.kt:49-58` is documented "no I/O" but transitively does
  `File.exists()/canExecute()` per inventory entry (incl. from editor-notification EDT paths).
- **Cancellation:** luacheck's 5 s `waitFor` neither kills the process on timeout nor
  polls the indicator; `rocks/build/WorkspaceBuildRunner.kt:64-68` ignores the indicator
  during `waitFor()` (cancel leaves `luarocks make` running); the 2-minute install task is
  constructed non-cancellable.

### 2.2 Rule 3 — Immutability, `!!`, wildcard imports

- **`!!` (~40 sites, contract bans it outright).** Debugger payload parsing is the worst
  (`LuaDebugConnection` 279/281, `LuaDebuggerController` ×5, `LuaDebugValueParser` ×3,
  `LuaRemoteStack` ×2, `LuaDebugVariable` ×2, `LuaDebugValue` ×3, `LuaValue:169`,
  `LuaPosition:43`, `LuaLineBreakpointType:81`) — any malformed remote data crashes.
  Others: `lang/psi/LuaBaseElements.kt:72` (`getName()` NPEs on error-recovery nodes),
  `LuaElementFactory.kt:24`, `LuaComplexTypes.kt:14`, `LuaGraphType.kt:113`,
  `LuaNameReference.kt:76`, `LuaScopeProcessor.kt:83`, `LuaTypesVisitor.kt:571`,
  `doc/LuaDocumentationRenderer.kt:96`, `structure/LuaStructureViewTreeElement.kt:10`,
  `hint/LuaParameterInlayHintsProvider.kt:49,51`, `LuaCatsDocumentationRenderer` ×4,
  `LuaCheckNodes` ×3 + `LuaCheckAnnotator:51` + `LuaCheckInvoker:41-44`,
  `LuaInterpreterService.kt:71` (P1 #10), both settings configurables,
  `LuaInterpretersTable`, `LuaBundle.kt:28`, `LuaProjectSettings.kt:107`,
  `PlatformVersionRegistry.kt:85`, `LuaInterpreter.kt:140`.
- **Mutable global state:** `luacats/lang/syntax/LuaCatsHighlight.kt:9-26` — seven of
  eight `TextAttributesKey` singletons are `var`.
- **Wildcard imports:** `LuaControlFlowBuilder.kt:7`, `LuaShadowingVariableInspection.kt:12`,
  `LuaDeprecatedApiInspection.kt:11`, `LuaCheckSettings.kt:4`, `LuaCheckNodes.kt:17`.

### 2.3 Rule 4 — Memory & references

- `analysis/luacheck/LuaCheckModel.kt:61` — `Problem` retains a hard `PsiFile` reference
  and is populated from a process thread.
- `lang/indexing/LuaFileBindingsIndex.kt:86-94` — index-extension constructor subscribes
  to the app message bus via `simpleConnect()` (never disposed) for `INDEX_PATTERNS_CHANGED`
  — the TODO-pattern event, copy-pasted from the platform TodoIndex and irrelevant here;
  every TODO-pattern edit triggers a full needless rebuild. Delete the `init` block.
- `rocks/browser/LuaRocksPackageBrowserToolWindowFactory.kt:59` — parentless `Alarm` (P3 #71).
- Dead `LuaBindings.kt` data classes retain hard `PsiElement`/`Project` refs (moot after §3).

### 2.4 Rule 5 — Tripwires (function size, mixed concerns)

- `lang/psi/types/LuaTypesVisitor.kt` — `visitFunctionBody` (~70 logic lines) and
  `visitFuncCall` (~75) mix raw PSI traversal with graph orchestration.
- `lang/insight/hint/LuaParameterInfoHandler.kt:50-214` — `resolveCandidates` is ~160
  lines chaining four resolution strategies; the `LuaFuncDecl`/`LuaLocalFuncDecl` branches
  are near-duplicates.

### 2.5 Systemic patterns (fix once, not per-site)

1. **Mutable type identity.** `LuaGraphType.Table` is a mutable data class used as a
   shared static singleton (P1 #1) *and* as a hash key (P3 #58). Making graph types
   immutable(-by-copy) eliminates the whole class of heisenbugs.
2. **Hand-rolled caching instead of platform idioms.** `FileUserData` (P2 #21); no
   `ResolveCache` in `LuaNameReference.multiResolve` — Phase-2 iterates every Lua file's
   bindings per unresolved name per highlighting pass; per-call `CachedValue` creation
   (P2 #24). `CachedValuesManager` + `ResolveCache` solve staleness and performance at once.
3. **Copy-paste drift** — duplicates that have already diverged:
   - Scope walk ×3: `LuaNameReference.multiResolve:43-73` ≡ `LuaResolveUtil.scopeCrawlUp`
     (canonical) ≡ `LuaCompletionContributor.addSymbols:66-97`.
   - Require extraction ×2: `LuaNameReference.extractRequiresFromStatement:202-246` ≡
     `LuaFileBindingsIndexer:314-358` (~45 identical lines; near-dupe of
     `LuaTypesVisitor.extractModuleName`).
   - Module-file resolution ×2: `LuaRequireReference.resolve:19-49` vs
     `LuaTypeManagerImpl.doResolveModule:107-142` — **already diverged on the refresh
     flag; the divergence is P1 #3.**
   - luarocks command-building ×5 (`LuaRocksActionHandler`, `LuaRocksMetadataService`,
     `LuaRocksSearchService`, `WorkspaceBuildRunner`, `LuaRocksRunConfiguration`) — a
     single `LuaRocksEnvironment.command(project, args)` helper also fixes P2 #42
     structurally.
   - Run-config boilerplate: ~150 lines duplicated between `LuaRunConfiguration` and
     `LuaTestRunConfiguration` (StoredProperty blocks, interpreter/env property impls,
     verbatim LUA_PATH/LUA_CPATH fallback also in `LuaTestCommandLineState:133-145`).
   - `LuaTypeAssignabilityInspection` ≡ `LuaReturnTypeMismatchInspection` except one
     predicate — extract `reportTypeErrors(holder, predicate)`.
   - `tool/LuaToolValidator.detectLuaVersion:97-102` re-runs the same subprocess
     `extractVersion` just ran; parse both patterns from one output.
   - `LuaCatsDocumentationRenderer` — five signature builders repeat identical
     deprecated/`<pre>` boilerplate; param and field rows emit the same markup.
4. **Known-idiom migration** (CLAUDE.md Lessons Learned): the SYNTAX-07-07 method-chain
   provider (`hint/LuaMethodChainInlayHintProvider.kt:133-141`) still does manual
   `"$class$sep$method"` stub-index probing — replace with
   `LuaTypeManager.resolveType(className)` + `LuaClassType.resolveMember(name)`.
5. **Performance patterns:**
   - `lang/psi/LuaRecursiveVisitor.kt:22-28` — `findChildrenOfType(LuaCatsComment)` per
     visited element makes every type-graph build O(n²); likely the dominant snapshot cost.
   - `analysis/controlflow/LuaControlFlow.kt:17-34` — fresh BFS per `isReachable` × per
     instruction = O(N²) per highlighting pass. Compute the reachable set once per flow.
   - `hint/LuaTypeInlayHintProvider.kt:72` — `element.text == ")"` materializes subtree
     text per node; use an element-type check. Same `node.text` pattern in
     `LuaFoldingBuilder.foldIfStatement`/`foldStatementWithEnd`/`foldFunctionDecl`
     (use `textContains('\n')`/document line numbers).
   - `LuaDebugConnection.kt:196-231` — 50 ms busy-poll on `reader.ready()`; no `soTimeout`.
   - `GlobalSymbolRankingService.kt:82,153` — `StubIndex.getAllKeys` over two indexes per
     completion invocation; cache per `PsiModificationTracker.MODIFICATION_COUNT`.
   - `LuaCatsLazyCommentImpl` — 26 getters × full recursive subtree walk each;
     `isDocCommentEmpty` triggers all of them. Collect once, cache.
   - `LuaCatsLexer` token map rebuilt per lexer instance (per editor/pass) — move to
     companion.
   - `LuaTypeNodes.kt:71-116` — `VariableElement.write/read` re-traverse the graph per
     access; `nodes` getter copies the list per access.
   - `LuaCheckAnnotator` dedupes the same list twice (`doAnnotate` and `apply`).
   - `LuaFoldingBuilder` emits useless one-line fold regions for single-line comments and
     small tables; `foldBlocks` (repeat) lacks the multiline check its siblings have.
   - Member completion builds a full snapshot incl. `checkTypes` (5 s wall-clock bound)
     per completion session (`LuaCompletionContributor.kt:261`).
   - `indexing/LuaDescriptionIndex.kt:88` — `"|"`-joined values grow unboundedly in
     doc-heavy projects.
6. **Error-reporting hygiene.** `Logger.error` for designed cutoffs and expected user
   errors (P1 #13, #14) produces fatal-error popups; PCE must never be logged. Silent
   swallowing at the other extreme: `LuaCheckInvoker.kt:25` drops `ExecutionException`
   with no log — a missing luacheck binary looks identical to "no problems"; exit codes
   ≥ 3 (crash/invalid args) also read as clean.
7. **Robustness gaps worth one hardening pass on the debugger:** hard-coded port 8172
   with no config/recovery (`LuaDebuggerController.kt:52`), the unexplained
   `Thread.sleep(1000)` post-connect race paper-over (`:130-134`), the dead
   `InterruptedException` catch around `accept()`, and busted mode buffering the entire
   run output with events only at termination (stderr re-emitted as stdout,
   `LuaTestOutputToEventsConverter.kt:142-170`).

---

## 3. Dead code to remove

All confirmed zero-reference by repo-wide search (production and, unless noted, tests).
~1,000+ lines total; removal is pure win and shrinks the future review surface.

### Whole files
- `lang/insight/LuaBindings.kt` (267 lines) — `Binding`, `Reference`, `Scope`,
  `DottedElements`, `DelayedBinding`, `LuaBindings`, `LuaImports`, `getFuncNameElements`,
  `getVarElements`: zero users. Only reference is the unused import in
  `LuaParameterInfoHandler.kt:8` — remove both.
- `analysis/luacheck/LuaCheckNodes.kt` (179 lines) — `ProblemTreeBuilder`,
  `ProblemTreeStructure`, all node classes: no tool window registers them. With it:
  `ProblemSummary`, `ProblemFile`, `ProblemFilter` in `LuaCheckModel.kt:5-49`, and the
  write-only/never-set `Problem.name`/`code`/`absFile`/`psiFile` fields (the
  `LuaCheckAnnotator.kt:50` tooltip branch reading `name` is dead too).
- `command/LuaRunProfile.kt` — `LuaRunProfile`, `LuaRunProfileState`, plus
  `LuaCommandLine.newLuaDefaultInterpreterCommandLine`: zero production usages (only
  `LuaRunProfileTest`). Delete file + test, or wire up if intended.
- `run/InputStreamReaderExt.kt` — the `readLine()` extension is never selected
  (`LuaDebugConnection.reader` is a `BufferedReader`, so the member wins). Delete
  extension + its test.

### Registered no-ops (actively cost cycles)
- `lang/syntax/LuaAnnotators.kt:150-170` — `LuaLocalBindingsAnnotator`,
  `LuaGlobalBindingsAnnotator`, `LuaGotoAnnotator` are empty but registered in
  `plugin.xml:144-150`: invoked per PSI element per highlighting pass. Remove classes +
  registrations.
- `lang/format/LuaEnterHandlerDelegate.kt:18-46` — `preprocessEnter` computes `lineText`
  then returns `Continue` on both branches: a no-op. Delete the body.
- `lang/indexing/LuaFileBindingsIndex.kt:86-94` — the `INDEX_PATTERNS_CHANGED`
  subscription (TodoIndex copy-paste; irrelevant event, undisposed connection, triggers
  needless full rebuilds).

### Dead declarations / branches
- `lang/psi/types/LuaTypeGraph.kt:142-176` — `collectGenerics` and `substitute` local
  functions in `doInstantiateGeneric`: defined, extensively commented, never called.
- `analysis/controlflow/LuaInstruction.kt:25-28` — `LuaBranchInstruction` never
  instantiated (or: use it for condition nodes when fixing P2 #33).
- `lang/indexing/LuaIndex.kt:38-52` — `Dotted<T>`.
- `lang/indexing/LuaFileBindingsIndex.kt:151-154` — `PackageFileBindings`.
- `lang/psi/LuaPsiUtils.kt:80-98` — `processChildDeclarationsS`.
- `lang/psi/LuaPsiImplUtil.kt:80-106` — `prevSiblingSkipWhitespace`, `prevSiblingSkipNewline`.
- `lang/LuaCompletionContributor.kt:64` — `prefix` param never passed non-null (dead
  filter); `:325` unused `prevType`.
- `lang/lexer/LuaLexer.kt:55/69` — duplicate `IDENTIFIER` map entry.
- `lang/lexer/lua.flex:160` — unreachable `\\'` rule (shadowed by line 159).
- `lang/lexer/LuaTokenTypes.kt:111,119` — `WITH`, `CONTINUE` tokens never emitted.
- `lang/insight/LuaFoldingBuilder.kt:142-144` — the `end is PsiWhiteSpace` decrement can
  never trigger (loop already advanced past whitespace).
- `lang/completion/GlobalSymbolRankingService` — `GlobalSymbolCompletion.isImported`
  hard-coded `false` at both construction sites; the `!symbol.isImported` check in
  `LuaCrossFileCompletionProvider.buildGlobalLookupElement` is constant.
- `luacats/lang/lexer/luacats.flex:109,187-192,80` — `TAG_OVERLOAD` state block
  unreachable (`@overload` routes to `TAG_TYPE`); `COMMENT_END` declared, no rules.
- `luacats/lang/lexer/LuaCatsLexer.kt:22-33` — `tokenSet`;
  `luacats/.../LuaCatsTokenTypes.kt:22-33` — `LUACATS_TOKENS`;
  `luacats/.../LuaCatsSyntax.kt:16-31` — `STRINGS`, `KEYWORDS`: zero references.
- `run/LuaDebugConnection.kt:120-131` + `run/LuaDebuggerController.kt:141-144` —
  `DebugCommandKind.EXIT`/`DONE` and `controller.terminate()` never invoked from
  production (`LuaDebugProcess.stop()` just destroys the process). Either call
  `terminate()` from `stop()` for a graceful exit, or remove.
- `run/LuaDebuggerController.kt:111-112` — dead `catch (InterruptedException)` around
  `accept()` (never thrown there).
- `coverage/LuaCoverageAnnotator.kt:17-27` — both overrides delegate to `super`; delete.
- `rocks/publish/RockUploadCommand.kt:28` — `force` parameter never passed `true` by
  `PublishRockAction`; the documented "re-upload with `--force`" flow doesn't exist
  (wire up or remove).
- Dead settings-event API (see P2 #41): `LuaProjectSettings.setTargetAndNotify` /
  `setProjectToolBindingAndNotify`, `LuaToolManager.setGlobalBinding`/`getGlobalBinding`,
  `project/LuaSettingsChangeListener.kt` — either wire up or delete alongside the fix.
- Redundant nested write actions: `LuaUnreachableCodeInspection.kt:133-141` and
  `LuaGlobalCreationInspection.kt:89-96` wrap `applyFix` mutations in
  `WriteCommandAction.runWriteCommandAction` although quick fixes already run inside a
  write command (also an intention-preview hazard).
- Two equivalent snapshot entry points: `LuaTypesVisitor.getTypes(element)` vs
  `LuaTypesSnapshot.forFile(file)` — same cache lookup, callers split inconsistently;
  keep one.
- `rocks/browser/LuaRocksSearchService.kt:73-86` — `withServer` applied to the purely
  local `luarocks list` (noise); doc claims lower-cased keys but names are stored as-is.

---

## Suggested execution order

1. **P1 crash/corruption fixes** — small and isolated; each is an independent
   plan-bug/implement-bug candidate.
2. **Debugger hardening** as one MAINT feature: framing/charset (#5), connect/breakpoint
   failure paths (#4, #55), error propagation (#13), state sync (#18), Run to Cursor (#6),
   `!!` removal in payload parsing (§2.2).
3. **Dead-code sweep** (§3) — mechanical, zero risk, do early to shrink later diffs.
4. **Systemic passes** as separate MAINT features: immutable graph types (§2.5.1),
   platform caching (`ResolveCache`/`CachedValuesManager`, §2.5.2), dedup (§2.5.3),
   `LuaProcessUtil` discipline (§2.1). These overlap naturally with the existing Wave 12
   backlog and should be cross-referenced when planned.
