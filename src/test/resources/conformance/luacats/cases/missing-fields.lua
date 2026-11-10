-- LuaCATS conformance cases, adapted from LuaLS/lua-language-server test/diagnostics/missing-fields.lua @ a58fc7b0 (MIT); see LICENSE-LuaLS

-- ==== case missing-fields-001 rule=field.missing source=missing-fields.lua#1
---@diagnostic disable: unused-local
---@class A
---@field x number
---@field y? number
---@field z number

---@type A
local t = {} -- E

-- ==== case missing-fields-002 rule=field.missing source=missing-fields.lua#2
---@diagnostic disable: unused-local
---@class A
---@field x number
---@field y? number
---@field z number

---@type A
local t = { -- E
    x = 1,
}

-- ==== case missing-fields-003 rule=field.missing source=missing-fields.lua#3
---@diagnostic disable: unused-local
---@class A
---@field x number
---@field y? number
---@field z number

---@type A
local t = { -- E
    x = 1,
    y = 2,
}

-- ==== case missing-fields-004 rule=field.missing source=missing-fields.lua#4
---@diagnostic disable: unused-local
---@class A
---@field x number
---@field y? number
---@field z number

---@type A
local t = {
    x = 1,
    y = 2,
    z = 3,
}

-- ==== case missing-fields-005 rule=field.missing source=missing-fields.lua#5
---@diagnostic disable: unused-local
---@class A
---@field x number
---@field y? number
---@field z number
local A = {}

function A:fun()
end

---@type A
local t = {
    x = 1,
    y = 2,
    z = 3,
}

-- ==== case missing-fields-006 rule=field.missing source=missing-fields.lua#6
---@diagnostic disable: unused-local

---@class Parent
---@field a number
local Parent = {}

function Parent:fun2()
end

---@class A : Parent
---@field x number
---@field y? number
---@field z number
local A = {}

function A:fun()
end

---@type A
local t = { -- E
    x = 1,
    y = 2,
}

-- ==== case missing-fields-007 rule=field.missing source=missing-fields.lua#7
---@diagnostic disable: unused-local

---@class Parent
---@field a number
local Parent = {}

function Parent:fun2()
end

---@class A : Parent
---@field x number
---@field y? number
---@field z number
local A = {}

function A:fun()
end

---@type A
local t = {
    x = 1,
    y = 2,
    z = 3,
    a = 1,
}

-- ==== case missing-fields-008 rule=field.missing source=missing-fields.lua#8
---@diagnostic disable: unused-local
---@class A
---@field x number
---@field y? number
---@field z number
local A = {}

function A:fun()
end

---@type A
local t = { -- E
    x = 1,
    y = 2,
}

-- ==== case missing-fields-009 rule=field.missing source=missing-fields.lua#9
---@diagnostic disable: unused-local
---@class A
---@field x number
---@field y? number
---@field z number

---@type A
local t = {
    x = 1,
    z = 3,
}

-- ==== case missing-fields-010 rule=field.missing source=missing-fields.lua#10
---@diagnostic disable: unused-local
---@class A
---@field x number
---@field y? number
---@field z number

---@param a A
local function f(a) end

f {} -- E

-- ==== case missing-fields-011 rule=field.missing source=missing-fields.lua#11
---@diagnostic disable: unused-local
---@class A
---@field x number
---@field y? number
---@field z number

---@param a A
local function f(a) end

f { -- E
    x = 1,
}

-- ==== case missing-fields-012 rule=field.missing source=missing-fields.lua#12
---@diagnostic disable: unused-local
---@class A
---@field x number
---@field y? number
---@field z number

---@param a A
local function f(a) end

f { -- E
    x = 1,
    y = 2,
}

-- ==== case missing-fields-013 rule=field.missing source=missing-fields.lua#13
---@diagnostic disable: unused-local
---@class A
---@field x number
---@field y? number
---@field z number

---@param a A
local function f(a) end

f {
    x = 1,
    y = 2,
    z = 3,
}

-- ==== case missing-fields-014 rule=field.missing source=missing-fields.lua#14
---@diagnostic disable: unused-local
---@class A
---@field x number
---@field y? number
---@field z number

---@param a A
local function f(a) end

f {
    x = 1,
    z = 3,
}

-- ==== case missing-fields-015 rule=field.missing source=missing-fields.lua#15
---@diagnostic disable: unused-local
---@class A
---@field x number
local t = {}

-- ==== case missing-fields-016 rule=field.missing source=missing-fields.lua#16
---@diagnostic disable: unused-local

---@class A
---@field x number

---@class A
local t = {}

-- ==== case missing-fields-017 rule=field.missing source=missing-fields.lua#17
---@diagnostic disable: unused-local

---@class Foo
---@field a number
---@field b number
---@field c number

---@type Foo|Foo[]
local a = {
    {
        a = 1,
        b = 2,
        c = 3,
    }
}

-- ==== case missing-fields-018 rule=field.missing source=missing-fields.lua#18
---@diagnostic disable: unused-local

---@class Foo
---@field a number
---@field b number
---@field c number

---@class Bar
---@field ba number
---@field bb number
---@field bc number

---@type Foo|Bar
local b = {
    a = 1,
    b = 2,
    c = 3,
}

-- ==== case missing-fields-019 rule=field.missing source=missing-fields.lua#19
---@class A
---@field x integer

---@type A
return {} -- E

-- ==== case missing-fields-020 rule=field.missing source=missing-fields.lua#20
---@class A
---@field x number

---@class B
---@field y number

---@type A|B
local t = { -- E
    z = 1,
}

-- ==== case missing-fields-021 rule=field.missing source=missing-fields.lua#21
---@class A
---@field x number

---@class B
---@field y number

---@type A|B
local t = {
    y = 1,
}

-- ==== case missing-fields-022 rule=field.missing source=missing-fields.lua#22
---@diagnostic disable: unused-local

---@class Foo
---@field a number
---@field b number
---@field c number

---@class Foo

---@class Bar
---@field ba number
---@field bb number
---@field bc number

---@class Bar
---@field bd number

---@type Foo|Bar
local x = {
	ba = 1,
	bb = 2,
	bc = 3,
	bd = 4,
}

-- ==== case missing-fields-023 rule=field.missing source=missing-fields.lua#23
---@diagnostic disable: unused-local

---@class Foo
---@field a number
---@field b number
---@field c number

---@class Foo

---@class Bar
---@field ba number
---@field bb number
---@field bc number

---@class Bar
---@field bd number

---@type Foo|Bar
local x = {
	a = 1,
	b = 2,
	c = 3,
}

-- ==== case missing-fields-024 rule=field.missing source=missing-fields.lua#24
---@diagnostic disable: unused-local

---@class Foo
---@field a number
---@field b number
---@field c number

---@class Foo

---@class Bar
---@field ba number
---@field bb number
---@field bc number

---@class Bar
---@field bd number

---@type Foo|Bar
local x = { -- E
	a = 1,
	b = 2,
}

-- ==== case missing-fields-025 rule=field.missing source=missing-fields.lua#25
---@diagnostic disable: unused-local

---@class Foo
---@field a number
---@field b number
---@field c number

---@class Foo

---@class Bar
---@field ba number
---@field bb number
---@field bc number

---@class Bar
---@field bd number

---@type Foo|Bar
local x = { -- E
	ba = 1,
	bb = 2,
	bd = 4,
}

-- ==== case missing-fields-026 rule=field.missing source=missing-fields.lua#26
---@class A
---@field [1] string
---@field x number

---@type A
local t = {x = 1, ""}

-- ==== case missing-fields-027 rule=field.missing source=missing-fields.lua#27
---@class A
---@field [1] string
---@field x number

---@type A
local t = {x = 1} -- E

-- ==== case missing-fields-028 rule=field.missing source=missing-fields.lua#28
---@class A
---@field x number

---@class B: A

---@type B
local t = {} -- E

-- ==== case missing-fields-029 rule=field.missing source=missing-fields.lua#29
---@class A
---@field x number
---@field y number

---@class B: A

---@type B
local t = {y = 1} -- E

-- ==== case missing-fields-030 rule=field.missing source=missing-fields.lua#30
---@class A
---@field x number

---@class B: A
---@field y number

---@type B
local t = {y = 1} -- E

-- ==== case missing-fields-031 rule=field.missing source=missing-fields.lua#31
---@class A
---@field x? number

---@class B: A

---@type B
local t = {}

-- ==== case missing-fields-032 rule=field.missing source=missing-fields.lua#32
---@class A
---@field x? number
---@field y number

---@class B: A

---@type B
local t = {y = 1}

-- ==== case missing-fields-033 rule=field.missing source=missing-fields.lua#33
---@class A
---@field x? number

---@class B: A
---@field y number

---@type B
local t = {y = 1}

-- ==== case missing-fields-034 rule=field.missing source=missing-fields.lua#34
---@class A
---@field x number

---@class B: A

---@param b B
local function f(b) end

f {} -- E

-- ==== case missing-fields-035 rule=field.missing source=missing-fields.lua#35
---@class A
---@field x number
---@field y number

---@class B: A

---@param b B
local function f(b) end

f {y = 1} -- E

-- ==== case missing-fields-036 rule=field.missing source=missing-fields.lua#36
---@class A
---@field x number

---@class B: A
---@field y number

---@param b B
local function f(b) end

f {y = 1} -- E

-- ==== case missing-fields-037 rule=field.missing source=missing-fields.lua#37
---@class A
---@field x number

---@class (partial) B: A

---@type B
local t = {}

-- ==== case missing-fields-038 rule=field.missing source=missing-fields.lua#38
---@class A
---@field x number

---@class (partial) B: A
---@field y number

---@type B
local t = {} -- E

-- ==== case missing-fields-039 rule=field.missing source=missing-fields.lua#39
---@class A
---@field x number

---@class (partial) B: A
---@field y number

---@type B
local t = {y = 1}
