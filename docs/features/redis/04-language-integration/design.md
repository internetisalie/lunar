---
id: "REDIS-04-DESIGN"
title: "Technical Design"
type: "design"
status: "todo"
parent_id: "REDIS-04"
folders:
  - "[[features/redis/04-language-integration/requirements|requirements]]"
---

# Technical Design: REDIS-04 — Language-Engine Integration

Redis-aware editing support with **zero** dependency on the connection stack (REDIS-01) or
the debugger (REDIS-02). Everything keys off the project `Target`
(`LuaProjectSettings.getInstance(project).state.getTarget()`) and a bundled per-version
command spec. Five strands: (1) ambient `KEYS`/`ARGV` typing, (2) a command-spec application
service, (3) command-name completion, (4) unknown/arity/determinism inspections + quick fix,
(5) sandbox-API inspection and Global-creation escalation. `redis.pcall` narrowing is a stub
edit.

## 1. Architecture Overview

### Current State
TARGET-04 ships the per-target stdlib-stub mechanism. For `Target(REDIS,"7+")` the resources
`runtime/redis/redis-7/{global,redis,os,cjson,cmsgpack,struct,bit}.lua` are exposed as a
`SyntheticLibrary` by `net.internetisalie.lunar.lang.library.LuaLibraryProvider`
(`AdditionalLibraryRootsProvider`, `src/main/kotlin/.../lang/library/LuaLibraryProvider.kt:13`),
resolved through `RuntimeLibraryProvider.getLibraryRoot(target)`
(`src/main/kotlin/.../platform/target/RuntimeLibraryProvider.kt:24`) from
`Target.getLibraryRootPath()` (`.../platform/target/Target.kt:65`). `global.lua` already
declares `KEYS`/`ARGV` as `string[]`
(`src/main/resources/runtime/redis/redis-7/global.lua:1-8`) and `redis.lua` declares the
`redis` table with `call`/`pcall` (`.../redis-7/redis.lua:1-30`). **Gap:** only the
`redis-7` resource dir exists; `PlatformVersionRegistry`
(`.../platform/target/PlatformVersionRegistry.kt:27-31`) declares path segments `redis-5` and
`redis-6` but no such resource dirs are bundled, so stubs currently activate only for `7+`.
No command intelligence, completion, arity/sandbox/determinism inspection, or command doc
exists.

### Prior Art in This Repo (grounding — extend, do not duplicate)
- **Completion** — `net.internetisalie.lunar.lang.LuaCompletionContributor`
  (`src/main/kotlin/.../lang/LuaCompletionContributor.kt:21`), registered
  `<completion.contributor language="Lua" …>` at `plugin.xml:288-290`. It uses the
  `extend(CompletionType.BASIC, pattern, provider)` idiom and reads the target via
  `LuaProjectSettings.getInstance(project).state.getTarget()` (line 138). REDIS-04 adds a
  **new sibling** `CompletionContributor` (its own `<completion.contributor>` registration),
  NOT an edit to `LuaCompletionContributor`, so the Redis logic is isolated and no-ops off
  Redis targets.
- **String-literal-in-call detection** — `LuaRequireReferenceContributor`
  (`.../lang/LuaRequireReferenceContributor.kt:13-49`) is the exact prior art: it matches a
  `LuaTerminalExpr.string` whose enclosing `LuaFuncCall.varOrExp.var.nameRef.identifier.text
  == "require"`. REDIS-04 reuses this call-shape walk (adapted for the `redis.call` member
  form) in the completion contributor, the inspection, and the doc target.
- **Inspection + quick fix** — `LuaGlobalCreationInspection`
  (`.../analysis/inspections/LuaGlobalCreationInspection.kt:24`) is the pattern:
  `LocalInspectionTool` + `buildVisitor` returning a `LuaVisitor`, `holder.registerProblem`,
  a `LocalQuickFix`, and `LuaInspectionSuppression.isSuppressed(...)`. REDIS-04 **edits** this
  class for AC-8 (severity escalation) and **adds** two new inspections modeled on it.
  `LuaDeprecatedApiInspection` (`.../inspections/LuaDeprecatedApiInspection.kt:18`) is the
  reference for a spec/stub-driven visitor.
- **Documentation** — `LuaDocumentationTargetProvider`
  (`.../lang/doc/LuaDocumentationTargetProvider.kt`, registered
  `<platform.backend.documentation.targetProvider …>` at `plugin.xml:115-117`) is the modern
  `DocumentationTargetProvider` EP. REDIS-04 adds a **new sibling** target provider.
- **App service** — `GlobalSymbolRankingService`
  (`.../lang/completion/GlobalSymbolRankingService.kt:30`, `@Service(Service.Level.PROJECT)`)
  and `<applicationService>` rows at `plugin.xml:425-433` are the service idiom. Gson is
  bundled and used (`.../rocks/RockspecBridge.kt:3`, `.../toolchain/provision/feed/`).
- **Type engine** — `LuaTypesVisitor.getTypes(file): LuaTypes`
  (`.../lang/psi/types/LuaTypesVisitor.kt:778`) → `LuaTypes.getValueType(el): LuaGraphType`
  (`.../lang/psi/types/LuaTypes.kt:21`); `LuaGraphType.getMembers()`
  (`.../lang/psi/types/LuaGraphType.kt:84`). **AC-6** (`redis.pcall` union) needs **no** engine
  code — it is a stub edit consumed by this engine (ground-truth-verified, REDIS-04-phase-1
  handoff). **AC-1** (`KEYS`/`ARGV` typing) is **NOT** a pure stub edit — see §3.1: the engine
  is strictly single-file (`buildSnapshot` visits only `file`, scope starts empty —
  `LuaTypesVisitor.kt:25,782-787`) and has no array-subscript element inference
  (`visitIndexExpr` handles only dotted access — `LuaTypesVisitor.kt:644-658`). AC-1 therefore
  requires **two grounded engine changes** (§3.1a, §3.1b) plus a regression contract (§3.1c).

### Target State
- Data (`commandspec/*.json` resources) → `RedisCommandSpecService` (app service, lazy, Gson,
  cached) → three consumers keyed on `Target`: `LuaRedisCommandCompletionContributor`,
  `LuaRedisCommandInspection` (+ `LuaRedisRenameCommandQuickFix`), and
  `RedisCommandDocumentationTargetProvider`.
- `LuaRedisSandboxInspection` (allowlist derived from the target's stub roots) and the
  `LuaGlobalCreationInspection` escalation are independent of the command spec.
- `KEYS`/`ARGV` typing (AC-1) and `redis.pcall` narrowing (AC-6) are **resource edits** under
  `runtime/redis/…`, consumed by the existing type engine and library provider.

## 2. Core Components

### 2.1 (resource edits) `runtime/redis/{redis-5,redis-6,redis-7}/{global,redis}.lua`
- **Responsibility**: declare `KEYS`/`ARGV` as `string[]` and the `redis`/`server` API per
  version. These stubs are **necessary but not sufficient** for AC-1: the `global.lua`
  `@type string[]` binding is only *consumed* by inference after the §3.1a engine change feeds
  stub globals into the type graph, and `KEYS[1] → string` additionally needs the §3.1b
  array-subscript change. `redis.pcall` union (AC-6) IS fully realized by the stub edit alone.
- **Threading**: n/a (static bundled resources; loaded by `RuntimeLibraryProvider` /
  library-roots mechanism, already read-action-safe).
- **Collaborators**: `LuaLibraryProvider`, `RuntimeLibraryProvider`, the type engine.
- **Work**:
  1. Create `runtime/redis/redis-5/` and `runtime/redis/redis-6/` dirs, each mirroring the
     `redis-7` file set (copy, then remove version-inappropriate members per §3.10). This
     closes the "stubs only for 7+" gap so AC-1 holds for all registered Redis versions.
  2. Add `redis.replicate_commands()` (`---@return boolean`) to every `redis.lua`
     (needed by AC-9 / TC-DET-2; currently absent).
  3. Change `redis.pcall`'s `@return` to the union in §4.2 (AC-6).

### 2.2 `net.internetisalie.lunar.analysis.redis.RedisCommandSpecService`
- **Responsibility**: load, parse (Gson), and cache the bundled command spec for a `Target`;
  the single reusable seam REDIS-05 consumes for its command surface.
- **Threading**: light `@Service(Service.Level.APP)`; parsing happens lazily inside a
  `synchronized` memo (no PSI/VFS access → callable from read actions).
- **Collaborators**: Gson (bundled), classloader resources under `commandspec/`.
- **Key API**:
  ```kotlin
  @Service(Service.Level.APP)
  class RedisCommandSpecService {
      fun specFor(target: Target): RedisCommandSpec  // EMPTY when no bundled spec
      companion object { fun getInstance(): RedisCommandSpecService }
  }
  data class RedisCommandSpec(val commands: Map<String, RedisCommandInfo>) {
      fun lookup(name: String): RedisCommandInfo?          // upper-cased key
      fun names(): Set<String>
      companion object { val EMPTY = RedisCommandSpec(emptyMap()) }
  }
  data class RedisCommandInfo(
      val name: String, val arity: Int, val since: String,
      val summary: String, val flags: Set<String>,   // e.g. "write", "nondeterministic"
  )
  ```
- **Spec-file selection**: maps `Target` → resource path (§3.2). Cache key is the resolved
  resource path (so REDIS versions sharing a file share one parsed instance).

### 2.3 `net.internetisalie.lunar.lang.completion.LuaRedisCommandCompletionContributor`
- **Responsibility**: offer version-valid command names as the first string arg of
  `redis.call`/`pcall` (and `server.call`/`pcall` once Valkey exists) (AC-3).
- **Threading**: EDT completion callback; only PSI reads + service lookup (no I/O).
- **Collaborators**: `RedisCommandSpecService`, `LuaProjectSettings`, `RedisCallSiteMatcher`
  (§3.3), `LuaTerminalExpr`, `LuaFuncCall`.
- **Key API**: `CompletionContributor` with one `extend(CompletionType.BASIC,
  psiElement().withElementType(LuaTokenTypes.STRING), provider)` (same idiom as
  `LuaCompletionContributor`'s `withElementType(LuaElementTypes.IDENTIFIER)`,
  `.../lang/LuaCompletionContributor.kt:221`); the provider bails unless
  `RedisCallSiteMatcher.match(parameters.position)` returns a `RedisCallSite` at the first-arg
  literal and the target is Redis.
- **Registration**: `<completion.contributor language="Lua" implementationClass=…>` (§7).

### 2.4 `net.internetisalie.lunar.analysis.redis.LuaRedisCommandInspection`
- **Responsibility**: on each `redis.call`/`pcall` string-literal command: flag unknown
  commands (WARNING + rename quick fix), below-minimum arity (WARNING), and — for Redis 5/6 —
  the determinism rule (AC-4, AC-9). Dynamic (non-literal) command names never flagged.
- **Threading**: local inspection (`buildVisitor` on a read action, IntelliJ-managed).
- **Collaborators**: `RedisCommandSpecService`, `RedisCallSiteMatcher`,
  `LuaRedisRenameCommandQuickFix`, `LuaInspectionSuppression`, `LuaProjectSettings`.
- **Key API**:
  ```kotlin
  class LuaRedisCommandInspection : LocalInspectionTool() {
      override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor
      // visitor is a LuaVisitor overriding visitFuncCall(o: LuaFuncCall)
  }
  ```
- **Registration**: `<localInspection … shortName="LuaRedisCommand" level="WARNING">` (§7).

### 2.5 `net.internetisalie.lunar.analysis.redis.LuaRedisRenameCommandQuickFix`
- **Responsibility**: replace the offending command literal with a suggested valid name.
- **Threading**: `applyFix` under `WriteCommandAction.runWriteCommandAction(project)`.
- **Collaborators**: `LuaElementFactory` (used by `LuaMakeLocalQuickFix`,
  `.../inspections/LuaGlobalCreationInspection.kt:82-98`), `LuaTerminalExpr`.
- **Key API**:
  ```kotlin
  class LuaRedisRenameCommandQuickFix(private val suggestion: String) : LocalQuickFix {
      override fun getFamilyName(): String = "Change to '$suggestion'"
      override fun applyFix(project: Project, descriptor: ProblemDescriptor)
  }
  ```

### 2.6 (resource edit) `redis.pcall` union return — see §2.1 / §4.2
No Kotlin component; AC-6 is a stub annotation edit consumed by the type engine.

### 2.7 `net.internetisalie.lunar.analysis.redis.LuaRedisSandboxInspection`
- **Responsibility**: under Redis/Valkey targets, flag references to APIs not in the sandbox
  allowlist (`io`, most of `os`, `require`, `dofile`, `loadfile`, `print`) (AC-7). Ships at
  WARNING (RISK-R07); the `level` attribute is the single escalation knob.
- **Threading**: local inspection.
- **Collaborators**: `RedisSandboxAllowlist` (§3.7), `LuaProjectSettings`,
  `RuntimeLibraryProvider`, `LuaInspectionSuppression`.
- **Seam API**: `RedisSandboxAllowlist.forTarget(project: Project, target: Target): Set<String>`
  — the `Project` parameter is mandatory because §3.7 reads the stub roots through
  `RuntimeLibraryProvider(project)`, whose constructor requires a `Project`
  (`.../platform/target/RuntimeLibraryProvider.kt:16`). The inspection supplies `ref.project`.
- **Key API**: `LocalInspectionTool` whose `LuaVisitor` overrides
  `visitNameRef(o: LuaNameRef)` (mirrors `LuaDeprecatedApiInspection`).
- **Registration**: `<localInspection … shortName="LuaRedisSandbox" level="WARNING">` (§7).

### 2.8 (edit) `net.internetisalie.lunar.analysis.inspections.LuaGlobalCreationInspection`
- **Responsibility**: escalate the existing global-creation problem from WARNING to ERROR
  under Redis/Valkey targets (AC-8), preserving `LuaInspectionSuppression` and the existing
  exemptions.
- **Change**: at the `registerProblem` site (line 55-61) choose
  `ProblemHighlightType.ERROR` vs `GENERIC_ERROR_OR_WARNING` from a new
  `private fun isRedisTarget(project): Boolean` reading
  `LuaProjectSettings.getInstance(project).state.getTarget().platform`. No behavior change off
  Redis; suppression path untouched.

### 2.9 (part of 2.4) Determinism rule
Implemented inside `LuaRedisCommandInspection` (§3.9), version-gated on the target version
label (`"5"`/`"6"` only). Kept in the command inspection because it shares the call-site walk
and the spec lookup.

### 2.10 `net.internetisalie.lunar.analysis.redis.RedisCallSiteMatcher` [NEW helper]
- **Responsibility**: given a `PsiElement` (completion position, string literal, or
  `LuaFuncCall`), decide whether it is a `redis.call`/`redis.pcall` (or `server.*`) call site
  and expose the command-name literal + the argument count. Single source of truth for the
  call shape (dedupe across §2.3/§2.4/§2.5/doc).
- **Threading**: pure PSI reads.
- **Key API**:
  ```kotlin
  data class RedisCallSite(
      val funcCall: LuaFuncCall,
      val nameLiteral: LuaTerminalExpr?,   // null when the first arg is non-literal
      val commandName: String?,            // upper-cased, quotes stripped; null if dynamic
      val argCount: Int,                   // count of value args including the command name
      val namespace: String,               // "redis" or "server"
      val member: String,                  // "call" or "pcall"
  )
  object RedisCallSiteMatcher {
      fun match(anchor: PsiElement): RedisCallSite?
  }
  ```

## 3. Algorithms

### 3.1 Ambient `KEYS`/`ARGV`/`redis.*` typing (AC-1) — TWO REQUIRED engine changes

> **Re-plan note (2026-07-14, DR-03 re-opened).** The prior "no new algorithm" premise was
> ground-truth-**false** on the real engine under `Target(REDIS,"7+")` (REDIS-04-phase-1
> handoff): `getValueType(KEYS)`/`getValueType(ARGV)` → `Undefined`, `KEYS` raises
> "Undeclared variable", and `getValueType(KEYS[1])` → `Undefined`. Two independent engine
> gaps cause this; both must be fixed. Root cause is grounded below at `file:line`.

#### 3.1a — Feed stub-declared globals into inference (root cause #1)
- **Grounded root cause.** The type engine is **strictly single-file**: `buildSnapshot(file)`
  runs `LuaTypesVisitor()` over `file` only, with an **empty** root scope
  (`LuaScope.root` — `LuaTypesVisitor.kt:25`, `782-787`). `visitNameRef` types a name purely
  by `scope.lookup(o.text)` (`LuaTypesVisitor.kt:676-680`); a name not declared *in the current
  file* returns `null` → no node → `Undefined`. `KEYS` is declared in a *different* file
  (`runtime/redis/redis-7/global.lua:3` — `KEYS = {}` with `---@type string[]`), so it is never
  in scope. (Note reference *resolution* is cross-file via `LuaNameReference.multiResolve`
  Phase-2 library query — `LuaNameReference.kt:82-100`,`176-187` — which is why `redis.call`
  resolves; but that path never runs for the type visitor. Whether the bare `KEYS = {}` global
  even resolves is moot for inference — the visitor never consults it.)
- **Change point.** `LuaTypesVisitor` — seed the **root scope** with the active target's
  ambient stub globals *before* visiting the file, so `visitNameRef("KEYS")` resolves to a
  pre-seeded node carrying the `@type string[]` type. Add a private
  `seedAmbientGlobals(file: PsiFile)` called at the top of `buildSnapshot` (before
  `file.accept(visitor)`), and thread the seeded bindings into the initial `scope`.
- **Algorithm (concrete, not narrated).**
  1. `project = file.project`; `target = LuaProjectSettings.getInstance(project).state.getTarget()`.
  2. Acquire the target's ambient-global stub file(s): the `global.lua` under the target's
     library root. Resolve via the existing provider —
     `RuntimeLibraryProvider(project).getLibraryFiles(target)`
     (`RuntimeLibraryProvider.kt:38` — returns `List<VirtualFile>`), filtered to VF name
     `global.lua`; convert to PSI with
     `PsiManager.getInstance(project).findFile(vf) as? LuaFile`. If none (non-Redis target, or
     target with no stub) → seed nothing → `KEYS` stays undeclared (this is exactly TC-KEYS-3's
     no-leak guarantee — **no** extra gating code needed).
  3. For each top-level global assignment in that stub file
     (`LuaAssignmentStatement` whose LHS `LuaVar` is a bare `nameRef` with no `varSuffix`, e.g.
     `KEYS`/`ARGV`), read its attached `---@type` via the **existing** bridge
     `LuaTypeGraphBridge.injectTypeAnnotation(cats, anchor, node, graph, context)`
     (`LuaTypeGraphBridge.kt:57-88`) — the identical mechanism `visitLocalVarDecl` uses at
     `LuaTypesVisitor.kt:444-448`. For `KEYS`: `injectTypeAnnotation` resolves `"string[]"` →
     `LuaArrayType(String)` → `LuaGraphType.Array(String)` and flows it into a fresh
     `graph.variable(anchor)` node.
  4. `rootScope.declare(globalName, seededNode)` for each seeded global.
- **Grounded reuse (no new type-resolution code).** Steps 3–4 reuse `LuaTypeGraphBridge`
  verbatim; `string[]` → `LuaArrayType` → `LuaGraphType.Array(String)` is already implemented
  (`LuaGraphType.kt:152-154`, `fromLuaType`). The only new code is the seed loop + wiring.
- **Threading / caching.** Runs inside `buildSnapshot`, which is already invoked under the
  file's cached-value computation (`LuaTypesSnapshot.forFile` → `cacheFileUserData`,
  `LuaTypes.kt:136-138`) — a read-action context. Reading the stub `global.lua`'s PSI is a PSI
  read (safe here). The seeded set is small (2 names). **Invalidation:** the snapshot cache is
  keyed on the *edited file's* text (`cacheFileUserData` on the document hash); a **target
  switch** must invalidate it. Confirm the existing target-change drop path already clears this
  file cache (the library-root refresh on `setTargetAndNotify` triggers a PSI/VFS change → cache
  drop); if it does **not**, this is a residual gap → tracked as DR-03b (§risks-and-gaps). The
  reference resolver already invalidates correctly on target change (REDIS-03
  `LibraryLoadingAfterTargetChangeTest`), so the mechanism exists.
- **Contract.** No hard `Project`/`Editor` field retention; `project` is obtained locally from
  `file.project` and used transiently. Stub PSI is not stored.

#### 3.1b — Array-subscript element inference (root cause #2)
- **Grounded root cause.** `visitIndexExpr` (`LuaTypesVisitor.kt:644-658`) records an element
  node **only** when `o.nameRef != null` — i.e. dotted member access `a.b`
  (`indexExpr ::= ('[' expr ']') | ('.' nameRef)`, `lua.bnf:279`). A **bracket** subscript
  `KEYS[1]`/`arr[1]` has `nameRef == null` and a non-null `o.expr` (the `[1]` index expr,
  `LuaIndexExpr.getExpr()`), so the branch is skipped, `elementNodes[o]` is never set → the
  subscript reads `Undefined`. `LuaGraphType.Array.elementType` (`LuaGraphType.kt:54-56`) is
  never propagated on a read. This holds even for a directly-annotated local
  (`---@type string[] local arr = {}` infers `arr` as the expected union but `arr[1]` →
  `Undefined`), so it is independent of 3.1a.
- **Change point.** Extend `visitIndexExpr` with an `else`-branch (`nameRef == null &&
  o.expr != null`) that models bracket-subscript element extraction. Mirror the existing
  dotted-branch shape (receiver node + a `graph.use` constraint + `elementNodes[o]`).
- **Algorithm (concrete).**
  1. `nameRef != null` → existing dotted logic, unchanged (§3.1b MUST NOT alter it — see
     regression contract §3.1c).
  2. Else if `o.expr != null` (bracket subscript):
     a. `varElement = PsiTreeUtil.getParentOfType(o, LuaVar::class.java) ?: return` and
        `receiverNode = firstNode(unwrapExpression(varElement.firstChild)) ?: return` — the
        **same** receiver acquisition the dotted branch uses (`LuaTypesVisitor.kt:648-650`).
     b. `elementNode = graph.variable(o)`.
     c. Constrain the receiver to be an array whose element flows to `elementNode`:
        `arrayConstraint = LuaGraphType.Array(elementType = <the elementNode's read type>)`.
        Because `LuaGraphType.Array` takes a *type* not a node, drive the flow through the
        graph instead: register a **use** constraint that, when the receiver's *value* is an
        `Array(T)`, flows `T` into `elementNode`. Implement as a new
        `checkCompatibility` case (see §3.1b-graph below) analogous to the Table-member flow
        in `checkTableCompatibility` (`LuaTypeGraph.kt:491-506`), which does
        `addEdge(valueNode, useNode)` to flow a member value into the use's member node.
     d. `elementNodes[o] = listOf(elementNode)`.
  - **§3.1b-graph (the propagation seam).** Add an `Array`-value case to `checkCompatibility`
    (`LuaTypeGraph.kt:275-361`): when `valueType is LuaGraphType.Array` and `useType is
    LuaGraphType.Array`, in addition to the existing element-compat check
    (`LuaTypeGraph.kt:349-354`) **flow the value's element type into the use's element sink**
    so a subscript-result node backed by `use(o, Array(elementSink))` receives `T`. Concretely:
    represent the subscript use as `graph.use(o, LuaGraphType.Array(<sentinel-from-elementNode>))`
    and, on match, `addEdge(valueArrayElementValueNode, elementNode)`. Because `Array` holds a
    `LuaGraphType` (not a node), the mechanical implementation is: in `visitIndexExpr`'s bracket
    branch, after acquiring the receiver's resolved value type via a fixed-point-safe read, if
    it is `Array(T)` set `elementNodes[o] = listOf(graph.value(o, T))`. **The implementer picks
    one of these two seams** (a: extend `checkCompatibility` Array-arm to propagate; b: read the
    receiver's Array element type directly in the visitor and emit a `value` node) — both are
    grounded; §3.1c's regression contract governs either. Recommended: seam (a), because it
    composes with unions (`KEYS` seeded as `Array(String)` flows cleanly) and mirrors the
    already-proven Table-member propagation; seam (b) is simpler but reads a not-yet-fixed-point
    value and can miss late-resolved receivers. **This choice is the one residual design
    decision — pinned to seam (a) as the default; DR-03a covers the spike if (a) proves
    intractable within the fixed-point loop.**
  3. **Non-array receiver.** If the receiver's value type is not an `Array` (e.g. a `Table`
     used as a numeric map, or `Undefined`), the bracket branch emits **no** value edge →
     the subscript stays `Undefined` exactly as today. This bounds the blast radius: only
     genuine `Array(T)` receivers change behavior.
- **`#ARGV` note (TC-KEYS-2).** `visitUnOpExpr` hardcodes `#` → `Number`
  (`LuaTypesVisitor.kt:393-398`) **and** adds a `use(String | Table)` constraint on the
  operand. Once `ARGV` seeds as `Array(String)` (3.1a), that `String | Table` constraint sees
  an `Array` value — an `Array` is neither `String` nor `Table`, so `checkCompatibility` would
  emit a spurious "not assignable" error on every `#ARGV`. **Required sub-fix:** widen the `#`
  operand constraint to also admit `Array` (add `LuaGraphType.Array(LuaGraphType.Any)` to the
  union at `LuaTypesVisitor.kt:396`, or special-case `Array` as length-able). Without this,
  3.1a regresses `#array` length usage. TC-KEYS-2 is re-specified below to assert the operand
  resolves as `string[]` (not merely that `#` is `number`).

#### 3.1c — Regression contract (blast radius; §2 quantified in the re-plan report)
`visitIndexExpr` and the single-file inference path are a **shared, serial type-engine hot
seam** (roadmap: "Serial: type-engine"). Consumers of index/subscript/global types that MUST
NOT regress: `LuaTypeAssignabilityInspection`, `LuaReturnTypeMismatchInspection`,
`LuaSuspiciousConcatenationInspection`, `LuaInferredTypeAnnotator`, and the three inlay-hint
providers (`LuaTypeInlayHintProvider`, `LuaParameterInlayHintsProvider`,
`LuaMethodChainInlayHintProvider`) — all read `LuaTypes.getValueType`
(grep: `getValueType` consumers, `src/main/kotlin`). Invariants the change MUST preserve:
1. **Dotted member access unchanged.** `a.b` still records the Table-member node
   (`nameRef != null` branch untouched). Regression assertion: the existing
   `TableTypeTest`, `QualifiedMemberResolutionTest`, `ReceiverAwareMemberResolutionTest`,
   `MemberFieldIndexTest` stay green.
2. **Non-array bracket access unchanged.** `t[k]` where `t` is a non-array Table (or
   `Undefined`) still yields `Undefined` (no new false type). New regression assertion:
   a subscript-inference unit test asserting `local t = {}; t[1]` → `Undefined` (unchanged).
3. **Existing global typing unchanged off Redis.** A file with no seeded target (STANDARD)
   seeds nothing; every current type test that does **not** set a Redis target is unaffected
   because `seedAmbientGlobals` no-ops when `getLibraryFiles(target)` yields no `global.lua`.
   Regression: the full `.../lang/types/*` suite (`TestLuaTypeEnginePhase1`,
   `TestFlowSensitiveType`, `CrossFileInferenceTest`, `UnionAndGenericTest`,
   `PrimitiveTypeCompatibilityTest`, `LuaUnionDistributionTest`, `FunctionSignatureMatchingTest`,
   `LuaMethodMembersTest`, `MultiReturnValueTest`, `TableTypeTest`, and the Union benchmark/spike
   tests) MUST stay green on the FULL suite run (not isolated `--tests`, per the synthetic-lambda
   masking note).
4. **`#` over arrays does not error** (the 3.1a/3.1b sub-fix): new regression assertion that
   `#arr` on a `string[]` produces `number` with **no** assignability error.

**Off-Redis no-leak (TC-KEYS-3) is now structural**, not a separate guard: with no Redis
target, `seedAmbientGlobals` seeds nothing → `KEYS` is undeclared and `getValueType` →
`Undefined`, exactly as before this change.

### 3.2 `Target` → spec resource path (§2.2)
- **Input → Output**: `Target` → resource path `String?`.
- **Steps**:
  1. If `target.platform != LuaPlatform.REDIS` **and** `target.platform.name != "VALKEY"`
     → return `null` (→ `RedisCommandSpec.EMPTY`).
  2. Else map `target.version.pathSegment` to a file:
     `redis-5 → commandspec/redis-5.json`, `redis-6 → commandspec/redis-6.json`,
     `redis-7 → commandspec/redis-7.json`; a Valkey path segment
     (`valkey-7.2`/`valkey-8`, added by REDIS-03) → `commandspec/valkey-8.json`.
  3. If the resource is absent on the classpath → return `null`.
- **Edge handling**: unknown/unbundled target → `EMPTY`, never an exception (TC-SPEC-2).
- **Note**: keying on `pathSegment` (already the resource key for stubs) avoids a second
  hand-maintained platform switch and lets REDIS-03 slot Valkey in by adding a row.

### 3.3 Command-name completion (AC-3)
- **Input → Output**: completion `position` → lookup elements added to the result set.
- **Steps**:
  1. `project = parameters.editor.project ?: return`.
  2. `site = RedisCallSiteMatcher.match(position) ?: return`; require
     `site.member ∈ {"call","pcall"}` and the completion position is inside
     `site.funcCall.nameAndArgsList[0]`'s **first** argument.
  3. `target = LuaProjectSettings.getInstance(project).state.getTarget()`; require the
     namespace matches the platform (`redis` for REDIS/VALKEY, `server` only for VALKEY).
  4. `spec = RedisCommandSpecService.getInstance().specFor(target)`; if `EMPTY` → return.
  5. For each `RedisCommandInfo` in `spec.commands.values` **filtered** by
     `sinceLe(info.since, target)` (§3.11): build
     `LookupElementBuilder.create(info.name).withTailText(" ${info.summary}", true)`; wrap in
     `PrioritizedLookupElement.withPriority(builder, 90.0)`; `result.addElement(...)`.
- **Edge handling**: non-literal first arg → `site.nameLiteral == null` → step 2 requires the
  position be inside a string literal, so a name-ref arg yields no site match (TC-COMP-3).
  Off Redis → step 3 mismatch → return (TC-COMP-4).

### 3.4 Unknown / arity check (AC-4) inside `visitFuncCall`
- **Input → Output**: `LuaFuncCall` → 0..1 `registerProblem` calls.
- **Steps**:
  1. `site = RedisCallSiteMatcher.match(o) ?: return`; require `site.member ∈ {call,pcall}`.
  2. `name = site.commandName ?: return` (dynamic → no flag; TC-UNK-2);
     `literal = site.nameLiteral ?: return` (a non-null `commandName` always has a backing
     literal, but bind it explicitly so no `!!` is needed for the `registerProblem` anchor).
  3. `target = …getTarget()`; require Redis/Valkey platform else return (no-op off Redis).
  4. `spec = specFor(target)`; if `EMPTY` return.
  5. `info = spec.lookup(name)`:
     - **null** → suppression check. `LuaInspectionSuppression.isSuppressed(ref: LuaNameRef,
       name, diagnosticId)` (`.../inspections/LuaInspectionSuppression.kt:43`) takes a
       `LuaNameRef`, but the command anchor here is a string-literal `LuaTerminalExpr`, not a
       name-ref. `isSuppressed` only reads `ref.containingFile` and `ref.textOffset`
       (`LuaInspectionSuppression.kt:44-45`), which every `LuaNameRef` in the enclosing call
       exposes — so drive the guard off the **call's own name-ref** (the `redis`/`server`
       root name-ref reached via `site.funcCall`), passing that `LuaNameRef` with
       `DIAGNOSTIC_ID="redis-unknown-command"`. If not suppressed, compute suggestions (§3.5)
       and `registerProblem(literal, "Unknown Redis command '$name'", WARNING, *renameFixes)`
       (`literal` bound non-null in step 2).
     - **non-null** → arity: Redis arity counts the command token itself, and a **negative**
       arity means "≥ |arity| args". `minArgs = if (info.arity < 0) -info.arity else info.arity`.
       If `site.argCount < minArgs` → `registerProblem(o.nameAndArgsList[0],
       "Redis command '$name' expects at least ${minArgs-1} argument(s), found
       ${site.argCount-1}", WARNING)` (subtracting the command token for the user-facing count).
  6. Determinism (§3.9) runs after, only for versions `"5"`/`"6"`.
- **Rules**: exactly one problem per call site max for the unknown/arity pair (unknown short-
  circuits before arity). String is upper-cased before lookup so `"get"`/`"GET"` both resolve.

### 3.5 Did-you-mean suggestion (AC-4)
- **Input → Output**: unknown `name` + `spec.names()` → ordered `List<String>` (≤ 3).
- **Steps**: compute the Levenshtein edit distance (classic two-row DP, O(m·n)) between
  `name` and each candidate in `spec.names()`; keep candidates with `distance ≤ 2`; sort by
  ascending distance then lexicographically; take the first 3. Each becomes one
  `LuaRedisRenameCommandQuickFix(candidate)`.
- **Edge handling**: empty result → `registerProblem` with no fixes (still a WARNING).
- **Complexity**: bounded by `spec.names().size` (≈ few hundred) × name length — negligible.

### 3.6 Command quick documentation (AC-5)
- **Input → Output**: `(file, offset)` → `List<DocumentationTarget>`.
- **Steps** (`RedisCommandDocumentationTargetProvider.documentationTargets`):
  1. `element = file.findElementAt(offset) ?: return emptyList()`; require its element type is
     the STRING token and it belongs to a `LuaTerminalExpr`.
  2. `site = RedisCallSiteMatcher.match(element) ?: return emptyList()`;
     `name = site.commandName ?: return emptyList()`.
  3. `target = …getTarget()`; `info = specFor(target).lookup(name) ?: return emptyList()`.
  4. Return `listOf(RedisCommandDocumentationTarget(info))`, a `DocumentationTarget` whose
     `computeDocumentation(): DocumentationResult?` returns
     `DocumentationResult.documentation("<b>NAME</b><br>summary<br>Since <since><br>Arity
     <arity>")` — the exact idiom `LuaDocumentationTargetProvider` uses
     (`.../lang/doc/LuaDocumentationTargetProvider.kt:237-241`). `computePresentation()`
     returns a `TargetPresentation` (see the same file's targets).

### 3.7 Sandbox-API inspection (AC-7) inside `visitNameRef`
- **Input → Output**: `LuaNameRef` → 0..1 `registerProblem`.
- **Steps**:
  1. `target = …getTarget()`; require Redis/Valkey platform else return.
  2. Consider only **root** name-refs whose resolved binding is a global (skip local decls;
     mirror `LuaDeprecatedApiInspection.isDeclaration`, `.../LuaDeprecatedApiInspection.kt:59`).
     Determine the accessed root symbol: for `io.read()` the root ref is `io`; for a bare
     `print(...)` the ref is `print`.
  3. `allow = RedisSandboxAllowlist.forTarget(project, target)` (§ below; `project` from
     `ref.project`). If the root symbol name is a
     blocked library (`io`, `os`, `require`, `dofile`, `loadfile`, `print`) **and** the
     specific member is not in the allowlist → `registerProblem(ref,
     "'<name>' is not available in the Redis script sandbox", WARNING)` (suppressible).
- **`RedisSandboxAllowlist.forTarget(project: Project, target: Target): Set<String>`**: the
  `Project` is required because the stub roots are read through `RuntimeLibraryProvider`, whose
  constructor takes a `Project` (`.../platform/target/RuntimeLibraryProvider.kt:16`) — the
  same acquisition idiom as `LuaLibraryProvider` (`RuntimeLibraryProvider(project)`,
  `.../lang/library/LuaLibraryProvider.kt:17`). Derived from the stub roots (RISK-R07 single
  source of truth): the set of top-level global/table names for which a `.lua` stub file
  exists under `runtime/redis/<seg>/` (`os`, `cjson`, `cmsgpack`, `struct`, `bit`, `redis`,
  plus `global.lua`'s `KEYS`/`ARGV`), **plus** the specific `os` members present in that
  version's `os.lua` (e.g. `time`, `clock`). Anything named that is a Lua stdlib library but
  has **no** stub file (`io`) or is a stdlib global not re-exported (`require`, `dofile`,
  `loadfile`, `print`) is blocked. Computed once per target via
  `RuntimeLibraryProvider(project).getLibraryFiles(target)`
  (`.../platform/target/RuntimeLibraryProvider.kt:38`) + a parse of `os.lua`'s
  `---@field`/`function os.x` names; cached per path segment.

### 3.8 Global-creation escalation (AC-8) — §2.8
Single branch at the `registerProblem` call: `type = if (isRedisTarget(project))
ProblemHighlightType.ERROR else ProblemHighlightType.GENERIC_ERROR_OR_WARNING`. All other
logic (resolve check, exemptions, suppression) unchanged.

### 3.9 Determinism rule (AC-9), Redis 5/6 only
- **Input → Output**: a `redis.call`/`pcall` site whose command is nondeterministic → 0..1
  WARNING.
- **Steps** (evaluated per matched call site in §3.4, after the arity block):
  1. Require `target.version.label ∈ {"5","6"}` (version-gated off for 7+; TC-DET-3).
  2. `info = spec.lookup(name)`; require `"nondeterministic" in info.flags`.
     `literal = site.nameLiteral ?: return` (bind the anchor explicitly; a nondeterministic
     match always has a literal command, but no `!!`).
  3. Determine the enclosing script scope = the `LuaFile`. Walk statements in **document
     order** from file start; classify each `redis.call`/`pcall` site via the same matcher:
     - a call whose spec `flags` contain `"write"` is a **write**;
     - a call to `redis.replicate_commands` (namespace `redis`, member
       `replicate_commands`) sets `guardSeen = true`.
  4. The current nondeterministic site is flagged iff, scanning the ordered site list:
     `guardSeen` is false at the point of this site **and** at least one **write** site occurs
     at a document offset **greater** than this site's offset (a write follows). If no write
     follows (TC-DET-4) or a guard preceded (TC-DET-2) → no flag.
  5. `registerProblem(literal, "Nondeterministic command '$name' called before a
     write under verbatim replication (Redis <7); call redis.replicate_commands() first",
     WARNING)` (`literal` bound non-null in step 2).
- **Rules**: ordering is by `PsiElement.textOffset`; only same-file scope; the guard check is
  "any guard call at a lower offset than this site". Implemented by collecting all matched
  sites for the file once per `visitFuncCall` pass is wasteful; instead collect the file's
  sites lazily via a `CachedValuesManager` keyed on the file (mirrors engine caching) or, at
  minimum, memoize per `buildVisitor` invocation.

### 3.10 Per-version stub trimming (§2.1)
- `redis-5`/`redis-6` copies of `redis.lua` drop members introduced later:
  `redis.setresp` (RESP3, Redis 6+ keeps it; drop from redis-5), `redis.acl_check_cmd`
  (Redis 7+; drop from 5 and 6). `redis.replicate_commands` is present in all (5/6/7).
  `os.lua` allowlist is identical across versions (time/clock). Exact per-member matrix is a
  §DR task if upstream disagreement is found; the safe default is: keep the union, since an
  extra stub only over-permits completion/allowlist, and RISK-R07 already ships sandbox at
  WARNING.

### 3.11 Version filter `sinceLe(since, target)`
- **Input → Output**: `(sinceString, target)` → `Boolean`.
- **Steps**: parse `since` as dotted integers (`"7.0.0" → [7,0,0]`); parse a target server
  version from `target.version.label` (`"5" → [5]`, `"6" → [6]`, `"7+" → [7]`; Valkey labels
  map to their Redis-compat baseline `[7,2]`). Compare component-wise (missing components =
  0); return `targetVersion >= since`. Used by completion (§3.3) so a command newer than the
  target is hidden (TC-COMP-2). The bundled per-version spec files are already trimmed to that
  version's surface, so this filter is belt-and-suspenders for specs that over-list.

## 4. External Data & Parsing

### 4.1 Bundled command spec — `src/main/resources/commandspec/<seg>.json`  [NEW format]
DR-02 source decision (recorded in risks-and-gaps §): **generate from the BSD-3-Clause Valkey
repository** `src/commands/*.json` at build/vendor time and reduce to the minimal schema
below. Rationale: Valkey's command surface is a superset of the shared Redis ≤7.2 baseline and
is unambiguously redistributable (BSD-3); Redis-repo `commands.json` (RSALv2/SSPLv1 ≥7.4,
AGPLv3 in 8) is **not** bundled. Per-version files are produced by filtering Valkey's spec on
each command's `since` ≤ the version (a vendoring script, not runtime code).

- **Format**: a single JSON object, command-name (upper-case) → info object:
  ```json
  {
    "GET":  { "arity": 2,  "since": "1.0.0",  "summary": "Get the value of a key.",
              "flags": ["readonly", "fast"] },
    "SET":  { "arity": -3, "since": "1.0.0",  "summary": "Set the string value of a key.",
              "flags": ["write", "denyoom"] },
    "TIME": { "arity": 1,  "since": "2.6.0",  "summary": "Return the server time.",
              "flags": ["nondeterministic", "loading", "fast"] }
  }
  ```
- **Schema** (each value): `arity` (`Int`, Redis convention — negative = "≥ |arity|"),
  `since` (dotted `String`), `summary` (`String`), `flags` (`Array<String>`; the only flags
  REDIS-04 reads are `"write"` and `"nondeterministic"`; others are retained verbatim for
  future use).
- **Parse strategy**: Gson `JsonParser.parseReader(resourceReader).asJsonObject`; for each
  entry read the four fields defensively (missing `summary` → `""`, missing `flags` → empty
  set, missing `arity` → `0`, missing `since` → `"0"`). Never `!!`; a malformed entry is
  skipped and logged via `com.intellij.openapi.diagnostic.Logger`.
- **Maps to**: `RedisCommandInfo` (§2.2). Command-name keys are stored upper-cased.
- **Failure handling**: unreadable/absent resource → `RedisCommandSpec.EMPTY`; a parse
  exception on the whole file → log + `EMPTY` (never crash the inspection/completion).

### 4.2 `redis.pcall` union return (AC-6) — stub annotation
Edit `runtime/redis/*/redis.lua`'s `redis.pcall`:
```lua
---@return any|{ err: string }
function redis.pcall(command, ...) end
```
`LuaCatsUnionType` (`gen/.../luacats/lang/psi/LuaCatsUnionType.java`) and
`LuaCatsLiteralTableType` (`literalTableType ::= '{' tableLiteralEntries? '}'`,
`luacats.bnf:182`) are already supported grammar, and `tableLiteralEntry ::= simpleType ':'
type` (`luacats.bnf:184`) parses `err: string`. The existing engine surfaces `err` as a member
so `if reply.err then` narrows (TC-PCALL-1). No engine change.

## 5. Data Flow

### Example 1: `redis.call("Gte", KEYS[1])` under Redis 7+
Editor open → `LuaRedisCommandInspection.visitFuncCall` → `RedisCallSiteMatcher.match` yields
`RedisCallSite(commandName="GTE", argCount=2, member="call")` → target is REDIS →
`specFor(7+).lookup("GTE")` = null → suggestions §3.5 = `["GET"]` (distance 1) →
`registerProblem(nameLiteral, "Unknown Redis command 'GTE'", WARNING,
LuaRedisRenameCommandQuickFix("GET"))`. User invokes the fix → `applyFix` under a write
command replaces the literal text with `"GET"`.

### Example 2: completion in `redis.call("SE<caret>")` under Redis 6
`LuaRedisCommandCompletionContributor` → matcher confirms first-arg literal site →
`specFor(redis-6)` → for each command with `since ≤ 6` add a lookup with summary tail text →
result includes `SET`, `SETEX`, `SETNX`, … (each with its summary); `SINTERCARD` (`since
7.0.0`) is absent.

### Example 3: `KEYS[1]` typing (post-§3.1a/§3.1b engine fix)
`buildSnapshot(file)` → `seedAmbientGlobals` (§3.1a) reads the Redis target's `global.lua`
stub, injects `KEYS`'s `@type string[]` via `LuaTypeGraphBridge` → root scope binds
`KEYS → Array(String)`. `visitNameRef("KEYS")` resolves to that node.
`visitIndexExpr` bracket branch (§3.1b) sees receiver `Array(String)`, flows `String` into the
subscript node → `getValueType(KEYS[1]) = string`. Off a Redis target `seedAmbientGlobals`
seeds nothing → `KEYS` undeclared, `getValueType(KEYS[1]) = Undefined` (TC-KEYS-3).

## 6. Edge Cases
- **Non-Redis target**: every REDIS-04 consumer reads the target first and returns early;
  the completion contributor / inspections are inert (TC-KEYS-3, TC-COMP-4, TC-SBX-3).
- **Missing bundled spec** (e.g. redis-5 file not yet vendored): `specFor` → `EMPTY`;
  completion/inspection degrade to no-ops, never crash (TC-SPEC-2).
- **`redis.call` with a computed first arg**: `commandName == null`; no unknown/arity/doc/
  completion (TC-UNK-2, TC-COMP-3).
- **Suppression**: unknown/arity/sandbox/global-escalation all honor
  `LuaInspectionSuppression` (TC-GLOB-2).
- **Determinism guard mid-script**: `redis.replicate_commands()` before the nondeterministic
  call suppresses the warning even if it is in a branch — the rule is "any guard at a lower
  offset" (conservative; false-negative-biased by design, WARNING-level).
- **`server.call` off Valkey**: with REDIS-03 absent there is no VALKEY platform, so the
  `server` namespace never matches — no false positives; the code path is dormant until
  REDIS-03 registers the platform.

## 7. Integration Points

```xml
<!-- plugin.xml, inside <extensions defaultExtensionNs="com.intellij"> -->

<!-- AC-2: command-spec application service -->
<applicationService
    serviceImplementation="net.internetisalie.lunar.analysis.redis.RedisCommandSpecService"/>

<!-- AC-3: command-name completion (sibling to LuaCompletionContributor) -->
<completion.contributor
    language="Lua"
    implementationClass="net.internetisalie.lunar.lang.completion.LuaRedisCommandCompletionContributor"/>

<!-- AC-4 + AC-9: unknown / arity / determinism inspection -->
<localInspection
    language="Lua"
    shortName="LuaRedisCommand"
    displayName="Redis command validity"
    groupPath="Lua"
    groupName="Redis"
    enabledByDefault="true"
    level="WARNING"
    implementationClass="net.internetisalie.lunar.analysis.redis.LuaRedisCommandInspection"/>

<!-- AC-7: sandbox-API inspection (ships WARNING per RISK-R07) -->
<localInspection
    language="Lua"
    shortName="LuaRedisSandbox"
    displayName="Redis script sandbox"
    groupPath="Lua"
    groupName="Redis"
    enabledByDefault="true"
    level="WARNING"
    implementationClass="net.internetisalie.lunar.analysis.redis.LuaRedisSandboxInspection"/>

<!-- AC-5: command quick documentation (sibling to LuaDocumentationTargetProvider) -->
<platform.backend.documentation.targetProvider
    implementation="net.internetisalie.lunar.analysis.redis.RedisCommandDocumentationTargetProvider"/>
```
- **AC-8** edits the already-registered `LuaGlobalCreation` inspection (`plugin.xml:180-187`);
  no new registration.
- **Inspection descriptions**: add HTML under
  `src/main/resources/inspectionDescriptions/LuaRedisCommand.html` and
  `LuaRedisSandbox.html` (existing convention:
  `src/main/resources/inspectionDescriptions/`).
- **Resources**: `src/main/resources/commandspec/{redis-5,redis-6,redis-7}.json` (§4.1);
  `src/main/resources/runtime/redis/{redis-5,redis-6}/…` stub dirs (§2.1).
- **No** hard `Project`/`Editor`/`PsiFile` fields are retained on any service; the app
  service holds only parsed data keyed by resource path (contract §4).

### Reusable seam for REDIS-05
- `RedisCommandSpecService` / `RedisCommandSpec` / `RedisCommandInfo` is the shared
  command-surface API; REDIS-05 (`redis.register_function` typing, function-library files)
  consumes `specFor(target)` for command completion/validation inside function bodies with no
  duplication.
- `RedisCallSiteMatcher` is the shared call-shape parser REDIS-05 reuses for `redis.call`
  inside function libraries.
- **Ambient-global seam for REDIS-05 suppression**: `KEYS`/`ARGV` are stub-declared globals
  (not engine-injected), and the escalated Global-creation rule keys on the target. REDIS-05's
  "function-library files must not see `KEYS`/`ARGV` and must allow module-scoped registration"
  is therefore a **scoped inspection/stub exemption**, not an engine change — REDIS-04 exposes
  the target-gating helper `isRedisTarget(project)`/`RedisSandboxAllowlist` and the
  stub-based typing that REDIS-05 narrows per-file (matching RISK-R08's chosen approach).

## 8. Requirement Coverage

| Requirement | Priority | Implemented by (section) |
|-------------|----------|--------------------------|
| AC-1 `KEYS`/`ARGV` typing | S | §2.1 (stubs) + §3.1a/§3.1b/§3.1c (REQUIRED engine work) |
| AC-2 command-spec service | S | §2.2, §3.2, §4.1 |
| AC-3 command completion | S | §2.3, §2.10, §3.3, §3.11 |
| AC-4 unknown/arity inspection + fix | S | §2.4, §2.5, §3.4, §3.5 |
| AC-5 command quick doc | S | §2.10, §3.6 |
| AC-6 `pcall` union narrowing | S | §2.1, §4.2 |
| AC-7 sandbox inspection | S | §2.7, §3.7 |
| AC-8 global-creation escalation | S | §2.8, §3.8 |
| AC-9 determinism inspection | S | §2.9, §3.9 |
| AC-10 unit tests | S | impl-plan Phase 7 + all TC-* |

## 9. Alternatives Considered
- **Engine scope-injection for `KEYS`/`ARGV`** (vs stub-declared globals): the original design
  rejected scope-injection believing stub-declared globals were consumed for free. Ground-truthing
  (REDIS-04-phase-1) proved otherwise — the type engine is single-file and never consults stub
  globals for inference (§3.1a root cause). The chosen approach (§3.1a) **seeds the root scope from
  the target's stub `global.lua` via the existing `RuntimeLibraryProvider` + `LuaTypeGraphBridge`**,
  reusing library-root invalidation (TARGET-04) rather than a bespoke ambient-injection service.
  This keeps the single source of truth in the bundled stub while acknowledging the engine work is
  real, not zero-surface.
- **AC-1 contract redesign** (assert only reference-resolution / no-undeclared, defer `string[]`
  indexing to a separate ticket) — the handoff's Option B: **rejected.** AC-1 is a `Must` that
  explicitly requires `KEYS[1] == string`; downgrading it silently drops acceptance. The engine
  work (§3.1a/§3.1b) is bounded and grounded, so Option A is taken.
- **Runtime `COMMAND DOCS`** (vs bundled spec): a live source would couple REDIS-04 to the
  connection stack (REDIS-01) and break the "fully parallel, engine-only" scope. Bundled
  BSD-Valkey-derived JSON keeps REDIS-04 independent; runtime augmentation is future work
  (open gap in risks §).
- **Editing `LuaCompletionContributor` in place** (vs sibling): rejected — a sibling keeps
  Redis logic isolated and trivially target-gated, matching the existing multi-contributor
  registration style (`plugin.xml` already registers `JsonSchemaCompletionContributor` and
  `LuaCompletionContributor` side by side).

## 10. Open Questions

None left unresolved for the implementer — the two §3.1 engine sub-decisions are pinned with defaults and tracked as de-risking tasks REDIS-04-DR-03a (array-element propagation seam) and REDIS-04-DR-03b (target-switch cache invalidation) in risks-and-gaps.md, alongside DR-02 (spec source) and the per-version stub matrix (Gap 2.1).
