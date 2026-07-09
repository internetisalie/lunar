package net.internetisalie.lunar.toolchain.health

import net.internetisalie.lunar.toolchain.model.LuaRuntimeInfo
import net.internetisalie.lunar.toolchain.model.LuaToolHealth

data class LuaToolCheckResult(
    val health: LuaToolHealth,
    val version: String?,
    val luaVersion: String?,
    val runtime: LuaRuntimeInfo?
)
