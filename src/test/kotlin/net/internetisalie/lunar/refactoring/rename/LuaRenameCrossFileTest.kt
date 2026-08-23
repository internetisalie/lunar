package net.internetisalie.lunar.refactoring.rename

import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * REFACT-01 Phase 2 — a Lua global is `_ENV.x` and is therefore visible in every file, so its
 * rename is not file-local (REFACT-01-07).
 *
 * The failure mode being guarded is the one that makes a rename *look* successful: the declaring
 * file changes, the consuming file does not, and nothing warns. Every case therefore asserts the
 * OTHER file's text as well as its own — asserting only the declaring file is green for a rename
 * that reaches nothing.
 *
 * All four global declaration forms are covered because `LuaDeclarationSite` classifies them
 * through four different rows and only one is stub-backed: a bare `function greet()`, a bare
 * `config = {}` assignment, and the Lua 5.5 `global x` / `global function f` declarations, whose
 * cross-file resolution came from the Phase 1 index extension. The harness is
 * `LuaCrossFileGlobalResolutionTest`'s — a light fixture is enough to exercise
 * `LuaGlobalAssignmentIndex` across files.
 */
@RunWith(JUnit4::class)
class LuaRenameCrossFileTest : BasePlatformTestCase() {
    /** TC-08 — a bare `function greet() end`, the stub-indexed form. */
    @Test
    fun testRenameGlobalFunctionAcrossFiles() {
        val consumer = myFixture.addFileToProject("b.lua", "greet()\n")
        myFixture.configureByText("a.lua", "function <caret>greet() end\n")

        myFixture.renameElementAtCaret("hello")

        myFixture.checkResult("function hello() end\n")
        assertFileText("hello()\n", consumer)
    }

    /** TC-27 — the canonical Lua global: a bare file-scope assignment (§3.5 row 14). */
    @Test
    fun testRenameBareGlobalAssignmentAcrossFiles() {
        val consumer = myFixture.addFileToProject("b.lua", "print(config)\n")
        myFixture.configureByText("a.lua", "con<caret>fig = {}\n")

        myFixture.renameElementAtCaret("settings")

        myFixture.checkResult("settings = {}\n")
        assertFileText("print(settings)\n", consumer)
    }

    /**
     * TC-28 — Lua 5.5 `global x = 1`. Fails if §3.5 row 5 is missing: the declaration would fall
     * through to the `LuaAttName` row, classify as `LOCAL_VARIABLE`, and §3.2 would narrow the
     * search to one file, leaving `b.lua` bound to the old name.
     */
    @Test
    fun testRenameLua55GlobalVariableAcrossFiles() {
        val consumer = myFixture.addFileToProject("b.lua", "print(count)\n")
        myFixture.configureByText("a.lua", "global c<caret>ount = 0\n")

        myFixture.renameElementAtCaret("total")

        myFixture.checkResult("global total = 0\n")
        assertFileText("print(total)\n", consumer)
    }

    /**
     * TC-29 — Lua 5.5 `global function f() end`. `globalFuncDecl` has no `funcName` node
     * (`lua.bnf:229`), so §3.5 row 9 does not match it and row 7 is what makes it renameable at all.
     */
    @Test
    fun testRenameLua55GlobalFunctionAcrossFiles() {
        val consumer = myFixture.addFileToProject("b.lua", "greet()\n")
        myFixture.configureByText("a.lua", "global function gr<caret>eet() end\n")

        myFixture.renameElementAtCaret("hello")

        myFixture.checkResult("global function hello() end\n")
        assertFileText("hello()\n", consumer)
    }

    private fun assertFileText(
        expected: String,
        file: PsiFile,
    ) = assertEquals(
        "the consuming file must be rewritten too — a global rename that reaches one file is BUG-457",
        expected,
        file.text,
    )
}
