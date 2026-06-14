package net.internetisalie.lunar.lang.navigation

import com.intellij.icons.AllIcons
import com.intellij.lang.Language
import com.intellij.navigation.ChooseByNameContributorEx
import com.intellij.navigation.GotoClassContributor
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.stubs.StubIndexKey
import com.intellij.util.Processor
import com.intellij.util.indexing.FindSymbolParameters
import com.intellij.util.indexing.IdFilter
import net.internetisalie.lunar.lang.LuaLanguage
import net.internetisalie.lunar.lang.indexing.LuaAliasIndex
import net.internetisalie.lunar.lang.indexing.LuaClassNameIndex
import net.internetisalie.lunar.lang.psi.LuaLocalVarDecl
import javax.swing.Icon

/**
 * "Go to Class" for Lua: surfaces LuaCATS `@class` (NAV-03-01) and `@alias` (NAV-03-04)
 * declarations. Streams names straight from the stub indexes; no file parsing.
 */
class LuaGotoClassContributor : GotoClassContributor, ChooseByNameContributorEx {

    override fun processNames(processor: Processor<in String>, scope: GlobalSearchScope, filter: IdFilter?) {
        val index = StubIndex.getInstance()
        index.processAllKeys(LuaClassNameIndex.KEY, processor, scope, filter)
        index.processAllKeys(LuaAliasIndex.KEY, processor, scope, filter)
    }

    override fun processElementsWithName(
        name: String,
        processor: Processor<in NavigationItem>,
        parameters: FindSymbolParameters,
    ) {
        val project = parameters.project
        val scope = parameters.searchScope
        val classIcon = AllIcons.Nodes.Class
        val aliasIcon = AllIcons.Nodes.Type
        val emitClasses = emit(LuaClassNameIndex.KEY, name, project, scope, classIcon, processor)
        if (!emitClasses) return
        emit(LuaAliasIndex.KEY, name, project, scope, aliasIcon, processor)
    }

    private fun emit(
        key: StubIndexKey<String, LuaLocalVarDecl>,
        name: String,
        project: Project,
        scope: GlobalSearchScope,
        icon: Icon,
        processor: Processor<in NavigationItem>,
    ): Boolean {
        val elements = StubIndex.getElements(key, name, project, scope, LuaLocalVarDecl::class.java)
        for (element in elements) {
            if (!processor.process(LuaNavigationItem(element, name, icon))) return false
        }
        return true
    }

    override fun getQualifiedName(item: NavigationItem): String? = item.name

    override fun getQualifiedNameSeparator(): String = "."

    override fun getElementLanguage(): Language = LuaLanguage
}
