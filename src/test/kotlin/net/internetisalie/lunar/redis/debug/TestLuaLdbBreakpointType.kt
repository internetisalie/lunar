package net.internetisalie.lunar.redis.debug

import com.intellij.testFramework.runInEdtAndGet
import net.internetisalie.lunar.BaseDocumentTest
import net.internetisalie.lunar.lang.LuaFileType
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * TC-LDB-BP-1: [LuaLdbBreakpointType.canPutAt] accepts an executable statement line and rejects a
 * blank/comment line (design §2.5, §3.9). Light [BaseDocumentTest] fixture, mirroring how the
 * MobDebug breakpoint/evaluator tests configure a Lua file — no `!!` in the tested code path.
 */
class TestLuaLdbBreakpointType : BaseDocumentTest() {

    private val breakpointType = LuaLdbBreakpointType()

    /** True where [line] (0-based) of [text] holds a statement, false for blank/comment lines. */
    private fun canPutAt(text: String, line: Int): Boolean {
        val psiFile = myFixture.configureByText(LuaFileType, text)
        val project = myFixture.project
        val virtualFile = psiFile.virtualFile
        return runInEdtAndGet { breakpointType.canPutAt(virtualFile, line, project) }
    }

    /** A statement line is breakable. */
    @Test
    fun testStatementLineIsBreakable() {
        assertTrue(canPutAt("-- header\nlocal x = 1\n", 1))
    }

    /** A comment-only line is not breakable. */
    @Test
    fun testCommentLineIsNotBreakable() {
        assertFalse(canPutAt("-- just a comment\nlocal x = 1\n", 0))
    }

    /** A blank line is not breakable. */
    @Test
    fun testBlankLineIsNotBreakable() {
        assertFalse(canPutAt("local x = 1\n\nlocal y = 2\n", 1))
    }
}
