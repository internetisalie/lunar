package net.internetisalie.lunar.lang.spellcheck

import com.intellij.openapi.util.TextRange
import com.intellij.spellchecker.inspections.IdentifierSplitter
import com.intellij.spellchecker.tokenizer.TokenConsumer
import com.intellij.spellchecker.tokenizer.Tokenizer
import net.internetisalie.lunar.lang.psi.LuaAttName
import net.internetisalie.lunar.lang.psi.LuaLocalFuncDecl
import net.internetisalie.lunar.lang.psi.LuaNameList
import net.internetisalie.lunar.lang.psi.LuaNameRef

/**
 * Spellchecks Lua declaration names (EDITOR-02-03, EDITOR-02-04, EDITOR-02-05).
 *
 * Lua's PSI has **no dedicated declaration element** for locals/functions/params — names are all
 * [LuaNameRef] (`IDENTIFIER`), the same node used for references. A name is treated as a
 * *declaration* (and therefore spellchecked) only when its parent is a declaration-only container:
 * - [LuaAttName]       — local variable name (`local x`)
 * - [LuaLocalFuncDecl] — local function name (`local function f`)
 * - [LuaNameList]      — function parameters and generic-`for` variables (both decl-only positions)
 *
 * Deliberately not checked: reference names, the base of a global `function tbl.foo()` (where the
 * leading name is a table *reference*, not a new binding), and numeric-`for` variables (a bare
 * `IDENTIFIER`, not a [LuaNameRef]).
 *
 * Uses [IdentifierSplitter] for camelCase/snake_case splitting, suppresses stdlib globals / keywords
 * / LuaCATS type names via [LuaSpellcheckSuppressions], and passes useRename=true so the platform
 * offers the Rename quick-fix. Design §2.3, §3.3 (corrected: routes [LuaNameRef], not the
 * label-only `LuaNameDeclElement`).
 */
class LuaIdentifierTokenizer : Tokenizer<LuaNameRef>() {

    override fun tokenize(element: LuaNameRef, consumer: TokenConsumer) {
        if (!isDeclarationName(element)) return
        val name = element.text
        if (name.isEmpty() || LuaSpellcheckSuppressions.isSuppressed(name, element.project)) return
        consumer.consumeToken(element, name, true, 0, TextRange.allOf(name), IdentifierSplitter.getInstance())
    }

    private fun isDeclarationName(nameRef: LuaNameRef): Boolean =
        when (nameRef.parent) {
            is LuaAttName, is LuaLocalFuncDecl, is LuaNameList -> true
            else -> false
        }
}
