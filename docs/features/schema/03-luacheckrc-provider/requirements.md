---
id: "SCHEMA-03"
title: "03: Luacheckrc Schema Provider"
type: "feature"
status: "done"
vf_icon: ✅
priority: "medium"
parent_id: "SCHEMA"
folders:
  - "[[features/schema/requirements|requirements]]"
---

# SCHEMA-03: Luacheckrc Schema Provider

## Overview
A provider mapping `.luacheckrc` (a Lua **data** file of top-level config globals — the shape-A
document, structurally identical to a rockspec) to a **new** bundled luacheck-config JSON schema.
This is the second engine consumer that proves [SCHEMA-01](../01-engine/requirements.md) generalises
beyond rockspec.

## Scope

### In Scope
- Author `luacheck-config.schema.json` (bundled under `src/main/resources/jsonschema/`) from the
  documented luacheck option set (`std`, `globals`, `read_globals`, `ignore`, `enable`, `files`,
  `exclude_files`, `max_line_length`, `max_cyclomatic_complexity`, `allow_defined`, `unused`, …).
- `LuacheckrcSchemaProvider : LuaSchemaFileProvider` — `isAvailable(file)` = name `.luacheckrc`;
  `getSchemaFile()` = the bundled schema.
- Register the provider factory.

### Out of Scope
- The engine (SCHEMA-01).
- Running luacheck or reconciling the schema with a specific luacheck version (the schema reflects the
  current documented option set; version drift is technical debt).
- Per-file `std` table semantics beyond a string/array/object shape.

## Functional Requirements

| ID | Requirement | Priority | Description |
|----|-------------|----------|-------------|
| SCHEMA-03-01 | **Luacheck config schema asset** | M | Bundle `luacheck-config.schema.json` capturing the top-level luacheck options + value types. |
| SCHEMA-03-02 | **`.luacheckrc` mapping** | M | Map `.luacheckrc` (and `.luacheckrc.lua` if used) → that schema via a `LuaSchemaFileProvider`. |
| SCHEMA-03-03 | **Validation/completion parity** | S | Unknown option, wrong value type, and completion of option keys all surface via the engine. |

## Test Cases

| # | Requirement | Given | When | Then |
|---|-------------|-------|------|------|
| 1 | 03-01, 03-02, 03-03 | A `.luacheckrc` file with `globals = true` | highlight | WARNING type mismatch (expected array) on `true` |
| 2 | 03-01, 03-02, 03-03 | A `.luacheckrc` file with `max_line_length = 120` | highlight | No warning (integer allowed) |
| 3 | 03-01, 03-02, 03-03 | A `.luacheckrc` file with `max_line_length = false` | highlight | No warning (boolean false allowed to disable) |
| 4 | 03-02 (completion) | Caret at top level in `.luacheckrc` | completeBasic | Suggests `std`, `globals`, `ignore`, `max_line_length`, etc. |
| 5 | 03-02 (isolation) | A plain `main.lua` file with `globals = true` | highlight | No schema warnings (engine not engaged) |

## Dependencies
- **[SCHEMA-01](../01-engine/requirements.md)**.
- Relates to the ANALYSIS epic's luacheck integration (`analysis/luacheck/`), which currently does not
  model `.luacheckrc` (only passes `--std`).

## See Also
- Engine: [../01-engine/requirements.md](../01-engine/requirements.md)
