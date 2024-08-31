package net.internetisalie.lunar.lang.structure

import com.intellij.icons.AllIcons
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.navigation.ItemPresentation
import com.intellij.psi.PsiElement
import javax.swing.Icon

class LuaLocalVariableStructureViewTreeElement(private var myIdentifier : PsiElement) : LuaStructureViewTreeElement(myIdentifier) {
    override fun getPresentation(): ItemPresentation {
        return object : ItemPresentation {
            override fun getPresentableText(): String? {
                return myIdentifier.text
            }

            override fun getIcon(unused: Boolean): Icon {
                return AllIcons.Nodes.Variable
            }
        }
    }


    override fun getChildren(): Array<TreeElement> {
        return emptyArray()
    }

    override fun getValue(): Any {
        return myIdentifier
    }
}