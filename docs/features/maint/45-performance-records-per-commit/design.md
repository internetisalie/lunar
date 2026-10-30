---
id: "MAINT-45-DESIGN"
title: "Technical Design"
type: "design"
parent_id: "MAINT-45"
folders:
  - "[[features/maint/45-performance-records-per-commit/requirements|requirements]]"
---

# Technical Design: MAINT-45 — Performance Records Stored Per Commit

This feature applies [[MAINT-48]]'s tooling to counted workloads. Unchanged conventions are cited,
not restated: the `lunar-ci` executor rules (MAINT-40 design §2.8), the `loom-cli` contract and test
stub (MAINT-40 design §4.3), `loom_store.py` (MAINT-48 design §2.2). Evidence lives in the DR rows of
[risks-and-gaps.md](risks-and-gaps.md).

## 1. Architecture Overview

```text
build job, after the UniTrack ingest:
  gradlew test --rerun -PwithPerfRecords --tests '*PerfRecord*'     -> build/perf-records/*.json
  perf_ctrf.py ci            (reads tooling/perf/gate-mode: report-only prints, gating also fails)
    push          -> assemble, print WOBBLE lines, store a3 suite perf
    pull_request  -> assemble, print WOBBLE lines, deepen history, merge-base, fetch baseline, diff
```

### Prior Art in This Repo

| Component | Disposition |
|---|---|
| `LuaParserNestingCostTest.MarkCountingBuilder` | **Pattern reused.** It is private to that test and stays. `CountingPsiBuilder` (§2.2) also counts `advanceLexer`. |
| `LuaTypesSnapshot.rootResolutionCount` / `RootAccessor` | **Used unchanged**, as `LuaTypeGraphRootResolutionBudgetTest` does. |
| `LuaTypeGraphRootResolutionBudgetTest`, `performance/` suites | **Unchanged.** |
| `CorpusFindingsFile` | **Pattern reused**: one Gson file per unit under `build/`, cleared by a `doFirst` (`build.gradle.kts`, the `withCorpus` block). |
| `tooling/ctrf/loom_store.py` | **Used unchanged**: `Slot`, `Wanted`, `lookup`, `store_once`, `first_parents`, `fetch_first`, `LoomError`, `EXIT_UNMEASURED`. |
| `junit_ctrf.code_identical_first_parents` | **Imported, not copied.** |
| `junit_ctrf.diff`, `corpus_ctrf.diff` | **Not reused.** They compare statuses and findings; this compares numbers (§3.4). |
| `tooling/ci/install-loom-cli.sh` | **Used unchanged**: the `build` job's "Install loom-cli" step already runs it. |

## 2. Core Components

Nothing here is plugin code: no `plugin.xml` registration, service or production Kotlin. Kotlin below
is test source. Workloads touching PSI run on the EDT through `EdtTestUtil.runInEdtAndWait`, as
`LuaTypeGraphRootResolutionBudgetTest` does; `myFixture.completeBasic()` runs off it, as
`LuaTypeInferredCompletionTest` does.

### 2.1 `build.gradle.kts` (edited)

In the `test { … }` block, directly after the `withCorpus` `doFirst` block:

```kotlin
// MAINT-45: counted workloads get a JVM of their own (design §5, DR-02), so they run only when asked.
if (project.hasProperty("withPerfRecords")) {
    val perfRecordsDir = layout.buildDirectory.dir("perf-records").get().asFile
    doFirst { perfRecordsDir.deleteRecursively() }
} else {
    filter {
        excludeTestsMatching("*PerfRecord*")
        isFailOnNoMatchingTests = false
    }
}
```

The pattern also matches method names (BUG-497), so no other test may contain `PerfRecord`. Nothing
enforces that: `SweepNamingGuardTest` checks only its own word (Risk 1.8).

### 2.2 `src/test/kotlin/net/internetisalie/lunar/perfrecords/` (new)

`PerfCounters.kt`:

```kotlin
internal class CountingPsiBuilder(delegate: PsiBuilder) : PsiBuilderAdapter(delegate) {
    var marks = 0L
        private set
    var advances = 0L
        private set
    override fun mark(): PsiBuilder.Marker { marks++; return super.mark() }
    override fun advanceLexer() { advances++; super.advanceLexer() }
}

/** Per-index stub lookups since the last [drain], read from the platform's own aggregates (DR-03). */
internal object StubLookupCounter {
    fun drain(): Map<String, Long>
}
```

`StubLookupCounter.drain` is the reflection in §3.1.

`PerfRecordFile.kt`, following `CorpusFindingsFile`:

```kotlin
internal data class PerfPassValues(val counts: Map<String, Long>, val outputs: Map<String, Long>)
internal data class PerfRecord(
    val schema: Int, val workload: String, val kind: String, val durationMs: Long,
    val fixture: String, val counts: Map<String, Long>, val outputs: Map<String, Long>,
    val warm: PerfPassValues,
)
internal object PerfRecordFile {
    fun write(repoRoot: File, record: PerfRecord)  // build/perf-records/<workload>.json, Gson, UTF-8
}
```

`repoRoot` is `File(System.getProperty("user.dir"))`, the test task's working directory (DR-01 read
its files from `build/` there). Every map in a record is a `toSortedMap()`.

`PerfRecordFixtures.kt`: the `internal` functions and the constant of §4.1.1, verbatim.

`PerfRecordChecks.kt`:

```kotlin
internal data class WorkloadSpec(val name: String, val kind: String, val fixture: String)
internal data class WorkloadChecks(
    val required: Set<String>, val exactOutputs: Map<String, Long>, val positiveOutputs: Set<String>,
)
internal data class Workload(val spec: WorkloadSpec, val checks: WorkloadChecks, val run: () -> Measured)
internal data class Measured(val counts: Map<String, Long>, val outputs: Map<String, Long>)
internal data class Pass(val rawCounts: Map<String, Long>, val outputs: Map<String, Long>, val durationMs: Long)

internal fun violations(checks: WorkloadChecks, rawCounts: Map<String, Long>, outputs: Map<String, Long>): List<String>
internal fun toRecord(spec: WorkloadSpec, warm: Pass, recorded: Pass): PerfRecord
```

`LuaPerfRecordWorkloadsTest.kt`:

```kotlin
class LuaPerfRecordWorkloadsTest : IndexedDocumentTest() {
    @Test
    fun recordEveryWorkload()
}
```

Private members: `workloads(): List<Workload>`, `resetWorkspace()`, `measurePass(workload: Workload): Pass`.

`PerfRecordChecksTest.kt`: a plain JUnit class with no fixture, holding TC 30 and 32–34 against
`violations` and `toRecord`.

### 2.3 `tooling/ctrf/perf_ctrf.py` and `test_perf_ctrf.py` (new)

Constants: `CHECK = "a3"`, `SUITE_ARGV = ("--suite", "perf")`, `LABEL = "perf"`,
`TOOL_NAME = "lunar-perf-records"`, `SPEC_VERSION = "0.2.0"`, `RECORD_SCHEMA = 1`,
`ACCEPTED_SCHEMA = 1`, `DEFAULT_MAX_DEPTH = 50`, `TIMING_FACTOR = 2`, `TIMING_FLOOR_MS = 300`
(DR-09 sets both), `CPUINFO = "/proc/cpuinfo"`, `REPORT_ONLY = "report-only"`, `GATING = "gating"`,
`MODES = (REPORT_ONLY, GATING)`, `STABILITY_PAIRS = 5`, `STABILITY_CROSS_MODEL_PAIRS = 1`,
`STABILITY_MAX_DEPTH = 400` (DR-21 sets these three), `UNKNOWN_MODEL = "unknown"`, and:

```python
COUNT_PATHS = (
    "src/main",
    "src/test/kotlin/net/internetisalie/lunar/perfrecords",
    "src/test/kotlin/net/internetisalie/lunar/BaseDocumentTest.kt",
    "src/test/kotlin/net/internetisalie/lunar/IndexedDocumentTest.kt",
    "build.gradle.kts",
    "gradle.properties",
    "settings.gradle.kts",
    "gradle",
    "tooling/ci-image",
    ".gitea/workflows/build-plugin.yml",
)
```

`COUNT_PATHS` lists what in this repo can move a count without a host doing it: production code, the
workloads and their base classes, the build, the platform and dependency versions, the job image's source
and the workflow that runs the workloads. The job image in use is pinned outside this repo, so a change to
that pin shows in no `git diff` here. A missed path gives a false `BREAK`, never a false `STABLE`.

| Subcommand | Arguments | Exit |
|---|---|---|
| `assemble` | `--records DIR --out PATH [--cpuinfo PATH]` | 0; 2 not comparable |
| `fetch` | `--commit C --out PATH [--max-depth N] [--repo .]` | 0; 1; 3 no record |
| `diff` | `BEFORE AFTER --accepted PATH` | 0; 1 unaccepted rise; 2 not comparable |
| `ci` | `--records DIR --out PATH --accepted PATH --gate-mode PATH --event E [--base-ref R] [--cpuinfo PATH] [--repo .]` | §3.6 |
| `stability` | `--first-parent REV [--max-depth N] [--work DIR] [--repo .]` | 0 `STABLE`; 1 `NOT_YET` or a tooling failure; 2 a shallow checkout |
| `series` | `(--first-parent REV \| --tags) [--max-depth N] [--work DIR] [--repo .]` | 0; 1 |

`--accepted` and `--gate-mode` on `ci` are relative to `--repo`. `--work` defaults to `build/perf-series`.
`stability`'s `--max-depth` defaults to `STABILITY_MAX_DEPTH`.

Functions: `read_records(directory) -> list[dict]`, `read_cpu(path) -> dict`,
`ctrf_document(records, cpu) -> bytes`, `write_document(out, data)`, `read_mode(path) -> str`,
`parse_accepted(text) -> list[dict]`, `read_accepted(path) -> list[dict]`,
`accepted_at(repo, commit, path) -> list[dict]`, `own_entries(current, previous) -> list[dict]`,
`diff(before, after, accepted) -> tuple[list[str], int]`, `wobble_lines(doc) -> list[str]`,
`count_differences(newer, older) -> list[str]`, `same_count_code(repo, older, newer) -> bool`,
`credit_pair(credit, pair, head)`, `waiting_lines(head, credit) -> list[str]`,
`walk_pair(repo, walk, record) -> bool`, `timing_lines(before, after) -> list[str]`,
`baseline(slot, shas, out) -> tuple[str, int, dict] | None`, `deepen(repo, base_ref)`,
`compare(args, slot, after) -> int`, `gate(mode, moved) -> int`, `slot_for(repo) -> Slot`, `ci(args) -> int`,
`stability(args) -> int`, `series(args) -> int`, `main(argv) -> int`.
`main` maps `NotComparable` to exit 2 (`NOT COMPARABLE: <reason>` on stderr), and `LoomError` and
`subprocess.CalledProcessError` to exit 1 (`loom-cli: <reason>` / `git: <reason>`).
`NotComparable` is imported from `junit_ctrf`, with `code_identical_first_parents`.

Run the tests with `cd tooling/ctrf && python3 -m unittest test_perf_ctrf`. They reuse `loom_stub.STUB`,
patching `loom_store.LOOM_CLI`, and build scratch git repositories with real `git`.

### 2.4 `tooling/perf/accepted-increases.json` (new)

Committed as `{"schema":1,"accepted":[]}`. Format in §4.3.

`tooling/perf/gate-mode` (new) is one line, `report-only` as shipped. §3.6 reads it and §3.9 states when
it changes.

### 2.5 CI

**`.gitea/workflows/build-plugin.yml`, `build` job.** A new step after "Ingest into UniTrack" and
before "Report lint + test result". It must follow both, because this `test` invocation replaces
`build/test-results/test/` and rewrites `test-filter.json`.

```yaml
      - name: Performance records (MAINT-45)
        # After the ingest: this invocation replaces build/test-results/test/. --rerun for the reason
        # the Unit tests step gives. The routing is inside perf_ctrf.py ci: a push stores, a PR compares.
        # tooling/perf/gate-mode decides whether a rise or a wobble can fail: never edit this step for
        # that. Keep the `|| touch`: without it the step's own exit aborts the job script before the
        # final step (DR-14). Any failure is recorded and fails the job in the final step.
        run: |
          if ./gradlew test --rerun --no-daemon --stacktrace -PwithPerfRecords --tests '*PerfRecord*'; then
            python3 tooling/ctrf/perf_ctrf.py ci --records build/perf-records --out build/perf-ctrf/perf.ctrf.json --accepted tooling/perf/accepted-increases.json --gate-mode tooling/perf/gate-mode --event "$GITHUB_EVENT_NAME" --base-ref "${GITHUB_BASE_REF:-}" || touch /tmp/lunar-perf-failed
          else
            touch /tmp/lunar-perf-failed
          fi
```

No `uses:`, step `if:`, `${{ }}` or bashism; the `if` is a shell `if` (MAINT-40 design §2.8).
`GITHUB_EVENT_NAME` and `GITHUB_BASE_REF` are plain env (DR-08).

**"Report lint + test result"**: before `if [ "$verdict" -ne 0 ]`, add
`if [ -f /tmp/lunar-perf-failed ]; then echo "::error::performance records failed, or a count moved in gating mode — see the Performance records step"; verdict=1; fi`.

**`.github/workflows/build-plugin.yml`**: one line in the header's differences list:
`#   - no performance records (MAINT-45): the gate needs the store and lunar-ci's fixed host class.`
No other edit. `build-release` is unchanged.

`changes` needs no edit: `tooling/ctrf/` and `build.gradle.kts` are already code paths.

## 3. Algorithms

### 3.1 `StubLookupCounter.drain`

1. `Class.forName("com.intellij.util.indexing.diagnostic.IndexLookupTimingsReporting\$IndexOperationAggregatesCollector")`,
   `getDeclaredField("stubEntriesLookupDurationsMsByIndexId")`, `isAccessible = true`, `get(null)`, cast
   to `ConcurrentHashMap<*, *>`. Hold it in a `by lazy`.
2. For each entry: invoke `getIntervalHistogram` on the value and `getTotalCount` on the result,
   both reflectively. The interval histogram resets, so this is a delta.
3. Return `"stub:" + key.toString()` to the count, zeros included, as a `toSortedMap()`. Zeros are
   dropped only by `toRecord`, after `violations` has seen them.

A reflective failure throws, failing the run (Risk 1.2).

### 3.2 `recordEveryWorkload`

1. `val workloads = workloads()`.
2. `val warm = workloads.map(::measurePass)`, then `val recorded = workloads.map(::measurePass)`. Only
   the second pass's durations are recorded: the first carries JIT warm-up (DR-09).
3. `val failures = workloads.zip(recorded).flatMap { (w, pass) -> violations(w.checks, pass.rawCounts, pass.outputs).map { "${w.spec.name}: $it" } }`,
   then `assertTrue(failures.isEmpty(), failures.joinToString("\n"))`. These hold in every gate mode.
4. Only then write `toRecord(w.spec, warm[i], recorded[i])` for each index `i` through
   `PerfRecordFile.write`, so a failed run writes nothing. The test never compares the passes: `ci` does,
   from `warm` (§3.6), because the gate mode decides what a difference means.

`measurePass(workload)`: `resetWorkspace()`; `System.gc()`; `StubLookupCounter.drain()` (discarded);
`t0 = System.nanoTime()`; `measured = workload.run()`; `ms = (System.nanoTime() - t0) / 1_000_000`; return
`Pass(measured.counts + StubLookupCounter.drain(), measured.outputs, ms)`. Workload names are unique;
`Workload.run` closes over the fixture text.

`resetWorkspace()` runs before every row of both passes, so a row's counts depend on its own fixture
alone (DR-18, DR-20). `StubIndex.getAllKeys` keeps the keys of content since overwritten, so without the
rebuild a completion count depends on every earlier row:

```kotlin
private fun resetWorkspace() {
    EdtTestUtil.runInEdtAndWait<RuntimeException> {
        WriteCommandAction.runWriteCommandAction(myFixture.project) {
            LightPlatformTestCase.getSourceRoot().children.forEach { it.delete(this) }
        }
        WriteAction.run<RuntimeException> {
            StubIndex.getInstance().forceRebuild(Throwable("MAINT-45 workload reset"))
        }
    }
    IndexingTestUtil.waitUntilIndexesAreReady(myFixture.project)
    ReadAction.run<RuntimeException> {
        StubIndex.getInstance().getAllKeys(LuaGlobalDeclarationIndex.KEY, myFixture.project)
    }
}
```

The closing read finishes the rebuild before `t0`, so no row's duration carries it.

`violations(checks, rawCounts, outputs)` returns, concatenated:

- `"required counter $it is zero"` for each `it` in `checks.required` with `rawCounts.getOrDefault(it, 0L) <= 0L`.
- `"output $k is ${outputs[k]}, expected $v"` for each `(k, v)` in `checks.exactOutputs` with `outputs[k] != v`.
- `"output $it is zero"` for each `it` in `checks.positiveOutputs` with `outputs.getOrDefault(it, 0L) <= 0L`.

`toRecord(spec, warm, recorded)`: `schema = 1`, `workload = spec.name`, `kind = spec.kind`,
`durationMs = recorded.durationMs`, `fixture` = the first 16 hex characters of the SHA-256 of
`spec.fixture`'s UTF-8 bytes, `counts` = `recorded.rawCounts` with values above 0,
`outputs = recorded.outputs`, `warm = PerfPassValues(warm.rawCounts with values above 0, warm.outputs)`.

### 3.3 `ctrf_document(records, cpu)`

`read_records`: for each `sorted(Path(directory).glob("*.json"))`, parse; raise `NotComparable` when the
list is empty or any `schema != RECORD_SCHEMA`. `read_cpu(path)`: `cpuModel` = the first match of
`^model name\s*:\s*(.*)$` (multiline), else `UNKNOWN_MODEL`; `vcpus` = the number of matches of
`^processor\s*:` (multiline). Build §4.2 with tests sorted by workload; `summary.tests` and `.passed`
are the record count, every other summary field 0. Serialise as `junit_ctrf.ctrf_document` does:
`json.dumps(doc, sort_keys=True, separators=(",", ":"), ensure_ascii=False).encode() + b"\n"`.
`write_document(out, data)` creates `out`'s parent, then writes the bytes; `assemble` and `ci` both
call it.

Rerun: durations differ, so `store_once` keeps the first and logs its `WARNING` (DR-05).

### 3.4 `diff(before, after, accepted)`

1. Raise `NotComparable` unless both `results.tool.name == TOOL_NAME`.
2. `B`, `A` = maps from `name` to test. `used` = an empty list.
3. For each `id` in `sorted(A.keys() & B.keys())`:
   - If `extra.fixture` differs: append `FIXTURE_CHANGED\t{id}`, next id.
   - For each `counter` in `sorted(keys of both extra.counts)`, with `b`, `a` the values or 0:
     - `a > b`: find the first entry `e` with `e.workload == id`, `e.counter == counter`,
       `e.from == b`, `a <= e.to`. Found: add `e` to `used`, append
       `ACCEPTED\t{id}\t{counter}\t{b}\t{a}\t{e.reason}`. Not found: `RISE\t{id}\t{counter}\t{b}\t{a}`.
     - `a < b`: `FALL\t{id}\t{counter}\t{b}\t{a}`.
   - For each key of `extra.outputs` on either side whose values differ (absent is `None`):
     `OUTPUT_CHANGED\t{id}\t{key}\t{b}\t{a}`.
4. `ADDED\t{id}` for `sorted(A.keys() - B.keys())`, `REMOVED\t{id}` for `sorted(B.keys() - A.keys())`.
5. For each entry not in `used`: `ACCEPTANCE_UNUSED\t{workload}\t{counter}\t{from}`.
6. Extend with `timing_lines`.
7. Return the lines and 1 if any line starts `RISE\t`, else 0. The `diff` subcommand loads both JSON
   files and `read_accepted(--accepted)`, prints the lines, and returns the code.

`accepted` is the entries this comparison may apply. `ci` passes only the PR's own (§3.5); the local
`diff` subcommand passes the whole file, and no gate reads its exit.

`timing_lines`: `cb`, `ca` = each side's `environment.extra.cpuModel`. If they differ: the single line
`TIMING_NOT_COMPARABLE\t{cb}\t{ca}`. Else for each `id` in the sorted common names:
`TIMING\t{id}\t{b}\t{a}`, with `\tFLAG` appended when `a >= TIMING_FACTOR * b and a - b >= TIMING_FLOOR_MS`.

### 3.5 Acceptance — `read_accepted(path)`

`read_accepted` raises `NotComparable` when the file is missing, then returns `parse_accepted` of its
text. `parse_accepted(text)` raises `NotComparable` on `json.JSONDecodeError`, on
`schema != ACCEPTED_SCHEMA`, or when any entry lacks a non-empty `workload`, `counter` or `reason`, or an
integer `from` and `to` with `to > from`. Otherwise it returns `accepted`.

`accepted_at(repo, commit, path)`: `git -C repo ls-tree --name-only {commit} -- {path}`; empty output
returns `[]`; otherwise `parse_accepted` of `git -C repo show {commit}:{path}`, `check=True`. Both read
the merge-base's blob through the promisor remote `deepen` registers (DR-15).

`own_entries(current, previous) = [e for e in current if e not in previous]`, comparing whole entries.
So an entry applies only on the PR that adds it. Editing any field of a retained entry makes it a new
entry, and that edit is in the PR's diff.

Merged entries stay in the file as the record of why a count moved, and never apply again, because
every later merge-base holds them. Any PR may remove one. `ACCEPTANCE_UNUSED` is printed only for the
PR's own entries, where it means the author's entry matched nothing. A rebase onto a moved baseline
makes an own entry unusable, which is the intended re-review.

### 3.6 `ci(args)`

`read_mode(path)`: `NotComparable` when the file is missing or its stripped text is not in `MODES`,
otherwise that text.

`wobble_lines(doc)`: for each test, in order of name, then `"counts"` and `"outputs"`, then each sorted
key of either the recorded map or `extra.warm`'s map: if the values differ (absent is 0 for counts,
`None` for outputs), `WOBBLE\t{name}\t{key}\t{warm}\t{recorded}`.

`gate(mode, moved)`: `moved` false returns 0. In `GATING`, print
`::error::a count moved; accept a rise in tooling/perf/accepted-increases.json or fix it`, return 1. In
`REPORT_ONLY`, print `::warning::a count moved; tooling/perf/gate-mode is report-only, so this does not fail the job`,
return 0.

`slot_for(repo)` returns `Slot(CHECK, SUITE_ARGV, repo, LABEL)`. `ci`, `fetch`, `stability` and `series`
each bind `slot = slot_for(repo)` first.

1. `mode = read_mode(Path(repo, args.gate_mode))`; print `perf: gate-mode {mode}`.
2. `data = ctrf_document(read_records(records), read_cpu(cpuinfo))`; `write_document(out, data)`;
   `after = json.loads(data)`.
3. `wobbles = wobble_lines(after)`; print each.
4. `event == "push"`: if `gate(mode, bool(wobbles))` returns 1, return 1 without storing, so in `gating`
   mode a wobbling run never becomes a baseline. Otherwise return `store_once(slot, out, data)`.
5. `event != "pull_request"`: print `::warning::perf: event {event} neither stores nor compares`; return
   `gate(mode, bool(wobbles))`.
6. `code = compare(args, slot, after)`; return `gate(mode, bool(wobbles) or code == 1)`.

`compare(args, slot, after)`:

1. `current = read_accepted(Path(repo, args.accepted))`.
2. `deepen(repo, base_ref)`:
   `argv = ["git", "-C", repo, "fetch", "-q"]`; if `git -C repo rev-parse --is-shallow-repository`
   prints `true`, add `--unshallow`; add `--filter=blob:none origin <HEAD sha> +refs/heads/{base_ref}:refs/remotes/origin/{base_ref}`;
   run with `check=True`. Empty `base_ref` raises `NotComparable`. DR-06 executed this form.
3. `merge_base = git -C repo merge-base HEAD origin/{base_ref}`, `check=True`.
4. `own = own_entries(current, accepted_at(repo, merge_base, args.accepted))`.
5. `shas = code_identical_first_parents(repo, merge_base, DEFAULT_MAX_DEPTH)`, then
   `hit = baseline(slot, shas, out.with_name("before.ctrf.json"))`. `baseline` returns
   `fetch_first(slot, shas, Wanted(out, accept))` with
   `accept(doc) = doc["results"].get("tool", {}).get("name") == TOOL_NAME`. The `fetch` subcommand
   makes the same two calls for `--commit`, prints `fetched {sha} distance={distance}` and returns 0,
   or prints `no perf record for a code-identical ancestor of {commit}` and returns 3.
6. `None`: print `::warning::no perf record for a code-identical ancestor of {merge_base}; counts were not gated`; return 0.
7. Print `baseline {sha} distance={distance}`, then every line of `diff(before, after, own)`. Return its code.

A `NotComparable`, `LoomError` or `CalledProcessError` leaves `ci` through `main` at a non-zero exit in
either mode. The step's shape carries each exit to the final step (DR-14).

### 3.7 `series(args)`

- `--first-parent REV`: `commits = list(reversed(first_parents(repo, rev, depth)))`, each `(sha, sha)`
  (label, walk start), walking the commit alone.
- `--tags`: `git -C repo tag --list 'v*' --sort=creatordate`, each `(tag, tag)`, walking
  `code_identical_first_parents(repo, tag, depth)`.

For each, in order, `fetch_first(slot, walk, Wanted(Path(work)/"record.json", accept))`, deleting the
file after reading. No hit: count it. Hit: for each test and each counter collect
`(workload, counter, position, label, value)`, and `(workload, "duration", position, label, ms)` plus
the document's CPU model as a sixth field. Print sorted by `(workload, counter, position)`, tab
separated, without `position`, then `unmeasured\t{count}`. Needs store credentials and full history.

### 3.8 `stability(args)`

The premise under test is that the same code gives the same counts on different hosts, so only records of
the same code are compared. Walking first-parent from the newest record, each two consecutive records
form a **pair**. A pair is **eligible** when `same_count_code` holds. A workload is compared on an eligible
pair when both records hold it with equal `extra.fixture`. Anything else is not evidence: it adds no credit,
raises no `BREAK` and does not stop the walk.

`same_count_code(repo, older, newer)`: `git -C repo diff --no-renames --name-only {older} {newer} -- {*COUNT_PATHS}`,
`check=True`; true when the output is empty.

`count_differences(newer, older)`: for each `id` in the sorted common test names with equal `extra.fixture`,
for each `counter` in the sorted keys of both `extra.counts`, with `n`, `o` the values or 0: if `n != o`,
`{id}\t{counter}\t{n}\t{o}`.

`Walk` is a dataclass: `head: dict | None`, `newer: tuple[str, dict] | None`, `credit: Credit`, `eligible: int`,
`ignored: int`, `broken: bool`, `models: set[str]`. `Credit` holds two `collections.Counter`s, `pairs` and `cross`.

`credit_pair(credit, pair, head)`, with `pair = (newer_doc, older_doc)`, called on an eligible pair with no
difference. `cross` is true when both `environment.extra.cpuModel` values differ and neither is
`UNKNOWN_MODEL`. For each `id` in `head`'s tests held by both records, when both `extra.fixture` equal
`head`'s: `credit.pairs[id] += 1`, and `credit.cross[id] += 1` if `cross`.

`waiting_lines(head, credit)`: for each sorted `id` in `head`'s tests with
`credit.pairs[id] < STABILITY_PAIRS or credit.cross[id] < STABILITY_CROSS_MODEL_PAIRS`:
`WAITING\t{id}\t{pairs}\t{cross}`. So a workload with no cross-model pair is named, and a workload added
inside the walk starts at zero.

1. `git -C repo rev-parse --is-shallow-repository` printing `true` raises `NotComparable`: `first_parents`
   would stop silently at the shallow boundary.
2. `walk = Walk(...)` empty. For each `sha` in `first_parents(repo, rev, depth)`, newest first:
3. `hit = fetch_first(slot, [sha], Wanted(Path(work)/"record.json", accept))`, with `accept` as §3.6.
   `None` (a docs-only commit, a commit inside a push that built only its tip, or a cancelled or failed
   build): next `sha`. Otherwise `doc = hit[2]`; delete the file; add its CPU model to `walk.models` unless
   `UNKNOWN_MODEL`.
4. `wobble_lines(doc)` not empty: print `BREAK\t{sha}\t{line}` for each, set `broken`, stop.
5. `walk.head` is `None`: set `head` and `newer`, next `sha`.
6. `walk_pair(repo, walk, (sha, doc))` returns false (it set `broken`): stop.
7. `waiting_lines(walk.head, walk.credit)` is empty: stop.

`walk_pair`: with `(sha, doc) = record` and `(newer_sha, newer_doc) = walk.newer`. If `same_count_code(repo, sha, newer_sha)` is false:
`ignored += 1`. Otherwise `eligible += 1`, `lines = count_differences(newer_doc, doc)`. `lines` not empty:
print `BREAK\t{newer_sha}\t{sha}\t{line}` for each, set `broken`, return false. Empty:
`credit_pair(walk.credit, (newer_doc, doc), walk.head)`. Either way set `walk.newer = (sha, doc)`; return true.

Then print `eligible\t{eligible}`, `ignored\t{ignored}`, `cpuModels\t{",".join(sorted(models))}`, and each
`waiting_lines` line when `head` is not `None`. If `head` is not `None`, `broken` is false and there are no
`waiting_lines`, print `STABLE` and return 0; else print `NOT_YET` and return 1.

A pair that differs on an old fixture is still a `BREAK`, because it refutes the premise whichever fixture
it ran. Credit, though, is earned only on `head`'s fixture. A `BREAK` is never passed over: gating stays
off until enough pairs newer than it exist for the walk to stop before reaching it.

Needs store credentials and a non-shallow checkout with `REV` fetched. `--filter=blob:none` is enough:
`--name-only` compares trees and fetches no blob (DR-21).

### 3.9 The switch, and when it changes

`tooling/perf/gate-mode` ships as `report-only`.

| Check | Where it is judged | `report-only` | `gating` |
|---|---|---|---|
| A count above its baseline, not accepted (`RISE`) | `ci` on a PR | prints | fails the job |
| The two passes differ (`WOBBLE`) | `ci` on a PR or push | prints; a push stores | fails the job; a push does not store |
| A required counter is zero, or an output is wrong | the workload test | fails the job | fails the job |
| Reflection, `loom-cli`, `git`, an unreadable `gate-mode` or accepted file | wherever it occurs | fails the job | fails the job |

The third and fourth rows stay hard on `main` and on PRs because neither is a comparison between two
runs, so neither depends on the premise the switch protects: that a count is the same on two hosts. A
failed check means the workload did not do its work and would store a vacuous baseline. A tooling failure
means the run compared or stored nothing while reading as green.

**Turning gating on** is one PR changing that file to `gating`, opened only after this prints `STABLE`:

```bash
git fetch origin main
set -a; . ~/.config/loom/loom.artifacts.env; set +a
python3 tooling/ctrf/perf_ctrf.py stability --first-parent origin/main
```

**Recovery from a false `RISE` or `WOBBLE`** is one PR changing the file back to `report-only`. It takes
effect at once for `main`. An open PR runs at its own head (DR-08), so it needs that commit merged in, or
the same one-line edit of its own. The file is not a docs path, so the change builds and stores (`changes`
treats everything outside `docs/` and `*.md` as code). Gating returns by the same command, once it stops
short of the false rise (§3.8).

## 4. External Data & Parsing

### 4.1 Workloads

`workloads()` returns the rows below in table order, each brace expanded left to right. The order is pinned so
a run is reproducible. `resetWorkspace` is what makes the counts independent of it (DR-20). `n` or `d` is the
size in the name.

| Name | Kind | Fixture (§4.1.1) | Action | Counts | Required counters | Outputs and check |
|---|---|---|---|---|---|---|
| `parse-flat-{500,2000}` | parse | `flat(n)` | §4.1.2 | `marks`, `advances` | `marks`, `advances` | none |
| `parse-callbacks-{8,12}` | parse | `callbacks(d)` | §4.1.2 | same | same | none |
| `parse-parens-12` | parse | `parens(d)` | §4.1.2 | same | same | none |
| `snapshot-annotated-{20,80,160}` | snapshot | `annotatedCalls(n)` | `configureByText`, `LuaTypesSnapshot.forFile` | `write`, `read`, `demand` from `rootResolutionCount`, plus stub counters | `write`, `read`, `demand`, `stub:lunar.global.decl` | none |
| `resolve-colon-{80,320}` | resolve | `colonCalls(n)` | as snapshot, then `reference?.resolve()` on every `LuaNameRef` with `it.parent is LuaMethodExpr`, as the budget test's `assertEveryColonCallSiteResolves` does | `write`, `read`, `demand`, plus any stub counters | `write`, `read`, `demand` | `resolved`; exactly `n` |
| `completion-member-{200,800}` | completion | `members(n)` | `configureByText`, `completeBasic` | stub counters | `stub:lunar.global.decl` | `lookupItems`; exactly `n` |
| `completion-global-{200,800}` | completion | `globals(n) + "\nglobal_fu<caret>"` | same | stub counters | `stub:lunar.global.decl` | `lookupItems`; above 0 |
| `completion-crossfile-{200,800}` | completion | `globals(n)` and `USE_LUA` | `myFixture.configureByText("globals.lua", globals(n))`, `myFixture.configureByText("use.lua", USE_LUA)`, `completeBasic` | stub counters | `stub:lunar.global.decl` | `lookupItems`; above 0 |

`configureByText` with no receiver is `BaseDocumentTest.configureByText`, writing `test.lua`.
`lookupItems` is `myFixture.lookupElements?.size?.toLong() ?: 0L`, read after `completeBasic`.

The crossfile row must write `globals.lua` with `myFixture.configureByText`, never `addFileToProject`.
`BaseDocumentTest` builds `LightTempDirTestFixtureImpl(false)`, so `addFileToProject` writes under
`temp:///root`, outside the source root, and the file is not indexed (DR-20).

The last column maps to `WorkloadChecks`: "exactly `n`" is `exactOutputs`, "above 0" is `positiveOutputs`.
DR-16 holds each required counter's observed value.

`fixture` for the crossfile row hashes `globals(n) + "\n" + USE_LUA`.

**§4.1.1 Fixtures**, `PerfRecordFixtures.kt`:

```kotlin
internal const val USE_LUA = "local x = global_fu<caret>"

internal fun flat(n: Int): String =
    (0 until n).joinToString("\n") { i ->
        when (i % 5) {
            0 -> "local t$i = { a = $i, b = \"s$i\" }"
            1 -> "function f$i(x) if x > $i then return x else return $i end end"
            2 -> "for k = 1, $i do local c = function() return k end; c() end"
            3 -> "obj:method$i(1); obj.field$i = $i"
            else -> "local w = 0; while w < 3 do w = w + 1 end; repeat w = w - 1 until w == 0"
        }
    }

internal fun callbacks(d: Int): String = "f(function()\n".repeat(d) + "x()\n" + "end)\n".repeat(d)

internal fun parens(d: Int): String = "local x = " + "(".repeat(d) + "a" + ")".repeat(d)

internal fun annotatedCalls(n: Int): String =
    "---@class Builder\nlocal Builder = {}\nfunction Builder:setName(n) end\n\nlocal b = Builder\n" +
        (0 until n).joinToString("\n") { "b:setName(\"a$it\")" }

internal fun colonCalls(n: Int): String =
    "local t = {}\nfunction t:m(n) end\n\n" + (0 until n).joinToString("\n") { "t:m(\"a$it\")" }

internal fun members(n: Int): String =
    "---@class Builder\nlocal Builder = {}\n" +
        (0 until n).joinToString("\n") { "function Builder.m$it() end" } + "\nlocal b = Builder\nb.<caret>"

internal fun globals(n: Int): String = (0 until n).joinToString("\n") { "function global_func_$it() end" }
```

`members` needs its `local Builder = {}`: without it the row returns no items (DR-19).

**§4.1.2 Parse action**, as `LuaParserNestingCostTest.marksToParse`:
`val definition = LuaParserDefinition()`, `CountingPsiBuilder(PsiBuilderFactory.getInstance().createBuilder(definition, definition.createLexer(myFixture.project), text))`,
`LuaParser().parseLight(definition.fileNodeType, builder)`.

Which values `ci` judges: everything in `counts` and `warm`. Elapsed time and allocation are not judged
(DR-01). A differing `outputs` value between two records is a printed `OUTPUT_CHANGED`, never a gate.

### 4.2 Files

**`build/perf-records/<workload>.json`**: `{"schema":1,"workload":"parse-flat-500","kind":"parse","durationMs":46,"fixture":"<16 hex>","counts":{"advances":10200,"marks":95123},"outputs":{},"warm":{"counts":{"advances":10200,"marks":95123},"outputs":{}}}`.

**CTRF document**, `a3.perf.ctrf.json`, schema as MAINT-48 design §4.3 pins it (DR-07 validated this shape):

```json
{"reportFormat":"CTRF","specVersion":"0.2.0","results":{
 "tool":{"name":"lunar-perf-records"},
 "summary":{"tests":1,"passed":1,"failed":0,"skipped":0,"pending":0,"other":0,"start":0,"stop":0},
 "environment":{"osPlatform":"linux","extra":{"cpuModel":"<model name>","vcpus":4}},
 "tests":[{"name":"parse-flat-500","testId":"parse-flat-500","status":"passed","duration":46,
           "suite":["parse"],"extra":{"counts":{"advances":10200,"marks":95123},"fixture":"<16 hex>","outputs":{},
           "warm":{"counts":{"advances":10200,"marks":95123},"outputs":{}}}}]}}
```

`osPlatform` is `platform.system().lower()`.

### 4.3 `tooling/perf/accepted-increases.json`

```json
{"schema":1,"accepted":[{"workload":"parse-flat-500","counter":"marks","from":95123,"to":101000,
 "reason":"the attribute grammar adds one rule entry per local"}]}
```

### 4.4 `/proc/cpuinfo`

§3.3's two patterns. `model name\t: Intel(R) Xeon(R) …`; a VM's `model\t\t: 85` line is why the first
pattern spells `model name`.

### 4.5 `loom-cli`

MAINT-40 design §4.3 with `SUITE_ARGV` on resolve and put. No `--producer`, `--no-item` on put (DR-05).

## 5. Data Flow

**`main` push.** Unit steps as today → the new step runs the workloads → `ci` stores `a3.perf.ctrf.json`
→ report.

**PR.** Same steps → `ci` deepens, finds the merge-base, walks docs-only ancestors for a record, diffs,
prints, and in `gating` mode fails through `/tmp/lunar-perf-failed`.

The workloads take their own Gradle invocation, at a cost of about a minute (DR-04), instead of
sharing the `test` JVM. `resetWorkspace` clears the source root and the index, but a file that reaches
the index by another route moves counts (DR-02), and nothing resets other project state such as library
roots or settings. A new test elsewhere could otherwise fail a later PR's gate.

## 6. Edge Cases

| Case | Handling |
|---|---|
| A workload throws or a check fails | the test fails, no files, `else` branch of the step: sentinel, red, in either mode |
| Merge-base's `build` not finished yet, or cancelled | no record: warning, not gated (§3.6 `compare` step 6, Risk 1.3) |
| CI rerun on `main` | keep-first `WARNING`, exit 0 |
| Two `main` runs of one commit racing | the losing `put` exits 1 and reds that run (DR-05); same as MAINT-48 |
| A platform bump moves counts | `RISE` lines on its PR, accepted like any other (Risk 1.2) |
| `main` wobbles in `gating` | `ci` returns 1 without storing: that push's job is red. A later PR whose nearest code-identical ancestor is that commit finds no record and warns "counts were not gated" (§3.6 `compare` step 6) until the next code commit stores one |
| A rise while `report-only` | `RISE` and `::warning::` in the step log, green; the merged value is the next baseline (Risk 1.3) |
| A PR run with a red unit suite | the step still runs; both verdicts are reported |
| A merged `accepted` entry | not applied, no line (§3.5) |
| An own entry matching nothing | `ACCEPTANCE_UNUSED`, exit unaffected |

## 7. Integration Points

No `plugin.xml` registrations. Touch points: `build.gradle.kts` (§2.1), the new test package (§2.2),
`tooling/ctrf/` (§2.3), `tooling/perf/` (§2.4, §3.9), both workflows (§2.5), `tooling/ctrf/README.md` (usage
lines), `docs/profiling.md` (a pointer to the series report), MAINT-38's `requirements.md`
(MAINT-38-09 row: answered by MAINT-45).

## 8. Requirement Coverage

| Requirement | Priority | Implemented by |
|---|---|---|
| MAINT-45-01 | M | §2.2, §3.1, §4.1 |
| MAINT-45-02 | M | §3.2, §3.6 `wobble_lines`, §4.1 |
| MAINT-45-03 | M | §2.1, §5 |
| MAINT-45-04 | M | §2.3, §3.3, §4.2 |
| MAINT-45-05 | M | §3.3, §4.4 |
| MAINT-45-06 | M | §2.5, §3.6 steps 1–4 |
| MAINT-45-07 | M | §3.6 `compare` steps 2–5 |
| MAINT-45-08 | M | §3.4, §3.6, §2.5 |
| MAINT-45-09 | M | §2.4, §3.4, §3.5, §4.3 |
| MAINT-45-10 | M | §3.4 `timing_lines` |
| MAINT-45-11 | M | §3.6 `compare` step 6, §6 |
| MAINT-45-12 | S | §3.4 steps 3–4 |
| MAINT-45-13 | S | §3.7 |
| MAINT-45-15 | M | §2.4, §3.6, §3.9 |
| MAINT-45-16 | M | §3.8, §3.9 |

## 9. Alternatives Considered

- **Accept by commit trailer.** Hidden from the file diff a reviewer reads, and it must be re-found
  by walking the PR's commits. Squash merging may keep or drop it.
- **Accept by PR label.** Needs an API call and token, and adding a label does not rerun the job.
- **Accept by editing a committed baseline.** The stored record is the reference by Scope, and a
  second copy of every number is what the store removes.
- **Workloads inside the main `test` JVM.** Saves about a minute, and lets any test's leftovers fail
  the gate (§5).
- **A reset without the rebuild, or a rebuild only per test method.** Both leave a row's count depending
  on earlier rows (DR-18). Rebuilding only before completion rows gives the same counts today and costs
  less (DR-18), at the price of a second rule to keep true.
- **Seeding the index with an extra discarded pass.** It would steady the passes, but each row's count would
  then depend on the whole workload list, so adding a workload would move every completion row.
- **`stability` comparing against the head record.** A workload whose fixture changed at head then has
  nothing to compare, and a pair of unrelated code is compared. Consecutive pairs with the path check are
  the comparison the premise describes.
- **A counter in production `LuaNameReference` or a stub-index wrapper.** Touches hot paths, against
  the brief; the platform already aggregates stub lookups.
- **Storing PR runs too**, as MAINT-48 does. Scope says `main` pushes; here the routing is a Python
  branch, so the executor's lack of step `if:` does not force it.
- **A fixed tolerance on counts.** Counts held on the one host measured (DR-01, DR-18). A tolerance would admit
  regressions without answering whether they vary elsewhere; the switch and `stability` measure that.
- **Stopping the gate by editing the step.** Deleting the `|| touch` aborts the job script (DR-14), and
  it cannot reach a wobble in the Gradle branch. Hence one committed file, read by `ci`.
- **Failing the Kotlin test on a wobble, with the test reading `gate-mode`.** Puts the mode decision in
  two languages. Recording `warm` keeps every decision in `ci`, under Python test cases, and gives
  `stability` the evidence on `main`.

## 10. Premises

- **`a3`, CTRF, suite `perf`**: fixed by Scope and loom's per-check format (MAINT-40 Gap 2.1); the
  name `perf` is chosen and passes `ParseSuite` (DR-05).
- **Immutability**: fixed by the store. Keep-first is the only behaviour available.
- **Depth-1 checkout**: fixed by the executor; the feature deepens rather than working around it.
- **`lunar-ci` as the only recording host**: chosen, by Scope, for timings. Whether counts need a fixed
  host is measured by `stability`, not assumed (DR-11 covers core count on one host).
- **Python and `loom_store`**: reused because this is the same store with the same record lifetime.
- **Gating on every value in `counts`**: chosen; each held across runs on one host (DR-01), and
  `stability` must hold across hosts before gating turns on.
- **`forceRebuild` before every row**: chosen, with its cost in DR-04. `getAllKeys` keeping stale keys is
  platform behaviour the feature cannot remove (DR-18).
- **`STABILITY_PAIRS`, `STABILITY_CROSS_MODEL_PAIRS`, `STABILITY_MAX_DEPTH`, `COUNT_PATHS`**: chosen against
  DR-21's history. One cross-model pair is what tests the cross-host premise, since a model-dependent
  count differs on every such pair. The pair threshold is a trade of evidence against wait, not a statistical
  bound. Each record also compares its own two passes, which is where a scheduling-dependent count shows.
- **Report-only until measured**: chosen. The cross-host premise cannot be measured before records
  exist, so the gate cannot be proven before it ships.
- **Merge-base's copy of the accepted file**: chosen, so an entry binds to the PR that adds it (§3.5).
- **The PR head's copy of `gate-mode`**: chosen, so a blocked PR can carry the recovery itself. Its cost
  is Risk 1.5.
- **`DEFAULT_MAX_DEPTH = 50`**: reused from MAINT-48 for the same walk.
- **A reflective read of a platform field**: chosen over a production counter, with its cost in Risk 1.2.
- **Non-blocking missing baseline**: chosen. A merge-base's own `build` is often still running when a
  PR is pushed, so red would be a routine false alarm.

## 11. Open Questions

None.
