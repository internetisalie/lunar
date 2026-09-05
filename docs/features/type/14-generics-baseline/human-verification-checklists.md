---
id: "TYPE-14-CHECKLIST"
title: "14: Verification checklists"
type: "qa"
parent_id: "TYPE-14"
folders:
  - "[[features/type/requirements|requirements]]"
---

# Verification Checklists: TYPE-14 — Generics baseline

Run in GoLand on the builder VM per the `verify-in-ide` skill, with the plugin jar from the
branch under test hot-swapped in. Each scenario starts from a fresh project containing only the
files it names. Record the result inline.

## 1. Generic functions

### Scenario 1.1: Nested generic return infers at the call site
- **Setup**: `a.lua`:
  ```lua
  ---@generic T
  ---@param xs T[]
  ---@return T
  local function first(xs) return xs[1] end
  ---@type string[]
  local names = {}
  local n = first(names)
  ```
- **Steps**:
  1. Hover `n`.
  2. Type `n:` and invoke completion.
- **Expected**: the hover shows `string`; completion offers the string-library methods (`upper`,
  `sub`, …). No inspection highlight anywhere in the file.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 1.2: A cross-file generic global raises nothing and infers
- **Setup**: `lib.lua` with `---@generic T ---@param v T ---@return T` `function ident(v) return v end`;
  `main.lua` with `local s = ident("x")` and `local k = ident(42)`.
- **Steps**: open `main.lua`; hover `s`, hover `k`; look at the inspection gutter.
- **Expected**: `string`, `number`; no "is not assignable to T" highlight on either argument.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 1.3: A generic method on an annotated receiver
- **Setup**: the DR-01 fixture D (`---@class Box`, `function Box:echo(v)` with `@generic`,
  `---@type Box local b`, `local r = b:echo("x")`).
- **Steps**: hover `r`; check the gutter.
- **Expected**: `string`; no highlight on `:echo` or on `"x"`.
- **Result**: ⬜ Pass / ⬜ Fail

## 2. Constraints

### Scenario 2.1: The bound is usable in the body and checked at the call
- **Setup**:
  ```lua
  ---@class Base
  ---@field k string
  local Base = {}
  ---@generic T : Base
  ---@param p T
  local function f(p)
    local m = p.k
  end
  f(1)
  ```
- **Steps**: inside `f`, type `p.` and invoke completion; hover `m`; look at `f(1)`.
- **Expected**: completion offers `k`; `m` hovers `string`; `1` carries an assignability
  highlight whose tooltip names `number` and `Base`.
- **Result**: ⬜ Pass / ⬜ Fail

## 3. Generic classes

### Scenario 3.1: Completion through an instantiated class
- **Setup**:
  ```lua
  ---@class Box<T>
  ---@field value T
  ---@field items T[]
  local Box = {}
  ---@type Box<string>
  local b
  ```
- **Steps**: type `b.` and complete; select `value`; hover it. Type `b.items[1]:` and complete.
- **Expected**: completion offers `value` and `items`; `b.value` hovers `string`;
  `b.items[1]:` offers string methods.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 3.2: Go to Class finds a generic class by its head
- **Setup**: scenario 3.1's file.
- **Steps**: Navigate → Class, type `Box`.
- **Expected**: `Box` is listed (not `Box<T>`); selecting it lands on the `---@class` line.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 3.3: Inheritance through a parameterized parent (BUG-420)
- **Setup**:
  ```lua
  ---@class PBase<T>
  ---@field inherited T
  local PBase = {}
  ---@class Kid : PBase<string>
  ---@field own number
  local Kid = {}
  ---@type Kid
  local kid
  ```
- **Steps**: type `kid.` and complete; hover `kid.inherited`.
- **Expected**: completion offers `inherited` and `own`; `inherited` hovers `string`.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 3.4: A generic constructor returns an instantiation
- **Setup**: scenario 3.1's class plus
  ```lua
  ---@generic T
  ---@param v T
  ---@return Box<T>
  local function make(v) return { value = v } end
  local made = make(1)
  ```
- **Steps**: hover `made`; type `made.value` and hover it. Enable type inlay hints and read the
  hint on `made`.
- **Expected**: `Box<number>`; `number`; the inlay reads `: Box<number>`.
- **Result**: ⬜ Pass / ⬜ Fail

## 4. Grammar

### Scenario 4.1: A nested type argument is not prose
- **Setup**: `---@type table<string, Widget|nil>` `local q` with `---@class Widget` declared above.
- **Steps**: place the caret on `Widget` inside the angle brackets; Go to Declaration.
- **Expected**: the caret lands on the `---@class Widget` line, and the comment shows no
  description-coloured tail after `table`.
- **Result**: ⬜ Pass / ⬜ Fail

## 5. Regression

### Scenario 5.1: The TYPE-05 baseline is unchanged
- **Setup**: the `identity` example from `docs/features/type/05-generics-support/requirements.md` §5.1.
- **Steps**: hover `s` and `n`.
- **Expected**: `string`, `number`; no highlights.
- **Result**: ⬜ Pass / ⬜ Fail
