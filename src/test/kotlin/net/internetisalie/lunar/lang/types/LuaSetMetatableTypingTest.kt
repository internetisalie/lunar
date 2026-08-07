package net.internetisalie.lunar.lang.types

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.psi.LuaFile
import net.internetisalie.lunar.lang.psi.LuaNameRef
import net.internetisalie.lunar.lang.psi.types.LuaGraphType
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * BUG-426. COMP-04-08 models `setmetatable(t, mt)` by adding `mt.__index`'s table as a supertype of
 * `t` — but only when `mt` is written **inline as a table literal**, which is the form its own test
 * uses and close to the only form real Lua does not.
 *
 * With a named metatable the result was `Undefined`, and `Undefined` absorbs every check, so the
 * whole idiomatic constructor pattern was invisible to the type engine while *looking* supported:
 * no member checking, no assignability, no completion — and no diagnostics either.
 *
 * The cause is polarity. `V.__index = V` goes through `visitIndexExpr`, which records the member as
 * a **demand** on `V` (`graph.use`), never as part of its `write`. `handleSetMetatable` consulted
 * only `write`, so it never saw `__index` and bailed.
 */
@RunWith(JUnit4::class)
class LuaSetMetatableTypingTest : BasePlatformTestCase() {
    private fun typeOfLocal(
        source: String,
        name: String,
    ): LuaGraphType {
        val file = myFixture.configureByText("t.lua", source) as LuaFile
        val snapshot = LuaTypesSnapshot.forFile(file)
        val ref =
            PsiTreeUtil
                .findChildrenOfType(file, LuaNameRef::class.java)
                .first { it.text == name }
        return snapshot.getValueType(ref)
    }

    /** The plain COMP-04-08 shape, with the metatable named instead of inlined. */
    @Test
    fun testNamedMetatableProducesATypedResult() {
        val type =
            typeOfLocal(
                """
                local V = {}
                V.__index = V
                local instance = setmetatable({}, V)
                """.trimIndent(),
                "instance",
            )
        assertTrue("a setmetatable result must be a table, was: $type", type is LuaGraphType.Table)
    }

    /**
     * Typed is not enough — the point of `__index` is that the metatable's members are reachable
     * through it. `V.greet = …` is a member *demand* on `V`, which is exactly the polarity the bug
     * was about, so this is the assertion that distinguishes a real fix from one that merely stops
     * returning `Undefined`.
     */
    @Test
    fun testMembersOfANamedMetatableAreReachable() {
        val type =
            typeOfLocal(
                """
                local V = {}
                V.__index = V
                V.greet = function() return "hi" end
                local instance = setmetatable({}, V)
                """.trimIndent(),
                "instance",
            )
        assertTrue(
            "the metatable's members must be reachable through __index, got: ${type.getMembers().keys}",
            type.getMembers().containsKey("greet"),
        )
    }

    /** The constructor form, which is why anyone writes any of this. */
    @Test
    fun testTheIdiomaticConstructorPatternIsTyped() {
        val type =
            typeOfLocal(
                """
                local Account = {}
                Account.__index = Account
                Account.balance = 0

                local function new()
                    local self = {}
                    return setmetatable(self, Account)
                end

                local acct = new()
                """.trimIndent(),
                "acct",
            )
        assertTrue(
            "an instance from a constructor must carry the metatable's members, got: ${type.getMembers().keys}",
            type.getMembers().containsKey("balance"),
        )
    }

    /**
     * The regression guard: the inline-literal form is what COMP-04-08's own TC-05 covers, and it
     * must keep working. Asserted here at the type level as well as through completion, because a
     * fix that swapped which polarity is consulted could satisfy one and break the other.
     */
    @Test
    fun testLiteralMetatableStillWorks() {
        val type =
            typeOfLocal(
                """
                local instance = setmetatable({}, { __index = { x = 1 } })
                """.trimIndent(),
                "instance",
            )
        assertTrue(
            "the inline-literal form must keep resolving its members, got: ${type.getMembers().keys}",
            type.getMembers().containsKey("x"),
        )
    }

    /** A metatable with no `__index` establishes no members, but must still leave the table typed. */
    @Test
    fun testMetatableWithoutIndexLeavesTheTableTyped() {
        val type =
            typeOfLocal(
                """
                local V = {}
                V.__tostring = function() return "V" end
                local instance = setmetatable({ own = 1 }, V)
                """.trimIndent(),
                "instance",
            )
        assertTrue("the table's own type must survive setmetatable, was: $type", type is LuaGraphType.Table)
        assertTrue(
            "…including its own members, got: ${type.getMembers().keys}",
            type.getMembers().containsKey("own"),
        )
    }
}
