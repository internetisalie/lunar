---
id: "INSP-03-CHECKLIST"
title: "Verification Checklists"
type: "qa"
status: "done"
parent_id: "INSP-03"
folders:
  - "[[features/inspections/03-type-mismatch/requirements|requirements]]"
---

# Verification Checklists: INSP-03 — Type Mismatch

Run in a sandbox IDE (`./gradlew runIde`) on a `.lua` file. The relevant inspections
(`Type assignability`, `Return type mismatch`) are enabled by default.

## 1. Assignment & Union (INSP-03-01 / -02)

### Scenario 1.1: Scalar mismatch is flagged
- **Setup**: empty `.lua` file.
- **Steps**:
  1. Type `---@type string` then `local x = 42`.
- **Expected**: `42` is highlighted with a message that `number` is not assignable to `string`.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 1.2: Union member match is NOT flagged
- **Steps**:
  1. Type `---@type string|number` then `local x = 42`.
- **Expected**: no warning on `42`.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 1.3: Union miss names the closest match
- **Steps**:
  1. Declare `---@class Point` / `---@field x number` / `---@field y number`, similarly a `Color`,
     then `---@type Point|Color` / `local p = { x = 1 }`.
- **Expected**: warning names closest match `Point` and the missing field `y`.
- **Result**: ⬜ Pass / ⬜ Fail

## 2. Returns & Arguments (INSP-03-03 / -04)

### Scenario 2.1: Return mismatch is flagged
- **Steps**:
  1. Type `---@return number` / `local function f() return "x" end`.
- **Expected**: the returned `"x"` is flagged as not assignable to `number`.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 2.2: Matching return is clean
- **Steps**:
  1. Type `---@return number` / `local function f() return 42 end`.
- **Expected**: no warning.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 2.3: Argument arity is flagged
- **Steps**:
  1. Type `---@param a number` / `---@param b number` / `local function add(a,b) end`, then `add(1)`.
- **Expected**: a `Too few arguments` warning on the call.
- **Result**: ⬜ Pass / ⬜ Fail

## 3. No false positives

### Scenario 3.1: Untyped / unknown operands are silent
- **Steps**:
  1. Type `local x = someUndefinedThing()` with no annotations.
- **Expected**: no type-mismatch warning (engine suppresses `any`/`unknown`).
- **Result**: ⬜ Pass / ⬜ Fail
