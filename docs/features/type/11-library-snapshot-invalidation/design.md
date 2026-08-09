---
id: "TYPE-11-DESIGN"
title: "Technical Design"
type: "design"
parent_id: "TYPE-11"
folders:
  - "[[features/type/11-library-snapshot-invalidation/requirements|requirements]]"
---

# Technical Design: TYPE-11 — Library Snapshot Invalidation

## 1. What the de-risking measured, and what it overturned

All figures below were produced on gce-builder (libvirt `debian13`) on **2026-08-09** by the harnesses
in `src/test/kotlin/net/internetisalie/lunar/type/`, against a temporary production edit that was
**reverted before commit** (see `risks-and-gaps.md` → "What the measurement ran against"). Nothing in
this section is read off a call shape; three of the five findings contradict what `requirements.md`
asserted from reading.

### 1.1 The residual is REAL, and the simple version of this feature is unsound

`requirements.md` says "Nothing above should be built until that is measured." It was. The naive
form — pin every provenance-matched library file's snapshot to a generation tracker — was built and
run against the full suite:

```
2563 tests completed, 2 failed, 1 skipped
> Task :test FAILED
BUILD FAILED in 9m 48s
```

Both failures are TYPE-11's own residual fixtures, and both are residual paths `requirements.md`
named:

```
DR-01 path1 before edit: libAlias members = [beforeEdit]
DR-01 path1 after edit:  libAlias members = [beforeEdit]      <- expected [afterEdit]
DR-01 path2 before edit: libHandle members = [beforeEdit]
DR-01 path2 after edit:  libHandle members = [afterEdit, beforeEdit]
```

```
junit.framework.AssertionFailedError: editing the project file must be reflected in the
library global's type expected:<[afterEdit]> but was:<[beforeEdit]>
junit.framework.AssertionFailedError: the removed project method must disappear;
got [afterEdit, beforeEdit]
```

**So blanket pinning ships a stale-type defect and TYPE-11-04 fails.** The design below is therefore
not the one `requirements.md` sketched; it is the alternative that document itself allows —
"leaving cross-file-resolving snapshots on the global tracker" — made exact.

**Second finding, equally load-bearing: the existing suite is not a gate for this.** 2543 pre-existing
tests passed unchanged under the unsound build. ⚠ The corpus half of that claim was **not measured** — the sweep classes never ran (see TC-11). `git status --short
src/test/resources/corpus/` was empty. The only red came from fixtures written for this de-risking.
Any future change in this area that relies on "the suite is green" is relying on nothing.

### 1.2 Residual path 2 exists, but not in the shape `requirements.md` described

`requirements.md` says a project file declaring `function LibClass:helper()` makes a
generation-pinned library snapshot stale, citing `materializeClass` → `collectMethodMembers`. The
first fixture written to that description was **green on `main` and green under pinning** — it could
not fail. Probed:

```
P2 resolveGlobal(LibClass) = []
P2 resolveType(LibClass)   = [beforeEdit]
P2 global='LibClass' in beta.lua: graphType=Table(className=null, localMembers={}, superTypes=[], isExact=false, metamethods=[])
```

With the **unhosted** `---@class` form (`---@class C` over a bare `C = {}` — BUG-400's shape, and
what every bundled stdlib stub uses) the class name never reaches the graph, so no project member is
frozen into any snapshot. `resolveType` does return the project method, but `resolveType` is memoized
in `typeCache`, which depends on project-wide `PsiModificationTracker` (`LuaTypeManagerImpl:35-45`)
and is untouched by this feature.

The **hosted** form does carry it:

```
P2H resolveGlobal(libHandle2) = [beforeEdit]
P2H global='libHandle2' in host2.lua: graphType=Union(types=[Table(className=HostClass,
    localMembers={beforeEdit=…VariableElement@27f2e992}, …), Table(className=null, …)])
```

`beforeEdit` is declared in a **project** file and is sitting inside a **library** file's snapshot
graph. The route is `freeGlobalSeed` → `resolveGlobal` → `tableToLuaType` (`LuaTypes.kt:156-172`,
which merges nominal members for a named class) → `LuaGraphType.fromLuaType`
(`LuaGraphType.kt:251`), not `materializeClass` reaching the snapshot directly.

Measured on the bundled stdlib: 22 `---@class` tags across `runtime/standard/lua-5.4/`, of which
**one** (`local File = {}` in `io.lua`) is hosted. The shape is real but rare in what ships; a fetched
LuaCATS definition library is free to use either.

### 1.3 Provenance works, but not by reference identity, and only through `originalFile`

```
DR-02 target=Target(platform=Standard, version=5.4)
      runtimeRoot=jar:///…/lunar-0.18.0.jar!/runtime/standard/lua-5.4 fileSystem=jar
DR-02 runtimeRoot children = [builtin.lua, coroutine.lua, debug.lua, io.lua, math.lua, os.lua,
      package.lua, string.lua, table.lua, utf8.lua]
DR-02 registered definition roots = [/…/system-test/lunar/definitions/luassert-d3528bb6…]
DR-02 global='wx'          vf=…/luassert-d3528bb6…/wx.lua  fs=file provisioned=true
DR-02 global='projectOnly' vf=/src/projectGlobal.lua       fs=temp provisioned=false
DR-02 copy: virtualFile=null originalFile.virtualFile=…/wx.lua byVirtualFile=false byOriginalFile=true
```

Two traps, both measured:

- **Reference identity is not reliable.** `PsiManager.findFile(vf).virtualFile === vf` is `true` for
  the library file and **`false`** for a project file in the light fixture's `temp` file system —
  `doubleEq` (`equals`) holds in both cases:
  ```
  P2-ID 'wx'          … class=VirtualFileImpl tripleEq=true  doubleEq=true
  P2-ID 'projectOnly' … class=VirtualFileImpl tripleEq=false doubleEq=true
  ```
  TYPE-11-03's wording "matched by `VirtualFile` identity" is therefore only satisfiable as
  **containment by URL**, never as `===`. §3.2 matches on `VirtualFile.url` prefixes.
- **A copy has no `virtualFile` at all** (`virtualFile=null`). Any predicate reading
  `psiFile.virtualFile` misclassifies a completion/intention copy of a library file. §3.2 reads
  `psiFile.originalFile.virtualFile`.

Also recorded: the bundled runtime root arrives over the **`jar://`** file system inside the plugin
jar, while definition libraries arrive over `file://`. Both are handled by URL-prefix containment;
`VfsUtilCore.isAncestor` also works, since it never crosses file systems.

### 1.4 The conditional rule holds, and it pins the files that matter

Under §3's rule (pin only when the recorded source set is entirely inside the provisioned set), both
residual fixtures go green and the pinnability trace shows nothing valuable is excluded:

```
DR-01 path1 after edit: libAlias members  = [afterEdit]
DR-01 path2 after edit: libHandle members = [afterEdit]
DR-01 control after project edit: projectAlias = [after] libOnly = [fromLibrary]
```

```
TYPE11-TRACE file=builtin.lua   provisioned=true pinnable=true sources=0 outside=[]
TYPE11-TRACE file=coroutine.lua provisioned=true pinnable=true sources=0 outside=[]
TYPE11-TRACE file=debug.lua     provisioned=true pinnable=true sources=0 outside=[]
TYPE11-TRACE file=io.lua        provisioned=true pinnable=true sources=1 outside=[]
TYPE11-TRACE file=math.lua      provisioned=true pinnable=true sources=0 outside=[]
TYPE11-TRACE file=os.lua        provisioned=true pinnable=true sources=0 outside=[]
TYPE11-TRACE file=package.lua   provisioned=true pinnable=true sources=0 outside=[]
TYPE11-TRACE file=string.lua    provisioned=true pinnable=true sources=0 outside=[]
TYPE11-TRACE file=table.lua     provisioned=true pinnable=true sources=0 outside=[]
TYPE11-TRACE file=utf8.lua      provisioned=true pinnable=true sources=0 outside=[]
TYPE11-TRACE file=wx.lua        provisioned=true pinnable=true sources=0 outside=[]
```

The full suite under the conditional rule:

```
BUILD SUCCESSFUL in 9m 45s
tests 2564 failures 0 skipped 1
```

⚠ **corrected 2026-08-09**: the corpus sweep was not run for that measurement. `git status --short src/test/resources/corpus/` is **not** evidence: baselines are only rewritten under `-PrecordCorpusBaseline` (`build.gradle.kts:286-288`), so that check is clean whether the sweep passed, regressed, or never ran. The corpus classes are excluded from the routine loop by design (`build.gradle.kts:272-283`) — they index ~300-file third-party trees and need `tooling/corpus/fetch-corpus.py`. **The gate is the sweep itself**: `tooling/gce-builder/gce-builder.sh run "test -PwithCorpus --rerun --no-build-cache"`, in which `BaselineRatchetTest` compares against the recorded baselines in-test.

Every bundled stdlib file and DR-04's 123 KiB synthetic library is pinnable. `io.lua` records one
source (its own hosted `File` class) and that source is inside the provisioned set.

### 1.5 DR-04 — the win is large and the target is not met

`forFile(consumer)`, medians of five, distinct consumer text per sample so the per-file-text
memoization cannot serve a warm answer. **Compare only within a row**: the no-library baseline moved
between 3.2 ms and 10.4 ms across runs on the same machine, so no cross-row ratio is quotable — the
COMP-09 §1.2 rule applies here unchanged.

| build | arm A, no definition library | arm B, 123 KiB definition library |
| :-- | --: | --: |
| unpinned (today's behaviour) | 10 440 µs | **349 700 µs** |
| blanket pin (unsound, §1.1) | 3 175 µs | 16 657 µs |
| §3 conditional rule | 9 655 µs | **32 081 µs** |
| §3 conditional rule, re-run | 8 132 µs | 42 002 µs |

Raw samples for the last two rows:

```
DR-04 arm A … samples(us)=[7528, 9303, 9655, 9940, 14896]  median=9655us
DR-04 arm B … samples(us)=[26651, 31017, 32081, 32830, 858515] median=32081us
DR-04 arm A … samples(us)=[7450, 7997, 8132, 8675, 14476]  median=8132us
DR-04 arm B … samples(us)=[30665, 37554, 42002, 49221, 843603] median=42002us
```

**Direction: the 123 KiB library stops costing hundreds of milliseconds per keystroke.** What is
*not* achieved is `requirements.md`'s DR-04 success criterion of "landing near the 9 ms no-library
baseline": arm B lands at 3–5× arm A in the same run. `requirements.md` predicted this outcome and
named the reason — every free global still re-runs `resolveGlobal` + `graphTypeToLuaType`, which
builds a fresh `visited` map per call and walks the library table's full member set. That residual
cost is **out of scope here** and is filed as a follow-up in `risks-and-gaps.md`.

### 1.6 DR-05 — the dumb-mode staleness class did NOT reproduce

`requirements.md` asserts that a snapshot built while `DumbService.isDumb` bakes in `resolveGlobal`'s
nulls and that "under a generation dependency it is sticky until the next roots/target tick". The
first half is true; the second is not, in this harness:

```
DR-05 while dumb: libDumb graph type = Undefined psi=488188368 stamp=0 valid=true
DR-05 after dumb: psi=488188368 stamp=1 sameInstance=true oldValid=true oldStamp=1
                  graph=Table(className=null, localMembers={fromLibrary=…}, …)
DR-05 after leaving dumb mode: libDumb members = [fromLibrary]
```

The nulls **are** baked in while dumb (`Undefined`). They do not survive, and the mechanism is not the
churn tracker: the same `PsiFile` instance's `modificationStamp` moves **0 → 1** when the fixture
leaves dumb mode, and `forFile` passes `psiFile` itself as a dependency —
`CachedValueBase` converts a `PsiElement` dependency to `containingFile.modificationStamp`. Two
mutations confirm it (§ `risks-and-gaps.md` mutation table): removing the `!isDumb` term left the test
green, and replacing the generation tracker with `ModificationTracker.NEVER_CHANGED` **also** left it
green.

**Consequence for this design: TYPE-11-05's guard is retained (§3.4) but it is insurance, not a fix
for a demonstrated defect, and the DR-05 harness is explicitly NOT a gate.** Saying otherwise would
be the exact "test that cannot fail" this plan exists to avoid. Closing it properly is a tracked
de-risking task (`risks-and-gaps.md` → DR-06).

### Prior Art in This Repo

| Component | file:line | Relationship |
| :-- | :-- | :-- |
| `LuaTypesSnapshot.forFile` | `lang/psi/types/LuaTypes.kt:212-224` | **Edited** — the churn dependency becomes conditional. Not replaced; the reentrancy guard, the `psiFile` dependency and `targetModificationTracker` all stay. |
| `LuaTypeManagerImpl.typeCache` / `moduleCache` / `globalCache` | `lang/psi/types/LuaTypeManagerImpl.kt:34-68` | **Extended** — each gains a parallel source-URL record. Their `PsiModificationTracker` dependency is deliberately unchanged. |
| `RuntimeLibraryProvider.getLibraryRoot` | `platform/target/RuntimeLibraryProvider.kt:25-30` | **Reused** as provenance source 1. It is the single root both `LuaLibraryProvider` (`lang/library/LuaLibraryProvider.kt:19`) and `PlatformLibraryProvider.getSupportLibraries` (`project/PlatformLibraryProvider.kt:47`) are built from, so matching it covers both without asking either. |
| `LuaDefinitionLibraryProvider.getRootsToWatch` | `definitions/LuaDefinitionLibraryProvider.kt:44` | **Reused** as provenance source 2. |
| `LuaRocksLibraryProvider` | `rocks/library/LuaRocksLibraryProvider.kt` | **Deliberately excluded** — out of v1 scope per `requirements.md`. |
| `PlatformLibraryProvider.getExternalLibraries` | `project/PlatformLibraryProvider.kt:52-69` | **Deliberately excluded** — its "Search Trees" are arbitrary user source paths from `PathConfiguration.getStaticSourcePathPatterns`, i.e. mutable project-adjacent code, not a plugin-provisioned immutable library. |
| `ProjectFileIndex.isInLibrary` | platform API | **Not used**, per TYPE-11-03. Never needed once provenance answers the question. |
| `CompNineDr20Test` | `src/test/kotlin/net/internetisalie/lunar/definitions/CompNineDr20Test.kt` | **Superseded for this feature** by `TypeElevenDr04LatencyTest`, which uses the same shape over a provenance-matched library instead of an anonymous test provider. DR-20 is left where it stands. |
| `LibraryRootTestCase` | `src/test/kotlin/net/internetisalie/lunar/definitions/LibraryRootTestCase.kt` | **Not used** by TYPE-11 tests. Its anonymous `AdditionalLibraryRootsProvider` is invisible to provenance by construction, so a TYPE-11 test built on it would validate the mechanism against a path no user hits. `TypeElevenDefinitionLibraryTestCase` replaces it for this feature only. |

### Current State

`LuaTypesSnapshot.forFile` (`LuaTypes.kt:212-224`) memoizes a file's type graph with three
dependencies: the `PsiFile`, `PsiModificationTracker.MODIFICATION_COUNT`, and the project's
`targetModificationTracker`. The second is project-wide, so one keystroke in one project file
discards every cached snapshot, including those of files the user cannot edit.

### Target State

`forFile` keeps the same three-dependency shape. Only the middle dependency changes, and only when a
per-build predicate says it is safe:

```
buildSnapshot runs inside LuaTypeSourceRecorder.recording { … }
        │
        ├─ every cross-file consumption in LuaTypeManagerImpl reports its file URL
        │  (into EVERY open frame, so nesting is transitive)
        │
        └─ forFile receives (snapshot, sources: Set<String>)
                 │
                 ├─ pinnable = smart mode ∧ this file is provisioned ∧ every source is provisioned
                 │
                 ├─ pinnable   → dependencies = [psiFile, ProjectRootModificationTracker, targetTracker]
                 └─ otherwise  → dependencies = [psiFile, PsiModificationTracker.MODIFICATION_COUNT, targetTracker]
```

## 2. Core Components

### 2.1 `net.internetisalie.lunar.lang.psi.types.LuaTypeSourceRecorder`

- **Responsibility**: record, for the duration of one `buildSnapshot`, the URL of every file whose
  content was consumed to answer a cross-file type question.
- **Threading**: none of its own. It is a `ThreadLocal` stack and is only ever touched under the read
  action that `forFile` already runs inside. It holds `String` URLs, never `VirtualFile`/`PsiFile`
  references, so it cannot retain heavy framework objects (engineering contract §4).
- **Collaborators**: `LuaTypesSnapshot.forFile` (opens frames), `LuaTypeManagerImpl` (reports).
- **Key API**:
  ```kotlin
  object LuaTypeSourceRecorder {
      fun <T> recording(body: () -> T): Pair<T, Set<String>>
      fun report(urls: Collection<String>)
      fun reportFile(file: PsiFile?)
  }
  ```

### 2.2 `net.internetisalie.lunar.lang.psi.types.LuaLibraryProvenance`

- **Responsibility**: answer "did this plugin provision this file?" for a `PsiFile` or a URL.
- **Threading**: read action. Reads settings and the VFS only; no PSI, no writes. The root list is
  memoized per project, so the classloader resource lookup in `RuntimeLibraryProvider.getLibraryRoot`
  and the catalog load in `LuaDefinitionLibraryProvider.getRootsToWatch` run once per generation,
  not once per snapshot build.
- **Collaborators**: `RuntimeLibraryProvider`, `LuaDefinitionLibraryProvider`, `LuaProjectSettings`,
  `ProjectRootModificationTracker`, `CachedValuesManager`.
- **Registration**: light `@Service(Service.Level.PROJECT)` — annotation-registered, no `plugin.xml`
  entry, matching `LuaProjectSettings` (`settings/LuaProjectSettings.kt:17`) and
  `RockspecSourcePathProvider` (`rocks/RockspecSourcePathProvider.kt:21`).
- **Key API**:
  ```kotlin
  @Service(Service.Level.PROJECT)
  class LuaLibraryProvenance(private val targetProject: Project) {
      fun generationTracker(): ModificationTracker
      fun isProvisioned(file: PsiFile): Boolean
      fun isProvisionedUrl(url: String): Boolean
      companion object {
          fun getInstance(project: Project): LuaLibraryProvenance =
              project.getService(LuaLibraryProvenance::class.java)
      }
  }
  ```

### 2.3 `net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot.Companion.forFile` (edited)

- **Responsibility**: unchanged — compute or return the cached snapshot. Gains the pinnable decision.
- **Threading**: read action (unchanged; callers already hold one).
- **Collaborators**: `LuaTypeSourceRecorder`, `LuaLibraryProvenance`, `DumbService`.
- **Key API**: signature unchanged — `fun forFile(file: PsiFile): LuaTypes`.

### 2.4 `net.internetisalie.lunar.lang.psi.types.LuaTypeManagerImpl` (edited)

- **Responsibility**: unchanged, plus reporting every cross-file consumption to §2.1.
- **Threading**: unchanged.
- **Key API**: no public signature changes. Two private helpers are added:
  ```kotlin
  private fun <T> recordUnder(key: String, body: () -> T): T
  private fun replaySources(key: String)
  ```
  and one new field mirroring the existing caches exactly:
  ```kotlin
  private val sourceCache: CachedValue<MutableMap<String, Set<String>>>
  ```

## 3. Algorithms

### 3.1 Source recording

- **Input → Output**: a `buildSnapshot` invocation → `Set<String>` of `VirtualFile.url`.
- **Steps**:
  1. `recording` pushes a fresh `MutableSet<String>` onto a `ThreadLocal<ArrayDeque<MutableSet<String>>>`.
  2. It runs `body()`, then pops the frame in a `finally` and returns `body`'s result paired with a
     defensive copy of the frame.
  3. `report(urls)` adds every URL to **every** frame currently on the stack, not only the innermost.
  4. `reportFile(file)` reads `file?.originalFile?.virtualFile?.url` and delegates to `report`;
     a null at any step is a no-op.
- **Rules / edge handling**:
  - **Stack-wide reporting is the whole point of step 3.** `forFile(libraryA)` can nest inside
    `forFile(libraryB)` through `resolveGlobal`; if only the innermost frame were filled, `libraryB`
    would be judged pinnable while transitively depending on a project file through `libraryA`.
  - An empty stack is a no-op: the type manager is called from many places that are not snapshot
    builds, and those must cost nothing.
  - Reentrancy into the *same* file is already prevented upstream by
    `LuaTypesVisitor.inProgressSnapshot` (`LuaTypes.kt:214`), which returns before any frame is opened.
- **Complexity / bounds**: O(frames × urls). Frame depth is bounded by the type manager's own
  reentrancy guards (`resolvingGlobals`, `resolvingTypes`, `resolvingModules`); in the measured runs
  the recorded set was 0 or 1 entries per stdlib file.

### 3.2 The provenance predicate

- **Input → Output**: `PsiFile` or `String` URL → `Boolean`.
- **Steps**:
  1. Compute the root URL list, memoized per project via
     `CachedValuesManager.getManager(project).getCachedValue(project) { … }` with dependencies
     `ProjectRootModificationTracker.getInstance(project)` and
     `LuaProjectSettings.getInstance(project).state.targetModificationTracker`:
     ```kotlin
     val target = LuaProjectSettings.getInstance(project).state.getTarget()
     val runtimeRoot = RuntimeLibraryProvider(project).getLibraryRoot(target)
     val definitionRoots =
         AdditionalLibraryRootsProvider.EP_NAME.extensionList
             .filterIsInstance<LuaDefinitionLibraryProvider>()
             .flatMap { it.getRootsToWatch(project) }
     (listOfNotNull(runtimeRoot) + definitionRoots).map { it.url }
     ```
  2. `isProvisioned(file)` reads `file.originalFile.virtualFile?.url`; a null URL returns `false`.
  3. `isProvisionedUrl(url)` returns `rootUrls.any { url == it || url.startsWith("$it/") }`.
- **Rules / edge handling**:
  - **`originalFile`, not `virtualFile`** — measured: a `PsiFile.copy()` of a library file has
    `virtualFile == null` (§1.3). Reading `virtualFile` would silently classify every completion copy
    as unprovisioned. That direction is safe (no staleness, just no speedup), which is exactly why it
    would never be noticed.
  - **URL prefix, not reference identity and not `equals` on `VirtualFile`** — measured: `===` is
    false for a light-fixture project file even when `PsiManager.findFile` was handed that very file
    (§1.3). The `"$it/"` suffix in the prefix test is required: without it, a sibling root
    `…/luassert-abc` would match a file under `…/luassert-abcdef`.
  - The list is taken from the **EP-registered** `LuaDefinitionLibraryProvider` instances, not a
    freshly constructed one. A locally constructed provider would pass a unit test while the feature
    was dead in a running IDE — the failure mode `LuaDefinitionLibraryProviderTest
    .testProviderIsRegisteredWithThePlatform` already exists to catch.
  - `RuntimeLibraryProvider.getLibraryRoot` returns `null` for a target with no bundled resources
    (e.g. PANDOC); `listOfNotNull` handles it and provenance simply has one source.
- **Complexity / bounds**: O(roots) per query, roots ≤ 1 + number of enabled definition libraries.
  The expensive part (classloader resource lookup, catalog load) runs once per generation.

### 3.3 The pinnable decision

- **Input → Output**: `(PsiFile, Set<String> sources)` → the churn dependency object.
- **Steps**:
  1. `if (DumbService.isDumb(project)) → not pinnable` (§3.4).
  2. `if (!provenance.isProvisioned(psiFile)) → not pinnable`.
  3. `if (sources.any { !provenance.isProvisionedUrl(it) }) → not pinnable`.
  4. Otherwise pinnable.
  5. Pinnable → churn is `provenance.generationTracker()`; not pinnable → churn is
     `PsiModificationTracker.MODIFICATION_COUNT`.
- **Rules / edge handling**:
  - **This is the answer to "what happens to a snapshot that resolved cross-file into a project
    file": nothing changes for it.** It keeps today's dependency exactly, so it keeps today's
    correctness exactly. There is no attempt to track *which* project file, no scoped tracker, and no
    partial invalidation — a snapshot is either a pure function of provisioned content or it is on
    the global tracker.
  - The three tests are ordered cheapest-first and short-circuit. Step 2 rejects every project file
    before any set iteration; project files are the overwhelming majority of `forFile` calls.
  - The decision is made **inside** the `CachedValueProvider`, so it is re-evaluated on every rebuild.
    A library file that becomes project-dependent (the user adds a shadowing global) is re-judged on
    its next build, which the global tracker guarantees happens.
- **Complexity / bounds**: O(|sources| × |roots|), both measured at ≤ 1 for the shipped stubs.

### 3.4 The dumb-mode guard

- **Input → Output**: `Project` → the boolean in step 1 of §3.3.
- **Steps**: `!DumbService.isDumb(project)`.
- **Rules / edge handling**: while indexing, `resolveGlobal` returns `null` unconditionally
  (`LuaTypeManagerImpl:141`) and `resolveType` likewise (`LuaTypeManagerImpl:84`), so a snapshot built
  then records **zero** sources — which would otherwise make it maximally pinnable, precisely when it
  is least trustworthy. The guard exists to close that inversion.
  **It is not backed by a reproduced defect** (§1.6): with the guard removed the DR-05 harness stayed
  green, because the file's own `modificationStamp` moves when dumb mode ends. It is kept because it
  costs one boolean, it cannot be wrong, and the alternative is relying on an accident of
  `modificationStamp` behaviour that no test pins.
- **Complexity / bounds**: O(1).

### 3.5 Cross-file consumption sites that must report

Every site below is a place `LuaTypeManagerImpl` turns another file's content into type information.
Missing one produces a snapshot judged pinnable while depending on an unrecorded file — a silent
stale-type defect, not a crash. The list is exhaustive as of this design; §6 states the guard.

| Site (`LuaTypeManagerImpl`) | Line | Reports |
| :-- | --: | :-- |
| `typeOfGlobalIn` — each candidate file visited | 176 | `.onEach { LuaTypeSourceRecorder.reportFile(it) }` inserted after the existing `.filter { it != exclude }` |
| `getModuleType` — the module file, before the stub fast path | 203 | `reportFile(psiFile)` as the first statement |
| `materializeClass` — every declaring file | 262 | `decls.forEach { reportFile(it.containingFile) }` |
| `materializeUnhostedClass` — every `---@class` tag's file | 308 | `tags.forEach { reportFile(it.containingFile) }` |
| `addMethodsOf` — the selected `LuaFuncDecl`'s file | 467 | `reportFile(decl.containingFile)` after the `firstOrNull` |
| `doResolveType` — the alias declaration's file | 250 | `reportFile(aliasDecls.first().containingFile)` |

`typeOfGlobalIn` reports **every file it visits**, not only the one that yielded a type. This is a
deliberate over-approximation: `doResolveGlobal` searches project scope first (BUG-427,
`LuaTypeManagerImpl:162`), so a project file that declares the name but gives it no useful type is
still a file whose future content can change the answer. Over-approximating costs a lost pin; under-
approximating costs a stale type.

### 3.6 Cache replay

- **Input → Output**: a door name + argument → the source set recorded when that answer was computed.
- **Steps**:
  1. Add `sourceCache`, a `CachedValue<MutableMap<String, Set<String>>>` built exactly like the three
     existing caches (`CachedValuesManager.getManager(project).createCachedValue({ … }, false)` with
     `PsiModificationTracker.getInstance(project)` as the sole dependency and a
     `Collections.synchronizedMap`).
  2. Key format: `"type:$name"`, `"module:$moduleName"`, `"global:$name"`. Three flat namespaces in
     one map; the prefixes exist because the same string can be a class name and a global name.
  3. On a **miss**, wrap the `doResolveX` call in
     `recordUnder(key) { … }`, which runs it inside `LuaTypeSourceRecorder.recording` and stores the
     resulting set under `key`.
  4. On a **hit**, call `replaySources(key)` **before** returning the cached type, which re-reports
     the stored URLs into whatever frames are currently open.
- **Rules / edge handling**:
  - Without step 4 the feature is unsound. `resolveGlobal("wx")` is memoized project-wide; the first
    snapshot build to ask pays and records, and every later build gets the type with no sources — so
    a library file that depends on a project global through a cache hit would be judged pinnable.
  - `sourceCache` shares the three existing caches' `PsiModificationTracker` dependency, so entries
    are discarded on exactly the same tick as the types they describe. They cannot drift apart.
  - The early returns that precede each cache (a primitive in `resolveType`, the dumb-mode guard, the
    reentrancy guards) return before both the cache read and the record, and must stay that way: they
    consume no file, so they have nothing to report.

## 4. External Data & Parsing

**None.** This feature consumes no CLI output, no network response and no file format. Its only
external inputs are platform APIs (`ProjectRootModificationTracker`, `DumbService`,
`AdditionalLibraryRootsProvider`) and this plugin's own settings. The one string format it defines is
the `sourceCache` key (§3.6 step 2), which is produced and consumed in the same class.

## 5. Data Flow

### Example 1: keystroke in a project file, 123 KiB definition library registered (DR-04 arm B)

1. The user types a character in `consumer.lua`. `PsiModificationTracker.MODIFICATION_COUNT` ticks.
2. Completion asks for `LuaTypesSnapshot.forFile(consumer.lua)`. `consumer.lua` is not provisioned
   (§3.3 step 2), so its snapshot was on the global tracker and is gone. It rebuilds.
3. The rebuild opens a recorder frame and meets the free global `wx`.
   `resolveGlobal("wx")` misses `globalCache` (the tick cleared it), so it recomputes:
   `typeOfGlobalIn` reports `…/luassert-…/wx.lua`, then calls `globalTypeIn` → `forFile(wx.lua)`.
4. `forFile(wx.lua)` finds its cached value **still valid**: its churn dependency is
   `ProjectRootModificationTracker`, which did not tick. The 123 KiB graph is not rebuilt.
   *This is the entire feature.*
5. `resolveGlobal` stores its answer in `globalCache` and its source set under `"global:wx"`.
6. `consumer.lua`'s snapshot completes. Its recorded sources include `wx.lua`, which is provisioned —
   but `consumer.lua` itself is not, so step 2 already decided: global tracker.

### Example 2: the same keystroke, but a project file shadows a library global (DR-01 path 1)

1. `alpha.lua` (library) contains `libAlias = sharedByProject`; `shared.lua` (project) declares
   `sharedByProject`.
2. `forFile(alpha.lua)` opens a frame, meets the free global `sharedByProject`, and
   `typeOfGlobalIn(projectScope, …)` reports `/src/shared.lua` into the frame.
3. §3.3 step 3 sees `/src/shared.lua` outside the provisioned roots → **not pinnable** →
   `alpha.lua`'s snapshot stays on `MODIFICATION_COUNT`.
4. The user edits `shared.lua`. `alpha.lua`'s snapshot is discarded with everything else, and
   `libAlias` re-infers to the new type. Measured: `DR-01 path1 after edit: libAlias members =
   [afterEdit]`.

### Example 3: the user enables a definition library

**This scenario is traced by reading, not by running** — every TYPE-11 fixture announces the roots
change itself, so none of them exercises the chain below. It is `risks-and-gaps.md` Gap 2.3 and
implementation-plan Phase 3 covers it.

1. `LuaDefinitionLibraryEnabler.apply` persists the id and calls
   `LuaProjectSettings.notifyDefinitionRootsChanged()` (`LuaProjectSettings.kt:199-205`), which
   publishes `LuaSettingsChangedListener.TOPIC`; `LuaSettingsChangeListener.onSettingsChanged`
   (`project/LuaSettingsChangeListener.kt:32`) runs `PlatformLibraryIndex.reload()`, which calls
   `ProjectRootManagerEx.makeRootsChange` (`project/PlatformLibraryProvider.kt:149`).
2. `ProjectRootModificationTracker` ticks. Every pinned library snapshot is discarded, and so is
   `LuaLibraryProvenance`'s memoized root list (same dependency), so the new tree is provisioned from
   its first query.
3. Non-pinned snapshots are unaffected by this tick and are still governed by `MODIFICATION_COUNT`,
   as they are today.

## 6. Edge Cases

| Case | Handling |
| :-- | :-- |
| A completion/intention **copy** of a library file | `originalFile.virtualFile` matches the root, so the copy is judged provisioned (§3.2). Measured: `byVirtualFile=false byOriginalFile=true`. |
| A scratch file, or any `PsiFile` with no `VirtualFile` | `isProvisioned` returns `false`; global tracker; today's behaviour. |
| The bundled runtime root lives in a `jar://` file system | URL-prefix containment is file-system agnostic. Measured: `runtimeRoot=jar:///…/lunar-0.18.0.jar!/runtime/standard/lua-5.4`. |
| A target with no bundled resources (PANDOC) | `getLibraryRoot` returns `null`; `listOfNotNull` drops it; definition libraries still provision. |
| The user switches target (Lua ↔ REDIS) | `targetModificationTracker` is a dependency of both the snapshot and the memoized root list, so both are discarded (REDIS-04 §3.1a behaviour preserved). |
| A LuaRocks tree | Never provisioned in v1 → always on the global tracker → behaviour identical to today. |
| A library file that `require`s a project module | `getModuleType` reports the project file (§3.5) → not pinnable. |
| Indexing in progress | Not pinnable (§3.4). |
| A **new** cross-file consumption site added later to `LuaTypeManagerImpl` | Not automatically covered. `LuaTypeSourceRecorderCoverageTest` (implementation plan Phase 3) fails if `LuaTypeManagerImpl` gains a call to `PsiManager.findFile`, `StubIndex.getElements` or `FileBasedIndex.getContainingFiles` that is not followed by a `reportFile`; see `risks-and-gaps.md` Risk 1.1.  |
| A `LuaTypeReference` resolved lazily after the frame closed | Its source escapes recording. Bounded, not eliminated — see `risks-and-gaps.md` Risk 1.3 and DR-07. |

## 7. Integration Points

**No `plugin.xml` change is required, and that is a deliberate check rather than an omission.**

- `LuaLibraryProvenance` is a light `@Service(Service.Level.PROJECT)`. Light services are registered
  by the annotation and must **not** appear in `plugin.xml`; adding a `<projectService>` entry for one
  is an error. Precedent in this repo: `LuaProjectSettings` (`settings/LuaProjectSettings.kt:17`),
  `RockspecSourcePathProvider` (`rocks/RockspecSourcePathProvider.kt:21`).
- `LuaTypeSourceRecorder` is a Kotlin `object` with no platform lifecycle.
- `LuaTypeManagerImpl` is already registered and its registration is unchanged:
  ```xml
  <!-- src/main/resources/META-INF/plugin.xml:556-558 — UNCHANGED -->
  <projectService
          serviceInterface="net.internetisalie.lunar.lang.psi.types.LuaTypeManager"
          serviceImplementation="net.internetisalie.lunar.lang.psi.types.LuaTypeManagerImpl" />
  ```
- The four `additionalLibraryRootsProvider` registrations (`plugin.xml:516-520`) are read but not
  changed. `LuaDefinitionLibraryProvider` (line 520) must stay registered or provenance loses source 2;
  that is already pinned by `LuaDefinitionLibraryProviderTest.testProviderIsRegisteredWithThePlatform`.

Platform APIs used, all verified present by compiling the measurement build against them:

| Symbol | Package |
| :-- | :-- |
| `ProjectRootModificationTracker.getInstance(Project)` | `com.intellij.openapi.roots` |
| `AdditionalLibraryRootsProvider.EP_NAME.extensionList` | `com.intellij.openapi.roots` |
| `DumbService.isDumb(Project)` | `com.intellij.openapi.project` |
| `ModificationTracker` | `com.intellij.openapi.util` |
| `CachedValuesManager` / `CachedValueProvider.Result` | `com.intellij.psi.util` |
| `PsiModificationTracker.MODIFICATION_COUNT` | `com.intellij.psi.util` |
| `VfsUtilCore.isAncestor` | `com.intellij.openapi.vfs` |

## 8. Requirement Coverage

| Requirement | Priority | Implemented by (section) | Acceptance |
| :-- | :-- | :-- | :-- |
| TYPE-11-01 — a platform-library snapshot survives an unrelated edit | M | §2.2, §2.3, §3.2, §3.3 | `TypeElevenDr04LatencyTest` arm B; §1.5 |
| TYPE-11-02 — every generation signal invalidates it | M | §3.2 step 1, §3.3 step 5, §5 example 3 | `TypeElevenGenerationSignalTest` (plan Phase 3) |
| TYPE-11-03 — identification is by provenance | M | §3.2 | `TypeElevenDr02ProvenanceTest`, 5 assertions, all mutation-proved |
| TYPE-11-04 — no new stale-type defect | M | §3.1, §3.3, §3.5, §3.6 | `TypeElevenDr01ResidualTest` (3 tests) + full suite + four corpus baselines |
| TYPE-11-05 — a dumb-mode build is never cached across the generation | M | §3.4 | Guard implemented; **no reproducing test exists** (§1.6). Tracked as `risks-and-gaps.md` DR-06. |

### 8.1 Acceptance cases, as input → output

`requirements.md` states the requirements but carries no test-case table; these are the concrete
cases, every one of which is already an executable fixture. Paths are relative to
`src/test/kotlin/net/internetisalie/lunar/type/`.

| # | Requirement | Input | Expected output |
| --: | :-- | :-- | :-- |
| TC-1 | TYPE-11-01 | Definition library `luassert-…/wx.lua` = 123 KiB `---@meta` (one `---@class wx`, 3 400 fields, 200 methods). Five consumer files with **distinct** text, each `local pad$i = $i` + `wx.wxC_0 = $i`; time `LuaTypesSnapshot.forFile(consumer$i)` for each. | Median of the five at least 5× below the same harness's `main` figure, compared against **arm A of the same run**. `TypeElevenDr04LatencyTest`. |
| TC-2 | TYPE-11-02 (roots) | Library installed and enabled; snapshot built; then the enabled-library list is emptied and a roots change announced. | `resolveGlobal("wx")` no longer returns the library type. `TypeElevenGenerationSignalTest` case (a), plan Phase 3. |
| TC-3 | TYPE-11-02 (target) | Pinned library snapshot; then `LuaProjectSettings.setTarget(Target(REDIS, …))`. | The snapshot is rebuilt against the new target's stubs. `TypeElevenGenerationSignalTest` case (b). |
| TC-4 | TYPE-11-02 (no false tick) | Pinned library snapshot; edit an unrelated project file. | `ProjectRootModificationTracker.modificationCount` unchanged **and** the library snapshot instance is not rebuilt. `TypeElevenGenerationSignalTest` case (c). |
| TC-5 | TYPE-11-03 | `FileBasedIndex.getContainingFiles(LuaGlobalAssignmentIndex.KEY, "wx", allScope)` → `…/luassert-…/wx.lua`; same query for `projectOnly` → `/src/projectGlobal.lua`. | `isProvisioned` = `true` and `false` respectively. `TypeElevenDr02ProvenanceTest.testEveryFileResolveGlobalWouldVisitIsClassifiedByProvenance`. |
| TC-6 | TYPE-11-03 (copy) | `PsiManager.findFile(wx.lua).copy() as PsiFile`. | `isProvisioned(copy.virtualFile)` = `false`; `isProvisioned(copy.originalFile.virtualFile)` = `true`. `…testACopyOfALibraryFileIsOnlyMatchedThroughOriginalFile`. |
| TC-7 | TYPE-11-03 (live binding) | The EP-registered `LuaDefinitionLibraryProvider` instances' `getRootsToWatch(project)` after installing `luassert`. | Contains the seeded root. `…testTheSeededLibraryReachesTheProjectThroughTheRegisteredProvider`. |
| TC-8 | TYPE-11-04 (residual 1) | Library `alpha.lua` = `libAlias = sharedByProject`; project `shared.lua` = `sharedByProject = { beforeEdit = 1 }` → rewritten to `{ afterEdit = 1 }`. | `resolveGlobal("libAlias").getMembers().keys` = `[beforeEdit]` before, `[afterEdit]` after. `TypeElevenDr01ResidualTest`. |
| TC-9 | TYPE-11-04 (residual 2) | Library `host.lua` = `---@class HostClass` / `local HostClass = {}` / `HostGlobal = HostClass`, `host2.lua` = `libHandle = HostGlobal`; project `ext.lua` = `function HostClass:beforeEdit() end` → rewritten to `afterEdit`. | `resolveGlobal("libHandle").getMembers().keys` contains `afterEdit` and **not** `beforeEdit`. `TypeElevenDr01ResidualTest`. |
| TC-10 | TYPE-11-04 (project→project) | Project `a.lua` = `projectShared = { before = 1 }`, `b.lua` = `projectAlias = projectShared`; rewrite `a.lua` to `{ after = 1 }`. | `resolveGlobal("projectAlias").getMembers().keys` = `[after]`. `…testAProjectToProjectDependencyIsNeverPinned`. |
| TC-11 | TYPE-11-04 (suite) | The full suite, **and the corpus sweep run explicitly**: `run "test -PwithCorpus --rerun --no-build-cache"`. | 0 failures across both. The sweep classes (`BaselineRatchetTest`, `LuaCorpusSweepTest`, `LuaInspectionParityTest`, `LuaTortureCorpusTest`, `LexerInvariantsTest`, `ParseOracleTest`) must appear in `build/test-results/test/` — their **absence** is the failure mode, and it is silent. Reference run on `69ad6b57`: **2 571 tests, 0 failures**, ratchet 35/0, sweep 4/0, parity 1/0, torture 1/0, 19m 43s. |
| TC-12 | TYPE-11-05 | Library `delta.lua` = `libDumb = sharedByLibrary`, `deltaSource.lua` = `sharedByLibrary = { fromLibrary = 1 }`; build `forFile(delta.lua)` inside `DumbModeTestUtils.runInDumbModeSynchronously`, then leave dumb mode. | `resolveGlobal("libDumb").getMembers().keys` = `[fromLibrary]`. `TypeElevenDr05DumbModeTest` — **records, does not gate** (§1.6). |

## 9. Alternatives Considered

| Option | Why not |
| :-- | :-- |
| **Blanket pin every provisioned library file** (what `requirements.md` sketched) | Measured unsound: §1.1, two residual paths fire, `BUILD FAILED`. |
| **Composite the generation tracker out of more signals** (roots + target + dumb + a rocks signal) | Does not touch the residual at all. The stale content comes from a *project* file whose change ticks none of those signals, however many are added. `requirements.md` says this itself. |
| **A scoped tracker per dependency set** | Requires materialising and invalidating a per-file dependency graph across the whole project. Strictly more machinery than §3.3, and §1.4 shows §3.3 already pins every file that costs anything. Revisit only if the trace shows valuable files being excluded. |
| **Pin only files that made no cross-file resolution at all** | Simpler (no recorder plumbing into the manager) but strictly weaker: it also excludes library→library dependencies, which are safe under a shared generation tracker. It would have excluded `io.lua` (`sources=1`) for no correctness gain. |
| **Identify library files with `ProjectFileIndex.isInLibrary`** | Ruled out by TYPE-11-03; behaviour for this plugin's `SyntheticLibrary` roots is unverified, and provenance answers the question without needing to find out. |
| **Match files by `VirtualFile` reference identity** | Measured false for a project file the index itself supplied (§1.3). |
| **Extend the scope to LuaRocks trees now** | Out of v1 scope per `requirements.md`. Rocks are mutable in place and `RockspecSourcePathProvider.forceRefreshTracker`'s behaviour on `luarocks install` is unverified (TYPE-11-DR-03, deliberately not run). |

## 10. Open Questions

_None — every unresolved item is a tracked de-risking task in `risks-and-gaps.md` (DR-06, DR-07, DR-08)._
