package net.internetisalie.lunar.analysis.redis

import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.ResolveState
import com.intellij.psi.scope.PsiScopeProcessor
import net.internetisalie.lunar.lang.psi.LuaGenericForStatement
import net.internetisalie.lunar.lang.psi.LuaLocalFuncDecl
import net.internetisalie.lunar.lang.psi.LuaLocalVarDecl
import net.internetisalie.lunar.lang.psi.LuaNumericForStatement
import net.internetisalie.lunar.lang.psi.LuaParList

/**
 * Side-effect-free scope processor that reports whether [targetName] binds to a **local**
 * declaration visible in scope (REDIS-06-01, design §2.1, §3.1).
 *
 * Fed by [net.internetisalie.lunar.lang.psi.LuaResolveUtil.scopeCrawlUp], which walks scope
 * containers bottom-up and feeds each container's `processDeclarations` here. This processor
 * stops the walk (returns `false`) only when it finds a local declaration whose name equals
 * [targetName]; it continues (returns `true`) for every other element — including all global
 * declaration kinds (`LuaGlobalVarDecl`, `LuaGlobalFuncDecl`, `LuaFuncDecl`, `LuaVar`,
 * `LuaAssignmentStatement`) — so a file-level global is never treated as a shadow (risk §1.2).
 *
 * The five local kinds matched (design §3.1): [LuaLocalVarDecl] (each `attNameList` entry),
 * [LuaLocalFuncDecl] (its `nameRef`), [LuaParList] (each `nameList?.nameRefList` entry),
 * [LuaNumericForStatement] (its `identifier`), [LuaGenericForStatement] (each
 * `nameList.nameRefList` entry).
 *
 * Threading: invoked inside the inspection visitor's read context; touches only in-tree PSI —
 * no VFS, stub index, or type-engine access (risk §1.1). Holds only [targetName] and a flag.
 */
class LocalBindingScopeProcessor(private val targetName: String) : PsiScopeProcessor {

    var foundLocal: Boolean = false
        private set

    override fun execute(element: PsiElement, state: ResolveState): Boolean {
        when (element) {
            is LuaLocalVarDecl ->
                if (element.attNameList.any { it.nameRef.identifier.text == targetName }) return match()

            is LuaLocalFuncDecl ->
                if (element.nameRef.identifier.text == targetName) return match()

            is LuaParList ->
                if (element.nameList?.nameRefList?.any { it.identifier.text == targetName } == true) return match()

            is LuaNumericForStatement ->
                if (element.identifier.text == targetName) return match()

            is LuaGenericForStatement ->
                if (element.nameList.nameRefList.any { it.identifier.text == targetName }) return match()
        }
        return true
    }

    private fun match(): Boolean {
        foundLocal = true
        return false
    }

    override fun <T> getHint(hintKey: Key<T>): T? = null

    override fun handleEvent(event: PsiScopeProcessor.Event, associated: Any?) {}
}
