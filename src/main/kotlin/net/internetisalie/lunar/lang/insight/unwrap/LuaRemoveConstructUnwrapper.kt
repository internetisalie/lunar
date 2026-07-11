package net.internetisalie.lunar.lang.insight.unwrap

import com.intellij.psi.PsiElement

/**
 * "Remove enclosing block": delete a whole construct including its body (no hoist). Implements
 * EDITOR-06-03. Applies to any unwrappable construct — including a multi-branch `if` — but not the
 * expression-form `LuaFuncDef` (would leave a dangling `local f =`). `addElementToExtract` before
 * `delete` makes the preview highlight the whole construct (§3.4 note). Design §2.6.
 */
class LuaRemoveConstructUnwrapper : LuaUnwrapper("Remove enclosing block") {

    override fun isApplicableTo(e: PsiElement): Boolean = LuaConstruct.isConstruct(e)

    override fun doUnwrap(element: PsiElement, context: Context) {
        context.addElementToExtract(element)
        context.delete(element)
    }
}
