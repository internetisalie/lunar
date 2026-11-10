-- LuaCATS conformance cases, adapted from LuaLS/lua-language-server test/diagnostics/missing-return-value.lua @ a58fc7b0 (MIT); see LICENSE-LuaLS

-- ==== case missing-return-value-001 rule=return.count source=missing-return-value.lua#1
---@type fun():number
local function f()
    return -- E
end

-- ==== case missing-return-value-002 rule=return.count source=missing-return-value.lua#2
---@return number
function F()
    return -- E
end

-- ==== case missing-return-value-003 rule=return.count source=missing-return-value.lua#3
---@return number, number
function F()
    return 1 -- E
end

-- ==== case missing-return-value-004 rule=return.count source=missing-return-value.lua#4
---@return number, number?
function F()
    return 1
end

-- ==== case missing-return-value-005 rule=return.count source=missing-return-value.lua#5
---@return ...
function F()
    return
end
