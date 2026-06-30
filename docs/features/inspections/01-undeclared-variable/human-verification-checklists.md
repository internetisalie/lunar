---
id: "INSP-01-CHECKLIST"
title: "Human Verification Checklist"
type: "qa"
parent_id: "INSP-01"
priority: "medium"
folders:
  - "[[features/inspections/01-undeclared-variable/requirements|requirements]]"
---

# Human Verification Checklist: INSP-01 Undeclared Variable

Use this checklist to manually verify the implementation of undeclared variable highlighting in the IDE.

## Basic Highlighting
- [ ] Type a new variable name in an expression (e.g., `print(myNewVar)`).
- [ ] Verify that `myNewVar` is highlighted (Error/Warning).
- [ ] Hover over the variable and verify the message: "Undeclared variable 'myNewVar'".

## Local Scopes
- [ ] Define a local variable: `local myLocal = 1`.
- [ ] Use it on the next line: `print(myLocal)`.
- [ ] Verify that `myLocal` is NOT highlighted.
- [ ] Use it in a nested block: `if true then print(myLocal) end`.
- [ ] Verify that `myLocal` is NOT highlighted.
- [ ] Move the `print(myLocal)` call ABOVE the `local` declaration.
- [ ] Verify that it IS highlighted as undeclared.

## Global Scopes
- [ ] Create two files: `file1.lua` and `file2.lua`.
- [ ] In `file1.lua`, define a global: `myGlobal = 10`.
- [ ] In `file2.lua`, use it: `print(myGlobal)`.
- [ ] Verify that `myGlobal` is NOT highlighted (after indexing completes).
- [ ] Change `myGlobal` to a typo in `file2.lua`.
- [ ] Verify that it IS highlighted.

## Standard Libraries
- [ ] Use `print`, `math.abs`, `table.insert`, `string.upper`.
- [ ] Verify that none of these are highlighted as undeclared.

## Assignments (Write Context)
- [ ] Type `newGlobal = 5`.
- [ ] Verify that `newGlobal` is NOT highlighted as an Undeclared Variable (it may be a Global Creation Warning depending on INSP-05 settings).
- [ ] Type `local x; x = 10`.
- [ ] Verify `x` is not highlighted.

## Settings
- [ ] Add `myCustomGlobal` to the "Additional Globals" list in project settings.
- [ ] Use it in a file: `print(myCustomGlobal)`.
- [ ] Verify it is NOT highlighted.
- [ ] Use the "Add 'X' to Additional Globals" quick fix on an undeclared variable and verify
      the warning clears.

## Inline Suppression
- [ ] Above `print(mysteryGlobal)`, add `---@diagnostic disable-next-line: undefined-global`;
      verify the warning is gone.
- [ ] Add a trailing `-- luacheck: ignore mysteryGlobal`; verify the warning is gone.
- [ ] Verify a `_`-prefixed global (e.g. `_FOO`) is not highlighted with default settings.
