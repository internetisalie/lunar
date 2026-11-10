-- LuaCATS conformance cases, adapted from LuaLS/lua-language-server test/diagnostics/missing-return.lua @ a58fc7b0 (MIT); see LICENSE-LuaLS

-- ==== case missing-return-001 rule=return.missing source=missing-return.lua#1
---@type fun():number
local function f()
end -- E

-- ==== case missing-return-002 rule=return.missing source=missing-return.lua#2
---@type fun():number?
local function f()
end

-- ==== case missing-return-003 rule=return.missing source=missing-return.lua#3
---@type fun():...
local function f()
end

-- ==== case missing-return-004 rule=return.missing source=missing-return.lua#4
---@return number
function F()
    X = 1 -- E
end

-- ==== case missing-return-005 rule=return.missing source=missing-return.lua#5
local A
---@return number
function F()
    if A then
        return 1
    end -- E
end

-- ==== case missing-return-006 rule=return.missing source=missing-return.lua#6
local A, B
---@return number
function F()
    if A then
        return 1
    elseif B then
        return 2
    end -- E
end

-- ==== case missing-return-007 rule=return.missing source=missing-return.lua#7
local A, B
---@return number
function F()
    if A then
        return 1
    elseif B then
        return 2
    else
        return 3
    end
end

-- ==== case missing-return-008 rule=return.missing source=missing-return.lua#8
local A, B
---@return number
function F()
    if A then
    elseif B then
        return 2
    else
        return 3
    end -- E
end

-- ==== case missing-return-009 rule=return.missing source=missing-return.lua#9
---@return any
function F()
    X = 1
end

-- ==== case missing-return-010 rule=return.missing source=missing-return.lua#10
---@return any, number
function F()
    X = 1 -- E
end

-- ==== case missing-return-011 rule=return.missing source=missing-return.lua#11
---@return number, any
function F()
    X = 1 -- E
end

-- ==== case missing-return-012 rule=return.missing source=missing-return.lua#12
---@return any, any
function F()
    X = 1
end

-- ==== case missing-return-013 rule=return.missing source=missing-return.lua#13
local A
---@return number
function F()
    for _ = 1, 10 do
        if A then
            return 1
        end
    end
    error('should not be here')
end

-- ==== case missing-return-014 rule=return.missing source=missing-return.lua#14
local A
---@return number
function F()
    while true do
        if A then
            return 1
        end
    end
end

-- ==== case missing-return-015 rule=return.missing source=missing-return.lua#15
local A
---@return number
function F()
    while A do
        if A then
            return 1
        end
    end -- E
end

-- ==== case missing-return-016 rule=return.missing source=missing-return.lua#16
local A
---@return number
function F()
    while A do
        if A then
            return 1
        else
            return 2
        end
    end
end

-- ==== case missing-return-017 rule=return.missing source=missing-return.lua#17
---@return number?
function F()

end

-- ==== case missing-return-018 rule=return.missing source=missing-return.lua#18
---@generic T
---@param t T
---@return T
function F(t)
	return t
end

-- ==== case missing-return-019 rule=return.missing source=missing-return.lua#19
---@generic T
---@param t T
---@return T?
function F(t)

end
