package net.internetisalie.lunar.refactoring

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.rename.RenameUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.LuaKeywords
import net.internetisalie.lunar.lang.psi.LuaLabelName
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Unit tests for [LuaNamesValidator]. The validator is a pure function, so it is instantiated
 * directly and called with `project = null`; no rename UI is required.
 */
@RunWith(JUnit4::class)
class LuaNamesValidatorTest : BasePlatformTestCase() {
    private val validator = LuaNamesValidator()

    @Test
    fun testValidIdentifierAccepted() {
        assertTrue(validator.isIdentifier("foo", null))
        assertTrue(validator.isIdentifier("_x1", null))
        assertTrue(validator.isIdentifier("X", null))
        assertFalse(validator.isKeyword("foo", null))
    }

    @Test
    fun testKeywordRejected() {
        assertTrue(validator.isKeyword("local", null))
        assertFalse(validator.isIdentifier("local", null))
    }

    @Test
    fun testInvalidIdentifierRejected() {
        assertFalse(validator.isIdentifier("1var", null))
        assertFalse(validator.isIdentifier("a-b", null))
        assertFalse(validator.isIdentifier("", null))
        assertFalse(validator.isIdentifier("foo bar", null))
        assertFalse(validator.isKeyword("1var", null))
    }

    @Test
    fun testGotoIsKeyword() {
        assertTrue(validator.isKeyword("goto", null))
        assertFalse(validator.isIdentifier("goto", null))
    }

    @Test
    fun testEndIsKeyword() {
        assertTrue(validator.isKeyword("end", null))
        assertFalse(validator.isIdentifier("end", null))
    }

    @Test
    fun testNearKeywordIsValidIdentifier() {
        assertTrue(validator.isIdentifier("end_", null))
        assertTrue(validator.isIdentifier("End", null))
        assertFalse(validator.isKeyword("End", null))
    }

    @Test
    fun testAllReservedWordsAreKeywords() {
        for (word in LuaKeywords.RESERVED) {
            assertTrue("'$word' should be a keyword", validator.isKeyword(word, null))
            assertFalse("'$word' should not be an identifier", validator.isIdentifier(word, null))
        }
    }

    /**
     * TC-04-O (`REFACT-04-06`) — `RenameUtil.isValidName` reaches [LuaNamesValidator] through
     * `LanguageNamesValidation` for a [LuaLabelName], proving the validator is on the label
     * rename **path** rather than merely correct in isolation (design §6 E-7). The other tests in
     * this class already assert the validator's own booleans; this one asserts the platform wiring
     * that puts a `LuaLabelName` in front of it.
     *
     * **Mutation:** drop the `&& !LuaKeywords.isReserved(name)` clause from
     * [LuaNamesValidator.isIdentifier] (`LuaNamesValidator.kt:18-21`) — `"end"` would then be
     * accepted as a valid label rename target.
     */
    @Test
    fun testRenameUtilReachesValidatorForLabel() {
        val file = myFixture.configureByText("test.lua", "::<caret>top::")
        val labelName = PsiTreeUtil.findChildOfType(file, LuaLabelName::class.java)!!

        assertFalse(
            "a reserved word must be rejected as a label rename target",
            RenameUtil.isValidName(project, labelName, "end"),
        )
        assertTrue(
            "a plain identifier must be accepted as a label rename target",
            RenameUtil.isValidName(project, labelName, "finished"),
        )
    }
}
