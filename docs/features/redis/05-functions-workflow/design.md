---
id: "REDIS-05-DESIGN"
title: "Technical Design"
type: "design"
status: "todo"
parent_id: "REDIS-05"
folders:
  - "[[features/redis/05-functions-workflow/requirements|requirements]]"
---

# Technical Design: REDIS-05 — Redis Functions Workflow

Realizes every acceptance criterion in [requirements.md](requirements.md). REDIS-05 is a thin
layer on already-planned seams: it **consumes** REDIS-01's connection/run-config/RESP stack and
REDIS-04's call-site/spec/ambient-global seams **by name** and adds only the Functions-specific
surface (shebang library model, `register_function` stubs, the FCALL deploy mode, and the
functions panel). Every existing symbol is grounded to `file:line`; every net-new symbol is
flagged **[NEW]** and confirmed absent via grep (see risks-and-gaps "NEW Symbols" ledger).

Package root is `net.internetisalie.lunar` (verified: `src/main/kotlin/net/internetisalie/lunar/`).
New code lives under the existing REDIS-01 package tree `net.internetisalie.lunar.redis.*`
(`redis.run`, `redis.functions`, `redis.functions.ui`) plus one inspection under
`net.internetisalie.lunar.analysis.redis` (mirroring REDIS-04's inspection package). Resource
edits land under `src/main/resources/runtime/redis/{redis-7}/` (and the REDIS-04-created
`redis-5/redis-6` dirs, which do not receive Functions stubs — Functions are Redis 7+ only).

## 1. Architecture Overview

### Current State (Prior Art in This Repo — grounded)

- **Shebang lexing already exists.** `lua.flex:102` (`"#!" { yybegin(XSHORTCOMMENT); return
  SHEBANG; }`) emits a `SHEBANG` token for `#!`, then the remainder of the line lexes as
  `SHORTCOMMENT` (`lua.flex:188-193`, the `<XSHORTCOMMENT>` state returns `SHORTCOMMENT` until
  the newline). `SHEBANG` is a real element type (`LuaTokenTypes.kt:44`,
  `LuaElementTypes.java:121`), mapped in `LuaLexer.kt:84`, and **already highlighted** as
  `LuaHighlight.COMMENT` (`LuaSyntaxHighlighter.kt:40`). It is declared in the grammar
  (`lua.bnf:33` `SHEBANG = '#!'`). So AC-1's "lexes/parses cleanly (no error elements) and is
  rendered distinctly" is **already true** — REDIS-05 adds no lexer/grammar change, only a
  static detector (§3.2) and a verification test (TC-SHB-1). `LuaImportInserter.kt:69-85` is the
  reference for walking the leading shebang/comment leaves
  (`PsiTreeUtil.getDeepestFirst(file)` + `elementType == LuaElementTypes.SHEBANG`).
- **Redis stubs.** `src/main/resources/runtime/redis/redis-7/redis.lua:1-60` declares the
  `redis` table with `call`/`pcall`/`log`/… but **no `register_function`** (grep
  `register_function` in `src/main/resources/runtime/` → 0 hits). `global.lua:1-8` declares
  `KEYS`/`ARGV` as `---@type string[]`. The LuaCATS `fun(...)` function-signature type is
  grammar-supported: `functionSignatureType ::= 'fun' '(' functionSignatureArguments? ')'
  functionSignatureReturnType?` (`luacats.bnf:180`), PSI
  `LuaCatsFunctionSignatureType` (`gen/.../luacats/lang/psi/LuaCatsFunctionSignatureType.java`)
  — so `fun(keys: string[], args: string[]): any` is a valid, already-parsed stub annotation.
  `@overload` (`luacats.bnf:134-139`, `LuaCatsOverloadFunctionSignature.java`) supports the
  two-form (positional vs table) declaration.
- **Run configuration (REDIS-01).** `LuaRedisRunConfiguration` /
  `LuaRedisRunConfigurationOptions` / `LuaRedisExecMode { EVAL, EVALSHA, FCALL }`
  (REDIS-01 design §2.8) — `FCALL` is the **reserved slot** REDIS-05 enables (REDIS-01
  risks §"Public Seams": "`LuaRedisExecMode.FCALL` — reserved enum slot; REDIS-05 enables it").
  The options-persistence idiom is `string()` `StoredProperty` delegates and the `List<String>
  ↔ '\n'-joined string` bridge for `keysRaw`/`argvRaw` (REDIS-01 §2.8). `checkConfiguration`
  throws `RuntimeConfigurationException` (grounded `run/test/LuaTestRunConfiguration.kt:257-264`).
- **Script execution (REDIS-01).** `LuaRedisScriptExecutor` (REDIS-01 §3.8) selects the command
  by `(execMode, readOnly)` and sends it via `RespClient.command(...)` (REDIS-01 §2.3). REDIS-05
  adds a sibling `LuaRedisFunctionExecutor` reusing the same `RespClient` seam and the same
  `numkeys`/keys/argv marshalling (REDIS-01 §3.8 EVAL row).
- **RESP client & console (REDIS-01).** `RespClient.command(vararg args: String): RespValue`
  (§2.3), `RespValue` sealed model (§2.1), `RespReplyTreeConsole.showReply/showError` (§2.6),
  `LuaRedisConnectionSettings.getInstance(project).connections()` / `findById(id)` (§2.5).
- **REDIS-02 debug runner.** `LuaRedisDebugRunner : GenericProgramRunner<RunnerSettings>`
  (REDIS-02 design §2.1) whose `canRun` returns `executorId == DefaultDebugExecutor.EXECUTOR_ID
  && runProfile is LuaRedisRunConfiguration`. Pattern grounded on `run/LuaDebugRunner.kt:53-56`.
- **REDIS-04 language seams.** `RedisCallSiteMatcher.match(anchor): RedisCallSite?` and
  `RedisCallSite(funcCall, nameLiteral, commandName, argCount, namespace, member)` (REDIS-04
  §2.10); `LuaInspectionSuppression.isSuppressed(ref, name, diagnosticId)`
  (`analysis/inspections/LuaInspectionSuppression.kt:43`); the target-gating helper
  `isRedisTarget(project)` and stub-declared-global ambient seam (REDIS-04 §7 "Reusable seam for
  REDIS-05"). Inspection pattern: `LocalInspectionTool` + `buildVisitor` → `LuaVisitor`
  overriding `visitNameRef` (`analysis/inspections/LuaDeprecatedApiInspection.kt:18`).
- **Argument PSI.** `LuaFuncCall.nameAndArgsList` (`gen/.../LuaFuncCall.java:11`) →
  `LuaNameAndArgs.args` (`LuaNameAndArgs.java:11`) → `LuaArgs.exprList`
  (`LuaArgs.java:10`, `LuaExprList.exprList: List<LuaExpr>` `LuaExprList.java:11`) or
  `LuaArgs.tableConstructor` (`LuaArgs.java:13`). Table entries: `LuaTableConstructor.fieldList`
  → `LuaField.identifier` (key) + `LuaField.exprList` (value) (`LuaField.java:10-14`). Literal
  strings via `LuaTerminalExpr.string` (`LuaTerminalExpr.java:14`), the exact accessor
  `LuaRequireReferenceContributor.kt:37` uses.
- **Tool window & panel.** `LuaRocksToolWindowFactory : ToolWindowFactory, DumbAware`
  (`rocks/ui/LuaRocksToolWindowFactory.kt:14`) registered via `<toolWindow …
  factoryClass=…>` (`plugin.xml:68-73`); `DependencyTreePanel(project) : JPanel(BorderLayout())`
  (`rocks/ui/DependencyTreePanel.kt:35`) is the reference panel: a `Tree`/toolbar built with
  `com.intellij.ui.treeStructure.Tree`, `ContentFactory.getInstance().createContent`,
  refresh on a pooled thread published to the EDT, holding only `Project` (no PSI retention).
- **Notifications / write actions.** `NotificationGroupManager` group
  `notification.group.lunar.tools` (`plugin.xml:554`); confirmation via
  `com.intellij.openapi.ui.Messages.showYesNoDialog` (grounded platform API).

**Why insufficient**: `redis.register_function` is unresolved under any target; there is no
library-file model, no FCALL execution path (the slot is rejected in `checkConfiguration`), no
KEYS/ARGV-in-library inspection, and no functions panel.

### Target State

```
runtime/redis/redis-7/redis.lua   [EDIT]  + register_function (@overload two forms)
LuaRedisFunctionLibrary [NEW]  (static model: detect(file) name; registeredNames(file))
  ├─ LuaRedisFunctionKeysInspection [NEW]  (KEYS/ARGV WARNING inside a library file — AC-3)
  └─ consumed by:
LuaRedisRunConfiguration [REDIS-01 §2.8, EDIT]  — FCALL fields + FCALL enabled
  ├─ LuaRedisFunctionExecutor [NEW]  (FUNCTION LOAD [REPLACE] + FCALL/FCALL_RO — AC-4/5)
  ├─ checkConfiguration [EDIT]  — function-name validation + no-writes hint (AC-6/5)
  └─ LuaRedisDebugRunner.canRun [REDIS-02, EDIT]  — gate execMode != FCALL (AC-9)
LuaRedisFunctionsToolWindowFactory [NEW]  →  LuaRedisFunctionsPanel [NEW]
  ├─ LuaRedisFunctionsController [NEW]  (FUNCTION LIST/DELETE/LOAD over RespClient — AC-7)
  ├─ LuaRedisFunctionListParser [NEW]  (RespValue → RedisLibraryEntry list — AC-7)
  └─ LuaRedisFunctionDrift [NEW]  (local vs library_code sha — AC-8)
```

## 2. Core Components

### 2.1 (resource edit) `runtime/redis/redis-7/redis.lua` — `redis.register_function` **[EDIT]**
- **Responsibility**: declare `redis.register_function` in both call forms so the existing type
  engine resolves it and types the callback (AC-2). No Kotlin.
- **Threading**: n/a (static bundled resource; read via the existing library-root mechanism).
- **Collaborators**: `LuaLibraryProvider`, `RuntimeLibraryProvider`, the type engine (all
  grounded in REDIS-04 §1). `fun(...)` and `@overload` are grammar-supported (§1).
- **Change** (appended to `runtime/redis/redis-7/redis.lua`; **only** the `redis-7` dir — Functions
  are Redis 7+; `redis-5`/`redis-6`, created by REDIS-04 §2.1, do **not** get this member):
  ```lua
  ---Registers a Redis Function callback. Positional form: (name, callback).
  ---The callback receives the keys and args arrays (NOT the KEYS/ARGV globals).
  ---@param name string
  ---@param callback fun(keys: string[], args: string[]): any
  ---@overload fun(spec: { function_name: string, callback: fun(keys: string[], args: string[]): any, flags?: string[], description?: string })
  function redis.register_function(name, callback) end
  ```
  The primary signature is the positional `(name, callback)`; the `@overload` is the table form.
  Both callback types use the grammar's `fun(keys: string[], args: string[]): any`. Under REDIS-03,
  `server.register_function` is inherited automatically by the `---@class server : redis` stub
  (REDIS-03 §3.2) — **no** separate authoring (AC-2/parity; a `server.lua` copy of this member is
  the documented fallback only if inheritance hover-doc fidelity is insufficient, mirroring
  REDIS-03 §2.4's inheritance-vs-copy decision).

### 2.2 `net.internetisalie.lunar.redis.functions.LuaRedisFunctionLibrary` **[NEW]**
- **Responsibility**: the single static-analysis source of truth for a Function library file:
  detect the `#!lua name=<lib>` shebang and statically collect registered function names.
  Consumed by the inspection (§2.3), the run-config validation (§3.6/§3.7), and the panel drift
  check (§3.10).
- **Threading**: pure PSI reads (callable inside read actions / inspections). No I/O, no retained
  heavy refs.
- **Collaborators**: `LuaFile` (`lang/psi/LuaFile.kt`), `LuaElementTypes.SHEBANG`,
  `RedisCallSiteMatcher` (REDIS-04 §2.10) for the registration scan, `LuaTerminalExpr.string`,
  `LuaTableConstructor`/`LuaField`.
- **Key API**:
  ```kotlin
  data class RegisteredNames(val names: Set<String>, val hasDynamic: Boolean)
  object LuaRedisFunctionLibrary {
      val SHEBANG_NAME = Regex("""^#!\s*lua\s+name=([A-Za-z0-9_]+)""")   // §3.2
      fun detect(file: PsiFile): String?              // library name, or null (§3.2)
      fun isLibrary(file: PsiFile): Boolean = detect(file) != null
      fun registeredNames(file: PsiFile): RegisteredNames    // §3.7
  }
  ```

### 2.3 `net.internetisalie.lunar.analysis.redis.LuaRedisFunctionKeysInspection` **[NEW]**
- **Responsibility**: under a Redis 7+/Valkey target **and** inside a Function library file, flag
  every `KEYS`/`ARGV` name reference as EVAL-only (AC-3). Silent in non-library files (REDIS-04's
  ambient typing still applies there) and off Redis.
- **Threading**: local inspection (`buildVisitor` in a platform-managed read action; no I/O).
- **Collaborators**: `LuaRedisFunctionLibrary.isLibrary` (§2.2), `LuaProjectSettings`
  (`settings/LuaProjectSettings.kt:90` `getTarget()`), the REDIS-04 target helper pattern
  `isRedisTarget` (REDIS-04 §7), `LuaInspectionSuppression` (§43), `LuaVisitor`, `LuaNameRef`.
- **Key API**:
  ```kotlin
  class LuaRedisFunctionKeysInspection : LocalInspectionTool() {
      override fun getShortName(): String = "LuaRedisFunctionKeys"
      override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor // §3.4
  }
  ```
- **Registration**: `<localInspection language="Lua" shortName="LuaRedisFunctionKeys"
  level="WARNING">` (§7), plus `inspectionDescriptions/LuaRedisFunctionKeys.html` (existing
  convention `src/main/resources/inspectionDescriptions/`).

### 2.4 `net.internetisalie.lunar.redis.run.LuaRedisRunConfiguration` **[EDIT, REDIS-01 §2.8]**
- **Responsibility**: extend REDIS-01's config with the FCALL-mode fields and enable the reserved
  `FCALL` slot (AC-4). This is the REDIS-01 amendment A1 (§7).
- **Threading**: EDT (editor/settings); `getState` selects the profile state.
- **Change** — three new `string()` `StoredProperty` fields on `LuaRedisRunConfigurationOptions`
  (same delegate the repo's run configs use, REDIS-01 §2.8), and the matching bridged getters on
  `LuaRedisRunConfiguration`:
  ```kotlin
  // LuaRedisRunConfigurationOptions [EDIT] — appended fields
  var functionName: String?    // string("")     — FCALL target function
  var replaceOnLoad: String?   // string("true") — "true"|"false" → FUNCTION LOAD REPLACE
  var deployOnly: String?      // string("false")— "true" → load without FCALL (valid deploy run)
  // LuaRedisRunConfiguration [EDIT] — bridged getters/setters (Boolean/String)
  var functionName: String?; var replaceOnLoad: Boolean; var deployOnly: Boolean
  ```
  `execMode == FCALL` is now **accepted** (the REDIS-01 §3.7 step-3 rejection "FCALL mode is not
  available until REDIS-05" is removed; replaced by §3.6 validation here). `getState` returns
  `LuaRedisRunProfileState` (REDIS-01 §2.11) unchanged — the state dispatches to
  `LuaRedisFunctionExecutor` when `execMode == FCALL` (§2.6) instead of `LuaRedisScriptExecutor`.
  The settings editor `LuaRedisSettingsEditor` (REDIS-01 [NEW]) gains a FCALL section (function
  name field, REPLACE + deploy-only checkboxes) shown when mode == FCALL — this is a UI edit,
  no new class.

### 2.5 `net.internetisalie.lunar.redis.run.LuaRedisFunctionExecutor` **[NEW]**
- **Responsibility**: the FCALL-mode analogue of `LuaRedisScriptExecutor` (REDIS-01 §3.8):
  `FUNCTION LOAD [REPLACE]` then optionally `FCALL`/`FCALL_RO` (AC-4/AC-5). Splits work into
  ≤30-line helpers (contract §3).
- **Threading**: called from `LuaRedisRunProfileState` on the session `childScope` pooled
  coroutine (REDIS-01 §2.11); `RespClient.command` is `suspend`. No EDT I/O.
- **Collaborators**: `RespClient` (REDIS-01 §2.3), `LuaRedisRunConfiguration` (§2.4),
  `RespValue` (REDIS-01 §2.1).
- **Key API**:
  ```kotlin
  class LuaRedisFunctionExecutor {
      suspend fun execute(client: RespClient, config: LuaRedisRunConfiguration, body: String): RespValue // §3.5
  }
  ```

### 2.6 `LuaRedisRunProfileState` FCALL dispatch **[EDIT, REDIS-01 §2.11]**
No new class. REDIS-01 §2.11's `execute` reads the script `body` and calls the executor. The single
edit: choose `LuaRedisFunctionExecutor` (§2.5) when `config.execMode == FCALL`, else the existing
`LuaRedisScriptExecutor`. The reply is rendered by the existing `RespReplyTreeConsole` (REDIS-01
§2.6). This is amendment A1 (§7).

### 2.7 `net.internetisalie.lunar.redis.functions.LuaRedisFunctionListParser` **[NEW]**
- **Responsibility**: decode a `FUNCTION LIST [WITHCODE]` `RespValue` reply into a typed model
  (AC-7/AC-8). Pure function on the REDIS-01 `RespValue` sealed type.
- **Threading**: pure; run on the panel's pooled coroutine, never EDT.
- **Collaborators**: `RespValue` (REDIS-01 §2.1: `Array`, `Map`, `Bulk`, `Simple`).
- **Key API**:
  ```kotlin
  data class RedisFunctionEntry(val name: String, val flags: Set<String>)
  data class RedisLibraryEntry(
      val name: String,
      val functions: List<RedisFunctionEntry>,
      val libraryCode: String?,   // present only with WITHCODE (AC-8); else null
  )
  object LuaRedisFunctionListParser {
      fun parse(reply: RespValue): List<RedisLibraryEntry>   // §3.8
  }
  ```

### 2.8 `net.internetisalie.lunar.redis.functions.LuaRedisFunctionDrift` **[NEW]**
- **Responsibility**: compare a server `library_code` to a local file body → a drift verdict (AC-8).
- **Threading**: pure (sha1 over normalized bytes); no I/O beyond the strings passed in.
- **Collaborators**: `java.security.MessageDigest` (JDK, the same `SHA-1` idiom REDIS-01 §3.8 uses
  for `sha1Hex`).
- **Key API**:
  ```kotlin
  enum class DriftStatus { IN_SYNC, DRIFTED, UNKNOWN }
  object LuaRedisFunctionDrift {
      fun compare(serverCode: String?, localBody: String): DriftStatus   // §3.10
  }
  ```

### 2.9 `net.internetisalie.lunar.redis.functions.LuaRedisFunctionsController` **[NEW]**
- **Responsibility**: the panel's server-facing operations over a chosen connection's `RespClient`:
  list, delete, deploy (LOAD REPLACE). `Disposable` (owns the transient client per operation).
- **Threading**: every method is `suspend`, run on the panel's coroutine (pooled); results
  marshalled to the panel via `withContext(Dispatchers.EDT)` by the caller (§2.10).
- **Collaborators**: `RespClient.open(connection, timeouts)` / `command` (REDIS-01 §2.3),
  `LuaRedisConnectionSettings.findById` (REDIS-01 §2.5), `LuaRedisFunctionListParser` (§2.7),
  `LuaRedisFunctionDrift` (§2.8).
- **Key API**:
  ```kotlin
  class LuaRedisFunctionsController(private val project: Project) {
      suspend fun list(connection: LuaRedisServerConnection, withCode: Boolean): List<RedisLibraryEntry> // §3.9
      suspend fun delete(connection: LuaRedisServerConnection, libraryName: String): RespValue           // §3.9
      suspend fun deploy(connection: LuaRedisServerConnection, libraryBody: String): RespValue           // §3.9
  }
  ```

### 2.10 `net.internetisalie.lunar.redis.functions.ui.LuaRedisFunctionsPanel` **[NEW]** + factory
- **Responsibility**: the "Redis Functions" tool-window panel — a connection selector, a
  libraries/functions tree with flag + drift glyphs, and per-library Deploy/Delete actions with a
  confirmation dialog (AC-7/AC-8). Mirrors `DependencyTreePanel` (`rocks/ui/DependencyTreePanel.kt:35`).
- **Threading**: Swing on EDT; `LuaRedisFunctionsController` calls on the panel's
  `LunarCoroutineScopeService.getInstance(project).scope`
  (`util/LunarCoroutineScopeService.kt:19`) child scope; model published with
  `withContext(Dispatchers.EDT)`. Holds only `Project` (no PSI/VFS fields — contract §4).
- **Collaborators**: `com.intellij.ui.treeStructure.Tree` + `DefaultTreeModel` (grounded, used by
  `DependencyTreePanel`), `ContentFactory` (`DependencyTreePanel` pattern),
  `com.intellij.openapi.ui.Messages.showYesNoDialog` (confirmation), `LuaRedisConnectionSettings`
  (connection dropdown), `LuaRedisFunctionsController` (§2.9).
- **Key API**:
  ```kotlin
  class LuaRedisFunctionsToolWindowFactory : ToolWindowFactory, DumbAware {
      override fun createToolWindowContent(project: Project, toolWindow: ToolWindow)  // adds one LuaRedisFunctionsPanel content
  }
  class LuaRedisFunctionsPanel(private val project: Project) : JPanel(BorderLayout()) {
      fun refresh()   // reloads FUNCTION LIST WITHCODE for the selected connection (§3.9)
  }
  ```
- **Registration**: `<toolWindow id="Redis Functions" anchor="bottom" secondary="true"
  icon="net.internetisalie.lunar.lang.LuaIcons.ROCKET"
  factoryClass="…redis.functions.ui.LuaRedisFunctionsToolWindowFactory"/>` (§7).

## 3. Algorithms

### 3.1 Shebang recognition (AC-1) — no lexer change
The lexer already emits `SHEBANG` + `SHORTCOMMENT` (§1). AC-1 is a **verification only**
requirement plus the detector in §3.2. TC-SHB-1 asserts: parse `#!lua name=mylib\n<code>`, walk
for `PsiErrorElement` via `PsiTreeUtil.findChildOfType(file, PsiErrorElement::class.java)` → null;
the deepest-first leaf is `SHEBANG`; its next non-whitespace leaf is a `SHORTCOMMENT` whose text is
`lua name=mylib`; and the `SHEBANG` range maps to `LuaHighlight.COMMENT` in `LuaSyntaxHighlighter`
(distinct render).

### 3.2 Library detection (`LuaRedisFunctionLibrary.detect`)
- **Input → Output**: `PsiFile` → library name `String?`.
- **Steps**:
  1. `if (file !is LuaFile) return null`.
  2. Find the leading shebang: `var leaf = PsiTreeUtil.getDeepestFirst(file)`; walk
     `PsiTreeUtil.nextLeaf` while the leaf is whitespace/comment (mirroring
     `LuaImportInserter.isHeaderElement`, `LuaImportInserter.kt:84`); stop at the first
     `elementType == LuaElementTypes.SHEBANG` leaf **or** the first non-header leaf.
  3. If no `SHEBANG` leaf was found before real code → `return null` (not a library).
  4. Read the shebang line text = the `SHEBANG` leaf text (`"#!"`) concatenated with the following
     `SHORTCOMMENT` leaf text (the `lua name=...` remainder). Match against `SHEBANG_NAME`
     (`^#!\s*lua\s+name=([A-Za-z0-9_]+)`); on match return group 1, else `null`.
- **Rules / edge handling**: only a shebang **before any code** counts; a `#!` not on the first
  meaningful line is impossible (the lexer only produces `SHEBANG` for a leading `#!`). Interior
  whitespace after `#!` and around `lua`/`name` is tolerated by the regex (TC-SHB-2). Non-`lua`
  shebangs (`#!/bin/sh`) → `null`.
- **Caching**: `CachedValuesManager.getCachedValue(file) { … PsiModificationTracker … }` (the
  engine caching idiom, contract §4) so the detector is not re-walked per inspection element.

### 3.3 `register_function` typing (AC-2) — no algorithm
Delegated to the existing type engine (REDIS-04 §3.1 pattern): the §2.1 stub edit makes
`redis.register_function` resolve with its positional signature and the `@overload` table form.
`LuaTypesVisitor.getTypes(file).getValueType(exprFor("keys[1]"))` inside the callback returns
`string` because the callback parameter `keys` is typed `string[]` by the `fun(keys: string[],
args: string[])` annotation. Verification-only (TC-STUB-1/2).

### 3.4 KEYS/ARGV-in-library inspection (`visitNameRef`, AC-3)
- **Input → Output**: `LuaNameRef` → 0..1 WARNING.
- **Guard** (read once per `buildVisitor`): `val target = LuaProjectSettings.getInstance(
  holder.project).state.getTarget()`; require `target.platform == LuaPlatform.REDIS` (or VALKEY
  once REDIS-03 exists) **and** `target.version.label == "7+"` (Functions are Redis 7+;
  `VersionEntry.label`, grounded `PlatformVersionRegistry.kt:30`) — else return
  `PsiElementVisitor.EMPTY_VISITOR`. Then require `LuaRedisFunctionLibrary.isLibrary(
  holder.file)` — else `EMPTY_VISITOR` (TC-KEYS-2: non-library file → inert).
- **Visitor** (`object : LuaVisitor()` overriding `visitNameRef(o: LuaNameRef)`):
  1. `val name = o.identifier.text`; require `name == "KEYS" || name == "ARGV"`.
  2. Skip declaration/member positions the same way `LuaDeprecatedApiInspection.isDeclaration`
     (`LuaDeprecatedApiInspection.kt:59`) does — only flag a global read reference.
  3. `if (LuaInspectionSuppression.isSuppressed(o, name, "redis-function-keys")) return`
     (`LuaInspectionSuppression.kt:43`; `o` is a `LuaNameRef`, the exact accepted type).
  4. `holder.registerProblem(o, "'$name' is not available in a Redis Function library; use the
     callback's 'keys'/'args' parameters", ProblemHighlightType.GENERIC_ERROR_OR_WARNING)`.
- **Rules**: no quick fix (no mechanical rewrite — the parameter names are user-chosen). WARNING
  severity (RISK 1.1). Off Redis / non-library / non-7+ → inert (TC-KEYS-3).

### 3.5 FCALL execution (`LuaRedisFunctionExecutor.execute`, AC-4/AC-5)
- **Input → Output**: `(RespClient, LuaRedisRunConfiguration, body)` → `RespValue`.
- **Steps**:
  1. **Load**: build the LOAD command list: `["FUNCTION", "LOAD"] + (if replaceOnLoad ["REPLACE"]
     else []) + [body]`; `val loadReply = client.command(loadArgs)`. If `loadReply is
     RespValue.Error` → return it (surfaced by the console; no FCALL attempted).
  2. **Deploy-only**: `if (config.deployOnly) return loadReply` (TC-DEPLOY-1 — a load-without-call
     is a valid deploy run; `loadReply` is the `+<libname>` simple string).
  3. **Invoke**: `val verb = if (config.readOnly) "FCALL_RO" else "FCALL"` (AC-5 read-only
     mapping — the same `readOnly` toggle REDIS-01 §2.8 already persists). `val numkeys =
     config.keys.size.toString()`. `val callArgs = listOf(verb, config.functionName.orEmpty(),
     numkeys) + config.keys + config.argv`. Return `client.command(callArgs)`.
- **Command shapes** (parallels REDIS-01 §3.8's EVAL table):
  | mode | readOnly | deployOnly | commands |
  |------|----------|-----------|----------|
  | FCALL | false | false | `FUNCTION LOAD [REPLACE] <body>` ; `FCALL <fn> <#keys> <keys…> <argv…>` |
  | FCALL | true  | false | `FUNCTION LOAD [REPLACE] <body>` ; `FCALL_RO <fn> <#keys> <keys…> <argv…>` |
  | FCALL | any   | true  | `FUNCTION LOAD [REPLACE] <body>` only |
- **Edge handling**: a write attempted under `FCALL_RO` returns a server `RespValue.Error`
  (`ERR ... Write commands are not allowed …`) — surfaced verbatim via `RespReplyTreeConsole.
  showError` (REDIS-01 §2.6), **not** blocked client-side (TC-RO-1). `functionName` is validated
  before the run at edit time (§3.6), so `orEmpty()` here is only reached for a deploy-only run
  where it is unused. No `!!` anywhere.

### 3.6 `checkConfiguration` for FCALL (AC-4/AC-5/AC-6) — **[EDIT, REDIS-01 §3.7]**
- **Steps** (added FCALL branch; throws `RuntimeConfigurationException`, cf.
  `LuaTestRunConfiguration.kt:257-264`):
  1. Reuse REDIS-01 §3.7 steps 1–2 (script path present, connection resolved).
  2. `if (execMode != FCALL) …` → existing REDIS-01 EVAL/EVALSHA path (the old step-3 FCALL
     rejection is deleted).
  3. FCALL branch:
     - `if (!deployOnly && functionName.isNullOrBlank())` → throw "Function name is not defined".
     - Resolve the library `PsiFile` from `scriptPath` (via `VirtualFileManager.findFileByUrl` +
       `PsiManager.findFile`, in a `runReadAction`); if resolvable, `val reg =
       LuaRedisFunctionLibrary.registeredNames(file)` (§3.7). If `!deployOnly` and
       `!reg.hasDynamic` and `functionName !in reg.names` → throw
       `"Function '$functionName' is not registered in ${file.name} (registered:
       ${reg.names.sorted().joinToString(", ")})"` (TC-VALID-1). A library with any dynamic
       registration (`reg.hasDynamic`) skips name validation (TC-VALID-2).
  4. **no-writes hint** (AC-5, non-error): if `!readOnly` and `functionName` is registered and its
     static flag set contains `no-writes`, surface a hint (not a `RuntimeConfigurationException`)
     via the settings editor's warning label — "`$functionName` declares `no-writes`; consider
     read-only (`FCALL_RO`)". `checkConfiguration` cannot show a non-error hint, so the hint is
     rendered by `LuaRedisSettingsEditor` `applyEditorTo`/validation callback (a UI edit), keyed
     off `LuaRedisFunctionLibrary.registeredFlags(functionName)` (§3.7). TC-RO-2.
- **Rules**: an unresolvable script file (missing on disk) does not block validation (the run will
  fail later at load) — validation is best-effort, matching AC-6 "best-effort static scan".

### 3.7 Static registration scan (`LuaRedisFunctionLibrary.registeredNames`)
- **Input → Output**: `PsiFile` → `RegisteredNames(names, hasDynamic)`.
- **Steps** (single top-level walk; cached via `CachedValuesManager` on the file):
  1. Collect every `LuaFuncCall` in the file: `PsiTreeUtil.findChildrenOfType(file,
     LuaFuncCall::class.java)`.
  2. For each, `val site = RedisCallSiteMatcher.match(funcCall) ?: continue` (REDIS-04 §2.10) and
     require `site.namespace ∈ {"redis","server"} && site.member == "register_function"`.
     (`RedisCallSiteMatcher` already walks the `redis`/`server` `.member` shape; `register_function`
     is just another member string — no matcher change.)
  3. Read the call's argument list = the last `site.funcCall.nameAndArgsList` entry's
     `args` (`LuaNameAndArgs.args`, `LuaArgs`):
     - **Positional form** (`args.exprList != null`): first arg = name. If it is a
       `LuaTerminalExpr` with a non-null `.string` → strip quotes → add to `names`. Else (a
       name-ref / computed expr) → `hasDynamic = true`.
     - **Table form** (`args.tableConstructor != null`, or the first `exprList` expr is a
       `LuaTableConstructor`): scan `tableConstructor.fieldList.fieldList`; the `LuaField` whose
       `identifier.text == "function_name"` supplies the name literal (its `exprList[0]` as a
       `LuaTerminalExpr.string`) → add, else `hasDynamic = true` if the value is non-literal.
       Capture the `flags` field (a `LuaTableConstructor` of string literals) into the
       name→flags map used by §3.6 step 4.
  4. Return `RegisteredNames(names, hasDynamic)`. A sibling `registeredFlags(name): Set<String>`
     reads the captured flags map.
- **Rules / edge handling**: quotes stripped exactly as `LuaRequireReferenceContributor.kt:39`
  (`trim('"','\'','[',']','=')`). Any non-literal name (positional or `function_name`) sets
  `hasDynamic` (TC-SCAN-1, TC-VALID-2). No `!!`; every accessor is null-guarded with `?: continue`.

### 3.8 `FUNCTION LIST` parsing (`LuaRedisFunctionListParser.parse`, AC-7/AC-8)
- **Input → Output**: `RespValue` (the reply to `FUNCTION LIST [WITHCODE]`) →
  `List<RedisLibraryEntry>`.
- **Wire shape**: `FUNCTION LIST` returns an array; each element is a map (RESP3) or an even-length
  array of key/value pairs (RESP2) with keys `library_name` (bulk), `engine` (bulk), `functions`
  (array of per-function maps with `name` bulk + `flags` array of bulk), and — with `WITHCODE` —
  `library_code` (bulk). Both `RespValue.Map` and the RESP2 `RespValue.Array`-of-pairs are
  handled (REDIS-01 negotiates protocol per connection).
- **Steps**:
  1. Require `reply is RespValue.Array` with non-null `items`; else return `emptyList()`.
  2. For each item, coerce to a key→`RespValue` lookup via a helper `asPairs(v: RespValue):
     Map<String, RespValue>` that reads `RespValue.Map.entries` **or** pairs an
     `RespValue.Array.items` two-by-two (keys via `Bulk.asString()`).
  3. `name = pairs["library_name"]?.bulkString() ?: continue`;
     `libraryCode = pairs["library_code"]?.bulkString()` (null without WITHCODE, AC-8);
     `functions = (pairs["functions"] as? RespValue.Array)?.items?.map { fn -> …name/flags… }
     ?: emptyList()`.
  4. Per function: `fnName = fnPairs["name"].bulkString()`, `flags = (fnPairs["flags"] as?
     RespValue.Array)?.items?.mapNotNull { it.bulkString() }?.toSet() ?: emptySet()`.
- **Rules**: `bulkString()` = a local extension reading `RespValue.Bulk.asString()` /
  `RespValue.Simple.text` else null (REDIS-01 §2.1 accessors). Malformed/absent fields degrade to
  empty, never throw (defensive, matching REDIS-04 §4.1 parse discipline). No `!!`.

### 3.9 Panel operations (`LuaRedisFunctionsController`, AC-7)
- **`list(connection, withCode)`**: `RespClient.open(connection, RespTimeouts())` (REDIS-01 §2.3);
  `val reply = client.command(if (withCode) listOf("FUNCTION","LIST","WITHCODE") else
  listOf("FUNCTION","LIST"))`; `LuaRedisFunctionListParser.parse(reply)`; `client.dispose()` in a
  `finally`.
- **`delete(connection, libraryName)`**: `client.command("FUNCTION", "DELETE", libraryName)` →
  `RespValue`; caller removes the row on a non-error reply (TC-PANEL-2). The panel shows a
  `Messages.showYesNoDialog` confirmation **before** calling (AC-7 "with confirmation").
- **`deploy(connection, libraryBody)`**: `client.command("FUNCTION","LOAD","REPLACE", libraryBody)`
  (REPLACE per AC-7 "LOAD REPLACE from the local file"); on success the panel calls `refresh()`
  (TC-PANEL-3). `libraryBody` is read from the backing local file in a `runReadAction`.
- **Threading**: all three are `suspend`, opened/closed per call on the panel's coroutine; the
  panel marshals results to the tree with `withContext(Dispatchers.EDT)`. Errors are caught and
  shown via a status label / `Messages` (contract §2 error bounding), never crash the tool window.

### 3.10 Drift detection (`LuaRedisFunctionDrift.compare`, AC-8)
- **Input → Output**: `(serverCode: String?, localBody: String)` → `DriftStatus`.
- **Steps**:
  1. `if (serverCode == null) return UNKNOWN` (server did not report `library_code`, i.e. no
     WITHCODE support — no drift glyph).
  2. `fun norm(s: String) = s.replace("\r\n", "\n").trimEnd()`.
  3. `return if (sha1(norm(serverCode)) == sha1(norm(localBody))) IN_SYNC else DRIFTED`, where
     `sha1` = lowercase hex of `MessageDigest.getInstance("SHA-1")` over UTF-8 bytes (the REDIS-01
     §3.8 `sha1Hex` idiom).
- **Rules**: line-ending + trailing-whitespace normalization avoids spurious drift; the panel maps
  `IN_SYNC`→no glyph, `DRIFTED`→a warning glyph, `UNKNOWN`→no glyph (TC-DRIFT-1). The local body
  is matched by library name (`RedisLibraryEntry.name`) against the local file's shebang name
  (`LuaRedisFunctionLibrary.detect`).

### 3.11 Debug executor disable for FCALL (AC-9) — **[EDIT, REDIS-02 §2.1]**
REDIS-02's `LuaRedisDebugRunner.canRun` currently returns `executorId ==
DefaultDebugExecutor.EXECUTOR_ID && runProfile is LuaRedisRunConfiguration`. Because REDIS-05 now
**accepts** FCALL in `checkConfiguration`, the old implicit disable (REDIS-01 rejected FCALL) no
longer holds. Amendment A2 (§7): add the FCALL guard:
```kotlin
override fun canRun(executorId: String, runProfile: RunProfile): Boolean =
    executorId == DefaultDebugExecutor.EXECUTOR_ID &&
        runProfile is LuaRedisRunConfiguration &&
        runProfile.execMode != LuaRedisExecMode.FCALL
```
With `canRun` false, the platform greys the Debug action for FCALL configs. The explanatory
tooltip is provided by REDIS-02's existing disabled-action tooltip mechanism (REDIS-02 design
§2.2/§ RISK-R05 "Disabled … FCALL-mode Debug executor … with explanatory tooltips"); REDIS-05
supplies the tooltip text "LDB cannot debug FCALL (Function) invocations (Redis limitation)".
TC-DBG-1.

## 4. External Data & Parsing

### 4.1 `FUNCTION LIST [WITHCODE]` reply
- **Format**: an array of library maps; keys `library_name`, `engine`, `functions` (array of
  `{name, flags[]}`), `library_code` (only with `WITHCODE`). Fully specified in §3.8, parsed off
  the REDIS-01 `RespValue` model (REDIS-01 §2.1 / §3.3 owns the byte-level RESP decode). Both
  RESP2 (array-of-pairs) and RESP3 (`%map`) shapes are handled (§3.8 step 2).
- **Maps to**: `RedisLibraryEntry` / `RedisFunctionEntry` (§2.7). **Failure**: malformed entry →
  skipped, never throws (§3.8 rules).

### 4.2 `#!lua name=<lib>` shebang line
- **Format**: first meaningful line `#!` (SHEBANG token) + `lua name=<identifier>` (SHORTCOMMENT).
- **Parse strategy**: `SHEBANG_NAME` regex (§2.2 / §3.2). **Failure**: no match → `detect` returns
  `null` (not a library). The lexer guarantees the leaf shape (§1); no byte-level parsing.

### 4.3 `FUNCTION LOAD` reply
- **Format**: on success a simple string `+<libname>\r\n` → `RespValue.Simple(libname)`; on failure
  `-ERR …` → `RespValue.Error`. Consumed directly (§3.5), rendered by the console. No new parse.

## 5. Data Flow

### Example 1: FCALL run (deploy + call) against a remote connection
1. User runs a FCALL config → `LuaRedisRunProfileState.execute` (REDIS-01 §2.11) launches a
   coroutine on the session childScope and returns a `RespReplyTreeConsole` result.
2. Coroutine: `RespClient.open` (REDIS-01 §3.1); read the library body via `readAction { }`.
3. `LuaRedisFunctionExecutor.execute` (§3.5): `FUNCTION LOAD REPLACE <body>` → `+lib`; then
   `FCALL f 1 k1 a1` → reply.
4. `withContext(Dispatchers.EDT) { console.showReply(reply) }`; client disposed.

### Example 2: Functions panel — list, drift, delete
1. User opens the "Redis Functions" tool window; selects a connection.
2. `LuaRedisFunctionsPanel.refresh` → `controller.list(conn, withCode=true)` on the pooled scope
   → `FUNCTION LIST WITHCODE` → `LuaRedisFunctionListParser.parse` → model.
3. For each library, `LuaRedisFunctionDrift.compare(entry.libraryCode, localBodyForName)` sets the
   row glyph; model published to the tree on EDT.
4. User right-clicks a library → Delete → `Messages.showYesNoDialog` → `controller.delete` →
   `FUNCTION DELETE lib` → row removed on `+OK`.

### Example 3: Editing a library file
1. User opens `#!lua name=lib` file under a Redis 7+ target.
2. `LuaRedisFunctionKeysInspection` guard: target Redis-7+ and `isLibrary` true → active.
3. A `KEYS[1]` read is flagged WARNING (§3.4); `redis.register_function('f', fn)` types the
   callback `keys`/`args` as `string[]` (§3.3, no inspection runs on it).

## 6. Edge Cases
- **No shebang / non-`lua` shebang**: `detect` → `null`; the KEYS inspection is inert and REDIS-04
  EVAL typing applies (TC-KEYS-2, TC-SHB-2).
- **Redis 5/6 target**: Functions require Redis 7+; the §3.4 guard requires `label == "7+"`, and
  the §2.1 stub is only in `redis-7`, so `register_function` is unresolved on 5/6 (correct — the
  feature doesn't exist there).
- **Dynamic registration** (`register_function(fnName, cb)`): `hasDynamic = true` → FCALL
  name-validation skipped (TC-VALID-2, TC-SCAN-1).
- **`FCALL_RO` write attempt**: server returns an error; surfaced, not client-blocked (TC-RO-1).
- **Server without WITHCODE** (older 7.0): `library_code` absent → drift `UNKNOWN`, no glyph
  (TC-DRIFT-1).
- **Deploy-only run**: `FUNCTION LOAD` only, no FCALL; the `+libname` reply is shown (TC-DEPLOY-1).
- **Debug on a FCALL config**: `canRun` false → Debug greyed with tooltip (TC-DBG-1).
- **Valkey**: with REDIS-03 present, `server.register_function` resolves by stub inheritance and
  the same executor/panel/`FUNCTION *` commands apply (TC-INT-1 Valkey parity); with REDIS-03
  absent, only `redis.*` is available (no rescope).

## 7. Integration Points

```xml
<!-- plugin.xml — append to <extensions defaultExtensionNs="com.intellij"> -->

<!-- AC-3: KEYS/ARGV-in-library inspection (sibling to REDIS-04 inspections) -->
<localInspection
    language="Lua"
    shortName="LuaRedisFunctionKeys"
    displayName="KEYS/ARGV in a Redis Function library"
    groupPath="Lua"
    groupName="Redis"
    enabledByDefault="true"
    level="WARNING"
    implementationClass="net.internetisalie.lunar.analysis.redis.LuaRedisFunctionKeysInspection"/>

<!-- AC-7/AC-8: Functions tool window (mirrors the LuaRocks toolWindow, plugin.xml:68-73) -->
<toolWindow
    id="Redis Functions"
    anchor="bottom"
    secondary="true"
    icon="net.internetisalie.lunar.lang.LuaIcons.ROCKET"
    factoryClass="net.internetisalie.lunar.redis.functions.ui.LuaRedisFunctionsToolWindowFactory"/>
```
- **Inspection description**: `src/main/resources/inspectionDescriptions/LuaRedisFunctionKeys.html`
  (existing convention).
- **Resource edit**: `runtime/redis/redis-7/redis.lua` gains `register_function` (§2.1). No new EP
  (picked up by the existing `AdditionalLibraryRootsProvider`).
- **No new run-config type / producer**: REDIS-05 reuses REDIS-01's `LuaRedisRunConfiguration` and
  its already-registered `<configurationType>`/`<runConfigurationProducer>` (REDIS-01 §7). FCALL is
  a mode within it.

### Cross-feature amendments (requested, not silent redefinitions)
- **A1 — REDIS-01** (`LuaRedisRunConfiguration`/`Options`, `LuaRedisRunProfileState`,
  `checkConfiguration`, `LuaRedisSettingsEditor`): enable the reserved `FCALL` slot; add
  `functionName`/`replaceOnLoad`/`deployOnly` options fields (§2.4); dispatch the profile state to
  `LuaRedisFunctionExecutor` for FCALL (§2.6); remove the "FCALL not available" rejection and add
  the FCALL validation branch + no-writes hint (§3.6). All additive; REDIS-01's EVAL/EVALSHA paths
  are untouched. This realizes REDIS-01 risks §"Public Seams" ("REDIS-05 enables it and adds
  FUNCTION LOAD deploy, reusing `LuaRedisScriptExecutor`'s command-selection shape").
- **A2 — REDIS-02** (`LuaRedisDebugRunner.canRun`): add `&& runProfile.execMode !=
  LuaRedisExecMode.FCALL` (§3.11). REDIS-02 risks already anticipate this ("the Debug executor
  stays disabled for FCALL mode") but currently rely on REDIS-01's `checkConfiguration` rejection,
  which REDIS-05 removes — so the guard must move into `canRun`. Additive, non-breaking for EVAL.
- **A3 — REDIS-04** (consumed, no change requested): `RedisCallSiteMatcher` already matches any
  `redis`/`server` `.member`, so `register_function` needs no matcher edit; `isRedisTarget` /
  `LuaInspectionSuppression` / the stub-declared-global seam are consumed as-is.
- **A4 — REDIS-03** (consumed, no change requested): `server.register_function` is provided by the
  existing `---@class server : redis` inheritance (REDIS-03 §3.2). If REDIS-03 is not yet
  implemented, only `redis.*` Functions are available; nothing in REDIS-05 blocks on it.

## 8. Requirement Coverage

| Acceptance criterion (requirements.md order) | Priority | Implemented by |
|----------------------------------------------|----------|----------------|
| AC-1 Shebang recognition (clean lex, distinct render) | C | §1 (already lexed), §3.1, §3.2; TC-SHB-1, TC-SHB-2 |
| AC-2 `register_function` typed stubs (positional + table, callback signature) | C | §2.1, §3.3; TC-STUB-1, TC-STUB-2 |
| AC-3 KEYS/ARGV flagged in library files; REDIS-04 ambient suppressed | C | §2.2, §2.3, §3.4; TC-KEYS-1..3 |
| AC-4 FCALL run-config mode (deploy-only valid, fields persisted) | C | §2.4, §2.5, §2.6, §3.5, §3.6; TC-MODE-1, TC-DEPLOY-1, TC-CALL-1 |
| AC-5 `FCALL_RO` + no-writes hint + write-error surface | C | §3.5, §3.6; TC-RO-1, TC-RO-2 |
| AC-6 FCALL name validation (static scan, dynamic skipped) | C | §2.2, §3.6, §3.7; TC-VALID-1, TC-VALID-2, TC-SCAN-1 |
| AC-7 Functions panel (list, Deploy/Delete + confirm) | C | §2.7, §2.9, §2.10, §3.8, §3.9; TC-PANEL-1..3 |
| AC-8 Local-vs-server drift indicator | C | §2.8, §3.10; TC-DRIFT-1 |
| AC-9 Debug executor disabled for FCALL (tooltip) | C | §3.11, §7 A2; TC-DBG-1 |
| AC-10 Integration test (load/list/call/replace/delete) + Valkey parity | C | impl-plan Phase 6; TC-INT-1 |

## 9. Alternatives Considered
- **A new lexer state / element type for the shebang name** — rejected: the existing
  `SHEBANG`+`SHORTCOMMENT` lexing already satisfies AC-1 (clean, distinctly highlighted); a static
  regex detector (§3.2) is sufficient and avoids grammar regeneration risk (MAINT-20 lesson:
  headless grammar-kit generation is fragile).
- **A dedicated `LuaRedisFunctionsRunConfiguration` type** — rejected: the requirements explicitly
  put FCALL "on the run configuration" (REDIS-01), and REDIS-01 reserved the `FCALL` enum slot for
  exactly this. A second config type would duplicate the connection/console/launcher wiring.
- **Client-side write blocking under `no-writes`** — rejected: the server enforces `FCALL_RO` /
  `no-writes` authoritatively; duplicating that in the client would drift from server semantics.
  REDIS-05 surfaces the server error and offers a hint (AC-5), not a client-side gate.
- **Resolving registered names by running `FUNCTION LIST`** (vs static scan) — rejected for
  validation: `checkConfiguration` runs at edit time with no live connection (same constraint as
  REDIS-01 §3.7). The static scan (§3.7) is the edit-time source; the panel uses the live list.

## 10. Open Questions

_None — feature has cleared the planning bar. Deferred items (WITHCODE availability across server
versions, panel refresh cadence) are tracked in [risks-and-gaps.md](risks-and-gaps.md) as
non-blocking DR tasks. The two cross-feature amendments (A1 REDIS-01, A2 REDIS-02) are explicit,
additive, and recorded in §7 and risks-and-gaps._
