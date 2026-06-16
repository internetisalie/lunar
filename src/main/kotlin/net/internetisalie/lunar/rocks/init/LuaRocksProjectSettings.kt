package net.internetisalie.lunar.rocks.init

enum class RockKind { SINGLE_ROCK, WORKSPACE }
enum class RockType { LIBRARY, APPLICATION }

data class LuaRocksProjectSettings(
    var name: String = "",
    var kind: RockKind = RockKind.SINGLE_ROCK,
    var type: RockType = RockType.LIBRARY,
    var luaVersions: String = "5.1,5.2,5.3,5.4",
    var loaderSetup: Boolean = false,
    var bustedConfig: Boolean = false,
    var makefile: Boolean = false,
    var workspaceName: String = "",
    var initialRocks: List<String> = emptyList(),
)
