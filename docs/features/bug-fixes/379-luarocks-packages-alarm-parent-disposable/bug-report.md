---
id: "BUG-379"
title: "LuaRocks Packages tool window logs a SEVERE error (Alarm without parent Disposable) on every open"
type: "bug"
parent_id: "BUG"
priority: "high"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-379: LuaRocks Packages tool window logs a SEVERE error (Alarm without parent Disposable) on every open

## 1. Reproduction

1. Open any project in GoLand with the Lunar plugin loaded.
2. Open the **LuaRocks Packages** tool window (View ▸ Tool Windows ▸ LuaRocks Packages, or
   Find Action ▸ "Activate LuaRocks Packages window"). It also fires when the window is
   auto-restored from a saved layout on project open.

Observed (VNC-verified live in GoLand 2026.1.3 on 2026-07-16): a red **"IDE error occurred —
See details and submit report"** balloon appears, and the log records a `SEVERE` throwable
attributed to `Plugin to blame: lunar`. The panel itself still renders (the error is logged,
not fatal to content creation), but the error balloon surfaces to the user every time.

## 2. Expected vs Actual Behavior

- **Expected**: opening the LuaRocks Packages tool window completes with no error in the IDE log
  / Event Log and no error balloon.
- **Actual**: a `SEVERE` `IllegalArgumentException` is logged (and shown as a red error balloon)
  on every open.

## 3. Context / Environment

- **Confidence**: high — **root-caused and captured live** (this is the concrete panel-open
  exception the [[bug-report|BUG-364]] umbrella was filed to find).
- **Captured stack trace** (GoLand 2026.1.3, lunar 0.18.0; `Last Action:
  ActivateLuaRocksPackagesToolWindow`):
  ```
  SEVERE - #c.i.u.Alarm - You must provide parent Disposable for non-swing thread Alarm
  java.lang.IllegalArgumentException: You must provide parent Disposable for non-swing thread Alarm
      at com.intellij.util.Alarm.<init>(Alarm.kt:169)
      at com.intellij.util.Alarm.<init>(Alarm.kt:66)
      at com.intellij.util.Alarm.<init>(Alarm.kt:107)
      at net.internetisalie.lunar.rocks.browser.LuaRocksPackageBrowserToolWindowFactory$PackageBrowserPanel.<init>(LuaRocksPackageBrowserToolWindowFactory.kt:59)
      at net.internetisalie.lunar.rocks.browser.LuaRocksPackageBrowserToolWindowFactory.createToolWindowContent(LuaRocksPackageBrowserToolWindowFactory.kt:43)
      at com.intellij.openapi.wm.impl.ToolWindowImpl.createContentIfNeeded(ToolWindowImpl.kt:788)
      ...
  ```
- **Root cause**:
  [`LuaRocksPackageBrowserToolWindowFactory.kt:59`](../../../../src/main/kotlin/net/internetisalie/lunar/rocks/browser/LuaRocksPackageBrowserToolWindowFactory.kt)
  constructs the 300 ms search-debounce alarm as `Alarm(Alarm.ThreadToUse.POOLED_THREAD)` — the
  no-parent-`Disposable` constructor. The platform requires a parent `Disposable` for any
  non-`SWING_THREAD` alarm (so it can be cancelled/cleaned up), and rejects the parentless form.
  `PackageBrowserPanel` is a bare `JPanel` with no lifecycle owner, and tool-window content is
  built off the EDT, so the parentless `POOLED_THREAD` alarm trips the platform check.
- **Fix direction**: give the alarm a parent `Disposable` tied to the tool window's content
  lifecycle — e.g. make `PackageBrowserPanel` implement `Disposable`, set it as the content's
  disposer (`content.setDisposer(panel)` / `Disposer.register(...)`), and use
  `Alarm(Alarm.ThreadToUse.POOLED_THREAD, panel)`. (The `ToolWindow`'s `disposable` or the
  `Content` are both valid parents.)
- **Relevant files**:
  - `src/main/kotlin/net/internetisalie/lunar/rocks/browser/LuaRocksPackageBrowserToolWindowFactory.kt`
    (the `alarm` field at line 59; `createToolWindowContent` at line 43 adds the content without a
    disposer).

## 4. Other Notes

- **Overlaps [[../16-package-browser-redesign/requirements|ROCKS-16]]**, which rebuilds this exact
  factory/panel (`PackageBrowserPanel` → a Plugins-idiom two-tab surface). Two options: (a) fix
  this now as a small standalone change since it is a **live crash-balloon on every open** at
  0.18.0 and ROCKS-16 is not yet scheduled; (b) fold it into ROCKS-16 Phase 5 as a must-satisfy
  (the rebuilt panel must parent its debounce alarm / coroutine scope to the content disposable).
  Recommend (a) — the one-liner shouldn't wait on the larger rework, and ROCKS-16 carries the fix
  forward regardless.
- The sibling tool windows verified clean in the same session: **LuaRocks** (dependency tree,
  `LuaRocksToolWindowFactory`) and **Redis Functions** (`LuaRedisFunctionsToolWindowFactory`) both
  open with no logged error — the defect is specific to the package browser's alarm.
