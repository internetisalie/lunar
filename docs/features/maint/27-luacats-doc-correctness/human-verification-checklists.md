---
id: "MAINT-27-CHECKLIST"
title: "Verification Checklists"
type: "qa"
parent_id: "MAINT-27"
folders:
  - "[[features/maint/27-luacats-doc-correctness/requirements|requirements]]"
---

# Verification Checklists: MAINT-27 — LuaCATS Doc & Lexer Correctness

Manual, VNC-driven GoLand checks (per the `verify-in-ide` skill) for the doc-popup rendering that
unit `renderDoc` string assertions cannot fully confirm.

## 1. Documentation Popup

### Scenario 1.1: Structured type renders escaped, not broken
- **Setup**: a `.lua` file with `---@type table<string, integer>` above `local m = {}`.
- **Steps**:
  1. Place the caret on `m` and press Ctrl+Q (Quick Documentation).
- **Expected**: the popup shows `local m : table<string, integer>` with the angle brackets visible
  as text (not swallowed / not a broken link); no missing or garbled markup.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 1.2: `@type` uses `local :`, not `class`
- **Setup**: `---@type Player` above `local p = {}` (with a `---@class Player` defined elsewhere).
- **Steps**:
  1. Ctrl+Q on `p`.
- **Expected**: definition line reads `local p : Player` with `Player` a working navigation link;
  the word `class` does not appear.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 1.3: Grandparent inherited fields
- **Setup**: `---@class A` + `---@field id integer`; `---@class B : A`; `---@class C : B`.
- **Steps**:
  1. Ctrl+Q on `C`.
- **Expected**: an "Inherited Fields:" section lists `id`.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 1.4: Union-alias values
- **Setup**: `---@alias Mode "r"|"w"`.
- **Steps**:
  1. Ctrl+Q on the alias name.
- **Expected**: a "Values:" section lists `"r"` and `"w"`.
- **Result**: ⬜ Pass / ⬜ Fail

## 2. Lexer / Editor Robustness

### Scenario 2.1: Unclosed backtick does not corrupt following lines
- **Setup**: a comment block where one line has an unterminated backtick, e.g.
  ``---@param x `oops`` on its own line, followed by `---@param y number`.
- **Steps**:
  1. Observe syntax highlighting of the lines after the unclosed backtick.
- **Expected**: the following `@param y number` line highlights normally (tag/name/type colors),
  not as one run of string/code color.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 2.2: Unicode class name
- **Setup**: `---@class 名前` above `local x = {}`.
- **Steps**:
  1. Observe highlighting; place caret on `名前` and invoke Go to Class for `名前`.
- **Expected**: `名前` highlights as a type name and is navigable (lexes as one name token).
- **Result**: ⬜ Pass / ⬜ Fail
