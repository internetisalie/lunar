/*
 * Copyright 2016 Jon S Akhtar (Sylvanaar)
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package net.internetisalie.lunar.settings

import com.intellij.openapi.util.SystemInfo
import net.internetisalie.lunar.util.LuaGlobUtil

class LuaInterpreterFamily {
    enum class BinaryType {
        SystemBinary,
        JavaJar,
    }

    @JvmField
    var interpreterName: String
    var executableName: String
    @JvmField
    var familyNameMatch: String
    var binaryType: BinaryType
    var argExecCode: String? = null
    var argLoadLib: String? = null

    constructor(
        interpreterName: String,
        executableName: String,
        familyNameMatch: String,
        binaryType: BinaryType,
        argExecCode: String?,
        argLoadLib: String?
    ) {
        this.interpreterName = interpreterName
        this.executableName = executableName
        this.familyNameMatch = familyNameMatch
        this.binaryType = binaryType
        this.argExecCode = argExecCode
        this.argLoadLib = argLoadLib
    }

    constructor(
        interpreterName: String,
        executableName: String,
        familyNameMatch: String,
        binaryType: BinaryType
    ) {
        this.interpreterName = interpreterName
        this.executableName = executableName
        this.familyNameMatch = familyNameMatch
        this.binaryType = binaryType
    }

    val platformExecutableName: String
        get() {
            if (binaryType == BinaryType.JavaJar
                || !SystemInfo.isWindows
            ) return executableName

            return "$executableName.exe"
        }

    fun hasLoadLib(): Boolean {
        return (argLoadLib != null)
    }

    fun hasExecCode(): Boolean {
        return (argExecCode != null)
    }

    fun key() : String {
        return familyNameMatch
    }

    companion object {
        val UNKNOWN_KEY = "unknown"
        val INVALID_KEY = "invalid"

        @JvmField
        val FAMILIES: Map<String, LuaInterpreterFamily> = listOf(
            LuaInterpreterFamily("Lua", "lua", "Lua", BinaryType.SystemBinary, "-e", "-l"),
//            LuaInterpreterFamily("LuaJIT", "luajit", "LuaJIT", BinaryType.SystemBinary, "-e", "-l"),
//            LuaInterpreterFamily("Tarantool", "tarantool", "Tarantool", BinaryType.SystemBinary),
//            LuaInterpreterFamily("LOVE", "love", "LOVE", BinaryType.SystemBinary),
//            LuaInterpreterFamily("Torch", "qlua", "Lua", BinaryType.SystemBinary, "-e", "-l"),
//            LuaInterpreterFamily("LuaJ JSE", "luaj-jse*.jar", "Luaj-jse", BinaryType.JavaJar),
//            LuaInterpreterFamily("LuaJ JME", "luaj-jme*.jar", "Luaj-jme", BinaryType.JavaJar),
        ).associateBy { it.familyNameMatch }

        @JvmField
        val INVALID_INTERPRETER: LuaInterpreterFamily = LuaInterpreterFamily("Invalid", "", "", BinaryType.SystemBinary)
        @JvmField
        val UNKNOWN_INTERPRETER: LuaInterpreterFamily = LuaInterpreterFamily("Unknown", "", "", BinaryType.SystemBinary)

        fun findByName(s: String): LuaInterpreterFamily? {
            return FAMILIES.firstNotNullOfOrNull { if (it.value.interpreterName == s) it.value else null  }
        }

        @JvmStatic
        fun findByMatch(s: String, e: String): LuaInterpreterFamily? {
            return FAMILIES.firstNotNullOfOrNull {
                if (it.value.familyNameMatch != s) return null
                if (LuaGlobUtil.isGlob(it.value.executableName)) {
                    if (!LuaGlobUtil.matchesGlob(
                            it.value.executableName,
                            e
                        )
                    ) return null
                } else if (e != it.value.platformExecutableName) return null
                it.value
            }
        }
    }
}
