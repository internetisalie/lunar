TEST [[
---@class A
---@field x number
local t = {}
<!t.x!> = true
]]

TEST [[
---@type number
local <!x!> = 'a'
]]

TEST [[
---@type number[]
local a = {}
local b = 1
a[1] = 2
]]
