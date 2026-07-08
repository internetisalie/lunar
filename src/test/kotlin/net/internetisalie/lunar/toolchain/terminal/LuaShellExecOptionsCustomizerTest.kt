package net.internetisalie.lunar.toolchain.terminal

import net.internetisalie.lunar.toolchain.exec.LuaExecutionEnvironmentBuilder
import net.internetisalie.lunar.toolchain.model.LuaRegisteredTool
import net.internetisalie.lunar.toolchain.model.LuaToolHealth
import net.internetisalie.lunar.toolchain.model.Origin
import net.internetisalie.lunar.toolchain.registry.ToolchainSettingsTestCase
import java.nio.file.Path
import java.util.UUID

/**
 * TC 17 (TOOLING-03-12): with builder dirs `[d1, d2]`, [LuaShellExecOptionsCustomizer] must feed the
 * front-inserting `prependEntryToPATH` sink in reverse (`d2` then `d1`) so the highest-priority dir
 * stays first on the resulting PATH.
 *
 * `MutableShellExecOptions` is a `sealed interface` and its only implementation is `internal` to
 * `org.jetbrains.plugins.terminal`, so a recording fake cannot implement it from this module. Per the
 * plan's sanctioned fallback, the reverse-ordering is verified through the extracted
 * [LuaShellExecOptionsCustomizer.prependInReverse] seam that the production `customizeExecOptions`
 * delegates to; the builder is seeded with two real tools so `pathPrependDirs()` returns `[d1, d2]`.
 */
class LuaShellExecOptionsCustomizerTest : ToolchainSettingsTestCase() {

    fun testPrependsDirsInReverseSoHighestPriorityStaysFirst() {
        bindTool("lua", "/d1/lua")
        bindTool("luacheck", "/d2/luacheck")

        val builder = LuaExecutionEnvironmentBuilder.getInstance(project)
        builder.invalidate()
        assertEquals(listOf(Path.of("/d1"), Path.of("/d2")), builder.pathPrependDirs())

        val recorded = mutableListOf<Path>()
        LuaShellExecOptionsCustomizer.prependInReverse(builder.pathPrependDirs()) { recorded.add(it) }

        assertEquals(listOf(Path.of("/d2"), Path.of("/d1")), recorded)
    }

    private fun bindTool(kindId: String, path: String) {
        val tool = LuaRegisteredTool(
            id = UUID.randomUUID().toString(),
            kindId = kindId,
            path = path,
            version = "1.0.0",
            luaVersion = null,
            runtime = null,
            origin = Origin.MANUAL,
            environmentId = null,
            health = LuaToolHealth(
                fileExists = true,
                executable = true,
                probeOk = true,
                probedAtMtime = 1L,
                reason = null,
            ),
        )
        registry.registerProvisioned(tool)
        settings.setBinding(kindId, tool.id)
    }
}
