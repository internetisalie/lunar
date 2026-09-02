---
id: "REFACT-09-CHECKLIST"
title: "09: Verification Checklists"
type: "qa"
parent_id: "REFACT-09"
folders:
  - "[[features/refactoring/09-colon-method-rename/requirements|requirements]]"
---

# Verification Checklists: REFACT-09 — Colon-method rename

Run these in the containerised GoLand (`.agents/skills/verify-in-ide/SKILL.md`). They cover the one
thing the unit suite cannot: under `BasePlatformTestCase`, `CommonRefactoringUtil.showErrorHint`
**throws** instead of painting, so no automated test has ever seen a refusal balloon
(`risks-and-gaps.md`, Test Case Gaps).

## 1. Rename

### Scenario 1.1: The declaration caret renames every call site
- **Setup**: a new `.lua` file containing
  ```lua
  local t = {}
  function t:m() end
  t:m()
  t:m()
  ```
- **Steps**: put the caret on the `m` of `function t:m()`, press <kbd>Shift+F6</kbd>, type `n`, press Enter.
- **Expected**: the rename **dialog** opens (not an in-place template — `METHOD_FUNCTION` is not
  file-local, so both in-place gates decline). All three sites read `n`.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 1.2: A call-site caret renames the same set
- **Setup**: Scenario 1.1's file.
- **Steps**: caret on the `m` of the first `t:m()`; <kbd>Shift+F6</kbd>; `n`; Enter.
- **Expected**: identical result to 1.1.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 1.3: A `self:` call renames with the declaration
- **Setup**:
  ```lua
  local C = {}
  function C:m() end
  function C:a() self:m() end
  C:m()
  ```
- **Steps**: caret on the `m` of `function C:m()`; <kbd>Shift+F6</kbd>; `n`; Enter.
- **Expected**: `function C:n()`, `self:n()` and `C:n()`.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 1.4: One undo restores the file
- **Steps**: after 1.3, press <kbd>Ctrl+Z</kbd> **once**.
- **Expected**: the file returns to its exact pre-rename text, and the undo entry reads as one
  rename action rather than three edits.
- **Result**: ⬜ Pass / ⬜ Fail

## 2. Refusals — the balloon text

Each scenario asserts the balloon a user actually sees. Confirm the message names its own reason,
that no text changes in the editor, and that the balloon is dismissible.

### Scenario 2.1: Global receiver
- **Setup**: `a.lua` = `Obj = {}` / `function Obj:m() end` / `Obj:m()`, and `b.lua` = `Obj:m()`.
- **Steps**: caret on the `m` of the declaration; <kbd>Shift+F6</kbd>.
- **Expected**: a "Cannot perform refactoring" balloon reading *The receiver 'Obj' is not a
  file-local table, so call sites in other files cannot be found.* Both files unchanged.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 2.2: The receiver's value escapes
- **Setup**: `local M = {}` / `function M:m() end` / `M:m()` / `return M`.
- **Steps**: caret on the declaration's `m`; <kbd>Shift+F6</kbd>.
- **Expected**: *The receiver's value escapes at 'M' (bare, not a call head), so not every call site
  of this method can be found.* File unchanged.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 2.3: Dotted access to the same member
- **Setup**: `local t = {}` / `function t:m() end` / `t:m()` / `print(t.m)`.
- **Expected**: *This method is also accessed as '.m', which this rename does not rewrite.*
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 2.4: An undecidable call site
- **Setup**: `local t = {}` / `function t:m() end` / `t:m()` / `local function f(x) x:m() end`.
- **Expected**: *The call 'm' on line 4 cannot be bound to a declaration…* — and check the **line
  number is right**, since it is computed from the document and nothing else asserts it.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 2.5: Caret on `self`
- **Setup**: Scenario 1.3's file.
- **Steps**: put the caret in the middle of the word `self`; <kbd>Shift+F6</kbd>.
- **Expected**: *'self' is not the method name; put the caret on the method name to rename it.*
  Nothing is renamed — in particular the enclosing `function C:a()` is **not** renamed, which is what
  happens without the guard.
- **Result**: ⬜ Pass / ⬜ Fail

## 3. Conflicts

### Scenario 3.1: The receiver already has the new name
- **Setup**: `local t = {}` / `function t:m() end` / `function t:n() end` / `t:m()` / `t:n()`.
- **Steps**: rename `t:m` to `n`.
- **Expected**: the platform's **conflicts dialog** lists *This table already has a member named 'n';
  renaming would merge the two*, with Continue and Cancel. Cancel leaves the file unchanged.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 3.2: An identical shape in another file is not a conflict
- **Setup**: two files, each containing `local t = {}` / `function t:m() end` / `t:m()`.
- **Steps**: rename `t:m` to `n` in the first file.
- **Expected**: **no** conflicts dialog; the first file renames and the second is untouched.
- **Result**: ⬜ Pass / ⬜ Fail

## 4. No regression

### Scenario 4.1: The dotted form is unchanged
- **Setup**: `local M = {}` / `function M.run() end` / `M.run()`.
- **Steps**: rename `run` from the declaration, then repeat from the call site.
- **Expected**: both rename all sites, exactly as before this feature.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 4.2: In-place rename still starts for a local
- **Setup**: `local counter = 0` / `print(counter)`.
- **Steps**: caret on `counter`; <kbd>Shift+F6</kbd>.
- **Expected**: the **in-place template** starts (a red box in the editor), not a dialog — the
  colon-method work must not have put a second handler in the registry's map.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 4.3: Label rename still works
- **Setup**: `::retry::` / `goto retry`.
- **Steps**: rename the label.
- **Expected**: unchanged behaviour (REFACT-04).
- **Result**: ⬜ Pass / ⬜ Fail
