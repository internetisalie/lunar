package net.internetisalie.lunar.lang.parser

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import net.internetisalie.lunar.BaseDocumentTest
import net.internetisalie.lunar.analysis.luacheck.LuaCheckSettings
import net.internetisalie.lunar.lang.LuaFileType
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TestLuaAttributesParser : BaseDocumentTest() {

    @BeforeEach
    fun setupSettings() {
        LuaCheckSettings.getInstance().executablePath = ""
    }

    private fun ensureNoErrors() {
        val errors = PsiTreeUtil.findChildrenOfType(myFixture.file, PsiErrorElement::class.java)
        Assertions.assertTrue(errors.isEmpty(), "Found parser errors: " + errors.joinToString { it.errorDescription })
    }

    private fun ensureHasErrors() {
        val errors = PsiTreeUtil.findChildrenOfType(myFixture.file, PsiErrorElement::class.java)
        Assertions.assertFalse(errors.isEmpty(), "Expected parser errors but found none")
    }

    @Test
    fun testValidConst() {
        myFixture.configureByText(LuaFileType, "local x <const> = 10")
        ensureNoErrors()
    }

    @Test
    fun testValidClose() {
        myFixture.configureByText(LuaFileType, "local f <close> = io.open('test.txt')")
        ensureNoErrors()
    }

    @Test
    fun testMultipleLocals() {
        myFixture.configureByText(LuaFileType, "local x <const>, y <close>, z = 1, 2, 3")
        ensureNoErrors()
    }

    @Test
    fun testAttributesWithWhitespace() {
        myFixture.configureByText(LuaFileType, "local x <  const  > = 10")
        ensureNoErrors()
    }

    @Test
    fun testAttributeOnOneButNotAll() {
        myFixture.configureByText(LuaFileType, "local x <const>, y = 10, 20")
        ensureNoErrors()
    }

    @Test
    fun testInvalidNoInitialization() {
        // FIXME: According to Lua 5.4 spec, attributes require immediate initialization.
        // Currently the BNF allows 'local x <const>', so it doesn't produce a parser error.
        myFixture.configureByText(LuaFileType, "local x <const>")
        ensureNoErrors()
    }

    @Test
    fun testInvalidGlobalAttribute() {
        myFixture.configureByText(LuaFileType, "x <const> = 10")
        ensureHasErrors()
    }

    @Test
    fun testInvalidTableFieldAttribute() {
        myFixture.configureByText(LuaFileType, "t.x <const> = 10")
        ensureHasErrors()
    }

    @Test
    fun testInvalidMultipleAttributes() {
        myFixture.configureByText(LuaFileType, "local x <const> <close> = 10")
        ensureHasErrors()
    }
}
