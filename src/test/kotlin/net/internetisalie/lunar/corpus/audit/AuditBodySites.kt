package net.internetisalie.lunar.corpus.audit

import com.intellij.openapi.util.TextRange
import com.intellij.psi.util.PsiTreeUtil
import net.internetisalie.lunar.lang.psi.LuaAssignmentStatement
import net.internetisalie.lunar.lang.psi.LuaCommentOwner
import net.internetisalie.lunar.lang.psi.LuaFinalStatement
import net.internetisalie.lunar.lang.psi.LuaFuncDecl
import net.internetisalie.lunar.lang.psi.LuaFuncDef
import net.internetisalie.lunar.lang.psi.LuaLocalFuncDecl
import net.internetisalie.lunar.lang.psi.LuaVar
import net.internetisalie.lunar.lang.psi.types.LuaClassType
import net.internetisalie.lunar.lang.psi.types.LuaType
import net.internetisalie.lunar.lang.psi.types.LuaTypes
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot
import net.internetisalie.lunar.lang.psi.types.LuaUnionType
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsComment
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsFieldTag

private data class FieldLookup(
    val tag: LuaCatsFieldTag?,
)

internal data class FieldScan(
    val sites: List<AuditSite>,
    val untagged: Int,
)

/** Design §3.2 "Returns" and "Fields": sites that are not call arguments. */
internal object AuditBodySites {
    fun returnSites(scope: ScanScope): List<AuditSite> {
        val decls =
            PsiTreeUtil.findChildrenOfType(scope.file, LuaFuncDecl::class.java) +
                PsiTreeUtil.findChildrenOfType(scope.file, LuaLocalFuncDecl::class.java)
        return decls.flatMap { decl -> declReturnSites(decl, scope) }
    }

    fun fieldSites(scope: ScanScope): FieldScan {
        val types = LuaTypesSnapshot.forFile(scope.file)
        var untagged = 0
        val sites =
            PsiTreeUtil.findChildrenOfType(scope.file, LuaAssignmentStatement::class.java).mapNotNull { assignment ->
                val target = fieldTarget(assignment) ?: return@mapNotNull null
                val lookup = fieldLookup(types, target) ?: return@mapNotNull null
                if (lookup.tag == null) untagged++
                lookup.tag?.let { fieldSite(assignment, it, scope) }
            }
        return FieldScan(sites, untagged)
    }

    private fun declReturnSites(
        decl: LuaCommentOwner,
        scope: ScanScope,
    ): List<AuditSite> {
        val comment = decl.catsComment ?: return emptyList()
        val descriptor =
            comment.returnTagList
                .firstOrNull()
                ?.returnTypeDescriptorList
                ?.singleOrNull() ?: return emptyList()
        val kind = requiredPrimitive(descriptor.argType.text, comment) ?: return emptyList()
        val form = if (decl is LuaFuncDecl) AuditCallResolution.formOf(decl) else CalleeForm.LOCAL
        val base = CallBase(CalleePath.SAME_FILE, form, scope.relativePath, decl.textRange, null, decl.textRange)
        return PsiTreeUtil.findChildrenOfType(decl, LuaFinalStatement::class.java).mapNotNull { statement ->
            val value = statement.exprList?.exprList?.singleOrNull()
            if (nearestFunction(statement) != decl || value == null) return@mapNotNull null
            val injection = primitiveInjection(value.textRange, kind.text)
            base
                .copy(
                    site = statement.textRange,
                ).site("return.mismatch", injection, null)
                .copy(declaredType = kind.text)
        }
    }

    private fun primitiveInjection(
        edit: TextRange,
        primitive: String,
    ): Injection = Injection(edit, AuditKinds.primitiveWrong(primitive), AuditKinds.primitiveControl(primitive))

    private fun nearestFunction(statement: LuaFinalStatement) =
        PsiTreeUtil.getParentOfType(
            statement,
            true,
            LuaFuncDecl::class.java,
            LuaLocalFuncDecl::class.java,
            LuaFuncDef::class.java,
        )

    /** A `@return`/`@field` type that classifies PRIMITIVE and not optional, or null. */
    private fun requiredPrimitive(
        typeText: String,
        comment: LuaCatsComment,
    ): DeclaredKind? = AuditKinds.classify(typeText, comment)?.takeIf { it.kind == Kind.PRIMITIVE && !it.optional }

    /** `recv.name = value`, one target and one value, a dotted name suffix only. */
    private fun fieldTarget(assignment: LuaAssignmentStatement): LuaVar? {
        val target = assignment.varList.varList.singleOrNull() ?: return null
        if (assignment.exprList.exprList.size != 1 || target.nameRef == null) return null
        val index = target.varSuffixList.singleOrNull()?.indexExpr ?: return null
        return if (index.nameRef != null && index.expr == null) target else null
    }

    /** Null when the receiver is not a class; otherwise the field's tag, null when untagged. */
    private fun fieldLookup(
        types: LuaTypes,
        target: LuaVar,
    ): FieldLookup? {
        val receiver = target.nameRef ?: return null
        val fieldName =
            target.varSuffixList
                .single()
                .indexExpr.nameRef
                ?.text ?: return null
        val receiverClass = classOf(types.graphTypeToLuaType(types.getValueType(receiver))) ?: return null
        val member = receiverClass.resolveMember(fieldName) ?: return FieldLookup(null)
        return FieldLookup(AuditSiteFinder.fieldTag(member, fieldName))
    }

    private fun classOf(type: LuaType): LuaClassType? =
        if (type is LuaUnionType) {
            type.types.firstOrNull {
                it is LuaClassType
            } as? LuaClassType
        } else {
            type as? LuaClassType
        }

    private fun fieldSite(
        assignment: LuaAssignmentStatement,
        tag: LuaCatsFieldTag,
        scope: ScanScope,
    ): AuditSite? {
        val comment = PsiTreeUtil.getParentOfType(tag, LuaCatsComment::class.java) ?: return null
        val kind = requiredPrimitive(tag.argType.text, comment) ?: return null
        val value = assignment.exprList.exprList.single()
        val location = AuditPaths.locate(tag, scope)
        val base =
            CallBase(location.path, CalleeForm.FIELD, scope.relativePath, assignment.textRange, location.declFile, null)
        val injection = primitiveInjection(value.textRange, kind.text)
        return base.site("field.assign", injection, null).copy(declaredType = kind.text)
    }
}
