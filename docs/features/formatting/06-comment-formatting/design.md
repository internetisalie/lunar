---
id: FORMAT-06-DESIGN
title: "Technical Design"
type: design
parent_id: FORMAT-06
status: "planned"
priority: "medium"
folders:
  - "[[features/formatting/06-comment-formatting/requirements|requirements]]"
---

# Technical Design: FORMAT-06 Comment Formatting

## 1. Architecture Overview

### Current State
`LuaLanguageCodeStyleSettingsProvider` already exposes the COMMENTER options
(`LINE_COMMENT_ADD_SPACE`, `LINE_COMMENT_ADD_SPACE_ON_REFORMAT`,
`LINE_COMMENT_AT_FIRST_COLUMN`) and `LuaCommenter` handles comment toggling. Line comments are
`LuaElementTypes.SHORTCOMMENT`; LuaCATS doc comments are `LuaLazyElementTypes.LUACATS_COMMENT`.
The "add space after `--`" behaviour is the platform's when the option is set; long-comment
hard-wrap is not implemented.

### Target State
On reformat, the platform's commenter handles the leading space (FORMAT-06-01, just confirm the
option is wired). A new opt-in `PostFormatProcessor` hard-wraps long `SHORTCOMMENT` lines at the
right margin, never touching `LUACATS_COMMENT`.

## 2. Core Components

### 2.1 `LuaCodeStyleSettings` (add field)
```kotlin
@JvmField var WRAP_LONG_COMMENTS: Boolean = false
```

### 2.2 `net.internetisalie.lunar.lang.format.LuaCommentWrapPostProcessor` (new)
- **Key API** (`PostFormatProcessor`):
  ```kotlin
  class LuaCommentWrapPostProcessor : PostFormatProcessor {
      override fun processElement(source: PsiElement, settings: CodeStyleSettings): PsiElement
      override fun processText(file: PsiFile, range: TextRange, settings: CodeStyleSettings): TextRange
  }
  ```
- Iterates `SHORTCOMMENT` leaves within `range`; for any whose rendered column length exceeds
  `RIGHT_MARGIN` (and `WRAP_LONG_COMMENTS` is on), rewrap (§3.1). Skips `LUACATS_COMMENT`.

## 3. Algorithms

### 3.1 Comment hard-wrap (FORMAT-06-02)
- **Input**: a `--` comment leaf, its start column, `rightMargin`.
- **Steps**:
  1. Strip the leading `--` and one optional space → the text body; capture the indent of the
     comment's line.
  2. Greedily pack words into lines of at most `rightMargin - (indent + 3)` chars (`3` = `-- `).
  3. Re-emit each line as `<indent>-- <words>`; replace the original comment leaf's text.
- **Edge handling**: a single word longer than the budget stays on its own line (no mid-word
  break). `---@` (doc) comments are excluded by element type.

### 3.2 Space after `--` (FORMAT-06-01)
- No new code: verify `LINE_COMMENT_ADD_SPACE_ON_REFORMAT` causes the platform commenter to
  insert the space on reformat; add a test (TC-FORMAT-06-01). If the plugin's commenter
  overrides this, set `LuaCommenter` to honor the option.

## 4. External Data & Parsing
None — comment text only.

## 5. Data Flow
Reformat → platform applies the leading-space option → `LuaCommentWrapPostProcessor` rewraps
over-long `--` lines (if enabled), leaving doc comments intact.

## 6. Edge Cases

| Case | Handling |
| :--- | :--- |
| `---@param …` doc comment | excluded (element type `LUACATS_COMMENT`). |
| Trailing comment after code | wrapped only if it alone exceeds the margin; indent uses the comment's column. |
| Comment with a URL longer than budget | kept on one line (no mid-token break). |
| `WRAP_LONG_COMMENTS` off | post-processor is a no-op for wrapping. |

## 7. Integration Points

```xml
<!-- META-INF/plugin.xml -->
<postFormatProcessor implementation="net.internetisalie.lunar.lang.format.LuaCommentWrapPostProcessor"/>
```
- Reuses the existing commenter settings UI + `LuaCommenter`; adds one settings field.

## 8. Requirement Coverage

| Requirement | Priority | Implemented by (section) |
|-------------|----------|--------------------------|
| FORMAT-06-01 Space after `--` | C | §3.2 |
| FORMAT-06-02 Wrap long comments | C | §2.2, §3.1 |

## 9. Alternatives Considered
- **`PostFormatProcessor` vs a `Block`-level approach**: comment reflow is text-shaped, not
  block-structured, so a post-processor is the right tool (the same pattern as FORMAT-03's EOF
  processor).

## 10. Open Questions

_None — feature has cleared the planning bar._
