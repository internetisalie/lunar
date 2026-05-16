package net.internetisalie.lunar.lang

import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.ResolveState
import com.intellij.psi.scope.PsiScopeProcessor
import net.internetisalie.lunar.lang.psi.LuaAssignmentStatement
import net.internetisalie.lunar.lang.psi.LuaFuncDecl
import net.internetisalie.lunar.lang.psi.LuaGenericForStatement
import net.internetisalie.lunar.lang.psi.LuaLocalFuncDecl
import net.internetisalie.lunar.lang.psi.LuaLocalVarDecl
import net.internetisalie.lunar.lang.psi.LuaNameList
import net.internetisalie.lunar.lang.psi.LuaNumericForStatement
import net.internetisalie.lunar.lang.psi.LuaParList
import net.internetisalie.lunar.lang.psi.LuaVar

/**
 * Lazy scope processor for resolving named symbols in Lua.
 *
 * Walks up the PSI tree from a reference location, checking each scope level
 * for a matching declaration. Stops on the first match.
 */
class LuaScopeProcessor(val name: String) : PsiScopeProcessor {
    var result: PsiElement? = null
        private set

    private var found = false

    override fun execute(element: PsiElement, state: ResolveState): Boolean {
        // Already found a match; stop the walk
        if (found) return false

        // Check if element is a named declaration matching our search
        when (element) {
            is LuaLocalVarDecl -> {
                // Extract identifier from attName
                element.attNameList.forEach { attName ->
                    if (attName.nameRef.identifier.text == name) {
                        result = attName.nameRef.identifier
                        found = true
                        return@execute false  // Stop walk
                    }
                }
            }

            is LuaLocalFuncDecl -> {
                if (element.nameRef.identifier.text == name) {
                    result = element.nameRef.identifier
                    found = true
                    return false
                }
            }

            is LuaFuncDecl -> {
                // Check function name (for recursion)
                if (element.funcName.nameRef.identifier.text == name) {
                    result = element.funcName.nameRef.identifier
                    found = true
                    return false
                }
                // Check implicit self
                if (name == "self" && element.funcName.funcNameMethod != null) {
                    result = element.funcName.funcNameMethod!!.nameRef.identifier
                    found = true
                    return false
                }
            }

            is LuaParList -> {
                // Check parameter names
                val nameList = element.nameList
                if (nameList != null) {
                    nameList.nameRefList.forEach { nameRef ->
                        if (nameRef.identifier.text == name) {
                            result = nameRef.identifier
                            found = true
                            return@execute false
                        }
                    }
                }
            }

            is LuaNumericForStatement -> {
                if (element.identifier.text == name) {
                    result = element.identifier
                    found = true
                    return false
                }
            }

            is LuaGenericForStatement -> {
                element.nameList.nameRefList.forEach { nameRef ->
                    if (nameRef.identifier.text == name) {
                        result = nameRef.identifier
                        found = true
                        return@execute false
                    }
                }
            }
        }

        return true  // Continue walk
    }

    override fun <T : Any?> getHint(hintKey: Key<T>): T? = null

    override fun handleEvent(event: PsiScopeProcessor.Event, associated: Any?) {}
}

/**
 * Completion variant of [LuaScopeProcessor].
 *
 * Collects all visible names in scope without stopping, suitable for code completion.
 */
class LuaCompletionScopeProcessor : PsiScopeProcessor {
    val results: MutableSet<String> = mutableSetOf()

    override fun execute(element: PsiElement, state: ResolveState): Boolean {
        // Collect all names, don't stop
        when (element) {
            is LuaLocalVarDecl -> {
                element.attNameList.forEach { attName ->
                    results.add(attName.nameRef.identifier.text)
                }
            }

            is LuaLocalFuncDecl -> {
                results.add(element.nameRef.identifier.text)
            }

            is LuaFuncDecl -> {
                results.add(element.funcName.nameRef.identifier.text)
                if (element.funcName.funcNameMethod != null) {
                    results.add("self")
                }
            }

            is LuaParList -> {
                element.nameList?.nameRefList?.forEach { nameRef ->
                    results.add(nameRef.identifier.text)
                }
            }

            is LuaNumericForStatement -> {
                results.add(element.identifier.text)
            }

            is LuaGenericForStatement -> {
                element.nameList.nameRefList.forEach { nameRef ->
                    results.add(nameRef.identifier.text)
                }
            }

            is LuaAssignmentStatement -> {
                // Collect variable names from assignment (for global variables at file level)
                element.varList.varList.forEach { varElement ->
                    val nameRef = varElement.nameRef
                    if (nameRef != null) {
                        results.add(nameRef.identifier.text)
                    }
                }
            }

            is LuaVar -> {
                // Process variable from assignment (for global variables)
                val nameRef = element.nameRef
                if (nameRef != null) {
                    results.add(nameRef.identifier.text)
                }
            }
        }

        return true  // Always continue
    }

    override fun <T : Any?> getHint(hintKey: Key<T>): T? = null

    override fun handleEvent(event: PsiScopeProcessor.Event, associated: Any?) {}
}
