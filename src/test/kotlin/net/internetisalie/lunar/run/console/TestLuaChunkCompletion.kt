package net.internetisalie.lunar.run.console

import com.intellij.testFramework.runInEdtAndGet
import net.internetisalie.lunar.BaseDocumentTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TestLuaChunkCompletion : BaseDocumentTest() {

    private fun isComplete(text: String): Boolean =
        runInEdtAndGet {
            LuaChunkCompletion.isComplete(myFixture.project, text)
        }

    /** TC-RUN-03-01: a complete one-liner is submitted immediately. */
    @Test
    fun completeOneLinerIsComplete() {
        assertTrue(isComplete("return 1 + 1"))
        assertTrue(isComplete("print(1 + 1)"))
    }

    /** TC-RUN-03-02: an unclosed block reports an error at EOF and stays multi-line. */
    @Test
    fun unclosedBlockIsIncomplete() {
        assertFalse(isComplete("function greet(name)"))
        assertFalse(isComplete("function greet(name)\n  return name"))
        assertFalse(isComplete("if x then"))
    }

    /** TC-RUN-03-03: an at-EOF dangling expression is incomplete; a mid-chunk error submits. */
    @Test
    fun midChunkErrorIsComplete() {
        assertFalse(isComplete("return 1 +"))
        assertTrue(isComplete("local 1x = 2"))
    }

    /** TC 18: an open table literal reports an error at EOF and stays multi-line. */
    @Test
    fun openTableIsIncomplete() {
        assertFalse(isComplete("t = {"))
    }

    /** TC 19: open `do`/`repeat` blocks are incomplete. */
    @Test
    fun openBlocksAreIncomplete() {
        assertFalse(isComplete("do"))
        assertFalse(isComplete("repeat"))
    }

    /** TC 20: an unclosed call paren is incomplete. */
    @Test
    fun openParenIsIncomplete() {
        assertFalse(isComplete("print("))
    }

    /**
     * TC 21: an open long string `[[` is lexed into the long-string state (lua.flex
     * `XLONGSTRING`), consuming EOF as valid string content rather than emitting a
     * `PsiErrorElement` at EOF — so `isComplete` reports the chunk complete. Documents the
     * parser's actual behavior (the completion heuristic only tracks parse errors at EOF, not
     * an unterminated long-string token).
     */
    @Test
    fun openLongStringIsComplete() {
        assertTrue(isComplete("s = [["))
    }

    /** TC 23: blank input has no error element and force-submits. */
    @Test
    fun blankInputIsComplete() {
        assertTrue(isComplete(""))
    }
}
