---
folders:
  - "[[features/syntax/07-inlay-hints|hints]]"
title: "09: Per-Category Settings"
priority: medium
status: done
vf_icon: ✅
---
# Requirements: SYNTAX-07-09 Per-Category Settings

## Overview
Provide users with granular control over which inlay hints are displayed in the editor. Each category of inlay hints (local variables, parameters, return types, method chains) must be independently toggleable via the IDE settings.

## Scope

### In Scope
- Define configuration properties in the Lunar settings model.
- Implement persistence for these settings.
- Create a settings UI integrated into the IntelliJ Inlay Hints settings page.
- Refactor logic into three specialized providers: `LuaTypeInlayHintProvider`, `LuaParameterInlayHintsProvider`, and `LuaMethodChainInlayHintProvider`.
- Update all inlay hint providers to check these settings before rendering.

### Out of Scope
- Global "Inlay Hints" toggle (handled by the IntelliJ Platform).
- Customizing colors per category (this should follow standard IDE color schemes for now).

## Requirements Table

| ID | Priority | Description | Status |
| :--- | :---: | :--- | :---: |
| **07-09-REQ-01** | [Must] | Create a `LuaInlayHintsSettings` class to store toggle states for each category. | **Done** |
| **07-09-REQ-02** | [Must] | Persist settings using the IntelliJ `PersistentStateComponent` API at the Application level. | **Done** |
| **07-09-REQ-03** | [Must] | Implement a settings UI via `LuaInlayHintsCustomSettingsProvider` integrated into the IntelliJ Inlay Hints settings page. | **Done** |
| **07-09-REQ-04** | [Must] | Ensure `LuaTypeInlayHintProvider` respects the "Local Variable" and "Annotation Suppression" settings. | **Done** |
| **07-09-REQ-05** | [Must] | Ensure Parameter Hints respect the "Parameter Name" setting. | **Done** |
| **07-09-REQ-06** | [Must] | Ensure Return Type Hints respect the "Return Type" setting. | **Done** |
| **07-09-REQ-07** | [Must] | Ensure Method Chaining Hints respect the "Method Chaining" setting. | **Done** |
| **07-09-REQ-08** | [Must] | Implement a setting for the "Large File Threshold" (default: 10,000 lines). | **Done** |
| **07-09-REQ-09** | [Should] | Provide "Restore Defaults" functionality in the settings UI. | **Future Work** |

## Settings Mapping

| Category | Setting Key | Default |
| :--- | :--- | :---: |
| Local Variable Types | `showLocalVariableTypeHints` | On |
| Parameter Names | `showParameterNameHints` | On |
| Return Types | `showReturnTypeHints` | Off |
| Method Chaining | `showMethodChainHints` | On |
| Respect Annotations | `respectAnnotations` | On |
| Large File Threshold | `largeFileThreshold` | 10,000 |

## Test Cases (TC)

| ID | Action | Expected Result |
| :--- | :--- | :--- |
| **TC-01** | Uncheck "Local Variable Types" | Inferred types for `local` variables disappear from the editor. |
| **TC-02** | Check "Return Types" | Inferred return types appear after function parameter lists. |
| **TC-03** | Restart IDE | Settings for inlay hints are correctly persisted and restored. |
| **TC-04** | Toggle settings | Editor refreshes immediately (or on next re-parse) to reflect change. |
