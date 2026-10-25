---
id: "MAINT-21-DESIGN"
title: "Technical Design"
type: "design"
parent_id: "MAINT-21"
folders:
  - "[[features/maint/21-gradle9-ijpgp-262/requirements|requirements]]"
---

# Technical Design: MAINT-21 — Gradle 9, IJPGP 2.19 and the GoLand 2026.2 Platform

This is a build and environment change. It adds a test class (§2.6.8) and a Gradle task (§2.6.7),
and no `plugin.xml` extensions. Each subsection of §2 names one file and gives the exact edit.
Every behavioural claim cites a DR row in [risks-and-gaps.md](risks-and-gaps.md), which holds the
commands, logs and counts.

§2.1–§2.6 were executed on the builder as one probe diff (`maint-21-probe-r2.diff`; risks-and-gaps,
"Prototype"). They reproduce that diff and add only comments. §2.7's image and §2.8.1's lines were
executed in a local container (DR-19). The other workflow edits, §2.10's bootstrap block, and
§2.11–§2.12 have not been run.

## 1. Architecture Overview

### Current State

| Item | Today | Where |
|------|-------|-------|
| Gradle | 8.14.4 | `gradle/wrapper/gradle-wrapper.properties`, `gradle.properties` `gradleVersion` |
| IJPGP | 2.5.0 | `gradle/libs.versions.toml` `intelliJPlatform` |
| Compile and unit-test platform | GoLand 2026.1.3 (261) | `gradle.properties` `platformVersion`, `testVersion` |
| Toolchain | `jvmToolchain(21)` | `build.gradle.kts` |
| Compatibility floor | `pluginSinceBuild = 261`, no until-build (the patched `plugin.xml` has `<idea-version since-build="261" />`) | `gradle.properties` |
| Verifier IDEs | `recommended()` | `build.gradle.kts` `pluginVerification` |
| JDK in CI | `openjdk-21-jdk-headless` in `lunar-ci:0.2.0`; `setup-java` 21 on `act-ci` and GitHub | `tooling/ci-image/Dockerfile`, both `build-plugin.yml` |
| JDK on the builder | Corretto 21 at `/opt/jdk` (`JAVA_HOME`) | `tooling/gce-builder/builder-bootstrap.sh`, `startup-script.sh` |
| `lunar-ci` image deploy | the pool label is tagless and resolves the newest semver tag in GAR, so a copy to GAR deploys | `.agents/ci-topology.md:181-182`; `.gitea/workflows/build-plugin.yml:319-327` |

### Prior Art in This Repo
- **MAINT-03 §2.5 spike (2026-07-03,** `docs/features/maint/03-deprecation-cleanup/design.md:139-165`**).**
  Gradle 9.1.0, IJPGP 2.17.0 and the plugin cascade configure and compile on 261, and then the
  tests fail on 261. This feature **extends** that spike to 262.
- **`goland-262` prototype (2026-10-10, uncommitted).** It measured why the bump cannot be split
  (DR-18), and supplied three fixes reused here: `jvmToolchain(25)`, the two `bundledModule`
  lines, and nullability in `expandMacros`. Two of its statements are **replaced**, because this
  prototype measured them wrong:
  - It said the test-runner modules "load in the core classloader". They load as content modules
    of a separate plugin (DR-05).
  - Its `expandMacros` fix compiles, but it is binary-incompatible with 261 (DR-09).
- **Existing `Verify toolchain` steps** in the Gitea `build` and `corpus` jobs are **extended** with
  JDK 25 assertions.
- **`SandboxAssetsIntegrationTest`** (`src/test/kotlin/net/internetisalie/lunar/SandboxAssetsIntegrationTest.kt`)
  is **changed** so that it reads one exact sandbox instead of searching for one (§2.6.6).

### Target State
Gradle 9.7.1 runs IJPGP 2.19.0 against GoLand 2026.2.3. A JDK 25 toolchain compiles to Java 21
bytecode. The verifier gates 2026.1.3 and 2026.2.3 on every tag. Every CI and builder environment
has a JDK 25 that Gradle finds locally, and toolchain auto-download is disabled there.

## 2. Core Components

Comments shown in code blocks are part of the edit.

### 2.1 `gradle/wrapper/gradle-wrapper.properties` and `gradle.properties` `gradleVersion`
- `distributionUrl=https\://services.gradle.org/distributions/gradle-9.7.1-bin.zip`
- `gradleVersion = 9.7.1`
- Do not run the `wrapper` task. The existing `gradlew` and `gradle-wrapper.jar` start 9.7.1
  unchanged (DR-01).
- **Why 9.7.1:** it is the newest 9.x below 9.8.0, and IJPGP 2.19.0 has an unreleased fix for a
  9.8.0+ defect (DR-01).

### 2.2 `gradle/libs.versions.toml`
```toml
changelog = "2.4.0"
intelliJPlatform = "2.19.0"
kotlin = "2.3.20"
kover = "0.9.2"
qodana = "2025.1.1"
```
- IJPGP 2.19.0 is the newest release.
- Kover, Qodana and changelog take the versions the MAINT-03 spike proved. They apply on 9.7.1
  (DR-02), and Kover produces its report (DR-17).
- No Qodana task runs anywhere in this repo's CI or gates.
- `kotlin` does not change.

### 2.3 `settings.gradle.kts`
`id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"`. Version 0.8.0 fails
configuration on Gradle 9 (DR-01). The resolver stays for developer machines only. CI and the
builder disable auto-download (§2.7, §2.10).

### 2.4 `gradle.properties`
Replace lines 13–17 (the comment between the `# IntelliJ Platform Properties ->` line and
`platformType = GO`) with:
```properties
# platformType/platformVersion are the IDE the plugin compiles + runs UNIT tests against. We track
# the 2026.2 line; pluginSinceBuild=261 keeps 2026.1.x as the declared floor, and verifyPlugin
# proves it against GoLand 2026.1.3 on every tag build (build.gradle.kts pluginVerification).
```
Set `platformVersion = 2026.2.3` and `testVersion = 2026.2.3`; the `testVersion` comment block is
unchanged. After the `platformBundledPlugins` line, add:
```properties
# IJPGP 2.19 puts every bundled plugin of a 262 IDE on the unit-test classpath. The ones appended
# below break the suite (MAINT-21 DR-04): go-linter execs `go` at startup, and JavaScript indexes
# its bundled TypeScript libraries in every light project. Setting this property REPLACES IJPGP's
# default list, so that list is copied first. On every IJPGP bump, compare it against
# GradleProperties.TestIdeBundledPluginsClasspathExcludes in the new version. On every
# platformVersion bump, re-run the full suite and compare its duration with the last one.
# TestClasspathExclusionsTest fails if an appended plugin is loaded again.
org.jetbrains.intellij.platform.testIdeBundledPluginsClasspathExcludes = com.intellij.openRewrite,com.intellij.ja,com.intellij.ko,com.intellij.zh,org.jetbrains.plugins.vue,com.ypwang.plugin.go-linter,JavaScript
```
`pluginSinceBuild = 261` and its comment do not change.

### 2.5 `net.internetisalie.lunar.settings` — `LuaProjectSettings.kt`, top-level `String.expandMacros`
```kotlin
fun String.expandMacros(project: Project): String =
    project
        .service<PathMacroManager>()
        .expandPath(this) ?: this
```
- **Threading**: unchanged. It is a service lookup, callable on any thread.
- **Why not `PathMacroManager.getInstance(project)`**: compiled against 262, that call links to a
  `PathMacroManager.Companion` that 261 does not have (DR-09).
- **Same instance on both platforms**: for a `Project`, 261's `getInstance(ComponentManager)` is
  `componentManager.getService(PathMacroManager.class)`. Source:
  `platform/projectModel-impl/src/com/intellij/openapi/components/PathMacroManager.java:28-34` in
  the local `intellij-community`.
- `?: this` is for 262, where `expandPath` returns `String?`.
- `service` comes from the file's existing `import com.intellij.openapi.components.*`.

### 2.6 `build.gradle.kts`

#### 2.6.1 Imports (after the existing `TestFrameworkType` import)
```kotlin
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
```

#### 2.6.2 Toolchain and bytecode target (replaces the `kotlin { jvmToolchain(21) }` block)
```kotlin
// Compile with JDK 25: the 262 platform jars are class-file 69, which javac 21 cannot read.
// Emit Java 21 bytecode: the 261 platform is itself Java 21 bytecode, and pluginSinceBuild=261
// covers 2026.1 installs on a Java 21 runtime. Set per task: the extension-level jvmTarget
// misses the test compilations (MAINT-21 DR-10).
kotlin {
    jvmToolchain(25)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_21)
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
}
```

#### 2.6.3 ide-starter scope (in `dependencies { … }`)
Delete these two lines:
```kotlin
    testImplementation(libs.ide.starter)
    testImplementation("com.jetbrains.intellij.tools:ide-starter:253.29346.240")
```
Add these directly above `integrationTestImplementation("org.junit.jupiter:junit-jupiter:5.9.2")`:
```kotlin
    // IDE-Starter is used only by src/integrationTest. On the unit-test classpath its transitive
    // platform 253 jars shadowed the 262 platform (MAINT-21 DR-03).
    integrationTestImplementation(libs.ide.starter)
    integrationTestImplementation("com.jetbrains.intellij.tools:ide-starter:253.29346.240")
```
- `integrationTestImplementation` and `redisIntegrationTestImplementation` both extend
  `testImplementation`.
- `integrationTest` keeps ide-starter. `redisIntegrationTest` loses it, which is harmless: nothing
  under `src/redisIntegrationTest` or `src/test` imports `com.intellij.ide.starter`.
- TC-07 compiles both source sets.

#### 2.6.4 Test-runner modules (in `intellijPlatform { … }`, after `bundledModule("intellij.spellchecker")`)
```kotlin
        // 262 moved the test-runner classes out of the core into content modules of the bundled
        // `intellij.testRunner.plugin`; 261 keeps them in the core. Compile-time only: at runtime
        // 262 adds intellij.platform.smRunner (which depends on testRunner) as an implicit
        // dependency of every non-bundled plugin (MAINT-21 DR-05).
        bundledModule("intellij.platform.testRunner")
        bundledModule("intellij.platform.smRunner")
```

#### 2.6.5 Verifier (replaces `pluginVerification { ides { recommended() } }`)
The 2026.1.3 entry is confirmed, together with `pluginSinceBuild = 261` (Gap 2.1). The failure
levels are pending user confirmation.
```kotlin
    pluginVerification {
        // Fail on binary incompatibility only. The internal, override-only and non-extendable
        // usages the verifier also lists exist on both 261 and 262 (MAINT-21 DR-09) and are
        // tracked as debt, not gated here.
        failureLevel.set(
            listOf(
                VerifyPluginTask.FailureLevel.COMPATIBILITY_PROBLEMS,
                VerifyPluginTask.FailureLevel.MISSING_DEPENDENCIES,
                VerifyPluginTask.FailureLevel.INVALID_PLUGIN,
            ),
        )
        // The floor (pluginSinceBuild=261) and the compile platform, pinned: recommended() also
        // pulls the newest EAP, so its verdict would move with JetBrains' release calendar.
        ides {
            create(IntelliJPlatformType.GoLand, "2026.1.3")
            create(IntelliJPlatformType.GoLand, "2026.2.3")
        }
    }
```

#### 2.6.6 `sandbox.home` and `SandboxAssetsIntegrationTest`
In `tasks { test { … } }`:
```kotlin
        systemProperty("sandbox.home", prepareSandbox.get().sandboxDirectory.get().asFile.absolutePath)
```
- `sandbox.home` is now the exact sandbox `prepareSandbox` produces for this platform: in IJPGP 2.19
  that is `.intellijPlatform/sandbox/<project.name>/<productCode>-<version>` (DR-08).
- It used to be the parent of every platform sandbox. That parent keeps stale sandboxes from other
  versions, because `.intellijPlatform/` is outside `build/`.

In `SandboxAssetsIntegrationTest.kt`, replace the `sandboxHome` val and the whole
`val platformDir = sandboxHome.listDirectoryEntries().find { … } ?: throw …` expression with:
```kotlin
        val platformDir = Path.of(System.getProperty("sandbox.home"))
```
Then delete `import kotlin.io.path.name`. The rest of the test is unchanged.

The test now reads one directory and cannot select a stale sibling. With `sandbox.home` reverted to
`build/idea-sandbox` it fails on the builder, which still has a stale `build/idea-sandbox` (DR-08).

#### 2.6.7 `runIde261` (top level, directly above `// Gradle Changelog Plugin configuration.`)
```kotlin
// The plugin built against platformVersion, run inside GoLand 2026.1.3 (the pluginSinceBuild floor)
// for live compatibility checks: `./gradlew runIde261`.
intellijPlatformTesting.runIde.register("runIde261") {
    type = IntelliJPlatformType.GoLand
    version = "2026.1.3"
}
```
- `register` and not `by registering`, which Gradle 9.6+ deprecates (DR-01).
- The task resolves and prepares its sandbox at
  `.intellijPlatform/sandbox/lunar/GO-2026.2.3/{config,plugins,system,log}_runIde261` (DR-21).
  IJPGP names that sandbox after the base platform. Which IDE it actually launches is checked by
  TC-16's build-number assertion.

#### 2.6.8 New test: `net.internetisalie.lunar.TestClasspathExclusionsTest`
File `src/test/kotlin/net/internetisalie/lunar/TestClasspathExclusionsTest.kt`:
```kotlin
package net.internetisalie.lunar

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Guards `testIdeBundledPluginsClasspathExcludes` in gradle.properties: the bundled plugins it
 * removes from the unit-test classpath must stay absent from the test application (MAINT-21).
 */
class TestClasspathExclusionsTest : BasePlatformTestCase() {
    fun testExcludedBundledPluginsAreNotLoaded() {
        val loadedExclusions =
            EXCLUDED_PLUGIN_IDS.filter { PluginManagerCore.getPlugin(PluginId.getId(it)) != null }
        assertEmpty("bundled plugins excluded from the test classpath were loaded anyway", loadedExclusions)
    }

    private companion object {
        val EXCLUDED_PLUGIN_IDS = listOf("com.ypwang.plugin.go-linter", "JavaScript")
    }
}
```
- **Threading**: JUnit3 light fixture; `PluginManagerCore.getPlugin` needs no read action.
- The list holds only the IDs that `gradle.properties` appends to IJPGP's defaults.
- Measured green with the exclusions, and red with `JavaScript` removed (DR-20).
- The name contains neither `Corpus` nor `Performance`/`Benchmark`, so it runs in the routine suite.

### 2.7 `tooling/ci-image/Dockerfile` → `lunar-ci:0.3.0`
- In the `apt-get install` list, add `openjdk-25-jdk-headless` directly after
  `openjdk-21-jdk-headless`.
- Add this comment paragraph above the package list, after the `openjdk-21` paragraph:
  ```
  # openjdk-25: the Java 25 TOOLCHAIN (build.gradle.kts jvmToolchain(25)). Gradle finds it under
  #   /usr/lib/jvm by auto-detection; JAVA_HOME stays 21 and keeps running Gradle itself.
  #   Installing it repoints /usr/bin/java at 25 (update-alternatives picks the highest); main's
  #   jvmToolchain(21) build was run in this image with that in place (MAINT-21 DR-19).
  #   Toolchain auto-download is switched off per job, in $GRADLE_USER_HOME/gradle.properties,
  #   by the workflow's Verify toolchain step: GRADLE_OPTS cannot do it (MAINT-21 DR-19).
  ```
- `ENV JAVA_HOME` stays `/usr/lib/jvm/java-21-openjdk-amd64`. Add no other `ENV`.
- In the build-time check chain, after `"$JAVA_HOME/bin/java" -version`, add
  `&& /usr/lib/jvm/java-25-openjdk-amd64/bin/java -version \`.
- **Header.** Replace the build and push example (header lines 17–20) with the publish flow:
  ```
  #   # 1. build and push to the tier2 staging registry (anonymous push):
  #   docker build --platform=linux/amd64 \
  #     -t registry.internetisalie.net/lunar/lunar-ci:<semver> tooling/ci-image
  #   docker push registry.internetisalie.net/lunar/lunar-ci:<semver>
  #   # 2. gate: run main's job script in that tier2 image locally (MAINT-21 design §5.2)
  #   # 3. DEPLOY: copy to GAR, which the lunar-ci pool reads
  #   crane copy registry.internetisalie.net/lunar/lunar-ci:<semver> \
  #     northamerica-northeast1-docker.pkg.dev/cosmic-region-400615/lunar/lunar-ci:<semver>
  ```
  Then replace the sentence on header lines 27–28, "Bump the tag / in
  deployment/values/lunar-ci.yaml deliberately.", with:
  `# The pool's image label is tagless and resolves the newest semver tag in GAR, so the crane copy
  # IS the deploy: every lunar-ci job that starts afterwards runs the new image. Gate it on step 2.
  # The image publish job (separate MAINT) will automate steps 1-2.`
- **Registry path.** The tier2 namespace follows that registry's per-project layout. Its catalog has
  `loom/…` and `lunar/repository`, and `lunar/lunar-ci` is not yet known: `NAME_UNKNOWN`
  (DR-26).
- **Deploy precondition.** The supervisor runs the `crane copy` from a machine with GAR write
  credentials, for example after `gcloud auth configure-docker northamerica-northeast1-docker.pkg.dev`
  or `crane auth login`. If the copy fails with an authentication or permission error, stop and ask
  the user. Do not retry with other credentials.
- **Which image a job ran.** No image or digest line appears in a job log
  (`.gitea/workflows/build-plugin.yml:329-332`). The first line of `Verify toolchain` is
  `java -version`, through `/usr/bin/java`, and that line differs between the two images:
  - 0.2.0: `openjdk version "21.0.11"`, measured on `main`'s build job (DR-25);
  - 0.3.0: `openjdk version "25.0.4.1"`, because the JDK 25 package repoints `/usr/bin/java` (DR-19).
- **Rollback.** Pick the branch by how far the rollout has gone.

  | State | Rollback |
  |-------|----------|
  | After the deploy, **before PR 1 merges** | `gcloud artifacts docker tags delete northamerica-northeast1-docker.pkg.dev/cosmic-region-400615/lunar/lunar-ci:<semver> --quiet`. Jobs that start afterwards resolve 0.2.0 again; a job already running keeps its image. Confirm on the next `main` build: the first line of `Verify toolchain` reads `openjdk version "21.` |
  | **After PR 1 merges, before PR 2** | First revert PR 1 on `main`, because its `Verify toolchain` asserts JDK 25 and would fail on 0.2.0. Then delete the GAR tag as above, and confirm the same way |
  | **After PR 2 merges** | Do not roll back to 0.2.0. `main` then needs JDK 25, and downloads are off. Fix forward instead: build a corrected image as the next semver (`0.3.1`) and deploy it through the same flow |

  The tag delete has not been executed (Risk 1.5). The `gradle.properties` line a job wrote stays in
  that VM's node-local `/cache` until the VM is reaped. It is harmless for `jvmToolchain(21)`
  (DR-19 job3).

### 2.8 `.gitea/workflows/build-plugin.yml`
1. **`build` job, step `Verify toolchain`.** Append these as the **last** lines of `run:`, after the
   `fi` of the cache block. On this executor a step's verdict is its last command, as the corpus
   job's comment records:
   ```sh
   # JDK 25 is the build TOOLCHAIN (MAINT-21); JAVA_HOME stays 21 for Gradle itself. Toolchains
   # must come from the image: with auto-download off, a missing JDK fails the build instead of
   # being fetched per cold VM. A gradle.properties line covers every later gradlew call in the
   # job; GRADLE_OPTS -D and ORG_GRADLE_PROJECT_ were measured ineffective (MAINT-21 DR-19).
   mkdir -p "$GRADLE_USER_HOME"
   grep -qx 'org.gradle.java.installations.auto-download=false' "$GRADLE_USER_HOME/gradle.properties" 2>/dev/null \
     || echo 'org.gradle.java.installations.auto-download=false' >> "$GRADLE_USER_HOME/gradle.properties"
   grep -x 'org.gradle.java.installations.auto-download=false' "$GRADLE_USER_HOME/gradle.properties" && /usr/lib/jvm/java-25-openjdk-amd64/bin/java -version && echo "jdk25 ok"
   ```
   Both assertions are in that one final compound line, so either failing fails the step.
2. **`corpus` job, step `Verify toolchain (incl. …)`.**
   - Before the final compound line, insert the same `mkdir -p …` and `grep -qx … || echo …` lines
     (with their comment) as in item 1.
   - In the final compound line, insert
     `grep -qx 'org.gradle.java.installations.auto-download=false' "$GRADLE_USER_HOME/gradle.properties" && `
     at its start, and `/usr/lib/jvm/java-25-openjdk-amd64/bin/java -version && `
     directly before `echo "toolchain ok (image >= 0.2.0)"`, and change that text to
     `"toolchain ok (image >= 0.3.0)"`.
   - Change the comment "…catch the pool running a pre-0.2.0 image (no cc/python3)" to
     "…a pre-0.3.0 image (no cc/python3, or no JDK 25)".
3. **`build-release` job.**
   - Rename the step `Set up JDK 21` to `Set up JDK 25` and set `java-version: '25'`.
   - Rename `Build plugin (compile + verifyPlugin + distributable zip)` to
     `Build plugin (compile + distributable zip)`. `buildPlugin` does not run `verifyPlugin` (DR-02).
   - After that step, add:
   ```yaml
      - name: Verify plugin compatibility (GoLand 2026.1.3 + 2026.2.3)
        # pluginSinceBuild=261 is a promise to 2026.1 users; this is the step that keeps it
        # (MAINT-21). Tag builds only: it downloads a second full IDE, which the routine lane on a
        # cold per-VM pool should not pay on every push.
        run: ./gradlew verifyPlugin --no-daemon --stacktrace -PpluginVersion=${{ steps.version.outputs.version }}
   ```
4. **`build` job.** Make the same rename of its `Build plugin (…)` step.

### 2.9 `.github/workflows/build-plugin.yml`
1. Rename `Set up JDK 21` to `Set up JDK 25` and set `java-version: '25'`.
2. Rename `Build plugin (compile + verifyPlugin + distributable zip)` to `Build plugin (compile + distributable zip)`.
3. After that step, add §2.8.3's step with `if: startsWith(github.ref, 'refs/tags/v')` and the same
   `run:`. This job runs for tags and branches alike, so the `if:` makes its effect match Gitea's
   tag-only `build-release`.
4. Add to the header's "Differences" list:
   `#   - verifyPlugin runs on tags via a step `if:`; Gitea runs it in the tag-only build-release job. Same effect.`

These lanes run Gradle itself on JDK 25. DR-13 measured every Gradle step they run with the
Gradle daemon on JDK 25: `buildPlugin`, `ktlintCheck`, `verifyPlugin`, `test --rerun` and
`koverXmlReport`. §5.5 says how the GitHub lane is exercised before merge, and Risk 1.6 covers
Gitea's `build-release`.

### 2.10 `tooling/gce-builder/builder-bootstrap.sh` and `startup-script.sh`
Add this block after the Corretto 21 block in both scripts:
```sh
echo "=== Corretto 25 -> /usr/lib/jvm/corretto-25 (Gradle toolchain; JAVA_HOME stays 21) ==="
JDK25=/usr/lib/jvm/corretto-25
if [ ! -x "$JDK25/bin/java" ]; then
  tmp="$(mktemp -d)"
  wget -qO "$tmp/corretto25.tar.gz" \
    "https://corretto.aws/downloads/latest/amazon-corretto-25-x64-linux-jdk.tar.gz"
  mkdir -p "$JDK25"
  tar xzf "$tmp/corretto25.tar.gz" -C "$JDK25" --strip-components=1
  rm -rf "$tmp"
fi
# Toolchains come from the VM, never from a download (MAINT-21-09).
mkdir -p /opt/cache/gradle
grep -qx 'org.gradle.java.installations.auto-download=false' /opt/cache/gradle/gradle.properties 2>/dev/null \
  || echo 'org.gradle.java.installations.auto-download=false' >> /opt/cache/gradle/gradle.properties
```
- **`builder-bootstrap.sh` verify block**: add `/usr/lib/jvm/corretto-25/bin/java -version` after
  `/opt/jdk/bin/java -version` in the `=== verify ===` block.
- **`startup-script.sh` verify line**: add
  `echo "jdk25: $(/usr/lib/jvm/corretto-25/bin/java -version 2>&1 | head -1)"` directly before its
  final `echo "=== bootstrap complete: …"` line.
- **Header line**: change "Installs the exact build deps + Corretto 21" to
  "+ Corretto 21 (Gradle) and 25 (toolchain)".
- **`gce-builder.sh:93` log message**: change "JDK 21" to "JDK 21 + 25".

Grounding for this block:
- `/usr/lib/jvm` is one of the roots Gradle 9.7.1 scans for JDKs (DR-07).
- `/opt/cache/gradle` is `GRADLE_USER_HOME` on both backends (`config.sh:53`), and Gradle reads
  `gradle.properties` from there.
- The latest-Corretto URL resolved on 2026-10-25 (`302 → …/25.0.5.0.9/amazon-corretto-25.0.5.0.9-linux-x64.tar.gz`).
- Both blocks are idempotent.

### 2.11 Version, sandbox and CI references (tracked files)

| File | Edit |
|------|------|
| `README.md:38` | → `Requires **JDK 21** to run Gradle and **JDK 25** as the build toolchain (Gradle finds it, or downloads one on a developer machine). The Gradle wrapper pins Gradle 9.7.1.` |
| `README.md:55-56` | `2026.1.3` → `2026.2.3` in both lines, including the `e.g. go:goland:` example |
| `README.md:59` | append: `verifyPlugin checks the built zip against GoLand 2026.1.3 and 2026.2.3.` |
| `docker/Dockerfile:70` | `IDE_VERSION=2026.1.3` → `IDE_VERSION=2026.2.3`. The `build` stage keeps `openjdk-21-jdk`; on that deprecated path foojay downloads JDK 25 |
| `docker/README.md` | every `2026.1.3` → `2026.2.3` (lines 7, 23, 64, 192, 249, 251, 253, 349). Lines 255–257 → `All three track the latest 2026.2 patch. pluginSinceBuild = 261 keeps 2026.1.x as the declared compatibility floor, and verifyPlugin checks it against 2026.1.3 on every tag build.` |
| `.run/Run Plugin.run.xml:3,31` | `$PROJECT_DIR$/build/idea-sandbox/system/log/idea.log` → `$PROJECT_DIR$/.intellijPlatform/sandbox/lunar/GO-2026.2.3/log/idea.log` |
| `docs/features/maint/03-deprecation-cleanup/requirements.md:64-68` (Out of Scope bullet) | replace `parked in the **roadmap backlog**, to be picked up when 2026.2 ships (see the MAINT-03-04 row and design §2.5)` with `delivered by MAINT-21 together with Gradle 9 and the 2026.2 platform (see the MAINT-03-04 row and design §2.5)` |
| `docs/features/maint/03-deprecation-cleanup/requirements.md:87` (MAINT-03-04 row) | replace the cell `**Deferred (blocked).** …` up to its end with `**Moved to MAINT-21.** Delivered there together with Gradle 9 and the 2026.2 platform.` |
| `CHANGELOG.md` | one bullet at the top of the top `## [..]` block, per the `changelog` skill: `- **GoLand 2026.2 platform** (MAINT-21): Lunar is now built and tested against GoLand 2026.2 and still installs on 2026.1, which the plugin verifier checks on every release ([<sha>](<url>)).` |

### 2.12 Local-only files (gitignored; edited in the primary checkout, not committed)

| File | Edit |
|------|------|
| `.agents/skills/generate-parser/scripts/generate.sh:65` | replace the `GOLAND_LIB_DIR=` line with §3.3. Line 36 (`JAVA_HOME` corretto-21) stays: Gradle runs on 21, and foojay provisions JDK 25 into `~/.gradle/jdks` (DR-02). `GK_JAVA` (the GoLand JBR) needs no change, because 262 bundles JBR 25 (DR-06) |
| `.agents/AGENTS.md:23` | `default test IDE is **GoLand 2026.1.1**` → `**GoLand 2026.2.3**` |
| `.agents/AGENTS.md:178` (CI table) | `buildPlugin` (compile + `verifyPlugin` + zip) → `buildPlugin` (compile + zip); and in the `build-release` description add `→ verifyPlugin (GoLand 2026.1.3 + 2026.2.3)` after `buildPlugin` |
| `.agents/AGENTS.md:235` | `build/idea-sandbox/GO-*/log/idea.log` → `.intellijPlatform/sandbox/lunar/GO-*/log/idea.log` |
| `.agents/ci-topology.md:179` | `JDK 21` → `JDK 21 (Gradle) and JDK 25 (toolchain, auto-download off)` |
| `.agents/skills/verify-in-ide/SKILL.md:143,219,225,510` | `/home/builder/lunar/build/idea-sandbox/*/log/idea.log` → `/home/builder/lunar/.intellijPlatform/sandbox/lunar/*/log/idea.log` |
| `.agents/skills/verify-in-ide/SKILL.md:244` | the key path → `/home/builder/lunar/.intellijPlatform/sandbox/lunar/GO-2026.2.3/config/goland.key` (copied there in §5.3 step 3), and add "the 2026.1.3 original stays at `build/idea-sandbox/GO-2026.1.3/config/goland.key`" |
| `.agents/skills/implement-feature/SKILL.md:97` | `build/idea-sandbox/GO-*/log/idea.log` → `.intellijPlatform/sandbox/lunar/GO-*/log/idea.log` |
| `.agents/skills/jdb-debugger/SKILL.md:36` | the same replacement |

## 3. Algorithms

### 3.1 Which bundled plugins the unit tests exclude
- **Input**: the 262 GoLand `product-info.json` `bundledPlugins` list (`logs/13-262-bundled-plugins.txt`).
- **Rule**: include by default. Exclude a bundled plugin only when one of two things is measured:
  (a) it fails tests unrelated to it, or (b) it dominates suite time, evidenced by a thread dump
  that shows it working while the test waits. Excluding a plugin also disables the plugins that
  depend on it.
- **Current list**: go-linter is excluded under (a) and JavaScript under (b) (DR-04).
- **Not an option**: `testIdeBundledPluginsClasspathEnabled=false`, which removes the 262 platform
  plugins that used to be core (DR-04).
- **Adding an exclusion**: append its ID to the property (§2.4) and to `EXCLUDED_PLUGIN_IDS` (§2.6.8).

### 3.2 JDK provisioning per environment

| Environment | Gradle JVM (`JAVA_HOME`) | JDK 25 toolchain from | Auto-download | Proof |
|-------------|--------------------------|-----------------------|---------------|-------|
| `lunar-ci` (`build`, `corpus`) | 21 (image ENV) | `openjdk-25-jdk-headless` in `/usr/lib/jvm` | off (`$GRADLE_USER_HOME/gradle.properties`, written by `Verify toolchain`) | DR-19; TC-08, TC-10 |
| `act-ci` `build-release`, GitHub `build` | 25 (`setup-java`) | the Gradle JVM itself | not needed | DR-13 |
| builder (libvirt and GCE) | 21 (`/opt/jdk`) | `/usr/lib/jvm/corretto-25` | off (`/opt/cache/gradle/gradle.properties`) | TC-09 |
| developer machine | whatever `JAVA_HOME` is | auto-detected, or downloaded by foojay into `~/.gradle/jdks` | on | DR-02 |

An auto-provisioned JDK in `$GRADLE_USER_HOME/jdks` stays detectable even with auto-download off,
and Gradle could pick it over the environment's own. On the libvirt builder, foojay's Temurin 25 was
deleted with the user's approval on 2026-10-25, leaving only `CACHEDIR.TAG`, so TC-09 can only see
Corretto. Gate runs use fresh volumes for the same reason (§5.2).

### 3.3 `generate.sh` GoLand lib lookup (§2.12)
1. `ver=$(grep -E '^platformVersion' gradle.properties | sed -E 's/.*= *//')`, which gives `2026.2.3`.
2. `GOLAND_DIR=$(find "$HOME/.gradle/caches" -maxdepth 5 -type d -path "*/transforms/*/transformed/goland-$ver" | head -n 1)`.
3. If `GOLAND_DIR` is empty, print `Error: no transformed goland-$ver under ~/.gradle/caches — run ./gradlew compileKotlin first` and `exit 1`.
4. `GOLAND_LIB_DIR="$GOLAND_DIR/lib"`.

The builder's path shape is `caches/9.7.1/transforms/<hash>/transformed/goland-2026.2.3` (DR-05).
Matching on the version means a stale `goland-2026.1.3` extraction is never picked.

## 4. External Data & Parsing
The plugin consumes no new external input at runtime. The build consumes these, by exit status or
a fixed line:
- **`verifyPlugin`**: IJPGP 2.19 fails the task from the verifier's report files. TC-04 also greps
  the per-IDE verdict line `Plugin net.internetisalie.lunar:<version> against GO-<build>: Compatible.`
- **JUnit XML** (verification only): `build/test-results/test/TEST-*.xml`, root
  `<testsuite tests= failures= errors= skipped=>`, summed over files, with mtimes checked against
  the run. `--rerun` does not clear that directory.
- **Gradle `--info`** (TC-08, TC-09): the line `Compiling with toolchain '<path>'.` from `JavaCompile` (DR-19).

## 5. Data Flow and Procedures

### 5.1 TC-02 class-version reader (run on the builder)
```bash
cd ~/lunar/build/distributions && python3 - <<'EOF'
import collections, glob, io, zipfile
z = zipfile.ZipFile(sorted(glob.glob("lunar-*.zip"))[-1]); hist = collections.Counter()
for jn in (n for n in z.namelist() if n.startswith("lunar/lib/") and n.endswith(".jar")):
    inner = zipfile.ZipFile(io.BytesIO(z.read(jn)))
    for cn in inner.namelist():
        if cn.endswith(".class") and cn.startswith("net/internetisalie/"):
            b = inner.read(cn); hist[(jn, b[6] * 256 + b[7])] += 1   # u2 big-endian major_version
for (jn, major), n in sorted(hist.items()): print(f"{jn}: major={major} classes={n}")
EOF
```

### 5.2 The local gate: TC-13 and TC-08, against the tier2 image (before the deploy)
Run both cases against the image **as pushed to tier2**, so that the gate tests the exact digest the
deploy copies:
```bash
docker pull registry.internetisalie.net/lunar/lunar-ci:<semver>
crane digest registry.internetisalie.net/lunar/lunar-ci:<semver>   # record it in the PR
```

- **TC-13** (`main` in the image).
  - **Source**: `main`'s tree from `git archive <main-sha>`, extracted to `/workspace` inside the
    container.
  - **Environment**: `GRADLE_USER_HOME=/cache/lunar/gradle` on a **new** named volume, and the
    image's own `JAVA_HOME`.
  - **Command**: `/bin/sh` running PR 1's `Verify toolchain` lines (§2.8.1), then
    `./gradlew buildPlugin --no-daemon --stacktrace`, `./gradlew ktlintCheck --no-daemon --stacktrace`
    and `./gradlew test --rerun --no-daemon --stacktrace -PexcludeExternalFixtureTests`.
  - **Template**: DR-19's `b2/job3.sh` and its `docker run`, with the image name replaced by the
    tier2 reference.
  - **Pass**: all three print `BUILD SUCCESSFUL`, and the XML tally shows `failures=0`.
  - **Window between deploy and PR 1**: `main` runs on the new image *without* PR 1's lines. That
    configuration is covered by DR-19 job1 (3200 tests, 0 failures).
- **TC-08** (the probe tree in the image).
  - **Source**: `main` plus `maint-21-probe-r2.diff`, on another **new** named volume. A reused
    volume can hold a downloaded JDK, which would satisfy the toolchain without testing the image.
  - **Command**: after the Verify lines, `./gradlew javaToolchains` and
    `./gradlew compileJava --rerun --info`.
  - **Pass**: `Auto-download: Disabled`, only `/usr/lib/jvm` JDKs listed,
    `Compiling with toolchain '/usr/lib/jvm/java-25-openjdk-amd64'.`, and no
    `$GRADLE_USER_HOME/jdks` directory.
  - **Template**: DR-19's `b2/job4.sh`, steps D0–D2, run on a fresh volume.

### 5.3 Phase 3 live check (TC-15, TC-16) on the libvirt builder
Use the `verify-in-ide` skill for the Xvfb, `scrot`, `xdotool` and teardown mechanics. This
section gives only what is specific to MAINT-21.
1. **Fixture.** Create `/home/builder/maint21-live/spec/arith_spec.lua`:
   ```lua
   describe("arith", function()
     it("adds", function() assert.are.equal(2, 1 + 1) end)
     it("subtracts", function() assert.are.equal(1, 3 - 1) end)
   end)
   ```
   busted 2.3.0 is at `/usr/local/bin/busted` on the builder: a LuaRocks wrapper over
   `/usr/bin/lua5.1` (DR-22).
2. **Register busted** in each sandbox config, the same way the license is injected. Lunar resolves
   busted only from its toolchain registry (`LuaToolResolver.resolveDetailed`), and the 262
   sandboxes start with a fresh config. The registry is the application component
   `LuaToolchainRegistry`, stored in `options/lunar.xml`. Write this file as
   `/home/builder/lunar/.intellijPlatform/sandbox/lunar/GO-2026.2.3/config/options/lunar.xml` and
   as `…/GO-2026.2.3/config_runIde261/options/lunar.xml`, before launching:
   ```xml
   <application>
     <component name="LuaToolchainRegistry">
       <option name="tools">
         <list>
           <RegisteredToolState>
             <option name="executable" value="true" />
             <option name="fileExists" value="true" />
             <option name="id" value="maint21-busted" />
             <option name="kindId" value="busted" />
             <option name="origin" value="MANUAL" />
             <option name="path" value="/usr/local/bin/busted" />
           </RegisteredToolState>
         </list>
       </option>
     </component>
   </application>
   ```
   This exact XML, loaded into the registry, resolves busted to `/usr/local/bin/busted` through
   `INVENTORY_FALLBACK` (DR-23). The control, an empty registry, throws "Busted is not
   configured" (`LuaTestRunnerTest.testBustedUnresolvedThrows`). If the IDE still reports "Busted
   is not configured", register it from *Settings ▸ Lua ▸ Toolchain* (`plugin.xml:591-595`) by auto-discovery, and record
   that in the PR.
3. **License.** Copy the activated key
   `/home/builder/lunar/build/idea-sandbox/GO-2026.1.3/config/goland.key` into
   `/home/builder/lunar/.intellijPlatform/sandbox/lunar/GO-2026.2.3/config/` and into
   `…/GO-2026.2.3/config_runIde261/`. That source sits under `build/`, so any builder `clean`
   deletes it. If it is gone, or GoLand still shows the license dialog, follow `verify-in-ide`
   §3c Route 1 (offline activation code).
4. **Launch 2026.2.3 (TC-15).** Check for orphans first. Then:
   `cd /home/builder/lunar && export DISPLAY=:99 JAVA_HOME=/opt/jdk GRADLE_USER_HOME=/opt/cache/gradle && nohup ./gradlew runIde --args=/home/builder/maint21-live > /tmp/$USER-runIde.log 2>&1 &`.
   If the project does not open, open `/home/builder/maint21-live` with *File ▸ Open*. The log is
   `/home/builder/lunar/.intellijPlatform/sandbox/lunar/GO-2026.2.3/log/idea.log`; truncate it
   before launching.
5. **Run.** In the Project view, right-click `spec/arith_spec.lua` and choose
   *Run 'Lua Tests: arith_spec.lua'*. This is the producer's file-level name
   (`LuaTestRunConfigurationProducer.kt:97`).
6. **Assert.**
   - The Run tool window's tree shows `adds` passed and `subtracts` failed. Take a screenshot.
   - `grep -c 'Loaded custom plugins.*lunar' <log>` is ≥ 1.
   - `grep -E 'NoClassDefFoundError|ClassNotFoundException' <log> | grep -c 'execution/testframework'` is 0.
7. **2026.1.3 (TC-16).** Tear down, then repeat steps 4–6 with `./gradlew runIde261`. The log is
   `…/GO-2026.2.3/log_runIde261/idea.log`. Also assert that `grep -m1 'IDE: ' <log>` (the startup
   banner) names build `261.25134.147`.
8. **If that banner names `262.*`**, `runIde261` launched the wrong IDE and TC-16 has not been run.
   Do not treat that as a test-runner result. Launch 2026.1.3 directly instead:
   1. `G=$(find /opt/cache/gradle/caches -maxdepth 5 -type d -path '*/transformed/goland-2026.1.3' | head -n 1)`.
      The `verifyPlugin` runs extracted it there; its `product-info.json` reads `261.25134.147`
      (DR-26). If `G` is empty, run `builder-run libvirt verifyPlugin` once to extract it.
   2. Create `/home/builder/maint21-261/{config/options,plugins,system,log}`. Unzip PR 2's
      `lunar-<version>.zip` into `plugins/`. Copy step 2's `lunar.xml` into `config/options/` and
      step 3's `goland.key` into `config/`.
   3. Write `/home/builder/maint21-261/idea.properties` with four lines:
      `idea.config.path=/home/builder/maint21-261/config`,
      `idea.system.path=/home/builder/maint21-261/system`,
      `idea.plugins.path=/home/builder/maint21-261/plugins` and
      `idea.log.path=/home/builder/maint21-261/log`.
   4. `GOLAND_PROPERTIES=/home/builder/maint21-261/idea.properties DISPLAY=:99 nohup "$G/bin/goland.sh" /home/builder/maint21-live > /tmp/$USER-goland261.log 2>&1 &`.
      `goland.sh` passes `$GOLAND_PROPERTIES` as `-Didea.properties.file` (DR-26).
   5. Repeat steps 5–6 with the log `/home/builder/maint21-261/log/idea.log`, and the banner check.
9. Tear down everything you started (`verify-in-ide` §2a).

### 5.4 Routine push and tag
- **Routine push** (Gitea `build`, `lunar-ci`):
  1. The image provides JDK 21 and 25 with auto-download off, and `Verify toolchain` asserts 25.
  2. `buildPlugin` runs on the JDK 21 daemon and compiles with JDK 25 to Java 21 bytecode.
  3. `ktlintCheck`.
  4. `test --rerun`, on the platform's bundled JBR 25. The worker's command line is
     `…/goland-2026.2.3/jbr/bin/java` (DR-04).
  5. Kover and the verdict step. No verifier.
- **Tag**: `setup-java` 25, `buildPlugin`, `verifyPlugin` against 2026.1.3 and 2026.2.3, then
  test, lint and publish. A compatibility problem stops the job before publish.

### 5.5 GitHub lane before and after merge (TC-18)
- **Triggers.** GitHub runs Actions for this repository, on both `main` and `v*` tag pushes (DR-24).
  The workflow triggers on `push` to `main`, on `v*` tags, and on `pull_request`
  (`.github/workflows/build-plugin.yml:35-47`). A plain branch push does **not** trigger it, so
  the pre-merge run comes from a pull request.
- **Before merge**:
  1. `git push github <pr2-branch>`;
  2. `gh pr create -R internetisalie/lunar --draft --base main --head <pr2-branch> --title "MAINT-21 CI check (do not merge)" --body "Exercises the GitHub build lane before the Gitea merge."`;
  3. `gh run list -R internetisalie/lunar --branch <pr2-branch> --limit 1`, then
     `gh run view <id> --log | grep -E 'Set up JDK 25|BUILD SUCCESSFUL'`.
  
  The `build` job runs `Set up JDK 25`, `buildPlugin`, `test --rerun` and `ktlintCheck`. The
  verify step is tag-only and is skipped; the `release` job is tag-only and does not run, so
  nothing is published. Close the draft PR after the run, because the change merges on Gitea.
- **After merge** (the first `v*` tag, pushed to both remotes): check both lanes.
  - Gitea `build-release` and GitHub `build`: each shows `Set up JDK 25`, `Verify plugin
    compatibility` and two `Compatible.` verdicts.
  - GitHub `release` runs only after its `build` succeeds.
  - **If either lane fails before publishing**, fix forward on `main` and tag the next patch. Do not
    re-tag.
  - **If one lane published and the other failed**, the release exists on one forge only. Fix forward
    the same way. The next patch tag publishes on both.

## 6. Edge Cases
- **An image without JDK 25.** `Verify toolchain` fails by name. With auto-download off, Gradle
  would also fail rather than download (DR-19).
- **`main` on the new image.** Run against the tier2 image before the GAR deploy (TC-13, DR-19).
- **A stale sandbox from another platform version.** It is never read: `sandbox.home` names one
  directory (§2.6.6).
- **A developer machine with no JDK 25 and no network.** Configuration fails with a Gradle
  toolchain resolution error. Accepted; the README states the requirement (§2.11).
- **The test runner fails live.** §7 specifies the response per IDE.

## 7. Integration Points
**`plugin.xml`: no change.** The existing `<depends>com.intellij.modules.platform</depends>` (line
22) is the single declaration for both lines (DR-05):
- **261**: `intellij.platform.smRunner` is a core `productModuleV2`, and the testRunner classes are
  in the core.
- **262**: both are content modules of `intellij.testRunner.plugin`. smRunner is in the core's
  `externalNonBundledPluginCompatibilityDependencies` and depends on testRunner, and
  `MultiParentClassLoaderHelper.getAllParents` walks parents transitively.

The transitive step is read, not run. The verifier reported no unresolved test-runner classes on
either IDE (DR-09). TC-15 and TC-16 settle it live.

**If TC-15 or TC-16 fails** with a `NoClassDefFoundError` or `ClassNotFoundException` in
`com/intellij/execution/testframework`:

| TC-15 (2026.2.3) | TC-16 (2026.1.3) | Response |
|------------------|------------------|----------|
| fails | passes | **Fix forward on PR 2.** Add `<depends optional="true" config-file="lunar-testrunner.xml">intellij.testRunner.plugin</depends>` after line 22, with a new `src/main/resources/META-INF/lunar-testrunner.xml` containing `<idea-plugin/>`. On 262 the dependency resolves and adds that plugin's class loader as a parent. On 261 the plugin does not exist, and an optional dependency is skipped. Re-run TC-04, TC-15 and TC-16. **If TC-15 still fails**, this is a 2026.2 defect that dropping 2026.1 cannot fix. File a BUG with the IDE log and the attempted fallback, and **hold PR 2's merge** until it is fixed. Lunar on 2026.2 without working test runs is not shippable, and `main` stays on 261 meanwhile |
| passes | fails | Gap 2.1 (the since-build decision) |
| fails | fails | Not a 261/262 split. File a BUG with both logs and hold PR 2's merge until it is fixed |

The optional-dependency fallback is read, not run. Only TC-15 can show whether it works.

No services, extensions, indexes or settings keys change.

## 8. Requirement Coverage

| Requirement | Priority | Implemented by (section) |
|-------------|----------|--------------------------|
| MAINT-21-01 | M | §2.1, §2.2, §2.3 |
| MAINT-21-02 | M | §2.4, §2.5, §2.6.4 |
| MAINT-21-03 | M | §2.6.2 |
| MAINT-21-04 | M | §2.4, §2.6.3, §3.1 |
| MAINT-21-05 | M | §2.5, §2.6.5, §2.8.3, §2.9.3, §7 |
| MAINT-21-06 | M | the whole change (DR-14) |
| MAINT-21-07 | M | no edit (DR-11, run over the new and edited test files) |
| MAINT-21-08 | M | §2.6.3 |
| MAINT-21-09 | M | §2.7, §2.8.1–2, §2.9.1, §2.10, §3.2 |
| MAINT-21-10 | M | §2.8, §2.9, §5.5 |
| MAINT-21-11 | M | §2.6.6 |
| MAINT-21-12 | M | §2.4, §2.6.8, §3.1 |
| MAINT-21-13 | M | §2.7 header and rollback, §5.2 |
| MAINT-21-14 | S | §2.6.7, §5.3, §7 |
| MAINT-21-15 | S | §2.11, §2.12, §3.3 |

## 9. Alternatives Considered
- **Gradle 9.1.0** (the spike's version). 9.7.1 is the newest that avoids IJPGP's open 9.8 defect,
  and the prototype ran on it.
- **IJPGP 2.17.0** (the spike's version). 2.18.0 added the bundled-plugin test classpath and the 262
  Starter dependencies, and 2.19.0 fixed `verifyPlugin` failing silently on some levels. Shipping
  the newest release means the gate measured in DR-09 is the gate that ships.
- **`jvmTarget` 25.** The output would be class-file 69, which a 2026.1 install on a Java 21
  runtime cannot load. The override costs three lines.
- **`sinceBuild = 262`.** Nothing measured forces it (DR-09). It is Gap 2.1's fallback.
- **`recommended()` verifier IDEs.** They include the newest EAP (DR-15), which makes the gate
  date-dependent.
- **Verifier on every push.** Two IDE downloads on a cold per-VM pool. Tag builds plus TC-04 cover
  the promise (Risk 1.4).
- **Relying on foojay in CI.** It would download about 140 MB on every cold VM. The JDK
  provenance is no better pinned either way: the image uses Debian's package and the builder uses
  Corretto's `latest` URL.
- **Disabling auto-download with an image `ENV`.** `GRADLE_OPTS=-D…` and
  `ORG_GRADLE_PROJECT_…` both leave it enabled (DR-19). `-P` works, but would have to be on every
  `gradlew` line in two workflows. The `gradle.properties` line covers them all.
- **Keeping `/usr/bin/java` → 21 in the image.** Not needed. `main` passes with `/usr/bin/java` → 25
  (DR-19).
- **Testing the new image by deploying it.** The GAR copy deploys it (§2.7), so the gate runs
  first, against the tier2 image (§5.2).
- **`.run/Run Plugin.run.xml` with a `GO-*` glob.** Not adopted: whether that log field
  accepts a glob is unverified. The path is one line to change on a platform bump (§2.11).

## 10. Open Questions
_None._
