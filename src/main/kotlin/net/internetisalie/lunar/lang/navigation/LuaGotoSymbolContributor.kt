package net.internetisalie.lunar.lang.navigation

import com.intellij.icons.AllIcons
import com.intellij.navigation.ChooseByNameContributorEx
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.stubs.StubIndexKey
import com.intellij.util.Processor
import com.intellij.util.indexing.FindSymbolParameters
import com.intellij.util.indexing.IdFilter
import net.internetisalie.lunar.lang.indexing.LuaClassNameIndex
import net.internetisalie.lunar.lang.indexing.LuaGlobalDeclarationIndex
import net.internetisalie.lunar.lang.psi.LuaFuncDecl
import net.internetisalie.lunar.lang.psi.LuaLocalVarDecl

/**
 * "Go to Symbol" for Lua: surfaces named global functions (NAV-03-02) plus `@class`/`@alias`
 * declarations so any top-level named symbol is reachable. Stub-index only; no file parsing.
 */
class LuaGotoSymbolContributor : ChooseByNameContributorEx {

    override fun processNames(processor: Processor<in String>, scope: GlobalSearchScope, filter: IdFilter?) {
        val index = StubIndex.getInstance()
        index.processAllKeys(LuaGlobalDeclarationIndex.KEY, processor, scope, filter)
        index.processAllKeys(LuaClassNameIndex.KEY, processor, scope, filter)
        LuaAliasNavigation.processNames(processor, scope, filter)
    }

    override fun processElementsWithName(
        name: String,
        processor: Processor<in NavigationItem>,
        parameters: FindSymbolParameters,
    ) {
        val project = parameters.project
        val scope = parameters.searchScope
        if (!emitFunctions(name, project, scope, processor)) return
        if (!emitVars(LuaClassNameIndex.KEY, name, project, scope, processor)) return
        LuaAliasNavigation.processElements(name, project, scope, AllIcons.Nodes.Type, processor)
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

    private fun emitVars(
        key: StubIndexKey<String, LuaLocalVarDecl>,
        name: String,
        project: Project,
        scope: GlobalSearchScope,
        processor: Processor<in NavigationItem>,
    ): Boolean {
        val elements = StubIndex.getElements(key, name, project, scope, LuaLocalVarDecl::class.java)
        for (element in elements) {
            if (!processor.process(LuaNavigationItem(element, name, AllIcons.Nodes.Class))) return false
        }
        return true
    }
}
