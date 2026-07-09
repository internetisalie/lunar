package net.internetisalie.lunar.rocks

import net.internetisalie.lunar.settings.LuaProjectSettings
import net.internetisalie.lunar.toolchain.model.LuaRegisteredTool
import net.internetisalie.lunar.toolchain.model.LuaToolHealth
import net.internetisalie.lunar.toolchain.model.Origin
import net.internetisalie.lunar.toolchain.registry.LuaKindOptionKeys
import net.internetisalie.lunar.toolchain.registry.ToolchainSettingsTestCase
import java.util.UUID

/**
 * TOOLING-05 Phase 2 — TC 7/8. [LuaRocksEnvironment] resolves the executable through the
 * TOOLING-01/02 stack ([net.internetisalie.lunar.toolchain.resolve.LuaToolResolver]) with a
 * nullable contract (no hardcoded `"luarocks"` default), and preserves the server precedence
 * (project override → app default kind option → none) + `withServer` injection unchanged.
 */
class LuaRocksEnvironmentTest : ToolchainSettingsTestCase() {

    override fun tearDown() {
        try {
            LuaProjectSettings.getInstance(project).state.rocksServerUrl = ""
        } finally {
            super.tearDown()
        }
    }

    // ── TC 7: resolver-backed executable + nullable contract ──────────────────

    fun `test TC7 resolves bound luarocks tool path`() {
        val luaRocks = seedLuaRocksAt("/tmp/tools/luarocks")
        settings.setBinding("luarocks", luaRocks.id)

        assertEquals("/tmp/tools/luarocks", LuaRocksEnvironment.resolveExecutable(project))
    }

    fun `test TC7 empty registry yields null without default path`() {
        assertNull(LuaRocksEnvironment.resolveExecutable(project))
    }

    // ── TC 8: server precedence + withServer injection ────────────────────────

    fun `test TC8 project server url wins and withServer prepends it`() {
        LuaProjectSettings.getInstance(project).state.rocksServerUrl = "https://x"

        val server = LuaRocksEnvironment.resolveServer(project)
        assertEquals("https://x", server)
        assertEquals(
            listOf("--server", "https://x", "search", "--porcelain", "q"),
            LuaRocksEnvironment.withServer(listOf("search", "--porcelain", "q"), server),
        )
    }

    fun `test TC8 app kind option is the default when no project override`() {
        registry.setKindOption(LuaKindOptionKeys.LUAROCKS_SERVER_URL, "https://app-default")

        assertEquals("https://app-default", LuaRocksEnvironment.resolveServer(project))
    }

    fun `test TC8 no server configured yields null and unchanged args`() {
        val args = listOf("search", "--porcelain", "q")
        assertNull(LuaRocksEnvironment.resolveServer(project))
        assertEquals(args, LuaRocksEnvironment.withServer(args, null))
    }

    private fun seedLuaRocksAt(path: String): LuaRegisteredTool {
        val model = LuaRegisteredTool(
            id = UUID.randomUUID().toString(),
            kindId = "luarocks",
            path = path,
            version = "3.0.0",
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
