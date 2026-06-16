package net.internetisalie.lunar.tool.health

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import net.internetisalie.lunar.tool.LuaTool
import net.internetisalie.lunar.tool.LuaToolManager

/**
 * Diagnostic snapshot helper (TOOL-03-05).
 *
 * Writes one line per registered tool to the IDE log so users and support can diagnose
 * "which tools does Lunar see, and are they healthy?" without opening the settings UI.
 *
 * Exposed via the "Lua: Report Tool Status" action and called automatically after
 * [LuaToolHealthMonitor.revalidateAll].
 */
object LuaToolDiagnostics {

    private val log = logger<LuaToolDiagnostics>()

    /**
     * Writes a tool-inventory snapshot to the IDE log at INFO level.
     *
     * Format per line:
     * ```
     * [TOOL-DIAG] LUACHECK /usr/bin/luacheck v=0.26.0 lua= valid=true reason="OK 0.26.0"
     * ```
     *
     * @param project Optional project context (used only for project binding resolution in the
     *   future; the current implementation logs the global inventory).
     */
    fun logSnapshot(project: Project?) {
        val tools: List<LuaTool> = LuaToolManager.getInstance().getTools()
        if (tools.isEmpty()) {
            log.info("[TOOL-DIAG] No tools registered.")
            return
        }
        for (tool in tools) {
            log.info(
                "[TOOL-DIAG] ${tool.type} ${tool.path} " +
                    "v=${tool.version.ifEmpty { "-" }} " +
                    "lua=${tool.luaVersion.ifEmpty { "-" }} " +
                    "valid=${tool.isValid} " +
                    "reason=\"${tool.lastCheckReason.ifEmpty { "(not checked)" }}\""
            )
        }
    }
}
