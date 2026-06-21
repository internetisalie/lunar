package net.internetisalie.lunar.rocks.init

enum class RockType { LIBRARY, APPLICATION }

data class LuaRocksProjectSettings(
    var name: String = "",
    var type: RockType = RockType.LIBRARY,
    var luaVersions: String = "5.1,5.2,5.3,5.4",
    var loaderSetup: Boolean = false,
    var bustedConfig: Boolean = false,
    var makefile: Boolean = false,
)
