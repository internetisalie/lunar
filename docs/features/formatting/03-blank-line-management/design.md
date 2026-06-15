---
id: FORMAT-03-DESIGN
title: "Technical Design"
type: design
parent_id: FORMAT-03
status: "planned"
priority: "medium"
folders:
  - "[[features/formatting/03-blank-line-management/requirements|requirements]]"
---

# Technical Design: FORMAT-03 Blank Line Management

## 1. Architecture Overview

### Current State
`LuaSpacingBuilder` (`LuaFormatBlock.kt`) already separates function declarations with
`STANZA_SPACING = Spacing.createSpacing(1, 1, 2, true, 1)` — i.e. **hard-coded** to exactly one
blank line (minLineFeeds = 2) after `FUNC_DECL`/`LOCAL_FUNC_DECL` (`LuaFormatBlock.kt:227-230`).
The Blank Lines settings tab is exposed (`LuaLanguageCodeStyleSettingsProvider`
`BLANK_LINES_SETTINGS -> showStandardOptions()`) but the formatter does **not** read those
values, and there is no keep-max or EOF handling.

### Target State
Drive function-separation blank lines from `commonSettings.BLANK_LINES_AROUND_METHOD`, cap runs
via `KEEP_BLANK_LINES_IN_CODE`, and ensure a single trailing newline.

## 2. Core Components

### 2.1 `LuaSpacingBuilder` (modify)
- Replace the hard-coded `STANZA_SPACING` for function separation with a settings-derived
  `Spacing`:
  ```kotlin
  // left is FUNC_DECL / LOCAL_FUNC_DECL:
  val blanks = commonSettings.BLANK_LINES_AROUND_METHOD
  return Spacing.createSpacing(0, 0, blanks + 1, true, commonSettings.KEEP_BLANK_LINES_IN_CODE)
  ```
  `Spacing.createSpacing(minSpaces, maxSpaces, minLineFeeds, keepLineBreaks, keepBlankLines)` —
  `minLineFeeds = blanks + 1` yields `blanks` blank lines; `keepBlankLines =
  KEEP_BLANK_LINES_IN_CODE` caps existing runs (FORMAT-03-02).

### 2.2 `net.internetisalie.lunar.lang.format.LuaTrailingNewlinePostProcessor` (new)
- **Responsibility**: ensure one trailing `\n` (FORMAT-03-03).
- **Key API** (`com.intellij.psi.impl.source.codeStyle.PostFormatProcessor`):
  ```kotlin
  class LuaTrailingNewlinePostProcessor : PostFormatProcessor {
      override fun processElement(source: PsiElement, settings: CodeStyleSettings): PsiElement
      override fun processText(file: PsiFile, range: TextRange, settings: CodeStyleSettings): TextRange
  }
  ```
  For a `LuaFile`, trim trailing whitespace and append a single `\n`. Registered
  `<postFormatProcessor implementation=…>`.

## 3. Algorithms

### 3.1 Function-separation spacing (FORMAT-03-01/02)
- In `LuaSpacingBuilder.getSpacing`, when `left` is `FUNC_DECL`/`LOCAL_FUNC_DECL`, return the
  settings-derived `Spacing` (§2.1) instead of the constant. `keepBlankLines =
  KEEP_BLANK_LINES_IN_CODE` makes the platform collapse longer runs to that maximum.

### 3.2 Keep-max generally (FORMAT-03-02)
- The default spacing path (`spacingBuilder.getSpacing`) inherits `KEEP_BLANK_LINES_IN_CODE`
  from the common settings via the `SpacingBuilder(settings, …)`; verify and, where the code
  returns explicit `Spacing` constants inside blocks, pass `keepBlankLines =
  KEEP_BLANK_LINES_IN_CODE` rather than `0`.

### 3.3 Trailing newline (FORMAT-03-03)
- `processText`: if the file text does not end with exactly one `\n`, replace the trailing
  whitespace run with a single `\n`; return the adjusted range.

## 4. External Data & Parsing
None.

## 5. Data Flow
Reformat → `LuaFormatBlock`/`LuaSpacingBuilder` apply per-pair spacing (function separation from
settings) → `LuaTrailingNewlinePostProcessor` normalizes EOF.

## 6. Edge Cases

| Case | Handling |
| :--- | :--- |
| `BLANK_LINES_AROUND_METHOD = 0` | `minLineFeeds = 1` → functions adjacent (no blank line). |
| File already ends with one `\n` | post-processor is a no-op. |
| Empty file | post-processor leaves it empty. |

## 7. Integration Points

```xml
<!-- META-INF/plugin.xml -->
<postFormatProcessor implementation="net.internetisalie.lunar.lang.format.LuaTrailingNewlinePostProcessor"/>
```
- Reuses the existing `<lang.formatter>` (`LuaFormattingModelBuilder`) and the Blank Lines
  settings UI (already shown). No new settings field needed (uses `CommonCodeStyleSettings`).

## 8. Requirement Coverage

| Requirement | Priority | Implemented by (section) |
|-------------|----------|--------------------------|
| FORMAT-03-01 Around functions | S | §2.1, §3.1 |
| FORMAT-03-02 Keep-max | S | §3.1, §3.2 |
| FORMAT-03-03 Trailing newline | S | §2.2, §3.3 |

## 9. Alternatives Considered
- **Settings-derived `Spacing` vs hard-coded**: reading `BLANK_LINES_AROUND_METHOD` makes the
  existing function-separation behaviour configurable rather than fixed at 1.

## 10. Open Questions

_None — feature has cleared the planning bar._
