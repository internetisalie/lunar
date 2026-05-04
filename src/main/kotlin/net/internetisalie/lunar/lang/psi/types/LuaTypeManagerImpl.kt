package net.internetisalie.lunar.lang.psi.types

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import net.internetisalie.lunar.lang.indexing.LuaAliasIndex
import net.internetisalie.lunar.lang.indexing.LuaClassNameIndex
import net.internetisalie.lunar.lang.psi.LuaLocalVarDecl
import org.jetbrains.annotations.VisibleForTesting
import java.util.concurrent.ConcurrentHashMap

class LuaTypeManagerImpl(private val project: Project) : LuaTypeManager {

    private val typeCache = ConcurrentHashMap<String, LuaType?>()

    override fun resolveType(name: String, context: PsiElement): LuaType? {
        // 1. Primitives (no caching needed)
        LuaPrimitiveType.PRIMITIVES[name]?.let { return it }

        // 2. Check in-memory cache first (for current session)
        typeCache[name]?.let { return it }

        return try {
            doResolveType(name, project)
        } catch (e: Exception) {
            logError("Error resolving type $name", e)
            throw e
        }
    }

    private fun doResolveType(name: String, project: Project): LuaType? {
        val scope = GlobalSearchScope.projectScope(project)
        
        // 1. Check Classes
        val classDecls = StubIndex.getElements(LuaClassNameIndex.KEY, name, project, scope, LuaLocalVarDecl::class.java)
        if (classDecls.isNotEmpty()) {
            return materializeClass(name, classDecls).also { typeCache[name] = it }
        }
        
        // 2. Check Aliases
        val aliasDecls = StubIndex.getElements(LuaAliasIndex.KEY, name, project, scope, LuaLocalVarDecl::class.java)
        if (aliasDecls.isNotEmpty()) {
            return materializeAlias(name, aliasDecls.first()).also { typeCache[name] = it }
        }

        typeCache[name] = null
        return null
    }

    private fun materializeClass(name: String, decls: Collection<LuaLocalVarDecl>): LuaType {
        val members = mutableMapOf<String, LuaTypeMember>()
        val superTypes = mutableListOf<LuaType>()
        
        for (decl in decls) {
            val stub = decl.stub
            if (stub != null) {
                stub.luacatsFields.forEach { (fName, fTypeStr) ->
                    members[fName] = LuaTypeMember(fName, LuaTypeReference(fTypeStr, decl), sourceElement = decl)
                }
                stub.luacatsExtends?.let {
                    superTypes.add(LuaTypeReference(it, decl))
                }
            } else {
                val cats = net.internetisalie.lunar.lang.psi.LuaPsiImplUtil.getCatsComment(decl)
                cats?.getFieldTagList()?.forEach { 
                    val desc = it.fieldDescriptor
                    val fName = desc.argName?.text ?: desc.argType?.text ?: ""
                    val fTypeStr = it.argType.text
                    members[fName] = LuaTypeMember(fName, LuaTypeReference(fTypeStr, decl), sourceElement = it)
                }
                cats?.getClassTagList()?.firstOrNull()?.parentTypes?.text?.removePrefix(":")?.trim()?.let {
                    superTypes.add(LuaTypeReference(it, decl))
                }
            }
        }
        
        return LuaClassType(name, superTypes, members)
    }

    private fun materializeAlias(name: String, decl: LuaLocalVarDecl): LuaType {
        val stub = decl.stub
        val targetTypeStr = if (stub != null) {
            stub.luacatsAliasTarget
        } else {
            val cats = net.internetisalie.lunar.lang.psi.LuaPsiImplUtil.getCatsComment(decl)
            cats?.getAliasTagList()?.firstOrNull()?.argType?.text
        }
        
        return LuaAliasType(name, LuaTypeReference(targetTypeStr ?: "any", decl))
    }

    override fun inferType(element: PsiElement): LuaType {
        return LuaPrimitiveType.ANY
    }

    override fun createTypeReference(name: String, context: PsiElement): LuaType {
        return LuaTypeReference(name, context)
    }

    @VisibleForTesting
    fun clearCache() {
        typeCache.clear()
    }

    private fun logError(message: String, e: Exception) {
        val log = com.intellij.openapi.diagnostic.Logger.getInstance(LuaTypeManagerImpl::class.java)
        log.error(message, e)
    }
}
