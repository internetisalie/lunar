package net.internetisalie.lunar.analysis

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.psi.util.PsiTreeUtil
import net.internetisalie.lunar.lang.psi.LuaLocalVarDecl
import net.internetisalie.lunar.lang.psi.LuaPsiImplUtil
import net.internetisalie.lunar.lang.psi.stubs.LuaLocalVarStub
import net.internetisalie.lunar.lang.types.IndexedBasePlatformTestCase
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsComment
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * BUG-9: a LuaCATS block binds only to the line directly after it (LuaLS `bindDocs`). A blank line
 * ends the block, so a bare `---@class` followed by a blank line and `local M = {}` is a type-level
 * class, and `M`'s members are not part of it.
 */
@RunWith(JUnit4::class)
class LuaCatsBlankLineBindingTest : IndexedBasePlatformTestCase() {
    private val classBlock =
        "---@class ProcessOpts\n---@field args string[]\n---@field name string\n---@field cwd? string\n"

    private val moduleBody = "local M = {}\nM.running = {}\nM.signals = {}\nfunction M.spawn() end\n"

    private val caller = "\n---@param opts ProcessOpts\nlocal function use(opts) end\n"

    private fun hits(text: String): List<String> {
        myFixture.enableInspections(LuaTypeAssignabilityInspection())
        myFixture.configureByText("t.lua", text)
        return myFixture
            .doHighlighting()
            .mapNotNull { it.description }
            .filter { it.contains("Missing required field") }
            .distinct()
    }

    private fun catsCommentOfFirstLocal(text: String): LuaCatsComment? {
        val file = myFixture.configureByText("t.lua", text)
        return runReadAction {
            val local = PsiTreeUtil.findChildrenOfType(file, LuaLocalVarDecl::class.java).first()
            LuaPsiImplUtil.getCatsComment(local)
        }
    }

    private fun classNameOf(comment: LuaCatsComment?): String? =
        runReadAction {
            comment
                ?.getClassTagList()
                ?.firstOrNull()
                ?.argType
                ?.text
        }

    private fun stubbedLocal(file: PsiFile): LuaLocalVarStub {
        val stubTree =
            checkNotNull(
                runReadAction { (file as PsiFileImpl).stubTree },
            ) { "stub arm: proc.lua must still be stub-backed" }
        return runReadAction {
            stubTree.plainList.filterIsInstance<LuaLocalVarStub>().first { "M" in it.names }
        }
    }

    @Test
    fun testT1BlankLineDetachesClassBlock() {
        val comment = catsCommentOfFirstLocal(classBlock + "\n" + moduleBody)
        assertNull("a blank line must end the block's reach, got ${classNameOf(comment)}", comment)
    }

    @Test
    fun testT2AdjacentClassBlockStillBinds() {
        val comment = catsCommentOfFirstLocal(classBlock + moduleBody)
        assertEquals("ProcessOpts", classNameOf(comment))
    }

    @Test
    fun testT3PlainCommentWithoutBlankLineIsTransparent() {
        val comment = catsCommentOfFirstLocal("---@class A\n--[[ note ]]\nlocal A = {}\n")
        assertEquals("A", classNameOf(comment))
    }

    @Test
    fun testT4BlankLineAfterPlainCommentDetaches() {
        val comment = catsCommentOfFirstLocal("---@class A\n--[[ note ]]\n\nlocal A = {}\n")
        assertNull("a blank line must end the block's reach, got ${classNameOf(comment)}", comment)
    }

    @Test
    fun testT5DetachedClassDoesNotRequireModuleMembers() {
        val found = hits(classBlock + "\n" + moduleBody + caller + "use({ args = {}, name = \"n\", cwd = \"x\" })\n")
        assertEquals("no module member may be required of ProcessOpts", emptyList<String>(), found)
    }

    @Test
    fun testT6DetachedClassKeepsItsOwnFields() {
        val found = hits(classBlock + "\n" + moduleBody + caller + "use({ cwd = \"x\" })\n")
        assertTrue(
            "ProcessOpts must still resolve with its @field name, got $found",
            found.any { it.contains("'name'") },
        )
        assertFalse("no module member may be required, got $found", found.any { it.contains("'running'") })
    }

    @Test
    fun testT7DetachedClassAcrossFilesUsesTheStub() {
        val proc = myFixture.addFileToProject("proc.lua", classBlock + "\n" + moduleBody + "return M\n")
        assertNull("stub must not hoist the detached @class onto M", stubbedLocal(proc).luacatsClassName)

        val absent = hits(caller + "use({ args = {}, name = \"n\", cwd = \"x\" })\n")
        assertFalse(
            "running/signals must not be required, got $absent",
            absent.any {
                it.contains("'running'") ||
                    it.contains("'signals'")
            },
        )
        val present = hits(caller + "use({ cwd = \"x\" })\n")
        assertTrue(
            "ProcessOpts must still require its @field name, got $present",
            present.any { it.contains("'name'") },
        )
    }
}
