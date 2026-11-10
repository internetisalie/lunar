-- LuaCATS conformance cases, adapted from LuaLS/lua-language-server test/diagnostics/missing-parameter.lua @ a58fc7b0 (MIT); see LICENSE-LuaLS

-- ==== case missing-parameter-001 rule=arity.tooFew source=missing-parameter.lua#1
local function x(a, b)
    return a, b
end
x(1)

-- ==== case missing-parameter-002 rule=arity.tooFew source=missing-parameter.lua#2
---@param a integer
---@param b integer
local function x(a, b)
    return a, b
end
x(1) -- E

-- ==== case missing-parameter-003 rule=arity.tooFew source=missing-parameter.lua#3
---@param a integer
---@param b integer
local function x(a, b)
    return a, b
end
x() -- E

-- ==== case missing-parameter-004 rule=arity.tooFew source=missing-parameter.lua#4
---@param a integer
---@param b integer
---@param ... integer
local function x(a, b, ...)
    return a, b, ...
end
x(1, 2)

-- ==== case missing-parameter-005 rule=arity.tooFew source=missing-parameter.lua#5
---@param a integer
---@param b integer
local function f(a, b)
end

f(...)

-- ==== case missing-parameter-006 rule=arity.tooFew source=missing-parameter.lua#6
---@param a integer
---@param b integer
local function f(a, b)
end

local function return2Numbers()
    return 1, 2
end

f(return2Numbers())

-- ==== case missing-parameter-007 rule=arity.tooFew source=missing-parameter.lua#7
---@param a integer
---@param b? integer
local function x(a, b)
    return a, b
end
x(1)

-- ==== case missing-parameter-008 rule=arity.tooFew source=missing-parameter.lua#8
---@param b integer?
local function x(a, b)
    return a, b
end
x(1)

-- ==== case missing-parameter-009 rule=arity.tooFew source=missing-parameter.lua#9
---@param b integer|nil
local function x(a, b)
    return a, b
end
x(1)

-- ==== case missing-parameter-010 rule=arity.tooFew source=missing-parameter.lua#10
local t = {}

function t:init() end

t.init() -- E
