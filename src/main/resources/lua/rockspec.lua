--- Export a rockspec manifest to JSON.
--- ```sh
--- export LUNAR_LUA_PATH_TEMPLATE="<path-to-plugin-lua-directory>/?/init.lua;<path-to-plugin-lua-directory>/?.lua"
--- lua rockspec.lua luacheck-dev-1.rockspec
--- ```

local lunar_path = os.getenv("LUNAR_LUA_PATH_TEMPLATE")
package.path = lunar_path .. ";" .. package.path

local rockspec_path = arg[1]

-- Run the rockspec in a sandbox that separates the fields it declares from the
-- standard globals. Reads fall through to _G (so a rockspec may reference the
-- stdlib while evaluating), but every top-level assignment lands directly in
-- `manifest`. Exporting from `manifest` — never _G — keeps stdlib tables such as
-- `package` (a table full of functions) from leaking in when the rockspec omits
-- that field, which otherwise crashes the JSON encoder.
local manifest = setmetatable({}, { __index = _G })

local chunk, load_error
if setfenv then
    -- Lua 5.1: load, then bind the chunk's environment.
    chunk, load_error = loadfile(rockspec_path)
    if chunk then
        setfenv(chunk, manifest)
    end
else
    -- Lua 5.2+: pass the environment as the chunk's _ENV upvalue.
    chunk, load_error = loadfile(rockspec_path, "t", manifest)
end
if not chunk then
    error(load_error)
end
chunk()

-- Export the fields defined in the rockspec schema.
require("lunar.export").json(manifest, {
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
    "deploy",
    "hooks"
})
