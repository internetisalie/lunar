---
id: "MAINT-49-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "MAINT-49"
folders:
  - "[[features/maint/49-fixture-tests-run-in-ci/requirements|requirements]]"
---

# MAINT-49: Implementation Plan

Each phase is its own branch and PR, merged before the next opens. Phases 1 and 2 are independent of
each other; Phase 3 needs both on `main`. The gate after every phase is the full suite on the
builder, `test --rerun --no-build-cache`, read from its log, never piped through `tail`.

## Phases

### Phase 1: Read the pinned corpus [Must] — PR 1
- **Goal**: the two tests that read hand-placed files read pinned corpus members. Needs nothing from
  TOOLING-09.
- **Tasks**:
  - [ ] `LuaRecursiveReferenceTest`: the path edit — design §2.1.
  - [ ] `LuaDescriptionIndexTest`: new `testIndexRealWorldProjectAndMeasureSize`, `addLuaFiles`,
        `projectIndexBytes`, companion constants (`SIZE_MEMBER`, `PINNED_LINES`,
        `MAX_BYTES_PER_LINE`), KDoc — design §2.2, §3.2.
  - [ ] `tooling/corpus/corpus.json` NB strings and `tooling/corpus/README.md` `Note on test/luacheck` —
        design §2.9 rows 1–2.
- **Exit criteria**: requirements cases 2–6 (the mutants of cases 3, 5 and 6 each run once on the
  builder and reverted); full suite green. CI still excludes the classes, so the builder is the only runner.

### Phase 2: Download `luac` [Must] — PR 2
- **Goal**: the oracle comes from the TOOLING-09 release, verified, with no compile.
- **Tasks**:
  - [ ] `tooling/corpus/fetch-luac.py` per design §2.3 and §3.1.
  - [ ] `tooling/corpus/luac.json` per design §4.1.
  - [ ] `FetchLuacScriptTest` (new) with requirements cases 7–11; delete
        `testChecksumMismatchInstallsNothing` and `runFetchScript` from `ParseOracleTest` — design §2.3.
  - [ ] `ParseOracleTest` `"5.5.0"`, `ParseOracleTest` header KDoc, `ParseOracle.kt` prose,
        the `gce-builder.sh:145` comment — design §2.4, §2.9 rows 4 and 9.
  - [ ] Remove `check_luac_glibc` and replace the README section — design §2.8.
  - [ ] `python3 git` on the apt line of Gitea `build-release` and GitHub `build` — design §2.6
        Phase 2 paragraph. `FetchLuacScriptTest` runs `python3` in those lanes' suite, so a tag cut
        between PR 2 and PR 3 needs it.
  - [ ] Stale prose: design §2.9 rows 10 and 11.
  - [ ] Locally, re-run `python3 tooling/corpus/fetch-luac.py` so the fixture tree holds the
        downloaded binaries before the builder run (risks Risk 1.1); state this in the PR body.
- **Exit criteria**: requirements cases 7–13 and 18; full suite green; the PR's `corpus` job passes
  (it runs because the PR touches `tooling/corpus/`, `build-plugin.yml:102`). After merge, the first
  GitHub `build` run on `main` is green (it is the first run of `FetchLuacScriptTest` with the new
  apt line); if red, a follow-up fix PR, not a revert by default.

### Phase 3: Provision in CI and drop the exclusion [Must] — PR 3
- **Goal**: every CI suite lane runs the four classes.
- **Tasks**:
  - [ ] Gitea `build`: provisioning step, store guard, report line — design §2.5.
  - [ ] Gitea `build-release` and GitHub `build`: the provisioning step — design §2.6 Phase 3
        paragraph.
  - [ ] Remove the filter block and every `-PexcludeExternalFixtureTests`; rename the test steps —
        design §2.7.
  - [ ] Remaining prose: design §2.9 rows 5–7.
- **Exit criteria**: requirements cases 1, 14, 17 and 19 on the PR's Gitea runs and tree; case 15 on
  a throwaway PR branched from PR 3, closed unmerged after the observation; full suite green on the
  builder. **After merge:** case 16's GitHub half on the first GitHub `build` run on `main` (GitHub
  runs no Gitea PR, `.github/workflows/build-plugin.yml:36-48`); if it is red, open a follow-up fix
  PR (not a revert by default) and cut no `v*` tag until it is green. Record in the PR the wall time of the `build` job's provisioning step
  (cold or warm, from its `gradle cache:` line), since DR-04 measured the `corpus` job only.

## Requirement → Phase Coverage

| Requirement | Priority | Delivered in |
|-------------|----------|--------------|
| MAINT-49-01 | M | Phase 1 (case 1 observed in Phase 3) |
| MAINT-49-02 | M | Phase 1 |
| MAINT-49-03 | M | Phase 2 |
| MAINT-49-04 | M | Phase 2 |
| MAINT-49-05 | M | Phase 3 |
| MAINT-49-06 | M | Phases 2 (`python3`) and 3; case 16 after merge (GitHub `main`, then the next tag) |
| MAINT-49-07 | M | Phase 3 |
| MAINT-49-08 | S | Phase 2 |
| MAINT-49-09 | S | Phases 1, 2, 3 (design §2.9, per row above) |

## Verification Tasks
- [ ] Phase 1: builder `test --rerun --no-build-cache --tests '*LuaRecursiveReferenceTest' --tests '*LuaDescriptionIndexTest'`,
      then the mutants of cases 3, 5 and 6 one at a time on the same pattern (expect red at the
      assertion each case names), each reverted with the `temporary-edits` skill — cases 3–6.
- [ ] Phase 2: builder `--tests '*FetchLuacScriptTest' --tests '*ParseOracleTest'`; case 12 run locally
      into an empty `LUNAR_LUAC_ROOT` under scratch — cases 7–13.
- [ ] Phase 3: read the PR's Gitea `build` log for `fixtures ok` and its test XML for the four classes;
      `junit_ctrf.py fetch --commit <PR head>` for the record's filter; the throwaway PR for case 15 —
      cases 1, 14, 15, 17, 19; after merge, the GitHub `main` run and the next tag — case 16.
- [ ] No `CHANGELOG.md` entry: nothing user-visible changes.

## Task Summary

| Phase | Status | Priority |
|-------|--------|----------|
| Phase 1: Read the pinned corpus | todo | Must |
| Phase 2: Download `luac` | todo | Must |
| Phase 3: Provision in CI and drop the exclusion | todo | Must |
