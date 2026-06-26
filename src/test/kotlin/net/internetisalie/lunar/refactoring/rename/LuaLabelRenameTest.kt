package net.internetisalie.lunar.refactoring.rename

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.psi.LuaLabelName
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class LuaLabelRenameTest : BasePlatformTestCase() {

    @Test
    fun testNameIdentifierOwner() {
        myFixture.configureByText("test.lua", "::lbl::")

        val labelName = PsiTreeUtil.findChildOfType(myFixture.file, LuaLabelName::class.java)
        assertNotNull("LuaLabelName should be found in PSI", labelName)

        val owner = labelName as? PsiNameIdentifierOwner
        assertNotNull("LuaLabelName should be assignable to PsiNameIdentifierOwner", owner)

        val identifier = owner?.nameIdentifier
        assertNotNull("Name identifier should not be null", identifier)
        assertEquals("lbl", identifier?.text)
        assertEquals("lbl", owner?.name)

        WriteCommandAction.runWriteCommandAction(project) {
            owner?.setName("renamed")
        }

        assertEquals("renamed", owner?.name)
        assertEquals("renamed", owner?.nameIdentifier?.text)

        val parent = owner?.parent
        assertNotNull("Parent of labelName should not be null", parent)
        assertEquals("::renamed::", parent?.text)
    }

    @Test
    fun testRenameFromDeclaration() {
        myFixture.configureByText("test.lua", """
            ::<caret>myLabel::
            goto myLabel
        """.trimIndent())

        myFixture.renameElementAtCaret("newLabel")

        myFixture.checkResult("""
            ::newLabel::
            goto newLabel
        """.trimIndent())
    }

    @Test
    fun testRenameFromReference() {
        myFixture.configureByText("test.lua", """
            ::myLabel::
            goto my<caret>Label
        """.trimIndent())

        myFixture.renameElementAtCaret("newLabel")

        myFixture.checkResult("""
            ::newLabel::
            goto newLabel
        """.trimIndent())
    }

    @Test
    fun testScopeIsolatedRename() {
        myFixture.configureByText("test.lua", """
            function a()
                ::<caret>L::
                goto L
            end
            function b()
                ::L::
                goto L
            end
        """.trimIndent())

        myFixture.renameElementAtCaret("L2")

        myFixture.checkResult("""
            function a()
                ::L2::
                goto L2
            end
            function b()
                ::L::
                goto L
            end
        """.trimIndent())
    }
}
