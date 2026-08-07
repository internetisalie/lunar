package net.internetisalie.lunar.lang.structure

import com.intellij.icons.AllIcons
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.navigation.ItemPresentation
import com.intellij.psi.PsiElement
import javax.swing.Icon

class LuaLocalVariableStructureViewTreeElement(
    private var myIdentifier: PsiElement,
) : LuaStructureViewTreeElement(myIdentifier) {
    override fun getPresentation(): ItemPresentation =
        object : ItemPresentation {
            override fun getPresentableText(): String? = myIdentifier.text

            override fun getIcon(unused: Boolean): Icon = AllIcons.Nodes.Variable
        }

    override fun getChildren(): Array<TreeElement> = emptyArray()

    override fun getValue(): Any = myIdentifier
}
