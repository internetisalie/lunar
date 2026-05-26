---
id: "ROCKS-RISKS"
title: "Design Gaps & De-risking"
type: "spec"
parent_id: "ROCKS"
status: "planned"
priority: "high"
folders:
  - "[[features/rocks/requirements|requirements]]"
---

# Design Gaps & De-risking: LuaRocks Integration (ROCKS Epic)

## 1. Technical Risks

### R1: CLI Version Mismatch (Medium)
- **Risk**: The user might try to run commands (like \`init\`) with an old version of LuaRocks that doesn't support specific flags.
- **Mitigation**: \`TOOL-01\`'s minimum version enforcement must be used.

### R2: LUA_INIT Collisions (High)
- **Risk**: Users might already have `LUA_INIT` defined in their shell. Prepending or overwriting it in the IDE might cause unexpected behavior.
- **Mitigation**: Use `-l setup` as an alternative in Run Configurations when possible, or provide a UI option to choose between prepending/overwriting.

### R3: Lua JSON Dependency (Low)
- **Risk**: The `rockspec.lua` parser requires `lunajson` to export data to the IDE. If missing from the user's environment, parsing will fail.
- **Mitigation**: Bundle `lunajson.lua` (or a single-file equivalent) within the plugin resources and include it in the `LUA_PATH` during parser execution.

## 2. Design Gaps

### G1: Multiple Rockspecs
- **Gap**: The current design assumes a single \`.rockspec\` in the root. Some projects have multiple (e.g., for different versions).
- **Requirement**: \`ROCKS-04\` should allow picking a specific rockspec.

## 3. De-risking Action Items [Must]

| ID | Action Item | Priority | Target Feature | Status |
| :--- | :--- | :---: | :--- | :--- |
| **ROCKS-DR-01** | Prototype \`LUA_INIT\` vs \`-l setup\` for module resolution | High | \`ROCKS-01\` | Pending |
| **ROCKS-DR-02** | Test `luarocks init` behavior on non-empty directories | Medium | `ROCKS-01` | Pending |
| **ROCKS-DR-03** | Verify Lua JSON Bridge for Rockspec Parsing | High | `ROCKS-03` | Pending |

### ROCKS-DR-03 Notes:
- Test the suitability of embedding the encoder from [rxi/json.lua](https://github.com/rxi/json.lua/blob/master/json.lua) as a lightweight, single-file alternative to `lunajson`.
