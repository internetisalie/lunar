package net.internetisalie.lunar.refactoring.rename

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.rename.RenameUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.psi.LuaDeclarationSite
import net.internetisalie.lunar.lang.psi.LuaLocalVarDecl
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsClassTag
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsTypeDeclarations
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * `REFACT-08` Phase 5: [LuaCatsTypeNameInputValidator] (design.md §2.7, §3.8).
 *
 * Row numbers below are `requirements.md`'s TC numbers.
 */
@RunWith(JUnit4::class)
class LuaCatsTypeNameInputValidatorTest : BasePlatformTestCase() {
    /**
     * TC-8 — `RenameUtil.isValidName` for seven candidates against a LuaCATS declaration leaf, plus
     * the Lua-side `M` local as a control.
     *
     * Mutation E (mutation-proved by hand, `temporary-edits` skill) — delete
     * `newName !in LuaCatsTypeDeclarations.BUILTIN_KEYWORDS` from `isInputValid`: `table` reddens
     * from `false` to `true`, admitting a new name whose every future use would parse as
     * `LuaCatsBuiltinType`.
     */
    @Test
    fun testIsValidNameForVariousCandidates() {
        val types = myFixture.configureByText("types.lua", "--- @class parser.object\nlocal M = {}\n")
        val tag = requireNotNull(PsiTreeUtil.findChildOfType(types, LuaCatsClassTag::class.java))
        val declarationLeaf = requireNotNull(LuaCatsTypeDeclarations.classDeclarationLeaf(tag))
        val localDecl = requireNotNull(PsiTreeUtil.findChildOfType(types, LuaLocalVarDecl::class.java))
        val localLeaf = requireNotNull(LuaDeclarationSite.identifierLeafOf(localDecl))

        assertTrue("parser.node", RenameUtil.isValidName(project, declarationLeaf, "parser.node"))
        assertTrue("Gadget", RenameUtil.isValidName(project, declarationLeaf, "Gadget"))
        assertTrue("ffi.cdata*", RenameUtil.isValidName(project, declarationLeaf, "ffi.cdata*"))
        assertFalse("table", RenameUtil.isValidName(project, declarationLeaf, "table"))
        assertFalse("has space", RenameUtil.isValidName(project, declarationLeaf, "has space"))
        assertTrue("9bad", RenameUtil.isValidName(project, declarationLeaf, "9bad"))
        assertTrue("goto", RenameUtil.isValidName(project, declarationLeaf, "goto"))

        assertFalse(
            "the Lua-side control element must not be reached by the LuaCATS validator",
            RenameUtil.isValidName(project, localLeaf, "parser.node"),
        )
    }

    /**
     * TC-9 — `parser.object` renames to `parser.node` end to end, both files.
     *
     * Mutation B (Phase 3's, still reachable here) — narrowing the searcher's context turns both
     * use lines stale; this row is the input-validator's own end-to-end gate, not a re-proof of
     * mutation B, so no mutation is re-run for it here.
     */
    @Test
    fun testParserObjectRenamesToParserNodeEndToEnd() {
        val types = myFixture.configureByText("types.lua", "--- @class parser.ob<caret>ject\nlocal M = {}\n")
        val uses =
            myFixture.addFileToProject(
                "uses.lua",
                "--- @param p parser.object\n--- @return parser.object\nlocal function f(p) return p end\n",
            )

        myFixture.renameElementAtCaret("parser.node")

        myFixture.checkResult("--- @class parser.node\nlocal M = {}\n")
        assertEquals(
            "--- @param p parser.node\n--- @return parser.node\nlocal function f(p) return p end\n",
            uses.text,
        )
    }
}
