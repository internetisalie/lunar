---
id: "MAINT-63-DESIGN"
title: "Technical Design"
type: "design"
parent_id: "MAINT-63"
folders:
  - "[[features/maint/63-edit-cascade-cost/requirements|requirements]]"
---

# Technical Design: MAINT-63 — Per-Source Invalidation of Unpinned Snapshots

## 1. Architecture Overview

### Current State
`LuaTypesSnapshot.forFile` (`LuaTypes.kt:310`) caches a snapshot with
`CachedValuesManager.getCachedValue(psiFile, LuaTypesVisitor.KEY)`, built inside
`LuaTypeSourceRecorder.recording`, which opens a `SourceFrame` (`LuaTypeSourceRecorder.kt:42`) that
`reportFile`, `reportAbsence`, `reportRescuedGlobal`, `reportInProgressHit` and `reportWarmSnapshot`
(or its `unreplayedWarm`) fill. `dependenciesFor` (`:354`) returns `[psiFile, churnDependencyFor(…),
targetModificationTracker]`; `churnDependencyFor` (`:407`) returns the generation tracker when
`isPinnable`, otherwise `PsiModificationTracker.MODIFICATION_COUNT`, which every PSI change and every
dumb-mode enter and exit ticks. So every unpinned snapshot is discarded on every edit.

### Prior Art in This Repo
- `LuaTypeSourceRecorder` / `SourceFrame` (TYPE-11). **Extended**: a frame also records the frames
  of the in-progress builds it read (§3.2), and its sets become concurrent.
- `LuaTypesSnapshot.churnDependencyFor` / `dependenciesFor` (TYPE-11). **Extended**: only the
  unpinned branch changes; the pinned branch and the three-element shape stay (risks DR-04).
- `LuaTypeManagerImpl`, a `plugin.xml` project service; §2.1 is registered the same way (§6).
- `LuaSnapshotCostSweepTest` and `SnapshotCostVerdict` (MAINT-60, test code). **Extended** by §2.6.
- No class in `src/main` implements `PsiTreeChangePreprocessor` or `PsiTreeChangeListener`.

### Target State
An unpinned snapshot depends on a `LuaSourceSetTracker`: per-URL change counters summed over its
recorded sources, plus a resolution count that moves whenever an index answer or a file-less PSI
event can change resolution. A tree-change preprocessor maintains the counters. A frame whose
sources cannot be identified keeps `MODIFICATION_COUNT`. The sweep gains an after-edit oracle (§2.6).

## 2. Core Components

### 2.1 `net.internetisalie.lunar.lang.psi.types.LuaSourceChangeTracker` (new)
- **Shape**: `class LuaSourceChangeTracker(private val targetProject: Project)`, a `projectService`
  (§6), with `companion object { fun getInstance(project: Project): LuaSourceChangeTracker = project.service() }`.
- **State**: `fileCounters: ConcurrentHashMap<String, AtomicLong>`, `structureEpoch: SimpleModificationTracker`,
  `stampTotal: LuaStampTotal` (§2.2a). Only URL strings and longs: no `PsiFile`, `VirtualFile` or `Editor`.
- **Threading**: `fileCounters` and `structureEpoch` are lock-free atomics, so `fileChanged` and
  `structureChanged` are safe from any thread (§2.3 calls them from `fireEvent`, which holds the
  write lock for physical files but not for every event). `stampTotal`'s methods are
  `@Synchronized`. `countOf`, `resolutionCount` and `trackerFor` run in read actions.
- **Key API**:
  ```kotlin
  fun fileChanged(url: String)                                         // fileCounters.computeIfAbsent(url) { AtomicLong() }.incrementAndGet()
  fun structureChanged()                                               // structureEpoch.incModificationCount()
  fun countOf(url: String): Long                                       // fileCounters[url]?.get() ?: 0L
  fun resolutionCount(): Long                                          // §3.3
  fun psiModificationCount(): Long                                     // PsiModificationTracker.getInstance(targetProject).modificationCount
  fun trackerFor(psiFile: PsiFile, frame: LuaTypeSourceRecorder.SourceFrame): ModificationTracker?  // §3.1
  ```

### 2.2 `net.internetisalie.lunar.lang.psi.types.LuaSourceSetTracker` (new, `internal class`, same file as §2.1)
- **Shape**: `internal class LuaSourceSetTracker(private val owner: LuaSourceChangeTracker, private val sources: Set<String>, private val pending: List<LuaTypeSourceRecorder.SourceFrame>) : ModificationTracker`,
  holding no `Project` or `PsiFile`. `getModificationCount()` (§3.3) runs in a read action, possibly
  while another thread's build adds to a pending frame; §2.4's concurrent sets make that safe.

### 2.2a `net.internetisalie.lunar.lang.psi.types.LuaStampTotal` (new, `internal class`, same file as §2.1)
- **Shape**: turns the index stamps into a total that never repeats an earlier value once any stamp
  has changed (risks Risk 1.3). No constructor arguments. Fields: `private val lastStamps = LongArray(INDEX_COUNT)`,
  `private var offset = 0L`, `private var highWater = Long.MIN_VALUE`, `private var lastIndexTerm = 0L`.
  `INDEX_COUNT = 5` and `DUMB_OFFSET = 1L shl 62` are `internal const val`s in that file.
- **Key API** (both `@Synchronized`, no PSI or index access):
  ```kotlin
  @Synchronized fun advance(stamps: LongArray, shared: Long): Long   // §3.3
  @Synchronized fun dumbValue(shared: Long): Long                    // shared + lastIndexTerm + DUMB_OFFSET
  ```

### 2.3 `net.internetisalie.lunar.lang.psi.types.LuaSourceChangePreprocessor` (new)
- **Shape**: `class LuaSourceChangePreprocessor : PsiTreeChangePreprocessor` (`com.intellij.psi.impl`),
  no constructor arguments, as `PsiJavaModuleTreeChangePreprocessor` in `intellij-community`.
- **Key API**: `override fun treeChanged(event: PsiTreeChangeEventImpl)` (§3.4). No index or PSI
  reads. Looks the tracker up with `manager.project.serviceIfCreated<LuaSourceChangeTracker>()`
  (`com.intellij.openapi.components.serviceIfCreated`), so an event never creates the service.
- **Why a preprocessor**: `PsiManagerImpl.fireEvent` calls `PsiTreeChangePreprocessor.EP` extensions
  unconditionally, `PsiTreeChangeListener.EP` ones only when `myTreeChangeListeners` is non-empty.

### 2.4 Edits to existing classes
- `LuaTypeSourceRecorder` (`LuaTypeSourceRecorder.kt`):
  - Every `SourceFrame` set becomes `ConcurrentHashMap.newKeySet<String>()`.
  - New set `val pendingFrames: MutableSet<SourceFrame> = ConcurrentHashMap.newKeySet()`, by identity
    (`SourceFrame` does not override `equals`); added to `absorb` (`pendingFrames.addAll(other.pendingFrames)`)
    and to `isEmpty`.
  - New `private val inProgressFrames = ThreadLocal.withInitial { HashMap<PsiFile, SourceFrame>() }`.
  - `recording(body)` keeps its signature and delegates to a new `private fun <T> recordingInto(frame:
    SourceFrame, body: () -> T): Pair<T, SourceFrame>` holding today's body (push, run, pop in `finally`).
  - New `fun <T> recordingFor(psiFile: PsiFile, body: () -> T): Pair<T, SourceFrame>` (§3.2).
  - `reportInProgressHit(psiFile)` gains §3.2's pending step after its existing loop.
  - New `internal fun isUnidentified(url: String): Boolean = url.startsWith(UNIDENTIFIED_SOURCE)`.
- `LuaTypesSnapshot` (`LuaTypes.kt`):
  - `forFile` calls `LuaTypeSourceRecorder.recordingFor(psiFile) { LuaTypesVisitor.buildSnapshot(psiFile) }`
    in place of `recording { … }`.
  - `churnDependencyFor`'s `else` branch becomes
    `LuaSourceChangeTracker.getInstance(psiFile.project).trackerFor(psiFile, sourceFrame) ?: PsiModificationTracker.MODIFICATION_COUNT`.
  - The KDoc of `forFile` and `churnDependencyFor` names the unpinned dependency as §3.1's tracker.

### 2.5 `net.internetisalie.lunar.type.LuaSourceChangeTrackerTest` (new, test source)
- `BasePlatformTestCase`; methods `test…_MAINT63_TC1` to `…_TC8`, `…_TC14_<fixture>` (one per
  requirements *TC 14 fixtures* row), `…_TC15` and `…_TC16` (constructs `LuaStampTotal()` directly).
- Private helpers, each at most three arguments:
  - `append(file: PsiFile, text: String)`: a `WriteCommandAction` inserting `text` at the document
    end, then `commitDocument` (the sweep's `appendNewline` with the text as a parameter).
  - `replaceText(file: PsiFile, old: String, new: String)`: a `WriteCommandAction` setting the
    document text to `document.text.replace(old, new)`, then `commitDocument`.
  - `typeOf(file: PsiFile, name: String): String`: the last `LuaNameRef` with that text
    (`PsiTreeUtil.findChildrenOfType`), then `LuaTypesSnapshot.forFile(file).getValueType(ref).displayName()`.
  - `frameOf(file: PsiFile)`: `checkNotNull(LuaTypeSourceRecorder.snapshotFrames[LuaTypesSnapshot.forFile(file)])`.
  - `forceFullRebuild()`: `(LuaProjectSettings.getInstance(project).state.targetModificationTracker as SimpleModificationTracker).incModificationCount()`,
    then `dropPsiCaches()` in a `WriteCommandAction`. Both ticks are needed (risks DR-20).
  TC 8 and TC 15 build frames with `SourceFrame()` and read in `runReadAction`, as
  `TypeElevenGenerationSignalTest` does.

### 2.6 After-edit oracle in the sweep (test code)
- **Rows**: `Read`, `ColdRead`, `FileResult` and `FileResult.toRow` (`LuaSnapshotCostSweepTest.kt`)
  become `internal` (O4 builds a `FileResult`). `SweepRow` gains, after `digest`, with defaults: `editDigest: Int = 0`, `rebuildStatus: String = "not-run"`,
  `rebuildDigest: Int = 0`, `rebuildMs: Long = 0`, `rebuildBuilds: Int = 0`. `toRow` fills
  `editDigest` from `edit.errorDigest` and the rest from the rebuild `Read` (`status`,
  `errorDigest`, `ms`, `builds.size`); `SweepRow.toJson` writes them in that order.
- **`SnapshotCostVerdict` constants**: `const val ORACLE_BUDGET_MS = 15 * 60_000L` and
  `val ORACLE_SKIPPED = setOf("luals")` (risks Risk 1.5).
- **Oracle switch.** In `build.gradle.kts`, the `withSnapshotSweep` else-branch adds
  `systemProperty("lunar.snapshotCost.oracle", project.hasProperty("withSnapshotOracle").toString())`.
  The sweep test's companion gains `val ORACLE_ON: Boolean = System.getProperty("lunar.snapshotCost.oracle") == "true"`.
  With it off, `measureMember` does not call `rebuildPass` (every row's rebuild is
  `notRead(NOT_RUN)`) and `testZzVerdict` adds neither `oracleChecks` nor `oracleCounts`. Timing
  sweeps run with it off, so base and change timing sweeps do identical work and the rebuild pass
  times nothing; the oracle runs in its own invocation, `-PwithSnapshotOracle`, whose miss counts
  are recorded and never compared.
- **Rebuild time is outside the measured budget.** A companion `val REBUILD_MS = AtomicLong()` in
  the sweep test accumulates every rebuild read's `ms`; `budgetLeft()` becomes
  `System.currentTimeMillis() - RUN_START_MS - REBUILD_MS.get() < SnapshotCostVerdict.RUN_BUDGET_MS`,
  and `testZzVerdict` passes `System.currentTimeMillis() - RUN_START_MS - REBUILD_MS.get()` to
  `runTimeFailures`.
- **`measureMember`**: when `ORACLE_ON`, after `editPass`, call
  `rebuildPass(member: String, files: List<SweptFile>, edits: List<Read>): List<Read>` with
  `entry.name`. Per file, in order:
  1. Edit status not `OK`, or `member in ORACLE_SKIPPED`: `notRead(NOT_RUN)`.
  2. `REBUILD_MS.get() >= ORACLE_BUDGET_MS`: `notRead(SKIPPED)`.
  3. Otherwise a private `forceFullRebuild()` with §2.5's body, then
     `readTimed(file, SnapshotCostVerdict.COLD_DEADLINE_MS)`; add its `ms` to `REBUILD_MS`; return it.

  `FileResult` gains a fifth field `rebuild: Read`; `sweepMember`'s over-budget branch passes
  `notRead(SKIPPED)` for it.
- **`oracleChecks(rows: List<SweepRow>): List<String>`** in `SnapshotCostVerdict`, the concatenation
  of these, in this order. "Edit ok" is `editStatus == OK`; "compared" is edit ok and
  `rebuildStatus == OK`.
  - `oracleMismatches(rows)`: per compared row with `editDigest != rebuildDigest`,
    `"<member>/<file> oracle: edit digest <editDigest> != rebuild digest <rebuildDigest>"`.
  - `oracleUnforced(rows)`: per compared row with `rebuildBuilds == 0`,
    `"<member>/<file> oracle: rebuild read built nothing"` (a forced rebuild always builds the read
    file's own snapshot, so the read was served from the edit read's cache).
  - `oracleUnread(rows)`: per edit-ok row whose member is not in `ORACLE_SKIPPED` and whose
    `rebuildStatus` is not `OK`, `TIMEOUT` or `SKIPPED` (so `not-run` or an `exception:` status),
    `"<member>/<file> oracle: rebuild read <rebuildStatus>"`.
  - `oracleSkipped(rows)`: when any edit-ok row has `rebuildStatus == SKIPPED`, the single line
    `"oracle rebuild pass over budget: <n> row(s) not compared"`.
  - `"oracle compared no rows"` when `oracleCompared(rows) == 0`.
- **Fresh file.** Rows from an older `sweep.jsonl` have a null `rebuildStatus`, so each edit-ok one
  would emit `oracle: rebuild read null`. This cannot happen: the opt-in deletes
  `build/snapshot-cost/` before every run, so `testZzVerdict` reads only its own run's rows.
- **Counts**: `oracleCompared(rows)` counts compared rows. `oracleCounts(rows)` is
  `"oracle compared=<n> rebuildTimeouts=<m> rebuildMs=<t>"` (`m`: rows with `rebuildStatus ==
  TIMEOUT`; `t`: the sum of `rebuildMs`).
- **`testZzVerdict`**, when `ORACLE_ON`, adds `SnapshotCostVerdict.oracleChecks(rows)` to
  `checks`, after `runTimeFailures`. It writes `verdict.txt` as
  `(checks.map { "check: $it" } + counts + release).joinToString("\n")`, where `counts` is
  `listOf(oracleCounts(rows))` when `ORACLE_ON` and empty otherwise, so every check line is kept on
  disk, one per line, prefixed `check: `. Printing to stdout and the assertion are
  unchanged.
- **Tests** (`SnapshotCostVerdictTest`), each with the mutant that turns it red:

  | Test | Rows | Expected | Mutant |
  |---|---|---|---|
  | `testO1OracleMismatchIsReported` | one compared row, digests 1 and 2 | one `oracle: edit digest` line | `oracleMismatches` returns an empty list |
  | `testO2OracleSkipsUnreadRows` | edit ok / rebuild `timeout`, digests 1 and 0; edit `timeout` / rebuild ok, digests 0 and 1 | no `oracleMismatches` line | `oracleMismatches` drops either status condition |
  | `testO3OracleComparedCountsOkPairs` | one compared row; one edit ok / rebuild `timeout` | `oracleCompared` is 1 | `oracleCompared` counts edit-ok rows |
  | `testO4RowTakesEachDigestFromItsRead` | a `FileResult` whose cold, edit and rebuild reads have digests 1, 2, 3, the rebuild with one `BuildRecord` | `toRow("m")`: `digest` 1, `editDigest` 2, `rebuildDigest` 3, `rebuildBuilds` 1 | `editDigest` taken from the cold read (requirements mutation 17) |
  | `testO5UnforcedRebuildIsReported` | one compared row, equal digests, `rebuildBuilds` 0; the same with 3 | one `oracleUnforced` line; none | `oracleUnforced` returns an empty list |
  | `testO6OverBudgetRebuildIsReported` | edit ok / rebuild `skipped`; edit `skipped` / rebuild `skipped` | one `oracleSkipped` line; none | `oracleSkipped` returns an empty list; or drops the edit-ok condition |
  | `testO7UnreadRebuildIsReported` | member `m` unless named: edit ok / rebuild `not-run`; the same, member `luals`; edit ok / rebuild `exception:X`; edit ok / rebuild `timeout`; edit `timeout` / rebuild `not-run` | one line; none; one line; none; none | `oracleUnread` returns an empty list; or drops the `ORACLE_SKIPPED` condition; or drops the `TIMEOUT` exclusion; or drops the edit-ok condition |
  | `testO8NoComparedRowIsReported` | one edit ok / rebuild `timeout` row | `oracleChecks` contains `oracle compared no rows` | the no-rows line removed |

## 3. Algorithms

### 3.1 `trackerFor(psiFile, frame)`
1. `val ownUrl = psiFile.originalFile.virtualFile?.url ?: return null`.
2. If `frame.unreplayedWarm.isNotEmpty()`, return null.
3. `val sources = HashSet<String>()`; add `ownUrl`, every `frame.urls` and every `frame.inProgressHits`.
4. If any element of `sources` satisfies `LuaTypeSourceRecorder.isUnidentified`, return null.
5. Return `LuaSourceSetTracker(this, sources, frame.pendingFrames.toList())`.

A null result makes `churnDependencyFor` return `MODIFICATION_COUNT`.

### 3.2 Pending in-progress frames
A build that reads another file's in-progress snapshot depends on whatever that build consumes,
including sources consumed before the reader's frame was opened (requirements TC 5).
- `recordingFor(psiFile, body)`: `val frame = SourceFrame()`; `val building = inProgressFrames.get()`;
  `building[psiFile] = frame`; `try { return recordingInto(frame, body) } finally { building.remove(psiFile) }`.
- `reportInProgressHit(psiFile)`, after adding the URL to every open frame's `inProgressHits`:
  `val building = inProgressFrames.get()[psiFile] ?: return`; for each open frame `f` with
  `f !== building`, `f.pendingFrames.add(building)`.

`inProgressFrames` and `LuaTypesVisitor.inProgressBuilds` share the key (`forFile`'s
`file.containingFile`) and lifetime (one build on this thread). `absorb` carries pending frames out.

### 3.3 Counts
`LuaSourceChangeTracker.resolutionCount()`:
1. `val shared = structureEpoch.modificationCount`.
2. If `DumbService.isDumb(targetProject)`, return `stampTotal.dumbValue(shared)`.
3. `stamps = longArrayOf(…)` of `FileBasedIndex.getInstance().getIndexModificationStamp(id, targetProject)`,
   through `private fun <K, V> stampOf(id: ID<K, V>): Long`, as explicit calls in this order:
   `StubUpdatingIndex.INDEX_ID`, `LuaGlobalAssignmentIndex.KEY`, `LuaCatsTypeNameIndex.KEY`,
   `LuaReceiverMemberIndex.KEY`, `LuaMemberFieldIndex.KEY`.
4. On `IndexNotReadyException` or `ServiceNotReadyException` from step 3, return as step 2. Both
   mean an index is not available yet; the second is thrown in smart mode while a forced rebuild
   is pending, as after a target switch (risks Gap 2.4). Any other `ProcessCanceledException`
   propagates.
5. Return `stampTotal.advance(stamps, shared)`.

`LuaStampTotal.advance(stamps, shared)`:
1. `val changed = !stamps.contentEquals(lastStamps)`.
2. `var total = shared + offset + stamps.sum()`.
3. If `changed && total <= highWater`: `offset += highWater + 1 - total`, then `total = highWater + 1`.
4. `stamps.copyInto(lastStamps)`; `lastIndexTerm = offset + stamps.sum()`;
   `highWater = maxOf(highWater, total)`; return `total`.

**Why `advance` never repeats a value.** `shared` never decreases and `offset` only grows. With the
stamps and `shared` unchanged, `total` equals the previous total. With `shared` grown and the stamps
unchanged, `total` is above every earlier total. With any stamp changed, step 3 puts `total` above
`highWater`, even when a stamp went down. So the smart-mode count never decreases, and strictly
increases whenever a stamp or `shared` changes (requirements TC 16).

`LuaSourceSetTracker.getModificationCount()`:
1. `val live = HashSet(sources)`; `val visited = Collections.newSetFromMap(IdentityHashMap<SourceFrame, Boolean>())`;
   `val work = ArrayDeque(pending)`; `var degraded = false`.
2. While `work` is not empty: `val f = work.removeLast()`; if `!visited.add(f)`, continue; if
   `f.unreplayedWarm.isNotEmpty()`, `degraded = true`; add `f.urls` and `f.inProgressHits` to `live`;
   add every `f.pendingFrames` element to `work`.
3. If any element of `live` is unidentified, `degraded = true`.
4. `val fallback = if (degraded) owner.psiModificationCount() else 0L`.
5. Return `live.sumOf { owner.countOf(it) } + owner.resolutionCount() + fallback`.

**Why the value is sound.** Every term never decreases in smart mode and `live` only grows, so any
tick in a term the snapshot depends on increases the sum. Entering or leaving the dumb branch moves
the value by `DUMB_OFFSET`, far above any reachable count: a snapshot built in smart mode is rebuilt
on its first read in dumb mode, and one built in dumb mode is kept while dumb and rebuilt on exit,
as base's `MODIFICATION_COUNT` ticks on enter and exit.

**Index choice.** The ids cover every index a build reads for another file's declarations without
recording its URL (risks DR-18 built a late declaration for each): `Stubs`, from which
`LuaGlobalDeclarationIndex`, `LuaClassNameIndex` and `LuaAliasIndex` derive; `LuaGlobalAssignmentIndex`
and `LuaCatsTypeNameIndex` (`LuaTypeManagerImpl` `:242`, `:515`); `LuaReceiverMemberIndex`
(`LuaTypeManagerImpl:637`, `LuaImplicitFields:129`); `LuaMemberFieldIndex` (`LuaNameReference:164`,
reached through `LuaExpectedCallbackResolver`'s `nameRef.reference?.resolve()`). Excluded:
`LuaDescriptionIndex` (only `LuaDocSearchEverywhereContributor` reads it) and `LuaFileBindingsIndex`
(`LuaNameReference.queryFiles`, `:132`), because the declarations
`LuaExpectedCallbackResolver.resolveCalleeDeclaration` accepts (`LuaExpectedCallbackResolver.kt:65`)
are stubbed, so a `@param` edit ticks `Stubs` and records the declaring file (DR-16 L1, DR-18
`localfn`; risks Risk 1.2).

### 3.4 `LuaSourceChangePreprocessor.treeChanged(event)`
1. If `!PsiModificationTrackerImpl.canAffectPsi(event)`, return: the filter that decides whether
   `MODIFICATION_COUNT` ticks.
2. `val manager = event.source as? PsiManager ?: return`.
3. `val tracker = manager.project.serviceIfCreated<LuaSourceChangeTracker>() ?: return`.
4. `val file = event.file`. If `file != null && file !is LuaFile`
   (`net.internetisalie.lunar.lang.psi.LuaFile`), return.
5. `val url = file?.originalFile?.virtualFile?.url`. If null, `tracker.structureChanged()`;
   otherwise `tracker.fileChanged(url)`.

`originalFile` matches the recorder's key, so an edit to a completion copy bumps its original.

## 4. External Data & Parsing
None beyond the sweep's own files (§2.6) and those TC 11 and TC 12 name.

## 5. Edge Cases
A cancelled pending build's frame stays as far as it got, and readers keep it pending. File
create, delete, move and rename, roots and file-type changes arrive as null-file events (DR-15).
`dropResolveCaches()` ticks nothing, in base or here. Library files take the unpinned branch like
project files. An index stamp reset is `advance` step 3 (risks Risk 1.3).

## 6. Integration Points
`plugin.xml`, inside `<extensions defaultExtensionNs="com.intellij">`, next to the `LuaTypeManager`
`projectService` (declared there, not with `@Service`, per `docs/engineering-contract.md` §4):

```xml
<!-- MAINT-63: per-source change counters for unpinned type snapshots -->
<projectService serviceImplementation="net.internetisalie.lunar.lang.psi.types.LuaSourceChangeTracker"/>
<psi.treeChangePreprocessor implementation="net.internetisalie.lunar.lang.psi.types.LuaSourceChangePreprocessor"/>
```

No settings or index changes. `build.gradle.kts` gains only §2.6's oracle switch.

## 7. Requirement Coverage

| Requirement | Priority | Implemented by (section) |
|-------------|----------|--------------------------|
| MAINT-63-01 | M | §2.1, §2.2, §2.4, §3.1, §3.3, §3.4 |
| MAINT-63-02 | M | §2.2a, §3.3 (resolution count, stamp decrease, dumb mode), §3.1 (fallbacks), §3.2 (pending), §3.4 (null-file events) |
| MAINT-63-03 | M | §3 as a whole; risks DR-11, DR-12 |
| MAINT-63-04 | M | §3 soundness; checked by TC 11, TC 12 and TC 13 (§2.6) |

## 8. Alternatives Considered
Variants are defined in risks *Variants*; frame fallbacks are risks Decision 2.1. Not taken: V1's
whitespace-only edit epoch (V3's index stamps answer from the indexes, DR-12); V2's strongly held
library `PsiFile`s (the contract forbids it); a `psi.treeChangeListener` (§2.3); an epoch on
`LuaSettingsChangedListener.TOPIC` or `DumbService.DUMB_MODE` (every DR-15 tick arrived as a
preprocessor event or a dumb-mode transition).

## 9. Open Questions
_None. Deferred items are tracked in [risks-and-gaps.md](risks-and-gaps.md)._
