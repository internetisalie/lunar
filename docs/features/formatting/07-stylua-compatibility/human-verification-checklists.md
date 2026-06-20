---
id: "FORMAT-07-CHECKLIST"
title: "Verification Checklists — Stylua Compatibility"
type: "qa"
status: "todo"
parent_id: "FORMAT-07"
folders:
  - "[[features/formatting/07-stylua-compatibility/requirements|requirements]]"
---

# Verification Checklists: FORMAT-07 — Stylua Compatibility

## 1. Core Formatting Behavior

### Scenario 1.1: Basic reformat with Stylua
- **Setup**: Sandbox IDE with Stylua ≥0.10.0 installed at `/usr/local/bin/stylua`.
  Tool registered in Lunar settings (Settings → Tools → Stylua).
  Open a `.lua` file with `local x =   1`.
- **Steps**:
  1. Select "Reformat Code" from the Code menu (or press Ctrl+Alt+L).
  2. Observe the file content.
- **Expected**: File content becomes `local x = 1` (formatted by Stylua).
  No error notifications.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 1.2: Fallback to built-in formatter
- **Setup**: Same IDE. Unbind Stylua (Settings → Tools → clear the Stylua binding).
  Open a `.lua` file with `local x =   1`.
- **Steps**:
  1. Reformat code.
- **Expected**: File is formatted by Lunar's built-in formatter (indentation, spacing
  per code style settings). Stylua is not invoked.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 1.3: Stylua with `.stylua.toml`
- **Setup**: Stylua bound. Create a project directory with a `.stylua.toml`:
  ```toml
  indent_type = "Spaces"
  indent_width = 4
  ```
  Open a Lua file in the same directory with `local x=1`.
- **Steps**:
  1. Reformat code.
- **Expected**: File uses 4-space indentation (from `.stylua.toml`).
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 1.4: Non-Lua file unaffected
- **Setup**: Stylua bound. Open a Python file in the same project.
- **Steps**:
  1. Reformat code.
- **Expected**: Python file is formatted by the platform's Python formatter (or the
  default). Stylua is not invoked for the `.py` file.
- **Result**: ⬜ Pass / ⬜ Fail

## 2. Error Handling

### Scenario 2.1: Stylua syntax error
- **Setup**: Stylua bound. Open a `.lua` file with malformed content:
  `local x =`.
- **Steps**:
  1. Reformat code.
- **Expected**: A red BALLOON notification appears with the Stylua error message
  (e.g., "unexpected symbol near '='"). The file content is NOT changed.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 2.2: Stylua binary not found
- **Setup**: Register a Stylua tool at a path that doesn't exist (e.g.,
  `/tmp/nonexistent/stylua`). Bind it. Open a `.lua` file.
- **Steps**:
  1. Reformat code.
- **Expected**: A notification appears: "Could not execute stylua at /tmp/..."
  File content is unchanged. After closing the notification, the file should
  remain editable and functional.
- **Result**: ⬜ Pass / ⬜ Fail

## 3. Settings & Tool Integration

### Scenario 3.1: Project-level binding
- **Setup**: Two projects open. Project A has Stylua bound; Project B does not.
- **Steps**:
  1. In Project A, reformat a `.lua` file.
  2. In Project B, reformat a `.lua` file.
- **Expected**: Project A uses Stylua. Project B uses the built-in formatter.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 3.2: Global binding override
- **Setup**: Stylua bound globally, but project override set to a different (nonexistent)
  Stylua path.
- **Steps**:
  1. Reformat a `.lua` file in the project with the override.
- **Expected**: Error notification about the nonexistent path. If the global binding
  remains valid for other projects, they still use Stylua normally.
- **Result**: ⬜ Pass / ⬜ Fail

## 4. First-Use Notification (FOR-07-05)

### Scenario 4.1: Notification on first format
- **Setup**: Fresh IDE session (or clear `lunar.stylua.firstUse.notified`).
  Stylua bound and valid.
- **Steps**:
  1. Reformat a `.lua` file.
- **Expected**: The file is formatted. A BALLOON notification appears:
  "Formatted with Stylua <version>".
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 4.2: Notification only once
- **Setup**: After Scenario 4.1, same IDE session.
- **Steps**:
  1. Reformat another `.lua` file.
- **Expected**: File is formatted. NO notification appears this time.
- **Result**: ⬜ Pass / ⬜ Fail

## 5. Concurrency

### Scenario 5.1: Rapid reformat with file switch
- **Setup**: Stylua bound. Open two `.lua` files, File1 and File2.
- **Steps**:
  1. Reformat File1.
  2. Immediately switch to File2 and reformat.
  3. Switch back to File1.
- **Expected**: Both files are formatted correctly. No errors, no hung IDE.
- **Result**: ⬜ Pass / ⬜ Fail