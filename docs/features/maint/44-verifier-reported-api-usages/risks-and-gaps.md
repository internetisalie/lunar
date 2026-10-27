---
id: "MAINT-44-RISKS"
title: "Risks & Gaps"
type: "risk"
parent_id: "MAINT-44"
folders:
  - "[[features/maint/44-verifier-reported-api-usages/requirements|requirements]]"
---

# MAINT-44: Risks & Gaps

## Load-bearing premises

| Premise | Kind | Settled by |
|---------|------|------------|
| Each replacement is public, and not deprecated, on both 261 and 262 | read (bytecode) | DR-01 |
| The kept overrides have no public replacement on either platform | read (bytecode and source) | DR-01, DR-12 |
| The replacements remove the reported usages and add no experimental usage | runtime | DR-02, DR-03 |
| The gate fails on a reintroduced usage, and on a stale allowlist entry | runtime | DR-04, DR-05 |
| The gate fails when any verified IDE has no verdict, and stale report directories cannot exist | runtime | DR-14 |
| `ignoredProblemsFile` cannot exempt an internal usage | read (verifier bytecode), with a runtime observation that has no positive control | DR-06 |
| Nothing regresses | runtime | DR-07 |
| The new plugin-root lookup equals the old one | runtime (test JVM); production class loader not run | DR-08, Risk 1.2 |
| The new presentation equals the platform's for Lunar PSI, and TC-02's icon and colour mutations are reachable | runtime | DR-09, DR-13 |
| The new chooser accepts the same files | runtime | DR-10 |
| The wizard calls `generateProject` on an already-open project | read (bytecode) and runtime (test JVM); live wizard not run | DR-11, Risk 1.1 |

## Premises examined
- **`pluginSinceBuild = 261`**: chosen, not forced. It is not what blocks a full internal gate: the
  kept overrides are internal on 262 too (DR-01).
- **The verifier IDE pair (2026.1.3, 2026.2.3)**: fixed by MAINT-21, and taken as given here.
- **IJPGP's `failureLevel` is all-or-nothing per category**: genuinely fixed. Its verdict check is
  per category, and the verifier's ignore file does not reach API usages (DR-06). The allowlist
  check (design §2.9) exists only because of this.
- **The allowlist key is the verifier's own report line**: chosen because it is the only identifier
  the verifier emits. Its wording can change between verifier releases, and the check then fails
  rather than passing (design §3.1).
- **Deprecated and experimental usages are out of scope**: the user's scope. The design does not
  add any (DR-03).
- **The gate runs on `v*` tag builds only**: chosen by MAINT-21, not forced. `verifyPlugin` downloads
  a second full IDE, so the routine `lunar-ci` lane does not run it (`.gitea/workflows/build-plugin.yml:498-502`).
  A new internal, override-only or non-extendable usage is therefore caught when a release is tagged,
  after it has merged, and the release does not publish until it is fixed. Running `verifyPlugin` on
  PRs would catch it before merge, at the cost MAINT-21 declined. This feature keeps that choice.
- **Which IDE the human checks run on**: `runIde` is the compile platform, 2026.2.3. HV-02 and HV-03
  touch class loading and the project wizard, which can differ at the floor, so the implementation
  plan repeats them on `runIde261` (2026.1.3, `build.gradle.kts:188-193`). The other HV checks run on
  `runIde` only: their API calls are the same members on both platforms (DR-01).

## Critical Risks

### Risk 1.1: the wizard's provisioning now starts before post-startup activities finish
- **Impact**: `runAfterOpened` deferred provisioning to the end of post-startup when it was called
  before the freeze. The direct call starts the background task immediately. If a Lunar
  post-startup activity (`LuaEnvRedetectionStartup`, `LuaEnvironmentDetectionStartup`,
  `plugin.xml:570-580`) misreads a half-provisioned `.lua` directory, a spurious notification could
  appear.
- **Likelihood**: low. The environment is registered in settings before the call (design §2.3), and
  `LuaEnvRedetectionStartup` offers re-registration only for an id that is not registered.
- **Mitigation**: HV-03 (implementation plan) creates a provisioned LuaRocks project in a sandbox
  IDE and checks that provisioning completes with no re-registration offer.

### Risk 1.2: the plugin-root lookup under the production class loader
- **Impact**: debugging a Lua run configuration fails with "Failed to locate plugin directory".
- **Likelihood**: low. `PathManager.getJarForClass` resolves the class's resource URL, so it does not
  depend on the class loader type. DR-08 measured it under the test JVM's `PathClassLoader` only.
- **Mitigation**: HV-02 starts a debug session in the sandbox IDE, where the plugin loads through
  its own class loader from `plugins/lunar/lib/`.

## Design Gaps
None. Every decision is in design.md, and the premises not executed are Risk 1.1 and Risk 1.2.

## Technical Debt & Future Work
- **The kept `CoverageEngine` overrides** stay internal usages until the platform offers a public
  extension point for coverage line keys or project-view statistics. The allowlist gate turns any
  change to them into a build failure.
- **`LuaRocksInterpreterInitializerTest.testScheduleProvisionActivatesEnvironment`** already calls
  `provision` inline for `/tmp/wizard-proj/.lua` (DR-11: post-startup has passed in the light
  fixture, so `runAfterOpened` ran inline before this feature). Whether that test starts a real
  download is not established. TC-03 avoids it by reserving the root first.

## Pre-Implementation De-risking Tasks

DR-01 to DR-12 were executed 2026-10-26 on worktree `maint-44` at `66247ddb`; DR-13 and DR-14 at
`4ce2ee0e` (after rebasing onto BUG-524 and BUG-528). All ran on the libvirt builder (`builder-run
libvirt`). Logs and dumps are under `~/.cache/claude-scratch/lunar/maint-44/` (local evidence, not
committed). The prototype code was design §2's code plus throwaway probe tests and, for DR-06 only,
an `ignoredProblemsFile`; it was reverted.

**Re-measure at implementation.** `main` moved after DR-02 and DR-03 (BUG-528 added
`getActionUpdateThread` to `LuaCovReportImportAction` and other actions). The implementer
re-runs DR-02's baseline and DR-03's counts on the rebased branch, and TC-10's expected counts are
the re-measured ones if they differ.

Re-measured 2026-10-26 on `d26298d2` (after BUG-528), unchanged from DR-02 and DR-03. Baseline: 262
`… 39 usages of deprecated API. 45 usages of experimental API. 9 usages of internal API. 1
non-extendable API usage violation. 1 override-only API usage violation`; 261 `… 29 … 60 … 8 …`, the
same two violations. With Phase 1's replacements: 262 `… 38 usages of deprecated API. 45 usages of
experimental API. 2 usages of internal API`; 261 `… 28 … 60 … 2 usages of internal API`. TC-10's
expected counts therefore stand.

| ID | Action | Observed | Verdict |
|----|--------|----------|---------|
| DR-01 | `javap -v` of each API in the 2026.1.3 and 2026.2.3 jars from the build's Gradle transforms (`javap/p261`, `javap/p262`; scripts `scripts/apicheck.sh`, `scripts/annot.py`) | See the table below | Every replacement is public on both; the kept overrides are internal on both |
| DR-02 | Baseline `verifyPlugin` at `66247ddb` (`verify-baseline.log`, reports in `reports-baseline/`) | 262: `Compatible. 8 usages of scheduled for removal API and 39 usages of deprecated API. 45 usages of experimental API. 9 usages of internal API. 1 non-extendable API usage violation. 1 override-only API usage violation`. 261: `… 29 usages of deprecated API. 60 usages of experimental API. 8 usages of internal API. 1 non-extendable … 1 override-only …`. `BUILD SUCCESSFUL in 41s` | Matches requirements.md's list |
| DR-03 | `verifyPlugin` on the prototype (`verify-proto.log`; repeated with the final `LuaFileUtil` in `probe2.log`) | 262: `Compatible. 8 usages of scheduled for removal API and 38 usages of deprecated API. 45 usages of experimental API. 2 usages of internal API`. 261: `… 28 usages of deprecated API. 60 usages of experimental API. 2 usages of internal API`. Each `Internal API usages (2)` block lists exactly `getQualifiedName(File, PsiFile)` and `coverageProjectViewStatisticsApplicableTo(VirtualFile)`. `BUILD SUCCESSFUL in 54s` | Internal 9→2 / 8→2, override-only and non-extendable 1→0; deprecated −1 each (the removed `isFileSelectable` override was also deprecated); experimental unchanged |
| DR-04 | Mutation on the prototype: `action.actionPerformed(actionEvent)` restored, plus an anonymous `FileChooserDescriptor` overriding `isFileSelectable` (`verify-mutA.log`) | Both IDEs: `1 non-extendable API usage violation. 1 override-only API usage violation`; `Verification failed with [OVERRIDE_ONLY_API_USAGES, NON_EXTENDABLE_API_USAGES] problems`; `BUILD FAILED in 46s` | TC-08's gate fires |
| DR-05 | Mutation on the prototype: one `CoverageRunner.getInstanceById("LuaCov")` restored in `LuaCovReportImportAction`, and `Internal method com.example.Stale.entry() : void is invoked in net.internetisalie.lunar.Nowhere` appended to the allowlist (`verify-mutB.log`) | Both IDEs `3 usages of internal API`. `Internal API usages differ from verifier-internal-api-allowlist.txt.` / `Not allowlisted:` / `Internal method com.intellij.coverage.CoverageRunner.getInstanceById(java.lang.String id) : com.intellij.coverage.CoverageRunner is invoked in net.internetisalie.lunar.coverage.LuaCovReportImportAction.actionPerformed(AnActionEvent) : void` / `Allowlisted but no longer reported:` / `Internal method com.example.Stale.entry() …`; `BUILD FAILED in 43s` | TC-09's gate fires on both halves. The control is DR-03 (same check, exact allowlist, passes) |
| DR-06 | `ignoredProblemsFile` set to a file containing `.*getQualifiedName.*`, and the verifier 1.411 classes read with `javap` | `verify-mutA.log` (`--info`) shows the command line carries `-ignored-problems /home/builder/lunar/gradle/maint44-probe-ignored-problems.txt`, and `getQualifiedName` is still listed and counted (`2 usages of internal API`). `IgnoredProblemsFilter` implements only `ProblemsFilter.shouldReportProblem(CompatibilityProblem, VerificationContext)`; API usages go through the separate `ApiUsageFilter.shouldReport(ApiUsage, …)` | The conclusion rests on the filter signatures. No positive control showed the file suppressing anything, so the run alone proves only that this file did not suppress this usage |
| DR-07 | Full suite on the prototype: `test --rerun`, twice (`test-proto.log` with the first `LuaFileUtil` variant; `test-proto2.log` with the final design) | `BUILD SUCCESSFUL in 10m 11s`, then `BUILD SUCCESSFUL in 10m 8s`. XML of the second run: `files 507 tests 3235 failures 0 errors 0 skipped 4`, of which the `Maint44*` probe tests account for 3 (pinned to this run). `javap` of IJPGP 2.19.0's `VerifyPluginTask`: `@UntrackedTask(because="Should always run")`, and `public abstract org.gradle.api.file.ConfigurableFileCollection getIdes()` | No regression. The `doLast` cannot be skipped as up-to-date |
| DR-08 | Probe `Maint44PluginPathProbeTest` (XML in `probe2-xml/`; first variant in `test-proto.log`'s run) | `loaderClass=com.intellij.util.lang.PathClassLoader`; `PluginAwareClassLoader` variant: `awarePath=null`. `getPluginPath=…/sandbox/lunar/GO-2026.2.3/plugins-test/lunar`; final design: `newDir=…/plugins-test/lunar`, `newLib=…/plugins-test/lunar/lib`. The test sandbox has no `lua/` (`ls plugins-test/lunar` → `lib` only), so `getPluginVirtualDirectoryChild("lua")` is `null` before and after | Same root as `getPlugin` in the test JVM. TC-04 asserts `lib`, not `lua` |
| DR-09 | Probe `Maint44PresentationProbeTest.testProbePresentationEquivalence` on TC-02's fixture, comparing `targetPresentation(e)` with `LuaTargetPresentation.of(e)` | For `LuaLocalVarDeclImpl` `[local Animal = {}]`, `LuaFuncDeclImpl` `[function Animal:greet() end]`, `LuaLocalFuncDeclImpl` `[local function helper() end]`, and each `LuaNameRefImpl` (`[Animal]` first): `textEq=true iconEq=true bgEq=true`; platform `containerText` and `locationText` `null` throughout | Equal on Lunar PSI in a light fixture. The location text the platform derives from a real module is not reproduced (MAINT-44-11) |
| DR-10 | Probe `testProbeDescriptorFilter`: `singleFile().withFileFilter { it.name.endsWith(".out") }` on `LightVirtualFile`s | `luacov.report.out selectable=true`, `luacov.stats.out selectable=true`, `main.lua selectable=false`, `report.txt selectable=false`; `chooseFolders=false chooseFiles=true multiple=false` | TC-06's expectations |
| DR-11 | `javap -c` of `AbstractNewProjectStep.doGenerateProject` in both platforms' `intellij.platform.lang.impl.jar`; probe line in `Maint44PluginPathProbeTest` | Both: offset 305 `ProjectManagerEx.openProject`, then offset 342 `DirectoryProjectGenerator.generateProject`. Light fixture: `postStartupPassed=true` | `generateProject` receives an opened project. In tests `runAfterOpened` already ran inline. Live wizard: Risk 1.1 |
| DR-12 | Why the kept overrides stay: `javap -c` of 262's `CoverageEngine` and `CoverageEditorAnnotatorImpl`; community source `plugins/coverage-common/src/com/intellij/coverage/CoverageEditorAnnotatorImpl.java:303-310` and `SimpleCoverageAnnotator.java:222` | 262 `getQualifiedName(Path, PsiFile)` calls `Path.toFile()` then `getQualifiedName(File, PsiFile)`; the annotator calls `getCorrespondingOutputPaths` then `getQualifiedName(Path, PsiFile)`. Base `getQualifiedName` returns `null`; base `coverageProjectViewStatisticsApplicableTo` returns `false` | Read, not run. If it is wrong, the cost is an unneeded allowlist line, not a defect |
| DR-13 | Probe `Maint44IconProbeTest` (`r2-icon.log`; values in `TEST-net.internetisalie.lunar.lang.doc.Maint44IconProbeTest.xml`, copied to `~/.cache/claude-scratch/lunar/maint-44/`) on TC-02's fixture, with a `DumbAware` `EditorTabColorProvider` registered `LoadingOrder.FIRST` whose `getProjectViewColor` returns `Color.RED` | For each of `LuaLocalVarDeclImpl`, `LuaFuncDeclImpl`, `LuaLocalFuncDeclImpl`, `LuaNameRefImpl`: platform icon `RowIcon(icons=[…path=/icons/rocket_16.png), null])`, `iconEq=true`, and `flag0Eq=true` (`getIcon(0)` gives the same icon); `oldBg=java.awt.Color[r=255,g=0,b=0] newBg=java.awt.Color[r=255,g=0,b=0] bgEq=true`; a presentation without `.backgroundColor`: `noBgMutantEq=false`; without `.icon`: `noIconMutantEq=false`. Without the provider both colours were `null` (observed once; that XML was not retained) | TC-02's icon and colour assertions hold, and the delete-icon and delete-colour mutations turn them red. An icon-flag mutation is unreachable |
| DR-14 | The verdict check (design §3.1 step 2) on the prototype gate: clean (`r2-clean.log`); a prior `doLast` deleting 262's `verification-verdict.txt` (`r2-mutC-all.log`); the same with the check weakened to `any` (`r2-mutC-any.log`); `pluginDirName` version `0.0.0-mutant` (`r2-mutC-version.log`) | Every run logs `The verification directory /home/builder/lunar/build/reports/pluginVerifier is being deleted because it is not empty.` before verifying. Clean: `BUILD SUCCESSFUL in 2m 25s` (with probe tests). Deleted verdict: `No verifier verdict under /home/builder/lunar/build/reports/pluginVerifier/<IDE>/plugins/net.internetisalie.lunar/0.18.0 for each of 2 verified IDEs; found [GO-262.10968.67, GO-261.25134.147]`, `BUILD FAILED in 44s`. Weakened to `any`: `BUILD SUCCESSFUL in 44s`. Wrong version: the same message with `…/0.0.0-mutant`, `BUILD FAILED in 43s` | TC-11's branch is reachable and fires. `all` is what catches a one-IDE gap. `ides.files.size` equals the number of report directories on a clean run |

### DR-01: API status by platform

`javap/<platform>/<class>.javap:<line>`, where the line is the member's signature line in `javap -v`
output. "—" means no `ApiStatus` or `Deprecated` annotation.

| API | 261 (`GO-261.25134.147`) | 262 (`GO-262.10968.67`) |
|-----|------|------|
| **Reported, replaced** | | |
| `CoverageRunner.getInstanceById(String)` | Internal `:747` | Internal `:914` |
| `UtilKt.targetPresentation(PsiElement)` | Internal `:620` | Internal `:620` |
| `StartupManager.runAfterOpened(Runnable)` | Internal `:165` | Internal `:165` |
| `PluginManagerCore.getPlugin(PluginId)` | — `:7158` | Internal `:8324` |
| `AnAction.actionPerformed(AnActionEvent)` | OverrideOnly `:2084` | OverrideOnly `:2088` |
| `FileChooserDescriptor.isFileSelectable(VirtualFile)` | NonExtendable, Deprecated `:1996` | NonExtendable, Deprecated `:2018` |
| **Reported, kept** | | |
| `CoverageEngine.getQualifiedName(File, PsiFile)` | Internal `:1125` | Internal, Deprecated `:1206`; the `(Path, PsiFile)` overload Internal `:1255` |
| `CoverageEngine.coverageProjectViewStatisticsApplicableTo(VirtualFile)` | Internal `:1692` | Internal `:1958` |
| `CoverageEngine.getCorrespondingOutputFiles(…)`, `createSrcFileAnnotator(…)` (the only other routes) | Internal `:999`, `:1848` | Internal `:1021`, `:2114` |
| **Replacements** | | |
| `CoverageRunner.EP_NAME` | — `:284` | — `:304` |
| `ExtensionPointName.findExtension(Class)` | — `:1130` | — `:1177` |
| `TargetPresentation.Companion.builder(String)` | — `:108` | — `:108` |
| `TargetPresentationBuilder.icon`, `.backgroundColor`, `.presentation` | — `:91`, `:70`, `:55` | — `:91`, `:70`, `:55` |
| `VfsPresentationUtil.getFileBackgroundColor(Project, VirtualFile)` | — `:386` | — `:386` |
| `NavigationItem.getPresentation()` | — `:56` | — `:56` |
| `PathManager.getJarForClass(Class)` | — `:5468` | — `:5468` |
| `ActionUtil.performAction(AnAction, AnActionEvent)` | — `:2873` | — `:2992` |
| `FileChooserDescriptorFactory.singleFile()` | — `:148` | — `:148` |
| `FileChooserDescriptor.withFileFilter`, `.withTitle`, `.withDescription` | — `:1623`, `:1215`, `:1285` | — `:1645`, `:1237`, `:1307` |
| **Rejected alternatives** | | |
| `PluginManager.getPluginByClass(Class)`, `findEnabledPlugin(PluginId)` | — `:518`, `:851` | Internal `:501`, `:782` |
| `PluginAwareClassLoader.getPluginDescriptor()` | — `:87` | — `:87` |
| `PsiElementListCellRenderer.getModuleTextWithIcon(Object)` | Internal `:1487` | Internal `:1488` |
| `UtilKt.fileLocation`, `UtilKt.fileStatusAttributes` | Experimental `:1051`, `:960` | Experimental `:1051`, `:960` |
| `StartupManager.runWhenProjectIsInitialized(Runnable)` | Deprecated `:183` | Deprecated `:183` |

Class-level annotations in the same dumps: `TargetPresentation`, `TargetPresentationBuilder`,
`PluginDescriptor` and `PluginAwareClassLoader` are `@ApiStatus.NonExtendable`. That forbids
implementing them, not calling them. `PsiElementListCellRenderer` is `@ApiStatus.Obsolete`. No
other class in the table has a class-level `ApiStatus` annotation.

## Test Case Gaps
- MAINT-44-05 has no unit test (requirements TC-05). It is covered by the TC-08 gate and HV-01.
- The live wizard (Risk 1.1) and the production class loader (Risk 1.2) are covered by HV-03 and
  HV-02 only.

## See Also
- Requirements: [requirements.md](requirements.md)
- Design: [design.md](design.md)
