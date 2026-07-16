---
id: "BUG-363"
title: "LuaRocks browser detail panel renders text in a non-UI (monospaced) default font"
type: "bug"
parent_id: "BUG"
priority: "low"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-363: LuaRocks browser detail panel renders text in a non-UI (monospaced) default font

## 1. Reproduction

1. Open the **LuaRocks package browser** tool window.
2. Select a package so the right-hand detail panel populates.

Observed: the **Summary** and **Dependencies** text render in a different font (a monospaced /
Swing-default face) than the rest of the UI — the labels, buttons, and headers around them use the
standard IntelliJ font, so the panel looks inconsistent.

## 2. Expected vs Actual Behavior

- **Expected**: all text in the panel uses the standard IntelliJ UI font, matching the surrounding
  `JBLabel`s and the rest of the IDE.
- **Actual**: the summary/dependencies areas use the Swing/LAF default text-area font (monospaced),
  which does not match.

## 3. Context / Environment

- **Confidence**: high — root-caused.
- **Root cause**:
  [`PackageDetailPanel`](../../../../src/main/kotlin/net/internetisalie/lunar/rocks/browser/PackageDetailPanel.kt)
  builds `summaryArea` (line 27) and `depsArea` (line 30) with raw `javax.swing.JTextArea` and never
  sets a font. `JTextArea` resolves its font from the LAF's `TextArea.font` UIDefault, which under the
  IntelliJ LAF is a monospaced face — unlike `JBLabel`, which inherits the correct UI label font used
  everywhere else in the panel.
- **Fix direction**: swap the two `JTextArea`s for `com.intellij.ui.components.JBTextArea` (applies the
  IDE UI font), or explicitly set `font = com.intellij.util.ui.JBFont.label()` /
  `com.intellij.util.ui.UIUtil.getLabelFont()`. Aligns with the engineering-contract preference for
  `JB*` Swing components over raw `javax.swing.*`.
- **Relevant files**:
  - `src/main/kotlin/net/internetisalie/lunar/rocks/browser/PackageDetailPanel.kt` (the two
    `JTextArea` fields).

## 4. Other Notes

- Check the sibling
  [`DependencyInspectorPanel`](../../../../src/main/kotlin/net/internetisalie/lunar/rocks/ui/DependencyInspectorPanel.kt),
  which renders `JEditorPane("text/html", …)` — HTML `JEditorPane`s default to a serif font unless the
  content CSS sets the UI font, so it may exhibit the same class of mismatch and is worth fixing in the
  same pass.
- Purely cosmetic; no functional impact.

## Absorbed by ROCKS-16

This bug is folded into **[ROCKS-16: Package Browser Redesign](../../rocks/16-package-browser-redesign/requirements.md)** as an acceptance criterion; it will be fixed as part of that feature's detail-pane / tool-window rework rather than as a standalone bug fix.
