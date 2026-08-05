---
id: "MAINT-33"
title: "33: Corpus Sweep — Regression Ratchet Across Pinned Real-World Lua Projects"
type: "feature"
parent_id: "MAINT"
status: "done"
priority: "medium"
folders:
  - "[[features/maint/requirements|requirements]]"
---

# MAINT-33: Corpus Sweep — Regression Ratchet Across Pinned Real-World Lua Projects

## Overview

Lunar's unit suite is deep on synthetic single-file snippets and blind to real project *shapes*.
This feature adds a **corpus sweep**: pinned checkouts of real open-source Lua projects that the
plugin is measured across, with the counts compared to a recorded baseline so the suite fails only
when a number gets **worse**. Because the assertions are invariant-style (nobody authors
per-file expectations), the corpus can be large and can grow cheaply.

A working prototype already exists (luacheck + luarocks, parse-error and unresolved-`require`
metrics) and found [BUG-389](../../bug-fixes/389-string-call-require-no-reference/bug-report.md) on
its first run. This feature hardens that prototype and adds the two metrics that make the corpus
an instrument rather than a crash detector: **inspection-hit counts** (false-positive mining) and
**ballast inventory** (integration discovery).

Parent epic: [MAINT](../requirements.md).

## Scope

### In Scope
- A pinned, reproducible corpus provisioning mechanism (manifest + fetch script), out-of-repo.
- A headless sweep that measures parse errors, `require` resolution, inspection hits, and non-Lua
  file inventory across each corpus project.
- A ratchet gate: fail on regression, report improvements, hard-fail if the checkout drifted.
- Opt-in execution on the builder, excluded from the routine `test` loop and from CI.
- Expansion of the corpus from 2 to 3 projects (ZeroBrane Studio). KOReader was admitted, measured
  and parked on sweep time — see risks-and-gaps "Technical Debt & Future Work" for the numbers.

### Out of Scope
- Fixing anything the sweep finds. Findings are filed as separate `BUG-*` reports (BUG-389 is the
  first); the ratchet only records the current floor.
- Adding support for the integrations the ballast inventory surfaces (Teal, LDoc `config.ld`,
  `.luacov`). Those become their own features; this feature only *reports* them.
- Running the corpus in Gitea CI. CI checkouts have no out-of-repo `test/` tree and the runner
  cannot absorb the clones — the sweep stays a builder-only gate.
- Per-file golden expectations of any kind.
- Live-IDE verification, which is covered by
  [vnc-multi-project-smoke-tests.md](../vnc-multi-project-smoke-tests.md).

## Functional Requirements

| ID | Requirement | Priority | Description |
|----|-------------|----------|-------------|
| MAINT-33-01 | **Pinned Provisioning** | M | A manifest pins each project to a commit SHA; a fetch script materialises it reproducibly into `test/corpus/<name>`, stamped and re-runnable as a no-op. |
| MAINT-33-02 | **Parse-Error Metric** | M | Count `PsiErrorElement`s across every indexed `.lua` file, and record which files carry them. |
| MAINT-33-03 | **Require-Resolution Metric** | M | Count recognised `require` references and how many resolve to nothing. |
| MAINT-33-04 | **Ratchet Gate** | M | Compare against a recorded baseline: fail on any gated metric increasing; report decreases without failing; hard-fail if the corpus commit or file count differs from the baseline. |
| MAINT-33-05 | **Opt-In Execution** | M | Excluded from the default `test` task; enabled by `-PwithCorpus`; baselines rewritten by `-PrecordCorpusBaseline`; never runs in CI. |
| MAINT-33-06 | **Inspection-Hit Metric** | S | Per-inspection warning counts across the corpus, gated like the other defect metrics. |
| MAINT-33-07 | **Ballast Inventory** | S | Inventory non-`.lua` files by extension/filename and flag those no registered Lunar file type claims. |
| MAINT-33-08 | **Corpus Expansion** | S | Add ZeroBrane Studio with its own baseline. **Penlight admitted 2026-08-04** (`1.15.0`, `e0bc8f7f`, roots `lua,spec`, `moduleRoot=lua`): 56 indexed files, parseErrors 0, requires 100 / unresolved **0**. It carries the LDoc constructs BUG-393 was found through, at an eighth of KOReader's cost. KOReader remains measured and parked — it fits on disk (12 MB) but sweeps in 10.2 min, pushing the corpus past its 10-minute ceiling; it found BUG-393 on its one run. See risks-and-gaps "Technical Debt & Future Work" for the measurements. |
| MAINT-33-09 | **Index Timing** | C | Record wall-clock for the sweep per project as an advisory (ungated) metric. |
| MAINT-33-10 | **Per-Symbol Breakdown** | S | For each inspection, record the symbols it fired on most often (capped per inspection), so a missing-definitions problem is distinguishable from a resolution defect. Reported, never gated. |

## Detailed Specifications

### MAINT-33-01: Pinned Provisioning

The manifest is JSON at `tooling/corpus/corpus.json`, one entry per project (was positional TSV until 2026-08-05 — see BUG-407), fields:

| Column | Meaning |
|---|---|
| `name` | Corpus id; also the directory under `test/corpus/`. |
| `url` | Clone URL. |
| `commit` | **Commit SHA resolved from a release tag.** Branches are forbidden — the ratchet requires a byte-identical tree across runs. |
| `roots` | Comma-separated subdirectories to index. Narrower than the checkout, so vendored/build trees never enter the measurement. |
| `prune` | Comma-separated directories deleted after checkout. **Binaries only.** Optional. |
| `luaLevel` | The `LuaLanguageLevel` the sweep pins for this project (e.g. `LUA51`). Optional; defaults to `LUA54`, matching `LuaProjectSettings.kt:46`. Required for the inspection metric to be comparable — see MAINT-33-06. |
| `moduleRoot` | The subdirectory module names resolve against, put on the project's `package.path` for the sweep. Optional; defaults to null, meaning "resolve from the checkout root" — which is what all three pinned projects do. Needed when a project names modules relative to a subtree (KOReader's `require("ui/uimanager")` means `frontend/ui/uimanager.lua`); without it, every such require reads unresolved and the metric measures the manifest's ignorance rather than the resolver. The patterns must point at the **on-disk checkout**, not the copied fixture tree: `LuaModuleFileResolver.findByPath` consults `LocalFileSystem` only, while `copyDirectoryToProject` materialises into `temp://`. |

Blank lines and `#` comments are skipped. Rows have ≥4 columns; `prune` is optional.

`test/corpus/` sits under the tracked `test` symlink (→ `../test`), so checkouts are never
committed and reach the builder via the existing `rsync -aL` of the `test/` tree
(`tooling/gce-builder/gce-builder.sh:124`). The fetch strips `.git` to keep that rsync cheap, and
writes `test/corpus/<name>/.corpus-sha` as the on-disk pin stamp.

**Prune is binaries-only** because the non-Lua files in a real project are the record of which
ecosystem tools it uses — the input to MAINT-33-07. `luarocks/win32` (12 `.exe` + 10 `.dll`,
11 MB) qualifies; `docs/`, `*.rockspec`, `config.ld` do not.

### MAINT-33-02: Parse-Error Metric

Per indexed file, the count of `com.intellij.psi.PsiErrorElement` descendants. The baseline also
records the *set* of files with a non-zero count, sorted, so a regression is triageable from the
baseline diff alone without re-running.

A non-zero floor is expected and correct: luacheck ships deliberately-malformed samples
(`spec/samples/utf8_error.lua`, `spec/samples/compound_operators.lua`). The metric is a ratchet,
not an assertion of zero.

### MAINT-33-03: Require-Resolution Metric

Two counts: how many `require` references the plugin *recognises*, and how many of those resolve
to nothing. Both are meaningful — a collapse in the first number is itself a defect signal, which
is exactly how BUG-389 surfaced (3 recognised across 132 luacheck files, against ~155 actual call
sites).

`requires` is identity-checked rather than gated, because it may legitimately *rise* when a
resolution bug is fixed; see MAINT-33-04.

### MAINT-33-04: Ratchet Gate

Gated metrics (a rise fails the build): `parseErrors`, `unresolvedRequires`, `highlightFailures`,
and — **only while `highlightFailures` is zero on both sides** — each per-inspection count from
MAINT-33-06. See design §3.2 step 2: BUG-390 makes those counts non-reproducible run to run, so
gating them today would be flaky.

Identity-checked metrics (any change fails, with a "re-record" instruction): `commit`, `files`,
`requires`. These are facts about the corpus or about recognition coverage, not defect counts; a
change means the comparison is no longer apples-to-apples.

Decreases in a gated metric print an `IMPROVED` line and do **not** fail — a green run after a fix
should not require a doc change in the same commit, but the operator is told to re-record.

### MAINT-33-06: Inspection-Hit Metric

The likeliest user-perceived defect in a corpus this size is not a crash but a **false positive**:
`LuaUndeclaredVariable` firing on a legitimate global, `LuaUnusedLocal` on a deliberate one. This
metric counts warnings per inspection so that regressions in the noise floor are caught the same
way parse errors are.

Attribution is exact, via `HighlightInfo.getInspectionToolId()` (see design §3.3); infos with a
null id are counted under a reserved `unattributed` key rather than dropped — **net of** the
`PsiErrorElement` syntax errors, which produce null-id infos too and are already gated by
MAINT-33-02. Without that subtraction every parse-error regression would fail two metrics at once.
The sweep pins each
project's language level from the manifest, because the level selects which stdlib globals are
known and would otherwise make every count a function of a default.

### MAINT-33-07: Ballast Inventory

**Ballast is exactly the complement of what the sweep indexes**: a file is ballast unless it has
extension `lua` *and* lies under a declared root. Both conditions matter — `tlconfig.lua` is
ballast despite being `.lua` (it sits outside the roots and is never parsed), and the 107 `.tl`
files inside `luarocks/src` are ballast despite being under a root. Counts are therefore
whole-checkout counts.

Each ballast file is grouped (design §3.4 gives the exact key rule: base name when it has no dot
after position 0, else the lowercase suffix after the last dot — so `.luacov` → `.luacov` and
`config.ld` → `ld`) and marked **claimed** or **unclaimed** by whether any registered file type
claims it.

The first two projects already yield the unclaimed groups `tl` (117 — Teal, whose `tlconfig.lua`
is separately present but *claimed*, since Lunar registers the `lua` extension), `ld` (LDoc's
`config.ld`, in luarocks) and `.luacov` (luacov's config, in luacheck; distinct from the
`luacov.report.out` we already claim at `plugin.xml:618`).

**Interpretation is gated on DR-05.** The `FileTypeManager` inside a `BasePlatformTestCase` holds
the platform core types plus the plugin under test, *not* the bundled Markdown/YAML plugins a real
IDE has — so `md`, `yml` and friends will report unclaimed as a fixture artefact. DR-05 produces
the ignore list; until then the inventory is recorded but not read as a finding list.

## Behavior Rules

- The sweep never mutates the corpus checkout; it copies the declared roots into the test fixture.
- A corpus whose `.corpus-sha` differs from the manifest fails immediately with the fetch command
  in the message — a stale checkout must never be silently measured.
- A missing baseline fails with the record command in the message; it is never auto-created during
  a gating run.
- Recorded baselines are echoed to stdout as well as written, because the suite runs on a remote
  builder and the console is the reliable retrieval path.

## Test Cases

| # | Requirement | Given (input) | When (action) | Then (expected) |
|---|-------------|---------------|---------------|-----------------|
| 1 | MAINT-33-01 | Empty `test/corpus/` | Run `tooling/corpus/fetch-corpus.sh` | `test/corpus/luacheck` and `test/corpus/luarocks` exist, contain no `.git`, and each `.corpus-sha` equals the manifest `commit` |
| 2 | MAINT-33-01 | Corpus already at the pin | Re-run the fetch script | No network fetch; both projects logged as "already at <sha> — skipping"; exit 0 |
| 3 | MAINT-33-01 | Manifest row for luarocks with `prune=win32` | Run the fetch script | `test/corpus/luarocks/win32` absent; `src` and `spec` present |
| 4 | MAINT-33-02 | luacheck v1.2.0 corpus | Run the sweep | `parseErrors=3`; `parseErrorFile` lines are exactly `spec/samples/compound_operators.lua` and `spec/samples/utf8_error.lua` |
| 5 | MAINT-33-03 | luarocks v3.12.2 corpus | Run the sweep | `requires=606`, `unresolvedRequires=12` |
| 6 | MAINT-33-04 | Baseline `parseErrors=3`, sweep observes 4 | Run the gating sweep | Test fails; message contains `parseErrors: baseline 3 → observed 4` |
| 7 | MAINT-33-04 | Baseline `parseErrors=3`, sweep observes 2 | Run the gating sweep | Test **passes**; stdout contains `IMPROVED` and the re-record instruction |
| 8 | MAINT-33-04 | A baseline file whose `commit=` differs from the observed metrics' — i.e. the checkout is stamped at the manifest's pin, and only the *recorded baseline* is stale | Call `CorpusGuards.assertRatchet` | Fails with "recorded against a different corpus commit; re-record it". Note the manifest is **not** re-pinned: that would trip `assertCorpusFetched` first, with a different message |
| 9 | MAINT-33-04 | `test/corpus/luacheck` absent | Run the gating sweep | Test fails with the `tooling/corpus/fetch-corpus.sh` instruction |
| 10 | MAINT-33-05 | No Gradle properties | `./gradlew test` | No `*Corpus*` test executes; suite result unchanged |
| 11 | MAINT-33-05 | `-PwithCorpus -PrecordCorpusBaseline` | `./gradlew test --tests *Corpus*` | `src/test/resources/corpus/{luacheck,luarocks}.baseline` written, and their content echoed to stdout |
| 12 | MAINT-33-06 | luacheck corpus, baseline `LuaUndeclaredVariable=N` | Sweep observes `N+1` | Test fails naming `LuaUndeclaredVariable` |
| 13 | MAINT-33-07 | luarocks corpus | Run the sweep | `ballast.unclaimed.tl=117` (whole-checkout count — 107 of them live *inside* `src/`, and are ballast because they are not `.lua`), `ballast.unclaimed.ld=1` (`config.ld`), `ballast.claimed.rockspec=31`, and `ballast.claimed.lua=1` (`tlconfig.lua` — ballast because it is outside `roots`, claimed because `plugin.xml:99` registers `lua`) |
| 13b | MAINT-33-07 | luacheck corpus | Run the sweep | `ballast.claimed..luacheckrc=3` and `ballast.claimed..busted=1` (exact names, `plugin.xml:100`); `ballast.claimed.lua=3` (`bin/`, `build/bin/`, `scripts/` — outside the roots; the `lua` group is per-project, 1 for luarocks); `ballast.unclaimed.luacheckrc=18` — luacheck's `*_config.luacheckrc` fixtures, genuinely unsupported since only the exact name is registered; `ballast.unclaimed.rockspec=54` — **a false signal from the all-members rule**, see risks Gap 2.3 |
| 14 | MAINT-33-08 | ZeroBrane Studio corpus (wxLua IDE, global-heavy 5.1) | Run the sweep | Sweep completes; a baseline is recorded; its floors are per-project, never a threshold shared with luacheck/luarocks |
| 15 | MAINT-33-06 | luacheck corpus, manifest `luaLevel=LUA51` | Run the sweep | `LuaProjectSettings.getInstance(project).state.languageLevel == LuaLanguageLevel.LUA51` during the sweep; the recorded `inspection.LuaLanguageLevel` count is the 5.1 one, not the LUA54-default one |
| 17 | MAINT-33-10 | ZeroBrane corpus, whose `LuaUndeclaredVariable` count is 1009 | Run the sweep | `symbol.LuaUndeclaredVariable.*` keys are recorded, at most 10 per inspection, and their sum is ≤ the inspection's own count; a symbol containing dots (`ide.config`) round-trips intact |
| 16 | MAINT-33-09 | Any corpus project | Run the sweep | An advisory `[corpus:<name>] elapsedMs=<n>` line is printed; **no** `elapsed` key appears in the baseline and no comparison gates on it |

## Acceptance Criteria

- [ ] MAINT-33-01: `fetch-corpus.sh` provisions every manifest row reproducibly, stamps it, and is a no-op on re-run.
- [ ] MAINT-33-02/03: A sweep over luacheck and luarocks produces the counts in TC 4 and TC 5.
- [ ] MAINT-33-04: TC 6, 7, 8 and 9 each behave as specified.
- [ ] MAINT-33-05: `./gradlew test` with no properties runs zero corpus tests; CI is unaffected.
- [ ] MAINT-33-06: Per-inspection counts are recorded and gated for all corpus projects.
- [ ] MAINT-33-06: TC 15 — the pinned `luaLevel` is in effect during the sweep.
- [ ] MAINT-33-07: TC 13 and TC 13b — the inventory reports Teal, `ld` and `.luacov` as unclaimed
      and `rockspec`/`.luacheckrc`/`.busted` as claimed, with DR-05's ignore list applied.
- [ ] MAINT-33-08: ZeroBrane Studio has a recorded baseline.
- [ ] MAINT-33-09: TC 16 — timing is printed as advisory output and never enters a baseline.
- [ ] The full unit suite is green on the builder with and without `-PwithCorpus`.

## Non-Functional Requirements

- **Runtime**: the two-project sweep measured 63.5 s. The four-project sweep with inspections must
  stay under 10 minutes on the builder; MAINT-33-09 records the number so drift is visible.
- **Rsync budget**: the corpus must stay under ~50 MB on disk, since `cmd_sync` pushes the whole
  dereferenced `test/` tree on every builder run. Enforced by `.git` stripping and binary pruning.
- **Threading**: the sweep is a `BasePlatformTestCase`; PSI access follows the platform test
  threading rules already used by `LuaRecursiveReferenceTest`. No production threading impact —
  this feature ships no production code.
- **CI**: zero impact. Corpus tests are excluded by default and `-PwithCorpus` is never passed in
  `.github/workflows/build-plugin.yml`.

## Dependencies

- The out-of-repo `test/` fixture tree and its `rsync -aL` push (`gce-builder.sh:120-126`).
- The builder VM; the sweep is not runnable locally (`./gradlew` is not used directly per the
  engineering guide).
- Independent of [BUG-389](../../bug-fixes/389-string-call-require-no-reference/bug-report.md),
  but fixing that bug requires re-recording the luacheck baseline.

## See Also
- Design: [design.md](design.md)
- Plan: [implementation-plan.md](implementation-plan.md)
- Risks: [risks-and-gaps.md](risks-and-gaps.md)
- Checklists: [human-verification-checklists.md](human-verification-checklists.md)
