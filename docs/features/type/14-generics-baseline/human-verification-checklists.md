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

**Run on 2026-11-07.**
- **Setup:** GoLand 2026.2.3 via `runIde` on the libvirt builder, with the plugin built from
  `6f08f293`. The log line `Loaded custom plugins: lunar` was confirmed for this run.
- **Deviation:** all scenarios share one project (`t14proj`), with one file per scenario. Class
  names were chosen so they do not collide.
- **How types were read:** from type inlay hints rather than hover. The hover popup did not
  appear under `xdotool`.
- **Environment:** the project has no Lua runtime configured, so string-library completion is not
  checkable. A control confirmed this.

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
- **Result** (2026-11-07, HEAD `6f08f293`): **Fail, partly.** The type checks pass: the `n` inlay reads `: string`. Two expectations do not:
  - **Highlights.** As written, the file has 6 errors. All come from BUG-481's `local names = {}`: `{ ... } is not assignable to string[]`, and from that, `{ ... } is not assignable to T[]`. With `local names`, the file has no error.
  - **Completion.** `n:` gave "No suggestions", and so did the control (`local c = "x"` `c:`). The project has no Lua runtime ("No usable Lua runtime" banner), so string-library completion could not be checked here.

### Scenario 1.2: A cross-file generic global raises nothing and infers
- **Setup**: `lib.lua` with `---@generic T ---@param v T ---@return T` `function ident(v) return v end`;
  `main.lua` with `local s = ident("x")` and `local k = ident(42)`.
- **Steps**: open `main.lua`; hover `s`, hover `k`; look at the inspection gutter.
- **Expected**: `string`, `number`; no "is not assignable to T" highlight on either argument.
- **Result** (2026-11-07, HEAD `6f08f293`): **Pass.** The inlays read `s: string` and `k: number`. The only problems are two *Unused local* warnings.

### Scenario 1.3: A generic method on an annotated receiver
- **Setup**: the DR-01 fixture D (`---@class Box`, `function Box:echo(v)` with `@generic`,
  `---@type Box local b`, `local r = b:echo("x")`).
- **Steps**: hover `r`; check the gutter.
- **Expected**: `string`; no highlight on `:echo` or on `"x"`.
- **Result** (2026-11-07, HEAD `6f08f293`): **Pass.** The inlay reads `r: string`. The only problem is *Unused local 'r'*. The class is named `Box13` so it does not collide with section 3's `Box`.

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
- **Expected**: completion offers `k`; `m` hovers `string`; `f(1)` carries no highlight, the same
  as a plain `---@param p Base` (requirements case 19(c): a primitive satisfies a class demand).
- **Result** (2026-11-07, HEAD `6f08f293`): **Pass.**
  - Completion after `p.` inside `f` offers `k  string`, and the `m` inlay reads `: string`.
  - `f(1)` carries no highlight.
  - Typing `p.` logged a plugin error, filed as [[BUG-36]].

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
- **Result** (2026-11-07, HEAD `6f08f293`): **Pass, partly.** Completion after `b.` offers `items  string[]` and `value  string`, and the `b.value` inlay reads `: string`. Not established:
  - completion after `b.items[1]:`, which needs a Lua runtime (see 1.1);
  - the type of `b.items[1]`, which shows no inlay; hover was not driven successfully.

### Scenario 3.2: Go to Class finds a generic class by its head
- **Setup**: scenario 3.1's file.
- **Steps**: Navigate → Class, type `Box`.
- **Expected**: `Box` is listed (not `Box<T>`); selecting it lands on the `---@class` line.
- **Result** (2026-11-07, HEAD `6f08f293`): **Pass, with a defect.** `Box` is listed, not `Box<T>`, and selecting it lands on the `---@class Box<T>` line. However, `Box` is listed three times, each one landing on line 1, while non-generic `Box13` is listed once. Filed as [[BUG-35]].

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
- **Result** (2026-11-07, HEAD `6f08f293`): **Pass.** Completion after `kid.` offers `inherited  string` and `own  number`, and the `kid.inherited` inlay reads `: string`.

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
- **Result** (2026-11-07, HEAD `6f08f293`): **Fail.** As written, with `return { value = v }`, the inlays read `made: Box<number> | { ... }` and `mv: number | T`. With an empty body they read `Box<number>` and `number`. Filed as [[BUG-34]].

## 4. Grammar

### Scenario 4.1: A nested type argument is not prose
- **Setup**: `---@type table<string, Widget|nil>` `local q` with `---@class Widget` declared above.
- **Steps**: place the caret on `Widget` inside the angle brackets; Go to Declaration.
- **Expected**: the caret lands on the `---@class Widget` line, and the comment shows no
  description-coloured tail after `table`.
- **Result** (2026-11-07, HEAD `6f08f293`): **Pass.** Go to Declaration on `Widget` inside `table<string, Widget|nil>` lands on `1:11`, the `---@class Widget` line. There is no description-coloured tail. The first attempt reported "It's the only usage" and did not move, which was probably a timing issue. Retried, and checked against `---@type Widget|nil` and `table<string, Widget>`, all three land on `1:11`.

## 5. Regression

### Scenario 5.1: The TYPE-05 baseline is unchanged
- **Setup**: the `identity` example from `docs/features/type/05-generics-support/requirements.md` §5.1.
- **Steps**: hover `s` and `n`.
- **Expected**: `string`, `number`; no highlights.
- **Result** (2026-11-07, HEAD `6f08f293`): **Pass.** The inlays read `s: string` and `n: number`. The only problems are two *Unused local* warnings.
