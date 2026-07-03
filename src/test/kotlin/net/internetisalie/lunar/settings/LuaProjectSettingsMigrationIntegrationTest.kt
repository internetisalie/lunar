package net.internetisalie.lunar.settings

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.LuaLanguageLevel
import net.internetisalie.lunar.platform.LuaPlatform
import net.internetisalie.lunar.platform.target.PlatformVersionRegistry
import net.internetisalie.lunar.platform.target.Target

class LuaProjectSettingsMigrationIntegrationTest : BasePlatformTestCase() {

    fun testScenario1StandardLua51() {
        val settings = LuaProjectSettings.getInstance(project)
        val state = LuaProjectSettings.State()
        state.languageLevel = LuaLanguageLevel.LUA51

        settings.loadState(state)

        val target = settings.state.getTarget()
        assertEquals(LuaPlatform.STANDARD, target.platform)
        assertEquals("5.1", target.version.label)
        assertEquals(LuaLanguageLevel.LUA51, settings.state.languageLevel)
    }

    fun testScenario2RedisNoLang() {
        val settings = LuaProjectSettings.getInstance(project)
        val state = LuaProjectSettings.State()
        state.languageLevel = LuaLanguageLevel.LUA51
        val redisVersion = PlatformVersionRegistry.findVersion(LuaPlatform.REDIS, LuaLanguageLevel.LUA51.version)
            ?: PlatformVersionRegistry.defaultVersion(LuaPlatform.REDIS)!!
        state.setTarget(Target(LuaPlatform.REDIS, redisVersion))

        settings.loadState(state)
        
        val target = settings.state.getTarget()
        assertEquals(LuaPlatform.REDIS, target.platform)
        // Redis should use its default version (5) for LUA51
        assertEquals("5", target.version.label)
        assertEquals(LuaLanguageLevel.LUA51, settings.state.languageLevel)
    }


    fun testScenario3UnknownVersion() {
        val settings = LuaProjectSettings.getInstance(project)
        val state = LuaProjectSettings.State()
        val targetState = LuaProjectSettings.TargetState()
        targetState.platform = LuaPlatform.STANDARD
        targetState.versionLabel = "99.9"
        state.target = targetState

        settings.loadState(state)

        val target = settings.state.getTarget()
        assertEquals(LuaPlatform.STANDARD, target.platform)
        // Should fall back to default version for STANDARD (5.1)
        assertEquals("5.1", target.version.label)
    }

    fun testScenario4Tarantool() {
        val settings = LuaProjectSettings.getInstance(project)
        val state = LuaProjectSettings.State()
        state.languageLevel = LuaLanguageLevel.LUA51
        val tarantoolVersion = PlatformVersionRegistry.findVersion(LuaPlatform.TARANTOOL, LuaLanguageLevel.LUA51.version)
            ?: PlatformVersionRegistry.defaultVersion(LuaPlatform.TARANTOOL)!!
        state.setTarget(Target(LuaPlatform.TARANTOOL, tarantoolVersion))

        settings.loadState(state)

        val target = settings.state.getTarget()
        assertEquals(LuaPlatform.TARANTOOL, target.platform)
        assertEquals("2.10", target.version.label)
    }

}

