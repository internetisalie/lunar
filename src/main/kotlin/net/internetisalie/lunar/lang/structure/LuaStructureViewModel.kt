package net.internetisalie.lunar.lang.structure

import com.intellij.ide.structureView.StructureViewModel
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.TextEditorBasedStructureViewModel
import com.intellij.ide.util.treeView.smartTree.Sorter
import com.intellij.psi.PsiFile
import net.internetisalie.lunar.lang.psi.LuaFile
import net.internetisalie.lunar.lang.psi.LuaFinalStatement
import net.internetisalie.lunar.lang.psi.LuaFuncDecl
import net.internetisalie.lunar.lang.psi.LuaLabel
import net.internetisalie.lunar.lang.psi.LuaLocalFuncDecl

class LuaStructureViewModel(private var file: LuaFile) : TextEditorBasedStructureViewModel(file), StructureViewModel.ElementInfoProvider {
    val SUITABLE_CLASSES = arrayOf<Class<*>>(
        LuaFile::class.java,
        LuaLabel::class.java,
        LuaFuncDecl::class.java,
        LuaLocalFuncDecl::class.java,
        LuaFinalStatement::class.java,
    )

    override fun getPsiFile(): PsiFile {
        return file
    }

    override fun getRoot(): StructureViewTreeElement {
        return LuaFileStructureViewTreeElement(file)
    }

    override fun getSorters(): Array<Sorter> {
        return arrayOf(Sorter.ALPHA_SORTER)
    }

    override fun isAlwaysShowsPlus(element: StructureViewTreeElement?): Boolean {
        return false
    }

    override fun isAlwaysLeaf(element: StructureViewTreeElement?): Boolean {
        return element is LuaLocalVariableStructureViewTreeElement ||
                element is LuaLabelStructureViewTreeElement ||
                element is LuaReturnStructureViewTreeElement
    }

    override fun getSuitableClasses(): Array<Class<*>> {
        return SUITABLE_CLASSES
    }
}