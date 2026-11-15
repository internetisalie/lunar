package net.internetisalie.lunar.analysis

import com.intellij.openapi.application.runReadAction
import net.internetisalie.lunar.lang.psi.types.ErrorSeverity
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot
import net.internetisalie.lunar.lang.types.IndexedBasePlatformTestCase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * BUG-85 and BUG-91. A `---@return T?` position accepts `nil` (written or omitted), and an
 * `---@overload` signature widens what the function may return. A wrong type, and a `nil` for a
 * required return, are still reported.
 */
@RunWith(JUnit4::class)
class LuaOptionalAndOverloadedReturnTest : IndexedBasePlatformTestCase() {
    private fun hits(text: String): List<String> {
        val caseFile = myFixture.configureByText("t.lua", text)
        return runReadAction {
            LuaTypesSnapshot
                .forFile(caseFile)
                .getErrors()
                .filter { it.severity != ErrorSeverity.HYPOTHESIS }
                .map { it.message }
        }
    }

    @Test
    fun testExplicitNilForOptionalReturnIsSilent() =
        assertEquals(emptyList<String>(), hits("---@return number?\nfunction F()\n    return nil\nend\n"))

    @Test
    fun testOmittedOptionalTrailingReturnIsSilent() =
        assertEquals(emptyList<String>(), hits("---@return number, number?\nfunction F()\n    return 1\nend\n"))

    @Test
    fun testOverloadReturnTypeIsAccepted() =
        assertEquals(
            emptyList<String>(),
            hits(
                "---@param x boolean\n---@return number\n---@overload fun(): boolean\n" +
                    "local function f(x)\n    if x then\n        return 1\n    else\n        return false\n    end\nend\n",
            ),
        )

    @Test
    fun testWrongTypeForOptionalReturnIsReported() {
        val found = hits("---@return number?\nfunction F()\n    return true\nend\n")
        assertEquals(found.toString(), 1, found.size)
    }

    @Test
    fun testNilForRequiredReturnIsReported() {
        val found = hits("---@return number\nfunction F()\n    return nil\nend\n")
        assertEquals(found.toString(), 1, found.size)
    }

    @Test
    fun testTypeOutsideOverloadIsReported() {
        val found = hits("---@return number\n---@overload fun(): boolean\nfunction F()\n    return \"s\"\nend\n")
        assertEquals(found.toString(), 1, found.size)
    }
}
