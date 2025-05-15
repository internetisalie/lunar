package net.internetisalie.lunar.lang.structure

import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.navigation.ItemPresentation
import com.intellij.psi.util.PsiTreeUtil
import net.internetisalie.lunar.lang.LuaIcons
import net.internetisalie.lunar.lang.psi.LuaFile
import net.internetisalie.lunar.lang.psi.LuaFuncDecl
import net.internetisalie.lunar.lang.psi.LuaLocalFuncDecl
import javax.swing.Icon

class LuaFileStructureViewTreeElement(private var myFile: LuaFile) : LuaStructureViewTreeElement(myFile) {
    override fun getPresentation(): ItemPresentation {
        return object : ItemPresentation {
            override fun getPresentableText(): String? {
                return myFile.getName()
            }

            override fun getIcon(unused: Boolean): Icon {
                return LuaIcons.FILE
            }
        }
    }

    override fun getChildren(): Array<TreeElement> {
        return TreeElementUtils
            .getRootChildren(myFile)
            .toTypedArray()
    }

    override fun getValue(): Any {
        return myFile
    }
}