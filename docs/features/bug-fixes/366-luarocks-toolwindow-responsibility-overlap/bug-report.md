---
id: "BUG-366"
title: "Unclear separation of responsibilities between the \"LuaRocks\" and \"LuaRocks Packages\" tool windows"
type: "bug"
parent_id: "BUG"
priority: "low"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-366: Unclear separation of responsibilities between the "LuaRocks" and "LuaRocks Packages" tool windows

## 1. Reproduction

The plugin registers two separate, similarly-named tool windows:

- **LuaRocks** (`id="LuaRocks"`, anchor right/secondary, ROCKS-03) →
  `LuaRocksToolWindowFactory` → `DependencyTreePanel`: shows the **current project's dependency tree**.
- **LuaRocks Packages** (`id="LuaRocks Packages"`, anchor bottom, ROCKS-02) →
  `LuaRocksPackageBrowserToolWindowFactory` → search/browse the **LuaRocks registry** and
  install/uninstall packages.

Observed (user feedback): the split of responsibilities between the two is unclear — the names don't
convey which one manages project dependencies vs. which one browses/installs from the registry.

## 2. Expected vs Actual Behavior

- **Expected**: a user can tell at a glance which surface does what — ideally one obvious "LuaRocks"
  entry point, or clearly differentiated names/grouping (e.g. tabs within a single tool window, or
  names like "LuaRocks: Dependencies" vs "LuaRocks: Browse & Install").
- **Actual**: two right-/bottom-anchored tool windows both named "LuaRocks…" with overlapping mental
  models; the distinction (project dependency view vs. registry browser) is not discoverable from the
  labels.

## 3. Context / Environment

- **Confidence**: n/a — this is a **UX / information-architecture** concern, not a defect with a code
  root cause. Resolving it is a design decision, not a bug fix.
- **Relevant files**:
  - `src/main/resources/META-INF/plugin.xml` — the two `<toolWindow>` registrations (ids `LuaRocks`
    and `LuaRocks Packages`).
  - `src/main/kotlin/net/internetisalie/lunar/rocks/ui/LuaRocksToolWindowFactory.kt` (dependencies).
  - `src/main/kotlin/net/internetisalie/lunar/rocks/browser/LuaRocksPackageBrowserToolWindowFactory.kt`
    (registry browser).

## 4. Other Notes

- Options for a design pass: (a) merge both into a single **LuaRocks** tool window with two tabs
  ("Dependencies", "Browse"); (b) rename for clarity and co-anchor them; (c) keep separate but add
  descriptive tool-window titles/stripe tooltips.
- Recommend a `plan-bug` (or lightweight design) pass to choose the approach before touching code;
  this touches user-facing IA and should be decided deliberately.

## Absorbed by ROCKS-16

This bug is folded into **[ROCKS-16: Package Browser Redesign](../../rocks/16-package-browser-redesign/requirements.md)** as an acceptance criterion; it will be fixed as part of that feature's detail-pane / tool-window rework rather than as a standalone bug fix.
