---
id: "MAINT-44-DESIGN"
title: "Technical Design"
type: "design"
parent_id: "MAINT-44"
folders:
  - "[[features/maint/44-verifier-reported-api-usages/requirements|requirements]]"
---

# Technical Design: MAINT-44 — Verifier-reported API usages

Every API status below (internal, public, deprecated, per platform) comes from DR-01, which reads
the platform jars the build uses. Every measured behaviour comes from a DR row in
[risks-and-gaps.md](risks-and-gaps.md). This document cites them and does not repeat them.

## 1. Architecture Overview

### Current State
The usages are listed in requirements.md "What the verifier reports". Every one has a public
replacement on both platforms except the `CoverageEngine` overrides (§2.7).

### Prior Art in This Repo
| Component | `file:line` | Relation |
|-----------|-------------|----------|
| `CoverageEngine.EP_NAME.findExtension(LuaCoverageEngine::class.java)` in `LuaCoverageSuite.getCoverageEngine` | `coverage/LuaCoverageEngine.kt:31` | **Extended.** §2.1 applies the same lookup to the runner |
| `TargetPresentation.builder(...)` in `LuaCatsFieldDocumentationTarget`, `LuaCatsTypeDocumentationTarget` and `RedisCommandDocumentationTarget` | `lang/doc/LuaDocumentationTargetProvider.kt:357`, `:469`; `analysis/redis/RedisCommandDocumentationTargetProvider.kt:60` | **Extended.** §2.2 uses the same builder for the targets that still call the internal function |
| `FileChooserDescriptorFactory.singleFile()` | `redis/run/LuaRedisRunConfiguration.kt:333` | **Reused** by §2.6 |
| `LuaToolProvisioner.tryReserve` test seam | `toolchain/provision/LuaToolProvisioner.kt:67` | **Reused** by TC-03 |
| Balloon capture from `Notifications.TOPIC` | `src/test/.../redis/connection/LuaRedisFlavorWarningTest.kt:56-67` | **Copied** into TC-03's test (same notification group) |
| `LuaRocksBridgeFiles`, which extracts classpath resources | `rocks/LuaRocksBridgeFiles.kt:18` | **Not used.** See §9 |

Apart from the sites this feature edits, no component builds a `TargetPresentation` from an
arbitrary `PsiElement`, locates the plugin directory, or checks verifier reports.

### Target State
The replaceable call sites move to public API (§2.1–§2.6). The `CoverageEngine` overrides stay
(§2.7). `verifyPlugin` gains the override-only and non-extendable failure levels (§2.8) and an
allowlist check over its own reports (§2.9).

## 2. Core Components

All paths are under `src/main/kotlin/net/internetisalie/lunar/` unless they start with `src/test`,
`gradle/` or `build.gradle.kts`.

### 2.1 `net.internetisalie.lunar.coverage.LuaCoverageRunner` — runner lookup (MAINT-44-01)
- **Responsibility**: return Lunar's registered `coverageRunner` extension (`plugin.xml:645-646`).
- **Threading**: any thread; extension lookup only.
- **Key API** (add to the class, after `acceptsCoverageEngine`):
  ```kotlin
  companion object {
      fun getInstance(): LuaCoverageRunner? = CoverageRunner.EP_NAME.findExtension(LuaCoverageRunner::class.java)
  }
  ```
- **Call sites** (each replaces `CoverageRunner.getInstanceById("LuaCov")`; the `?:` tails stay):
  - `coverage/LuaCoverageEngine.kt:38` → `LuaCoverageRunner.getInstance() ?: LuaCoverageRunner(),`
  - `coverage/report/LuaCovReportNotificationProvider.kt:37` → `val runner = LuaCoverageRunner.getInstance()`.
    Remove `import com.intellij.coverage.CoverageRunner` and add
    `import net.internetisalie.lunar.coverage.LuaCoverageRunner`.
  - `coverage/LuaCovReportImportAction.kt:40` → `val runner = LuaCoverageRunner.getInstance() ?: return`.
    Remove `import com.intellij.coverage.CoverageRunner`.
- **Equivalence**: `getInstanceById` returned the first extension whose `getId()` is `"LuaCov"`.
  Only `LuaCoverageRunner` returns that id (`LuaCoverageRunner.kt:39`), and `findExtension(Class)`
  returns the registered instance of that class.

### 2.2 `net.internetisalie.lunar.lang.doc.LuaTargetPresentation` — new (MAINT-44-02)
- **Responsibility**: the public-API stand-in for `targetPresentation(PsiElement)`.
- **Threading**: the caller's. `DocumentationTarget.computePresentation` is called under a read
  action, as it was before.
- **Key API** (new file `lang/doc/LuaTargetPresentation.kt`):
  ```kotlin
  internal object LuaTargetPresentation {
      fun of(element: PsiElement): TargetPresentation {
          val presentableText =
              (element as? NavigationItem)?.presentation?.presentableText
                  ?: (element as? PsiNamedElement)?.name
                  ?: element.text
                  ?: element.toString()
          val virtualFile = element.containingFile?.virtualFile
          return TargetPresentation
              .builder(presentableText)
              .icon(element.getIcon(Iconable.ICON_FLAG_VISIBILITY or Iconable.ICON_FLAG_READ_STATUS))
              .backgroundColor(virtualFile?.let { VfsPresentationUtil.getFileBackgroundColor(element.project, it) })
              .presentation()
      }
  }
  ```
  Imports: `com.intellij.navigation.NavigationItem`, `com.intellij.openapi.util.Iconable`,
  `com.intellij.openapi.vfs.newvfs.VfsPresentationUtil`,
  `com.intellij.platform.backend.presentation.TargetPresentation`, `com.intellij.psi.PsiElement`,
  `com.intellij.psi.PsiNamedElement`. A one-line KDoc names the internal function it replaces.
- **Call sites** in `lang/doc/LuaDocumentationTargetProvider.kt`: `:314`
  `targetPresentation(element)` → `LuaTargetPresentation.of(element)`; `:420`
  `targetPresentation(anchor)` → `LuaTargetPresentation.of(anchor)`. Delete the import at `:3`.
- **Algorithm**: §3.2.

### 2.3 `net.internetisalie.lunar.rocks.init.LuaRocksInterpreterInitializer.scheduleProvision` (MAINT-44-03)
- **Change**: `rocks/init/LuaRocksInterpreterInitializer.kt:66-68` becomes the single statement
  `LuaToolProvisioner.getInstance().provision(project, request)`. Remove
  `import com.intellij.openapi.startup.StartupManager`. Update the class KDoc's last bullet: it
  "queues a TOOLING-04 request" and drop "once the project has opened, so it never races project
  initialization".
- **Why a direct call is enough**: the only caller is `LuaRocksProjectGenerator.generateProject`
  (`rocks/init/LuaRocksProjectGenerator.kt:45`). The platform reaches a `DirectoryProjectGenerator`
  through `AbstractNewProjectStep.doGenerateProject` (community
  `platform/lang-impl/src/com/intellij/ide/util/projectWizard/AbstractNewProjectStep.java:267`). The
  only other callers of `DirectoryProjectGenerator.generateProject` in the community source are `WebModuleBuilder` (`WebModuleBuilder.java:108`),
  which takes a `WebProjectTemplate` (`LuaRocksProjectGenerator` extends
  `DirectoryProjectGeneratorBase`), and `PythonModuleBuilderBase` (Python generators only). The platform calls `generateProject` after
  `ProjectManagerEx.openProject` has returned the opened project, on both platforms (DR-11).
  `runAfterOpened` on such a project either ran inline or queued to the end of post-startup; a
  direct call runs inline. `provision` only validates, reserves and queues a `Task.Backgroundable`,
  and its KDoc permits an EDT caller (`LuaToolProvisioner.kt:21`). The environment is upserted
  before the call, as now (`:63-65`).
- **Threading**: the caller's (the EDT in the wizard). Unchanged work on that thread.

### 2.4 `net.internetisalie.lunar.util.LuaFileUtil.pluginVirtualDirectory` (MAINT-44-04)
- **Change**: replace the getter body (`util/LuaFileUtil.kt:17-20`) with:
  ```kotlin
  get() {
      val libDir = PathManager.getJarForClass(LuaPlugin::class.java)?.parent ?: return null
      if (libDir.fileName?.toString() != "lib") return null
      val pluginDir = libDir.parent ?: return null
      return VirtualFileManager.getInstance().findFileByNioPath(pluginDir)
  }
  ```
  Imports: remove `com.intellij.ide.plugins.PluginManagerCore.getPlugin` and
  `com.intellij.openapi.extensions.PluginId`; add `com.intellij.openapi.application.PathManager`.
- **Algorithm**: §3.3.
- **Caller**: `run/LuaRunConfiguration.kt:322-324` (debug launch, `getPluginVirtualDirectoryChild("lua")`). Unchanged.

### 2.5 `net.internetisalie.lunar.toolchain.ui.LuaEnvStatusBarWidget.EnvPopupStep.invokeProvisionAction` (MAINT-44-05)
- **Change**: `toolchain/ui/LuaEnvStatusBarWidget.kt:105` `action.actionPerformed(actionEvent)` →
  `ActionUtil.performAction(action, actionEvent)`. Add
  `import com.intellij.openapi.actionSystem.ex.ActionUtil`. Leave the event construction
  (`AnActionEvent.createFromDataContext`, deprecated) as it is: deprecations are MAINT-03's.
- **Behaviour**: `performAction` skips a non-`DumbAware` action in dumb mode, and skips a disabled
  presentation. `LuaProvisionToolchainAction` is a `DumbAwareAction` with no `update` override
  (`toolchain/provision/LuaToolchainActions.kt:16`), and the event's fresh `Presentation()` is
  enabled, so the action runs in every state it ran in before.
- **Threading**: EDT (popup `onChosen`), as before.

### 2.6 `net.internetisalie.lunar.coverage.LuaCovReportImportAction` (MAINT-44-06)
- **Change**: add a companion function and use it at `coverage/LuaCovReportImportAction.kt:26-35`:
  ```kotlin
  companion object {
      internal fun reportFileDescriptor(): FileChooserDescriptor =
          FileChooserDescriptorFactory
              .singleFile()
              .withFileFilter { file -> file.name.endsWith(".out") }
              .withTitle("Select LuaCov Report File")
              .withDescription("Select a luacov.report.out or luacov.stats.out file to load coverage data")
  }
  ```
  In `actionPerformed`: `val descriptor = reportFileDescriptor()`. Add the import
  `com.intellij.openapi.fileChooser.FileChooserDescriptorFactory`, and remove
  `com.intellij.openapi.vfs.VirtualFile`, which is then unused.
- **Equivalence**: the old predicate was `file != null && !file.isDirectory && (name.endsWith(".out")
  || name == "luacov.report.out" || name == "luacov.stats.out")`. Both named files end in `.out`, so
  it reduces to `.endsWith(".out")` on non-directories. `singleFile()` does not choose folders and
  allows one selection (DR-10).
- **Visibility change (deliberate)**: the old override changed only which file could be confirmed.
  `withFileFilter` also feeds `isFileVisible`, which calls `matchesFilters`, which tests the filter
  (`javap` of `FileChooserDescriptor` on 261 and 262: `isFileVisible` → `matchesFilters`, which reads
  `myFileFilter`; DR-01 dumps, lines 1941/2227 and 1963/2249). So in the IDE's own chooser tree,
  files not ending in `.out` are no longer shown. In a native chooser the filter affects only the
  final eligibility check, as the override did. `withExtensionFilter("out")` was not chosen: it is
  case-insensitive and adds a native-dialog filter label, which changes more than the override did.
- **Universal chooser (BUG-540)**: GoLand 2026.2 enables `universal.file.chooser.is.enabled`, and
  `UniversalFileChooser` consults neither the filter nor `isFileSelectable`; the old override was
  ignored there too. `actionPerformed` therefore passes the chosen file to
  `importReport(project, file)`, which shows an error and returns `false` unless the file is a
  non-directory `*.out` file.

### 2.7 `net.internetisalie.lunar.coverage.LuaCoverageEngine` — kept overrides (MAINT-44-07)
- **No code change** to `getQualifiedName` (`coverage/LuaCoverageEngine.kt:72-75`) or
  `coverageProjectViewStatisticsApplicableTo` (`:77-78`).
- **Why they stay** (DR-12): deleting `getQualifiedName` would stop the editor annotator collecting
  any coverage lines for Lua files, and deleting `coverageProjectViewStatisticsApplicableTo` would
  drop Lua files from the project view's directory coverage, which `LuaCoverageAnnotator` inherits
  from `SimpleCoverageAnnotator` (`coverage/LuaCoverageAnnotator.kt:6-8`).
- Both are internal on 261 and on 262 (DR-01), so no `pluginSinceBuild` removes them.

### 2.8 `build.gradle.kts` — failure levels (MAINT-44-08)
Replace the `failureLevel` block and its comment (`build.gradle.kts:169-178`) with:
```kotlin
        // Binary compatibility, plus override-only and non-extendable usage, which Lunar has none of
        // (MAINT-44). Internal usage is checked against an allowlist by verifyPlugin's doLast below,
        // because the coverage overrides have no public replacement and failureLevel cannot exempt
        // them.
        failureLevel.set(
            listOf(
                VerifyPluginTask.FailureLevel.COMPATIBILITY_PROBLEMS,
                VerifyPluginTask.FailureLevel.MISSING_DEPENDENCIES,
                VerifyPluginTask.FailureLevel.INVALID_PLUGIN,
                VerifyPluginTask.FailureLevel.OVERRIDE_ONLY_API_USAGES,
                VerifyPluginTask.FailureLevel.NON_EXTENDABLE_API_USAGES,
            ),
        )
```
Do **not** set `ignoredProblemsFile`; the prototype set it only to measure DR-06.

### 2.9 `build.gradle.kts` + `gradle/verifier-internal-api-allowlist.txt` — allowlist check (MAINT-44-09)
- **Where**: a `verifyPlugin { … }` block inside the existing `tasks { … }` block
  (`build.gradle.kts:209`), placed before `publishPlugin {` (`:246`). `VerifyPluginTask` is an
  `@UntrackedTask` (DR-07), so the `doLast` runs on every `verifyPlugin`. It runs only when the
  verifier's own failure levels passed. CI needs no workflow edit: the tag job already runs
  `./gradlew verifyPlugin` (`.gitea/workflows/build-plugin.yml:502`,
  `.github/workflows/build-plugin.yml:106`).
- **Code** (verbatim from the prototype):
  ```kotlin
    verifyPlugin {
        val reportsDir = verificationReportsDirectory
        val allowlist = layout.projectDirectory.file("gradle/verifier-internal-api-allowlist.txt")
        val pluginDirName = "net.internetisalie.lunar/${project.version}"
        val verifiedIdes = ides
        doLast {
            val ideDirs = reportsDir.get().asFile.listFiles().orEmpty().filter { it.isDirectory }
            val reportFiles = ideDirs.map { it.resolve("plugins/$pluginDirName") }
            val expectedIdeCount = verifiedIdes.files.size
            check(ideDirs.size == expectedIdeCount && reportFiles.all { it.resolve("verification-verdict.txt").isFile }) {
                "No verifier verdict under ${reportsDir.get().asFile}/<IDE>/plugins/$pluginDirName for each of " +
                    "$expectedIdeCount verified IDEs; found ${ideDirs.map { it.name }}"
            }
            val reported =
                reportFiles
                    .map { it.resolve("internal-api-usages.txt") }
                    .filter { it.isFile }
                    .flatMap { it.readLines() }
                    .filter { it.isNotBlank() }
                    .map { it.substringBefore(". This method") }
                    .toSortedSet()
            val allowed =
                allowlist.asFile
                    .readLines()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() && !it.startsWith("#") }
                    .toSortedSet()
            val unexpected = reported - allowed
            val stale = allowed - reported
            if (unexpected.isNotEmpty() || stale.isNotEmpty()) {
                throw GradleException(
                    "Internal API usages differ from ${allowlist.asFile.name}.\n" +
                        "Not allowlisted:\n" + unexpected.joinToString("\n") +
                        "\nAllowlisted but no longer reported:\n" + stale.joinToString("\n"),
                )
            }
        }
    }
  ```
  `net.internetisalie.lunar` is the plugin id (`plugin.xml:4`, `LuaPlugin.kt:4`). `project.version`
  is `pluginVersion`, including a CI `-PpluginVersion` override (`build.gradle.kts:20`). `ides` is the
  task's own `ConfigurableFileCollection` of verified IDEs (`VerifyPluginTask.getIdes()`, see the
  IJPGP `javap` in DR-07). The values are captured at configuration time, so the action references
  no `project`.
- **`gradle/verifier-internal-api-allowlist.txt`** (new, verbatim):
  ```text
  # Internal (@ApiStatus.Internal) platform API that Lunar uses with no public replacement on 261 or 262.
  # One line per usage: the verifier's internal-api-usages.txt line up to ". This method".
  # verifyPlugin fails on any usage not listed here and on any entry the verifier no longer reports.
  Internal method com.intellij.coverage.CoverageEngine.getQualifiedName(java.io.File outputFile, com.intellij.psi.PsiFile sourceFile) : java.lang.String is overridden in class net.internetisalie.lunar.coverage.LuaCoverageEngine
  Internal method com.intellij.coverage.CoverageEngine.coverageProjectViewStatisticsApplicableTo(com.intellij.openapi.vfs.VirtualFile fileOrDir) : boolean is overridden in class net.internetisalie.lunar.coverage.LuaCoverageEngine
  ```
- **Algorithm**: §3.1. **Report format**: §4.1.

## 3. Algorithms

### 3.1 Internal-usage allowlist comparison (§2.9)
- **Input → Output**: the verifier reports directory and the allowlist file → pass, or a
  `GradleException`.
- **Steps**:
  1. `ideDirs` = the child directories of the reports directory (one per verified IDE build; the
     verifier empties the directory at the start of each run, DR-14). For each, take
     `D/plugins/net.internetisalie.lunar/<project.version>`.
  2. Fail with "No verifier verdict under … for each of N verified IDEs; found […]" unless the number
     of `ideDirs` equals the number of entries in the task's `ides` collection **and every one** of
     them contains `verification-verdict.txt`. A missing report for any one IDE, or a moved layout,
     therefore cannot pass vacuously, and a usage reported by only one IDE cannot go unseen because
     that IDE's report is absent (DR-14).
  3. `reported` = for each of them that contains `internal-api-usages.txt`, every non-blank line,
     cut at the first `". This method"`, as one set across all IDEs. An IDE with no such file adds
     nothing.
  4. `allowed` = the allowlist's trimmed lines, without blank lines and lines starting with `#`.
  5. `unexpected = reported − allowed`; `stale = allowed − reported`. If either is non-empty, fail
     with both lists, sorted. Otherwise pass.
- **Rules**: the comparison is exact string equality on the cut line. A change to the verifier's
  wording fails the build (every entry turns `stale` and every usage `unexpected`). This is
  deliberate: a gate that stops matching must not keep passing. The union over IDEs means a usage
  internal on only one IDE (like `getPlugin` on 262) is still caught.

### 3.2 Presentable text (§2.2)
The first non-null of: the element's `NavigationItem.presentation.presentableText`, its
`PsiNamedElement.name`, `element.text`, `element.toString()`. This is the platform function's
order, with its error-logging fallback replaced by `toString()` alone. The icon uses the same flags
(`ICON_FLAG_VISIBILITY or ICON_FLAG_READ_STATUS`), and the background colour comes from the same
`VfsPresentationUtil.getFileBackgroundColor`. The container text and location text are not set
(MAINT-44-11). DR-09 and DR-13 measure equality with the platform function on Lunar PSI.

### 3.3 Plugin root (§2.4)
`PathManager.getJarForClass(LuaPlugin::class.java)` is the jar that holds `LuaPlugin`. A plugin
installs as `<plugin root>/lib/<jar>`, so the root is the jar's grandparent, provided the parent is
named `lib`. Any other layout, such as classes loaded from a directory, returns `null`. That is the
same result the old code gave when the descriptor was missing, and the debug launcher already
handles it (`LuaRunConfiguration.kt:323-324` throws `ExecutionException("Failed to locate plugin
directory")`). DR-08 measures the result in the test JVM.

## 4. External Data & Parsing

### 4.1 Plugin Verifier reports (§2.9, §3.1)
- **Location**: `<verificationReportsDirectory>/<IDE build>/plugins/<plugin id>/<version>/`. The
  default directory is `build/reports/pluginVerifier`. Example:
  `build/reports/pluginVerifier/GO-261.25134.147/plugins/net.internetisalie.lunar/0.18.0/` (DR-02).
- **`verification-verdict.txt`**: one line, for example `Compatible. 1 usage of scheduled for removal
  API and 29 usages of deprecated API. 60 usages of experimental API. 8 usages of internal API. 1
  non-extendable API usage violation. 1 override-only API usage violation`. Only its presence is used.
- **`internal-api-usages.txt`**: one usage per line, `<description>. This method is marked with
  @org.jetbrains.annotations.ApiStatus.Internal annotation …`. Example:
  `Internal method com.intellij.coverage.CoverageEngine.coverageProjectViewStatisticsApplicableTo(com.intellij.openapi.vfs.VirtualFile fileOrDir) : boolean is overridden in class net.internetisalie.lunar.coverage.LuaCoverageEngine. This method is marked with …`.
- **Parse**: `line.substringBefore(". This method")`. No regex.
- **Failure handling**: a missing verdict for any verified IDE fails the build (§3.1 step 2). A
  missing usages file counts as zero usages.

## 5. Data Flow
`./gradlew verifyPlugin` → the verifier checks the zip against both IDEs and writes the reports →
IJPGP fails on any configured failure level (§2.8) → otherwise the `doLast` (§2.9) compares the
internal usages against the allowlist → pass or fail.

## 6. Edge Cases
- **Report directories left by an earlier run**: none survive. The verifier empties the reports
  directory before it writes (DR-14).
- **A replacement that is internal on a future platform**: the allowlist gate fails, as intended.
  The fix is a code change, or a new allowlist line with its reason in the commit message.
- **An allowlisted override removed**: its entry becomes stale and the build fails until the line is
  deleted, so the allowlist cannot outlive the code.

## 7. Integration Points
- No `plugin.xml` change. The extensions involved are already registered: `coverageEngine` and
  `coverageRunner` (`plugin.xml:643-646`), `editorNotificationProvider`
  `LuaCovReportNotificationProvider` (`:665-666`), action `Lunar.ImportLuaCovReport`
  (`LuaCovReportImportAction`, `:829-830`), action `Lunar.Toolchain.Provision` (`:839-842`), and
  `directoryProjectGenerator` (`:64`).
- `build.gradle.kts`: §2.8 and §2.9. New file `gradle/verifier-internal-api-allowlist.txt`.

## 8. Requirement Coverage

| Requirement | Priority | Implemented by (section) |
|-------------|----------|--------------------------|
| MAINT-44-01 | M | §2.1 |
| MAINT-44-02 | M | §2.2, §3.2 |
| MAINT-44-03 | M | §2.3 |
| MAINT-44-04 | M | §2.4, §3.3 |
| MAINT-44-05 | M | §2.5 |
| MAINT-44-06 | M | §2.6 |
| MAINT-44-07 | M | §2.7, §2.9 (allowlist content) |
| MAINT-44-08 | M | §2.8 |
| MAINT-44-09 | M | §2.9, §3.1, §4.1 |
| MAINT-44-10 | W | §2.8 (not added) |
| MAINT-44-11 | W | §3.2 (not set) |

## 9. Alternatives Considered
- **`INTERNAL_API_USAGES` plus `ignoredProblemsFile` for the kept overrides.** The verifier's ignore
  filter applies to compatibility problems only; a pattern matching `getQualifiedName` left the
  usage reported (DR-06).
- **Deleting the kept overrides.** See §2.7.
- **Raising `pluginSinceBuild` to 262.** It removes nothing: the replacements are public on 261,
  and the kept overrides are internal on 262 (DR-01).
- **`PluginAwareClassLoader.getPluginDescriptor()` for the plugin root.** It does not find the plugin
  in the test JVM (DR-08). `PluginManager.getPluginByClass` and `findEnabledPlugin` are internal on
  262 (DR-01).
- **Shipping the debugger scripts as classpath resources** (the `LuaRocksBridgeFiles` pattern). It
  removes the need for a plugin root, but it changes `prepareSandbox`, the run configuration and
  the debugger's `LUA_PATH`, which is far more than this feature needs.
- **A `ProjectActivity` hand-off for wizard provisioning.** It is unnecessary because the project is
  already open (DR-11), and it would add a race between stashing the request and the activity.
- **The experimental `fileLocation`/`fileStatusAttributes` for location and container text.** That
  swaps internal usage for experimental usage (MAINT-44-11).

## 10. Open Questions

_None._
