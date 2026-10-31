---
id: "MAINT-45-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "MAINT-45"
folders:
  - "[[features/maint/45-performance-records-per-commit/requirements|requirements]]"
---

# MAINT-45: Implementation Plan

## Gates

- **Unit suite**: `tooling/gce-builder/gce-builder.sh run "test --rerun --no-build-cache"`, then
  `run ktlintCheck` on its own. Never pipe either through `tail`.
- **Workloads**: `run "test --rerun --no-build-cache -PwithPerfRecords --tests '*PerfRecord*'"`.
- **Copy-back**: as MAINT-48's plan. Read a copied file only after `BUILD SUCCESSFUL`.
- **Python**: `cd tooling/ctrf && python3 -m unittest test_perf_ctrf test_junit_ctrf`.
- **Mutants**: each requirements "Red under" entry is applied and reverted with the
  `temporary-edits` skill, and observed red, locally: the Python ones with the Python gate, the
  Kotlin and Gradle ones on the builder.
- **CI mutants**: at most one, named in Phase 3.
- **Status while in flight**: front-matter `status` becomes `in_progress` with Phase 1's PR and stays there
  until Close-out. Each phase's PR sets its requirements' Status:

| Phase | `Full` | `Partial` |
|---|---|---|
| 1 | MAINT-45-01, -03 | MAINT-45-02 (no `WOBBLE` printed yet) |
| 2 | MAINT-45-02, -04, -05, -06 | MAINT-45-15 (the switch is live on a push; a PR compares nothing yet) |
| 3 | MAINT-45-07, -09, -10, -11, -12, -15 | MAINT-45-08 (a `RISE` prints and cannot fail), -16 (the command exists; no `STABLE` yet) |
| 4 | MAINT-45-13 | |
| 5 | MAINT-45-08, -16 | |

## Phases

Each phase is one PR. Each leaves `main` working without the next. Phase 3 needs Phase 2 merged,
because it needs a record on `main`. Phase 5 needs Phase 3 merged and the condition in its task to hold,
so it may wait some time after Phase 4.

### Phase 1: Workloads [Must]
- **Tasks**:
  - [x] Edit `build.gradle.kts` per design §2.1.
  - [x] Create the test package per design §2.2, §3.1, §3.2 and §4.1.
- **Exit criteria**: TC 1–4, 30–34 and 51 hold and each of their mutants is observed red. TC 51's check, run
  on a copied-back directory, is that this prints nothing:
  ```bash
  python3 -c "import json,glob;[print(f) for f in glob.glob('build/perf-records/*.json') for r in [json.load(open(f))] if (r['counts'],r['outputs'])!=(r['warm']['counts'],r['warm']['outputs'])]"
  ```
  The workloads gate run twice gives byte-equal `counts` and `outputs` across the two copied-back
  directories. Those values equal DR-16's when compared at the production code DR-16 was measured on
  (`960e67ff`); otherwise a difference caused by a `src/main` merge since then is re-recorded into DR-16 in
  this phase's PR. The unit suite has no failing test the pre-edit run lacked, and
  ktlintCheck passes.

### Phase 2: Store from `main` [Must]
- **Tasks**:
  - [x] Create `tooling/ctrf/perf_ctrf.py` (`assemble`, `read_mode`, `wobble_lines`, `gate`, and `ci`'s
        steps 1–5, with no step 6, so every event but `push` takes step 5; design §2.3, §3.3, §3.6, §4.2) and
        `tooling/ctrf/test_perf_ctrf.py` with TC 5–8, 35–38 and 61–63.
  - [x] Create `tooling/perf/gate-mode` holding `report-only` (design §2.4).
  - [x] Add the "Performance records" step and the report line (design §2.5, Gitea) and the header
        line (GitHub).
  - [x] Add `tooling/ctrf/README.md` lines for the subcommands.
- **Exit criteria**: the Python gate passes and each mutant of those test cases is observed red. `assemble` of a
  copied-back Phase 1 run validates against the schema MAINT-40 design §4.2 pins, downloaded from that tag and
  path to `build/ctrf.schema.json` and checked with `sha256sum` against that hash (DR-07). This exits 0:
  ```bash
  python3 -c "import json,jsonschema;jsonschema.validate(json.load(open('build/perf-ctrf/perf.ctrf.json')),json.load(open('build/ctrf.schema.json')))"
  ```
  The step reads as design §2.5. After merge: `loom-cli artifact resolve --commit <merge sha> --check a3 --suite perf --json`
  exits 0, and the record's `environment.extra.cpuModel` is a real model name. The merge run's log
  gives this step's duration, recorded in DR-04's row. This phase's own PR run logs
  `::warning::perf: event pull_request neither stores nor compares`, and `resolve --suite perf` on its head exits 3.

### Phase 3: Compare on PRs [Must]
- **Tasks**:
  - [x] Add `diff`, `fetch`, `read_accepted`, `accepted_at`, `own_entries`, `deepen`, `baseline`, `compare`
        and `ci`'s step 6 (design §3.4–§3.6). Create `tooling/perf/accepted-increases.json`.
  - [x] Add `stability` (design §3.8), and `tooling/ctrf/README.md` lines for this phase's subcommands.
  - [x] Add TC 9–27, 39–50, 52–60 and 64–73 to `test_perf_ctrf.py`.
  - [x] In MAINT-38's `requirements.md`, change the MAINT-38-09 row's description to point at MAINT-45.
- **Exit criteria**: the Python gate passes and each mutant of those test cases is observed red. This
  PR's own run prints `perf: gate-mode report-only`, a `baseline` line and a diff with no `RISE`.
  `tooling/perf/gate-mode` still holds `report-only` at merge.
- **The one CI mutant**: on this PR, push a commit that sets `tooling/perf/gate-mode` to `gating` and adds
  `builder.mark().drop()` as the first statement after the `recursion_guard_` line of
  `LuaParserUtil.parseSuffixedExpr`. Expected: the job goes red, the log has `RISE` lines for the parse
  workloads, and the job's final step prints the `::error::` line. Then revert it with a commit and see
  the job go green. This is the one case no local test reaches: that a real count rise travels through
  the real step to a red job. Record the run IDs in DR-12.

### Phase 4: Series report [Should]
- **Tasks**:
  - [ ] Add `series` (design §3.7) and TC 28–29.
  - [ ] Add a `tooling/ctrf/README.md` line, and a pointer in `docs/profiling.md`.
- **Exit criteria**: the Python gate passes and each mutant is observed red. Run once locally with
  store credentials over `main`, and paste the head of its output into DR-13.

### Phase 5: Turn gating on [Must]
- **Condition**: the command in design §3.9 prints `STABLE`. Run it when `main` has enough records; DR-21
  gives the expected wait. A `NOT_YET` means wait, and it is never a reason to change `STABILITY_PAIRS`,
  `STABILITY_CROSS_MODEL_PAIRS` or `COUNT_PATHS`. A `BREAK` is a finding: Risk 1.1 says what follows.
- **Tasks**:
  - [ ] Paste that output into DR-17.
  - [ ] Change `tooling/perf/gate-mode` to `gating`, and nothing else outside the docs.
- **Exit criteria**: this PR's own run prints `perf: gate-mode gating` and a diff with no `RISE`. After
  merge, the merge run stores its record and the job is green.

## Close-out

Only when every phase's exit criteria hold, Phase 5's included: set `status: done`; confirm every `M` and
`S` requirement's Status is `Full`, leaving MAINT-45-14 at `Future Work`; in the MAINT-45 row of
`docs/features/maint/requirements.md`, set the status to `Full` and the size to `M`, matching
`docs/roadmap.md`; then delete the MAINT-45 row from `docs/roadmap.md`.

## See Also

- Requirements: [requirements.md](requirements.md)
- Design: [design.md](design.md)
- Risks: [risks-and-gaps.md](risks-and-gaps.md)
