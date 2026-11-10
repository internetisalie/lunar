---
id: "BUG-78"
title: "Assigning a wrong value to a typed local (`x = f()`, a literal outside `1|2`, a mismatched `table<K, V>`) is not reported"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-78: Assigning a wrong value to a typed local (`x = f()`, a literal outside `1|2`, a mismatched `table<K, V>`) is not reported

Found by the MAINT-55 conformance suite: 10 cases (rule `cast.local`, observed `missed`) disagree with LuaLS's expected findings. Cases are in `src/test/resources/conformance/luacats/cases/`, and the recorded outcomes in `results.json`.

## 1. Reproduction
Conformance case `cast-local-type-011` (`rule=cast.local source=cast-local-type.lua#11`); lines marked `-- E` are the ones LuaLS reports.

```lua
---@type integer
local x

---@type number
x = f() -- E
```

Cases in this group: `cast-local-type-011`, `cast-local-type-013`, `cast-local-type-015`, `cast-local-type-023`, `cast-local-type-028`, `cast-local-type-029`, `cast-local-type-031`, `cast-local-type-032`, `cast-local-type-034`, `cast-local-type-035`.

## 2. Expected vs Actual Behavior
- **Expected**: The marked assignment is reported as not assignable to the local's `@type`.
- **Actual**: No finding at all, for every case in the group.
- **Recorded for `cast-local-type-011`**: no findings; conformance lines [5] must be reported.
