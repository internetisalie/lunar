package net.internetisalie.lunar.lang.insight

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
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

    /**
     * BUG-412: the intention must never emit source the parser rejects.
     *
     * This drives the real machinery — `findSingleIntention` + `launchAction` + a reparse — rather
     * than asserting on the encoder, because the encoder-level round trip is blind to the defect:
     * `extractLuaString` strips a fixed number of characters off each end without checking where
     * the string actually closes, so the broken `[[]=]]]` extracts back to `]=]` and looks fine
     * while `luac` reports *unexpected symbol near ']'*.
     *
     * `a]]b` (covered above) happens to pick a working level even with the bug present, which is
     * why the defect survived: the obvious example is the one that passes.
     */
    @Test
    fun testLongConversionAlwaysParses() {
        for (value in listOf("]=]", "]==]", "abc]", "x]=", "]]")) {
            val escaped = value.replace("\\", "\\\\").replace("\"", "\\\"")
            myFixture.configureByText("test.lua", "local s = \"<caret>$escaped\"")
            myFixture.launchAction(myFixture.findSingleIntention("Convert to long-bracket string"))

            val text = myFixture.editor.document.text
            val reparsed = myFixture.configureByText("check.lua", text)
            val error = PsiTreeUtil.findChildOfType(reparsed, PsiErrorElement::class.java)
            assertNull("converting \"$value\" produced unparseable Lua: $text — ${error?.errorDescription}", error)
            assertEquals("$value: value must survive the conversion", value, extractLuaString(text.removePrefix("local s = ")))
        }
    }

    @Test
    fun testNotOfferedOutsideString() {
        myFixture.configureByText("test.lua", "local s<caret> = 1")
        assertEmpty(myFixture.filterAvailableIntentions("Convert to single-quoted string"))
        assertEmpty(myFixture.filterAvailableIntentions("Convert to double-quoted string"))
        assertEmpty(myFixture.filterAvailableIntentions("Convert to long-bracket string"))
    }
}
