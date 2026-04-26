package net.internetisalie.lunar.platform

import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import net.internetisalie.lunar.lang.LuaIcons
import net.internetisalie.lunar.lang.LuaLanguageLevel
import java.nio.file.Path
import javax.swing.Icon

data class LuaInterpreter(
    var path: String? = null,
    var banner: String? = null,
    var product: String? = null,
    var version: String? = null,
    var platform: String? = null,
    var languageLevel: String? = null,
) {
    constructor(other: LuaInterpreter) : this(
        other.path,
        other.banner,
        other.product,
        other.version,
        other.platform,
        other.languageLevel
    )

    constructor(executable: Path) : this(
        executable.toString(),
        null,
        LuaInterpreterFamily.UNKNOWN_PRODUCT,
        null,
        null,
        null,
    )

    val family: LuaInterpreterFamily?
        get() {
            if (product == null) return LuaInterpreterFamily.Companion.UNKNOWN_INTERPRETER
            return LuaInterpreterFamily.Companion.FAMILIES[product]
        }

    val familyOrUnknown: LuaInterpreterFamily
        get() {
            return if (product == null || !LuaInterpreterFamily.Companion.FAMILIES.containsKey(product)) LuaInterpreterFamily.Companion.UNKNOWN_INTERPRETER
            else LuaInterpreterFamily.Companion.FAMILIES[product]!!
        }

    val valid: Boolean
        get() = product != LuaInterpreterFamily.INVALID_PRODUCT

    val executable: VirtualFile?
        get() {
            val interpreterFileName = path?.trim(' ') ?: return null
            if (interpreterFileName.isEmpty()) return null
            val interpreterPath = Path.of(interpreterFileName)
            return VfsUtil.findFile(interpreterPath, true)
        }

    override fun toString(): String {
        return path ?: ""
    }
}

class LuaInterpreterFamily(
    val interpreterName: String,
    val executableName: String,
    val productName: String,
    val binaryType: BinaryType,
    val platform: LuaPlatform,
    val leveler: ((String) -> LuaLanguageLevel?),
    val argExecCode: String?,
    val argLoadLib: String?
) {
    enum class BinaryType {
        SystemBinary,
        JavaJar,
    }

    val icon: Icon
        get() = LuaIcons.FILE

    val platformExecutableName: String
        get() {
            if (binaryType == BinaryType.JavaJar
                || !SystemInfo.isWindows
            ) return executableName

            return "$executableName.exe"
        }

    fun languageLevel(productVersion : String) : LuaLanguageLevel? {
        return leveler.invoke(productVersion)
    }

    companion object {
        const val UNKNOWN_PRODUCT = "unknown"
        const val INVALID_PRODUCT = "invalid"

        val FAMILIES: Map<String, LuaInterpreterFamily> = listOf(
            LuaInterpreterFamily(
                interpreterName = "Lua",
                executableName = "lua",
                productName = "Lua",
                binaryType = BinaryType.SystemBinary,
                argExecCode = "-e",
                argLoadLib = "-l",
                platform = LuaPlatform.STANDARD,
                leveler = { version ->
                    when {
                        version.startsWith("5.1") -> LuaLanguageLevel.LUA51
                        version.startsWith("5.2") -> LuaLanguageLevel.LUA52
                        version.startsWith("5.3") -> LuaLanguageLevel.LUA53
                        version.startsWith("5.4") -> LuaLanguageLevel.LUA54
                        else -> LuaLanguageLevel.LUA50
                    }
                }
            ),
            LuaInterpreterFamily(
                interpreterName = "LuaJIT",
                executableName = "luajit",
                productName = "LuaJIT",
                binaryType = BinaryType.SystemBinary,
                argExecCode = "-e",
                argLoadLib = "-l",
                platform = LuaPlatform.STANDARD,
                leveler = { LuaLanguageLevel.LUA51 },
            ),
            LuaInterpreterFamily(
                interpreterName = "Tarantool",
                executableName = "tarantool",
                productName = "Tarantool",
                binaryType = BinaryType.SystemBinary,
                argExecCode = null,
                argLoadLib = null,
                platform = LuaPlatform.TARANTOOL,
                leveler = { LuaLanguageLevel.LUA51 }
            ),
        ).associateBy { it.productName }

        val UNKNOWN_INTERPRETER: LuaInterpreterFamily = LuaInterpreterFamily(
            interpreterName = "Unknown",
            executableName = "",
            productName = UNKNOWN_PRODUCT,
            binaryType = BinaryType.SystemBinary,
            platform = LuaPlatform.STANDARD,
            argExecCode = null,
            argLoadLib = null,
            leveler = { null },
        )

        fun findByInterpreterName(interpreterName: String): LuaInterpreterFamily? {
            return FAMILIES.firstNotNullOfOrNull { if (it.value.interpreterName == interpreterName) it.value else null }
        }

        fun find(productName: String, executableName: String): LuaInterpreterFamily? {
            return FAMILIES.firstNotNullOfOrNull {
                if (it.value.productName != productName) return null
                if (isGlob(it.value.executableName)) {
                    if (!matchesGlob(
                            it.value.executableName,
                            executableName
                        )
                    ) return null
                } else if (executableName != it.value.platformExecutableName) return null
                it.value
            }
        }
    }
}
