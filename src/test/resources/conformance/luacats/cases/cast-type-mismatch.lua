-- LuaCATS conformance cases, adapted from LuaLS/lua-language-server test/diagnostics/cast-type-mismatch.lua @ a58fc7b0 (MIT); see LICENSE-LuaLS

-- ==== case cast-type-mismatch-001 rule=cast.mismatch source=cast-type-mismatch.lua#1
---@type string|boolean
local t

---@cast t string
