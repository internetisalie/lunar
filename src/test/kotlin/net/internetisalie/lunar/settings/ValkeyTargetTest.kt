package net.internetisalie.lunar.settings

import com.intellij.util.xmlb.XmlSerializer
import net.internetisalie.lunar.lang.LuaLanguageLevel
import net.internetisalie.lunar.platform.LuaPlatform
import net.internetisalie.lunar.platform.target.PlatformVersionRegistry
import net.internetisalie.lunar.platform.target.Target
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * REDIS-03 Phase 1 — Valkey platform registry + target derivation (TC-REG-3, TC-REG-4).
 *
 * Verifies that adding the `VALKEY` enum member round-trips through the `lunar.xml`
 * serialized settings state, and that adding the member did not break the existing
 * `REDIS` tag (legacy migration is unaffected).
 */
class ValkeyTargetTest {

    // TC-REG-3: a serialized state with platform="VALKEY" versionLabel="7.2" round-trips
    // to Target(VALKEY, "7.2") deriving LUA51.
    @Test
    fun testValkeyStateRoundTrip() {
        val valkey72 = PlatformVersionRegistry.findVersion(LuaPlatform.VALKEY, "7.2")
        assertTrue(valkey72 != null)
        val original = LuaProjectSettings.State()
        original.setTarget(Target(LuaPlatform.VALKEY, valkey72))

        val serialized = XmlSerializer.serialize(original)
        val restored = XmlSerializer.deserialize(serialized, LuaProjectSettings.State::class.java)

        val restoredTarget = restored.getTarget()
        assertEquals(LuaPlatform.VALKEY, restoredTarget.platform)
        assertEquals("7.2", restoredTarget.version.label)
        assertEquals(LuaLanguageLevel.LUA51, restoredTarget.getImplicitLanguageLevel())
    }

    // TC-REG-4: a legacy state with platform="REDIS" versionLabel="7+" (no VALKEY) still
    // resolves to Target(REDIS, "7+"); adding VALKEY did not break the existing tag, and
    // platforms() now contains VALKEY.
    @Test
    fun testLegacyRedisStateUnaffectedByValkeyAddition() {
        val redis7 = PlatformVersionRegistry.findVersion(LuaPlatform.REDIS, "7+")
        assertTrue(redis7 != null)
        val legacy = LuaProjectSettings.State()
        legacy.setTarget(Target(LuaPlatform.REDIS, redis7))

        val serialized = XmlSerializer.serialize(legacy)
        val restored = XmlSerializer.deserialize(serialized, LuaProjectSettings.State::class.java)

        val restoredTarget = restored.getTarget()
        assertEquals(LuaPlatform.REDIS, restoredTarget.platform)
        assertEquals("7+", restoredTarget.version.label)
        assertTrue(PlatformVersionRegistry.platforms().contains(LuaPlatform.VALKEY))
    }
}
