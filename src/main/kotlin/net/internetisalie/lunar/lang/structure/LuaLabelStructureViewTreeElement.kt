package net.internetisalie.lunar.lang.structure

import com.intellij.icons.AllIcons
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.navigation.ItemPresentation
import com.intellij.psi.util.PsiTreeUtil
import net.internetisalie.lunar.lang.psi.LuaLabel
import javax.swing.Icon

class LuaLabelStructureViewTreeElement(private var myLabel : LuaLabel) : LuaStructureViewTreeElement(myLabel) {
    override fun getPresentation(): ItemPresentation {
        return object : ItemPresentation {
            override fun getPresentableText(): String? {
                val labelName = myLabel.labelName
                return labelName.identifier?.text ?: labelName.firstChild?.text
            }

            override fun getIcon(unused: Boolean): Icon {
                return AllIcons.Nodes.Bookmark
            }
        }
    }


    override fun getChildren(): Array<TreeElement> {
        return emptyArray()
    }

    override fun getValue(): Any {
        val labelName = myLabel.labelName
        return labelName.identifier ?: labelName.firstChild ?: labelName
    }
}