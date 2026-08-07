package net.internetisalie.lunar.lang.structure

import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.navigation.ItemPresentation
import net.internetisalie.lunar.lang.LuaIcons
import net.internetisalie.lunar.lang.psi.LuaFile
import javax.swing.Icon

class LuaFileStructureViewTreeElement(
    private var myFile: LuaFile,
) : LuaStructureViewTreeElement(myFile) {
    override fun getPresentation(): ItemPresentation =
        object : ItemPresentation {
            override fun getPresentableText(): String? = myFile.getName()

            override fun getIcon(unused: Boolean): Icon = LuaIcons.FILE
        }

    override fun getChildren(): Array<TreeElement> =
        TreeElementUtils
            .getRootChildren(myFile)
            .toTypedArray()

    override fun getValue(): Any = myFile
}
