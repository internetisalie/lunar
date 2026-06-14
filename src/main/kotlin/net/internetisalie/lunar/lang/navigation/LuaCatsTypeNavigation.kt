package net.internetisalie.lunar.lang.navigation

import com.intellij.icons.AllIcons
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.Processor
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.IdFilter
import net.internetisalie.lunar.lang.indexing.LuaCatsTypeNameIndex
import net.internetisalie.lunar.lang.psi.LuaFile
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsAliasTag
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsArgName
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsArgType
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsClassTag

/**
 * Shared "go to LuaCATS type" plumbing for the Go-to-Class and Go-to-Symbol contributors — both
 * `@class` (NAV-03-01) and `@alias` (NAV-03-04). Backed by [LuaCatsTypeNameIndex] so bare tag
 * comments (no associated `LuaLocalVarDecl`) are reachable, then re-resolves the tag PSI for
 * navigation. The navigation target is the *comment* identifier (the type's name token), and the
 * icon is derived from the resolved tag kind rather than stored in the index.
 */
object LuaCatsTypeNavigation {

    fun processNames(processor: Processor<in String>, scope: GlobalSearchScope, filter: IdFilter?) {
        FileBasedIndex.getInstance().processAllKeys(LuaCatsTypeNameIndex.KEY, processor, scope, filter)
    }

    /** Emits a navigation item per matching `@class` / `@alias` declaration; false if the processor stops. */
    fun processElements(
        name: String,
        project: Project,
        scope: GlobalSearchScope,
        processor: Processor<in NavigationItem>,
    ): Boolean {
        val index = FileBasedIndex.getInstance()
        val psiManager = PsiManager.getInstance(project)
        for (virtualFile in index.getContainingFiles(LuaCatsTypeNameIndex.KEY, name, scope)) {
            val luaFile = psiManager.findFile(virtualFile) as? LuaFile ?: continue
            for (tag in PsiTreeUtil.findChildrenOfType(luaFile, LuaCatsClassTag::class.java)) {
                val id = PsiTreeUtil.getChildOfType(tag, LuaCatsArgType::class.java) ?: continue
                if (id.text.trim() != name) continue
                if (!processor.process(LuaNavigationItem(id, name, AllIcons.Nodes.Class))) return false
            }
            for (tag in PsiTreeUtil.findChildrenOfType(luaFile, LuaCatsAliasTag::class.java)) {
                val id = PsiTreeUtil.getChildOfType(tag, LuaCatsArgName::class.java) ?: continue
                if (id.text.trim() != name) continue
                if (!processor.process(LuaNavigationItem(id, name, AllIcons.Nodes.Type))) return false
            }
        }
        return true
    }
}
