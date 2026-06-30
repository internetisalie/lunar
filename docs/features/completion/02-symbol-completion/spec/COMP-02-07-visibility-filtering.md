---
id: "COMP-02-07"
type: "user-story"
parent_id: "COMP-02"
title: "User Story: Visibility Filtering"
folders: ["[[features/completion/02-symbol-completion/requirements|requirements]]"]
status: "done"
vf_icon: ✅
priority: "low"
---

# COMP-02-07: Visibility Filtering

As a Lua developer, I want to only see symbols that are actually visible at the current cursor position.

## Acceptance Criteria
- [x] Exclude symbols declared after the cursor position in the same scope.
- [x] Correctly handle forward references in global scope vs local scope.
