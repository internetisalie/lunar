package net.internetisalie.lunar.lang.spellcheck

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.SyntaxTraverser
import com.intellij.psi.tree.TokenSet
import com.intellij.spellchecker.inspections.Splitter
import com.intellij.spellchecker.tokenizer.SpellcheckingStrategy
import com.intellij.spellchecker.tokenizer.TokenConsumer
import com.intellij.spellchecker.tokenizer.Tokenizer
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.psi.LuaElementTypes
import net.internetisalie.lunar.lang.psi.LuaNameList
import net.internetisalie.lunar.lang.psi.LuaNameRef
import net.internetisalie.lunar.lang.syntax.LuaSyntax

/**
 * Deterministic unit tests for the EDITOR-02 spellchecking strategy.
 *
 * Platform spellchecking runs through Grazie **asynchronously**, so typo highlights do not
 * surface during the fixture's synchronous `doHighlighting()` — asserting `<TYPO>` ranges
 * in-process is unreliable. These tests instead exercise the logic EDITOR-02 actually owns:
 * the strategy's tokenizer routing (§3.1) and each tokenizer's output / suppression
 * (§2.4, §3.2, §3.3). The live "typo underlined in the editor" behaviour is covered by the
 * human-verification checklist (VNC gate), not here.
 */
class LuaSpellcheckingStrategyTest : BasePlatformTestCase() {

    private val strategy = LuaSpellcheckingStrategy()

    private data class Captured(val text: String, val useRename: Boolean, val offset: Int, val range: TextRange)

    private class CapturingConsumer(val tokens: MutableList<Captured>) : TokenConsumer() {
        override fun consumeToken(
            element: PsiElement,
            text: String,
            useRename: Boolean,
            offset: Int,
            rangeToCheck: TextRange,
            splitter: Splitter,
        ) {
            tokens += Captured(text, useRename, offset, rangeToCheck)
        }
    }

    private fun <T : PsiElement> capture(tokenizer: Tokenizer<T>, element: T): List<Captured> {
        val tokens = mutableListOf<Captured>()
        ReadAction.run<RuntimeException> { tokenizer.tokenize(element, CapturingConsumer(tokens)) }
        return tokens
    }

    private fun tokenizerOf(element: PsiElement): Tokenizer<*> =
        ReadAction.compute<Tokenizer<*>, RuntimeException> { strategy.getTokenizer(element) }

    private fun firstOfType(set: TokenSet): PsiElement =
        SyntaxTraverser.psiTraverser(myFixture.file)
            .firstOrNull { it.node?.elementType?.let(set::contains) == true }
            ?: error("no element with type in $set")

    private inline fun <reified T : PsiElement> firstPsi(): T =
        SyntaxTraverser.psiTraverser(myFixture.file).filter(T::class.java).first()
            ?: error("no ${T::class.simpleName} in file")

    // --- LuaStringTokenizer delimiter stripping (pure) — §3.2 / TC-2, TC-3 ---

    fun testStripDoubleQuoted() {
        assertEquals("helo" to 1, LuaStringTokenizer.stripDelimiters("\"helo\""))
    }

    fun testStripLevelledLongBracket() {
        assertEquals("helo" to 4, LuaStringTokenizer.stripDelimiters("[==[helo]==]"))
        assertTrue(LuaStringTokenizer.isLongBracket("[==[x]==]"))
        assertFalse(LuaStringTokenizer.isLongBracket("\"x\""))
    }

    // --- Suppressions — §2.4 / TC-5 ---

    fun testSuppressesStdlibGlobal() = assertTrue(LuaSpellcheckSuppressions.isSuppressed("pairs", project))

    fun testSuppressesReservedKeyword() = assertTrue(LuaSpellcheckSuppressions.isSuppressed("function", project))

    fun testSuppressesCatsPrimitiveType() = assertTrue(LuaSpellcheckSuppressions.isSuppressed("number", project))

    fun testDoesNotSuppressPlainName() = assertFalse(LuaSpellcheckSuppressions.isSuppressed("recieveBuffer", project))

    // --- Strategy routing — §3.1 / TC-1, TC-6 ---

    fun testCommentRoutesToTextTokenizer() {
        myFixture.configureByText("a.lua", "-- helo world")
        assertSame(SpellcheckingStrategy.TEXT_TOKENIZER, tokenizerOf(firstOfType(LuaSyntax.CommentTokens)))
    }

    fun testShebangIsNotSpellchecked() {
        myFixture.configureByText("a.lua", "#!/usr/bin/lua\n-- ok")
        val shebang = SyntaxTraverser.psiTraverser(myFixture.file)
            .firstOrNull { it.node?.elementType == LuaElementTypes.SHEBANG } ?: error("no shebang")
        assertSame(SpellcheckingStrategy.EMPTY_TOKENIZER, tokenizerOf(shebang))
    }

    fun testStringRoutesToStringTokenizer() {
        myFixture.configureByText("a.lua", "local s = \"helo\"")
        assertTrue(tokenizerOf(firstOfType(LuaSyntax.StringLiteralTokens)) is LuaStringTokenizer)
    }

    fun testNameRefRoutesToIdentifierTokenizer() {
        myFixture.configureByText("a.lua", "local recieveBuffer = 1")
        assertTrue(tokenizerOf(firstPsi<LuaNameRef>()) is LuaIdentifierTokenizer)
    }

    // --- Identifier tokenizer: declaration positions vs suppression vs references — §3.3 (corrected) ---

    fun testLocalVarDeclarationEmitsRenamableToken() {
        myFixture.configureByText("a.lua", "local recieveBuffer = 1")
        val tokens = capture(LuaIdentifierTokenizer(), firstPsi<LuaNameRef>())
        assertEquals(1, tokens.size)
        assertEquals("recieveBuffer", tokens[0].text)
        assertTrue("identifier typos must offer Rename", tokens[0].useRename)
    }

    fun testSuppressedStdlibRedeclarationEmitsNothing() {
        myFixture.configureByText("a.lua", "local pairs = 1")
        assertTrue(capture(LuaIdentifierTokenizer(), firstPsi<LuaNameRef>()).isEmpty())
    }

    fun testParameterNameIsSpellchecked() {
        myFixture.configureByText("a.lua", "local function f(recieveArg) end")
        val param = SyntaxTraverser.psiTraverser(myFixture.file)
            .filter(LuaNameRef::class.java).firstOrNull { it.parent is LuaNameList } ?: error("no param nameRef")
        val tokens = capture(LuaIdentifierTokenizer(), param)
        assertEquals(1, tokens.size)
        assertEquals("recieveArg", tokens[0].text)
    }

    fun testReferenceNameIsNotSpellchecked() {
        // A bare usage (not a declaration) must not be spellchecked, even if misspelled.
        myFixture.configureByText("a.lua", "return recieveBuffer")
        assertTrue(capture(LuaIdentifierTokenizer(), firstPsi<LuaNameRef>()).isEmpty())
    }

    // --- String tokenizer: inner text + source-mapped offset — §3.2 / TC-2 ---

    fun testStringTokenizerEmitsInnerTextAtOffset() {
        myFixture.configureByText("a.lua", "local s = \"helo\"")
        val tokens = capture(LuaStringTokenizer(), firstOfType(LuaSyntax.StringLiteralTokens))
        assertEquals(1, tokens.size)
        assertEquals("helo", tokens[0].text)
        assertEquals("inner text starts one char past the opening quote", 1, tokens[0].offset)
    }
}
