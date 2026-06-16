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
}
