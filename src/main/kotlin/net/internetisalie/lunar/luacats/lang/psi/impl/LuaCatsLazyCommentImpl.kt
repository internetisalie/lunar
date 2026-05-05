package net.internetisalie.lunar.luacats.lang.psi.impl

import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.impl.source.tree.LazyParseablePsiElement
import com.intellij.psi.util.PsiTreeUtil
import net.internetisalie.lunar.lang.psi.LuaLazyElementTypes
import net.internetisalie.lunar.luacats.lang.psi.*

/**
 * @author ilyas
 */
class LuaCatsLazyCommentImpl(text: CharSequence?) : LazyParseablePsiElement(LuaLazyElementTypes.LUACATS_COMMENT, text),
    LuaCatsComment {
    override fun toString(): String {
        return "LuaCatsLazyCommentImpl(" + node.elementType + ")"
    }

    fun accept(visitor: LuaCatsVisitor) {
        visitor.visitComment(this)
    }

    override fun accept(visitor: PsiElementVisitor) {
        if (visitor is LuaCatsVisitor) accept(visitor)
        else super.accept(visitor)
    }

    override fun getAliasTagList(): List<LuaCatsAliasTag> {
        return PsiTreeUtil.findChildrenOfType(this, LuaCatsAliasTag::class.java).toList()
    }

    override fun getAsyncTagList(): List<LuaCatsAsyncTag> {
        return PsiTreeUtil.findChildrenOfType(this, LuaCatsAsyncTag::class.java).toList()
    }

    override fun getCastTagList(): List<LuaCatsCastTag> {
        return PsiTreeUtil.findChildrenOfType(this, LuaCatsCastTag::class.java).toList()
    }

    override fun getClassTagList(): List<LuaCatsClassTag> {
        return PsiTreeUtil.findChildrenOfType(this, LuaCatsClassTag::class.java).toList()
    }

    override fun getDeprecatedTagList(): List<LuaCatsDeprecatedTag> {
        return PsiTreeUtil.findChildrenOfType(this, LuaCatsDeprecatedTag::class.java).toList()
    }

    override fun getDescriptionList(): List<LuaCatsDescription> {
        return PsiTreeUtil.findChildrenOfType(this, LuaCatsDescription::class.java).toList()
    }

    override fun getDiagnosticTagList(): List<LuaCatsDiagnosticTag> {
        return PsiTreeUtil.findChildrenOfType(this, LuaCatsDiagnosticTag::class.java).toList()
    }

    override fun getEnumTagList(): List<LuaCatsEnumTag> {
        return PsiTreeUtil.findChildrenOfType(this, LuaCatsEnumTag::class.java).toList()
    }

    override fun getFieldTagList(): List<LuaCatsFieldTag> {
        return PsiTreeUtil.findChildrenOfType(this, LuaCatsFieldTag::class.java).toList()
    }

    override fun getGenericTagList(): List<LuaCatsGenericTag> {
        return PsiTreeUtil.findChildrenOfType(this, LuaCatsGenericTag::class.java).toList()
    }

    override fun getMetaTagList(): List<LuaCatsMetaTag> {
        return PsiTreeUtil.findChildrenOfType(this, LuaCatsMetaTag::class.java).toList()
    }

    override fun getModuleTagList(): List<LuaCatsModuleTag> {
        return PsiTreeUtil.findChildrenOfType(this, LuaCatsModuleTag::class.java).toList()
    }

    override fun getNodiscardTagList(): List<LuaCatsNodiscardTag> {
        return PsiTreeUtil.findChildrenOfType(this, LuaCatsNodiscardTag::class.java).toList()
    }

    override fun getOperatorTagList(): List<LuaCatsOperatorTag> {
        return PsiTreeUtil.findChildrenOfType(this, LuaCatsOperatorTag::class.java).toList()
    }

    override fun getOverloadTagList(): List<LuaCatsOverloadTag> {
        return PsiTreeUtil.findChildrenOfType(this, LuaCatsOverloadTag::class.java).toList()
    }

    override fun getPackageTagList(): List<LuaCatsPackageTag> {
        return PsiTreeUtil.findChildrenOfType(this, LuaCatsPackageTag::class.java).toList()
    }

    override fun getParamTagList(): List<LuaCatsParamTag> {
        return PsiTreeUtil.findChildrenOfType(this, LuaCatsParamTag::class.java).toList()
    }

    override fun getPrivateTagList(): List<LuaCatsPrivateTag> {
        return PsiTreeUtil.findChildrenOfType(this, LuaCatsPrivateTag::class.java).toList()
    }

    override fun getProtectedTagList(): List<LuaCatsProtectedTag> {
        return PsiTreeUtil.findChildrenOfType(this, LuaCatsProtectedTag::class.java).toList()
    }

    override fun getReturnTagList(): List<LuaCatsReturnTag> {
        return PsiTreeUtil.findChildrenOfType(this, LuaCatsReturnTag::class.java).toList()
    }

    override fun getSeeTagList(): List<LuaCatsSeeTag> {
        return PsiTreeUtil.findChildrenOfType(this, LuaCatsSeeTag::class.java).toList()
    }

    override fun getSourceTagList(): List<LuaCatsSourceTag> {
        return PsiTreeUtil.findChildrenOfType(this, LuaCatsSourceTag::class.java).toList()
    }

    override fun getTypeOptionList(): List<LuaCatsTypeOption> {
        return PsiTreeUtil.findChildrenOfType(this, LuaCatsTypeOption::class.java).toList()
    }

    override fun getTypeTagList(): List<LuaCatsTypeTag> {
        return PsiTreeUtil.findChildrenOfType(this, LuaCatsTypeTag::class.java).toList()
    }

    override fun getVarargTagList(): List<LuaCatsVarargTag> {
        return PsiTreeUtil.findChildrenOfType(this, LuaCatsVarargTag::class.java).toList()
    }

    override fun getVersionTagList(): List<LuaCatsVersionTag> {
        return PsiTreeUtil.findChildrenOfType(this, LuaCatsVersionTag::class.java).toList()
    }
}
