package net.internetisalie.lunar.lang.psi.types

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import net.internetisalie.lunar.lang.psi.LuaElementFactory
import net.internetisalie.lunar.lang.psi.LuaLocalVarDecl
import net.internetisalie.lunar.lang.psi.LuaPsiImplUtil
import net.internetisalie.lunar.luacats.lang.psi.*

object TypeParser {
    /**
     * Parses [typeString]. [typeParameters] maps the `@generic` names in scope to their bound (null =
     * unconstrained); a name in it parses to a [LuaGenericType] at every depth, ahead of any class of
     * the same name. Empty keeps the nominal-only behaviour.
     */
    fun parse(
        typeString: String,
        context: PsiElement,
        typeParameters: Map<String, LuaType?> = emptyMap(),
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

        return parseType(luaCatsType, context, typeParameters)
    }

    /** True when [typeString] spells any of [names] as a whole identifier, wherever it sits. */
    fun mentionsAny(
        typeString: String,
        names: Set<String>,
    ): Boolean = names.isNotEmpty() && typeString.split(TOKEN_SEPARATORS).any { it in names }

    private val TOKEN_SEPARATORS = Regex("[^A-Za-z0-9_.]+")

    private fun parseType(
        type: LuaCatsType,
        context: PsiElement,
        typeParameters: Map<String, LuaType?>,
    ): LuaType = parseUnionType(type.unionType, context, typeParameters)

    private fun parseUnionType(
        unionType: LuaCatsUnionType,
        context: PsiElement,
        typeParameters: Map<String, LuaType?>,
    ): LuaType {
        val arrays = unionType.arrayTypeList
        if (arrays.isEmpty()) {
            // Fallback: try to parse distinct types if arrayTypeList is empty for some reason
            val distinctTypes = PsiTreeUtil.findChildrenOfType(unionType, LuaCatsDistinctType::class.java)
            if (distinctTypes.isEmpty()) return LuaPrimitiveType.UNKNOWN
            val types = distinctTypes.map { parseDistinctType(it, context, typeParameters) }.toSet()
            if (types.size == 1) return types.first()
            return LuaUnionType(types)
        }

        val types = arrays.map { parseArrayType(it, context, typeParameters) }.toSet()
        if (types.size == 1) return types.first()
        return LuaUnionType(types)
    }

    private fun parseArrayType(
        arrayType: LuaCatsArrayType,
        context: PsiElement,
        typeParameters: Map<String, LuaType?>,
    ): LuaType {
        var baseType = parseDistinctType(arrayType.distinctType, context, typeParameters)
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
        typeParameters: Map<String, LuaType?>,
    ): LuaType {
        distinctType.parenthesizedType?.let { return parseUnionType(it.unionType, context, typeParameters) }
        distinctType.builtinType?.let {
            val name = it.text
            return LuaPrimitiveType.PRIMITIVES[name] ?: LuaPrimitiveType.UNKNOWN
        }
        distinctType.namedType?.let { return nameType(it.text, context, typeParameters) }
        distinctType.parameterName?.let { return captureType(it.text, typeParameters) }
        distinctType.parameterizedName?.let { return parseParameterizedName(it, context, typeParameters) }
        distinctType.tupleType?.let {
            // Treat tuple as an array of ANY for simplicity right now
            return LuaArrayType(LuaPrimitiveType.ANY)
        }
        distinctType.dictionaryType?.let { return parseDictionary(it, context, typeParameters) }
        distinctType.literalTableType?.let { return parseTableLiteral(it, context, typeParameters) }
        distinctType.functionSignatureType?.let { return parseFunctionSignature(it, context, typeParameters) }
        distinctType.literalType?.let { return literalValueType(it.text) }
        return LuaPrimitiveType.UNKNOWN
    }

    /** A generic name wins over a class of the same name; anything else is a lazy nominal reference. */
    private fun nameType(
        name: String,
        context: PsiElement,
        typeParameters: Map<String, LuaType?>,
    ): LuaType =
        if (name in typeParameters) {
            LuaGenericType(name, typeParameters[name])
        } else {
            LuaTypeManager.getInstance(context.project).createTypeReference(name, context)
        }

    /** `` `T` `` captures the class a string argument names; only a declared parameter qualifies. */
    private fun captureType(
        text: String,
        typeParameters: Map<String, LuaType?>,
    ): LuaType {
        val name = text.trim('`')
        if (name !in typeParameters) return LuaPrimitiveType.UNKNOWN
        return LuaGenericType(name, typeParameters[name], capture = true)
    }

    /** The head is always a class (`T<string>` is unsupported); only the arguments may be generic. */
    private fun parseParameterizedName(
        node: LuaCatsParameterizedName,
        context: PsiElement,
        typeParameters: Map<String, LuaType?>,
    ): LuaType {
        val baseType = LuaTypeManager.getInstance(context.project).createTypeReference(node.genericType.text, context)
        val typeArgs =
            PsiTreeUtil
                .getChildrenOfAnyType(node, LuaCatsTypeParam::class.java, LuaCatsType::class.java)
                .map { typeArgument(it, context, typeParameters) }
        return LuaParameterizedType(baseType, typeArgs)
    }

    /** A bare-name argument is a [LuaCatsTypeParam]; any other argument is a nested [LuaCatsType]. */
    private fun typeArgument(
        argument: PsiElement,
        context: PsiElement,
        typeParameters: Map<String, LuaType?>,
    ): LuaType =
        if (argument is LuaCatsType) {
            parseType(argument, context, typeParameters)
        } else {
            nameType(argument.text, context, typeParameters)
        }

    private fun parseDictionary(
        dictType: LuaCatsDictionaryType,
        context: PsiElement,
        typeParameters: Map<String, LuaType?>,
    ): LuaType {
        // dictType.typeList usually has [KeyType, ValueType]
        val types = dictType.typeList
        val keyType = if (types.isNotEmpty()) parseType(types[0], context, typeParameters) else LuaPrimitiveType.ANY
        val valueType = if (types.size > 1) parseType(types[1], context, typeParameters) else LuaPrimitiveType.ANY

        // Map<K, V> via parameterized type
        val tableBase = LuaTypeManager.getInstance(context.project).createTypeReference("table", context)
        return LuaParameterizedType(tableBase, listOf(keyType, valueType))
    }

    /** No literal type exists, so `"a"|"b"` widens to the type of its values. */
    private fun literalValueType(literal: String): LuaType =
        when {
            literal == "true" || literal == "false" -> LuaPrimitiveType.BOOLEAN
            literal.first().isDigit() -> LuaPrimitiveType.NUMBER
            else -> LuaPrimitiveType.STRING
        }

    private fun parseTableLiteral(
        tableLiteral: LuaCatsLiteralTableType,
        context: PsiElement,
        typeParameters: Map<String, LuaType?>,
    ): LuaType {
        // A bracket key (`[1]:`) names no member; without the filter it would land under "unknown".
        val namedEntries =
            tableLiteral.tableLiteralEntryList.filter {
                PsiTreeUtil.getChildOfType(it, LuaCatsFieldKeyDescriptor::class.java) == null
            }
        val members =
            namedEntries.associate { entry ->
                val key = entry.namedType?.text ?: entry.builtinType?.text ?: "unknown"
                val valType = parseType(entry.type, context, typeParameters)
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
        typeParameters: Map<String, LuaType?>,
    ): LuaType {
        val params = funcSig.functionSignatureArgumentList.map { parseFunctionArgument(it, context, typeParameters) }
        val returnTypeNode =
            PsiTreeUtil.findChildOfType(
                funcSig.functionSignatureReturnType?.argType,
                LuaCatsType::class.java,
            )
        val returnType = returnTypeNode?.let { parseType(it, context, typeParameters) } ?: LuaPrimitiveType.VOID
        // A `fun(...)` signature is written by hand, so its arity is a contract (BUG-419).
        return LuaFunctionType(params, returnType, declaredSignature = true)
    }

    private fun parseFunctionArgument(
        arg: LuaCatsFunctionSignatureArgument,
        context: PsiElement,
        typeParameters: Map<String, LuaType?>,
    ): LuaParameter {
        // A type-only parameter (`fun(table)`) is positional: `_`, which inlay hints already suppress.
        val argName = arg.argName?.text ?: "_"
        val argTypeNode = PsiTreeUtil.findChildOfType(arg.argType, LuaCatsType::class.java)
        val argType = if (argTypeNode != null) parseType(argTypeNode, context, typeParameters) else LuaPrimitiveType.ANY
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
