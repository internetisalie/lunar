--- Initialize a debugging session.
--- ```sh
--- export LUNAR_LUA_PATH_TEMPLATE="<path-to-plugin-lua-directory>/?/init.lua;<path-to-plugin-lua-directory>/?.lua"
--- export LUA_INIT="@<path-to-this-file>"
--- export LUNAR_DEBUGGER_PACKAGE="mobdebug"
--- lua <path-to-entrypoint-to-debug>
--- ```

local lunar_path = os.getenv("LUNAR_LUA_PATH_TEMPLATE")
package.path = lunar_path .. ";" .. package.path
require("lunar.debug").start()
