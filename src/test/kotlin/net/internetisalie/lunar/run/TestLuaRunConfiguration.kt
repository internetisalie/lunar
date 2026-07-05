package net.internetisalie.lunar.run

import net.internetisalie.lunar.BaseDocumentTest
import net.internetisalie.lunar.platform.LuaInterpreter
import net.internetisalie.lunar.settings.LuaProjectSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TestLuaRunConfiguration : BaseDocumentTest() {

    @Test
    fun testOptionsPersistence() {
        val project = myFixture.project
        val configType = LuaRunConfigurationType()
        val factory = LuaRunConfigurationFactory(configType)
        val config = LuaRunConfiguration(project, factory, "Test Config")

        config.scriptName = "myscript.lua"
        config.workingDirectory = "/home/user/work"
        config.programArguments = "--arg1 val1"

        assertEquals("myscript.lua", config.scriptName)
        assertEquals("/home/user/work", config.workingDirectory)
        assertEquals("--arg1 val1", config.programArguments)
    }

    /** ROCKS-16 follow-up: an unset config resolves to the project interpreter; explicit wins. */
    @Test
    fun testResolveInterpreterFallsBackToProjectInterpreter() {
        val project = myFixture.project
        val settings = LuaProjectSettings.getInstance(project)
        val original = settings.state.interpreter
        try {
            settings.state.interpreter = LuaInterpreter(path = "/env/.lua/bin/lua")
            val config = LuaRunConfiguration(project, LuaRunConfigurationFactory(LuaRunConfigurationType()), "cfg")

            // No explicit interpreter on the config -> falls back to the project interpreter.
            assertNull(config.interpreter)
            assertEquals("/env/.lua/bin/lua", config.resolveInterpreter()?.path)

            // An explicit config interpreter takes precedence over the project one.
            config.interpreter = LuaInterpreter(path = "/usr/bin/lua")
            assertEquals("/usr/bin/lua", config.resolveInterpreter()?.path)
        } finally {
            settings.state.interpreter = original
        }
    }
}
