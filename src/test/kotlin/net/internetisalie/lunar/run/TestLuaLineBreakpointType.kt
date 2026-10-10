package net.internetisalie.lunar.run

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiFile
import com.intellij.testFramework.EdtTestUtil
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.breakpoints.XBreakpointManager
import com.intellij.xdebugger.breakpoints.XBreakpointProperties
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import com.intellij.xdebugger.breakpoints.XLineBreakpointType
import net.internetisalie.lunar.BaseDocumentTest
import net.internetisalie.lunar.lang.LuaFileType
import org.junit.jupiter.api.Disabled
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TestLuaLineBreakpointType : BaseDocumentTest() {
    /**
     * `canPutAt` accepts a line that holds a statement. Mutation: `return false` turns this red.
     */
    @Test
    fun testCanPutAtStatementLines() {
        val file = configureCanPutAtFixture()
        assertTrue(canPutAt(file, 2), "second statement")
        assertTrue(canPutAt(file, 4), "statement after a comment and a blank line")
    }

    /**
     * The first statement of a block is refused today: the parent walk in `canPutAt` stops at the
     * BLOCK (which starts on the same line) and BLOCK is not a `LuaStatement`. Un-ignore with BUG-485.
     */
    @Test
    @Disabled("BUG-485: canPutAt refuses the first statement of every block")
    fun testCanPutAtFirstStatementOfBlock() {
        assertTrue(canPutAt(configureCanPutAtFixture(), 1))
    }

    /**
     * Comment-only and blank lines are refused. Mutation: `return true` (or dropping the
     * `PsiWhiteSpace`/`PsiComment` skip) turns this red.
     */
    @Test
    fun testCanPutAtRefusesCommentAndBlankLines() {
        val file = configureCanPutAtFixture()
        assertFalse(canPutAt(file, 0), "header comment")
        assertFalse(canPutAt(file, 3), "blank line")
    }

    /**
     * A non-Lua file is refused. The `as? LuaFile` guard alone is redundant (a text file has no
     * `LuaStatement`); this goes red only if the statement check is also weakened (BUG-461 mutation proofs).
     */
    @Test
    fun testCanPutAtRefusesNonLuaFile() {
        val plain = myFixture.configureByText("notes.txt", "local x = 1\n")
        assertFalse(canPutAt(plain, 0))
    }

    private fun configureCanPutAtFixture(): PsiFile =
        myFixture.configureByText(
            LuaFileType,
            "-- header\nlocal x = 1\nlocal y = 2\n\nprint(x)\n",
        )

    private fun canPutAt(
        file: PsiFile,
        line: Int,
    ): Boolean = runReadAction { LuaLineBreakpointType().canPutAt(file.virtualFile, line, myFixture.project) }

    /** TC-05d (#59): the display text renders a 1-based line (0-based line 41 → "Line 42"). */
    @Test
    fun testGetDisplayTextIsOneBased() {
        val psiFile = myFixture.configureByText(LuaFileType, "print('x')\n".repeat(50))
        val fileUrl = psiFile.virtualFile.url

        @Suppress("UNCHECKED_CAST")
        val breakpointType =
            XLineBreakpointType.EXTENSION_POINT_NAME.extensionList
                .first { it is LuaLineBreakpointType } as XLineBreakpointType<XBreakpointProperties<*>?>

        val manager: XBreakpointManager =
            XDebuggerManager.getInstance(myFixture.project).breakpointManager

        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            val breakpoint: XLineBreakpoint<XBreakpointProperties<*>?> =
                WriteAction.compute<XLineBreakpoint<XBreakpointProperties<*>?>, RuntimeException> {
                    manager.addLineBreakpoint(
                        breakpointType,
                        fileUrl,
                        41,
                        breakpointType.createBreakpointProperties(psiFile.virtualFile, 41),
                    )
                }

            val text = breakpointType.getDisplayText(breakpoint)
            assertTrue(text.startsWith("Line 42 in file"), "expected a 1-based line, was: $text")

            WriteAction.run<RuntimeException> { manager.removeBreakpoint(breakpoint) }
        }
    }
}
