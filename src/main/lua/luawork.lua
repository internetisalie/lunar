--- Export a luawork manifest to JSON.
-- ```sh
-- lua luawork.lua .luawork
-- ```
local luna = require "lunajson"

-- Execute the requested manifest
if (1 == #arg) then
    dofile(arg[1])
end

-- Set the required exports
local exports ={
    "package.path",
    "package.cpath",
}

local function getField (f)
    local v = _G    -- start with the table of globals
    for w in string.gmatch(f, "[%w_]+") do
        v = v[w]
    end
    return v
end

-- Collect the specified names from the _G table into the data table
local data = {}
for _, key in ipairs(exports) do
    local value = getField(key)
    if value ~= nil then
        data[key] = value
    end
end

-- Print the data table formatted as a JSON object
print(luna.encode(data))
