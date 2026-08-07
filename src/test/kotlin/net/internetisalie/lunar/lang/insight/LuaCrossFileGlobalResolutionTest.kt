package net.internetisalie.lunar.lang.insight

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.analysis.inspections.LuaUndeclaredVariableInspection
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * BUG-391 — a global assigned at file scope in one project file must resolve from every other file,
 * with no `require` between them.
 *
 * External resolution previously reached only platform libraries and files the *current* file
 * `require`s. A Lua global is visible everywhere once assigned, and sharing an application object
 * that way is idiomatic — on ZeroBrane Studio it accounted for 391 of 1009 undeclared-variable
 * warnings (`ID` and `ide`, both plain top-level assignments in indexed files).
 */
@RunWith(JUnit4::class)
class LuaCrossFileGlobalResolutionTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(LuaUndeclaredVariableInspection())
    }

    private fun undeclaredIn(text: String): List<String> {
        myFixture.configureByText("consumer.lua", text)
        return myFixture
            .doHighlighting()
            .mapNotNull { it.description }
            .filter { it.startsWith("Undeclared variable") }
    }

    @Test
    fun globalAssignedInAnotherFileResolves() {
        myFixture.addFileToProject("declarer.lua", "ide = { config = 1 }\n")
        assertEmpty(
            "A global assigned at file scope elsewhere must not be reported undeclared",
            undeclaredIn("print(ide)\n"),
        )
    }

    @Test
    fun globalResolvesWithoutAnyRequireBetweenTheFiles() {
        // The point of the fix: no `require("declarer")` anywhere.
        myFixture.addFileToProject("declarer.lua", "ID = setmetatable({}, {})\n")
        assertEmpty(undeclaredIn("local x = ID\nprint(x)\n"))
    }

    @Test
    fun multipleAssignmentTargetsAreAllDeclared() {
        myFixture.addFileToProject("declarer.lua", "alpha, beta = 1, 2\n")
        assertEmpty(undeclaredIn("print(alpha)\nprint(beta)\n"))
    }

    @Test
    fun trulyUndeclaredNameIsStillReported() {
        // The fix must not silence the inspection wholesale.
        myFixture.addFileToProject("declarer.lua", "ide = {}\n")
        val warnings = undeclaredIn("print(neverAssignedAnywhere)\n")
        assertEquals("Undeclared variable 'neverAssignedAnywhere'", warnings.singleOrNull())
    }

    @Test
    fun localInAnotherFileDoesNotLeakAsGlobal() {
        // `local` is file-scoped in Lua; indexing it as a global would be a false negative.
        myFixture.addFileToProject("declarer.lua", "local hidden = 1\nprint(hidden)\n")
        assertEquals("Undeclared variable 'hidden'", undeclaredIn("print(hidden)\n").singleOrNull())
    }

    @Test
    fun assignmentToAFileScopeLocalIsNotAGlobal() {
        // `local shadowed` then `shadowed = 2` is a local write, not a global declaration.
        myFixture.addFileToProject("declarer.lua", "local shadowed\nshadowed = 2\n")
        assertEquals("Undeclared variable 'shadowed'", undeclaredIn("print(shadowed)\n").singleOrNull())
    }

    @Test
    fun assignmentNestedInsideAFunctionIsNotIndexed() {
        // Only file-scope assignments are indexed: a nested one may be writing to an enclosing
        // local, and the indexer must not attempt scope resolution.
        myFixture.addFileToProject("declarer.lua", "local function f()\n   nested = 1\nend\n")
        assertEquals("Undeclared variable 'nested'", undeclaredIn("print(nested)\n").singleOrNull())
    }
}
