package net.internetisalie.lunar.lang.psi.types

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import net.internetisalie.lunar.lang.psi.LuaElementFactory
import net.internetisalie.lunar.lang.psi.LuaLocalVarDecl
import net.internetisalie.lunar.lang.psi.LuaPsiImplUtil
import net.internetisalie.lunar.luacats.lang.psi.*

object TypeParser {
    fun parse(
        typeString: String,
        context: PsiElement,
    ): LuaType {
        val project = context.project
        val luaFile = LuaElementFactory.createFile(project, "---@type $typeString\nlocal _")
        val varDecl =
            PsiTreeUtil.findChildOfType(luaFile, LuaLocalVarDecl::class.java)
                ?: return LuaPrimitiveType.UNKNOWN

        val comment =
            LuaPsiImplUtil.getCatsComment(varDecl)
                ?: return LuaPrimitiveType.UNKNOWN

        val luaCatsType =
            PsiTreeUtil.findChildOfType(comment, LuaCatsType::class.java)
                ?: return LuaPrimitiveType.UNKNOWN

        return parseType(luaCatsType, context)
    }

    private fun parseType(
        type: LuaCatsType,
        context: PsiElement,
    ): LuaType = parseUnionType(type.unionType, context)

    private fun parseUnionType(
        unionType: LuaCatsUnionType,
        context: PsiElement,
    ): LuaType {
        val arrays = unionType.arrayTypeList
        if (arrays.isEmpty()) {
            // Fallback: try to parse distinct types if arrayTypeList is empty for some reason
            val distinctTypes = PsiTreeUtil.findChildrenOfType(unionType, LuaCatsDistinctType::class.java)
            if (distinctTypes.isEmpty()) return LuaPrimitiveType.UNKNOWN
            val types = distinctTypes.map { parseDistinctType(it, context) }.toSet()
            if (types.size == 1) return types.first()
            return LuaUnionType(types)
        }

        val types = arrays.map { parseArrayType(it, context) }.toSet()
        if (types.size == 1) return types.first()
        return LuaUnionType(types)
    }

    private fun parseArrayType(
        arrayType: LuaCatsArrayType,
        context: PsiElement,
    ): LuaType {
        var baseType = parseDistinctType(arrayType.distinctType, context)
        // If it's an array type, text ends with "[]" but the grammar might just have the bracket tokens.
        // The AST structure: arrayType ::= distinctType '[]'?
        // We can check if it has bracket children.
        if (arrayType.text.endsWith("[]")) {
            baseType = LuaArrayType(baseType)
        }
        return baseType
    }

    private fun parseDistinctType(
        distinctType: LuaCatsDistinctType,
        context: PsiElement,
    ): LuaType {
        distinctType.builtinType?.let {
            val name = it.text
            return LuaPrimitiveType.PRIMITIVES[name] ?: LuaPrimitiveType.UNKNOWN
        }
        distinctType.namedType?.let {
            val name = it.text
            return LuaTypeManager.getInstance(context.project).createTypeReference(name, context)
        }
        distinctType.parameterizedName?.let {
            val name = it.genericType.text
            val baseType = LuaTypeManager.getInstance(context.project).createTypeReference(name, context)
            val typeArgs =
                it.typeParamList.map { param ->
                    LuaTypeManager.getInstance(context.project).createTypeReference(param.text, context)
                }
            return LuaParameterizedType(baseType, typeArgs)
        }
        distinctType.tupleType?.let {
            // Treat tuple as an array of ANY for simplicity right now
            return LuaArrayType(LuaPrimitiveType.ANY)
        }
        distinctType.dictionaryType?.let { dictType ->
            // dictType.typeList usually has [KeyType, ValueType]
            val types = dictType.typeList
            val keyType = if (types.isNotEmpty()) parseType(types[0], context) else LuaPrimitiveType.ANY
            val valueType = if (types.size > 1) parseType(types[1], context) else LuaPrimitiveType.ANY

            // Map<K, V> via parameterized type
            val tableBase = LuaTypeManager.getInstance(context.project).createTypeReference("table", context)
            return LuaParameterizedType(tableBase, listOf(keyType, valueType))
        }
        distinctType.literalTableType?.let { return parseTableLiteral(it, context) }
        distinctType.functionSignatureType?.let { return parseFunctionSignature(it, context) }
        return LuaPrimitiveType.UNKNOWN
    }

    private fun parseTableLiteral(
        tableLiteral: LuaCatsLiteralTableType,
        context: PsiElement,
    ): LuaType {
        // A bracket key (`[1]:`) names no member; without the filter it would land under "unknown".
        val namedEntries =
            tableLiteral.tableLiteralEntryList.filter {
                PsiTreeUtil.getChildOfType(it, LuaCatsFieldKeyDescriptor::class.java) == null
            }
        val members =
            namedEntries.associate { entry ->
                val key = entry.namedType?.text ?: entry.builtinType?.text ?: "unknown"
                val valType = parseType(entry.type, context)
                // An optional key admits nil, the same widening `@field name?` gets (BUG-401).
                val memberType =
                    if (hasOptionalMarker(
                            entry,
                        )
                    ) {
                        LuaUnionType(setOf(valType, LuaPrimitiveType.NIL))
                    } else {
                        valType
                    }
                key to LuaTypeMember(key, memberType)
            }
        return LuaTableLiteralType(members)
    }

    private fun parseFunctionSignature(
        funcSig: LuaCatsFunctionSignatureType,
        context: PsiElement,
    ): LuaType {
        val params = funcSig.functionSignatureArgumentList.map { parseFunctionArgument(it, context) }
        val returnTypeNode =
            PsiTreeUtil.findChildOfType(
                funcSig.functionSignatureReturnType?.argType,
                LuaCatsType::class.java,
            )
        val returnType = if (returnTypeNode != null) parseType(returnTypeNode, context) else LuaPrimitiveType.VOID
        // A `fun(...)` signature is written by hand, so its arity is a contract (BUG-419).
        return LuaFunctionType(params, returnType, declaredSignature = true)
    }

    private fun parseFunctionArgument(
        arg: LuaCatsFunctionSignatureArgument,
        context: PsiElement,
    ): LuaParameter {
        // A type-only parameter (`fun(table)`) is positional: `_`, which inlay hints already suppress.
        val argName = arg.argName?.text ?: "_"
        val argTypeNode = PsiTreeUtil.findChildOfType(arg.argType, LuaCatsType::class.java)
        val argType = if (argTypeNode != null) parseType(argTypeNode, context) else LuaPrimitiveType.ANY
        // `p?: T` marks the name; `p: T?` admits nil. Either way the argument may be omitted.
        val isOptional =
            hasOptionalMarker(arg) ||
                arg.argType
                    ?.text
                    ?.trimEnd()
                    ?.endsWith("?") == true
        return LuaParameter(argName, argType, isOptional, isVararg = argName == "...")
    }

    /** `?` is a bare leaf; PSI `children` skips leaves, so the AST is the only place it shows. */
    private fun hasOptionalMarker(element: PsiElement): Boolean = element.node.getChildren(null).any { it.text == "?" }
}
