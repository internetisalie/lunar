---
id: "MAINT-44"
title: "44: Verifier-Reported API Usages"
type: "feature"
parent_id: "MAINT"
status: "todo"
priority: "low"
folders:
  - "[[features/maint/requirements|maint]]"
---

# MAINT-44: Verifier-reported API usages

## The gap

MAINT-21 gates `verifyPlugin` on binary compatibility only: its failure levels are
`COMPATIBILITY_PROBLEMS`, `MISSING_DEPENDENCIES` and `INVALID_PLUGIN` (confirmed by the user on
2026-10-26; MAINT-21 design §2.6.5). The verifier also reports internal, override-only and
non-extendable API usages. They exist on both GoLand 2026.1.3 (261.25134.147) and 2026.2.3
(262.10968.67), so they predate the platform bump and are not gated (MAINT-21 DR-09). Nothing
tracks them except this item.

## What the verifier reports

From `verifyPlugin` on the MAINT-21 branch, 2026-10-26. Both IDEs report the same usages, except
where a row says otherwise.

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

Counts: 8 internal usages on 261 and 9 on 262; 1 override-only and 1 non-extendable on each.
`TestClasspathExclusionsTest` also calls `PluginManagerCore.getPlugin`, but it is test code and is
not in the verified zip.

## Scope to plan

- **Per usage:** the public replacement, or the reason none exists on 261 (the declared floor).
- **The gate:** once the list is empty, whether to add `INTERNAL_API_USAGES`,
  `OVERRIDE_ONLY_API_USAGES` and `NON_EXTENDABLE_API_USAGES` to `pluginVerification.failureLevel`
  in `build.gradle.kts`, so new usages fail the tag build.
- **Out of scope:** deprecated, scheduled-for-removal and experimental API usages, which the
  verifier also lists. MAINT-03 owns deprecations.

Not planned. Plan with `plan-feature`.
