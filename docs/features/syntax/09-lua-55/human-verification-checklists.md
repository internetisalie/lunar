---
id: "SYNTAX-09-QA"
title: "Verification Checklist"
type: "qa"
parent_id: "SYNTAX-09"
folders:
  - "[[features/syntax/09-lua-55/requirements|requirements]]"
---

# Human Verification Checklist: SYNTAX-09

## 1. Language Level Setting
**Purpose**: Verify Lua 5.5 is selectable in the IDE.
- [ ] Open the IDE Sandbox (`./gradlew runIde`).
- [ ] Open Project Settings → Languages & Frameworks → Lunar.
- [ ] Verify that Platform `Standard` Version `5.5` is available in the dropdown.
- [ ] Verify that selecting `5.5` updates the Language Level label to `5.5`.

## 2. Global Variable Syntax (5.5 Mode)
**Purpose**: Verify `global` syntax parses without errors.
- [ ] Set project language level to `5.5`.
- [ ] Create `global_test.lua`.
- [ ] Enter:
  ```lua
  global x = 10
  global y
  y = 20
  print(x, y)
  ```
- [ ] Verify no red squiggly lines (syntax errors) appear.
- [ ] Verify `x` and `y` are resolved as globals (Ctrl+Click / Navigate to Declaration works on `x` in `print(x)`).

## 3. Global Function Syntax (5.5 Mode)
**Purpose**: Verify `global function` parses without errors.
- [ ] Enter:
  ```lua
  global function greet(name)
      return "Hello " .. name
  end
  ```
- [ ] Verify syntax highlighting and code folding work as expected.

## 4. Backward Compatibility Inspection
**Purpose**: Verify `global` warns at language level < 5.5.
- [ ] Change project language level to `5.4`.
- [ ] Verify a warning appears under `global` in `global x = 10`.
- [ ] Press `Alt+Enter` on the warning and execute "Set project language level to 5.5".
- [ ] Verify the warning disappears and language level is now 5.5.

## 5. Integer Division Not Broken
**Purpose**: Verify `//` still works as integer division on all language levels.
- [ ] At language levels `5.3`, `5.4`, and `5.5`:
- [ ] Enter `local x = 10 // 3` and verify it parses without error.
- [ ] Verify no "comment" or "global" warning appears on the `//` token.
