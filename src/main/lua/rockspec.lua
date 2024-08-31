--- Export a rockspec manifest to JSON.
-- ```sh
-- lua rockspec.lua luacheck-dev-1.rockspec
-- ```
local luna = require "lunajson"

-- Execute the requested manifest
dofile(arg[1])

-- Set the required exports
local exports ={
    "rockspec_format",
    "package",
    "version",
    "description",
    "supported_platforms",
    "dependencies",
    "build_dependencies",
    "external_dependencies",
    "test_dependencies",
    "source",
    "build",
    "test",
}

-- Collect the specified names from the _G table into the data table
local data = {}
local key
for _, key in ipairs(exports) do
    local value = _G[key]
    if value ~= nil then
        data[key] = value
    end
end

-- Print the data table formatted as a JSON object
print(luna.encode(data))
