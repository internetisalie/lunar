package net.internetisalie.lunar.lang.psi.stubs.impl

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.PsiTreeUtil
import net.internetisalie.lunar.lang.psi.LuaBlock
import net.internetisalie.lunar.lang.psi.LuaElementTypes
import net.internetisalie.lunar.lang.psi.LuaFuncDecl
import net.internetisalie.lunar.lang.psi.LuaFuncName
import net.internetisalie.lunar.lang.psi.LuaGenericForStatement
import net.internetisalie.lunar.lang.psi.LuaLocalFuncDecl
import net.internetisalie.lunar.lang.psi.LuaLocalVarDecl
import net.internetisalie.lunar.lang.psi.LuaNameRef
import net.internetisalie.lunar.lang.psi.LuaNumericForStatement
import net.internetisalie.lunar.lang.psi.LuaParList

/**
 * BUG-509: whether the first segment of a `function` statement's name (`f` in `function f()`, `R`
 * in `function R.a.b()` / `function R:m()`) is bound by a file-local declaration in scope.
 *
 * Pure PSI, no index and no `LuaScopeProcessor`: it runs while stubs are built, where a partially
 * parsed declaration must not be asked for its `@NotNull` block.
 */
internal object LuaFuncNameBinding {
    fun rootIsLocal(declaration: LuaFuncDecl): Boolean {
        val funcName = declaration.node.findChildByType(LuaElementTypes.FUNC_NAME)?.psi as? LuaFuncName ?: return false
        val root = directNameRef(funcName) ?: return false
        return generateSequence<PsiElement>(declaration) { it.parent }
            .takeWhile { it !is PsiFile }
            .zipWithNext()
            .any { (child, scope) -> bindsName(scope, child, root) }
    }

    private fun bindsName(
        scope: PsiElement,
        child: PsiElement,
        name: String,
    ): Boolean =
        when (scope) {
            is LuaBlock -> localsOf(scope)[name]?.let { it <= child.textRange.startOffset } == true
            is LuaGenericForStatement -> scope.nameList.nameRefList.any { it.identifier.text == name }
            is LuaNumericForStatement -> scope.identifier.text == name
            is LuaLocalFuncDecl -> directNameRef(scope) == name || parameterNames(scope).contains(name)
            else -> parameterNames(scope).contains(name)
        }

    private fun parameterNames(scope: PsiElement): List<String> =
        PsiTreeUtil
            .getChildOfType(scope, LuaParList::class.java)
            ?.nameList
            ?.nameRefList
            ?.map { it.identifier.text }
            .orEmpty()

    /**
     * Local name → end offset of the first statement in [block] declaring it. Cached because every
     * `function` declaration of one stub build asks the same block; it holds only strings and
     * offsets, no PSI.
     */
    private fun localsOf(block: LuaBlock): Map<String, Int> =
        CachedValuesManager.getCachedValue(block) {
            CachedValueProvider.Result.create(collectLocals(block), PsiModificationTracker.MODIFICATION_COUNT)
        }

    private fun collectLocals(block: LuaBlock): Map<String, Int> {
        val firstEnd = mutableMapOf<String, Int>()
        for (statement in block.statementList) {
            val end = statement.textRange.endOffset
            declaredNames(statement).forEach { firstEnd.putIfAbsent(it, end) }
        }
        return firstEnd
    }

    private fun declaredNames(statement: PsiElement): List<String> =
        when (statement) {
            is LuaLocalVarDecl -> statement.attNameList.mapNotNull { directNameRef(it) }
            is LuaLocalFuncDecl -> listOfNotNull(directNameRef(statement))
            else -> emptyList()
        }

    /** The declared name read off the node: a partially parsed declaration may lack it. */
    private fun directNameRef(declaration: PsiElement): String? =
        PsiTreeUtil.getChildOfType(declaration, LuaNameRef::class.java)?.identifier?.text
}
