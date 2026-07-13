package net.internetisalie.lunar.lang.editor

import com.intellij.codeInsight.editorActions.moveLeftRight.MoveElementLeftRightHandler
import com.intellij.psi.PsiElement
import net.internetisalie.lunar.lang.psi.LuaExprList
import net.internetisalie.lunar.lang.psi.LuaFieldList
import net.internetisalie.lunar.lang.psi.LuaGlobalVarDecl
import net.internetisalie.lunar.lang.psi.LuaLocalVarDecl
import net.internetisalie.lunar.lang.psi.LuaNameList

/**
 * `Code | Move Element Left/Right` (Ctrl+Alt+Shift+←/→) for Lua list containers: call/return argument
 * lists and assignment RHS (`LuaExprList`), table-constructor fields (`LuaFieldList`), generic-`for` /
 * parameter name lists (`LuaNameList`), and `local`/`global` declaration name sides
 * (`LuaLocalVarDecl`/`LuaGlobalVarDecl` `attNameList`). The platform swaps two adjacent returned elements,
 * preserving the separators between them. Stateless. EDITOR-07-03. Design §2.2 / §3.2.
 */
class LuaMoveLeftRightHandler : MoveElementLeftRightHandler() {

    override fun getMovableSubElements(element: PsiElement): Array<PsiElement> = when (element) {
        is LuaExprList -> movable(element.exprList)
        is LuaFieldList -> movable(element.fieldList)
        is LuaNameList -> movable(element.nameRefList)
        is LuaLocalVarDecl -> movable(element.attNameList)
        is LuaGlobalVarDecl -> movable(element.attNameList)
        else -> PsiElement.EMPTY_ARRAY
    }

    private fun movable(items: List<PsiElement>): Array<PsiElement> = items.toTypedArray()
}
