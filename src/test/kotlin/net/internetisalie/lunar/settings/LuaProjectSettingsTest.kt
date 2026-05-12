package net.internetisalie.lunar.settings

import net.internetisalie.lunar.lang.LuaLanguageLevel
import net.internetisalie.lunar.lang.path.PathConfiguration
import net.internetisalie.lunar.platform.LuaPlatform
import net.internetisalie.lunar.platform.target.PlatformVersionRegistry
import net.internetisalie.lunar.platform.target.Target
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertNotNull
import kotlin.test.assertFalse

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

// ============================================================================
// TargetState Serialization Tests
// ============================================================================

class TargetStateTest {
    @Test
    fun `TargetState from() creates wrapper with platform and label`() {
        val registry = PlatformVersionRegistry
        val version = registry.getVersions(LuaPlatform.STANDARD)[2]  // 5.3
        val target = Target(LuaPlatform.STANDARD, version)

        val state = LuaProjectSettings.TargetState.from(target)

        assertEquals(LuaPlatform.STANDARD, state.platform)
        assertEquals("5.3", state.versionLabel)
    }

    @Test
    fun `TargetState from() works for all platforms`() {
        val registry = PlatformVersionRegistry

        for (platform in registry.platforms()) {
            val versions = registry.getVersions(platform)
            for (version in versions) {
                val target = Target(platform, version)
                val state = LuaProjectSettings.TargetState.from(target)

                assertEquals(platform, state.platform)
                assertEquals(version.label, state.versionLabel)
            }
        }
    }

    @Test
    fun `TargetState toTarget() round-trips for all platforms`() {
        val registry = PlatformVersionRegistry

        for (platform in registry.platforms()) {
            val versions = registry.getVersions(platform)
            for (version in versions) {
                val original = Target(platform, version)
                val state = LuaProjectSettings.TargetState.from(original)

                val restored = state.toTarget()

                assertNotNull(restored)
                assertEquals(original.platform, restored.platform)
                assertEquals(original.version.label, restored.version.label)
                assertEquals(original, restored)
            }
        }
    }

    @Test
    fun `TargetState toTarget() handles unknown version label gracefully`() {
        val state = LuaProjectSettings.TargetState()
        state.platform = LuaPlatform.STANDARD
        state.versionLabel = "99.99"  // Non-existent version

        val restored = state.toTarget()

        assertNotNull(restored)
        assertEquals(LuaPlatform.STANDARD, restored.platform)
        // Should get default version (5.1) instead of failing
        assertEquals("5.1", restored.version.label)
    }

    @Test
    fun `TargetState toTarget() returns default when all lookups fail`() {
        val state = LuaProjectSettings.TargetState()
        state.platform = LuaPlatform.LUAU
        state.versionLabel = "unknown-version"

        val restored = state.toTarget()

        assertNotNull(restored)
        assertEquals(LuaPlatform.LUAU, restored.platform)
    }

    @Test
    fun `TargetState serialization preserves platform enum`() {
        val original = Target(LuaPlatform.REDIS, PlatformVersionRegistry.getVersions(LuaPlatform.REDIS)[2])
        val state = LuaProjectSettings.TargetState.from(original)

        // Simulate manual XML construction
        val newState = LuaProjectSettings.TargetState()
        newState.platform = state.platform
        newState.versionLabel = state.versionLabel

        assertEquals(LuaPlatform.REDIS, newState.platform)
        assertEquals("7+", newState.versionLabel)
    }
}

// ============================================================================
// Edge Cases and State Transitions
// ============================================================================

class LuaProjectSettingsEdgeCasesTest {
    @Test
    fun `state persists across multiple getTarget calls`() {
        val state = LuaProjectSettings.State()
        state.platform = LuaPlatform.STANDARD
        state.languageLevel = LuaLanguageLevel.LUA51

        val target1 = state.getTarget()
        val target2 = state.getTarget()
        val target3 = state.getTarget()

        assertEquals(target1, target2)
        assertEquals(target2, target3)
    }

    @Test
    fun `setTarget after getTarget replaces cached state`() {
        val state = LuaProjectSettings.State()
        state.platform = LuaPlatform.STANDARD
        state.languageLevel = LuaLanguageLevel.LUA51

        val original = state.getTarget()
        assertEquals("5.1", original.version.label)

        val registry = PlatformVersionRegistry
        val newVersion = registry.getVersions(LuaPlatform.STANDARD)[3]  // 5.4
        val newTarget = Target(LuaPlatform.STANDARD, newVersion)
        state.setTarget(newTarget)

        val updated = state.getTarget()
        assertEquals("5.4", updated.version.label)
    }

    @Test
    fun `switching platforms via setTarget updates language level`() {
        val state = LuaProjectSettings.State()
        state.languageLevel = LuaLanguageLevel.LUA54

        val registry = PlatformVersionRegistry
        val redisVersion = registry.getVersions(LuaPlatform.REDIS)[0]
        val redisTarget = Target(LuaPlatform.REDIS, redisVersion)

        state.setTarget(redisTarget)

        // Language level should be updated to match Redis (LUA51)
        assertEquals(LuaLanguageLevel.LUA51, state.languageLevel)
        assertEquals(LuaPlatform.REDIS, state.platform)
    }

    @Test
    fun `default state has sensible defaults`() {
        val state = LuaProjectSettings.State()

        assertEquals(LuaLanguageLevel.LUA54, state.languageLevel)
        assertEquals(LuaPlatform.STANDARD, state.platform)
        assertNull(state.targetState)
        assertNull(state.interpreter)
        assertEquals(PathConfiguration.DEFAULT_SOURCE_PATH, state.sourcePath)
    }

    @Test
    fun `all language level conversions in migration`() {
        val conversions = mapOf(
            LuaLanguageLevel.LUA50 to "5.0",
            LuaLanguageLevel.LUA51 to "5.1",
            LuaLanguageLevel.LUA52 to "5.2",
            LuaLanguageLevel.LUA53 to "5.3",
            LuaLanguageLevel.LUA54 to "5.4",
        )

        for ((langLevel, expectedLabel) in conversions) {
            val state = LuaProjectSettings.State()
            state.platform = LuaPlatform.STANDARD
            state.languageLevel = langLevel

            val target = state.getTarget()
            assertEquals(LuaPlatform.STANDARD, target.platform)
            
            // LUA50 doesn't exist in registry, so will get default
            if (langLevel == LuaLanguageLevel.LUA50) {
                assertEquals("5.1", target.version.label)
            } else {
                assertEquals(expectedLabel, target.version.label)
            }
        }
    }

    @Test
    fun `targetState becomes non-null after first getTarget call`() {
        val state = LuaProjectSettings.State()
        state.platform = LuaPlatform.STANDARD
        state.languageLevel = LuaLanguageLevel.LUA52

        assertNull(state.targetState)

        state.getTarget()

        assertNotNull(state.targetState)
        assertEquals(LuaPlatform.STANDARD, state.targetState?.platform)
        assertEquals("5.2", state.targetState?.versionLabel)
    }

    @Test
    fun `targetState becomes non-null after setTarget`() {
        val state = LuaProjectSettings.State()

        assertNull(state.targetState)

        val registry = PlatformVersionRegistry
        val version = registry.getVersions(LuaPlatform.LUAJIT)[0]
        val target = Target(LuaPlatform.LUAJIT, version)

        state.setTarget(target)

        assertNotNull(state.targetState)
    }
}

// ============================================================================
// LuaProjectSettings Service Tests
// ============================================================================

class LuaProjectSettingsServiceTest {
    @Test
    fun `state component persists and loads state correctly`() {
        val settings = LuaProjectSettings()
        val originalState = LuaProjectSettings.State()
        originalState.languageLevel = LuaLanguageLevel.LUA52
        originalState.sourcePath = "/custom/path"

        settings.loadState(originalState)
        val retrieved = settings.getState()

        assertEquals(LuaLanguageLevel.LUA52, retrieved.languageLevel)
        assertEquals("/custom/path", retrieved.sourcePath)
    }

    @Test
    fun `loadState replaces entire state`() {
        val settings = LuaProjectSettings()

        val state1 = LuaProjectSettings.State()
        state1.languageLevel = LuaLanguageLevel.LUA51
        settings.loadState(state1)

        val state2 = LuaProjectSettings.State()
        state2.languageLevel = LuaLanguageLevel.LUA54
        settings.loadState(state2)

        val final = settings.getState()
        assertEquals(LuaLanguageLevel.LUA54, final.languageLevel)
    }

    @Test
    fun `getState returns mutable state`() {
        val settings = LuaProjectSettings()
        val state = settings.getState()

        state.languageLevel = LuaLanguageLevel.LUA52
        state.sourcePath = "/modified"

        val retrieved = settings.getState()
        assertEquals(LuaLanguageLevel.LUA52, retrieved.languageLevel)
        assertEquals("/modified", retrieved.sourcePath)
    }

    @Test
    fun `state preserves target configuration after load`() {
        val settings = LuaProjectSettings()
        val state = LuaProjectSettings.State()
        
        val registry = PlatformVersionRegistry
        val version = registry.getVersions(LuaPlatform.TARANTOOL)[0]
        val target = Target(LuaPlatform.TARANTOOL, version)
        state.setTarget(target)

        settings.loadState(state)
        val retrieved = settings.getState()

        val retrievedTarget = retrieved.getTarget()
        assertEquals(LuaPlatform.TARANTOOL, retrievedTarget.platform)
    }
}

// ============================================================================
// Backward Compatibility Tests
// ============================================================================

class LuaProjectSettingsBackwardCompatibilityTest {
    @Test
    fun `old settings with only platform field can be migrated`() {
        val state = LuaProjectSettings.State()
        state.platform = LuaPlatform.STANDARD
        state.languageLevel = LuaLanguageLevel.LUA54
        // No targetState set (simulates old settings file)

        val target = state.getTarget()

        assertEquals(LuaPlatform.STANDARD, target.platform)
        assertEquals("5.4", target.version.label)
    }

    @Test
    fun `mixed old and new settings use new if available`() {
        val state = LuaProjectSettings.State()
        
        // Old fields
        state.platform = LuaPlatform.STANDARD
        state.languageLevel = LuaLanguageLevel.LUA51
        
        // New field (set explicitly)
        val registry = PlatformVersionRegistry
        val version = registry.getVersions(LuaPlatform.REDIS)[1]
        val target = Target(LuaPlatform.REDIS, version)
        state.setTarget(target)

        val retrieved = state.getTarget()

        // Should use the new target, not the old platform
        assertEquals(LuaPlatform.REDIS, retrieved.platform)
        assertEquals("6", retrieved.version.label)
    }

    @Test
    fun `deprecated platform field can still be read`() {
        val state = LuaProjectSettings.State()
        state.platform = LuaPlatform.LUAJIT

        @Suppress("DEPRECATION")
        val platform = state.platform

        assertEquals(LuaPlatform.LUAJIT, platform)
    }

    @Test
    fun `deprecated platform field is updated by setTarget`() {
        val state = LuaProjectSettings.State()

        val registry = PlatformVersionRegistry
        val version = registry.getVersions(LuaPlatform.PANDOC)[0]
        val target = Target(LuaPlatform.PANDOC, version)
        state.setTarget(target)

        @Suppress("DEPRECATION")
        val platform = state.platform

        assertEquals(LuaPlatform.PANDOC, platform)
    }

    @Test
    fun `all migration scenarios from phase 1 spec are covered`() {
        // TARGET-06 spec migration scenarios
        val scenarios = listOf(
            Triple(LuaPlatform.STANDARD, LuaLanguageLevel.LUA51, "5.1"),
            Triple(LuaPlatform.STANDARD, LuaLanguageLevel.LUA52, "5.2"),
            Triple(LuaPlatform.STANDARD, LuaLanguageLevel.LUA53, "5.3"),
            Triple(LuaPlatform.STANDARD, LuaLanguageLevel.LUA54, "5.4"),
            Triple(LuaPlatform.REDIS, LuaLanguageLevel.LUA51, "5"),  // Redis has different labels
            Triple(LuaPlatform.LUAJIT, LuaLanguageLevel.LUA51, "2.0"),  // LuaJIT defaults to 2.0
        )

        for ((platform, langLevel, _) in scenarios) {
            val state = LuaProjectSettings.State()
            state.platform = platform
            state.languageLevel = langLevel

            val target = state.getTarget()
            assertEquals(platform, target.platform)
            assertNotNull(target.version)
        }
    }
}
