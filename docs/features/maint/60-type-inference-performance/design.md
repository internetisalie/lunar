---
id: "MAINT-60-DESIGN"
title: "Technical Design"
type: "design"
parent_id: "MAINT-60"
folders:
  - "[[features/maint/60-type-inference-performance/requirements|requirements]]"
---

# Technical Design: MAINT-60 — Type-Inference Performance

## 1. Architecture Overview

### Current State
Every claim here is measured; the runs are in [risks-and-gaps.md](risks-and-gaps.md).

- **Where the time goes.** A cold read builds the file's own snapshot and every snapshot its
  inference reaches that is not yet built. Of the files over 250 ms cold, some are slow in their own
  build and the rest in the files they reach (DR-06). In the own build, `LuaTypeGraph.checkTypes`
  checks every (value in `upSet`, use in `downSet`) pair of every variable over transitively closed
  sets; on fallo `init.lua` and luals `script/parser/compile.lua` its first fixed-point pass outlasts
  any deadline the sweep uses (DR-08).
- **Edits.** `LuaTypesSnapshot.dependenciesFor` (`LuaTypes.kt:354`) makes every project-file snapshot
  depend on `PsiModificationTracker.MODIFICATION_COUNT`, so any edit invalidates every snapshot and
  re-reading a file rebuilds its whole reach (DR-01). TYPE-11's source frames cannot narrow this for
  project files (DR-04).
- **Cancellation already works.** PSI access inside the pair loop polls for cancellation (DR-11), so
  this design adds no poll.
- **No targeted change reaches the targets** (DR-05, DR-06).

### Prior Art in This Repo
| Component | file:line | This design |
|---|---|---|
| MAINT-45 performance records (`LuaPerfRecordWorkloadsTest`, `Workload`, `WorkloadChecks`, `violations`, `rootCounts`) | `src/test/kotlin/net/internetisalie/lunar/perfrecords/LuaPerfRecordWorkloadsTest.kt:28`, `PerfRecordChecks.kt:11,37` | **extended**: the §2.3 workloads join `workloads()`, and an equal-passes check joins `PerfRecordChecks.kt` (§2.3) |
| MAINT-56 member setup (`CorpusManifest.load`/`entry`, `CorpusGuards.assertCorpusFetched`, `CorpusTarget.pin`/`memberTarget`/`restore`, `CorpusDefinitionLibraries.provision`/`release`, `CorpusSourcePath.snapshot`/`restore`) | `corpus/CorpusManifest.kt:53,66`, `CorpusGuards.kt:17`, `CorpusTarget.kt:35,49,51`, `CorpusDefinitionLibraries.kt:99,105`, `CorpusSourcePath.kt:11,14` | **reused** by the sweep (§2.1) exactly as MAINT-56 DR-16's probe used them |
| `fetch-corpus.py` | `tooling/corpus/fetch-corpus.py:101` | **extended** with `--only` (§2.4) |
| `LuaTypeGraph.checkTypes` and its cutoffs | `LuaTypeGraph.kt:297` | **unchanged** (DR-08, DR-11) |
| TYPE-11 pinning (`isPinnable`, `churnDependencyFor`) | `LuaTypes.kt:381,407` | **unchanged** (DR-04) |
| `LuaTypePairWriteDifferential` | `LuaTypeGraph.kt` (`checkNodePairs`, `:377`) | **unchanged** |

### Target State
No change to what the engine computes. One test-visible build meter (§2.2), a committed sweep with a
verdict (§2.1, §3), the inference workloads (§2.3), a fetcher option (§2.4), and one roadmap item per measured
cause (§3.3). The targets of MAINT-60-01/-06 become true as those items land; the verdict's
`release` line (§3.2) is what says so.

## 2. Core Components

### 2.1 `net.internetisalie.lunar.corpus.LuaSnapshotCostSweepTest` (new, test)
- **Responsibility**: measure cold and after-edit cost per type-corpus file; write
  `build/snapshot-cost/sweep.jsonl`, `ranked.tsv` and `verdict.txt` (§4.1); assert the verdict (§3.2).
- **Base**: `BasePlatformTestCase`, `@RunWith(JUnit4::class)`, `@FixMethodOrder(MethodSorters.NAME_ASCENDING)`,
  `getTestDataPath() = System.getProperty("user.dir")`.
- **Setup / teardown**: as MAINT-56 DR-16's probe: `CorpusDefinitionLibraries.requireSettingsSubscriber`,
  `VfsRootAccess.allowRootAccess` for `test/corpus` and `LuaDefinitionLibraryFetcher.defaultCacheRoot()`,
  `enabledDefinitionLibraries = mutableListOf()`, `CorpusSourcePath.snapshot`; teardown restores the
  source path, releases provisioned libraries, restores the target. `setUp` reads
  `RUN_START_MS` (a `private companion object` `val` = `System.currentTimeMillis()`), so the run clock
  starts with the first test method.
- **Test methods**: one per member of `tooling/corpus/type-corpus.json`, named `testM<NN><Member>`,
  `NN` from `01` in manifest order, except `testM00aLuals` and `testM00bFallo`, which run first so
  the slowest members cannot be cut by the budget; then `testZzVerdict`. A member with no method is
  caught by `coverage` (§3.2).
- **Key API** (private helpers, ≤3 args each):
  ```kotlin
  private fun sweepMember(name: String)                         // §3.1
  private fun readTimed(file: LuaFile, deadlineMs: Long): Read  // §3.1 step 4
  private fun appendNewline(file: LuaFile)                      // WriteCommandAction + commitDocument
  private fun budgetLeft(): Boolean                             // now − RUN_START_MS < RUN_BUDGET_MS
  @Test fun testZzVerdict()                                     // §3.2
  ```
  `Read(ms: Long, status: String, errorDigest: Int, builds: List<LuaSnapshotBuildMeter.BuildRecord>)`.
- **Threading**: test thread (EDT in a light fixture); reads under
  `ProgressManager.getInstance().runProcess(Computable { … }, indicator)`.
- **Name**: carries no `Corpus`, so `SweepNamingGuardTest` needs no change; it is kept out of the
  routine suite by its own filter (§7).

### 2.2 `net.internetisalie.lunar.lang.psi.types.LuaSnapshotBuildMeter` (new, main)
- **Responsibility**: attribute a read's time to each snapshot build it triggers, so the sweep can
  tell a file's own cost from its reach. Off unless a test installs a sink.
- **Key API**:
  ```kotlin
  internal object LuaSnapshotBuildMeter {
      data class BuildRecord(val path: String, val depth: Int, val inclusiveMs: Long, val selfMs: Long)
      @Volatile var sink: ((BuildRecord) -> Unit)? = null
      fun <T> measure(file: PsiFile, build: () -> T): T
  }
  ```
- **Algorithm**: `measure` returns `build()` directly when `sink == null`. Otherwise it pushes a
  frame `(path = file.virtualFile?.path ?: file.name, startNs, childNs = 0L)` on a `ThreadLocal`
  `ArrayDeque`, runs `build()` in `try`, and in `finally` pops the frame, computes
  `inclusiveNs = System.nanoTime() − startNs`, adds `inclusiveNs` to the parent frame's `childNs` if
  there is one, and calls `sink` with
  `BuildRecord(path, depth = remaining stack size, inclusiveNs / 1_000_000L, (inclusiveNs − childNs) / 1_000_000L)`
  (integer division, so both are `Long`).
- **Wiring**: `LuaTypesVisitor.buildSnapshot(file)` (companion, `LuaTypesVisitor.kt:1668`) wraps its
  whole body in `LuaSnapshotBuildMeter.measure(file) { … }`.
- **Contract**: holds a path string, never the `PsiFile`.

### 2.3 Inference workloads (edit, test: `LuaPerfRecordWorkloadsTest.kt`, `PerfRecordChecks.kt`)
- Add to `workloads()`: `inferenceWorkload("infer-luals-compiler", "luals", "script/vm/compiler.lua")`,
  `inferenceWorkload("infer-ldoc-markdown", "ldoc", "ldoc/markdown.lua")` (p99-cost file, DR-06) and
  `inferenceWorkload("infer-beequeue-raisedelayed", "beequeue", "lib/lua/raiseDelayedJobs.lua")`
  (median-cost file, DR-06).
- `inferenceWorkload(name, member, path)`: reads the text from `test/corpus/<member>/<path>` after
  `CorpusGuards.assertCorpusFetched(repoRoot, CorpusManifest.entry(repoRoot, member, CorpusManifest.TYPE_MANIFEST_PATH))`;
  `WorkloadSpec(name, "inference", text)`;
  `WorkloadChecks(setOf("write", "read", "demand"), emptyMap(), emptySet(), equalPasses = true)` (no
  positive-output check: beequeue's file has no errors, DR-10). The run's whole body is one
  `EdtTestUtil.runInEdtAndGet<Measured, RuntimeException> { … }` block, as `snapshotWorkload`'s is,
  because `CorpusTarget` runs on the test's EDT (`CorpusTarget.kt:27`). Inside it:
  `val saved = CorpusTarget.pin(project, CorpusTarget.memberTarget(entry))`, then in `try`: call
  `configureByText(text)`, read `val types = LuaTypesSnapshot.forFile(myFixture.file) as LuaTypesSnapshot`
  (`forFile` returns the `LuaTypes` interface; `rootCounts` takes the class, as `snapshotWorkload`
  does), return `Measured(rootCounts(types), mapOf("errors" to types.getErrors().size.toLong()))`; in
  `finally`: `CorpusTarget.restore(project, saved)`, still inside the block. The text alone is the fixture: no source path, so no other project
  file is reached. There is no prime: under the records harness the warm pass builds the library
  snapshots the file reaches, so the recorded pass starts from the same state (DR-12).
- **Equal passes** (MAINT-60-04): `WorkloadChecks` gains a last field
  `val equalPasses: Boolean = false`, so every existing call site is unchanged. `PerfRecordChecks.kt`
  gains
  ```kotlin
  internal val ROOT_COUNT_KEYS = listOf("demand", "read", "write")
  internal fun passDisagreements(warm: Pass, recorded: Pass): List<String>
  ```
  over `ROOT_COUNT_KEYS` in that order, a key absent from a pass's `rawCounts` read as `0`: one
  `"counts differ across passes: <key> <warm> -> <recorded>"` per key whose values differ. These are
  the root counts `rootCounts` writes, the ones MAINT-60-04 names; the `stub:*` keys `measurePass`
  adds from `StubLookupCounter.drain()`, and any other key, are not compared.
  `recordEveryWorkload` adds, for every workload `i` with `checks.equalPasses`,
  `passDisagreements(warm[i], recorded[i]).map { "${spec.name}: $it" }` to `failures` before its
  `assertTrue`, so a disagreement fails the test.
- **Not governed by `tooling/perf/gate-mode`** (a decision): equal root counts across passes is a
  correctness check on the workload, which must measure the file's inference and nothing left over
  from earlier work, not a performance wobble. So a disagreement fails `recordEveryWorkload` in every
  gate mode: no record is written, `perf_ctrf.py` does not run (so no `WOBBLE` line is printed), and
  the CI step's `else` branch records the failure, which turns the job red in its final step
  (`.gitea/workflows/build-plugin.yml`, Performance records step).

### 2.4 `tooling/corpus/fetch-corpus.py --only` (edit)
- `parser.add_argument("--only", default="", help="comma-separated member names to fetch (default: all)")`.
- After loading the manifest: `wanted = [n for n in args.only.split(",") if n]`; if `wanted` names a
  member absent from `manifest["corpora"]`, `die(f"--only: unknown member(s): {', '.join(missing)}")`;
  otherwise replace the manifest by `{**manifest, "corpora": [e for e in manifest["corpora"] if not wanted or e["name"] in wanted]}`
  before both the fetch loop and `fetch_definition_archives`.
- Test: `tooling/corpus/test_fetch_corpus_only.py` (`unittest`, imports the module via
  `importlib.util.spec_from_file_location`), patches `fetch_one` and `fetch_definition_archives` and
  asserts the names they receive: `--only ldoc,beequeue` → exactly those two; no `--only` → every
  member; `--only nosuch` → `SystemExit` naming `nosuch`.

## 3. Algorithms

### 3.1 The per-member sweep (`sweepMember`)
1. `entry = CorpusManifest.entry(repoRoot, name, CorpusManifest.TYPE_MANIFEST_PATH)`;
   `selected = SnapshotCostVerdict.selectedFiles(repoRoot, entry)`: for each of `entry.roots` in
   order, every regular file with extension `lua` under `test/corpus/<name>/<root>`, as
   `"<root>/<path relative to that root>"` with `/` separators; the whole list sorted. If
   `!budgetLeft()`, emit one row per selected file with `coldStatus = editStatus = "skipped"` and return.
2. `CorpusGuards.assertCorpusFetched`; pin `CorpusTarget.memberTarget(entry)`; provision definition
   libraries; when `entry.moduleRoot` is set, source path = `"$base/?.lua;$base/?/init.lua"` with
   `base` = the checkout's `moduleRoot`.
3. Copy each of `entry.roots` with `myFixture.copyDirectoryToProject("test/corpus/<name>/<root>", root)`;
   a selected path's `LuaFile` is `PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir(path))`.
4. **Cold pass**, files in `selected` order. For each file, the first rule that applies:
   - `!budgetLeft()`: `coldStatus = editStatus = "skipped"`, no read.
   - an earlier file of this member ended cold `timeout`: `coldStatus = editStatus = "unmeasured"`,
     no read, so a build cancelled at the deadline cannot cost each later read of the member up to
     the deadline again.
   - otherwise record `prebuilt` (the file's `LuaTypesVisitor.KEY` cached value
     `hasUpToDateValue()`), then `readTimed(file, COLD_DEADLINE_MS)`: reads
     `LuaTypesSnapshot.forFile(file).getErrors()` under an `EmptyProgressIndicator` cancelled by
     `AppExecutorUtil.getAppScheduledExecutorService().schedule` at the deadline, with
     `LuaSnapshotBuildMeter.sink` collecting the read's `BuildRecord`s. Status `ok`, `timeout` on
     `ProcessCanceledException`, or `exception:<SimpleName>`. `errorDigest` = `hashCode()` of the
     sorted `"${textOffset}:${severity}:${message}"` strings.
5. **Edit pass**, same order, after the cold pass, for each file whose `coldStatus` is `ok`:
   `!budgetLeft()` → `editStatus = "skipped"`; else `appendNewline(file)` and
   `readTimed(file, EDIT_DEADLINE_MS)`. A file whose `coldStatus` is `timeout` or `exception:*` gets
   `editStatus = "not-run"`.
6. Emit one JSON line per selected file (§4.1). From a read's records: `rootSelfMs` = `selfMs` of the
   `depth == 0` record, `-1` when there is none (the file was already built); `topDep` / `topDepSelfMs`
   = `path` / `selfMs` of the `depth >= 1` record with the largest `selfMs` (the first on a tie), `""` /
   `-1` when there is none. A cancelled build still yields its record, because the meter reports in
   `finally`. Fields of a pass that was not read: `ms = 0`, `rootSelfMs = topDepSelfMs = -1`,
   `topDep = ""`, builds `0`.

### 3.2 The verdict (`testZzVerdict`, logic in `SnapshotCostVerdict`)
`net.internetisalie.lunar.corpus.SnapshotCostVerdict` (test sources, `object`):
```kotlin
const val BOUND_MS = 250L
const val STALE_BELOW_MS = 200L
const val P99_MS = 100L
const val RUN_LIMIT_MS = 45 * 60_000L
fun violations(rows: List<SweepRow>, exceptions: List<CostException>, roadmapIds: Set<String>): List<String>
fun staleExceptions(rows: List<SweepRow>, exceptions: List<CostException>): List<String>
fun coverage(selected: Map<String, List<String>>, rows: List<SweepRow>): List<String>
fun runTimeFailures(elapsedMs: Long): List<String>
fun rankedLines(rows: List<SweepRow>, exceptions: List<CostException>): List<String>
fun releaseLines(rows: List<SweepRow>, exceptions: List<CostException>, checkFailures: Int): List<String>
```
A row has two passes, `cold` (bound `cold-250`: `coldMs`, `coldStatus`) and `edit` (bound
`edit-250`: `editMs`, `editStatus`). A pass's **value** is `"<ms> ms"` when its status is `ok`, else
the status. A pass **misses** its bound when its status is not `ok` or its ms is `>= BOUND_MS`; it
**clears** its bound when its status is `ok` and its ms is `< STALE_BELOW_MS`. A key is
`(member, file, bound)`.

`violations`, in this order:
1. Each pass whose status starts with `exception:`: `"<m>/<f> <bound> <status>: sweep error"`, whether
   or not an exception lists it.
2. Each other pass that misses its bound and whose key is not in `exceptions`:
   `"<m>/<f> <bound> <value>: no exception"`.
3. Each exception whose `(member, file)` has no row: `"<m>/<f>: exception for an unselected file"`.
4. Each exception whose `item` is not in `roadmapIds`: `"<item>: no roadmap row"`.
5. Each exception whose row exists, whose pass misses its bound and whose `item` differs from
   `attribute(row, bound, rows).item` (§3.3), skipping a computed item of `""` (a sweep error, step 1):
   `"<m>/<f> <bound>: misfiled under <item>, attributed to <computed>"`.

`staleExceptions`: each pass whose key is in `exceptions` and that clears its bound:
`"<m>/<f> <bound> <value>: stale exception: remove"`. A listed pass at `STALE_BELOW_MS` ≤ ms <
`BOUND_MS` is not stale: this band is the hysteresis that keeps a listed pass from going stale on a
small gain. Phase 3 lists only passes that miss (§4.1), so an unlisted pass in the band is not a
violation and needs no listing.

`coverage(selected, rows)`, where `selected` maps every member of `CorpusManifest.load(repoRoot,
TYPE_MANIFEST_PATH)` to its `selectedFiles`: `"<m>: no selected files"` for a member with an empty
list; `"<m>/<f>: no row"` for each selected file with no row; `"<m>/<f>: row for an unselected file"`
for each row not selected; `"<m>/<f>: duplicate row"` for each row whose `(member, file)` an earlier
row already has.

`runTimeFailures(elapsedMs)`: `["sweep took <elapsedMs / 60_000> min"]` when
`elapsedMs >= RUN_LIMIT_MS`, else `[]`. The elapsed time is an argument, so the check is unit-tested
without a clock (T1, T2).

`releaseLines(rows, exceptions, checkFailures)`, in this order, each of the first four lines ending
`PASS` or `FAIL`:
- `"p99 cold <v>: <verdict>"` and `"p99 edit <v>: <verdict>"`: the nearest-rank 99th percentile over
  **all** rows, excepted or not: sort the pass's ms ascending, a pass whose status is not `ok` as
  `Long.MAX_VALUE`, take index `ceil(0.99 × n) − 1`; `<v>` is `"<ms> ms"`, or `unmeasured` for
  `Long.MAX_VALUE` or when `n == 0` (no rows: no index is taken). `PASS` when the value is
  `< P99_MS`.
- `"misses <n>: <verdict>"`: `n` = the passes of `rows` that miss their bound, listed or not; `PASS`
  when `n == 0`.
- `"checks <checkFailures>: <verdict>"`, `PASS` when `0`.
- `"remove before tagging: <m>/<f> <bound>"` for each exception, in `exceptions` order, whose row
  exists and whose pass does not miss. These lines carry no verdict: such a listing does not block
  `release READY`; the supervisor deletes those rows in the release commit.
- last, `"release READY"` when each of the first four lines ends `PASS`, else `"release HOLD"`.

`testZzVerdict`: parses `sweep.jsonl` (§4.1), `exceptions.tsv` (§4.2) and `docs/roadmap.md` (§4.3);
computes `checks = coverage(...) + violations(...) + runTimeFailures(System.currentTimeMillis() − RUN_START_MS)`
and `stale = staleExceptions(rows, exceptions)`; writes `ranked.tsv` = `rankedLines(rows, exceptions)`
and `verdict.txt` = `releaseLines(rows, exceptions, checks.size)`, each joined by `\n`; prints
`checks`, `stale` and the release lines; then asserts `checks + stale` is empty. So a stale listing
fails the test but not `release`, and a run of 45 minutes or more fails both. The release lines are
written, not asserted: `release HOLD` is the expected state until the cause items land, and
MAINT-60-07 reads it.

### 3.3 Cause of a missed bound (`attribute`)
```kotlin
data class Attribution(val cause: String, val item: String, val dependency: String, val dependencyMs: Long)
fun attribute(row: SweepRow, bound: String, rows: List<SweepRow>): Attribution  // only for a pass that misses
const val OWN_BUILD_ITEM = "MAINT-61"; const val REACH_ITEM = "MAINT-62"; const val EDIT_CASCADE_ITEM = "MAINT-63"
val OWN_BUILD_ITEMS = mapOf("/lua/fallo/init.lua" to "BUG-96", "/script/parser/compile.lua" to "BUG-97")
```
`ownItem(path)` = the value of the first `OWN_BUILD_ITEMS` key that `path` ends with, else
`OWN_BUILD_ITEM`. `attribute` files only passes that miss their bound: its callers are `violations` step 5 and
`rankedLines`, and both call it for a missing pass only. For the row's pass of `bound` (status `s`, root self `r`, top dependency `d` with
self `ds`), the first rule that applies:
1. `s` is `skipped`, `not-run` or `unmeasured`: cause **unmeasured**, item = the item of the row that
   blocks measuring it, `dependency = ""`, `dependencyMs = -1`:
   - `not-run`: `attribute(row, "cold-250", rows).item`;
   - `unmeasured`: `attribute(stopper, "cold-250", rows).item`, `stopper` = the first row of the same
     member, in `rows` order, whose `coldStatus` is `timeout`;
   - `skipped`: `attribute(top, "cold-250", rows).item`, `top` = the row with the largest `coldMs`
     among rows whose `coldStatus` is `ok` or `timeout` (the first on a tie).
2. `s` starts with `exception:`: cause **sweep error**, item `""`, `dependency = ""`,
   `dependencyMs = -1` (a §3.2 step 1 violation, never excepted).
3. `r >= BOUND_MS`: cause **own build**, item `ownItem("/<member>/<file>")`, dependency `""`, `-1`.
4. Otherwise cause **reach** for `cold-250`, **edit cascade** for `edit-250`, with `dependency = d`,
   `dependencyMs = ds`; item = `ownItem(d)` when `ds >= BOUND_MS` (the dependency's own build
   dominates, so the row is filed under that build's item), else `REACH_ITEM` for `cold-250` and
   `EDIT_CASCADE_ITEM` for `edit-250`. A row with `r == -1` takes this rule.

The recursion ends: a blocker's cold pass is `ok` or `timeout`, which rule 1 never takes. A cancelled
read takes rules 3–4 like any other, its records carrying the time up to the cancel (§2.2); so luals
`compiler.lua`, cancelled with little time of its own while building `compile.lua` (DR-06, DR-08),
is reach under BUG-97, and fallo `init.lua`'s cold pass is own build and its `not-run` edit pass is
unmeasured, both under BUG-96.

Items, one per cause; Phase 3 sets `OWN_BUILD_ITEM`, `REACH_ITEM` and `EDIT_CASCADE_ITEM` to the minted IDs:

| Item | Files it holds | Measured in |
|---|---|---|
| [[BUG-96]] (re-scoped) | fallo `init.lua`, and every row whose dominant dependency it is | DR-08 |
| [[BUG-97]] (re-scoped) | every row whose dominant dependency is luals `script/parser/compile.lua` | DR-01, DR-08 |
| MAINT-61 (new) | own build of every other file, and every row whose dominant dependency is one of those | DR-06, DR-08 |
| MAINT-62 (new) | reach with no dominant dependency at or over 250 ms | DR-06 |
| MAINT-63 (new) | edit cascade with no dominant dependency at or over 250 ms | DR-04, DR-09 |
| MAINT-64 (new) | none: the measured walk flattenings, which change findings | DR-06 |

The MAINT IDs are placeholders for the next free `MAINT-` numbers at commit time.

### 3.4 Budgets
`COLD_DEADLINE_MS = 60_000`, `EDIT_DEADLINE_MS = 1_000`, `RUN_BUDGET_MS = 35 × 60_000`. A read over its
deadline misses its bound either way, so a deadline only caps what an over-target file costs the run.

`budgetLeft()` is checked before each member's setup and before each read, so the run passes
`RUN_BUDGET_MS` by at most one read or one member's setup. Bound on the run (first test method to
`testZzVerdict`'s end):
- **Expected**, from `base` runs whose reads match this design's (a member stops after its first cold
  timeout, as the probes did): cold reads 4.18 min (DR-06), after-edit reads capped at 1 s 8.0 min
  (DR-09), everything else at most 7.0 min (DR-09's run less its summed reads). About 19 min.
- **Worst case**: 35 min + one cold deadline (1 min) + one member's setup (≤ 7 min, the whole run's
  non-read time in DR-09) + the verdict, which reads `sweep.jsonl`, `exceptions.tsv` and the roadmap. Under 45 min if the
  verdict takes under 2 min. Phase 1's run confirms it: `testZzVerdict` asserts the 45 minutes (§3.2).

## 4. External Data & Parsing

### 4.1 Produced under `build/snapshot-cost/`
`sweep.jsonl`: one JSON object per line, built as a string as MAINT-56's probe did:
`{"member":s,"file":s,"lines":n,"prebuilt":b,"coldMs":n,"coldStatus":s,"rootSelfMs":n,"topDep":s,"topDepSelfMs":n,"builds":n,"editMs":n,"editStatus":s,"editRootSelfMs":n,"editTopDep":s,"editTopDepSelfMs":n,"editBuilds":n,"digest":n}`,
strings escaped for `"` and `\`. `file` is the selected path (§3.1 step 1). `SweepRow` is the data
class with these fields in this order; the verdict parses each line with
`com.google.gson.Gson().fromJson(line, SweepRow::class.java)` (Gson is on the test classpath via the
platform).

`ranked.tsv` = `rankedLines(rows, exceptions)`: first the header
`member\tfile\tbound\tvalue\tcause\titem\tdependency\tdependencyMs\texcepted`, then one line per pass
that misses its bound or is `ok` at `>= STALE_BELOW_MS`; `excepted` = `yes`/`no` by key; sorted by ms
descending, a non-`ok` pass first (as `Long.MAX_VALUE`), then by member, file, bound. A missing pass
takes `cause`/`item`/`dependency`/`dependencyMs` from `attribute`. An in-band pass (`ok`,
`STALE_BELOW_MS` ≤ ms < `BOUND_MS`) is there for information only: `cause` = `in band`, `item` and
`dependency` empty, `dependencyMs` = `-1`; it is never listed. Phase 3 commits the lines whose cause
is not `in band`.

`verdict.txt`: `releaseLines`, one per line (§3.2).

### 4.2 `src/test/resources/snapshot-cost/exceptions.tsv` (committed)
Header `member\tfile\tbound\titem\tms`, then one row per key; `bound` ∈ `cold-250|edit-250`; `ms` the
value when listed (documentation only, never compared). `parseExceptions(text): List<CostException>`
with `CostException(member, file, bound, item)`: skip the header line and blank lines, split on `\t`;
fail naming the 1-based line number when a line has other than five fields, a `bound` outside the two,
or a key already seen. Empty (header only) until Phase 3.

### 4.3 `docs/roadmap.md` item IDs
`parseRoadmapIds(text): Set<String>`: a line matching `^\|\s*ID\s*\|\s*Title\s*\|\s*Status\s*\|` opens
a wave table; the table runs until the first line not starting with `|`. Inside a wave table, each
line matching `^\|\s*([A-Z]+-\d+)\s*\|` contributes group 1. Lines outside wave tables contribute
nothing. A closed item's row is deleted from the roadmap, so its exceptions fail step 4 until removed.

## 5. Data Flow

### Example 1: a read that reaches other files
`readTimed(lua/gitsigns.lua)` → `forFile` → `buildSnapshot` (meter depth 0) → its visitor resolves
`require` → `getModuleType` → `forFile` → nested builds (depth 1…). The sink gets one record per build;
the depth-0 record gives `rootSelfMs`, the largest depth-≥1 record gives `topDep`. Measured shape:
DR-06's gitsigns row in risks-and-gaps, cause **reach**.

### Example 2: an edit
`appendNewline(file)` bumps `MODIFICATION_COUNT`; every non-pinned snapshot is stale; the edit read
rebuilds the file and everything it reaches (DR-09).

## 6. Edge Cases
- A member whose cold read times out: its later files are `unmeasured` (§3.1 step 4), the timed-out
  file's edit pass `not-run`; all are filed under the timed-out read's item (§3.3 rule 1).
- Files already built when read cold (`prebuilt`) read in about 0 ms; their findings depend on which
  read built them (DR-07), so digests are compared over non-`prebuilt` rows only (implementation-plan Phase 1's
  digest comparison).
- The 5 s cutoff fires only after a pass ends (DR-08); the workloads' builds stay under it with margin
  (DR-10, Risk 1.2).

## 7. Integration Points
No `plugin.xml` change. `build.gradle.kts`, inside `tasks.test`, beside the MAINT-56 block:
```kotlin
// MAINT-60: per-file snapshot cost over the type corpus. Its own opt-in (BUG-418):
// `./gradlew test -PwithSnapshotSweep --tests '*SnapshotCostSweep*'`.
if (!project.hasProperty("withSnapshotSweep")) {
    filter {
        excludeTestsMatching("*SnapshotCostSweep*")
        isFailOnNoMatchingTests = false
    }
} else {
    val snapshotCostDir = layout.buildDirectory.dir("snapshot-cost").get().asFile
    doFirst { snapshotCostDir.deleteRecursively() }
}
```
`.gitea/workflows/build-plugin.yml`, `build` job, the fixtures step (line 203): insert
`&& python3 tooling/corpus/fetch-corpus.py --manifest tooling/corpus/type-corpus.json --only luals,ldoc,beequeue`
immediately before `&& echo "fixtures ok"`. `.github/workflows/build-plugin.yml` has no
performance-records step, so it needs no fetch.

## 8. Requirement Coverage

| Requirement | Priority | Implemented by | Test | Mutation that turns it red |
|---|---|---|---|---|
| MAINT-60-01 | M | §2.1, §3.2 | TC 1: V1, V2, P1, P2, P3, P10 | per case below |
| MAINT-60-02 | M | §3.2, §3.3, §4.1, Phase 3 | TC 3: V3–V7, V9, V11–V13, A1–A7, E1–E4, R1, K1; the Phase 3 sweep run | per case below |
| MAINT-60-03 | M | Phase 1's exit check | TC 4: audit + conformance + sweep digests before/after | none of its own: an artifact comparison, red exactly when the files differ (risks-and-gaps "Test Case Gaps") |
| MAINT-60-04 | M | §2.3, §2.4, §7 | TC 5: D1–D3; the workloads run | D1–D3 below; in the workloads run, Phase 2's temporary count perturbation in `recordEveryWorkload` (the prime that was expected to matter is not needed, DR-12) |
| MAINT-60-05 | M | §2.1, §3.1, §3.4, §4.1 | TC 6: C1–C4, V10, T1, T2; the Phase 1 sweep run | per case below; in the run, set `RUN_BUDGET_MS` above 45 min |
| MAINT-60-06 | M | §2.1, §3.2 | TC 2: V8, P4 | per case below |
| MAINT-60-07 | M | §3.2 `releaseLines`, `runTimeFailures` | TC 7: P5–P10, T1 | per case below |

**`SnapshotCostVerdictTest`** (`net.internetisalie.lunar.corpus`, JUnit 5, no fixture). `row(cold,
edit, coldStatus = "ok", editStatus = "ok", file = "f.lua")` builds a `SweepRow` for member `m` with
`rootSelfMs = editRootSelfMs = -1`, `topDep = editTopDep = ""`, other numbers 0, so `attribute` files
its missing cold pass under `REACH_ITEM` and its missing edit pass under `EDIT_CASCADE_ITEM` (§3.3
rule 4); `ids` = `setOf(OWN_BUILD_ITEM, REACH_ITEM, EDIT_CASCADE_ITEM)`; `ex(bound, file = "f.lua")` =
`CostException("m", file, bound, if (bound == "cold-250") REACH_ITEM else EDIT_CASCADE_ITEM)`. Expected
strings that contain an item are written with the constant interpolated (`"…$REACH_ITEM"`).
`violations(rows, exceptions, ids)` and, where the column says so, `staleExceptions(rows, exceptions)`:

| Case | rows | exceptions | expected | mutation |
|---|---|---|---|---|
| V1 | `row(300, 10)` | none | `["m/f.lua cold-250 300 ms: no exception"]` | drop the `ms >= BOUND_MS` arm of *misses* |
| V2 | `row(300, 10)` | `ex("cold-250")` | `[]` | ignore `exceptions` in step 2 |
| V3 | `row(300, 10)` | `CostException("m", "f.lua", "cold-250", "MAINT-99")` | `["MAINT-99: no roadmap row", "m/f.lua cold-250: misfiled under MAINT-99, attributed to $REACH_ITEM"]` | drop step 4 |
| V4 | `row(300, 10)` | `ex("edit-250")` | `violations` = `["m/f.lua cold-250 300 ms: no exception"]`; `staleExceptions` = `["m/f.lua edit-250 10 ms: stale exception: remove"]` | match keys without `bound` |
| V5 | `row(150, 10)` | `ex("cold-250")` | `violations` = `[]`; `staleExceptions` = `["m/f.lua cold-250 150 ms: stale exception: remove"]` | `staleExceptions` returns `emptyList()` |
| V6 | `row(220, 10)` | `ex("cold-250")` | `violations` = `[]`; `staleExceptions` = `[]` | *clears* at `ms < BOUND_MS` instead of `STALE_BELOW_MS` |
| V7 | `row(240, 10)` | none | `[]` | *misses* at `ms >= STALE_BELOW_MS` |
| V8 | `row(10, 300)` | none | `["m/f.lua edit-250 300 ms: no exception"]` | read the edit pass's ms from `coldMs` |
| V9 | `row(10, 0, editStatus = "not-run")` | none | `["m/f.lua edit-250 not-run: no exception"]` | treat `not-run` as `ok` |
| V10 | `row(0, 0, "skipped", "skipped")` | none | `["m/f.lua cold-250 skipped: no exception", "m/f.lua edit-250 skipped: no exception"]` | treat `skipped` as `ok` |
| V11 | `row(0, 0, "exception:IllegalStateException", "not-run")` | `ex("cold-250")`, `ex("edit-250")` | `["m/f.lua cold-250 exception:IllegalStateException: sweep error"]` | let an exception cover step 1 |
| V12 | `row(10, 10)` | `ex("cold-250", "g.lua")` | `["m/g.lua: exception for an unselected file"]` | drop step 3 |
| V13 | `row(300, 10)` | `CostException("m", "f.lua", "cold-250", OWN_BUILD_ITEM)` | `["m/f.lua cold-250: misfiled under $OWN_BUILD_ITEM, attributed to $REACH_ITEM"]` | drop step 5 |

`releaseLines(rows, exceptions, checkFailures)`, `checkFailures = 0` unless the case says otherwise;
`tens(k)` = `k` rows `row(10, 10, file = "f<i>.lua")` for `i` in `0 until k`:

| Case | rows | exceptions | expected | mutation |
|---|---|---|---|---|
| P1 | `tens(99)` + `row(500, 10)` | none | contains `"p99 cold 10 ms: PASS"` | index `ceil(0.99 n)` without `− 1` |
| P2 | `tens(98)` + `row(500, 10, file = "x.lua")` + `row(500, 10, file = "y.lua")` | `ex("cold-250", "x.lua")`, `ex("cold-250", "y.lua")` | contains `"p99 cold 500 ms: FAIL"` | compute p99 over unexcepted rows only |
| P3 | `tens(98)` + `row(0, 10, "timeout", file = "x.lua")` + `row(0, 10, "timeout", file = "y.lua")` | none | contains `"p99 cold unmeasured: FAIL"` | use a non-`ok` pass's ms |
| P4 | `tens(98)` + `row(10, 500, file = "x.lua")` + `row(10, 500, file = "y.lua")` | none | contains `"p99 cold 10 ms: PASS"` and `"p99 edit 500 ms: FAIL"` | compute the edit line from `coldMs` |
| P5 | `tens(100)` | none | last line `"release READY"` | always `HOLD` |
| P6 | `tens(100)` | `ex("cold-250", "f1.lua")` | contains `"remove before tagging: m/f1.lua cold-250"`, last line `"release READY"` | count every listing as a miss (gives `HOLD`); or drop the `remove before tagging` lines |
| P7 | `tens(98)` + `row(150, 10, file = "x.lua")` + `row(150, 10, file = "y.lua")` | none | contains `"p99 cold 150 ms: FAIL"` and `"misses 0: PASS"`, last line `"release HOLD"` | leave the p99 lines out of `release` |
| P8 | `tens(100)`, `checkFailures = 1` | none | contains `"checks 1: FAIL"`, last line `"release HOLD"` | leave the checks line out of `release` |
| P9 | `tens(99)` + `row(300, 10, file = "x.lua")` | `ex("cold-250", "x.lua")` | contains `"p99 cold 10 ms: PASS"` and `"misses 1: FAIL"`, last line `"release HOLD"` | count only unlisted passes as misses |
| P10 | no rows | none | contains `"p99 cold unmeasured: FAIL"` and `"p99 edit unmeasured: FAIL"`, last line `"release HOLD"` | drop the `n == 0` guard (index `−1` throws) |

`runTimeFailures(elapsedMs)`:

| Case | elapsedMs | expected | mutation |
|---|---|---|---|
| T1 | `RUN_LIMIT_MS` | `["sweep took 45 min"]` | compare with `>` instead of `>=` |
| T2 | `RUN_LIMIT_MS − 1` | `[]` | compare against `RUN_BUDGET_MS` instead of `RUN_LIMIT_MS` |

`coverage(selected, rows)`:

| Case | selected | rows | expected | mutation |
|---|---|---|---|---|
| C1 | `m → [a.lua, b.lua]` | `m/a.lua` | `["m/b.lua: no row"]` | check only rows ⊆ selected |
| C2 | `m → [a.lua]` | `m/a.lua`, `m/z.lua` | `["m/z.lua: row for an unselected file"]` | check only selected ⊆ rows |
| C3 | `m → [a.lua]`, `n → []` | `m/a.lua` | `["n: no selected files"]` | iterate only members that have rows |
| C4 | `m → [a.lua]` | `m/a.lua`, `m/a.lua` | `["m/a.lua: duplicate row"]` | drop the duplicate check |

`attribute(row, bound, rows)`; rows here set `rootSelfMs`/`topDep`/`topDepSelfMs` as stated:

| Case | input | expected `(cause, item)` | mutation |
|---|---|---|---|
| A1 | `fallo/lua/fallo/init.lua`, cold `timeout`, `rootSelfMs = 59 982`; bound `cold-250` | `(own build, BUG-96)` | drop `OWN_BUILD_ITEMS` |
| A2 | `luals/script/vm/compiler.lua`, cold `timeout`, `rootSelfMs = 205`, `topDep = "/x/test/corpus/luals/script/parser/compile.lua"`, `topDepSelfMs = 45 000`; `cold-250` | `(reach, BUG-97)` | file reach under `REACH_ITEM` regardless of `ds` |
| A3 | A1's row with `editStatus = "not-run"`; `edit-250` | `(unmeasured, BUG-96)` | file `not-run` under `EDIT_CASCADE_ITEM` |
| A4 | rows A1, A2, then `luals/script/vm/a.lua` with cold `unmeasured`; `a.lua`, `cold-250` | `(unmeasured, BUG-97)` | take the first `timeout` row of any member as the stopper (gives fallo's, `BUG-96`) |
| A5 | `m/f.lua`, edit `ok` 900 ms, `editRootSelfMs = 20`, `editTopDepSelfMs = 120`; `edit-250` | `(edit cascade, EDIT_CASCADE_ITEM)` | read `topDepSelfMs` (cold) for the edit pass, with cold `topDepSelfMs = 400` in the fixture |
| A6 | rows, in order: `m/x.lua` cold `ok` 900 ms (`rootSelfMs = -1`); A1's row (`coldMs = 60 047`); `m/z.lua` cold `skipped`; `z.lua`, `cold-250` | `(unmeasured, BUG-96)` | take the first `ok`/`timeout` row as `top` (gives `REACH_ITEM`); or consider `ok` rows only |
| A7 | `m/f.lua`, cold `exception:IllegalStateException`; `cold-250` | `(sweep error, "")` | drop rule 2 (gives `(reach, REACH_ITEM)`) |

Parsers:

| Case | input | expected | mutation |
|---|---|---|---|
| E1 | header + `m\tf.lua\tcold-250\tMAINT-61\t300` | `[CostException("m", "f.lua", "cold-250", "MAINT-61")]` | skip no header |
| E2 | header + `m\tf.lua\tcold-250\tMAINT-61` | fails, message contains `line 2` | accept four or more fields |
| E3 | header + the E1 line twice | fails, message contains `line 3` | drop the duplicate check |
| E4 | header + `m\tf.lua\tcold-300\tMAINT-61\t300` | fails, message contains `line 2` | drop the `bound` check |
| R1 | `"| Branch | Tip |\n| BUG-1 | x |\n\n| ID | Title | Status |\n| :--- | :--- | :--- |\n| MAINT-61 | t | todo |\n\ntext\n| BUG-2 | y |"` | `setOf("MAINT-61")` | match the row regex on every line |

`rankedLines(rows, exceptions)`:

| Case | rows | exceptions | expected | mutation |
|---|---|---|---|---|
| K1 | `row(220, 10)`, `row(150, 10, file = "g.lua")` | none | `[header, "m\tf.lua\tcold-250\t220 ms\tin band\t\t\t-1\tno"]` | rank only passes that miss (drops the line); or rank every `ok` pass (adds `g.lua`) |

**`PerfRecordChecksTest`** gains: D1 `passDisagreements(Pass(mapOf("write" to 2L), emptyMap(), 0), Pass(mapOf("write" to 3L), emptyMap(), 0))`
= `["counts differ across passes: write 2 -> 3"]` (mutation: return `emptyList()`); D2 the same with
`"read" to 1L` added to the warm pass only = `["counts differ across passes: read 1 -> 0",
"counts differ across passes: write 2 -> 3"]` (mutation: skip a key absent from either pass); D3
`passDisagreements(Pass(mapOf("write" to 2L, "stub:lunar.global.decl" to 5L), emptyMap(), 0), Pass(mapOf("write" to 2L, "stub:lunar.global.decl" to 7L), emptyMap(), 0))`
= `[]` (mutation: compare the union of both passes' keys).

## 9. Alternatives Considered
All built and measured (risks-and-gaps DR-01, DR-02, DR-04, DR-06, DR-08, DR-11); none ships here.
- **Delta pair iteration** and the **flat declared-demand walk**: exact, too small a gain (DR-06);
  recorded for MAINT-61.
- **Flat write / flat read walks, identity compatibility memo**: fastest, but change findings (DR-06)
  → MAINT-64.
- **Cached `Table.hashCode`, hoisted `read`/`declaredDemand` per use**: within noise (DR-01).
- **TYPE-11 frames as edit dependencies**: none qualify after an edit (DR-04).
- **A `ProgressManager.checkCanceled()` per use node in `checkNodePairs`**: not needed (DR-11).
- **A deterministic pair budget in place of the 5 s cutoff**: pairs do not order builds by time
  (DR-08), so no budget preserves today's results.
- **Reading a member's remaining files after a cold timeout**: whether a later read rebuilds the
  cancelled build is not measured, and if it does, each such read costs up to the deadline; stopping
  the member and filing its rows as unmeasured (§3.1) bounds the run either way.

## 10. Open Questions
_None — the product questions are decided in risks-and-gaps "Design Gaps"._
