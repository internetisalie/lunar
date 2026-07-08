package net.internetisalie.lunar.toolchain.model

data class LuaEnvironmentState(
    var id: String = "",
    var name: String = "",
    var rootDir: String = "",
    var toolIds: MutableList<String> = mutableListOf()
)
