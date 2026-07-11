---
id: "EDITOR-04-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "EDITOR-04"
folders:
  - "[[features/editor/04-word-selection/requirements|requirements]]"
---

# EDITOR-04: Implementation Plan

All four handlers are independent, stateless, and read-only; they can be built and tested in
any order. Phases are grouped by requirement priority (M → S → C) so the ladder's Must rung
lands first and each phase leaves the build green on its own.

## Phases

### Phase 1: Construct ladder (identifier → … → function) [Must]
- **Goal**: Ctrl+W climbs `identifier → argument → call/index expr → statement → block →
  enclosing function`, with the block-body (shell-free) intermediate step added by Lunar.
  Realizes `EDITOR-04-01`.
- **Tasks**:
  - [x] Create `net.internetisalie.lunar.lang.insight.LuaBlockSelectioner`
    (`extends ExtendWordSelectionHandlerBase`) — realizes design §2.4 / §3.4 (block-body range
    from `LuaBlock.getStatementList()`).
  - [x] Register `<extendWordSelectionHandler implementation="…LuaBlockSelectioner"/>` in the
    `com.intellij` extensions block of `src/main/resources/META-INF/plugin.xml` — design §7.
  - [x] Confirm the platform default supplies the identifier/call/index-expr/statement/function
    ancestor rungs (no code — verified by TC-01 assertions).
- **Exit criteria**: TC-01 passes — caret in a function-body identifier expands through
  `identifier → arg → call → statement → body-statements → function…end`.

### Phase 2: String interior [Should]
- **Goal**: inside a string literal, one Ctrl+W selects the content without delimiters, the
  next includes them. Realizes `EDITOR-04-02`.
- **Tasks**:
  - [x] Create `net.internetisalie.lunar.lang.insight.LuaStringInteriorSelectioner` — realizes
    design §2.1 / §3.1, reusing
    `net.internetisalie.lunar.lang.syntax.LuaLiterals.getLuaStringDelimiterLength`.
  - [x] Register `<extendWordSelectionHandler implementation="…LuaStringInteriorSelectioner"/>`
    — design §7.
- **Exit criteria**: TC-02 and TC-05 (long string) pass.

### Phase 3: Argument / field lists [Should]
- **Goal**: a step selects one list item, the next the whole comma-separated list inside its
  brackets. Realizes `EDITOR-04-03`.
- **Tasks**:
  - [x] Create `net.internetisalie.lunar.lang.insight.LuaArgumentListSelectioner` — realizes
    design §2.3 / §3.3 (`LuaExprList` and `LuaFieldList` item span).
  - [x] Register `<extendWordSelectionHandler implementation="…LuaArgumentListSelectioner"/>`
    — design §7.
- **Exit criteria**: TC-03 (call args) and TC-06 (table constructor) pass.

### Phase 4: Comment interior [Could]
- **Goal**: inside a comment, a step selects the comment text without the `--` /
  long-bracket markers. Realizes `EDITOR-04-04`.
- **Tasks**:
  - [x] Create `net.internetisalie.lunar.lang.insight.LuaCommentInteriorSelectioner` — realizes
    design §2.2 / §3.2 (`SHORTCOMMENT` `--` prefix strip; `LONGCOMMENT` `--[==[ … ]==]` marker
    strip via the `LuaLongCommentAnnotator` level rule).
  - [x] Register `<extendWordSelectionHandler implementation="…LuaCommentInteriorSelectioner"/>`
    — design §7.
- **Exit criteria**: TC-04 (short comment) and TC-07 (long comment) pass.

## Requirement → Phase Coverage

| Requirement | Priority | Delivered in |
|-------------|----------|--------------|
| EDITOR-04-01 Construct ladder | M | Phase 1 |
| EDITOR-04-02 String interior | S | Phase 2 |
| EDITOR-04-03 Argument/field lists | S | Phase 3 |
| EDITOR-04-04 Comment interior | C | Phase 4 |

## Verification Tasks

Real-flow DoD gate (epic requirements §DoD): every test drives the actual Extend/Shrink
Selection action through `CodeInsightTestFixture` and asserts the selected `TextRange` after
each step. Create `src/test/kotlin/net/internetisalie/lunar/lang/insight/LuaWordSelectionTest.kt`
(`extends BasePlatformTestCase`).

Test driver pattern (per contract §5, light fixture):
```kotlin
myFixture.configureByText("a.lua", source)   // <caret> marks the start position
myFixture.performEditorAction(IdeActions.ACTION_EDITOR_SELECT_WORD_AT_CARET)   // "EditorSelectWord"
assertEquals(expectedText, myFixture.editor.selectionModel.selectedText)
// repeat performEditorAction for each ladder step; use ACTION_EDITOR_UNSELECT_WORD_AT_CARET to shrink
```
Wrap editor/PSI reads in `EdtTestUtil.runInEdtAndWait { runReadAction { … } }` where needed
(CLAUDE.md Lessons Learned — threading in tests).

- [x] TC-01 — `local function f() local a = print(x, y) end` caret in `x`: assert the
  successive selections `x` → `x, y` → `(x, y)` → `print(x, y)` → `local a = print(x, y)` →
  (body) → `function…end`. Covers `EDITOR-04-01`.
- [x] TC-02 — `local s = "hello"` caret in `hello`: assert `hello` then `"hello"`.
  Covers `EDITOR-04-02`.
- [x] TC-03 — `f(a, b, c)` caret in `b`: assert `b` → `a, b, c` → `(a, b, c)`.
  Covers `EDITOR-04-03`.
- [x] TC-04 — `-- a note` caret in `note`: assert `note` → `a note` (whole text) → `-- a note`.
  Covers `EDITOR-04-04`.
- [x] TC-05 — `local s = [[raw]]` caret in `raw`: assert `raw` then `[[raw]]`.
  Covers `EDITOR-04-02` (long string).
- [x] TC-06 — `local t = {1, 2, 3}` caret in `2`: assert `2` → `1, 2, 3` → `{1, 2, 3}`.
  Covers `EDITOR-04-03` (table constructor).
- [x] TC-07 — `--[==[ block ]==]` caret in `block`: assert content step selects ` block `
  (interior between the `[==[`/`]==]` markers) then the whole comment.
  Covers `EDITOR-04-04` (long comment).
- [x] TC-08 (shrink) — from a wide selection, `ACTION_EDITOR_UNSELECT_WORD_AT_CARET` walks the
  ladder back down. Covers Ctrl+Shift+W.
- [x] Behavior verified **headlessly** — `LuaWordSelectionTest` drives the *real* Extend/Shrink editor
  actions (`ACTION_EDITOR_SELECT_WORD_AT_CARET`/`UNSELECT`) and asserts `selectionModel.selectedText`
  after each rung, which is the actual observable behavior (not a proxy). No separate VNC checklist is
  needed for EDITOR-04 (no UI-only surface); live VNC spot-check optional.

## Task Summary

| Phase | Status | Priority |
|-------|--------|----------|
| Phase 1: Construct ladder | done | Must |
| Phase 2: String interior | done | Should |
| Phase 3: Argument/field lists | done | Should |
| Phase 4: Comment interior | done | Could |
