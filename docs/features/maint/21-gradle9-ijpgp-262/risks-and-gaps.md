---
id: "MAINT-21-RISKS"
title: "Risks & Gaps"
type: "risk"
parent_id: "MAINT-21"
folders:
  - "[[features/maint/21-gradle9-ijpgp-262/requirements|requirements]]"
---

# MAINT-21: Risks & Gaps

## Prototype

The plan's central premise is runtime behaviour: the combined bump builds and the suite passes. So it
was executed before the design was written.

- **Base**: `2d278b21` (= `origin/main` at planning time), worktree `maint-21-plan`.
- **Builder**: libvirt `debian13`. The Gradle daemon runs on Corretto 21.0.11, and tests run on
  GoLand 2026.2.3's bundled JBR 25.0.4.
- **Local container** (DR-19): the PR-1 image, built from `pr1-image/Dockerfile`.
- **Edits**: the final probe diff is
  `~/.cache/claude-scratch/lunar/maint-21/maint-21-probe-r2.diff`. It reproduces design §2.1–§2.6
  exactly, without comments. `maint-21-probe.diff` is the earlier version, without §2.6.6's test
  change, §2.6.7 and §2.6.8. The probe was restored to `HEAD` and is not committed.
- **Logs**: `~/.cache/claude-scratch/lunar/maint-21/logs/NN-*`, and `b2/out/*` for the container.
  Every JUnit tally was read straight after its run, and its XML mtimes were checked against the run.

Load-bearing premises, each *read* (existence) or *runtime* (behaviour):

| Premise | Kind | Settled by |
|---------|------|-----------|
| The bump cannot be split | runtime | DR-18 |
| The combined bump compiles and builds a zip | runtime | DR-01, DR-02 |
| The unit suite passes on 262 | runtime | DR-03, DR-04 and the final suite runs |
| The plugin is binary-compatible with 261 | runtime | DR-09 |
| Java 21 bytecode can be emitted against 262 | runtime | DR-10 |
| The corpus baselines do not move | runtime | DR-14 |
| The new image does not break `main` | runtime | DR-19 |
| JDK 25 is found locally, and a download can be prevented | runtime | DR-07, DR-19 |
| No `plugin.xml` change is needed for the test runner | read, then runtime | DR-05, then TC-15 and TC-16 |
| The sandbox moved, and the test reads only the current one | read and runtime | DR-08 |

## Pre-Implementation De-risking Tasks

| ID | Action | Observed (verbatim where quoted) | Verdict |
|----|--------|----------------------------------|---------|
| DR-01 | Gradle 9.7.1, IJPGP 2.19.0 and the spike plugin versions, with foojay 0.8.0: `buildPlugin` (`01-buildPlugin.log`) | `Class org.gradle.jvm.toolchain.JvmVendorSpec does not have member field 'org.gradle.jvm.toolchain.JvmVendorSpec IBM_SEMERU'`, `BUILD FAILED in 1m 31s`. The unchanged wrapper jar downloaded and ran 9.7.1. Warnings: `build.gradle.kts:54:53`, `:57:58` and `:357:62` "deprecated … See the Gradle 9.6 upgrading guide" | Fixed forward: foojay 1.0.0. The deprecations are warnings only. 9.7.1 because IJPGP's CHANGELOG `[next]` lists the fix for "`Could not find …jar (bundledPlugin:…)` errors with Gradle 9.8.0+" (`ijpgp-CHANGELOG.md:44`) |
| DR-02 | The same, with foojay 1.0.0 (`02-buildPlugin.log`, `24-javaToolchains.log`) | `BUILD SUCCESSFUL in 2m 35s`. The task list runs `… prepareSandbox, buildSearchableOptions SKIPPED, … buildPlugin` with no `verifyPlugin`. foojay auto-provisioned `Eclipse Temurin JDK 25 (25.0.5+8-LTS)`, `Location: /opt/cache/gradle/jdks/eclipse_adoptium-25-amd64-linux.2`, `Detected by: Auto-provisioned by Gradle` | Confirmed. That JDK was deleted from the builder with the user's approval on 2026-10-25 (`ls /opt/cache/gradle/jdks` → `CACHEDIR.TAG` only, `42-busted-and-configs.txt`) |
| DR-03 | Full suite with ide-starter still in `testImplementation` (`03-test.log`, `03-test.junit.txt`, `04-deps.log`) | `tests=2487 failures=1847`: 1258 × `NoSuchMethodError: …ApplicationManagerEx.runInStressTest`, 540 + 36 × `…TestLogger.isInStressTest()`. The other 13 are knock-on: lexer assertions reporting "lexer threw NoSuchMethodError" (8), `Could not initialize class …LightVirtualFile` (2), `Missing extension point: com.intellij.eelProvider` (2) and one `ExceptionInInitializerError`. `testRuntimeClasspath` resolves `com.jetbrains.intellij.platform:core:253.29346.240`, `util:253…` and others through `ide-starter-junit5:253.29346.240` | The 253 jars shadow 262. Fixed forward: design §2.6.3 |
| DR-04 | IJPGP 2.19's bundled-plugin test classpath, in four configurations (`05`, `09`, `11` and `15-test.*`, `11-test-threaddump.txt`) | **Default**: `tests=3226 failures=538`, all `TestLoggerAssertionError: Finished `go` with an exception. Commandline: go env GOPATH … [Plugin: com.ypwang.plugin.go-linter]`; `BUILD FAILED in 32m 39s`. **`…ClasspathEnabled=false`**: `failures=1621`; top messages `PsiPlainTextFileImpl cannot be cast to … LuaFile` and `Missing extension point: JavaScript.JsonSchema.ProviderFactory`. **Excludes + go-linter**: `failures=1`, `BUILD FAILED in 32m 28s`. The EDT thread dump shows the test parked in `IndexWaiter.waitNow` while `DefaultDispatcher-worker-16 @Indexing` runs `TypeScriptPropertySignatureImpl … JSFileStubBuilder`. **Excludes + go-linter + JavaScript** (with §2.6.2 and the first-round `sandbox.home` also applied): `failures=0`, `BUILD SUCCESSFUL in 11m 8s`. During these runs `ps` showed the test worker as `…/transformed/goland-2026.2.3/jbr/bin/java` | Fixed forward: design §2.4, §3.1. The JavaScript attribution rests on the **thread dump**. The 32m→11m pair differs by three changes, so on its own it attributes nothing. The one failure in the third configuration, `LuaRenameConflictTest.testCancellationIsCheckedPerFileNameRefNotPerCollisionsCall` ("cost 8"), is **not attributed** (Risk 1.3) |
| DR-05 | 262 and 261 test-runner layout (`06-testrunner-descriptors.txt`, `07-smrunner-deps.txt`, `08-261-testrunner-layout.txt`, javap of the 262 core's `ProductPluginInitContextKt`) | **262**: `plugins/platform-testRunner-plugin/lib/modules/intellij.platform.{testRunner,smRunner}.jar`. Plugin `intellij.testRunner.plugin` declares `<content namespace="jetbrains"><module name="intellij.platform.testRunner" loading="required">` with `visibility="public"`, and smRunner's `<dependencies>` include `intellij.platform.testRunner`. The list behind `externalNonBundledPluginCompatibilityDependencies` ends `…, "intellij.platform.bookmarks", "intellij.platform.smRunner"`. **261** (261.25134.147): `{'name': 'intellij.platform.smRunner', 'kind': 'productModuleV2', 'classPath': ['lib/intellij.platform.smRunner.jar']}`, and no testRunner layout entry | In 262, smRunner is an implicit dependency of non-bundled plugins, and reaches testRunner transitively (`MultiParentClassLoaderHelper.getAllParents`). The transitive step is **read** (Risk 1.2) |
| DR-06 | Platform class-file versions and bundled JBRs (`12-platform-classver.txt`) | 261.25134.147: `LanguageBraceMatching` and `GeneratedParserUtilBase` major=65. 262.10968.67: both major=69. Bundled JBRs: `25.0.3` (2026.1.3) and `25.0.4` (2026.2.3) | 261 is Java 21 bytecode. JDK 25 is needed only for 262 |
| DR-07 | Debian 13 JDK 25 and Gradle auto-detection (`10-image-probe.log`; strings of Gradle 9.7.1's `LinuxInstallationSupplier`) | `openjdk-25-jdk-headless` installs beside 21, and `/usr/bin/java` moves to 25. Gradle 9.7.1 running on JDK 21 shows `Debian JDK 25 (25.0.4.1+1-1-deb13u1-Debian)`, `Location: /usr/lib/jvm/java-25-openjdk-amd64`, `Detected by: Common Linux Locations`. A `toolchain(25)` compile gives major `69`. Roots scanned: `/usr/lib/jvm`, `/usr/lib64/jvm`, `/usr/java`, `/usr/local/java`, `/opt/java` | Confirmed |
| DR-08 | Sandbox location, and the test's selection of it (builder listing; IJPGP 2.19 source; `32-r2-green.*`, `33-r2-mutants.*`) | The sandbox is `.intellijPlatform/sandbox/lunar/GO-2026.2.3/…`, and a stale `build/idea-sandbox/GO-2026.1.3` remains on the builder. `PrepareSandboxTask.kt:416-419`: `container.dir(project.name).dir("${productInfo.productCode}-${productInfo.version}")`. With design §2.6.6, `SandboxAssetsIntegrationTest` passes. With `sandbox.home` reverted to `build/idea-sandbox` it fails on the builder: `Expected runtime assets directory to exist: /home/builder/lunar/build/idea-sandbox/plugins/lunar/runtime` | Confirmed, both halves |
| DR-09 | `verifyPlugin` against GoLand 2026.1.3 and 2026.2.3, before and after the fix (`16-verify.log`, `17-verify.log`) | **Before**: GO-262.10968.67 `Compatible`. GO-261.25134.147 `2 compatibility problems`, both `Access to unresolved class/field com.intellij.openapi.components.PathMacroManager.Companion` in `LuaProjectSettingsKt.expandMacros`. The task failed on `[INTERNAL_API_USAGES, OVERRIDE_ONLY_API_USAGES, COMPATIBILITY_PROBLEMS]`. Internal and override-only usages appear on **both** IDEs: 8 on 261, 9 on 262. **After** design §2.5 and §2.6.5: `against GO-261.25134.147: Compatible.`, `against GO-262.10968.67: Compatible.`, `BUILD SUCCESSFUL in 53s` | Nothing forces `sinceBuild = 262`. No unresolved `com.intellij.execution.testframework` class on either IDE |
| DR-10 | Bytecode target (`14-test.log`, `18-zip-classver.txt`) | With `kotlin { compilerOptions { jvmTarget } }`: `Inconsistent JVM-target compatibility detected for tasks 'compileTestJava' (21) and 'compileTestKotlin' (25)`. With design §2.6.2: `lunar/lib/lunar-0.18.0.jar: major=65 classes=1764` | Confirmed |
| DR-11 | `ktlintCheck` on the probe: first the round-1 probe on the builder (`21-ktlint.log`), then the round-2 probe in the PR-1 image (`45-r3-container.log` D3, `b2/job4.sh`). The second includes the edited `SandboxAssetsIntegrationTest.kt` and the new `TestClasspathExclusionsTest.kt` | `BUILD SUCCESSFUL in 42s`; then `D3 exit=0`, `BUILD SUCCESSFUL in 52s` | Confirmed over every file the feature edits |
| DR-12 | `main` baseline: the full suite at `2d278b21`, restored tree, same builder (`26-main-baseline-test.*`) | `tests=3226 failures=0 errors=0 skipped=4`, `BUILD SUCCESSFUL in 11m 51s` (Gradle 8.14.4, IJPGP 2.5.0, 261). The log's line 4 reads `Starting a Gradle Daemon, 7 busy Daemons could not be reused`, so other builds shared the VM | Baseline for MAINT-21-04. Its duration is inflated by that contention, which makes TC-03's 1.5× bound looser, not tighter. The mutation in DR-04 (≈32 min) still exceeds it |
| DR-13 | Gradle 9.7.1 itself on JDK 25, as the `setup-java` lanes run it (`23-`, `25-gradle-on-jdk25*.log`, `36-verify-on-jdk25.log`) | Daemon on Temurin 25: `1 incompatible Daemon could not be reused`. `buildPlugin ktlintCheck --rerun-tasks` executed `compileKotlin`, `compileJava`, `ktlintCheck` and `buildPlugin`: `BUILD SUCCESSFUL in 48s`. `verifyPlugin`: both IDEs `Compatible.`, `BUILD SUCCESSFUL in 44s`. In the PR-1 image with `JAVA_HOME` set to JDK 25 (`45-r3-container.log` D4–D5): `test --rerun -PexcludeExternalFixtureTests` `BUILD SUCCESSFUL in 11m 24s`, `tests=3201 failures=0 skipped=4`; `koverXmlReport -x test …` `BUILD SUCCESSFUL in 2m 19s`, `<counter type="LINE" missed="5275" covered="22460"/>` | Confirmed for every Gradle step of the `setup-java` lanes |
| DR-14 | Corpus gate on the probe (`22-corpus.log`, `22-corpus.junit.txt`) | `BUILD SUCCESSFUL in 5m 23s`. XML from this run only: `LuaCorpusSweepTest`, `LuaTortureCorpusTest`, `tests=5 failures=0` | Baselines hold |
| DR-15 | What `recommended()` resolves to (`printProductsReleases`, `16-verify.log`) | `GO-263.7307.43`, `GO-2026.2.3`, `GO-2026.1.5` | Replaced by pinned IDEs |
| DR-16 | Non-unit sources on the probe (`39-r2-compile-nonunit-rerun.log`) | `compileIntegrationTestKotlin` and `compileRedisIntegrationTestKotlin` executed, not from cache: `BUILD SUCCESSFUL in 20s` | Both compile. Integration runtime is BUG-524 |
| DR-17 | Kover on the probe suite (`20-kover.log`, `20-kover-report.txt`) | `koverXmlReport -x test -x integrationTest -x redisIntegrationTest`: `BUILD SUCCESSFUL in 4s`. `<counter type="LINE" missed="5066" covered="22669"/>` | Kover 0.9.2 works on Gradle 9.7.1 |
| DR-18 | Why the bump cannot be split. Two runs bracket it: the `goland-262` prototype (2026-10-10, `6726d4f8/goland-262/05-test-stacktrace-ijpgp250.log`, `06-test-ijpgp2110.log`) and the July spike | **262 on Gradle 8.14.4, with IJPGP 2.5.0 and with 2.11.0** (2.11.0 is the newest IJPGP whose minimum Gradle, 8.13, 8.14.4 meets): `:test` fails before any test with `nl.adaptivity.xmlutil.serialization.UnknownXmlFieldException: Could not find a field for name (org.jetbrains.intellij.platform.gradle.models.ModuleDescriptor) module/namespace (Attribute)`, then `Could not resolve all dependencies for configuration ':testRuntimeClasspath'` (2.5.0) / `':intellijPlatformTestClasspath'` (2.11.0). IJPGP's CHANGELOG puts "Accept `namespace` and `visibility` attributes when parsing `module-descriptor.xml` files" (`ijpgp-CHANGELOG.md:261`) and "Update minimal supported Gradle version to `9.0.0`" (`:254`) both in 2.12.0. **The other half, IJPGP 2.17.0 on 261**: `:test` failed 1080/1459, 1075 of them on `NoSuchMethodError: JulLogger.configureLogFileAndConsole(…)` (`docs/features/maint/03-deprecation-cleanup/design.md:146-148`) | 262 needs IJPGP ≥ 2.12, which needs Gradle 9. On 261, only IJPGP 2.17.0 was measured to fail; IJPGP 2.12–2.16 and 2.18–2.19 on 261 were not run. The plan does not depend on that bracket, because it moves to 262 regardless |
| DR-19 | The PR-1 image (`pr1-image/Dockerfile`: 0.2.0 plus `openjdk-25-jdk-headless`) running `main` and the probe as the `lunar-ci` job does (`30-pr1-image-build.log`; `31-b2-container.log`, `b2/job.sh`; `40-b2-autodownload.log`, `b2/job2.sh`; `41-b2-main-with-pr1-verify.log`, `b2/job3.sh`) | **Image**: builds; `java -version` → `openjdk version "25.0.4.1"`; `JAVA_HOME` 21. **`main` (Gradle 8.14.4, `jvmToolchain(21)`)**: `buildPlugin` `BUILD SUCCESSFUL in 8m 22s`; `test --rerun -PexcludeExternalFixtureTests` `BUILD SUCCESSFUL in 11m 49s`, `tests=3200 failures=0 skipped=4`. **`main` after PR 1's Verify lines**: under the table. **Probe in the image**: `Compiling with toolchain '/usr/lib/jvm/java-25-openjdk-amd64'.`, and no `jdks` directory is created. **Auto-download switches**, read from `javaToolchains`: `GRADLE_OPTS=-D…=false` → `Auto-download: Enabled`; `ORG_GRADLE_PROJECT_…=false` → `Enabled`; `-P…=false` → `Disabled`; a `$GRADLE_USER_HOME/gradle.properties` line → `Disabled`. **JDK 25 hidden** (`-Porg.gradle.java.installations.auto-detect=false`): with auto-download off, `compileJava` fails with `Cannot find a Java installation on your machine … matching: {languageVersion=25 …}. Toolchain auto-provisioning is not enabled.` and no `jdks` directory. With the line removed (the control), it succeeds and creates `jdks/eclipse_adoptium-25-amd64-linux.2` | The image does not break `main` with `/usr/bin/java` → 25. Only `gradle.properties` (or `-P`) disables downloads, and with it disabled a missing JDK fails instead of downloading |
| DR-20 | `TestClasspathExclusionsTest` (design §2.6.8), green and mutant (`32-r2-green.*`, `33-r2-mutants.*`) | With the exclusions: passes. With `JavaScript` removed from the property: `bundled plugins excluded from the test classpath were loaded anyway expected:<[<empty>]> but was:<[JavaScript ]>` | The guard can fail |
| DR-21 | `runIde261` (design §2.6.7) (`34-runIde261-task.log`, `35-prepareSandbox-runIde261.log`) | `help --task runIde261` resolves the task. `prepareSandbox_runIde261` succeeds and creates `.intellijPlatform/sandbox/lunar/GO-2026.2.3/{config,log,plugins,system}_runIde261`, with `plugins_runIde261/lunar` | The task registers. Which IDE it launches is TC-16's build-line check; design §5.3 step 8 is the fallback |
| DR-22 | busted on the builder, read-only (`42-busted-and-configs.txt`) | `/usr/local/bin/busted` (root, 0755): a LuaRocks wrapper, `exec '/usr/bin/lua5.1' … '/usr/local/lib/luarocks/rocks-5.1/busted/2.3.0-1/bin/busted'`; `busted --version` → `2.3.0`. The 262 sandbox configs (`config`, `config_runIde261`) hold only `options/updates.xml`. The 2026.1.3 sandbox's `options/lunar.xml` registers busted at `/usr/local/bin/busted` (`43-261-sandbox-lunar-xml.txt`) | busted exists. The 262 sandboxes need it registered (design §5.3 step 2) |
| DR-23 | Does design §5.3's minimal `lunar.xml` register busted? A throwaway test on `main` loads that XML into `LuaToolchainRegistry` and resolves (`44-busted-registration-probe.log`; source kept as `maint-21/Maint21BustedRegistrationProbeTest.kt`) | `MAINT21-PROBE resolution=Resolved(tool=LuaRegisteredTool(id=maint21-busted, kindId=busted, path=/usr/local/bin/busted, …, health=LuaToolHealth(fileExists=true, executable=true, probeOk=null, …)), source=INVENTORY_FALLBACK)`; `tests="1" failures="0"`. The control, an empty registry, is the existing `LuaTestRunnerTest.testBustedUnresolvedThrows` ("Busted is not configured") | The XML is sufficient. The probe file was removed |
| DR-24 | Does GitHub run this repository's Actions, and on what? (`48-github-actions.txt`, read-only `gh`) | Runs for `Build Plugin` on `main` pushes and on tags `v0.21.9`–`v0.21.11`, all `success` (latest 2026-10-15). Both workflows are `active`. No `pull_request` runs yet. The workflow triggers on `push: branches: [main]`, `tags: ['v*']` and `pull_request` | A branch push alone does not run it; a draft PR does (design §5.5) |
| DR-25 | Can a job log tell 0.2.0 from 0.3.0? (`46-gitea-build-java-version.txt`; read-only Gitea API, `gitea-java-version.py`) | `main`'s newest green `build` job (run 30189, `2d278b21`) logs `openjdk version "21.0.11" 2026-04-21`, `OpenJDK Runtime Environment (build 21.0.11+10-1-deb13u2-Debian)` from `Verify toolchain`. In the PR-1 image, `java -version` reads `openjdk version "25.0.4.1"` (DR-19). `kubectl` here has no `lunar-ci` runner deployment (`kubectl get deploy -A` lists only `sim-ci`), so the controller-log route is not available from this machine | The first `Verify toolchain` line identifies the image |
| DR-26 | Paths the procedures name (`47-goland-261-launcher.txt`, `49-tier2-catalog.txt`) | `/opt/cache/gradle/caches/9.7.1/transforms/ac0c49ca…/transformed/goland-2026.1.3` exists, `product-info.json` reads `261.25134.147`, and `bin/goland.sh:96-97` reads `if [ -n "$GOLAND_PROPERTIES" ]; then IDE_PROPERTIES_PROPERTY="-Didea.properties.file=$GOLAND_PROPERTIES"`. The tier2 catalog has per-project namespaces (`loom/…`, `lunar/repository`); `lunar/lunar-ci` answers `NAME_UNKNOWN` (not yet pushed). `crane` v0.22.1 is installed locally (`~/go/bin/crane`) | Grounds design §2.7 and §5.3 step 8 |
| DR-27 | GAR tags, read-only (`50-gar-tags.txt`) | `crane ls northamerica-northeast1-docker.pkg.dev/cosmic-region-400615/lunar/lunar-ci` → `0.1.0`, `0.2.0` (2026-10-25). Read access works from this machine; write access was not tested | `0.3.0` is monotonic today. Phase 1 step 3a re-checks it at deploy time |

**DR-19, job3** (`main` with PR 1's Verify-toolchain lines run first, `41-b2-main-with-pr1-verify.log`):
- The Verify lines print `jdk25 ok`, and `javaToolchains` shows `Auto-download: Disabled`.
- `buildPlugin`: `BUILD SUCCESSFUL in 34s`.
- `ktlintCheck`: `BUILD SUCCESSFUL in 32s`.
- `test --rerun -PexcludeExternalFixtureTests`: `BUILD SUCCESSFUL in 7m 52s`,
  `tests=3200 failures=0 skipped=4`.
- The Gradle volume still held the JDK 25 that job2's control had downloaded. `main` compiles
  with toolchain 21, so that JDK is irrelevant to this result.

**DR-19, job4** (round-2 probe tree on a **fresh** Gradle volume, `45-r3-container.log`, `b2/job4.sh`):
after the Verify lines, `javaToolchains` lists `Auto-download: Disabled` and only
`/usr/lib/jvm/java-21-openjdk-amd64` (Current JVM) and `/usr/lib/jvm/java-25-openjdk-amd64`
(Common Linux Locations). `compileJava --rerun --info` gives
`Compiling with toolchain '/usr/lib/jvm/java-25-openjdk-amd64'.` and `no jdks dir`, including
after the later test and Kover runs. This is TC-08 as specified (design §5.2).

**Final probe, full suite** (`37-r2-test.log`, `37-r2-test.junit.txt`), on the configuration in design
§2.1–§2.6: `tests=3227 failures=0 errors=0 skipped=4`, `BUILD SUCCESSFUL in 10m 7s`.
`TestClasspathExclusionsTest` is the added test.

## Critical Risks

### Risk 1.1: A future GoLand patch bundles another plugin that breaks or slows the suite
- **Impact**: with IJPGP 2.19 every bundled plugin is on the test classpath (DR-04), so a new one
  can fail or slow the suite through no change of ours.
- **Likelihood**: medium. It surfaces when `platformVersion` moves.
- **Mitigation**:
  - A failing plugin shows as test failures in the routine suite.
  - A slow plugin has no automatic guard. The `gradle.properties` comment (design §2.4) requires a
    duration comparison on every `platformVersion` bump, against the previous run.
  - `TestClasspathExclusionsTest` keeps the existing exclusions in force (DR-20).
  - The same comment requires comparing IJPGP's default exclusion list on every IJPGP bump.

### Risk 1.2: Test-runner classes not visible to the plugin class loader in a real IDE
- **Impact**: Lua test run configurations fail with `NoClassDefFoundError`.
- **Likelihood**: low. DR-05's descriptors and DR-09's verdicts both point the other way, but the
  transitive class-loader step is only read.
- **Mitigation**: TC-15 and TC-16 (Phase 3). The response for each outcome is design §7's table.
  Gap 2.1 is involved only when 2026.1.3 alone fails.

### Risk 1.3: `LuaRenameConflictTest`'s cancellation-count test is environment-sensitive
- **Impact**: it counts `checkCanceled` calls attributed by stack walk. It read 8 against a required
  ≥10 in one configuration and passed in the next (DR-04). Shifting platform frames can flip it.
- **Likelihood**: low.
- **Mitigation**: TC-03 runs it. If it fails in Phase 2 or later, file a BUG with the log rather
  than editing it inside MAINT-21.

### Risk 1.4: 2026.1 compatibility is gated only at tag time
- **Impact**: code merged between tags can call a 262-only API, and that stays unnoticed until the
  next tag's `verifyPlugin` (design §9).
- **Likelihood**: medium over time.
- **Mitigation**: the tag build fails before publish, so no incompatible release ships. Running
  `verifyPlugin` on the builder is a cheap pre-merge check for any PR touching platform APIs.

### Risk 1.5: The image rollback has not been executed
- **Impact**: if 0.3.0 breaks something TC-13 does not cover, such as the `corpus` lane, every
  `lunar-ci` job is affected until it is rolled back.
- **Likelihood**: low. TC-13 runs the routine job's Gradle steps, including Kover's prerequisites,
  in the image.
- **Mitigation**:
  - Design §2.7 gives a rollback per rollout state.
  - Each state is checkable from the job log's first `Verify toolchain` line (DR-25).
  - Phase 1 step 5 checks that line right after the deploy.
  - **Not executed**: that deleting the GAR tag makes the pool resolve 0.2.0 again. That is read
    from `.agents/ci-topology.md` ("resolves newest-semver by digest").

### Risk 1.6: Gitea's `build-release` runs for the first time on a tag
- **Impact**: it runs only on `v*` tags and publishes in the same job, so it cannot be exercised
  before merge without publishing. The GitHub `build` job *can* be exercised before merge, through
  a draft PR (DR-24, TC-18).
- **Likelihood**: low. Its Gradle commands have passed with the daemon on JDK 25: `buildPlugin`,
  `ktlintCheck`, `verifyPlugin`, `test --rerun` and `koverXmlReport` (DR-13). Its YAML parses (TC-14).
- **Mitigation**: design §5.5's post-merge check of both lanes and its fix-forward branches. A
  failing verify step precedes publishing.

## Design Gaps

### Gap 2.1: `pluginSinceBuild` — keep 261 (confirmed by the user, subject to Phase 3)
- **Question**: must Lunar drop 2026.1 (`sinceBuild = 262`)?
- **Decision**: keep 261, confirmed by the user, subject to TC-15 and TC-16.
- **Evidence now**: nothing forces 262.
  - With design §2.5, the verifier reports both IDEs `Compatible` (DR-09).
  - The zip is Java 21 bytecode (DR-10).
  - `plugin.xml` needs no 261/262 split (DR-05).
- **Trigger**: any of these.
  - TC-16 fails while TC-15 passes (design §7).
  - A tag build's verifier reports a GO-261 compatibility problem that has no binary-compatible fix.
  - The user prefers to drop 2026.1.
- **Response if triggered**: the user chooses one of two.
  - Drop 2026.1: `pluginSinceBuild = 262`, delete `create(IntelliJPlatformType.GoLand, "2026.1.3")`
    from §2.6.5 and `runIde261` (§2.6.7), and add a CHANGELOG line that 2026.1 is no longer
    supported.
  - Fix the offending site.
- **Not covered here**: a failure on 2026.2.3. Dropping 2026.1 cannot fix it, so design §7 handles
  it: fix forward, or file a BUG and hold PR 2's merge.
- **Verifier failure levels**: the narrowed set in design §2.6.5 was confirmed by the user on
  2026-10-26.

## Technical Debt & Future Work
- **BUG-524**: `:integrationTest` on 262, and the 253 ide-starter pins. After PR 2 merges, every
  builder `run build` runs those unverified tests.
- **The image publish job (separate MAINT)**: a CI job that builds `lunar-ci` and pushes it to
  tier2. It is not designed here. Until it exists, Phase 1 builds and pushes by hand, and the GAR
  `crane copy` stays a supervisor step either way.
- **Gradle 10 deprecations** in `build.gradle.kts`: `by configurations.getting` at lines 54 and 57,
  and `by registering` at 357 (DR-01).
- **API usage the verifier lists but does not gate**, the same on 261 and 262 (DR-09). Filed as
  [MAINT-44](../44-verifier-reported-api-usages/requirements.md):
  - internal: `CoverageEngine.getQualifiedName`, `coverageProjectViewStatisticsApplicableTo`,
    `CoverageRunner.getInstanceById`, `UtilKt.targetPresentation`, `PluginManagerCore.getPlugin`
    (262 only) and `StartupManager.runAfterOpened`;
  - override-only: `AnAction.actionPerformed` from `LuaEnvStatusBarWidget.EnvPopupStep.invokeProvisionAction`;
  - non-extendable: `FileChooserDescriptor.isFileSelectable` in `LuaCovReportImportAction`.
- **`docker/` build stage**: it installs only JDK 21, so foojay downloads JDK 25 there. The path is
  deprecated.

## Premises Examined
- **"The bump must be one PR."** True for the build side (DR-18) and false for the environment side.
  That is why Phase 1 is separate.
- **"`JAVA_HOME` must become 25."** Removed. Gradle runs on 21 wherever the image or bootstrap
  provides the JDK. The `setup-java` lanes run Gradle on 25 because that is the simpler edit there
  (DR-13).
- **"Bump the tag in the pool values."** Removed. That premise came from a stale Dockerfile header.
  The pool resolves the newest semver in GAR, so the GAR copy is the deploy. The tier2 push before
  it is not (design §2.7).
- **"Rolling back means deleting the tag."** Only true before PR 1 merges. After that, PR 1 must be
  reverted first; after PR 2, fix forward instead (design §2.7).
- **"`/usr/bin/java` must stay 21 for `main`."** Measured unnecessary (DR-19).
- **"An image ENV can switch off toolchain downloads."** Measured false. Only `gradle.properties`
  or `-P` do it (DR-19).
- **"foojay supplies the toolchain."** Kept only for developer machines.
- **"`recommended()` verifier IDEs."** A template convention. Replaced, because it pulls an EAP
  (DR-15).
- **"The verifier's default failure levels."** Narrowed. The extra usages predate this feature
  (DR-09). Confirmed by the user on 2026-10-26.
- **"A branch push exercises the GitHub workflow."** Measured false: GitHub triggers on `main`,
  tags and pull requests only (DR-24). A draft PR is used instead.
- **"Tests should see only declared plugins"** (IJPGP 2.5 behaviour). Not restorable on 262 without
  losing the plugins that used to be core (DR-04), so the exclusion list plus its guard (DR-20)
  replace it.

## Test Case Gaps
- TC-15 and TC-16 (Phase 3) are not executed in the prototype. Their busted registration is (DR-23).
- TC-09 and TC-10 need Phase 1's bootstrap and deploy. TC-18 needs the PR 2 branch.
- TC-14 is a syntax check only (Risk 1.6).

## See Also
- Requirements: [requirements.md](requirements.md)
- Design: [design.md](design.md)
- Plan: [implementation-plan.md](implementation-plan.md)
- Follow-up: [BUG-524](../../bug-fixes/524-integration-tests-unverified-on-262/bug-report.md)
