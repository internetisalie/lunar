package net.internetisalie.lunar.lang.psi.types

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import net.internetisalie.lunar.lang.indexing.LuaAliasIndex
import net.internetisalie.lunar.lang.indexing.LuaClassNameIndex
import net.internetisalie.lunar.lang.indexing.LuaGlobalDeclarationIndex
import net.internetisalie.lunar.lang.psi.LuaFuncDecl
import net.internetisalie.lunar.lang.path.PathConfiguration
import net.internetisalie.lunar.lang.psi.LuaFile
import net.internetisalie.lunar.lang.psi.LuaLocalVarDecl
import net.internetisalie.lunar.lang.psi.stubs.LuaFileStub
import org.jetbrains.annotations.VisibleForTesting
import java.util.concurrent.ConcurrentHashMap
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import java.io.File

class LuaTypeManagerImpl(private val project: Project) : LuaTypeManager {

    private val typeCache: CachedValue<MutableMap<String, LuaType?>> =
        CachedValuesManager.getManager(project).createCachedValue(
            {
                CachedValueProvider.Result.create(
                    java.util.Collections.synchronizedMap(mutableMapOf<String, LuaType?>()),
                    PsiModificationTracker.getInstance(project),
                )
            },
            /* trackValue = */ false,
        )

    private val moduleCache: CachedValue<MutableMap<String, LuaType?>> =
        CachedValuesManager.getManager(project).createCachedValue(
            {
                CachedValueProvider.Result.create(
                    java.util.Collections.synchronizedMap(mutableMapOf<String, LuaType?>()),
                    PsiModificationTracker.getInstance(project),
                )
            },
            /* trackValue = */ false,
        )

    private val resolvingModules = ThreadLocal.withInitial { mutableSetOf<String>() }
    private val resolvingTypes = ThreadLocal.withInitial { mutableSetOf<String>() }

    override fun resolveType(name: String, context: PsiElement): LuaType? {
        LuaPrimitiveType.PRIMITIVES[name]?.let { return it }
        val cache = typeCache.value
        if (cache.containsKey(name)) return cache[name]
        if (name in resolvingTypes.get()) return null // Break reentrant cycles

        return try {
            resolvingTypes.get().add(name)
            doResolveType(name, project)
        } catch (e: Exception) {
            logError("Error resolving type $name", e)
            throw e
        } finally {
            resolvingTypes.get().remove(name)
        }
    }

    override fun resolveModule(moduleName: String, context: PsiElement): LuaType? {
        val cache = moduleCache.value
        if (cache.containsKey(moduleName)) return cache[moduleName]

        val active = resolvingModules.get()
        if (!active.add(moduleName)) {
            return LuaPrimitiveType.ANY // Cycle detected
        }
        try {
            val result = doResolveModule(moduleName, context)
            cache[moduleName] = result
            return result
        } finally {
            active.remove(moduleName)
        }
    }

    private fun doResolveModule(moduleName: String, context: PsiElement): LuaType {
        val patterns = PathConfiguration.getProjectSourcePathPatterns(project)
        for (pattern in patterns) {
            val path = pattern.interpolate(moduleName)

            var virtualFile = findVirtualFile(path)
            if (virtualFile == null) {
                val fileName = path.substringAfterLast('/')
                val projectScope = GlobalSearchScope.allScope(project)
                virtualFile = FilenameIndex.getVirtualFilesByName(fileName, projectScope).firstOrNull()
            }

            if (virtualFile == null) continue

            val psiFile = PsiManager.getInstance(project).findFile(virtualFile) as? LuaFile ?: continue

            val stub = psiFile.stub
            if (stub != null) {
                val exportedTypeString = stub.exportedTypeString
                if (exportedTypeString != null) {
                    return TypeParser.parse(exportedTypeString, context)
                }
            } else {
                // Fallback: use live analysis if stub is not available
                val snapshot = LuaTypesVisitor.getTypes(psiFile)
                val graphType = snapshot.getFileReturnType()
                if (graphType != LuaGraphType.Any && graphType != LuaGraphType.Undefined) {
                    return snapshot.graphTypeToLuaType(graphType)
                }
            }
        }
        return LuaPrimitiveType.ANY
    }

    private fun findVirtualFile(path: String): VirtualFile? {
        LocalFileSystem.getInstance().findFileByPath(path)?.let { return it }
        return VfsUtil.findFileByIoFile(File(path), true)
    }

    private fun doResolveType(name: String, project: Project): LuaType? {
        val scope = GlobalSearchScope.projectScope(project)
        val classDecls = StubIndex.getElements(LuaClassNameIndex.KEY, name, project, scope, LuaLocalVarDecl::class.java)
        if (classDecls.isNotEmpty()) {
            return materializeClass(name, classDecls).also { typeCache.value[name] = it }
        }
        val aliasDecls = StubIndex.getElements(LuaAliasIndex.KEY, name, project, scope, LuaLocalVarDecl::class.java)
        if (aliasDecls.isNotEmpty()) {
            return materializeAlias(name, aliasDecls.first()).also { typeCache.value[name] = it }
        }
        typeCache.value[name] = null
        return null
    }

    private fun materializeClass(name: String, decls: Collection<LuaLocalVarDecl>): LuaType {
        val membersMap = mutableMapOf<String, LuaTypeMember>()
        val superTypes = mutableListOf<LuaType>()
        for (decl in decls) {
            val stub = decl.stub
            if (stub != null) {
                stub.luacatsFields.forEach { (fName, fTypeStr) ->
                    membersMap[fName] = LuaTypeMember(fName, LuaTypeReference(fTypeStr, decl), sourceElement = decl)
                }
                stub.luacatsExtends?.split(',')?.forEach {
                    val extendedType = it.trim()
                    if (extendedType.isNotEmpty()) {
                        superTypes.add(LuaTypeReference(extendedType, decl))
                    }
                }
            } else {
                val cats = net.internetisalie.lunar.lang.psi.LuaPsiImplUtil.getCatsComment(decl)
                cats?.getFieldTagList()?.forEach {
                    val desc = it.fieldDescriptor
                    var fName = desc.argName?.text ?: desc.argType?.text ?: ""
                    var fTypeStr = it.argType.text
                    val isOptional = fName.endsWith("?")
                    if (isOptional) {
                        fName = fName.removeSuffix("?")
                        fTypeStr = "($fTypeStr) | nil"
                    }
                    membersMap[fName] = LuaTypeMember(fName, LuaTypeReference(fTypeStr, decl), sourceElement = it)
                }
                cats?.getClassTagList()?.firstOrNull()?.parentTypes?.let { parentTypes ->
                    parentTypes.argTypeList.forEach { parentType ->
                        superTypes.add(LuaTypeReference(parentType.text.trim(), decl))
                    }
                }
            }
        }
        LuaImplicitFields.collect(name, decls, membersMap)
        // Method-aware members: `function Class:m` / `function Class.fn` declarations are not
        // captured as @field/implicit members, so resolveMember would miss them otherwise.
        collectMethodMembers(name, membersMap)
        return LuaClassType(name, superTypes, membersMap)
    }

    /**
     * Enumerate every `function <className>:method` and `function <className>.fn` declaration
     * project-wide via [LuaGlobalDeclarationIndex] key iteration, reading from stubs only, and
     * add them as function-typed members. The result is memoized by the caller (materializeClass
     * is cached in [typeCache], invalidated on PSI modification).
     */
    private fun collectMethodMembers(className: String, membersMap: MutableMap<String, LuaTypeMember>) {
        val scope = GlobalSearchScope.projectScope(project)
        val colonPrefix = "$className:"
        val dotPrefix = "$className."
        val allKeys = StubIndex.getInstance().getAllKeys(LuaGlobalDeclarationIndex.KEY, project)
        for (key in allKeys) {
            val isColon = key.startsWith(colonPrefix)
            val isDot = key.startsWith(dotPrefix)
            if (!isColon && !isDot) continue
            val memberName = key.substring(className.length + 1)
            // Skip nested qualifiers (e.g. "Foo.bar.baz" when collecting members of "Foo")
            if (memberName.contains('.') || memberName.contains(':')) continue
            if (membersMap.containsKey(memberName)) continue

            val decls = StubIndex.getElements(
                LuaGlobalDeclarationIndex.KEY,
                key,
                project,
                scope,
                LuaFuncDecl::class.java,
            )
            val decl = decls.firstOrNull() ?: continue
            val fnType = funcTypeFromStub(className, decl)
            membersMap[memberName] = LuaTypeMember(memberName, fnType, sourceElement = decl)
        }
    }

    private fun funcTypeFromStub(className: String, decl: LuaFuncDecl): LuaType {
        // Prefer the stub (no AST load) but fall back to the cats comment for AST-backed decls —
        // the stub is null when the method is declared in the file currently being edited, where
        // resolving its `---@return` still matters (NAV-05/06, parameter hints). Mirrors how
        // materializeClass reads @field from either the stub or LuaPsiImplUtil.getCatsComment.
        val stub = decl.stub
        val cats = if (stub == null) net.internetisalie.lunar.lang.psi.LuaPsiImplUtil.getCatsComment(decl) else null
        val paramTypes: Map<String, String> = stub?.luacatsParamTypes
            ?: cats?.getParamTagList()?.associate { (it.argName?.text ?: "") to it.argType.text }
            ?: emptyMap()
        val rawReturn = stub?.luacatsReturnType ?: cats?.getReturnTagList()?.firstOrNull()?.argType?.text

        val params = paramTypes.map { (pName, pType) -> LuaParameter(pName, LuaTypeReference(pType, decl)) }
        // `---@return self` parses to a type literally named "self"; substitute the receiver class.
        val returnType: LuaType = when {
            rawReturn == null -> LuaPrimitiveType.UNKNOWN
            rawReturn == "self" -> LuaTypeReference(className, decl)
            else -> LuaTypeReference(rawReturn, decl)
        }
        return LuaFunctionType(params, returnType)
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

    override fun inferType(element: PsiElement): LuaType = LuaPrimitiveType.ANY
    override fun createTypeReference(name: String, context: PsiElement): LuaType = LuaTypeReference(name, context)

    @VisibleForTesting
    fun clearCache() { typeCache.value.clear() }

    private fun logError(message: String, e: Exception) {
        val log = com.intellij.openapi.diagnostic.Logger.getInstance(LuaTypeManagerImpl::class.java)
        log.error(message, e)
    }
}
