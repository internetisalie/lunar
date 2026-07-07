package net.internetisalie.lunar.toolchain.model

import net.internetisalie.lunar.lang.LuaLanguageLevel
import net.internetisalie.lunar.platform.LuaPlatform

data class LuaRegisteredTool(
    val id: String,
    val kindId: String,
    val path: String,
    val version: String?,
    val luaVersion: String?,
    val runtime: LuaRuntimeInfo?,
    val origin: Origin,
    val environmentId: String?,
    val health: LuaToolHealth
)

enum class Origin {
    DISCOVERED,
    MANUAL,
    PROVISIONED
}

data class LuaToolHealth(
    val fileExists: Boolean,
    val executable: Boolean,
    val probeOk: Boolean?,
    val probedAtMtime: Long?,
    val reason: String?
)

val LuaRegisteredTool.isUsable: Boolean
    get() = health.fileExists && health.executable && health.probeOk != false

data class LuaRuntimeInfo(
    val product: String,
    val version: String,
    val languageLevel: LuaLanguageLevel,
    val platform: LuaPlatform,
    val banner: String
)
