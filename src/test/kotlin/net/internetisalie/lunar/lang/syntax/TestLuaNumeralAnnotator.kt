package net.internetisalie.lunar.lang.syntax

import com.intellij.lang.annotation.HighlightSeverity
import net.internetisalie.lunar.BaseDocumentTest
import net.internetisalie.lunar.analysis.luacheck.LuaCheckSettings
import net.internetisalie.lunar.lang.LuaFileType
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TestLuaNumeralAnnotator : BaseDocumentTest() {

    @BeforeEach
    fun setupSettings() {
        LuaCheckSettings.getInstance().executablePath = ""
    }

    // ==================== Valid numerals - no errors expected ====================

    @Test
    fun `valid decimal integer`() {
        assertNoErrors("local x = 123")
    }

    @Test
    fun `valid decimal float`() {
        assertNoErrors("local x = 3.14")
    }

    @Test
    fun `valid leading dot float`() {
        assertNoErrors("local x = .5")
    }

    @Test
    fun `valid trailing dot float`() {
        assertNoErrors("local x = 5.")
    }

    @Test
    fun `valid scientific notation`() {
        assertNoErrors("local x = 1e10")
        assertNoErrors("local x = 2E-5")
        assertNoErrors("local x = 1.5e+3")
        assertNoErrors("local x = 12.3e-2")
    }

    @Test
    fun `valid hex integer`() {
        assertNoErrors("local x = 0x1A")
        assertNoErrors("local x = 0XFF")
        assertNoErrors("local x = 0xBEBADA")
    }

    @Test
    fun `valid hex float with exponent`() {
        assertNoErrors("local x = 0xA23p-4")
        assertNoErrors("local x = 0X1p+1")
        assertNoErrors("local x = 0x1.8p+1")
        assertNoErrors("local x = 0X.5p-3")
    }

    @Test
    fun `valid hex float without binary exponent`() {
        assertNoErrors("local x = 0x0.1E")
        assertNoErrors("local x = 0x1.8")
    }

    // ==================== Invalid numerals - errors expected ====================

    @Test
    fun `invalid decimal exponent no digits`() {
        assertHasError("local x = 12e", "exponent has no digits")
    }

    @Test
    fun `invalid decimal exponent only sign`() {
        assertHasError("local x = 12e+", "exponent has no digits")
        assertHasError("local x = 12e-", "exponent has no digits")
    }

    @Test
    fun `invalid hex exponent no digits`() {
        assertHasError("local x = 0x1p", "exponent has no digits")
        assertHasError("local x = 0x1p+", "exponent has no digits")
    }

    @Test
    fun `invalid hex no digits before exponent`() {
        assertHasError("local x = 0xp", "expected hexadecimal digit before exponent")
    }

    // ==================== Semantic highlighting ====================

    @Test
    fun `integer highlighted as NUMBER_INT`() {
        myFixture.configureByText(LuaFileType, "local x = 42")
        val highlights = myFixture.doHighlighting(HighlightSeverity.TEXT_ATTRIBUTES)
        val numHighlight = highlights.find { it.text == "42" }
        Assertions.assertNotNull(numHighlight, "Number '42' should be highlighted")
        Assertions.assertEquals(LuaHighlight.NUMBER_INT, numHighlight!!.forcedTextAttributesKey)
    }

    @Test
    fun `float highlighted as NUMBER_FLOAT`() {
        myFixture.configureByText(LuaFileType, "local x = 3.14")
        val highlights = myFixture.doHighlighting(HighlightSeverity.TEXT_ATTRIBUTES)
        val numHighlight = highlights.find { it.text == "3.14" }
        Assertions.assertNotNull(numHighlight, "Number '3.14' should be highlighted as float")
        Assertions.assertEquals(LuaHighlight.NUMBER_FLOAT, numHighlight!!.forcedTextAttributesKey)
    }

    @Test
    fun `scientific notation highlighted as NUMBER_FLOAT`() {
        myFixture.configureByText(LuaFileType, "local x = 1e10")
        val highlights = myFixture.doHighlighting(HighlightSeverity.TEXT_ATTRIBUTES)
        val numHighlight = highlights.find { it.text == "1e10" }
        Assertions.assertNotNull(numHighlight, "Number '1e10' should be highlighted as float")
        Assertions.assertEquals(LuaHighlight.NUMBER_FLOAT, numHighlight!!.forcedTextAttributesKey)
    }

    @Test
    fun `hex float highlighted as NUMBER_FLOAT`() {
        myFixture.configureByText(LuaFileType, "local x = 0x1p+1")
        val highlights = myFixture.doHighlighting(HighlightSeverity.TEXT_ATTRIBUTES)
        val numHighlight = highlights.find { it.text == "0x1p+1" }
        Assertions.assertNotNull(numHighlight, "Number '0x1p+1' should be highlighted as float")
        Assertions.assertEquals(LuaHighlight.NUMBER_FLOAT, numHighlight!!.forcedTextAttributesKey)
    }

    // ==================== Helpers ====================

    private fun assertNoErrors(code: String) {
        myFixture.configureByText(LuaFileType, code)
        val errors = myFixture.doHighlighting(HighlightSeverity.ERROR)
            .filter { it.description?.contains("exponent") == true || it.description?.contains("hexadecimal") == true }
        Assertions.assertTrue(errors.isEmpty(), "Expected no numeral errors in: $code but got: ${errors.map { it.description }}")
    }

    private fun assertHasError(code: String, fragment: String) {
        myFixture.configureByText(LuaFileType, code)
        val errors = myFixture.doHighlighting(HighlightSeverity.ERROR)
        val match = errors.find { it.description?.contains(fragment) == true }
        Assertions.assertNotNull(match, "Expected error containing '$fragment' in: $code")
    }
}
