package net.internetisalie.lunar.lang.insight

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * BUG-395: member completion after `.`/`:` on a **global declared in another file**.
 *
 * The post-dot caret is served by `LuaCompletionContributor`'s member provider, which reads the
 * receiver's type out of the current file's [net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot].
 * That snapshot is file-local — `LuaTypesVisitor.visitNameRef` bound a name only if the *file's* own
 * scope declared it — so a global assigned in a bundled stdlib stub, a definition library, or simply
 * another project file had no type at the caret and completed nothing.
 */
@RunWith(JUnit4::class)
class LuaGlobalMemberCompletionTest : BasePlatformTestCase() {
    private fun completionsFor(text: String): List<String> {
        myFixture.configureByText("consumer.lua", text)
        myFixture.completeBasic()
        return myFixture.lookupElementStrings.orEmpty()
    }

    private fun assertCompletes(
        text: String,
        vararg expected: String,
    ) {
        val found = completionsFor(text)
        expected.forEach { assertTrue("Completion should contain '$it'. Found: $found", found.contains(it)) }
    }

    /** The bundled stdlib case: `table` is `table = {}` plus a `function table.<name>` per entry. */
    @Test
    fun stdlibGlobalCompletesItsMembers() {
        assertCompletes("table.<caret>", "concat", "insert", "remove", "sort")
    }

    /** `:` still filters to functions — the stdlib members are all functions, so they survive. */
    @Test
    fun stdlibGlobalCompletesMethodsAfterColon() {
        assertCompletes("table:<caret>", "concat", "insert")
    }

    /** The definition-library / plain cross-file case, modelled with a second project file. */
    @Test
    fun projectGlobalDeclaredInAnotherFileCompletesItsMembers() {
        myFixture.addFileToProject("lib.lua", "Lib = {}\nfunction Lib.helper() end\nLib.version = \"1.0\"\n")
        assertCompletes("Lib.<caret>", "helper", "version")
    }

    /**
     * TARGET-08 TC 6's real shape: busted's `@meta` publishes `assert = require("luassert")`, and
     * luassert's returns a `---@class` local. So the global's type arrives through a `require`, not
     * from a table literal — the case that has to work for a definition library to be useful.
     *
     * The inherited `luassert.internal` members (`True`, `are`) are asserted too: they were missing
     * until BUG-398, because the parent class is named apart from the local that carries it.
     */
    @Test
    fun globalBoundToARequiredModuleCompletesThatModulesMembers() {
        myFixture.addFileToProject(
            "luassert.lua",
            """
            ---@meta
            ---@class luassert.internal
            local internal = {}
            ---@param value any
            function internal.True(value) end
            internal.are = internal

            ---@class luassert : luassert.internal
            local luassert = {}
            ---@param namespace string
            function luassert.unregister(namespace) end
            return luassert
            """.trimIndent(),
        )
        myFixture.addFileToProject("busted.lua", "---@meta\nassert = require(\"luassert\")\n")
        assertCompletes("assert.<caret>", "unregister", "True", "are")
    }

    /**
     * BUG-398's other half: after a `.` only members are valid, but the cross-file provider ran
     * regardless and answered with project-wide globals and `---@class`-carrying locals — so
     * `assert.` offered `internal` and `luassert`, the declaring file's own locals, as if they were
     * members of `assert`.
     */
    @Test
    fun aMemberCaretOffersNoCrossFileGlobals() {
        // Two members, deliberately: a lookup with exactly one match auto-inserts it and
        // `lookupElementStrings` comes back null, which would read as an empty list either way.
        myFixture.addFileToProject(
            "luassert.lua",
            """
            ---@meta
            ---@class luassert
            local luassert = {}
            function luassert.unregister(n) end
            function luassert.register(n) end
            return luassert
            """.trimIndent(),
        )
        myFixture.addFileToProject("busted.lua", "---@meta\nassert = require(\"luassert\")\n")
        val found = completionsFor("assert.<caret>")
        assertEquals(
            "nothing but the receiver's members may follow a dot. Found: $found",
            setOf("register", "unregister"),
            found.toSet(),
        )
    }

    /** A file-local declaration must still win over the cross-file global of the same name. */
    @Test
    fun aLocalShadowsTheCrossFileGlobal() {
        myFixture.addFileToProject("shadowed.lua", "Shadow = {}\nfunction Shadow.fromLibrary() end\n")
        val found = completionsFor("local Shadow = { fromLocal = 1 }\nShadow.<caret>")
        assertTrue("the local's own member must complete. Found: $found", found.contains("fromLocal"))
        assertFalse(
            "the shadowed global's members must NOT leak into the local. Found: $found",
            found.contains("fromLibrary"),
        )
    }
}
