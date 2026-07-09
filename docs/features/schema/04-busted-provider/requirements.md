---
id: "SCHEMA-04"
title: "04: Busted Config Schema Provider"
type: "feature"
status: "done"
vf_icon: ✅
priority: "low"
parent_id: "SCHEMA"
folders:
  - "[[features/schema/requirements|requirements]]"
---

# SCHEMA-04: Busted Config Schema Provider

## Overview
A provider mapping a busted `.busted` config to a bundled busted-config JSON schema. `.busted` uses the
**shape-B** document form (`return { default = { … }, … }`), making it the validation target that
exercises [SCHEMA-01](../01-engine/requirements.md)'s returned-table root handling.

## Scope

### In Scope
- Author `busted-config.schema.json` (bundled) from busted's documented run-config keys (`default`,
  `output`, `verbose`, `coverage`, `pattern`, `ROOT`, `lpath`, `cpath`, `helper`, `tags`, …), each
  profile being an object of those options.
- `BustedSchemaProvider : LuaSchemaFileProvider` — `isAvailable(file)` = name `.busted`;
  `getSchemaFile()` = the bundled schema.

### Out of Scope
- The engine (SCHEMA-01).
- Running busted; reconciling against a specific busted version.

## Functional Requirements (to be detailed when SCHEMA-01 lands)

| ID | Requirement | Priority | Description |
|----|-------------|----------|-------------|
| SCHEMA-04-01 | **Busted config schema asset** | C | Bundle `busted-config.schema.json` for the busted run-config option set. |
| SCHEMA-04-02 | **`.busted` mapping (shape B)** | C | Map `.busted` → that schema; validates the returned-table root (SCHEMA-01 shape B). |

## Dependencies
- **[SCHEMA-01](../01-engine/requirements.md)** — specifically the shape-B (`return {table}`) root.

## See Also
- Engine: [../01-engine/requirements.md](../01-engine/requirements.md)

## Test Cases

| # | Requirement | Given | When | Then |
|---|-------------|-------|------|------|
| 1 | 04-02 | `.busted` with `return { default = { verbose = true } }` | highlight | No warnings |
| 2 | 04-02 | `.busted` with `return { default = { bogus = true } }` | highlight | WARNING "Property 'bogus' is not allowed" |
| 3 | 04-02 | `.busted` with `return { default = { verbose = "yes" } }` | highlight | WARNING type mismatch (expected boolean) |
| 4 | 04-02 (completion) | Caret inside `return { default = { <caret> } }` | completeBasic | Suggests `output`, `verbose`, `coverage`, `pattern`, `ROOT`, `lpath`, `cpath`, `helper`, `tags` |
