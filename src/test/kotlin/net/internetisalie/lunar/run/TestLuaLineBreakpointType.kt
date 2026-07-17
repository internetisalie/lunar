package net.internetisalie.lunar.run

import com.intellij.openapi.application.WriteAction
import com.intellij.testFramework.EdtTestUtil
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.breakpoints.XBreakpointManager
import com.intellij.xdebugger.breakpoints.XBreakpointProperties
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import com.intellij.xdebugger.breakpoints.XLineBreakpointType
import net.internetisalie.lunar.BaseDocumentTest
import net.internetisalie.lunar.lang.LuaFileType
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TestLuaLineBreakpointType : BaseDocumentTest() {

    @Test
    fun testBreakpointType() {
        val breakpointType = LuaLineBreakpointType()
        assertNotNull(breakpointType)
    }

    /** TC-05d (#59): the display text renders a 1-based line (0-based line 41 → "Line 42"). */
    @Test
    fun testGetDisplayTextIsOneBased() {
        val psiFile = myFixture.configureByText(LuaFileType, "print('x')\n".repeat(50))
        val fileUrl = psiFile.virtualFile.url

        @Suppress("UNCHECKED_CAST")
        val breakpointType = XLineBreakpointType.EXTENSION_POINT_NAME.extensionList
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
