package net.internetisalie.lunar.rocks.init

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.LuaLanguageLevel
import net.internetisalie.lunar.platform.LuaPlatform
import net.internetisalie.lunar.rocks.env.HererocksFlavor
import net.internetisalie.lunar.settings.InterpreterMode
import net.internetisalie.lunar.settings.LuaProjectSettings

/** ROCKS-17: wizard interpreter choice → project settings (Explicit interpreter vs. Managed env). */
class LuaRocksInterpreterInitializerTest : BasePlatformTestCase() {

    override fun tearDown() {
        try {
            val state = LuaProjectSettings.getInstance(project).state
            state.interpreterMode = InterpreterMode.EXPLICIT
            state.interpreter = null
            state.target = null
            state.interpreterModeMigrated = false
        } finally {
            super.tearDown()
        }
    }

    fun testExplicitPathSetsInterpreterAndTarget() {
        val settings = LuaRocksProjectSettings(
            name = "lib",
            flavor = HererocksFlavor.PUC,
            luaVersion = "5.1",
            provisionHererocks = false,
            interpreterPath = "/usr/bin/lua",
        )

        LuaRocksInterpreterInitializer.applySettings(project, settings)

        val state = LuaProjectSettings.getInstance(project).state
        assertEquals(InterpreterMode.EXPLICIT, state.interpreterMode)
        assertEquals("/usr/bin/lua", state.interpreter?.path)
        assertEquals(LuaPlatform.STANDARD, state.getTarget().platform)
        assertEquals("5.1", state.getTarget().version.label)
        assertEquals(LuaLanguageLevel.LUA51, state.languageLevel)
        assertTrue("wizard pins the migration flag", state.interpreterModeMigrated)
    }

    fun testExplicitPathWithNoInterpreterLeavesItNull() {
        val settings = LuaRocksProjectSettings(name = "lib", provisionHererocks = false, interpreterPath = "")

        LuaRocksInterpreterInitializer.applySettings(project, settings)

        val state = LuaProjectSettings.getInstance(project).state
        assertEquals(InterpreterMode.EXPLICIT, state.interpreterMode)
        assertNull(state.interpreter)
    }

    fun testProvisionSetsManagedModeAndTargetButNotInterpreter() {
        val settings = LuaRocksProjectSettings(
            name = "lib",
            flavor = HererocksFlavor.PUC,
            luaVersion = "5.5",
            provisionHererocks = true,
        )

        LuaRocksInterpreterInitializer.applySettings(project, settings)

        val state = LuaProjectSettings.getInstance(project).state
        assertEquals(InterpreterMode.HEREROCKS_MANAGED, state.interpreterMode)
        // Interpreter is derived later, when the provisioned env binds.
        assertNull(state.interpreter)
        assertEquals(LuaPlatform.STANDARD, state.getTarget().platform)
        assertEquals("5.5", state.getTarget().version.label)
        assertEquals(LuaLanguageLevel.LUA55, state.languageLevel)
        assertTrue(state.interpreterModeMigrated)
    }

    fun testLuaJitFlavorMapsToLuaJitTarget() {
        val settings = LuaRocksProjectSettings(
            name = "lib",
            flavor = HererocksFlavor.LUAJIT,
            luaVersion = "2.1",
            provisionHererocks = true,
        )

        LuaRocksInterpreterInitializer.applySettings(project, settings)

        val state = LuaProjectSettings.getInstance(project).state
        assertEquals(LuaPlatform.LUAJIT, state.getTarget().platform)
        assertEquals("2.1", state.getTarget().version.label)
        assertEquals(LuaLanguageLevel.LUA51, state.languageLevel)
    }
}
