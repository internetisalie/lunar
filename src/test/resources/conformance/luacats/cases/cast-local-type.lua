-- LuaCATS conformance cases, adapted from LuaLS/lua-language-server test/diagnostics/cast-local-type.lua @ a58fc7b0 (MIT); see LICENSE-LuaLS

-- ==== case cast-local-type-001 rule=cast.local source=cast-local-type.lua#1
local x = 0

x = true -- E

-- ==== case cast-local-type-002 rule=cast.local source=cast-local-type.lua#2
---@type integer
local x

x = true -- E

-- ==== case cast-local-type-003 rule=cast.local source=cast-local-type.lua#3
---@type unknown
local x

x = nil

-- ==== case cast-local-type-004 rule=cast.local source=cast-local-type.lua#4
---@type unknown
local x

x = 1

-- ==== case cast-local-type-005 rule=cast.local source=cast-local-type.lua#5
---@type unknown|nil
local x

x = 1

-- ==== case cast-local-type-006 rule=cast.local source=cast-local-type.lua#6
local x = {}

x = nil

-- ==== case cast-local-type-008 rule=cast.local source=cast-local-type.lua#8
---@type string?
local x

x = nil

-- ==== case cast-local-type-009 rule=cast.local source=cast-local-type.lua#9
---@type table
local x

x = nil -- E

-- ==== case cast-local-type-010 rule=cast.local source=cast-local-type.lua#10
local x

x = nil

-- ==== case cast-local-type-011 rule=cast.local source=cast-local-type.lua#11
---@type integer
local x

---@type number
x = f() -- E

-- ==== case cast-local-type-012 rule=cast.local source=cast-local-type.lua#12
---@type number
local x

---@type integer
x = f()

-- ==== case cast-local-type-013 rule=cast.local source=cast-local-type.lua#13
---@type number|boolean
local x

---@type string
x = f() -- E

-- ==== case cast-local-type-014 rule=cast.local source=cast-local-type.lua#14
---@type number|boolean
local x

---@type boolean
x = f()

-- ==== case cast-local-type-015 rule=cast.local source=cast-local-type.lua#15
---@type number|boolean
local x

---@type boolean|string
x = f() -- E

-- ==== case cast-local-type-016 rule=cast.local source=cast-local-type.lua#16
---@type boolean
local x

if not x then
    return
end

x = f()

-- ==== case cast-local-type-017 rule=cast.local source=cast-local-type.lua#17
---@type boolean
local x

---@type integer
local y

x = y -- E

-- ==== case cast-local-type-018 rule=cast.local source=cast-local-type.lua#18
local y = true

local x
x = 1
x = y

-- ==== case cast-local-type-019 rule=cast.local source=cast-local-type.lua#19
local t = {}

local x = 0
x = x + #t

-- ==== case cast-local-type-020 rule=cast.local source=cast-local-type.lua#20
local x = 0

x = 1.0

-- ==== case cast-local-type-021 rule=cast.local source=cast-local-type.lua#21
---@class A

local t = {}

---@type A
local a

t = a

-- ==== case cast-local-type-022 rule=cast.local source=cast-local-type.lua#22
---@type integer
local x

x = 1.0

-- ==== case cast-local-type-023 rule=cast.local source=cast-local-type.lua#23
---@type integer
local x

x = 1.5 -- E

-- ==== case cast-local-type-024 rule=cast.local source=cast-local-type.lua#24
---@type integer
local x

x = 1 + G

-- ==== case cast-local-type-025 rule=cast.local source=cast-local-type.lua#25
---@type integer
local x

x = 1 + G

-- ==== case cast-local-type-026 rule=cast.local source=cast-local-type.lua#26
---@alias A integer

---@type A
local a

---@type integer
local b

b = a

-- ==== case cast-local-type-027 rule=cast.local source=cast-local-type.lua#27
---@type string[]
local t

t = 'xxx' -- E

-- ==== case cast-local-type-028 rule=cast.local source=cast-local-type.lua#28
---@type 1|2
local x

x = 1
x = 2
x = 3 -- E

-- ==== case cast-local-type-029 rule=cast.local source=cast-local-type.lua#29
---@type 'x'|'y'
local x

x = 'x'
x = 'y'
x = 'z' -- E

-- ==== case cast-local-type-030 rule=cast.local source=cast-local-type.lua#30
local t = {
    x = 1,
}

local x
t[x] = true

-- ==== case cast-local-type-031 rule=cast.local source=cast-local-type.lua#31
---@type table<string, string>
local x

---@type table<number, string>
local y

x = y -- E

-- ==== case cast-local-type-032 rule=cast.local source=cast-local-type.lua#32
---@type table<string, string>
local x

---@type table<string, number>
local y

x = y -- E

-- ==== case cast-local-type-033 rule=cast.local source=cast-local-type.lua#33
---@type table<string, string>
local x

---@type table<string, string>
local y

x = y

-- ==== case cast-local-type-034 rule=cast.local source=cast-local-type.lua#34
---@type { x: number, y: number }
local t1

---@type { x: number }
local t2

t1 = t2 -- E

-- ==== case cast-local-type-035 rule=cast.local source=cast-local-type.lua#35
---@type { x: number, [integer]: number }
local t1

---@type { x: number }
local t2

t1 = t2 -- E

-- ==== case cast-local-type-036 rule=cast.local source=cast-local-type.lua#36
local x

if X then
    x = 'A'
elseif X then
    x = 'B'
else
    x = 'C'
end

local y = x

y = nil -- E

-- ==== case cast-local-type-037 rule=cast.local source=cast-local-type.lua#37
---@type 'A'|'B'|'C'|'D'|'E'|'F'|'G'|'H'|'I'|'J'|'K'|'L'|'M'|'N'|'O'|'P'|'Q'|'R'|'S'|'T'|'U'|'V'|'W'|'X'|'Y'|'Z'
local x

x = nil -- E

-- ==== case cast-local-type-038 rule=cast.local source=cast-local-type.lua#38
---@class A
---@field x string
---@field y number

local a = {x = "", y = 0}

---@type A
local v
v = a
