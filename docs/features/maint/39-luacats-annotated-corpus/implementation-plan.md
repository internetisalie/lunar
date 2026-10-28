---
id: "MAINT-39-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "MAINT-39"
folders:
  - "[[features/maint/39-luacats-annotated-corpus/requirements|requirements]]"
---

# MAINT-39: Implementation Plan

The phases land on one branch and one PR; the PR touches `tooling/corpus/` and
`src/test/resources/corpus/`, so CI's `corpus` job runs on it.

## Gates

- **Commands.** Builder runs use `tooling/gce-builder/gce-builder.sh run "<args>"` on the libvirt
  backend, one at a time. Never pipe a run through `tail`; judge it by its `BUILD SUCCESSFUL` /
  `BUILD FAILED` line.
  - **Routine suite**: `test --rerun --no-build-cache`.
  - **Corpus gate**: `test --rerun --no-build-cache --stacktrace -PwithCorpus --tests '*Corpus*'`.
  - **Record**: the gate's arguments plus `-PrecordCorpusBaseline`. Recording and gating share the
    `'*Corpus*'` filter because counts are deterministic per invocation shape only (BUG-418).
  - **Lint**: `ktlintCheck` alone, never paired with `ktlintFormat`.
- **Copy-back.** `BLD=builder@192.168.3.10:lunar`, `RSH="ssh -i $HOME/.ssh/pi.key -o IdentitiesOnly=yes"`,
  `SCR=$HOME/.cache/claude-scratch/lunar/maint-39`, `XML=$SCR/results/TEST-net.internetisalie.lunar.corpus.LuaCorpusSweepTest.xml`.
  Test results: `rsync -az --delete -e "$RSH" "$BLD"/build/test-results/test/ $SCR/results/`. A read
  that follows a run is made only after the run ended `BUILD SUCCESSFUL` (or, for a step that expects
  failure, `BUILD FAILED`) and the `rsync` exited 0. A run whose output ends before Gradle starts, or
  an `rsync` that fails, is retried once; failing again, it is a failed run.
- **Stop** (Phase 1 and the PR gate; in Phase 2 a failing check is fixed in place). On a failed run,
  or a check that prints anything other than its expected output:
  1. Keep the evidence in `$SCR/stop/`: the run's console, the copied-back XML, the check's output.
  2. Restore `tooling/corpus/corpus.json`, `src/test/kotlin/net/internetisalie/lunar/corpus/LuaCorpusSweepTest.kt`
     and every tracked path `git status --porcelain -- src/test/resources/corpus/` lists with
     `git show HEAD:<path> > <path>`; delete `src/test/resources/corpus/lazynvim.baseline` if present.
     Confirm `git status --porcelain -- src/test/resources/corpus/ tooling/corpus/` prints nothing.
     The design §2.5 extension, committed by Phase 1's first task, stays: it is correct without the
     member.
  3. File a BUG with the evidence and its roadmap row (ID claimed at commit time); commit only that.
  4. The phase stays open and restarts from its first unfinished task once the BUG's fix lands.

## Phases

### Phase 1: Admit `lazynvim` [Must]
- **Goal**: the member is fetched, on its target and library, deterministic, recorded and gated
  without moving any other member.
- **Tasks** (design §3.1, in order):
  - [x] Apply design §2.5 to `CorpusManifest.kt`, `CorpusTarget.kt` and `LuaCorpusSweepTest.kt:125`;
        add its tests (design §2.5). Routine suite green with **TC 10–13** passing; `ktlintCheck` green.
        Commit, so that the Stop restore (`git show HEAD:<path>`) keeps it.
  - [x] Append the design §2.1 entry to `tooling/corpus/corpus.json`; run
        `python3 tooling/corpus/fetch-corpus.py` — **TC 1**, then **TC 5** on the fetched checkout.
  - [x] Insert `testLazynvimCorpus` into `LuaCorpusSweepTest` after `testPenlightCorpus` (design §2.2).
  - [x] **Record 1** (§3.1 step 2), then
        `rsync -az -e "$RSH" "$BLD"/src/test/resources/corpus/ src/test/resources/corpus/`.
  - [x] **TC 6** (§3.1 step 3), with the BUG-541 rule and restore as given there. Otherwise stop;
        the BUG carries `git diff -- src/test/resources/corpus/` and cites DR-08.
  - [x] **Repeat** (§3.1 step 4, TC 7). For `i` in 2–8: the record command, then
        `mkdir -p $SCR/rec/$i`, then
        `rsync -az --delete -e "$RSH" "$BLD"/src/test/resources/corpus/ $SCR/rec/$i/`, then
        `for f in src/test/resources/corpus/*.baseline src/test/resources/corpus/*.expected-accepts; do cmp "$f" "$SCR/rec/$i/$(basename "$f")"; done`.
        It prints nothing, or only a `zerobrane.baseline … differ` line for a copy that differs only
        by BUG-541 (design §3.1, applied with `F=$SCR/rec/$i/zerobrane.baseline`). Otherwise stop;
        the BUG carries the `diff` of each differing pair and cites [[BUG-501]] (for
        `lazynvim.baseline`) or DR-08 (for any other file).
  - [x] **TC 3**: the corpus gate, copy-back, then
        `grep -o '<testcase name="testLazynvimCorpus"' $XML` and
        `grep -o '\[corpus:lazynvim\] files=54 ' $XML` each print their match, and design §3.1
        step 5's `IMPROVED` filter over `$XML` prints nothing.
  - [x] **TC 4**, then **TC 2**, each with the `temporary-edits` skill: snapshot the one file the TC
        edits (`lazynvim.baseline`, then `corpus.json`), edit it, run the corpus gate, copy back,
        `grep -o` the TC's expected failure message in `$XML`, restore, and confirm
        `git status --porcelain` lists only this phase's own changes.
- **Commit point**: after the last task above, with every TC edit restored, commit the `corpus.json` entry, `testLazynvimCorpus` and `lazynvim.baseline` together; Phase 2 works over that committed baseline.
- **Exit criteria**: TC 1–7 and TC 10–13 as above; routine suite green; `ktlintCheck` green.

### Phase 2: Findings and corrections [Should]
- **Goal**: what the sweep found is filed; what it falsified is corrected.
- **Tasks**:
  - [x] Design §3.2 over the committed `lazynvim.baseline`: one BUG report (skill `report-bug`) and
        one roadmap row per row marked **file** and per new class — **TC 8**.
  - [x] Apply design §2.4's replacements — **TC 9**.
  - [x] `python3 scripts/lint_docs.py docs` and `python3 scripts/lint_planning.py` clean.
- **Exit criteria**: TC 8 and TC 9 hold; routine suite and `ktlintCheck` green (the
  `build.gradle.kts`, KDoc and `$comment` edits are comments only).

### PR gate
CI's `corpus` job on the PR is green. Red on a machine where the builder was green is a finding about
determinism across hosts: stop (Gates), with the job's diagnostics output as evidence.

### Close-out
After the PR gate, in the same PR:
- [x] `requirements.md` front-matter `status: "done"`; every Must/Should row's Status `Full`.
- [x] Delete the MAINT-39 row from `docs/roadmap.md`.
- [x] Add a MAINT-39 row (Status **Full**) to the feature table of `docs/features/maint/requirements.md`,
      after MAINT-38's.
- [x] No `CHANGELOG.md` entry: the change is test-only and not user-facing.
- [x] `python3 scripts/lint_docs.py docs` and `python3 scripts/lint_planning.py` clean.

## Requirement → Phase Coverage

| Requirement | Priority | Delivered in |
| :-- | :-- | :-- |
| MAINT-39-01 | M | Phase 1 |
| MAINT-39-02 | M | Phase 1 |
| MAINT-39-03 | M | Phase 1 |
| MAINT-39-04 | M | Phase 1 |
| MAINT-39-05 | S | Phase 2 |
| MAINT-39-06 | S | Phase 2 |
| MAINT-39-07 | W | — (follow-on candidates) |
| MAINT-39-08 | M | Phase 1 |

## Verification Tasks
- [x] TC 1–7 and TC 10–13 — Phase 1.
- [x] TC 8–9 — Phase 2.
- [x] CI `corpus` job green on the PR.

## Task Summary

| Phase | Status | Priority |
| :-- | :-- | :-- |
| Phase 1: Admit `lazynvim` | done | Must |
| Phase 2: Findings and corrections | done | Should |
