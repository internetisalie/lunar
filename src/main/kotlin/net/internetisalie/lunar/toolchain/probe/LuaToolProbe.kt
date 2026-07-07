package net.internetisalie.lunar.toolchain.probe

import net.internetisalie.lunar.toolchain.model.LuaRuntimeInfo
import net.internetisalie.lunar.toolchain.model.LuaToolKind
import java.nio.file.Path

interface LuaToolProbe {
    /**
     * Probes a tool's version, compatibility, and optional runtime info.
     * Must be called on a background thread.
     */
    fun probe(kind: LuaToolKind, binaryPath: Path): LuaToolProbeResult

    companion object {
        fun getInstance(): LuaToolProbe {
            return com.intellij.openapi.application.ApplicationManager.getApplication()
                .getService(LuaToolProbe::class.java)
        }
    }
}

data class LuaToolProbeResult(
    val ok: Boolean,
    val version: String?,
    val luaVersion: String?,
    val runtime: LuaRuntimeInfo?,
    val failure: String?
)
