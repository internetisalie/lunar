---
id: "BUG-82"
title: "Calling a colon-defined method with a dot (`t.init()` for `function t:init()`) is not reported as missing `self`, and has no quick fix"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-82: Calling a colon-defined method with a dot (`t.init()` for `function t:init()`) is not reported as missing `self`, and has no quick fix

Found by the MAINT-55 conformance suite: 1 case (rule `arity.tooFew`, observed `missed`) disagrees with LuaLS's expected findings. Cases are in `src/test/resources/conformance/luacats/cases/`, and the recorded outcomes in `results.json`.

## 1. Reproduction
Conformance case `missing-parameter-010` (`rule=arity.tooFew source=missing-parameter.lua#10`); lines marked `-- E` are the ones LuaLS reports.

```lua
local t = {}

function t:init() end

t.init() -- E
```

Cases in this group: `missing-parameter-010`.

## 2. Expected vs Actual Behavior
- **Expected**: `t.init()` is reported as too few arguments: the method's implicit `self` is missing. A quick fix rewrites the dot call `t.init()` to the colon call `t:init()`.
- **Actual**: No finding.
- **Recorded for `missing-parameter-010`**: no findings; conformance lines [5] must be reported.

## 3. Related
The suite's rejected `missing-self` deviation proposal was for this case; this report requires the finding and the fix instead.
