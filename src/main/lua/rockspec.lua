--- Export a rockspec manifest to JSON.
--- ```sh
--- export LUNAR_LUA_PATH_TEMPLATE="<path-to-plugin-lua-directory>/?/init.lua;<path-to-plugin-lua-directory>/?.lua"
--- lua rockspec.lua luacheck-dev-1.rockspec
--- ```

local lunar_path = os.getenv("LUNAR_LUA_PATH_TEMPLATE")
package.path = lunar_path .. ";" .. package.path

-- Execute the requested manifest
-- No _ENV substitution in Lua 5.1
dofile(arg[1])

-- Export the fields defined
-- in the rockspec schema.
require("lunar.export").json(_G, {
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