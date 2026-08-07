package net.internetisalie.lunar.lang.structure

import com.intellij.icons.AllIcons
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.navigation.ItemPresentation
import net.internetisalie.lunar.lang.psi.LuaFinalStatement
import javax.swing.Icon

class LuaReturnStructureViewTreeElement(
    private var myReturn: LuaFinalStatement,
) : LuaStructureViewTreeElement(myReturn) {
    override fun getPresentation(): ItemPresentation =
        object : ItemPresentation {
            override fun getPresentableText(): String? = "return"

            override fun getIcon(unused: Boolean): Icon = AllIcons.Debugger.EvaluationResult
        }

    override fun getChildren(): Array<TreeElement> = emptyArray()

    override fun getValue(): Any = myReturn
}
