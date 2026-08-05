---
id: "MAINT-35-DESIGN"
title: "Technical Design"
type: "design"
parent_id: "MAINT-35"
folders:
  - "[[features/maint/35-parser-torture-testing/requirements|requirements]]"
---

# Technical Design: MAINT-35 — Lexer/Parser Torture Testing

## 1. Architecture

Two new test-only objects and five new metrics on the existing MAINT-33 pipeline. No production
code changes; **no `plugin.xml` registration, extension point, service or index is added or
changed** by this feature.

```
CorpusSweep.run(fixture, entry, checkoutDir)
   └─ per file: tally(psiFile, entry.luaLevel)          ← §4.1
        ├─ (existing) parseErrors / requires / unresolvedRequires   ─ wrapped in runCatching{Throwable}
        ├─ NEW LexerInvariants.check(psiFile.text)      → roundTripFailed, lex crash
        └─ NEW ParseOracle.judge(psiFile.text, level)   → Verdict          (off-EDT, §2.2a)
             │
             └─► FileTally(+3) ─► run() aggregates, caps oracleSites ─► CorpusMetrics (5 new fields)
                                                └─► CorpusBaseline.render/parse/compare  (§4.3, §4.4)
                                                       └─► CorpusGuards.assertRatchet
```

## 2. `ParseOracle` (MAINT-35-01, -03)

**File**: `src/test/kotlin/net/internetisalie/lunar/corpus/ParseOracle.kt`

```kotlin
internal object ParseOracle {

    sealed interface Verdict {
        object Accept : Verdict
        data class Reject(val message: String) : Verdict
        /** Only ever a per-file TIMEOUT — never "no binary", which fails fast instead. */
        data class NotJudged(val reason: String) : Verdict
    }

    fun judge(source: CharSequence, level: LuaLanguageLevel): Verdict

    /** The pinned `test/luac/<version>/luac` for [level]. Throws with the remedy (§2.0). */
    fun requireBinary(level: LuaLanguageLevel): File
}
```

### 2.0 The oracle is built from pinned source — there is no apt path

**One source, no fallbacks**: `luac` is compiled from an official PUC Lua tarball, pinned by version
and **sha256**, cached under the out-of-repo `test/luac/<version>/luac`. Exactly like the corpus
itself is pinned by commit — same discipline, same reason.

- **`tooling/corpus/luac.tsv`** — `luaLevel<TAB>version<TAB>url<TAB>sha256`, e.g.
  `LUA51<TAB>5.1.5<TAB>https://www.lua.org/ftp/lua-5.1.5.tar.gz<TAB><sha256>`.
- **`tooling/corpus/fetch-luac.sh`** — downloads, **refuses on checksum mismatch**, builds, copies
  `src/luac` to `test/luac/<version>/luac`, writes a `.luac-sha` stamp so re-runs are a no-op.
  Mirrors `fetch-corpus.sh` exactly.
- `requireBinary(level)` resolves **one** path: `test/luac/<pinned version>/luac`. No `PATH` search,
  no candidate list, no system binary — ever.

#### Why not apt

An earlier draft installed `lua5.1`–`lua5.4` via apt and searched `PATH` for `luac5.1`. That is a
heavy assumption dressed up as a simple one:

- **Unpinned inside a ratchet.** apt gives whatever the distro release carries — today `5.1.5-11`,
  `5.2.4-3+b3`, `5.3.6-2+b4`, `5.4.7-1+b2`. A distro bump silently changes the oracle, and therefore
  the verdicts a *ratchet* treats as ground truth. MAINT-33 pins corpus checkouts to commit SHAs for
  exactly this reason; pinning the corpus while floating the judge is incoherent.
- **Distro-coupled.** Debian-only; the `luac5.1` binary-name convention is Debian's, not upstream's.
- **Not upstream.** Debian's builds are patched (`-11`, `+b3`, `+b2` are Debian revisions). Ground
  truth should be unmodified PUC Lua.
- **Two machines, two oracles.** The worst property: with a `PATH` search a baseline recorded where
  apt supplied `5.4.7-1+b2` could be validated where a source build supplied `5.4.7`, and the gate
  would look uniform while judging by different rules. Probing for an installed binary *before*
  building our own is therefore not a harmless optimisation — it reintroduces precisely the
  non-determinism the pinning exists to remove.

The one thing still taken from the system is a **C compiler** (`build-essential`), which
`builder-bootstrap.sh`, `startup-script.sh` and `.gitea/workflows/build-plugin.yml` must install —
none does today, and `gcc`'s presence on the builder is as accidental as `luac5.1`'s was. A compiler
is a different class of dependency: it does not decide the oracle's verdicts.

#### Why not our own provisioner either

`PucLuaBuildRecipe.kt:115-126` already compiles `luac.c` and installs `bin/luac`, and using it would
dogfood TOOLING-04. It is deliberately **not** used: a judge must not share failure modes with the
thing it judges. If provisioning broke, the oracle would break with it, and a red gate could not
distinguish "the parser regressed" from "the provisioner did". `fetch-luac.sh` is short and
independent. Dogfooding the provisioner remains worth doing — as its own test, not as ground truth
for this one.

#### Fail-fast

`requireBinary` **throws** when the pinned binary is absent, before any file is judged:

```
No luac for LUA51 at test/luac/5.1.5/luac.
The parse oracle is built from pinned source, not installed from a package.
Run:  tooling/corpus/fetch-luac.sh
```

`LUA55` needs no special case: lua.org publishes 5.5 tarballs, so it is one more `luac.tsv` row.

### 2.1 Version matching is the whole correctness of the oracle

`luac5.4` accepts `local a = 1 // 2`; `luac5.1` rejects it — **verified on the builder, both
directions**. Judging a `LUA51` corpus with the wrong version invents disagreements (or hides them).
With §2.0's pinning the mapping is a table lookup in `luac.tsv`, not a `PATH` search:

| `LuaLanguageLevel` | pinned build |
| :-- | :-- |
| `LUA50` | *(none)* — `requireBinary` throws. `LuaLanguageLevel` declares `LUA50` (`lang/LuaLanguageLevel.kt:20`), so the exhaustive `when` must cover it; PUC 5.0 is not a supported target. |
| `LUA51` | `test/luac/5.1.5/luac` |
| `LUA52` | `test/luac/5.2.4/luac` |
| `LUA53` | `test/luac/5.3.6/luac` |
| `LUA54` | `test/luac/5.4.7/luac` |
| `LUA55` | `test/luac/5.5.x/luac` — same mechanism |

`requireBinary` resolves via an **exhaustive** `when` over all six constants — no `else` — so a new
level fails to compile rather than silently resolving to nothing. Exact point versions and their
sha256s are recorded in `luac.tsv` when Phase 1 lands (DR-03).

### 2.2 Invocation

`luac -p -` — parse-only, reading the source from **stdin**. Exit 0 = Accept; non-zero =
`Reject(stderr)`.

Stdin, not a temp file. An earlier draft claimed "`luac` reads paths, not stdin, on 5.1"; that is
**false** — verified on the builder, `printf 'local a = 1\n' | luac5.1 -p -` and the same under
`luac5.4` both exit 0. `luac.c` maps the filename `-` to `luaL_loadfile(L, NULL)` in both. Using
stdin removes temp-file creation, cleanup and path leakage into `stderr` (diagnostics read
`stdin:1:` instead of an absolute path, which also keeps the baseline machine-independent).

Timeout: 10 s per invocation, `destroyForcibly()` on expiry, counted as
`NotJudged("timeout")` — never as `Reject`, because a hung oracle is not evidence about the input.

### 2.2a Threading — stated, because it is not obvious

`LuaCorpusSweepTest` extends `BasePlatformTestCase`, whose `runInDispatchThread()` defaults to
`true`, so **the sweep body runs on the EDT**. Spawning 419 processes synchronously there would put
blocking I/O on the EDT, which the engineering contract forbids.

Therefore every `ParseOracle.judge` call is made **off the EDT**, using the repo's existing helper
shape — `ApplicationManager.getApplication().executeOnPooledThread<T> { … }.get(timeout, SECONDS)`,
exactly as `LuaToolExecutionServiceTest.kt:21-22` does for the same reason. `ParseOracle.judge`
asserts it is not on the EDT and fails loudly if it is.

This supersedes an earlier draft of this section, which used `ProcessBuilder` *because* it evades
`ThreadingAssertions.softAssertBackgroundThread()`. That reasoning was backwards: the assertion was
detecting a real contract violation, and the fix is to move the work off the EDT, not to pick a
primitive that does not check. `ProcessBuilder` is still what runs the command — the production
`LuaToolExecutionService` is deliberately **not** reused, because it is an `@Service(APP)` whose
timeout/cancellation semantics are tuned for user-facing runs; but that is now a preference, not a
workaround.

### 2.3 Comparison rule (MAINT-35-02)

For each swept file, with `lunarAccepts = (parseErrors == 0)`:

| oracle | lunar | outcome |
| :-- | :-- | :-- |
| Accept | accepts | agree |
| Reject | rejects | agree |
| Accept | rejects | **disagreement — false reject** (Lunar rejects valid Lua; BUG-392's class) |
| Reject | accepts | **disagreement — false accept** (Lunar admits invalid Lua) |
| `NotJudged` (timeout) | — | not counted as agreement or disagreement; increments the diagnostic `oracleTimeouts` |

There is no "oracle absent" row: a missing binary fails the sweep before it starts (§2.0). The only
`NotJudged` cause is a per-file timeout, which is diagnostic — a timing-out oracle is judging
nothing, so `oracleTimeouts` being non-zero is reported loudly, but it never disables the gate.

Both directions count toward `oracleDisagreements`. They are recorded separately in the diagnostic
list (`falseReject:<path>` / `falseAccept:<path>`) because they have different severities: a false
reject is a visible red squiggle on good code, a false accept is silent under-reporting.

## 3. `LexerInvariants` (MAINT-35-04, -05)

**File**: `src/test/kotlin/net/internetisalie/lunar/corpus/LexerInvariants.kt`

```kotlin
internal object LexerInvariants {

    /** [crash] is the throwable's class name, or null when the lex completed. */
    data class Result(val roundTripFailed: Boolean, val crash: String?)

    /** Lexes [source] with the production [LuaLexer] stack. Never throws. */
    fun check(source: CharSequence): Result
}
```

**Scope: lexing only.** An earlier draft had this same signature also perform a parse via
`PsiFileFactory`, which is unimplementable — `PsiFileFactory.getInstance(project)` needs a `Project`
and the signature has none. The parse half of MAINT-35-05 is therefore **not** here; it is captured
in `CorpusSweep`, which already holds a parsed `PsiFile` (§4.1). One `crash` slot is correct for one
crash site.

### 3.1 Round-trip

```kotlin
val lexer = LuaLexer()
lexer.start(source)
val rebuilt = StringBuilder()
while (lexer.tokenType != null) {
    rebuilt.append(lexer.tokenText)
    lexer.advance()
}
roundTripFailed = rebuilt.toString() != source.toString()
```

The `LuaLexer()` / `start` / `tokenType` / `tokenText` / `advance` idiom is the one already used by
`TestLuaLexer.kt:17-27`. `LuaLexer` — **not** `_LuaLexer` — is deliberate: the merging adapters are
what the parser, highlighter and TODO indexer all consume, so a merge bug is exactly what this
invariant must catch. BUG-392 lived in `LongStringMergingLexerAdapter`, and a raw-flex round-trip
would have passed while the real token stream was broken.

Note the read-then-advance order: `MergingLexerAdapterBase.getTokenType()` locates the token lazily,
so reading before the first `advance()` is correct and no leading `advance()` is wanted here.

### 3.2 Crash-freedom (lex half)

The whole loop runs inside `runCatching { }` catching **`Throwable`**, not `Exception`, because
BUG-390's failure mode was `StackOverflowError`. Only the throwable's class name is recorded — never
the message, which can carry absolute paths and would churn the baseline across machines.

## 4. Metrics and baseline (MAINT-35-07)

### 4.1 Where the data comes from (MAINT-35-04, -05)

`CorpusSweep.tally(psiFile)` (`CorpusSweep.kt:215`) already holds the parsed `PsiFile` for every
swept file, so both new checks hang off it rather than re-reading anything:

- **source text** = `psiFile.text`. Not `VfsUtilCore.loadText`, not the on-disk bytes: `psiFile.text`
  is *by definition* the text the lexer and parser saw, which is what a round-trip must compare
  against. It also makes the main-corpus encoding question moot — the platform has already decoded.
- **parse crash** = the existing `tally` body is wrapped in `runCatching { }` catching `Throwable`.
  On a throw the file yields `crash = "parse:<Class>"` and is **excluded from the oracle comparison
  entirely** — it is neither an accept nor a reject. Feeding it in as `parseErrors = 0` would make
  `lunarAccepts` true and score a crashed file as a *false accept* (§2.3), conflating a crash with a
  semantic disagreement. `requires`/`unresolved` are carried as 0 for that file, which is honest
  (nothing was counted) and safe: `CorpusGuards.assertIdentity:68-73` identity-checks the `requires`
  **total**, so a newly-crashing file changes it and the ratchet reports
  "Recognised require count changed" — a deliberate, loud failure rather than a silent one.
- **lex crash / round-trip** = `LexerInvariants.check(psiFile.text)`; a lex crash is recorded as
  `lex:<Class>`.

`FileTally` (`CorpusSweep.kt:31`) gains three fields — `roundTripFailed: Boolean`,
`crash: String?`, `oracle: ParseOracle.Verdict` — and `CorpusSweep.run` (`:40`) aggregates them into
the new `CorpusMetrics` fields alongside the existing `sumOf` calls.

### 4.2 New fields

```kotlin
val oracleDisagreements: Int = 0,                 // plain Int — §2.0 removes the "absent" state
val oracleSites: List<String> = emptyList(),      // "falseReject:<path>" / "falseAccept:<path>"
val oracleTimeouts: Int = 0,                      // diagnostic; a timing-out oracle judges nothing
val lexerRoundTripFailures: Int = 0,
val crashes: Map<String, Int> = emptyMap(),       // "lex:<Class>" / "parse:<Class>" → count
```

`crashes` is one map spanning both sites (§4.1), keyed by prefix, rather than two fields.

### 4.3 Baseline format — exact keys

`CorpusBaseline` (`CorpusMetrics.kt:76`) already defines `INSPECTION_PREFIX`, `SYMBOL_PREFIX` and
`BALLAST_PREFIX` at `:82-88`. Three additions, in the same style:

| Field | Rendered as | Notes |
| :-- | :-- | :-- |
| `oracleDisagreements` | `oracleDisagreements=<n>` | always rendered |
| `lexerRoundTripFailures` | `lexerRoundTripFailures=<n>` | always rendered |
| `crashes` | `crash.<key>=<n>`, one line each, sorted | new `CRASH_PREFIX = "crash."` |
| `oracleSites` | `oracleSite=<entry>`, repeated, sorted | new `ORACLE_SITE_KEY`. **Capped at 20 — but the cap is applied in `CorpusSweep.run`, never in `render`** (see below). |
| `oracleTimeouts` | `oracleTimeouts=<n>` | diagnostic scalar (§2.3); rendered always, gated never |

**The cap is applied at construction, not at render.** A render-time cap is lossy, and
`BaselineRatchetTest.renderParseRoundTrip` (`:44-60`) asserts `original == parse(render(original))`
over the whole data class — so a 25-site metrics object would render 20 and parse back as 20 ≠ 25,
breaking an existing green test. A truncation marker would be worse: it carries the `oracleSite`
key, so `parse` would read it back as a **fake disagreement site** indistinguishable from a real one.

The prior art is already in this pipeline and is exactly this shape: `SYMBOLS_PER_INSPECTION = 10`
is applied by `topSymbols(...)` at **construction** (`CorpusSweep.kt:54`), so `CorpusMetrics.symbolHits`
is already capped by the time `render` sees it and the round-trip is lossless. `oracleSites` is
capped the same way — sorted then truncated to 20 inside `run` — and the full count remains visible
because `oracleDisagreements` is the *uncapped* number.

`parse` (`:115`) must add `oracleSite` and `crash.` to the `filterNot` chain at `:120-125` that
strips prefixed keys before reading scalars — otherwise they are parsed as scalars and
`renderParseRoundTrip` breaks.

**`BaselineRatchetTest.renderParseRoundTrip` (`:44-60`) asserts `original == parse(render(original))`
over the whole data class**, so every field above must round-trip or an existing green test fails.

### 4.4 `compare`

`oracleDisagreements` is an ordinary numeric delta and joins the existing `gated` `Triple` list with
no special handling. `describe()` (`CorpusMetrics.kt:202-203`) is untouched.

An earlier draft carried a four-case table here for a nullable metric (number→null = regression,
null→number = improvement, …). §2.0 removed the nullable state, so the table went with it. Recording
that this simplification was *earned* rather than overlooked: the complexity existed only because
the oracle was treated as an environmental accident instead of a dependency the feature installs.

### 4.5 What is gated, and how

Restoring a rule an earlier draft stated and a later edit dropped. Ratchet direction is the existing
one throughout: an increase is a regression, a decrease prints `[corpus] IMPROVED`.

| Field | Gated? | How |
| :-- | :-- | :-- |
| `oracleDisagreements` | **yes** | appended to `compare`'s `gated` `Triple` list as an ordinary numeric delta |
| `lexerRoundTripFailures` | **yes** | appended to the same `gated` list |
| `crashes` | **yes, per key** | one `Triple` per key across `baseline.keys + observed.keys`, exactly as `inspectionHits` does at `CorpusMetrics.kt:187-195` — a key present on one side counts 0 on the other, so a crash that *starts* happening is a movement rather than a silent no-op. Per-key, not on the sum: a new `StackOverflowError` appearing while an `AssertionError` disappears must not net to zero. |
| `oracleSites` | no — diagnostic | locatability, like `parseErrorFiles` |
| `oracleTimeouts` | no — diagnostic | printed; a non-zero value is a loud warning, not a gate |

## 5. Torture corpus (MAINT-35-06)

squeek502 publishes **minimized** lexer corpora as release assets. They are not a Lua project:
no `require`s, no inspections worth counting, no commit, and the files have no extension.

**File**: `src/test/kotlin/net/internetisalie/lunar/corpus/LuaTortureCorpusTest.kt`
**Class**: `LuaTortureCorpusTest` — file and class name **must match**, and both must contain
`Corpus`, because that is what makes `build.gradle.kts:266`'s guard and its
`excludeTestsMatching("*Corpus*")` at `:268` govern the test with no build change. Naming it
`LuaTortureTest` would let it escape the filter and fail in CI, where no corpus is fetched.

### 5.1 It needs its own metrics type

`CorpusMetrics` cannot be reused: `CorpusGuards.assertIdentity` (`CorpusGuards.kt:53-74`)
identity-checks `commit`, `files` **and** `requires`, and a torture member has no commit (it is
pinned by sha256) and no requires. Forcing a sha256 into `commit` would be a lie the ratchet then
enforces. So:

```kotlin
internal data class TortureMetrics(
    val sha256: String,
    val files: Int,
    val parseErrors: Int,          // §2.3 needs it: lunarAccepts = (parseErrors == 0)
    val oracleDisagreements: Int,
    val oracleSites: List<String>,
    val oracleTimeouts: Int,
    val lexerRoundTripFailures: Int,
    val crashes: Map<String, Int>,
)
```

with its own `render`/`parse`/`compare` in `TortureBaseline`, deliberately mirroring
`CorpusBaseline`'s key style (§4.3) and its null-oracle rule (§4.4). Identity check: `sha256` and
`files` must match, or the baseline is refused.

`parseErrors` is present deliberately: §2.3's comparison is defined as
`lunarAccepts = (parseErrors == 0)`, so dropping the field would leave the oracle nothing to compare
against. Gating and null-handling follow §4.4/§4.5 unchanged.

**Getting a Lua `PsiFile` from an extensionless input.** The torture files have no extension, so
nothing in the platform will type them as Lua. The test builds the PSI explicitly:

```kotlin
PsiFileFactory.getInstance(project)
    .createFileFromText("torture-$index.lua", LuaFileType, text)
```

— the same call `LuaElementFactory.kt:43` uses. The synthetic `.lua` name is what selects the
language; the on-disk name is irrelevant.

### 5.2 Input discovery

```kotlin
File(tortureRoot).walkTopDown()
    .onEnter { it.name != ".git" }            // prune the DIRECTORY, not just dot-files
    .filter { it.isFile && !it.name.startsWith(".") }
    .sortedBy { it.relativeTo(tortureRoot).path }
```

`onEnter` is the prior art at `CorpusSweep.kt:101` and is required: a dot-*file* filter does not
prune a `.git` **directory** — `walkTopDown` descends into it and its contents (`config`, `HEAD`,
loose objects) are not dot-named, so they would be swept as inputs. Sorting by relative path makes
the baseline deterministic. Every remaining file is an input regardless of extension — the corpus is
deliberately extensionless.

Inputs are read as **bytes decoded ISO-8859-1**, not UTF-8: a fuzz corpus contains invalid UTF-8 by
construction, and a lossy decode would break the round-trip invariant at the decode rather than at
the lexer, manufacturing failures that look like lexer bugs. (This rule is specific to the torture
corpus; the main sweep reads `psiFile.text`, already decoded by the platform — §4.1.)

### 5.3 Fetch

- **`tooling/corpus/torture.tsv`** — `name<TAB>url<TAB>sha256<TAB>luaLevel`.
- **`tooling/corpus/fetch-torture.sh`** — downloads, verifies sha256 (**refusing on mismatch**),
  unpacks to `test/corpus-torture/<name>/`, writes a `.corpus-sha` stamp like `fetch-corpus.sh`.

## 6. Verification

- `test --tests *LuaTortureCorpus* -PwithCorpus`, then the full corpus ratchet, then the full suite.
- Run the corpus gate and the full suite as **separate invocations**. Combining them
  (`test -PwithCorpus --rerun --no-build-cache`) wedged the Gradle daemon on 2026-08-04 — two live
  workers, load 0.00, twenty minutes of silence, recovered only by `pkill -9`.

## De-risking

Two de-risking tasks are tracked in [`risks-and-gaps.md`](risks-and-gaps.md) — DR-01 measures what
the oracle says about today's corpus, DR-02 confirms the upstream torture asset is stably
checksummable. Neither blocks a requirement: MAINT-35-01…-05 stand on the existing corpus alone, and
MAINT-35-06 is a `Should`.

## Open Questions

None.
