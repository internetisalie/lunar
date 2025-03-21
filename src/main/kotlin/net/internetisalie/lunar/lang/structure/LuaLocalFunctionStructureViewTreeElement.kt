package net.internetisalie.lunar.lang.structure

import com.intellij.icons.AllIcons
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.navigation.ItemPresentation
import net.internetisalie.lunar.lang.psi.LuaLocalFuncDecl
import javax.swing.Icon

class LuaLocalFunctionStructureViewTreeElement(private var myLocalFuncDecl : LuaLocalFuncDecl) : LuaStructureViewTreeElement(myLocalFuncDecl) {
    override fun getPresentation(): ItemPresentation {
        return object : ItemPresentation {
            override fun getPresentableText(): String {
                return myLocalFuncDecl.localFuncName.identifier.text
            }

            override fun getIcon(open: Boolean): Icon {
                return AllIcons.Nodes.Function
            }
        }
    }

    override fun getChildren(): Array<TreeElement> {
        return TreeElementUtils.getFuncBodyChildren(myLocalFuncDecl.funcBody)
    }

    override fun getValue(): Any {
        return myLocalFuncDecl
    }
}