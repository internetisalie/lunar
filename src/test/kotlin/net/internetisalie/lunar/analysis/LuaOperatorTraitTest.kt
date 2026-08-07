package net.internetisalie.lunar.analysis

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.psi.types.LuaGraphType
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * BUG-423 / BUG-424: what an operator POSITION admits.
 *
 * `visitBinOpExpr` demanded exactly `Number` at every arithmetic operator and exactly `String` at
 * `..`. Lua coerces between the two at both, and consults a metamethod when neither operand is a
 * number — so the engine rejected legal code, and did so 661 times on one corpus member alone.
 *
 * These drive the platform inspection rather than the snapshot, because the thing that must not
 * regress is what the user is shown.
 */
@RunWith(JUnit4::class)
class LuaOperatorTraitTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(LuaTypeAssignabilityInspection())
    }

    private fun assignabilityProblems(text: String): List<String> {
        myFixture.configureByText("test.lua", text)
        return myFixture
            .doHighlighting()
            .mapNotNull { it.description }
            .filter { it.contains("not assignable") }
    }

    private fun assertNoProblem(
        why: String,
        text: String,
    ) {
        val problems = assignabilityProblems(text)
        assertTrue("$why — got: $problems", problems.isEmpty())
    }

    private fun assertProblem(
        why: String,
        text: String,
    ) {
        val problems = assignabilityProblems(text)
        assertTrue("$why — got no assignability problem", problems.isNotEmpty())
    }

    // --- Coercion: what Lua accepts at arithmetic and concatenation (BUG-423) ----------------

    /** `"10" + 5` is 15 on every supported level. Measured on 5.0.3, 5.4.7 and 5.5.0. */
    @Test
    fun testStringOperandAtArithmeticIsAccepted() =
        assertNoProblem(
            "Lua coerces a string operand at arithmetic",
            """local total = "10" + 5""",
        )

    /** `-"5"` is −5. Unary minus is the fourth `NUMBER` row in the design's operator table. */
    @Test
    fun testStringOperandAtUnaryMinusIsAccepted() =
        assertNoProblem(
            "Lua coerces a string operand at unary minus",
            """local negated = -"5"""",
        )

    /** `1 .. "x"` is `"1x"`. Concatenation coerces in the other direction. */
    @Test
    fun testNumberOperandAtConcatIsAccepted() =
        assertNoProblem(
            "Lua coerces a number operand at concatenation",
            """local joined = 1 .. "x"""",
        )

    /**
     * The variable hop. BUG-419 shipped a defect for exactly this shape — the checked pair is
     * frequently (value, *variable*), not (value, use-node) — so the direct form proves less than
     * it looks.
     */
    @Test
    fun testStringOperandReachingArithmeticThroughAVariableIsAccepted() =
        assertNoProblem(
            "a coercible operand must stay accepted through a variable",
            """
            local text = "10"
            local total = text + 5
            """.trimIndent(),
        )

    // --- …and what it still rejects ---------------------------------------------------------

    /** Widening must not become "anything goes": `true + 1` is a runtime error in every Lua. */
    @Test
    fun testBooleanOperandAtArithmeticIsStillRejected() =
        assertProblem(
            "a boolean operand at arithmetic is a genuine Lua error",
            """local total = true + 1""",
        )

    /** The `..` counterpart, and the shape `testBooleanConcatMismatchReported` already pins. */
    @Test
    fun testBooleanOperandAtConcatIsStillRejected() =
        assertProblem(
            "a boolean operand at concatenation is a genuine Lua error",
            """
            local flag = true
            local joined = flag .. "a"
            """.trimIndent(),
        )

    /**
     * The control for the whole trait mechanism: widening a *position* must not become "anything
     * goes at that position". A plain table has no arithmetic metamethod, so it is a genuine Lua
     * error and must still be reported.
     *
     * This is the fixture that keeps [LuaGraphType.Trait.admits] honest. Without it, adding
     * `Table` to `Numberable` would make every metamethod test pass while modelling nothing —
     * which is exactly the mistake BUG-424's own fix section warns about.
     */
    @Test
    fun testPlainTableAtArithmeticIsStillRejected() =
        assertProblem(
            "a table with no arithmetic metamethod is a genuine Lua error",
            """
            local plain = {}
            local total = plain + 2
            """.trimIndent(),
        )

    /**
     * **Characterization, not an endorsement — BUG-424's arm is BLOCKED, and this records why.**
     *
     * The metamethod arm (`__add`/`__concat`/`__len` making a table satisfy an operator position)
     * is not implemented, because it cannot be tested. `setmetatable(t, mt)` with a **named**
     * metatable — the form all real code uses, including BUG-424's own measured fixture and LPeg —
     * infers `Undefined`, so there is no typed table for a metamethod check to consult:
     *
     * ```
     * local plain = {}                                     -> Table            <- typed
     * local i = setmetatable({}, { __index = { x = 1 } })   -> Table            <- typed (TC-05)
     * local V = {}; V.__index = V
     * local i = setmetatable({}, V)                        -> Undefined        <- NOT typed
     * local i = setmetatable(base, V)                      -> Undefined
     * ```
     *
     * `Undefined` absorbs every check, so an operator on such a value is silently accepted — which
     * is why BUG-424's probe read "Lunar reports 0 errors" and correctly called it *not support*.
     * A metamethod arm built today would be unreachable code with no fixture able to prove it
     * works, and the same 0-errors reading would then be mistaken for the feature working.
     *
     * **When this test goes red**, `setmetatable` with a named metatable types again — at which
     * point the metamethod arm becomes both necessary (these values start being reported) and
     * testable. That is the trigger to build it; see BUG-424.
     */
    @Test
    fun testMetamethodTablesAreUntypedToday() =
        assertNoProblem(
            "setmetatable with a named metatable still infers Undefined; see the KDoc before 'fixing' this",
            """
            local V = {}
            V.__index = V
            V.__add = function(a, b) return 0 end
            local instance = setmetatable({}, V)
            local total = instance + 2
            local joined = instance .. "s"
            local size = #instance
            """.trimIndent(),
        )
}
