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

### 2. UI Integration
- **`LuaInlayHintsConfigurable`**: A configurable that provides the Swing UI for the settings.
- **Declarative Inlay Integration**: Since we use the Declarative Inlay Hints API, we will register the configurable in `plugin.xml` so it appears in the `Settings -> Editor -> Inlay Hints` tree under `Lua`.
- **Refresh Mechanism**: On `apply()`, the configurable will trigger an editor refresh for all open Lua files to ensure hints are updated immediately.

### 3. Logic Integration
- Update `LuaTypeInlayHintProvider` to:
    1. Check `largeFileThreshold` against the current file size at the start of collection.
    2. Check specific category flags before visiting or processing relevant PSI elements.
    3. Pass the `respectAnnotations` flag to the suppression logic.

## Persistence
- Settings will be saved in `options/lunar_inlay_hints.xml`.
- Standard IntelliJ XML serialization will be used.
