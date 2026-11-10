---
id: "BUG-90"
title: "Extra arguments to a standard-library function (`next({}, 1, 2)`, `table.insert({}, 1, 2, 3)`) are not reported"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-90: Extra arguments to a standard-library function (`next({}, 1, 2)`, `table.insert({}, 1, 2, 3)`) are not reported

Found by the MAINT-55 conformance suite: 2 cases (rule `arity.tooMany`, observed `missed`) disagree with LuaLS's expected findings. Cases are in `src/test/resources/conformance/luacats/cases/`, and the recorded outcomes in `results.json`.

## 1. Reproduction
Conformance case `redundant-parameter-013` (`rule=arity.tooMany source=redundant-parameter.lua#13`); lines marked `-- E` are the ones LuaLS reports.

```lua
local _ = next({}, 1, 2) -- E
print(1, 2, 3, 4, 5)
```

Cases in this group: `redundant-parameter-013`, `redundant-parameter-018`.

## 2. Expected vs Actual Behavior
- **Expected**: The call is reported as having too many arguments.
- **Actual**: No finding for either case.
- **Recorded for `redundant-parameter-013`**: no findings; conformance lines [1] must be reported.

## 3. Related
[[BUG-74]] is the opposite defect on in-file class methods (a correct call reported as too many).
