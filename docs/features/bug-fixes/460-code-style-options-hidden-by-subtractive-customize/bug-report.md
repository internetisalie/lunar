---
id: "BUG-460"
title: "Naming two indent options hides tabs-vs-spaces and tab width from the settings UI, and suppresses four `.editorconfig` properties"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-460: `showStandardOptions` is subtractive, not additive

Found 2026-08-22 by the [[FORMAT-02]] retroactive-requirements agent. One root cause, four
user-visible symptoms.

## 1. Reproduction

1. **Settings | Editor | Code Style | Lua | Tabs and Indents** — look for *Use tab character* and
   *Tab size*.
2. Open the **Blank Lines** tab.
3. Put `indent_style = tab` in an `.editorconfig` and reformat a Lua file.

## 2. Expected vs actual

- **Expected (1)**: tabs-vs-spaces and tab width are configurable. The epic table's own text for
  FORMAT-02 promises exactly this — *"configure tab size, use of tabs/spaces"*.
  **Actual**: neither control is present.
- **Expected (2)**: the blank-line options Lunar honours appear. **Actual**: the tab renders empty,
  although `BLANK_LINES_AROUND_METHOD` and `KEEP_BLANK_LINES_IN_CODE` *are* implemented and *are*
  covered by tests that set them programmatically. FORMAT-03's shipped behaviour has no UI.
- **Expected (3)**: `.editorconfig` indent properties apply. **Actual**: `indent_style`, `tab_width`,
  `smart_tabs` and `keep_indents_on_empty_lines` are all ignored.

## 3. Root cause

`IndentOptionsEditor.showStandardOptions` opens by hiding the indent field, the tab-size field and
the *Use tab character* checkbox, then re-shows **only what it is passed**. Lunar passes
`INDENT_SIZE` and `CONTINUATION_INDENT_SIZE`, so the other three stay hidden. Naming a subset is
subtractive.

The `.editorconfig` half is the same two lines. `LanguageCodeStylePropertyMapper.getSupportedIndentOptions()`
adds `TAB_SIZE` / `USE_TAB_CHARACTER` / `SMART_TABS` / `KEEP_INDENTS_ON_EMPTY_LINES` **only when the
provider's `INDENT_SETTINGS` set is empty**. By naming two options, Lunar actively removes four.

The Blank Lines tab has a different cause with the same shape: `BLANK_LINES_SETTINGS` calls
`showStandardOptions()` with **no arguments**, and the options tree skips every option not in the
allowed set.

## 4. Fix strategy

For `TABS_AND_INDENTS`, either call `showStandardOptions()` with no arguments (restoring the full
standard set plus the `.editorconfig` mapping) or name every option that should appear, including
`USE_TAB_CHARACTER` and `TAB_SIZE`. The no-argument form is preferable: it cannot drift out of sync
with the platform's option list.

For `BLANK_LINES_SETTINGS`, name the options Lunar actually honours.

## 5. Related, same file, cheap to fold in

- **No Lua right margin** — `RIGHT_MARGIN` is not named, so `getRightMargin` falls through to the
  IDE-wide default, although FORMAT-04's plan assumes a Lua-specific one.
- **The `COMMENTER_SETTINGS` block is dead as UI** — `CommenterForm` is never constructed for this
  panel, so `WRAP_LONG_COMMENTS` has no control at all.
- **Six dead `LuaBundle` keys** for a commented-out semicolon-spacing block that would not have
  worked anyway: it passes `CommonCodeStyleSettings` field names to a `showCustomOption` call typed
  for `LuaCodeStyleSettings`.
- **The opposite surprise, worth not breaking**: `.editorconfig` already works for *custom* settings,
  because the descriptor passes all fields. Nothing in `src/` or `docs/` mentions it.

## 6. Test strategy

One `getSupportedFields` assertion catches the UI and `.editorconfig` halves together, because both
derive from the same set. Note the constraint recorded in the engineering contract: the platform
serializes these fields **by field name**, so renaming one discards users' saved settings.
