package net.internetisalie.lunar.luacats.lang.doc

import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import net.internetisalie.lunar.lang.doc.buildTypeLink
import net.internetisalie.lunar.lang.doc.codeFragment
import net.internetisalie.lunar.lang.indexing.LuaClassNameIndex
import net.internetisalie.lunar.lang.psi.LuaFuncDecl
import net.internetisalie.lunar.lang.psi.LuaLocalFuncDecl
import net.internetisalie.lunar.lang.psi.LuaLocalVarDecl
import net.internetisalie.lunar.lang.syntax.LuaCatsSummary
import net.internetisalie.lunar.lang.syntax.LuaHighlight
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsComment
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsParamTag
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsReturnTag
import net.internetisalie.lunar.luacats.lang.syntax.LuaCatsHighlight
import org.intellij.markdown.flavours.commonmark.CommonMarkFlavourDescriptor
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.parser.MarkdownParser

object LuaCatsDocumentationRenderer {
    fun render(sb: StringBuilder, element: PsiElement, comment: LuaCatsComment) {
        when (element) {
            is LuaFuncDecl -> renderLuaFuncDecl(sb, element, comment)
            is LuaLocalFuncDecl -> buildLuaLocalFuncDecl(sb, element, comment)
            is LuaLocalVarDecl -> renderLuaLocalVarDecl(sb, element, comment)
        }
    }

    fun renderLuaFuncDecl(sb: StringBuilder, element: LuaFuncDecl, comment: LuaCatsComment) {
        buildFunctionSignature(comment, element, sb)
        renderSummary(comment, sb)
        sb.append(DocumentationMarkup.SECTIONS_START)
        buildParamTags(comment, sb)
        buildReturnTags(comment, sb)
        sb.append(DocumentationMarkup.SECTIONS_END)
    }

    private fun renderSummary(comment: LuaCatsComment, sb: StringBuilder) {
        val summary = LuaCatsSummary.getText(comment)
        if (summary != null) {
            sb.append(markdownDescription(summary))
        }
    }

    private fun buildLuaLocalFuncDecl(sb: StringBuilder, element: LuaLocalFuncDecl, comment: LuaCatsComment) {
        buildLocalFunctionSignature(comment, element, sb)
        renderSummary(comment, sb)
        sb.append(DocumentationMarkup.SECTIONS_START)
        buildParamTags(comment, sb)
        buildReturnTags(comment, sb)
        sb.append(DocumentationMarkup.SECTIONS_END)
    }

    private fun renderLuaLocalVarDecl(sb: StringBuilder, element: LuaLocalVarDecl, comment: LuaCatsComment) {
        // Render class header
        val classTag = comment.classTagList.firstOrNull()
        val typeTag = comment.typeTagList.firstOrNull()
        
        if (classTag != null || typeTag != null) {
            sb.append("<pre>\n")
            sb.append(codeFragment(LuaHighlight.KEYWORD, "class"))
            sb.append(" ")
            val className = classTag?.argType?.text ?: typeTag?.argType?.text ?: element.attNameList.firstOrNull()?.text ?: "Unknown"
            sb.append(buildTypeLink(className))
            
            // Check for parent types
            if (classTag?.parentTypes != null) {
                sb.append(" ")
                sb.append(codeFragment(LuaHighlight.OPERATORS, ":"))
                sb.append(" ")
                sb.append(buildTypeLink(classTag.parentTypes!!.text))
            }
            sb.append("\n</pre><hr size=1>")
        }
        
        renderSummary(comment, sb)
        
        // Render field tags if any
        val hasDirectFields = comment.fieldTagList.isNotEmpty()
        val parentTypeName = classTag?.parentTypes?.text
        val parentComment = parentTypeName?.let { lookupParentComment(element.project, it) }
        val hasInheritedFields = parentComment != null && parentComment.fieldTagList.isNotEmpty()
        
        if (hasDirectFields || hasInheritedFields) {
            sb.append(DocumentationMarkup.SECTIONS_START)
            
            if (hasDirectFields) {
                buildSectionHeader(Section.FIELD, sb)
                for (fieldTag in comment.fieldTagList) {
                    val fieldDescriptor = fieldTag.fieldDescriptor
                    if (fieldDescriptor?.argName != null) {
                        sb.append("<pre>")
                            .append(codeFragment(LuaHighlight.VAR_LOCAL, fieldDescriptor.argName!!.text))
                            .append(codeFragment(LuaHighlight.OPERATORS, " : "))
                            .append(buildTypeLink(fieldDescriptor.argType?.text ?: "any"))
                            .append("</pre>")
                        if (fieldTag.description != null) {
                            sb.append("<div class=body>")
                                .append(markdownDescription(fieldTag.description!!.text))
                                .append("</div>")
                        }
                    }
                }
            }
            
            if (hasInheritedFields) {
                buildSectionHeader(Section.INHERITED, sb)
                for (fieldTag in parentComment!!.fieldTagList) {
                    val fieldDescriptor = fieldTag.fieldDescriptor
                    if (fieldDescriptor?.argName != null) {
                        sb.append("<pre>")
                            .append(codeFragment(LuaHighlight.VAR_LOCAL, fieldDescriptor.argName!!.text))
                            .append(codeFragment(LuaHighlight.OPERATORS, " : "))
                            .append(buildTypeLink(fieldDescriptor.argType?.text ?: "any"))
                            .append("</pre>")
                        if (fieldTag.description != null) {
                            sb.append("<div class=body>")
                                .append(markdownDescription(fieldTag.description!!.text))
                                .append("</div>")
                        }
                    }
                }
            }
            
            sb.append(DocumentationMarkup.SECTIONS_END)
        }
    }
    
    private fun lookupParentComment(project: Project, parentTypeName: String): LuaCatsComment? {
        val scope = GlobalSearchScope.projectScope(project)
        val parentDecl = StubIndex.getElements(LuaClassNameIndex.KEY, parentTypeName, project, scope, LuaLocalVarDecl::class.java).firstOrNull()
        return parentDecl?.catsComment
    }

    private fun buildSectionHeader(section: Section, sb: StringBuilder) {
        sb.append(DocumentationMarkup.SECTION_HEADER_START)
        sb.append(sectionTitles[section])
        sb.append(DocumentationMarkup.SECTION_SEPARATOR)
    }

    private fun buildFunctionSignature(comment: LuaCatsComment, element: LuaFuncDecl, sb: StringBuilder) {
        sb.append("<pre>\n")
            .append(codeFragment(LuaHighlight.KEYWORD, "function"))
            .append(" ")
            .append(codeFragment(LuaHighlight.VAR_LOCAL, element.funcName.text))

        buildFunctionSignatureTypeParams(comment, sb)

        sb.append(codeFragment(LuaHighlight.OPERATORS, "("))
            .append("\n")

        buildFunctionSignatureParams(comment, sb)

        sb.append("\n) : ")

        buildFunctionSignatureReturns(comment, sb)

        sb.append("\n</pre>").append("<hr size=1>")
    }

    private fun buildFunctionSignatureTypeParams(comment: LuaCatsComment, sb: StringBuilder) {
        if (comment.genericTagList.isEmpty()) return
        sb.append(codeFragment(LuaHighlight.OPERATORS, "<"))
        var first = true
        comment.genericTagList.forEach { tag ->
            val typeParamList = tag.genericTypeParams?.genericTypeParamList ?: return@forEach
            typeParamList.forEach { param ->
                if (!first) {
                    sb.append(codeFragment(LuaHighlight.OPERATORS, ","))
                        .append(" ")
                } else {
                    first = false
                }
                sb.append(buildTypeLink(param.argName.text))

                val argType = param.argType ?: return@forEach
                sb
                    .append(" ")
                    .append(codeFragment(LuaHighlight.OPERATORS, ":"))
                    .append(" ")
                    .append(buildTypeLink(argType.text))
            }
        }
        sb.append(codeFragment(LuaHighlight.OPERATORS, ">"))
    }

    private fun buildLocalFunctionSignature(comment: LuaCatsComment, element: LuaLocalFuncDecl, sb: StringBuilder) {
        sb.append("<pre>\n")
            .append(codeFragment(LuaHighlight.KEYWORD, "local function"))
            .append(" ")
            .append(codeFragment(LuaHighlight.VAR_LOCAL, element.nameRef.text))
            .append(codeFragment(LuaHighlight.OPERATORS, "("))
            .append("\n")

        buildFunctionSignatureParams(comment, sb)

        sb.append("\n) : ")

        buildFunctionSignatureReturns(comment, sb)

        sb.append("\n</pre>").append("<hr size=1>")
    }

    private fun buildFunctionSignatureParams(comment: LuaCatsComment, sb: StringBuilder) {
        var paramCount = 0
        comment.paramTagList.forEach {
            if (paramCount > 0) {
                sb.append(codeFragment(LuaHighlight.OPERATORS, ","))
                    .append("\n")
            }
            paramCount++
            sb.append("    ")

            if (it.argName != null) {
                sb.append(codeFragment(LuaHighlight.VAR_LOCAL, it.argName!!.text))
            } else {
                sb.append(codeFragment(LuaHighlight.OPERATORS, "..."))
            }
            sb.append(codeFragment(LuaHighlight.OPERATORS, " : "))
            sb.append(buildTypeLink(it.argType.text))
        }
    }

    private fun buildFunctionSignatureReturns(comment: LuaCatsComment, sb: StringBuilder) {
        var returnCount = 0
        comment.returnTagList.forEach {
            if (returnCount > 0) {
                sb.append(codeFragment(LuaHighlight.OPERATORS, ", "))
            }
            returnCount++

            sb.append(it.argType.text)
        }
    }

    private fun buildParamTags(comment: LuaCatsComment, sb: StringBuilder) {
        val tags = comment.paramTagList
        if (tags.isEmpty()) return
        buildSectionHeader(Section.PARAM, sb)
        tags.forEach { buildParamTag(it, sb) }
    }

    private fun buildParamTag(it: LuaCatsParamTag, sb: StringBuilder) {
        if (it.argName != null) {
            sb.append("<pre>")
                .append(codeFragment(LuaHighlight.VAR_LOCAL, it.argName!!.text))
                .append(codeFragment(LuaHighlight.OPERATORS, " : "))
                .append(buildTypeLink(it.argType.text))
                .append("</pre>")
        } else {
            sb.append("<pre>")
                .append(codeFragment(LuaHighlight.OPERATORS, "... : "))
                .append(buildTypeLink(it.argType.text))
                .append("</pre>")
        }
        if (it.description != null) {
            sb.append("<div class=body>")
                .append(markdownDescription(it.description!!.text))
                .append("</div>")
        }
    }

    private fun buildReturnTags(comment: LuaCatsComment, sb: StringBuilder) {
        val tags = comment.returnTagList
        if (tags.isEmpty()) return
        buildSectionHeader(Section.RETURN, sb)
        tags.forEach { buildReturnTag(it, sb) }
    }

    private fun buildReturnTag(it: LuaCatsReturnTag, sb: StringBuilder) {
        if (it.argName != null) {
            sb.append("<pre>")
                .append(codeFragment(LuaHighlight.VAR_LOCAL, it.argName!!.text))
                .append(codeFragment(LuaHighlight.OPERATORS, " : "))
                .append(buildTypeLink(it.argType.text))
                .append("</pre>")
        } else {
            sb.append("<pre>")
                .append(codeFragment(LuaHighlight.VAR_LOCAL, "_"))
                .append(codeFragment(LuaHighlight.OPERATORS, " : "))
                .append(buildTypeLink(it.argType.text))
                .append("</pre>")
        }

        if (it.description != null) {
            sb.append("<div class=body>")
                .append(markdownDescription(it.description!!.text))
                .append("</div>")
        }
    }

    private fun markdownDescription(markdown: String): String {
        val flavour = CommonMarkFlavourDescriptor()
        val parsedTree = MarkdownParser(flavour).buildMarkdownTreeFromString(markdown)
        return HtmlGenerator(markdown, parsedTree, flavour).generateHtml()
    }
}

enum class Section {
    PARAM,
    RETURN,
    FIELD,
    INHERITED,
}

private val sectionTitles = mapOf(
    Section.PARAM to "Parameters",
    Section.RETURN to "Returns",
    Section.FIELD to "Fields",
    Section.INHERITED to "Inherited",
)
