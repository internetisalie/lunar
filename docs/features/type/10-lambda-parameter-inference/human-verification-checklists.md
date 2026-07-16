---
id: "TYPE-10-CHECKLIST"
title: "Verification Checklists"
type: "qa"
parent_id: "TYPE-10"
folders:
  - "[[features/type/10-lambda-parameter-inference/requirements|requirements]]"
---

# Verification Checklists: TYPE-10 — Expected-Type → Lambda-Parameter Inference

Manual, human-run scenarios in a real GoLand (per the `verify-in-ide` skill). These confirm
the propagated type reaches a **user-visible** surface — the real-flow DoD that engine-level
`getValueType` tests cannot give.

## 1. Redis callback typing

### Scenario 1.1: `register_function` typed callback params (completion)
- **Setup**: a project with a Redis target (interpreter/target = Redis 7), a `.lua` file open.
- **Steps**:
  1. Type `redis.register_function('f', function(keys, args) return keys[1] end)`.
  2. Place the caret after `keys[1]` and type `:` to trigger method completion, e.g.
     `local c = keys[1]:` .
  3. Invoke completion (Ctrl+Space).
- **Expected**: string methods (e.g. `sub`, `upper`, `len`, `byte`) are offered — proving
  `keys` is `string[]` and `keys[1]` is `string` (re-enables REDIS-05 TC-STUB-1).
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 1.2: `register_function` callback params via inferred-type surface
- **Setup**: same as 1.1.
- **Steps**:
  1. With `function(keys, args) … end` present, hover / read the inferred-type inlay on
     `keys` (or Quick Documentation on `keys`).
- **Expected**: `keys` shows type `string[]` (and `args` `string[]`), not `undefined`/`any`.
- **Result**: ⬜ Pass / ⬜ Fail

## 2. Precedence and no-regression

### Scenario 2.1: Direct `---@param` still wins
- **Setup**: any target; a `.lua` file.
- **Steps**:
  1. Type a callee `---@param cb fun(x: string)` `\n` `local function run(cb) cb('a') end`.
  2. Type `run(` then a lambda annotated `---@param x number` on its own line, i.e.
     `run(--[[@param x number]] function(x) return x end)` (or the block-comment `---@param`
     form your fixture supports).
  3. Hover / read the inferred type of `x` inside the lambda.
- **Expected**: `x` is `number` (the direct `---@param`), NOT `string`.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 2.2: Untyped callback slot stays unresolved (no spurious narrowing)
- **Setup**: any target; a `.lua` file.
- **Steps**:
  1. Type `local function run(cb) cb(1) end` (note: `cb` has NO `---@param`).
  2. Type `run(function(v) return v end)`.
  3. Hover / read the inferred type of `v`.
- **Expected**: `v` is `undefined`/`any` — the feature does not narrow untyped slots.
- **Result**: ⬜ Pass / ⬜ Fail
