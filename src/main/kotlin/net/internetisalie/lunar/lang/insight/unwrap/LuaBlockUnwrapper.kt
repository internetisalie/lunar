package net.internetisalie.lunar.lang.insight.unwrap

import com.intellij.psi.PsiElement
import net.internetisalie.lunar.lang.editor.LuaBlockStructure
import net.internetisalie.lunar.lang.psi.LuaIfStatement

/**
 * "Unwrap block": remove one construct's keyword + `end`, hoisting its (single) body to the parent scope.
 * Implements EDITOR-06-01. For `IF`, applies only to a plain `if…then…end` (no `elseif`/`else`) — a
 * multi-branch `if` is collapsed by [LuaElseBranchRemover] instead, matching Java's `JavaIfUnwrapper`.
 * Design §2.3.
 */
class LuaBlockUnwrapper(private val construct: LuaConstruct) : LuaUnwrapper(construct.unwrapDescription) {

    override fun isApplicableTo(e: PsiElement): Boolean {
        if (!construct.matches(e)) return false
        return construct != LuaConstruct.IF || !LuaBlockStructure.hasElseOrElseIf(e as LuaIfStatement)
    }

    override fun doUnwrap(element: PsiElement, context: Context) {
        val body = LuaBlockStructure.primaryBody(element) ?: return
        context.extractBlockBody(body, element)
        context.delete(element)
    }
}
