-- LuaCATS conformance cases, adapted from LuaLS/lua-language-server test/diagnostics/return-type-mismatch.lua @ a58fc7b0 (MIT); see LICENSE-LuaLS

-- ==== case return-type-mismatch-001 rule=return.mismatch source=return-type-mismatch.lua#1
---@return number
function F()
    return true -- E
end

-- ==== case return-type-mismatch-002 rule=return.mismatch source=return-type-mismatch.lua#2
---@return number?
function F()
    return 1
end

-- ==== case return-type-mismatch-003 rule=return.mismatch source=return-type-mismatch.lua#3
---@return number?
function F()
    return nil
end

-- ==== case return-type-mismatch-004 rule=return.mismatch source=return-type-mismatch.lua#4
---@return number, number
local function f()
    return 1, 1
end

---@return number, boolean
function F()
    return f() -- E
end

-- ==== case return-type-mismatch-005 rule=return.mismatch source=return-type-mismatch.lua#5
---@return boolean, number
local function f()
    return true, 1
end

---@return number, boolean
function F()
    return f() -- E
end

-- ==== case return-type-mismatch-006 rule=return.mismatch source=return-type-mismatch.lua#6
---@return boolean, number?
local function f()
    return true, 1
end

---@return number, boolean
function F()
    return 1, f()
end

-- ==== case return-type-mismatch-007 rule=return.mismatch source=return-type-mismatch.lua#7
---@return number, number?
local function f()
    return 1, 1
end

---@return number, boolean, number
function F()
    return 1, f() -- E
end

-- ==== case return-type-mismatch-008 rule=return.mismatch source=return-type-mismatch.lua#8
---@class A
---@field x number?

---@return number
function F()
    ---@type A
    local t
    return t.x
end

-- ==== case return-type-mismatch-009 rule=return.mismatch source=return-type-mismatch.lua#9
---@class A
---@field x number?
local t = {}

---@return number
function F()
    return t.x
end

-- ==== case return-type-mismatch-010 rule=return.mismatch source=return-type-mismatch.lua#10
---@param ... number
local function f(...)
end

f(nil)

-- ==== case return-type-mismatch-011 rule=return.mismatch source=return-type-mismatch.lua#11
---@return number
function F()
    local n = 0
    if true then
        n = 1
    end
    return n
end

-- ==== case return-type-mismatch-012 rule=return.mismatch source=return-type-mismatch.lua#12
---@param x boolean
---@return number
---@overload fun(): boolean
local function f(x)
    if x then
        return 1
    else
        return false
    end
end

-- ==== case return-type-mismatch-013 rule=return.mismatch source=return-type-mismatch.lua#13
---@param x boolean
---@return number
---@overload fun()
local function f(x)
    if x then
        return 1
    else
        return
    end
end

-- ==== case return-type-mismatch-014 rule=return.mismatch source=return-type-mismatch.lua#14
---@param x boolean
---@return number
---@overload fun()
local function f(x)
    if x then
        return 1
    end
end

-- ==== case return-type-mismatch-015 rule=return.mismatch source=return-type-mismatch.lua#15
---@param x boolean
---@return number
---@overload fun(): boolean, boolean
local function f(x)
    if x then
        return 1
    else
        return false, false
    end
end

-- ==== case return-type-mismatch-016 rule=return.mismatch source=return-type-mismatch.lua#16
---@type fun():number
local function f()
    return true -- E
end
