package net.internetisalie.lunar.lang.doc

import com.intellij.codeInsight.documentation.DocumentationManagerProtocol
import com.intellij.platform.backend.documentation.DocumentationLinkHandler
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.LinkResolveResult
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import net.internetisalie.lunar.lang.indexing.LuaAliasIndex
import net.internetisalie.lunar.lang.indexing.LuaClassNameIndex
import net.internetisalie.lunar.lang.psi.LuaLocalVarDecl

class LuaDocumentationLinkHandler : DocumentationLinkHandler {
    override fun resolveLink(target: DocumentationTarget, url: String): LinkResolveResult? {
        if (target !is LuaCatsDocumentationTarget) return null
        if (!url.startsWith(DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL)) return null
        val typeName = url.removePrefix(DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL)
        
        val project = target.element.project
        val scope = GlobalSearchScope.projectScope(project)
        
        // Try class first, then alias
        val decl = StubIndex.getElements(LuaClassNameIndex.KEY, typeName, project, scope, LuaLocalVarDecl::class.java).firstOrNull()
            ?: StubIndex.getElements(LuaAliasIndex.KEY, typeName, project, scope, LuaLocalVarDecl::class.java).firstOrNull()
            ?: return null
        
        return LinkResolveResult.resolvedTarget(LuaCatsDocumentationTarget(decl))
    }
}
