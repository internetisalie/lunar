---
id: "MAINT-30-DESIGN"
title: "Technical Design"
type: "design"
parent_id: "MAINT-30"
folders:
  - "[[features/maint/30-resolution-caching/requirements|requirements]]"
---

# Technical Design: MAINT-30 — Indexing & Resolution Caching

## 1. Architecture Overview

### Current State

Four resolution/indexing seams carry the defects this feature closes (all re-verified against
`main` @ `828db71d`, 2026-07-17):

- **`LuaFileBindingsIndexer.extractBindingsFromStatement`**
  (`lang/indexing/LuaFileBindingsIndex.kt:354-381`) walks every statement with a
  `PsiRecursiveElementVisitor` and records **every** `PsiNamedElement` (`:364-375`), keyed only by
  first-seen name (`visited` set). Because a `LuaNameRef` **usage** is a `PsiNamedElement`, a file
  that merely *reads* a global (`print(foo)`) records a `foo` binding. `LuaNameReference.collectFileResults`
  (`lang/LuaNameReference.kt:176-187`) then resolves a cross-file `foo` to that usage's leaf
  (`findElementAt(binding.textOffset)`), so Go-to-Declaration / Find-Usages can land on a *usage*
  in an unrelated file (review #20, P1 #3-adjacent).
- **`FileUserData`** (`lang/psi/FileUserData.kt:7-20`) is a hand-rolled memo keyed on
  `psiFile.fileDocument.text.hashCode()`. It re-hashes the whole document on every access, is stale
  after a reparse that leaves text equal-length-but-changed only if the hash collides, and is a
  duplicate of what `CachedValuesManager` provides for free (review #21). Its only two consumers are
  `LuaTypesSnapshot.forFile` (`lang/psi/types/LuaTypes.kt:140-157`, which does NOT call
  `cacheFileUserData` — it inlines the same pattern with a target-folded key) and the extension
  function `cacheFileUserData` itself (currently **zero** callers — grep `cacheFileUserData` returns
  only the definition).
- **`LuaNameReference.multiResolve`** (`lang/LuaNameReference.kt:36-137`) runs the full Phase-1 scope
  walk + Phase-2 stub/index queries on **every** call with **no** `ResolveCache`. The platform
  re-invokes `multiResolve` per highlight pass, per Find-Usages probe, per completion filter — each a
  full bindings iteration (review §2.5.2).
- **Copy-paste drift** (review §2.5.3), re-verified for what *actually* remains after ROCKS-16 /
  REDIS-06 / MAINT-28's earlier passes — see §3.5.

### Prior Art in This Repo

Searched `grep -rn` across `src/main/kotlin` for each seam:

| Existing component | Location | This design |
| :--- | :--- | :--- |
| `LuaResolveUtil.scopeCrawlUp` | `lang/psi/LuaResolveUtil.kt:9-41` | **The canonical scope walk — EXTEND** (used by REDIS-06 `LuaRedisSandboxInspection`, `LuaShadowingVariableInspection`). `LuaNameReference.multiResolve:42-73` inlines a **near-identical** copy that differs only in passing `element` instead of `prev` as `lastParent` for the non-for cases and in stopping on a `LuaScopeProcessor.result`. MAINT-30 makes `multiResolve` call `scopeCrawlUp` (§2.1). |
| `LuaScopeProcessor` / `LuaCompletionScopeProcessor` | `lang/LuaScopeProcessor.kt:25-239` | **`LuaScopeProcessor` — EXTEND** (the stop-on-first-match processor `multiResolve` already feeds; §2.1). `LuaCompletionScopeProcessor` is a **reference model only, not reused**: the §3.1 filter matches the same *set* of declaration-container kinds it enumerates, but §3.1 hand-rolls its **own** top-level container walk (it must also emit method-qualified names `<recv>.<m>`/`<recv>:<m>`, which `LuaCompletionScopeProcessor` does **not** — it records only the plain `funcName` + `"self"`, `LuaScopeProcessor.kt:185-191`). No code is shared; §3.1 is a parallel enumeration by design. |
| `LuaCrossFileCompletionProvider.extractRequires` | `lang/completion/LuaCrossFileCompletionProvider.kt:101-119` | **The canonical require extractor — EXTEND.** Already reads the FileBindings index via `FileBasedIndex.getValues(LuaFileBindingsIndexName, …)` + `CachedValuesManager`. The two AST-walking copies (`LuaNameReference.kt:189-246`, `LuaFileBindingsIndex.kt:285-340`) converge onto this index-backed helper (§2.4). |
| `LuaRocksEnvironment` | `rocks/LuaRocksEnvironment.kt:23-64` | **EXTEND.** ROCKS-16 already centralized `resolveExecutable` / `resolveServer` / `withServer`; the remaining dedup is a thin `command(project, subArgs)` builder (§2.6). |
| `LuaTypeManagerImpl.doResolveModule` | `lang/psi/types/LuaTypeManagerImpl.kt:107-146` | **The canonical module→file resolver — EXTEND.** `LuaRequireReference.resolve` (`lang/LuaRequireReference.kt:19-49`) is a second copy with a refresh-flag divergence (§3.6). |
| `ResolveCache` | platform `com.intellij.psi.impl.source.resolve.ResolveCache` | **ADOPT** — no existing Lunar usage of `resolveWithCaching` (only `dropResolveCaches()` at `settings/LuaApplicationSettingsPanel.kt:66`). |

No component is duplicated by this design; every new helper replaces ≥2 existing copies.

### Target State

- `LuaFileBindingsIndexer` records **only file-scope declarations** (§3.1), bumping the index version
  → one full re-index of Lua files (§3.2).
- `FileUserData` is deleted; `LuaTypesSnapshot.forFile` re-keys onto `CachedValuesManager`
  preserving the target-folded key and the TYPE-10 reentrancy guard (§2.3).
- `LuaNameReference.multiResolve` delegates to `ResolveCache.resolveWithCaching` via a static
  `PolyVariantResolver` (§2.2), and its scope walk collapses onto `LuaResolveUtil.scopeCrawlUp` (§2.1).
- One require extractor (§2.4), one module resolver (§2.5), one `LuaRocksEnvironment.command` (§2.6),
  and the method-chain provider migrated to `resolveMember` (§2.7).

## 2. Core Components

### 2.1 `net.internetisalie.lunar.lang.psi.LuaResolveUtil` (extended)

- **Responsibility**: sole owner of the bottom-up scope walk. `multiResolve` stops inlining it.
- **Threading**: read action (invoked from `multiResolve`, itself under the platform read lock).
- **Collaborators**: `LuaScopeProcessor` (`lang/LuaScopeProcessor.kt:25`), `PsiScopeProcessor.processDeclarations`.
- **Key API** (unchanged signature; `multiResolve` becomes a caller):
  ```kotlin
  object LuaResolveUtil {
      fun scopeCrawlUp(processor: PsiScopeProcessor, element: PsiElement): Boolean // false == matched & stopped
  }
  ```
  `multiResolve` Phase-1 becomes: `val processor = LuaScopeProcessor(name); LuaResolveUtil.scopeCrawlUp(processor, element); processor.result?.let { return arrayOf(PsiElementResolveResult(it)) }`.
  The existing inline loop (`LuaNameReference.kt:42-73`) is deleted. See §3.3 for the `lastParent`
  equivalence proof that makes this safe.

### 2.2 `net.internetisalie.lunar.lang.LuaNameReference` (edited)

- **Responsibility**: poly-variant reference for a bare/dotted Lua name; now caches through `ResolveCache`.
- **Threading**: read action; `resolveWithCaching` asserts read access for physical files.
- **Collaborators**: `com.intellij.psi.impl.source.resolve.ResolveCache`, `ResolveCache.PolyVariantResolver<LuaNameReference>`.
- **Key API**:
  ```kotlin
  class LuaNameReference(element: PsiElement, textRange: TextRange) :
      PsiReferenceBase<PsiElement?>(element, textRange), PsiPolyVariantReference {

      override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
          val hostElement = myElement ?: return ResolveResult.EMPTY_ARRAY
          return ResolveCache.getInstance(hostElement.project)
              .resolveWithCaching(this, RESOLVER, /* needToPreventRecursion = */ false, incompleteCode)
      }

      private fun doMultiResolve(incompleteCode: Boolean): Array<ResolveResult> {
          RESOLVE_INVOCATIONS.incrementAndGet()   // first statement of the body — TC-03 observation seam
          /* today's body (Phase-1 replaced per §2.1) */
      }

      companion object {
          private val RESOLVER =
              ResolveCache.PolyVariantResolver<LuaNameReference> { ref, incomplete -> ref.doMultiResolve(incomplete) }

          /** TC-03 observation seam: counts entries into [doMultiResolve] (the un-cached compute path). */
          @org.jetbrains.annotations.VisibleForTesting
          internal val RESOLVE_INVOCATIONS = java.util.concurrent.atomic.AtomicInteger()
      }
  }
  ```
  The entire current `multiResolve` body (`:37-136`) moves verbatim into `doMultiResolve`, except its
  Phase-1 scope walk which is replaced per §2.1. `RESOLVER` is a **static singleton** (contract §1
  UPPER_SNAKE_CASE; a per-call lambda would defeat the cache-key identity). `needToPreventRecursion`
  is `false` — Lua name resolution has no reference→reference recursion (Phase-2 uses the stub index,
  not `.resolve()`), matching how `require` module resolution is already guarded separately.
- **Test observation seam (TC-03)**: because `doMultiResolve` is `private` and has no return-value hook a
  test can count, add a companion-object counter `RESOLVE_INVOCATIONS: AtomicInteger` (visibility
  `internal`, annotated `@org.jetbrains.annotations.VisibleForTesting` — the idiom already used at
  `LuaTypeManagerImpl.kt:274`), incremented as the **first statement** of `doMultiResolve`. The counter
  lives on `LuaNameReference.Companion`, so a test reads it as `LuaNameReference.RESOLVE_INVOCATIONS.get()`.
  It counts only the *un-cached* compute path (a `ResolveCache` hit never calls the resolver), so a repeat
  `multiResolve` with no intervening PSI change must leave the count unchanged. See requirements TC-03 for
  the exact assertion sequence.

### 2.3 `net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot` (edited) + delete `FileUserData`

- **Responsibility**: preserve snapshot memoization while removing the hand-rolled `FileUserData`.
- **Threading**: read action (existing).
- **Collaborators**: `com.intellij.psi.util.CachedValuesManager`, `CachedValueProvider.Result`,
  `com.intellij.psi.util.PsiModificationTracker`, `LuaTypesVisitor` (`KEY`, `inProgressSnapshot`,
  `buildSnapshot`), `LuaProjectSettings`.
- **Key API** — `forFile` re-keyed (algorithm §3.4):
  ```kotlin
  fun forFile(file: PsiFile): LuaTypes {
      val psiFile = file.containingFile
      LuaTypesVisitor.inProgressSnapshot(psiFile)?.let { return it }   // TYPE-10 guard — UNCHANGED, runs first
      return CachedValuesManager.getCachedValue(psiFile, SNAPSHOT_KEY) {
          val target = LuaProjectSettings.getInstance(psiFile.project).state.getTarget()
          CachedValueProvider.Result.create(
              LuaTypesVisitor.buildSnapshot(psiFile),
              psiFile, PsiModificationTracker.MODIFICATION_COUNT, LuaProjectSettings.getInstance(psiFile.project),
          )
      }
  }
  ```
  `SNAPSHOT_KEY: Key<CachedValue<LuaTypes>>` replaces `LuaTypesVisitor.KEY` (`LuaTypesVisitor.kt:899`,
  currently `Key<FileUserData<LuaTypes>>`). `FileUserData.kt` is **deleted** (its `cacheFileUserData`
  has no callers; its only data-class use is here). The imports of `FileUserData` at `LuaTypes.kt:5`
  and `LuaTypesVisitor.kt` are removed. **Memoization semantics are preserved, not weakened** — see §3.4
  and the MAINT-28 §2.5.5 dependency note in §7.

### 2.4 Require extraction — single index-backed helper

- **Responsibility**: one function returning a file's `require(...)` module names.
- **Threading**: read action; the `CachedValuesManager` value is `MODIFICATION_COUNT`-invalidated.
- **Collaborators**: `LuaCrossFileCompletionProvider.extractRequires`
  (`lang/completion/LuaCrossFileCompletionProvider.kt:101-119`) is promoted to a top-level function
  `net.internetisalie.lunar.lang.indexing.fileRequires(file: LuaFile): List<String>` in a new file
  `lang/indexing/LuaRequireExtraction.kt`.
- **Key API**:
  ```kotlin
  // lang/indexing/LuaRequireExtraction.kt
  fun fileRequires(file: LuaFile): List<String>   // reads LuaFileBindingsIndexName record.requires via CachedValuesManager
  ```
  `LuaNameReference.extractRequires`/`extractRequiresFromStatement` (`:189-246`) are **deleted**;
  `multiResolve`'s Phase-2 call `extractRequires(element.containingFile)` (`:89`) becomes
  `fileRequires(element.containingFile as LuaFile)` (guard non-`LuaFile` → `emptyList()`).
  `LuaCrossFileCompletionProvider` calls `fileRequires` instead of its private copy. The **indexer's**
  own `extractRequires` (`LuaFileBindingsIndex.kt:285-340`) **stays** as the raw AST walk — it *is*
  the index that `fileRequires` reads, so it cannot read itself (no circularity). §3.5 records this.

### 2.5 Module→file resolution — single helper

- **Responsibility**: resolve a `require` module name to its `LuaFile`, one code path.
- **Threading**: read action (VFS + PSI).
- **Collaborators**: `PathConfiguration.getProjectSourcePathPatterns`, `FilenameIndex`, `PsiManager`.
- **Key API** — extract the shared **file-finding** body of `LuaTypeManagerImpl.doResolveModule`
  (`:107-142`) into a top-level function that yields **all** candidate files in pattern order (not just
  the first found). This is required because `doResolveModule` does **not** return the first *found*
  file: when a pattern matches a file but `getModuleType(psiFile, context)` yields `null`
  (`LuaTypeManagerImpl.kt:122`), it **continues** to the next pattern (`continue` at `:119`, and the
  `getModuleType?.let { return it }` gate). A `resolveModuleFile(): LuaFile?` returning the first found
  file would change that (it would stop at the first found-but-untyped file and never try later
  patterns). So the shared helper yields a lazy sequence and each caller applies its own terminal:
  ```kotlin
  // lang/path/LuaModuleFileResolver.kt
  // All candidate LuaFiles for `moduleName`, in resolution order: source-path patterns first
  // (PathConfiguration.getProjectSourcePathPatterns), then the FilenameIndex fallback
  // (fileName + "init.lua", filtered by expectedPathPart/expectedInitPathPart/no-dot). refresh=false (§3.6).
  fun resolveModuleCandidates(project: Project, moduleName: String): Sequence<LuaFile>
  ```
  Per-caller usage:
  - `LuaRequireReference.resolve` (`:19-49`) — returns the **first found** file (it has no type gate):
    `return resolveModuleCandidates(element.project, moduleName).firstOrNull()`.
  - `LuaTypeManagerImpl.doResolveModule` (`:107-142`) — keeps its `getModuleType`-gated selection over
    the candidates, preserving the "skip untyped, try next pattern" semantic:
    `resolveModuleCandidates(project, moduleName).firstNotNullOfOrNull { getModuleType(it, context) } ?: LuaPrimitiveType.ANY`.

  `resolveModuleCandidates` is a `Sequence` (lazy) so `firstOrNull()` short-circuits after the first
  match for the require-reference caller, and `firstNotNullOfOrNull` only materializes `getModuleType`
  for as many candidates as needed for the type caller. The refresh-flag divergence (`findVirtualFile`
  uses `true` at `LuaTypeManagerImpl.kt:146`; `LuaRequireReference` uses `false` at `:25`) is resolved to
  `false` inside the shared helper (§3.6).

### 2.6 `net.internetisalie.lunar.rocks.LuaRocksEnvironment.command` (added)

- **Responsibility**: one place that assembles the effective luarocks `GeneralCommandLine`.
- **Threading**: EDT-safe state reads (per the class contract); caller runs the command off-EDT.
- **Collaborators**: `resolveExecutable`, `resolveServer`, `withServer` (already in the object),
  `com.intellij.execution.configurations.GeneralCommandLine`.
- **Key API**:
  ```kotlin
  /** null when no luarocks executable resolves (caller surfaces the configure hint). */
  fun command(project: Project?, subArgs: List<String>): GeneralCommandLine? {
      val exe = resolveExecutable(project) ?: return null
      val args = withServer(subArgs, resolveServer(project))
      return GeneralCommandLine(exe, *args.toTypedArray())
  }
  ```
  Migrate the `resolveExecutable(project) ?: <err>; GeneralCommandLine(exe, …)` sites that currently
  re-inline this (paths given with subpackage):
  - `rocks/browser/LuaRocksInstalledService.kt:26-28`
  - `rocks/browser/LuaRocksInstallExecutor.kt:59-60`
  - `rocks/build/WorkspaceBuildRunner.kt:29`
  - `rocks/browser/LuaRocksSearchService.kt:52-56` and `:86-93`
  - `rocks/browser/LuaRocksMetadataService.kt:33,39` — `resolveExecutable(project) ?: return null` then
    `GeneralCommandLine(buildList { add(exe); add("show"); … })`; migrates to
    `command(project, listOf("show", "--porcelain", name) + version-tail)` (its `?: return null` degrade
    path is preserved by `command`'s null return).

  **Out of scope** (build different command shapes, not the plain `GeneralCommandLine(exe, *subArgs)`
  form `command()` produces; §3.5 records this):
  - `rocks/run/LuaRocksRunConfiguration.kt:189,218` — its own `buildCommandLine` with a user-set command
    list.
  - `rocks/publish/PublishRockAction.kt:64` — resolves the exe but hands it to `RockUploadCommand.build(exe,
    rockspecPath, apiKey, server)` (`RockUploadCommand.kt:42` builds `GeneralCommandLine(luarocksBinary)`
    with upload-specific args + `--api-key`); it is the `RockUploadCommand` shape, already server-aware.
    (`PublishRockAction` was last touched by BUG-376; its current shape confirmed — it does **not** inline
    the `command()` shape.)
  - `rocks/publish/RockUploadCommand.kt` — already assembles its own upload command (`withServer`-aware).

### 2.7 `net.internetisalie.lunar.lang.insight.hint.LuaMethodChainInlayHintProvider` (edited)

- **Responsibility**: resolve a chain step's method through the type engine's `resolveMember`, not a
  raw stub-index probe.
- **Threading**: read action (inlay collector).
- **Collaborators**: `LuaTypeManager.getInstance(project).resolveType(className, contextFile)`
  returning `LuaClassType`; `LuaClassType.resolveMember(name): LuaTypeMember?` →
  `LuaFunctionType` (per the `.agents/AGENTS.md` "Type engine" idiom).
- **Key API** — replace `findMethodDecl` (`:131-140`) + `annotatedReturnNames`/`inferredReturnNames`
  (`:143-158`) with a `resolveMember`-based lookup:
  ```kotlin
  private fun resolveStepType(receiverClass: String?, methodName: String, types: LuaTypes, file: LuaFile): StepType
  // classType = LuaTypeManager.getInstance(file.project).resolveType(receiverClass, file) as? LuaClassType
  // member = classType?.resolveMember(methodName)  // LuaFunctionType
  // returnNames derived from member's return LuaType.name (self already resolved to receiverClass by the engine)
  ```
  `MAINT-30-04` is priority **C**; if `resolveMember` cannot reproduce the multi-return names the
  stub path yields, this is the DR-03 spike (risks) — the stub path stays as a documented fallback.

## 3. Algorithms

### 3.1 Declaration-only file-bindings filter (MAINT-30-01)

- **Input → Output**: a `LuaFile` PSI tree → `List<LuaBinding>` of *file-scope declaration* names only.
- **Reality constraint**: Lua has **no declaration PSI for locals/funcs/params** — a declared name is a
  `LuaNameRef` inside a declaration *container* (per `.agents/AGENTS.md` / memory "Lua declarations are
  LuaNameRef"). So the filter cannot test "is this a decl PSI"; it must match the **container** kinds
  that introduce file-scope names. This filter is a **new, self-contained container walk** (it does not
  call `LuaCompletionScopeProcessor`); it matches the same container *set* that processor enumerates
  (`LuaScopeProcessor.kt:158-230`) **and additionally** emits the method-qualified name for
  `LuaFuncDecl` with a `funcNameMethod` (step 2 below) — which that processor does not produce.
- **Steps** (replace `extractBindingsFromStatement`, `LuaFileBindingsIndex.kt:354-381`):
  1. Iterate `file.getBlockList()` → each `block.statementList` (top-level statements only; do **not**
     recurse into nested function/for bodies — those are not file scope).
  2. For each top-level statement `stmt`, match its **container** kind and emit a `LuaBinding(name,
     nameLeaf.textOffset, 0)` for each declared name leaf:
     - `LuaLocalVarDecl` → each `attNameList[i].nameRef.identifier`
     - `LuaLocalFuncDecl` → `nameRef.identifier`
     - `LuaGlobalVarDecl` → each `attNameList[i].nameRef.identifier`
     - `LuaGlobalFuncDecl` → `nameRef.identifier`
     - `LuaFuncDecl` → `funcName.nameRef.identifier` (and, when `funcName.funcNameMethod != null`, the
       dotted/colon qualified name `<recv>.<m>` / `<recv>:<m>` at `funcNameMethod.nameRef.identifier.textOffset`)
     - `LuaAssignmentStatement` → for each `varList.varList[i]`, if `var.nameRef != null` **and** the
       var has no `varSuffixList` (a bare `x = …`, i.e. a global assignment, not `t.f = …`):
       `var.nameRef.identifier`
  3. De-duplicate by name (keep first offset), as today (`visited` set).
- **Rules / edge handling**: a bare `LuaNameRef` **usage** (`print(foo)`) is **not** one of the
  matched containers → **not** recorded (this is the #20 fix). A dotted assignment `t.f = 1` is
  reached only via the qualified-name path (member-field index owns it, `LuaMemberFieldNavigation`),
  so the bare `t`/`f` are not file-scope bindings. Empty file → empty list.
- **Complexity**: O(top-level statements), strictly less than the current full recursive walk.
- **Why restricting `record.bindings` to declarations is safe (reader enumeration)**: the shrunk binding
  set has exactly **one** consumer, so the blast radius is bounded and known:
  - `record.bindings` (the `FileBindingsRecord` field) is read by **one** site only —
    `LuaNameReference.collectFileResults` (`LuaNameReference.kt:176-187`, the `filesQueryResult.bindings.forEach`
    at `:182`), the cross-file Phase-2 resolver that TC-01 targets. (Grep for `.bindings` over
    `src/main/kotlin` otherwise hits only the index's own serialize/read in `LuaFileBindingsIndex.kt` and
    the unrelated *toolchain-settings* `State.bindings` map — neither is a `FileBindings` consumer.)
  - `LuaCrossFileCompletionProvider` reads **only** `record.requires` (`LuaCrossFileCompletionProvider.kt:111`),
    never `record.bindings` — cross-file completion is unaffected.
  - `LuaDescriptionIndex` does **not** read the FileBindings record at all (separate stub index).
  - `LuaFileBindingsIndexTest` asserts **only** on `.requires` (`:54-68`), never on `.bindings` — no test
    pins the old usage-inclusive binding set, so shrinking it breaks no existing assertion. TC-01/TC-07
    add the new declaration-only assertions.

### 3.2 Index version bump & re-index (MAINT-30-01)

- `LuaFileBindingsIndex.VERSION` (`LuaFileBindingsIndex.kt:112`) is bumped `2 → 3`. `getVersion()`
  (`:91-93`) returns it. The platform detects the version delta and **fully rebuilds** the
  `lunar.FileBindings` index for all `.lua` files on next project open (one-time; visible as a
  background "Indexing" pass). No data migration is possible or needed — the record shape
  (`LuaFileBindingsRecord`) is unchanged; only the *set* of bindings shrinks. This is honest and
  mandatory: a stale-version index would keep serving usage-bindings.

### 3.3 `lastParent` analysis (the collapse is a deliberate, correct semantic change)

**The premise the old draft used ("`LuaScopeProcessor` ignores `lastParent`") is false.** `lastParent`
is not consumed by the processor — it is consumed by the *scope containers* as the early-binding
(forward-reference) cutoff:

- `LuaBlock.processDeclarations` breaks the statement loop at
  `if (lastParent != null && statement.textOffset >= lastParent.textOffset) break` (`LuaBlockExt.kt:34`).
- `LuaFile.processDeclarations` applies the same cutoff over its children
  (`LuaFile.kt:51`), then delegates to each block with the same `lastParent` (`LuaFile.kt:70`).

So `lastParent` decides **which statements in the enclosing block are visible** to the reference. The
two walks pass *different* `lastParent` for the enclosing block:

- inline `multiResolve` passes `element` — the **deep reference** itself (`LuaNameReference.kt:51`, and
  `:52-54` for `LuaFuncDef`/`LuaFuncDecl`/`LuaLocalFuncDecl`).
- `scopeCrawlUp` passes `prev` — the **immediate child of `current` that the walk ascended from**
  (`LuaResolveUtil.kt:16-19`). When `current` is the block enclosing the reference, `prev` is the
  top-level statement that contains the reference.

**These diverge on exactly one case: whether the reference's own enclosing statement is processed.**
Work the self-referential initializer `local x = x` (the RHS `x` referencing the `x` being declared):

- The enclosing block iterates its statements. Reaching the `LuaLocalVarDecl` statement (`local x = x`),
  whose `statement.textOffset` is the offset of the `local` keyword (the statement start):
  - **inline walk** (`lastParent = element`, the RHS `x` leaf): cutoff test
    `statement.textOffset >= element.textOffset` → `local`-offset `>=` RHS-`x`-offset → **false** (the
    statement starts *before* the RHS `x`), so the statement is **not** cut off → the block calls
    `processor.execute(LuaLocalVarDecl)` → `LuaScopeProcessor.execute` matches `attName.nameRef.identifier.text == "x"`
    (`LuaScopeProcessor.kt:37-45`) → **the RHS `x` resolves to the just-declared local `x`.**
  - **`scopeCrawlUp`** (`lastParent = prev`, where `prev` == that same `LuaLocalVarDecl` statement, since
    the walk ascended *from* it into the block): cutoff test `statement.textOffset >= prev.textOffset`
    → `prev` **is** the statement → `offset >= offset` → **true** → **break** → the declaring statement is
    **not** processed → the RHS `x` resolves to an outer/undeclared `x` (Phase-2 / null).

**Verdict: the two walks DIVERGE on `local x = x`-style self-referential initializers.** The collapse is
therefore a **semantic change**, not an equivalence — it must be adopted explicitly on its merits:

- **`scopeCrawlUp`'s exclusion is the *correct* Lua behavior.** Lua 5.1–5.4 §3.3.3 / §3.5: the scope of a
  `local` starts *after* its declaration, so the RHS of `local x = x` sees the **outer** `x`, not the new
  local. The inline walk's inclusion is the bug; adopting `scopeCrawlUp` fixes it.
- **This corrected semantic is already the one the codebase relies on elsewhere.** `scopeCrawlUp` is the
  walk used by `LuaRedisSandboxInspection` (`LuaRedisSandboxInspection.kt:150`) and
  `LuaShadowingVariableInspection` (`LuaShadowingVariableInspection.kt:67`), and it is **pinned** by
  `LuaRedisSandboxInspectionTest.testSelfShadowInitializerStillFlagsRhs` (`:217-226`): for
  `local print = print`, the RHS `print` "is the genuine global (not yet in scope)" and must still be
  flagged. That test asserts precisely that `scopeCrawlUp` does **not** resolve the RHS to the new local.
- **No existing test pins the old inline (RHS-resolves-to-self) behavior.** Grep for self-referential
  initializer resolution over `src/test/kotlin` finds only `testSelfShadowInitializerStillFlagsRhs`
  (which pins the *corrected* semantic, above). The reference/scope-resolution tests pin only
  forward-reference exclusion (`LuaScopeResolveTest.testForwardReferenceDoesNotResolveToLaterLocal`,
  `:19-26`) and after-declaration resolution (`:40-48`) — neither is the self-referential-RHS case, and
  both remain green under `scopeCrawlUp` (a genuine forward reference `print(x); local x` still has
  `prev` == the `print(x)` statement, whose offset is `<` the later `local x`, so `local x` is cut off).

**Adoption + new test.** MAINT-30 adopts `scopeCrawlUp` in `multiResolve` (§2.1) and adds **TC-02** to
lock the corrected semantic on the reference path (Go-to-Declaration of the RHS `x` in `local x = x`
must **not** resolve to the enclosing `local x` — it resolves elsewhere or to null, matching Lua scope
rules). TC-01 regression-locks the declaration-only-index fix; TC-02 regression-locks this scope-walk
correction. All other cases (nested-block outer-local, after-declaration, for-loop variable) are
unaffected: the for-loop case passes `prev ?: element` **identically** in both walks
(`LuaNameReference.kt:59-60` == `LuaResolveUtil.kt:20-21`), and the "also process the file itself if
`result == null`" tail exists in both (`LuaNameReference.kt:71-73`; `LuaResolveUtil.kt:33-38`, the latter
now also passing `prev` — consistent with the corrected cutoff).

### 3.4 Snapshot re-keying preserves memoization (MAINT-30-02, TYPE-10 & REDIS-04 safe)

- **Input → Output**: `PsiFile` → cached `LuaTypes`, recomputed only when file structure OR active
  target changes.
- **Steps**: `inProgressSnapshot(psiFile)` is checked **first, unchanged** (TYPE-10 reentrancy — a
  `visitFuncCall` that re-enters `forFile` during a build still short-circuits to the partially-built
  visitor; `CachedValuesManager` is never reached re-entrantly). Otherwise `getCachedValue` returns the
  memo or rebuilds via `buildSnapshot`.
- **Target folding**: the old key folded the target hash into the text hash (`LuaTypes.kt:153-157`).
  The replacement lists `LuaProjectSettings.getInstance(project)` as a **dependency** in
  `Result.create`. `LuaProjectSettings` is a `PersistentStateComponent`; a target switch mutates its
  state. To make that observable to `CachedValuesManager`, `LuaProjectSettings` must expose a
  `ModificationTracker` (see DR-01) — if it already increments a modification count on state change,
  pass that tracker as a `Result.create` dependency. **Grounding**: as of `main` @ `828db71d`,
  `LuaProjectSettings` (`settings/LuaProjectSettings.kt:18`) is a `PersistentStateComponent` that does
  **not** implement `ModificationTracker` and exposes no mod-count (grep confirms no `ModificationTracker`
  / `incModificationCount` in the class). **Critically, `PsiModificationTracker.MODIFICATION_COUNT` does
  NOT bump on a text-free target switch** — switching the active target mutates settings state, not the
  PSI tree, so a `MODIFICATION_COUNT`-only dependency would serve a stale, wrongly-seeded snapshot across
  a target switch (the exact REDIS-04 §3.1a regression, TC-04).
  - **If DR-01 adds a tracker** to `LuaProjectSettings` (leaning): list it as a `Result.create`
    dependency alongside `psiFile` + `MODIFICATION_COUNT`; `CachedValuesManager` then drops the memo on
    a target switch automatically.
  - **Fallback if no tracker is added**: `CachedValuesManager` cannot observe the switch, so the target
    hash must be compared on **every** `forFile` call. `forFile` computes the current
    `31 * textHash + target.hashCode()` key (as today, `LuaTypes.kt:153-157`) and — before returning the
    `getCachedValue` result — verifies the cached snapshot was built under the same key; on mismatch it
    drops and rebuilds. This means the target-hash compare runs on **every** `forFile` invocation (it is
    cheap: one `getTarget()` + two `hashCode()`), which is the price of not having a tracker. DR-01
    decides which branch ships; TC-04 locks the target-switch invalidation either way.
- **Edge**: physical vs non-physical files — `CachedValuesManager.getCachedValue(psiFile, …)` handles
  both; `MODIFICATION_COUNT` covers reparse (the #21 staleness bug is structurally impossible now).

### 3.5 §2.5.3 dedup — what actually remains (re-verified)

Re-grounded 2026-07-17. Original review listed six clusters; current truth:

| Cluster | Original claim | Verified current state | MAINT-30 action |
| :--- | :--- | :--- | :--- |
| Scope walk ×3 | 3 copies | `scopeCrawlUp` (canonical) + inline copy in `multiResolve:42-73`. REDIS-06's `LocalBindingScopeProcessor` *consumes* `scopeCrawlUp` (not a copy). So **2 walk sites**, 1 duplicated. | Collapse `multiResolve` onto `scopeCrawlUp` (§2.1) → 1 walk. |
| Require extraction ×2 | 2 copies | **3** AST copies existed; MAINT-28 already migrated the completion one to an index+cache read (`:101-119`). Remaining raw-AST copies: `LuaNameReference:189-246` + `LuaFileBindingsIndex:285-340`. | Delete the `LuaNameReference` copy → `fileRequires` (§2.4). The indexer's copy is the index source, kept by design. Net: 1 consumer helper + 1 indexer primitive. |
| Module resolution ×2 | 2 copies (the P1 #3 divergence) | Confirmed 2 copies: `LuaRequireReference.resolve:22-46` + `LuaTypeManagerImpl.doResolveModule:108-142`, divergent refresh flag (§3.6) and divergent terminal (first-found vs `getModuleType`-gated). | One `resolveModuleCandidates` sequence; each caller applies its own terminal (§2.5). |
| luarocks command ×5 | 5 copies | ROCKS-16 centralized exe/server/withServer; remaining inlined `exe ?: …; GeneralCommandLine(exe,…)` at **5** migratable call sites: `LuaRocksInstalledService`, `LuaRocksInstallExecutor`, `WorkspaceBuildRunner`, `LuaRocksSearchService` (×2), `LuaRocksMetadataService`. `LuaRocksRunConfiguration`/`PublishRockAction`+`RockUploadCommand` build different shapes (out of scope, §2.6). | Add `command()` (§2.6); migrate the 5 sites. |
| run-config boilerplate | — | Out of scope (not a resolution/caching seam); tracked by MAINT-13's own docs. | None. |
| assignability-inspection twins | — | Out of scope (inspection layer; MAINT-29). | None. |

### 3.6 Module-resolver refresh-flag reconciliation (MAINT-30-03)

`LuaRequireReference` uses `VfsUtil.findByIoFile(File(path), false)` (`:25`) — no VFS refresh;
`LuaTypeManagerImpl` uses `VfsUtil.findByIoFile(File(path), true)` (`:146`) — forces a refresh (a
potential slow VFS op off a read action). The single `resolveModuleCandidates` uses **`false`**: resolution
must not trigger synchronous VFS refresh on the read path (contract §1 threading — refresh is I/O), and
a freshly-written module file is picked up on the next VFS event anyway. This removes the divergence
(the P1 #3 root) by standardizing on the non-refreshing form.

## 4. External Data & Parsing

None — this feature consumes no CLI/text/network input. It only reads PSI, the FileBindings
`FileBasedIndex`, and stub indexes, all produced by our own code. (`LuaRocksEnvironment.command`
*builds* a command line but MAINT-30 does not parse its output.)

## 5. Data Flow

### Example 1: Cross-file global no longer resolves to a usage (#20)
File `a.lua`: `local M = {}; return M`. File `b.lua`: `M.foo()` then, elsewhere, `print(bar)` where
`bar` is a global declared in `c.lua`. Before: `b.lua`'s index recorded `bar` (a usage) → resolving
`bar` from `d.lua` could land on `b.lua`'s usage leaf. After §3.1: `b.lua` records no `bar` binding →
`bar` resolves only to `c.lua`'s declaration. TC-01.

### Example 2: Repeated highlight passes hit the cache (§2.5.2)
Editor highlights `foo` three times in one pass batch. Before: 3 full `multiResolve` runs. After §2.2:
first run populates `ResolveCache`; runs 2-3 return the cached `ResolveResult[]` until the next PSI
modification drops the cache. TC-03 asserts the resolver body runs once via a call counter.

### Example 3: Target switch still invalidates the snapshot (§3.4)
`local x = KEYS[1]` with REDIS target → `x: string`. User switches target to plain Lua (KEYS not
seeded) without editing text. Before/after: the snapshot recomputes because the target dependency
(DR-01) changed. TC-04 (mirrors REDIS-04 §3.1a).

## 6. Edge Cases

- **Reparse with equal-length text**: impossible to stale now — `MODIFICATION_COUNT` bumps on any PSI
  change regardless of text hash (fixes #21's collision path). TC-05.
- **Non-`LuaFile` containing file** in `multiResolve` Phase-2: `fileRequires` guards `file !is LuaFile`
  → `emptyList()` (matches today's `extractRequires` early return, `:191`).
- **Method chain with no resolvable class** (§2.7): `resolveType` returns non-`LuaClassType` →
  `StepType(emptyList(), receiverClass)`, identical to today's null-`funcDecl` branch (`:120-121`).
- **`inProgressSnapshot` re-entry** during `getCachedValue` compute: the compute lambda calls
  `buildSnapshot`, which registers itself in `inProgressBuilds` (`LuaTypesVisitor.kt:927-935`); a nested
  `forFile` short-circuits at §3.4 step "inProgressSnapshot first" **before** touching
  `CachedValuesManager`, so no nested `getCachedValue` recursion. TC-06.
- **Dotted assignment `t.f = v`** in the declaration filter: excluded (has `varSuffixList`), owned by
  the member-field index — not a regression, the member-field path is untouched. TC-07.

## 7. Integration Points

No new `plugin.xml` registration. The existing entry is unchanged except that a version bump forces
re-index:

```xml
<!-- src/main/resources/META-INF/plugin.xml:649 (UNCHANGED registration; VERSION bump forces rebuild) -->
<fileBasedIndex implementation="net.internetisalie.lunar.lang.indexing.LuaFileBindingsIndex"/>
```

- **Index**: `LuaFileBindingsIndexName` = `ID.create("$ExternalIdPrefix.FileBindings")`
  (`LuaFileBindingsIndex.kt:25-26`); `VERSION 2 → 3` (§3.2).
- **ResolveCache**: `com.intellij.psi.impl.source.resolve.ResolveCache.getInstance(project)` — a
  platform application/project service, no registration.
- **CachedValuesManager**: `com.intellij.psi.util.CachedValuesManager.getCachedValue(psiFile, KEY) { … }`.
- **Cross-feature — MAINT-28 §2.5.5 no-op** (`docs/features/maint/28-completion-correctness/design.md:249-253`):
  that feature's "member-snapshot already cached, item is a no-op" rationale depends on the memo's
  **existence**, not its keying. §2.3/§3.4 here **preserve** memoization (re-key onto
  `CachedValuesManager`, same or stronger invalidation), so the MAINT-28 no-op conclusion survives
  regardless of merge order. If MAINT-30 lands first, MAINT-28 need only re-verify the memo still
  exists (it does).
- **Cross-feature — MAINT-25 (type-graph immutability)** touches the same `LuaTypes.kt` / `LuaTypesVisitor`
  `forFile` seam (`docs/.../25-type-graph-immutability/design.md:83`). Both are Wave-19; the roadmap
  marks **MAINT-25 as `Serial` on the type engine**. Serialize: **MAINT-25 first** (it stabilizes the
  snapshot's internal immutability), then MAINT-30 re-keys the cache around it. If MAINT-30 lands first,
  MAINT-25 rebases its `forFile` edits onto the `CachedValuesManager` form (mechanical). DR-02 records
  the coordination.
- **Cross-feature — TYPE-10 reentrancy** (`LuaTypesVisitor.kt:908-913`): §2.3/§3.4 keep the
  `inProgressSnapshot` check as the **first** statement of `forFile`, ahead of `getCachedValue`, so the
  ThreadLocal `inProgressBuilds` guard is unaffected. TC-06 locks this.

## 8. Requirement Coverage

| Requirement | Priority | Implemented by (section) |
|-------------|----------|--------------------------|
| MAINT-30-01 | M | §3.1, §3.2, §7 |
| MAINT-30-02 | M | §2.2, §2.3, §3.4 |
| MAINT-30-03 | S | §2.1, §2.4, §2.5, §2.6, §3.3, §3.5, §3.6 |
| MAINT-30-04 | C | §2.7 |

## 9. Alternatives Considered

- **`CachedValuesManager` for `multiResolve` instead of `ResolveCache`**: rejected — `ResolveCache` is
  the platform-idiomatic reference cache (incomplete-code aware, physical/non-physical split,
  recursion guard), and the review named it specifically (§2.5.2). `CachedValuesManager` is right for
  the *snapshot* (§2.3), not for a reference.
- **Keep `FileUserData`, just fix the hash**: rejected — it re-implements `CachedValuesManager` and can
  never observe non-text invalidation (target switch, dependency change). #21 is a "delete it" finding.
- **Stub the file-bindings as PSI declarations**: rejected — Lua has no decl PSI (memory note), and
  MAINT-09/10 already chose the `FileBasedIndex` shape; §3.1 filters within that shape.
- **Merge the indexer's require walk into `fileRequires`**: rejected — the index cannot read itself;
  the raw AST walk is the index's own primitive (§3.5).

## 10. Open Questions

None.
