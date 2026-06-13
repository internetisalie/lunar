---
id: "FORMAT-04-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "FORMAT-04"
status: "planned"
priority: "medium"
folders:
  - "[[features/formatting/04-expression-wrapping/requirements|requirements]]"
---

# Implementation Plan: FORMAT-04 Expression Wrapping

## Phase 1: Wrap infrastructure [Should] — FORMAT-04-01/02
- [ ] Add a `wrap` ctor param to `LuaFormatBlock` (replacing the hard-coded `WrapType.NONE`).
- [ ] In `addChildBlocks`, build the shared construct `Wrap` for `ARGS`/`TABLE_CONSTRUCTOR`
      (§2.2/§3.1) and pass it to item children.
- [ ] Tests: TC-FORMAT-04-01, TC-FORMAT-04-02, TC-FORMAT-04-03.

## Phase 2: Settings [Should] — FORMAT-04-03
- [ ] Add `WRAP_ARGUMENTS` / `WRAP_TABLE_CONSTRUCTOR` to `LuaCodeStyleSettings`.
- [ ] Expose them via `showCustomOption` in `WRAPPING_AND_BRACES_SETTINGS` (§2.3).

## Verification Tasks
- Unit (`CodeStyleManager.reformat` with a set `RIGHT_MARGIN`): long constructs chop down, short
  ones stay inline; policy honored.
- Manual: toggle the wrap option → reformat reflects it.
