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

    /** `luac` for [level]: system package, else a provisioned env (§2.0a). Throws with the remedy. */
    fun requireBinary(level: LuaLanguageLevel): File
}
```

### 2.0 The oracle is a provisioned dependency, not a runtime maybe

`luac` is **owned by this feature** (MAINT-35-00): `lua5.1`–`lua5.4` are added to
`builder-bootstrap.sh:11`, `startup-script.sh:20-22` and `.gitea/workflows/build-plugin.yml:116`,
alongside the `lua5.4`/`lua-socket`/`fontconfig` those files already install for other tests. All
four are packaged on Debian 13 (verified: `5.1.5-11`, `5.2.4-3+b3`, `5.3.6-2+b4`, `5.4.7-1+b2`).

**This deletes an entire subsystem from an earlier draft of this design.** That draft treated a
missing `luac` as a normal runtime state and built machinery to survive it: a nullable
`oracleDisagreements`, a four-case comparison table, an `Unavailable` verdict threaded through every
layer, a skip-vs-fail rule for CI, and a top-rated risk (R1) devoted to the possibility that the gate
would silently disable itself. All of that was the cost of *not owning a dependency that is one apt
package away*. Owning it makes the tolerance unnecessary, and fail-fast strictly safer than any
tolerance could be: a gate that cannot run is louder than a gate that runs empty.

So `requireBinary` **throws** when the binary is absent, before any file is judged, with the remedy
in the message:

```
No luac for LUA51. The parse oracle is a provisioned dependency of MAINT-35.
Install it:  apt-get install lua5.1     (see tooling/gce-builder/builder-bootstrap.sh)
```

### 2.0a Two sources, in order — and we already build the second one

`luac` resolution has two sources, tried in order:

1. **System package** — `luac5.1`–`luac5.4` on `PATH`, provisioned by MAINT-35-00. Fast, no compile.
2. **A provisioned environment's `bin/luac`** — because **Lunar builds `luac` itself**.
   `PucLuaBuildRecipe.kt:115-126` compiles `luac.c` and copies it to `<prefix>/bin/luac`;
   `:144` marks it executable; `LuaProvisionEngine.kt:203` records it as an `extraBinary` in the env
   manifest. Every source-built environment therefore already carries a version-exact `luac`.

Source 2 is what covers **`LUA55`**, which Debian does not package — and which the native provisioner
*defaults to*. An earlier draft instead made `fetch-corpus.sh` reject a `LUA55` row, which was the
wrong instinct twice over: it declared a level unsupportable while our own toolchain was already
building a `luac` for it, and it treated "apt cannot supply this" as "this cannot be supplied".

A side benefit worth naming: source 2 **dogfoods TOOLING-04**. If the provisioner ever emits a
`luac` that cannot parse valid Lua, the oracle turns that product defect into a test failure.

### 2.1 Version matching is the whole correctness of the oracle

`luac5.4` accepts `local a = 1 // 2`; `luac5.1` rejects it — **verified on the builder, both
directions**. Judging a `LUA51` corpus with whatever `luac` happens to be first on `PATH` therefore
invents disagreements (or hides them). The mapping is explicit, and resolution is by *versioned*
name only — a bare `luac` is never used, because its version is unknowable without running it:

| `LuaLanguageLevel` | binary candidates, in order |
| :-- | :-- |
| `LUA50` | *(none)* — `requireBinary` throws "no packaged luac for 5.0". `LuaLanguageLevel` really does declare `LUA50` (`lang/LuaLanguageLevel.kt:20`), so an exhaustive `when` must cover it. |
| `LUA51` | `luac5.1`, `luac51` |
| `LUA52` | `luac5.2`, `luac52` |
| `LUA53` | `luac5.3`, `luac53` |
| `LUA54` | `luac5.4`, `luac54` |
| `LUA55` | `luac5.5`, `luac55` — **not packaged on Debian**; resolved from a provisioned env's `bin/luac` (§2.0a) |

`requireBinary` resolves via an **exhaustive** `when` over all six constants — no `else` — so a future
level fails to compile rather than silently resolving to nothing. Each candidate is looked up on
`PATH`. After MAINT-35-00 the builder carries `luac5.1`–`luac5.4` from its bootstrap, and all four
current corpus rows are `LUA51`, so the oracle covers the whole corpus.

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
