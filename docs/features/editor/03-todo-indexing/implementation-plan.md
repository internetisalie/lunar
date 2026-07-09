---
id: "EDITOR-03-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "EDITOR-03"
folders:
  - "[[features/editor/03-todo-indexing/requirements|requirements]]"
---

# EDITOR-03: Implementation Plan

Single small class + one declarative registration + one real-flow test. Design (`design.md`) has
cleared the bar: class named, `plugin.xml` block stated, delta algorithm specified (§3.1),
Open Questions empty. Two phases keep the build green at each step.

## Phases

### Phase 1: Index-pattern builder + registration [Must]
- **Goal**: Lua comments become TODO-scannable; default and custom TODO patterns surface in the
  TODO tool window, gutter, error stripe, and `PsiTodoSearchHelper`.
- **Tasks**:
  - [ ] Create `net.internetisalie.lunar.lang.todo.LuaTodoIndexPatternBuilder` implementing
        `com.intellij.psi.impl.search.IndexPatternBuilder` — realizes design §2.1. Guard every
        method on `file is LuaFile`; define the `COMMENT_TOKENS` `TokenSet` from
        `LuaElementTypes.SHORTCOMMENT`, `LuaElementTypes.LONGCOMMENT`, and
        `LuaLazyElementTypes.LUACATS_COMMENT` (exclude `SHEBANG`, design §6).
  - [ ] Implement the three private delta helpers `fixedStartDelta` / `longBracketStartDelta` /
        `endDelta` — realizes design §3.1 (each ≤30 logic lines, ≤3 args; no `!!`, no negative or
        out-of-range delta).
  - [ ] Register `<indexPatternBuilder implementation="net.internetisalie.lunar.lang.todo.LuaTodoIndexPatternBuilder"/>`
        in the existing `<extensions defaultExtensionNs="com.intellij">` block of
        `src/main/resources/META-INF/plugin.xml` — realizes design §7.
- **Exit criteria**: plugin loads; `PsiTodoSearchHelper.getInstance(project).findTodoItems(luaFile)`
  returns a match for `-- TODO: x` and none for a string literal (TC-1, TC-5 below).

### Phase 2: Block / doc-comment coverage + custom pattern [Should]
- **Goal**: TODOs match inside `--[[ ]]` block comments (including `--[==[`), `---` LuaCATS doc
  comments, and against user-configured custom patterns.
- **Tasks**:
  - [ ] Verify `longBracketStartDelta` against `--[[ FIXME ]]` and `--[==[ TODO ]==]` — realizes
        design §3.1 steps 3–5 (no code beyond Phase 1; this task is the block-comment test).
  - [ ] Verify `LUACATS_COMMENT` delta (3) matches TODOs inside `--- TODO: doc` — realizes design
        §2.1 / §3.1 step 2 (`EDITOR-03-04`).
  - [ ] Add a custom-pattern test toggling `TodoConfiguration.getInstance().setTodoPatterns(...)`
        for a `\bHACK\b.*` pattern — realizes design §6 custom-pattern edge case (`EDITOR-03-02`).
- **Exit criteria**: TC-2, TC-3, TC-4, TC-6 pass (below).

## Requirement → Phase Coverage

| Requirement | Priority | Delivered in |
|-------------|----------|--------------|
| EDITOR-03-01 | M | Phase 1 |
| EDITOR-03-02 | M | Phase 1 (default) + Phase 2 (custom) |
| EDITOR-03-03 | S | Phase 1 (platform TODO attributes, no wiring) |
| EDITOR-03-04 | S | Phase 2 |

## Verification Tasks

Real-flow DoD (epic DoD gate): drive the platform TODO machinery and assert the user-visible result
via `PsiTodoSearchHelper.findTodoItems`, mirroring `MarkdownTodoTest`
(`~/Documents/src/lua/intellij-community/plugins/markdown/test/.../MarkdownTodoTest.java`). New test
class `net.internetisalie.lunar.lang.todo.LuaTodoIndexPatternBuilderTest` extends
`BasePlatformTestCase`; `myFixture.configureByText(LuaFileType.INSTANCE, text)` then assert
`PsiTodoSearchHelper.getInstance(project).findTodoItems(myFixture.file).size`.

- [ ] **TC-1 (line comment, positive)** — `local x = 1 -- TODO: refactor` → 1 TodoItem. Covers
      EDITOR-03-01, -02.
- [ ] **TC-2 (block comment, positive)** — `--[[ FIXME see #12 ]]` → 1 TodoItem. Covers EDITOR-03-04.
- [ ] **TC-3 (leveled block comment)** — `--[==[ TODO leveled ]==]` → 1 TodoItem (validates the
      variable bracket length in §3.1). Covers EDITOR-03-01, -04.
- [ ] **TC-4 (LuaCATS doc comment, positive)** — `--- TODO: document this` → 1 TodoItem. Covers
      EDITOR-03-04.
- [ ] **TC-5 (string literal, negative)** — `local s = "TODO not a comment"` → 0 TodoItems. Covers
      EDITOR-03-01 (only comment tokens scanned).
- [ ] **TC-6 (code, negative)** — `local TODO = 1` (identifier, no comment) → 0 TodoItems.
- [ ] **TC-7 (custom pattern)** — set `TodoPattern("\\bHACK\\b.*", TodoAttributesUtil.createDefault(),
      false)` via `TodoConfiguration`, then `-- HACK: temp` → 1 TodoItem; restore patterns in
      `finally`. Covers EDITOR-03-02.
- [ ] Run `human-verification-checklists.md` (VNC: open a Lua file with `-- TODO`, confirm TODO tool
      window entry + gutter mark).

## Task Summary

| Phase | Status | Priority |
|-------|--------|----------|
| Phase 1: Builder + registration | planned | Must |
| Phase 2: Block/doc/custom coverage | planned | Should |
