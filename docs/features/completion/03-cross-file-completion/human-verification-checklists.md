---
id: "COMP-03-QA"
title: "Verification Checklists"
type: "qa"
parent_id: "COMP-03"
status: "in_progress"
priority: "high"
folders:
  - "[[features/completion/03-cross-file-completion/requirements|requirements]]"
---

# COMP-03: Human Verification Checklists

## Manual Verification Steps

### Symbols from `require`
- [ ] Create `module.lua` returning a table `{ helper = 1 }`.
- [ ] In `main.lua`, add `local m = require("module")`.
- [ ] Type `m.` and verify `helper` is suggested.

### Project-wide Globals
- [ ] Define `function global_util()` in `other.lua`.
- [ ] In `main.lua` (without require), type `glo` and verify `global_util` appears.

### Auto-import
- [ ] Select `global_util` from the completion list.
- [ ] Verify `local other = require("other")` (or appropriate path) is automatically added if the module returns a value.
- [ ] Verify `require("other")` is added without assignment if it's a pure global declaration.
- [ ] Verify `require("net.utils")` is suggested for `net/utils/init.lua`.

### Circular & Recursive Resolution
- [ ] File A requires B, B requires C.
- [ ] Verify symbols from C are suggested in A.
- [ ] File A requires B, B requires A.
- [ ] Verify completion still works in both files without hanging.

### Visibility
- [ ] Verify symbols starting with `_` in `other.lua` are NOT suggested in `main.lua`.
- [ ] Verify symbols starting with `_` ARE suggested when completing inside the same file.
