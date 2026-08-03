---
id: "MAINT-33-DESIGN"
title: "Technical Design"
type: "design"
parent_id: "MAINT-33"
folders:
  - "[[features/maint/33-corpus-sweep/requirements|requirements]]"
---

# Technical Design: MAINT-33 — Corpus Sweep

## 1. Architecture Overview

### Current State

Real-project coverage today is one hand-built tree and one temp file:

- `src/test/kotlin/**` — synthetic snippets via `myFixture.configureByText`; no project layout.
- The out-of-repo `test/` tree, read by exactly two tests. `LuaRecursiveReferenceTest.kt:94` copies
  a *single* file (`test/luacheck/src/luacheck/parser.lua`) into the fixture.
- `src/integrationTest/**` — `ProjectOpenIntegrationTest.kt:44` writes one `main.lua`.
- `src/test/resources/legacy-projects/scenario-{1..4}.xml` — settings-migration fixtures only.

Nothing measures the plugin across a whole real project, so defects that only appear at project
scale (BUG-389: an entire call syntax contributing no references) survive a green suite.

### Prior Art in This Repo

| Existing component | Relationship |
|---|---|
| `src/test/kotlin/net/internetisalie/lunar/lang/parser/TestLuaParsingExhaustive.kt` — "parsing tests collected from official Lua tests, luacheck, and other sources", data-driven over inline snippets asserting `PsiErrorElement` presence/absence | **Complementary — neither extends nor replaces.** It asserts *known* snippets parse a *known* way. MAINT-33 asserts nothing in advance and measures whole pinned trees. The parse-error metric (§3.1) deliberately uses the same `PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java)` primitive as `TestLuaParsingExhaustive.kt:30`. |
| `LuaRecursiveReferenceTest` (`getTestDataPath() = System.getProperty("user.dir")`, `copyFileToProject` from the `test/` symlink) | **Extends the pattern.** MAINT-33 reuses the repo-root `testDataPath` idiom and the `-PexcludeExternalFixtureTests` precedent, but copies *directories* and adds its own corpus tree so this test's fixture is untouched. |
| `redisIntegrationTest` (`build.gradle.kts:293`) — an opt-in verification task outside `build`/`test`/`check` | **Precedent, not reused.** The perf-suite comment at `build.gradle.kts:239-243` records that a standalone `register<Test>` fails at platform fixture init, so MAINT-33 uses the `*Performance*` **filter-exclusion** pattern (`build.gradle.kts:244-250`) on the IntelliJ-configured `test` task instead of a new task. |
| `docs/features/maint/vnc-multi-project-smoke-tests.md` — manual VNC multi-rock / Go+Lua checklist | **Complementary.** That covers live-IDE coexistence; MAINT-33 is the headless, repeatable half. |
| `LuaUndeclaredVariableInspectionTest.kt:15,20-22` — `enableInspections(...)` + `doHighlighting()` filtered on `HighlightInfo.description` | **Extends.** §3.3 reuses this exact idiom for corpus-wide inspection counting. |

No existing component sweeps a corpus or maintains a metric baseline; nothing is replaced.

### Target State

```
tooling/corpus/corpus.tsv          pins (name, url, commit, roots, prune)
tooling/corpus/fetch-corpus.sh     → test/corpus/<name>/  (+ .corpus-sha, no .git)
                                          │
src/test/kotlin/.../corpus/               ▼
  CorpusManifest.kt   reads the manifest + the on-disk stamp
  CorpusSweep.kt      copies roots into the fixture, measures
  CorpusMetrics.kt    metric model + baseline render/parse/compare
  LuaCorpusSweepTest.kt  one @Test per project: sweep → ratchet
                                          │
src/test/resources/corpus/<name>.baseline ◄┘  committed floor
```

No production code ships. Everything lives in the test source set, `tooling/`, and one
`build.gradle.kts` filter block.

## 2. Core Components

### 2.1 `net.internetisalie.lunar.corpus.CorpusEntry` / `CorpusManifest`

- **Responsibility**: parse `tooling/corpus/corpus.tsv` and report the commit stamped on disk.
- **Threading**: plain file I/O on the test thread; no PSI, no platform services.
- **Collaborators**: none (`java.io.File` only).
- **Key API**:
  ```kotlin
  data class CorpusEntry(
      val name: String,
      val commit: String,
      val roots: List<String>,
      val luaLevel: LuaLanguageLevel,   // §4.1 column 5; defaults to LUA54
  )

  object CorpusManifest {
      const val CORPUS_DIR = "test/corpus"
      fun load(repoRoot: File): List<CorpusEntry>
      fun entry(repoRoot: File, name: String): CorpusEntry      // errors if absent/duplicate
      fun checkedOutCommit(repoRoot: File, name: String): String?  // null when not fetched
      fun checkoutDir(repoRoot: File, name: String): File       // <repoRoot>/test/corpus/<name>
  }
  ```

### 2.2 `net.internetisalie.lunar.corpus.CorpusSweep`

- **Responsibility**: copy each declared root into the fixture and tally metrics per file.
- **Threading**: runs inside `BasePlatformTestCase`, on the platform test thread; PSI access
  follows the same rules as `LuaRecursiveReferenceTest`.
- **Collaborators**: `CodeInsightTestFixture.copyDirectoryToProject`,
  `com.intellij.openapi.vfs.VfsUtilCore.iterateChildrenRecursively`,
  `com.intellij.psi.PsiManager.findFile`,
  `com.intellij.psi.util.PsiTreeUtil.findChildrenOfType`,
  `com.intellij.psi.PsiErrorElement`,
  `net.internetisalie.lunar.lang.psi.LuaTerminalExpr` (`src/main/gen/.../psi/LuaTerminalExpr.java:8`),
  `net.internetisalie.lunar.lang.LuaRequireReference` (`src/main/kotlin/.../LuaRequireReference.kt:8`).
- **Key API**: `run` takes the checkout directory explicitly, because §3.4 walks the **whole
  checkout** while `entry.roots` only names the swept subset — the fixture cannot supply that path.
  ```kotlin
  object CorpusSweep {
      // checkoutDir = CorpusManifest.checkoutDir(repoRoot, entry.name)
      fun run(fixture: CodeInsightTestFixture, entry: CorpusEntry, checkoutDir: File): CorpusMetrics

      // §3.3; called by run() once the roots are copied. Requires the ten tools already enabled
      // in setUp and entry.luaLevel already applied. Takes the §3.1 per-file tallies (not bare
      // VirtualFiles) because the unattributed subtraction needs each file's parseErrors.
      private fun inspectionHits(
          fixture: CodeInsightTestFixture,
          tallies: List<Pair<VirtualFile, FileTally>>,
      ): Map<String, Int>

      // §3.4; independent of the fixture — pure filesystem + FileTypeManager.
      private fun ballast(checkoutDir: File, entry: CorpusEntry): Map<String, BallastGroup>
  }
  ```

> **Grounding note**: `copyDirectoryToProject` and `iterateChildrenRecursively` do not appear
> elsewhere in `src/test/kotlin`, but this component has been **executed** on the builder — the
> prototype compiled and produced the TC 4 / TC 5 numbers — which is stronger evidence than a grep.

### 2.3 `net.internetisalie.lunar.corpus.CorpusMetrics` / `CorpusComparison` / `CorpusBaseline`

- **Responsibility**: the metric model, its on-disk text form, and the ratchet comparison.
- **Threading**: pure functions + file I/O.
- **Key API**:
  ```kotlin
  data class CorpusMetrics(
      val commit: String,
      val files: Int,
      val parseErrors: Int,
      val requires: Int,
      val unresolvedRequires: Int,
      val parseErrorFiles: List<String>,
      val inspectionHits: Map<String, Int>,   // MAINT-33-06, short name → count
      val ballast: Map<String, BallastGroup>, // MAINT-33-07, extension/filename → group
  )

  data class BallastGroup(val count: Int, val claimed: Boolean)
  data class CorpusComparison(val regressions: List<String>, val improvements: List<String>)

  object CorpusBaseline {
      fun file(repoRoot: File, name: String): File
      fun render(metrics: CorpusMetrics): String
      fun parse(text: String): CorpusMetrics
      fun compare(baseline: CorpusMetrics, observed: CorpusMetrics): CorpusComparison
  }
  ```

### 2.4 `net.internetisalie.lunar.corpus.LuaCorpusSweepTest`

- **Responsibility**: one `@Test` per corpus project; wires manifest → sweep → ratchet.
- **Threading**: `BasePlatformTestCase` with `@RunWith(JUnit4::class)`, matching
  `LuaRecursiveReferenceTest.kt:8`.
- **Key API**:
  ```kotlin
  class LuaCorpusSweepTest : BasePlatformTestCase() {
      override fun getTestDataPath(): String = System.getProperty("user.dir")
      @Test fun testLuacheckCorpus()
      @Test fun testLuarocksCorpus()
      @Test fun testKoreaderCorpus()          // MAINT-33-08
      @Test fun testZerobraneCorpus()         // MAINT-33-08

      private fun sweepAndRatchet(name: String)   // calls CorpusGuards (§2.4a)
      private fun recordBaseline(baselineFile: File, observed: CorpusMetrics)
  }
  ```
  The two guards are **not** members of this class — see §2.4a.

### 2.4a `net.internetisalie.lunar.corpus.CorpusGuards`

- **Responsibility**: the two pre-comparison guards, extracted from `LuaCorpusSweepTest`'s
  `private fun assertCorpusFetched` (`LuaCorpusSweepTest.kt:47`) and `private fun assertRatchet`
  (`:69`) so that both the sweep test and `BaselineRatchetTest` can call them. Neither guard
  touches `myFixture`, PSI or VFS — they use only `CorpusManifest`, `CorpusBaseline` and JUnit
  asserts — so the extraction is a visibility change, not a redesign.
- **Threading**: none; plain functions.
- **Key API**:
  ```kotlin
  // Asserts are inherited from TestCase today; on a standalone object they need an explicit
  // import of org.junit.Assert.{assertEquals, assertNotNull, assertTrue} (JUnit 4 is already on
  // the test classpath — every test here is @RunWith(JUnit4::class)).
  internal object CorpusGuards {
      internal fun assertCorpusFetched(repoRoot: File, entry: CorpusEntry)
      // Identity checks (§3.2 step 1): commit, then files, then requires.
      // Deliberately NOT in CorpusBaseline.compare.
      internal fun assertRatchet(baselineFile: File, observed: CorpusMetrics)
  }
  ```
  The extraction also updates the KDoc link at `CorpusMetrics.kt:61`, which currently points at
  `[LuaCorpusSweepTest.assertRatchet]`.

### 2.5 `tooling/corpus/fetch-corpus.sh`

- **Responsibility**: materialise every manifest row idempotently (§3.5).
- **Contract**: exit 0 on success; non-zero with a message on a malformed row, a failed fetch, or
  a declared root missing from the checkout. Honours `LUNAR_CORPUS_ROOT` for testing.

### 2.6 `net.internetisalie.lunar.corpus.BaselineRatchetTest`

- **Responsibility**: prove the gate can fail (implementation-plan Phase 2). Pure unit test — no
  fixture, no fetched corpus — so it runs in the **routine** suite. Named without "Corpus"
  deliberately: `excludeTestsMatching("*Corpus*")` is case-sensitive and does not match the
  lowercase `…lunar.corpus.` package segment.
- **Threading**: none; plain JUnit.
- **Key API**:
  ```kotlin
  class BaselineRatchetTest {
      @Test fun renderParseRoundTrip()            // §4.2, incl. `.luacov` and `unattributed` keys
      @Test fun gatedMetricIncreaseIsRegression() // TC 6
      @Test fun gatedMetricDecreaseIsImprovement()// TC 7
      @Test fun missingScalarKeyThrows()          // §4.2 failure handling
      @Test fun ballastKeyInverseParse()          // §4.2 key grammar, doubled-dot case
      @Test fun malformedManifestRowThrows()      // §4.1 failure handling
      @Test fun duplicateManifestNameThrows()     // §4.1 duplicate-name error

      // TC 9 — assertCorpusFetched on an absent corpus.
      @Test fun absentCorpusFailsWithFetchInstruction()
      // TC 8 — assertRatchet when the stamped commit and the baseline's disagree.
      @Test fun divergentBaselineCommitFailsWithReRecordInstruction()
  }
  ```
  `renderParseRoundTrip` grows by phase: five scalars + `parseErrorFile` in Phase 2,
  `inspection.*`/`unattributed` in Phase 3, `ballast.*` in Phase 4 — those key families do not
  exist earlier. `ballastKeyInverseParse` lands in Phase 4 for the same reason.

  **TC 8 / TC 9 live here, not in a fixture-bound class.** Both guards (§2.4a) are fixture-free,
  so a separate `BasePlatformTestCase` would buy nothing and — because
  `excludeTestsMatching("*Corpus*")` (`build.gradle.kts:266-271`) is keyed on the class name —
  would take these two out of the routine suite, which is exactly where Risk 1.3 needs them. Both
  are driven with a **synthetic `repoRoot`**, never the real corpus:

  | TC | Arrange | Act | Assert |
  |---|---|---|---|
  | 9 | A temp dir containing `tooling/corpus/corpus.tsv` with one row, and **no** `test/corpus/<name>/` | `CorpusGuards.assertCorpusFetched(tempRoot, entry)` | Fails; message names `tooling/corpus/fetch-corpus.sh` (`LuaCorpusSweepTest.kt:49-52`) |
  | 8 | A temp baseline file whose `commit=` differs from the `CorpusMetrics` passed in — no checkout needed, since `assertRatchet` reads only the baseline file and the observed metrics | `CorpusGuards.assertRatchet(tempBaseline, handBuiltMetrics)` | Fails with "recorded against a different corpus commit; re-record it" (`LuaCorpusSweepTest.kt:75-79`) |

  TC 8 deliberately does **not** re-pin the manifest: with `.corpus-sha` still matching the
  manifest, `assertCorpusFetched` would fire first (`LuaCorpusSweepTest.kt:53-57`) with a
  different message. The condition under test is stamped-commit == manifest-commit ≠
  baseline-commit.

## 3. Algorithms

### 3.1 Per-file tally

- **Input → Output**: `PsiFile` → `FileTally(parseErrors: Int, requires: Int, unresolved: Int)`.
- **Steps**:
  1. `parseErrors = PsiTreeUtil.findChildrenOfType(psiFile, PsiErrorElement::class.java).size`.
  2. `refs = PsiTreeUtil.findChildrenOfType(psiFile, LuaTerminalExpr::class.java)`
     `.flatMap { it.references.asIterable() }.filterIsInstance<LuaRequireReference>()`.
  3. `requires = refs.size`; `unresolved = refs.count { it.resolve() == null }`.
- **Rules / edge handling**: a file `PsiManager.findFile` returns null for is skipped and does not
  count toward `files`. Only `extension == "lua"` files are visited. Relative path label is
  `"<root>/" + VfsUtilCore.getRelativePath(file, copiedRoot)`, falling back to the file name.
- **Note**: step 2 measures the plugin's *reference contribution*, which is exactly why it
  detected BUG-389. It intentionally does **not** re-implement require extraction — using
  `fileRequires()` (`LuaRequireExtraction.kt:21`) instead would have masked the bug, since the indexer
  handles the string-call form that the reference contributor misses.

### 3.2 Ratchet comparison

- **Input → Output**: `(baseline: CorpusMetrics, observed: CorpusMetrics)` → `CorpusComparison`.
- **Steps**:
  1. **Identity checks (caller, hard fail)**: `commit`, then `files`, then `requires`. Any
     mismatch fails with a specific message and the comparison is not attempted.
  2. Build the gated pair list: `parseErrors`, `unresolvedRequires`, and one entry per key in
     `baseline.inspectionHits ∪ observed.inspectionHits` (missing key ⇒ 0).
  3. `regressions` = pairs where `observed > baseline`; `improvements` = pairs where
     `observed < baseline`. Both rendered as `"<metric>: baseline <b> → observed <o>"`.
  4. Print every improvement with a re-record instruction. Fail iff `regressions` is non-empty,
     with all regressions joined by newline.
- **Rules / edge handling**: equal ⇒ neither list. Ballast groups (§3.4) are **reported, never
  gated** — a new unclaimed extension is a discovery, not a regression.

### 3.3 Inspection attribution (MAINT-33-06)

- **Input → Output**: the corpus files → `Map<inspectionShortName, hitCount>`.
- **Tool set — TEN inspections.** One table, deliberately: an earlier split into "eight plus two"
  desynchronised from the instructions that consume it. Every row below is instantiated by its
  `plugin.xml` `implementationClass` and enabled in `setUp`.

  | Baseline key | Class | Package | `plugin.xml` (shortName / implementationClass) |
  |---|---|---|---|
  | `LuaUndeclaredVariable` | `LuaUndeclaredVariableInspection` | `…analysis.inspections` | :208 / :213 |
  | `LuaGlobalCreation` | `LuaGlobalCreationInspection` | `…analysis.inspections` | :217 / :222 |
  | `LuaUnusedLocal` | `LuaUnusedLocalInspection` | `…analysis.inspections` | :223 / :226 |
  | `LuaShadowingVariable` | `LuaShadowingVariableInspection` | `…analysis.inspections` | :230 / :235 |
  | `LuaDeprecatedApi` | `LuaDeprecatedApiInspection` | `…analysis.inspections` | :239 / :244 |
  | `LuaSuspiciousConcatenation` | `LuaSuspiciousConcatenationInspection` | `…analysis.inspections` | :248 / :253 |
  | `LuaUnreachableCode` | `LuaUnreachableCodeInspection` | `…analysis.inspections` | :257 / :262 |
  | `LuaLanguageLevel` | `LuaLanguageLevelInspection` | `…analysis.inspections` | :266 / :271 |
  | `LuaTypeAssignability` † | `LuaTypeAssignabilityInspection` | `…analysis` | — / :196 |
  | `LuaReturnTypeMismatch` † | `LuaReturnTypeMismatchInspection` | `…analysis` | — / :204 |

  † The last two declare **no `shortName`**, so the platform derives their ids from the class name
  via `InspectionProfileEntry.getShortName` (`InspectionProfileEntry.java:363-364` —
  `trimEnd(trimEnd(className, "Inspection"), "InspectionBase")`). Their `<localInspection>` blocks
  open at `plugin.xml:190` and `:198`. Both are `level="ERROR"` and exercise the type engine, so
  they are the highest-value entries in a false-positive metric — excluding them would leave the
  noisiest surface unmeasured. DR-07 confirms the two derived ids against the first recorded
  baseline rather than trusting the derivation blind.

  **Excluded — these six are the complete remainder** (16 `<localInspection>` registrations
  total: 10 included, 6 excluded; the partition is exhaustive by construction):

  | Excluded | `plugin.xml` | Why it would measure the environment, not the plugin |
  |---|---|---|
  | `LuaCheck` | :335 | Needs the external `luacheck` binary, absent in the fixture |
  | `LuaValkeyPortability` | :275 | Needs Redis/Valkey context |
  | `LuaRedisCommand` | :284 | Needs Redis context |
  | `LuaRedisSandbox` | :295 | Needs Redis context |
  | `LuaRedisFunctionKeys` | :306 | Needs Redis context |
  | `LuaJsonSchemaCompliance` | :315 | `language="Lua"` (`:314`) and it *does* target Lua files — but `LuaJsonSchemaComplianceInspection.kt:19` returns `EMPTY_VISITOR` unless `JsonSchemaService.isApplicableToFile` matches, and no corpus file has a schema mapping, so it is inert here |
- **Language level (required)**: `LuaLanguageLevelInspection.kt:138` reads
  `LuaProjectSettings.getInstance(element.project).state.languageLevel`, which defaults to
  `LuaLanguageLevel.LUA54` (`LuaProjectSettings.kt:46`) while both current corpus projects are
  Lua 5.1. The level is therefore **pinned per project** from the manifest's `luaLevel` column
  (§4.1), applied before the sweep:
  ```kotlin
  LuaProjectSettings.getInstance(myFixture.project).state.languageLevel = entry.luaLevel
  ```
  This is the in-repo idiom — `LuaGlobalSoftKeywordTest.kt:26` sets it exactly this way
  (`LuaProjectSettingsMigrationIntegrationTest.kt:14` also assigns `state.languageLevel`, but on a
  detached `State()` passed to `loadState`, which is a different pattern). The values come from
  `net.internetisalie.lunar.lang.LuaLanguageLevel` (`LuaLanguageLevel.kt:19`).
  Pinning matters beyond `LuaLanguageLevel` itself: the level selects which stdlib globals are
  known, so `LuaUndeclaredVariable` moves with it too. Left unpinned, every inspection baseline
  would be a function of a default that a future commit could change.
- **Steps**:
  1. In `setUp`, `myFixture.enableInspections(<all ten instances above>)`.
  2. Per corpus file: `myFixture.openFileInEditor(virtualFile)` then
     `myFixture.doHighlighting(com.intellij.lang.annotation.HighlightSeverity.WEAK_WARNING)` — the
     severity-filtered overload at `CodeInsightTestFixture.java:368`. Do **not** hand-roll the
     filter, and do not use the unfiltered `doHighlighting()` at :364, which folds in INFORMATION-
     level decoration (semantic highlighting, injections) that no inspection produces.
  3. For each returned `HighlightInfo`, read
     `com.intellij.codeInsight.daemon.impl.HighlightInfo.getInspectionToolId()`
     (`HighlightInfo.java:462` — public, `@Nullable`) and increment that id's counter.
  4. **Null disposition**: an info whose tool id is null goes to the reserved key `unattributed`,
     which is recorded and gated like any other. Four candidate sources produce a null id; **two
     actually feed the key** (rows 2 and 4), because row 1 is subtracted out and row 3 never
     reaches the severity floor:

     | Source | Reaches the WEAK_WARNING floor? | Disposition |
     |---|---|---|
     | `PsiErrorElement` syntax errors | Yes — `DefaultHighlightVisitor.java:54-59` calls `createErrorElementInfo`, which builds `HighlightInfoType.ERROR` at `:81-84` and **never sets a toolId** | **Subtracted** — see the formula below |
     | Annotators emitting above the floor | Yes | Counted in `unattributed` |
     | Annotators emitting only `newSilentAnnotation` (four at `TEXT_ATTRIBUTES`, `LuaCatsAnnotator` at `INFORMATION`) | No | Filtered out by the severity floor |
     | An inspection that loses its id | Yes | Counted in `unattributed` — the signal this key exists for |

     **Subtraction formula.** Parse errors are already counted exactly by §3.1, so leaving them in
     would double-gate every parse-error regression and make TC 6's failure output unpredictable.
     Per file:
     ```
     unattributed(file) = max(0, nullToolIdInfos(file) − parseErrors(file))
     ```
     The `max(0, …)` floor guards the case where a `HighlightErrorFilter`
     (`DefaultHighlightVisitor.java:55-56`) suppresses an error element that §3.1's PSI walk still
     counted. Both operands are already computed for the same file, so this is arithmetic on
     existing numbers rather than a second classification pass.

     **Which annotators can contribute.** `plugin.xml:162-188` registers **nine** `<annotator>`s.
     Four can exceed the floor: `LuaNumeralAnnotator` (`LuaAnnotators.kt:28,39,47,53`),
     `LuaAttribNameAnnotator` (`LuaAnnotators.kt:161`), `LuaStandaloneExpressionAnnotator.kt:20`
     and `LuaVarargAnnotator.kt:35` — all four `newAnnotation(HighlightSeverity.ERROR, …)`. The
     other five emit only `newSilentAnnotation`, below the floor either way: four at
     `TEXT_ATTRIBUTES` (`LuaLongStringAnnotator` `LuaAnnotators.kt:83,87`,
     `LuaLongCommentAnnotator` `:106,110`, `LuaGlobalKeywordAnnotator` `:130`,
     `LuaInferredTypeAnnotator` `LuaInferredTypeAnnotator.kt:28`) and `LuaCatsAnnotator`
     (`luacats/lang/syntax/LuaCatsAnnotator.kt:14`) at `INFORMATION`, which is 10 against
     `WEAK_WARNING`'s 200 (`HighlightSeverity.java:42`, `:85`). The `<externalAnnotator>`
     `LuaCheckAnnotator` (`plugin.xml:329-331`) is inert here for the same reason its inspection is
     excluded — no luacheck binary.

     Gating the remainder is deliberate: an annotator that starts firing more often across a real
     corpus is a regression worth catching. It does mean an intentional annotator change requires a
     re-record, and that this key is a *bucket*, not an anomaly counter.
- **Complexity / bounds**: one editor open + highlight pass per file. This dominates sweep runtime;
  see Risk 1.1 and MAINT-33-09.

### 3.4 Ballast classification (MAINT-33-07)

Ballast is **exactly the complement of what the sweep indexes**. §3.1 indexes a file iff it has
extension `lua` **and** lies under a declared root, so:

> `ballast(f) ⇔ ¬(extension(f) == "lua" ∧ underRoot(f))`

Both conjuncts matter, and neither alone is the rule. Dropping `underRoot` would exclude
`tlconfig.lua` — a Lua-syntax *config* file at the checkout root that the sweep never parses.
Dropping the extension test would exclude the 107 `.tl` files that live inside `luarocks/src`,
which are exactly the Teal signal the inventory exists to surface. The rule keeps both: ballast
totals are whole-checkout counts (`tl=117`, not 10; `rockspec=53`, not 48).

- **Input → Output**: `(checkoutDir: File, entry: CorpusEntry)` → `Map<String, BallastGroup>`.
- **Steps**:
  1. Walk `checkoutDir` recursively, skipping `.git` and `.corpus-sha`.
  2. Skip a file iff its extension is `lua` **and** its path lies under one of `entry.roots` —
     that is, iff §3.1 indexed it. Every other file is ballast, including a `.lua` file outside the
     roots and a non-`.lua` file inside them.
  3. `groupKey` = the file's **base name** when it contains no `.` after position 0 (covers
     `Makefile`, `.busted`, `.luacov`, `configure`); otherwise the **lowercase substring after the
     last `.`** (`tl`, `rockspec`, `md`). Dotfiles keep their leading dot: the key for `.luacov` is
     `.luacov`. `config.ld` has a dot after position 0, so its key is `ld` — the requirement, the
     test case and the checklist all say `ld`, not `config.ld`.
  4. `claimed` = true iff
     `com.intellij.openapi.fileTypes.FileTypeManager.getInstance().getFileTypeByFileName(fileName)`
     returns anything other than `com.intellij.openapi.fileTypes.UnknownFileType.INSTANCE`.
     Evaluated per file; a group is `claimed` iff **every** file in it is claimed (a split group is
     reported `unclaimed`, which is the conservative direction for a discovery signal).
  5. Emit every group with `count >= 1`, sorted by key.
- **Rules / edge handling**: classification is per *file name*, so `.luacheckrc` (claimed via
  `plugin.xml:100` `fileNames`) and `.luacov` (unclaimed) separate correctly. Note that being
  ballast and being `claimed` are independent: `tlconfig.lua` is ballast (never indexed, because it
  is outside the roots) yet **claimed**, since `plugin.xml:99` registers `extensions="lua;rockspec"`
  — so it lands in `ballast.claimed.lua`. That is the correct outcome: Lunar does understand the
  file's syntax, so it is not an integration gap. The Teal gap is carried by the 117 unclaimed
  `.tl` files, not by this one.
- **Registry caveat**: the `FileTypeManager` in a `BasePlatformTestCase` contains the platform core
  types plus the plugin under test — **not** the bundled Markdown/YAML/etc. plugins a real IDE has.
  So `md`, `yml`, `sh` and similar are expected to report `unclaimed` and are **noise, not
  findings**. DR-05 establishes the actual registry contents and produces the *ignore list* of
  groups that are unclaimed purely by fixture artefact; only groups outside that list are
  integration candidates. Until DR-05 lands, the inventory is recorded but not interpreted.

### 3.5 Fetch idempotency

- **Input → Output**: a manifest row → a checkout at `test/corpus/<name>`.
- **Steps**:
  1. If `<dest>/.corpus-sha` exists and equals `commit`, log "already at" and return.
  2. Otherwise `rm -rf <dest>`; `git init`; `git remote add origin <url>`;
     `git fetch --depth 1 origin <commit>`; `git checkout FETCH_HEAD`.
  3. `rm -rf <dest>/.git`; then `rm -rf <dest>/<p>` for each `p` in `prune`.
  4. Write `commit` to `<dest>/.corpus-sha`.
  5. Assert every declared root exists; fail the script otherwise.
- **Rules / edge handling**: step 2 depends on the host allowing a SHA in `want`. All pins are tag
  tips, which GitHub serves; a non-tag SHA may fail, hence the manifest rule that pins come from
  release tags. Step 1 is what makes the script safe to call from any workflow.

## 4. External Data & Parsing

### 4.1 `tooling/corpus/corpus.tsv`

- **Format**: tab-separated, `#`-comment and blank lines skipped, ≥4 columns per row. Columns 4
  (`prune`) and 5 (`luaLevel`) are optional.
  ```
  # name	url	commit	roots	prune	luaLevel
  luacheck	https://github.com/lunarmodules/luacheck.git	cc089e3f65acdd1ef8716cc73a3eca24a6b845e4	src,spec		LUA51
  luarocks	https://github.com/luarocks/luarocks.git	990ec6ca3b097c7160fe925cfca4b8e57cfe5685	src,spec	win32	LUA51
  ```
- **Parse strategy**: `split('\t')`; `roots`/`prune` are `split(',')` with blanks filtered.
  `luaLevel` is `LuaLanguageLevel.valueOf(...)` on the trimmed value
  (`net.internetisalie.lunar.lang.LuaLanguageLevel`, `LuaLanguageLevel.kt:19`), defaulting to
  `LuaLanguageLevel.LUA54` when the column is absent or blank — i.e. the same default as
  `LuaProjectSettings.kt:46`, so omitting it changes nothing.
- **Maps to**: `CorpusEntry` (columns 0, 2, 3, 5); columns 1 and 4 are consumed only by the script.
- **Failure handling**: fewer than 4 columns ⇒ `require(...)` failure naming the row. An
  unrecognised `luaLevel` ⇒ `IllegalArgumentException` from `valueOf`, naming the row. A name with
  no matching row ⇒ `error(...)` from `CorpusManifest.entry`; **more than one row with the same
  name ⇒ a distinct "duplicate corpus entry" error**, not the "no entry" message (`singleOrNull`
  alone would conflate the two).

### 4.2 `src/test/resources/corpus/<name>.baseline`

- **Format**: one `key=value` per line. Lines without `=`, and lines starting with `#`, ignored.
  Emission order is fixed so a diff is legible: the five scalars, then `inspection.*` sorted by
  key, then `ballast.*` sorted by key, then `parseErrorFile` sorted.
  ```
  commit=cc089e3f65acdd1ef8716cc73a3eca24a6b845e4
  files=132
  parseErrors=3
  requires=3
  unresolvedRequires=3
  inspection.LuaUndeclaredVariable=41
  inspection.LuaUnusedLocal=7
  inspection.unattributed=0
  ballast.claimed.rockspec=53
  ballast.unclaimed..luacov=1
  parseErrorFile=spec/samples/compound_operators.lua
  parseErrorFile=spec/samples/utf8_error.lua
  ```
- **Parse strategy**: split each line on the **first** `=`. `commit` is a string; `files`,
  `parseErrors`, `requires`, `unresolvedRequires`, `inspection.*` and `ballast.*` are `Int`.
  `parseErrorFile` accumulates into a list.
- **Key grammar and its inverse** (the group key may itself contain or begin with `.`, so the
  decomposition must be positional, never a naive `split('.')`):

  | Prefix | Emitted as | Inverse parse |
  |---|---|---|
  | `inspection.` | `inspection.<toolId>=<n>` | `toolId = key.removePrefix("inspection.")` — the remainder is taken **whole**, including any dots. The reserved id `unattributed` (§3.3 step 4) is an ordinary key here. |
  | `ballast.` | `ballast.<claimed\|unclaimed>.<groupKey>=<n>` | strip `ballast.`, then take the substring up to the **first** `.` as the claimed flag (exactly `claimed` or `unclaimed`; anything else is a malformed-baseline failure), and everything **after that first `.`** as `groupKey` — taken whole. |

  Worked cases: `ballast.unclaimed..luacov=1` → flag `unclaimed`, key `.luacov`;
  `ballast.claimed.rockspec=53` → flag `claimed`, key `rockspec`. The doubled dot in the first is
  correct and expected, because dotfile keys retain their leading dot (§3.4 step 3).
- **Maps to**: `CorpusMetrics`.
- **Failure handling**: a missing scalar key ⇒ `getValue` throws `NoSuchElementException`, which
  fails the test — an unreadable baseline must never be treated as "no regression". A
  non-numeric value ⇒ `NumberFormatException`, likewise fatal.

## 5. Data Flow

### Example 1: gating run, no regression

`gce-builder run "test --tests *Corpus* -PwithCorpus"` → `LuaCorpusSweepTest.testLuacheckCorpus` →
`CorpusManifest.entry` reads the pin → `assertCorpusFetched` compares it to `.corpus-sha` → OK →
`CorpusSweep.run` copies `src` and `spec` into the fixture, walks 132 `.lua` files, tallies →
`CorpusBaseline.parse` reads the committed floor → identity checks pass →
`compare` returns empty/empty → test passes, counts printed.

### Example 2: BUG-389 gets fixed

The contributor starts matching the string-call form. luacheck's `requires` jumps 3 → ~155. The
**identity check on `requires` fails first**, with "re-record it" — deliberately, because the
`unresolvedRequires` floor computed against 3 references is meaningless against 155. The operator
re-records with `-PrecordCorpusBaseline`, commits the new baseline alongside the fix, and the
ratchet resumes from the new floor.

### Example 3: a new corpus project

Append a row → `fetch-corpus.sh` → add a `@Test` → run with `-PrecordCorpusBaseline` → copy the
echoed baseline into `src/test/resources/corpus/` → commit. Later runs gate against it.

## 6. Edge Cases

| Case | Handling |
|---|---|
| Corpus not fetched | `assertCorpusFetched` fails naming `tooling/corpus/fetch-corpus.sh` (TC 9). |
| Corpus at the wrong SHA | Same assertion, distinct message — a stale tree is never measured. |
| Baseline file absent during a gating run | Fail with the record command; never auto-create. |
| Corpus legitimately re-pinned | `commit` identity check fails first, before any misleading metric delta. |
| A metric improves | Printed as `IMPROVED`, does not fail — a fix and its baseline update may land in separate commits. |
| Deliberately-malformed corpus files | Expected and baselined (`parseErrorFile` list); they form a non-zero floor. |
| KOReader fetched without submodules | Its unresolved-`require` floor is inherently high; floors are **per project**, never a shared threshold. |
| `PsiManager.findFile` returns null | File skipped and excluded from `files`; keeps `files` consistent with what was actually measured. |
| Corpus grows past the rsync budget | Binary pruning + `.git` stripping; Risk 1.2. |

## 7. Integration Points

**This feature registers nothing in `plugin.xml`** — it ships no production code, no extension
points, no services. Its only build-level integration is the `test` task filter:

```kotlin
// build.gradle.kts — inside tasks.test { }, alongside the existing
// *Performance*/*Benchmark* and excludeExternalFixtureTests filters
if (!project.hasProperty("withCorpus")) {
    filter {
        excludeTestsMatching("*Corpus*")
        isFailOnNoMatchingTests = false
    }
}
if (project.hasProperty("recordCorpusBaseline")) {
    systemProperty("lunar.corpus.record", "true")
}
```

Consequences, all intentional:

- `./gradlew test` — zero corpus tests, unchanged runtime.
- `.github/workflows/build-plugin.yml` — unaffected; it never passes `-PwithCorpus`, so no
  `excludeExternalFixtureTests` entry is needed for these tests.
- `tooling/gce-builder/gce-builder.sh` — unchanged. `cmd_sync` already pushes `test/` with
  `rsync -aLz --delete` (`:124`), which carries `test/corpus/` to the builder for free.

## 8. Requirement Coverage

| Requirement | Priority | Implemented by (section) |
|-------------|----------|--------------------------|
| MAINT-33-01 Pinned Provisioning | M | §2.1, §2.5, §3.5, §4.1 |
| MAINT-33-02 Parse-Error Metric | M | §2.2, §3.1, §4.2 |
| MAINT-33-03 Require-Resolution Metric | M | §2.2, §3.1 |
| MAINT-33-04 Ratchet Gate | M | §2.3, §2.4, §2.4a, §2.6, §3.2, §6 |
| MAINT-33-05 Opt-In Execution | M | §7 |
| MAINT-33-06 Inspection-Hit Metric | S | §2.3, §3.3, §4.2 |
| MAINT-33-07 Ballast Inventory | S | §2.3, §3.4, §4.2 |
| MAINT-33-08 Corpus Expansion | S | §2.4, §4.1 |
| MAINT-33-09 Index Timing | C | §2.4 (advisory line printed, not baselined) |

## 9. Alternatives Considered

- **Golden files per corpus file** — rejected: authoring effort scales with corpus size, which
  defeats the point. Invariant metrics let the corpus grow for the cost of one manifest row.
- **A standalone `register<Test>("corpusTest")` task**, mirroring `redisIntegrationTest` — rejected
  on the evidence already recorded at `build.gradle.kts:239-243`: a standalone `Test` task fails at
  platform fixture init. The filter-exclusion pattern is the one that works here.
- **Git submodules for the corpus** — rejected: submodules land inside the repo and would be
  cloned by CI and by every contributor; the fetch script keeps them out-of-repo and optional.
- **Reusing `fileRequires()` (`LuaRequireExtraction.kt:21`) for the require metric** — rejected: the
  indexer handles the string-call form that the reference contributor misses, so it would have
  hidden BUG-389. The sweep must measure the user-facing path.
- **Reusing the existing `test/luacheck` checkout** — rejected: it is a clone of the internal
  `glimmer/luacheck` fork with commits absent upstream, so it is neither reproducible for others
  nor pinned; and repurposing it would couple this feature to `LuaRecursiveReferenceTest`.
- **JSON baselines** — rejected: the flat sorted `key=value` form makes a ratchet movement legible
  in a review diff, which is the artifact's main audience.

## 10. Open Questions

_None — feature has cleared the planning bar._
