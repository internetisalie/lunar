package net.internetisalie.lunar.lang.hierarchy

import com.intellij.ide.hierarchy.HierarchyNodeDescriptor
import com.intellij.ide.hierarchy.HierarchyTreeStructure
import com.intellij.ide.hierarchy.TypeHierarchyBrowserBase
import com.intellij.ide.util.treeView.NodeDescriptor
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.psi.PsiElement
import net.internetisalie.lunar.lang.psi.LuaLocalVarDecl
import javax.swing.JPanel
import javax.swing.JTree

/**
 * Type Hierarchy tool-window browser for Lua `@class` declarations (NAV-06). Provides the supertype
 * (NAV-06-02) and subtype (NAV-06-01) trees; the combined "type hierarchy" view reuses the subtype
 * structure rooted at the target, matching the Python reference.
 *
 * Mirrors `com.jetbrains.python.hierarchy.PyTypeHierarchyBrowser`; implements the full abstract set of
 * [TypeHierarchyBrowserBase]/`HierarchyBrowserBaseEx`: [getElementFromDescriptor], [createTrees],
 * [createLegendPanel], [isApplicableElement], [createHierarchyTreeStructure], [getComparator],
 * [isInterface], [canBeDeleted], [getQualifiedName].
 */
class LuaTypeHierarchyBrowser(decl: LuaLocalVarDecl) :
    TypeHierarchyBrowserBase(decl.project, decl) {

    override fun getElementFromDescriptor(descriptor: HierarchyNodeDescriptor): PsiElement? =
        descriptor.psiElement

    override fun createTrees(trees: MutableMap<in String, in JTree>) {
        createTreeAndSetupCommonActions(trees, IdeActions.GROUP_TYPE_HIERARCHY_POPUP)
    }

    override fun createLegendPanel(): JPanel? = null

    override fun isApplicableElement(element: PsiElement): Boolean =
        element is LuaLocalVarDecl && LuaHierarchyUtil.className(element) != null

    override fun createHierarchyTreeStructure(typeName: String, psiElement: PsiElement): HierarchyTreeStructure? {
        val decl = psiElement as? LuaLocalVarDecl ?: return null
        val name = LuaHierarchyUtil.className(decl) ?: return null
        return when (typeName) {
            getSupertypesHierarchyType() -> LuaSuperTypesHierarchyTreeStructure(decl, name)
            getSubtypesHierarchyType() -> LuaSubTypesHierarchyTreeStructure(decl, name)
            getTypeHierarchyType() -> LuaSubTypesHierarchyTreeStructure(decl, name)
            else -> null
        }
    }

    override fun getComparator(): Comparator<NodeDescriptor<*>?> = LuaHierarchyUtil.comparator(myProject)

    override fun isInterface(psiElement: PsiElement): Boolean = false

    override fun canBeDeleted(psiElement: PsiElement?): Boolean = psiElement is LuaLocalVarDecl

    override fun getQualifiedName(psiElement: PsiElement?): String =
        (psiElement as? LuaLocalVarDecl)?.let { LuaHierarchyUtil.className(it) } ?: ""
}
