---
id: "MAINT-44"
title: "44: Verifier-Reported API Usages"
type: "feature"
parent_id: "MAINT"
status: "done"
priority: "low"
folders:
  - "[[features/maint/requirements|maint]]"
---

# MAINT-44: Verifier-reported API usages

## Overview

MAINT-21 gates `verifyPlugin` on binary compatibility only: `COMPATIBILITY_PROBLEMS`,
`MISSING_DEPENDENCIES` and `INVALID_PLUGIN` (MAINT-21 design §2.6.5, confirmed by the user on
2026-10-26). The verifier also reports internal, override-only and non-extendable API usages on
both verifier IDEs, GoLand 2026.1.3 (`GO-261.25134.147`, the `pluginSinceBuild` floor) and 2026.2.3
(`GO-262.10968.67`, the compile platform). This feature replaces every such usage that has a public
replacement on both platforms. It keeps the ones that have none, and gates the result so a new
usage fails `verifyPlugin`, which runs on every `v*` tag build.

## What the verifier reports

Measured on `66247ddb` (DR-02); re-measured after rebasing at implementation (risks "Pre-Implementation De-risking Tasks"). Both IDEs report the same usages, except where a row says otherwise.

| Kind | Platform API | Lunar site |
|------|--------------|------------|
| Internal (overridden) | `CoverageEngine.getQualifiedName(File, PsiFile)` | `coverage.LuaCoverageEngine` |
| Internal (overridden) | `CoverageEngine.coverageProjectViewStatisticsApplicableTo(VirtualFile)` | `coverage.LuaCoverageEngine` |
| Internal (invoked) | `codeInsight.navigation.UtilKt.targetPresentation(PsiElement)` | `lang.doc.LuaFieldDocumentationTarget.computePresentation`, `lang.doc.LuaCatsDocumentationTarget.computePresentation` |
| Internal (invoked) | `CoverageRunner.getInstanceById(String)` | `coverage.LuaCoverageEnabledConfiguration.<init>`, `coverage.report.LuaCovReportNotificationProvider.collectNotificationData`, `coverage.LuaCovReportImportAction.actionPerformed` |
| Internal (invoked) | `StartupManager.runAfterOpened(Runnable)` | `rocks.init.LuaRocksInterpreterInitializer.scheduleProvision` |
| Internal (invoked), **262 only** | `PluginManagerCore.getPlugin(PluginId)` | `util.LuaFileUtil.getPluginVirtualDirectory` |
| Override-only (invoked) | `AnAction.actionPerformed(AnActionEvent)` | `toolchain.ui.LuaEnvStatusBarWidget.EnvPopupStep.invokeProvisionAction` |
| Non-extendable (overridden) | `FileChooserDescriptor.isFileSelectable(VirtualFile)` | the anonymous descriptor in `coverage.LuaCovReportImportAction.actionPerformed` |

`TestClasspathExclusionsTest` also calls `PluginManagerCore.getPlugin`. It is test code, is not in
the verified zip, and is out of scope.

## Scope

### In Scope
- A public replacement for every usage above that has one on both 261 and 262 (design §2).
- Keeping the `CoverageEngine` overrides that have no public replacement on either platform,
  and recording why (design §2.7).
- The gate: the override-only and non-extendable `failureLevel` entries, plus an allowlist check
  for internal usages that `failureLevel` cannot express (design §2.8, §2.9).

### Out of Scope
- Deprecated, scheduled-for-removal and experimental API usages, which the verifier also lists.
  MAINT-03 owns deprecations. This feature must not add new usages of these kinds (TC-10).
- `pluginSinceBuild`. It stays 261: every replacement is public on 261, and the kept usages are
  internal on 262 as well, so raising the floor removes nothing (DR-01).
- Test code. The verifier checks only the plugin zip.

## Functional Requirements

| ID | Requirement | Priority | Status | Description |
|----|-------------|----------|--------|-------------|
| MAINT-44-01 | **Coverage runner lookup** | M | Full | Every `CoverageRunner.getInstanceById("LuaCov")` call resolves Lunar's registered runner through public API and return the same instance (design §2.1). |
| MAINT-44-02 | **Documentation target presentation** | M | Full | `LuaCatsDocumentationTarget` and `LuaFieldDocumentationTarget` build their `TargetPresentation` with public API. For Lunar PSI, the presentable text, icon and background colour equal what `targetPresentation(PsiElement)` returned (design §2.2, DR-09, DR-13). |
| MAINT-44-03 | **Wizard provisioning** | M | Full | `LuaRocksInterpreterInitializer.scheduleProvision` still requests provisioning, without `StartupManager.runAfterOpened` (design §2.3). |
| MAINT-44-04 | **Plugin directory lookup** | M | Full | `LuaFileUtil.pluginVirtualDirectory` returns the plugin's root directory (the parent of `lib/`) without `PluginManagerCore.getPlugin` (design §2.4). |
| MAINT-44-05 | **Status-bar provisioning action** | M | Full | Choosing the add-environment item in the Lua environment status-bar popup still runs the `Lunar.Toolchain.Provision` action, without invoking `AnAction.actionPerformed` (design §2.5). |
| MAINT-44-06 | **LuaCov report chooser** | M | Full | *Import LuaCov Report* still imports exactly the single non-directory files whose names end in `.out`, without overriding `FileChooserDescriptor.isFileSelectable` (design §2.6). The action checks the chosen file itself, because the universal file chooser ignores the descriptor (BUG-540). |
| MAINT-44-07 | **Kept coverage overrides** | M | Full | `LuaCoverageEngine.getQualifiedName` and `coverageProjectViewStatisticsApplicableTo` stay, and are the only entries in the internal-usage allowlist (design §2.7). |
| MAINT-44-08 | **Override-only and non-extendable gate** | M | Full | `verifyPlugin` fails when either IDE reports an override-only or a non-extendable usage (design §2.8). |
| MAINT-44-09 | **Internal-usage allowlist gate** | M | Full | `verifyPlugin` fails when either IDE reports an internal usage that is not in the allowlist, when the allowlist names a usage that neither IDE reports, or when any verified IDE has no verdict for this plugin version (design §2.9, §3.1). |
| MAINT-44-10 | **`INTERNAL_API_USAGES` failure level** | W | Future Work | Not added. The kept overrides would fail every build, and `ignoredProblemsFile` cannot exempt them (DR-06). MAINT-44-09 is the gate instead. |
| MAINT-44-11 | **Module location in documentation presentation** | W | Future Work | Not reproduced. The platform derived it from `PsiElementListCellRenderer.getModuleTextWithIcon`, which is internal on both platforms, or from `fileLocation`, which is experimental (DR-01). The loss is not visible in a light fixture (DR-09). |

## Test Cases

Every case names the mutation that turns it red. The mutation is applied to the code the case
exercises, and the fixture reaches it.

| # | Requirement | Given | When | Then | Red under |
|---|-------------|-------|------|------|-----------|
| TC-01 | MAINT-44-01 | light fixture (Lunar's `coverageRunner` EP registered from `plugin.xml`) | `LuaCoverageRunner.getInstance()` | returns the element of `CoverageRunner.EP_NAME.extensionList` that `is LuaCoverageRunner` (`assertSame`), and its `id` is `"LuaCov"` | `getInstance()` returning `LuaCoverageRunner()` (a fresh instance fails `assertSame`) or `null` |
| TC-02 | MAINT-44-02 | `probe.lua` = `---@class Animal` / `local Animal = {}` / `---Says hi` / `function Animal:greet() end` / `local function helper() end` / `Animal.name = 'x'` ; an `EditorTabColorProvider` registered first (`EditorTabColorProvider.EP_NAME.point.registerExtension(provider, LoadingOrder.FIRST, testRootDisposable)`, a `DumbAware` provider whose `getProjectViewColor` returns `Color.RED`) | for the first `LuaLocalVarDecl`, `LuaFuncDecl`, `LuaLocalFuncDecl` and `LuaNameRef` `e`: `LuaTargetPresentation.of(e)` and the platform's `targetPresentation(e)` (test code may call it) | `presentableText` is `"local Animal = {}"`, `"function Animal:greet() end"`, `"local function helper() end"`, `"Animal"`; `icon` is non-null and equal to the platform's; `backgroundColor` is `Color.RED` and equal to the platform's (DR-09, DR-13) | text chain replaced by `element.toString()`; `.icon(…)` deleted (icon becomes `null`); `.backgroundColor(…)` deleted (colour becomes `null`). Changing the icon flags is **not** reachable: Lunar's icons do not vary with them (DR-13) |
| TC-03 | MAINT-44-03 | the canonical root of `/tmp/wizard-proj/.lua` already reserved via `LuaToolProvisioner.getInstance().tryReserve(...)`; `notification.group.lunar.tools` balloons captured from `Notifications.TOPIC` | `scheduleProvision(project, "/tmp/wizard-proj", settings)` with `provisionEnvironment = true` | exactly one captured balloon whose content contains `"Provisioning already in progress"` | the `provision(...)` call deleted from `scheduleProvision`; or `settings.provisionEnvironment = false` in the fixture (control: no balloon) |
| TC-04 | MAINT-44-04 | light fixture; the plugin loads from `plugins-test/lunar/lib/lunar-<version>.jar` (DR-08) | `LuaFileUtil.pluginVirtualDirectory` | non-null, and `findChild("lib")` is non-null | returning `libDir` instead of `libDir.parent` (`lib/lib` is absent), or returning `null` |
| TC-05 | MAINT-44-05 | `Lunar.Toolchain.Provision` replaced by a recording action; `EnvPopupStep(project, emptyList(), null)` | `onChosen(ADD_ENV_TEXT, true)`, then the step's `finalRunnable` | the action ran once, after the popup's final step (`LuaEnvPopupFinalStepTest`, added by BUG-537). The API change itself is gated by TC-08, and the dialog by HV-01 | the `ActionUtil.performAction` call deleted from `invokeProvisionAction` |
| TC-06 | MAINT-44-06 | `LuaCovReportImportAction.reportFileDescriptor()` | `isFileSelectable(LightVirtualFile(name))` for `luacov.report.out`, `luacov.stats.out`, `main.lua`, `report.txt`; and `isChooseFolders`, `isChooseMultiple` | `true`, `true`, `false`, `false`; `false`, `false` (DR-10) | `.withFileFilter { … }` removed (`main.lua` becomes selectable) |
| TC-07 | MAINT-44-07 | `gradle/verifier-internal-api-allowlist.txt` as given in design §2.9 | TC-10 | the allowlist check passes with no unexpected and no stale entry | either override deleted from `LuaCoverageEngine` (its entry becomes stale: TC-09's second half) |
| TC-08 | MAINT-44-08 | the finished change, then `action.actionPerformed(actionEvent)` restored in `invokeProvisionAction` and an `isFileSelectable` override restored in the import action | `verifyPlugin` | `BUILD FAILED`, `Verification failed with [OVERRIDE_ONLY_API_USAGES, NON_EXTENDABLE_API_USAGES] problems` (DR-04) | the override-only and non-extendable `failureLevel` entries removed (the build passes) |
| TC-09 | MAINT-44-09 | the finished change, then one `CoverageRunner.getInstanceById("LuaCov")` call restored and one line that no IDE reports appended to the allowlist | `verifyPlugin` | `BUILD FAILED`, with the restored usage under `Not allowlisted:` and the appended line under `Allowlisted but no longer reported:` (DR-05) | the `doLast` check removed (the build passes) |
| TC-10 | MAINT-44-01–09 | the finished change | `verifyPlugin` | `BUILD SUCCESSFUL`; each verdict line ends in `2 usages of internal API`, with no override-only or non-extendable clause; experimental counts unchanged from DR-02 (45 on 262, 60 on 261) (DR-03) | any one replacement reverted |
| TC-11 | MAINT-44-09 | the finished change, then (a) a `doLast` registered before the check that deletes `GO-262.10968.67/plugins/net.internetisalie.lunar/<version>/verification-verdict.txt`, or (b) `pluginDirName` set to `net.internetisalie.lunar/0.0.0-mutant` | `verifyPlugin` | `BUILD FAILED`, `No verifier verdict under …/<IDE>/plugins/net.internetisalie.lunar/<version> for each of 2 verified IDEs; found [GO-262.10968.67, GO-261.25134.147]` (DR-14) | under (a), the check weakened to `reportFiles.any { … }` (the build passes, DR-14) |
| TC-12 | MAINT-44-06 | `main.lua` as a `LightVirtualFile`; dialogs captured with `TestDialogManager` | `LuaCovReportImportAction.importReport(project, file)` | returns `false` after one error dialog, `main.lua is not a LuaCov report file.` (BUG-540) | the `isReportFile` check disabled (the file reaches `addExternalCoverageSuite`) |

## Acceptance Criteria
- [x] TC-01 to TC-06 and TC-12 are unit tests and pass in `test --rerun`, with no new
      failure relative to `main` (`LuaRenameConflictTest.testCancellationIsCheckedPerFileNameRefNotPerCollisionsCall`
      is a known flake, BUG-529).
- [x] TC-10 holds. TC-08, TC-09 and TC-11 were run as mutations; their logs are in
      `~/.cache/claude-scratch/lunar/maint-44/impl/` and cited in the PR.
- [x] HV-01 to HV-05 (implementation plan) were checked in a sandbox IDE.
- [x] `ktlintCheck` passes.

## Non-Functional Requirements
- No threading change. Every replacement runs on the thread its predecessor ran on (design §2).
- No new deprecated, scheduled-for-removal or experimental usage (TC-10's experimental counts; the
  deprecated counts fall by one per IDE, DR-03).

## Dependencies
- MAINT-21 (merged): IJPGP 2.19.0, the pinned verifier IDEs, and the current `failureLevel` list.

## See Also
- Design: [design.md](design.md)
- Plan: [implementation-plan.md](implementation-plan.md)
- Risks: [risks-and-gaps.md](risks-and-gaps.md)
