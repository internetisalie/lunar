package net.internetisalie.lunar.analysis

import com.intellij.openapi.application.runReadAction
import net.internetisalie.lunar.lang.psi.types.ErrorSeverity
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot
import net.internetisalie.lunar.lang.types.IndexedBasePlatformTestCase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * BUG-94. `---@param self T` on a `:` method names the implicit receiver, and `T: SomeClass` makes
 * it read as its bound: a field the bound declares is silent, one it lacks is an undefined field,
 * and the declaration itself raises nothing.
 */
@RunWith(JUnit4::class)
class LuaGenericSelfMethodTest : IndexedBasePlatformTestCase() {
    private var counter = 0

    private fun hits(text: String): List<String> {
        val caseFile = myFixture.configureByText("t${counter++}.lua", text)
        return runReadAction {
            val document = myFixture.editor.document
            LuaTypesSnapshot
                .forFile(caseFile)
                .getErrors()
                .filter { it.severity != ErrorSeverity.HYPOTHESIS }
                .map { "${document.getLineNumber(it.element.textRange.startOffset) + 1}:${it.message}" }
        }
    }

    private val classHeader =
        "---@class SomeClass\n---@field someExistingVar integer\nlocal SomeClass = {}\n\n"

    @Test
    fun testOnlyTheUndefinedFieldOfAGenericSelfIsReported() =
        assertEquals(
            listOf("10:Undefined field 'doesNotExist'"),
            hits(
                classHeader +
                    "---@generic T: SomeClass\n---@param self T\n---@return T\nfunction SomeClass:xyz()\n" +
                    "    print(self.someExistingVar)\n    print(self.doesNotExist)\n    return self\nend\n",
            ),
        )

    @Test
    fun testNonGenericSelfTagIsNotACountedArgument() =
        assertEquals(
            emptyList<String>(),
            hits(
                classHeader +
                    "---@param self SomeClass\nfunction SomeClass:xyz()\n    print(self.someExistingVar)\nend\n",
            ),
        )

    @Test
    fun testFieldInheritedByTheBoundIsNotUndefined() =
        assertEquals(
            emptyList<String>(),
            hits(
                classHeader +
                    "---@class Child: SomeClass\n---@field own integer\nlocal Child = {}\n\n" +
                    "---@generic T: Child\n---@param self T\nfunction Child:xyz()\n" +
                    "    print(self.own, self.someExistingVar)\nend\n",
            ),
        )

    @Test
    fun testWritingAFieldOfAGenericSelfIsNotUndefined() =
        assertEquals(
            emptyList<String>(),
            hits(
                classHeader +
                    "---@generic T: SomeClass\n---@param self T\nfunction SomeClass:xyz()\n    self.fresh = 1\nend\n",
            ),
        )

    @Test
    fun testDeclaredParameterAfterSelfStillCountsArguments() {
        val found =
            hits(
                classHeader +
                    "---@param self SomeClass\n---@param a string\nfunction SomeClass:m(a) end\n\n" +
                    "---@type SomeClass\nlocal c\nc:m(\"x\", \"y\")\n",
            )
        assertEquals(found.toString(), listOf("11:Too many arguments: expected at most 1, got 2"), found.distinct())
    }
}
