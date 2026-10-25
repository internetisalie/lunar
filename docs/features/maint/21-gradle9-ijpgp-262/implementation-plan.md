---
id: "MAINT-21-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "MAINT-21"
folders:
  - "[[features/maint/21-gradle9-ijpgp-262/requirements|requirements]]"
---

# MAINT-21: Implementation Plan

There are two PRs. Before PR 1 comes the image publish: build, push to tier2, gate, deploy to GAR.
After PR 2 come two steps that produce no PR. Phase 3 runs **before** PR 2 merges.

**The bump itself cannot be split** (DR-18):
- 262 needs IJPGP 2.12 or later, and IJPGP 2.12 needs Gradle 9.
- IJPGP 2.17's test framework fails on 261.
- The toolchain, ide-starter, exclusion and sandbox edits are needed only once those move.

**Phase 1 ships first.** It only adds JDK 25 to environments that keep running Gradle on 21, and
`main`'s job script is run in the new image before that image is deployed (DR-19).

**How to run commands:**
- Every builder command runs from the feature worktree as
  `~/.cache/claude-scratch/lunar/bin/builder-run libvirt <args>`.
- Judge each run by its log text.
- Read the JUnit XML straight after the run, and check that its mtimes postdate the run's start.

## Phases

### Phase 1: JDK 25 in the build environments [Must]
- **Goal**: every CI and builder environment carries JDK 25 beside 21, with toolchain auto-download
  off, without changing `main`'s results.
- **Done already**: foojay's JDK was deleted from the builder with the user's approval on
  2026-10-25, and `/opt/cache/gradle/jdks/` now holds only `CACHEDIR.TAG`.
- **Tasks, in this order**:
  - [ ] **1. Edit.** On a branch for PR 1:
        - `tooling/ci-image/Dockerfile` per design §2.7;
        - the `.gitea/workflows/build-plugin.yml` `Verify toolchain` steps per §2.8.1 and §2.8.2;
        - `tooling/gce-builder/builder-bootstrap.sh`, `startup-script.sh` and the `gce-builder.sh`
          log line per §2.10.
  - [ ] **2. Build, and push to tier2.** The push is anonymous and deploys nothing:
        ```bash
        docker build --platform=linux/amd64 \
          -t registry.internetisalie.net/lunar/lunar-ci:0.3.0 tooling/ci-image
        docker push registry.internetisalie.net/lunar/lunar-ci:0.3.0
        crane digest registry.internetisalie.net/lunar/lunar-ci:0.3.0
        ```
  - [ ] **3. Gate against the tier2 image** (TC-13, TC-08): design §5.2, each case on a fresh
        Gradle volume. **Stop on any failure**: nothing has been deployed yet. Fix the Dockerfile,
        then push as `0.3.1` rather than re-pushing `0.3.0`.
  - [ ] **3a. Pre-copy checks.** Both must pass before step 4.
        - **Monotonic:** run `crane ls northamerica-northeast1-docker.pkg.dev/cosmic-region-400615/lunar/lunar-ci`.
          `0.3.0` must sort above every listed tag under semver; at planning time GAR held `0.1.0`
          and `0.2.0` (DR-27). If any listed tag is equal or higher, stop: the copy would not become
          the newest semver, or would overwrite a deployed tag. Pick the next free semver and repeat
          steps 2–3 under it.
        - **Toolchain:** the image must run the job's own toolchain. Step 3 covers this: TC-13 runs
          `main`'s Gradle 8 / JDK 21 job, and TC-08 shows the JDK 25 toolchain resolved from
          `/usr/lib/jvm` with auto-download off, both in this exact tier2 digest. Nothing further
          is run here.
  - [ ] **4. Deploy — supervisor step, separately checkpointed, after steps 3 and 3a pass.** It is
        authorised by the user. Precondition: GAR write credentials on the machine that runs it
        (design §2.7).
        ```bash
        crane copy registry.internetisalie.net/lunar/lunar-ci:0.3.0 \
          northamerica-northeast1-docker.pkg.dev/cosmic-region-400615/lunar/lunar-ci:0.3.0
        crane digest northamerica-northeast1-docker.pkg.dev/cosmic-region-400615/lunar/lunar-ci:0.3.0
        ```
        - The GAR digest must equal step 2's tier2 digest. Record both in this plan's Phase 1
          Resolution (below) and in PR 1's description.
        - **If the copy fails on authentication or permission**, stop and ask the user. Do not
          retry with other credentials.
        - Do not touch `glimmer-gitea-runner`'s pool values: the label is tagless.
  - [ ] **5. Check which image ran.** Push to `main` (the Gitea workflow has no manual trigger) or
        wait for the next `main` build. Its `Verify toolchain` step's first line must read
        `openjdk version "25.` (0.3.0), not `"21.` (0.2.0) (design §2.7, DR-25). The job must be
        green. On a red job, use design §2.7's rollback ("before PR 1 merges"). On a green job
        whose first line still reads `"21.`, the deploy did not take effect: confirm the GAR
        digest with `crane digest`, wait for the pool's next fresh VM, and stop and report to the
        user if a second `main` build still shows `"21.` — do not open PR 1.
  - [ ] **6. Builder.** Re-run the bootstrap as root:
        `ssh builder 'bash -s' < tooling/gce-builder/builder-bootstrap.sh`.
  - [ ] **7. PR 1.** Open PR 1 with step 1's edits.
- **Exit criteria**:
  - Step 3 passed against the digest that step 4 copied.
  - Step 5 shows `"25.` and a green `main` build.
  - TC-10 passes on PR 1's own `build` job, and its `corpus` job prints `toolchain ok (image >= 0.3.0)`
    if path filters trigger it.
  - Merge PR 1. From here on, a rollback needs PR 1 reverted first (design §2.7). TC-09 runs in
    Phase 2, because it needs the feature branch.
- **Resolution** (filled in at step 4):
  - tier2 `registry.internetisalie.net/lunar/lunar-ci:0.3.0` digest: `sha256:…`
  - GAR `…/lunar/lunar-ci:0.3.0` digest: `sha256:…`; must equal the tier2 digest
  - GAR tags before the copy (`crane ls`): `…`

### Phase 2: The combined bump (PR 2) [Must]
- **Goal**: Gradle 9.7.1, IJPGP 2.19.0 and the 2026.2.3 platform land together.
- **Precondition**: Phase 1 is complete.
- **Tasks** (one commit is fine; the order below is for the reader):
  - [ ] Wrapper and `gradleVersion` — design §2.1.
  - [ ] `gradle/libs.versions.toml` — §2.2.
  - [ ] `settings.gradle.kts` foojay 1.0.0 — §2.3.
  - [ ] `gradle.properties` comment, platform, test version and exclusions — §2.4.
  - [ ] `LuaProjectSettings.kt` `expandMacros` — §2.5.
  - [ ] `build.gradle.kts`:
        - imports §2.6.1;
        - toolchain §2.6.2;
        - ide-starter §2.6.3;
        - test-runner modules §2.6.4;
        - verifier §2.6.5;
        - `sandbox.home` §2.6.6;
        - `runIde261` §2.6.7.
  - [ ] `SandboxAssetsIntegrationTest.kt` per §2.6.6, and the new `TestClasspathExclusionsTest.kt`
        per §2.6.8.
  - [ ] Workflows per §2.8.3, §2.8.4 and §2.9.
  - [ ] Tracked references per §2.11. That includes MAINT-03-04's row, which now points at
        MAINT-21.
  - [ ] `CHANGELOG.md` per §2.11, using the `changelog` skill. Fill in the commit link after the
        squash merge.
  - [ ] Format as AGENTS.md describes: run `ktlintFormat` on the VM, then pull the result back.
- **Exit criteria**:
  - **On the builder**, from the PR branch: TC-01 to TC-07, TC-09, TC-11 and TC-12, each its own
    `builder-run`, with every log path in the PR description.
  - **Locally**: TC-14.
  - **CI on the PR**: the Gitea `build` job is green, and so is the `corpus` job if it triggers.
  - **GitHub before merge**: TC-18 (design §5.5).
  - **Not exercised before merge** (Risk 1.6): Gitea's `build-release`. Its Gradle commands are
    covered with the daemon on JDK 25 (DR-13), and its YAML parses (TC-14).
  - **Post-merge**: design §5.5's check of both lanes on the first `v*` tag, with its failure
    branches.
  - **Phase 3 has passed**, and the user has answered the narrowed verifier levels.

### Phase 3: Live test-runner check [Should]
- **Goal**: settle Risk 1.2 with TC-15 and TC-16.
- **Tasks**: follow design §5.3 steps 1–9 on PR 2's branch, synced to the builder by any
  `builder-run`. Use `runIde` for 2026.2.3, and `runIde261` (or step 8's direct launch) for 2026.1.3.
- **Exit criteria**: TC-15 and TC-16 pass, with screenshots and log greps in PR 2. On a failure,
  follow design §7's table. Every branch of that table that is not a pass holds PR 2's merge.
- **Timing**: after Phase 2's builder gates, and **before PR 2 merges**. The user's since-build
  confirmation is subject to this phase.

### Phase 4: Local-only files [Should]
- **Goal**: parser regeneration, the agent guides and the skills match the new build. These files
  are gitignored, so this phase has no PR.
- **Tasks**:
  - [ ] Every row of design §2.12, in the primary checkout.
- **Exit criteria**: TC-17 passes on the merged `main`, and `grep -rn 'build/idea-sandbox'
  .agents/AGENTS.md .agents/skills/verify-in-ide/SKILL.md .agents/skills/implement-feature/SKILL.md
  .agents/skills/jdb-debugger/SKILL.md` prints only the line that §2.12 keeps for the 2026.1.3
  license original.

## Requirement → Phase Coverage

| Requirement | Priority | Delivered in |
|-------------|----------|--------------|
| MAINT-21-01 | M | Phase 2 |
| MAINT-21-02 | M | Phase 2 |
| MAINT-21-03 | M | Phase 2 |
| MAINT-21-04 | M | Phase 2 |
| MAINT-21-05 | M | Phase 2 (gate); Phase 3 (live) |
| MAINT-21-06 | M | Phase 2 |
| MAINT-21-07 | M | Phase 2 |
| MAINT-21-08 | M | Phase 2 |
| MAINT-21-09 | M | Phase 1 (image, bootstrap, Gitea verify steps); Phase 2 (setup-java lanes, TC-09) |
| MAINT-21-10 | M | Phase 1, Phase 2 |
| MAINT-21-11 | M | Phase 2 |
| MAINT-21-12 | M | Phase 2 |
| MAINT-21-13 | M | Phase 1 |
| MAINT-21-14 | S | Phase 3 |
| MAINT-21-15 | S | Phase 2 (tracked docs, CHANGELOG); Phase 4 (local-only) |

## Verification Tasks
- [ ] TC-08 and TC-13: local Docker against the tier2 image, before the GAR deploy (Phase 1, step 3).
- [ ] The which-image-ran check after the deploy (Phase 1, step 5).
- [ ] TC-10: Gitea `build` job logs of PR 1 and PR 2.
- [ ] TC-01 to TC-07, TC-09, TC-11, TC-12: builder runs (Phase 2).
- [ ] TC-14: local YAML parse (Phase 2).
- [ ] TC-18: GitHub draft-PR run (Phase 2, design §5.5).
- [ ] TC-15 and TC-16: `verify-in-ide`, before PR 2 merges (Phase 3).
- [ ] TC-17: `generate.sh` no-op regeneration (Phase 4).
- [ ] Post-merge: both lanes on the first `v*` tag (design §5.5).

## Task Summary

| Phase | Status | Priority |
|-------|--------|----------|
| Phase 1: JDK 25 in the build environments | todo | Must |
| Phase 2: The combined bump | todo | Must |
| Phase 3: Live test-runner check | todo | Should |
| Phase 4: Local-only files | todo | Should |
