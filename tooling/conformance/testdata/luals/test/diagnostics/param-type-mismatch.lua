TEST [[
---@param x number
local function f(x) end

f(<!true!>)
]]

config.set(nil, 'Lua.type.weakUnionCheck', true)
TEST [[
---@param x number|boolean
local function f(x) end
f(<!true!>)
]]
config.set(nil, 'Lua.type.weakUnionCheck', false)

--TEST [[
---@param x number
local function f(x) end
f(<!1!>)
]]

TEST [[
local function f(x) end
f(<?x?>)
]]

TEST [[
local t = 1
---@cast t <!number!>
]]
