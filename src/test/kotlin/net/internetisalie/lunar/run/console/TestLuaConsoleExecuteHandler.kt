package net.internetisalie.lunar.run.console

import com.intellij.testFramework.runInEdtAndGet
import net.internetisalie.lunar.BaseDocumentTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the dispatch predicate that [LuaConsoleExecuteHandler.runExecuteAction] branches on
 * (`text.isBlank() || LuaChunkCompletion.isComplete(project, text)`) — true submits, false inserts
 * a continuation newline (MAINT-13-06). The branch actions drive a live process attachment / editor
 * write and are out of unit scope, so only the pure boolean key is evaluated here.
 */
class TestLuaConsoleExecuteHandler : BaseDocumentTest() {

    /** The literal predicate from LuaConsoleExecuteHandler.kt:26. */
    private fun shouldSubmit(text: String): Boolean =
        runInEdtAndGet {
            text.isBlank() || LuaChunkCompletion.isComplete(myFixture.project, text)
        }

    /** TC 24: a complete chunk submits. */
    @Test
    fun completeChunkSubmits() {
        assertTrue(shouldSubmit("print(1)"))
    }

    /** TC 25: an incomplete chunk continues (newline). */
    @Test
    fun incompleteChunkContinues() {
        assertFalse(shouldSubmit("function f()"))
    }

    /** TC 26: blank input force-submits via the escape hatch. */
    @Test
    fun blankForceSubmits() {
        assertTrue(shouldSubmit("   "))
    }
}
