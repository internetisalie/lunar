package net.internetisalie.lunar.analysis.inspections

import com.intellij.lang.annotation.HighlightSeverity
import net.internetisalie.lunar.BaseDocumentTest
import net.internetisalie.lunar.lang.LuaFileType
import net.internetisalie.lunar.lang.LuaLanguageLevel
import net.internetisalie.lunar.settings.LuaProjectSettings
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Migrated from `LuaLanguageLevelAnnotatorTest` when INSP-09 replaced the annotator with
 * [LuaLanguageLevelInspection]. Same inputs, same messages, same `doHighlighting(ERROR)` assertions;
 * only the source of the diagnostics changed (annotator → enabled inspection).
 */
class LuaLanguageLevelInspectionTest : BaseDocumentTest() {

    @BeforeEach
    fun setupProject() {
        myFixture.enableInspections(LuaLanguageLevelInspection())
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
        getLanguageLevelErrors()
        // XOR uses ^ which is also power operator; ^ is never flagged. No assertion (parity with original).
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
            """.trimIndent(),
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
            """.trimIndent(),
        )
        val errors = getLanguageLevelErrors()
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

    // ==================== Version transition tests (5.1 → 5.2 → 5.3 → 5.4) ====================

    @Test
    fun lua51ToLua52TransitionGoto() {
        setLanguageLevel(LuaLanguageLevel.LUA51)
        myFixture.configureByText(LuaFileType, "goto exit ::exit::")
        val errorsBefore = getLanguageLevelErrors()
        Assertions.assertTrue(errorsBefore.size >= 2, "Expected errors for goto and label in Lua 5.1")

        setLanguageLevel(LuaLanguageLevel.LUA52)
        myFixture.configureByText(LuaFileType, "goto exit ::exit::")
        val errorsAfter = getLanguageLevelErrors()
        Assertions.assertTrue(errorsAfter.isEmpty(), "No errors expected after upgrade to Lua 5.2")
    }

    @Test
    fun lua52ToLua53TransitionBitwiseOps() {
        setLanguageLevel(LuaLanguageLevel.LUA52)
        myFixture.configureByText(LuaFileType, "local x = 1 & 2")
        val errorsBefore = getLanguageLevelErrors()
        Assertions.assertTrue(errorsBefore.any { it.contains("Bitwise AND") }, "Expected error in Lua 5.2")

        setLanguageLevel(LuaLanguageLevel.LUA53)
        myFixture.configureByText(LuaFileType, "local x = 1 & 2")
        val errorsAfter = getLanguageLevelErrors()
        Assertions.assertTrue(errorsAfter.isEmpty(), "No errors expected after upgrade to Lua 5.3")
    }

    @Test
    fun lua53ToLua54TransitionAttributes() {
        setLanguageLevel(LuaLanguageLevel.LUA53)
        myFixture.configureByText(LuaFileType, "local x <const> = 1")
        val errorsBefore = getLanguageLevelErrors()
        Assertions.assertTrue(errorsBefore.any { it.contains("Variable attributes") }, "Expected error in Lua 5.3")

        setLanguageLevel(LuaLanguageLevel.LUA54)
        myFixture.configureByText(LuaFileType, "local x <const> = 1")
        val errorsAfter = getLanguageLevelErrors()
        Assertions.assertTrue(errorsAfter.isEmpty(), "No errors expected after upgrade to Lua 5.4")
    }

    // ==================== No false positives: Allowed features ====================

    @Test
    fun noErrorsForFeaturesAllowedInLua51() {
        setLanguageLevel(LuaLanguageLevel.LUA51)
        myFixture.configureByText(
            LuaFileType,
            """
            local x = 1
            local y = x + 1
            local z = x * 2
            local a = x ^ 2
            local t = {1, 2, 3}
            function foo() return x end
            """.trimIndent(),
        )
        val errors = getLanguageLevelErrors()
        Assertions.assertTrue(errors.isEmpty(), "Standard Lua 5.1 features should not error")
    }

    @Test
    fun noErrorsForAllFeaturesInLua54() {
        setLanguageLevel(LuaLanguageLevel.LUA54)
        myFixture.configureByText(
            LuaFileType,
            """
            goto skip
            ::skip::
            local x = 1 & 2 | 3 ~ 4 << 1 >> 1
            local y = 10 // 3
            local a <const> = 1
            local b <close> = io.open('f')
            """.trimIndent(),
        )
        val errors = getLanguageLevelErrors()
        Assertions.assertTrue(errors.isEmpty(), "All features should be allowed in Lua 5.4")
    }

    @Test
    fun noErrorsForRegularOperatorsInAllVersions() {
        for (level in listOf(LuaLanguageLevel.LUA51, LuaLanguageLevel.LUA52, LuaLanguageLevel.LUA53, LuaLanguageLevel.LUA54)) {
            setLanguageLevel(level)
            myFixture.configureByText(LuaFileType, "local a = 1 + 2")
            var errors = getLanguageLevelErrors()
            Assertions.assertTrue(errors.isEmpty(), "Addition should work in $level")

            setLanguageLevel(level)
            myFixture.configureByText(LuaFileType, "local b = 3 - 1")
            errors = getLanguageLevelErrors()
            Assertions.assertTrue(errors.isEmpty(), "Subtraction should work in $level")

            setLanguageLevel(level)
            myFixture.configureByText(LuaFileType, "local c = 2 * 3")
            errors = getLanguageLevelErrors()
            Assertions.assertTrue(errors.isEmpty(), "Multiplication should work in $level")

            setLanguageLevel(level)
            myFixture.configureByText(LuaFileType, "local d = 10 / 3")
            errors = getLanguageLevelErrors()
            Assertions.assertTrue(errors.isEmpty(), "Division should work in $level")

            setLanguageLevel(level)
            myFixture.configureByText(LuaFileType, "local e = 2 ^ 3")
            errors = getLanguageLevelErrors()
            Assertions.assertTrue(errors.isEmpty(), "Power should work in $level")

            setLanguageLevel(level)
            myFixture.configureByText(LuaFileType, "local f = \"hello\" .. \"world\"")
            errors = getLanguageLevelErrors()
            Assertions.assertTrue(errors.isEmpty(), "Concatenation should work in $level")
        }
    }

    // ==================== Cross-version compatibility ====================

    @Test
    fun mixedCodeWithSomeDisallowedFeatures() {
        setLanguageLevel(LuaLanguageLevel.LUA52)
        myFixture.configureByText(
            LuaFileType,
            """
            -- Allowed
            goto exit
            ::exit::
            local x = 10 / 3

            -- Not allowed
            local y = 1 & 2
            local z = 10 // 3
            """.trimIndent(),
        )
        val errors = getLanguageLevelErrors()
        Assertions.assertEquals(2, errors.size, "Should have exactly 2 errors for disallowed bitwise/div")
    }

    @Test
    fun noErrorsWhenLanguageLevelExceeds() {
        setLanguageLevel(LuaLanguageLevel.LUA54)
        myFixture.configureByText(
            LuaFileType,
            """
            -- All of these should be fine in Lua 5.4
            goto skip
            ::skip::
            local a = 1 & 2
            local b = 1 | 2
            local c = ~1
            local d = 1 << 2
            local e = 1 >> 2
            local f = 1 ^ 2
            local g = 10 // 3
            local h <const> = 1
            local i <close> = nil
            """.trimIndent(),
        )
        val errors = getLanguageLevelErrors()
        Assertions.assertTrue(errors.isEmpty(), "Lua 5.4 should support all features")
    }

    // ==================== Nested and complex expressions ====================

    @Test
    fun nestedBitwiseOperationsInLua52() {
        setLanguageLevel(LuaLanguageLevel.LUA52)
        myFixture.configureByText(LuaFileType, "local x = (1 & 2) | (3 << 2)")
        val errors = getLanguageLevelErrors()
        Assertions.assertTrue(errors.size >= 2, "Multiple bitwise ops should each error")
    }

    @Test
    fun bitwiseOperatorsInFunctionCall() {
        setLanguageLevel(LuaLanguageLevel.LUA52)
        myFixture.configureByText(LuaFileType, "print(1 & 2)")
        val errors = getLanguageLevelErrors()
        Assertions.assertTrue(errors.any { it.contains("Bitwise AND") }, "Should detect bitwise in function call")
    }

    @Test
    fun bitwiseOperatorsInTableConstructor() {
        setLanguageLevel(LuaLanguageLevel.LUA52)
        myFixture.configureByText(LuaFileType, "local t = {1 & 2, 3 | 4}")
        val errors = getLanguageLevelErrors()
        Assertions.assertTrue(errors.size >= 2, "Should detect bitwise in table")
    }

    @Test
    fun integerDivisionInNestedExpression() {
        setLanguageLevel(LuaLanguageLevel.LUA52)
        myFixture.configureByText(LuaFileType, "local x = (10 // 3) + 5")
        val errors = getLanguageLevelErrors()
        Assertions.assertTrue(errors.any { it.contains("Integer division") }, "Should detect // in nested expr")
    }

    @Test
    fun attributesInMultipleLocalDeclarations() {
        setLanguageLevel(LuaLanguageLevel.LUA53)
        myFixture.configureByText(LuaFileType, "local x <const>, y <close> = 1, nil")
        val errors = getLanguageLevelErrors()
        Assertions.assertTrue(errors.size >= 2, "Both attributes should error in Lua 5.3")
    }

    // ==================== Boundary conditions ====================

    @Test
    fun emptyFileNoErrors() {
        setLanguageLevel(LuaLanguageLevel.LUA51)
        myFixture.configureByText(LuaFileType, "")
        val errors = getLanguageLevelErrors()
        Assertions.assertTrue(errors.isEmpty(), "Empty file should not error")
    }

    @Test
    fun onlyCommentsNoErrors() {
        setLanguageLevel(LuaLanguageLevel.LUA51)
        myFixture.configureByText(LuaFileType, "-- This is a comment")
        val errors = getLanguageLevelErrors()
        Assertions.assertTrue(errors.isEmpty(), "Comments should not error")
    }

    @Test
    fun stringContainingGotoNotError() {
        setLanguageLevel(LuaLanguageLevel.LUA51)
        myFixture.configureByText(LuaFileType, "local s = \"goto exit\"")
        val errors = getLanguageLevelErrors()
        Assertions.assertTrue(errors.isEmpty(), "String containing 'goto' should not error")
    }

    @Test
    fun stringContainingBitwiseSymbolsNotError() {
        setLanguageLevel(LuaLanguageLevel.LUA51)
        myFixture.configureByText(LuaFileType, "local s = \"1 & 2\"")
        val errors = getLanguageLevelErrors()
        Assertions.assertTrue(errors.isEmpty(), "String containing '&' should not error")
    }

    // ==================== Version-specific operator behavior ====================

    @Test
    fun xorBinaryInLua52() {
        setLanguageLevel(LuaLanguageLevel.LUA52)
        myFixture.configureByText(LuaFileType, "local x = 1 ^ 2")
        val errors = getLanguageLevelErrors()
        Assertions.assertTrue(errors.isEmpty(), "^ should be power in Lua 5.2, not bitwise XOR")
    }

    @Test
    fun xorBinaryInLua53Plus() {
        setLanguageLevel(LuaLanguageLevel.LUA53)
        myFixture.configureByText(LuaFileType, "local x = 1 ^ 2")
        val errors = getLanguageLevelErrors()
        Assertions.assertTrue(errors.isEmpty(), "^ is allowed in Lua 5.3+")
    }

    @Test
    fun unaryMinusAllowedInAllVersions() {
        for (level in listOf(LuaLanguageLevel.LUA51, LuaLanguageLevel.LUA52, LuaLanguageLevel.LUA53, LuaLanguageLevel.LUA54)) {
            setLanguageLevel(level)
            myFixture.configureByText(LuaFileType, "local x = -5")
            val errors = getLanguageLevelErrors()
            Assertions.assertTrue(errors.isEmpty(), "Unary minus should work in $level")
        }
    }

    @Test
    fun notOperatorAllowedInAllVersions() {
        for (level in listOf(LuaLanguageLevel.LUA51, LuaLanguageLevel.LUA52, LuaLanguageLevel.LUA53, LuaLanguageLevel.LUA54)) {
            setLanguageLevel(level)
            myFixture.configureByText(LuaFileType, "local x = not true")
            val errors = getLanguageLevelErrors()
            Assertions.assertTrue(errors.isEmpty(), "Not operator should work in $level")
        }
    }

    @Test
    fun lengthOperatorAllowedInAllVersions() {
        for (level in listOf(LuaLanguageLevel.LUA51, LuaLanguageLevel.LUA52, LuaLanguageLevel.LUA53, LuaLanguageLevel.LUA54)) {
            setLanguageLevel(level)
            myFixture.configureByText(LuaFileType, "local x = #t")
            val errors = getLanguageLevelErrors()
            Assertions.assertTrue(errors.isEmpty(), "Length operator should work in $level")
        }
    }

    @Test
    fun gotoWithinNestedScopes() {
        setLanguageLevel(LuaLanguageLevel.LUA51)
        myFixture.configureByText(
            LuaFileType,
            """
            local function foo()
                local function bar()
                    goto exit
                    ::exit::
                end
            end
            """.trimIndent(),
        )
        val errors = getLanguageLevelErrors()
        Assertions.assertTrue(errors.size >= 2, "Goto in nested function should error in Lua 5.1")
    }

    // ==================== Real-world usage patterns ====================

    @Test
    fun typicalModernLua54Code() {
        setLanguageLevel(LuaLanguageLevel.LUA54)
        myFixture.configureByText(
            LuaFileType,
            """
            local function process(data)
                if not data then
                    goto skip_processing
                end

                local result <const> = data & 0xFF
                local count <close> = io.open('count')

                return result // 16

                ::skip_processing::
                return 0
            end
            """.trimIndent(),
        )
        val errors = getLanguageLevelErrors()
        Assertions.assertTrue(errors.isEmpty(), "Modern Lua 5.4 code should be error-free")
    }

    @Test
    fun typicalLegacyLua51Code() {
        setLanguageLevel(LuaLanguageLevel.LUA51)
        myFixture.configureByText(
            LuaFileType,
            """
            local function process(data)
                if not data then
                    return 0
                end

                local result = data
                return result / 16
            end
            """.trimIndent(),
        )
        val errors = getLanguageLevelErrors()
        Assertions.assertTrue(errors.isEmpty(), "Legacy Lua 5.1 code should be error-free")
    }

    @Test
    fun migrationFromLua51ToLua53() {
        setLanguageLevel(LuaLanguageLevel.LUA53)
        myFixture.configureByText(
            LuaFileType,
            """
            -- Migrating to Lua 5.3
            local x = 10 // 3      -- Integer division (5.3+)
            local mask = 0xFF & data -- Bitwise AND (5.3+)
            local flags = mask | 0x80 -- Bitwise OR (5.3+)

            -- Still no goto/labels (Lua 5.1 compat)
            """.trimIndent(),
        )
        val errors = getLanguageLevelErrors()
        Assertions.assertTrue(errors.isEmpty(), "Lua 5.3 migration code should be error-free")
    }

    // ==================== Quick fixes (INSP-09-07) ====================

    @Test
    fun upgradeQuickFixRaisesLanguageLevel() {
        setLanguageLevel(LuaLanguageLevel.LUA51)
        myFixture.configureByText(LuaFileType, "go<caret>to exit")
        myFixture.doHighlighting(HighlightSeverity.ERROR)
        val fix = myFixture.findSingleIntention("Upgrade project to Lua 5.2")
        myFixture.launchAction(fix)
        Assertions.assertEquals(
            LuaLanguageLevel.LUA52,
            LuaProjectSettings.getInstance(myFixture.project).state.languageLevel,
            "Upgrade fix should raise the project language level to 5.2 (clearing the goto error)",
        )
    }

    /** TC-01: replace `7 // 2` with `math.floor(7 / 2)` at Lua 5.1 via the intention. */
    @Test
    fun replaceIntegerDivisionQuickFixRebuildsFloor() {
        setLanguageLevel(LuaLanguageLevel.LUA51)
        myFixture.configureByText(LuaFileType, "local n = 7 /<caret>/ 2")
        myFixture.doHighlighting(HighlightSeverity.ERROR)
        val fix = myFixture.findSingleIntention("Replace // with / and math.floor()")
        myFixture.launchAction(fix)
        myFixture.checkResult("local n = math.floor(7 / 2)")
    }

    /** TC-02: operands and parentheses are preserved (`(a+b) // c` → `math.floor((a+b) / c)`). */
    @Test
    fun replaceIntegerDivisionQuickFixPreservesOperands() {
        setLanguageLevel(LuaLanguageLevel.LUA51)
        myFixture.configureByText(LuaFileType, "local n = (a+b) /<caret>/ c")
        myFixture.doHighlighting(HighlightSeverity.ERROR)
        val fix = myFixture.findSingleIntention("Replace // with / and math.floor()")
        myFixture.launchAction(fix)
        myFixture.checkResult("local n = math.floor((a+b) / c)")
    }

    @Test
    fun removeGotoQuickFixDeletesStatement() {
        setLanguageLevel(LuaLanguageLevel.LUA51)
        myFixture.configureByText(LuaFileType, "go<caret>to exit")
        myFixture.doHighlighting(HighlightSeverity.ERROR)
        val fix = myFixture.findSingleIntention("Remove goto statement")
        myFixture.launchAction(fix)
        Assertions.assertFalse(myFixture.file.text.contains("goto"), "Remove fix should delete the goto statement")
    }

    // ==================== Lua 5.5+ Features (global declarations) ====================

    @Test
    fun globalVarDeclNotAllowedInLua54() {
        setLanguageLevel(LuaLanguageLevel.LUA54)
        myFixture.configureByText(LuaFileType, "global x = 10")
        val errors = getLanguageLevelErrors()
        Assertions.assertTrue(errors.any { it.contains("Global variable declarations") }, "Expected global var error")
    }

    @Test
    fun globalFuncDeclNotAllowedInLua54() {
        setLanguageLevel(LuaLanguageLevel.LUA54)
        myFixture.configureByText(LuaFileType, "global function f() end")
        val errors = getLanguageLevelErrors()
        Assertions.assertTrue(errors.any { it.contains("Global function declarations") }, "Expected global func error")
    }

    @Test
    fun globalModeDeclNotAllowedInLua54() {
        setLanguageLevel(LuaLanguageLevel.LUA54)
        myFixture.configureByText(LuaFileType, "global *")
        val errors = getLanguageLevelErrors()
        Assertions.assertTrue(errors.any { it.contains("Global mode declarations") }, "Expected global mode error")
    }

    @Test
    fun globalDeclarationsAllowedInLua55() {
        setLanguageLevel(LuaLanguageLevel.LUA55)
        myFixture.configureByText(LuaFileType, "global x = 10\nglobal function f() end\nglobal *")
        val errors = getLanguageLevelErrors()
        Assertions.assertTrue(errors.isEmpty(), "No errors expected in Lua 5.5 for global declarations")
    }

    @Test
    fun upgradeToLua55QuickFix() {
        setLanguageLevel(LuaLanguageLevel.LUA54)
        myFixture.configureByText(LuaFileType, "glo<caret>bal x = 10")
        myFixture.doHighlighting(HighlightSeverity.ERROR)
        val fix = myFixture.findSingleIntention("Upgrade project to Lua 5.5")
        myFixture.launchAction(fix)
        Assertions.assertEquals(
            LuaLanguageLevel.LUA55,
            LuaProjectSettings.getInstance(myFixture.project).state.languageLevel,
            "Upgrade fix should raise the project language level to 5.5",
        )
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
