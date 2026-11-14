---
id: "MAINT-65-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "MAINT-65"
folders:
  - "[[features/maint/65-cycle-aware-inference/requirements|requirements]]"
---

# MAINT-65: Implementation Plan

**Precondition:** BUG-98 has landed (requirements MAINT-65-02).

## Phases

### Phase 1: Alone-digest harness [Must]
- **Goal**: the base commit for TC 5 to TC 7. It has the harness and does not have the gate.
- **Tasks**:
  - [ ] Create `net.internetisalie.lunar.corpus.CorpusSnapshotReads` (design §2.3). Move
    `readErrors` and `errorDigest` out of `LuaSnapshotCostSweepTest` into it, and call them from
    the sweep.
  - [ ] Create `net.internetisalie.lunar.corpus.LuaAloneDigestTest` (design §2.2), implementing
    §3.2 and §3.3.
  - [ ] Add the `withAloneDigest` block to `build.gradle.kts` (design §7).
  - [ ] Smoke-run the harness. Write two one-row files on the builder VM,
    `/home/builder/maint-65-acceptance/smoke-a.jsonl` and `smoke-b.jsonl`. Both hold the row below
    on one line, except that `digest` is `1` in the first file and `2` in the second:
    ```json
    {"member":"blinkcmp","file":"lua/blink/cmp/fuzzy/init.lua","lines":0,"prebuilt":false,"coldMs":0,"coldStatus":"ok","rootSelfMs":-1,"topDep":"","topDepSelfMs":-1,"builds":0,"editMs":0,"editStatus":"ok","editRootSelfMs":-1,"editTopDep":"","editTopDepSelfMs":-1,"editBuilds":0,"digest":1}
    ```
    Then run design §7's command with those two paths.
- **Exit criteria**:
  - The full `test --rerun --no-build-cache` suite has no new failures. `LuaAloneDigestTest` is
    excluded there.
  - The smoke run passes, and `build/alone-digest/digests.tsv` holds exactly one row, for
    `fuzzy/init.lua`, with `status` `ok`.
  - `ktlintCheck` passes.

### Phase 2: Gate and tests [Must]
- **Goal**: lambda-less calls stop resolving their callee.
- **Tasks**:
  - [ ] Add TC 1 to TC 3 to `src/test/kotlin/net/internetisalie/lunar/lang/types/LambdaParamInferenceTest.kt`.
    - Name them `testNoLambdaArgResolvesNoCallee_MAINT65_TC1`,
      `testLambdaArgStillResolvesCallee_MAINT65_TC2` and `testLambdaAfterOtherArgKeepsIndex_MAINT65_TC3`.
    - Use `myFixture.configureByText("main.lua", …)` with the requirements' text.
    - Read `val snapshot = LuaTypesSnapshot.forFile(myFixture.file)`, then
      `LuaTypeSourceRecorder.snapshotFrames[snapshot]`.
    - Assert on `inProgressHits`, and on `snapshot.getValueType(x).displayName()` for the lambda
      parameter `x`, found as in `testLuaCatsLocalCalleeSeedsLambdaParam_TC4`.
  - [ ] In `net.internetisalie.lunar.lang.psi.types.LuaTypesVisitor`, rewrite
    `propagateExpectedLambdaParams` as design §3.1 steps 1 to 6. This realizes design §2.1 and §3.1.
- **Exit criteria**:
  - TC 1 to TC 4 pass.
  - TC 1 fails with the edit reverted. DR-07 shows this on base.
  - The full `test --rerun --no-build-cache` suite has no new failures.
  - `ktlintCheck` passes.

### Phase 3: Acceptance runs [Must]
- **Goal**: evidence for MAINT-65-02 and MAINT-65-03.
- **Tasks**: run each step one at a time, each with `--rerun --no-build-cache`.
  - After every run, copy its output files on the builder VM, with `ssh builder cp`, to
    `/home/builder/maint-65-acceptance/<phase>-<run>/`, for example `p1-sweep1/`.
  - Copy on the VM because each sweep run deletes `build/snapshot-cost/`, and sync to the VM
    runs one way only.
  - TC 7's `-PaloneDigest*` arguments are VM paths under that directory.
  - Pull the directory back with `scp` for the PR record.
  - [ ] At the Phase 1 commit: run TC 5's command, then TC 6's sweep twice.
  - [ ] At the Phase 2 commit: the same three runs.
  - [ ] Run TC 7 (design §7's command) at the Phase 1 commit, then at the Phase 2 commit. Pass
    the first sweep of each commit as `-PaloneDigestBase` and `-PaloneDigestChange`.
  - [ ] Compare as TC 5, TC 6 and TC 7 specify. Record the four miss counts per bound, the
    candidate count (including "0 candidates") and the digest comparison in the PR description.
- **On slot overflow** (`testSlot00` fails with "candidates exceed 40 slots"):
  1. Add `@Test` methods `testSlot40()` onward, up to the candidate count rounded up to the next
     multiple of ten, and raise the `40` in `readSlot`'s assertion (design §3.3 step 2) to match.
  2. Re-run TC 7 at both commits.
- **On a failure of TC 5, TC 6 or TC 7** (TC 7: a row differs, or is not `ok`, in either run):
  1. The requirement fails. Do not merge. Report the failing outputs to the supervisor: TC 5's two
     files, TC 6's four counts per bound, or TC 7's differing rows.
  2. Do not re-run any sweep, audit or alone run to get a passing result. The two sweeps per commit
     in TC 6 are the only allowance for variance, and DR-10 found alone reads stable.
  3. A slot overflow is not a failure. Handle it as above.
- **Exit criteria**: TC 5, TC 6 and TC 7 hold.

## Requirement → Phase Coverage

| Requirement | Priority | Delivered in |
|-------------|----------|--------------|
| MAINT-65-01 | M | Phase 2 |
| MAINT-65-02 | M | Phase 1 (harness), Phase 3 (check) |
| MAINT-65-03 | M | Phase 3 |

## Verification Tasks
- [ ] TC 1 to TC 3: the new tests in `LambdaParamInferenceTest`.
- [ ] TC 4: the existing `LambdaParamInferenceTest` and `ExpectedCallbackResolverTest`.
- [ ] TC 5 to TC 7: the Phase 3 runs.

## Task Summary

| Phase | Status | Priority |
|-------|--------|----------|
| Phase 1: Alone-digest harness | todo | Must |
| Phase 2: Gate and tests | todo | Must |
| Phase 3: Acceptance runs | todo | Must |
