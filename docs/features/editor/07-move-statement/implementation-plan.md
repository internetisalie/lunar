---
id: "EDITOR-07-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "EDITOR-07"
folders:
  - "[[features/editor/07-move-statement/requirements|requirements]]"
---

# EDITOR-07: Implementation Plan

Sequence the two EPs so each phase leaves the build green and is proven by a real-flow
`CodeInsightTestFixture` action test (the roadmap DoD gate). Phase 1 (left/right) is the smallest
and lowest-risk surface; Phase 2 (up/down sibling) delivers the `Must`; Phase 3 adds block-entry
re-indent; Phase 4 nails the fallback. All classes/algorithms are defined in `design.md`.

## Phases

### Phase 1: Move Element Left/Right handler [Should]
- **Goal**: Ctrl+Alt+Shift+←/→ reorders call args, table fields, and name lists.
- **Tasks**:
  - [x] Create `net.internetisalie.lunar.lang.editor.LuaMoveLeftRightHandler` (design §2.2)
        implementing `getMovableSubElements` per the §3.2 first-match dispatch over `LuaExprList`,
        `LuaFieldList`, `LuaNameList`, `LuaLocalVarDecl.attNameList`, `LuaGlobalVarDecl.attNameList`.
  - [x] Register `<lang.moveLeftRightHandler language="Lua" implementationClass="…LuaMoveLeftRightHandler"/>`
        in `plugin.xml` (design §7).
- **Exit criteria**: TC-03a…TC-03d pass (reorder arg, field, `for` name, `local a, b` name); a
  single-element list is a no-op.

### Phase 2: Statement Up/Down — sibling move [Must]
- **Goal**: Ctrl+Shift+↑/↓ moves a whole statement over its sibling without corrupting delimiters.
- **Tasks**:
  - [x] Create `net.internetisalie.lunar.lang.editor.LuaStatementMover` (design §2.1) with
        `checkAvailable` steps 1–6 and helpers `enclosingStatement`, `statementRange`,
        `targetStatement` (design §3.1). Block-boundary path may return `prohibitMove()` for now;
        `blockBoundaryTarget` stub returns `null` until Phase 3.
  - [x] Register `<statementUpDownMover id="LuaStatementMover" implementation="…LuaStatementMover"/>`
        in `plugin.xml` (design §7).
- **Exit criteria**: TC-01a (swap two siblings up and down, round-trips to original), TC-01b
  (`if…end`/`repeat…until` delimiter never swapped away — text asserted), TC-01c (first/last
  statement is a no-op) pass.

### Phase 3: Enter/leave blocks + re-indent [Should]
- **Goal**: Moving a statement into/out of an adjacent block body re-indents correctly.
- **Tasks**:
  - [x] Implement `LuaStatementMover.blockBoundaryTarget` (design §3.1) computing the body-edge or
        delimiter-line `LineRange`, and set the `ENTERED_BLOCK` `MoveInfo` user-data flag.
  - [x] Override `LuaStatementMover.afterMove` to call `CodeStyleManager.adjustLineIndent` on the
        moved range when `ENTERED_BLOCK` is set (design §3.3), guarded by try/catch + `Logger`.
- **Exit criteria**: TC-02a (statement moves into an `if` body and is indented), TC-02b (statement
  moves out of a `for` body over `end` and is de-indented) pass.

### Phase 4: Line-move fallback [Could]
- **Goal**: Where no structural move applies, defer to the platform `LineMover`.
- **Tasks**:
  - [x] Confirm `checkAvailable` returns `false` (not `prohibitMove()`) on the no-enclosing-statement
        and multi-line-string paths (design §3.1 steps 3–4) so `LineMover` runs.
- **Exit criteria**: TC-04a (caret on blank line → plain line swap), TC-04b (caret in a multi-line
  string → line swap, literal not split) pass.

## Requirement → Phase Coverage

| Requirement | Priority | Delivered in |
|-------------|----------|--------------|
| EDITOR-07-01 Move statement up/down | M | Phase 2 |
| EDITOR-07-02 Enter/leave blocks | S | Phase 3 |
| EDITOR-07-03 Move element left/right | S | Phase 1 |
| EDITOR-07-04 Line-move fallback | C | Phase 4 |

## Verification Tasks
All tests are real-flow: `myFixture.configureByText("a.lua", before)`,
`myFixture.performEditorAction(IdeActions.ACTION_MOVE_STATEMENT_DOWN_ACTION / _UP_ACTION /
MOVE_ELEMENT_RIGHT / MOVE_ELEMENT_LEFT)`, then `myFixture.checkResult(after)` (engine-only asserts
do NOT satisfy the DoD gate — EDITOR epic DoD).
- [x] `LuaMoveLeftRightHandlerTest` (extends `BasePlatformTestCase`) — covers TC-03a…TC-03d, plus a
      round-trip (right then left returns the original) mirroring `GroovyMoveLeftRightHandlerTest`.
- [x] `LuaStatementMoverTest` (extends `BasePlatformTestCase`) — covers TC-01a…TC-01c, TC-02a/-02b,
      TC-04a/-04b; assert full document text after each action.
- [ ] Run `human-verification-checklists.md` (drive the four actions live in GoLand per verify-in-ide) —
      optional; `LuaStatementMoverTest`/`LuaMoveLeftRightHandlerTest` drive the real editor actions headlessly.

## Implementation Notes (as-built)
- **Block enter/leave needs no custom PSI move.** `MoveInfo.toMove`/`toMove2` are two line-ranges the
  platform *swaps*; entering a block = swap the statement with the block's **opening line** (down) or its
  **`end` line** (up); leaving = swap with the enclosing construct's delimiter line. `MoveInfo.indentTarget`
  (default true) re-indents the moved line for free, so **no `afterMove` override** was needed (design §3.3
  simplified away). A single `delimiterLine(construct, enteringStart)` helper covers all four directions.
- **`repeat…until` and file-edge prohibit** (safe no-op): a body statement can't step over `until <expr>`
  (not a bare delimiter) nor off the file, so `targetRange` returns null → `prohibitMove()`.
- **EP tag correction:** the left/right handler registers as `<moveLeftRightHandler>` (EP name
  `com.intellij.moveLeftRightHandler`), **not** `<lang.moveLeftRightHandler>` — the wrong tag makes it a
  silent no-op. The mover is the application-level `<statementUpDownMover>` (no `language`; self-guards on
  `LuaFile`).

## Task Summary

| Phase | Status | Priority |
|-------|--------|----------|
| Phase 1: Move Element Left/Right handler | done | Should |
| Phase 2: Statement Up/Down — sibling move | done | Must |
| Phase 3: Enter/leave blocks + re-indent | done | Should |
| Phase 4: Line-move fallback | done | Could |
