package net.internetisalie.lunar.luacats.lang.doc

import net.internetisalie.lunar.lang.psi.types.LuaPrimitiveType
import net.internetisalie.lunar.lang.psi.types.LuaTypeManager
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsComment
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsParamTag

/**
 * Whether a `@param`'s declared type is really a type, or the first word of an English description.
 *
 * BUG-406: LDoc writes `@param <name> <description>` with **no type slot**, LuaCATS writes
 * `@param <name> <type> <description>`, and `luacats.bnf:143` cannot tell them apart because every
 * English word is a valid bare `NAME`. So `--- @param array Lua table of values` parses with
 * `argType = "Lua"`, and the documentation surfaces render `array: Lua`.
 *
 * The grammar is the wrong place to fix this: no parse-time heuristic separates `value` from a
 * `@class Value`, and the parser must not depend on resolution. Resolution is exactly the knowledge
 * that discriminates, so the check lives here and the display surfaces consult it — which also makes
 * them consistent with the type engine, where `LuaTypeGraphBridge.injectParamAnnotations` already
 * ignores a `@param` type that resolves to nothing.
 *
 * A per-file doc-dialect signal was measured and rejected: only 11 of the 26 Penlight files using a
 * bare `@param` also carry `@tparam`/`@treturn`, so a marker-based signal misclassifies most of them.
 */
object LuaCatsDeclaredType {

    /**
     * Characters that only appear in type syntax — unions, arrays, generics, optionals, function
     * types, table literals and string literals. Any of them means the text was parsed as a
     * structured type expression, which prose cannot produce.
     */
    private val STRUCTURAL = charArrayOf('|', '[', '<', '?', '(', '{', '"', '\'')

    /**
     * True when [tag]'s declared type names something resolvable, is structurally a type, or has no
     * prose behind it.
     *
     * **Two signals, both required to demote**: the word resolves to nothing, *and* the tag carries
     * a description after it. Resolution alone is too aggressive — `---@param a Player` for a class
     * declared in a file the index has not seen is indistinguishable from prose, and demoting it
     * would silently drop a type the author did write (it also breaks `TC-02c`, which fixed simple
     * identifier types being hyperlinked). Requiring trailing prose as well narrows the rule to what
     * it is actually for: *an unresolvable name followed by a sentence is the first word of that
     * sentence.*
     *
     * Worked through the reported cases: `@param array Lua table of values to search` demotes (`Lua`
     * resolves to nothing, prose follows); `@param e a value` demotes; `@param name string the …`
     * keeps (resolves); `@param b Builder the builder` keeps when `Builder` is declared;
     * `@param a Player` keeps regardless, having no description to be the first word of.
     */
    fun isType(tag: LuaCatsParamTag, comment: LuaCatsComment): Boolean {
        val text = tag.argType.text.trim()
        if (text.isEmpty()) return false
        if (STRUCTURAL.any { it in text }) return true
        if (LuaPrimitiveType.PRIMITIVES.containsKey(text)) return true
        if (text in genericNames(comment)) return true
        if (tag.description?.text.isNullOrBlank()) return true
        return LuaTypeManager.getInstance(tag.project).resolveType(text, tag) != null
    }

    /**
     * `self` and the comment's own `@generic` parameters resolve through no index, so they are
     * collected here rather than left to fail. Mirrors `LuaTypeGraphBridge`'s generic handling.
     */
    private fun genericNames(comment: LuaCatsComment): Set<String> =
        comment.genericTagList
            .flatMap { it.genericTypeParams?.genericTypeParamList ?: emptyList() }
            .map { it.argName.text }
            .toSet() + "self"
}
