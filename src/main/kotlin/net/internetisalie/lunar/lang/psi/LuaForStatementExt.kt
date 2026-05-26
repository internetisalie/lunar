package net.internetisalie.lunar.lang.psi

import com.intellij.psi.PsiElement
import com.intellij.psi.ResolveState
import com.intellij.psi.scope.PsiScopeProcessor

fun LuaNumericForStatement.processDeclarations(
    processor: PsiScopeProcessor,
    state: ResolveState,
    lastParent: PsiElement?,
    place: PsiElement
): Boolean {
    // Loop variable is only visible in the loop body (block)
    val block = block
    if (lastParent == block) {
        if (!processor.execute(this, state)) {
            return false
        }
    }

    // Then process loop body (recursive call to process inner variables)
    return block?.processDeclarations(processor, state, lastParent, place) ?: true
}

fun LuaGenericForStatement.processDeclarations(
    processor: PsiScopeProcessor,
    state: ResolveState,
    lastParent: PsiElement?,
    place: PsiElement
): Boolean {
    // Loop variables are only visible in the loop body (block)
    val block = block
    if (lastParent == block) {
        if (!processor.execute(this, state)) {
            return false
        }
    }

    // Then process loop body (recursive call to process inner variables)
    return block?.processDeclarations(processor, state, lastParent, place) ?: true
}

