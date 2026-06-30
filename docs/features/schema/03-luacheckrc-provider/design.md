---
id: "SCHEMA-03-DESIGN"
title: "Technical Design"
type: "design"
parent_id: "SCHEMA-03"
folders:
  - "[[features/schema/03-luacheckrc-provider/requirements|requirements]]"
---

# Technical Design: SCHEMA-03 — Luacheckrc Schema Provider

## 1. Architecture Overview

### Current State
Currently, `.luacheckrc` files are treated as standard `.lua` files. There is no code completion for luacheck options (`std`, `globals`, etc.), and no validation for option value types (e.g., providing a boolean instead of a string array for `globals`). The ANALYSIS epic integrates Luacheck execution (`net.internetisalie.lunar.analysis.luacheck.LuaCheckInvoker`) but relies on the raw user config on disk without IDE-level schema support.

### Prior Art in This Repo
- **LuaCheck integration**: `net.internetisalie.lunar.analysis.luacheck.LuaCheckAnnotator` runs the linter. It does not model `.luacheckrc`.
- **SCHEMA-01 Engine**: The `net.internetisalie.lunar.lang.schema.LuaSchemaFileProvider` base class (defined in `SCHEMA-01`) is the extension point for all JSON-Schema Lua file providers. We will **EXTEND** this base class. No existing luacheck schema validation exists.

### Target State
A new schema provider, `LuacheckrcSchemaProvider` (extending `LuaSchemaFileProvider`), maps `.luacheckrc` and `.luacheckrc.lua` files to a newly bundled JSON schema asset (`luacheck-config.schema.json`). The SCHEMA-01 engine automatically provides validation, completion, and hover docs based on this schema.

## 2. Core Components

### 2.1 `net.internetisalie.lunar.lang.schema.LuacheckrcSchemaProvider`
- **Responsibility**: Claims `.luacheckrc` and `.luacheckrc.lua` files and supplies the bundled JSON schema.
- **Threading**: Read action (file name matching).
- **Collaborators**: `com.intellij.openapi.vfs.VirtualFile`, `com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider` (via `LuaSchemaFileProvider`).
- **Key API**:
  ```kotlin
  class LuacheckrcSchemaProvider(project: Project) : LuaSchemaFileProvider(project, "Luacheckrc", "/jsonschema/luacheck-config.schema.json") {
      override fun isAvailable(file: VirtualFile): Boolean
  }
  ```

### 2.2 `net.internetisalie.lunar.lang.schema.LuacheckrcSchemaProviderFactory`
- **Responsibility**: Factory registered in `plugin.xml` that returns a singleton list containing `LuacheckrcSchemaProvider`.
- **Threading**: Read action.
- **Collaborators**: `net.internetisalie.lunar.lang.schema.LuaSchemaProviderFactory`.
- **Key API**:
  ```kotlin
  class LuacheckrcSchemaProviderFactory : LuaSchemaProviderFactory() {
      override fun getProviders(project: Project): List<JsonSchemaFileProvider>
  }
  ```

## 3. Algorithms

### 3.1 `isAvailable` predicate
- **Input → Output**: `VirtualFile` → `Boolean`
- **Steps**:
  1. Return `true` if `file.name` equals `".luacheckrc"` or `".luacheckrc.lua"`.
  2. Else return `false`.



## 4. External Data & Parsing

### 4.1 `luacheck-config.schema.json`
- **Format**: Draft-07 JSON Schema.
- **Parse strategy**: Loaded by the platform `JsonSchemaService`. No manual parsing required.
- **Content Map**:
  - `std`: `string`
  - `globals`: `array` of `string`
  - `read_globals`: `array` of `string`
  - `ignore`: `array` of `string`
  - `enable`: `array` of `string`
  - `files`: `object` mapping string paths to configuration objects.
  - `max_line_length`: `integer` or `boolean` (false to disable)
  - `max_cyclomatic_complexity`: `integer` or `boolean`
  - `allow_defined`: `boolean`
  - `allow_defined_top`: `boolean`
  - `module`: `boolean`
  - `compat`: `boolean`

## 5. Data Flow

### Example 1: Validation
1. User creates `.luacheckrc` and adds `globals = true`.
2. Platform detects file extension/name change and queries `<JsonSchema.ProviderFactory>`.
3. `LuacheckrcSchemaProviderFactory` returns `LuacheckrcSchemaProvider`, which claims the file.
4. `SCHEMA-01` engine evaluates `globals`. The schema defines it as `array` of `string`.
5. The engine highlights `true` with a WARNING: "Type mismatch (expected array)".

### Example 2: Completion
1. User types `<caret>` at the top level of `.luacheckrc`.
2. The engine retrieves the properties from `luacheck-config.schema.json`.
3. The completion lookup displays `std`, `globals`, `ignore`, `max_line_length`, etc., with descriptions.

## 6. Edge Cases
- **Missing Resource**: If `luacheck-config.schema.json` fails to load, `getSchemaFile()` returns null, and validation silently degrades (handled natively by the engine).
- **Unknown configuration options**: The schema should set `"additionalProperties": true` at the root by default to avoid strict warnings on user-specific or newer luacheck config keys not yet bundled, although we can strictly warn on known keys.

## 7. Integration Points

```xml
<!-- plugin.xml -->
<extensions defaultExtensionNs="JavaScript.JsonSchema">
    <ProviderFactory implementation="net.internetisalie.lunar.lang.schema.LuacheckrcSchemaProviderFactory"/>
</extensions>
```
*Note: `JavaScript.JsonSchema.ProviderFactory` is the actual platform EP name for JSON schema provider factories.*

## 8. Requirement Coverage

| Requirement | Priority | Implemented by (section) |
|-------------|----------|--------------------------|
| SCHEMA-03-01 | M | §4.1 (JSON schema asset) |
| SCHEMA-03-02 | M | §2.1, §3.1 (`LuacheckrcSchemaProvider` mapping) |
| SCHEMA-03-03 | S | §5 (Engine integration) |

## 9. Alternatives Considered
- Writing a custom annotator for `.luacheckrc`: Rejected, as we have the `SCHEMA-01` engine specifically to avoid custom annotators for data configs.

## 10. Open Questions

_None — feature has cleared the planning bar._
