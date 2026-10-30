---
id: "MAINT-48-DESIGN"
title: "Technical Design"
type: "design"
parent_id: "MAINT-48"
folders:
  - "[[features/maint/48-unit-test-results-per-commit/requirements|requirements]]"
---

# Technical Design: MAINT-48 — Unit Test Results Stored Per Commit

This feature applies [[MAINT-40]]'s store conventions to the unit suite. Where a convention is
unchanged it is cited, not restated: MAINT-40 design §2.8 (CI executor rules, install step), §4.3
(`loom-cli` resolve/get/put contract and the test stub) and MAINT-40 risks DR-02/DR-03/DR-10 (put
immutability, resolve exit codes, suite argv).

## 1. Architecture Overview

### Current State

The `build` job runs `./gradlew test --rerun … -PexcludeExternalFixtureTests`
(`.gitea/workflows/build-plugin.yml:230-232`). Its only consumer of the per-test XML is the
UniTrack ingest (`:261-280`).

### Prior Art in This Repo

| Component | Disposition |
|---|---|
| `tooling/corpus/corpus_ctrf.py`: `LOOM_CLI`, `EXIT_UNMEASURED`, `LoomError`, `loom`, `_output`, `lookup`, the resolve-then-put part of `store`, `_first_parents`, `_get`, the walk in `fetch` | **Extracted** into `tooling/ctrf/loom_store.py` (§2.2). `corpus_ctrf.py` keeps its CTRF assembly, identity, shape refusal and `diff`, and calls the shared module (§2.4). Its observable behaviour is unchanged, except that `fetch` now creates the parent of `--out` (§2.2). |
| The `corpus` job's inline loom-cli install (`build-plugin.yml:370-394`) | **Extracted** into `tooling/ci/install-loom-cli.sh` (§2.5). Both jobs call it. |
| `corpus_ctrf.diff` | **Not reused.** It compares findings (always `failed`) by fingerprint and per-rule counts. A unit diff compares statuses and must know each side's test set (§3.7). |
| UniTrack ingest (`build-plugin.yml:243-280`) | **Unchanged.** It reads the same XML. It is analytics, has no fetch-by-commit contract in this repo, and is `continue-on-error`. |
| `build.gradle.kts` `test` filters (`:375-443`) | **Unchanged.** The sidecar (§2.1) reads them; nothing duplicates the pattern list. |
| JUnit-to-CTRF converters | None in the repo (`grep -rli junit tooling/` matches only `tooling/gce-builder/README.md`, a `TemporaryFolder` mention). The npm converters are not used: the `lunar-ci` image has `python3` (`tooling/ci-image/Dockerfile:79`) and deliberately no Node (`:10-11`, `:68`). |

### Target State

Each `test` run writes `build/test-results/test-filter.json` (§2.1). The `build` job converts the
XML plus that sidecar into one CTRF document and stores it as `a3`, no suite (§2.6).
`junit_ctrf.py fetch` returns the record of the nearest code-identical first-parent ancestor, and
`junit_ctrf.py diff` compares two documents test by test (§3.6, §3.7). Consumers, starting with
the `supervise-implement` gate, are MAINT-50's.

## 2. Core Components

Nothing here is plugin code: there is no `plugin.xml` registration, PSI, threading or IDE
service. Python is stdlib-only (MAINT-40 non-functional requirement).

### 2.1 `build.gradle.kts`: the test-set sidecar (edited)

Inside the `test { … }` block, after the last `filter { … }` block and before
`if (project.hasProperty("recordCorpusBaseline"))` (`build.gradle.kts:446`), unconditionally:

```kotlin
// MAINT-48: the patterns this invocation actually filtered on, for tooling/ctrf/junit_ctrf.py.
// Outside test-results/test/, whose contents the task's report replaces (MAINT-48 DR-02).
val testFilterFile = layout.buildDirectory.file("test-results/test-filter.json").get().asFile
outputs.file(testFilterFile)
doFirst {
    val effectiveFilter = (this as Test).filter
    val commandLinePatterns = (effectiveFilter as? DefaultTestFilter)?.commandLineIncludePatterns.orEmpty()
    testFilterFile.parentFile.mkdirs()
    testFilterFile.writeText(
        JsonOutput.toJson(
            mapOf(
                "schema" to 1,
                "excludePatterns" to effectiveFilter.excludePatterns.sorted(),
                "includePatterns" to effectiveFilter.includePatterns.sorted(),
                "commandLineIncludePatterns" to commandLinePatterns.sorted(),
            ),
        ) + "\n",
    )
}
```

Imports at the top of `build.gradle.kts`: `groovy.json.JsonOutput` and
`org.gradle.api.internal.tasks.testing.filter.DefaultTestFilter`. `getCommandLineIncludePatterns`
is internal API (no public accessor exists, MAINT-40 DR-08); the safe cast falls back to an empty
list, which §3.5 then cannot tell from a full run (Risk 1.2). MAINT-48 DR-03 executed a throwaway
version of this block.

`outputs.file` makes the sidecar an output of the same task as the XML, so the two can't
disagree. When `test` is UP-TO-DATE, neither file is touched. When it is FROM-CACHE, both are
restored from one cache entry. A changed filter changes the task's inputs, so the task runs again
and the `doFirst` rewrites the sidecar (DR-10). Without `outputs.file`, a cache hit would bring
back the XML and leave a sidecar from an earlier invocation.

### 2.2 `tooling/ctrf/loom_store.py` (new)

```python
LOOM_CLI = os.environ.get("LOOM_CLI", "loom-cli")
EXIT_UNMEASURED = 3

class LoomError(Exception): ...           # moved from corpus_ctrf, same meaning

@dataclass(frozen=True)
class Slot:
    check: str                             # "a3"
    suite_argv: tuple[str, ...]            # () for the primary record, ("--suite", "corpus") for MAINT-40
    repo: str                              # "--repo" value, default "."
    label: str                             # used in messages: "corpus", "unit-test"

def loom(argv: list[str]) -> subprocess.CompletedProcess          # moved verbatim
def output(result: subprocess.CompletedProcess) -> str             # moved verbatim (was _output)
def lookup(slot: Slot, sha: str) -> dict | None                    # §3.5 step 2 of MAINT-40, slot's check and suite
def store_once(slot: Slot, out: Path, data: bytes) -> int          # MAINT-40 §3.5 steps 2-3
def first_parents(repo: str, commit: str, depth: int) -> list[str] # moved verbatim (was _first_parents)
def get(record: dict, part: Path) -> dict                          # moved verbatim (was _get)
def fetch_first(slot: Slot, shas: list[str], out: Path, accept: Callable[[dict], bool]) -> tuple[str, int, dict] | None
```

- `lookup` runs `[LOOM_CLI, "artifact", "resolve", "--commit", sha, "--check", slot.check, "--repo",
  slot.repo, "--json", *slot.suite_argv]`: exit 3 → `None`; exit 0 → `checks[0]["record"]`; else
  `LoomError`. This is the argv `corpus_ctrf.lookup` builds today.
- `store_once`: `record = lookup(slot, "HEAD")`. If not None: print
  `store: already stored, identical` when `record["loom-sha256"] == sha256(data).hexdigest()`, else
  `WARNING store: HEAD already has a different {slot.check} {slot.label} record; kept the first`;
  return 0. If None: run `[LOOM_CLI, "artifact", "put", "conformance", str(out), "--commit", "HEAD",
  "--check", slot.check, "--no-item", "--repo", slot.repo, *slot.suite_argv]`; exit 0 → print
  `store: <stdout stripped>`, return 0; else print `store: put exited <n>: <output>` to stderr, return 1.
  With `label = "corpus"` every message is byte-identical to today's.
- `fetch_first`: first `out.parent.mkdir(parents=True, exist_ok=True)`, because `loom-cli get`
  does not create a missing parent (DR-09). This is the one behaviour change for corpus: its fetch
  used to fail when the parent was missing. Then, for `distance, sha in enumerate(shas)`: `record = lookup(slot, sha)`; `None` →
  continue; `doc = get(record, Path(f"{out}.part"))`; if not `accept(doc)` → unlink the part,
  continue; else `part.replace(out)` and return `(sha, distance, doc)`. Returns `None` when the list
  is exhausted. It prints nothing; callers print.

### 2.3 `tooling/ctrf/junit_ctrf.py` (new) and `tooling/ctrf/test_junit_ctrf.py` (new)

Constants: `CHECK = "a3"`, `SUITE_ARGV = ()`, `LABEL = "unit-test"`, `TOOL_NAME = "lunar-unit-tests"`,
`SPEC_VERSION = "0.2.0"`, `FILTER_SCHEMA = 1`, `MESSAGE_CAP = 1000`, `DEFAULT_MAX_DEPTH = 50`,
`EXIT_DROP = 4`, `DOCS_ONLY_PATH = re.compile(r"^docs/|\.md$")` (the `changes` job's docs-only rule,
`build-plugin.yml:106`; a path matching it is not code). `DEFAULT_MAX_DEPTH` is corpus's value
(`corpus_ctrf.py:31`), for the same walk. `MESSAGE_CAP` is longer than every message DR-04
recorded. It stops a failure that embeds a whole expected/actual dump from inflating the document;
`diff` prints the message only as context.

| Subcommand | Arguments | Exit |
|---|---|---|
| `assemble` | `--results DIR --filter PATH --out PATH` | 0; 2 on `NotComparable` |
| `store` | `--results DIR --filter PATH --out PATH [--repo .]` | §3.5 |
| `fetch` | `--commit C --out PATH [--max-depth N] [--repo .]` | §3.6 |
| `diff` | `BEFORE AFTER` | §3.8 |

Functions: `read_filter(path) -> dict`, `read_cases(directory) -> list[dict]`,
`outcome(testcase: Element) -> tuple[str, str | None, str]`, `identify(cases) -> list[tuple[str, dict]]`,
`test_set_identity(filter_doc) -> str`, `ctrf_document(cases, filter_doc) -> bytes`,
`code_identical_first_parents(repo, commit, depth) -> list[str]`, `selected(filter_doc, test) -> bool`,
`diff(before, after) -> tuple[list[str], int]`, `main(argv) -> int`. Exception `NotComparable`
(`main` prints `NOT COMPARABLE: <reason>` to stderr, returns 2); `loom_store.LoomError` (prints
`loom-cli: <reason>`, returns 1). Imports `loom_store` directly (same directory).

Tests: `cd tooling/ctrf && python3 -m unittest test_junit_ctrf`. They reuse MAINT-40's stub
`loom-cli` (MAINT-40 design §4.3), moved to `tooling/ctrf/loom_stub.py` as the constant `STUB` and
imported by both test files, patching `loom_store.LOOM_CLI`.

### 2.4 `tooling/corpus/corpus_ctrf.py` (edited) and its test

- After the stdlib imports: `sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "ctrf"))`,
  then `import loom_store` and `from loom_store import EXIT_UNMEASURED, LoomError`.
- Delete `LOOM_CLI`, `EXIT_UNMEASURED`, `LoomError`, `loom`, `_output`, `lookup`, `_first_parents`,
  `_get`. Add `def _slot(repo) -> loom_store.Slot: return loom_store.Slot(CHECK, SUITE_ARGV, repo, "corpus")`.
- `store`: keep assembly and the shape refusal; replace from `record = lookup(...)` to the end with
  `return loom_store.store_once(_slot(args.repo), Path(args.out), data)`.
- `fetch`: keep `want` and the messages; call
  `loom_store.fetch_first(_slot(args.repo), loom_store.first_parents(args.repo, args.commit, args.max_depth), out, accept)`
  with `accept(doc) = doc["results"].get("tool", {}).get("name") == TOOL_NAME and (want is None or _identity(doc) == want)`.
  On a hit print `fetched {sha} identity={_identity(doc)} distance={distance}`, return 0.
- `test_corpus_ctrf.py`: `STUB` moves to `loom_stub.py` (imported after `corpus_ctrf`, which put
  `tooling/ctrf` on the path), and `StubbedLoomTest` patches `loom_store.LOOM_CLI` instead of
  `corpus_ctrf.LOOM_CLI`. No assertion changes.

### 2.5 `tooling/ci/install-loom-cli.sh` (new)

The body of `build-plugin.yml:370-394`, moved verbatim into a `#!/bin/sh` script (no `set -e`; every
guard stays an explicit `if … exit 1`), followed by `loom-cli --version` as its last command. MAINT-40
DR-07 executed this body on `lunar-ci`.

In the `corpus` job's "Verify toolchain" step, those lines become
`if ! sh tooling/ci/install-loom-cli.sh; then exit 1; fi`. The final compound keeps its
`loom-cli --version &&` prefix. Its comment (`build-plugin.yml:358-368`) is cut to one line
pointing at the script, and the explanation moves into the script's header.

The `changes` job's CORPUS regex (`build-plugin.yml:102`) gains `^tooling/ctrf/|^tooling/ci/`. The
`corpus` job now depends on both directories, so a PR that touches them runs that job.

### 2.6 CI: the Gitea `build` job (edited)

- **New step "Install loom-cli"**, after "Verify toolchain":
  `sh tooling/ci/install-loom-cli.sh || touch /tmp/lunar-unit-store-failed`.
- **New step "Store unit-test results (loom conformance, check a3)"**, directly after "Unit tests":
  `python3 tooling/ctrf/junit_ctrf.py store --results build/test-results/test --filter build/test-results/test-filter.json --out build/unit-ctrf/unit.ctrf.json || touch /tmp/lunar-unit-store-failed`.
  The test step records its failure in a sentinel (`:231-232`), so this step runs on a red suite.
- **"Report lint + test result"**: before `if [ "$verdict" -ne 0 ]`, add
  `if [ -f /tmp/lunar-unit-store-failed ]; then echo "::error::storing the unit-test results failed — see the Install loom-cli and Store unit-test results steps"; verdict=1; fi`,
  and its last line becomes `echo "ktlintCheck and unit tests passed; unit-test results stored"`.
- Every run of `build` stores: `main` pushes and PR runs. A step-level ref test is unavailable on
  this executor; a squash-merged PR head is never a first-parent ancestor on `main`, so its record
  is never fetched (MAINT-40 design §6). Tag pushes never reach `build` (`:138`), so no tag run
  stores and no tag run races a `main` run.
- `build-release` and `.github/workflows/build-plugin.yml` are unchanged.
- The store credentials and `LOOM_MODULES_TOKEN` arrive as plain env (MAINT-40 design §2.8).

## 3. Algorithms

### 3.1 Reading cases — `read_cases(directory)`, `outcome(testcase)`

1. For each `path` in `sorted(Path(directory).glob("TEST-*.xml"))`, parse it with
   `xml.etree.ElementTree` and visit every `testcase` element in document order. If the glob is
   empty, raise `NotComparable("no JUnit XML in <dir>")`.
2. `outcome`: the first child present among `failure`, `error`, `skipped`, in that order, gives
   `status` = `failed`, `failed`, `skipped` and `raw` = the tag name; none gives `passed`, `None`.
   `message` = the child's `message` attribute if non-empty, else the first line of its text
   stripped, else `""`; then truncated to `MESSAGE_CAP` characters. A passed case has `""`.
3. Each case is `{"classname", "method": name attribute, "file": path.name, "status", "raw",
   "message", "duration": round(float(time attribute or "0") * 1000)}`.

### 3.2 Test identity — `identify(cases)`

1. Sort the cases by `(classname, method, file, message, status, duration)`.
2. `name = f"{classname}.{method}"`. `ordinal` = the number of earlier cases in that order with the
   same `name`. `testId = name` when `ordinal == 0`, else `f"{name}#{ordinal}"`.
3. If two `testId`s are equal (possible only when a real method name ends in `#<digits>`), raise
   `NotComparable("duplicate testId <id>")`.

No name is normalised. The method name is whatever the engine reports (DR-02, DR-04): JUnit 3 and JUnit 4
report the method (`testGotoAndLabel`, or a backticked Kotlin name with spaces), Jupiter its display
name (`testArgs()`). Real names contain spaces, parentheses and `#`, so nothing ever parses a
`testId`: the class is `suite[0]` and the method is `name` minus the `suite[0] + "."` prefix.
Ordinals depend only on the multiset of cases, never on file or element order, because every field
that can differ is in the sort key. The other side of that: repeats of one name are told apart only
by message, status and duration. If those change, the repeats can swap ordinals and `diff` reports
both of them. A name that occurs once always has `ordinal` 0, so its `testId` never moves. DR-02
found no repeated `class.method` in a full run.

### 3.3 Test-set identity — `test_set_identity(filter_doc)`

`read_filter` raises `NotComparable` when the file is missing (`no test-filter.json at <path>`)
or when `schema != FILTER_SCHEMA`. It returns the document with each of the three lists sorted, and
those sorted lists are what `environment.extra.filter` carries. `assemble` and `store` call
`read_filter` before `read_cases`, so a run that never reached `test` reports the missing sidecar.
The identity is
`"set-" + sha256(json.dumps({k: filter_doc[k] for k in ("excludePatterns", "includePatterns", "commandLineIncludePatterns")}, sort_keys=True, separators=(",", ":")).encode()).hexdigest()[:16]`.
CI's set and the builder's set therefore get different identities without anyone typing either.

### 3.4 Document assembly — `ctrf_document(cases, filter_doc)`

1. Build one §4.3 test per `identify` pair, in `testId` order.
2. `summary`: `tests` = the number of tests; `passed`, `failed`, `skipped` = the counts of each status;
   `pending`, `other`, `start`, `stop` = 0.
3. Return `json.dumps(doc, sort_keys=True, separators=(",", ":"), ensure_ascii=False).encode() + b"\n"`.

`assemble` and `store` write those bytes to `--out` after `Path(out).parent.mkdir(parents=True, exist_ok=True)`,
as corpus's `_assemble_to` does (`corpus_ctrf.py:173`).

### 3.5 Store — `store(args)`

1. Assemble to `--out` (raises `NotComparable` → exit 2 on a missing or bad sidecar, then on missing XML).
2. If `filter_doc["commandLineIncludePatterns"]` is non-empty, print
   `refusing to store a partial run: --tests <patterns joined by ", ">` and return 2 before any
   `loom-cli` call.
3. `return loom_store.store_once(Slot(CHECK, SUITE_ARGV, args.repo, LABEL), Path(args.out), data)`.

A CI rerun has different durations, so its bytes differ: `store_once` keeps the first record and
logs the `WARNING` line, exit 0. DR-06 measured durations differing on most tests between two
runs, and MAINT-40 DR-02 measured that a differing put is refused.

### 3.6 Fetch — `fetch(args)` and `code_identical_first_parents(repo, commit, depth)`

1. `shas = loom_store.first_parents(repo, commit, depth)`.
2. Keep the longest prefix of `shas` whose every element `s` satisfies: every line of
   `git -C repo diff --name-only s commit` matches `DOCS_ONLY_PATH`. `shas[0]` is `commit` itself and always
   qualifies. Stop at the first `s` that fails: an ancestor that differs in code is not a baseline.
3. `hit = loom_store.fetch_first(slot, prefix, Path(args.out), accept)` with
   `accept(doc) = doc["results"].get("tool", {}).get("name") == TOOL_NAME`.
4. Hit: print `fetched {sha} identity={testEnvironment} distance={distance}`, return 0. Miss: print
   `no unit-test record for a code-identical first-parent ancestor of {commit} (searched {len(prefix)})`,
   return 3.

Docs-only pushes to `main` skip the `build` job (`build-plugin.yml:138`, `changes` at `:106`), so the
merge-base often has no record of its own; its nearest code-identical ancestor tested the same code.

### 3.7 Test selection — `selected(filter_doc, test)`

A Gradle filter pattern becomes a regular expression by escaping every character and replacing each
`*` with `.*`. A pattern **hits** a test when it fully matches the test's `suite[0]` (the class) or
the test's `name` (`f"{suite[0]}.{method}"`, §3.2). A test is selected when (`includePatterns` is empty or one hits) and
(`commandLineIncludePatterns` is empty or one hits) and no `excludePatterns` entry hits. This is the
subset of Gradle's matching the repo's patterns use: every one starts with `*` (`build.gradle.kts:377-440`).

### 3.8 Diff — `diff(before, after)`

1. Refuse (`NotComparable`) unless both `results.tool.name == TOOL_NAME`. A difference in test set is
   not a refusal; it is handled per test below.
2. `B`, `A` = maps from `testId` to test. `fb`, `fa` = each side's
   `environment.extra.filter`. Executed means status `passed` or `failed`.
3. For each id in both: before `passed`/`skipped` and after `failed` → `NEWLY_FAILING\t{id}\t{message}`;
   before `passed` and after `skipped` → `NEWLY_SKIPPED\t{id}`; before `failed` and after `passed` →
   `NEWLY_PASSING\t{id}`; any other status change → `STATUS\t{before}->{after}\t{id}`; equal
   statuses → nothing.
4. For each id only in `A`: `ADDED\t{status}\t{id}` if `selected(fb, test)`, else
   `OUT_OF_SET\tafter\t{status}\t{id}`. For each id only in `B`: `REMOVED\t{status}\t{id}` if
   `selected(fa, test)`, else `OUT_OF_SET\tbefore\t{status}\t{id}`.
5. `executed(doc, other_filter)` = the number of executed tests in `doc` that `selected(other_filter, …)`
   accepts: the count over the tests both sets run. `eb = executed(before, fa)`, `ea = executed(after, fb)`.
6. Output: the step 3–4 lines sorted, then `EXECUTED\t{eb}\t{ea}`, then `EXECUTED_DROP\t{eb - ea}` when `ea < eb`.
7. Exit 1 if any `NEWLY_FAILING`, `NEWLY_SKIPPED`, `ADDED\tfailed` or `OUT_OF_SET\tafter\tfailed` line
   exists. A test the baseline never ran cannot be shown to be pre-existing, so its failure counts.
   A test that passed and is now skipped is checked no longer, so it counts too; otherwise an
   `@Ignore` would read as no regression whenever another test was added. Else exit 4
   (`EXIT_DROP`) when `ea < eb`. Else 0.

## 4. External Data & Parsing

### 4.1 Sidecar — `build/test-results/test-filter.json` (written by Gradle, §2.1)

`{"commandLineIncludePatterns":[…],"excludePatterns":[…],"includePatterns":[…],"schema":1}`, each
list sorted. `JsonOutput.toJson` writes the map's keys in insertion order; Python reads it with
`json.loads`, so key order is irrelevant. DR-03 shows the CI and builder values.

### 4.2 JUnit XML — `build/test-results/test/TEST-<class>.xml` (written by Gradle)

The fields read: `testcase@classname`, `testcase@name`, `testcase@time` (seconds, decimal), and the
optional children `failure`/`error`/`skipped` with `@message` and text. Everything else
(`testsuite@timestamp`, `@hostname`, `system-out`, `system-err`, `properties`) is ignored, so no
host or time value reaches the document. DR-02 records the shapes measured on the builder.

### 4.3 CTRF document (written and read by `junit_ctrf.py`)

CTRF 0.2.0, the schema MAINT-40 pinned (MAINT-40 design §4.2: tag `v0.2.0`, sha256 `cb9bd075…6cf3`).

```json
{"reportFormat":"CTRF","specVersion":"0.2.0","results":{
 "tool":{"name":"lunar-unit-tests"},
 "summary":{"tests":2,"passed":1,"failed":1,"skipped":0,"pending":0,"other":0,"start":0,"stop":0},
 "environment":{"testEnvironment":"set-<16 hex>","extra":{"filter":{<the sidecar's three lists>}}},
 "tests":[{"name":"p.C.testA","testId":"p.C.testA","status":"passed","duration":12,"suite":["p.C"]},
          {"name":"p.C.testB","testId":"p.C.testB","status":"failed","duration":3,"suite":["p.C"],
           "rawStatus":"failure","message":"expected:<a> but was:<b>"}]}}
```

| Case field | CTRF location |
|---|---|
| `name`, `testId` (§3.2) | `tests[].name` (no ordinal), `tests[].testId` |
| status | `tests[].status`; the XML child tag in `tests[].rawStatus` ("Original status from source tool") when not passed |
| duration | `tests[].duration`, integer milliseconds |
| message | `tests[].message`, present only when non-empty |
| classname | `tests[].suite` = `[classname]` |
| test-set identity (§3.3), sidecar lists | `environment.testEnvironment`, `environment.extra.filter` |

No timestamp, host, commit or stack trace is written. Allowed keys outside `extra` (the TC 2 check):
MAINT-40 design §4.2's list plus `rawStatus` on a test.

### 4.4 `loom-cli`

MAINT-40 design §4.3, with no `--suite` on resolve and put. A resolve without `--suite` sees only the
primary record (MAINT-40 DR-10), so corpus records never answer a unit-test lookup.

## 5. Data Flow

**CI, `main` push.** Unit tests (sentinel on failure) → store: assemble, resolve `HEAD`, put
`a3.ctrf.json` → Report: test and store sentinels decide the verdict.

**A consumer.** `fetch --commit <a commit>` → before document; its own run → `assemble` → `diff` →
verdict by exit code.

## 6. Edge Cases

| Case | Handling |
|---|---|
| Compile fails, no XML | `test` never starts, so there is neither sidecar nor XML; `store` exits 2 on the missing sidecar, sentinel; the job is already red |
| CI rerun of one commit | differing durations: `WARNING … kept the first`, exit 0 (§3.5) |
| A JUnit3 synthetic `test…$lambda$N` | JUnit reports it as a failing `<class>.warning` case whose message names the synthetic (DR-04); several in one class become `warning`, `warning#1`, … ordered by message |
| Baseline from CI's set, branch run on the builder's full set | the classes `-PexcludeExternalFixtureTests` excludes are `OUT_OF_SET after`; a failure among them exits 1 (§3.8 step 7). Once MAINT-49 removes the exclusion, the two sets are equal and no such line appears; neither the tool nor the gate changes |
| A phase that `@Ignore`s a passing test | `NEWLY_SKIPPED`, exit 1 (§3.8 step 7) |
| A `--tests` run on the builder used as `after` | `commandLineIncludePatterns` makes every unmatched baseline test `OUT_OF_SET before`; `EXECUTED` shows the small count |

## 7. Integration Points

No `plugin.xml` registrations. Touch points: `build.gradle.kts` (§2.1), `tooling/ctrf/` (§2.2,
§2.3), `tooling/corpus/corpus_ctrf.py` and its test (§2.4), `tooling/ci/install-loom-cli.sh` (§2.5),
the Gitea `build` and `corpus` jobs (§2.5, §2.6), and
`tooling/corpus/README.md` plus a new `tooling/ctrf/README.md` (usage lines).

## 8. Requirement Coverage

| Requirement | Priority | Implemented by |
|---|---|---|
| MAINT-48-01 | M | §2.1, §4.1 |
| MAINT-48-02 | M | §2.3, §3.1, §3.4, §4.2, §4.3 |
| MAINT-48-03 | M | §3.2 |
| MAINT-48-04 | M | §3.3, §4.3 |
| MAINT-48-05 | M | §2.5, §2.6, §3.5 |
| MAINT-48-06 | M | §3.6 |
| MAINT-48-07 | M | §3.7, §3.8 |
| MAINT-48-08 | M | §2.2, §2.4, §2.5 |

## 9. Alternatives Considered

- **Hard-code the excluded classes in the tool.** A second copy of `build.gradle.kts:399-414`
  that drifts. The sidecar reads the filter that actually ran, including `-PwithPerf`/`-PwithDocker`/`-PwithCorpus`.
- **Infer the set from which XML files exist.** A deleted class and an excluded class look the same.
- **Run the branch with `-PexcludeExternalFixtureTests` to match CI.** Drops the external-fixture classes from
  every builder comparison.
- **A suite (`--suite unit`).** The brief assigns `a3`'s primary record; corpus and MAINT-45 take suites.
- **UniTrack as the baseline source.** It holds the same XML but has no fetch-by-commit contract here
  and is a LAN analytics service every consumer would then depend on.
- **Zero durations for byte-identical reruns.** Loses the timing the requirement asks for; the first
  record is as good a baseline as a rerun's.
- **Fail the `build` job only on `main` when storing fails.** A shell ref test would work, but a PR's
  store failure means the same outage that will break the `main` push; reporting it early is cheaper.

## 10. Premises

- **Check `a3`, primary record, CTRF**: chosen by the owner (requirements Scope); loom fixes `a3` as CTRF (MAINT-40 Gap 2.1).
- **Immutability, keep-first**: fixed by the store (MAINT-40 DR-02).
- **CI excludes the external-fixture classes**: true today (`build.gradle.kts:395-414`). MAINT-49
  (filed on `main`, b94768d4) removes the flag and the filter. Nothing here depends on either
  state: the sidecar records whichever filter ran, so the test-set identity changes at that commit,
  and `diff` handles any difference per test (§3.7, §3.8).
- **The executor's rules** (no `if:`, sentinels, job-level env): fixed by `lunar-ci` (MAINT-40 design §2.8).
- **Python**: chosen; the image carries `python3` and the corpus tooling sets the precedent.
- **No consumer here**: the `supervise-implement` gate that uses `fetch` and `diff` is MAINT-50, a
  separate feature. This one ends when CI stores records and the tools work.
- **A failing store fails the `build` job**: chosen, as in the `corpus` job, so a missing baseline is
  never silent. A loom outage therefore reds every build until it recovers (risks Risk 1.1).

## 11. Open Questions

None.
