---
id: "BUG-521"
title: "The Color Scheme page offers colour keys that nothing applies, and the highlighter maps node types the lexer never emits"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-521: colour options that are offered but never applied

Found in the third-pass review (`docs/review.md` §6, finding #98). Verified by grep over `src/`; **not run**.

## 1. Reproduction (read, not run)

1. Open Settings, Editor, Color Scheme, Lua. Change "Label", "Long string", "Shadowed variable",
   "Undefined reference" or "Doc value" to a conspicuous colour.
2. In a Lua file write `::here::` / `goto here`, a `[[ long string ]]`, a shadowed local, an undeclared
   reference and a doc value.
3. Predicted: none of the five constructs changes colour. The page's own demo text fakes the label colour with
   `<label>` tags.

## 2. Expected vs actual

- **Expected:** every colour option the page offers is applied to the construct it names.
- **Actual (by reading):** five offered options are inert (`LuaHighlight.LABEL`, `LONGSTRING`, `VAR_SHADOWED`,
  `REF_UNDEFINED`, `DOC_VALUE`).

## 3. Root cause (read, not executed)

- `LuaSyntaxHighlighter.kt:42,60` and `LuaSyntax.kt:79` key the map on `LuaElementTypes.ATTRIB_NAME`,
  `LABEL_REF` and `LABEL_NAME`. These are **composite parser node types** (generated `LuaElementTypes.java:13-14,43-44`),
  and `getTokenHighlights` only ever receives **lexer** tokens, so those three keys can never be returned.
  `ATTRIB_NAME` is rescued by `LuaAnnotators.kt:195`.
- `LuaHighlight.LABEL` is applied nowhere: `git grep` finds it only in the highlighter and `LuaColorSettingsPage`,
  and there is no label annotator.
- `LONGSTRING` is never applied because the merging lexer turns every long string into `STRING`, and the
  highlighter maps `STRING` to `STRING` only.
- `VAR_SHADOWED`, `REF_UNDEFINED` and `DOC_VALUE` are offered on the page and applied by nothing.
- No highlighting test references any of these keys.

This contradicts review §4.2, which lists `LuaColorSettingsPage` as re-checked clean, and MAINT-36's
out-of-scope note that repeats it.

## 4. Fix strategy

Not planned. Plan with `plan-bug`. The report's suggested direction: add label highlighting in the annotator or
map the label tokens; then either apply `LONGSTRING`, `VAR_SHADOWED`, `REF_UNDEFINED` and `DOC_VALUE` or remove them from
the page. MAINT-41's proposed "every offered colour key is applied" check guards the class.
