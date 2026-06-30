---
id: "SYNTAX-07-11-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "SYNTAX-07-11"
priority: "medium"
folders:
  - "[[features/syntax/07-inlay-hints/07-11-large-file-threshold/requirements|requirements]]"
---
# Implementation Plan: SYNTAX-07-11 Large File Threshold

## Phase 1: Settings Extension
- [ ] Add `largeFileThreshold` to `LuaInlayHintsSettings.State`.
- [ ] Ensure default value is correctly initialized to 10,000.
- [ ] Add the corresponding input field to the settings UI (coordinated with Task 282).

## Phase 2: Provider Logic
- [ ] Implement the early exit check in `LuaTypeInlayHintProvider.getCollectorFor`.
- [ ] Verify that the check uses `Document.lineCount` for efficiency.

## Phase 3: Verification
- [ ] Create a test case with a mock file and threshold to verify the skipping logic.
- [ ] Verify that changing the threshold updates the editor behavior live.
- [ ] Manual test with a large generated Lua file.
