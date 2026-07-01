---
id: MAINT-06
title: LuaCATS Literal Highlighting Requirements
type: feature
parent_id: MAINT
status: done
folders:
  - "[[features/maint/requirements|requirements]]"
---

# LuaCATS Literal Highlighting Requirements

## Requirements Table
| ID | Requirement | Priority | Status | Description |
|---|---|---|---|---|
| MAINT-06-01 | Keyword Colors | Must | Full | Highlight literal string/number types in LuaCATS comments (e.g. `---@alias Mode "read"\|"write"`, `---@type 1\|2`) as keywords via `LuaCatsAnnotator`. |
| MAINT-06-02 | Boolean Literals | Should | Full | Highlight boolean literal types `true`/`false` (e.g. `---@type true\|false`) as keywords. `nil` remains the nil *type* (single inhabitant), not a literal. Requires `literalType` grammar extension + parser regen. |
