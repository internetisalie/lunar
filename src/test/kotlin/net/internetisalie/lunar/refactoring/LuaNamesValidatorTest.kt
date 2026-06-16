package net.internetisalie.lunar.refactoring

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.LuaKeywords
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
}
