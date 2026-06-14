package net.internetisalie.lunar.lang.hierarchy

import com.intellij.ide.hierarchy.HierarchyBrowser
import com.intellij.ide.hierarchy.HierarchyProvider
import com.intellij.ide.hierarchy.TypeHierarchyBrowserBase
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import net.internetisalie.lunar.lang.psi.LuaLocalVarDecl

/**
 * Opens the Type Hierarchy tool window for a Lua `@class` (NAV-06). [getTarget] locates the
 * `local Name = {}` declaration carrying the `---@class` annotation at the caret; the class itself is
 * stub-indexed on [LuaLocalVarDecl] via [net.internetisalie.lunar.lang.indexing.LuaClassNameIndex].
 *
 * Mirrors `com.jetbrains.python.hierarchy.PyTypeHierachyProvider`.
 */
class LuaTypeHierarchyProvider : HierarchyProvider {

    override fun getTarget(dataContext: DataContext): PsiElement? {
        val atCaret = elementAtCaret(dataContext) ?: return null
        val decl = atCaret as? LuaLocalVarDecl
            ?: PsiTreeUtil.getParentOfType(atCaret, LuaLocalVarDecl::class.java)
            ?: return null
        return decl.takeIf { LuaHierarchyUtil.className(it) != null }
    }

    override fun createHierarchyBrowser(target: PsiElement): HierarchyBrowser =
        LuaTypeHierarchyBrowser(target as LuaLocalVarDecl)

    override fun browserActivated(hierarchyBrowser: HierarchyBrowser) {
        (hierarchyBrowser as LuaTypeHierarchyBrowser)
            .changeView(TypeHierarchyBrowserBase.getTypeHierarchyType())
    }

    private fun elementAtCaret(dataContext: DataContext): PsiElement? {
        CommonDataKeys.PSI_ELEMENT.getData(dataContext)?.let { return it }
        val editor = CommonDataKeys.EDITOR.getData(dataContext) ?: return null
        val file = CommonDataKeys.PSI_FILE.getData(dataContext) ?: return null
        return file.findElementAt(editor.caretModel.offset)
    }
}
