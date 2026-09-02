package net.internetisalie.lunar.refactoring

import com.intellij.psi.ElementDescriptionLocation
import com.intellij.psi.ElementDescriptionProvider
import com.intellij.psi.PsiElement
import com.intellij.usageView.UsageViewShortNameLocation
import com.intellij.usageView.UsageViewTypeLocation
import net.internetisalie.lunar.lang.psi.LuaDeclarationSite

/**
 * Supplies the human-readable *type* and *short name* the platform prints for a Lua declaration
 * (BUG-475).
 *
 * Without this, `ElementDescriptionUtil.getElementDescription` has no provider for the
 * [net.internetisalie.lunar.lang.psi.LuaNameRef] composite and falls back to de-camel-casing the
 * implementation class, so the in-place rename's undo entry read
 * *"Undo Renaming Lua Name Ref Impl con…"*. The dialog route was unaffected because it renames
 * through an element the platform can already describe — which is why the defect looked
 * path-specific rather than missing-registration.
 *
 * The wording is not invented here: [net.internetisalie.lunar.lang.psi.LuaDeclarationKind.usageViewType]
 * already carries it, and is the same vocabulary the rename conflict messages use. Anything that is
 * not a declaration site returns null so the platform keeps its own default.
 */
class LuaElementDescriptionProvider : ElementDescriptionProvider {
    override fun getElementDescription(
        element: PsiElement,
        location: ElementDescriptionLocation,
    ): String? {
        val leaf = LuaDeclarationSite.identifierLeafOf(element) ?: return null
        return when (location) {
            is UsageViewTypeLocation -> LuaDeclarationSite.kindOf(leaf)?.usageViewType
            is UsageViewShortNameLocation -> leaf.text
            else -> null
        }
    }
}
