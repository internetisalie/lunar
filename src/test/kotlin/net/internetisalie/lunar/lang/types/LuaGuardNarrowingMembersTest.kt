package net.internetisalie.lunar.lang.types

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * BUG-435 — narrowing a value to `table` must not *remove* its members.
 *
 * `if type(Shadow) == "table"` asserts the value **is** a table, so inside the guard `Shadow.` should
 * offer at least what it offers outside it. Measured, it offered nothing but the keywords an open
 * `if` block contributes. Proven pre-existing at `fb79c038`, before COMP-09 Phase 2.
 */
class LuaGuardNarrowingMembersTest : BasePlatformTestCase() {
    /** The control, and the reason the guarded case means anything: unguarded already works. */
    fun testMembersAreOfferedOutsideTheGuard() {
        assertTrue(
            "the fixture itself is wrong if this fails",
            offered("local Shadow = { fromLocal = 1 }\nShadow.<caret>\n").contains("fromLocal"),
        )
    }

    fun testMembersSurviveATypeTableGuard() {
        val inside =
            offered(
                "local Shadow = { fromLocal = 1 }\n\nif type(Shadow) == \"table\" then\n    Shadow.<caret>\nend\n",
            )
        assertTrue(
            "narrowing to `table` asserts the value IS a table — it must not remove members. Offered: $inside",
            inside.contains("fromLocal"),
        )
    }

    private fun offered(text: String): List<String> {
        myFixture.configureByText("consumer.lua", text)
        val offered = myFixture.completeBasic()?.map { it.lookupString } ?: emptyList()
        println("BUG-435 offered=$offered")
        return offered
    }
}
