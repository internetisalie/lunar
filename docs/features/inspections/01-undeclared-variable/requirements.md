---
id: INSP-01
title: "01: Undeclared Variable"
type: feature
parent_id: INSP
status: "done"
priority: "medium"
folders:
  - "[[features/inspections/requirements|requirements]]"
---

# Undeclared Variable Requirements (`INSP-01`)

Highlights variables that are *used* (read) but have no visible declaration in the current
scope, the file, the project, or the configured standard library.

## Scope

- **In Scope**:
    - Highlighting `LuaNameRef` identifiers in read position that do not resolve to a local,
      parameter, loop variable, file/project global, or standard-library symbol.
    - Recognition of standard Lua globals per language level (Lua 5.1–5.4).
    - Recognition of user-defined globals across files in the project (via the existing
      resolution path in `LuaNameReference`).
    - Correct handling of variables used *before* their `local` declaration (Lua early
      binding).
    - A project-level "Additional Globals" allowlist.
    - Inline suppression via `---@diagnostic` and `-- luacheck: ignore` comments.
- **Out of Scope**:
    - Symbols added dynamically to the global table (e.g., `_G["myVar"] = 1`).
    - Symbols provided by C-based host environments without Lua stubs (covered by the
      Additional Globals allowlist instead).
    - Flagging *write* targets (`x = 1`) as undeclared — implicit global creation is the
      separate INSP-05 inspection, not this one.

## Requirements Table

| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :---: | :--- | :--- |
| `INSP-01-01` | **Resolve to Local** | **M** | Full | A read of a name that resolves to a local / parameter / loop variable in an accessible scope is not flagged. |
| `INSP-01-02` | **Resolve to Global** | **M** | Full | A read that resolves to a file-level or project-wide global definition is not flagged. |
| `INSP-01-03` | **Standard Library Support** | **M** | Full | Standard Lua globals for the project's language level (5.1–5.4) are never flagged. |
| `INSP-01-04` | **Unresolved Highlighting** | **M** | Full | A read that resolves to nothing is highlighted with a configurable severity (default `WARNING`) and the message `Undeclared variable '<name>'`. |
| `INSP-01-05` | **Early Binding** | **M** | Full | A read appearing textually before the `local` that declares the name is flagged (it does not bind to the later local). |
| `INSP-01-06` | **Write Target Exclusion** | **M** | Full | A simple assignment target (`name = ...`) is never flagged; the base of an indexed write (`name.field = ...`) is treated as a read. (Func-name heads are not flagged at all — see Deferred: the dotted-base case `function undeclaredTable.method()`.) |
| `INSP-01-07` | **Additional Globals** | **S** | Full | Names in the project's "Additional Globals" allowlist are never flagged (via `state.additionalGlobals`, populated by the quick fix; settings-panel UI deferred — see Deferred). |
| `INSP-01-08` | **Inline Suppression** | **S** | Full | `---@diagnostic disable[-next-line\|-line]: undefined-global` and `-- luacheck: ignore [names]` comments suppress the warning over their defined scope. |

## Test Cases

### TC-01: Simple Local Resolution (INSP-01-01)
- **Input**:
  ```lua
  local x = 10
  print(x)
  ```
- **Action**: Run inspection on the file.
- **Output**: No warning on `x` (line 2). No warning on `print`.

### TC-02: Undeclared Global (INSP-01-04)
- **Input**:
  ```lua
  print(undeclaredVar)
  ```
- **Action**: Run inspection.
- **Output**: One `WARNING` highlight on `undeclaredVar` with message
  `Undeclared variable 'undeclaredVar'`.

### TC-03: Used Before Local Declaration (INSP-01-05)
- **Input**:
  ```lua
  print(x)
  local x = 10
  ```
- **Action**: Run inspection.
- **Output**: One warning on the `x` of line 1; no warning on the `x` of line 2 (declaration
  site).

### TC-04: Standard Library Global (INSP-01-03)
- **Input** (project language level = any of 5.1–5.4):
  ```lua
  print(math.abs(-10))
  ```
- **Action**: Run inspection.
- **Output**: No warning on `print` or `math`.

### TC-05: Cross-File Global (INSP-01-02)
- **Input**: file `a.lua` contains `function Helper() end`; file `b.lua` contains
  ```lua
  Helper()
  ```
- **Action**: Run inspection on `b.lua` with both files indexed.
- **Output**: No warning on `Helper`.

### TC-06: Write Target Excluded (INSP-01-06)
- **Input**:
  ```lua
  newGlobal = 5
  existing.field = 6
  ```
- **Action**: Run inspection (no prior declaration of `newGlobal` or `existing`).
- **Output**: No warning on `newGlobal` (write target). One warning on `existing` (read of
  the index base). No warning on `field`.

### TC-07: Additional Globals Allowlist (INSP-01-07)
- **Input**: project setting Additional Globals = `["love"]`; file:
  ```lua
  love.graphics.print("hi")
  ```
- **Action**: Run inspection.
- **Output**: No warning on `love`.

### TC-08: Diagnostic Suppression (INSP-01-08)
- **Input**:
  ```lua
  ---@diagnostic disable-next-line: undefined-global
  print(mysteryGlobal)
  ```
- **Action**: Run inspection.
- **Output**: No warning on `mysteryGlobal`.

### TC-09: Luacheck Suppression (INSP-01-08)
- **Input**:
  ```lua
  print(mysteryGlobal) -- luacheck: ignore mysteryGlobal
  ```
- **Action**: Run inspection.
- **Output**: No warning on `mysteryGlobal`.

### TC-11: Function-Name Head (INSP-01-06)
- **Input**:
  ```lua
  function PlainGlobal() end
  function undeclaredTable.method() end
  ```
- **Action**: Run inspection (no prior declaration of `undeclaredTable`).
- **Output**: No warning on `PlainGlobal` (plain global function declaration). One warning on
  `undeclaredTable` (read of the table being indexed). No warning on `method`.

### TC-10: Underscore-Prefixed Globals (INSP-01-04)
- **Input** (default project settings, `suppressUnderscorePrefixedGlobals = true`):
  ```lua
  print(_ENV_PLACEHOLDER)
  ```
- **Action**: Run inspection.
- **Output**: No warning on `_ENV_PLACEHOLDER` (suppressed by underscore-prefix setting).

## Deferred (Future Work)

- **Additional-Globals settings UI (INSP-01-07):** the allowlist is fully functional via
  `state.additionalGlobals` (the "Add to globals" quick fix writes it), but a dedicated list
  editor in the Lua settings panel is deferred to avoid scope creep.
- **Dotted func-name head flagging (TC-11 negative half):** a func-name head is never flagged.
  The resolver resolves a func-name head only to itself in every case (even when its base table
  is genuinely declared), so there is no signal to distinguish a truly-undeclared dotted base
  (`function undeclaredTable.method()`) from a declared one — flagging would false-positive on
  valid `function declared.m()`. Deferred until the resolver links a func-name head's base to
  its declaration.
