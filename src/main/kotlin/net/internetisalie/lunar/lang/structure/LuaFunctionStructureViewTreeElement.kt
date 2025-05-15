package net.internetisalie.lunar.lang.structure

import com.intellij.icons.AllIcons
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.navigation.ItemPresentation
import net.internetisalie.lunar.lang.psi.LuaFuncDecl
import javax.swing.Icon

class LuaFunctionStructureViewTreeElement(private var myFuncDecl : LuaFuncDecl) : LuaStructureViewTreeElement(myFuncDecl) {
    override fun getPresentation(): ItemPresentation {
        return object : ItemPresentation {
            override fun getPresentableText(): String {
                return myFuncDecl.funcName.text
            }

            override fun getIcon(open: Boolean): Icon {
                return AllIcons.Nodes.Function
            }
        }
    }

    override fun getChildren(): Array<TreeElement> {
        return TreeElementUtils
            .getFuncBodyChildren(myFuncDecl.parList, myFuncDecl.block)
            .toTypedArray()
    }

    override fun getValue(): Any {
        return myFuncDecl
    }
}