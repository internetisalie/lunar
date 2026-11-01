---
id: "BUG-21"
title: "A later reassignment reaches an earlier argument's demand"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-21: A later reassignment reaches an earlier argument's demand

Found fixing [[BUG-19]]. Observed in the corpus; root cause not investigated.

## 1. Reproduction

`corpus/zerobrane/src/editor/style.lua:513-520`, Lua 5.1 target:

```lua
local cfgfn, err = loadfile(config)
if not cfgfn then return end
setfenv(cfgfn, cfg)
cfgfn, err = pcall(cfgfn, style)
```

## 2. Expected vs Actual
- **Expected**: no diagnostic. `cfgfn` is a function when `setfenv` receives it.
- **Actual**: *boolean is not assignable to union function | number* on lines 513, 519 and 520.

## 3. Context
- The boolean is `pcall`'s result, assigned after the call. Each variable has one `VariableElement`,
  which collects every write, and `checkTypes` crosses all of them with every demand, so order is
  not modelled. Read from the code.
- The fix is flow-sensitive reads, which is [[ANALYSIS-07]]. An order check on this path alone would
  be one more special case on the demand seam, so this waits for that feature.
- These three hits appeared when [[BUG-19]] began checking function-typed demands, and are in
  `zerobrane.baseline`. Whether a scalar demand reports the same shape was not measured.
