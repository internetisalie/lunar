package net.internetisalie.lunar.lang.parser

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import net.internetisalie.lunar.BaseDocumentTest
import net.internetisalie.lunar.lang.LuaFileType
import net.internetisalie.lunar.lang.LuaLanguageLevel
import net.internetisalie.lunar.lang.psi.LuaGlobalFuncDecl
import net.internetisalie.lunar.lang.psi.LuaGlobalModeDecl
import net.internetisalie.lunar.lang.psi.LuaGlobalVarDecl
import net.internetisalie.lunar.lang.psi.LuaNameRef
import net.internetisalie.lunar.lang.syntax.LuaHighlight
import net.internetisalie.lunar.settings.LuaProjectSettings
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

/**
 * BUG-361: `global` is a *soft* keyword. It must lex/parse as an ordinary identifier or field in
 * Lua 5.1–5.4 (and even in 5.5 outside a declaration), and only lead a global declaration when the
 * surrounding syntax is actually a declaration. Keyword highlighting must follow the same rule.
 */
class LuaGlobalSoftKeywordTest : BaseDocumentTest() {

    private fun setLanguageLevel(level: LuaLanguageLevel) {
        LuaProjectSettings.getInstance(myFixture.project).state.languageLevel = level
    }

    private fun assertNoParseErrors(code: String) {
        myFixture.configureByText(LuaFileType, code)
        val errors = PsiTreeUtil.findChildrenOfType(myFixture.file, PsiErrorElement::class.java)
        Assertions.assertTrue(
            errors.isEmpty(),
            "Expected no parse errors in:\n$code\nErrors: " + errors.joinToString { it.errorDescription },
        )
    }

    // ==================== `global` as a plain identifier (pre-5.5) ====================

    @Test
    fun globalAsLocalNameParsesCleanlyPre55() {
        for (level in PRE_55) {
            setLanguageLevel(level)
            assertNoParseErrors("local global = 1\nprint(global)")
        }
    }

    @Test
    fun globalAsFieldKeyAndMemberAccessParsesCleanlyPre55() {
        for (level in PRE_55) {
            setLanguageLevel(level)
            assertNoParseErrors("local t = { global = 2 }\nreturn t.global")
        }
    }

    @Test
    fun globalAsAssignmentTargetParsesAsAssignmentPre55() {
        setLanguageLevel(LuaLanguageLevel.LUA54)
        myFixture.configureByText(LuaFileType, "global.x = 1")
        Assertions.assertTrue(
            PsiTreeUtil.findChildrenOfType(myFixture.file, PsiErrorElement::class.java).isEmpty(),
            "'global.x = 1' should parse without errors",
        )
        Assertions.assertNull(
            PsiTreeUtil.findChildOfType(myFixture.file, LuaGlobalVarDecl::class.java),
            "'global.x = 1' must NOT be a global declaration",
        )
    }

    @Test
    fun globalAsCallTargetParsesAsExpressionPre55() {
        setLanguageLevel(LuaLanguageLevel.LUA54)
        myFixture.configureByText(LuaFileType, "global()")
        Assertions.assertTrue(
            PsiTreeUtil.findChildrenOfType(myFixture.file, PsiErrorElement::class.java).isEmpty(),
            "'global()' should parse without errors",
        )
        Assertions.assertNull(
            PsiTreeUtil.findChildOfType(myFixture.file, LuaGlobalVarDecl::class.java),
            "'global()' must NOT be a global declaration",
        )
    }

    @Test
    fun globalAsParameterParsesCleanlyPre55() {
        setLanguageLevel(LuaLanguageLevel.LUA51)
        assertNoParseErrors("local function f(global)\n  return global\nend")
    }

    // ==================== `global` as a 5.5 declaration keyword ====================

    @Test
    fun globalVarDeclarationParsesUnder55() {
        setLanguageLevel(LuaLanguageLevel.LUA55)
        myFixture.configureByText(LuaFileType, "global x = 10")
        Assertions.assertTrue(
            PsiTreeUtil.findChildrenOfType(myFixture.file, PsiErrorElement::class.java).isEmpty(),
            "'global x = 10' should parse without errors under 5.5",
        )
        Assertions.assertNotNull(
            PsiTreeUtil.findChildOfType(myFixture.file, LuaGlobalVarDecl::class.java),
            "'global x = 10' should parse as a LuaGlobalVarDecl",
        )
    }

    @Test
    fun globalDeclarationsParseIndependentlyOfLevel() {
        // The parser accepts declarations at every level; only the inspection gates the level.
        for (level in listOf(LuaLanguageLevel.LUA51, LuaLanguageLevel.LUA54, LuaLanguageLevel.LUA55)) {
            setLanguageLevel(level)
            myFixture.configureByText(LuaFileType, "global function f() end")
            Assertions.assertNotNull(
                PsiTreeUtil.findChildOfType(myFixture.file, LuaGlobalFuncDecl::class.java),
                "'global function f() end' should parse as LuaGlobalFuncDecl at $level",
            )
            myFixture.configureByText(LuaFileType, "global *")
            Assertions.assertNotNull(
                PsiTreeUtil.findChildOfType(myFixture.file, LuaGlobalModeDecl::class.java),
                "'global *' should parse as LuaGlobalModeDecl at $level",
            )
        }
    }

    @Test
    fun globalRemappedTokenBecomesResolvableName() {
        // `local global = 1; print(global)` — the second `global` is a nameRef IDENTIFIER, not GLOBAL.
        setLanguageLevel(LuaLanguageLevel.LUA54)
        myFixture.configureByText(LuaFileType, "local global = 1\nprint(global)")
        val refs = PsiTreeUtil.findChildrenOfType(myFixture.file, LuaNameRef::class.java)
        Assertions.assertTrue(
            refs.any { it.text == "global" },
            "'global' identifier usage should be a LuaNameRef",
        )
    }

    // ==================== Keyword highlighting ====================

    private fun assertHighlighted(text: String, expectedKey: TextAttributesKey, present: Boolean) {
        val infos = myFixture.doHighlighting()
        val found = infos.any { it.forcedTextAttributesKey == expectedKey && it.text == text }
        if (present) {
            Assertions.assertTrue(found, "Expected '$text' highlighted as ${expectedKey.externalName}")
        } else {
            Assertions.assertFalse(found, "Did NOT expect '$text' highlighted as ${expectedKey.externalName}")
        }
    }

    @Test
    fun declarationKeywordIsHighlighted() {
        setLanguageLevel(LuaLanguageLevel.LUA55)
        myFixture.configureByText(LuaFileType, "global x = 10")
        assertHighlighted("global", LuaHighlight.KEYWORD, present = true)
    }

    @Test
    fun identifierUsageIsNotKeywordHighlighted() {
        setLanguageLevel(LuaLanguageLevel.LUA54)
        myFixture.configureByText(LuaFileType, "local global = 1\nreturn global")
        assertHighlighted("global", LuaHighlight.KEYWORD, present = false)
    }

    @Test
    fun memberAccessGlobalIsNotKeywordHighlighted() {
        setLanguageLevel(LuaLanguageLevel.LUA54)
        myFixture.configureByText(LuaFileType, "global.x = 1")
        assertHighlighted("global", LuaHighlight.KEYWORD, present = false)
    }

    companion object {
        private val PRE_55 = listOf(
            LuaLanguageLevel.LUA51,
            LuaLanguageLevel.LUA52,
            LuaLanguageLevel.LUA53,
            LuaLanguageLevel.LUA54,
        )
    }
}
