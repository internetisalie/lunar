package net.internetisalie.lunar.lang.doc

import com.intellij.codeInsight.navigation.targetPresentation
import com.intellij.icons.AllIcons
import com.intellij.model.Pointer
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.DocumentationTargetProvider
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.createSmartPointer
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.intellij.util.indexing.FileBasedIndex
import net.internetisalie.lunar.lang.indexing.LuaAliasIndex
import net.internetisalie.lunar.lang.indexing.LuaCatsTypeNameIndex
import net.internetisalie.lunar.lang.indexing.LuaClassNameIndex
import net.internetisalie.lunar.lang.indexing.LuaGlobalDeclarationIndex
import net.internetisalie.lunar.lang.indexing.dottedMemberName
import net.internetisalie.lunar.lang.navigation.LuaMemberFieldNavigation
import net.internetisalie.lunar.lang.psi.*
import net.internetisalie.lunar.lang.psi.types.LuaClassType
import net.internetisalie.lunar.lang.psi.types.LuaTypeManager
import net.internetisalie.lunar.lang.syntax.LuaCatsSummary
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsAliasTag
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsArgName
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsArgType
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsClassTag
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsComment
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsCommentOwner
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsDeclarations
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsElementTypes
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsFieldTag

class LuaDocumentationTargetProvider : DocumentationTargetProvider {
    override fun documentationTargets(
        file: PsiFile,
        offset: Int,
    ): List<DocumentationTarget> {
        var element = file.findElementAt(offset) ?: return emptyList()
        val et = element.elementType
        if (et == LuaElementTypes.IDENTIFIER ||
            et == LuaCatsElementTypes.NAME ||
            et == LuaCatsElementTypes.BUILTIN_TYPE
        ) {
            // NAV-12-03: a dotted member field documents its `receiver.field = value` declaration.
            memberFieldDocumentationTarget(element)?.let { return listOf(it) }
            // BUG-440: and a member declared as `---@field` documents its TAG.
            catsFieldDocumentationTarget(element)?.let { return listOf(it) }

            val isMemberSegment = element.parent?.parent is LuaIndexExpr
            if (!isMemberSegment) {
                val name = element.text
                if (name != null) {
                    val typeElement =
                        findTypeElement(name, element.project, GlobalSearchScope.allScope(element.project))
                    if (typeElement != null) {
                        return listOf(LuaCatsTypeDocumentationTarget(typeElement, name))
                    }
                }
            }

            element = resolveDocumentationTarget(element) ?: return emptyList()
        }
        if (element is LuaCatsCommentOwner) {
            return arrayListOf(LuaCatsDocumentationTarget(element))
        }
        if (element is LuaCatsClassTag || element is LuaCatsAliasTag) {
            val name =
                when (element) {
                    is LuaCatsClassTag -> element.argType.text.trim()
                    is LuaCatsAliasTag -> element.argName.text.trim()
                    else -> ""
                }
            return arrayListOf(LuaCatsTypeDocumentationTarget(element, name))
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

    /**
     * BUG-440 — a member declared `---@field` on a `---@class`, which has **no declaration PSI**.
     *
     * `resolveDocumentationTarget` obtains its target through `reference.resolve()`, and a field
     * exists only as a tag inside a LuaCATS comment (the `AGENTS.md` invariant: LuaCATS tags are not
     * stubbed, they ride a host declaration's stub). So `resolve()` returned null, no target was
     * produced, and Quick Doc rendered "No documentation found" — for **every** `---@field` member of
     * **every** library. The bug was reported as "openresty fails, love2d works"; measured against
     * the real catalog files, a love2d field fails identically. The variable was the declaration
     * form, never the library.
     *
     * Resolution is deliberately NOT made to succeed for a field. That would mint a declaration that
     * does not exist and reach Find Usages, Rename and the type engine; `LuaCatsTypeNavigation` set
     * the precedent for a bare `---@class` by targeting the tag instead, and this follows it.
     *
     * The member is looked up through the resolved class rather than by name, so an undeclared member
     * still yields nothing — showing an arbitrary same-named symbol would be worse than showing
     * nothing, which is the rule `memberFieldDocumentationTarget` already states above.
     */
    private fun catsFieldDocumentationTarget(element: PsiElement): DocumentationTarget? {
        if (element.parent?.parent !is LuaIndexExpr) return null
        val container = PsiTreeUtil.getParentOfType(element, LuaVar::class.java) ?: return null
        val receiver = container.nameRef?.text ?: return null
        val memberName = element.text ?: return null
        val resolved = LuaTypeManager.getInstance(element.project).resolveType(receiver, element)
        val member = (resolved as? LuaClassType)?.resolveMember(memberName) ?: return null
        val tag = member.sourceElement as? LuaCatsFieldTag ?: return null
        return LuaCatsFieldDocumentationTarget(tag, "$receiver.$memberName")
    }

    private fun precedingCatsComment(statement: PsiElement): LuaCatsComment? {
        var prev = statement.prevSibling
        while (prev is PsiWhiteSpace) prev = prev.prevSibling
        return prev as? LuaCatsComment ?: (prev?.firstChild as? LuaCatsComment)
    }

    private fun resolveDocumentationTarget(element: PsiElement): PsiElement? {
        // First try resolving through reference (for call sites)
        val parent = element.parent

        val resolvedElement =
            when {
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
            val classDecl =
                StubIndex
                    .getElements(
                        LuaClassNameIndex.KEY,
                        elementText,
                        project,
                        scope,
                        LuaLocalVarDecl::class.java,
                    ).firstOrNull()
            if (classDecl != null) return classDecl

            val aliasDecl =
                StubIndex
                    .getElements(
                        LuaAliasIndex.KEY,
                        elementText,
                        project,
                        scope,
                        LuaLocalVarDecl::class.java,
                    ).firstOrNull()
            if (aliasDecl != null) return aliasDecl

            val funcDecl =
                StubIndex
                    .getElements(
                        LuaGlobalDeclarationIndex.KEY,
                        elementText,
                        project,
                        scope,
                        LuaFuncDecl::class.java,
                    ).firstOrNull()
            if (funcDecl != null) return funcDecl
        }

        // Fallback for member functions like math.abs
        if (parent is LuaNameRefElement) {
            val topExpr = PsiTreeUtil.getTopmostParentOfType(parent, LuaExpr::class.java)
            if (topExpr != null) {
                val fullName = topExpr.text
                if (fullName != null && fullName != elementText) {
                    return StubIndex
                        .getElements(
                            LuaGlobalDeclarationIndex.KEY,
                            fullName,
                            project,
                            scope,
                            LuaFuncDecl::class.java,
                        ).firstOrNull()
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
                if (catsComment != null &&
                    (
                        catsComment.classTagList.isNotEmpty() ||
                            catsComment.typeTagList.isNotEmpty() ||
                            catsComment.enumTagList.isNotEmpty()
                    )
                ) {
                    owner
                } else {
                    null
                }
            }
            else -> null
        }
    }

    private fun findTypeElement(
        name: String,
        project: Project,
        scope: GlobalSearchScope,
    ): PsiElement? {
        val index = FileBasedIndex.getInstance()
        val psiManager =
            com.intellij.psi.PsiManager
                .getInstance(project)
        for (virtualFile in index.getContainingFiles(LuaCatsTypeNameIndex.KEY, name, scope)) {
            val luaFile = psiManager.findFile(virtualFile) as? LuaFile ?: continue
            for (tag in PsiTreeUtil.findChildrenOfType(luaFile, LuaCatsClassTag::class.java)) {
                val id = PsiTreeUtil.getChildOfType(tag, LuaCatsArgType::class.java) ?: continue
                if (id.text.trim() == name) return tag
            }
            for (tag in PsiTreeUtil.findChildrenOfType(luaFile, LuaCatsAliasTag::class.java)) {
                val id = PsiTreeUtil.getChildOfType(tag, LuaCatsArgName::class.java) ?: continue
                if (id.text.trim() == name) return tag
            }
        }
        return null
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

    override fun computePresentation(): TargetPresentation = targetPresentation(element)

    override val navigatable: Navigatable?
        get() = element as? Navigatable

    override fun computeDocumentation(): DocumentationResult? {
        return DocumentationResult.documentation(
            LuaDocumentationRenderer.renderFullDocumentation(element) ?: return null,
        )
    }
}

/**
 * Documentation for a member field (`receiver.field`, NAV-12-03). The field's `---@type`/doc comment
 * rides its assignment statement (not a [LuaCommentOwner]), so it is rendered directly from the
 * preceding [LuaCatsComment] and anchored on the field identifier for presentation/navigation.
 */

/**
 * BUG-440 — renders a `---@field` from the **tag**, not from its comment.
 *
 * [LuaFieldDocumentationTarget] cannot serve this: it reads a `---@type` tag off the comment and the
 * comment's own summary, which for a `---@class` block describe the CLASS, not the field. Pointed at
 * a field tag it produced null — a target with no documentation, which renders exactly as the
 * missing target did. The type and the prose both belong to the tag, and
 * `LuaCatsDeclarations.fieldMember` already parses the first.
 */
internal class LuaCatsFieldDocumentationTarget(
    private val tag: LuaCatsFieldTag,
    private val qualifiedName: String,
) : DocumentationTarget {
    override fun createPointer(): Pointer<out DocumentationTarget> {
        val tagPtr = tag.createSmartPointer()
        val name = qualifiedName
        return Pointer {
            val restored = tagPtr.dereference() ?: return@Pointer null
            LuaCatsFieldDocumentationTarget(restored, name)
        }
    }

    override val navigatable: Navigatable?
        get() = tag as? Navigatable

    override fun computePresentation(): TargetPresentation = TargetPresentation.builder(qualifiedName).presentation()

    override fun computeDocumentation(): DocumentationResult {
        val typeText = LuaCatsDeclarations.fieldMember(tag).typeName.trim()
        val body =
            buildString {
                append("<div class='definition'><pre>")
                append(qualifiedName)
                if (typeText.isNotEmpty()) {
                    append(" : ")
                    append(typeText)
                }
                append("</pre></div>")
                val prose = fieldProse()
                if (prose.isNotEmpty()) {
                    append("<div class='content'>")
                    append(LuaDocumentationRenderer.markdownDescription(prose))
                    append("</div>")
                }
            }
        return DocumentationResult.documentation(
            LuaDocumentationRenderer.DOC_COMMENT_HEADER + body + LuaDocumentationRenderer.DOC_COMMENT_FOOTER,
        )
    }

    /**
     * The `---` prose immediately above this tag, stopping at the previous tag.
     *
     * A `---@class` block documents each of its fields in turn, so the comment's own summary is the
     * class's and the lines between two tags belong to the second of them.
     */
    private fun fieldProse(): String {
        val lines = mutableListOf<String>()
        var prev = tag.prevSibling
        while (prev != null) {
            if (prev is LuaCatsFieldTag || prev is LuaCatsClassTag) break
            val text = prev.text
            if (text != null && text.isNotBlank()) {
                val stripped = text.trimStart().removePrefix("---").trim()
                if (stripped.isNotEmpty()) lines.add(stripped)
            }
            prev = prev.prevSibling
        }
        return lines.reversed().joinToString(" ").trim()
    }
}

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
        val typeText =
            comment.typeTagList
                .firstOrNull()
                ?.argType
                ?.text
                ?.trim()
        val summary = LuaCatsSummary.getText(comment)
        if (typeText.isNullOrEmpty() && summary.isNullOrEmpty()) return null

        val body =
            buildString {
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

internal class LuaCatsTypeDocumentationTarget(
    val element: PsiElement,
    private val typeName: String,
) : DocumentationTarget {
    override fun createPointer(): Pointer<out DocumentationTarget> {
        val elementPtr = element.createSmartPointer()
        val name = typeName
        return Pointer {
            val element = elementPtr.dereference() ?: return@Pointer null
            LuaCatsTypeDocumentationTarget(element, name)
        }
    }

    override fun computePresentation(): TargetPresentation {
        val icon = if (element is LuaCatsClassTag) AllIcons.Nodes.Class else AllIcons.Nodes.Type
        return TargetPresentation
            .builder(typeName)
            .icon(icon)
            .presentation()
    }

    override val navigatable: Navigatable?
        get() = element as? Navigatable

    override fun computeDocumentation(): DocumentationResult? {
        return DocumentationResult.documentation(
            LuaDocumentationRenderer.renderFullDocumentation(element) ?: return null,
        )
    }
}
