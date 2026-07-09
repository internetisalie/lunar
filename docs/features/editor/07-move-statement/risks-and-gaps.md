---
id: "EDITOR-07-RISKS"
title: "Risks & Gaps"
type: "risk"
parent_id: "EDITOR-07"
folders:
  - "[[features/editor/07-move-statement/requirements|requirements]]"
---

# EDITOR-07: Risks & Gaps

## Critical Risks

### Risk 1.1: Block-delimiter corruption (`end` / `until` / `then` / `do`)
- **Impact**: A move that swaps a statement past its enclosing `end`/`until`/`then`/`else`/`elseif`
  produces unbalanced Lua that no longer parses — the worst possible outcome for a mover.
- **Likelihood**: medium (this is the classic mover failure mode; the naive `LineMover` does exactly
  this, which is why Lua needs a structural mover).
- **Mitigation**: `targetStatement` selects only siblings that `is LuaStatement` **within the same
  `LuaBlock`** (design §3.1). Block delimiters (`end`, `until`, `else`, `elseif`, `then`, `do`) are
  keyword leaves / `LuaBlockParent` structure, never `LuaStatement`s, so they can never be chosen as
  a swap target. The dedicated TC-01b asserts that a move at the last statement of an `if`/`repeat`
  body does not displace the delimiter. `blockBoundaryTarget` moves *into a body edge line* or
  *over a single delimiter line*, never rewriting the delimiter token.

### Risk 1.2: `afterMove` re-indent throwing on ERROR-element PSI
- **Impact**: If the moved text transiently produces an `ERROR_ELEMENT`, `adjustLineIndent` could
  throw and (unguarded) crash the move action.
- **Likelihood**: low.
- **Mitigation**: `afterMove` wraps `adjustLineIndent` in try/catch, logs via
  `Logger.getInstance(LuaStatementMover::class.java)`, and swallows (engineering contract §2 — no IDE
  crashes). Only the moved `LineRange` is reindented, never the whole file.

### Risk 1.3: EP registration shape (application-level vs `LanguageExtension`)
- **Impact**: Registering `statementUpDownMover` with a `language=` attribute (like the language EPs)
  would silently no-op; registering `moveLeftRightHandler` without `language="Lua"` would apply it to
  all languages.
- **Likelihood**: low (pinned in design §7 with `file:line` evidence).
- **Mitigation**: `statementUpDownMover` is application-level (`implementation=` + `id`, no `language`;
  self-guards on `file is LuaFile`); `moveLeftRightHandler` is a `LanguageExtension` registered as
  `lang.moveLeftRightHandler language="Lua"`. Verified against `StatementUpDownMover.java:26` and
  `MoveElementLeftRightHandler.java:14`.

## Design Gaps

_None open._ Every decision the implementer needs is pinned in `design.md` (§3.1–§3.3 algorithms,
§7 registrations). Items deliberately out of scope are parked under Technical Debt below, not left
as questions.

## Technical Debt & Future Work
- **TBD: Reorder assignment LHS `x, y = 1, 2`** — the `varList` (LHS) side is not made movable in
  §3.2; only the RHS `LuaExprList`, arg lists, table fields, and name lists reorder. Reordering the
  LHS in isolation would desynchronize it from the paired RHS values, so it is deferred.
- **TBD: Move across `elseif`/`else` branches** — moving a statement from an `if` branch into the
  matching `elseif`/`else` branch is not handled; the boundary logic (§3.1) enters/leaves the nearest
  adjacent `LuaBlockParent` body only. Multi-branch traversal is future polish.
- **TBD: Move whole compound statement (`if`/`function`) as a unit past another block** — supported by
  the sibling walk (a `LuaIfStatement` *is* a `LuaStatement`), but re-indent of a multi-line moved
  block relies on `adjustLineIndent` per moved line; deep nested-indent fidelity is verified manually
  (human-verification), not asserted line-for-line in unit tests.

## Pre-Implementation De-risking Tasks

| ID | Action | Resolves | Status |
|----|--------|----------|--------|
| EDITOR-00-DR-01 | Prototype `LuaStatementMover.checkAvailable` on `if…end` / `repeat…until` fixtures; assert the delimiter line is never chosen as `toMove2` (write TC-01b first). | Risk 1.1 | todo |
| EDITOR-00-DR-02 | Confirm platform `MoveElementLeftRightAction` keeps `,`/`;` separators when swapping two `LuaField`s with a `;` separator (`{a; b; c}`). | EDITOR-07-03 separator validity | todo |
| EDITOR-00-DR-03 | Verify `adjustLineIndent` in `afterMove` produces block-correct indentation on enter/leave (TC-02a/-02b) under the platform's write command. | Risk 1.2, EDITOR-07-02 | todo |

## Test Case Gaps
- Nested-block indent fidelity (moving a multi-line `if` into another block) is covered by
  human-verification only, not a line-exact unit assertion (see Technical Debt).
- Selection-spanning-multiple-statements move (multi-line selection) is handled by the inherited
  `getLineRangeFromSelection` but is not given a dedicated TC; add one if regressions appear.

## See Also
- Requirements: [requirements.md](requirements.md)
- Design: [design.md](design.md)
