package net.internetisalie.lunar.lang.types

import com.intellij.psi.util.PsiTreeUtil
import net.internetisalie.lunar.lang.psi.LuaNameRef
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot
import net.internetisalie.lunar.platform.LuaPlatform
import net.internetisalie.lunar.platform.target.Target
import net.internetisalie.lunar.platform.target.VersionEntry
import net.internetisalie.lunar.settings.LuaProjectSettings
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * REDIS-04 §3.1a: ambient stub-global seeding into the single-file type engine.
 *
 * Under a Redis target, the bundled `redis-7/global.lua` declares `KEYS`/`ARGV` as `string[]`;
 * [net.internetisalie.lunar.lang.psi.types.LuaTypesVisitor.seedAmbientGlobals] feeds those into the
 * root scope so a bare reference to `KEYS` infers `string[]`. Off Redis nothing is seeded
 * (structural no-leak). Also covers DR-03b: a target switch re-seeds (the snapshot cache is dropped).
 */
@RunWith(JUnit4::class)
class StubGlobalSeedTypeTest : IndexedBasePlatformTestCase() {

    private fun redis7() = Target(LuaPlatform.REDIS, VersionEntry("7+", "redis-7"))
    private fun standard54() = Target(LuaPlatform.STANDARD, VersionEntry("5.4", "lua-5.4"))

    @Test
    fun testStubGlobalSeededAsArrayUnderRedis_TC_SEED_1() {
        LuaProjectSettings.getInstance(project).setTargetAndNotify(redis7())
        val file = myFixture.configureByText(
            "test.lua",
            """
            local x = KEYS
            """.trimIndent(),
        )
        val snapshot = LuaTypesSnapshot.forFile(file)
        val keysRef = PsiTreeUtil.findChildrenOfType(file, LuaNameRef::class.java).first { it.text == "KEYS" }
        assertEquals("KEYS must seed as string[] under a Redis target", "string[]", snapshot.getValueType(keysRef).displayName())
    }

    @Test
    fun testStubGlobalNotSeededOffRedis_TC_KEYS_3_structural() {
        LuaProjectSettings.getInstance(project).setTargetAndNotify(standard54())
        val file = myFixture.configureByText(
            "test.lua",
            """
            local x = KEYS
            """.trimIndent(),
        )
        val snapshot = LuaTypesSnapshot.forFile(file)
        val keysRef = PsiTreeUtil.findChildrenOfType(file, LuaNameRef::class.java).first { it.text == "KEYS" }
        assertEquals("KEYS must stay undefined off a Redis target (no leak)", "undefined", snapshot.getValueType(keysRef).displayName())
    }

    @Test
    fun testTargetSwitchInvalidatesSeededSnapshot_DR_03b() {
        val settings = LuaProjectSettings.getInstance(project)
        settings.setTargetAndNotify(standard54())
        val file = myFixture.configureByText(
            "test.lua",
            """
            local x = KEYS
            """.trimIndent(),
        )
        val keysRef = PsiTreeUtil.findChildrenOfType(file, LuaNameRef::class.java).first { it.text == "KEYS" }

        val beforeSwitch = LuaTypesSnapshot.forFile(file).getValueType(keysRef).displayName()
        assertEquals("KEYS undefined off Redis before switch", "undefined", beforeSwitch)

        settings.setTargetAndNotify(redis7())

        val afterSwitch = LuaTypesSnapshot.forFile(file).getValueType(keysRef).displayName()
        assertEquals("target switch must drop the stale snapshot and re-seed KEYS as string[]", "string[]", afterSwitch)
    }
}
