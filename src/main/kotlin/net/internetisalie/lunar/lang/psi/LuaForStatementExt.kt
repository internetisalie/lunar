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
    // Process loop counter variable first
    if (!processor.execute(this, state)) {
        return false
    }
    
    // Then process loop body
    val block = block
    return block?.processDeclarations(processor, state, lastParent, place) ?: true
}

fun LuaGenericForStatement.processDeclarations(
    processor: PsiScopeProcessor,
    state: ResolveState,
    lastParent: PsiElement?,
    place: PsiElement
): Boolean {
    // Process loop variable list first
    val nameList = nameList
    if (!processor.execute(nameList, state)) {
        return false
    }
    
    // Then process loop body
    val block = block
    return block?.processDeclarations(processor, state, lastParent, place) ?: true
}
