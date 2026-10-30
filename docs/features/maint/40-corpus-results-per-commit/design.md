---
id: "MAINT-40-DESIGN"
title: "Technical Design"
type: "design"
parent_id: "MAINT-40"
folders:
  - "[[features/maint/40-corpus-results-per-commit/requirements|requirements]]"
---

# Technical Design: MAINT-40 — Corpus Sweep Results Stored Per Commit

## 1. Architecture Overview

### Current State

`CorpusSweep.run` (`src/test/kotlin/net/internetisalie/lunar/corpus/CorpusSweep.kt:75`) folds each
file into a `FileTally` of integers. It sums those into `CorpusMetrics`, which `CorpusBaseline.render`
writes to `src/test/resources/corpus/<member>.baseline` (`CorpusMetrics.kt:144`). Every location is
discarded except `parseErrorFiles` and the capped `oracleSites`.

### Prior Art in This Repo

| Component | Disposition |
|---|---|
| `CorpusSweep` (`CorpusSweep.kt:28`) | **Extended**: it collects findings, and its counters are derived from them (§2.3). |
| `CorpusMetrics`, `CorpusBaseline` (`CorpusMetrics.kt:10`, `:117`) | **Extended**: a `findings` field that is not rendered. Render, parse and compare are unchanged. |
| `CorpusGuards.assertRatchet` (`CorpusGuards.kt:37`) | **Unchanged**: it remains the gate (§3.8). |
| `CorpusAttribution`, `tooling/corpus/attribute_delta.py` | **Unchanged**: they consume the metrics and tallies. MAINT-40 does not feed them. |
| `tooling/corpus/definition_pins.py` `resolve_libraries` (`:71`) | **Not reused**: the identity uses the pins the sweep *observed* (`CorpusMetrics.libraries`, filled by `CorpusDefinitionLibraries.enabledPins`, `CorpusDefinitionLibraries.kt:121`), not a second prediction of them. |
| `TypeGolden.kt:144` (`MessageDigest` SHA-256), `LuaEnvManifest.kt:38` (`GsonBuilder`) | Idioms followed. |
| CTRF emission elsewhere in Lunar | None found (`grep -rni ctrf src/ tooling/` matches nothing at `72cd1a99`). |

### Target State

Each member's sweep writes `build/corpus-findings/<member>.json` before the unchanged ratchet
asserts (§2.5). `tooling/corpus/corpus_ctrf.py` (§2.7) assembles those files into one CTRF
document, stores it, fetches a stored one, and diffs two.

## 2. Core Components

All Kotlin below lives in the **test** source set, package `net.internetisalie.lunar.corpus`. It
runs inside `CorpusSweep`'s existing per-file calls on the test thread. It adds no thread and no
PSI pass of its own. Nothing is registered in `plugin.xml`.

### 2.1 `CorpusFinding`, `FindingSite`, `CorpusRules` (new file `CorpusFinding.kt`)

```kotlin
data class CorpusFinding(
    val rule: String, val path: String, val startOffset: Int, val endOffset: Int,
    val line: Int, val column: Int, val message: String,
    val symbol: String? = null, val count: Int = 1,
)

data class FindingSite(val path: String, val document: Document?) {
    fun finding(rule: String, range: TextRange, message: String): CorpusFinding
    fun fileFinding(rule: String, message: String, count: Int = 1): CorpusFinding // offsets -1, line/column 0
}

object CorpusRules {
    const val PARSE_ERROR = "corpus.parseError"
    const val INVALID_STATEMENT = "corpus.invalidStatement"
    const val UNRESOLVED_REQUIRE = "corpus.unresolvedRequire"
    const val CRASH = "corpus.crash"
    const val LEXER_ROUND_TRIP = "corpus.lexerRoundTrip"
    const val UNMERGED_TOKENS = "corpus.unmergedTokens"
    const val ORACLE_FALSE_REJECT = "corpus.oracleFalseReject"
    const val ORACLE_FALSE_ACCEPT = "corpus.oracleFalseAccept"
    const val ORACLE_TIMEOUT = "corpus.oracleTimeout"
    const val HIGHLIGHT_FAILURE = "corpus.highlightFailure"
    const val UNATTRIBUTED_HIGHLIGHT = "corpus.unattributedHighlight"
    fun inspection(toolId: String): String = "${CorpusBaseline.INSPECTION_PREFIX}$toolId"
}
```

`FindingSite.finding` computes `line = document?.getLineNumber(range.startOffset)?.plus(1) ?: 0`.
This is the rule `describeParseErrors` uses today (`CorpusSweep.kt:371`). It computes
`column = range.startOffset - document.getLineStartOffset(line - 1) + 1`, or `0` when there is no
document. A symbol is attached by the caller as `site.finding(rule, range, message).copy(symbol = s)`,
which keeps `finding` at three arguments.

### 2.2 `CorpusFindingCounts` (new, `CorpusFindingCounts.kt`, `internal object`)

These are pure functions over `List<CorpusFinding>`. Each one is the single derivation of a
`CorpusMetrics` field (§3.1), and `derive` composes them; TC 2 tests `derive` directly.

```kotlin
fun derive(findings: List<CorpusFinding>): CorpusMetrics // commit "", files 0, requires 0, ballast and libraries empty; every §3.1 field set
fun count(findings: List<CorpusFinding>, rule: String): Int          // Σ count where rule matches
fun inspectionHits(findings: List<CorpusFinding>): Map<String, Int>  // §3.1 steps
fun symbolHits(findings: List<CorpusFinding>): Map<String, Int>      // uncapped "<toolId>.<symbol>", inspection.* findings only
fun crashes(findings: List<CorpusFinding>): Map<String, Int>         // message → n
fun oracleSites(findings: List<CorpusFinding>): List<String>         // moves from CorpusSweep.oracleSites
fun parseErrorFiles(findings: List<CorpusFinding>): List<String>     // "<path>:<line>:<message>", list order
```

`ORACLE_SITES_CAP = 20` (`CorpusSweep.kt:67`), `SYMBOLS_PER_INSPECTION = 10` (`:29`) and `topSymbols`
(`:164`) move here, with their KDoc.

### 2.3 `CorpusSweep` (edited)

- `FileTally` becomes `FileTally(requires: Int, findings: List<CorpusFinding>, oracle: ParseOracle.Verdict?)`.
  It carries the computed getters `parseErrors`, `invalidStatements`, `unresolved`, `roundTripFailed`,
  `unmergedTokens` and `crash`, each derived from `findings`, so `SweptFile.lunarAccepts`
  (`CorpusSweep.kt:57`) is unchanged.
- `tally(psiFile, site)` emits `PARSE_ERROR`, `INVALID_STATEMENT` and `UNRESOLVED_REQUIRE` findings
  (§3.1). `describeParseErrors` is deleted.
- `tallyGuarded(context, psiFile, site)` appends the `CRASH`, `LEXER_ROUND_TRIP` and
  `UNMERGED_TOKENS` findings.
- `SweptFile.findings` = `tally.findings` + its oracle findings (`ORACLE_FALSE_REJECT` when
  `falseReject`, `ORACLE_FALSE_ACCEPT` when `falseAccept`, `ORACLE_TIMEOUT` when
  `oracle is NotJudged`).
- `HitSink` is deleted. `inspectionHits` returns `List<CorpusFinding>`. `accumulateHits` builds each
  file's list locally and returns it, so a file whose highlight throws contributes only its
  `HIGHLIGHT_FAILURE` finding (message `throwable.javaClass.simpleName`).
- `metricsOf` builds `findings = swept.flatMap { it.findings } + inspectionFindings` and returns
  `CorpusFindingCounts.derive(findings).copy(commit = …, files = …, requires = …, ballast = …)` (named arguments), those four computed
  as today; `run` still sets `libraries`.

### 2.4 `CorpusMetrics` (edited)

It gains `val findings: List<CorpusFinding> = emptyList()` as its last parameter, documented as not
rendered (like `allSymbolHits`, `CorpusMetrics.kt:73`). `CorpusBaseline.parse` leaves it empty, so
`BaselineRatchetTest`'s round-trip equalities (`BaselineRatchetTest.kt:64`) still hold.

### 2.5 `CorpusFindingsFile` (new, `CorpusFindingsFile.kt`) and the `LuaCorpusSweepTest` edit

```kotlin
object CorpusFindingsFile {
    val JVM_TOKEN: String = UUID.randomUUID().toString()   // once per test JVM
    fun file(repoRoot: File, name: String): File          // build/corpus-findings/<name>.json
    fun write(repoRoot: File, name: String, metrics: CorpusMetrics)
}
```

`write` serialises §4.1 with `GsonBuilder().disableHtmlEscaping().create()` and creates the parent
directory. In `LuaCorpusSweepTest.sweepAndRatchet` (`LuaCorpusSweepTest.kt:146`), on the
non-attribution path, call `CorpusFindingsFile.write(repoRoot, name, observed)` **before** the
record/ratchet branch. A regressing commit then still leaves its findings behind. Attribution
arms do not write.

### 2.6 `build.gradle.kts` (edited)

In the `test` task block, next to the JFR `doFirst` idiom (`build.gradle.kts:353-355`):

```kotlin
if (project.hasProperty("withCorpus")) {
    val corpusFindingsDir = layout.buildDirectory.dir("corpus-findings").get().asFile
    doFirst { corpusFindingsDir.deleteRecursively() }
}
```

The directory then holds exactly one invocation's files. This is what lets §3.2 read the shape
from it.

### 2.7 `tooling/corpus/corpus_ctrf.py` (new) and `tooling/corpus/test_corpus_ctrf.py` (new)

The script uses the stdlib only. Constants: `CHECK = "a3"`, `TOOL_NAME = "lunar-corpus-sweep"`,
`SPEC_VERSION = "0.2.0"`,
`COMPARABILITY_KEYS = ("commit", "roots", "luaLevel", "moduleRoot", "target", "definitionLibraries")`,
`SCHEMA = 1` (the findings file, §4.1), `DEFAULT_MAX_DEPTH = 50`, and `SUITE_ARGV = ("--suite", "corpus")`,
the arguments that select suite `corpus` on resolve and put (loom-cli v0.90.0, DR-10). `get` takes
the key resolve returns and no suite. `EXIT_UNMEASURED` and `LOOM_CLI` live in
`tooling/ctrf/loom_store.py` (MAINT-48 design §2.2).

| Subcommand | Arguments | Exit |
|---|---|---|
| `assemble` | `--findings DIR --manifest PATH --out PATH` | 0; 2 on `NotComparable` |
| `store` | `--findings DIR --manifest PATH --out PATH [--repo .]` | §3.5 |
| `fetch` | `--commit C --out PATH [--like DOC] [--max-depth N] [--repo .]` | §3.6 |
| `diff` | `BEFORE AFTER` | §3.7 |

Functions: `load_runs(dir) -> list[dict]` (sorted by `member`, refusing `schema != 1`),
`identity_of(runs, manifest) -> str`, `fingerprints(member, findings) -> list[tuple[str, dict]]`,
`ctrf_document(runs, manifest) -> bytes`, `store(args) -> int`, `fetch(args) -> int`,
`diff(before, after) -> tuple[list[str], int]`, and `main(argv) -> int`. The `loom-cli` calls
(`loom`, `lookup`, the store-once and first-parent fetch walks) and `LoomError` live in
`tooling/ctrf/loom_store.py` (MAINT-48 design §2.2). Exceptions: `NotComparable` and `LoomError`
(`main` prints either; it returns 2 and 1 respectively). Tests run as
`cd tooling/corpus && python3 -m unittest test_corpus_ctrf`, the precedent of
`test_attribute_delta.py`.

### 2.8 CI: the `corpus` job (`.gitea/workflows/build-plugin.yml:323`)

- **`loom-cli`, unpinned, fetched per job** (Phase 0), the fleet's own install step copied from
  `loom/.gitea/workflows/docs-lint.yml` ("install loom-cli"). With `LOOM_CLI_VERSION` unset, resolve
  the tag from `GET /api/v1/repos/loom/loom-cli/releases/latest`; set, it is an override for a
  release that breaks us, not a standing pin. Download
  `…/releases/download/<tag>/loom-cli-linux-amd64.tar.gz` with `Authorization: token $LOOM_MODULES_TOKEN`
  (no release asset on this forge is anonymously fetchable), prove it is an archive with `tar tzf`
  (a sign-in page arrives as HTTP 200), and install `loom-cli` to `/usr/local/bin` (pods run as
  root), so later steps find it on `PATH`. `curl` is already in the image. The step is dash-safe:
  no `pipefail`, every guard is an explicit `if … exit 1` naming what failed, and the token arrives
  as plain env, not `${{ secrets }}`. `LOOM_CLI_VERSION`, when set, arrives as a repo variable.
- **Verify toolchain** step: run the fetcher first, then prefix the final compound with
  `loom-cli --version &&`, so the log records which version ran.
- **Corpus ratchet** step (Phase 4): replace `&& exit 0 … exit 1` with
  `if ./gradlew …same args…; then echo "ratchet passed"; else <existing diagnostics>; touch /tmp/lunar-corpus-failed; fi`.
  The store step therefore also runs on a red ratchet.
- **New step "Store findings (loom conformance)"** (Phase 4 only):
  `python3 tooling/corpus/corpus_ctrf.py store --findings build/corpus-findings --manifest tooling/corpus/corpus.json --out build/corpus-ctrf/corpus.ctrf.json || touch /tmp/lunar-corpus-store-failed`
- **New final step "Report corpus result"** (Phase 4): emits `::error::` for each sentinel present, then
  `test ! -f /tmp/lunar-corpus-failed && test ! -f /tmp/lunar-corpus-store-failed && echo "corpus passed"`.
  Its last command is the verdict (`build-plugin.yml:351-354`, the "last command" note).
- Credentials arrive as plain env (`build-plugin.yml:127-128`), so the workflow names none.
- `.github/workflows/build-plugin.yml` is untouched: it has no `corpus` job (`:20`).

## 3. Algorithms

### 3.1 Emission and derivation

| Rule | Emitted at | Range | Message | Symbol / count |
|---|---|---|---|---|
| `PARSE_ERROR` | `tally`, each `PsiErrorElement` in `findChildrenOfType` order | `error.textRange` | `error.errorDescription` | — |
| `INVALID_STATEMENT` | `tally`, each of `LuaSyntaxDiagnostics.invalidStatements(psiFile)` | `statement.textRange` | `"invalid statement"` | — |
| `UNRESOLVED_REQUIRE` | `tally`, each `LuaRequireReference` with `resolve() == null` | `reference.absoluteRange` | `reference.canonicalText` | symbol = `canonicalText` (a) |
| `CRASH` | `tallyGuarded`, `crash != null` | file | the `lex:`/`parse:` string | — |
| `LEXER_ROUND_TRIP` | `tallyGuarded`, `lex.roundTripFailed` | file | `"token texts do not reconstitute the source"` | — |
| `UNMERGED_TOKENS` | `tallyGuarded`, `lex.unmergedTokens > 0` | file | `"internal long-bracket tokens escaped the merge"` | count = `lex.unmergedTokens` |
| `ORACLE_*` | `SweptFile.findings` (§2.3) | file | `"luac accepts, Lunar rejects"` / `"Lunar accepts, luac rejects"` / `"oracle did not judge the file"` | — |
| `inspection(toolId)` | `accumulateHits`, `info.inspectionToolId != null` | `TextRange(info.startOffset, info.endOffset)` | `info.description.orEmpty()` | symbol = `QUOTED_SYMBOL` group 1, else null |
| `UNATTRIBUTED_HIGHLIGHT` | `accumulateHits`, id null | as above | as above | — |
| `HIGHLIGHT_FAILURE` | `inspectionHits` catch | file | throwable simple name | — |

(a) `LuaRequireReference` does not override `getCanonicalText`, so it is `PsiReferenceBase.getValue()`
(`PsiReferenceBase.java:67-71`, `:98-99`): the range text, which spans the whole literal with its
delimiters (`LuaRequireReferenceContributor.kt:45-49`), e.g. `"socket"`.

The `accumulateHits` site is `FindingSite(entry.path, fixture.editor.document)`. The `tally` site is
`FindingSite(path, psiFile.viewProvider.document)`.

**Derivation** (`derive`): `parseErrors = count(PARSE_ERROR)`,
`unresolvedRequires = count(UNRESOLVED_REQUIRE)`, `oracleDisagreements = count(ORACLE_FALSE_REJECT)`,
`oracleFalseAccepts = count(ORACLE_FALSE_ACCEPT)`, `oracleTimeouts = count(ORACLE_TIMEOUT)`,
`lexerRoundTripFailures = count(LEXER_ROUND_TRIP)`, and `unmergedTokens = count(UNMERGED_TOKENS)`
(Σ `count`), `inspectionHits`, `symbolHits = topSymbols(symbolHits(findings))`,
`allSymbolHits = symbolHits(findings).toSortedMap()`, `crashes`, `oracleSites`, `parseErrorFiles`,
and `findings`.

`inspectionHits(findings)`, with `P = CorpusBaseline.INSPECTION_PREFIX`:
1. For each finding whose rule starts with `P`, add `count` to key `rule.removePrefix(P)`.
2. `h = count(HIGHLIGHT_FAILURE)`. If `h > 0`, set `HIGHLIGHT_FAILURES = h`.
3. Group the findings by `path`. For each group, `u = count(UNATTRIBUTED_HIGHLIGHT) - count(PARSE_ERROR)`.
   `U = Σ max(0, u)`. If `U > 0`, set `UNATTRIBUTED = U`. This is `CorpusSweep.kt:293-296` per file.

`parseErrorFiles`: the `PARSE_ERROR` findings in list order, mapped to `"${path}:${line}:${message}"`.
The list order is `swept` (sorted by path) × PSI order, which is the order today. `oracleSites` uses
the rule today's `CorpusSweep.kt:133-137` applies, taking paths from the `ORACLE_FALSE_*` findings.
`crashes`: `CRASH` findings grouped by `message`, counted. `symbolHits`: for each finding whose rule
starts with `P` and whose `symbol` is non-null, add `count` to `"${rule.removePrefix(P)}.$symbol"`.

### 3.2 Environment identity — `identity_of(runs, manifest)`

1. If the runs' `jvmToken`s are not all one value: raise `NotComparable("runs come from more than one JVM")`.
2. `names` = the manifest's `corpora[*].name`. If the set of `members == set(names)`, the shape is
   `full`. If there is exactly one run, the shape is `member`. Otherwise raise
   `NotComparable("partial run set: …")`.
3. For each run in `member` order: `entry` = the manifest entry with that name (missing → raise).
   `line = json.dumps({"member": m, "entry": {k: entry.get(k) for k in COMPARABILITY_KEYS}, "libraries": run["libraries"]}, sort_keys=True, separators=(",", ":"))`.
4. `digest = sha256("\n".join(lines).encode()).hexdigest()[:16]`.
5. Return `f"full-{digest}"`, or `f"member-{name}-{digest}"`.

A full shape hashes every member together because cross-member JVM state shifts counts
(`LuaCorpusSweepTest.kt:29-33`, BUG-418). `url`, `prune` and `$comment` are excluded, so editing
them does not split an identity. The identity is written to `results.environment.testEnvironment`
(§4.2). The store's suite is the fixed name `corpus` and is not derived from it.

### 3.3 Fingerprint — `fingerprints(member, findings)`

Returns `list[tuple[str, dict]]`, fingerprint and finding.
`key = "\0".join([member, rule, path, str(startOffset), str(endOffset), symbol or ""])`.
Sort the findings by `(rule, path, startOffset, endOffset, symbol or "", message, line, column, count)`;
`ordinal` = the number of findings before it in that order with the same `key`. The fingerprint is
`sha256(f"{key}\0{ordinal}".encode()).hexdigest()`. It is unique within a document, because the
member is in the key and the ordinal separates findings that share one.

**Property:** the (fingerprint, finding) pairs depend only on the multiset of findings, never on
list order. Findings tied on every sort field are identical, so which one takes which ordinal
cannot change a byte. TC 5's fifth run tests this.

The message is excluded from the key, so rewording an inspection does not change the fingerprint
set; it can only reorder same-key findings among their ordinals. Offsets are stable
because the corpus tree is pinned, and the pin is in the identity (§3.2).

### 3.4 Document assembly — `ctrf_document`

1. `runs = load_runs(dir)`, then `identity = identity_of(runs, manifest)`.
2. For every pair `fingerprints` returns, build the §4.2 test. Sort all tests by
   `(member, path, startOffset, endOffset, rule, symbol or "", testId)`, read from the finding
   (offsets are −1 on a file-level finding, which `extra` omits).
3. `summary` = `{"tests": n, "failed": n, "passed": 0, "skipped": 0, "pending": 0, "other": 0, "start": 0, "stop": 0}`,
   where `n` is the number of tests.
4. Return `json.dumps(doc, sort_keys=True, separators=(",", ":"), ensure_ascii=False).encode() + b"\n"`.

### 3.5 Store — `store(args)`

1. Assemble to `--out`. On `NotComparable`, print it and return 2. If the identity does not start
   with `full-`, print `"refusing to store a <shape> run: suite corpus holds full runs only"` and
   return 2, before any `loom-cli` call (MAINT-40-10).
2. `record = lookup("HEAD", repo)`, which runs
   `[LOOM_CLI, "artifact", "resolve", "--commit", sha, "--check", CHECK, "--repo", repo, "--json", *SUITE_ARGV]`.
   - Exit 3: go to step 3.
   - Exit 0: `record = checks[0]["record"]`. If `record["loom-sha256"]` equals
     `sha256(out bytes).hexdigest()`, print `"store: already stored, identical"` and return 0.
     Otherwise print `"WARNING store: HEAD already has a different a3 corpus record; kept the first"`
     and return 0.
   - Any other exit: print the output and return 1.
3. Run `[LOOM_CLI, "artifact", "put", "conformance", OUT, "--commit", "HEAD", "--check", CHECK, "--no-item", "--repo", repo, *SUITE_ARGV]`.
   `OUT` is positional: put has no `--file` (loom-cli v0.90.0 `put conformance --help`). On exit 0,
   print `store: <its stdout>` and return 0. Otherwise print `store: put exited <n>: <output>` to
   stderr and return 1.

### 3.6 Fetch — `fetch(args)`

1. `shas = git -C REPO rev-list --first-parent --max-count=N C` (N defaults to `DEFAULT_MAX_DEPTH`).
2. `want` = `results.environment.testEnvironment` of `--like`'s document, or None.
3. For `distance, sha` in `enumerate(shas)`: resolve as in §3.5 step 2. Exit 3 → continue; another
   non-zero exit → return 1.
   - `[LOOM_CLI, "artifact", "get", record["key"], "--output", f"{out}.part"]`, after deleting any
     existing `.part`. A non-zero exit: print its output, delete `.part`, return 1.
   - If the fetched `results.tool.name != TOOL_NAME`, or `want` is set and differs from its
     `testEnvironment`: delete `.part` and continue.
   - Rename `.part` to `out`, print `f"fetched {sha} identity={identity} distance={distance}"`, return 0.
4. Print `f"no stored result within {N} first-parent ancestors of {C}"` and return 3.

The tool-name check keeps a foreign `a3` CTRF record from being read as a corpus result.

### 3.7 Diff — `diff(before, after)`

1. Raise `NotComparable` when either document's `results.tool.name != TOOL_NAME`, when the
   `testEnvironment`s differ, when the `environment.extra.members` key sets differ, or when any
   member's `files` or `requires` differ (mirroring `CorpusGuards.assertIdentity`,
   `CorpusGuards.kt:58`). `main` prints `NOT COMPARABLE: <reason>` to stderr and returns 2. A
   refusal is never reported as a regression.
2. Per member (`suite[0]`): `B`, `A` = maps from `testId` to test. For each id in `A - B` (a newly
   failing test), emit `f"{m}\tNEW\t{ruleId}\t{uri}:{line}:{column}\t{message}"` from `extra`,
   using `0` for an absent line/column. For `B - A` (an absent test), do the same with `RESOLVED`.
   For each `ruleId` whose `Σ extra.count` differs, emit `f"{m}\tCOUNT\t{ruleId}\t{before}\t{after}"`.
3. Print the lines sorted. Return 1 if any `NEW` line was printed, else 0.

### 3.8 Reference decision (MAINT-40-09)

**The committed baselines stay the gate's reference.** Stored results are the attributable "before".
The reasons:

1. The gate must judge a PR before it merges, and also runs on the gce-builder and offline. Neither
   of those has store credentials, and a stored "before" may lie several commits back (§3.6).
2. Per-inspection counts are non-reproducible whenever `highlightFailures > 0`
   (`CorpusMetrics.kt:274-283`). A finding-level gate would be flaky exactly there.
3. An accepted movement stays a reviewable one-line diff, so MAINT-45's open question about
   accepting a deliberate increase never arises here.

The gap "a green gate does not prove the baselines are current" is closed without changing the gate.
Each `main` commit's findings are stored as observed, so a "before" no longer depends on a baseline
being current.

## 4. External Data & Parsing

### 4.1 Findings file — `build/corpus-findings/<member>.json` (written by Kotlin, read by Python)

```json
{"schema":1,"member":"luacheck","jvmToken":"<uuid>","files":132,"requires":154,
 "libraries":{"busted":"5ed85d0e…"},
 "findings":[{"rule":"corpus.parseError","path":"spec/x.lua","startOffset":812,"endOffset":812,
              "line":31,"column":7,"message":"<expr> expected","count":1}]}
```

The DTO is `CorpusFindingsFile.Payload(schema: Int, member: String, jvmToken: String, files: Int,
requires: Int, libraries: Map<String, String>, findings: List<CorpusFinding>)`, a data class in
`CorpusFindingsFile.kt`, written with `writeText(json, Charsets.UTF_8)`. Python opens it with
`encoding="utf-8"`. `symbol` is absent when null (Gson's default), read with `f.get("symbol")`.

### 4.2 CTRF document (written and read by `corpus_ctrf.py`)

The format is CTRF 0.2.0: `ctrf-io/ctrf` tag `v0.2.0` (`e325eb33c766`), `schema/ctrf.schema.json`,
sha256 `cb9bd075beb0e046076046793f96bb1ba72bd1b1d331b1fbd42ac0ea36776cf3`. DR-09 validates this shape
against it.

```json
{"reportFormat":"CTRF","specVersion":"0.2.0","results":{
 "tool":{"name":"lunar-corpus-sweep"},
 "summary":{"tests":1,"passed":0,"failed":1,"skipped":0,"pending":0,"other":0,"start":0,"stop":0},
 "environment":{"testEnvironment":"full-<16 hex>","extra":{"shape":"full","members":{
   "luacheck":{"files":132,"requires":154,"libraries":{…},"comparability":{<COMPARABILITY_KEYS>}}}}},
 "tests":[{"name":"luacheck corpus.parseError spec/x.lua <64 hex>","testId":"<64 hex>",
   "status":"failed","duration":0,"suite":["luacheck"],"message":"<expr> expected",
   "extra":{"ruleId":"corpus.parseError","uri":"spec/x.lua","startOffset":812,"endOffset":812,
            "line":31,"column":7,"message":"<expr> expected","count":1}}]}}
```

| Finding / run field | CTRF location | Schema basis |
|---|---|---|
| fingerprint (§3.3) | `tests[].testId` | "Stable identifier for the logical test case … derived from stable test attributes" |
| member, rule, path, fingerprint | `tests[].name` = `f"{member} {rule} {path} {fingerprint}"` | `name` is required |
| — | `tests[].status` = `"failed"`, `tests[].duration` = `0` | both required; a finding has no duration |
| member | `tests[].suite` = `[member]` | "Suite hierarchy", `minItems: 1` |
| message | `tests[].message` and `extra.message` | `message`: "Error or failure message" |
| rule, path, offsets, line, column, symbol, count | `tests[].extra` keys `ruleId`, `uri`, `startOffset`, `endOffset`, `line`, `column`, `symbol`, `count` | `extra`: "Extension point for arbitrary metadata" |
| identity (§3.2) | `results.environment.testEnvironment` | "Logical test environment"; `environment` has `additionalProperties: false`, so no other key is available |
| shape, per-member `files`, `requires`, `libraries`, comparability fields | `results.environment.extra` | `environment.extra` is the extension point |

- `uri` is the member-root-relative path exactly as in the findings file.
- `startOffset`/`endOffset` are omitted when `startOffset < 0`. `line` and `column` are omitted when
  `line == 0`. `symbol` is present only when non-null. `count` is always present.
- An empty `message` becomes the `ruleId`, in both `tests[].message` and `extra.message`.
- `summary.start` and `summary.stop` are required epoch-millisecond integers and are written as `0`.
  No timestamp, commit, `reportId` or token appears anywhere, so the same findings give the same
  bytes; a rerun's put of differing bytes is an immutable conflict (DR-02).
- **A test count is not coverage.** `summary.tests` counts findings, so a clean member contributes
  zero tests. What ran is `environment.extra.members` (its keys) and each member's `files`.
- **Allowed keys** (the TC 4 check, taken from the pinned schema; `extra` objects are free-form): top level `reportFormat`,
  `specVersion`, `results`; `results`: `tool`, `summary`, `tests`, `environment`; `tool`: `name`;
  `summary`: the keys of §3.4 step 3; `environment`: `testEnvironment`, `extra`; a test: `name`,
  `testId`, `status`, `duration`, `suite`, `message`, `extra`.

### 4.3 `loom-cli` (consumed by `store` and `fetch`)

The fields come from loom-cli's source (`cmd/loom-cli/artifact_resolve.go:23-31`, loom-delivery
`ListResult`). DR-03 executes them. Every resolve and put also carries `SUITE_ARGV` (§2.7); DR-10
executes them with it on loom-cli v0.90.0.

- **resolve**: stdout `{"commit": "<sha>", "merge-base-of": …, "checks": [{"check": "a3", "status": "measured"|"unmeasured", "record": {"key": "…", "loom-sha256": "<hex>", …} | null}]}`.
  Exit 0 means measured, exit 3 unmeasured, any other exit a failure.
- **get**: `artifact get <key> --output <new path>`. Exit 0 means the file is written and
  checksum-verified. It takes the key that resolve returned, so it needs no suite argument.
- **put**: `artifact put conformance <path> --commit … --check … --no-item --repo … --suite corpus`.
  Exit 0 means stored or already present (DR-02). Any other exit is a failure.
- **Test stub** (`tooling/ctrf/loom_stub.py`): an executable written to a temp dir and selected through
  `LOOM_CLI`. It reads the JSON scenario named by env `STUB_LOOM`:
  `{"resolve": {"<commitish>": {"exit": 3} | {"exit": 0, "json": {…}}}, "get": {"<key>": "<source path>"}, "put": {"exit": 0}}`.
  It appends each argv as one JSON line to `<STUB_LOOM>.calls`, copies `get` sources to `--output`,
  and exits with the scenario's code. A resolve of a commit-ish absent from `resolve` exits 3; a get
  of a key absent from `get` exits 1.

## 5. Data Flow

**Engineer, "before" for a change.** `corpus_ctrf.py fetch --commit origin/main --out before.ctrf.json`.
Run the sweep locally, copy `build/corpus-findings/` back, then `assemble` to `after.ctrf.json`.
`diff before.ctrf.json after.ctrf.json` prints the finding-level delta, and a different identity
prints `NOT COMPARABLE`.

## 6. Edge Cases

| Case | Handling |
|---|---|
| A sweep fails before writing (corpus not fetched) | the run set is partial: `store` returns 2 and the job is red, as the ratchet already is |
| `main` push and tag push of one commit run concurrently | both resolve unmeasured. The second put gets already-present (identical) or fails (Risk 1.1) |
| A PR run stores its head | harmless: squash-merged PR heads are never first-parent ancestors on `main` |
| Gradle `maxParallelForks` > 1 splits members across JVMs | §3.2 step 1 refuses |

## 7. Integration Points

There are no `plugin.xml` registrations: all of this is test-source and tooling. The touch points
are `build.gradle.kts` (§2.6), the Gitea `corpus` job (§2.8), and the Gitea org secrets and
variables (requirements → Dependencies).

## 8. Requirement Coverage

| Requirement | Priority | Implemented by |
|---|---|---|
| MAINT-40-01 | M | §2.1, §2.3, §2.5, §2.6, §3.1, §4.1 |
| MAINT-40-02 | M | §2.2, §2.3, §2.4, §3.1 |
| MAINT-40-03 | M | §2.7, §3.4, §4.2 |
| MAINT-40-04 | M | §3.3, §4.2 |
| MAINT-40-05 | M | §3.2, §4.2 |
| MAINT-40-06 | M | §2.8, §3.5, §4.3 |
| MAINT-40-07 | M | §3.6, §4.3 |
| MAINT-40-08 | M | §3.7 |
| MAINT-40-09 | M | §3.8 |
| MAINT-40-10 | C | §3.5 step 1 (the refusal only) |

## 9. Alternatives Considered

- **Identity as `--check`.** Refused by the store: `--check` is a closed set (DR-01).
- **Shape from Gradle's `--tests` pattern.** `TestFilter` exposes no command-line patterns (DR-08).
  The run set in the cleaned directory is what actually ran.
- **Kotlin writes CTRF directly.** That would split fingerprint and identity logic across two
  languages. Kotlin writes plain findings, and one Python file owns every CTRF fact.
- **SARIF under `a4` or `a6`.** Those checks describe the commit's own code, and loom fixes each
  check's format (risks Gap 2.1), so the corpus record under `a3` is CTRF.
- **The identity as the store suite.** One `corpus` key per commit makes "the record for this
  commit" a single lookup; comparability is checked inside the document instead (§3.7).
- **Store `main` pushes only.** The step would need a ref test that this executor cannot evaluate
  (`if:` and `${{ }}` are unavailable). Storing every run costs one object and pollutes no ancestor walk.

## 10. Premises

- **Check `a3`, format CTRF, suite `corpus`**: fixed by the owner and by loom's per-check format map (risks Gap 2.1).
- **A finding is a failed test**: chosen by the owner. Generic `a3` readers therefore see
  `summary.failed > 0` on a healthy commit; the count is findings, not breakage (§4.2).
- **One document per commit+check+suite**: fixed by the store's key (LCL-241's
  `<commit>/conformance/<check>[.<suite>].<format>.json`). Hence one document for all members, and
  the refusal of member-alone runs.
- **Immutability**: fixed. The design never overwrites, and keeps the first record.
- **Committed baselines**: kept by choice (§3.8), not inherited.
- **Python for tooling**: chosen. The CI image carries `python3` (`Dockerfile:59-61`), and the
  attribution tooling sets the precedent.

## 11. Open Questions

None.
