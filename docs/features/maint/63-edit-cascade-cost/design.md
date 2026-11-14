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
`LuaTypesSnapshot.forFile` (`LuaTypes.kt:310`) caches a file's snapshot with
`CachedValuesManager.getCachedValue(psiFile, LuaTypesVisitor.KEY)`. The build runs inside
`LuaTypeSourceRecorder.recording`, which opens a `SourceFrame` (`LuaTypeSourceRecorder.kt:42`). The
type manager and `forFile` report into every open frame:
- `reportFile`: the URL of a file whose content was consumed;
- `reportAbsence`, `reportRescuedGlobal`: a resolution that found nothing, or was rescued;
- `reportInProgressHit`: a file whose build was still running (`LuaTypesVisitor.inProgressSnapshot`);
- `reportWarmSnapshot`: replays a cached snapshot's frame, or records `unreplayedWarm` when none is stored.

`dependenciesFor` (`LuaTypes.kt:354`) returns `[psiFile, churnDependencyFor(…), targetModificationTracker]`.
`churnDependencyFor` (`LuaTypes.kt:407`) returns the generation tracker when `isPinnable`, and
otherwise `PsiModificationTracker.MODIFICATION_COUNT`. Every PSI tree change ticks that count
(`PsiModificationTrackerImpl.treeChanged`), and so do dumb-mode enter and exit
(`PsiModificationTrackerImpl` constructor, `DumbModeListenerBackgroundable`). As a result, every
unpinned snapshot is discarded on every edit.

### Prior Art in This Repo
- `LuaTypeSourceRecorder` and `SourceFrame`, TYPE-11. **Extended**: a frame also records the
  frames of the in-progress builds it read (§3.2), and its sets become concurrent.
- `LuaTypesSnapshot.isPinnable` / `churnDependencyFor` / `dependenciesFor`, TYPE-11. **Extended**:
  only `churnDependencyFor`'s unpinned branch changes. The pinned branch and the three-element
  shape stay (risks DR-04: the array length must not vary by branch).
- `LuaTypeManagerImpl`, a project service registered in `plugin.xml`. §2.1 is registered the same
  way (design §7), as `docs/engineering-contract.md` §4 requires.
- `LuaSnapshotCostSweepTest` and `SnapshotCostVerdict` (MAINT-60), test code. **Extended** by
  §2.6's oracle pass and check.
- No class in `src/main` implements `PsiTreeChangePreprocessor` or `PsiTreeChangeListener`.
  `TypeElevenDr06StampProbeTest` adds a listener in a test only.

### Target State
An unpinned snapshot depends on a `LuaSourceSetTracker`. It sums per-URL change counters over
the snapshot's recorded sources, plus a resolution count that moves whenever an index answer or a
file-less PSI event can change what resolution returns. A tree-change preprocessor maintains the
counters. When a frame's sources cannot be identified, the snapshot falls back to
`MODIFICATION_COUNT`. The sweep gains an after-edit oracle (§2.6).

**The design is held** (risks Decision 2.1): it fails §2.6's oracle on `require` cycles (risks
Risk 1.1, DR-20), and implementation waits for risks DR-22.

## 2. Core Components

### 2.1 `net.internetisalie.lunar.lang.psi.types.LuaSourceChangeTracker` (new)
- **Responsibility**: holds the per-URL counters and the resolution count; builds trackers.
- **Shape**: `class LuaSourceChangeTracker(private val targetProject: Project)`, registered as a
  `projectService` (§7), with `companion object { fun getInstance(project: Project): LuaSourceChangeTracker = project.service() }`.
- **State**: `fileCounters: ConcurrentHashMap<String, AtomicLong>`, `structureEpoch: SimpleModificationTracker`,
  `lastIndexSum: AtomicLong`, and `lastStamps: LongArray(INDEX_COUNT)` guarded by `synchronized(lastStamps)`.
  Only URL strings and longs are held: no `PsiFile`, `VirtualFile` or `Editor`.
- **Threading**: lock-free. `fileChanged` and `structureChanged` run under the write lock (from
  §2.3); `countOf`, `resolutionCount` and `trackerFor` run in read actions.
- **Key API**:
  ```kotlin
  fun fileChanged(url: String)                                         // §3.4
  fun structureChanged()                                               // §3.4
  fun countOf(url: String): Long                                       // fileCounters[url]?.get() ?: 0L
  fun resolutionCount(): Long                                          // §3.3
  fun psiModificationCount(): Long                                     // PsiModificationTracker.getInstance(targetProject).modificationCount
  fun trackerFor(psiFile: PsiFile, frame: LuaTypeSourceRecorder.SourceFrame): ModificationTracker?  // §3.1
  ```

### 2.2 `net.internetisalie.lunar.lang.psi.types.LuaSourceSetTracker` (new, `internal class`, same file as §2.1)
- **Responsibility**: the churn dependency of one unpinned snapshot.
- **Shape**: `internal class LuaSourceSetTracker(private val owner: LuaSourceChangeTracker, private val sources: Set<String>, private val pending: List<LuaTypeSourceRecorder.SourceFrame>) : ModificationTracker`.
  It holds the service and frames, never a `Project` or `PsiFile` directly. It lives in the
  `CachedValue` stored on the `PsiFile`, so it dies with that file.
- **Threading**: `getModificationCount()` runs wherever the platform checks the cached value, in a
  read action, possibly while a build on another thread still adds to a pending frame. §3.2's
  concurrent sets make that safe.
- **Key API**: `override fun getModificationCount(): Long` (§3.3).

### 2.3 `net.internetisalie.lunar.lang.psi.types.LuaSourceChangePreprocessor` (new)
- **Responsibility**: feeds every PSI change that ticks `MODIFICATION_COUNT` into §2.1.
- **Shape**: `class LuaSourceChangePreprocessor : PsiTreeChangePreprocessor` (`com.intellij.psi.impl`),
  with no constructor arguments, as `PsiJavaModuleTreeChangePreprocessor` in `intellij-community`.
- **Threading**: called by `PsiManagerImpl.fireEvent` under the write lock, before listeners. It
  performs no index or PSI reads, and skips events in non-Lua files (§3.4 step 4).
- **Key API**: `override fun treeChanged(event: PsiTreeChangeEventImpl)` (§3.4).
- **Lookup**: `manager.project.serviceIfCreated<LuaSourceChangeTracker>()`
  (`com.intellij.openapi.components.serviceIfCreated`), so an event never creates the service.
- **Why a preprocessor rather than `psi.treeChangeListener`**: `PsiManagerImpl.fireEvent` calls
  every `PsiTreeChangePreprocessor.EP` extension unconditionally, but calls `PsiTreeChangeListener.EP`
  extensions only when `myTreeChangeListeners` is also non-empty (`PsiManagerImpl.java` in
  `intellij-community`, `fireEvent`). `PsiModificationTrackerImpl` is itself a preprocessor.

### 2.4 Edits to existing classes
- `LuaTypeSourceRecorder` (`LuaTypeSourceRecorder.kt`):
  - `SourceFrame`'s five sets become `ConcurrentHashMap.newKeySet<String>()`.
  - A sixth set is added: `val pendingFrames: MutableSet<SourceFrame> = ConcurrentHashMap.newKeySet()`.
    `SourceFrame` does not override `equals`, so the set is by identity. Add it to `absorb`
    (`pendingFrames.addAll(other.pendingFrames)`) and to `isEmpty`.
  - New `private val inProgressFrames = ThreadLocal.withInitial { HashMap<PsiFile, SourceFrame>() }`.
  - `recording(body)` keeps its signature and delegates to a new
    `private fun <T> recordingInto(frame: SourceFrame, body: () -> T): Pair<T, SourceFrame>`. That
    function holds today's body: push the frame, run, pop in `finally`.
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
- `BasePlatformTestCase`, with methods `test…_MAINT63_TC1` to `…_TC8` for requirements TC 1 to TC 8.
- Helpers, private to the class, each at most three arguments:
  - `append(file: PsiFile, text: String)`: a `WriteCommandAction` that inserts `text` at the
    document end, then `commitDocument`. This is the sweep's `appendNewline` with the text as a
    parameter.
  - `replaceText(file: PsiFile, old: String, new: String)`: a `WriteCommandAction` that sets the
    document text to `document.text.replace(old, new)`, then `commitDocument`.
  - `typeOf(file: PsiFile, name: String): String`: the last `LuaNameRef` with that text
    (`PsiTreeUtil.findChildrenOfType`), then `LuaTypesSnapshot.forFile(file).getValueType(ref).displayName()`.
  - `frameOf(file: PsiFile)`: `checkNotNull(LuaTypeSourceRecorder.snapshotFrames[LuaTypesSnapshot.forFile(file)])`.
  - `forceFullRebuild()`: `(LuaProjectSettings.getInstance(project).state.targetModificationTracker as SimpleModificationTracker).incModificationCount()`,
    then `dropPsiCaches()` in a `WriteCommandAction`. The first invalidates every snapshot through
    its third dependency, whatever its churn dependency; the second drops the type manager's caches.
    Neither alone is a full rebuild (risks DR-20).
- TC 8 and TC 15 build the frames with `LuaTypeSourceRecorder.SourceFrame()` and read inside
  `runReadAction`, as `TypeElevenGenerationSignalTest` does. TC 14 is one method per fixture,
  `test…_MAINT63_TC14_<fixture>`, with the fixture texts of requirements TC 14 and a distinct file
  and global name per method.

### 2.6 After-edit oracle in the sweep (test code)
- **`SweepRow`** (`SnapshotCostVerdict.kt`) gains three fields after `digest`, with defaults so
  existing constructors compile: `editDigest: Int = 0`, `rebuildStatus: String = "not-run"`,
  `rebuildDigest: Int = 0`. `toRow` fills `editDigest` from the edit `Read.errorDigest`, and the
  other two from the rebuild `Read`. `SweepRow.toJson` writes all three, in that order.
- **`LuaSnapshotCostSweepTest.measureMember`**: after `editPass`, call
  `rebuildPass(member: String, files: List<SweptFile>, edits: List<Read>): List<Read>` with
  `entry.name`. For each file, in order: if its edit status is not `OK`, or `!budgetLeft()`, or
  `member in ORACLE_SKIPPED` (a companion `val ORACLE_SKIPPED = setOf("luals")`; BUG-97: one
  rebuild there takes up to 48 s), return `notRead(NOT_RUN)`; otherwise call a private
  `forceFullRebuild()` with §2.5's body and return `readTimed(file, SnapshotCostVerdict.COLD_DEADLINE_MS)`.
  `FileResult` gains a fifth field `rebuild: Read`; `sweepMember`'s over-budget branch passes
  `notRead(SKIPPED)`. The pass runs after the member's edit pass, so no timed read is affected.
- **`SnapshotCostVerdict.oracleMismatches(rows: List<SweepRow>): List<String>`**: for every row
  with `editStatus == OK` and `rebuildStatus == OK` and `editDigest != rebuildDigest`, the line
  `"<member>/<file> oracle: edit digest <editDigest> != rebuild digest <rebuildDigest>"`.
  `testZzVerdict` adds it to `checks`, so any mismatch fails the sweep. Rows read from an older
  `sweep.jsonl` by Gson have a null `rebuildStatus`; `==` on it is false, so they compare nothing.
- **`SnapshotCostVerdict.oracleCompared(rows: List<SweepRow>): Int`**: the number of rows with both
  statuses `OK`. **`oracleCounts(rows)`**: `"oracle compared=<n> rebuildTimeouts=<m>"`, where `m`
  counts rows with `rebuildStatus == "timeout"`; `testZzVerdict` prints it and writes it to
  `verdict.txt`. When `oracleCompared(rows) == 0`, `testZzVerdict` adds the check line
  `"oracle compared no rows"`, so a sweep whose rebuild pass never ran cannot pass.
- **Cost**: the rebuild pass took 43 s on DR-20's five members (263 files), about 160 ms per file;
  over a full sweep's rows that is of the order of five minutes. It counts against `RUN_BUDGET_MS`
  through `budgetLeft()`, so an overrun shows as `not-run` rebuilds and skipped members, which
  `coverage` and `oracleCounts` report.
- **Tests**: `SnapshotCostVerdictTest` gains `testO1OracleMismatchIsReported` (one row, digests 1
  and 2, both `ok`: one line) and `testO2OracleSkipsUnreadRows` (`rebuildStatus = "timeout"` or
  `editStatus = "timeout"`: no line) and `testO3OracleComparedCountsOkPairs` (one `ok`/`ok` row and
  one `ok`/`not-run` row: 1). Mutant: `oracleMismatches` returns an empty list; O1 turns red.

## 3. Algorithms

### 3.1 `trackerFor(psiFile, frame)`
1. `val ownUrl = psiFile.originalFile.virtualFile?.url ?: return null`.
2. If `frame.unreplayedWarm.isNotEmpty()`, return null.
3. `val sources = HashSet<String>()`; add `ownUrl`, every `frame.urls` and every `frame.inProgressHits`.
4. If any element of `sources` satisfies `LuaTypeSourceRecorder.isUnidentified`, return null.
5. Return `LuaSourceSetTracker(this, sources, frame.pendingFrames.toList())`.

A null result makes `churnDependencyFor` return `MODIFICATION_COUNT`, today's behaviour.

### 3.2 Pending in-progress frames
A build that reads another file's in-progress snapshot depends on whatever that build consumes,
including sources it consumed before the reader's frame was opened (requirements TC 5).
- `recordingFor(psiFile, body)`:
  1. `val frame = SourceFrame()`; `val building = inProgressFrames.get()`; `building[psiFile] = frame`.
  2. `try { return recordingInto(frame, body) } finally { building.remove(psiFile) }`.
- `reportInProgressHit(psiFile)`, after adding the URL to every open frame's `inProgressHits`:
  1. `val building = inProgressFrames.get()[psiFile] ?: return`.
  2. For each open frame `f` with `f !== building`: `f.pendingFrames.add(building)`.

`inProgressFrames` and `LuaTypesVisitor.inProgressBuilds` are keyed by the same `psiFile`
(`forFile`'s `file.containingFile`), and both are populated only for the duration of a build on the
current thread. A warm replay carries pending frames outward through `absorb`.

### 3.3 Counts
`LuaSourceChangeTracker.resolutionCount()`:
1. `val shared = structureEpoch.modificationCount`.
2. If `DumbService.isDumb(targetProject)`, return `shared + lastIndexSum.get() + DUMB_OFFSET`, where
   `private const val DUMB_OFFSET = 1L shl 62`.
3. Otherwise compute `indexSum` as the sum of
   `FileBasedIndex.getInstance().getIndexModificationStamp(id, targetProject)` over these five ids
   (`INDEX_COUNT = 5`), through a helper `private fun <K, V> stampOf(id: ID<K, V>): Long`, written
   as explicit calls in this order:
   `StubUpdatingIndex.INDEX_ID`, `LuaGlobalAssignmentIndex.KEY`, `LuaCatsTypeNameIndex.KEY`,
   `LuaReceiverMemberIndex.KEY`, `LuaMemberFieldIndex.KEY`.
4. On `IndexNotReadyException` from step 3, return as step 2. Any `ProcessCanceledException`,
   including `ServiceNotReadyException`, propagates.
5. In `synchronized(lastStamps)`: if any stamp is below its entry in `lastStamps`, call
   `structureEpoch.incModificationCount()` (Risk 1.3); then copy the stamps into `lastStamps`.
6. `lastIndexSum.set(indexSum)`; return `structureEpoch.modificationCount + indexSum`.

`LuaSourceSetTracker.getModificationCount()`:
1. `val live = HashSet(sources)`; `val visited = Collections.newSetFromMap(IdentityHashMap<SourceFrame, Boolean>())`;
   `val work = ArrayDeque(pending)`; `var degraded = false`.
2. While `work` is not empty: `val f = work.removeLast()`; if `!visited.add(f)`, continue; if
   `f.unreplayedWarm.isNotEmpty()`, set `degraded = true`; add `f.urls` and `f.inProgressHits` to
   `live`; add every `f.pendingFrames` element to `work`.
3. If any element of `live` is unidentified, set `degraded = true`.
4. `val fallback = if (degraded) owner.psiModificationCount() else 0L`.
5. Return `live.sumOf { owner.countOf(it) } + owner.resolutionCount() + fallback`.

**Why the value is sound.** Every term is non-decreasing, and `live` only grows, so any tick in
a term the snapshot depends on changes the sum. The exceptions are entering and leaving the dumb
branch: `DUMB_OFFSET` is far larger than any reachable count, so that change always moves the value.
A snapshot built in smart mode is rebuilt on its first read in dumb mode. One built in dumb mode is
kept while dumb and rebuilt on exit. That is base's pattern, which ticks `MODIFICATION_COUNT` on
enter and on exit.

**Index choice.** The ids cover every index a snapshot build reads for another file's
declarations without recording that file's URL. Risks DR-18 constructed a late declaration for
each, and its mutants show which ids tick alone and which tick in pairs (requirements mutations 3
to 6):
- `Stubs`: the stub indexes `LuaGlobalDeclarationIndex`, `LuaClassNameIndex` and `LuaAliasIndex`
  are derived from it, and it ticked on every hosted-declaration edit probed (risks DR-14, DR-16).
- `LuaGlobalAssignmentIndex` and `LuaCatsTypeNameIndex`: read by `LuaTypeManagerImpl` (`:242`, `:515`).
- `LuaReceiverMemberIndex`: read by `LuaTypeManagerImpl` (`:637`) and `LuaImplicitFields` (`:129`).
- `LuaMemberFieldIndex`: read by `LuaNameReference` (`:164`, through `LuaMemberFieldNavigation.find`),
  which a build reaches through `LuaExpectedCallbackResolver`'s `nameRef.reference?.resolve()`. A
  new member-field target makes that `resolve()` ambiguous, so it returns null (DR-18 `mfield2`).

Two indexes are excluded:
- `LuaDescriptionIndex`: no snapshot build reads it (its reader is `LuaDocSearchEverywhereContributor`).
- `LuaFileBindingsIndex`: read through `LuaNameReference.queryFiles` (`:132`). The declarations
  `LuaExpectedCallbackResolver.resolveCalleeDeclaration` accepts are a `LuaLocalFuncDecl` (the
  resolved element's parent), a `LuaFuncDecl`, or the `LuaFuncDecl` two levels up
  (`LuaExpectedCallbackResolver.kt:65`). Both are stubbed: a `@param` edit on either ticks `Stubs`
  and records the declaring file's URL (DR-16 L1, DR-18 `localfn`). See risks Risk 1.2.

### 3.4 `LuaSourceChangePreprocessor.treeChanged(event)`
1. If `!PsiModificationTrackerImpl.canAffectPsi(event)`, return. This is the exact filter that
   decides whether `MODIFICATION_COUNT` ticks.
2. `val manager = event.source as? PsiManager ?: return`.
3. `val tracker = manager.project.serviceIfCreated<LuaSourceChangeTracker>() ?: return`. No tracker
   exists before the service does, so no snapshot depends on an earlier event.
4. `val file = event.file`. If `file != null && file !is LuaFile`
   (`net.internetisalie.lunar.lang.psi.LuaFile`), return: no snapshot records a non-Lua file.
5. `val url = file?.originalFile?.virtualFile?.url`. If null, `tracker.structureChanged()`;
   otherwise `tracker.fileChanged(url)`.

`originalFile` matches the recorder's key (`reportFile` uses `psiFile.originalFile.virtualFile.url`),
so an edit to a completion copy bumps its original's counter.

## 4. External Data & Parsing
None. The acceptance inputs are this repo's own `sweep.jsonl`, `luacheck.json`, `results.json` and
`digests.tsv`, compared as MAINT-65 compares them.

## 5. Data Flow

### Example 1: an edit to an unrelated file (TC 1)
`b.lua` is built: `require("a")` reports `a.lua`'s URL; `b`'s tracker has sources
`{b.lua, package.lua, a.lua}` and no pending frames. Appending to `c.lua` fires tree events with
file `c.lua`, so the preprocessor bumps only `c.lua`'s counter. No index entry changes, so
`resolutionCount` is unchanged. `b`'s sum is unchanged, and the cached snapshot is served.

### Example 2: a global declared later (TC 3)
`b2.lua` reads `GLOBAL_M63`, which nothing declares. Its frame records an absence and no URL.
Appending `GLOBAL_M63 = 'str'` to `c2.lua` bumps `c2.lua`'s counter, which `b2` does not depend on.
It also changes `LuaGlobalAssignmentIndex` and `LuaReceiverMemberIndex` entries, which moves
`resolutionCount`, so `b2` is rebuilt and `y` is typed `string`.

## 6. Edge Cases
- **Cancelled build of a pending file**: its frame stays as far as it got, and readers keep it as
  pending. What a reader saw is a function of sources already reported into that frame.
- **File created, deleted, moved or renamed**: each arrives as at least one null-file event (risks
  DR-15 P2–P4), which ticks `structureEpoch`, so every tracker moves.
- **Roots or file-type change**: `PROP_ROOTS` and `PROP_FILE_TYPES` arrive as null-file events
  (DR-15 P7). No VFS-structure or project-roots term is added: in DR-18 (`extraterms`) they changed
  no fixture's outcome.
- **Event in a non-Lua file**: §2.3 skips it, so a `.txt` or `.json` edit moves no tracker.
- **`dropResolveCaches()`**: ticks nothing, in base or here (DR-15 P6).
- **Library and checkout files** (the 66% of DR-10's rebuild time): they take the unpinned branch
  and get a tracker like a project file.
- **Index stamp reset by an index rebuild**: a stamp below its last seen value ticks
  `structureEpoch` (§3.3 step 5), so the sum cannot return to a stored value by coincidence (risks
  Risk 1.3).

## 7. Integration Points
`plugin.xml`, inside `<extensions defaultExtensionNs="com.intellij">` next to the `LuaTypeManager`
`projectService`:

```xml
<!-- MAINT-63: per-source change counters for unpinned type snapshots -->
<projectService serviceImplementation="net.internetisalie.lunar.lang.psi.types.LuaSourceChangeTracker"/>
<psi.treeChangePreprocessor implementation="net.internetisalie.lunar.lang.psi.types.LuaSourceChangePreprocessor"/>
```

The service is declared in `plugin.xml`, not with `@Service`, as `docs/engineering-contract.md` §4
requires. There are no settings, index or `build.gradle.kts` changes.

## 8. Requirement Coverage

| Requirement | Priority | Implemented by (section) |
|-------------|----------|--------------------------|
| MAINT-63-01 | M | §2.1, §2.2, §2.4, §3.1, §3.3, §3.4 |
| MAINT-63-02 | M | §3.1 (fallbacks), §3.2 (pending), §3.3 (resolution count, dumb mode), §3.4 (null-file events) |
| MAINT-63-03 | M | §3; risks DR-11, DR-12 |
| MAINT-63-04 | M | §3 soundness; checked by TC 11 and TC 12 (audits and alone digests) and TC 13 (§2.6 oracle) |

## 9. Alternatives Considered
Measurements are in [risks-and-gaps.md](risks-and-gaps.md).
- **A whitespace-only edit heuristic as the resolution epoch (V1).** Not taken: it guesses which
  edits change resolution. V3's index stamps answer that from the indexes themselves, and they
  ticked on no `"\n"` edit (DR-12).
- **Holding library `PsiFile`s strongly (V2).** Not taken: the contract forbids hard references to
  `PsiFile`. It is recorded as Future Work.
- **A `psi.treeChangeListener` or programmatic listener.** Not taken (§2.3).
- **Ticking the epoch on `LuaSettingsChangedListener.TOPIC` or `DumbService.DUMB_MODE`.** Not
  needed. Every `MODIFICATION_COUNT` tick in DR-15 arrived as a preprocessor event or a dumb-mode
  transition, and §3.3 step 2 covers dumb mode without a listener.
- **svca / r10.** Not taken: their provisional fallback for in-progress hits lost to base (risks
  DR-08). §3.2's pending frames replace that fallback.
- **v3p and v3r** (risks Premises and Variants): fall back to `MODIFICATION_COUNT` for frames that
  saw another file's in-progress build. Not taken: each still fails §2.6's oracle (9 files) and
  loses V3's gain on gitsigns and telescope (risks DR-21).

## 10. Open Questions
_None. Deferred items are tracked in [risks-and-gaps.md](risks-and-gaps.md)._
