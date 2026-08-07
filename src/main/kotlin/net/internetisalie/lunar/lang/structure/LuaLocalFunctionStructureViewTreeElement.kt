package net.internetisalie.lunar.lang.structure

import com.intellij.icons.AllIcons
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.navigation.ItemPresentation
import net.internetisalie.lunar.lang.psi.LuaLocalFuncDecl
import javax.swing.Icon

class LuaLocalFunctionStructureViewTreeElement(
    private var myLocalFuncDecl: LuaLocalFuncDecl,
) : LuaStructureViewTreeElement(myLocalFuncDecl) {
    override fun getPresentation(): ItemPresentation =
        object : ItemPresentation {
            override fun getPresentableText(): String = myLocalFuncDecl.nameRef.identifier.text

            override fun getIcon(open: Boolean): Icon = AllIcons.Nodes.Function
        }

    override fun getChildren(): Array<TreeElement> =
        TreeElementUtils
            .getFuncBodyChildren(
                myLocalFuncDecl.parList,
                myLocalFuncDecl.block,
            ).toTypedArray()

    override fun getValue(): Any = myLocalFuncDecl
}
