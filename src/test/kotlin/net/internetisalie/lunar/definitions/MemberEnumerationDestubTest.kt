package net.internetisalie.lunar.definitions

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.source.PsiFileImpl
import net.internetisalie.lunar.lang.psi.types.LuaClassType
import net.internetisalie.lunar.lang.psi.types.LuaTypeManager
import net.internetisalie.lunar.lang.psi.types.LuaTypeMember

/**
 * BUG-438 — **the `@class` door must not load a declaring file's AST to find implicit fields that
 * are not there.**
 *
 * `LuaImplicitFields.collect` walked every declaring file with `findChildrenOfType`, which loads the
 * AST before it visits anything. Staged `isContentsLoaded` probes through `materializeClass` put the
 * de-stub exactly there — `03afterAllHostedParts=[false]`, `04afterImplicitFields=[true]` — making
 * that one call ~63 % of a door measured at 269 ms against a 100 ms budget (NFR-1).
 *
 * **The observable is the de-stub, not a duration.** COMP-09's standing rule prefers a count gate to
 * a timing threshold wherever a count will do, and `PsiFileImpl.isContentsLoaded` is a state: it
 * observes the parse without causing it, where a timing assertion would be both flaky and unable to
 * say *why* it got slower.
 *
 * [LibraryRootTestCase] rather than a light fixture, for the reason that harness exists: a declaring
 * file inside the project is not stub-backed in the way a library file is, so the parse this test
 * asserts against would never have been avoidable there.
 *
 * **Mutation proof** (`bug-report.md` §8): delete the guard in `LuaImplicitFields.collect` and
 * [testAFieldOnlyDeclaringFileIsNeverParsed] goes red while the other two stay green. That split is
 * the point — the other two exist to prove the guard is a *skip* and not a behaviour change, so a
 * proof that reddens all three would mean the guard was dropping members.
 */
class MemberEnumerationDestubTest : LibraryRootTestCase() {
    /**
     * The win. `Klass` declares its members through `---@field` and `function Klass.method()`,
     * neither of which is an implicit assignment, so the walk had nothing to find and the parse it
     * forced was pure waste.
     *
     * The member assertion comes first deliberately: an empty class would satisfy the
     * `isContentsLoaded` assertion trivially, so without it a guard that skipped everything would
     * pass this test.
     */
    fun testAFieldOnlyDeclaringFileIsNeverParsed() {
        val root = registerLibraryRoot(mapOf(FIELDS_ONLY_PATH to FIELDS_ONLY_SOURCE))
        assertEquals(setOf("declared", "alsoDeclared", "method"), classMembers("Klass").keys)
        assertFalse(
            "the @class door must answer from stubs alone when the declaring file assigns no " +
                "implicit member — LuaImplicitFields.collect's walk loaded the AST to find nothing",
            contentsLoaded(root, FIELDS_ONLY_PATH),
        )
    }

    /**
     * The skip is a skip. `Widget` assigns two implicit members, so the walk must still run and
     * still type them from the RHS shape — `lightInferType`'s syntactic mapping, unchanged.
     */
    fun testAFileWithImplicitAssignmentsKeepsEveryMember() {
        registerLibraryRoot(mapOf("widget.lua" to WITH_IMPLICIT_SOURCE))
        val members = classMembers("Widget")
        assertEquals(setOf("declared", "implicitNumber", "implicitTable"), members.keys)
        assertEquals("number", typeNameOf(members, "implicitNumber"))
        assertEquals("table", typeNameOf(members, "implicitTable"))
    }

    /**
     * The control. `Gizmo.shared = 42` and `---@field shared string` name the same member, and
     * TYPE-02-05's precedence is that the declared `@field` wins — `collect` writes only where the
     * map has no key yet.
     *
     * A guard that fired here would drop nothing visible, because the `@field` supplies the member
     * either way. That is exactly why this case needs its own test: it is where an over-eager guard
     * is *invisible* to the two above.
     */
    fun testADeclaredFieldStillOutranksAnImplicitAssignment() {
        registerLibraryRoot(mapOf("gizmo.lua" to SHADOWED_SOURCE))
        val members = classMembers("Gizmo")
        assertEquals(setOf("shared"), members.keys)
        assertEquals("string", typeNameOf(members, "shared"))
    }

    /** Whether the platform has loaded [relativePath]'s AST — read without causing it to. */
    private fun contentsLoaded(
        root: VirtualFile,
        relativePath: String,
    ): Boolean =
        runReadAction {
            val virtual =
                checkNotNull(root.findFileByRelativePath(relativePath)) { "no $relativePath under $root" }
            val psi = checkNotNull(PsiManager.getInstance(project).findFile(virtual)) { "no PSI for $virtual" }
            (psi as PsiFileImpl).isContentsLoaded
        }

    private fun classMembers(className: String): Map<String, LuaTypeMember> {
        myFixture.configureByText("consumer.lua", "local x = 1\n")
        val contextFile = myFixture.file
        return runReadAction {
            val resolved = LuaTypeManager.getInstance(project).resolveType(className, contextFile)
            assertTrue("$className must resolve through the @class door, but got $resolved", resolved is LuaClassType)
            (resolved as LuaClassType).localMembers
        }
    }

    private fun typeNameOf(
        members: Map<String, LuaTypeMember>,
        name: String,
    ): String = runReadAction { checkNotNull(members[name]) { "no member '$name' in ${members.keys}" }.type.name }

    private companion object {
        const val FIELDS_ONLY_PATH = "klass.lua"

        /** No `Klass.x = …` anywhere: the shape the guard exists to skip. */
        val FIELDS_ONLY_SOURCE =
            """
            ---@class Klass
            ---@field declared string
            ---@field alsoDeclared number
            local Klass = {}

            ---@return string
            function Klass.method() end

            return Klass
            """.trimIndent()

        val WITH_IMPLICIT_SOURCE =
            """
            ---@class Widget
            ---@field declared string
            local Widget = {}

            Widget.implicitNumber = 42
            Widget.implicitTable = {}

            return Widget
            """.trimIndent()

        val SHADOWED_SOURCE =
            """
            ---@class Gizmo
            ---@field shared string
            local Gizmo = {}

            Gizmo.shared = 42

            return Gizmo
            """.trimIndent()
    }
}
