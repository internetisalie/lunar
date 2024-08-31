package net.internetisalie.lunar.lang.structure

import com.intellij.icons.AllIcons
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.navigation.ItemPresentation
import com.intellij.psi.PsiElement
import net.internetisalie.lunar.lang.psi.LuaFinalStatement
import javax.swing.Icon

class LuaReturnStructureViewTreeElement(private var myReturn : LuaFinalStatement) : LuaStructureViewTreeElement(myReturn) {
    override fun getPresentation(): ItemPresentation {
        return object : ItemPresentation {
            override fun getPresentableText(): String? {
                return "return"
            }

            override fun getIcon(unused: Boolean): Icon {
                return AllIcons.Debugger.EvaluationResult
            }
        }
    }

    override fun getChildren(): Array<TreeElement> {
        return emptyArray()
    }

    override fun getValue(): Any {
        return myReturn
    }
}