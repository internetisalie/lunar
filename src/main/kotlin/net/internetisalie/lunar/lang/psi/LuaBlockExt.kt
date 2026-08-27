package net.internetisalie.lunar.lang.psi

import com.intellij.psi.PsiElement
import com.intellij.psi.ResolveState
import com.intellij.psi.scope.PsiScopeProcessor
import net.internetisalie.lunar.lang.psi.LuaAssignmentStatement
import net.internetisalie.lunar.lang.psi.LuaBlock
import net.internetisalie.lunar.lang.psi.LuaFuncDecl
import net.internetisalie.lunar.lang.psi.LuaGenericForStatement
import net.internetisalie.lunar.lang.psi.LuaGlobalFuncDecl
import net.internetisalie.lunar.lang.psi.LuaGlobalVarDecl
import net.internetisalie.lunar.lang.psi.LuaLabel
import net.internetisalie.lunar.lang.psi.LuaLocalFuncDecl
import net.internetisalie.lunar.lang.psi.LuaLocalVarDecl
import net.internetisalie.lunar.lang.psi.LuaNameList
import net.internetisalie.lunar.lang.psi.LuaNumericForStatement
import net.internetisalie.lunar.lang.psi.LuaParList

/**
 * The prefix of this list that Lua's early-binding rule makes visible at [lastParent] — every
 * element declared strictly before the statement the scope walk ascended from.
 *
 * A view, never a copy: the set of visible statements is exactly the one the previous `break` at
 * the head of each `processDeclarations` loop produced, so early binding (`local x = x` reads the
 * OUTER `x`) is preserved unchanged. Only the order callers offer them in differs.
 */
private fun <T : PsiElement> List<T>.visibleBefore(lastParent: PsiElement?): List<T> {
    val boundary = lastParent?.textOffset ?: return this
    val firstHidden = indexOfFirst { it.textOffset >= boundary }
    return if (firstHidden < 0) this else subList(0, firstHidden)
}

/**
 * Extension function to implement processDeclarations for LuaBlock.
 *
 * Processes all locally-declared symbols visible at a given scope level.
 * This method supports lazy, incremental symbol resolution without full-file traversal.
 */
fun LuaBlock.processDeclarations(
    processor: PsiScopeProcessor,
    state: ResolveState,
    lastParent: PsiElement?,
    place: PsiElement,
): Boolean {
    val visible = statementList.visibleBefore(lastParent)

    // Nearest declaration first. LuaScopeProcessor stops on its first match, so a declaration must
    // be offered before every declaration it shadows, or the earliest one in the block wins and a
    // usage binds to the wrong name (BUG-472).
    for (statement in visible.asReversed()) {
        if (!statement.offerDeclaredNames(processor, state)) {
            return false
        }
    }

    // Assignment targets are a strictly lower tier, in source order as they have always been.
    // `x = 1` declares a global only while no `local x` binds the name (LuaDeclarationSite
    // .isGlobalAssignmentTarget states the same rule), so a write must never out-rank the
    // declaration it writes to — which reversing a single mixed loop would let it do.
    for (statement in visible) {
        if (!statement.offerAssignmentTargets(processor, state)) {
            return false
        }
    }

    return true // Continue walk to parent scope
}

/** Offers this statement itself when it declares a name; `false` once the processor matched. */
private fun LuaStatement.offerDeclaredNames(
    processor: PsiScopeProcessor,
    state: ResolveState,
): Boolean =
    when (this) {
        is LuaLocalVarDecl, is LuaLocalFuncDecl, is LuaGlobalVarDecl, is LuaGlobalFuncDecl ->
            processor.execute(this, state)

        else -> true
    }

/** Offers each undotted target of an assignment; `false` once the processor has matched. */
private fun LuaStatement.offerAssignmentTargets(
    processor: PsiScopeProcessor,
    state: ResolveState,
): Boolean {
    if (this !is LuaAssignmentStatement) return true
    return varList.varList.all { target ->
        target.nameRef == null || processor.execute(target, state)
    }
}

fun LuaBlock.processLabelDeclarations(
    processor: PsiScopeProcessor,
    state: ResolveState,
): Boolean {
    for (statement in statementList) {
        if (statement is LuaLabel && !processor.execute(statement, state)) {
            return false // processor matched → stop walk
        }
    }
    return true
}
