---
id: "SCHEMA-04-DESIGN"
title: "Technical Design"
type: "design"
parent_id: "SCHEMA-04"
folders:
  - "[[features/schema/04-busted-provider/requirements|requirements]]"
---

# Technical Design: SCHEMA-04 — Busted Config Schema Provider

## 1. Architecture Overview

### Current State
There is no editor support for validation, completion, or documentation in `.busted` config files.

### Prior Art in This Repo
- `SCHEMA-01` provides the base classes `LuaSchemaFileProvider` and `LuaSchemaProviderFactory`. **Note: SCHEMA-01 is currently planned but not implemented. SCHEMA-04 cannot be implemented or marked as 'planned' until SCHEMA-01 exists in the codebase.**
- `SCHEMA-02` (rockspec provider) and `SCHEMA-03` (luacheckrc provider) use these exact extension points. 

### Target State
A `BustedSchemaProviderFactory` that registers a `BustedSchemaProvider` for `.busted` files, mapping them to a bundled `busted-config.schema.json`.

## 2. Core Components

### 2.1 `net.internetisalie.lunar.lang.schema.providers.BustedSchemaProviderFactory`
- **Responsibility**: Returns the busted schema provider to the platform engine.
- **Threading**: Called by the platform on background threads.
- **Collaborators**: Extends `net.internetisalie.lunar.lang.schema.LuaSchemaProviderFactory`.
- **Key API**:
  ```kotlin
  class BustedSchemaProviderFactory : LuaSchemaProviderFactory() {
      override fun getProviders(project: Project): List<JsonSchemaFileProvider>
  }
  ```

### 2.2 `net.internetisalie.lunar.lang.schema.providers.BustedSchemaProvider`
- **Responsibility**: Maps a `.busted` VirtualFile to the bundled busted config JSON schema.
- **Threading**: `isAvailable(VirtualFile)` may be called on pooled threads.
- **Collaborators**: Extends `net.internetisalie.lunar.lang.schema.LuaSchemaFileProvider`.
- **Key API**:
  ```kotlin
  class BustedSchemaProvider(project: Project) : LuaSchemaFileProvider(
      project, 
      "Busted Config", 
      "/jsonschema/busted-config.schema.json"
  ) {
      override fun isAvailable(file: VirtualFile): Boolean
  }
  ```

## 3. Algorithms

### 3.1 Availability Check (`isAvailable`)
- **Input → Output**: `VirtualFile` → `Boolean`.
- **Steps**:
  1. Return `true` if `file.name == ".busted"`, otherwise `false`.

## 4. External Data & Parsing

### 4.1 Bundled JSON Schema
- **Format**: JSON Schema draft-07.
- **Schema Name**: `busted-config.schema.json`.
- **Contents**: A schema defining profiles. The root is an object where keys (profiles) map to an object with `additionalProperties: false`. Each profile object has the following properties:
  - `output` (string)
  - `verbose` (boolean)
  - `coverage` (boolean)
  - `pattern` (string)
  - `ROOT` (string)
  - `lpath` (string)
  - `cpath` (string)
  - `helper` (string)
  - `tags` (array of strings)
  - `defer-print` (boolean)
  - `lua` (string)
  - `exclude-tags` (array of strings)
  - `filter` (array of strings)
  - `filter-out` (array of strings)
  - `loaders` (array of strings)
- **Mapping**: Loaded into a `VirtualFile` using `com.intellij.openapi.vfs.VfsUtil.findFileByURL(this::class.java.getResource("/jsonschema/busted-config.schema.json"))`.

## 5. Data Flow

### Example 1: User edits `.busted`
1. User opens `.busted` containing `return { default = { verbose = true } }`.
2. The JSON Schema engine queries factories; `BustedSchemaProviderFactory` returns `BustedSchemaProvider`.
3. `isAvailable(file)` returns `true`.
4. Engine validates the returned table (shape B from SCHEMA-01) against the bundled busted schema.

## 6. Edge Cases
- **Missing schema resource**: Handled by platform returning null; validation is skipped safely.

## 7. Integration Points

```xml
<!-- plugin.xml -->
<depends>com.intellij.modules.json</depends>
<extensions defaultExtensionNs="com.intellij">
    <jsonSchema.providerFactory implementation="net.internetisalie.lunar.lang.schema.providers.BustedSchemaProviderFactory"/>
</extensions>
```

## 8. Requirement Coverage

| Requirement | Priority | Implemented by (section) |
|-------------|----------|--------------------------|
| SCHEMA-04-01 | C | §4.1 |
| SCHEMA-04-02 | C | §2.1, §2.2, §3.1, §7 |

## 9. Alternatives Considered
- **Hand-rolled check**: Rejected as it duplicates JSON schema interpretation, handled by SCHEMA-01.

## 10. Open Questions

_None — feature has cleared the planning bar._
