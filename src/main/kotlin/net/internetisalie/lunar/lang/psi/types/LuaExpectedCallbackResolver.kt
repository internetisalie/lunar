package net.internetisalie.lunar.lang.psi.types

import com.intellij.psi.PsiElement
import net.internetisalie.lunar.lang.psi.LuaFile
import net.internetisalie.lunar.lang.psi.LuaFuncCall
import net.internetisalie.lunar.lang.psi.LuaFuncDecl
import net.internetisalie.lunar.lang.psi.LuaLocalFuncDecl
import net.internetisalie.lunar.lang.psi.LuaNameRef
import net.internetisalie.lunar.lang.psi.LuaVar

/**
 * TYPE-10 §2.2/§3.2: resolves, for a lambda-bearing call, the callee's declared
 * [LuaFunctionType] and its per-slot callback type. Mirrors the proven resolution used by
 * `LuaParameterInlayHintsProvider.resolveStandardCall` / `resolveMethodCall`, reaching both
 * LuaCATS-annotated local callees and bundled external stubs (`redis.register_function`,
 * `table.sort`) via reference resolution against the declaration file's snapshot.
 *
 * Carries only the [LuaFuncCall] and the already-unwrapped callee — no
 * `Project`/`Editor`/`PsiFile` field is retained (memory rule).
 */
internal class LuaExpectedCallbackResolver(
    private val call: LuaFuncCall,
    private val calleeUnwrapped: PsiElement?,
) {
    /** The callee's declared function type, or null. Memoize in one `val` at the call site. */
    fun resolveCalleeType(): LuaFunctionType? {
        val callee = calleeUnwrapped ?: return null
        val nameAndArgs = call.nameAndArgsList.firstOrNull() ?: return null
        val methodExpr = nameAndArgs.methodExpr
        return if (methodExpr != null) {
            resolveMethodCalleeType(methodExpr.nameRef)
        } else {
            resolveStandardCalleeType(callee)
        }
    }

    /** Expected callback type for positional arg [index], or null if that slot is not a `fun(...)`. */
    fun expectedCallbackAt(index: Int, calleeType: LuaFunctionType, selfOffset: Int): LuaFunctionType? {
        val param = calleeType.params.getOrNull(index + selfOffset) ?: return null
        return extractFunctionType(param.type)
    }

    private fun resolveMethodCalleeType(nameRef: LuaNameRef): LuaFunctionType? {
        val resolved = nameRef.reference?.resolve() ?: return null
        val funcDecl = resolved.parent?.parent as? LuaFuncDecl ?: return null
        return functionTypeOf(funcDecl)
    }

    private fun resolveStandardCalleeType(callee: PsiElement): LuaFunctionType? {
        val types = LuaTypesSnapshot.forFile(call.containingFile)
        val direct = extractFunctionType(types.graphTypeToLuaType(types.getValueType(callee)))
        if (direct != null) return direct

        val decl = resolveCalleeDeclaration(callee) ?: return null
        return functionTypeOf(decl)
    }

    private fun resolveCalleeDeclaration(callee: PsiElement): PsiElement? {
        val nameRef = calleeNameRef(callee) ?: return null
        val resolved = nameRef.reference?.resolve() ?: return null
        return resolved.parent as? LuaLocalFuncDecl
            ?: resolved as? LuaFuncDecl
            ?: resolved.parent?.parent as? LuaFuncDecl
    }

    private fun calleeNameRef(callee: PsiElement): LuaNameRef? = when (callee) {
        is LuaNameRef -> callee
        is LuaVar -> callee.varSuffixList.lastOrNull()?.indexExpr?.nameRef ?: callee.nameRef
        else -> null
    }

    private fun functionTypeOf(decl: PsiElement): LuaFunctionType? {
        val declFile = decl.containingFile as? LuaFile ?: return null
        val declTypes = LuaTypesSnapshot.forFile(declFile)
        return extractFunctionType(declTypes.graphTypeToLuaType(declTypes.getValueType(decl)))
    }

    private fun extractFunctionType(type: LuaType): LuaFunctionType? {
        if (type is LuaFunctionType) return type
        if (type is LuaUnionType) {
            for (variant in type.types) {
                val funcType = extractFunctionType(variant)
                if (funcType != null) return funcType
            }
        }
        return null
    }
}
