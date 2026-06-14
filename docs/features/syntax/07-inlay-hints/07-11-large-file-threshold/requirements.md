---
id: "SYNTAX-07-11"
title: "11: Large File Threshold"
type: "feature"
parent_id: "SYNTAX-07"
status: "done"
priority: "medium"
folders:
  - "[[features/syntax/07-inlay-hints|hints]]"
---
# Requirements: SYNTAX-07-11 Large File Threshold

## Overview
To prevent performance degradation and editor hangs, inlay hints should be disabled for files that exceed a specific size threshold (measured in lines). This is particularly important for generated, minified, or exceptionally large data files where type inference could be computationally expensive.

## Scope

### In Scope
- Define a configurable threshold (number of lines) in the Lunar settings.
- Implement a check in the inlay hint provider to skip computation if the file size exceeds the threshold.
- Provide a default threshold value of 10,000 lines.
- Ensure the threshold is user-configurable via the settings UI.

### Out of Scope
- Disabling other editor features (like syntax highlighting) based on this threshold.
- Measuring file size in bytes (lines are a better proxy for PSI complexity in this context).

## Requirements Table

| ID | Priority | Description | Status |
| :--- | :---: | :--- | :---: |
| **07-11-REQ-01** | [Must] | Add a `largeFileThreshold` property to `LuaInlayHintsSettings` (Task 282). | **Full** |
| **07-11-REQ-02** | [Must] | The default value for `largeFileThreshold` must be 10,000 lines. | **Full** |
| **07-11-REQ-03** | [Must] | In `LuaTypeInlayHintProvider.collectFromElement`, check if the file's line count exceeds the threshold. | **Full** |
| **07-11-REQ-04** | [Must] | If the threshold is exceeded, the provider must immediately return an empty collection without traversing the PSI. | **Full** |
| **07-11-REQ-05** | [Should] | Expose an input field for the threshold in the settings UI. | **Full** |
| **07-11-REQ-06** | [Should] | Provide a visual indication or a way for the user to know hints were disabled due to file size (e.g., a notification or status bar hint, though this might be too intrusive). | **Future Work** |

## Test Cases (TC)

| ID | Action | Expected Result |
| :--- | :--- | :--- |
| **TC-01** | Set threshold to 100 lines and open a 200-line file. | No inlay hints are rendered. |
| **TC-02** | Set threshold to 500 lines and open a 200-line file. | Inlay hints are rendered normally. |
| **TC-03** | Set threshold to 0 or a very high value. | Verify stable behavior (0 should probably disable hints entirely or use a safe default). |
| **TC-04** | Change threshold while a large file is open. | Editor refreshes to show/hide hints accordingly. |
