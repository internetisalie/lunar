package net.internetisalie.lunar.lang.parser

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.impl.DebugUtil
import com.intellij.psi.util.PsiTreeUtil
import net.internetisalie.lunar.BaseDocumentTest
import net.internetisalie.lunar.lang.LuaFileType
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class TestStandaloneExpression : BaseDocumentTest() {

    private fun ensureNoErrors() {
        val errors = PsiTreeUtil.findChildrenOfType(myFixture.file, PsiErrorElement::class.java)
        Assertions.assertTrue(errors.isEmpty(), "Found parser errors: " + errors.joinToString { it.errorDescription })

        val highlights = myFixture.doHighlighting()
        val annotatorErrors = highlights.filter { it.severity == HighlightSeverity.ERROR && it.description != null && !it.description!!.contains("not defined") }
        Assertions.assertTrue(annotatorErrors.isEmpty(), "Found annotator errors: " + annotatorErrors.joinToString { it.description })
    }

    private fun ensureHasAnnotatorError(message: String) {
        val highlights = myFixture.doHighlighting()
        val annotatorErrors = highlights.filter { it.severity == HighlightSeverity.ERROR }
        Assertions.assertTrue(annotatorErrors.any { it.description == message },
            "Expected annotator error '$message' but found: " + annotatorErrors.joinToString { it.description })
    }

    @Test
    fun testValidAssignment() {
        myFixture.configureByText(LuaFileType, "x = 1")
        ensureNoErrors()
    }

    @Test
    fun testValidFunctionCall() {
        myFixture.configureByText(LuaFileType, "print(1)")
        ensureNoErrors()
    }

    @Test
    fun testInvalidStandaloneExpression() {
        myFixture.configureByText(LuaFileType, "x + 1")
        ensureHasAnnotatorError("Expression cannot be used as a statement")
    }

    @Test
    fun testInvalidStandaloneVariable() {
        myFixture.configureByText(LuaFileType, "x")
        ensureHasAnnotatorError("Expression cannot be used as a statement")
    }

    @Test
    fun testInvalidStandaloneParenthesizedExpression() {
        myFixture.configureByText(LuaFileType, "(print)")
        ensureHasAnnotatorError("Expression cannot be used as a statement")
    }
}
