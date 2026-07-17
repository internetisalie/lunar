package net.internetisalie.lunar.lang.syntax

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiElement
import net.internetisalie.lunar.lang.psi.*
import net.internetisalie.lunar.lang.psi.types.LuaGraphType
import net.internetisalie.lunar.lang.psi.types.LuaTypes
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot

/**
 * Applies type-derived [TextAttributesKey]s to [LuaNameRef] identifiers in call, class-ref,
 * and member positions, layering over the base scope coloring.
 *
 * Satisfies SYNTAX-17-01 (call-site coloring), SYNTAX-17-02 (class refs), SYNTAX-17-03
 * (field vs method distinction), and SYNTAX-17-04 (runs on the platform's background
 * highlighting pass; guarded by [DumbService.isDumb] while indexes rebuild).
 */
class LuaInferredTypeAnnotator : Annotator {

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element !is LuaNameRef) return
        if (DumbService.isDumb(element.project)) return
        val key = classify(element) ?: return
        holder.newSilentAnnotation(HighlightSeverity.TEXT_ATTRIBUTES)
            .range(element.identifier)
            .textAttributes(key)
            .create()
    }

    private fun classify(ref: LuaNameRef): TextAttributesKey? {
        val snap = LuaTypesSnapshot.forFile(ref.containingFile)
        val gt = snap.getValueType(ref)
        return classifyMember(ref, snap)
            ?: classifyCall(ref, gt)
            ?: classifyClassRef(ref, gt)
    }

    /** Step 2: member name in `t.field` or `t:method()` → INFERRED_FIELD or INFERRED_METHOD. */
    private fun classifyMember(ref: LuaNameRef, snap: LuaTypes): TextAttributesKey? {
        val recv = receiverOf(ref) ?: return null
        val recvType = snap.getValueType(recv)
        val memberWrite = recvType.getMembers()[ref.text]?.write ?: return null
        return if (memberWrite is LuaGraphType.Function) LuaHighlight.INFERRED_METHOD
        else LuaHighlight.INFERRED_FIELD
    }

    /** Step 3: ref in callee position whose inferred type is Function → INFERRED_LOCAL/GLOBAL_CALL. */
    private fun classifyCall(ref: LuaNameRef, gt: LuaGraphType): TextAttributesKey? {
        if (gt !is LuaGraphType.Function) return null
        if (!isCalleePosition(ref)) return null
        val target = ref.reference?.resolve()
        return if (isLocalTarget(target)) LuaHighlight.INFERRED_LOCAL_CALL
        else LuaHighlight.INFERRED_GLOBAL_CALL
    }

    /** Step 4: ref whose inferred type is a named Table (or Union with named Table) that matches ref.text → INFERRED_CLASS. */
    private fun classifyClassRef(ref: LuaNameRef, gt: LuaGraphType): TextAttributesKey? {
        val className = extractClassName(gt) ?: return null
        return if (className == ref.text) LuaHighlight.INFERRED_CLASS else null
    }

    /**
     * Returns the receiver PSI element for a member-access ref, or null if [ref] is
     * not a member name.  Covers `t.field` (LuaIndexExpr) and `t:method()` (LuaMethodExpr).
     */
    private fun receiverOf(ref: LuaNameRef): PsiElement? {
        return when (val parent = ref.parent) {
            is LuaIndexExpr -> {
                val varSuffix = parent.parent as? LuaVarSuffix ?: return null
                val luaVar = varSuffix.parent as? LuaVar ?: return null
                luaVar.nameRef
            }
            is LuaMethodExpr -> {
                val nameAndArgs = parent.parent as? LuaNameAndArgs ?: return null
                val funcCall = nameAndArgs.parent as? LuaFuncCall ?: return null
                funcCall.varOrExp.`var`?.nameRef
            }
            else -> null
        }
    }

    /**
     * Returns true when [ref] occupies the direct callee slot of a [LuaFuncCall] with no
     * `.`/`:` suffix between it and the argument list — i.e. `x()` but not `t.x()`.
     */
    private fun isCalleePosition(ref: LuaNameRef): Boolean {
        val luaVar = ref.parent as? LuaVar ?: return false
        if (luaVar.varSuffixList.isNotEmpty()) return false
        val varOrExp = luaVar.parent as? LuaVarOrExp ?: return false
        return varOrExp.parent is LuaFuncCall
    }

    /**
     * Returns true when [target] (the IDENTIFIER leaf returned by resolve()) is a local,
     * parameter, or loop-variable declaration — not a global assignment or stub-index match.
     */
    private fun isLocalTarget(target: PsiElement?): Boolean {
        target ?: return false
        val targetParent = target.parent
        if (targetParent is LuaNumericForStatement) return true
        if (targetParent !is LuaNameRef) return false
        return when (val declNode = targetParent.parent) {
            is LuaAttName -> true
            is LuaLocalFuncDecl -> true
            is LuaNameList -> declNode.parent is LuaParList || declNode.parent is LuaGenericForStatement
            else -> false
        }
    }

    /**
     * Extracts a non-null className from a [LuaGraphType], recursing into [LuaGraphType.Union]
     * members.  A `@class` annotation yields a Union containing the named Table — per AGENTS.md,
     * never use Union.displayName() for the class name.
     */
    private fun extractClassName(gt: LuaGraphType): String? = when (gt) {
        is LuaGraphType.Table -> gt.className
        is LuaGraphType.Union -> gt.types.firstNotNullOfOrNull { extractClassName(it) }
        else -> null
    }
}
