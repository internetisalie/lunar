package net.internetisalie.lunar.analysis.luacheck

import net.internetisalie.lunar.toolchain.model.LuaRegisteredTool
import net.internetisalie.lunar.toolchain.model.LuaToolHealth
import net.internetisalie.lunar.toolchain.model.Origin
import net.internetisalie.lunar.toolchain.registry.LuaKindOptionKeys
import net.internetisalie.lunar.toolchain.registry.ToolchainSettingsTestCase
import java.util.UUID

/**
 * TOOLING-05 Phase 1 — TC 1/2. `newLuaCheckCommandLine` resolves the binary via the
 * [net.internetisalie.lunar.toolchain.resolve.LuaToolResolver] and reads arguments from the
 * TOOLING-02 kind-scoped option; an empty registry yields `null` (no hardcoded default path).
 */
class LuaCheckCommandLineTest : ToolchainSettingsTestCase() {

    fun `test TC1 resolves bound tool and merges configured plus default arguments`() {
        val luaCheck = seedToolAt("luacheck", "/tmp/tools/luacheck")
        settings.setBinding("luacheck", luaCheck.id)
        settings.setKindOption(LuaKindOptionKeys.LUACHECK_ARGUMENTS, "--std max")

        val workDir = myFixture.tempDirFixture.getFile(".")!!
        val cmd = newLuaCheckCommandLine(project, "a.lua", workDir)!!

        assertEquals("/tmp/tools/luacheck", cmd.exePath)
        val params = cmd.parametersList.list
        assertTrue(params.containsAll(listOf("--std", "max", "--codes", "--ranges", "a.lua")))
        assertEquals("a.lua", params.last())
    }

    fun `test TC2 empty registry yields null without default path`() {
        val workDir = myFixture.tempDirFixture.getFile(".")!!
        assertNull(newLuaCheckCommandLine(project, "a.lua", workDir))
    }

    fun `test dedupePairs keeps repeated value tokens across distinct pairs`() {
        val input = listOf("--ignore", "611", "--max-line-length", "611", "--codes", "--ranges")
        assertEquals(input, dedupePairs(input))
    }

    fun `test dedupePairs collapses duplicate lone flag and duplicate pair`() {
        val input = listOf("--codes", "--codes", "--std", "max", "--std", "max")
        assertEquals(listOf("--codes", "--std", "max"), dedupePairs(input))
    }

    private fun seedToolAt(kindId: String, path: String): LuaRegisteredTool {
        val model = LuaRegisteredTool(
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
        registry.registerProvisioned(model)
        return model
    }
}
