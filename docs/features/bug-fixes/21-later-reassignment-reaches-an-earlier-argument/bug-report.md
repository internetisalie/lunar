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
- The boolean is `pcall`'s result, assigned after the call. A variable's writes appear to reach a
  demand regardless of order. This is an inference from the code, not a measurement.
- These three hits appeared when [[BUG-19]] began checking function-typed demands, and are in
  `zerobrane.baseline`. Whether a scalar demand reports the same shape was not measured.
