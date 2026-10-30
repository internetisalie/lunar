---
id: "MAINT-48-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "MAINT-48"
folders:
  - "[[features/maint/48-unit-test-results-per-commit/requirements|requirements]]"
---

# MAINT-48: Implementation Plan

## Gates

- **Unit suite**: `tooling/gce-builder/gce-builder.sh run "test --rerun --no-build-cache"`, then
  `run ktlintCheck` on its own. Never pipe either through `tail`.
- **Copy-back**: `$BLD` and `$RSH` as MAINT-37's implementation plan defines them ("Copying results
  back from the builder"). Read a copied file only after the run printed `BUILD SUCCESSFUL` and the
  `rsync` exited 0.
- **Python**: `cd tooling/ctrf && python3 -m unittest test_junit_ctrf` and
  `cd tooling/corpus && python3 -m unittest test_corpus_ctrf`.
- **Mutants**: each requirements "Red under" entry is applied and reverted with the
  `temporary-edits` skill, and observed red.

## Phases

Phases 1–3 need no store and no credentials and ship as one PR. Phase 4 is its own PR. The skill
gate that consumes the record is MAINT-50.

### Phase 1: Test-set sidecar [Must]
- **Tasks**:
  - [x] Edit `build.gradle.kts` per design §2.1, with its two imports.
- **Exit criteria**: TC 1 (a), (b) and (c) hold on copied-back sidecars, and each TC 1 mutant is
  observed red. The unit suite has no failing test that the pre-edit run lacked, and ktlintCheck passes.

### Phase 2: Shared store plumbing [Must]
- **Tasks**:
  - [x] Before editing: run the corpus Python gate and save its output.
  - [x] Create `tooling/ctrf/loom_store.py` (design §2.2) and `tooling/ctrf/loom_stub.py` (`STUB`,
        moved verbatim from `test_corpus_ctrf.py`).
  - [x] Edit `corpus_ctrf.py` and `test_corpus_ctrf.py` per design §2.4.
  - [x] In MAINT-40's `design.md` §2.7, replace the moved names in the "Functions:" sentence with a
        pointer to `tooling/ctrf/loom_store.py` (MAINT-48 design §2.2). In its §4.3 "Test stub"
        bullet, change `test_corpus_ctrf.py` to `tooling/ctrf/loom_stub.py`. In its §2.7 "Constants:"
        sentence, move `EXIT_UNMEASURED` and `LOOM_CLI` out to the same pointer.
- **Exit criteria**: TC 11. The corpus gate's test count equals the saved run's.

### Phase 3: Converter, fetch and diff [Must]
- **Tasks**:
  - [x] Create `tooling/ctrf/junit_ctrf.py` (design §2.3, §3.1–§3.8, §4.3).
  - [x] Create `tooling/ctrf/test_junit_ctrf.py` with TC 2–8, 13 and 14, using `loom_stub.STUB`.
  - [x] Create `tooling/ctrf/README.md`: one usage line per subcommand. In
        `tooling/corpus/README.md` ("Findings per commit"), note that loom calls live in `tooling/ctrf/loom_store.py`.
- **Exit criteria**: the Python gates pass, and every TC 2–8, 13 and 14 mutant is observed red. Then, on the
  builder, run the unit suite, copy back `build/test-results/test/` and the sidecar, `assemble`
  them, and validate the document with `jsonschema` 4.19.2 against the pinned schema (DR-05's
  command). Run `assemble` twice and `cmp` the outputs.

### Phase 4: Store from CI [Must]
- **Tasks**:
  - [x] Create `tooling/ci/install-loom-cli.sh`, edit the `corpus` job and its comment, and extend
        the `changes` job's CORPUS regex (design §2.5).
  - [x] In MAINT-40's `design.md` §2.8, cut the first bullet's install recipe to a pointer to
        `tooling/ci/install-loom-cli.sh` (MAINT-48 design §2.5).
  - [x] Edit the Gitea `build` job (design §2.6). Leave `build-release` and `.github/` unchanged.
  - [x] TC 9, both commits, on one throwaway branch; delete the branch and its PR afterwards.
- **Evidence**: PR #129 (`86224b15`): `build` stored `a3.ctrf.json` and passed; `corpus` logged
  `loom-cli v0.93.0` from the script and passed; `grep -c releases/latest` prints 0 (TC 12). TC 9 on
  PR #130 (closed, branch deleted): commit `2aa2e0e9` logged `store: stored …/a3.ctrf.json` and only
  the unit-test `::error::`; commit `80802602` passed its tests, logged `loom-cli: resolve HEAD
  exited 1: … chdir /nonexistent …` and only the store `::error::`.
  - [x] After merge: TC 10 on the merge commit. `88910731` (#129): `resolve --check a3` exits 0;
        `tool.name` is `lunar-unit-tests`, 3421 tests, 0 failed; `commandLineIncludePatterns` is
        empty; a builder run of `test --rerun -PexcludeExternalFixtureTests --tests
        '*LuaBundleCasingTest*'` at that commit wrote a sidecar with the same `excludePatterns`.
- **Exit criteria**: the PR's `build` job is green and its log has a `store:` line; its `corpus` job
  (the PR touches `build-plugin.yml`, so `changes` selects it) is green and logs the loom-cli version.
  TC 9, TC 10 and TC 12 hold.

## Requirement → Phase Coverage

Phase 1: MAINT-48-01. Phase 2: -08 (the Python half). Phase 3: -02, -03, -04, -06, -07, and -05's
`store` subcommand. Phase 4: -05, -08 (the install script).

## Verification Tasks

- [x] `CHANGELOG.md`: none. The feature has no user-facing change.
- [x] MAINT-45 `requirements.md` carries its one-line suite note (made at planning).

## Task Summary

| Phase | Status | Priority |
|---|---|---|
| Phase 1: Test-set sidecar | done | Must |
| Phase 2: Shared store plumbing | done | Must |
| Phase 3: Converter, fetch and diff | done | Must |
| Phase 4: Store from CI | done | Must |
