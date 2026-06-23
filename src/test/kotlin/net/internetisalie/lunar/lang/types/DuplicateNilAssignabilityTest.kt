package net.internetisalie.lunar.lang.types

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.analysis.LuaTypeAssignabilityInspection
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Regression for the duplicate "nil value is not assignable to string" diagnostic
 * (dual reporting in the Problems list and the hover tooltip), reproduced on the
 * `.luawork` `package.path = package.path .. ...` pattern.
 *
 * The engine's [net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot.getErrors]
 * returns the failure ONCE, but it was surfaced by two extension points
 * (LuaTypesAnnotator and LuaTypeAssignabilityInspection), so the editor painted it
 * twice. Only one surfacer must remain.
 */
@RunWith(JUnit4::class)
class DuplicateNilAssignabilityTest : BasePlatformTestCase() {

    @Test
    fun testNilConcatAssignHighlightedOnce() {
        myFixture.enableInspections(LuaTypeAssignabilityInspection())
        myFixture.configureByText(
            "test.lua",
            "package.path = package.path .. \";./?/init.lua;./?.lua\"\n",
        )

        val message = "nil value is not assignable to string"
        val matching = myFixture.doHighlighting().filter { it.description == message }

        assertEquals(
            "Type error '$message' must be surfaced exactly once, got ${matching.size} highlights",
            1,
            matching.size,
        )
    }
}
