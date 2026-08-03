package net.internetisalie.lunar.lang.parser

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import net.internetisalie.lunar.BaseDocumentTest
import net.internetisalie.lunar.lang.LuaFileType
import net.internetisalie.lunar.lang.psi.LuaElementTypes
import net.internetisalie.lunar.lang.lexer.LuaLexer
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

/**
 * BUG-392 regression. `lua.flex`'s XLONGSTRING_BEGIN state returns NL_BEFORE_LONGSTRING without
 * leaving the state, so a long string opening on a blank line produced one such token per newline.
 * [LuaLexer]'s merge consumed only the first, ending the STRING token at `[[\n` and leaking the
 * body as raw LONGSTRING / LONGSTRING_END tokens with no grammar rule — which surfaced as a
 * bogus parse error reported far away, at the enclosing function's parameter list.
 *
 * Reduced from `luarocks/src/luarocks/cmd.lua:439`, which reference Lua accepts.
 */
class LuaLongStringBlankLineTest : BaseDocumentTest() {

    private val luarocksCmdReproducer =
        """
        local function get_parser(description, cmd_modules)
           help_max_width(80):
           add_help_command():
           add_complete_command({
              help_max_width = 100,
              summary = "Output a shell completion script.",
              description = [[

        Enabling completions for Bash:

           Add the following line to your ~/.bashrc:
              source <(]] .. basename .. [[ completion bash)
           Add the following line to your ~/.config/fish/config.fish:
              ]] .. basename .. [[ completion fish | source
           or save the completion script to the local completion directory:
              ]] .. basename .. [[ completion fish > ~/.config/fish/completions/]] .. basename .. [[.fish
        ]], }):
           command_target("command"):
           require_command(false)

        end
        """.trimIndent()

    private fun assertParsesClean(code: String) {
        myFixture.configureByText(LuaFileType, code)
        val errors = runReadAction {
            PsiTreeUtil.findChildrenOfType(myFixture.file, PsiErrorElement::class.java)
                .map { "@${it.textOffset}: ${it.errorDescription}" }
        }
        Assertions.assertTrue(errors.isEmpty(), "Expected a clean parse, got: $errors\n\n$code")
    }

    @Test
    fun testLuarocksCmdReproducerParsesClean() {
        assertParsesClean(luarocksCmdReproducer)
    }

    /** The whole long string must be one token — the defect split it into three. */
    @Test
    fun testBlankLineLongStringIsASingleToken() {
        val source = "[[\n\n\nbody\n]]"
        val lexer = LuaLexer()
        lexer.start(source)
        Assertions.assertEquals(LuaElementTypes.STRING, lexer.tokenType)
        Assertions.assertEquals(source, lexer.tokenText)
        lexer.advance()
        Assertions.assertNull(lexer.tokenType, "Long string should be a single token")
    }
}
