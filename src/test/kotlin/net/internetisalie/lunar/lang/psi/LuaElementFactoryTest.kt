package net.internetisalie.lunar.lang.psi

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class LuaElementFactoryTest : BasePlatformTestCase() {

    @Test
    fun testCreateIdentifierProducesNamedElement() {
        runReadAction {
            val identifier = LuaElementFactory.createIdentifier(project, "foo")
            assertNotNull("Identifier should not be null", identifier)
            assertEquals("foo", identifier?.text)
        }
    }

    @Test
    fun testCreateLabelRefProducesLuaLabelRef() {
        runReadAction {
            val labelRef = LuaElementFactory.createLabelRef(project, "lbl")
            assertNotNull("LabelRef should not be null", labelRef)
            assertEquals("lbl", labelRef.text)
        }
    }

    @Test
    fun testCreateLabelProducesLuaLabel() {
        runReadAction {
            val label = LuaElementFactory.createLabel(project, "lbl")
            assertNotNull("Label should not be null", label)
            assertEquals("::lbl::", label?.text)
        }
    }

    @Test
    fun testCreateGotoStatementProducesLuaGotoStatement() {
        runReadAction {
            val gotoStmt = LuaElementFactory.createGotoStatement(project, "lbl")
            assertNotNull("GotoStatement should not be null", gotoStmt)
            assertEquals("goto lbl", gotoStmt.text)
        }
    }

    @Test
    fun testCreateExpressionProducesLuaExpr() {
        runReadAction {
            val expr = LuaElementFactory.createExpression(project, "1 + 2")
            assertNotNull("Expression should not be null", expr)
            assertEquals("1 + 2", expr?.text)
        }
    }

    @Test
    fun testCreateFileParsesWithoutErrorElements() {
        runReadAction {
            val file = LuaElementFactory.createFile(project, "local x = 1\nlocal y = 2")
            assertNotNull("File should not be null", file)
            val errors = PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java)
            assertTrue("File should not contain error elements", errors.isEmpty())
        }
    }

    @Test
    fun testCreateNewLineIsWhitespace() {
        runReadAction {
            val newline = LuaElementFactory.createNewLine(project)
            assertNotNull("Newline should not be null", newline)
            assertTrue("Newline element should be a whitespace", newline is PsiWhiteSpace)
            assertEquals("\n", newline.text)
        }
    }
}
