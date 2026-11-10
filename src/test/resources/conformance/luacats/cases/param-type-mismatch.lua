-- LuaCATS conformance cases, adapted from LuaLS/lua-language-server test/diagnostics/param-type-mismatch.lua @ a58fc7b0 (MIT); see LICENSE-LuaLS

-- ==== case param-type-mismatch-001 rule=arg source=param-type-mismatch.lua#1
---@param x number
local function f(x) end

f(true) -- E

-- ==== case param-type-mismatch-002 rule=arg source=param-type-mismatch.lua#2
---@class A

---@param n A
local function f(n)
end

---@class B
local a = {}

---@type A?
a.x = XX

f(a.x)

-- ==== case param-type-mismatch-003 rule=arg source=param-type-mismatch.lua#3
---@alias A string|boolean

---@param x string|boolean
local function f(x) end

---@type A
local x

f(x)

-- ==== case param-type-mismatch-004 rule=arg source=param-type-mismatch.lua#4
---@alias A string|boolean

---@param x A
local function f(x) end

---@type string|boolean
local x

f(x)

-- ==== case param-type-mismatch-005 rule=arg source=param-type-mismatch.lua#5
---@param b boolean
local function f(b)
end

---@type boolean
local t

if t then
    f(t)
end

-- ==== case param-type-mismatch-006 rule=arg source=param-type-mismatch.lua#6
---@enum A
local t = {
    x = 1,
    y = 2,
}

---@param x A
local function f(x)
end

f(t) -- E
f(t.x)
f(1)
f(3) -- E

-- ==== case param-type-mismatch-007 rule=arg source=param-type-mismatch.lua#7
---@enum A
local t = {
    x = { h = 1 },
    y = { h = 2 },
}

---@param x A
local function f(x)
end

f(t.x)
f(t.y)
f({ h = 1 }) -- E

-- ==== case param-type-mismatch-008 rule=arg source=param-type-mismatch.lua#8
---@enum(key) A
local t = {
    x = 1,
    ['y'] = 2,
}

---@param x A
local function f(x)
end

f('x')
f('y')
f('z') -- E

-- ==== case param-type-mismatch-009 rule=arg source=param-type-mismatch.lua#9
---@generic T: string | boolean | table
---@param x T
---@return T
local function f(x)
    return x
end

f(1) -- E

-- ==== case param-type-mismatch-010 rule=arg source=param-type-mismatch.lua#10
---@param opts {a:number, b:number}
local function foo(opts)

end

---@param opts {a:number, b:number}
local function bar(opts)
    foo(opts)
end

-- ==== case param-type-mismatch-011 rule=arg source=param-type-mismatch.lua#11
---@param opts {a:number, b:number}
local function foo(opts)

end

---@param opts {c:number, d:number}
local function bar(opts)
    foo(opts)  -- this should raise linting error -- E
end

-- ==== case param-type-mismatch-012 rule=arg source=param-type-mismatch.lua#12
---@param opts {[number]: boolean}
local function foo(opts)

end

---@param opts {[1]: boolean}
local function bar(opts)
    foo(opts)
end

-- ==== case param-type-mismatch-013 rule=arg source=param-type-mismatch.lua#13
---@generic T
---@param v1 T
---@param v2 T|table
local function func(v1, v2)
end

func('hello', 'world')

-- ==== case param-type-mismatch-014 rule=arg source=param-type-mismatch.lua#14
---@generic T1, T2, T3, T4, T5
---@param f fun(): T1?, T2?, T3?, T4?, T5?
---@return T1?, T2?, T3?, T4?, T5?
local function foo(f)
    return f()
end

local a, b = foo(function()
    return 1
end)

-- ==== case param-type-mismatch-015 rule=arg source=param-type-mismatch.lua#15
---@generic T1, T2, T3, T4, T5
---@param f fun(): T1|nil, T2|nil, T3|nil, T4|nil, T5|nil
---@return T1?, T2?, T3?, T4?, T5?
local function foo(f)
    return f()
end

local a, b = foo(function()
    return 1
end)

-- ==== case param-type-mismatch-016 rule=arg source=param-type-mismatch.lua#16
---@param v integer
---@return boolean
local function is_string(v)
    return type(v) == 'string'
end

print(is_string(3))

-- ==== case param-type-mismatch-017 rule=arg source=param-type-mismatch.lua#17
---@param p integer|string
local function get_val(p)
    local is_number = type(p) == 'number'
    return is_number and p or p
end

get_val('hi')

-- ==== case param-type-mismatch-018 rule=arg source=param-type-mismatch.lua#18
---@class Class
local Class = {}

---@param source string
function Class.staticCreator(source)

end

Class.staticCreator(true) -- E
Class:staticCreator() -- Expecting a waring -- E

-- ==== case param-type-mismatch-019 rule=arg source=param-type-mismatch.lua#19
---@class A

---@class B : A

---@class C : B

---@class D : B

---@param x A
local function func(x) end

---@type C|D
local var
func(var)

-- ==== case param-type-mismatch-020 rule=arg source=param-type-mismatch.lua#20
---@class MyClass
---@overload fun(x : string) : MyClass
local MyClass = {}

local w = MyClass(1) -- E

-- ==== case param-type-mismatch-021 rule=arg source=param-type-mismatch.lua#21
---@enum(key) A
local t1 = {
    x = 1,
}

---@enum(key) A
local t2 = {
    y = 1,
}

---@param v A
local function f(v) end

f 'x'
f 'y'

-- ==== case param-type-mismatch-022 rule=arg source=param-type-mismatch.lua#22
---@class A
---@field x string
---@field y number

local a = {x = "", y = 0}

---@param a A
function f(a) end

f(a)
