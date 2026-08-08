package net.internetisalie.lunar.lang.types

import net.internetisalie.lunar.lang.psi.LuaFile
import net.internetisalie.lunar.lang.psi.types.ErrorSeverity
import net.internetisalie.lunar.lang.psi.types.LuaTypeManager
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * BUG-427. A global API declared in another file must carry its signature across the file boundary.
 *
 * Split out of BUG-425, which fixed the *demand* half — the call site now checks a declared
 * parameter type — and then measured that two shapes fail earlier, in resolution:
 *
 * ```
 * function count(n) end      ->  resolveGlobal = null            <- never indexed
 * count = function(n) end    ->  fun(n: unknown)                 <- indexed, annotations lost
 * function Lib.count(n) end  ->  checked, and errors correctly   <- worked already
 * ```
 *
 * The member form worked because `funcTypeFromStub` reads the cats comment; the other two are the
 * forms Lua code actually uses for a bare global.
 */
@RunWith(JUnit4::class)
class LuaCrossFileGlobalFunctionTest : IndexedBasePlatformTestCase() {
    private fun errorsCalling(
        declaration: String,
        call: String,
    ): List<String> {
        myFixture.addFileToProject("defs.lua", declaration)
        val file = myFixture.configureByText("t.lua", call) as LuaFile
        return LuaTypesSnapshot
            .forFile(file)
            .getErrors()
            .filter { it.severity == ErrorSeverity.ERROR }
            .map { it.message }
    }

    private fun resolvedType(declaration: String): String? {
        myFixture.addFileToProject("defs.lua", declaration)
        val file = myFixture.configureByText("t.lua", "count(1)") as LuaFile
        return LuaTypeManager.getInstance(project).resolveGlobal("count", file)?.name
    }

    /** The declaration form: `function count(n) end` at file scope declares the global `count`. */
    @Test
    fun testGlobalFunctionDeclarationResolvesAcrossFiles() =
        assertEquals(
            "a global function declaration must carry its signature across files",
            "fun(n: number): unknown",
            resolvedType(
                """
                ---@param n number
                function count(n) end
                """.trimIndent(),
            ),
        )

    /** The assignment form was already indexed; what it lost was the annotation. */
    @Test
    fun testGlobalFunctionAssignmentKeepsItsAnnotations() =
        assertEquals(
            "a @param above `count = function(n)` belongs to that function",
            "fun(n: number): unknown",
            resolvedType(
                """
                ---@param n number
                count = function(n) end
                """.trimIndent(),
            ),
        )

    @Test
    fun testDeclarationFormContractIsEnforced() {
        val errors =
            errorsCalling(
                """
                ---@param n number
                function count(n) end
                """.trimIndent(),
                """count("s")""",
            )
        assertTrue("a cross-file contract must be enforced, got: $errors", errors.isNotEmpty())
    }

    @Test
    fun testAssignmentFormContractIsEnforced() {
        val errors =
            errorsCalling(
                """
                ---@param n number
                count = function(n) end
                """.trimIndent(),
                """count("s")""",
            )
        assertTrue("a cross-file contract must be enforced, got: $errors", errors.isNotEmpty())
    }

    /** The other half: a conforming call must stay silent, or the above proves only "always errors". */
    @Test
    fun testAConformingCrossFileCallIsSilent() {
        val errors =
            errorsCalling(
                """
                ---@param n number
                function count(n) end
                """.trimIndent(),
                """count(2)""",
            )
        assertTrue("a conforming call must produce no error, got: $errors", errors.isEmpty())
    }

    /**
     * An un-annotated declaration must not start erroring. Resolution improving is not licence to
     * invent a contract nobody wrote — the parameter is `unknown`, which absorbs.
     */
    @Test
    fun testAnUnannotatedCrossFileFunctionErrorsOnNothing() {
        val errors = errorsCalling("function count(n) end", """count("s")""")
        assertTrue("an un-annotated parameter is not a contract, got: $errors", errors.isEmpty())
    }
}
