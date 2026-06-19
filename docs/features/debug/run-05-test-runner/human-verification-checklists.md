---
id: "RUN-08-CHECKLIST"
title: "Verification Checklists"
type: "qa"
status: "todo"
parent_id: "RUN-08"
folders:
  - "[[features/debug/run-05-test-runner/requirements|requirements]]"
---

# Verification Checklists: RUN-08 — Lua Test Runner Integration

## 1. Test Configuration UI

### Scenario 1.1: Create a Lua Tests Run Configuration
- **Setup**: Open any Lua project in the IDE with the plugin installed.
- **Steps**:
  1. Open Run Configurations dialog (Run → Edit Configurations).
  2. Click "+" → look for "Lua Tests" configuration type.
  3. Fill in Framework (Busted), Test Target (select a `.lua` file), Interpreter.
  4. Click "Apply" then "OK".
- **Expected**: "Lua Tests" appears as a configuration type with its own icon. All fields are editable and persist after dialog close/reopen.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 1.2: Switch between Busted and Lunity
- **Setup**: A "Lua Tests" run configuration exists.
- **Steps**:
  1. Open the run configuration editor.
  2. Switch Framework from "Busted" to "Lunity".
  3. Verify the UI adapts (e.g., target type options may differ).
  4. Switch back to "Busted".
- **Expected**: Framework selection persists; switching frameworks does not lose other field values.
- **Result**: ⬜ Pass / ⬜ Fail

## 2. Test Execution — Busted

### Scenario 2.1: Run Busted Tests Successfully
- **Setup**: A project with Busted installed and a spec file containing `describe`/`it` blocks.
- **Steps**:
  1. Create a "Lua Tests" config pointing to the spec file with Busted framework.
  2. Click "Run".
- **Expected**: The Test Results window opens. Tests appear in a hierarchical tree (describe → it). Passing tests show green checkmarks. Duration is displayed.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 2.2: Run Busted Tests with Failures
- **Setup**: A spec file containing at least one failing assertion.
- **Steps**:
  1. Run the "Lua Tests" config.
- **Expected**: Failed tests show red ✗ with failure message and stack trace. The assertion message is visible in the test detail panel. Passing tests still show green.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 2.3: Busted Not Installed
- **Setup**: No `busted` binary in PATH or TOOL settings.
- **Steps**:
  1. Create and run a "Lua Tests" config with Busted framework.
- **Expected**: An error message appears indicating Busted is not found, with guidance to install via LuaRocks.
- **Result**: ⬜ Pass / ⬜ Fail

## 3. Test Execution — Lunity

### Scenario 3.1: Run Lunity Tests
- **Setup**: A Lua file using Lunity framework with JSON event output.
- **Steps**:
  1. Create a "Lua Tests" config with Lunity framework.
  2. Click "Run".
- **Expected**: Tests appear in the Test Results tree as events stream in. Results update in near-real-time.
- **Result**: ⬜ Pass / ⬜ Fail

## 4. Test Navigation

### Scenario 4.1: Navigate from Test Result to Source
- **Setup**: A completed test run with results in the Test Results window.
- **Steps**:
  1. Double-click a test node in the Test Results tree.
- **Expected**: The editor opens the test source file and positions the cursor on the test definition line.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 4.2: Gutter Run Icons
- **Setup**: Open a Busted spec file in the editor.
- **Steps**:
  1. Look at the gutter next to `describe(...)` and `it(...)` calls.
- **Expected**: Green "Play" icons appear in the gutter. Clicking one offers "Run" and "Debug" options.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 4.3: Right-Click Run from File
- **Setup**: A Busted spec file is open or selected in the project tree.
- **Steps**:
  1. Right-click the file → look for "Run Lua Tests in..." option.
- **Expected**: A context menu option creates and runs a "Lua Tests" configuration for the file.
- **Result**: ⬜ Pass / ⬜ Fail

## 5. Coverage

### Scenario 5.1: Run with Coverage
- **Setup**: `luacov` installed; a "Lua Tests" config exists.
- **Steps**:
  1. Click the "Run with Coverage" button (shield icon) for the test config.
  2. Wait for tests to complete.
- **Expected**: After completion, editor gutters show green (covered) and red (uncovered) line indicators on the tested source files. Coverage percentages appear in the project tree.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 5.2: Coverage Without Luacov
- **Setup**: `luacov` is NOT installed.
- **Steps**:
  1. Click "Run with Coverage".
- **Expected**: A balloon notification appears: "Code coverage library 'luacov' is not installed..." with a clickable link to install via LuaRocks. The coverage run is aborted.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 5.3: Import Coverage Report
- **Setup**: An existing `luacov.report.out` file from a previous run.
- **Steps**:
  1. Go to Analyze → Import LuaCov Report...
  2. Select the `luacov.report.out` file.
- **Expected**: Coverage data is loaded and displayed as gutter indicators on matching project files.
- **Result**: ⬜ Pass / ⬜ Fail

## 6. Coverage Report Viewer

### Scenario 6.1: Open luacov.report.out
- **Setup**: A `luacov.report.out` file exists in the project.
- **Steps**:
  1. Open `luacov.report.out` in the editor (double-click or File → Open).
- **Expected**: The file opens with custom syntax highlighting: `***0` prefixes in red, non-zero hit counts in green, file headers in bold, and Lua code portions with standard Lua syntax highlighting. An editor banner appears at the top.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 6.2: Load Coverage from Banner
- **Setup**: `luacov.report.out` is open in the editor with the banner visible.
- **Steps**:
  1. Click "Load Coverage onto Project Files" in the banner.
- **Expected**: Coverage data is parsed and applied as gutter indicators on the project's source files.
- **Result**: ⬜ Pass / ⬜ Fail

## 7. Tool Inventory

### Scenario 7.1: Auto-Discover Busted and LuaCov
- **Setup**: `busted` and `luacov` binaries are installed in system PATH.
- **Steps**:
  1. Go to Settings → Lua Tools.
  2. Click "Auto-Discover" or observe initial startup discovery.
- **Expected**: Both `busted` (category: TESTING) and `luacov` (category: COVERAGE) appear in the tool inventory with their paths and versions.
- **Result**: ⬜ Pass / ⬜ Fail

## 8. Rerun Failed Tests

### Scenario 8.1: Rerun Failed Subset
- **Setup**: A test run has completed with some failures.
- **Steps**:
  1. Click the "Rerun Failed Tests" button in the Test Results window.
- **Expected**: Only the previously failed tests are re-executed. The test tree updates with new results for only those tests.
- **Result**: ⬜ Pass / ⬜ Fail
