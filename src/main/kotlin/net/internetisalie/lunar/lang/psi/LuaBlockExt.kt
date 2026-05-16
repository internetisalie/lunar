package net.internetisalie.lunar.lang.psi

import com.intellij.psi.ResolveState
import com.intellij.psi.PsiElement
import com.intellij.psi.scope.PsiScopeProcessor
import net.internetisalie.lunar.lang.psi.LuaAssignmentStatement
import net.internetisalie.lunar.lang.psi.LuaBlock
import net.internetisalie.lunar.lang.psi.LuaFuncDecl
import net.internetisalie.lunar.lang.psi.LuaGenericForStatement
import net.internetisalie.lunar.lang.psi.LuaLocalFuncDecl
import net.internetisalie.lunar.lang.psi.LuaLocalVarDecl
import net.internetisalie.lunar.lang.psi.LuaNameList
import net.internetisalie.lunar.lang.psi.LuaNumericForStatement
import net.internetisalie.lunar.lang.psi.LuaParList

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
                // Process the local variable declaration statement
                if (!processor.execute(statement, state)) {
                    return false  // Processor found match, stop walk
                }
            }

            is LuaLocalFuncDecl -> {
                // Process the local function declaration itself
                if (!processor.execute(statement, state)) {
                    return false  // Processor found match, stop walk
                }
            }

            is LuaAssignmentStatement -> {
                // Process global variable assignments (at file level)
                // Collect variable names from assignment
                statement.varList.varList.forEach { varElement ->
                    val nameRef = varElement.nameRef
                    if (nameRef != null) {
                        if (!processor.execute(varElement, state)) {
                            return false  // Processor found match, stop walk
                        }
                    }
                }
            }
        }
    }

    return true  // Continue walk to parent scope
}
