---
id: "BUG-367"
title: "\"LuaRocks Packages\" detail panel shows a \"(no package selected)\" label instead of a proper empty state"
type: "bug"
parent_id: "BUG"
priority: "low"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-367: "LuaRocks Packages" detail panel shows a "(no package selected)" label instead of a proper empty state

## 1. Reproduction

1. Open the **LuaRocks Packages** tool window.
2. Before selecting any package (or after a selection is cleared), look at the detail panel.

Observed: the detail panel keeps its full form skeleton visible — the name label reads
`(no package selected)` and every other field (version picker, summary, license, homepage,
dependencies) is left blank. It looks inconsistent and unattractive.

## 2. Expected vs Actual Behavior

- **Expected**: a clean empty state — e.g. an `com.intellij.ui.components.JBPanelWithEmptyText`
  showing centered secondary-color text ("No package selected") with the form fields hidden, matching
  how IntelliJ tool windows present the no-selection case.
- **Actual**: the populated-form layout stays on screen with a literal `(no package selected)` string
  in the (bold, enlarged) name-label slot and blank fields everywhere else.

## 3. Context / Environment

- **Confidence**: high — root-caused.
- **Root cause**:
  [`PackageDetailPanel.showEmpty()`](../../../../src/main/kotlin/net/internetisalie/lunar/rocks/browser/PackageDetailPanel.kt)
  (lines 152–164) just blanks each field and sets `nameLabel.text = "(no package selected)"` rather
  than swapping to a dedicated empty-state component.
- **Fix direction**: render the no-selection state with `JBPanelWithEmptyText` (or toggle a
  `CardLayout` between an empty-state card and the detail card), so the form only appears once a
  package is selected.

## 4. Other Notes

- Same panel as [[bug-report|BUG-363]] (font), [[bug-report|BUG-365]] (alignment), and
  [[bug-report|BUG-368]] (dependencies list). These four are best addressed as a single
  `PackageDetailPanel` polish/rework.
