---
id: "MAINT-55-DESIGN"
title: "Technical Design"
type: "design"
parent_id: "MAINT-55"
folders:
  - "[[features/maint/55-luacats-conformance-suite/requirements|requirements]]"
---

# Technical Design: MAINT-55 — LuaCATS Conformance Suite

## 1. Architecture Overview

Nothing under `src/main` changes and no `plugin.xml` entry is added. Test code lives in package
`net.internetisalie.lunar.analysis.conformance` under `src/test/kotlin`; data under
`src/test/resources/conformance/luacats/`; tools under `tooling/conformance/`.

| Prior art | Used how |
|---|---|
| `LuaTypesSnapshot.forFile(file).getErrors()` (`LuaTypes.kt:310`) | the findings: every error except `ErrorSeverity.HYPOTHESIS` — what `LuaTypeAssignabilityInspection` and `LuaReturnTypeMismatchInspection` report between them (`TypeErrorClassification.kt:23`; risks-and-gaps DR-02) |
| `CorpusTarget.pin` / `restore` (`src/test/.../corpus/CorpusTarget.kt:30`, `:51`) | pins every run to 5.4 (DR-03) |
| `AuditKinds.classify` (`src/test/.../corpus/audit/AuditKinds.kt:18`), `LuaCatsDeclarations.isOptionalParam` (`LuaCatsDeclarations.kt:110`) | assigns a parameter-mismatch case to a MAINT-54/56 `arg.*` shape (§3.3) |
| `TypeGolden` record idiom (`TypeGolden.kt:28`, `build.gradle.kts:513`) | `-PrecordConformance` writes the baseline to `build/` for copying back by hand (§5.3) |
| Gson pretty printing (`AuditOutput.kt:13`) | results JSON (§2.5) |
| MAINT-56 `tooling/corpus/rule-weights.json` (MAINT-56 design §4.3) | read by the ranking (§3.6); never written |

The MAINT-54 audit harness is not extended: it injects errors into corpus code; this suite runs fixed
specification cases. The two share only `AuditKinds`.

## 2. Data

### 2.1 Case files (`src/test/resources/conformance/luacats/cases/<code>.lua`)
One file per in-scope LuaLS code (§2.2). Line 1 is the attribution header (§5.4). Each case starts
with a header line; its body is every following line up to the next header or end of file, with
trailing empty lines dropped.

- Header: `^-- ==== case ([a-z0-9-]+-[0-9]{3}) rule=([a-zA-Z.]+) source=(\S+)$`. A line starting
  `-- ==== case` that does not match is an error naming the file and its 1-based line.
- Lines before the first header are the preamble and are not parsed.
- `id` = `<code>-<nnn>`, `nnn` the zero-padded ordinal of the `TEST` block in the LuaLS file;
  `rule` = a §2.3 rule or `arg`; `source` = `<code>.lua#<ordinal>`. An id occurring twice across all
  files is an error.
- Markers: after trimming trailing whitespace, a body line matching `\s--\s(E\??)$` is a **must**
  line (`E`) or a **may** line (`E?`). Line numbers are 1-based within the body. Every other line must
  stay silent.
- The text given to the fixture is the body lines joined by `\n`, plus a final `\n`.

### 2.2 In-scope LuaLS codes
From risks-and-gaps DR-01: LuaLS's whole `type-check` group, and the `unbalanced` codes whose cases
carry LuaCATS tags. Each maps to a default rule:

| code | rule | code | rule |
|---|---|---|---|
| param-type-mismatch | `arg` (§3.3) | missing-fields | `field.missing` |
| assign-type-mismatch | `field.assign` / `assign.local` (§3.1 step 6) | undefined-field | `field.undefined` |
| return-type-mismatch | `return.mismatch` | inject-field | `field.inject` |
| missing-return-value, redundant-return-value | `return.count` | need-check-nil | `nil.check` |
| missing-return | `return.missing` | cast-local-type | `cast.local` |
| redundant-parameter | `arity.tooMany` | cast-type-mismatch | `cast.mismatch` |
| missing-parameter | `arity.tooFew` | | |

Excluded `unbalanced` codes: `redundant-value`, `unbalanced-assignments`.

### 2.3 Rules
A rule is the unit that is scored and ranked. Where a case exercises a MAINT-54 shape, the rule **is**
the shape name, so MAINT-56's `rule-weights.json` `shapes.<rule>` applies with no mapping:
`arg.primitive`, `arg.optional`, `arg.table`, `arg.record`, `arg.class`, `arg.array`, `arg.union`,
`arg.function`, `arg.generic`, `arity.tooMany`, `arity.tooFew`, `return.mismatch`, `field.assign`.
The other rules have no MAINT-54 shape and so no weight: `arg.other`, `assign.local`,
`field.missing`, `field.undefined`, `field.inject`, `nil.check`, `cast.local`, `cast.mismatch`,
`return.count`, `return.missing`. `ConformanceCaseParser.parse` rejects a header whose `rule` is in
neither list nor `arg`: `IllegalArgumentException` with message `<file>:<line>: unknown rule <rule>`.

### 2.4 Deviations (`deviations.json`)
```
{"schema": 1, "deviations": [{"case": "<id>", "rule": "<rule>", "policy": "<kebab-name>",
  "expect": "missed|false-report|misplaced", "reason": "<text>", "ref": "<BUG-n, feature id or file:line>",
  "decidedBy": "<person or agent id>", "decidedOn": "YYYY-MM-DD"}]}
```
Load errors: any field missing or blank; `expect` outside its three values; a `case` listed twice.
At run time `ConformanceDeviations.check(deviations, resolved)`, with `resolved` = case id → resolved
rule, returns in deviation-id order: `deviation <id> is for an unknown case` when `resolved` lacks the
id, else `deviation <id> has rule <rule> but the case resolves to <resolved>` when they differ. Any line
fails the run. The file exists from Phase 1 with `"deviations": []`.

### 2.5 Results (`results.json`, and `build/conformance/results.json`)
```
{"schema": 1, "luals": "<40-hex commit>", "level": "5.4",
 "cases": {"<id>": {"rule": "<rule>", "observed": "<outcome>", "outcome": "<outcome>",
                    "must": [<int>], "may": [<int>], "findings": ["<start>-<end>:<message>"]}},
 "rules": {"<rule>": {"<outcome>": <count>}}}
```
`observed` is §3.2's outcome before deviations; `outcome` after. Outcomes: `agree`, `missed`,
`false-report`, `misplaced`, `deviation`, `deviation-broken`. `rules` counts `outcome` per rule, only
non-zero entries. Maps are `TreeMap`s (sorted keys); `must`, `may` ascending; `findings` distinct and
sorted. Rendered with `GsonBuilder().disableHtmlEscaping().setPrettyPrinting()` plus a trailing `\n`.
`luals` is `import.json`'s `luals.commit`.

### 2.6 Import record (`import.json`)
```
{"schema": 1, "luals": {"repository": "https://github.com/LuaLS/lua-language-server",
  "commit": "<40-hex>", "license": "MIT"},
 "codes": {"<code>": {"blocks": <int>, "imported": <int>}},
 "excluded": [{"source": "<code>.lua#<n>", "reason": "config|foreign-marker|marker-on-doc-line"}]}
```
`json.dumps(obj, indent=2, sort_keys=True)` plus `\n`; `excluded` in file then ordinal order.

### 2.7 Bugs (`bugs.json`, Phase 4)
`{"schema": 1, "bugs": {"BUG-<n>": ["<case id>", ...]}}`, case ids sorted.

## 3. Algorithms

### 3.1 Import (`tooling/conformance/import_luals.py`)
`main(argv)`; `--luals <checkout>` `--out <dir>`; exit 0, or 2 with a message on stderr. `CODE_RULES` is
§2.2's table (code → rule, `None` for `assign-type-mismatch`); `EXCLUDED_UNBALANCED` its excluded list.
1. `commit` = `git -C <checkout> rev-parse HEAD`.
2. **Scope check** on `<checkout>/script/proto/diagnostic.lua`: for each match of
   `m\.register\s*\{(.*?)\}\s*\{(.*?)\}` (DOTALL), codes = `^\s*'([a-z0-9-]+)'` (MULTILINE) over group 1,
   group = `group\s*=\s*'([^']+)'` over group 2. Fail when a `CODE_RULES` key is in neither `type-check`
   nor `unbalanced`, when a `type-check` code has no `CODE_RULES` key, or when an `unbalanced` code is in
   neither `CODE_RULES` nor `EXCLUDED_UNBALANCED` — message `scope: <code> …`.
3. Per code in `CODE_RULES` (§2.2), read `test/diagnostics/<code>.lua` and scan
   `^config\.set\(\s*nil\s*,\s*'([^']+)'\s*,\s*([^)]*?)\s*\)|^TEST\s*\[(=*)\[(.*?)\]\3\]`
   (MULTILINE | DOTALL) in order. State starts at `BASELINE = {castNumberToInteger: "false",
   weakUnionCheck: "false", weakNilCheck: "false", checkTableShape: "false"}` (keys without `Lua.type.`,
   values compared as text). A `config.set` writes its value text (`nil` deletes the key). A `TEST` match increments the ordinal.
4. Body = group 4 minus one leading `\n` or `\r\n`. Exclude, first match wins: state ≠ `BASELINE` →
   `config`; body matches `<[?#]` → `foreign-marker`; a line containing `<!` whose left-stripped text
   starts with `---` → `marker-on-doc-line`.
5. Convert: split on `\n`, drop a final empty element; a line containing `<!` → delete every `<!`
   and `!>`, append ` -- E`; any other line → delete every `!>`.
6. Rule: `CODE_RULES[code]`, except `assign-type-mismatch`: anchor = the first converted line ending
   ` -- E` (marker removed), else the last line whose left-stripped text does not start with `--` and
   matches `(?<![=~<>])=(?!=)`, else `""`; rule = `field.assign` if the anchor matches
   `^\s*[A-Za-z_]\w*(\.[A-Za-z_]\w*|\[[^\]]*\])+\s*=(?!=)`, else `assign.local`.
7. Write `<out>/cases/<code>.lua`: the §5.4 header line, then per case the §2.1 header and body.
   Write `<out>/import.json` (§2.6) and copy `<checkout>/LICENSE` to `<out>/LICENSE-LuaLS`. Output is
   byte-identical on a re-run.

### 3.2 Scoring (`ConformanceScorer`)
A finding's span is `startLine..endLine`: the 1-based document lines of its element's
`textRange.startOffset` and of `endOffset - 1` (of `startOffset` when the range is empty).
`unmatched` = must lines no span contains; `stray` = findings whose span contains no must or may line.
- `observed`: both empty → `agree`; both non-empty → `misplaced`; only `unmatched` → `missed`;
  only `stray` → `false-report`.
- `outcome`: no deviation → `observed`; a deviation with `expect == observed` → `deviation`; else
  `deviation-broken`.

### 3.3 Parameter rule (`ConformanceRules.argRule`)
In a read action: `tag` = the first `LuaCatsParamTag` of `PsiTreeUtil.findChildrenOfType(file, …)`;
`comment` = `PsiTreeUtil.getParentOfType(tag, LuaCatsComment::class.java)`; `kind` =
`AuditKinds.classify(tag.argType.text, comment)`. No tag, no comment or `kind == null` → `arg.other`;
`Kind.PRIMITIVE` with `kind.optional || LuaCatsDeclarations.isOptionalParam(tag)` → `arg.optional`;
else `"arg." + kind.kind.name.lowercase()`.

### 3.4 Run (`LuaCatsConformanceTest`)
`setUp`: `super.setUp()`; `saved = ConformanceRunner.pin(project)`. `tearDown`: restore in `try`,
`super.tearDown()` in `finally`. `testConformanceRatchet` (on the test EDT):
1. Load every `cases/*.lua` sorted by name (§2.1), `deviations.json`, `import.json`'s commit.
2. Per case, `ProgressManager.checkCanceled()`; `findings = ConformanceRunner.findings(myFixture,
   case)`: `configureByText(FILE_NAME, case.text)` with `FILE_NAME = "conformance.lua"` for every case
   (DR-04), then `ReadAction.compute` over the snapshot errors; resolve the rule (§3.3 for `arg`);
   record its resolved rule; score (§3.2). After the loop, fail with every
   `ConformanceDeviations.check` line (§2.4), one per line, when any.
3. Build `ConformanceResults` (§2.5); write `build/conformance/results.json`.
4. `lunar.conformance.record == "true"` → also write `build/conformance/recorded/results.json` and pass.
5. Else read the committed `results.json` (absent → fail `no baseline; record with
   -PrecordConformance`); fail with every `ConformanceRatchet.violations` line, one per line, when any;
   print each `notes` line. From Phase 4, also fail on `ConformanceBugs.problems`.

### 3.5 Ratchet (`ConformanceRatchet`)
Over the union of case ids, in sorted order; `b` = baseline outcome, `a` = actual outcome. An id on
only one side produces exactly its `new case` or `removed case` line and no other; the remaining rows
apply only to ids on both sides:
| condition | line |
|---|---|
| id only in actual | violation `new case <id>; record` |
| id only in baseline | violation `removed case <id>; record` |
| `b == agree`, `a != agree` | violation `regressed <id>: agree -> <a>` |
| `a == deviation-broken` | violation `deviation broken <id>: observed <observed>` |
| `b` ∉ {`agree`, `deviation-broken`}, `a == agree` | violation `improved <id>: <b> -> agree; record` |
| `a == deviation`, `b != deviation` | violation `ruling not recorded <id>; record` |
| `a != b` and no row above applies | note `changed <id>: <b> -> <a>` |
A case can produce more than one line. `violations` and `notes` return them in this order.

### 3.6 Ranking (`tooling/conformance/conformance_rank.py`, Should)
Inputs: `rule-weights.json`, `results.json`, `bugs.json`. For rule `r`: `n` = cases with `outcome` ∉
{`deviation`, `deviation-broken`}; `D` = those with `outcome` ∈ {`missed`, `false-report`,
`misplaced`}; `rate = |D| / n` (null when `n = 0`); `w` = `shapes[r].pooled.exposurePerKloc`, null
when `r` is absent; `score = w × rate`, null when either is null. Exposure rather than MAINT-56's
`missedPerKloc`, because the suite measures its own miss rate and multiplying by MAINT-56's would count
misses twice. Order: non-null scores descending, then null scores; ties and nulls by `|D|`
descending, then rule name. Bug `b` with cases `C`: `score(b) = Σ_r score(r) × |C ∩ D_r| / |D_r|` over
rules with a non-null score and `D_r ∩ C` non-empty; null when no term exists. Write
`docs/features/maint/55-luacats-conformance-suite/ranking.md`: front matter (`id: "MAINT-55-RANKING"`,
`title: "Rule Ranking"`, `type: "results"`, `parent_id: "MAINT-55"`, `folders` as this file); a line
naming the weights' `lunarCommit` and the results' `luals`; table
`| rank | rule | exposurePerKloc | cases | disagreeing | rate | score | bugs |`; table
`| bug | score | rules | cases |` by score descending, nulls last, then id. Numbers to 4 places;
null → `-`. `main(["--weights", w, "--results", r, "--bugs", b, "--out", o])`; a missing input → exit 2,
`missing input: <path>`.

### 3.7 Filing disagreements (Phase 4, Should)
Take the cases whose `outcome` ∈ {`missed`, `false-report`, `misplaced`}. First assign the
mechanisms that already have open bugs: a case with a finding whose message matches `^\{ \.\.\. \} is not assignable
to .*\[\]$` → [[BUG-481]]; a `field.assign` case observed `missed` → [[BUG-44]]. Group the rest by
`(rule, observed)`. The `arity.tooFew`/`missed` group (`missing-parameter-010`, the rejected missing-`self`
deviation) is filed with a quick fix in its requirement: rewrite the dot call `t.init()` to `t:init()`.
Each group gets one BUG minted with
`loom-cli id mint BUG` and written with the `report-bug` skill, listing its case ids; a roadmap row is
added with it. Record every group in `bugs.json`. `plan-bug` may later split or merge a bug; whoever
does updates `bugs.json`. `ConformanceBugs.problems`: a listed case that does not exist, or whose
outcome is not one of those three → `<case> is filed under <bug> but is <outcome>`; a case with one of
those outcomes that no bug lists → `unfiled <case>`; a case under two bugs → `<case> is filed twice`.

## 4. Classes (all `src/test/kotlin/net/internetisalie/lunar/analysis/conformance/`)

| Class | Key signatures |
|---|---|
| `ConformanceCase` | `data class ConformanceCase(val id: String, val rule: String, val source: String, val body: List<String>, val must: Set<Int>, val may: Set<Int>) { val text: String }` |
| `ConformanceCaseParser` | `object`; `fun parse(text: String, fileName: String): List<ConformanceCase>`; `fun loadAll(dir: File): List<ConformanceCase>` |
| `ConformanceFinding` | `data class ConformanceFinding(val startLine: Int, val endLine: Int, val message: String) { fun render(): String }` |
| `ConformanceOutcome` | `enum class ConformanceOutcome(val json: String)`: `AGREE`, `MISSED`, `FALSE_REPORT`, `MISPLACED`, `DEVIATION`, `DEVIATION_BROKEN`; `companion fun of(json: String)` |
| `ConformanceScorer` | `object`; `fun observed(case: ConformanceCase, findings: List<ConformanceFinding>): ConformanceOutcome`; `fun outcome(observed: ConformanceOutcome, deviation: ConformanceDeviation?): ConformanceOutcome` |
| `ConformanceDeviation(s)` | `data class ConformanceDeviation(case, rule, policy, expect: ConformanceOutcome, reason, ref, decidedBy, decidedOn: String)`; `object ConformanceDeviations { fun load(file: File): Map<String, ConformanceDeviation>; fun check(deviations: Map<String, ConformanceDeviation>, resolved: Map<String, String>): List<String> }` |
| `ConformanceResults` | `data class CaseResult(val rule: String, val observed: String, val outcome: String, val must: List<Int>, val may: List<Int>, val findings: List<String>)`; `data class ConformanceResults(val schema: Int, val luals: String, val level: String, val cases: TreeMap<String, CaseResult>, val rules: TreeMap<String, TreeMap<String, Int>>)`; `object ConformanceResultsJson { fun render(results: ConformanceResults): String; fun parse(text: String): ConformanceResults; fun tally(cases: Map<String, CaseResult>): TreeMap<String, TreeMap<String, Int>> }` |
| `ConformanceRatchet` | `object`; `fun violations(baseline: ConformanceResults, actual: ConformanceResults): List<String>`; `fun notes(baseline: ConformanceResults, actual: ConformanceResults): List<String>` |
| `ConformanceBugs` | `object`; `fun load(file: File): Map<String, List<String>>`; `fun problems(results: ConformanceResults, bugs: Map<String, List<String>>): List<String>` |
| `ConformanceRules` | `object`; `fun argRule(file: PsiFile): String`; `val RULES: Set<String>` (§2.3) |
| `ConformanceRunner` | `object`; `const val FILE_NAME = "conformance.lua"`; `fun pin(project: Project): SavedTarget` (= `CorpusTarget.pin(project, LuaLanguageLevel.LUA54)`); `fun findings(fixture: CodeInsightTestFixture, case: ConformanceCase): List<ConformanceFinding>` |
| `ConformancePaths` | `object`; `fun root(): File` = `File(System.getProperty("user.dir"), "src/test/resources/conformance/luacats")`; `cases()`, `deviations()`, `results()`, `importRecord()`, `bugs()`, `actual()` (`build/conformance/results.json`), `recorded()` |
| `LuaCatsConformanceTest` | `@RunWith(JUnit4::class) class LuaCatsConformanceTest : BasePlatformTestCase()`; `@Test fun testConformanceRatchet()` |

Pure tests (plain JUnit4): `ConformanceCaseParserTest`, `ConformanceScorerTest`,
`ConformanceDeviationsTest`, `ConformanceResultsJsonTest`, `ConformanceRatchetTest`,
`ConformanceBugsTest`, `ConformanceAttributionTest`. Fixture test: `ConformanceRunnerTest :
BasePlatformTestCase()`. Python: `tooling/conformance/test_import_luals.py`,
`tooling/conformance/test_conformance_rank.py`, fixtures under `tooling/conformance/testdata/`.

## 5. Integration

### 5.1 Threading and memory
Everything runs in the JUnit test thread the light fixture provides (the EDT); PSI reads go through
`ReadAction.compute`. No service, listener or long-lived object holds a `Project`, `PsiFile` or
`Editor`; `ConformanceRunner` and the other objects are stateless.

### 5.2 Engineering contract
Functions ≤ 30 logic lines and ≤ 3 parameters (`Project`, `Disposable` excluded); no `!!`; no
wildcard imports. `ConformanceRunner` keeps PSI access out of the scorer, which sees only
`ConformanceFinding`s.

### 5.3 Build
In the `test` task's block beside `recordTypeGolden` (`build.gradle.kts:513`):
```kotlin
if (project.hasProperty("recordConformance")) {
    systemProperty("lunar.conformance.record", "true")
}
```
`LuaCatsConformanceTest` matches no exclusion (`*Corpus*`, `*FalseNegativeAudit*`, … at
`build.gradle.kts:411-478`) and runs in the routine `run test` (cost: DR-06).
Python tests: `python3 -m unittest discover -s tooling/conformance -p 'test_*.py'`.

### 5.4 Attribution
- Line 1 of each case file: `-- LuaCATS conformance cases, adapted from LuaLS/lua-language-server
  test/diagnostics/<code>.lua @ <first 8 hex of commit> (MIT); see LICENSE-LuaLS`.
- `src/test/resources/conformance/luacats/LICENSE-LuaLS`: LuaLS's `LICENSE`, verbatim.
- `NOTICE`, "MIT License components": an entry `lua-language-server (LuaLS)`, `Copyright (c) 2018
  最萌小汐`, "Diagnostic test cases adapted as the LuaCATS conformance suite (test data only)."
- `THIRD-PARTY.md`, "MIT License": a `### lua-language-server (LuaLS)` section with Copyright,
  License, Version (the commit), Usage, Files (`src/test/resources/conformance/luacats/cases/*.lua`),
  Upstream; and a "License texts" bullet naming `LICENSE-LuaLS`.

## 6. Requirement Coverage
-01 §2.1 · -02 §2.2, §2.6, §3.1 · -03 §2.3, §2.5, §3.2–§3.4 · -04 §3.5 · -05 §2.7, §3.7 ·
-06 §2.4, §3.2 · -07 §3.4, §4 `ConformanceRunner` · -08 §5.4 · -09 §3.6.

## 7. Alternatives Considered
- **Keep LuaLS's `<!…!>` range markers:** the file stops being valid Lua, and ranges compare anchors
  LuaCATS does not specify (DR-02).
- **One file per case:** hundreds of files with a one-line header each; a file per code mirrors
  upstream and keeps a re-import reviewable.
- **Run LuaLS as an oracle for the codes a case does not test:** a runtime dependency on another
  language server for a report that needs none; a stray report is reviewed through Phase 4 instead.
- **Case-free scoring (any error anywhere passes):** hides the parameter-anchor difference DR-02
  found.
- **Fail on any outcome change:** turns known disagreements into a build gate, which -04 forbids.

## 8. Open Questions

_None._
