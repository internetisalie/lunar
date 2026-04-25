package net.internetisalie.lunar.run

import net.internetisalie.lunar.BaseDocumentTest
import kotlin.test.Test
import kotlin.test.assertEquals

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
}
