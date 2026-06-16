---
id: FORMAT-06-PLAN
title: "Implementation Plan"
type: plan
parent_id: FORMAT-06
status: "done"
priority: "medium"
folders:
  - "[[features/formatting/06-comment-formatting/requirements|requirements]]"
---

# Implementation Plan: FORMAT-06 Comment Formatting

## Phase 1: Space after `--` [Could] — FORMAT-06-01
- [ ] Verify `LINE_COMMENT_ADD_SPACE_ON_REFORMAT` adds the space on reformat; align
      `LuaCommenter` if it overrides the platform behaviour.
- [ ] Test: TC-FORMAT-06-01.

## Phase 2: Long-comment wrap [Could] — FORMAT-06-02
- [ ] Add `WRAP_LONG_COMMENTS` to `LuaCodeStyleSettings` + a settings toggle.
- [ ] `LuaCommentWrapPostProcessor : PostFormatProcessor` (§3.1) skipping `LUACATS_COMMENT` +
      `<postFormatProcessor>` registration.
- [ ] Tests: TC-FORMAT-06-02 (wrap), TC-FORMAT-06-03 (doc comment preserved).

## Verification Tasks
- Unit (`reformat`): space-after-dashes; long `--` line wraps within the margin; `---@` intact.
- Manual: toggle wrap option → reformat reflects it.
