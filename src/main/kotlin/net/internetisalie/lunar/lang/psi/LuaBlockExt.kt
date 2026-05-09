package net.internetisalie.lunar.lang.psi

import com.intellij.psi.ResolveState
import com.intellij.psi.PsiElement
import com.intellij.psi.scope.PsiScopeProcessor

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
    place: PsiElement
): Boolean {
    // Iterate over all statements in the block in source order
    for (statement in statementList) {
        // Stop when reaching lastParent to enforce early-binding (prevent forward references)
        if (lastParent != null && statement.textOffset >= lastParent.textOffset) {
            break
        }

        when (statement) {
            is LuaLocalVarDecl -> {
                // Process each declared name in the local variable declaration
                for (attName in statement.attNameList) {
                    if (!processor.execute(attName, state)) {
                        return false  // Processor found match, stop walk
                    }
                }
            }

            is LuaLocalFuncDecl -> {
                // Process the local function declaration itself
                if (!processor.execute(statement, state)) {
                    return false  // Processor found match, stop walk
                }
            }
        }
    }

    return true  // Continue walk to parent scope
}
