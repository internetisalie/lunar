package net.internetisalie.lunar.analysis.inspections

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiPolyVariantReference
import com.intellij.psi.util.PsiTreeUtil
import net.internetisalie.lunar.lang.psi.*
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsCommentOwner
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsDeprecatedTag

/**
 * Inspection to flag references to deprecated APIs (INSP-08).
 */
class LuaDeprecatedApiInspection : LocalInspectionTool() {
    override fun getShortName(): String = "LuaDeprecatedApi"

    override fun getGroupDisplayName(): String = "Lua"

    override fun getDisplayName(): String = "Deprecated API"

    override fun isEnabledByDefault(): Boolean = true

    override fun getDefaultLevel(): HighlightDisplayLevel = HighlightDisplayLevel.WARNING

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): PsiElementVisitor =
        object : LuaVisitor() {
            override fun visitNameRef(o: LuaNameRef) {
                if (isDeclaration(o)) return
                val reference = o.reference as? PsiPolyVariantReference ?: return
                val resolveResults = reference.multiResolve(false)
                for (res in resolveResults) {
                    val element = res.element ?: continue
                    if (element == o || element == o.identifier) continue

                    val deprecatedTag = getDeprecatedTag(element, o)
                    if (deprecatedTag != null) {
                        val desc = deprecatedTag.description?.text?.trim()
                        val message =
                            if (!desc.isNullOrEmpty()) {
                                "Deprecated API: $desc"
                            } else {
                                "Deprecated API"
                            }
                        holder.registerProblem(
                            o,
                            message,
                            ProblemHighlightType.LIKE_DEPRECATED,
                        )
                        break
                    }
                }
            }
        }

    private fun isDeclaration(ref: LuaNameRef): Boolean {
        val parent = ref.parent ?: return false
        if (parent is LuaLocalFuncDecl) return true
        if (parent is LuaAttName) return true
        if (parent is LuaFuncName) return true
        if (parent is LuaFuncNameProperty || parent is LuaFuncNameMethod) return true
        if (parent is LuaNameList) {
            val owner = parent.parent
            if (owner is LuaParList || owner is LuaGenericForStatement) return true
        }
        return false
    }

    /**
     * `x` in `t.x` / `t:x()` is a member access; `t`, `self`, a parameter or a plain call `f()` is bare.
     * Function-name segments never reach here: [isDeclaration] skips them.
     */
    private fun isMemberAccess(ref: LuaNameRef): Boolean {
        val parent = ref.parent
        return parent is LuaIndexExpr || parent is LuaMethodExpr
    }

    private fun isMemberFunction(funcName: LuaFuncName): Boolean =
        funcName.funcNameMethod != null || funcName.funcNamePropertyList.isNotEmpty()

    /**
     * A dotted or colon function is reachable only as a member (`t.f`, `t:f`), so a bare reference —
     * the root `t`, an implicit `self`, a parameter, a name bound through a required file — never names
     * it. This assumes `_G` / `_ENV` are not modelled: in Lua, `function _G.f()` IS reachable as a bare
     * `f`, but resolution does not alias `_G`, so a bare `f` never resolves to it today. If `_G`
     * aliasing is ever modelled, this rule must exempt `_G.` / `_ENV.` declarations.
     */
    private fun isBareReferenceToAMemberFunction(
        reference: LuaNameRef,
        decl: LuaFuncDecl,
    ): Boolean = !isMemberAccess(reference) && isMemberFunction(decl.funcName)

    /**
     * True when [reference] does not name the function [owner] declares, whatever it resolved to.
     *
     * Resolution can land on a declaration the reference does not name: a root multi-resolves to every
     * `function <root>.<x>`, `self` to its method's name leaf, `t[1].x` (no qualified key) to a root
     * occurrence. So the deprecation is applied only when the reference's own name IS the declared name
     * (the segment [LuaDeclarationSite.functionNameLeafOf] returns), and — for the one case equal names
     * cannot tell apart, a bare name against a dotted or colon function — when its shape can name it.
     */
    private fun isNotTheDeclaredFunction(
        owner: LuaFuncDecl,
        reference: LuaNameRef,
    ): Boolean {
        if (LuaDeclarationSite.functionNameLeafOf(owner.funcName).text != reference.text) return true
        return isBareReferenceToAMemberFunction(reference, owner)
    }

    private fun getDeprecatedTag(
        resolved: PsiElement,
        reference: LuaNameRef,
    ): LuaCatsDeprecatedTag? {
        if (resolved is LuaFuncDecl && isNotTheDeclaredFunction(resolved, reference)) return null
        if (resolved is LuaCatsCommentOwner) {
            val tag = resolved.catsComment?.deprecatedTagList?.firstOrNull()
            if (tag != null) return tag
        }
        val commentOwner = PsiTreeUtil.getParentOfType(resolved, LuaCatsCommentOwner::class.java) ?: return null
        if (!isTheDeclaredName(commentOwner, resolved, reference)) return null
        return commentOwner.catsComment?.deprecatedTagList?.firstOrNull()
    }

    /**
     * True when [resolved] IS a name [owner] declares — identity, not containment. A leaf merely inside
     * a deprecated declaration (a parameter, a field written in its body) is not that declaration.
     * For a `function`, the reference must also name it ([isNotTheDeclaredFunction]): `self` resolves
     * to its method's own name leaf, so identity alone would pass it.
     */
    private fun isTheDeclaredName(
        owner: LuaCatsCommentOwner,
        resolved: PsiElement,
        reference: LuaNameRef,
    ): Boolean =
        when (owner) {
            is LuaFuncDecl ->
                resolved === LuaDeclarationSite.functionNameLeafOf(owner.funcName) &&
                    !isNotTheDeclaredFunction(owner, reference)
            is LuaLocalFuncDecl -> resolved === owner.nameRef.identifier
            is LuaGlobalFuncDecl -> resolved === owner.nameRef?.identifier
            is LuaLocalVarDecl -> resolved === owner || owner.attNameList.any { it.nameRef.identifier === resolved }
            is LuaGlobalVarDecl -> owner.attNameList.any { it.nameRef.identifier === resolved }
            else -> true
        }
}
