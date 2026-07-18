package net.internetisalie.lunar.lang.psi.types

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import net.internetisalie.lunar.settings.LuaProjectSettings

/**
 * Public interface for querying the inferred type of any PSI element in a file.
 * Consumers (IDE surfaces: hover, inlay hints, inspections) only use this interface;
 * they never touch [LuaTypeGraph] directly.
 *
 * Obtain via [LuaTypesSnapshot.forFile].
 *
 * See: docs/requirements/spec/type/design/phase-1-api-contracts.md §5
 */
interface LuaTypes {
    /**
     * Returns the inferred [LuaGraphType] for [element], or [LuaGraphType.Undefined] if
     * the element has no inferred type (was not visited or has no useful constraint).
     */
    fun getValueType(element: PsiElement): LuaGraphType

    /**
     * Returns all type errors detected in this file.  Phase 1: always empty
     * (checkTypes() is only wired in Phase 3+).
     */
    fun getErrors(): List<ElementError>

    /** Convert the graph-internal type back to a Layer-1 [LuaType] for IDE display. */
    fun graphTypeToLuaType(type: LuaGraphType): LuaType

    /** Returns the inferred return type of the file. */
    fun getFileReturnType(): LuaGraphType
}

/**
 * Immutable snapshot of the type graph for a single file.  Created and cached by
 * [LuaTypesSnapshot.forFile].
 */
class LuaTypesSnapshot(
    private val graph: LuaTypeGraph,
    /** Maps each PSI element to the graph node that represents its inferred type. */
    private val elementNodes: Map<PsiElement, List<TypeNode>>,
    private val fileReturnType: LuaGraphType = LuaGraphType.Any,
    /** The file this snapshot was built for — the context handle for nominal type resolution. */
    private val contextFile: PsiFile? = null,
) : LuaTypes {

    override fun getFileReturnType(): LuaGraphType = fileReturnType

    override fun getValueType(element: PsiElement): LuaGraphType {
        val node = elementNodes[element]?.firstOrNull() ?: return LuaGraphType.Undefined
        return when (node) {
            is VariableNode -> {
                val write = node.write
                val read = node.read
                if (write is LuaGraphType.Table && read is LuaGraphType.Table) {
                    val mergedMembers = mutableMapOf<String, VariableNode>()
                    mergedMembers.putAll(write.localMembers)
                    mergedMembers.putAll(read.localMembers)
                    LuaGraphType.Table(write.className ?: read.className, mergedMembers, write.superTypes, write.isExact)
                } else if (write != LuaGraphType.Undefined) {
                    write
                } else {
                    if (read != LuaGraphType.Any) read else LuaGraphType.Undefined
                }
            }
            is ValueNode -> node.write
            is UseNode -> node.read
            else -> LuaGraphType.Undefined
        }
    }

    override fun getErrors(): List<ElementError> = graph.errors

    override fun graphTypeToLuaType(type: LuaGraphType): LuaType =
        graphTypeToLuaType(type, mutableMapOf())

    /**
     * MAINT-25-02: cycle-safe conversion. Mirrors [LuaGraphType.fromLuaType]: register a placeholder
     * in [visited] before recursing into a structural type's members, so a self-referential graph
     * type (`t.self = t`) resolves the cycle-back reference to the in-construction placeholder
     * instead of recursing forever (StackOverflowError). Scalar heads cannot cycle — returned directly.
     */
    private fun graphTypeToLuaType(type: LuaGraphType, visited: MutableMap<LuaGraphType, LuaType>): LuaType {
        visited[type]?.let { return it }
        return when (type) {
            LuaGraphType.Any -> LuaPrimitiveType.ANY
            LuaGraphType.Undefined -> LuaPrimitiveType.UNKNOWN
            LuaGraphType.Nil -> LuaPrimitiveType.NIL
            LuaGraphType.Boolean -> LuaPrimitiveType.BOOLEAN
            LuaGraphType.Number -> LuaPrimitiveType.NUMBER
            LuaGraphType.String -> LuaPrimitiveType.STRING
            is LuaGraphType.Table -> tableToLuaType(type, visited)
            is LuaGraphType.Function -> functionToLuaType(type, visited).also { visited[type] = it }
            is LuaGraphType.Array ->
                LuaArrayType(graphTypeToLuaType(type.elementType, visited)).also { visited[type] = it }
            is LuaGraphType.Union -> {
                val luaTypes = type.types.map { graphTypeToLuaType(it, visited) }.toSet()
                LuaUnionType(luaTypes).also { visited[type] = it }
            }
            is LuaGraphType.Generic -> LuaGenericType(type.name)
        }
    }

    private fun tableToLuaType(type: LuaGraphType.Table, visited: MutableMap<LuaGraphType, LuaType>): LuaType {
        val members = LinkedHashMap<String, LuaTypeMember>()
        if (type.className != null) {
            val nominal = contextFile?.let {
                LuaTypeManager.getInstance(it.project).resolveType(type.className, it)
            }
            val superTypes = (nominal as? LuaClassType)?.superTypes ?: emptyList()
            val placeholder = LuaClassType(type.className, superTypes, members)
            visited[type] = placeholder
            // Enrich the graph-derived class with nominal members (incl. methods + supertypes) from
            // the type manager, so method-aware members reach nominal consumers such as
            // LuaParameterInlayHintsProvider.resolveMember and the NAV-05/06 hierarchy walk.
            (nominal as? LuaClassType)?.let { members.putAll(it.getMembers()) }
            type.getMembers().forEach { (name, node) ->
                members[name] = LuaTypeMember(name, graphTypeToLuaType(node.write, visited)) // graph members win
            }
            return placeholder
        }
        val placeholder = LuaTableLiteralType(members)
        visited[type] = placeholder
        type.getMembers().forEach { (name, node) ->
            members[name] = LuaTypeMember(name, graphTypeToLuaType(node.write, visited))
        }
        return placeholder
    }

    private fun functionToLuaType(type: LuaGraphType.Function, visited: MutableMap<LuaGraphType, LuaType>): LuaType {
        val params = type.params.map { p ->
            val name = p.name ?: when (val el = p.node.element) {
                is net.internetisalie.lunar.lang.psi.LuaNameRef -> el.text
                is net.internetisalie.lunar.lang.psi.LuaAttName -> el.nameRef.text
                else -> "p"
            }
            LuaParameter(name, graphTypeToLuaType(p.node.write, visited), p.isOptional, p.isVararg)
        }
        val returnType = type.returns.firstOrNull()?.let { graphTypeToLuaType(it.write, visited) } ?: LuaPrimitiveType.VOID
        return LuaFunctionType(params, returnType)
    }

    companion object {
        /**
         * Compute (or return a cached) [LuaTypes] snapshot for [file].
         *
         * MAINT-30-02 (§2.3/§3.4): memoized via [CachedValuesManager]. Dependencies are the file
         * itself + [PsiModificationTracker.MODIFICATION_COUNT] (any reparse — the FileUserData
         * text-hash-collision staleness is structurally impossible now) and the project's
         * [State.targetModificationTracker], so a text-free REDIS↔Lua target switch also invalidates
         * (REDIS-04 §3.1a / TC-04). The TYPE-10 [inProgressSnapshot] reentrancy guard runs FIRST,
         * ahead of `getCachedValue`, so a re-entrant `visitFuncCall → forFile` never recurses into a
         * nested `getCachedValue` compute (TC-06).
         */
        fun forFile(file: PsiFile): LuaTypes {
            val psiFile = file.containingFile
            LuaTypesVisitor.inProgressSnapshot(psiFile)?.let { return it }
            return CachedValuesManager.getCachedValue(psiFile, LuaTypesVisitor.KEY) {
                val targetTracker = LuaProjectSettings.getInstance(psiFile.project).state.targetModificationTracker
                CachedValueProvider.Result.create(
                    LuaTypesVisitor.buildSnapshot(psiFile),
                    psiFile,
                    PsiModificationTracker.MODIFICATION_COUNT,
                    targetTracker,
                )
            }
        }
    }
}
