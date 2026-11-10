-- LuaCATS conformance cases, adapted from LuaLS/lua-language-server test/diagnostics/need-check-nil.lua @ a58fc7b0 (MIT); see LICENSE-LuaLS

-- ==== case need-check-nil-001 rule=nil.check source=need-check-nil.lua#1
---@type string?
local x

local s = x:upper() -- E

-- ==== case need-check-nil-002 rule=nil.check source=need-check-nil.lua#2
---@type string?
local x

S = x:upper() -- E

-- ==== case need-check-nil-003 rule=nil.check source=need-check-nil.lua#3
---@type string?
local x

if x then
    S = x:upper()
end

-- ==== case need-check-nil-004 rule=nil.check source=need-check-nil.lua#4
---@type string?
local x

if not x then
    x = ''
end

S = x:upper()

-- ==== case need-check-nil-005 rule=nil.check source=need-check-nil.lua#5
---@type fun()?
local x

S = x() -- E

-- ==== case need-check-nil-006 rule=nil.check source=need-check-nil.lua#6
---@type integer?
local x

T = {}
T[x] = 1 -- E

-- ==== case need-check-nil-007 rule=nil.check source=need-check-nil.lua#7
local x, y
local z = x and y

print(z.y)

-- ==== case need-check-nil-008 rule=nil.check source=need-check-nil.lua#8
local x, y
function x()
    y()
end

function y()
    x()
end

x()

-- ==== case need-check-nil-009 rule=nil.check source=need-check-nil.lua#9
---@class A
---@field b string
---@field c 'string'|string1'
---@field d 0|1|2

---@type A?
local a

if a.b == "string1" then end -- E
if a.b == "string" then end -- E

-- ==== case need-check-nil-010 rule=nil.check source=need-check-nil.lua#10
---@type string?
local x

local s = x?.upper()

-- ==== case need-check-nil-011 rule=nil.check source=need-check-nil.lua#11
---@type string?
local x

local s = x?.:upper()

-- ==== case need-check-nil-012 rule=nil.check source=need-check-nil.lua#12
---@type string?
local x

local s = x?.[1]

-- ==== case need-check-nil-013 rule=nil.check source=need-check-nil.lua#13
---@type fun()?
local x

x?.()

-- ==== case need-check-nil-014 rule=nil.check source=need-check-nil.lua#14
---@type string?
local x

local s = x?.field

-- ==== case need-check-nil-015 rule=nil.check source=need-check-nil.lua#15
---@type string?
local x

local s = x?.field?.sub

-- ==== case need-check-nil-016 rule=nil.check source=need-check-nil.lua#16
---@type string?
local x

local s = x?.upper()?.field

-- ==== case need-check-nil-017 rule=nil.check source=need-check-nil.lua#17
---@type string?
local x

local s = x.upper()?.field -- E

-- ==== case need-check-nil-018 rule=nil.check source=need-check-nil.lua#18
---@type string?
local x

local s = x?:upper()
