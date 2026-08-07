package net.internetisalie.lunar.lang.structure

import com.intellij.icons.AllIcons
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.navigation.ItemPresentation
import net.internetisalie.lunar.lang.psi.LuaFuncDecl
import javax.swing.Icon

class LuaFunctionStructureViewTreeElement(
    private var myFuncDecl: LuaFuncDecl,
) : LuaStructureViewTreeElement(myFuncDecl) {
    override fun getPresentation(): ItemPresentation =
        object : ItemPresentation {
            override fun getPresentableText(): String = myFuncDecl.funcName.text

            override fun getIcon(open: Boolean): Icon = AllIcons.Nodes.Function
        }

    override fun getChildren(): Array<TreeElement> =
        TreeElementUtils
            .getFuncBodyChildren(myFuncDecl.parList, myFuncDecl.block)
            .toTypedArray()

    override fun getValue(): Any = myFuncDecl
}
