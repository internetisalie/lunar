---
id: "INSP-03-CHECKLIST"
title: "Human Verification Checklist"
type: "checklist"
status: "todo"
parent_id: "INSP-03"
---

# Human Verification Checklist

- [ ] Create a `---@type string` variable and assign `123`. Verify warning appears.
- [ ] Create a `---@type string|number` variable and assign `123`. Verify NO warning appears.
- [ ] Create a function with `---@return string` that returns `123`. Verify warning appears on the return value.
