---
id: "MAINT-21"
title: "21: Gradle 9, IntelliJ Platform Gradle Plugin 2.19 and the GoLand 2026.2 Platform"
type: "feature"
status: "in_progress"
priority: "medium"
parent_id: "MAINT"
folders:
  - "[[features/maint/requirements|maint]]"
---

# MAINT-21: Gradle 9, IntelliJ Platform Gradle Plugin 2.19 and the GoLand 2026.2 Platform

## Overview

Lunar compiles and unit-tests against GoLand 2026.1.3 (build 261) with Gradle 8.14.4 and the
IntelliJ Platform Gradle Plugin (IJPGP) 2.5.0. This feature moves all three together: Gradle 9.7.1,
IJPGP 2.19.0, and GoLand 2026.2.3 (build 262) as the compile and test platform. The three cannot
move separately (DR-18):
- 262 needs IJPGP 2.12 or later to resolve its test classpath, and IJPGP 2.12 needs Gradle 9.
- IJPGP 2.17's test framework fails on 261. Other IJPGP versions on 261 were not measured.

The plugin keeps supporting GoLand 2026.1. The user has confirmed this, subject to Phase 3's live
check (Gap 2.1). `pluginSinceBuild` stays `261`, the plugin verifier checks the built zip against
GoLand 2026.1.3 and 2026.2.3, and the bytecode target stays Java 21.

Parent epic: [MAINT](../requirements.md). The executed evidence for every claim below is in the DR
rows of [risks-and-gaps.md](risks-and-gaps.md).

## Scope

### In Scope
- Build-tool versions: the Gradle wrapper, IJPGP, the foojay toolchain resolver, and the Kover,
  Qodana and changelog Gradle plugins.
- The platform: `platformVersion` and `testVersion` move to 2026.2.3, together with the 262 module
  dependencies and the one 261/262 binary-compatibility fix.
- Toolchain: compile with JDK 25 and emit Java 21 bytecode.
- Test-classpath hygiene that 262 and IJPGP 2.19 make necessary: ide-starter scope, which bundled
  plugins the tests load, and which sandbox the tests read.
- Compatibility proof: the plugin verifier checks GoLand 2026.1.3 and 2026.2.3 on every tag build,
  and a `runIde261` task runs the plugin live in 2026.1.3.
- JDK 25 in every environment that builds Lunar: the `lunar-ci` job image, the `act-ci` and GitHub
  lanes, the builder bootstrap, and local-only tooling.
- Both CI workflow pairs, kept equivalent in effect.
- Version and sandbox-path references in tracked docs and in the local-only agent guides, plus the
  CHANGELOG entry.

### Out of Scope
- **`:integrationTest` on 262.** It is filed as [BUG-524](../../bug-fixes/524-integration-tests-unverified-on-262/bug-report.md).
  This feature keeps the integration sources compiling (MAINT-21-08) and does not run them. After
  PR 2 merges, `testVersion = 2026.2.3`, so every `gce-builder.sh run build` runs those unverified
  262 integration tests. A failure there is BUG-524's to fix, not a MAINT-21 regression.
- **The image publish job (separate MAINT)**, which builds `lunar-ci` in CI and pushes it to the
  tier2 registry. Until it exists, Phase 1 builds and pushes to tier2 by hand. The deploy, a
  `crane copy` from tier2 to GAR, is a supervisor step in either case (design §2.7).
- **Gradle 10 deprecations** in `build.gradle.kts` (DR-01). They are warnings only.
- **Dropping JDK 21 from the build images.** JDK 21 stays the Gradle daemon JVM on `lunar-ci` and on
  the builder (design §3.2).
- Publishing to the JetBrains Marketplace, which stays dormant.

## Functional Requirements

| ID | Requirement | Priority | Description |
|----|-------------|----------|-------------|
| MAINT-21-01 | **Build-tool versions** | M | Gradle 9.7.1, IJPGP 2.19.0, foojay resolver 1.0.0, Kover 0.9.2, Qodana 2025.1.1, changelog 2.4.0. `buildPlugin` succeeds. |
| MAINT-21-02 | **262 platform** | M | `platformVersion` and `testVersion` are `2026.2.3`, and the code compiles against 262. |
| MAINT-21-03 | **JDK 25 toolchain, Java 21 bytecode** | M | Kotlin and Java compile on a JDK 25 toolchain. Every class in the plugin jar is class-file major version 65 (Java 21). |
| MAINT-21-04 | **Unit suite green on 262** | M | The full `test --rerun --no-build-cache` run on the builder reports zero failures. It runs at least as many tests as `main`, and takes at most 1.5 times as long. |
| MAINT-21-05 | **2026.1 support kept and proven** | M | `pluginSinceBuild` stays `261`; the user has confirmed this, subject to TC-15 and TC-16. `verifyPlugin` checks GoLand 2026.1.3 and 2026.2.3 and reports no compatibility problems for either. The user confirmed the narrowed failure levels on 2026-10-26. |
| MAINT-21-06 | **Corpus gate unchanged** | M | `test --rerun --no-build-cache --tests '*Corpus*' -PwithCorpus` passes against the committed baselines. |
| MAINT-21-07 | **Lint gate** | M | `ktlintCheck` passes. |
| MAINT-21-08 | **Non-unit sources compile** | M | `compileIntegrationTestKotlin` and `compileRedisIntegrationTestKotlin` succeed. |
| MAINT-21-09 | **JDK 25 found locally, never downloaded** | M | On `lunar-ci` and on the builder, Gradle compiles with the environment's own JDK 25, and toolchain auto-download is disabled there. A missing JDK 25 fails the build instead of triggering a download. |
| MAINT-21-10 | **Workflow pairs equivalent** | M | `.gitea/workflows/build-plugin.yml` and `.github/workflows/build-plugin.yml` make the same JDK and verifier changes, within each platform's constraints, and both parse. The GitHub `build` job runs green on the feature branch before merge. |
| MAINT-21-11 | **Sandbox test reads the current sandbox** | M | `SandboxAssetsIntegrationTest` reads exactly the sandbox `prepareSandbox` produced for this build's platform. It never reads a stale sandbox left by another platform version or by the retired `build/idea-sandbox`. |
| MAINT-21-12 | **Bundled-plugin exclusions guarded** | M | The bundled plugins excluded from the unit-test classpath are absent from the test application, and a test fails if one is loaded. |
| MAINT-21-13 | **CI image rollout cannot break `main`** | M | `main`'s CI job script passes in the new `lunar-ci` image, pulled from the tier2 registry, before that image is copied to GAR. The GAR copy deploys it. A job log shows which image ran, and a rollback procedure exists for before and after PR 1 merges. |
| MAINT-21-14 | **Test runner works live** | S | In GoLand 2026.2.3 and in 2026.1.3, a busted spec runs from a Lua test run configuration and shows passed and failed nodes in the test tree. |
| MAINT-21-15 | **Docs, local tooling, changelog** | S | Tracked and local-only references match the new platform, the sandbox location and the CI facts. `generate.sh` (local-only) finds the 262 platform. The CHANGELOG carries the entry. |

## Detailed Specifications

### MAINT-21-03: Bytecode target
2026.1.3 and 2026.2.3 both bundle JBR 25 (DR-06). Java 21 bytecode therefore protects only 2026.1
installs running on a custom or alternate Java 21 runtime. The 261 platform is itself Java 21
bytecode (DR-06), so Java 21 is the floor that matches its own runtime requirement. Kotlin and Java
compilation must agree on the target (DR-10).

### MAINT-21-04: Comparison with `main`
The main-side numbers are DR-12's. A lower test count means tests were silently dropped, for
example by a classpath change that disables our plugin. Duration is part of the requirement because
one bundled plugin made the suite about three times slower (DR-04). DR-12 ran with other daemons busy
on the builder, so its duration is an upper estimate.

### MAINT-21-05: The gate
The since-build is confirmed, and the user confirmed the failure-level narrowing on 2026-10-26. The gate fails on `COMPATIBILITY_PROBLEMS`, `MISSING_DEPENDENCIES` and `INVALID_PLUGIN`. The
internal-API and override-only findings already exist on both IDEs, so they are reported but not
gated (DR-09); they are tracked as [MAINT-44](../44-verifier-reported-api-usages/requirements.md). Compiling against 262 can produce bytecode that 261 cannot link even when the source
is unchanged; design §2.5 fixes the one case found (DR-09). If a problem appears only on 2026.1.3
and has no binary-compatible fix, the response is Gap 2.1. A problem on 2026.2.3 is fixed forward,
or filed as a bug while PR 2's merge is held (design §7).

### MAINT-21-09: "Never downloaded"
Auto-download is disabled in the environment, not in the repo, through a line in
`$GRADLE_USER_HOME/gradle.properties`. Developer machines keep foojay as a fallback (design §3.2).
With auto-download disabled, a missing JDK 25 is a toolchain-resolution failure that names the
requested version (DR-19).

### MAINT-21-13: Why this is a requirement
The `lunar-ci` pool resolves its image as the newest semver tag in GAR, with no pin. Copying `0.3.0`
to GAR therefore puts it under every `lunar-ci` job at once, including `main`'s, before any PR has
run on it. The tier2 push before that copy deploys nothing, which is why the gate runs between the
two (design §2.7).

## Behavior Rules
- On `lunar-ci` and on the builder, Gradle's own JVM (`JAVA_HOME`) stays JDK 21. The JDK 25
  toolchain is found by auto-detection (DR-07). The `setup-java` lanes run Gradle on JDK 25 itself
  (DR-13).
- No CI lane may download a JDK.

## Test Cases

| # | Requirement | Given (input) | When (action) | Then (expected) |
|---|-------------|---------------|---------------|-----------------|
| TC-01 | MAINT-21-01, -02 | the feature branch | `builder-run libvirt buildPlugin --rerun` | `BUILD SUCCESSFUL`; `build/distributions/lunar-<version>.zip` exists |
| TC-02 | MAINT-21-03 | the zip from TC-01, on the builder | run the script in design §5.1 | every line reads `major=65`; no other major appears |
| TC-03 | MAINT-21-04 | the feature branch | `builder-run libvirt test --rerun --no-build-cache`; tally `build/test-results/test/*.xml`, read straight after the run, mtimes checked | `failures=0 errors=0`; `tests` ≥ DR-12's count; `BUILD SUCCESSFUL in` ≤ 1.5 × DR-12's duration |
| TC-04 | MAINT-21-05 | the feature branch | `builder-run libvirt verifyPlugin` | the log has `against GO-261.25134.147: Compatible.` and `against GO-262.10968.67: Compatible.`; `BUILD SUCCESSFUL` |
| TC-05 | MAINT-21-06 | the feature branch, corpus fetched into `test/` | `builder-run libvirt test --rerun --no-build-cache --tests '*Corpus*' -PwithCorpus` | `BUILD SUCCESSFUL`; the XML lists `LuaCorpusSweepTest` and `LuaTortureCorpusTest` with `failures=0` |
| TC-06 | MAINT-21-07 | the feature branch | `builder-run libvirt ktlintCheck` | `BUILD SUCCESSFUL` |
| TC-07 | MAINT-21-08 | the feature branch | `builder-run libvirt compileIntegrationTestKotlin compileRedisIntegrationTestKotlin` | `BUILD SUCCESSFUL` |
| TC-08 | MAINT-21-09 | `lunar-ci:0.3.0` built from PR 1's Dockerfile and pulled from tier2 by its recorded digest, a **fresh** Gradle volume, and `main` plus `maint-21-probe-r2.diff` (Phase 1 has no feature branch yet) | `docker build`; then in that image, after PR 1's `Verify toolchain` lines: `./gradlew javaToolchains` and `./gradlew compileJava --rerun --info` (design §5.2) | the image build succeeds; `javaToolchains` shows `Auto-download: Disabled` and only `/usr/lib/jvm` JDKs; the info log has `Compiling with toolchain '/usr/lib/jvm/java-25-openjdk-amd64'`; no `$GRADLE_USER_HOME/jdks` directory exists |
| TC-09 | MAINT-21-09 | the builder after Phase 1's bootstrap re-run (foojay's JDK was deleted on 2026-10-25) | `builder-run libvirt javaToolchains`, then `builder-run libvirt compileJava --rerun --info`, on the feature branch | `javaToolchains` shows `Auto-download: Disabled`; the info log has `Compiling with toolchain '/usr/lib/jvm/corretto-25'` |
| TC-10 | MAINT-21-09, -13 | Phase 1 PR on Gitea, after the GAR deploy | the `build` job runs | `Verify toolchain`'s first line is `openjdk version "25.`, which identifies image 0.3.0 (DR-25). It prints `org.gradle.java.installations.auto-download=false` and `jdk25 ok`. The job is green |
| TC-11 | MAINT-21-11 | the feature branch | `builder-run libvirt test --rerun --tests '*SandboxAssetsIntegrationTest*'` | passes |
| TC-12 | MAINT-21-12 | the feature branch | `builder-run libvirt test --rerun --tests '*TestClasspathExclusionsTest*'` | passes |
| TC-13 | MAINT-21-13 | `main`'s source in the 0.3.0 image pulled from tier2 by its recorded digest | run `main`'s `build` job steps after PR 1's `Verify toolchain` lines (design §5.2) | `buildPlugin`, `ktlintCheck` and `test --rerun` succeed with `failures=0` |
| TC-14 | MAINT-21-10 | both workflow files | `python3 -c 'import sys,yaml; [yaml.safe_load(open(f)) for f in sys.argv[1:]]' .gitea/workflows/build-plugin.yml .github/workflows/build-plugin.yml` | exits 0 |
| TC-15 | MAINT-21-14 | GoLand 2026.2.3 via `runIde`, fixture and busted registration in design §5.3 | run `Lua Tests: arith_spec.lua` | the test tree shows `adds` passed and `subtracts` failed; the IDE log has no `NoClassDefFoundError` containing `com/intellij/execution/testframework` |
| TC-16 | MAINT-21-14 | GoLand 2026.1.3 via `runIde261` (or design §5.3's fallback launch), same fixture | the same as TC-15 | the same as TC-15, and the log's build line names `261.25134.147` |
| TC-18 | MAINT-21-10 | the PR 2 branch pushed to the `github` remote, with a draft PR opened on GitHub | the `Build Plugin` workflow's `pull_request` run | its `build` job is green and its log has a `Set up JDK 25` step; `Verify plugin compatibility` is skipped (tag-only) |
| TC-17 | MAINT-21-15 | merged `main`, unchanged `lua.bnf`/`.flex`, the edited local `generate.sh` | run `.claude/skills/generate-parser/scripts/generate.sh` | it finishes, prints a `GoLand Lib Dir` ending `goland-2026.2.3/lib`, and `git diff --stat src/main/gen` is empty |

**What each case must catch**
- **TC-02** is red if `jvmTarget`/`release` is dropped: a JDK 25 toolchain on its own emits
  major 69 (DR-07).
- **TC-03** is red if either exclusion or the ide-starter move is undone (DR-03, DR-04).
- **TC-04** is red if design §2.5 reverts to `PathMacroManager.getInstance(project)` (DR-09).
- **TC-05** is red on any baseline drift.
- **TC-07** is red if a non-unit source set loses a dependency it imports.
- **TC-08** is red if the image lacks JDK 25: with auto-download off, the build fails rather than
  downloading (DR-19 N1). It is also red if the `gradle.properties` line is not written:
  `Auto-download: Enabled` (DR-19 M1).
- **TC-08** is also red on a reused Gradle volume that holds a downloaded JDK: the `jdks` check
  fails.
- **TC-09** is red if the bootstrap's `gradle.properties` line is missing (`Auto-download: Enabled`),
  or if any JDK other than Corretto compiles.
- **TC-10** is red on a pre-0.3.0 image: its first line reads `openjdk version "21.` (DR-25).
- **TC-11** is red with `sandbox.home` set back to `build/idea-sandbox` (measured, DR-08).
- **TC-12** is red with `JavaScript` removed from the exclusions (measured, DR-20).
- **TC-13** is red if the image breaks `main`.
- **TC-14** is red on a YAML syntax error.
- **TC-15** and **TC-16** are red if the test-runner classes are not visible to the plugin class
  loader (Risk 1.2).
- **TC-17** is red with the old hard-coded `caches/8.14.4` path.
- **TC-18** is red if the GitHub lane's JDK or Gradle steps fail. GitHub runs Actions for this
  repository (DR-24).

## Acceptance Criteria
- [ ] TC-08 and TC-13 pass against the tier2 image **before** it is copied to GAR. The
      which-image-ran check and TC-10 pass after.
- [ ] TC-01 to TC-07, TC-09, TC-11, TC-12, TC-14 and TC-18 pass on the PR 2 branch, with logs
      named in the PR.
- [ ] TC-15 and TC-16 pass through `verify-in-ide`, with screenshots, **before PR 2 merges**.
- [ ] TC-17 passes on the primary checkout after the merge.
- [x] The user has confirmed or overridden the narrowed verifier levels before PR 2 merges:
      confirmed on 2026-10-26. The since-build is already confirmed.

## Non-Functional Requirements
- The suite takes at most 1.5 times as long as `main` (MAINT-21-04).
- Lunar's runtime behaviour does not change. The only production source edit is `expandMacros`
  (design §2.5), which returns the same service instance as before.

## Dependencies
- The 2026.2 platform release, which has shipped (GoLand 2026.2.3, build 262.10968.67).
- Phase 1 (the image publish and deploy, and PR 1) completes before PR 2's CI run (implementation-plan).

## See Also
- Design: [design.md](design.md)
- Plan: [implementation-plan.md](implementation-plan.md)
- Risks: [risks-and-gaps.md](risks-and-gaps.md)
- Follow-up: [BUG-524](../../bug-fixes/524-integration-tests-unverified-on-262/bug-report.md)
