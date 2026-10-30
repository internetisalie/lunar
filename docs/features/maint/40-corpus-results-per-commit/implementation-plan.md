---
id: "MAINT-40-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "MAINT-40"
folders:
  - "[[features/maint/40-corpus-results-per-commit/requirements|requirements]]"
---

# MAINT-40: Implementation Plan

## Gates

- **Corpus gate**: `tooling/gce-builder/gce-builder.sh run "test -PwithCorpus --rerun --no-build-cache --stacktrace --tests '*Corpus*'"`.
  This is the full filter only (BUG-418).
- **Record command**: the same command with `-PrecordCorpusBaseline` added.
- **Copy-back**: `BLD` and `RSH` are as defined in MAINT-37's implementation plan ("Copying results
  back from the builder"). Run `rsync -az -e "$RSH" "$BLD"/src/test/resources/corpus/ src/test/resources/corpus/` and
  `rsync -az --delete -e "$RSH" "$BLD"/build/corpus-findings/ build/corpus-findings/`. Read the
  results only after the run has printed `BUILD SUCCESSFUL` and every `rsync` has exited 0.
- **Python**: `cd tooling/corpus && python3 -m unittest test_corpus_ctrf`.
- **Unit suite**: `tooling/gce-builder/gce-builder.sh run test`, then `run ktlintCheck` on its own.

## Phases

Phases 1–3 need no store and no credentials, and ship as one PR. Phases 0 and 4 are gated on
loom LCL-241 (risks Gap 2.3, DR-10): nothing is stored until the loom Manager signals that the LCL-241 loom-cli is installed everywhere the store is read and loom-catalog has been redeployed.

### Phase 1: Findings and derived counts [Must]
- **Goal**: every hit is a finding. The counters are derived from the findings, and the baselines are byte-identical.
- **Tasks**:
  - [x] **Baseline control, before any code edit**: run the record command plus copy-back at the
        base commit, then `git status --porcelain -- src/test/resources/corpus/`. If it prints
        anything, commit that re-record alone (`test(corpus): re-record baselines at <base sha>`) so
        TC 1 and TC 3 measure only this phase. At `0d69c425` it printed only `zerobrane.baseline`,
        whose sole movement was `inspection.LuaSuspiciousConcatenation` 3 → 1 (and its `symbol.` line),
        a count that flaps between 1 and 3 run to run. Nothing was committed.
  - [x] Create `CorpusFinding.kt` (`CorpusFinding`, `FindingSite`, `CorpusRules`) — design §2.1.
  - [x] Create `CorpusFindingCounts.kt` — design §2.2, §3.1.
  - [x] Edit `CorpusSweep.kt` per design §2.3, and `CorpusMetrics.kt` per §2.4. Repoint the KDoc
        that names `CorpusSweep.ORACLE_SITES_CAP` / `CorpusSweep.run` as the cap's home
        (`LuaTortureCorpusTest.kt:269`, `CorpusMetrics.kt:49`) to `CorpusFindingCounts`.
  - [x] Create `CorpusFindingsFile.kt` and edit `LuaCorpusSweepTest.sweepAndRatchet` — design §2.5.
  - [x] Edit `build.gradle.kts` — design §2.6.
  - [x] Create `src/test/kotlin/net/internetisalie/lunar/corpus/SweepFindingCountsTest.kt` (TC 2).
        The name avoids `Corpus`, which `SweepNamingGuardTest` reserves for the sweep classes.
- **Exit criteria**: the unit suite is green. TC 2 is red under each of its mutants, each applied and
  reverted with the `temporary-edits` skill. The record command plus copy-back gives TC 3 (no
  output). The corpus gate passes, and TC 1 holds on the copied-back `build/corpus-findings/luacheck.json`.

### Phase 2: CTRF assembly, identity and fingerprint [Must]
- **Goal**: one deterministic CTRF document per invocation.
- **Tasks**:
  - [x] Create `tooling/corpus/corpus_ctrf.py` with the `assemble` subcommand, `load_runs`,
        `identity_of`, `fingerprints` and `ctrf_document` — design §2.7, §3.2–§3.4, §4.2.
        `SUITE_ARGV = ()`.
  - [x] Create `tooling/corpus/test_corpus_ctrf.py` with TC 4, 5 and 6.
- **Exit criteria**: the Python gate passes. Each TC 4–6 mutant is observed red. `assemble` over
  Phase 1's copied-back `build/corpus-findings/` writes a document whose `testEnvironment` starts
  `full-`, and that document passes DR-09's `jsonschema` validation against the pinned schema.

### Phase 3: Fetch and diff [Must]
- **Goal**: Lunar-side lookup and comparison.
- **Tasks**:
  - [x] Add `loom`, `lookup`, `fetch` and `diff` to `corpus_ctrf.py` — design §3.6, §3.7, §4.3.
  - [x] Add the §4.3 stub and TC 9 and 10 to `test_corpus_ctrf.py`.
  - [x] Add `tooling/corpus/README.md` lines for `assemble` / `fetch` / `diff`.
- **Exit criteria**: the Python gate passes, and each TC 9–10 mutant is observed red.

### Phase 0: `loom-cli` in the `corpus` job [Must] — gated on LCL-241
- **Goal**: the `corpus` job can run `loom-cli`, and the suite argv is known.
- **Tasks**:
  - [ ] Run DR-10 and set `SUITE_ARGV` in `corpus_ctrf.py` from it.
  - [ ] Add the install step and the `loom-cli --version` prefix to the `corpus` job (design §2.8).
        No image change.
  - [x] Run DR-04 and DR-07 on a throwaway branch. Record each in its row.
- **Exit criteria**: DR-04, DR-07 and DR-10 are `done`. Any refutation is folded back into design
  §3.5 / §4.3 before Phase 4 begins.

### Phase 4: Store from CI [Must] — gated on LCL-241
- **Goal**: every `corpus` job run stores its document as `a3` suite `corpus`.
- **Tasks**:
  - [ ] Add `store` to `corpus_ctrf.py` — design §3.5. Add TC 7.
  - [ ] Edit the Gitea `corpus` job — design §2.8. Leave `.github/workflows/build-plugin.yml` unchanged.
  - [ ] Add the `store` line to `tooling/corpus/README.md`.
- **Exit criteria**: TC 7 passes and its mutants are observed red; TC 9 passes with the Phase 0
  `SUITE_ARGV`. The PR's `corpus` job is green and its log has a `store:` line. After merge, TC 8
  holds on the merge commit. A manual rerun of that job logs `already stored, identical` or the
  `WARNING` line, and that line is recorded in risks Risk 1.1.

## Requirement → Phase Coverage

Phase 1: MAINT-40-01, -02, -09 (no gate change; verified by inspecting Phase 4's workflow).
Phase 2: -03, -04, -05. Phase 3: -07, -08. Phases 0 and 4: -06, and -10's `store` refusal only.

## Verification Tasks

- [ ] `CHANGELOG.md`: none. The feature has no user-facing change.

## Task Summary

| Phase | Status | Priority |
|---|---|---|
| Phase 1: Findings and derived counts | done | Must |
| Phase 2: CTRF assembly | done | Must |
| Phase 3: Fetch and diff | done | Must |
| Phase 0: loom-cli fetch + DR execution (after LCL-241) | todo | Must |
| Phase 4: Store from CI (after LCL-241) | todo | Must |
