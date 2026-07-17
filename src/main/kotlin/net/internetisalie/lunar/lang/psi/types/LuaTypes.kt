package net.internetisalie.lunar.lang.psi.types

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import net.internetisalie.lunar.lang.psi.FileUserData
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

    override fun graphTypeToLuaType(type: LuaGraphType): LuaType = when (type) {
        LuaGraphType.Any -> LuaPrimitiveType.ANY
        LuaGraphType.Undefined -> LuaPrimitiveType.UNKNOWN
        LuaGraphType.Nil -> LuaPrimitiveType.NIL
        LuaGraphType.Boolean -> LuaPrimitiveType.BOOLEAN
        LuaGraphType.Number -> LuaPrimitiveType.NUMBER
        LuaGraphType.String -> LuaPrimitiveType.STRING
        is LuaGraphType.Table -> {
            val membersMap = type.getMembers().mapValues { (name, node) ->
                LuaTypeMember(name, graphTypeToLuaType(node.write))
            }
            if (type.className != null) {
                // Enrich the graph-derived class with nominal members (incl. methods + supertypes)
                // from the type manager, so method-aware members reach nominal consumers such as
                // LuaParameterInlayHintsProvider.resolveMember and the NAV-05/06 hierarchy walk.
                val nominal = contextFile?.let {
                    LuaTypeManager.getInstance(it.project).resolveType(type.className, it)
                }
                if (nominal is LuaClassType) {
                    val merged = LinkedHashMap<String, LuaTypeMember>()
                    merged.putAll(nominal.getMembers())
                    merged.putAll(membersMap) // graph members win on collision
                    LuaClassType(type.className, nominal.superTypes, merged)
                } else {
                    LuaClassType(type.className, emptyList(), membersMap)
                }
            } else {
                LuaTableLiteralType(membersMap)
            }
        }
        is LuaGraphType.Function -> {
            val params = type.params.map { p ->
                val name = p.name ?: when (val el = p.node.element) {
                    is net.internetisalie.lunar.lang.psi.LuaNameRef -> el.text
                    is net.internetisalie.lunar.lang.psi.LuaAttName -> el.nameRef.text
                    else -> "p"
                }
                LuaParameter(name, graphTypeToLuaType(p.node.write), p.isOptional, p.isVararg)
            }
            val returnType = type.returns.firstOrNull()?.let { graphTypeToLuaType(it.write) } ?: LuaPrimitiveType.VOID
            LuaFunctionType(params, returnType)
        }
        is LuaGraphType.Array -> {
            LuaArrayType(graphTypeToLuaType(type.elementType))
        }
        is LuaGraphType.Union -> {
            val luaTypes = type.types.map { graphTypeToLuaType(it) }.toSet()
            LuaUnionType(luaTypes)
        }
        is LuaGraphType.Generic -> {
            LuaGenericType(type.name)
        }
    }

    companion object {
        /**
         * Compute (or return a cached) [LuaTypes] snapshot for [file].
         *
         * The cache key folds the document-text hash with the active target
         * (REDIS-04 §3.1a / DR-03b): ambient stub-global seeding depends on the target, and a
         * target switch does not change the file text, so a text-only key would serve a stale,
         * wrongly-seeded snapshot. Both a text edit and a target switch now invalidate the cache.
         */
        fun forFile(file: PsiFile): LuaTypes {
            val psiFile = file.containingFile
            val cacheKey = snapshotCacheKey(psiFile)
            val existing = psiFile.getUserData(LuaTypesVisitor.KEY)
            if (existing != null && existing.hash == cacheKey) {
                return existing.data
            }
            val fresh = LuaTypesVisitor.buildSnapshot(psiFile)
            psiFile.putUserData(LuaTypesVisitor.KEY, FileUserData(cacheKey, fresh))
            return fresh
        }

        private fun snapshotCacheKey(file: PsiFile): Int {
            val textHash = file.fileDocument.text.hashCode()
            val target = LuaProjectSettings.getInstance(file.project).state.getTarget()
            return 31 * textHash + target.hashCode()
        }
    }
}
