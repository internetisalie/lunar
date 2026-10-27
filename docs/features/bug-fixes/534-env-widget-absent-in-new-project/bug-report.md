---
id: "BUG-534"
title: "The Lua Environment status-bar widget is missing in the session that creates a project until the project is reopened"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-534: Lua Environment widget absent in a new project

Found by MAINT-44's HV-03 live check on GoLand 2026.2.3 (`runIde`), in the ROCKS-17 flow
(*New Project → LuaRocks* with *Provision isolated environment* ticked).

## 1. Reproduction
1. *File → New → Project… → LuaRocks*, tick *Provision isolated environment*, create the project.
2. Wait for *Provisioned Lua toolchain 'lua 5.4' (2 tools)*.
3. Look at the status bar, then right-click it.

## 2. Expected vs Actual Behavior
- **Expected**: the status bar shows `lua 5.4` once the wizard registers the environment.
- **Actual**: no Lua Environment widget, although *Status Bar Widgets* lists *Lua Environment* as
  ticked. It appears only after the project is closed and reopened.

Evidence: `~/.cache/claude-scratch/lunar/maint-44/hv/r10.png` (no widget after provisioning) and
`hv03-runIde-widget-menu.png` (the widget ticked in the menu but not shown).

## 3. Context / Environment
- `toolchain/ui/LuaEnvStatusBarWidgetFactory.kt` (`isAvailable`)
- `toolchain/registry/LuaToolchainProjectSettings.kt` (publishes `LuaToolchainListener.TOPIC`)
- Platform: `AbstractNewProjectStep.doGenerateProject`, `StatusBarWidgetsManager`

## 4. Root Cause
`LuaEnvStatusBarWidgetFactory.isAvailable` returns `environments().isNotEmpty()` (BUG-375).
The platform evaluates it once, when `StatusBarWidgetsManager.init` builds the frame's status bar,
and re-evaluates it only when someone calls `StatusBarWidgetsManager.updateWidget(factory)`.
`StatusBarWidgetFactory.isAvailable`'s contract says so: *"Whenever availability is changed, you
need to call StatusBarWidgetsManager#updateWidget(StatusBarWidgetFactory) explicitly."*

In the New Project flow the environment arrives after that single evaluation.
`AbstractNewProjectStep.doGenerateProject` first calls `ProjectManagerEx.openProject(location,
options)`, which opens the frame and its status bar, and only then calls
`generator.generateProject(...)`. That is where `LuaRocksInterpreterInitializer.scheduleProvision`
upserts the environment. At `init` the project has no environments, so the widget is not created.
`upsertEnvironment` publishes `ENVIRONMENT_ADDED` on `LuaToolchainListener.TOPIC`, but the only
widget-side subscriber is the widget itself (`LuaEnvStatusBarWidget.install`), which does not exist.
Nothing calls `updateWidget`. On reopen, `init` sees the persisted environment and creates it.

The same gap affects any project that gains its first environment in-session (the provision dialog,
adoption by `LuaEnvironmentDetectionStartup`) or loses its last one (*Remove Environment* leaves a
`No Lua env` widget instead of removing it).

## 5. Fix Strategy
Ask the platform to re-check availability whenever the environment set changes, from the topic that
already publishes those changes:

- `LuaEnvWidgetAvailabilityListener`: a `LuaToolchainListener` registered declaratively under
  `<applicationListeners>` (the topic is published on the application bus). On
  `ENVIRONMENT_ADDED` / `ENVIRONMENT_REMOVED` for an open project, it calls
  `LuaEnvWidgetAvailability.getInstance(project).recheck()`. A project disposed between its
  `isDisposed` check and the service lookup throws `AlreadyDisposedException`, which it ignores.
- `LuaEnvWidgetAvailability` (project service, `serviceInterface` + `LuaEnvWidgetAvailabilityImpl`):
  `recheck()` launches on the service's injected `CoroutineScope`, which ends with the project, with
  `Dispatchers.EDT` (publishers include pooled threads such as the provision sink). On the EDT it
  returns if the project is disposed, then calls
  `project.service<StatusBarWidgetsManager>().updateWidget(LuaEnvStatusBarWidgetFactory::class.java)`.
  That overload re-runs `isAvailable` and then adds or removes the widget. `serviceAsync` is not
  used: it is `@ApiStatus.Experimental`.

**API status.** `StatusBarWidgetsManager` lives in `platform.ide.impl`, but `javap -v` of
`intellij.platform.ide.impl.jar` from GoLand 2026.1.3 (261) and 2026.2.3 (262) shows no
`ApiStatus` annotation on the class or on `updateWidget(Class)` / `updateWidget(StatusBarWidgetFactory)`,
and the package has no `package-info`. It is the API that `StatusBarWidgetFactory`'s Javadoc points to,
so this adds no internal-API usage for MAINT-44 to remove.

## 6. Test Strategy
`LuaEnvWidgetAvailabilityTest` (light fixture) swaps the project's `LuaEnvWidgetAvailability` for a
recording instance (`replaceService`) and:
- upserts the first environment → one re-check requested (red before the fix: no listener exists);
- removes the environment → a re-check requested;
- an event with no project, or an unrelated change (`KIND_OPTION_CHANGED`) → none.

A light fixture has no initialized status bar (`StatusBarWidgetsManager.updateWidget` returns early
without a frame), so the widget actually appearing is verified live: on `runIde` and `runIde261`,
New Project → LuaRocks with provisioning shows `lua 5.4` in the creating session.

## 7. Verification
- `LuaEnvWidgetAvailabilityTest`: adding and removing an environment failed before the listener
  existed (`expected:<1> but was:<0>`) and pass after it.
- Live, *New Project → LuaRocks* with provisioning: the status bar shows `lua 5.4` in the session
  that created the project, on GoLand 2026.2.3 (`runIde`) and 2026.1.3 (`runIde261`).
