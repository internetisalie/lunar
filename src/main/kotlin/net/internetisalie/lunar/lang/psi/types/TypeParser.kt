package net.internetisalie.lunar.lang.psi.types

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import net.internetisalie.lunar.lang.psi.LuaElementFactory
import net.internetisalie.lunar.lang.psi.LuaLocalVarDecl
import net.internetisalie.lunar.lang.psi.LuaPsiImplUtil
import net.internetisalie.lunar.luacats.lang.psi.*

object TypeParser {
    fun parse(typeString: String, context: PsiElement): LuaType {
        val project = context.project
        val luaFile = LuaElementFactory.createFile(project, "---@type $typeString\nlocal _")
        val varDecl = PsiTreeUtil.findChildOfType(luaFile, LuaLocalVarDecl::class.java)
            ?: return LuaPrimitiveType.UNKNOWN

        val comment = LuaPsiImplUtil.getCatsComment(varDecl)
            ?: return LuaPrimitiveType.UNKNOWN

        val luaCatsType = PsiTreeUtil.findChildOfType(comment, LuaCatsType::class.java)
            ?: return LuaPrimitiveType.UNKNOWN

        return parseType(luaCatsType, context)
    }

    private fun parseType(type: LuaCatsType, context: PsiElement): LuaType {
        return parseUnionType(type.unionType, context)
    }

    private fun parseUnionType(unionType: LuaCatsUnionType, context: PsiElement): LuaType {
        val arrays = unionType.arrayTypeList
        if (arrays.isEmpty()) return LuaPrimitiveType.UNKNOWN

        val types = arrays.map { parseArrayType(it, context) }.toSet()
        if (types.size == 1) return types.first()
        return LuaUnionType(types)
    }

    private fun parseArrayType(arrayType: LuaCatsArrayType, context: PsiElement): LuaType {
        var baseType = parseDistinctType(arrayType.distinctType, context)
        // If it's an array type, text ends with "[]" but the grammar might just have the bracket tokens.
        // The AST structure: arrayType ::= distinctType '[]'?
        // We can check if it has bracket children.
        if (arrayType.text.endsWith("[]")) {
            baseType = LuaArrayType(baseType)
        }
        return baseType
    }

    private fun parseDistinctType(distinctType: LuaCatsDistinctType, context: PsiElement): LuaType {
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
            val typeArgs = it.typeParamList.map { param ->
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
        distinctType.literalTableType?.let { tableLiteral ->
            val members = mutableMapOf<String, LuaTypeMember>()
            tableLiteral.tableLiteralEntryList.forEach { entry ->
                val key = entry.namedType?.text ?: entry.builtinType?.text ?: "unknown"
                val valType = parseType(entry.type, context)
                members[key] = LuaTypeMember(key, valType)
            }
            return LuaTableLiteralType(members)
        }
        distinctType.functionSignatureType?.let { funcSig ->
            val params = funcSig.functionSignatureArgumentList.map { arg ->
                val argName = arg.argName.text
                val argTypeNode = PsiTreeUtil.findChildOfType(arg.argType, LuaCatsType::class.java)
                val argType = if (argTypeNode != null) parseType(argTypeNode, context) else LuaPrimitiveType.ANY
                LuaParameter(argName, argType)
            }
            val returnTypeNode = PsiTreeUtil.findChildOfType(funcSig.functionSignatureReturnType?.argType, LuaCatsType::class.java)
            val returnType = if (returnTypeNode != null) parseType(returnTypeNode, context) else LuaPrimitiveType.VOID
            return LuaFunctionType(params, returnType)
        }
        return LuaPrimitiveType.UNKNOWN
    }
}
