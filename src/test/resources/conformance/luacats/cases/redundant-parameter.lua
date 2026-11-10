-- LuaCATS conformance cases, adapted from LuaLS/lua-language-server test/diagnostics/redundant-parameter.lua @ a58fc7b0 (MIT); see LICENSE-LuaLS

-- ==== case redundant-parameter-001 rule=arity.tooMany source=redundant-parameter.lua#1
local function x(a, b)
    return a, b
end
x(1, 2, 3) -- E

-- ==== case redundant-parameter-002 rule=arity.tooMany source=redundant-parameter.lua#2
local function x(a, b, ...)
    return a, b, ...
end
x(1, 2, 3, 4, 5)

-- ==== case redundant-parameter-003 rule=arity.tooMany source=redundant-parameter.lua#3
---@type fun(a, b, ...)
local x
x(1, 2, 3, 4, 5)

-- ==== case redundant-parameter-004 rule=arity.tooMany source=redundant-parameter.lua#4
local m = {}
function m:x(a, b)
    return a, b
end
m:x(1, 2, 3) -- E

-- ==== case redundant-parameter-005 rule=arity.tooMany source=redundant-parameter.lua#5
local m = {}
function m:x(a, b)
    return a, b
end
m.x(m, 2, 3, 4) -- E

-- ==== case redundant-parameter-006 rule=arity.tooMany source=redundant-parameter.lua#6
local m = {}
function m.x(a, b)
    return a, b
end
m:x(1, 2, 3, 4) -- E

-- ==== case redundant-parameter-007 rule=arity.tooMany source=redundant-parameter.lua#7
local m = {}
function m.x()
end
m:x()

-- ==== case redundant-parameter-008 rule=arity.tooMany source=redundant-parameter.lua#8
local function f(a, b)
    return a, b
end
f(1, 2, 3, 4) -- E

-- ==== case redundant-parameter-009 rule=arity.tooMany source=redundant-parameter.lua#9
local mt = {}
function mt:f(a, b)
    return a, b
end
mt.f(mt, 2, 3, 4) -- E

-- ==== case redundant-parameter-010 rule=arity.tooMany source=redundant-parameter.lua#10
local mt = {}
function mt.f(a, b)
    return a, b
end
mt:f(1, 2, 3, 4) -- E

-- ==== case redundant-parameter-011 rule=arity.tooMany source=redundant-parameter.lua#11
local mt = {}
function mt:f(a, b)
    return a, b
end
mt:f(1, 2, 3, 4) -- E

-- ==== case redundant-parameter-012 rule=arity.tooMany source=redundant-parameter.lua#12
local function f(a, b, ...)
    return a, b, ...
end
f(1, 2, 3, 4)

-- ==== case redundant-parameter-013 rule=arity.tooMany source=redundant-parameter.lua#13
local _ = next({}, 1, 2) -- E
print(1, 2, 3, 4, 5)

-- ==== case redundant-parameter-014 rule=arity.tooMany source=redundant-parameter.lua#14
local function f(callback)
    callback(1, 2, 3)
end
f(function () end)

-- ==== case redundant-parameter-015 rule=arity.tooMany source=redundant-parameter.lua#15
local f = load('')
if f then
    f(1, 2, 3)
end

-- ==== case redundant-parameter-016 rule=arity.tooMany source=redundant-parameter.lua#16
local m = {}
function m.open()
end

m:open()

-- ==== case redundant-parameter-017 rule=arity.tooMany source=redundant-parameter.lua#17
local m = {}
function m:open()
end

m.open(m)

-- ==== case redundant-parameter-018 rule=arity.tooMany source=redundant-parameter.lua#18
table.insert({}, 1, 2, 3) -- E

-- ==== case redundant-parameter-019 rule=arity.tooMany source=redundant-parameter.lua#19
---@overload fun(...)
local function f() end

f(1)

-- ==== case redundant-parameter-020 rule=arity.tooMany source=redundant-parameter.lua#20
function F() end

---@param x boolean
function F(x) end

F(k())

-- ==== case redundant-parameter-021 rule=arity.tooMany source=redundant-parameter.lua#21
local function f()
    return 1, 2, 3
end

local function k()
end

k(f()) -- E

-- ==== case redundant-parameter-022 rule=arity.tooMany source=redundant-parameter.lua#22
---@diagnostic disable: unused-local
local function f()
    return 1, 2, 3
end

---@param x integer
local function k(x)
end

k(f())

-- ==== case redundant-parameter-023 rule=arity.tooMany source=redundant-parameter.lua#23
---@meta

---@param x fun()
local function f1(x)
end

---@return fun()
local function f2()
end

f1(f2())

-- ==== case redundant-parameter-024 rule=arity.tooMany source=redundant-parameter.lua#24
---@meta

---@type fun():integer
local f

---@param x integer
local function foo(x) end

foo(f())

-- ==== case redundant-parameter-025 rule=arity.tooMany source=redundant-parameter.lua#25
---@meta
---@diagnostic disable: duplicate-set-field
---@class A
local m = {}

function m.ff() end

function m.ff(x) end

m.ff(1)
