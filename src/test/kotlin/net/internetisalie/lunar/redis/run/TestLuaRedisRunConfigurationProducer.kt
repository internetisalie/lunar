package net.internetisalie.lunar.redis.run

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.platform.LuaPlatform
import net.internetisalie.lunar.platform.target.PlatformVersionRegistry
import net.internetisalie.lunar.platform.target.Target
import net.internetisalie.lunar.settings.LuaProjectSettings

/**
 * Target-gating coverage for [LuaRedisRunConfigurationProducer] (design §7, TC-PROD-1).
 *
 * The producer offers a Redis Script config for a `.lua` file only when the project target is
 * [LuaPlatform.REDIS]; on a plain Lua target it returns `false` and never fabricates a config.
 */
class TestLuaRedisRunConfigurationProducer : BasePlatformTestCase() {

    private fun setTarget(platform: LuaPlatform) {
        val version = PlatformVersionRegistry.getVersions(platform).first()
        LuaProjectSettings.getInstance(project).state.setTarget(Target(platform, version))
    }

    /** TC-PROD-1 (positive): a `.lua` file with a REDIS target creates a config from context. */
    fun testProducerOffersForRedisTarget() {
        setTarget(LuaPlatform.REDIS)
        val psiFile = myFixture.configureByText("script.lua", "return 1")

        val context = ConfigurationContext(psiFile)
        val producer = LuaRedisRunConfigurationProducer()
        val fromContext = producer.createConfigurationFromContext(context)

        assertNotNull("expected a Redis Script config for a REDIS target", fromContext)
        val config = fromContext?.configuration as LuaRedisRunConfiguration
        assertEquals(psiFile.virtualFile.path, config.scriptPath)
        assertEquals("Redis Script: script.lua", config.name)
    }

    /** TC-PROD-1 (negative): a `.lua` file with a plain LUA/STANDARD target yields no Redis config. */
    fun testProducerDeclinesForNonRedisTarget() {
        setTarget(LuaPlatform.STANDARD)
        val psiFile = myFixture.configureByText("script.lua", "return 1")

        val context = ConfigurationContext(psiFile)
        val producer = LuaRedisRunConfigurationProducer()
        val fromContext = producer.createConfigurationFromContext(context)

        assertNull("Redis producer must decline on a non-Redis target", fromContext)
    }
}
