package net.internetisalie.lunar.lang.doc

import com.intellij.codeInsight.navigation.targetPresentation
import com.intellij.model.Pointer
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.DocumentationTargetProvider
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiReference
import com.intellij.psi.createSmartPointer
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import net.internetisalie.lunar.lang.indexing.LuaClassNameIndex
import net.internetisalie.lunar.lang.indexing.LuaAliasIndex
import net.internetisalie.lunar.lang.indexing.LuaGlobalDeclarationIndex
import net.internetisalie.lunar.lang.indexing.dottedMemberName
import net.internetisalie.lunar.lang.navigation.LuaMemberFieldNavigation
import net.internetisalie.lunar.lang.psi.*
import com.intellij.psi.PsiWhiteSpace
import net.internetisalie.lunar.lang.syntax.LuaCatsSummary
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsComment
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsCommentOwner
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsElementTypes

class LuaDocumentationTargetProvider : DocumentationTargetProvider {
    override fun documentationTargets(file: PsiFile, offset: Int): List<DocumentationTarget> {
        var element = file.findElementAt(offset) ?: return emptyList()
        val et = element.elementType
        if (et == LuaElementTypes.IDENTIFIER || et == LuaCatsElementTypes.NAME || et == LuaCatsElementTypes.BUILTIN_TYPE) {
            // NAV-12-03: a dotted member field documents its `receiver.field = value` declaration.
            memberFieldDocumentationTarget(element)?.let { return listOf(it) }
            element = resolveDocumentationTarget(element) ?: return emptyList()
        }
        if (element is LuaCatsCommentOwner) {
            return arrayListOf(LuaCatsDocumentationTarget(element))
        }
        return emptyList()
    }

    /**
     * For a dotted member segment (`path` in `package.path`), find the documented field declaration via
     * the member-field index and render its riding `---@type`/doc comment. A field can be re-assigned in
     * several files (e.g. `package.path = package.path .. ...`); the comment rides the assignment
     * statement, which is not a [LuaCommentOwner], so the preceding cats comment is read directly and the
     * first declaration that carries one is chosen over a bare re-assignment.
     */
    private fun memberFieldDocumentationTarget(element: PsiElement): DocumentationTarget? {
        if (element.parent?.parent !is LuaIndexExpr) return null
        val container = PsiTreeUtil.getParentOfType(element, LuaVar::class.java) ?: return null
        val qualifiedName = dottedMemberName(container) ?: return null
        val project = element.project
        val scope = GlobalSearchScope.allScope(project)
        for (field in LuaMemberFieldNavigation.find(project, qualifiedName, scope)) {
            val statement = PsiTreeUtil.getParentOfType(field, LuaStatement::class.java) ?: continue
            val comment = precedingCatsComment(statement) ?: continue
            return LuaFieldDocumentationTarget(field, comment, qualifiedName)
        }
        return null
    }

    private fun precedingCatsComment(statement: PsiElement): LuaCatsComment? {
        var prev = statement.prevSibling
        while (prev is PsiWhiteSpace) prev = prev.prevSibling
        return prev as? LuaCatsComment ?: (prev?.firstChild as? LuaCatsComment)
    }

    private fun resolveDocumentationTarget(element: PsiElement): PsiElement? {
        // First try resolving through reference (for call sites)
        val parent = element.parent

        val resolvedElement = when {
            parent is LuaNameRefElement -> {
                val ref = parent.reference
                var resolved = ref?.resolve()

                // The reference might resolve to a name token or another leaf element
                // Get the parent to get the actual declaration
                if (resolved != null && resolved !is LuaCatsCommentOwner) {
                    // First try to unwrap it to a declaration
                    val commentOwner = findElementDocCommentOwner(resolved)
                    if (commentOwner != null) {
                        resolved = commentOwner
                    } else {
                        // If that fails, try the resolved element's parent directly
                        // (in case it's already wrapped in a declaration)
                        val p = resolved.parent
                        if (p is LuaCatsCommentOwner) {
                            resolved = p
                        }
                    }
                }
                resolved
            }
            parent is PsiReference -> parent.resolve()
            element is PsiReference -> element.resolve()
            else -> null
        }

        if (resolvedElement != null && resolvedElement is LuaCatsCommentOwner) {
            return resolvedElement
        }

        // Resolve upwards (for identifiers that are part of declarations)
        val ownerElement = findElementDocCommentOwner(element)
        if (ownerElement != null) {
            return ownerElement
        }

        // Try cross-file type lookup using PSI search and stubs
        val elementText = element.text ?: return null
        val project = element.project
        val scope = GlobalSearchScope.allScope(project)

        // A dotted member segment (e.g. `path` in `package.path`) must NOT match an unrelated
        // top-level symbol that merely shares the short name: LuaGlobalDeclarationIndex is keyed by
        // receiver, so a bare "path" lookup returns every `path.*` function of an unrelated module.
        // For member segments resolve only through the qualified name below — showing nothing when
        // it has no documented declaration is correct; an arbitrary same-named symbol is not.
        val isMemberSegment = element.parent?.parent is LuaIndexExpr
        if (!isMemberSegment) {
            val classDecl = StubIndex.getElements(LuaClassNameIndex.KEY, elementText, project, scope, LuaLocalVarDecl::class.java).firstOrNull()
            if (classDecl != null) return classDecl

            val aliasDecl = StubIndex.getElements(LuaAliasIndex.KEY, elementText, project, scope, LuaLocalVarDecl::class.java).firstOrNull()
            if (aliasDecl != null) return aliasDecl

            val funcDecl = StubIndex.getElements(LuaGlobalDeclarationIndex.KEY, elementText, project, scope, LuaFuncDecl::class.java).firstOrNull()
            if (funcDecl != null) return funcDecl
        }

        // Fallback for member functions like math.abs
        if (parent is LuaNameRefElement) {
            val topExpr = PsiTreeUtil.getTopmostParentOfType(parent, LuaExpr::class.java)
            if (topExpr != null) {
                val fullName = topExpr.text
                if (fullName != null && fullName != elementText) {
                    return StubIndex.getElements(LuaGlobalDeclarationIndex.KEY, fullName, project, scope, LuaFuncDecl::class.java).firstOrNull()
                }
            }
        }

        return null
    }

    private fun findElementDocCommentOwner(element: PsiElement): LuaCatsCommentOwner? {
        val owner = PsiTreeUtil.getParentOfType(element, LuaCatsCommentOwner::class.java) ?: return null

        return when (owner) {
            is LuaFuncDecl -> owner
            is LuaLocalFuncDecl -> owner
            is LuaLocalVarDecl -> {
                val catsComment = owner.catsComment
                if (catsComment != null && (
                    catsComment.classTagList.isNotEmpty() ||
                    catsComment.typeTagList.isNotEmpty() ||
                    catsComment.enumTagList.isNotEmpty())
                ) {
                    owner
                } else {
                    null
                }
            }
            else -> null
        }
    }
}


internal class LuaCatsDocumentationTarget(
    val element: LuaCatsCommentOwner,
) : DocumentationTarget {
    override fun createPointer(): Pointer<out DocumentationTarget> {
        val elementPtr = element.createSmartPointer()

        return Pointer {
            val element = elementPtr.dereference() ?: return@Pointer null
            LuaCatsDocumentationTarget(element)
        }
    }

    override fun computePresentation(): TargetPresentation {
        return targetPresentation(element)
    }

    override val navigatable: Navigatable?
        get() = element as? Navigatable

    override fun computeDocumentation(): DocumentationResult? {
        return DocumentationResult.documentation(
            LuaDocumentationRenderer.renderFullDocumentation(element) ?: return null
        )
    }
}

/**
 * Documentation for a member field (`receiver.field`, NAV-12-03). The field's `---@type`/doc comment
 * rides its assignment statement (not a [LuaCommentOwner]), so it is rendered directly from the
 * preceding [LuaCatsComment] and anchored on the field identifier for presentation/navigation.
 */
internal class LuaFieldDocumentationTarget(
    val anchor: PsiElement,
    val comment: LuaCatsComment,
    private val qualifiedName: String,
) : DocumentationTarget {
    override fun createPointer(): Pointer<out DocumentationTarget> {
        val anchorPtr = anchor.createSmartPointer()
        val commentPtr = comment.createSmartPointer()
        val name = qualifiedName
        return Pointer {
            val anchor = anchorPtr.dereference() ?: return@Pointer null
            val comment = commentPtr.dereference() ?: return@Pointer null
            LuaFieldDocumentationTarget(anchor, comment, name)
        }
    }

    override fun computePresentation(): TargetPresentation = targetPresentation(anchor)

    override val navigatable: Navigatable?
        get() = anchor as? Navigatable

    override fun computeDocumentation(): DocumentationResult? {
        val typeText = comment.typeTagList.firstOrNull()?.argType?.text?.trim()
        val summary = LuaCatsSummary.getText(comment)
        if (typeText.isNullOrEmpty() && summary.isNullOrEmpty()) return null

        val body = buildString {
            append("<div class='definition'><pre>")
            append(qualifiedName)
            if (!typeText.isNullOrEmpty()) {
                append(" : ")
                append(typeText)
            }
            append("</pre></div>")
            if (!summary.isNullOrEmpty()) {
                append("<div class='content'>")
                append(LuaDocumentationRenderer.markdownDescription(summary))
                append("</div>")
            }
        }
        return DocumentationResult.documentation(
            LuaDocumentationRenderer.DOC_COMMENT_HEADER + body + LuaDocumentationRenderer.DOC_COMMENT_FOOTER,
        )
    }
}

