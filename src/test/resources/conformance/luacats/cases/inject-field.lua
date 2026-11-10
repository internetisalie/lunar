-- LuaCATS conformance cases, adapted from LuaLS/lua-language-server test/diagnostics/inject-field.lua @ a58fc7b0 (MIT); see LICENSE-LuaLS

-- ==== case inject-field-001 rule=field.inject source=inject-field.lua#1
---@class Class
local m = {}

m.xx = 1 -- OK

---@type Class
local m

m.xx = 1 -- OK
m.yy = 1 -- Warning -- E

-- ==== case inject-field-002 rule=field.inject source=inject-field.lua#2
---@class Class
local m = {}

m.xx = 1 -- OK

---@class Class
local m

m.xx = 1 -- OK
m.yy = 1 -- OK

-- ==== case inject-field-003 rule=field.inject source=inject-field.lua#3
---@type { xx: number }
local m

m.xx = 1 -- OK
m.yy = 1 -- Warning -- E

-- ==== case inject-field-004 rule=field.inject source=inject-field.lua#4
---@type { xx: number, [any]: any }
local m

m.xx = 1 -- OK
m.yy = 1 -- OK

-- ==== case inject-field-005 rule=field.inject source=inject-field.lua#5
---@class Class
---@field x number

---@type Class
local t

t.x = 1 -- OK
t.y = 2 -- Warning -- E

-- ==== case inject-field-006 rule=field.inject source=inject-field.lua#6
---@class Class
---@field x number
---@field [any] any

---@type Class
local t

t.x = 1 -- OK
t.y = 2 -- OK

-- ==== case inject-field-007 rule=field.inject source=inject-field.lua#7
---@class (exact) Class
---@field x number
local m = {
    x = 1, -- OK
    y = 2, -- Warning -- E
}

m.x = 1 -- OK
m.y = 2 -- Warning -- E

function m:init() -- OK
    self.x = 1 -- OK
    self.y = 2 -- Warning -- E
    function self:xx() -- Warning -- E
    end
end

-- ==== case inject-field-008 rule=field.inject source=inject-field.lua#8
---@class Class
local m = {
    xx = 1, -- OK
}

---@type Class
local m

m.xx = 1 -- OK
m.yy = 1 -- Warning -- E
