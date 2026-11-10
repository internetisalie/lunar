-- LuaCATS conformance cases, adapted from LuaLS/lua-language-server test/diagnostics/undefined-field.lua @ a58fc7b0 (MIT); see LICENSE-LuaLS

-- ==== case undefined-field-001 rule=field.undefined source=undefined-field.lua#1
---@class Foo
---@field field1 integer
local mt = {}
function mt:Constructor()
    self.field2 = 1
end
function mt:method1() return 1 end
function mt.method2() return 2 end

---@class Bar: Foo
---@field field4 integer
local mt2 = {}

---@type Foo
local v
print(v.field1 + 1)
print(v.field2 + 1)
print(v.field3 + 1) -- E
print(v:method1())
print(v.method2())
print(v:method3()) -- E

---@type Bar
local v2
print(v2.field1 + 1)
print(v2.field2 + 1)
print(v2.field3 + 1) -- E
print(v2.field4 + 1)
print(v2:method1())
print(v2.method2())
print(v2:method3()) -- E

local v3 = {}
print(v3.abc)

---@class Bar2
local mt3
function mt3:method() return 1 end
print(mt3:method())

-- ==== case undefined-field-002 rule=field.undefined source=undefined-field.lua#2
---@class Foo
local Foo
function Foo:method1() end

---@type Foo
local v
v:method1()
v:method2() -- doc.class.name -- E

-- ==== case undefined-field-003 rule=field.undefined source=undefined-field.lua#3
---@class Foo
local Foo
function Foo:method1() end
---@class Bar: Foo
local Bar
function Bar:method3() end

---@type Bar
local v
v:method1()
v:method2() -- doc.class.name -- E
v:method3()

-- ==== case undefined-field-004 rule=field.undefined source=undefined-field.lua#4
---@class Foo
local Foo
function Foo:method1() end
Foo:method2() -- doc.class -- E
Foo:method2() -- doc.class -- E

-- ==== case undefined-field-005 rule=field.undefined source=undefined-field.lua#5
local Foo
function Foo:method1()
    return Foo:method2() -- table
end

-- ==== case undefined-field-006 rule=field.undefined source=undefined-field.lua#6
---@class Foo
local mt
function mt:method1()
    mt.method2() -- doc.class -- E
    self:method1()
    return self.method2() -- doc.class.name -- E
end

-- ==== case undefined-field-007 rule=field.undefined source=undefined-field.lua#7
---@class Foo
local mt
function mt:method1() end

---@class Bar
local mt2
function mt2:method2() end

---@type Foo
local v
---@type Bar
local v2
v2 = v
v2:method1()
v2:method2() -- E

-- ==== case undefined-field-008 rule=field.undefined source=undefined-field.lua#8
---@type table
T1 = {}
print(T1.f1)
---@type tablelib
T2 = {}
print(T2.f2) -- E

-- ==== case undefined-field-009 rule=field.undefined source=undefined-field.lua#9
---@type string|table
local n

print(n.x)

-- ==== case undefined-field-010 rule=field.undefined source=undefined-field.lua#10
---@type 'x'
local t

local n = t:upper()

-- ==== case undefined-field-011 rule=field.undefined source=undefined-field.lua#11
---@alias A 'x'

---@type A
local t

local n = t:upper()

-- ==== case undefined-field-012 rule=field.undefined source=undefined-field.lua#12
---@enum X
X = {
    A = 1,
    B = 2
}

print(X.C) -- E

-- ==== case undefined-field-013 rule=field.undefined source=undefined-field.lua#13
---@class SomeClass
---@field someExistingVar integer
local SomeClass = {}

---@generic T: SomeClass
---@param self T
---@return T
function SomeClass:xyz()
    print(self.someExistingVar)
    print(self.doesNotExist) -- E
    return self
end

-- ==== case undefined-field-014 rule=field.undefined source=undefined-field.lua#14
---@class A
---@field a integer

---@class B
---@field b integer

---@generic T: A|B
---@param x T
local function f(x)
    print(x.a, x.b, x.c) -- E
end

-- ==== case undefined-field-015 rule=field.undefined source=undefined-field.lua#15
---@class A
local A = {}

-- self-referential constraint must not recurse forever
---@generic T: T
---@param self T
function A:f()
    print(self.x) -- E
end
