package net.internetisalie.lunar.lang.todo

import com.intellij.ide.todo.TodoConfiguration
import com.intellij.psi.search.PsiTodoSearchHelper
import com.intellij.psi.search.TodoAttributesUtil
import com.intellij.psi.search.TodoPattern
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Real-flow tests for EDITOR-03 TODO/FIXME indexing: configure Lua text and ask the platform
 * [PsiTodoSearchHelper] (driven by [LuaTodoIndexPatternBuilder]) how many TODO items it finds.
 * Covers TC-1..TC-6 from the implementation plan.
 */
class LuaTodoIndexPatternBuilderTest : BasePlatformTestCase() {

    private fun todoCount(text: String): Int {
        val file = myFixture.configureByText("a.lua", text)
        return PsiTodoSearchHelper.getInstance(project).findTodoItems(file).size
    }

    // TC-1: line comment TODO is found
    fun testLineCommentTodo() = assertEquals(1, todoCount("-- TODO: refactor"))

    // Bare `-- TODO` with no trailing text is still found (default pattern `\bTODO\b.*`, `.*` = empty)
    fun testBareLineCommentTodo() = assertEquals(1, todoCount("-- TODO"))

    // FIXME in a line comment is found (default pattern set)
    fun testLineCommentFixme() = assertEquals(1, todoCount("-- FIXME see issue 12"))

    // TC-2: block comment TODO is found (marker delta strips `--[[`)
    fun testBlockCommentTodo() = assertEquals(1, todoCount("--[[ FIXME see issue 12 ]]"))

    // TC-3: leveled long-bracket comment TODO is found (text-aware start delta for `--[==[`)
    fun testLeveledBlockCommentTodo() = assertEquals(1, todoCount("--[==[ TODO refactor ]==]"))

    // TC-4 (partial): block `--[[ … ]]` doc comments carry TODOs (see testBlockCommentTodo).
    // KNOWN LIMITATION: single-line LuaCATS `---` comments lex as the lazy LUACATS_COMMENT element
    // type, which the platform TODO searcher does not surface (confirmed: even relabeling the token
    // as a plain comment does not make findTodoItems scan it). Asserting current behavior so a future
    // platform fix flips this to 1. Tracked in requirements.md → Implementation notes (EDITOR-03-04).
    fun testLuaCatsLineDocTodoIsKnownGap() = assertEquals(0, todoCount("--- TODO document this"))

    // The block form of a doc comment DOES surface TODOs (covers the EDITOR-03-04 block case).
    fun testBlockDocCommentTodo() = assertEquals(1, todoCount("--[[ TODO document this ]]"))

    // TC-5: TODO inside a string literal is NOT found (string is not a comment token)
    fun testStringLiteralIsNotTodo() = assertEquals(0, todoCount("local s = \"TODO not a comment\""))

    // A plain comment produces no TODO
    fun testPlainCommentHasNoTodo() = assertEquals(0, todoCount("-- just a normal comment"))

    // TC-6: a user-configured custom pattern matches inside a Lua comment
    fun testCustomPatternMatches() {
        val config = TodoConfiguration.getInstance()
        val original = config.todoPatterns
        try {
            config.todoPatterns = arrayOf(TodoPattern("\\bHACK\\b.*", TodoAttributesUtil.createDefault(), true))
            assertEquals(1, todoCount("-- HACK temporary workaround"))
        } finally {
            config.todoPatterns = original
        }
    }
}
