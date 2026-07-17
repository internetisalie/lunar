package net.internetisalie.lunar.lang.syntax

import com.intellij.lang.annotation.HighlightSeverity
import net.internetisalie.lunar.BaseDocumentTest
import net.internetisalie.lunar.lang.LuaFileType
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Regression tests for BUG-386: [LuaLongStringAnnotator] and [LuaLongCommentAnnotator]
 * must not throw [StringIndexOutOfBoundsException] when the lexer produces a truncated
 * token (e.g. `[==` or `--[=` at EOF, typed mid-keystroke).
 */
class LuaLongBracketAnnotatorTest : BaseDocumentTest() {

    // ── BUG-386: truncated tokens must not throw ─────────────────────────────────

    @Test
    fun `truncated long-string open bracket does not throw`() {
        myFixture.configureByText(LuaFileType, "[==")
        assertDoesNotThrow {
            myFixture.doHighlighting(HighlightSeverity.TEXT_ATTRIBUTES)
        }
    }

    @Test
    fun `truncated long-comment open bracket does not throw`() {
        myFixture.configureByText(LuaFileType, "--[=")
        assertDoesNotThrow {
            myFixture.doHighlighting(HighlightSeverity.TEXT_ATTRIBUTES)
        }
    }

    // ── Well-formed long brackets are still highlighted ───────────────────────────

    @Test
    fun `level-0 long string brackets are highlighted`() {
        myFixture.configureByText(LuaFileType, "local s = [[hello]]")
        val highlights = myFixture.doHighlighting(HighlightSeverity.TEXT_ATTRIBUTES)
        assertTrue(highlights.any { it.forcedTextAttributesKey == LuaHighlight.LONGSTRING_BRACES })
    }

    @Test
    fun `level-2 long string brackets are highlighted`() {
        myFixture.configureByText(LuaFileType, "local s = [==[hello]==]")
        val highlights = myFixture.doHighlighting(HighlightSeverity.TEXT_ATTRIBUTES)
        assertTrue(highlights.any { it.forcedTextAttributesKey == LuaHighlight.LONGSTRING_BRACES })
    }

    @Test
    fun `level-1 long comment brackets are highlighted`() {
        myFixture.configureByText(LuaFileType, "--[=[hello]=]")
        val highlights = myFixture.doHighlighting(HighlightSeverity.TEXT_ATTRIBUTES)
        assertTrue(highlights.any { it.forcedTextAttributesKey == LuaHighlight.LONGCOMMENT_BRACES })
    }
}
