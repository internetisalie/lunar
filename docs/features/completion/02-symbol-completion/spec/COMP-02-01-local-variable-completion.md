---
id: "COMP-02-01"
type: "user-story"
parent_id: "COMP-02"
title: "User Story: Local Variable Completion"
folders: ["[[features/completion/02-symbol-completion/requirements|requirements]]"]
status: "done"
vf_icon: ✅
priority: "high"
---

# COMP-02-01: Local Variable Completion

As a Lua developer, I want to see local variables in the completion list so that I can code faster and avoid typos.

## Acceptance Criteria
- [x] Suggest local variables defined with `local` keyword.
- [x] Variables from current and parent scopes are included.
- [x] Basic keyword filtering when typing prefixes.
