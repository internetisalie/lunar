package net.internetisalie.lunar.rocks.env

import net.internetisalie.lunar.settings.InterpreterMode
import net.internetisalie.lunar.settings.LuaProjectSettings

/** Phase 1: env-set state + legacy migration (TC-1, TC-2). */
@Suppress("DEPRECATION")
class HererocksEnvSetStateTest : EnvSettingsTestCase() {

    fun testLegacyEnvMigratesIntoListOnLoad() {
        val settings = LuaProjectSettings.getInstance(project)
        val legacy = HererocksEnvState(id = "A", directory = "/p/.lua", flavor = HererocksFlavor.PUC, luaVersion = "5.4")
        val incoming = LuaProjectSettings.State().also { it.hererocksEnv = legacy }

        settings.loadState(incoming)

        val loaded = LuaProjectSettings.getInstance(project)
        assertEquals(listOf(legacy), loaded.resolveAllEnvs())
        assertEquals("A", loaded.state.activeEnvId)
        assertNull("legacy field is consumed", loaded.state.hererocksEnv)
        assertEquals(legacy, loaded.activeEnv())
    }

    fun testMigrationIsIdempotent() {
        val settings = LuaProjectSettings.getInstance(project)
        val env = HererocksEnvState(id = "A", directory = "/p/.lua")
        val incoming = LuaProjectSettings.State().also {
            it.hererocksEnvs = mutableListOf(env)
            it.activeEnvId = "A"
            it.hererocksEnv = env
        }

        settings.loadState(incoming)

        assertEquals(1, settings.resolveAllEnvs().size)
        assertEquals("A", settings.state.activeEnvId)
    }

    fun testTwoEnvsRoundTripAndActiveResolves() {
        val settings = LuaProjectSettings.getInstance(project)
        val a = HererocksEnvState(id = "A", directory = "/p/a", luaVersion = "5.3")
        val b = HererocksEnvState(id = "B", directory = "/p/b", luaVersion = "5.4")
        val incoming = LuaProjectSettings.State().also {
            it.hererocksEnvs = mutableListOf(a, b)
            it.activeEnvId = "B"
        }

        settings.loadState(incoming)

        assertEquals(listOf(a, b), settings.resolveAllEnvs())
        assertEquals(b, settings.activeEnv())
    }

    fun testAddEnvSkipsDuplicateId() {
        val settings = LuaProjectSettings.getInstance(project)
        settings.loadState(LuaProjectSettings.State())
        val env = HererocksEnvState(id = "A", directory = "/p/a")
        settings.addEnv(env)
        settings.addEnv(env.copy(directory = "/other"))
        assertEquals(1, settings.resolveAllEnvs().size)
    }

    /** ROCKS-16: a legacy project that already has a bound env defaults to Managed on load. */
    fun testInterpreterModeMigratesBoundProjectToManaged() {
        val settings = LuaProjectSettings.getInstance(project)
        val env = HererocksEnvState(id = "A", directory = "/p/a")
        val incoming = LuaProjectSettings.State().also {
            it.hererocksEnvs = mutableListOf(env)
            it.activeEnvId = "A"
        }

        settings.loadState(incoming)

        assertEquals(InterpreterMode.HEREROCKS_MANAGED, settings.state.interpreterMode)
        assertTrue(settings.state.interpreterModeMigrated)
    }

    /** ROCKS-16: a legacy project with no bound env defaults to Explicit on load. */
    fun testInterpreterModeMigratesUnboundProjectToExplicit() {
        val settings = LuaProjectSettings.getInstance(project)

        settings.loadState(LuaProjectSettings.State())

        assertEquals(InterpreterMode.EXPLICIT, settings.state.interpreterMode)
        assertTrue(settings.state.interpreterModeMigrated)
    }

    /** ROCKS-16: once migrated, a persisted Explicit choice is not re-seeded from a bound env. */
    fun testInterpreterModeMigrationIsIdempotent() {
        val settings = LuaProjectSettings.getInstance(project)
        val env = HererocksEnvState(id = "A", directory = "/p/a")
        val incoming = LuaProjectSettings.State().also {
            it.hererocksEnvs = mutableListOf(env)
            it.activeEnvId = "A"
            it.interpreterMode = InterpreterMode.EXPLICIT
            it.interpreterModeMigrated = true
        }

        settings.loadState(incoming)

        assertEquals(InterpreterMode.EXPLICIT, settings.state.interpreterMode)
    }
}
