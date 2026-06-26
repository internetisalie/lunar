package net.internetisalie.lunar.rocks.init

object LuaRocksTemplates {

    fun rockspec(name: String, type: RockType): String {
        val buildSection = when (type) {
            RockType.LIBRARY -> """
build = {
   type = "builtin",
   modules = {
      ["$name"] = "src/$name.lua",
   },
}
""".trimStart()
            RockType.APPLICATION -> """
build = {
   type = "builtin",
   modules = { ["$name"] = "src/main.lua" },
   install = { bin = { ["$name"] = "src/main.lua" } },
}
""".trimStart()
        }
        return """
rockspec_format = "3.0"
package = "$name"
version = "scm-1"
source = {
   url = "*** please add a source URL ***",
}
description = {
   summary = "$name",
   license = "MIT",
}
dependencies = {
   "lua >= 5.1",
}
$buildSection""".trimStart()
    }

    fun setupLua(): String = """
-- setup.lua: prepend locally-installed rocks to the module search paths.
local version = _VERSION:match("%d+%.%d+")
package.path  = "lua_modules/share/lua/" .. version .. "/?.lua;"
            ..  "lua_modules/share/lua/" .. version .. "/?/init.lua;" .. package.path
package.cpath = "lua_modules/lib/lua/"   .. version .. "/?.so;" .. package.cpath
""".trimStart()

    fun mainModule(name: String, type: RockType): String = when (type) {
        RockType.LIBRARY -> """
local $name = {}
function $name.hello()
   return "hello from $name"
end
return $name
""".trimStart()
        RockType.APPLICATION -> """
local function main(...)
   print("hello from $name")
end
main(...)
""".trimStart()
    }

    fun makefile(name: String): String = """
.PHONY: build test lint format coverage rocks clean

build:
	luarocks make

test:
	busted

lint:
	luacheck src spec

format:
	stylua src spec

coverage:
	busted --coverage
	luacov

rocks:
	luarocks install --local $name-scm-1.rockspec

clean:
	rm -rf lua_modules .luarocks luacov.stats.out luacov.report.out
""".trimStart()

    fun bustedSpec(name: String): String = """
describe("$name", function()
   it("loads", function()
      assert.is_table(require("$name"))
   end)
end)
""".trimStart()

    fun gitignore(): String = """
/lua_modules/
/.luarocks/
*.src.rock
*.rock
luacov.stats.out
luacov.report.out
""".trimStart()
}
