package net.internetisalie.lunar.lang.psi.types

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import net.internetisalie.lunar.lang.psi.cacheFileUserData

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
 * [LuaTypesVisitor.getTypes].
 */
class LuaTypesSnapshot(
    private val graph: LuaTypeGraph,
    /** Maps each PSI element to the graph node that represents its inferred type. */
    private val elementNodes: Map<PsiElement, List<TypeNode>>,
    private val fileReturnType: LuaGraphType = LuaGraphType.Any,
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
                LuaClassType(type.className, emptyList(), membersMap)
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
         * The cache is keyed on the document text hash, so it is automatically invalidated
         * whenever the file content changes.
         */
        fun forFile(file: PsiFile): LuaTypes = LuaTypesVisitor.KEY.cacheFileUserData(file) { psiFile: PsiFile ->
            LuaTypesVisitor.buildSnapshot(psiFile)
        }
    }
}
