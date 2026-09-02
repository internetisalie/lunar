package net.internetisalie.lunar.lang.psi

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil

/**
 * The single answer to "which function bounds this label's scope, which block declares it, and
 * which labels share its function". One rule, multiple callers — [LuaLabelReference] resolution,
 * [LuaNameDeclElementImpl.getUseScope] (REFACT-04-11) and label conflict detection (REFACT-04-07)
 * all read visibility through this object rather than re-enumerating the boundary rule themselves.
 */
object LuaLabelScopes {
    /**
     * True for the four PSI types at which label visibility stops. The single source of the
     * function-boundary rule; [walkLabelScopes] and [functionScopeOf] must both use it and
     * nothing else may re-enumerate it.
     */
    fun isFunctionBoundary(element: PsiElement): Boolean =
        element is LuaFuncDef ||
            element is LuaFuncDecl ||
            element is LuaLocalFuncDecl ||
            element is LuaGlobalFuncDecl

    /**
     * Visits every [LuaBlock] between [start] and its function boundary, innermost first,
     * stopping early when [visit] returns false. Moved verbatim from
     * `LuaLabelReference.walkLabelScopes` (`LuaLabelReference.kt:69-89`) with the boundary test
     * delegated to [isFunctionBoundary].
     */
    fun walkLabelScopes(
        start: PsiElement,
        visit: (LuaBlock) -> Boolean,
    ) {
        var current: PsiElement? = start
        while (current != null && current !is PsiFile) {
            if (current is LuaBlock) {
                if (!visit(current)) {
                    return
                }
            }
            if (isFunctionBoundary(current)) {
                return
            }
            current = current.parent
        }
    }

    /**
     * The element that bounds [element]'s label scope: the nearest ancestor for which
     * [isFunctionBoundary] holds, or the containing [LuaFile] when there is none. Null only when
     * [element] has no containing file.
     */
    fun functionScopeOf(element: PsiElement): PsiElement? {
        var current: PsiElement? = element
        while (current != null) {
            if (isFunctionBoundary(current) || current is PsiFile) {
                return current
            }
            current = current.parent
        }
        return null
    }

    /** The [LuaBlock] that declares [label], i.e. the block whose `statementList` holds its `LuaLabel`. */
    fun blockOf(label: LuaLabelName): LuaBlock? {
        val declaringLabel = label.parent as? LuaLabel ?: return null
        return declaringLabel.parent as? LuaBlock
    }

    /**
     * Every [LuaLabelName] declared directly in [scope]'s own function — descendants inside a
     * nested function are excluded. See REFACT-04 design §3.3 step 3.
     */
    fun labelsInFunctionScope(scope: PsiElement): List<LuaLabelName> =
        PsiTreeUtil.findChildrenOfType(scope, LuaLabelName::class.java).filter {
            functionScopeOf(it) === scope
        }
}
