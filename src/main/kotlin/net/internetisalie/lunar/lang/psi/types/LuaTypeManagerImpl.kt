package net.internetisalie.lunar.lang.psi.types

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.PsiTreeUtil
import net.internetisalie.lunar.lang.indexing.LuaAliasIndex
import net.internetisalie.lunar.lang.indexing.LuaCatsTypeNameIndex
import com.intellij.util.indexing.FileBasedIndex
import net.internetisalie.lunar.lang.indexing.LuaClassNameIndex
import net.internetisalie.lunar.lang.indexing.LuaGlobalAssignmentIndex
import net.internetisalie.lunar.lang.indexing.LuaGlobalDeclarationIndex
import net.internetisalie.lunar.lang.psi.LuaFuncDecl
import net.internetisalie.lunar.lang.path.resolveModuleCandidates
import net.internetisalie.lunar.lang.psi.LuaFile
import net.internetisalie.lunar.lang.psi.LuaLocalVarDecl
import net.internetisalie.lunar.lang.psi.stubs.LuaFileStub
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsClassTag
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsComment
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsDeclarations
import org.jetbrains.annotations.VisibleForTesting
import java.util.concurrent.ConcurrentHashMap

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

    private val globalCache: CachedValue<MutableMap<String, LuaType?>> =
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
    private val resolvingGlobals = ThreadLocal.withInitial { mutableSetOf<String>() }

    override fun resolveType(name: String, context: PsiElement): LuaType? {
        LuaPrimitiveType.PRIMITIVES[name]?.let { return it }
        val cache = typeCache.value
        if (cache.containsKey(name)) return cache[name]
        if (name in resolvingTypes.get()) return null // Break reentrant cycles

        return try {
            resolvingTypes.get().add(name)
            doResolveType(name, project)
        } catch (e: ProcessCanceledException) {
            throw e
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

    /**
     * BUG-395. Keyed on the name alone (a Lua global is one namespace project-wide) and cached until
     * the next PSI modification, because this runs for *every* unbound name reference the type
     * visitor meets — `print`, `pairs`, `table` — and each miss would otherwise re-query the index.
     *
     * [GlobalSearchScope.allScope] rather than `projectScope` is the whole point: library files
     * (bundled stdlib, definition libraries, LuaRocks trees) live outside project scope, which is
     * exactly why their members never appeared.
     */
    override fun resolveGlobal(name: String, context: PsiElement): LuaType? {
        if (DumbService.isDumb(project)) return null
        val cache = globalCache.value
        if (cache.containsKey(name)) return cache[name]
        if (!resolvingGlobals.get().add(name)) return null // Break reentrant cycles
        return try {
            doResolveGlobal(name, context).also { cache[name] = it }
        } finally {
            resolvingGlobals.get().remove(name)
        }
    }

    private fun doResolveGlobal(name: String, context: PsiElement): LuaType? {
        val declaringFiles = FileBasedIndex.getInstance()
            .getContainingFiles(LuaGlobalAssignmentIndex.KEY, name, GlobalSearchScope.allScope(project))
        val here = context.containingFile?.originalFile
        return declaringFiles.asSequence()
            .mapNotNull { PsiManager.getInstance(project).findFile(it) as? LuaFile }
            .filter { it != here }
            .firstNotNullOfOrNull { globalTypeIn(it, name) }
    }

    /** The type [file] gives the global [name], or null if it declares it without a useful type. */
    private fun globalTypeIn(file: LuaFile, name: String): LuaType? {
        val snapshot = LuaTypesSnapshot.forFile(file)
        val graphType = snapshot.getGlobalType(name)
        if (graphType == LuaGraphType.Undefined || graphType == LuaGraphType.Any) return null
        return snapshot.graphTypeToLuaType(graphType)
    }

    /**
     * The type a module file exports, from its stub if that can answer and from live analysis if not.
     *
     * BUG-398: live analysis used to run *only* when the file had no stub at all, so a stub whose
     * `exportedTypeString` was empty — anything the stub builder's narrow `return <local>` / `@type`
     * extraction does not recognise — returned null and the module lost its type outright. Whether a
     * file is stubbed or AST-backed depends on nothing more than whether something happened to load
     * its AST first, so the same `require` resolved differently from one caret to the next.
     */
    private fun getModuleType(psiFile: LuaFile, context: PsiElement): LuaType? {
        psiFile.stub?.exportedTypeString?.let { return TypeParser.parse(it, context) }

        val snapshot = LuaTypesSnapshot.forFile(psiFile)
        val graphType = snapshot.getFileReturnType()
        if (graphType == LuaGraphType.Any || graphType == LuaGraphType.Undefined) return null
        return snapshot.graphTypeToLuaType(graphType)
    }

    // MAINT-30-03 (§2.5): resolution over the single canonical candidate sequence; the terminal keeps
    // the "skip a found-but-untyped file, try the next pattern" semantic via firstNotNullOfOrNull.
    private fun doResolveModule(moduleName: String, context: PsiElement): LuaType =
        resolveModuleCandidates(project, moduleName)
            .firstNotNullOfOrNull { getModuleType(it, context) }
            ?: LuaPrimitiveType.ANY

    /**
     * BUG-399: searched under [GlobalSearchScope.allScope], not `projectScope`.
     *
     * A `---@class` declared in a library file — a bundled stdlib stub, a definition library, a
     * LuaRocks tree — is invisible to project scope by construction, so the class simply did not
     * exist as far as the type manager was concerned and every member of it went missing. This is
     * only observable with a real registered library root: inside a light fixture's own project
     * everything is in project scope, which is why the unit tests for BUG-395 and BUG-398 passed
     * while the live IDE showed nothing.
     */
    private fun doResolveType(name: String, project: Project): LuaType? {
        val scope = GlobalSearchScope.allScope(project)
        val classDecls = StubIndex.getElements(LuaClassNameIndex.KEY, name, project, scope, LuaLocalVarDecl::class.java)
        if (classDecls.isNotEmpty()) {
            return materializeClass(name, classDecls).also { typeCache.value[name] = it }
        }
        // BUG-400: a `---@class` with no stubbed host. LuaCATS tags are not stubbed — they ride a host
        // declaration's stub — and the bundled stdlib stubs declare their classes above a bare global
        // assignment (`---@class package` over `package = {}`), which is not a stubbed PSI type. So
        // LuaClassNameIndex never sees them and `package`, `io`, `os`, `debug`, `coroutine` and `utf8`
        // all resolved to nothing. The file-based LuaCatsTypeNameIndex reads the tag directly and was
        // built for exactly this; it was already wired to Go-to-Class and quick-doc, but not here.
        materializeUnhostedClass(name, project)?.let { return it.also { type -> typeCache.value[name] = type } }

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
            val parts = hostedParts(decl)
            // Last write wins, as it always has here — deliberately unlike materializeUnhostedClass,
            // which merges several tags for one name and so keeps first-wins.
            parts.members.forEach { member ->
                membersMap[member.name] = LuaTypeMember(
                    member.name,
                    LuaTypeReference(member.typeName, decl),
                    // The tag on the AST path, the host declaration on the stub path (which has no
                    // PSI). Preserved exactly: LuaOverrideLineMarkerProvider uses sourceElement as
                    // gutter navigation targets, so collapsing this to `decl` would silently
                    // regress override navigation — and the parity harness compares names and types
                    // only, so it would not catch that.
                    sourceElement = member.tag ?: decl,
                )
            }
            parts.superTypeNames.forEach { superTypes.add(LuaTypeReference(it, decl)) }
        }
        LuaImplicitFields.collect(classReceiverNames(name, decls), decls.mapNotNull { it.containingFile }.distinct(), membersMap)
        // Method-aware members: `function Class:m` / `function Class.fn` declarations are not
        // captured as @field/implicit members, so resolveMember would miss them otherwise.
        collectMethodMembers(name, decls, membersMap)
        return LuaClassType(name, superTypes, membersMap)
    }

    /**
     * Builds the class for a `---@class` that has no host declaration to hang a stub on (BUG-400).
     *
     * The receiver is the class name itself — `package = {}` binds `package`, so there is no separate
     * local to also match, which is why this needs none of [classReceiverNames]' machinery. Members
     * come from the same three places a hosted class uses: `@field` tags on the tag's own comment,
     * implicit `Name.field = …` assignments in the declaring files, and `function Name.fn()` decls.
     */
    private fun materializeUnhostedClass(name: String, project: Project): LuaType? {
        val tags = catsClassTags(name, project)
        if (tags.isEmpty()) return null

        val membersMap = mutableMapOf<String, LuaTypeMember>()
        val superTypes = mutableListOf<LuaType>()
        tags.forEach { tag ->
            val parts = unhostedParts(tag) ?: return@forEach
            // Two different merge rules, and both are pre-existing behaviour rather than a choice
            // made here. WITHIN one comment a repeated `@field` name is last-wins, because the old
            // code accumulated into a map before returning; ACROSS tags it is first-wins, via the
            // putIfAbsent below, because several `---@class Same` tags merge into one class.
            // Collapsing these into a single putIfAbsent per member reads more simply and silently
            // flips the within-comment case — exactly the drift this feature exists to remove.
            val perComment = LinkedHashMap<String, LuaTypeMember>()
            parts.members.forEach { member ->
                perComment[member.name] = LuaTypeMember(
                    member.name,
                    LuaTypeReference(member.typeName, tag),
                    sourceElement = member.tag ?: tag,
                )
            }
            perComment.forEach { (memberName, member) -> membersMap.putIfAbsent(memberName, member) }
            // The dropped `if (it !in superTypes)` guard was dead code, so dropping it preserves
            // behaviour rather than changing it: LuaTypeReference has no equals override, so `!in`
            // compared identity, and a fresh reference was constructed per tag on every call — the
            // same object was never re-added. Two `---@class Same` tags that both declare `: P`
            // accumulate two `P` references today and must continue to (locked by TC-10).
            // Substituting a name comparison here would look like a tidy-up and be a silent
            // behaviour change, which is the exact class of drift this feature exists to remove.
            parts.superTypeNames.forEach { superTypes.add(LuaTypeReference(it, tag)) }
        }
        LuaImplicitFields.collect(setOf(name), tags.mapNotNull { it.containingFile }.distinct(), membersMap)
        addMethodsOf(
            MethodScan(name, name, null),
            StubIndex.getInstance().getAllKeys(LuaGlobalDeclarationIndex.KEY, project),
            membersMap,
        )
        return LuaClassType(name, superTypes, membersMap)
    }

    /** Every `---@class <name>` tag in the project, found through the file-based tag index. */
    private fun catsClassTags(name: String, project: Project): List<LuaCatsClassTag> {
        val psiManager = PsiManager.getInstance(project)
        val scope = GlobalSearchScope.allScope(project)
        return FileBasedIndex.getInstance()
            .getContainingFiles(LuaCatsTypeNameIndex.KEY, name, scope)
            .mapNotNull { psiManager.findFile(it) as? LuaFile }
            .flatMap { PsiTreeUtil.findChildrenOfType(it, LuaCatsClassTag::class.java) }
            .filter { it.argType?.text?.trim() == name }
    }

    /**
     * Extraction output — names and type strings, not built engine types — so that the hosted and
     * un-hosted paths can share one shape and each apply its own merge rule to it.
     */
    private data class DeclaredParts(
        val members: List<LuaCatsDeclarations.FieldMember>,
        val superTypeNames: List<String>,
    )

    /**
     * What [decl] declares, from whichever source is available.
     *
     * This is the whole of the stub/AST fork now: a stub is a serialized snapshot with no PSI, so
     * the fork cannot be deleted — but with extraction living in [LuaCatsDeclarations] the two arms
     * can only differ in provenance, not in what a tag means.
     */
    private fun hostedParts(decl: LuaLocalVarDecl): DeclaredParts {
        val stub = decl.stub
        if (stub != null) {
            return DeclaredParts(
                members = stub.luacatsFields.map { (n, t) -> LuaCatsDeclarations.FieldMember(n, t, tag = null) },
                // BUG-402: the stub stores the list the extractor produced, so nothing is re-derived
                // here. The `split(',')` this replaces cut `Base<string, number>` into two fragments.
                superTypeNames = stub.luacatsParents,
            )
        }
        val cats = net.internetisalie.lunar.lang.psi.LuaPsiImplUtil.getCatsComment(decl)
            ?: return DeclaredParts(emptyList(), emptyList())
        return DeclaredParts(
            members = LuaCatsDeclarations.fieldMembers(cats),
            superTypeNames = cats.getClassTagList().firstOrNull()
                ?.let { LuaCatsDeclarations.parentTypeNames(it) }.orEmpty(),
        )
    }

    /**
     * The `@field` members and `: Parent` supertypes declared alongside [tag].
     *
     * Named apart from [hostedParts] deliberately: Kotlin would accept `declaredParts(decl)` and
     * `declaredParts(tag)` as an overload pair, so a half-applied rename would compile.
     */
    private fun unhostedParts(tag: LuaCatsClassTag): DeclaredParts? {
        val cats = PsiTreeUtil.getParentOfType(tag, LuaCatsComment::class.java) ?: return null
        return DeclaredParts(
            LuaCatsDeclarations.fieldMembers(cats),
            LuaCatsDeclarations.parentTypeNames(tag),
        )
    }

    /**
     * Enumerate every `function <receiver>:method` / `function <receiver>.fn` declaration and add it
     * as a function-typed member, reading from stubs only. The result is memoized by the caller
     * (materializeClass is cached in [typeCache], invalidated on PSI modification).
     *
     * BUG-398: the receiver is not always the class name — see [classReceiverNames]. A match on the
     * class name is honoured project-wide, because a class name is a global namespace; a match on a
     * declaring local's name is confined to that local's own file, where the variable exists.
     */
    private fun collectMethodMembers(
        className: String,
        decls: Collection<LuaLocalVarDecl>,
        membersMap: MutableMap<String, LuaTypeMember>,
    ) {
        val allKeys = StubIndex.getInstance().getAllKeys(LuaGlobalDeclarationIndex.KEY, project)
        addMethodsOf(MethodScan(className, className, null), allKeys, membersMap)
        decls.forEach { decl ->
            val localName = declaredName(decl)?.takeIf { it != className } ?: return@forEach
            addMethodsOf(MethodScan(className, localName, decl.containingFile), allKeys, membersMap)
        }
    }

    /** One receiver name to scan for, and the file it is confined to (null = project-wide). */
    private data class MethodScan(val className: String, val receiver: String, val onlyIn: PsiFile?)

    private fun addMethodsOf(
        scan: MethodScan,
        allKeys: Collection<String>,
        membersMap: MutableMap<String, LuaTypeMember>,
    ) {
        // allScope, for the same reason doResolveType uses it: `function Class.method` declarations
        // in a library file are a class's members just as much as a project file's are (BUG-399).
        val scope = GlobalSearchScope.allScope(project)
        for (key in allKeys) {
            val memberName = memberNameOf(key, scan.receiver) ?: continue
            if (membersMap.containsKey(memberName)) continue

            val decls = StubIndex.getElements(
                LuaGlobalDeclarationIndex.KEY,
                key,
                project,
                scope,
                LuaFuncDecl::class.java,
            )
            val decl = decls.firstOrNull { scan.onlyIn == null || it.containingFile == scan.onlyIn } ?: continue
            val fnType = funcTypeFromStub(scan.className, decl)
            membersMap[memberName] = LuaTypeMember(memberName, fnType, sourceElement = decl)
        }
    }

    /** `Receiver.m` / `Receiver:m` → `m`; null for a non-match or a nested qualifier (`Foo.bar.baz`). */
    private fun memberNameOf(key: String, receiver: String): String? {
        if (!key.startsWith("$receiver.") && !key.startsWith("$receiver:")) return null
        return key.substring(receiver.length + 1).takeIf { !it.contains('.') && !it.contains(':') }
    }

    private fun funcTypeFromStub(className: String, decl: LuaFuncDecl): LuaType {
        // Prefer the stub (no AST load) but fall back to the cats comment for AST-backed decls —
        // the stub is null when the method is declared in the file currently being edited, where
        // resolving its `---@return` still matters (NAV-05/06, parameter hints). Mirrors how
        // materializeClass reads @field from either the stub or LuaPsiImplUtil.getCatsComment.
        val stub = decl.stub
        val cats = if (stub == null) net.internetisalie.lunar.lang.psi.LuaPsiImplUtil.getCatsComment(decl) else null
        val paramTypes: Map<String, String> = stub?.luacatsParamTypes
            ?: cats?.let { LuaCatsDeclarations.paramTypes(it) }
            ?: emptyMap()
        val rawReturn = stub?.luacatsReturnType ?: cats?.let { LuaCatsDeclarations.returnTypeName(it) }

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
            cats?.let { LuaCatsDeclarations.aliasTarget(it) }
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
