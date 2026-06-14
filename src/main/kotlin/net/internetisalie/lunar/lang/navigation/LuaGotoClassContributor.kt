package net.internetisalie.lunar.lang.navigation

import com.intellij.lang.Language
import com.intellij.navigation.ChooseByNameContributorEx
import com.intellij.navigation.GotoClassContributor
import com.intellij.navigation.NavigationItem
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.Processor
import com.intellij.util.indexing.FindSymbolParameters
import com.intellij.util.indexing.IdFilter
import net.internetisalie.lunar.lang.LuaLanguage

/**
 * "Go to Class" for Lua: surfaces LuaCATS `@class` (NAV-03-01) and `@alias` (NAV-03-04) declarations,
 * including bare tag comments with no associated `local`. Backed by the file-based
 * [net.internetisalie.lunar.lang.indexing.LuaCatsTypeNameIndex] via [LuaCatsTypeNavigation]; the
 * stub `LuaClassNameIndex`/`LuaAliasIndex` remain for type resolution and documentation.
 */
class LuaGotoClassContributor : GotoClassContributor, ChooseByNameContributorEx {

    override fun processNames(processor: Processor<in String>, scope: GlobalSearchScope, filter: IdFilter?) {
        LuaCatsTypeNavigation.processNames(processor, scope, filter)
    }

    override fun processElementsWithName(
        name: String,
        processor: Processor<in NavigationItem>,
        parameters: FindSymbolParameters,
    ) {
        LuaCatsTypeNavigation.processElements(name, parameters.project, parameters.searchScope, processor)
    }

    override fun getQualifiedName(item: NavigationItem): String? = item.name

    override fun getQualifiedNameSeparator(): String = "."

    override fun getElementLanguage(): Language = LuaLanguage
}
