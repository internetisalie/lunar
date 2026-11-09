--- Export a rockspec manifest to JSON.
--- ```sh
--- export LUNAR_LUA_PATH_TEMPLATE="<path-to-plugin-lua-directory>/?/init.lua;<path-to-plugin-lua-directory>/?.lua"
--- lua rockspec.lua luacheck-dev-1.rockspec
--- ```

local lunar_path = os.getenv("LUNAR_LUA_PATH_TEMPLATE")
package.path = lunar_path .. ";" .. package.path

local rockspec_path = arg[1]

-- Evaluate the rockspec as declarative data, the way LuaRocks does
-- (`luarocks.core.persist.load_into_table`): in an empty environment, so it can
-- reach no standard library at all (no os, io, require, load or dofile). A read
-- of any global yields nil; every top-level assignment lands in `manifest`
-- (BUG-50).
local manifest = {}

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
