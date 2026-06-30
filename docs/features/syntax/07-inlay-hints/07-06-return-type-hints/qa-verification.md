---
id: "SYNTAX-07-06-QA"
title: "QA Verification"
type: "qa"
parent_id: "SYNTAX-07-06"
priority: "medium"
folders:
  - "[[features/syntax/07-inlay-hints/07-06-return-type-hints/requirements|requirements]]"
---
# QA Verification: SYNTAX-07-06 (Return Type Inlay Hints)

This document outlines the QA verification steps for the return type inlay hints feature.

## 1. Test Basic Functionality
Create a file named `qa_test.lua` and add the following functions. Ensure the inlay hints appear as shown in the comments:

```lua
-- Verify single return type
local function get_name() -- : string
    return "Luna"
end

-- Verify multiple return types
local function get_coords() -- : number, number
    return 10.5, 20.0
end
```

## 2. Test Suppression (Annotations)
Verify that explicit `---@return` tags take precedence and suppress automatically inferred hints:

```lua
---@return string
local function get_user() -- (No hint here)
    return 123 -- (Incorrect return, but hint should be suppressed)
end
```

## 3. Test Inferred Suppression (`any`/`unknown`)
Verify that functions returning `any` or `unknown` (or lacking returns entirely) do not show empty or useless hints:

```lua
local function do_nothing() -- (No hint here)
    -- No return
end

local function dynamic_func() -- (No hint here)
    return -- Returns 'any'/'unknown'
end
```

## 4. Test Nested/Higher-Order Functions
Verify that hints correctly appear on both the parent function and the nested callback:

```lua
local function process(callback) -- : boolean
    return callback("data")
end

process(function(m) -- : string, boolean
    return #m > 0
end)
```

## Verification Checklist
- [ ] Hints appear immediately after the `)` of the parameter list.
- [ ] Multiple return types are separated by commas (e.g., `: number, string`).
- [ ] No hints appear when `---@return` is present.
- [ ] No hints appear when the return type is `any`, `unknown`, or `void`.
- [ ] Hints update correctly when return statements are modified.
