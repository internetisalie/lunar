---
folders:
  - "[[features/syntax/07-inlay-hints/07-09-per-category-settings/requirements|requirements]]"
title: Technical Design
---
# Design: SYNTAX-07-09 Per-Category Settings

## Objective
Implement a robust settings infrastructure that allows users to customize their inlay hint experience.

## Architecture

### 1. Settings Model
- **`LuaInlayHintsSettings`**: An application-level service implementing `PersistentStateComponent<LuaInlayHintsSettings.State>`.
- **`State`**:
    - `showLocalVariableTypeHints: Boolean = true`
    - `showParameterNameHints: Boolean = true`
    - `showReturnTypeHints: Boolean = false`
    - `showMethodChainHints: Boolean = true`
    - `respectAnnotations: Boolean = true`
    - `largeFileThreshold: Int = 10000`

### 2. Provider Group Configuration
Inlay hints are split across three specialized providers to align with standard IDE groups:

1. **`LuaTypeInlayHintProvider`** (`TYPES_GROUP`)
   - Handles local variable types and return types.
   - Provides granular options with expand arrows.

2. **`LuaParameterInlayHintsProvider`** (`PARAMETERS_GROUP`)
   - Handles function call parameter names.
   - Registered without nested options to provide a simple top-level checkbox in the "Parameter names" group.

3. **`LuaMethodChainInlayHintProvider`** (`METHOD_CHAINS_GROUP`)
   - Handles method chaining hints.
   - Registered with a dedicated option in the "Method chains" group.

### 3. UI Integration
- **`LuaInlayHintsCustomSettingsProvider`**: Provides additional custom settings UI (e.g., large file threshold) integrated into the platform's inlay hints settings page (specifically linked to the `lua.type.hints` provider).
- **Declarative Inlay Integration**: Providers appear in their respective groups in the `Settings -> Editor -> Inlay Hints` tree.
- **Refresh Mechanism**: On `apply()`, the platform's inlay hints infrastructure handles immediate updates; for custom settings, an editor refresh is triggered manually if needed (though the platform's `persistSettings` call usually suffices when integrated).

### 4. Logic Integration
- Update `LuaTypeInlayHintProvider` to:
    1. Check `largeFileThreshold` against the current file size at the start of collection.
    2. Check specific category flags before visiting or processing relevant PSI elements.
    3. Pass the `respectAnnotations` flag to the suppression logic.

## Persistence
- Settings will be saved in `options/lunar_inlay_hints.xml`.
- Standard IntelliJ XML serialization will be used.
