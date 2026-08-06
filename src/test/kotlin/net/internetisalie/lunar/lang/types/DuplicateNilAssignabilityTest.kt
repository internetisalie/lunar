package net.internetisalie.lunar.lang.types

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.analysis.LuaTypeAssignabilityInspection
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Two regressions share this diagnostic:
 *
 * 1. **Duplicate surfacing** (the original concern): the engine's
 *    [net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot.getErrors] returns a failure ONCE,
 *    but it was surfaced by two extension points (LuaTypesAnnotator and
 *    LuaTypeAssignabilityInspection), so the editor painted it twice. Only one surfacer must
 *    remain — pinned by [testGenuineNilConcatHighlightedOnce] on a *real* nil concat.
 *
 * 2. **BUG-359 / BUG-397**: the `package.path = package.path .. ...` pattern must not report this
 *    diagnostic at all. It was a false positive manufactured by the seed-less free-global member
 *    access falling into visitBinOpExpr's `graph.nil` operand fallback; since BUG-397 Phase 2 the
 *    member types as `string` off the stdlib stub. This test originally asserted the false
 *    positive was surfaced exactly once (its concern was the duplication), which pinned the bug
 *    as expected behavior — inverted to zero when the fix landed, per the BUG-397 landing note.
 */
@RunWith(JUnit4::class)
class DuplicateNilAssignabilityTest : BasePlatformTestCase() {

    @Test
    fun testPackagePathConcatAssignNotFlagged() {
        myFixture.enableInspections(LuaTypeAssignabilityInspection())
        myFixture.configureByText(
            "test.lua",
            "package.path = package.path .. \";./?/init.lua;./?.lua\"\n",
        )

        val message = "nil value is not assignable to string"
        val matching = myFixture.doHighlighting().filter { it.description == message }

        assertEquals(
            "BUG-359: '$message' must not be reported on the package.path concat, got ${matching.size} highlights",
            0,
            matching.size,
        )
    }

    @Test
    fun testGenuineNilConcatHighlightedOnce() {
        myFixture.enableInspections(LuaTypeAssignabilityInspection())
        myFixture.configureByText(
            "test.lua",
            "local s = nil .. \"suffix\"\n",
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
