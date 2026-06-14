package net.internetisalie.lunar.lang.navigation

import com.intellij.navigation.NavigationItem
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.Processor
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.IdFilter
import net.internetisalie.lunar.lang.indexing.LuaAliasNameIndex
import net.internetisalie.lunar.lang.psi.LuaFile
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsAliasTag
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsArgName
import javax.swing.Icon

/**
 * Shared "go to LuaCATS `@alias`" plumbing for the Go-to-Class and Go-to-Symbol contributors
 * (NAV-03-04). Backed by [LuaAliasNameIndex] so bare `@alias` comments — those with no associated
 * `LuaLocalVarDecl` — are reachable, then re-resolves the `LuaCatsAliasTag` PSI for navigation.
 */
object LuaAliasNavigation {

    fun processNames(processor: Processor<in String>, scope: GlobalSearchScope, filter: IdFilter?) {
        FileBasedIndex.getInstance().processAllKeys(LuaAliasNameIndex.KEY, processor, scope, filter)
    }

    /** Emits a navigation item per `@alias <name>` declaration; returns false if the processor stops. */
    fun processElements(
        name: String,
        project: Project,
        scope: GlobalSearchScope,
        icon: Icon,
        processor: Processor<in NavigationItem>,
    ): Boolean {
        val index = FileBasedIndex.getInstance()
        val psiManager = PsiManager.getInstance(project)
        for (virtualFile in index.getContainingFiles(LuaAliasNameIndex.KEY, name, scope)) {
            val luaFile = psiManager.findFile(virtualFile) as? LuaFile ?: continue
            for (tag in PsiTreeUtil.findChildrenOfType(luaFile, LuaCatsAliasTag::class.java)) {
                val argName = PsiTreeUtil.getChildOfType(tag, LuaCatsArgName::class.java) ?: continue
                if (argName.text.trim() != name) continue
                if (!processor.process(LuaNavigationItem(argName, name, icon))) return false
            }
        }
        return true
    }
}
