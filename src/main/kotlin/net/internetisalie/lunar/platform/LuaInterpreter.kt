package net.internetisalie.lunar.platform

import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Path
import com.intellij.openapi.util.SystemInfo
import net.internetisalie.lunar.lang.LuaIcons
import net.internetisalie.lunar.util.LuaGlobUtil
import javax.swing.Icon
import kotlin.collections.get

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

class LuaInterpreterFamily {
    enum class BinaryType {
        SystemBinary,
        JavaJar,
    }

    var interpreterName: String
    var executableName: String
    var productName: String
    var binaryType: BinaryType
    var argExecCode: String? = null
    var argLoadLib: String? = null

    val icon: Icon
        get() = LuaIcons.FILE

    constructor(
        interpreterName: String,
        executableName: String,
        productName: String,
        binaryType: BinaryType,
        argExecCode: String?,
        argLoadLib: String?
    ) {
        this.interpreterName = interpreterName
        this.executableName = executableName
        this.productName = productName
        this.binaryType = binaryType
        this.argExecCode = argExecCode
        this.argLoadLib = argLoadLib
    }

    constructor(
        interpreterName: String,
        executableName: String,
        productName: String,
        binaryType: BinaryType
    ) {
        this.interpreterName = interpreterName
        this.executableName = executableName
        this.productName = productName
        this.binaryType = binaryType
    }

    val platformExecutableName: String
        get() {
            if (binaryType == BinaryType.JavaJar
                || !SystemInfo.isWindows
            ) return executableName

            return "$executableName.exe"
        }

    companion object {
        const val UNKNOWN_PRODUCT = "unknown"
        const val INVALID_PRODUCT = "invalid"

        val FAMILIES: Map<String, LuaInterpreterFamily> = listOf(
            LuaInterpreterFamily("Lua", "lua", "Lua", BinaryType.SystemBinary, "-e", "-l"),
            LuaInterpreterFamily("LuaJIT", "luajit", "LuaJIT", BinaryType.SystemBinary, "-e", "-l"),
            LuaInterpreterFamily("Tarantool", "tarantool", "Tarantool", BinaryType.SystemBinary),
        ).associateBy { it.productName }

        val UNKNOWN_INTERPRETER: LuaInterpreterFamily = LuaInterpreterFamily("Unknown", "", UNKNOWN_PRODUCT, BinaryType.SystemBinary)

        fun findByInterpreterName(interpreterName: String): LuaInterpreterFamily? {
            return FAMILIES.firstNotNullOfOrNull { if (it.value.interpreterName == interpreterName) it.value else null }
        }

        fun find(productName: String, executableName: String): LuaInterpreterFamily? {
            return FAMILIES.firstNotNullOfOrNull {
                if (it.value.productName != productName) return null
                if (LuaGlobUtil.isGlob(it.value.executableName)) {
                    if (!LuaGlobUtil.matchesGlob(
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
