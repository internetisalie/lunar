package net.internetisalie.lunar.settings

import net.internetisalie.lunar.lang.LuaLanguageLevel
import net.internetisalie.lunar.platform.LuaPlatform
import net.internetisalie.lunar.platform.target.PlatformVersionRegistry
import net.internetisalie.lunar.platform.target.Target
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LuaProjectSettingsTest {
    @Test
    fun `getTarget returns migrated target from languageLevel and platform`() {
        val state = LuaProjectSettings.State()
        state.platform = LuaPlatform.STANDARD
        state.languageLevel = LuaLanguageLevel.LUA52

        val target = state.getTarget()

        assertEquals(LuaPlatform.STANDARD, target.platform)
        assertEquals("5.2", target.version.label)
    }

    @Test
    fun `getTarget returns stored targetState if present`() {
        val state = LuaProjectSettings.State()
        val registry = PlatformVersionRegistry
        val version = registry.getVersions(LuaPlatform.REDIS)[0]
        val expectedTarget = Target(LuaPlatform.REDIS, version)

        state.setTarget(expectedTarget)
        val target = state.getTarget()

        assertEquals(LuaPlatform.REDIS, target.platform)
        assertEquals(version.label, target.version.label)
    }

    @Test
    fun `setTarget updates targetState and synchronizes legacy fields`() {
        val state = LuaProjectSettings.State()
        val registry = PlatformVersionRegistry
        val version = registry.getVersions(LuaPlatform.LUAJIT)[1]
        val target = Target(LuaPlatform.LUAJIT, version)

        state.setTarget(target)

        assertEquals(LuaPlatform.LUAJIT, state.platform)
        assertEquals(LuaLanguageLevel.LUA51, state.languageLevel)
        assertEquals(target, state.getTarget())
    }

    @Test
    fun `targetState round-trip preserves target through serialization`() {
        val state = LuaProjectSettings.State()
        val registry = PlatformVersionRegistry
        val versions = registry.getVersions(LuaPlatform.TARANTOOL)
        val original = Target(LuaPlatform.TARANTOOL, versions[0])

        state.setTarget(original)

        val targetState = state.targetState
        assertEquals(LuaPlatform.TARANTOOL, targetState?.platform)
        assertEquals(versions[0].label, targetState?.versionLabel)

        val restored = targetState?.toTarget()
        assertEquals(original, restored)
    }

    @Test
    fun `migration from legacy languageLevel LUA51 with STANDARD platform`() {
        val state = LuaProjectSettings.State()
        state.platform = LuaPlatform.STANDARD
        state.languageLevel = LuaLanguageLevel.LUA51

        val target = state.getTarget()

        assertEquals(LuaPlatform.STANDARD, target.platform)
        assertEquals("5.1", target.version.label)
        assertEquals("lua51", target.getLuacheckStd())
    }

    @Test
    fun `migration from legacy languageLevel LUA53 with STANDARD platform`() {
        val state = LuaProjectSettings.State()
        state.platform = LuaPlatform.STANDARD
        state.languageLevel = LuaLanguageLevel.LUA53

        val target = state.getTarget()

        assertEquals(LuaPlatform.STANDARD, target.platform)
        assertEquals("5.3", target.version.label)
        assertEquals("lua53", target.getLuacheckStd())
    }

    @Test
    fun `migration from legacy languageLevel LUA54 with STANDARD platform`() {
        val state = LuaProjectSettings.State()
        state.platform = LuaPlatform.STANDARD
        state.languageLevel = LuaLanguageLevel.LUA54

        val target = state.getTarget()

        assertEquals(LuaPlatform.STANDARD, target.platform)
        assertEquals("5.4", target.version.label)
        assertEquals("lua54", target.getLuacheckStd())
    }

    @Test
    fun `migration with unsupported languageLevel falls back to default`() {
        val state = LuaProjectSettings.State()
        state.platform = LuaPlatform.LUAU
        state.languageLevel = LuaLanguageLevel.LUA51  // LUAU doesn't support LUA51

        val target = state.getTarget()

        assertEquals(LuaPlatform.LUAU, target.platform)
        // Should get the default version for LUAU
        assertTrue(target.version.label.isNotEmpty())
    }

    @Test
    fun `getTarget idempotent - multiple calls return same migrated target`() {
        val state = LuaProjectSettings.State()
        state.platform = LuaPlatform.STANDARD
        state.languageLevel = LuaLanguageLevel.LUA52

        val target1 = state.getTarget()
        val target2 = state.getTarget()

        assertEquals(target1, target2)
        assertEquals(LuaPlatform.STANDARD, target2.platform)
        assertEquals("5.2", target2.version.label)
    }

    @Test
    fun `all platform versions can be migrated and serialized`() {
        val registry = PlatformVersionRegistry

        for (platform in registry.platforms()) {
            val versions = registry.getVersions(platform)
            for (version in versions) {
                val target = Target(platform, version)
                val state = LuaProjectSettings.State()

                state.setTarget(target)

                assertEquals(platform, state.platform)
                val restored = state.getTarget()
                assertEquals(target, restored)
            }
        }
    }

    @Test
    fun `redis platform migration scenarios`() {
        val state = LuaProjectSettings.State()
        val registry = PlatformVersionRegistry
        val redisVersions = registry.getVersions(LuaPlatform.REDIS)

        for (version in redisVersions) {
            val target = Target(LuaPlatform.REDIS, version)
            state.setTarget(target)

            val restored = state.getTarget()
            assertEquals(LuaPlatform.REDIS, restored.platform)
            assertEquals(version.label, restored.version.label)
            assertEquals(LuaLanguageLevel.LUA51, restored.getImplicitLanguageLevel())
        }
    }

    @Test
    fun `default target creation when registry lookup fails`() {
        val state = LuaProjectSettings.State()
        // Even if something goes wrong, toTarget() should fall back to default
        val target = state.getTarget()
        assertEquals(LuaPlatform.STANDARD, target.platform)
    }
}
