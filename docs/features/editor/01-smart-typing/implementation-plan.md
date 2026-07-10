---
id: "EDITOR-01-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "EDITOR-01"
folders:
  - "[[features/editor/01-smart-typing/requirements|requirements]]"
---

# EDITOR-01: Implementation Plan

Sequenced from `design.md`. Each phase leaves the build green and is independently
verifiable via a real-flow `myFixture` test (epic DoD gate, requirements.md:62).

## Phases

### Phase 1: Quote pairing + bracket suppression [Must]
- **Goal**: `"`/`'` auto-close, skip, and backspace-unpair; brackets suppressed inside
  strings/comments. Delivers EDITOR-01-01, -01-02, -01-03, and the quote half of -01-04.
- **Tasks**:
  - [ ] Create `net.internetisalie.lunar.lang.editor.LuaQuoteHandler` extending
    `SimpleTokenSetQuoteHandler(LuaSyntax.StringLiteralTokens)` and implementing
    `MultiCharQuoteHandler` — realizes design §2.1, §3.2 (`getClosingQuote` mid-word null-return).
  - [ ] Create `net.internetisalie.lunar.lang.editor.LuaTypedHandler` (`TypedHandlerDelegate`)
    with `beforeCharTyped` implementing the §3.1 string/comment suppression algorithm.
  - [ ] Register `<lang.quoteHandler language="Lua" …/>` and `<typedHandler …/>` in
    `plugin.xml` — realizes design §7.
- **Exit criteria**: TC-3, TC-4, TC-6, TC-7 pass (§Verification); brackets still auto-close
  outside strings (TC-1/TC-2, delivered by the existing brace matcher).

### Phase 2: Keyword-block completion core + settings [Must]
- **Goal**: accepting/typing a block keyword scaffolds `end`/`until`, on by default, behind a
  Smart Keys toggle. Delivers EDITOR-01-05.
- **Tasks**:
  - [ ] Create `net.internetisalie.lunar.settings.LuaEditorOptions`
    (`@Service(APP)` + `PersistentStateComponent`, `autoCloseKeywordBlocks = true`) — design §2.5.
  - [ ] Create `net.internetisalie.lunar.lang.editor.LuaEditorOptionsConfigurable`
    (`BeanConfigurable<LuaEditorOptions>`) and register
    `<editorSmartKeysConfigurable instance="…"/>` — design §2.6, §7.
  - [ ] Create `net.internetisalie.lunar.lang.editor.LuaKeywordBlockCloser` object with
    `closeIfNeeded(editor, file, openerEndOffset): Boolean` implementing the §3.4 algorithm
    (reuses `LuaBlockPairs`).
  - [ ] Attach the block-keyword `InsertHandler` lambda in
    `LuaCompletionContributor.addKeywords` (`LuaCompletionContributor.kt:38`), gated on
    `LuaEditorOptions.autoCloseKeywordBlocks` — design §2.6, §3.5.
  - [ ] Implement `LuaTypedHandler.charTyped` keystroke path (§3.5) calling
    `LuaKeywordBlockCloser`, gated on the toggle.
- **Exit criteria**: TC-8..TC-12 pass; toggling the setting off disables scaffolding (TC-12)
  while brackets/quotes remain active.

## Requirement → Phase Coverage

| Requirement | Priority | Delivered in |
|-------------|----------|--------------|
| EDITOR-01-01 | M | Phase 1 |
| EDITOR-01-02 | M | Phase 1 (existing brace matcher) |
| EDITOR-01-03 | M | Phase 1 |
| EDITOR-01-04 | S | Phase 1 (quotes) + platform (brackets) |
| EDITOR-01-05 | M | Phase 2 |

## Verification Tasks
- [ ] `LuaSmartTypingTest` (`src/test/kotlin/.../lang/editor/`) — `BasePlatformTestCase`,
  `myFixture.configureByText` + `myFixture.type(...)` then assert `editor.document.text`:
  - TC-1 `type("(")` after `print` → `print()` — covers -01-01.
  - TC-2 `type(")")` over inserted closer → single `()` — covers -01-02.
  - TC-3 `type("\"")` at value position → `""` with caret between — covers -01-03 open.
  - TC-4 `type("\"\"")` → skip closer, single `""` — covers -01-03 skip.
  - TC-5 `type("\b")` after fresh `""`/`()` deletes both — covers -01-04.
  - TC-6 `type("(")` inside a `-- comment` / string → no closer inserted — covers -01-01 suppression.
  - TC-7 `type("'")` after identifier (`don` + `'`) → no auto-close — covers -01-03 mid-word.
  - TC-8 accept `function` from completion → `function\nend`, caret on body — covers -01-05 completion.
  - TC-9 accept `if` then type `then ` → `end` scaffolded once — covers -01-05 keystroke.
  - TC-10 `repeat` → scaffolds `until`, not `end` — covers -01-05 repeat.
  - TC-11 accept keyword when block already balanced → no second terminator — covers §3.4 balance.
  - TC-12 with `LuaEditorOptions.autoCloseKeywordBlocks = false` → no scaffolding — covers toggle.
- [x] **VNC-verified 2026-07-10** (verify-in-ide, GoLand on lunar-builder): quote auto-close (`x = ""`),
  `do`→`end` keyword-block scaffolding, and the **"Lua"** section on the Editor ▸ General ▸ Smart Keys page.

## Task Summary

| Phase | Status | Priority |
|-------|--------|----------|
| Phase 1: Quote pairing + bracket suppression | todo | Must |
| Phase 2: Keyword-block completion core + settings | todo | Must |
