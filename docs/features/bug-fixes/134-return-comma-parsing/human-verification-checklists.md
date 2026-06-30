---
id: "BUG-134-QA"
title: "Verification Checklist"
type: "qa"
parent_id: "BUG-134"
folders:
  - "[[features/bug-fixes/134-return-comma-parsing/requirements|requirements]]"
---

# Human Verification Checklist: BUG-134

## 1. Comma-Separated @return Parsing
**Purpose**: Verify the parser accepts multiple return types on a single line.
- [ ] Open the IDE Sandbox.
- [ ] Create a new Lua file `return_parsing.lua`.
- [ ] Enter the following code:
  ```lua
  ---@return string id, boolean status
  local function fetchUser()
      return "user_123", true
  end
  ```
- [ ] Verify that no red squiggly lines (syntax errors) appear under the comma `,` in the `@return` tag.

## 2. Positional Type Inference
**Purpose**: Verify that multiple return types from a single tag are correctly assigned to multiple local variables.
- [ ] In the same file, add the following code below the function:
  ```lua
  local myId, myStatus = fetchUser()
  ```
- [ ] Trigger the "Type Info" action (or hover with `Ctrl` / `Cmd`) over `myId`.
- [ ] Verify the inferred type is `string`.
- [ ] Trigger the "Type Info" action over `myStatus`.
- [ ] Verify the inferred type is `boolean`.

## 3. Inlay Hints Continuity
**Purpose**: Ensure the refactor did not break existing method chain inlay hints.
- [ ] Define a class with a method returning `self`:
  ```lua
  ---@class Builder
  local Builder = {}

  ---@return self
  function Builder:build() return self end
  ```
- [ ] Chain the method calls: `Builder:build():build()`
- [ ] Verify that Method Chain inlay hints correctly appear after the calls, indicating the fallback to `firstOrNull()` is functioning properly.
