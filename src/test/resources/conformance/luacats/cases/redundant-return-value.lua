-- LuaCATS conformance cases, adapted from LuaLS/lua-language-server test/diagnostics/redundant-return-value.lua @ a58fc7b0 (MIT); see LICENSE-LuaLS

-- ==== case redundant-return-value-001 rule=return.count source=redundant-return-value.lua#1
---@type fun():number
local function f()
    return 1, true -- E
end

-- ==== case redundant-return-value-002 rule=return.count source=redundant-return-value.lua#2
---@return number, number?
function F()
    return 1, 1, 1 -- E
end

-- ==== case redundant-return-value-003 rule=return.count source=redundant-return-value.lua#3
---@return number, number?
function F()
    return 1, 1, 1, 2, 3 -- E
end

-- ==== case redundant-return-value-004 rule=return.count source=redundant-return-value.lua#4
---@meta

---@return number, number
local function r2() end

---@return number, number?
function F()
    return 1, r2() -- E
end
