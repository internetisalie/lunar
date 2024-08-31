package net.internetisalie.lunar.lang.structure

import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.util.PsiNavigationSupport
import com.intellij.psi.PsiElement

abstract class LuaStructureViewTreeElement(private var myElement : PsiElement) : StructureViewTreeElement {
    override fun navigate(requestFocus: Boolean) {
        assert(canNavigate()) { this }
        PsiNavigationSupport.getInstance().getDescriptor(myElement)!!.navigate(requestFocus)
    }

    override fun canNavigate(): Boolean {
        return PsiNavigationSupport.getInstance().canNavigate(myElement)
    }

    override fun canNavigateToSource(): Boolean {
        return canNavigate()
    }
}