---
id: "SCHEMA-02-DESIGN"
title: "Technical Design"
type: "design"
parent_id: "SCHEMA-02"
folders:
  - "[[features/schema/02-rockspec-provider/requirements|requirements]]"
---

# Technical Design: SCHEMA-02 — Rockspec Schema Provider

## 1. Architecture Overview

### Prior Art in This Repo
- `ROCKS-13` standalone validator (never implemented, superseded by SCHEMA-02; see `docs/features/schema/requirements.md`).
- `SCHEMA-01` provides the `LuaSchemaFileProvider` and `LuaSchemaProviderFactory` bases. **Note: SCHEMA-01 is currently planned but not implemented. SCHEMA-02 cannot be implemented or marked as 'planned' until SCHEMA-01 exists in the codebase.**

### Target State
A `JsonSchemaProviderFactory` that registers two providers: one for v3.1 rockspecs and one for v3.0 (and below) rockspecs. They rely on the bundled rockspec schemas and the `SCHEMA-01` Lua JSON-Schema Engine.

## 2. Core Components

### 2.1 `net.internetisalie.lunar.lang.schema.providers.RockspecSchemaProviderFactory`
- **Responsibility**: Returns the list of rockspec schema providers (v3.0 and v3.1) to the platform engine.
- **Threading**: Called by the platform on background threads.
- **Collaborators**: Extends `net.internetisalie.lunar.lang.schema.LuaSchemaProviderFactory`.
- **Key API**:
  ```kotlin
  class RockspecSchemaProviderFactory : LuaSchemaProviderFactory() {
      override fun getProviders(project: Project): List<JsonSchemaFileProvider>
  }
  ```

### 2.2 `net.internetisalie.lunar.lang.schema.providers.RockspecSchemaProvider`
- **Responsibility**: Maps a `.rockspec` VirtualFile to the appropriate bundled JSON schema based on the `rockspec_format` variable.
- **Threading**: `isAvailable(VirtualFile)` may be called on pooled threads; requires a read action to safely inspect PSI.
- **Collaborators**: Extends `net.internetisalie.lunar.lang.schema.LuaSchemaFileProvider`, reads `LuaAssignmentStatement` via `PsiManager`.
- **Key API**:
  ```kotlin
  sealed class RockspecSchemaProvider(project: Project, name: String, schemaResourcePath: String) : 
      LuaSchemaFileProvider(project, name, schemaResourcePath) {
      class V30(project: Project) : RockspecSchemaProvider(project, "Rockspec v3.0", "/jsonschema/rockspec-schema-v30.json")
      class V31(project: Project) : RockspecSchemaProvider(project, "Rockspec v3.1", "/jsonschema/rockspec-schema-v31.json")
      
      abstract override fun isAvailable(file: VirtualFile): Boolean
  }
  ```

## 3. Algorithms

### 3.1 Version selection (`isAvailable` logic)
- **Input → Output**: `VirtualFile` → `Boolean`.
- **Steps**:
  1. If `file.extension != "rockspec"`, return `false`.
  2. Obtain the PSI file: `val psiFile = runReadAction { PsiManager.getInstance(project).findFile(file) as? LuaFile } ?: return false`
  3. Scan the top-level assignments in `psiFile` for `rockspec_format`:
     - Search for a `LuaAssignmentStatement`.
     - Extract the first `LuaVar` from its `varList.varList`. If `var.nameRef?.text` equals `"rockspec_format"`:
     - Extract the first expression from `exprList.exprList`.
     - If it is a string literal starting with `"3.1"`, set `is31 = true`. Otherwise, `is31 = false`.
  4. For `RockspecSchemaProvider.V31`, return `is31`.
  5. For `RockspecSchemaProvider.V30`, return `!is31`.
- **Rules / edge handling**: If `rockspec_format` is missing, malformed, or unparseable, `is31` defaults to `false`, falling back to v3.0. This handles missing `rockspec_format` correctly as per LuaRocks defaults.

## 4. External Data & Parsing

### 4.1 Bundled JSON Schemas
- **Format**: JSON Schema draft-04/07 (handled by the platform).
- **Source**: `src/main/resources/jsonschema/rockspec-schema-v30.json` and `rockspec-schema-v31.json`.
- **Mapping**: Loaded into `VirtualFile` objects via `JsonSchemaProviderFactory.getResourceFile(...)` (handled by `LuaSchemaFileProvider` base class).

## 5. Data Flow

### Example 1: User opens a v3.1 rockspec
1. User opens `foo-1.0-1.rockspec` containing `rockspec_format = "3.1"`.
2. The platform's JSON Schema engine queries all factories. `RockspecSchemaProviderFactory` returns both `V30` and `V31` providers.
3. The engine calls `isAvailable(file)` on each.
4. `V30.isAvailable` reads the PSI, finds `rockspec_format = "3.1"`, and returns `false`.
5. `V31.isAvailable` reads the PSI, finds `rockspec_format = "3.1"`, and returns `true`.
6. The engine uses `rockspec-schema-v31.json` to validate and provide completions.

## 6. Edge Cases
- **Missing `rockspec_format`**: Handled; gracefully falls back to v3.0.
- **Malformed PSI / syntax error**: If `rockspec_format` cannot be parsed, falls back to v3.0.
- **Multiple `rockspec_format` globals**: Evaluates the first one found from the top of the file.

## 7. Integration Points

```xml
<!-- plugin.xml -->
<extensions defaultExtensionNs="com.intellij">
    <jsonSchema.providerFactory implementation="net.internetisalie.lunar.lang.schema.providers.RockspecSchemaProviderFactory"/>
</extensions>
```

## 8. Requirement Coverage

| Requirement | Priority | Implemented by (section) |
|-------------|----------|--------------------------|
| SCHEMA-02-01 | M | §2.1, §2.2, §7 |
| SCHEMA-02-02 | S | §3.1 |
| SCHEMA-02-03 | M | §3.1 (Test cases will assert these behaviors) |

## 9. Alternatives Considered
- **File-name based mapping only**: Using a single v3.0 schema for all `.rockspec` files. Rejected because v3.1 introduces significant differences in permitted fields that require the specific v3.1 schema.
- **String matching instead of PSI**: Using `VfsUtilCore.loadText()` and a Regex instead of PSI traversal. Rejected because we are in a language plugin and the `.rockspec` is already parsed as a `LuaFile`; querying the `LuaAssignmentStatement` via PSI is more robust against whitespace and comments.

## 10. Open Questions

_None — feature has cleared the planning bar._
