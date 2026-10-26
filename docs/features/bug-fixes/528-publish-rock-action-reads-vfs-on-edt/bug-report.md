---
id: "BUG-528"
title: "PublishRockAction reads `VIRTUAL_FILE` on the EDT in `update()`, and the IDE reports it as an error"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "low"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-528: PublishRockAction reads `VIRTUAL_FILE` on the EDT

## 1. Reproduction

Seen in MAINT-21's Phase 3 live check, on GoLand 2026.2.3 and 2026.1.3 alike.

1. Open any Lua project in a sandbox IDE with Lunar installed.
2. Right-click a file in the Project view.

## 2. Expected

The context menu opens with no IDE error.

## 3. Actual

The IDE shows an "IDE error occurred" balloon. `idea.log` records:

```
SEVERE - #c.i.o.a.i.PreCachedDataContext - 'virtualFile' is requested on EDT by PublishRockAction#presentation@ProjectViewPopup (net.internetisalie.lunar.rocks.publish.PublishRockAction) "Lua.Rocks.Publish". See ActionUpdateThread javadoc.
	at net.internetisalie.lunar.rocks.publish.PublishRockAction.update(PublishRockAction.kt:34)
```

## 4. Root cause

`PublishRockAction` (`src/main/kotlin/net/internetisalie/lunar/rocks/publish/PublishRockAction.kt:33`)
overrides `update()` and reads `event.getData(CommonDataKeys.VIRTUAL_FILE)`, but does not override
`getActionUpdateThread()`.

The platform picks the update thread in `AnAction.getActionUpdateThread()`
(`intellij-community/platform/editor-ui-api/src/com/intellij/openapi/actionSystem/AnAction.java:207`):
an action that does not override `update` gets `BGT`, and one that does gets `EDT`. On `EDT` the
`ActionUpdateThread` javadoc says "PSI, VFS and project models must not be used", and the data
context carries only UI data. In the Project view `VIRTUAL_FILE` is not UI data: it is derived from
the selected PSI element by a data rule. `PreCachedDataContext.reportValueProvidedByRulesUsage`
(`platform-impl/.../PreCachedDataContext.kt:275`) logs the `'virtualFile' is requested on EDT by …`
error whenever a rule-provided key is read on the EDT during an action update, which is the
balloon in the reproduction.

### Other actions inspected

Every action class and anonymous action in `src/main` was checked, including all classes registered
under `<action>` in `META-INF/*.xml`.

| Action | Overrides `update` | Declares thread | What `update` reads | Verdict |
|---|---|---|---|---|
| `PublishRockAction` | yes | no (EDT) | `VIRTUAL_FILE` (rule-provided) | **defect**: declare `BGT` |
| `RunMatrixAction` | yes | no (EDT) | `event.project`; `LuaToolchainProjectSettings.environments()`, an in-memory list copied under a lock | no error today; declare `BGT` |
| `LuaCovReportImportAction` | yes | no (EDT) | `e.project` | no error today; declare `BGT` |
| `LuaConsoleAction` | yes | no (EDT) | `CommonDataKeys.PROJECT` | no error today; declare `BGT` |
| `BuildWorkspaceAction` | yes | `BGT` | n/a | fine |
| `LuaChangeToolchainVersionsAction`, `LuaRecreateToolchainAction`, `LuaRemoveToolchainAction` | yes | `BGT` | n/a | fine |
| `LuaProvisionToolchainAction`, `LuaBatchProvisionToolchainsAction`, `LuaToolchainDiagnosticsAction` | no | default `BGT` | n/a | fine |
| `LuaToolchainInventoryTable` anonymous buttons | one does, as `EDT` | `EDT` explicitly | open projects list | fine |
| `PanelAction` in `DependencyTreePanel` and `LuaRedisFunctionsPanel` | no | default `BGT` | n/a | fine |
| `LuaEnvRedetectionStartup` notification action | no | default `BGT` | n/a | fine |

`LuaHierarchyNodeDescriptor.update()` is a `NodeDescriptor` method, not an action, so it is out of
scope.

The three actions that only read `project` raise no error at runtime, because `PROJECT` is UI data.
They still use the implicit-EDT default, which the platform's own DevKit inspection
`MissingActionUpdateThread` flags ("Override 'getActionUpdateThread' and choose 'EDT' or 'BGT'").
Its description gives the cost: for an implicit-EDT action the platform "caches all the possible data
on a background thread beforehand, even if it is not needed". Their reads are thread-safe (`project`,
and a service whose state is copied under `synchronized`), so `BGT`, which the `ActionUpdateThread`
javadoc calls the preferred value, is correct for them.

## 5. Fix strategy

Override `getActionUpdateThread()` to return `ActionUpdateThread.BGT` in `PublishRockAction`,
`RunMatrixAction`, `LuaCovReportImportAction` and `LuaConsoleAction`. On `BGT` the update runs under
read access with the full data context, so `PublishRockAction` can read `VIRTUAL_FILE`.
No `update` body changes.

## 6. Test strategy

`ActionUpdateThreadDeclarationTest` (light fixture) asserts that each of the four actions returns
`ActionUpdateThread.BGT`. Before the fix all four return `EDT`, so the test fails. It also runs
`PublishRockAction.update` with a `TestActionEvent` whose context holds a `.rockspec` file and then
a `.lua` file, and checks that the action is shown for the first and hidden for the second.
