---
id: "MAINT-37-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "MAINT-37"
folders:
  - "[[features/maint/37-pinned-definition-libraries/requirements|requirements]]"
---

# MAINT-37: Implementation Plan

> **Precondition**: [design.md](design.md) has cleared the bar. Done de-risking (each row in
> [risks-and-gaps.md](risks-and-gaps.md) carries its record): DR-01, DR-02, DR-03, DR-07, DR-07b,
> DR-09, DR-09b, DR-10, DR-11, DR-06, DR-12. **Remaining**, each with every outcome specified in its row:
> DR-08 at Phase 4's record-sequence
> step S2b and DR-04 after its step S4, because both need Phases 1–3b; DR-05 in Phase 5.
>
> **Startable now: Phases 0, 1, 2, 3, 3b, 4 and 6.** **Phase 5 is blocked on TARGET-10 Phase 4**
> (the `wxlua` catalog entry on `main`, loadable — Phase 5's precondition says how this feature
> checks it). Nothing else here depends on TARGET-10.
>
> **Scope guard for every phase**: no file under `src/main/` is modified. All Kotlin is test code in
> `src/test/kotlin/net/internetisalie/lunar/corpus/`. The one CI file changed is
> `.gitea/workflows/build-plugin.yml` (Phase 3).

## Gates

### Core commands
Kotlin phases end with `tooling/gce-builder/gce-builder.sh run ktlintCheck` (alone — never paired
with `ktlintFormat`) and the **full** routine suite
`tooling/gce-builder/gce-builder.sh run "test --rerun --no-build-cache"`. Phases that touch the
sweep add `tooling/gce-builder/gce-builder.sh run "test --rerun --no-build-cache --tests '*Corpus*' -PwithCorpus"`
and confirm the `LuaCorpusSweepTest` XML in `build/test-results/test/` carries this run's timestamp
(engineering contract §5). Python phases end with `mkdir -p $HOME/.cache/claude-scratch/lunar/maint-37`
and then `TMPDIR=$HOME/.cache/claude-scratch/lunar/maint-37 python3 -m unittest discover -s tooling/corpus -p 'test_*.py'`;
the directory must exist first, because `tempfile.gettempdir()` skips a missing `TMPDIR` without
error and falls back to `/tmp` (risks-and-gaps → Evidence E-3). Never pipe a builder run through
`tail`; judge it by the `BUILD SUCCESSFUL`/`FAILED` line.

### Test names
design §2.16 fixes the name of every Kotlin test method this feature adds and the files they live
in, and states the naming rule. `SweepNamingGuardTest` (design §2.16 "Enforcement", which states
what it checks) runs inside the full routine suite that the exit criteria of Phases 0, 1, 2 and 3b
run; a red guard is a red routine suite,
and the offending name is renamed to the one §2.16 lists. The guard checks names, not that a test
ran, so each phase that adds Kotlin tests (0, 1, 2, 3b) also exits only when this holds:
- **Membership.** (`$BLD` and `$RSH` as "Copying results back from the builder" below defines
  them.) Directly after the full routine-suite run, before any other builder run,
  `rsync -az --delete -e "$RSH" "$BLD"/build/test-results/test/ $HOME/.cache/claude-scratch/lunar/maint-37/routine-results/`
  exits 0. Then, for each method `<n>` that design §2.16 lists for the phase's TCs, in class `<C>`,
  `grep -o '<testcase name="<n>"' $HOME/.cache/claude-scratch/lunar/maint-37/routine-results/TEST-net.internetisalie.lunar.corpus.<C>.xml`
  prints `<testcase name="<n>"`. The format is DR-12's XML's (`<testcase name="testLuacheckCorpus" classname="net.internetisalie.lunar.corpus.LuaCorpusSweepTest"`).
  A method missing from the XML did not run in the routine suite, whatever its name.

### A read needs a completed run and a completed copy-back
Every `git status`, `git diff` or tally read in this plan that follows a builder run and an `rsync`
is made only when that run ended `BUILD SUCCESSFUL` and that `rsync` exited 0. Otherwise the local
tree is simply unchanged, and an empty `git status` would pass vacuously. Two transport cases are
retried once, not failed: a run whose output ends before Gradle starts (the tree sync failed; DR-12's
first gate attempt ended `ssh: connect to host … port 22: Connection refused`), and an `rsync` that
exits non-zero.

The same transport failure a second time, or a run that reaches Gradle and does not end
`BUILD SUCCESSFUL` (a `BUILD FAILED` line, or no `BUILD` line at all), is a **failed run**, and so is
an `rsync` that exits non-zero a second time. A failed run or copy-back stops under the stop rule
below at every read in this plan: the record sequence (S1–S12), both parts of the no-movement check
(Phase 1), Phase 2's tally read and Phase 3b. A failed run produces no measurement, so it never leads to a
comparison of code with design or returns a design section to review; that belongs to a completed
run whose read printed something unexpected (part B's "prints anything" branch). Where a failed run
and a printed read both stop (S1, part A), the BUG's title tells them apart: S1's title branch 0 is
the failed run's.

### The failed-run record
Gradle prints a failing test's exception class and location, not its message (this build sets no
`testLogging`; DR-07's console carried only the class and location while the message was in the
XML). So the output a stop keeps for a failed run is: the run's console from its first line ending
`FAILED` (or the compiler's first `e:` line) to its last line; then
`rsync -az --delete -e "$RSH" "$BLD"/build/test-results/test/ $HOME/.cache/claude-scratch/lunar/maint-37/failed-results/`
and, from each file `/usr/bin/grep -l '<failure' $HOME/.cache/claude-scratch/lunar/maint-37/failed-results/*.xml`
names, its `<failure message="…"` line; or, if that `rsync` exits non-zero, its stderr. For a
**failed run**, that text is evidence for the BUG and decides no branch. Two steps use the same
copy-back for runs they **expect** to fail, and do decide their verdict from the copied-back XML:
TC 28's mutation proof (Phase 4) and DR-11 (Phase 0). Those runs are not failed runs; each step's own
branches say what happens, and "Measured nothing" below is the branch they share.

### Measured nothing
A mutant run of TC 28's mutation proof or of DR-11 **measured nothing** when any of these holds:
the transport failure (output ending before Gradle starts) recurs on its one retry; the copy-back `rsync` exits non-zero on its one retry;
the run reaches Gradle and prints no `BUILD` line; or it ends `BUILD FAILED` while the step's own
kill read cannot judge the mutant (the step names that read: TC 28's grep printing nothing, DR-11's
own TC having no `<testcase>` in the copied-back XML, as when compilation fails). Then, after the
step's own restore of the mutant:
1. Do not judge the mutant: it is neither killed nor surviving, the step's other branches are not
   taken, and no design section returns to review.
2. File a BUG (ID claimed at commit time) carrying the failed-run record (above), or the `rsync`'s
   or transport's stderr, with its roadmap row, in its own commit.
3. Once the BUG is resolved, run that mutant's run again. If the resolving commit changes the
   phase's tree, "A fix re-runs the phase's exit" (below) applies first. The phase does not exit
   until the step reaches one of its other branches.

### Stop rule
"Stop" in this plan always means this rule. It applies wherever a step says "stop", and to every
failed run or copy-back (above) at the reads that section lists. A run this plan expects to end
`BUILD FAILED` (TC 28's mutant gate, DR-11's mutant runs) is decided by its own branches and by
"Measured nothing" (above), never by this rule. The read that would follow a failed run or copy-back
is not made. In this order:
1. Keep the evidence: `git diff -- src/test/resources/corpus/ > $HOME/.cache/claude-scratch/lunar/maint-37/stop.diff`,
   and the output that stopped it beside it: for a failed builder run, its failed-run record; for a
   failed `rsync`, its stderr; for a read, what it printed.
2. Restore every path that `git status --porcelain -- src/test/resources/corpus/` lists. Restore a
   tracked path with `git show HEAD:<path> > <path>`; delete an untracked one. Then confirm that
   `git status --porcelain -- src/test/resources/corpus/` prints nothing.
3. Restore `tooling/corpus/corpus.json` with
   `git show HEAD:tooling/corpus/corpus.json > tooling/corpus/corpus.json`, and, for a catalog
   re-pin, `src/main/resources/definitions/lunar-definitions-catalog.json` the same way. The pins
   added in this change do not land. (A no-op where the change added none: Phase 3b, step S1, both
   parts of the no-movement check and Phase 2's tally read.)
4. Uncommitted Kotlin (only Phase 3b has any): copy every path
   `git status --porcelain -- src/test/kotlin` lists into
   `$HOME/.cache/claude-scratch/lunar/maint-37/stop-kotlin/<path>`, keeping the path. Then restore the
   tracked paths with `git show HEAD:<path> > <path>` and delete the untracked ones. After the restart's
   S1 passes, copy them back.
5. File a BUG carrying `stop.diff` and the output (ID claimed at commit time), with its roadmap row.
   These BUG reports and their roadmap rows, together with any BUG already filed at step S5 for a
   withheld member, are the **only** things committed, in their own commit. Nothing else from the
   change is committed: no Kotlin, baselines, `corpus.json`, catalog or `rebaseline-attribution.md`.
   Kotlin the phase committed before the stop (part B of the no-movement check and Phase 2's tally
   read come after the phase's commit) stays committed; this step adds nothing to it and reverts
   nothing of it.
6. The phase stays open. A stop before the phase's commit (the record sequence, Phase 3b, part A of
   the no-movement check) restarts from the commit that resolves the BUG: the record sequence from
   S1, Phase 3b from its pre-gate, part A's phase from its start. A stop after the phase's commit
   (part B, Phase 2's tally read) runs that step again on the phase's tree once the BUG is resolved.
   When the resolving commit changes the phase's tree, that is the new tree, and **every** exit
   criterion of the phase is met again on it, not only the stopped step. A BUG with no resolving
   commit — a failed run or copy-back whose cause lay outside the tree, such as a builder or
   transport outage, closed by recording that cause — runs the stopped step again on the same tree.

### A fix re-runs the phase's exit
When a phase's exit criterion fails and a commit fixes the cause, the fixed tree is the phase's tree:
**every** exit criterion of the phase is met again on it, not only the one that failed (stop rule
step 6).

### Invocation shape
Every corpus record and every corpus gate in this plan uses the test filter of CI's `corpus` job,
`--tests '*Corpus*'` (`.gitea/workflows/build-plugin.yml`, step "Corpus ratchet"): BUG-418
established that counts are byte-deterministic per invocation shape only. The **record command** is
`tooling/gce-builder/gce-builder.sh run "test --rerun --no-build-cache --stacktrace -PwithCorpus -PrecordCorpusBaseline --tests '*Corpus*'"`
(DR-12). `-PrecordCorpusBaseline` sets `lunar.corpus.record=true` (`build.gradle.kts:334-336`), read by
`LuaCorpusSweepTest.kt:98` and `LuaTortureCorpusTest.kt:27`. Member-alone runs
(`-PcorpusAttribution`) are attribution, never records.

### Copying results back from the builder
`gce-builder.sh` pushes the tree with `rsync … "$REPO_ROOT/" "$REMOTE_USER@$ip:$REMOTE_DIR/"`
(`gce-builder.sh:141-144`) and has no pull command, so the `rsync` commands below copy back from the
same place, written `"$BLD"` with the transport `"$RSH"`. `REMOTE_DIR` is `lunar`, relative to the
remote user's home on either backend (`config.sh:51`):
- **libvirt** (the default, `config.sh:63`): `BLD=builder@192.168.3.10:lunar` (the checkout is
  `/home/builder/lunar`), `RSH="ssh -i $HOME/.ssh/pi.key -o IdentitiesOnly=yes"` (`config.sh:67-69`, `:75-76`).
- **gce** (`GCE_BUILDER_BACKEND=gce`): `BLD=$(id -un)@<ip>:lunar` (the checkout is `~$(id -un)/lunar`),
  with `<ip>` the `External IP:` line `GCE_BUILDER_BACKEND=gce tooling/gce-builder/gce-builder.sh status`
  prints, and `RSH="ssh -i $HOME/.ssh/google_compute_engine -o IdentitiesOnly=yes"` (`config.sh:47-48`).

Use the backend the builder runs went to; a run and its copy-back never span two backends.

### CI's `corpus` job is a phase gate
The `corpus:` job of `.gitea/workflows/build-plugin.yml` (`:295-397`) provisions the fixtures and
runs the corpus gate on a `lunar-ci` VM, not the builder that recorded the baselines. It runs on
every push to `main`, on `v*` tags, and on a PR whose diff matches the `changes` job's filter
(`:102`): `tooling/corpus/`, `tooling/ci-image/`, `src/test/resources/corpus/`,
`src/test/kotlin/net/internetisalie/lunar/corpus/`, the workflow file, and — after Phase 3's edit —
`src/main/resources/definitions/`. Each of Phases 0–5 touches one of those paths, and exits only when
its PR run's `corpus` job is green, read from the job
(`GET /api/v1/repos/lunar/lunar/actions/runs/{id}/jobs`, response `{"jobs":[…],"total_count":…}`,
format: Evidence E-3), not from the run, whose status can stay "in progress" (repo guide, CI
section). Quote the `corpus` entry's `name`, `status` and `conclusion` verbatim in the phase's exit
report. **Green** is `status` `completed` with `conclusion` `success`; `skipped` is **not** green (the
diff missed the filter); any other conclusion is red. **Red**: the PR does not merge. Read the job's
log (`…/actions/jobs/{jobId}/logs`) for the step that failed.
- **"Provision pinned fixtures", on Phase 3's PR**: Phase 3's own edit to that step is wrong. Fix
  it on the PR and re-run the job.
- **`skipped`**: every phase's diff touches a filter path, so the diff or the filter is not what
  this plan states. File a BUG (ID claimed at commit time) carrying
  `git diff --name-only <base>..HEAD` and the filter line (`build-plugin.yml:102`); the PR does not
  merge and the phase does not exit until that BUG is resolved and a run of the job is green.
- **Any other failure**: copy the failing step's log, from its first line to its last, into a BUG
  (ID claimed at commit time). For the "Corpus ratchet" step that includes the job's "ratchet
  diagnostics" lines; for "Provision pinned fixtures" on a phase after Phase 3 (an archive failing
  `--verify`, such as a GitHub repack, Risk 1.2) it includes the `definition_pins.py --verify`
  message naming the archive. It is the gate failing on CI's machine for this phase's tree (Risk 1.9).
  For a phase that commits baselines (3b, 4, 5), close the PR unmerged; the phase restarts from the
  commit that resolves the BUG. For Phases 0–3, the PR stays open and unmerged until that BUG is
  resolved, and the phase exits on a green re-run of the job.

`.github/workflows/build-plugin.yml` has no `corpus` job and no `changes` job (its header,
`:16-21`: "the corpus lane is Gitea-only"), so on GitHub there is no corpus gate to read.

## Phases

### Phase 0: Provisioning slice and de-risking [Must]
- **Goal**: the provisioning slice — a pinned library, enabled through the user path
  (`LuaDefinitionLibraryEnabler`), suppresses undeclared-variable reports in a light fixture, and
  both refresh paths produce the measured observation — before anything is built on it.
- **Tasks**:
  - [x] Add `definitionLibraries: List<String> = emptyList()` to `CorpusEntry` — design §2.1 (the
        parse arrives in Phase 1; the field is needed by the fixtures here).
  - [x] Create `CorpusDefinitionLibraries.kt` with `ObservedRefresh`, `requireSettingsSubscriber`,
        `resolve`, `archiveFile`, `stage`, `enable`, `observeRefresh`, `provision`, `release`,
        `enabledPins`, `refreshRoots`, `refreshUnpinned` and the private helpers listed in design §2.4
        — design §2.4, §3.2, §3.3, §3.4, §3.9, §3.12, §3.13.
  - [x] Create `CorpusRefreshProbe.kt` (`RefreshObservation`, `CorpusRefreshProbe`) — design §2.6a.
  - [x] Create `DefinitionPinProvisioningTest.kt` — design §2.8 — with TC 8–13, TC 36 and TC 37,
        each as the method design §2.16 names (TC 11's is
        `testMissingArchiveNamesTheFetchScript`, not a name built from `fetch-corpus.py`).
        TC 8 reaching highlighting re-confirms DR-01 and DR-02 on the real code; if it does not,
        follow DR-02's fallback.
  - [x] **DR-11** — mutation-prove the window exactly as risks-and-gaps' MAINT-37-00-DR-11 row
        specifies (its mutants, command, copy-back and the XML messages it reads). Restore with the
        `temporary-edits` skill after each run, and confirm `diff` of each mutated file against its
        pre-mutation snapshot prints nothing. The row's **Refutes** branch stops the phase.
- **Exit criteria**: DR-11 confirmed; TC 8–13, TC 36 and TC 37 green — TC 36 and TC 37 at exactly
  `RefreshObservation(1, 1L, 4)` — both run alone (`--tests '*DefinitionPinProvisioningTest*'`) and
  inside the full routine suite, where they share the light project with every other routine class;
  Gates "Test names" — the routine run's
  `TEST-net.internetisalie.lunar.corpus.DefinitionPinProvisioningTest.xml` lists the method of each of
  TC 8–13, 36 and 37; ktlint clean; and the branch's CI `build` job (`build-plugin.yml` on `lunar-ci`, `test --rerun`)
  and the PR's CI `corpus` job (Gates) green after the push that carries this phase. If TC 36 or TC 37 reads any other tuple on unmutated code, in any of
  those runs, the measured premise (DR-10) does not hold for the implemented code or that
  environment: stop, paste both tuples and where they were read into a new DR row in risks-and-gaps,
  and design §2.6a returns to Step 9 review.

### Phase 1: Manifest field and baseline identity [Must]
- **Goal**: pins parse, render, and gate as identity.
- **Tasks**:
  - [ ] **No-movement check, part A** (below), on this phase's base commit, before any edit.
  - [ ] `CorpusManifest.parseEntry` reads `definitionLibraries` — design §3.1.
  - [ ] Add `libraries` and `allSymbolHits` to `CorpusMetrics`; `CorpusBaseline.LIBRARY_PREFIX`,
        the render line, and `parse`'s decomposition into `rowsOf`, `scalarsOf`, `prefixedCounts`,
        `parseBallast`, `parseLibraries` — design §2.2, §4.3.
  - [ ] Add the library identity assertion to `CorpusGuards.assertIdentity` — design §2.3.
  - [ ] Extract `CorpusSweep.metricsOf(context, swept, sink)` (with `allSymbolHits`) and end `run`
        with `.copy(libraries = …)` — design §2.5.
  - [ ] Extend `BaselineRatchetTest` with TC 1, 2, 3, 14, 15, 16, 17 (it already has the
        `manifestRoot` and `metrics()` helpers), each as the `@Test` method design §2.16 names
        (TC 1's is `manifestDefinitionLibrariesParseDistinctAndDefaultEmpty`).
- **Exit criteria**: TC 1–3, 14–17 green; full routine suite green; Gates "Test names" — the
  routine run's
  `TEST-net.internetisalie.lunar.corpus.BaselineRatchetTest.xml` lists the method of each of TC 1–3
  and 14–17; ktlint clean; the PR's CI
  `corpus` job green (Gates); and the **no-movement check**'s part A printed nothing on the base
  commit and its part B prints nothing on this phase's tree. No entry pins anything yet, so
  `libraries` is `{}` and every member's baseline must re-record byte for byte.
- **No-movement check** (Phases 1 and 2, each on its own base commit and tree). Its commands are
  record sequence step S1's: the record command (Gates);
  `rsync -az -e "$RSH" "$BLD"/src/test/resources/corpus/ src/test/resources/corpus/`;
  `git status --porcelain -- src/test/resources/corpus/`, read only after a completed run and
  copy-back (Gates). A green corpus gate is not this check: the ratchet passes improvements and
  leaves keys ungated (design §3.11 step 2).
  - **Part A — the base commit**, before any edit of the phase: record sequence step **S1**
    verbatim, branches included. **Prints anything**, or a failed run: the drift predates the
    phase, so it is not charged to the phase's design: stop (stop rule steps 3 and 4 are no-ops:
    nothing of the phase exists yet). The BUG's title is the first of S1's numbered title branches
    that applies. Start the phase from the commit that resolves it (a failed run with no resolving
    commit: run part A again on the same base commit, stop rule step 6). **Prints nothing**: any
    movement part B finds is this phase's.
  - **Part B — the phase's tree**, after its tasks: the same commands. **A failed run or
    copy-back**: the phase does not exit; stop (steps 3 and 4 are no-ops: part B adds no pin and the
    phase's Kotlin is committed), with S1's title branch 0, naming the phase's tree instead of a
    base commit. No design section is compared or returned to review: the run produced no measurement.
    Once the BUG is resolved, run part B again on the phase's tree. **Prints nothing**: pass.
    **Prints anything**: the phase does not exit. Save `git diff -- src/test/resources/corpus/` to
    `$HOME/.cache/claude-scratch/lunar/maint-37/phase<N>-movement.diff`, restore every listed path as
    stop rule step 2 does, and file a BUG carrying the diff (ID claimed at commit time); if the diff
    of a listed baseline shows `inspection.highlightFailures` greater than 0, the BUG also cites
    BUG-390 (counts are not reproducible while highlighting fails, `CorpusMetrics.kt:244-248`). Then
    compare the phase's code with the design sections its tasks cite, line by line. They differ:
    the difference is a code defect; fix it and run part B again. They match: those sections return
    to Step 9 review with the diff. A Phase 2 candidate the BUG must rule in or out:
    `LuaTargetSynchronizer.applyTarget` queues an `invokeLater` that can rewrite the target and
    language level (`LuaTargetSynchronizer.kt:103-110`), and Phase 2's drains run after
    `sweepAndRatchet` sets the language level, so a still-queued one would now run before the sweep.

### Phase 2: Attribution mode in the sweep [Must]
- **Goal**: the sweep can emit bare/pinned tallies without touching baselines.
- **Tasks**:
  - [ ] **No-movement check, part A** (Phase 1), on this phase's base commit, before any edit.
  - [ ] Create `CorpusAttribution.kt` (`CorpusAttributionMode`, `CorpusAttribution`) — design §2.6, §3.8, §4.4.
  - [ ] Extend `LuaCorpusSweepTest` per design §2.7 **except** the `sourcePath` lines (Phase 3b):
        the `provisioned` field, the `setUp` additions (`requireSettingsSubscriber`, the cache root,
        the enable list), the `refreshFor` helper (step 2),
        `tearDown` as `try { CorpusDefinitionLibraries.release(project, provisioned) } finally { super.tearDown() }`
        (Phase 3b landed first: complete its `tearDown` to design §2.7's final form instead),
        the `measure` extraction, the `sweepAndRatchet` steps and `attributionMode()`.
  - [ ] Add the `corpusAttribution` property block to `build.gradle.kts` — design §2.9.
  - [ ] Add TC 18, TC 19, TC 48 and TC 49 to `BaselineRatchetTest`, as the `@Test` methods design
        §2.16 names (`attributionModeParsesFromProperty`,
        `symbolTallyAndRefreshLinesStayOutOfTheBaseline`,
        `attributionAndBaselineRecordAreMutuallyExclusive`,
        `bareArmCheckRejectsLibrariesAndAnEmptySymbolTally`).
- **Exit criteria**: TC 18, 19, 48 and 49 green; full routine suite green; Gates "Test names" — the
  routine run's
  `TEST-net.internetisalie.lunar.corpus.BaselineRatchetTest.xml` lists the methods of TC 18, 19, 48
  and 49; the PR's CI `corpus` job green (Gates); Phase 1's **no-movement check** part A printed
  nothing on this phase's base commit and part B prints nothing on this phase's tree (every member
  now runs `requireSettingsSubscriber`, the `observeRefresh` drain and the `refreshFor` path, with
  nothing pinned); the wiring grep and the tally read below.
- **Wiring grep.** TC 48 and TC 49 assert the mutual-exclusion rule and the bare arm's tally
  checks; this confirms the sweep calls them and that Gradle passes the mode in.
  `/usr/bin/grep -oE 'CorpusAttribution(Mode)?\.[A-Za-z]+\(' src/test/kotlin/net/internetisalie/lunar/corpus/LuaCorpusSweepTest.kt | sort -u`
  prints exactly these lines, in any order: `CorpusAttribution.checkArm(`,
  `CorpusAttribution.tallyFile(`, `CorpusAttribution.write(`, `CorpusAttributionMode.forRun(`
  (design §2.7); and
  `/usr/bin/grep -cF 'systemProperty("lunar.corpus.attribution", project.property("corpusAttribution")' build.gradle.kts`
  prints `1` (design §2.9). Anything else: the phase does not exit; correct the file to the design
  in a new commit (Gates, "A fix re-runs the phase's exit").
- **Tally read.**
  `tooling/gce-builder/gce-builder.sh run "test --rerun --no-build-cache --tests 'net.internetisalie.lunar.corpus.LuaCorpusSweepTest.testPenlightCorpus' -PwithCorpus -PcorpusAttribution=bare"`
  ends `BUILD SUCCESSFUL`, and then
  `rsync -az -e "$RSH" "$BLD"/build/corpus-attribution/bare/penlight.tally $HOME/.cache/claude-scratch/lunar/maint-37/phase2-penlight.tally`
  exits 0. That is the whole check: the tally's content is asserted inside the run, by `checkArm`
  (design §2.6, TC 49) before `write`, and its line format by TC 19; the arm deletes any earlier
  tally first (design §2.7 step 1), so a copied file is this run's. The sweep's own
  `settingsBroadcasts`/`rootsTicks` values are deliberately not read here: TC 37 covers
  `refreshUnpinned` in the routine suite, and Phase 4's TC 38 is their first check inside the sweep.
  A failed run or copy-back stops (stop rule steps 3 and 4 are no-ops: the tally read adds no pin
  and the phase's Kotlin is committed); the BUG's title is "MAINT-37 Phase 2 tally read: failed
  attribution run or copy-back on `<SHA>`", `<SHA>` being `git rev-parse HEAD` of the phase's tree.
  A `checkArm` failure is such a failed run, and its resolving commit is the code fix.

### Phase 3: Fetch and classifier tooling [Must]
- **Goal**: archives arrive verified; deltas classify mechanically.
- **Tasks**:
  - [x] Create `tooling/corpus/definition_pins.py` — design §2.10, §3.2, §3.6, §3.7, §3.14.
  - [x] Extend `tooling/corpus/fetch-corpus.py` — design §2.11 — and add a `definitionLibraries`
        line to `corpus.json`'s `$comment` field list (MAINT-37-08, TC 31's second half).
  - [x] Create `tooling/corpus/attribute_delta.py` — design §2.12, §3.5, §4.4, §4.5.
  - [x] Create `tooling/corpus/reconcile_baseline.py` — design §2.15, §3.11, §4.7.
  - [x] Create `tooling/corpus/test_definition_pins.py` (TC 4, 5, 6, 7, 27, 47),
        `tooling/corpus/test_attribute_delta.py` (TC 20–26 on the shared **B**/**P** tallies, and
        TC 32 on `main`) and `tooling/corpus/test_reconcile_baseline.py` (TC 39–46).
  - [x] Run `python3 tooling/corpus/fetch-corpus.py`; with no pins yet it fetches no archive and
        exits 0. Then `python3 tooling/corpus/definition_pins.py --verify test/corpus/.definitions`
        prints `definitions ok (0 archives verified in test/corpus/.definitions)` and exits 0.
  - [x] **CI `corpus` job**: make design §7 "CI" edits 1 and 2 to `.gitea/workflows/build-plugin.yml`
        in this phase's commit. Phase 3's own PR runs the job because it edits the workflow file, so
        edit 1's new alternative is checked locally instead, by two commands that must both succeed:
        ```bash
        grep -cF "'^tooling/corpus/|^tooling/ci-image/|^src/test/resources/corpus/|^src/test/kotlin/net/internetisalie/lunar/corpus/|^src/main/resources/definitions/|^\.gitea/workflows/build-plugin\.yml\$'" .gitea/workflows/build-plugin.yml
        echo src/main/resources/definitions/lunar-definitions-catalog.json | grep -qE '^tooling/corpus/|^tooling/ci-image/|^src/test/resources/corpus/|^src/test/kotlin/net/internetisalie/lunar/corpus/|^src/main/resources/definitions/|^\.gitea/workflows/build-plugin\.yml$'
        ```
        The first prints `1` (the file holds exactly this filter); the second exits `0` (it
        selects the catalog). Executed on `74b43cb8`: the second exits `0` with this regex and
        `1` with the filter as it stands at `:102`, and the first, run with the filter as it
        stands, prints `1`.

        Then `grep -nE '^  (changes|corpus):' .github/workflows/build-plugin.yml` must print
        nothing: the GitHub twin has neither job. If it prints a `corpus` job, make the same
        edits to it in this commit.
- **Exit criteria**: the unittest command is green; `fetch-corpus.py` and the `--verify` command
  exit 0; the `changes`-filter checks print `1` and exit `0`; the GitHub grep prints nothing (or both files carry the edits); and the PR's CI `corpus`
  job is green with its "Provision pinned fixtures" log showing the `definitions ok (0 archives …)`
  line (Gates).

### Phase 3b: BUG-496 — restore the module root per member [Must]
- **Goal**: no member's `sourcePath` reaches a later member, before any pin can add roots changes
  that would turn the leak into an indexed library (MAINT-37-09).
- **Tasks** (in order):
  - [x] **Pre-gate** — on the Phase 3 commit, before any edit, run Phase 4's record-sequence step
        **S1** verbatim. Output stops this phase. Empty output is what makes the "must be
        unchanged" check below meaningful: any later movement is this phase's.
  - [x] Create `CorpusSourcePath.kt` — design §2.13.
  - [x] Create `ModuleRootIsolationTest.kt` — design §2.14 — with TC 33, 34, 35 as the methods
        design §2.16 names; run **DR-06**
        and act on its outcome as risks-and-gaps specifies.
  - [x] Complete `LuaCorpusSweepTest` per design §2.7: the `savedSourcePath` field, its `setUp`
        snapshot, and the final `tearDown` (§3.10 order). Delivered before Phase 2 (one PR per
        slice), so there is no `provisioned` field yet: `tearDown` is §3.10 with `provisioned`
        empty — `restore`, then `refreshRoots` when it returned `true` — and Phase 2 inserts the
        `release(project, provisioned)` step and the `provisioned.isEmpty()` condition between them.
  - [x] Record the full shape: the record command (Gates);
        `rsync -az -e "$RSH" "$BLD"/src/test/resources/corpus/ src/test/resources/corpus/`.
        A failed run or copy-back stops (Gates), and the read below is not made.
  - [x] Read `git status --porcelain -- src/test/resources/corpus/`. It may list only
        `zerobrane.baseline`, on any key (Risk 1.6's unconfirmed lead). Every other path must be
        unchanged: the restore runs after the sweeps of the members before penlight and of penlight
        itself, and the torture member reads no project setting. Any other path listed: stop. The
        stop rule restores every listed path, leaves `corpus.json` untouched in this phase, and sets
        this phase's Kotlin aside (its step 4). Only the BUG report is committed.
  - [x] Add a **Resolution** section to BUG-496's `bug-report.md` holding the commit measured,
        DR-06's verdict, and zerobrane's per-key baseline diff verbatim (or "no change"); set its
        `status: done`; delete the BUG-496 row from `docs/roadmap.md`.
  - [x] Verify: the corpus gate (no record flag) is green.
  - [x] Commit the Kotlin, the re-recorded `zerobrane.baseline` (if changed) and the BUG-496 report
        together — no `corpus.json` pin in this commit.
- **Exit criteria**: TC 33–35 green (or TC 35 deleted under DR-06's first refuting branch); full
  routine suite green; Gates "Test names" — the routine run's
  `TEST-net.internetisalie.lunar.corpus.ModuleRootIsolationTest.xml` lists the method of each of
  TC 33–35 that the phase keeps; corpus gate green; ktlint clean; only `zerobrane.baseline` moved,
  read after a completed record and copy-back; and the PR's
  CI `corpus` job green on the re-recorded baseline (Gates — red closes the PR unmerged).

### Phase 4: busted pins and re-baseline [Must]
- **Goal**: luacheck, luarocks and penlight sweep with `busted`, with every movement attributed.
- **Tasks**: run the **record sequence** below with `PAIRED` = `luacheck`, `luarocks`, `penlight`
  and `LIBS` = `busted`, including the Phase-4-only steps marked ⁴ after S4. Its S2b is DR-08's
  first execution. Then:
  - [ ] Mutation-prove TC 28 using the `temporary-edits` skill: drop the `.copy(libraries = …)` call at
        the end of `CorpusSweep.run` (return `metricsOf(...)` alone) and run the corpus gate. Copy the
        test results back as Gates' "The failed-run record" does (its one transport retry applies),
        then run `/usr/bin/grep -l 'Definition-library pins changed' $HOME/.cache/claude-scratch/lunar/maint-37/failed-results/*.xml`
        (the message is in the XML, not on the console). Restore, and confirm
        `git status --porcelain -- src/test/kotlin` prints nothing. Then:
        - **The gate ended `BUILD FAILED` and the grep printed `TEST-net.internetisalie.lunar.corpus.LuaCorpusSweepTest.xml`'s path**: the mutant is killed.
        - **`BUILD SUCCESSFUL`** (the mutant survives): the phase does not exit; file a BUG (ID
          claimed at commit time) carrying the console, and design §2.3 and §2.5 return to Step 9
          review.
        - **Anything else** — `BUILD FAILED` with the grep printing nothing (a different failure), no
          `BUILD` line, or a transport or copy-back failure on its retry: Gates "Measured nothing".
  - [ ] Confirm TC 29 by grepping each kept member's pinned tally for `allsymbol.LuaUndeclaredVariable.`
        keys whose symbol is in the busted ∪ luassert set (requirements → MAINT-37-06).

#### Record sequence (S1–S12)

This is the **one** procedure for every re-record that adds or changes a pin: Phase 4's busted pins,
a withheld member's later pin (below), Phase 5's `wxlua` pin, and a catalog re-pin (whose S3 is the
catalog change, and whose `PAIRED` is every member whose `definitionLibraries` closure contains the
re-pinned id). Its inputs are `PAIRED` — the members whose pin is added or changed — and `LIBS`, the
catalog ids being pinned. `SCR` is `$HOME/.cache/claude-scratch/lunar/maint-37`. `MEMBERS` is the
list of every corpus member, read from the manifest each time it is needed, never typed out:
`python3 -c 'import json;print(*[c["name"] for c in json.load(open("tooling/corpus/corpus.json"))["corpora"]])'`.
For a member `<m>`, `<M>` is `<m>` with its first letter upper-cased (`luacheck` → `Luacheck`), and
its member-alone arm is
`tooling/gce-builder/gce-builder.sh run "test --rerun --no-build-cache --tests 'net.internetisalie.lunar.corpus.LuaCorpusSweepTest.test<M>Corpus' -PwithCorpus -PcorpusAttribution=<mode>"`.
Every read follows a completed run and copy-back (Gates); a failed run or copy-back stops the
sequence. "Stop" always means the **stop rule** (Gates). Run the steps in order, on one branch, with
no other edit in between.

  - [ ] **S1 — Pre-gate**, on the commit the sequence starts from, before any edit: the record
        command (Gates);
        `rsync -az -e "$RSH" "$BLD"/src/test/resources/corpus/ src/test/resources/corpus/`;
        `git status --porcelain -- src/test/resources/corpus/`, read only once the record run ended
        `BUILD SUCCESSFUL` and the `rsync` exited 0. **Prints nothing**, after both: a fresh
        full-shape record of this commit reproduces every committed baseline byte for byte, including
        the keys the ratchet does not gate and the improvements it tolerates (design §3.11 step 2), and
        no stray file came back; continue. **Prints anything**, or the run or `rsync` failed (after
        the Gates' one transport retry): stop. The BUG carries the diff (and, for a failed run, the
        failed-run record of Gates, or the `rsync`'s stderr), and its title is the first of these that applies:
        0. The record run or the `rsync` failed: a failed record or copy-back on that commit; the
           `git status` read was not made.
        1. The diff of a listed baseline adds, removes or changes a `library.` line: a catalog
           re-pin landed without its re-record (for example by a push that bypassed the PR's
           `corpus` job, Gates). The BUG names the catalog's last commit,
           `git log -1 --format=%H -- src/main/resources/definitions/lunar-definitions-catalog.json`.
           The commit that resolves it reverts that catalog change on `main`; the re-pin is then
           redone as its own record sequence, with the catalog change as S3.
        2. The diff of a listed baseline shows `inspection.highlightFailures` greater than 0: the BUG
           cites BUG-390 (counts are not reproducible while highlighting fails,
           `CorpusMetrics.kt:244-248`).
        3. Otherwise: baseline drift since the last no-movement check or record sequence passed.
           Phases 1 and 2 check their own base commit and tree (no-movement check, parts A and B), so
           a movement S1 finds was not introduced by their Kotlin.

        The sequence restarts from the commit that resolves the BUG (a branch 0 BUG with no
        resolving commit: from S1 on the same commit, stop rule step 6).
  - [ ] **S2 — Save `old`**: `mkdir -p $SCR/old`; for each `<m>` in `MEMBERS`:
        `git show HEAD:src/test/resources/corpus/<m>.baseline > $SCR/old/<m>.baseline`.
        After S1 these equal a fresh record of this commit.
  - [ ] **S2a — `before` arms**, still on the S1 tree: `python3 tooling/corpus/fetch-corpus.py` (it
        fetches every archive the **starting** catalog pins, which a re-pin's `before` needs); then,
        for each `<m>` in `MEMBERS`, its member-alone arm with `<mode>` = `pinned`; then
        `rsync -az --delete -e "$RSH" "$BLD"/build/corpus-attribution/pinned/ $SCR/before/`.
        Each `$SCR/before/<m>.tally` is the member-alone record of the configuration `old` records
        (design §3.11 "Terms").
  - [ ] **S2b — Offsets, old side** (first execution: **DR-08**): for each `<m>` in `MEMBERS`,
        `python3 tooling/corpus/reconcile_baseline.py --corpus <m> --offset --old $SCR/before/<m>.tally --new $SCR/old/<m>.baseline`.
        Every invocation exits `0`: continue. Any exits `1`: stop (design §3.11 "The bound"
        says what clears it). Exit `2` is a command error: fix the arguments and re-run.
  - [ ] **S3 — Pin edit**: add (or, for a re-pin, keep) `"definitionLibraries": [<LIBS>]` on each
        `PAIRED` entry of `tooling/corpus/corpus.json` (for a catalog re-pin this step is the catalog
        version change); run `python3 tooling/corpus/fetch-corpus.py`; confirm
        `test/corpus/.definitions/<id>-<version>.tar.gz` exists for every id in the closure.
  - [ ] **S4 — Pairs**: for each `<m>` in `PAIRED`, its member-alone arm with `<mode>` = `bare`, then
        with `<mode>` = `pinned`; then
        `rsync -az --delete -e "$RSH" "$BLD"/build/corpus-attribution/ $SCR/attribution/`
        and, for each `<m>` in `PAIRED`,
        `python3 tooling/corpus/attribute_delta.py --corpus <m> --bare $SCR/attribution/bare --pinned $SCR/attribution/pinned`.
  - [ ] ⁴ **DR-04** — both arms, twice each, as risks-and-gaps specifies. On either non-empty diff, stop as specified there.
  - [ ] ⁴ **TC 38** on DR-04's tallies (runs 1–4):
        `grep -E '^(settingsBroadcasts|rootsTicks|physicalPsiChanges)=' $SCR/run{1,2}/bare/luacheck.tally $SCR/run{3,4}/pinned/luacheck.tally`
        shows `settingsBroadcasts=1` and `rootsTicks=1` in every run, and one `physicalPsiChanges`
        value across runs 1–4;
        `python3 tooling/corpus/attribute_delta.py --corpus luacheck --bare $SCR/run1/bare --pinned $SCR/run3/pinned`
        does not exit `2`. **If the arms differ on any key of `REFRESH_KEYS` on unmutated code, or
        `settingsBroadcasts` or `rootsTicks` reads anything but 1**, first compare
        `LuaCorpusSweepTest.refreshFor` with design §2.7 step 2 line by line (`stage` before
        `observeRefresh`, never inside its lambda; the bare branch calling `refreshUnpinned`). A
        mismatch is a code defect: stop (the BUG names the mismatch), fix it in its own commit, and
        restart the sequence from that commit. If it matches, the premise
        that `refreshUnpinned` gives the bare arm the pinned arm's refresh is false in the sweep: stop
        (the BUG carries the grep output), and MAINT-37-05 returns to Step 9 review.
        Then mutation-prove it, for each mutant TC 38 lists, in turn:
        1. With the `temporary-edits` skill, apply the mutant alone.
        2. Re-run the arm TC 38 names for that mutant with DR-04's luacheck command.
        3. Copy back: `rsync -az --delete -e "$RSH" "$BLD"/build/corpus-attribution/ $SCR/run5/`.
        4. Classify as TC 38 names: a bare mutant as `--bare $SCR/run5/bare --pinned $SCR/run3/pinned`,
           a pinned mutant as `--bare $SCR/run1/bare --pinned $SCR/run5/pinned`.
        5. **Exit `2` with that mutant's stderr prefix**: the mutant is killed. **Anything else**: stop,
           and design §2.6a returns to Step 9 review.
        6. Restore with the skill, and confirm `git status --porcelain -- src/test/kotlin` prints nothing.
  - [ ] **S5 — Act on each pair's exit code**:
        - **0**: keep the pin.
        - **1**, in a **manifest pin** (S3 edits only `corpus.json`, leaving the catalog as at S1 —
          Phase 4, a withheld member's later pin, Phase 5): withhold the pin. Set that member's
          `definitionLibraries` in `corpus.json` back to its value at S1 (as
          `git show HEAD:tooling/corpus/corpus.json` shows it; for every member these phases pin, that
          means removing the field) and remove the member from `PAIRED`. Its configuration is then
          the one it had at S1, so design §3.11's "member not in `PAIRED`" column holds for it. File
          a BUG (claimed at commit time) carrying the `UNEXPLAINED` rows.
        - **1**, in a **catalog re-pin** (S3 changes a catalog version): **stop** the whole sequence,
          with the `UNEXPLAINED` rows in its BUG. No member is withheld: the catalog change moves
          every member that pins the library, so no member can be returned to its S1 configuration
          while the change lands; the stop rule restores `corpus.json` and the catalog, and no
          member's re-pin lands.
        - **2**: the pair is not comparable. Act on the stderr message as the table below says. A
          "re-run" repeats that member's S4 arms and classifier. If the same message comes back after
          one re-run, stop.
        - If `PAIRED` is now empty (manifest pins only, since a catalog re-pin never withholds):
          restore `corpus.json` with `git show HEAD:<path> > <path>`, commit only the BUG reports and their roadmap rows, and
          stop here. The phase stays open.

        | stderr names (design §3.5) | cause | action |
        | :-- | :-- | :-- |
        | row a, `not the same corpus commit` | the arms swept different manifest commits | re-run both arms |
        | row b, `file counts differ` | the builder's checkout changed between the arms | `python3 tooling/corpus/fetch-corpus.py`, then re-run both arms |
        | row c, `tally has no <key> line` | the tally writer omits a refresh line, a defect in this feature's Kotlin | stop |
        | row d, `refresh observable <key> differs` | the arms did not receive the same refresh, on **any** member | stop, as TC 38's unmutated-code rule; MAINT-37-05 returns to Step 9 review |
        | row e, `the bare run carried libraries` | `setUp` did not empty the enable list, a defect in this feature's Kotlin | stop |
        | row f, `the pinned run registered no library` | in a **manifest pin**: `git diff tooling/corpus/corpus.json` does not show this member's pin, so S3 missed it; in a **catalog re-pin**: every member in `PAIRED` already pins the library, so the cause is not S3 | manifest pin: redo S3 for this member, then re-run the pinned arm; if the diff does show the pin, stop. Catalog re-pin: stop |
        | row g, `highlight failures make inspection counts non-reproducible (BUG-390)` | BUG-390 on this member | as exit 1 does: in a manifest pin withhold the pin, in a catalog re-pin stop; either way the BUG references BUG-390 |
        | G-building, `not in the catalog` or `the catalog has` | the pinned arm saw a different catalog from the local tree | re-run the pinned arm (`gce-builder.sh run` re-syncs the tree) |
        | G-building, `archive … is missing` | the local `test/corpus/.definitions/` lacks it | `python3 tooling/corpus/fetch-corpus.py`, then re-run the classifier only |
  - [ ] **S6 — Record**: the record command (Gates);
        `rsync -az -e "$RSH" "$BLD"/src/test/resources/corpus/ src/test/resources/corpus/`;
        review `git status --porcelain -- src/test/resources/corpus/` and `git diff -- src/test/resources/corpus/`.
        Every member's baseline is rewritten, not only the `PAIRED` ones, which is why S8 reconciles
        every member. A listed path that is neither a member baseline nor a torture file: stop.
  - [ ] **S7 — Torture check**: `git diff --exit-code -- src/test/resources/corpus/torture-fuzzing-lua.baseline src/test/resources/corpus/torture-fuzzing-lua.expected-accepts`.
        Non-zero: stop — a definition library cannot move lexing or parsing, so the record shape is
        not reproducible.
  - [ ] **S8 — Reconcile every member** (design §3.11), for each `<m>` in `MEMBERS`:
        - `<m>` still in `PAIRED`:
          `python3 tooling/corpus/reconcile_baseline.py --corpus <m> --old $SCR/old/<m>.baseline --new src/test/resources/corpus/<m>.baseline --before $SCR/before --bare $SCR/attribution/bare --pinned $SCR/attribution/pinned`,
          then the offset, new side:
          `python3 tooling/corpus/reconcile_baseline.py --corpus <m> --offset --old $SCR/attribution/pinned/<m>.tally --new src/test/resources/corpus/<m>.baseline`.
        - Every other `<m>` (unpinned, already pinned and untouched, or withheld at S5 in a manifest
          pin — each in the configuration it had at S1):
          `python3 tooling/corpus/reconcile_baseline.py --corpus <m> --old $SCR/old/<m>.baseline --new src/test/resources/corpus/<m>.baseline`,
          then
          `python3 tooling/corpus/reconcile_baseline.py --corpus <m> --offset --old $SCR/before/<m>.tally --new src/test/resources/corpus/<m>.baseline`.

        Any exit `1`: stop (design §3.11 "The bound" says what clears a stop on the bound). Exit `2`
        is a command error: fix the arguments and re-run.
  - [ ] **S9 — Verify**: the corpus gate (no record flag) is green — TC 28.
  - [ ] **S10 — Attribution record**: create (first time, Phase 4) or append to
        `docs/features/maint/37-pinned-definition-libraries/rebaseline-attribution.md`
        (front-matter `id: "MAINT-37-RESULTS"`, `type: "results"`, `parent_id: "MAINT-37"`, the same
        `folders` as this file). It holds:
        - the commit SHA measured;
        - each S2b and S8 offset output, verbatim;
        - each S4 classifier output, verbatim;
        - each S8 reconciliation output, verbatim (shape residual, and the refresh or previous-pin
          effect) under a heading per member (Risk 1.6);
        - each withheld member with its BUG ID;
        - the BUG ID for each `TRIAGE-CONTRACT` inspection id with a positive delta.
  - [ ] **S11 — Commit**: file the BUGs (IDs claimed at commit time) with roadmap rows. Then commit
        these together: `corpus.json`; for a re-pin, the catalog; every path
        `git status --porcelain -- src/test/resources/corpus/` lists; `rebaseline-attribution.md`; and
        the BUG reports.
  - [ ] **S12 — CI**: push the branch and open its PR. The PR touches `tooling/corpus/corpus.json`
        or the catalog, and `src/test/resources/corpus/`, so its CI `corpus` job runs the gate on
        CI's machine with the definition archives fetched there (Gates). Green: the sequence is
        complete. Red: the PR does not merge, and Gates' branch for a phase that commits baselines
        applies — close the PR unmerged; the BUG carries the job's diagnostics; the sequence
        restarts from the commit that resolves it.

- **Exit criteria** — two completion states, mirroring Phase 5:
  - **Done** — every member Phase 4 started with in `PAIRED` kept its pin (classifier exit 0 each);
    every S2b and S8 invocation exited 0; DR-08 confirmed; TC 28, TC 29 and TC 38 hold; the torture
    files are unchanged; `rebaseline-attribution.md` exists and lints; the PR's CI `corpus` job is
    green (S12).
    MAINT-37-06 is **Full**.
  - **Committed but open** — at least one member was withheld. TC 28 and TC 29 hold for every kept
    member, every S2b and S8 invocation exited 0, TC 38 holds, the torture files are unchanged, the
    PR's CI `corpus` job is green (S12), and `rebaseline-attribution.md` names each withheld
    member and its BUG. Set MAINT-37-06 to **Partial** in the MAINT epic table
    (`docs/features/maint/requirements.md`) with the BUG IDs in the row's description, and leave
    this phase `in_progress` in the Task Summary. When a withheld member's BUG closes: run the
    record sequence (S1–S12) from the fixing commit with `PAIRED` = that member and `LIBS` =
    `busted`; when it commits and no member remains withheld, the phase is Done and MAINT-37-06 is
    Full.

### Phase 5: wxlua pin for zerobrane [Must] — blocked on TARGET-10 Phase 4
- **Precondition** — TARGET-10 Phase 4's exit criterion (`LuaDefinitionCatalogLoader.load()`
  succeeds with the new entry), checked by this feature itself, on `main`:
  1. `git grep -n '"id": "wxlua"' main -- src/main/resources/definitions/lunar-definitions-catalog.json`
     prints a line.
  2. The fields this feature consumes are well formed:
     `git show main:src/main/resources/definitions/lunar-definitions-catalog.json | python3 -c 'import json,sys; e=[l for l in json.load(sys.stdin)["libraries"] if l["id"]=="wxlua"][0]; assert e["rootPrefix"].endswith("/library") and e.get("requires", [])==[] and e["sha256"] and e["size"]>0 and all(e["version"] in u for u in e["urls"]), e; print("ok", e["version"])'`
     prints `ok <version>`.
  3. The loader accepts the catalog: `tooling/gce-builder/gce-builder.sh run "test --rerun --no-build-cache --tests '*LuaDefinitionCatalogLoaderTest*'"`
     on `main` is green — its existing `bundledCatalogLoads` and `everyBundledEntryIsPinnedAndAttributed`
     (`LuaDefinitionCatalogLoaderTest.kt:46`, `:58`) load the bundled resource and walk every entry.
  TARGET-10's own `wxlua`-specific loader assertion arrives in its Phase 5 and is not required.
  If any check fails, this phase stays blocked.
- **Goal**: zerobrane sweeps with `wxlua`, attributed.
- **Tasks**: run Phase 4's **record sequence** (S1–S12) with `PAIRED` = `zerobrane` and `LIBS` =
  `wxlua`, without the ⁴ steps, adding only:
  - [ ] At S4, **DR-05**: keep both `elapsedMs` lines and both `unresolvedRequires` values.
  - [ ] At S5, an exit of 1 also gets the BUG linked from TARGET-10's `risks-and-gaps.md`.
  - [ ] After S7, `git diff --exit-code -- src/test/resources/corpus/luacheck.baseline src/test/resources/corpus/luarocks.baseline src/test/resources/corpus/penlight.baseline`
        — the busted-pinned members precede zerobrane and gain nothing; non-zero: stop. Then
        confirm TC 30.
  - [ ] At S10, also append DR-05's figures (both `elapsedMs` and both `unresolvedRequires` values).
- **Exit criteria**: TC 30 holds; every S2b and S8 invocation exited 0; corpus gate green;
  torture files unchanged; the PR's CI `corpus` job green (S12). With the pin
  withheld (exit 1), the phase stays open and MAINT-37-07 reads **Partial** with the BUG ID, as in
  Phase 4.

### Phase 6: Documentation and status [Should]
- **Goal**: the workflow is discoverable and the tracker matches reality.
- **Tasks**:
  - [ ] `tooling/corpus/README.md`: replace the stale `fetch-corpus.sh`/`corpus.tsv` references with
        `fetch-corpus.py`/`corpus.json`; document `definitionLibraries`, `test/corpus/.definitions/`,
        the `library.<id>` identity row in the "What is measured" table, and the attribution
        tools (`-PcorpusAttribution=bare|pinned`, the refresh lines, `attribute_delta.py`
        and `reconcile_baseline.py` — pair, no-pair and `--offset` modes — with their exit codes), and a link to this plan's Phase 4
        **record sequence** (S1–S12) as the procedure for any later pin or re-pin — linked, not
        copied, so the procedure keeps one home — TC 31.
  - [ ] When Phases 4 and 5 are both **Done** (not "committed but open"): set this feature's
        `status: done`, set the MAINT epic table row to **Full**, and delete the MAINT-37 row from
        `docs/roadmap.md`. Otherwise the feature stays `in_progress` and the epic row reads
        **Partial**.
  - [ ] `python3 scripts/lint_docs.py docs` and `python3 scripts/lint_planning.py` clean.
- **Exit criteria**: TC 31; linters clean.

## Requirement → Phase Coverage

| Requirement | Priority | Delivered in |
|-------------|----------|--------------|
| MAINT-37-01 | M | Phase 0 (field, `resolve`), Phase 1 (parse) |
| MAINT-37-02 | M | Phase 3 (including the CI `corpus` job's trigger and archive check) |
| MAINT-37-03 | M | Phase 0, Phase 2 (sweep wiring) |
| MAINT-37-04 | M | Phase 1 |
| MAINT-37-05 | M | Phase 0 (`refreshUnpinned`, `observeRefresh`, `requireSettingsSubscriber`, `CorpusRefreshProbe`, TC 37), Phase 2, Phase 3, Phase 4 (record sequence, DR-08, DR-04, TC 38), Phase 5 (record sequence) |
| MAINT-37-06 | M | Phase 4 |
| MAINT-37-07 | M | Phase 5 |
| MAINT-37-08 | S | Phase 3 (`$comment`), Phase 6 (README) |
| MAINT-37-09 | M | Phase 3b |

## Verification Tasks

- [x] `DefinitionPinProvisioningTest` — TC 8–13, 36, 37.
- [x] `ModuleRootIsolationTest` — TC 33–35.
- [ ] `BaselineRatchetTest` extensions — TC 1–3, 14–19, 48, 49.
- [ ] Phase 2's wiring grep — `LuaCorpusSweepTest` calls `forRun` and `checkArm` (TC 48, TC 49 in the sweep), and `build.gradle.kts` passes the mode.
- [x] `test_definition_pins.py` — TC 4–7, 27, 47; `test_attribute_delta.py` — TC 20–26, 32;
      `test_reconcile_baseline.py` — TC 39–46.
- [x] DR-11: Phase 0's mutation proofs of the observation window (`observeRefresh`'s settle) and of
      the bare arm's drop (direct publish) — TC 36, TC 37.
- [ ] DR-04 tallies + mutation proof of each TC 38 mutant — TC 38; DR-08 and per-member offsets and reconciliation in every record sequence.
- [ ] Corpus gate + mutation proof — TC 28; tally greps — TC 29, TC 30.
- [ ] Test names (Gates): `SweepNamingGuardTest` green in the full routine suite, and the routine
      run's XML lists every method design §2.16 names — Phases 0, 1, 2, 3b.
- [x] The `changes`-filter checks — Phase 3.
- [ ] No-movement check, parts A and B, each read after a completed record and copy-back — Phases 1 and 2.
- [ ] The PR's CI `corpus` job green — Phases 0–5 (Gates; S12 in every record sequence).
- [ ] README/`$comment` greps — TC 31.
- [ ] `git diff --stat <phase-0-base>..HEAD -- src/main` is empty across the feature.

## Task Summary

| Phase | Status | Priority |
|-------|--------|----------|
| Phase 0: Provisioning slice and de-risking | done | Must |
| Phase 1: Manifest field and baseline identity | todo | Must |
| Phase 2: Attribution mode in the sweep | todo | Must |
| Phase 3: Fetch and classifier tooling | done | Must |
| Phase 3b: BUG-496 — restore the module root per member | done | Must |
| Phase 4: busted pins and re-baseline | todo | Must |
| Phase 5: wxlua pin for zerobrane | blocked (TARGET-10 Phase 4) | Must |
| Phase 6: Documentation and status | todo | Should |
