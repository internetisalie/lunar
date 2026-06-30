---
id: FORMAT-03-PLAN
title: "Implementation Plan"
type: plan
parent_id: FORMAT-03
priority: "medium"
folders:
  - "[[features/formatting/03-blank-line-management/requirements|requirements]]"
---

# Implementation Plan: FORMAT-03 Blank Line Management

Extends the existing `LuaSpacingBuilder` + a post-format processor.

## Phase 1: Settings-driven function spacing [Should] — FORMAT-03-01/02
- [ ] In `LuaSpacingBuilder.getSpacing`, replace the hard-coded `STANZA_SPACING` for
      `FUNC_DECL`/`LOCAL_FUNC_DECL` with `Spacing.createSpacing(0, 0,
      BLANK_LINES_AROUND_METHOD + 1, true, KEEP_BLANK_LINES_IN_CODE)` (§2.1/§3.1).
- [ ] Audit block-internal explicit `Spacing` constants to pass `KEEP_BLANK_LINES_IN_CODE`.
- [ ] Tests: TC-FORMAT-03-01, TC-FORMAT-03-02.

## Phase 2: Trailing newline [Should] — FORMAT-03-03
- [ ] `LuaTrailingNewlinePostProcessor : PostFormatProcessor` (§2.2/§3.3) +
      `<postFormatProcessor>` registration.
- [ ] Test: TC-FORMAT-03-03.

## Verification Tasks
- Unit (`CodeStyleManager.reformat` on a fixture): function separation honors the setting; runs
  collapse to keep-max; one trailing newline.
- Manual: change Blank Lines settings → reformat reflects them.
