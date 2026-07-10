package net.internetisalie.lunar.lang.spellcheck

import com.intellij.codeInsight.CodeInsightUtilCore
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.spellchecker.inspections.PlainTextSplitter
import com.intellij.spellchecker.tokenizer.EscapeSequenceTokenizer
import com.intellij.spellchecker.tokenizer.TokenConsumer

/**
 * Spellchecks the textual content of a Lua string literal (EDITOR-02-02).
 *
 * Strips the delimiter (quotes or long-bracket) and, for short strings,
 * decodes escape sequences using [CodeInsightUtilCore.parseStringCharacters]
 * so typo highlight ranges map back to the correct source offsets.
 *
 * Design §2.2, §3.2.
 */
class LuaStringTokenizer : EscapeSequenceTokenizer<PsiElement>() {

    override fun tokenize(element: PsiElement, consumer: TokenConsumer) {
        val raw = element.text
        val (inner, prefixLen) = stripDelimiters(raw)
        if (inner.isEmpty()) return
        if (!inner.contains('\\') || isLongBracket(raw)) {
            consumer.consumeToken(element, inner, false, prefixLen, TextRange.allOf(inner), PlainTextSplitter.getInstance())
        } else {
            val sb = StringBuilder(inner.length)
            val offsets = IntArray(inner.length + 1)
            CodeInsightUtilCore.parseStringCharacters(inner, sb, offsets)
            processTextWithOffsets(element, consumer, sb, offsets, prefixLen)
        }
    }

    companion object {
        /** Returns (innerText, prefixLength) for all Lua string forms. */
        fun stripDelimiters(raw: String): Pair<String, Int> {
            if (raw.startsWith("[")) {
                val level = countLongBracketLevel(raw)
                if (level >= 0) {
                    val prefix = 2 + level
                    val suffix = 2 + level
                    val end = raw.length - suffix
                    val inner = if (end > prefix) raw.substring(prefix, end) else ""
                    return inner to prefix
                }
            }
            if (raw.length >= 2) {
                val inner = raw.substring(1, raw.length - 1)
                return inner to 1
            }
            return raw to 0
        }

        /** Returns the `=` count in `[====[`, or -1 if not a long bracket. */
        private fun countLongBracketLevel(raw: String): Int {
            if (!raw.startsWith("[")) return -1
            var i = 1
            while (i < raw.length && raw[i] == '=') i++
            if (i >= raw.length || raw[i] != '[') return -1
            return i - 1
        }

        fun isLongBracket(raw: String): Boolean = countLongBracketLevel(raw) >= 0
    }
}
