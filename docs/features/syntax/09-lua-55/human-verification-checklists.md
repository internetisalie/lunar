---
id: "SYNTAX-09-QA"
title: "Verification Checklist"
type: "qa"
status: "todo"
parent_id: "SYNTAX-09"
folders:
  - "[[features/syntax/09-lua-55/requirements|requirements]]"
---

# Human Verification Checklist: SYNTAX-09

## 1. Project Settings Update
**Purpose**: Verify Lua 5.5 is available to users.
- [ ] Open the IDE Sandbox.
- [ ] Navigate to Settings -> Languages & Frameworks -> Lunar (or Lua).
- [ ] Verify that `5.5` appears as an option in the Language Level dropdown.

## 2. Syntax Validation (Lua 5.5 Mode)
**Purpose**: Verify new syntax parses without errors in 5.5 mode.
- [ ] Set the project language level to `5.5`.
- [ ] Create a new file `test.lua`.
- [ ] Enter the following code:
  ```lua
  // This is a single-line comment
  global my_new_var = 10
  ```
- [ ] Verify that no syntax errors or red squiggly lines appear.
- [ ] Verify that `my_new_var` is syntax-highlighted as a declared variable.

## 3. Backward Compatibility Inspection
**Purpose**: Verify 5.5 syntax throws a warning in older versions.
- [ ] Change the project language level to `5.4` or lower.
- [ ] Return to `test.lua`.
- [ ] Verify that a warning appears under `//` stating: "Single-line comments starting with '//' are available in Lua 5.5+".
- [ ] Verify that a warning appears under the `global` keyword stating: "Global variable declarations are available in Lua 5.5+".
- [ ] Press `Alt+Enter` on the warning and execute the "Set project language level to 5.5" quick fix.
- [ ] Verify the warnings disappear.
