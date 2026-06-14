package net.internetisalie.lunar.lang.hierarchy

import com.intellij.ide.hierarchy.HierarchyNodeDescriptor
import com.intellij.ide.util.treeView.NodeDescriptor
import com.intellij.openapi.roots.ui.util.CompositeAppearance
import com.intellij.openapi.util.Comparing
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement

/**
 * A node in a Lua type-hierarchy tree, wrapping a `@class` declaration ([LuaLocalVarDecl]) or a
 * method declaration. Displays [displayName] and navigates to the wrapped element.
 *
 * Mirrors `com.jetbrains.python.hierarchy.PyHierarchyNodeDescriptor` (the platform reference), but
 * derives its text from a caller-supplied name rather than [com.intellij.navigation.ItemPresentation],
 * because a Lua class node's label is the LuaCATS class name, not the local variable name.
 */
class LuaHierarchyNodeDescriptor(
    parentDescriptor: NodeDescriptor<*>?,
    element: PsiElement,
    private val displayName: String,
    isBase: Boolean,
) : HierarchyNodeDescriptor(element.project, parentDescriptor, element, isBase), Navigatable {

    override fun update(): Boolean {
        var changes = super.update()
        val oldText = myHighlightedText
        myHighlightedText = CompositeAppearance()

        if (psiElement == null) return invalidElement()

        myHighlightedText.ending.addText(displayName)
        myName = myHighlightedText.text

        if (!Comparing.equal(myHighlightedText, oldText)) changes = true
        return changes
    }

    override fun navigate(requestFocus: Boolean) {
        navigationTarget()?.takeIf { it.canNavigate() }?.navigate(requestFocus)
    }

    override fun canNavigate(): Boolean = navigationTarget()?.canNavigate() == true

    override fun canNavigateToSource(): Boolean = canNavigate()

    private fun navigationTarget(): Navigatable? = psiElement as? Navigatable
}
