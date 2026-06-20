package net.internetisalie.lunar.run.test

import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.platform.LuaInterpreter
import net.internetisalie.lunar.platform.LuaInterpreterFamily

class LuaTestRunConfigurationTest : BasePlatformTestCase() {

    fun testConfigurationProperties() {
        val targetProject = project
        val type = LuaTestRunConfigurationType.getInstance()
        val factory = type.configurationFactories[0]
        val config = factory.createTemplateConfiguration(targetProject) as LuaTestRunConfiguration

        // Default values
        assertEquals(LuaTestFramework.BUSTED, config.testFramework)
        assertEquals("FILE", config.testTargetType)
        assertEquals("", config.testTarget)

        // Mutating fields
        config.testFramework = LuaTestFramework.LUNITY
        config.testTargetType = "DIRECTORY"
        config.testTarget = "/path/to/tests"
        config.extraTestArguments = "--verbose"
        config.failedTestNames = "test1,test2"

        // Env vars
        val envs = mapOf("VAR1" to "value1", "VAR2" to "value2")
        config.environmentVariables = EnvironmentVariablesData.create(envs, true)

        // Verification of getters/setters
        assertEquals(LuaTestFramework.LUNITY, config.testFramework)
        assertEquals("DIRECTORY", config.testTargetType)
        assertEquals("/path/to/tests", config.testTarget)
        assertEquals("--verbose", config.extraTestArguments)
        assertEquals("test1,test2", config.failedTestNames)
        assertEquals(envs, config.environmentVariables?.envs)
        assertTrue(config.environmentVariables?.isPassParentEnvs ?: false)

        // Options serialization/deserialization verification
        val state = config.options
        assertNotNull(state)
        assertEquals("LUNITY", state.testFramework)
        assertEquals("DIRECTORY", state.testTargetType)
        assertEquals("/path/to/tests", state.testTarget)
        assertEquals("--verbose", state.extraTestArguments)
        assertEquals("test1,test2", state.failedTestNames)
        assertEquals(envs, state.environmentVariables)
    }

    fun testRunLineMarkerProvider() {
        myFixture.configureByText("my_spec.lua", """
            describe("suite", function()
                it("test", function()
                end)
            end)
            function test_lunity()
            end
        """.trimIndent())
        
        val gutters = myFixture.findAllGutters()
        val runGutters = gutters.filter { it.icon == com.intellij.icons.AllIcons.RunConfigurations.TestState.Run }
        assertEquals(3, runGutters.size)
    }

    fun testProducerFromContextBustedSpec() {
        val psiFile = myFixture.configureByText("my_spec.lua", """
            describe("suite", function()
                it("test", function()
                end)
            end)
        """.trimIndent())
        
        val context = com.intellij.execution.actions.ConfigurationContext(psiFile)
        val producer = LuaTestRunConfigurationProducer()
        val configFromContext = producer.createConfigurationFromContext(context) ?: throw AssertionError("Expected config")
        val config = configFromContext.configuration as LuaTestRunConfiguration
        
        assertEquals(LuaTestFramework.BUSTED, config.testFramework)
        assertEquals("FILE", config.testTargetType)
        assertEquals(psiFile.virtualFile.path, config.testTarget)
        assertEquals("Lua Tests: my_spec.lua", config.name)
    }

    fun testProducerFromContextBustedDescribe() {
        val psiFile = myFixture.configureByText("my_spec.lua", """
            describe("suite", function()
                it("test", function()
                end)
            end)
        """.trimIndent())
        
        val describeOffset = psiFile.text.indexOf("describe")
        val element = psiFile.findElementAt(describeOffset)!!
        val context = com.intellij.execution.actions.ConfigurationContext(element)
        val producer = LuaTestRunConfigurationProducer()
        val configFromContext = producer.createConfigurationFromContext(context) ?: throw AssertionError("Expected config")
        val config = configFromContext.configuration as LuaTestRunConfiguration
        
        assertEquals(LuaTestFramework.BUSTED, config.testFramework)
        assertEquals("PATTERN", config.testTargetType)
        assertEquals("suite", config.testTarget)
        assertEquals("Lua Tests: suite", config.name)
    }

    fun testProducerFromContextLunity() {
        val psiFile = myFixture.configureByText("test_math.lua", """
            function test_addition()
            end
        """.trimIndent())
        
        val context = com.intellij.execution.actions.ConfigurationContext(psiFile)
        val producer = LuaTestRunConfigurationProducer()
        val configFromContext = producer.createConfigurationFromContext(context) ?: throw AssertionError("Expected config")
        val config = configFromContext.configuration as LuaTestRunConfiguration
        
        assertEquals(LuaTestFramework.LUNITY, config.testFramework)
        assertEquals("FILE", config.testTargetType)
        assertEquals(psiFile.virtualFile.path, config.testTarget)
    }

    fun testValidationFailsWhenTargetEmpty() {
        val targetProject = project
        val type = LuaTestRunConfigurationType.getInstance()
        val factory = type.configurationFactories[0]
        val config = factory.createTemplateConfiguration(targetProject) as LuaTestRunConfiguration

        config.interpreter = LuaInterpreter("/usr/bin/lua5.4", LuaInterpreterFamily.UNKNOWN_PRODUCT)
        config.testTarget = "" // Empty target

        try {
            config.checkConfiguration()
            fail("Expected RuntimeConfigurationException")
        } catch (e: Exception) {
            // Expected
        }
    }
}
