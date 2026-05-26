---
id: "COMP-02-03"
parent_id: "COMP-02"
title: "User Story: Local Shadowing"
folders: ["[[features/completion/02-symbol-completion/requirements|requirements]]"]
status: "done"
priority: "high"
---

# COMP-02-03: Local Shadowing

As a Lua developer, I want the completion list to respect shadowing rules so that I always get the most relevant variable suggestion.

## Acceptance Criteria
- [x] Suggest the closest definition when multiple variables have the same name.
- [x] Shadowed locals from outer scopes are correctly hidden or prioritized.
