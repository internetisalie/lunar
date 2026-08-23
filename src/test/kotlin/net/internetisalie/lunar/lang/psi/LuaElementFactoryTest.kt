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
            assertEquals("lbl", labelRef?.text)
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
            assertEquals("goto lbl", gotoStmt?.text)
        }
    }

    /**
     * The null half of [LuaElementFactory.createIdentifier]'s contract, and the reason the `!!` it
     * used to carry was a defect rather than a shortcut: `goto end` cannot parse
     * (`gotoStatement ::= GOTO labelRef` is unpinned, `lua.bnf:125`), so no `LuaGotoStatement`
     * reaches the tree. A caller that has already edited the file by the time it learns this leaves
     * a half-applied edit behind — see `LuaRenameTest.testRenameRefusesWholesaleWhenTheNewName...`.
     */
    @Test
    fun testCreateIdentifierIsNullForANameThatCannotBeAnIdentifier() {
        runReadAction {
            assertNull("a reserved word is not an identifier", LuaElementFactory.createIdentifier(project, "end"))
            assertNull("and neither is an empty name", LuaElementFactory.createIdentifier(project, ""))
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

    /**
     * Every delimiter form Lua admits, because [LuaElementFactory.createStringLiteral] exists to
     * let `LuaRequireReference.handleElementRename` re-emit the user's own delimiters. A factory
     * that normalised `\'x\'` or `[[x]]` to `"x"` would turn a rename into an unrequested edit.
     */
    @Test
    fun testCreateStringLiteralKeepsEveryDelimiterForm() {
        runReadAction {
            for (literal in listOf("\"app.helpers\"", "'app.helpers'", "[[helpers]]", "[==[helpers]==]")) {
                val stringLiteral = LuaElementFactory.createStringLiteral(project, literal)
                assertNotNull("$literal should build a string literal", stringLiteral)
                assertEquals(literal, stringLiteral?.text)
                assertEquals(LuaElementTypes.STRING, stringLiteral?.node?.elementType)
            }
        }
    }

    /**
     * The null half of the contract. `local _ = "he"lpers"` parses its FIRST expression as the
     * complete-looking `"he"`, so without the round trip against the requested text the factory
     * would hand a caller a truncated literal and a `require("util")` would silently become
     * `require("he")`.
     *
     * An *unterminated* literal is deliberately not asserted here: `local _ = "helpers` lexes to a
     * STRING whose text is the whole remainder, so it round-trips and is returned. That costs
     * nothing — `renamedLiteral` re-emits the opening delimiter run as the closing one, so it
     * cannot produce an unterminated literal to begin with.
     */
    @Test
    fun testCreateStringLiteralIsNullWhenTheTextIsNotOneLiteral() {
        runReadAction {
            assertNull(
                "a truncating literal is not one literal",
                LuaElementFactory.createStringLiteral(project, "\"he\"lpers\""),
            )
            assertNull("nor a non-string expression", LuaElementFactory.createStringLiteral(project, "1 + 2"))
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
