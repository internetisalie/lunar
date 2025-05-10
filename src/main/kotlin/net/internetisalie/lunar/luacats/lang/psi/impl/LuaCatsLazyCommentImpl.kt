package net.internetisalie.lunar.luacats.lang.psi.impl

import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.impl.source.tree.LazyParseablePsiElement
import com.intellij.psi.util.PsiTreeUtil
import net.internetisalie.lunar.lang.psi.LuaLazyElementTypes
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsAliasTag
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsAsyncTag
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsCastTag
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsClassTag
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsComment
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsDeprecatedTag
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsDescription
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsDiagnosticTag
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsEnumTag
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsFieldTag
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsGenericTag
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsMetaTag
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsModuleTag
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsNodiscardTag
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsOperatorTag
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsOverloadTag
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsPackageTag
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsParamTag
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsPrivateTag
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsProtectedTag
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsReturnTag
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsSeeTag
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsSourceTag
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsTypeOption
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsTypeTag
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsVarargTag
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsVersionTag
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsVisitor

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

    override fun getAliasTagList(): MutableList<LuaCatsAliasTag?> {
        return PsiTreeUtil.getChildrenOfTypeAsList<LuaCatsAliasTag?>(this, LuaCatsAliasTag::class.java)
    }

    override fun getAsyncTagList(): MutableList<LuaCatsAsyncTag?> {
        return PsiTreeUtil.getChildrenOfTypeAsList<LuaCatsAsyncTag?>(this, LuaCatsAsyncTag::class.java)
    }

    override fun getCastTagList(): MutableList<LuaCatsCastTag?> {
        return PsiTreeUtil.getChildrenOfTypeAsList<LuaCatsCastTag?>(this, LuaCatsCastTag::class.java)
    }

    override fun getClassTagList(): MutableList<LuaCatsClassTag?> {
        return PsiTreeUtil.getChildrenOfTypeAsList<LuaCatsClassTag?>(this, LuaCatsClassTag::class.java)
    }

    override fun getDeprecatedTagList(): MutableList<LuaCatsDeprecatedTag?> {
        return PsiTreeUtil.getChildrenOfTypeAsList<LuaCatsDeprecatedTag?>(this, LuaCatsDeprecatedTag::class.java)
    }

    override fun getDescriptionList(): MutableList<LuaCatsDescription?> {
        return PsiTreeUtil.getChildrenOfTypeAsList<LuaCatsDescription?>(this, LuaCatsDescription::class.java)
    }

    override fun getDiagnosticTagList(): MutableList<LuaCatsDiagnosticTag?> {
        return PsiTreeUtil.getChildrenOfTypeAsList<LuaCatsDiagnosticTag?>(this, LuaCatsDiagnosticTag::class.java)
    }

    override fun getEnumTagList(): MutableList<LuaCatsEnumTag?> {
        return PsiTreeUtil.getChildrenOfTypeAsList<LuaCatsEnumTag?>(this, LuaCatsEnumTag::class.java)
    }

    override fun getFieldTagList(): MutableList<LuaCatsFieldTag?> {
        return PsiTreeUtil.getChildrenOfTypeAsList<LuaCatsFieldTag?>(this, LuaCatsFieldTag::class.java)
    }

    override fun getGenericTagList(): MutableList<LuaCatsGenericTag?> {
        return PsiTreeUtil.getChildrenOfTypeAsList<LuaCatsGenericTag?>(this, LuaCatsGenericTag::class.java)
    }

    override fun getMetaTagList(): MutableList<LuaCatsMetaTag?> {
        return PsiTreeUtil.getChildrenOfTypeAsList<LuaCatsMetaTag?>(this, LuaCatsMetaTag::class.java)
    }

    override fun getModuleTagList(): MutableList<LuaCatsModuleTag?> {
        return PsiTreeUtil.getChildrenOfTypeAsList<LuaCatsModuleTag?>(this, LuaCatsModuleTag::class.java)
    }

    override fun getNodiscardTagList(): MutableList<LuaCatsNodiscardTag?> {
        return PsiTreeUtil.getChildrenOfTypeAsList<LuaCatsNodiscardTag?>(this, LuaCatsNodiscardTag::class.java)
    }

    override fun getOperatorTagList(): MutableList<LuaCatsOperatorTag?> {
        return PsiTreeUtil.getChildrenOfTypeAsList<LuaCatsOperatorTag?>(this, LuaCatsOperatorTag::class.java)
    }

    override fun getOverloadTagList(): MutableList<LuaCatsOverloadTag?> {
        return PsiTreeUtil.getChildrenOfTypeAsList<LuaCatsOverloadTag?>(this, LuaCatsOverloadTag::class.java)
    }

    override fun getPackageTagList(): MutableList<LuaCatsPackageTag?> {
        return PsiTreeUtil.getChildrenOfTypeAsList<LuaCatsPackageTag?>(this, LuaCatsPackageTag::class.java)
    }

    override fun getParamTagList(): MutableList<LuaCatsParamTag?> {
        return PsiTreeUtil.getChildrenOfTypeAsList<LuaCatsParamTag?>(this, LuaCatsParamTag::class.java)
    }

    override fun getPrivateTagList(): MutableList<LuaCatsPrivateTag?> {
        return PsiTreeUtil.getChildrenOfTypeAsList<LuaCatsPrivateTag?>(this, LuaCatsPrivateTag::class.java)
    }

    override fun getProtectedTagList(): MutableList<LuaCatsProtectedTag?> {
        return PsiTreeUtil.getChildrenOfTypeAsList<LuaCatsProtectedTag?>(this, LuaCatsProtectedTag::class.java)
    }

    override fun getReturnTagList(): MutableList<LuaCatsReturnTag?> {
        return PsiTreeUtil.getChildrenOfTypeAsList<LuaCatsReturnTag?>(this, LuaCatsReturnTag::class.java)
    }

    override fun getSeeTagList(): MutableList<LuaCatsSeeTag?> {
        return PsiTreeUtil.getChildrenOfTypeAsList<LuaCatsSeeTag?>(this, LuaCatsSeeTag::class.java)
    }

    override fun getSourceTagList(): MutableList<LuaCatsSourceTag?> {
        return PsiTreeUtil.getChildrenOfTypeAsList<LuaCatsSourceTag?>(this, LuaCatsSourceTag::class.java)
    }

    override fun getTypeOptionList(): MutableList<LuaCatsTypeOption?> {
        return PsiTreeUtil.getChildrenOfTypeAsList<LuaCatsTypeOption?>(this, LuaCatsTypeOption::class.java)
    }

    override fun getTypeTagList(): MutableList<LuaCatsTypeTag?> {
        return PsiTreeUtil.getChildrenOfTypeAsList<LuaCatsTypeTag?>(this, LuaCatsTypeTag::class.java)
    }

    override fun getVarargTagList(): MutableList<LuaCatsVarargTag?> {
        return PsiTreeUtil.getChildrenOfTypeAsList<LuaCatsVarargTag?>(this, LuaCatsVarargTag::class.java)
    }

    override fun getVersionTagList(): MutableList<LuaCatsVersionTag?> {
        return PsiTreeUtil.getChildrenOfTypeAsList<LuaCatsVersionTag?>(this, LuaCatsVersionTag::class.java)
    }
}