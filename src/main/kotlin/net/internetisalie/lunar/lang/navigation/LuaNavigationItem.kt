package net.internetisalie.lunar.lang.navigation

import com.intellij.navigation.ItemPresentation
import com.intellij.navigation.NavigationItem
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import net.internetisalie.lunar.lang.psi.LuaFuncDecl
import net.internetisalie.lunar.lang.psi.LuaLocalVarDecl
import javax.swing.Icon

/**
 * Go-to-Class / Go-to-Symbol popup item wrapping an indexed Lua declaration (NAV-03).
 *
 * The indexed PSI ([LuaLocalVarDecl] / [LuaFuncDecl]) implement neither [NavigationItem] nor
 * [ItemPresentation], and — crucially — their own `name` is NOT the searchable name: a `@class`
 * is keyed on its LuaCATS class name while the local variable may be called something else. So
 * this adapter carries the index [name] explicitly and uses it for both [getName] and the
 * presentable text, while navigating to the declaration's name identifier so Enter lands on the
 * definition.
 */
class LuaNavigationItem(
    private val declaration: PsiElement,
    private val name: String,
    private val icon: Icon,
) : NavigationItem, ItemPresentation {

    private val target: Navigatable?
        get() = navigationTarget(declaration) as? Navigatable

    override fun getName(): String = name

    override fun getPresentation(): ItemPresentation = this

    override fun getPresentableText(): String = name

    override fun getLocationString(): String? =
        declaration.containingFile?.name

    override fun getIcon(unused: Boolean): Icon = icon

    override fun navigate(requestFocus: Boolean) {
        target?.navigate(requestFocus)
    }

    override fun canNavigate(): Boolean = target?.canNavigate() ?: false

    override fun canNavigateToSource(): Boolean = target?.canNavigateToSource() ?: false

    private fun navigationTarget(element: PsiElement): PsiElement =
        when (element) {
            is LuaFuncDecl -> element.funcName
            is LuaLocalVarDecl -> element.attNameList.firstOrNull()?.nameRef ?: element
            else -> element
        }
}
