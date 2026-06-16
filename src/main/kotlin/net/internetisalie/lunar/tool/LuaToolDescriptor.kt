package net.internetisalie.lunar.tool

import com.intellij.openapi.util.SystemInfo
import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import java.io.File

/**
 * TOOL-00-02 de-risking spike: OS-specific tool filename descriptor.
 *
 * Discovery must try different binary filenames per OS — e.g. `luarocks` on POSIX but
 * `luarocks.bat` on Windows. This descriptor enumerates the candidate filenames for a tool
 * and resolves the first one found on `PATH` via
 * [com.intellij.execution.configurations.PathEnvironmentVariableUtil.findInPath] (returns `File?`, verified against
 * the 2026.1 platform source). This is the minimal mapping handed to TOOL-01.
 *
 * Scope note: this is a spike artifact. TOOL-01 will own the production registry/discovery;
 * the descriptor table below is the de-risked contract it consumes.
 */
enum class LuaToolType {
    LUAROCKS,
    LUACHECK,
    STYLUA,
}

data class LuaToolDescriptor(val toolType: LuaToolType, private val baseName: String) {
    /**
     * Candidate filenames discovery must try, most-specific first. On Windows a tool may be
     * shipped as a `.bat` shim (LuaRocks), a `.exe` (StyLua), or a `.cmd`; on POSIX it is the
     * bare name. `findInPath` itself appends `PATHEXT` entries on Windows, but listing explicit
     * candidates makes the spike's intent (and the TOOL-01 table) unambiguous.
     */
    fun candidates(windows: Boolean = SystemInfo.isWindows): List<String> =
        if (windows) {
            listOf("$baseName.bat", "$baseName.exe", "$baseName.cmd", baseName)
        } else {
            listOf(baseName)
        }

    /** Resolve the first candidate present on PATH, or `null` if none resolve. */
    fun resolveOnPath(): File? =
        candidates().firstNotNullOfOrNull { PathEnvironmentVariableUtil.findInPath(it) }

    companion object {
        val DESCRIPTORS: List<LuaToolDescriptor> = listOf(
            LuaToolDescriptor(LuaToolType.LUAROCKS, "luarocks"),
            LuaToolDescriptor(LuaToolType.LUACHECK, "luacheck"),
            LuaToolDescriptor(LuaToolType.STYLUA, "stylua"),
        )
    }
}
