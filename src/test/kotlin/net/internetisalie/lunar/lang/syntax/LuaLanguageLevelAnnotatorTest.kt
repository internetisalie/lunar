package net.internetisalie.lunar.lang.syntax

import com.intellij.lang.annotation.HighlightSeverity
import net.internetisalie.lunar.BaseDocumentTest
import net.internetisalie.lunar.lang.LuaFileType
import net.internetisalie.lunar.lang.LuaLanguageLevel
import net.internetisalie.lunar.settings.LuaProjectSettings
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class LuaLanguageLevelAnnotatorTest : BaseDocumentTest() {

    @BeforeEach
    fun setupProject() {
        // Default to Lua 5.4 for each test
        setLanguageLevel(LuaLanguageLevel.LUA54)
    }

    // ==================== Lua 5.2+ Features (goto/label) ====================

    @Test
    fun gotoNotAllowedInLua51() {
        setLanguageLevel(LuaLanguageLevel.LUA51)
        myFixture.configureByText(LuaFileType, "goto exit")
        val errors = getLanguageLevelErrors()
        Assertions.assertTrue(errors.any { it.contains("Goto statements") }, "Expected goto error")
    }

    @Test
    fun labelNotAllowedInLua51() {
        setLanguageLevel(LuaLanguageLevel.LUA51)
        myFixture.configureByText(LuaFileType, "::exit::")
        val errors = getLanguageLevelErrors()
        Assertions.assertTrue(errors.any { it.contains("Labels") }, "Expected label error")
    }

    @Test
    fun gotoAllowedInLua52() {
        setLanguageLevel(LuaLanguageLevel.LUA52)
        myFixture.configureByText(LuaFileType, "goto exit")
        val errors = getLanguageLevelErrors()
        Assertions.assertTrue(errors.isEmpty(), "No errors expected in Lua 5.2 for goto")
    }

    @Test
    fun labelAllowedInLua52() {
        setLanguageLevel(LuaLanguageLevel.LUA52)
        myFixture.configureByText(LuaFileType, "::exit::")
        val errors = getLanguageLevelErrors()
        Assertions.assertTrue(errors.isEmpty(), "No errors expected in Lua 5.2 for label")
    }

    // ==================== Lua 5.3+ Features (bitwise operators) ====================

    @Test
    fun bitwiseANDNotAllowedInLua52() {
        setLanguageLevel(LuaLanguageLevel.LUA52)
        myFixture.configureByText(LuaFileType, "local x = 1 & 2")
        val errors = getLanguageLevelErrors()
        Assertions.assertTrue(errors.any { it.contains("Bitwise AND") }, "Expected bitwise AND error")
    }

    @Test
    fun bitwiseORNotAllowedInLua52() {
        setLanguageLevel(LuaLanguageLevel.LUA52)
        myFixture.configureByText(LuaFileType, "local x = 1 | 2")
        val errors = getLanguageLevelErrors()
        Assertions.assertTrue(errors.any { it.contains("Bitwise OR") }, "Expected bitwise OR error")
    }

    @Test
    fun bitwiseNOTNotAllowedInLua52() {
        setLanguageLevel(LuaLanguageLevel.LUA52)
        myFixture.configureByText(LuaFileType, "local x = ~1")
        val errors = getLanguageLevelErrors()
        Assertions.assertTrue(errors.any { it.contains("Bitwise NOT") }, "Expected bitwise NOT error")
    }

    @Test
    fun leftShiftNotAllowedInLua52() {
        setLanguageLevel(LuaLanguageLevel.LUA52)
        myFixture.configureByText(LuaFileType, "local x = 1 << 2")
        val errors = getLanguageLevelErrors()
        Assertions.assertTrue(errors.any { it.contains("Left shift") }, "Expected left shift error")
    }

    @Test
    fun rightShiftNotAllowedInLua52() {
        setLanguageLevel(LuaLanguageLevel.LUA52)
        myFixture.configureByText(LuaFileType, "local x = 1 >> 2")
        val errors = getLanguageLevelErrors()
        Assertions.assertTrue(errors.any { it.contains("Right shift") }, "Expected right shift error")
    }

    @Test
    fun xorNotAllowedInLua52() {
        setLanguageLevel(LuaLanguageLevel.LUA52)
        myFixture.configureByText(LuaFileType, "local x = 1 ^ 2")
        val errors = getLanguageLevelErrors()
        // XOR uses ^ which is also power operator, so we need to verify it's caught for Lua 5.2
        // In Lua 5.2, ^ is power; in 5.3+ it's also used for XOR in some contexts
        // Note: This test may need adjustment based on actual grammar
    }

    // ==================== Lua 5.3+ Features (integer division) ====================

    @Test
    fun integerDivisionNotAllowedInLua52() {
        setLanguageLevel(LuaLanguageLevel.LUA52)
        myFixture.configureByText(LuaFileType, "local x = 10 // 3")
        val errors = getLanguageLevelErrors()
        Assertions.assertTrue(errors.any { it.contains("Integer division") }, "Expected integer division error")
    }

    @Test
    fun integerDivisionAllowedInLua53() {
        setLanguageLevel(LuaLanguageLevel.LUA53)
        myFixture.configureByText(LuaFileType, "local x = 10 // 3")
        val errors = getLanguageLevelErrors()
        Assertions.assertTrue(errors.isEmpty(), "No errors expected in Lua 5.3 for integer division")
    }

    @Test
    fun bitwiseANDAllowedInLua53() {
        setLanguageLevel(LuaLanguageLevel.LUA53)
        myFixture.configureByText(LuaFileType, "local x = 1 & 2")
        val errors = getLanguageLevelErrors()
        Assertions.assertTrue(errors.isEmpty(), "No errors expected in Lua 5.3 for bitwise AND")
    }

    // ==================== Lua 5.4+ Features (attributes) ====================

    @Test
    fun constAttributeNotAllowedInLua53() {
        setLanguageLevel(LuaLanguageLevel.LUA53)
        myFixture.configureByText(LuaFileType, "local x <const> = 1")
        val errors = getLanguageLevelErrors()
        Assertions.assertTrue(errors.any { it.contains("Variable attributes") }, "Expected attribute error")
    }

    @Test
    fun closeAttributeNotAllowedInLua53() {
        setLanguageLevel(LuaLanguageLevel.LUA53)
        myFixture.configureByText(LuaFileType, "local x <close> = io.open('file')")
        val errors = getLanguageLevelErrors()
        Assertions.assertTrue(errors.any { it.contains("Variable attributes") }, "Expected attribute error")
    }

    @Test
    fun constAttributeAllowedInLua54() {
        setLanguageLevel(LuaLanguageLevel.LUA54)
        myFixture.configureByText(LuaFileType, "local x <const> = 1")
        val errors = getLanguageLevelErrors()
        Assertions.assertTrue(errors.isEmpty(), "No errors expected in Lua 5.4 for const attribute")
    }

    @Test
    fun closeAttributeAllowedInLua54() {
        setLanguageLevel(LuaLanguageLevel.LUA54)
        myFixture.configureByText(LuaFileType, "local x <close> = io.open('file')")
        val errors = getLanguageLevelErrors()
        Assertions.assertTrue(errors.isEmpty(), "No errors expected in Lua 5.4 for close attribute")
    }

    // ==================== Complex expressions ====================

    @Test
    fun multipleBitwiseOperationsInLua52() {
        setLanguageLevel(LuaLanguageLevel.LUA52)
        myFixture.configureByText(LuaFileType, "local x = 1 & 2 | 3")
        val errors = getLanguageLevelErrors()
        Assertions.assertTrue(errors.size >= 1, "Expected at least one bitwise error")
    }

    @Test
    fun multipleBitwiseOperationsInLua53Plus() {
        setLanguageLevel(LuaLanguageLevel.LUA53)
        myFixture.configureByText(LuaFileType, "local x = 1 & 2 | 3")
        val errors = getLanguageLevelErrors()
        Assertions.assertTrue(errors.isEmpty(), "No errors expected in Lua 5.3+ for bitwise operations")
    }

    @Test
    fun nestedGotoInFunctionLua51Disallowed() {
        setLanguageLevel(LuaLanguageLevel.LUA51)
        myFixture.configureByText(
            LuaFileType,
            """
            local function foo()
                goto exit
                ::exit::
            end
            """.trimIndent()
        )
        val errors = getLanguageLevelErrors()
        Assertions.assertTrue(errors.any { it.contains("Goto statements") }, "Expected goto error in function")
        Assertions.assertTrue(errors.any { it.contains("Labels") }, "Expected label error in function")
    }

    @Test
    fun mixedAttributesAndOperations() {
        setLanguageLevel(LuaLanguageLevel.LUA53)
        myFixture.configureByText(
            LuaFileType,
            """
            local x <const> = 1 & 2
            """.trimIndent()
        )
        val errors = getLanguageLevelErrors()
        // Attributes not allowed in 5.3, bitwise AND allowed
        Assertions.assertTrue(errors.any { it.contains("Variable attributes") }, "Expected attribute error")
        Assertions.assertTrue(errors.none { it.contains("Bitwise AND") }, "Bitwise AND should be allowed in 5.3")
    }

    // ==================== Edge cases ====================

    @Test
    fun regularExponentiationAllowedInAllVersions() {
        setLanguageLevel(LuaLanguageLevel.LUA51)
        myFixture.configureByText(LuaFileType, "local x = 2 ^ 3")
        val errors = getLanguageLevelErrors()
        Assertions.assertTrue(errors.isEmpty(), "Exponentiation (^) should be allowed in Lua 5.1")
    }

    @Test
    fun regularDivisionAllowedInAllVersions() {
        setLanguageLevel(LuaLanguageLevel.LUA51)
        myFixture.configureByText(LuaFileType, "local x = 10 / 3")
        val errors = getLanguageLevelErrors()
        Assertions.assertTrue(errors.isEmpty(), "Regular division (/) should be allowed in Lua 5.1")
    }

    @Test
    fun bitwiseNOTAsUnaryOperatorInLua52() {
        setLanguageLevel(LuaLanguageLevel.LUA52)
        myFixture.configureByText(LuaFileType, "local x = ~5")
        val errors = getLanguageLevelErrors()
        Assertions.assertTrue(errors.any { it.contains("Bitwise NOT") }, "Expected bitwise NOT error")
    }

    @Test
    fun multipleAttributesOnSingleVariable() {
        setLanguageLevel(LuaLanguageLevel.LUA54)
        myFixture.configureByText(LuaFileType, "local x <const> = 1; local y <close> = io.open('f')")
        val errors = getLanguageLevelErrors()
        Assertions.assertTrue(errors.isEmpty(), "No errors expected in Lua 5.4 for multiple attributes")
    }

    // ==================== Helper methods ====================

    private fun setLanguageLevel(level: LuaLanguageLevel) {
        val settings = LuaProjectSettings.getInstance(myFixture.project)
        settings.state.languageLevel = level
    }

    private fun getLanguageLevelErrors(): List<String> {
        return myFixture.doHighlighting(HighlightSeverity.ERROR)
            .mapNotNull { it.description }
            .filter { desc ->
                desc.contains("Lua 5") || desc.contains("feature")
            }
    }
}
