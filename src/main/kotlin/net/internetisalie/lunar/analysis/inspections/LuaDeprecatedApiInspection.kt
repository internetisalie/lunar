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

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : LuaVisitor() {
            override fun visitNameRef(o: LuaNameRef) {
                if (isDeclaration(o)) return
                val reference = o.reference as? PsiPolyVariantReference ?: return
                val resolveResults = reference.multiResolve(false)
                for (res in resolveResults) {
                    val element = res.element ?: continue
                    if (element == o || element == o.identifier) continue

                    val deprecatedTag = getDeprecatedTag(element)
                    if (deprecatedTag != null) {
                        val desc = deprecatedTag.description?.text?.trim()
                        val message = if (!desc.isNullOrEmpty()) {
                            "Deprecated API: $desc"
                        } else {
                            "Deprecated API"
                        }
                        holder.registerProblem(
                            o,
                            message,
                            ProblemHighlightType.LIKE_DEPRECATED
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
        if (parent is LuaNameList) {
            val owner = parent.parent
            if (owner is LuaParList || owner is LuaGenericForStatement) return true
        }
        return false
    }

    private fun getDeprecatedTag(resolved: PsiElement): LuaCatsDeprecatedTag? {
        if (resolved is LuaCatsCommentOwner) {
            val tag = resolved.catsComment?.deprecatedTagList?.firstOrNull()
            if (tag != null) return tag
        }
        val commentOwner = PsiTreeUtil.getParentOfType(resolved, LuaCatsCommentOwner::class.java)
        if (commentOwner != null) {
            if (commentOwner is LuaFuncDecl) {
                val funcNameIdentifier = commentOwner.funcName.nameRef.identifier
                if (funcNameIdentifier != resolved && funcNameIdentifier.text != resolved.text) {
                    return null
                }
            }
            if (commentOwner is LuaLocalFuncDecl) {
                val localFuncNameIdentifier = commentOwner.nameRef.identifier
                if (localFuncNameIdentifier != resolved && localFuncNameIdentifier.text != resolved.text) {
                    return null
                }
            }
            if (commentOwner is LuaLocalVarDecl) {
                val isDeclaredVar = commentOwner.attNameList.any { 
                    it.nameRef.identifier == resolved || it.nameRef.identifier.text == resolved.text 
                }
                if (!isDeclaredVar && resolved != commentOwner) {
                    return null
                }
            }
            return commentOwner.catsComment?.deprecatedTagList?.firstOrNull()
        }
        return null
    }
}
