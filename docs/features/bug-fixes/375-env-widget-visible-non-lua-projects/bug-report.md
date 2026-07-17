---
id: "BUG-375"
title: "Lua environment status-bar widget is visible in projects with no Lua content"
type: "bug"
parent_id: "BUG"
priority: "low"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-375: Lua environment status-bar widget is visible in projects with no Lua content

> **RESOLVED 2026-07-17 (this commit)**: `isAvailable()` now returns `LuaToolchainProjectSettings.getInstance(project).environments().isNotEmpty()` — an EDT-safe synchronized in-memory read. The widget is absent by default in non-Lua projects; users can still add it manually via the status-bar context menu.

## 1. Reproduction

1. Open any project containing no Lua files (e.g. a pure Go or Python project).
2. Look at the status bar.

The "No Lua env" widget is present (and clickable, offering to provision Lua environments) in a
project that has nothing to do with Lua.

## 2. Expected vs Actual Behavior

- **Expected**: language status-bar widgets follow platform convention — available only when the
  project plausibly uses the language (has Lua files / a rockspec / a configured Lua environment),
  as e.g. interpreter widgets in other language plugins do.
- **Actual**: the widget factory unconditionally reports availability, so every project in every
  IDE shows the Lua widget.

## 3. Context / Environment

- **Confidence**: high — root-caused in code.
- **Root cause**:
  `src/main/kotlin/net/internetisalie/lunar/toolchain/ui/LuaEnvStatusBarWidgetFactory.kt:15` —
  `override fun isAvailable(project: Project): Boolean = true` (and `canBeEnabledOn` is also an
  unconditional `true` at line 17). Note the hardcoded `true` lives in the **factory**, not in
  `LuaEnvStatusBarWidget.kt` itself (the widget class has no availability logic).
- **Relevant files**:
  - `src/main/kotlin/net/internetisalie/lunar/toolchain/ui/LuaEnvStatusBarWidgetFactory.kt`
  - `src/main/kotlin/net/internetisalie/lunar/toolchain/ui/LuaEnvStatusBarWidget.kt`
    (TOOLING-05-06, design §2.7)

## 4. Other Notes

- **Fix direction**: gate `isAvailable` on Lua-ness — cheapest signals: the project has any
  configured environment (`LuaToolchainProjectSettings.environments().isNotEmpty()`), a discovered
  rockspec, or Lua files (`FileTypeIndex`/`hasFilesOfType` under a read action — mind the EDT; the
  platform calls `isAvailable` on the EDT, so use a cached/async check like other language
  plugins). Users can still add the widget manually via the status-bar context menu even when a
  factory reports unavailable-by-default, so no capability is lost.
