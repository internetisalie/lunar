---
id: "BUG-365"
title: "UI alignment issue in the \"LuaRocks Packages\" detail panel"
type: "bug"
parent_id: "BUG"
priority: "low"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-365: UI alignment issue in the "LuaRocks Packages" detail panel

## 1. Reproduction

1. Open the **LuaRocks Packages** tool window (the package browser).
2. Select a package so the detail panel populates.

Observed (user feedback): the fields in the detail panel are misaligned — the layout does not line up
the way a standard IntelliJ settings/detail form does. The exact misalignment (labels vs. values,
row baselines, inconsistent insets) still needs to be pinned down from a screenshot.

## 2. Expected vs Actual Behavior

- **Expected**: labels and their values align on a consistent grid with standard insets, per IntelliJ
  form conventions.
- **Actual**: the panel's fields are visibly misaligned.

## 3. Context / Environment

- **Confidence**: low — user-reported UI; **not yet reproduced or characterized**. Needs a live
  screenshot via the `verify-in-ide` VNC flow to identify the exact alignment defect.
- **Relevant files**:
  - [`PackageDetailPanel`](../../../../src/main/kotlin/net/internetisalie/lunar/rocks/browser/PackageDetailPanel.kt)
    — the detail panel is assembled with nested `JPanel`/`BorderLayout` and manual borders rather than
    a `com.intellij.util.ui.FormBuilder` / `GridBag` grid, which is a likely source of inconsistent
    alignment.
- **Related**: same panel as [[bug-report|BUG-363]] (font mismatch) — fix both in one pass. Migrating
  the panel to `FormBuilder`/`panel { }` (Kotlin UI DSL) would likely resolve alignment *and* font
  together.

## 4. Other Notes

- Purely cosmetic. Best handled together with BUG-363 as a single "polish the LuaRocks Packages detail
  panel" change.
