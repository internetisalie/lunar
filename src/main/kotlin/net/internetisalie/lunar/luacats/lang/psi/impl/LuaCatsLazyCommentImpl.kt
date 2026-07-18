package net.internetisalie.lunar.luacats.lang.psi.impl

import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.impl.source.tree.LazyParseablePsiElement
import com.intellij.psi.util.PsiTreeUtil
import net.internetisalie.lunar.lang.psi.LuaLazyElementTypes
import net.internetisalie.lunar.luacats.lang.psi.*

/**
 * @author ilyas
 *
 * The lazy-parseable chameleon node whose single content child is the inner `comment`
 * ([LuaCatsComment], generated as [LuaCatsCommentImpl]) produced by `parseContents`
 * ([net.internetisalie.lunar.lang.psi.LuaLazyElementTypes.LUACATS_COMMENT]). Because the tags and
 * top-level descriptions are direct children of that inner comment (bnf `private commentLine`),
 * every accessor delegates to it. This restores the generated direct-children contract: a
 * description nested inside a tag (`classTag ::= … description?`) no longer leaks into
 * [getDescriptionList], fixing the duplicate-summary and `isDocCommentEmpty` skew (MAINT-27 #38).
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

    private fun innerComment(): LuaCatsComment? =
        PsiTreeUtil.getChildOfType(this, LuaCatsComment::class.java)

    private fun <T> delegate(select: (LuaCatsComment) -> List<T>): List<T> =
        innerComment()?.let(select) ?: emptyList()

    override fun getAliasTagList(): List<LuaCatsAliasTag> = delegate { it.aliasTagList }

    override fun getAsyncTagList(): List<LuaCatsAsyncTag> = delegate { it.asyncTagList }

    override fun getCastTagList(): List<LuaCatsCastTag> = delegate { it.castTagList }

    override fun getClassTagList(): List<LuaCatsClassTag> = delegate { it.classTagList }

    override fun getDeprecatedTagList(): List<LuaCatsDeprecatedTag> = delegate { it.deprecatedTagList }

    override fun getDescriptionList(): List<LuaCatsDescription> = delegate { it.descriptionList }

    override fun getDiagnosticTagList(): List<LuaCatsDiagnosticTag> = delegate { it.diagnosticTagList }

    override fun getEnumTagList(): List<LuaCatsEnumTag> = delegate { it.enumTagList }

    override fun getFieldTagList(): List<LuaCatsFieldTag> = delegate { it.fieldTagList }

    override fun getGenericTagList(): List<LuaCatsGenericTag> = delegate { it.genericTagList }

    override fun getMetaTagList(): List<LuaCatsMetaTag> = delegate { it.metaTagList }

    override fun getModuleTagList(): List<LuaCatsModuleTag> = delegate { it.moduleTagList }

    override fun getNodiscardTagList(): List<LuaCatsNodiscardTag> = delegate { it.nodiscardTagList }

    override fun getOperatorTagList(): List<LuaCatsOperatorTag> = delegate { it.operatorTagList }

    override fun getOverloadTagList(): List<LuaCatsOverloadTag> = delegate { it.overloadTagList }

    override fun getPackageTagList(): List<LuaCatsPackageTag> = delegate { it.packageTagList }

    override fun getParamTagList(): List<LuaCatsParamTag> = delegate { it.paramTagList }

    override fun getPrivateTagList(): List<LuaCatsPrivateTag> = delegate { it.privateTagList }

    override fun getProtectedTagList(): List<LuaCatsProtectedTag> = delegate { it.protectedTagList }

    override fun getReturnTagList(): List<LuaCatsReturnTag> = delegate { it.returnTagList }

    override fun getSeeTagList(): List<LuaCatsSeeTag> = delegate { it.seeTagList }

    override fun getSourceTagList(): List<LuaCatsSourceTag> = delegate { it.sourceTagList }

    override fun getTypeOptionList(): List<LuaCatsTypeOption> = delegate { it.typeOptionList }

    override fun getTypeTagList(): List<LuaCatsTypeTag> = delegate { it.typeTagList }

    override fun getVarargTagList(): List<LuaCatsVarargTag> = delegate { it.varargTagList }

    override fun getVersionTagList(): List<LuaCatsVersionTag> = delegate { it.versionTagList }
}
