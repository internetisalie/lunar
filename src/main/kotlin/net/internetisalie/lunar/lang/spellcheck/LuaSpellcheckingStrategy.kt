package net.internetisalie.lunar.lang.spellcheck

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.spellchecker.inspections.PlainTextSplitter
import com.intellij.spellchecker.tokenizer.SpellcheckingStrategy
import com.intellij.spellchecker.tokenizer.TokenConsumer
import com.intellij.spellchecker.tokenizer.Tokenizer
import net.internetisalie.lunar.lang.psi.LuaElementTypes
import net.internetisalie.lunar.lang.psi.LuaLazyElementTypes
import net.internetisalie.lunar.lang.psi.LuaNameRef
import net.internetisalie.lunar.lang.syntax.LuaSyntax
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsComment
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsDescription
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsElementTypes

/**
 * Spellchecking strategy for the Lua language (EDITOR-02).
 *
 * Routes each PSI element to the appropriate tokenizer:
 * - LuaCATS comments: prose-only tokenizer (descriptions only, not tags/types)
 * - Regular comments (SHORTCOMMENT, LONGCOMMENT): TEXT_TOKENIZER
 * - SHEBANG: EMPTY_TOKENIZER (not spellchecked)
 * - String literals: LuaStringTokenizer (delimiter-strip + escape-aware)
 * - Name references (LuaNameRef): LuaIdentifierTokenizer, which itself emits only for names in
 *   declaration position (camelCase/snake_case split + suppression)
 * - Everything else: EMPTY_TOKENIZER
 */
class LuaSpellcheckingStrategy : SpellcheckingStrategy(), DumbAware {

    private val stringTokenizer = LuaStringTokenizer()
    private val identifierTokenizer = LuaIdentifierTokenizer()
    private val catsCommentTokenizer = CatsProseTokenizer()

    override fun getTokenizer(element: PsiElement): Tokenizer<*> {
        if (isInjectedLanguageFragment(element)) return EMPTY_TOKENIZER
        val type = element.node?.elementType ?: return EMPTY_TOKENIZER
        return when {
            type == LuaLazyElementTypes.LUACATS_COMMENT -> catsCommentTokenizer
            type == LuaElementTypes.SHEBANG -> EMPTY_TOKENIZER
            LuaSyntax.CommentTokens.contains(type) -> TEXT_TOKENIZER
            LuaSyntax.StringLiteralTokens.contains(type) -> stringTokenizer
            element is LuaNameRef -> identifierTokenizer
            else -> EMPTY_TOKENIZER
        }
    }

    /**
     * Tokenizer for LuaCATS lazy-parseable comments.
     *
     * Only visits LuaCatsDescription and plain LuaCatsComment (COMMENT element type)
     * children, producing prose tokens. Tag names, type identifiers, and LuaCATS
     * keywords are skipped (not descended into).
     *
     * Design §3.5.
     */
    private class CatsProseTokenizer : Tokenizer<PsiElement>() {
        override fun tokenize(element: PsiElement, consumer: TokenConsumer) {
            val comment = element as? LuaCatsComment ?: return
            val descriptions = comment.getDescriptionList()
            for (desc in descriptions) {
                consumeProse(desc, consumer)
            }
            consumeInlineComments(comment, consumer)
        }

        private fun consumeProse(desc: LuaCatsDescription, consumer: TokenConsumer) {
            val text = desc.text
            if (text.isNotEmpty()) {
                consumer.consumeToken(desc, text, false, 0, TextRange.allOf(text), PlainTextSplitter.getInstance())
            }
        }

        private fun consumeInlineComments(comment: LuaCatsComment, consumer: TokenConsumer) {
            var child = comment.firstChild
            while (child != null) {
                val childType = child.node?.elementType
                if (childType == LuaCatsElementTypes.COMMENT) {
                    val text = child.text
                    if (text.isNotEmpty()) {
                        consumer.consumeToken(child, text, false, 0, TextRange.allOf(text), PlainTextSplitter.getInstance())
                    }
                }
                child = child.nextSibling
            }
        }
    }
}
