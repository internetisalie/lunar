---
id: "MAINT-53-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "MAINT-53"
folders:
  - "[[features/maint/53-member-resolution-by-receiver-type/requirements|requirements]]"
---

# MAINT-53: Implementation Plan

Each phase is one PR and ships alone. Phase 2 needs Phase 1 (DR-05). Phase 3 needs Phase 2, because the
typed path falls back to the corrected text path. The prototype is saved under
`~/.cache/claude-scratch/lunar/maint-53/probe/` as `r1/prototype.diff` with `r1/src/`, where each design
choice is a switch in `Maint53Probe`. It is for reference only and is not committed anywhere.

Gate for every phase, run from the worktree. First the suite, as CI's `build` lane runs it:

```bash
~/.cache/claude-scratch/lunar/bin/builder-run libvirt "test --rerun --no-build-cache"
```

Then record the corpus baselines in the shape CI's ratchet runs (`--tests '*Corpus*'`,
`.gitea/workflows/build-plugin.yml`), since a recording made in another shape does not hold
(`build.gradle.kts`, BUG-418). The builder's rsync is one-way, so copy the recording back, and do both
inside the backend's lock so no other run resyncs the VM in between:

```bash
B=libvirt
GCE_BUILDER_BACKEND=$B flock ~/.cache/claude-scratch/lunar/builder-$B.lock bash -c '
  source tooling/gce-builder/config.sh
  host=$LV_SSH_HOST  # on gce, derive the host as ~/.cache/claude-scratch/lunar/bin/builder-format-locked does
  tooling/gce-builder/gce-builder.sh run "test -PwithCorpus -PrecordCorpusBaseline --tests '\''*Corpus*'\'' --rerun --no-build-cache"
  rsync -az -e "ssh -i $SSH_KEY -o IdentitiesOnly=yes" "$REMOTE_USER@$host:$REMOTE_DIR/src/test/resources/corpus/" src/test/resources/corpus/'
git diff -- src/test/resources/corpus/
```

Judge the diff against that phase's row in DR-05's baseline table:

- Each listed `inspection.*` row moves by exactly the listed amount, and the `symbol.*` rows of those
  inspections may move with it. No other row moves.
- The one exception is [[BUG-541]]: zerobrane's `inspection.LuaSuspiciousConcatenation` and
  `symbol.LuaSuspiciousConcatenation.nil` may move together by 2 in either direction, provided the
  findings that moved are `menu_project.lua:96` and `:136`. Put both rows back to their committed
  values before committing. A move at any other location falls under the next rule.
- Any other difference stops the phase. Report it as a finding; do not re-record. The plan returns to
  Step 9 review.

## Phases

### Phase 1: Reads through reassignment (BUG-24) [Must]
- **Goal**: `LuaUnusedLocalInspection` stops reporting a local read after a reassignment.
- **Tasks**:
  - [x] File the BUG-24 report with `report-bug`. The ID is minted; no report exists. It opens and
    closes in this PR, so it gets no roadmap row.
  - [x] Edit `analysis/inspections/LuaUnusedLocalInspection.kt` — design §3.1.
  - [x] Add TC-01 to TC-03 to the existing `lang/insight/LuaUnusedLocalInspectionTest.kt`.
  - [x] Run the gate and commit the recording. Move BUG-24 to `done`.
- **Exit criteria**: TC-01 to TC-03 pass; TC-19 at the Phase 1 delta; `CHANGELOG.md` entry through the
  `changelog` skill.

### Phase 2: Kind (BUG-490, BUG-512) [Must]
- **Goal**: members and function-name segments are never variables; bare names are never methods.
- **Tasks**:
  - [x] Edit `lang/LuaNameReference.kt` — design §3.2.
  - [x] Edit `lang/indexing/LuaFileBindingsIndex.kt` — design §3.3.
  - [x] In `LuaColonCallUsageWithdrawalTest.aSameNamedGlobalFunctionLosesTheColonCallSite`, change
    `usageOffsetsAt(9)` to expect `emptyList()`, message `"the method's own name is not a usage"`, and
    update Case 25 (c) in that test's KDoc. Keep the `usageOffsetsAt(41)` assertion.
  - [x] In `LuaDeprecatedBareNameThroughRequireTest`, replace the `assertTrue` precondition with
    `assertFalse(closeResolvesIntoTheModule(), "a bare 'close' does not resolve to M:close's name leaf")`,
    and rewrite the KDoc's last two sentences: the resolution the precondition relied on was BUG-512,
    and the precondition now asserts that it is gone. Import `kotlin.test.assertFalse`, and drop the
    `kotlin.test.assertTrue` import if nothing in the file still uses it.
  - [x] Add TC-05 to TC-07 and TC-20 as `LuaMemberKindResolutionTest` (`BasePlatformTestCase`) under
    `src/test/kotlin/.../lang/`. Add TC-21 as a second test in `LuaDeprecatedBareNameThroughRequireTest`,
    whose harness puts `require` targets on real disk.
  - [x] Run the gate and commit the recording.
  - [x] Move BUG-490 and BUG-512 to `done` and delete their roadmap rows. Change the
    MAINT-53 roadmap row to read "covers BUG-508, BUG-516".
- **Exit criteria**: TC-04 to TC-08, TC-20 and TC-21 pass; TC-19 at the Phase 2 delta; `CHANGELOG.md`
  entry through the `changelog` skill.

### Phase 3: Typed resolution (BUG-508, BUG-516) [Must]
- **Goal**: members resolve through the receiver's type.
- **Tasks**:
  - [x] Create `lang/psi/LuaMemberResolution.kt` — design §2.1, §3.4 to §3.6.
  - [x] Edit `lang/LuaNameReference.kt` — design §3.7, including the `declarationIdentifier` KDoc.
  - [x] Add TC-09, TC-11 to TC-16, TC-18, TC-23 and TC-24 as `LuaMemberResolutionTest`
    (`IndexedBasePlatformTestCase`) under `src/test/kotlin/.../lang/`. TC-13 sets the target as
    [[BUG-516]] §1 does.
  - [x] Add TC-22 as a test in `LuaDeprecatedBareNameThroughRequireTest`.
  - [x] Replace the "References resolve by qualified name from receiver TEXT" lesson in
    `.agents/AGENTS.md`.
  - [x] Run the gate and commit the recording. Move BUG-508, BUG-516 and MAINT-53 to `done` and delete
    their roadmap rows.
- **Exit criteria**: TC-09, TC-11 to TC-16, TC-18, TC-19 and TC-22 to TC-24 pass; `LuaTypeGraphRootResolutionBudgetTest`,
  `LuaColonCallCrossFileFanOutTest` and `LuaResolveCacheTest` pass unchanged; Go to Declaration on
  `io.stdout:close()` checked live with `verify-in-ide`; `CHANGELOG.md` entry.
- **If a budget test fails**: stop. Do not relax it (requirements, Non-Functional). Report the test and
  its measured value; the plan returns to Step 9 review. DR-05's suite run passed them on the prototype.

## Requirement → Phase Coverage

| Requirement | Priority | Delivered in |
|-------------|----------|--------------|
| MAINT-53-01 | M | Phase 1 |
| MAINT-53-02 | M | Phase 2 |
| MAINT-53-03 | M | Phase 2 |
| MAINT-53-04 | M | Phase 2 |
| MAINT-53-05 | M | Phase 3 |
| MAINT-53-06 | M | Phase 3 |
| MAINT-53-07 | M | Phase 3 |
| MAINT-53-08 | M | Phase 3 |
| MAINT-53-09 | M | Phase 3 |
| MAINT-53-10 | S | Every phase |

## Verification Tasks

- [x] Mutation-prove each TC named in a phase with the `mutation-proof` skill, using the mutation in
  `requirements.md`.
- [x] `ktlintCheck` alone after each phase.
- [x] After each phase, set the Status column of the requirements it delivers (coverage table above) to
  Full.
