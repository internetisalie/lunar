package net.internetisalie.lunar.lang.spellcheck

import com.intellij.openapi.project.Project
import net.internetisalie.lunar.analysis.inspections.LuaStandardGlobals
import net.internetisalie.lunar.lang.LuaKeywords
import net.internetisalie.lunar.settings.LuaProjectSettings

/**
 * Identifier suppression list for Lua spellchecking (EDITOR-02-05).
 *
 * A name is suppressed (not spellchecked) if it is:
 * - A Lua reserved keyword (structural; cannot be a declaration anyway)
 * - A standard global for the project's configured language level
 * - A known LuaCATS primitive type token
 *
 * Design §2.4.
 */
object LuaSpellcheckSuppressions {

    /** LuaCATS / Lua type system primitive names (lowercase), per §2.4. */
    private val CATS_TYPES: Set<String> = setOf(
        "nil", "boolean", "number", "string", "userdata", "function",
        "thread", "table", "integer", "any", "self", "lightuserdata", "void", "unknown",
    )

    fun isSuppressed(name: String, project: Project): Boolean {
        if (LuaKeywords.isReserved(name)) return true
        if (name in CATS_TYPES) return true
        val level = LuaProjectSettings.getInstance(project).state.languageLevel
        return LuaStandardGlobals.contains(name, level)
    }
}
