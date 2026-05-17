package net.internetisalie.lunar.lang.completion

import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.ResolveState
import com.intellij.psi.scope.PsiScopeProcessor
import net.internetisalie.lunar.lang.psi.*

class LuaCrossFileCompletionScopeProcessor(
    private val prefix: String
) : PsiScopeProcessor {
    val results = mutableListOf<Pair<String, PsiElement>>()

    override fun execute(element: PsiElement, state: ResolveState): Boolean {
        val name = when (element) {
            is com.intellij.psi.PsiNamedElement -> element.name
            else -> null
        } ?: return true

        if (name.startsWith(prefix)) {
            // Only include top-level (file-level) declarations that are accessible from other files
            // In Lua, only globals and the module's return value are visible to require() callers
            when (element) {
                is LuaFuncDecl -> {
                    // Top-level function declarations are accessible
                    if (isTopLevel(element)) {
                        results.add(name to element)
                    }
                }
                is LuaLocalVarDecl -> {
                    // Top-level local variables are NOT accessible from other files
                    // They are file-local. Only globals (via assignment) are accessible.
                    // Skip locals entirely.
                }
                is LuaLocalFuncDecl -> {
                    // Top-level local functions are NOT accessible from other files
                    // They are file-local. Skip them.
                }
            }
        }

        return true
    }

    private fun isTopLevel(element: PsiElement): Boolean {
        // Check if element is at file level (direct child of LuaFile or in top-level block)
        var parent = element.parent
        while (parent != null) {
            when (parent) {
                is LuaFile -> return true
                is LuaBlock -> {
                    // If we hit a block that's a direct child of LuaFile, it's top-level
                    if (parent.parent is LuaFile) return true
                }
            }
            parent = parent.parent
        }
        return false
    }

    override fun <T : Any?> getHint(hintKey: Key<T>): T? = null

    override fun handleEvent(event: PsiScopeProcessor.Event, associated: Any?) {}
}
