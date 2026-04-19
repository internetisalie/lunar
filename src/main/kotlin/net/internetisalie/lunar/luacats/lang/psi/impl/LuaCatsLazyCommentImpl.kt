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

    override fun getAliasTagList(): List<LuaCatsAliasTag?> {
        return PsiTreeUtil.getChildrenOfTypeAsList(this, LuaCatsAliasTag::class.java)
    }

    override fun getAsyncTagList(): List<LuaCatsAsyncTag?> {
        return PsiTreeUtil.getChildrenOfTypeAsList(this, LuaCatsAsyncTag::class.java)
    }

    override fun getCastTagList(): List<LuaCatsCastTag?> {
        return PsiTreeUtil.getChildrenOfTypeAsList(this, LuaCatsCastTag::class.java)
    }

    override fun getClassTagList(): List<LuaCatsClassTag?> {
        return PsiTreeUtil.getChildrenOfTypeAsList(this, LuaCatsClassTag::class.java)
    }

    override fun getDeprecatedTagList(): List<LuaCatsDeprecatedTag?> {
        return PsiTreeUtil.getChildrenOfTypeAsList(this, LuaCatsDeprecatedTag::class.java)
    }

    override fun getDescriptionList(): List<LuaCatsDescription?> {
        return PsiTreeUtil.getChildrenOfTypeAsList(this, LuaCatsDescription::class.java)
    }

    override fun getDiagnosticTagList(): List<LuaCatsDiagnosticTag?> {
        return PsiTreeUtil.getChildrenOfTypeAsList(this, LuaCatsDiagnosticTag::class.java)
    }

    override fun getEnumTagList(): List<LuaCatsEnumTag?> {
        return PsiTreeUtil.getChildrenOfTypeAsList(this, LuaCatsEnumTag::class.java)
    }

    override fun getFieldTagList(): List<LuaCatsFieldTag?> {
        return PsiTreeUtil.getChildrenOfTypeAsList(this, LuaCatsFieldTag::class.java)
    }

    override fun getGenericTagList(): List<LuaCatsGenericTag?> {
        return PsiTreeUtil.getChildrenOfTypeAsList(this, LuaCatsGenericTag::class.java)
    }

    override fun getMetaTagList(): List<LuaCatsMetaTag?> {
        return PsiTreeUtil.getChildrenOfTypeAsList(this, LuaCatsMetaTag::class.java)
    }

    override fun getModuleTagList(): List<LuaCatsModuleTag?> {
        return PsiTreeUtil.getChildrenOfTypeAsList(this, LuaCatsModuleTag::class.java)
    }

    override fun getNodiscardTagList(): List<LuaCatsNodiscardTag?> {
        return PsiTreeUtil.getChildrenOfTypeAsList(this, LuaCatsNodiscardTag::class.java)
    }

    override fun getOperatorTagList(): List<LuaCatsOperatorTag?> {
        return PsiTreeUtil.getChildrenOfTypeAsList(this, LuaCatsOperatorTag::class.java)
    }

    override fun getOverloadTagList(): List<LuaCatsOverloadTag?> {
        return PsiTreeUtil.getChildrenOfTypeAsList(this, LuaCatsOverloadTag::class.java)
    }

    override fun getPackageTagList(): List<LuaCatsPackageTag?> {
        return PsiTreeUtil.getChildrenOfTypeAsList(this, LuaCatsPackageTag::class.java)
    }

    override fun getParamTagList(): List<LuaCatsParamTag?> {
        return PsiTreeUtil.getChildrenOfTypeAsList(this, LuaCatsParamTag::class.java)
    }

    override fun getPrivateTagList(): List<LuaCatsPrivateTag?> {
        return PsiTreeUtil.getChildrenOfTypeAsList(this, LuaCatsPrivateTag::class.java)
    }

    override fun getProtectedTagList(): List<LuaCatsProtectedTag?> {
        return PsiTreeUtil.getChildrenOfTypeAsList(this, LuaCatsProtectedTag::class.java)
    }

    override fun getReturnTagList(): List<LuaCatsReturnTag?> {
        return PsiTreeUtil.getChildrenOfTypeAsList(this, LuaCatsReturnTag::class.java)
    }

    override fun getSeeTagList(): List<LuaCatsSeeTag?> {
        return PsiTreeUtil.getChildrenOfTypeAsList(this, LuaCatsSeeTag::class.java)
    }

    override fun getSourceTagList(): List<LuaCatsSourceTag?> {
        return PsiTreeUtil.getChildrenOfTypeAsList(this, LuaCatsSourceTag::class.java)
    }

    override fun getTypeOptionList(): List<LuaCatsTypeOption?> {
        return PsiTreeUtil.getChildrenOfTypeAsList(this, LuaCatsTypeOption::class.java)
    }

    override fun getTypeTagList(): List<LuaCatsTypeTag?> {
        return PsiTreeUtil.getChildrenOfTypeAsList(this, LuaCatsTypeTag::class.java)
    }

    override fun getVarargTagList(): List<LuaCatsVarargTag?> {
        return PsiTreeUtil.getChildrenOfTypeAsList(this, LuaCatsVarargTag::class.java)
    }

    override fun getVersionTagList(): List<LuaCatsVersionTag?> {
        return PsiTreeUtil.getChildrenOfTypeAsList(this, LuaCatsVersionTag::class.java)
    }
}