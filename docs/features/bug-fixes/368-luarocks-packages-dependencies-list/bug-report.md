---
id: "BUG-368"
title: "\"LuaRocks Packages\" renders Dependencies as a text field instead of a list"
type: "bug"
parent_id: "BUG"
priority: "low"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-368: "LuaRocks Packages" renders Dependencies as a text field instead of a list

## 1. Reproduction

1. Open the **LuaRocks Packages** tool window and select a package that has dependencies.
2. Look at the **Dependencies** section of the detail panel.

Observed: dependencies are shown as newline-joined text inside a read-only multi-line text field,
rather than as a proper list of items.

## 2. Expected vs Actual Behavior

- **Expected**: dependencies render as a list (e.g. `com.intellij.ui.components.JBList`) — one row per
  dependency, selectable, with room for future affordances (navigate to a rock, per-dep status).
- **Actual**: dependencies are flattened into a single `JTextArea` as a `\n`-joined string
  (`"(none)"` when empty), which reads as a text blob and can't be interacted with per-item.

## 3. Context / Environment

- **Confidence**: high — root-caused.
- **Root cause**:
  [`PackageDetailPanel`](../../../../src/main/kotlin/net/internetisalie/lunar/rocks/browser/PackageDetailPanel.kt)
  declares `depsArea` as a `javax.swing.JTextArea` (line 30) and populates it via
  `meta.dependencies.joinToString("\n")` (line 144).
- **Fix direction**: replace `depsArea` with a `JBList` backed by a `DefaultListModel<String>` (the
  package browser's left pane already uses `JBList` for results, so the pattern is in-file), populated
  from `meta.dependencies`.

## 4. Other Notes

- Same panel/cluster as [[bug-report|BUG-363]] (font), [[bug-report|BUG-365]] (alignment), and
  [[bug-report|BUG-367]] (empty state) — best done together as one `PackageDetailPanel` rework.
- Note `summaryArea` is also a `JTextArea` (that one is genuinely free-text, so it can stay a text
  area — just fix its font per BUG-363).

## Absorbed by ROCKS-16

This bug is folded into **[ROCKS-16: Package Browser Redesign](../../rocks/16-package-browser-redesign/requirements.md)** as an acceptance criterion; it will be fixed as part of that feature's detail-pane / tool-window rework rather than as a standalone bug fix.
