---
id: "MAINT-26-CHECKLIST"
title: "Verification Checklists"
type: "qa"
parent_id: "MAINT-26"
folders:
  - "[[features/maint/26-luacheck-pipeline/requirements|requirements]]"
---

# Verification Checklists: MAINT-26 — Luacheck Pipeline Correctness

Manual, human-run scenarios (live GoLand, real luacheck) that automated light-fixture tests
cannot fully cover. Requires a configured luacheck tool and the LuaCheck inspection enabled.

## 1. Editor-accurate offsets (stdin)

### Scenario 1.1: Unsaved-buffer warning lands on the right token
- **Setup**: Open a Lua file; do **not** save. Type `local unusedVar = 1` on a fresh line.
- **Steps**:
  1. Wait for the LuaCheck annotator pass.
- **Expected**: The unused-variable warning underlines `unusedVar` exactly (not shifted, not the
  whole line), even though the buffer was never written to disk.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 1.2: Editing more lines than on disk does not break the pass
- **Setup**: Save a 1-line file, then paste 20 more lines without saving.
- **Steps**:
  1. Introduce a luacheck warning on line 15; wait for the pass.
- **Expected**: The warning appears on line 15; no error balloon, no "annotator threw" in the log
  (previously an IndexOutOfBoundsException killed the pass).
- **Result**: ⬜ Pass / ⬜ Fail

## 2. Failure surfacing

### Scenario 2.1: Missing / broken luacheck is visible
- **Setup**: Point the luacheck tool binding at a non-existent path (or rename the binary).
- **Steps**:
  1. Open a Lua file with an obvious lint issue; wait for the pass.
- **Expected**: A single file-wide WARNING annotation ("Could not execute luacheck") **and** the
  existing editor banner offering "Configure toolchain". The file does **not** appear clean.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 2.2: A bad `.luacheckrc` (fatal exit) surfaces
- **Setup**: Add an invalid `--std` or malformed `.luacheckrc` so luacheck exits ≥ 2.
- **Steps**:
  1. Open a Lua file; wait for the pass.
- **Expected**: One file-wide WARNING with the luacheck stderr message; not silently clean.
- **Result**: ⬜ Pass / ⬜ Fail

## 3. Suppression & stdlib

### Scenario 3.1: Named `enable` does not close unrelated blocks
- **Setup**: A file with `---@diagnostic disable: undefined-global`, then a `---@diagnostic disable: unused`,
  then `---@diagnostic enable: undefined-global`, with an unused local after the enable.
- **Steps**:
  1. Wait for inspections.
- **Expected**: The unused-local warning is still suppressed after the enable (only the
  `undefined-global` block closed).
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 3.2: Inline ignore is line-scoped; `arg` is not flagged (5.1)
- **Setup**: Set the target to Lua 5.1. Line 1: `foo() -- luacheck: ignore`. Line 2: `bar()`
  (both undeclared). Elsewhere: reference `arg`.
- **Steps**:
  1. Wait for inspections.
- **Expected**: Line 1 `foo` is suppressed; line 2 `bar` **is** flagged; `arg` is **not** flagged
  as an undeclared global.
- **Result**: ⬜ Pass / ⬜ Fail
