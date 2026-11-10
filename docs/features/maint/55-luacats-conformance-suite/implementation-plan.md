---
id: "MAINT-55-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "MAINT-55"
folders:
  - "[[features/maint/55-luacats-conformance-suite/requirements|requirements]]"
---

# MAINT-55: Implementation Plan

One PR for Phases 1–3, one for Phase 4, one for Phase 5. Every Gradle run goes through the builder
(`builder-run libvirt "test --rerun --no-build-cache …"`), judged by the log text and the JUnit XML.
Nothing under `src/main` changes; a diff there fails the phase. Python tests:
`python3 -m unittest discover -s tooling/conformance -p 'test_*.py'`. There is no live-IDE surface, so
there is no `human-verification-checklists.md`.

### Phase 1: Format, scoring and ratchet [Must]
- **Tasks**:
  - [x] Create the classes of design §4 except `LuaCatsConformanceTest` and `ConformanceRunner`:
        `ConformanceCase`, `ConformanceCaseParser` (§2.1; it also rejects a rule outside
        `ConformanceRules.RULES` + `arg`), `ConformanceFinding`, `ConformanceOutcome`,
        `ConformanceScorer` (§3.2), `ConformanceDeviation`/`ConformanceDeviations` (§2.4),
        `ConformanceResults`/`ConformanceResultsJson` (§2.5), `ConformanceRatchet` (§3.5),
        `ConformanceBugs` (§3.7), `ConformanceRules` (§2.3, §3.3), `ConformancePaths`.
  - [x] Pure tests: TC-1 to TC-8, TC-17 and TC-19 to TC-21 in the test classes design §4 names.
  - [x] Create `src/test/resources/conformance/luacats/deviations.json` as
        `{"schema": 1, "deviations": []}`.
- **Exit criteria**: `run "test --rerun --no-build-cache --tests *Conformance*"` green; `run ktlintCheck`
  green; each listed TC's mutation turns it red, then reverted.

### Phase 2: Import [Must]
- **Tasks**:
  - [x] Create `tooling/conformance/import_luals.py` (design §3.1) and `test_import_luals.py` with
        TC-9 to TC-11 and their fixtures under `tooling/conformance/testdata/luals/`.
  - [x] Fetch the pin into scratch (never into the repo):
        `git clone --depth 1 --filter=blob:none --sparse https://github.com/LuaLS/lua-language-server.git <scratch>/luals`;
        `git -C <scratch>/luals sparse-checkout set --no-cone /LICENSE /script/proto/diagnostic.lua /test/diagnostics/`;
        `git -C <scratch>/luals fetch --depth 1 origin a58fc7b01e1746c46275c71b0e61bfed40a6296d`;
        `git -C <scratch>/luals checkout --detach FETCH_HEAD`.
  - [x] Run `python3 tooling/conformance/import_luals.py --luals <scratch>/luals --out src/test/resources/conformance/luacats`;
        commit `cases/`, `import.json`, `LICENSE-LuaLS`. Delete the clone.
  - [x] Attribution (design §5.4): `NOTICE` entry, `THIRD-PARTY.md` section and licence bullet;
        `ConformanceAttributionTest` (TC-16).
- **Exit criteria**: the Python unittest command green; `import.json` `codes` per code equals
  risks-and-gaps DR-01's `TEST` blocks (`redundant-parameter` its live blocks) and its `excluded`
  equals DR-01's config-dependent, foreign-marker and doc-line-marker columns; a second import run
  leaves `git status` clean; TC-16 green on the builder; each TC's mutation turns it red, then
  reverted.

### Phase 3: Runner and baseline [Must]
- **Tasks**:
  - [x] `ConformanceRunner` and `LuaCatsConformanceTest` (design §3.4); `ConformanceRunnerTest` with
        TC-12 to TC-14.
  - [x] `build.gradle.kts`: the `recordConformance` block (design §5.3).
  - [x] Add to `deviations.json` exactly the entries risks-and-gaps "Deviations and rulings" specifies:
        one per case id of each accepted row; the rejected missing-`self` row writes none.
  - [x] `run "test --rerun --no-build-cache --tests *LuaCatsConformance* -PrecordConformance"`; copy the
        builder's `build/conformance/recorded/results.json` to
        `src/test/resources/conformance/luacats/results.json` (`GCE_BUILDER_BACKEND=libvirt tooling/gce-builder/gce-builder.sh shell "cat /home/builder/lunar/build/conformance/recorded/results.json" > …`);
        confirm no case is `deviation-broken`. If one is, its row's `expect` is wrong: stop and report
        the case and its observed outcome to the supervisor; do not record.
  - [x] `CHANGELOG.md`: none — test-only.
- **Exit criteria**: full `run test` green with `LuaCatsConformanceTest` in its report; TC-15's
  mutation (`LuaTypeGraph.kt:860`) turns it red with `regressed missing-parameter-002: agree -> missed`,
  then reverted; each other TC's mutation turns it red, then reverted; `run ktlintCheck` green.

### Phase 4: Bugs [Should]
- **Tasks**:
  - [ ] File the disagreements by design §3.7 (the missing-`self` group's report requires the
        `t.init()` → `t:init()` quick fix): `loom-cli id mint BUG` per new group, a `report-bug`
        report listing the group's case ids and one case body as the reproduction, a roadmap row each.
  - [ ] Write `src/test/resources/conformance/luacats/bugs.json` (design §2.7) and enable the
        `ConformanceBugs.problems` check in `LuaCatsConformanceTest` (design §3.4 step 5).
- **Exit criteria**: full `run test` green; deleting one case id from `bugs.json` turns
  `LuaCatsConformanceTest` red with `unfiled <id>`, then reverted.

### Phase 5: Ranking [Should] — starts only once `tooling/corpus/rule-weights.json` is on `main` (MAINT-56 Phase 4)
- **Tasks**:
  - [ ] `tooling/conformance/conformance_rank.py` (design §3.6) and `test_conformance_rank.py` with
        TC-18.
  - [ ] Run it against the committed inputs; commit `ranking.md`.
- **Exit criteria**: the Python unittest command green; `python3 scripts/lint_docs.py` 0 errors on
  `ranking.md`; TC-18's mutations turn it red, then reverted.

## Verification Tasks
- Unit: TC-1 to TC-8, TC-16, TC-17, TC-19 to TC-21 (Kotlin, pure); TC-9 to TC-11, TC-18 (Python).
- Fixture: TC-12 to TC-14 (`ConformanceRunnerTest`); TC-15 (`LuaCatsConformanceTest`, the routine suite).
- Manual: bug grouping review in the Phase 4 PR.

## See Also
- [requirements.md](requirements.md) · [design.md](design.md) · [risks-and-gaps.md](risks-and-gaps.md)
