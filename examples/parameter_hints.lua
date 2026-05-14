-- Example of Parameter Name Hints (SYNTAX-07-04)
-- This file demonstrates various scenarios where parameter name hints are displayed.

--- Basic Function Call
local function set_position(x, y, z)
    -- ...
end

-- Hints displayed: x: 10, y: 20, z: 30
set_position(10, 20, 30)

--- Suppression Heuristics
local function log(message)
    -- ...
end

-- Hint suppressed: only one parameter
log("hello")

local x, y = 10, 20
-- Hints suppressed: argument names match parameter names
set_position(x, y, 30)

--- Method (Colon) Calls
local Player = {}
function Player:move(posX, posY)
    -- ...
end

local p = Player
-- Hint suppressed: 'self' is the first parameter
-- Hints displayed: posX: 100, posY: 200
p:move(100, 200)

--- LuaCATS Annotations
---@param name string
---@param age number
---@param score number
local function register_user(name, age, score)
    -- ...
end

-- Hints displayed: name: "John", age: 30, score: 95
register_user("John", 30, 95)
