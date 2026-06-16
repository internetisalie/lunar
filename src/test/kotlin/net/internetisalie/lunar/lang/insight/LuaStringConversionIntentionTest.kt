package net.internetisalie.lunar.lang.insight

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.syntax.extractLuaString
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class LuaStringConversionIntentionTest : BasePlatformTestCase() {

    private fun convert(text: String, action: String): String {
        myFixture.configureByText("test.lua", text)
        val intention = myFixture.findSingleIntention(action)
        myFixture.launchAction(intention)
        return myFixture.editor.document.text
    }

    @Test
    fun testSingleToDouble() {
        myFixture.configureByText("test.lua", "local s = 'hel<caret>lo'")
        val intention = myFixture.findSingleIntention("Convert to double-quoted string")
        myFixture.launchAction(intention)
        myFixture.checkResult("local s = \"hello\"")
    }

    @Test
    fun testDoubleToLong() {
        myFixture.configureByText("test.lua", "local s = \"hel<caret>lo\"")
        val intention = myFixture.findSingleIntention("Convert to long-bracket string")
        myFixture.launchAction(intention)
        myFixture.checkResult("local s = [[hello]]")
    }

    @Test
    fun testLongToSingle() {
        myFixture.configureByText("test.lua", "local s = [[hel<caret>lo]]")
        val intention = myFixture.findSingleIntention("Convert to single-quoted string")
        myFixture.launchAction(intention)
        myFixture.checkResult("local s = 'hello'")
    }

    @Test
    fun testSingleToDoubleReescapesDelimiter() {
        myFixture.configureByText("test.lua", "local s = 'a<caret>\"b'")
        val intention = myFixture.findSingleIntention("Convert to double-quoted string")
        myFixture.launchAction(intention)
        myFixture.checkResult("local s = \"a\\\"b\"")
    }

    @Test
    fun testLongToSingleUnescapesDelimiter() {
        // Reaching single from double goes through long; the long form holds the raw value,
        // and converting long -> single re-escapes only the single quote, leaving " bare.
        myFixture.configureByText("test.lua", "local s = [[a<caret>\"b]]")
        val intention = myFixture.findSingleIntention("Convert to single-quoted string")
        myFixture.launchAction(intention)
        myFixture.checkResult("local s = 'a\"b'")
    }

    @Test
    fun testLongToSingleEscapesQuoteInContent() {
        myFixture.configureByText("test.lua", "local s = [[it<caret>'s]]")
        val intention = myFixture.findSingleIntention("Convert to single-quoted string")
        myFixture.launchAction(intention)
        myFixture.checkResult("local s = 'it\\'s'")
    }

    @Test
    fun testEscapeResolvedIntoLongContent() {
        myFixture.configureByText("test.lua", "local s = \"tab<caret>\\there\"")
        val intention = myFixture.findSingleIntention("Convert to long-bracket string")
        myFixture.launchAction(intention)
        myFixture.checkResult("local s = [[tab\there]]")
    }

    @Test
    fun testLongBracketGuardOnCloserContent() {
        val result = convert("local s = \"a<caret>]]b\"", "Convert to long-bracket string")
        val literal = result.removePrefix("local s = ")
        assertEquals("a]]b", extractLuaString(literal))
    }

    @Test
    fun testNotOfferedOutsideString() {
        myFixture.configureByText("test.lua", "local s<caret> = 1")
        assertEmpty(myFixture.filterAvailableIntentions("Convert to single-quoted string"))
        assertEmpty(myFixture.filterAvailableIntentions("Convert to double-quoted string"))
        assertEmpty(myFixture.filterAvailableIntentions("Convert to long-bracket string"))
    }
}
