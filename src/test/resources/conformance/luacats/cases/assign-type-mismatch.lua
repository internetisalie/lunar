-- LuaCATS conformance cases, adapted from LuaLS/lua-language-server test/diagnostics/assign-type-mismatch.lua @ a58fc7b0 (MIT); see LICENSE-LuaLS

-- ==== case assign-type-mismatch-001 rule=field.assign source=assign-type-mismatch.lua#1
local m = {}

---@type integer[]
m.ints = {}

-- ==== case assign-type-mismatch-002 rule=field.assign source=assign-type-mismatch.lua#2
---@class A
---@field x A

---@type A
local t

t.x = {}

-- ==== case assign-type-mismatch-003 rule=field.assign source=assign-type-mismatch.lua#3
---@class A
---@field x integer

---@type A
local t

t.x = true -- E

-- ==== case assign-type-mismatch-004 rule=field.assign source=assign-type-mismatch.lua#4
---@class A
---@field x integer

---@type A
local t

---@type boolean
local y

t.x = y -- E

-- ==== case assign-type-mismatch-005 rule=field.assign source=assign-type-mismatch.lua#5
---@class A
local m

m.x = 1

---@type A
local t

t.x = true -- E

-- ==== case assign-type-mismatch-006 rule=field.assign source=assign-type-mismatch.lua#6
---@class A
local m

---@type integer
m.x = 1

m.x = true -- E

-- ==== case assign-type-mismatch-007 rule=field.assign source=assign-type-mismatch.lua#7
---@class A
local mt

---@type integer
mt.x = 1

function mt:init()
    self.x = true -- E
end

-- ==== case assign-type-mismatch-008 rule=assign.local source=assign-type-mismatch.lua#8
---@class A
---@field x integer

---@type A
local t = {
    x = true -- E
}

-- ==== case assign-type-mismatch-009 rule=field.assign source=assign-type-mismatch.lua#9
---@type boolean[]
local t = {}

t[5] = nil

-- ==== case assign-type-mismatch-010 rule=field.assign source=assign-type-mismatch.lua#10
---@type table<string, true>
local t = {}

t['x'] = nil

-- ==== case assign-type-mismatch-011 rule=assign.local source=assign-type-mismatch.lua#11
---@type [boolean]
local t = { [1] = nil } -- E

t = nil

-- ==== case assign-type-mismatch-012 rule=field.assign source=assign-type-mismatch.lua#12
local t = { true }

t[1] = nil

-- ==== case assign-type-mismatch-013 rule=field.assign source=assign-type-mismatch.lua#13
---@class A
local t = {
    x = 1
}

t.x = true -- E

-- ==== case assign-type-mismatch-014 rule=assign.local source=assign-type-mismatch.lua#14
---@type number
local t

t = 1

-- ==== case assign-type-mismatch-015 rule=assign.local source=assign-type-mismatch.lua#15
---@type number
local t

---@type integer
local y

t = y

-- ==== case assign-type-mismatch-016 rule=field.assign source=assign-type-mismatch.lua#16
---@class A
local m

---@type number
m.x = 1

m.x = {} -- E

-- ==== case assign-type-mismatch-017 rule=assign.local source=assign-type-mismatch.lua#17
local n

if G then
    n = {}
else
    n = nil
end

local t = {
    x = n,
}

-- ==== case assign-type-mismatch-018 rule=field.assign source=assign-type-mismatch.lua#18
---@type boolean[]
local t = {}

---@type boolean?
local x

t[#t+1] = x

-- ==== case assign-type-mismatch-023 rule=assign.local source=assign-type-mismatch.lua#23
---@class Option: string

---@param x Option
local function f(x) end

---@type Option
local x = 'aaa'

f(x)

-- ==== case assign-type-mismatch-028 rule=assign.local source=assign-type-mismatch.lua#28
---@class A
local a = {}

---@class B
local b = a -- E

-- ==== case assign-type-mismatch-029 rule=assign.local source=assign-type-mismatch.lua#29
---@class A
local a = {}

---@class B: A
local b = a

-- ==== case assign-type-mismatch-030 rule=assign.local source=assign-type-mismatch.lua#30
---@class A
local a = {}
a.__index = a

---@class B: A
local b = setmetatable({}, a)

-- ==== case assign-type-mismatch-031 rule=assign.local source=assign-type-mismatch.lua#31
---@class A
local a = {}

---@class B: A
local b = setmetatable({}, {__index = a})

-- ==== case assign-type-mismatch-032 rule=assign.local source=assign-type-mismatch.lua#32
---@class A
local a = {}

---@class B
local b = setmetatable({}, {__index = a}) -- E

-- ==== case assign-type-mismatch-033 rule=field.assign source=assign-type-mismatch.lua#33
---@class A
---@field x number?
local a

---@class B
---@field x number
local b

b.x = a.x

-- ==== case assign-type-mismatch-034 rule=assign.local source=assign-type-mismatch.lua#34

---@class A
---@field x number?
local a

---@type number
local t

t = a.x

-- ==== case assign-type-mismatch-035 rule=field.assign source=assign-type-mismatch.lua#35
local mt = {}
mt.x = 1
mt.x = nil

-- ==== case assign-type-mismatch-038 rule=assign.local source=assign-type-mismatch.lua#38
---@alias test boolean

---@type test
local test = 4 -- E

-- ==== case assign-type-mismatch-039 rule=assign.local source=assign-type-mismatch.lua#39
---@class MyClass
local MyClass = {}

function MyClass:new()
    ---@class MyClass
    local myObject = setmetatable({
        initialField = true
    }, self)

    print(myObject.initialField)
end

-- ==== case assign-type-mismatch-040 rule=field.assign source=assign-type-mismatch.lua#40
---@class T
local t = {
    x = nil
}

t.x = 1

-- ==== case assign-type-mismatch-041 rule=assign.local source=assign-type-mismatch.lua#41
---@type {[1]: string, [10]: number, xx: boolean}
local t = {
    true, -- E
    [10] = 's', -- E
    xx = 1, -- E
}

-- ==== case assign-type-mismatch-042 rule=assign.local source=assign-type-mismatch.lua#42
---@type boolean[]
local t = { 1, 2, 3 } -- E

-- ==== case assign-type-mismatch-043 rule=assign.local source=assign-type-mismatch.lua#43
---@type boolean[]
local t = { true, false, nil }

-- ==== case assign-type-mismatch-044 rule=assign.local source=assign-type-mismatch.lua#44
---@type boolean|nil
local x

---@type boolean[]
local t = { true, false, x }

-- ==== case assign-type-mismatch-045 rule=assign.local source=assign-type-mismatch.lua#45
---@enum Enum
local t = {
    x = 1,
    y = 2,
}

---@type Enum
local y

---@type integer
local x = y

-- ==== case assign-type-mismatch-046 rule=assign.local source=assign-type-mismatch.lua#46
---@type string|string[]|string[][]
local t = {{'a'}}

-- ==== case assign-type-mismatch-047 rule=assign.local source=assign-type-mismatch.lua#47
local A = "Hello"
local B = "World"

---@alias myLiteralAliases `A` | `B`

---@type myLiteralAliases
local x = A

-- ==== case assign-type-mismatch-048 rule=assign.local source=assign-type-mismatch.lua#48
local enum = { a = 1, b = 2 }

---@type { [integer] : boolean }
local t = {
    [enum.a] = 1, -- E
    [enum.b] = 2, -- E
    [3] = 3, -- E
}

-- ==== case assign-type-mismatch-049 rule=assign.local source=assign-type-mismatch.lua#49
---@class SomeClass
---@field [1] string
-- ...

---@param some_param SomeClass|SomeClass[]
local function some_fn(some_param) return end

some_fn { { "test" } } -- <- diagnostic: "Cannot assign `table` to `string`."

-- ==== case assign-type-mismatch-050 rule=assign.local source=assign-type-mismatch.lua#50
---@type string[]
local arr = {
    3, -- E
}

-- ==== case assign-type-mismatch-051 rule=assign.local source=assign-type-mismatch.lua#51
---@type (string|boolean)[]
local arr2 = {
    3, -- no warnings -- E
}

-- ==== case assign-type-mismatch-052 rule=field.assign source=assign-type-mismatch.lua#52
local t = {}
t.a = 1
t.a = 2
return t
