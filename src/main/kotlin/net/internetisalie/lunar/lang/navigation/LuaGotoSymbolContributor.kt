package net.internetisalie.lunar.lang.navigation

import com.intellij.icons.AllIcons
import com.intellij.navigation.ChooseByNameContributorEx
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.util.Processor
import com.intellij.util.indexing.FindSymbolParameters
import com.intellij.util.indexing.IdFilter
import net.internetisalie.lunar.lang.indexing.LuaGlobalDeclarationIndex
import net.internetisalie.lunar.lang.psi.LuaFuncDecl

/**
 * "Go to Symbol" for Lua: surfaces named global functions (NAV-03-02) plus LuaCATS `@class`/`@alias`
 * types, so any top-level named symbol is reachable. Functions come from the stub index; types
 * (including bare tag comments) from the file-based [LuaCatsTypeNavigation].
 */
class LuaGotoSymbolContributor : ChooseByNameContributorEx {

    override fun processNames(processor: Processor<in String>, scope: GlobalSearchScope, filter: IdFilter?) {
        StubIndex.getInstance().processAllKeys(LuaGlobalDeclarationIndex.KEY, processor, scope, filter)
        LuaCatsTypeNavigation.processNames(processor, scope, filter)
    }

    override fun processElementsWithName(
        name: String,
        processor: Processor<in NavigationItem>,
        parameters: FindSymbolParameters,
    ) {
        val project = parameters.project
        val scope = parameters.searchScope
        if (!emitFunctions(name, project, scope, processor)) return
        LuaCatsTypeNavigation.processElements(name, project, scope, processor)
    }

    private fun emitFunctions(
        name: String,
        project: Project,
        scope: GlobalSearchScope,
        processor: Processor<in NavigationItem>,
    ): Boolean {
        val elements = StubIndex.getElements(
            LuaGlobalDeclarationIndex.KEY, name, project, scope, LuaFuncDecl::class.java,
        )
        for (element in elements) {
            if (!processor.process(LuaNavigationItem(element, name, AllIcons.Nodes.Function))) return false
        }
        return true
    }
}
