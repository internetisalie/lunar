---
id: "SYNTAX-17-CHECKLIST"
title: "Verification Checklists"
type: "spec"
parent_id: "SYNTAX-17"
status: "planned"
priority: "low"
folders: ["[[features/syntax/17-inferred-type-highlighting/requirements]]"]
---

# SYNTAX-17: Human Verification Checklists

## Manual Verification Steps

### Function Call Highlighting
- [ ] Define `local function myLocal() end`.
- [ ] Call `myLocal()` and verify its color matches the "Local function call" setting.
- [ ] Call a global function `myGlobal()` and verify its color matches "Global function call".
- [ ] Call `print()` and verify it matches "Platform function call".

### Class & Type Highlighting
- [ ] Add `---@class MyCustomClass`.
- [ ] Reference `MyCustomClass` in a `@type` tag or as a constructor `MyCustomClass()`.
- [ ] Verify `MyCustomClass` is highlighted with the class color.

### Member Differentiation
- [ ] Create a table `local t = { data = 1, func = function() end }`.
- [ ] Type `t.data` and `t.func()`.
- [ ] Verify `data` is colored as a Field and `func` as a Function/Method.

### Settings Integration
- [ ] Go to `Settings > Editor > Color Scheme > Lua`.
- [ ] Change the color for "Local function call".
- [ ] Verify the change is immediately reflected in the editor.
