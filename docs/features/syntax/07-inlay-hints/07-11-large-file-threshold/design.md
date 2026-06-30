---
id: "SYNTAX-07-11-DESIGN"
title: "Technical Design"
type: "design"
parent_id: "SYNTAX-07-11"
priority: "medium"
folders:
  - "[[features/syntax/07-inlay-hints/07-11-large-file-threshold/requirements|requirements]]"
---
# Design: SYNTAX-07-11 Large File Threshold

## Objective
Implement a performance safeguard that disables inlay hints for files beyond a certain size.

## Architecture

### 1. Settings Integration
- The `largeFileThreshold` property will be added to the `LuaInlayHintsSettings.State` class (created in Task 282).
- Default value: `10000`.

### 2. File Size Check
- The line count of a file can be efficiently retrieved from the `Document` object associated with the `PsiFile`.
- `document.lineCount` is a fast O(1) or O(log N) operation depending on the implementation, much faster than traversing the entire PSI tree.

### 3. Early Exit Strategy
- The check should happen as early as possible in the `collectFromElement` method of the `InlayHintsProvider`.
- If `lineCount > settings.largeFileThreshold`, the visitor/collector should not be instantiated or run.

## UI Integration
- The settings UI (Task 282) will include a numeric input field (e.g., `JBIntSpinner` or a standard `TextField` with validation) to allow users to customize the line limit.
- Label: "Disable hints for files larger than (lines):"

## Performance Considerations
- The check itself is trivial and has negligible overhead.
- By preventing PSI traversal and type resolution on massive files, we avoid potential CPU spikes and memory pressure.
