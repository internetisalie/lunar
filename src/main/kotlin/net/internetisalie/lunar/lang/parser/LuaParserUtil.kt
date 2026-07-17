package net.internetisalie.lunar.lang.parser

import com.intellij.lang.PsiBuilder
import com.intellij.lang.parser.GeneratedParserUtilBase
import com.intellij.psi.tree.TokenSet
import net.internetisalie.lunar.lang.psi.LuaElementTypes

/**
 * External parser helpers referenced from `lua.bnf` via `<<...>>` meta-rule syntax.
 *
 * Extends [GeneratedParserUtilBase] so the generated `LuaParser` keeps resolving the platform
 * parsing primitives (`consumeToken`, `enter_section_`, …) through this class — Grammar-Kit swaps
 * its `import static …GeneratedParserUtilBase.*` for `import static …LuaParserUtil.*` once a
 * `parserUtilClass` is declared.
 *
 * Hosts the `global` soft-keyword rule: `global` is lexed as a plain [LuaElementTypes.IDENTIFIER]
 * so it stays a valid identifier/field name in Lua 5.1–5.4 (BUG-361). Only when it *leads* a Lua
 * 5.5 global declaration is the current token remapped to [LuaElementTypes.GLOBAL], letting the
 * existing `globalVarDecl` / `globalFuncDecl` / `globalModeDecl` rules produce the same PSI as
 * before.
 */
object LuaParserUtil : GeneratedParserUtilBase() {

    private const val GLOBAL_TEXT = "global"

    /**
     * Tokens that may follow the leading `global` of a Lua 5.5 declaration:
     * `global <name>` (var), `global function` (func), `global *` (mode), `global <attrib> *`.
     * Any other follower (`.`, `(`, `=`, `:`, `[`, EOF, …) means `global` is a plain identifier.
     */
    private val DECLARATION_FOLLOWERS = TokenSet.create(
        LuaElementTypes.IDENTIFIER,
        LuaElementTypes.FUNCTION,
        LuaElementTypes.MULT,
        LuaElementTypes.LT,
    )

    /**
     * Matches an `IDENTIFIER` whose text is exactly `global` that leads a Lua 5.5 declaration,
     * remaps it to [LuaElementTypes.GLOBAL] and **consumes** it (adding the keyword leaf). Returns
     * `false` without advancing for any other token, letting the alternative (assignment/expression
     * statement) parse `global` as an ordinary name.
     */
    @JvmStatic
    fun globalKeyword(builder: PsiBuilder, level: Int): Boolean {
        val tokenType = builder.tokenType
        // An earlier declaration alternative may have already remapped this position: a
        // remapCurrentToken is NOT undone on marker rollback, so accept the GLOBAL token directly.
        if (tokenType == LuaElementTypes.GLOBAL) {
            builder.advanceLexer()
            return true
        }
        if (tokenType != LuaElementTypes.IDENTIFIER || builder.tokenText != GLOBAL_TEXT) return false
        // Remap only when `global` genuinely leads a declaration; otherwise it is an ordinary name
        // (`global.x = 1`, `global()`, `global = 1`). The remap is sticky across rolled-back
        // alternatives, so gate it on a one-token lookahead to keep the fall-through path clean.
        if (builder.lookAhead(1) !in DECLARATION_FOLLOWERS) return false
        builder.remapCurrentToken(LuaElementTypes.GLOBAL)
        builder.advanceLexer()
        return true
    }
}
