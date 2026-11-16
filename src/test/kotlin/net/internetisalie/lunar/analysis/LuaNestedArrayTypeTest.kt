package net.internetisalie.lunar.analysis

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.EdtTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.psi.LuaIndexExpr
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot
import net.internetisalie.lunar.lang.psi.types.TypeParser

/**
 * BUG-109. Every `[]` suffix on a LuaCATS type is one array level: `string[][]` is an array of
 * `string[]`, not a `string[]` followed by prose.
 */
class LuaNestedArrayTypeTest : BasePlatformTestCase() {
    private fun <T> inReadAction(action: () -> T): T =
        EdtTestUtil.runInEdtAndGet<T, RuntimeException> { runReadAction(action) }

    private fun assignabilityErrorsIn(source: String): List<String> =
        inReadAction {
            myFixture.configureByText("use.lua", source)
            LuaTypesSnapshot
                .forFile(myFixture.file)
                .getErrors()
                .map { it.message }
                .filter { it.contains("is not assignable") }
        }

    private fun assertClean(source: String) {
        val errors = assignabilityErrorsIn(source)
        assertTrue("expected no assignability error, got:\n${errors.joinToString("\n")}", errors.isEmpty())
    }

    private fun assertReported(source: String) {
        assertTrue("expected an assignability error", assignabilityErrorsIn(source).isNotEmpty())
    }

    /** The display name of the first bracket subscript in [source]. */
    private fun subscriptDisplayName(source: String): String =
        inReadAction {
            val file = myFixture.configureByText("use.lua", source)
            val subscript = PsiTreeUtil.findChildrenOfType(file, LuaIndexExpr::class.java).first { it.expr != null }
            LuaTypesSnapshot.forFile(file).getValueType(subscript).displayName()
        }

    fun testNestedConstructorIsATwoLevelStringArray() {
        assertClean("---@type string[][]\nlocal t = {{'a'}}")
    }

    fun testNestedConstructorMatchesTheTwoLevelArmOfAUnion() {
        assertClean("---@type string|string[]|string[][]\nlocal t = {{'a'}}")
    }

    fun testNestedConstructorOfTheWrongElementIsStillReported() {
        assertReported("---@type string[][]\nlocal t = {{1}}")
    }

    fun testThreeLevelArrayAcceptsAThreeLevelConstructor() {
        assertClean("---@type string[][][]\nlocal t = {{{'a'}}}")
    }

    fun testThreeLevelArrayRejectsATwoLevelConstructor() {
        assertReported("---@type string[][][]\nlocal t = {{'a'}}")
    }

    fun testTwoLevelArrayDisplaysBothLevels() {
        val name = inReadAction { TypeParser.parse("string[][]", myFixture.configureByText("use.lua", "")).name }
        assertEquals("string[][]", name)
    }

    fun testSubscriptOfATwoLevelArrayIsTheInnerArray() {
        assertEquals("string[]", subscriptDisplayName("---@type string[][]\nlocal arr = {}\nlocal x = arr[1]"))
    }

    fun testSubscriptOfAThreeLevelArrayIsATwoLevelArray() {
        assertEquals("string[][]", subscriptDisplayName("---@type string[][][]\nlocal arr = {}\nlocal x = arr[1]"))
    }
}
