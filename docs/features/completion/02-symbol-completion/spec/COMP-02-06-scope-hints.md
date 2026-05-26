---
id: "COMP-02-06"
parent_id: "COMP-02"
title: "User Story: Scope Hints"
folders: ["[[features/completion/02-symbol-completion/requirements|requirements]]"]
status: "done"
priority: "medium"
---

# COMP-02-06: Scope Hints

As a Lua developer, I want to see hints about the origin scope of a symbol in the completion list.

## Acceptance Criteria
- [x] Display "local", "parameter", or "global" in the tail text.
- [x] Help clarify shadowed or similar names.
