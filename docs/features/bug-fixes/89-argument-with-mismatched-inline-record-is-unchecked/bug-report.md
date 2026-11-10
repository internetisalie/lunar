---
id: "BUG-89"
title: "An inline record argument whose fields differ (`{c, d}` passed where `{a, b}` is declared) is not reported"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-89: An inline record argument whose fields differ (`{c, d}` passed where `{a, b}` is declared) is not reported

Found by the MAINT-55 conformance suite: 1 case (rule `arg.record`, observed `missed`) disagrees with LuaLS's expected findings. Cases are in `src/test/resources/conformance/luacats/cases/`, and the recorded outcomes in `results.json`.

## 1. Reproduction
Conformance case `param-type-mismatch-011` (`rule=arg source=param-type-mismatch.lua#11`); lines marked `-- E` are the ones LuaLS reports.

```lua
---@param opts {a:number, b:number}
local function foo(opts)

end

---@param opts {c:number, d:number}
local function bar(opts)
    foo(opts)  -- this should raise linting error -- E
end
```

Cases in this group: `param-type-mismatch-011`.

## 2. Expected vs Actual Behavior
- **Expected**: `foo(opts)` is reported as a parameter type mismatch (the source comment says it should raise a linting error).
- **Actual**: No finding.
- **Recorded for `param-type-mismatch-011`**: no findings; conformance lines [8] must be reported.
