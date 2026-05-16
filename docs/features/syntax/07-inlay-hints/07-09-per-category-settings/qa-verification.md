---
folders:
  - "[[features/syntax/07-inlay-hints/07-09-per-category-settings/requirements|requirements]]"
title: QA Verification
---
# QA Verification: SYNTAX-07-09 Per-Category Settings

## Overview
This document provides verification instructions for the SYNTAX-07-09 Per-Category Settings feature, which allows users to customize inlay hint behavior in the Lunar Lua plugin.

## Prerequisites
- IntelliJ IDEA (or compatible JetBrains IDE) with Lunar plugin installed
- A Lua project with sample files containing:
  - Local variable declarations
  - Function definitions with parameters
  - Function calls with multiple arguments
  - LuaCATS type annotations

---

## Test Cases

### TC-01: Settings Page Access
**Objective**: Verify the settings page is accessible and properly integrated.

**Steps**:
1. Open IntelliJ IDEA
2. Navigate to `File` → `Settings` (or `IntelliJ IDEA` → `Preferences` on macOS)
3. Navigate to `Editor` → `Inlay Hints`

**Expected Result**:
- "Lua" entries appear in three groups:
  - **Parameter names**: Simple checkbox (no expand arrow)
  - **Types**: Expandable entry (with arrow)
  - **Method chains**: Expandable entry (with arrow)
- Expanding **Types > Lua** shows a "Settings" link that opens a "Performance" section with a threshold input field.
- The "Settings" UI loads without errors.

---

### TC-02: Local Variable Type Hints Toggle
**Objective**: Verify local variable type hints can be enabled/disabled.

**Precondition**: A Lua file is open with the following code:
```lua
local x = 42
local name = "hello"
```

**Steps**:
1. Navigate to `Editor` → `Inlay Hints` → `Types` → `Lua`
2. Ensure "Local variable types" is **checked**
3. Observe the editor - hints should show `: number` and `: string`
4. Open Settings and **uncheck** "Local variable types"
5. Click "Apply"

**Expected Result**:
- When checked: Type hints appear after `x` and `name`
- When unchecked: Type hints disappear immediately

---

### TC-03: Parameter Name Hints Toggle
**Objective**: Verify parameter name hints can be enabled/disabled.

**Precondition**: A Lua file is open with the following code:
```lua
local function move(posX, posY) end
move(10, 20)
```

**Steps**:
1. Navigate to `Editor` → `Inlay Hints` → `Parameter names`
2. Ensure **Lua** is **checked**
3. Observe the editor - hints should show `posX:` and `posY:` before arguments
4. Open Settings and **uncheck** **Lua**
5. Click "Apply"

**Expected Result**:
- When checked: Parameter name hints appear before `10` and `20`
- When unchecked: Parameter name hints disappear immediately

---

### TC-04: Return Type Hints Toggle
**Objective**: Verify return type hints can be enabled/disabled.

**Precondition**: A Lua file is open with the following code:
```lua
local function double(n)
    return n * 2
end
```

**Steps**:
1. Navigate to `Editor` → `Inlay Hints` → `Types` → `Lua`
2. Ensure "Return types" is **unchecked** (default)
3. Observe the editor after `()` - no return type hint should appear
4. Open Settings and **check** "Return types"
5. Click "Apply"

**Expected Result**:
- When unchecked: No return type hint after function declaration
- When checked: Return type hint (e.g., `: number`) appears after `()`

---

### TC-05: Respect Annotations Toggle
**Objective**: Verify the annotation suppression setting works.

**Precondition**: A Lua file is open with the following code:
```lua
---@type number
local x = 42

local y = 100
```

**Steps**:
1. Navigate to `Editor` → `Inlay Hints` → `Types` → `Lua`
2. Ensure "Respect type annotations" is **checked**
3. Ensure "Local variable types" is **checked**
4. Observe the editor:
   - `x` should have NO type hint (suppressed by `@type` annotation)
   - `y` should show `: number` hint
5. Open Settings and **uncheck** "Respect type annotations"
6. Click "Apply"

**Expected Result**:
- When checked: `x` has no hint, `y` has hint
- When unchecked: Both `x` and `y` show type hints

---

### TC-06: Large File Threshold
**Objective**: Verify large files are skipped based on threshold.

**Precondition**: Create a Lua file with more than 50 lines

**Steps**:
1. Open Settings → Editor → Inlay Hints → Types → Lua
2. Click the "Settings" link (if not automatically showing settings)
3. Set "Large file threshold" to **10**
4. Click "Apply"
5. Open a Lua file with more than 10 lines
6. Observe inlay hints
7. Change threshold to **10000**
8. Click "Apply"

**Expected Result**:
- With threshold 10: No inlay hints appear in files with >10 lines
- With threshold 10000: Inlay hints appear normally

---

### TC-07: Settings Persistence
**Objective**: Verify settings persist across IDE restarts.

**Steps**:
1. Open Settings → Editor → Inlay Hints
2. Make several changes:
   - Under **Types > Lua**, uncheck "Local variable types"
   - Under **Types > Lua**, check "Return types"
   - Under **Types > Lua > Settings**, change threshold to 5000
   - Under **Parameter names**, uncheck **Lua**
3. Click "OK" to close settings
4. Restart IntelliJ IDEA
5. Reopen Settings → Editor → Inlay Hints

**Expected Result**:
- All previously changed settings are preserved
- "Local variable types" remains unchecked
- "Return types" remains checked
- Threshold shows 5000
- **Lua** under **Parameter names** remains unchecked

---

### TC-08: Restore Defaults (Manual)
**Objective**: Verify manual reset to defaults works.

**Steps**:
1. Open Settings → Editor → Inlay Hints
2. Change all settings to non-default values:
   - Uncheck all hint categories across all groups
   - Set threshold to 500
3. Click "Cancel" (to discard changes)
4. Reopen Settings → Editor → Inlay Hints

**Expected Result**:
- Settings return to their previously saved values

**Alternative for Apply-then-Reset**:
1. Make changes and click "Apply"
2. Manually reset each setting to default:
   - Types > Lua: Check "Local variable types", Uncheck "Return types", Check "Respect type annotations"
   - Parameter names > Lua: Check
   - Method chains > Lua: Check
   - Performance: Set threshold to 10000
3. Click "OK"

**Expected Result**:
- Settings are restored to defaults

---

### TC-09: Live Editor Updates
**Objective**: Verify editor refreshes when settings change.

**Precondition**: A Lua file is open and visible

**Steps**:
1. Open Settings → Editor → Inlay Hints
2. Ensure some hints are visible in the editor
3. Toggle "Local variable types" multiple times
4. Click "Apply" after each toggle

**Expected Result**:
- Editor updates immediately after each "Apply"
- No need to close and reopen the file

---

### TC-10: Multiple Projects
**Objective**: Verify settings work across multiple projects.

**Steps**:
1. Open Project A with a Lua file
2. Change settings (e.g., disable local variable hints)
3. Open Project B with a Lua file
4. Observe the editor in Project B

**Expected Result**:
- Settings are global (application-level) and apply to all projects
- Project B shows the same behavior as Project A

---

## Regression Tests

### RT-01: Existing Inlay Hints Functionality
**Objective**: Ensure existing hint functionality still works.

**Steps**:
1. Enable all hint categories
2. Open a complex Lua file with:
   - Multiple local variables
   - Function calls with named parameters
   - Functions with return values
   - Colon method calls (self parameter)
3. Verify all hints display correctly

**Expected Result**:
- All existing hint functionality works as before
- No regressions in hint accuracy or placement

---

## Known Limitations

1. **Method Chaining Hints**: The "Show method chaining hints" setting is implemented but the underlying feature (SYNTAX-07-07) is not yet complete. The setting will have no visible effect until that feature is implemented.

2. **Restore Defaults Button**: There is no single "Restore Defaults" button in the UI. Users must manually reset each setting to its default value.

---

## Sign-Off Checklist

| Item | Verified By | Date |
|------|-------------|------|
| TC-01: Settings Page Access | | |
| TC-02: Local Variable Type Hints Toggle | | |
| TC-03: Parameter Name Hints Toggle | | |
| TC-04: Return Type Hints Toggle | | |
| TC-05: Respect Annotations Toggle | | |
| TC-06: Large File Threshold | | |
| TC-07: Settings Persistence | | |
| TC-08: Restore Defaults (Manual) | | |
| TC-09: Live Editor Updates | | |
| TC-10: Multiple Projects | | |
| RT-01: Existing Functionality | | |

---

## Related Documents
- [Requirements](requirements.md)
- [Design](design.md)
- [Implementation Plan](implementation-plan.md)
