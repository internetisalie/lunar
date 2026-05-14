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
The `InlayHintsProvider` is registered in `plugin.xml` with the `group` attribute set to `TYPES_GROUP`:

```xml
<codeInsight.declarativeInlayProvider
        language="Lua"
        implementationClass="net.internetisalie.lunar.lang.insight.hint.LuaTypeInlayHintProvider"
        isInternal="false"
        isEnabledByDefault="true"
        group="TYPES_GROUP"
        providerId="lua.type.hints"
        ...>
```

**Available Inlay Groups** (from `com.intellij.codeInsight.hints.InlayGroup`):
- `CODE_VISION_GROUP_NEW` - Code vision hints (new style)
- `CODE_VISION_GROUP` - Code vision hints
- `PARAMETERS_GROUP` - Parameter name hints
- `TYPES_GROUP` - Type-related hints (used by Lunar)
- `VALUES_GROUP` - Value hints
- `ANNOTATIONS_GROUP` - Annotation hints
- `METHOD_CHAINS_GROUP` - Method chain hints
- `LAMBDAS_GROUP` - Lambda-related hints

The `TYPES_GROUP` categorizes our provider under **Settings | Editor | Inlay Hints | Types**, which is appropriate for Lua type hints (local variable types, return types).

### 3. UI Integration
- **`LuaInlayHintsCustomSettingsProvider`**: Provides additional custom settings UI (e.g., large file threshold) integrated into the platform's inlay hints settings page.
- **Declarative Inlay Integration**: Since we use the Declarative Inlay Hints API, the provider appears in the `Settings -> Editor -> Inlay Hints` tree under `Lua` within the `TYPES_GROUP`.
- **Refresh Mechanism**: On `apply()`, the configurable will trigger an editor refresh for all open Lua files to ensure hints are updated immediately.

### 4. Logic Integration
- Update `LuaTypeInlayHintProvider` to:
    1. Check `largeFileThreshold` against the current file size at the start of collection.
    2. Check specific category flags before visiting or processing relevant PSI elements.
    3. Pass the `respectAnnotations` flag to the suppression logic.

## Persistence
- Settings will be saved in `options/lunar_inlay_hints.xml`.
- Standard IntelliJ XML serialization will be used.
