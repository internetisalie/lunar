---
id: "SCHEMA-02"
title: "02: Rockspec Schema Provider"
type: "feature"
status: "in_progress"
priority: "medium"
parent_id: "SCHEMA"
folders:
  - "[[features/schema/requirements|requirements]]"
---

# SCHEMA-02: Rockspec Schema Provider

> **Supersedes the standalone [ROCKS-13](../../rocks/13-rockspec-editor-support/requirements.md)
> design.** ROCKS-13's hand-rolled validator is replaced by this thin provider on top of the
> [SCHEMA-01](../01-engine/requirements.md) engine. ROCKS-13's bundled schemas, `rockspec_format`
> version selection, and test cases are **reused**.

## Overview
A `JsonSchemaFileProvider` (subclass of SCHEMA-01's `LuaSchemaFileProvider`) that maps `.rockspec`
files to the bundled rockspec JSON schema, so the SCHEMA-01 engine delivers validation, completion,
and hover docs for rockspecs — with full JSON-Schema semantics (the v3.0 `$ref`/`oneOf` structure the
ROCKS-13 reduced interpreter had to collapse).

## Scope

### In Scope
- `RockspecSchemaProvider : LuaSchemaFileProvider` — `isAvailable(file)` = `file.extension == "rockspec"`;
  `getSchemaFile()` = the bundled `rockspec-schema-v3X.json`
  (`src/main/resources/jsonschema/rockspec-schema-v30.json` / `-v31.json`).
- `RockspecSchemaProviderFactory : LuaSchemaProviderFactory` registered `<JsonSchema.ProviderFactory>`.
- `rockspec_format`-based v3.0/v3.1 selection (reuse ROCKS-13 design §3.6 logic to choose the schema
  file).

### Out of Scope
- The engine itself (SCHEMA-01).
- Any rockspec-specific validation logic beyond the JSON schema (the schema is the source of truth).

## Functional Requirements (to be detailed when SCHEMA-01 lands)

| ID | Requirement | Priority | Description |
|----|-------------|----------|-------------|
| SCHEMA-02-01 | **Rockspec file mapping** | M | Map `.rockspec` → bundled rockspec schema via a `LuaSchemaFileProvider`. |
| SCHEMA-02-02 | **Version selection** | S | Choose v3.1 schema when `rockspec_format` starts with `3.1`, else v3.0 (reuse ROCKS-13 §3.6). |
| SCHEMA-02-03 | **Test-case parity** | M | Port ROCKS-13 TC #2-#12 onto the engine (unknown key, missing required, value kind, version switch, etc.). |

## Dependencies
- **[SCHEMA-01](../01-engine/requirements.md)** — base provider classes + registered walker/enabler.
- Bundled rockspec schemas (already present from ROCKS-13).

## See Also
- Engine: [../01-engine/requirements.md](../01-engine/requirements.md)
- Superseded standalone: [ROCKS-13](../../rocks/13-rockspec-editor-support/requirements.md)

## Test Cases

Since ROCKS-13 was superseded, these test cases are ported directly to validate rockspecs against the engine.

| # | Requirement | Given | When | Then |
|---|-------------|-------|------|------|
| 1 | 02-01, 02-03 | Rockspec with missing `package` or `version` | highlight | WARNING "missing required property 'package'" |
| 2 | 02-01, 02-03 | Rockspec with `version = 1.0` (number) | highlight | WARNING type mismatch (expected string) |
| 3 | 02-02, 02-03 | Rockspec (no format, defaults v3.0) with `test_dependencies = {}` | highlight | WARNING "Property 'test_dependencies' is not allowed" (only allowed in v3.1) |
| 4 | 02-02, 02-03 | Rockspec with `rockspec_format = "3.1"` and `test_dependencies = {}` | highlight | No warning (allowed in v3.1) |
| 5 | 02-02, 02-03 | Rockspec with `rockspec_format = "3.1"` and unknown key `invalid_key = 1` | highlight | WARNING "Property 'invalid_key' is not allowed" |
| 6 | 02-01 (completion) | Caret at top level in rockspec | completeBasic | Suggests `package`, `version`, `source`, `description`, etc. |
