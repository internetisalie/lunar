---
id: "TARGET-07-CHECKLIST"
title: "Verification Checklists"
type: "qa"
parent_id: "TARGET-07"
folders:
  - "[[features/target/07-lua-5.5-stdlib-stubs/requirements|requirements]]"
---

# Verification Checklists: TARGET-07 — Lua 5.5 Standard-Library Stubs

Live-IDE spot-check. Optional — the automated resolution test (implementation-plan Phase 3) is
the primary gate; these confirm the same behavior surfaces in the real UI.

## 1. Completion & Resolution

### Scenario 1.1: `table.create` completes at 5.5
- **Setup**: A project whose Lunar target is `Standard 5.5`; a `.lua` file.
- **Steps**:
  1. Type `table.cr` and invoke basic completion (Ctrl+Space).
  2. Accept `create`, then hover / Ctrl+hover the symbol.
- **Expected**: `create` appears in the completion popup; hover shows the LuaCATS signature
  `fun(nseq: integer, nrec?: integer): table` from `lua-5.5/table.lua`.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 1.2: Go to Declaration lands in the 5.5 stub
- **Setup**: 5.5 target; file containing `table.create(4)`.
- **Steps**:
  1. Ctrl+B (Go to Declaration) on `create`.
- **Expected**: navigates into `runtime/standard/lua-5.5/table.lua` at
  `function table.create(nseq, nrec)`.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 1.3: Version gating
- **Setup**: Switch the same project's target to `Standard 5.4`.
- **Steps**:
  1. In a file with `table.create(4)`, invoke completion for `table.cr` and Ctrl+B on `create`.
- **Expected**: `create` is **not** offered / does not resolve (no navigation target); only
  5.4 `table.*` members complete.
- **Result**: ⬜ Pass / ⬜ Fail

## 2. Baseline Parity

### Scenario 2.1: 5.4 symbols still work at 5.5
- **Setup**: 5.5 target; a `.lua` file.
- **Steps**:
  1. Complete and resolve `table.insert`, `string.format`, `math.floor`, `os.time`.
- **Expected**: all resolve into the corresponding `lua-5.5/*.lua` file — no regression from
  the 5.4 experience.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 2.2: `_VERSION` reads 5.5
- **Setup**: 5.5 target.
- **Steps**:
  1. Hover `_VERSION`.
- **Expected**: doc/type shows the value `"Lua 5.5"`.
- **Result**: ⬜ Pass / ⬜ Fail
