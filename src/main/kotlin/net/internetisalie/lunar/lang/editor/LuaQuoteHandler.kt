package net.internetisalie.lunar.lang.editor

import com.intellij.codeInsight.editorActions.MultiCharQuoteHandler
import com.intellij.codeInsight.editorActions.SimpleTokenSetQuoteHandler
import com.intellij.openapi.editor.highlighter.HighlighterIterator
import net.internetisalie.lunar.lang.syntax.LuaSyntax

/**
 * Auto-closes `"` and `'`, skips over an existing closer, and enables backspace-unpair
 * (via the platform BackspaceHandler). Realizes EDITOR-01-03 and the quote half of EDITOR-01-04.
 *
 * Extends [SimpleTokenSetQuoteHandler] seeded on [LuaSyntax.StringLiteralTokens] (the STRING
 * element type). The super-class delivers isOpeningQuote / isClosingQuote / isInsideLiteral /
 * hasNonClosedLiteral; we only need to add the MultiCharQuoteHandler contract.
 *
 * Mid-word suppression (design §3.2): [getClosingQuote] returns null when the character
 * immediately before the opening quote is a letter, digit, or `_` — e.g. typing `'` in `don't`
 * produces no auto-closer.
 *
 * Called AFTER the opening quote is already inserted; `offset` is the caret position just after
 * the typed char, so `charsSequence[offset - 2]` is the char before the opening quote.
 */
class LuaQuoteHandler :
    SimpleTokenSetQuoteHandler(LuaSyntax.StringLiteralTokens),
    MultiCharQuoteHandler {

    /**
     * Returns the matching quote char as a 1-char sequence, or null to suppress auto-close.
     * [offset] is the post-insertion caret position; `[offset-1]` is the typed opening quote.
     */
    override fun getClosingQuote(iterator: HighlighterIterator, offset: Int): CharSequence? {
        if (offset < 2) return null
        val charBeforeQuote = iterator.document.charsSequence[offset - 2]
        if (charBeforeQuote.isLetterOrDigit() || charBeforeQuote == '_') return null
        return matchingQuote(iterator.document.charsSequence[offset - 1])
    }

    private fun matchingQuote(openingChar: Char): CharSequence? =
        when (openingChar) {
            '"', '\'' -> openingChar.toString()
            else -> null
        }
}
