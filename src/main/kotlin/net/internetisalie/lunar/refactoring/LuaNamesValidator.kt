package net.internetisalie.lunar.refactoring

import com.intellij.lang.refactoring.NamesValidator
import com.intellij.openapi.project.Project
import net.internetisalie.lunar.lang.LuaKeywords

/**
 * Validates new names for the Lua Rename refactoring: a string is a keyword iff it is a Lua
 * reserved word, and a valid identifier iff it matches the ASCII Lua identifier grammar and is
 * not a reserved word. The platform rename UI uses these booleans to reject invalid renames.
 */
class LuaNamesValidator : NamesValidator {
    override fun isKeyword(name: String, project: Project?): Boolean =
        LuaKeywords.isReserved(name)

    override fun isIdentifier(name: String, project: Project?): Boolean =
        IDENTIFIER_PATTERN.matches(name) && !LuaKeywords.isReserved(name)

    private companion object {
        private val IDENTIFIER_PATTERN = Regex("^[A-Za-z_][A-Za-z0-9_]*$")
    }
}
