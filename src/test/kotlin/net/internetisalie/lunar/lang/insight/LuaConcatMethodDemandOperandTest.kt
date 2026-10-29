package net.internetisalie.lunar.lang.insight

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.analysis.inspections.LuaSuspiciousConcatenationInspection
import net.internetisalie.lunar.lang.psi.LuaNameRef
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * BUG-553. A value the engine cannot type (write = undefined) is typed by its demands. A colon call
 * `x:sub(...)` records a one-member `Table` demand, which used to win over the `..` operand's
 * `Stringable` demand, so Suspicious Concatenation fired on a value that is a string at run time.
 * An operator trait that admits no table now outranks every table demand.
 */
@RunWith(JUnit4::class)
class LuaConcatMethodDemandOperandTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(LuaSuspiciousConcatenationInspection())
    }

    private fun concatWarnings(text: String): List<String> {
        myFixture.configureByText("test.lua", text)
        return myFixture
            .doHighlighting()
            .mapNotNull { it.description }
            .filter { it.startsWith("Suspicious concatenation") }
    }

    private fun assertNoWarning(text: String) {
        val warnings = concatWarnings(text)
        assertTrue("Expected no suspicious-concatenation warnings but found: $warnings", warnings.isEmpty())
    }

    private fun assertTableOperandWarning(text: String) {
        assertEquals(listOf(TABLE_OPERAND), concatWarnings(text))
    }

    /** BUG-553: an untyped parameter whose first use is a colon call. */
    @Test
    fun testNoWarningOnUntypedParamAfterColonCall() {
        assertNoWarning(
            """
            local function f(x)
              local first = x:sub(1, 1)
              return "a" .. x
            end
            """.trimIndent(),
        )
    }

    /** BUG-553: an un-typeable call result whose first use is a colon call. */
    @Test
    fun testNoWarningOnUnknownCallResultAfterColonCall() {
        assertNoWarning(
            """
            local home = unknownGlobal.os_homedir()
            if home:sub(-1) == "/" then
              home = home:sub(1, -2)
            end
            local path = home .. "/x"
            """.trimIndent(),
        )
    }

    /** BUG-553: two colon calls AFTER the concatenation do not outrank it (table-demand merge). */
    @Test
    fun testNoWarningWhenTwoColonCallsFollowConcat() {
        assertNoWarning(
            """
            local function f(x)
              local s = "a" .. x
              local c = x:sub(1)
              local d = x:sub(2)
              return s
            end
            """.trimIndent(),
        )
    }

    /** BUG-553: a callback parameter's colon call flows back into the caller's argument. */
    @Test
    fun testNoWarningOnArgumentWhoseCallbackParamIsColonCalled() {
        assertNoWarning(
            """
            local function ls(fn)
              local name = unknownGlobal()
              local s = "p/" .. name
              fn(name)
            end
            ls(function(n)
              local a = n:sub(1)
            end)
            """.trimIndent(),
        )
    }

    /** BUG-553: an ipairs loop value over string literals, colon-called first. */
    @Test
    fun testNoWarningOnIpairsLiteralValueAfterColonCall() {
        assertNoWarning(
            """
            local env = {}
            for _, name in ipairs({ "config", "data" }) do
              env[name:upper()] = "root/" .. name
            end
            """.trimIndent(),
        )
    }

    /** Control: one colon call AFTER the concatenation — the `..` demand is first and wins. */
    @Test
    fun testControlOneColonCallAfterConcat() {
        assertNoWarning(
            """
            local function f(x)
              local s = "a" .. x
              local c = x:sub(1)
              return s
            end
            """.trimIndent(),
        )
    }

    /** Control: the same untyped parameter without the colon call is not reported. */
    @Test
    fun testControlUntypedParamWithoutColonCall() {
        assertNoWarning(
            """
            local function f(x)
              return "a" .. x
            end
            """.trimIndent(),
        )
    }

    /** Control: a declared string parameter keeps its declared type despite the colon call. */
    @Test
    fun testControlDeclaredStringParam() {
        assertNoWarning(
            """
            ---@param x string
            local function f(x)
              local first = x:sub(1, 1)
              return "a" .. x
            end
            """.trimIndent(),
        )
    }

    /** Control that must still warn: a real table operand. */
    @Test
    fun testControlRealTableStillWarns() {
        assertTableOperandWarning(
            """
            local t = {}
            local s = "a" .. t
            """.trimIndent(),
        )
    }

    /**
     * Deliberate side effect: an arithmetic demand also admits no table, so it outranks the colon
     * call's table demand and the untyped value reads as `number` rather than `{ ... }`.
     */
    @Test
    fun testColonCalledArithmeticOperandReadsAsNumber() {
        myFixture.configureByText(
            "test.lua",
            """
            local function f(x)
              local c = x:sub(1)
              local n = x * 2
              return <caret>x
            end
            """.trimIndent(),
        )
        val leaf = myFixture.file.findElementAt(myFixture.caretOffset)
        val nameRef = PsiTreeUtil.getParentOfType(leaf, LuaNameRef::class.java, false)
        assertNotNull("No name reference at the caret", nameRef)
        val type = nameRef?.let { LuaTypesSnapshot.forFile(myFixture.file).getValueType(it) }
        assertEquals("number", type?.displayName())
    }

    private companion object {
        private const val TABLE_OPERAND =
            "Suspicious concatenation: operand of type '{ ... }' cannot be concatenated"
    }
}
