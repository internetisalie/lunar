package net.internetisalie.lunar.luacats.lang.doc

import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.psi.PsiElement
import net.internetisalie.lunar.lang.doc.codeFragment
import net.internetisalie.lunar.lang.psi.LuaCatsSummary
import net.internetisalie.lunar.lang.psi.LuaFuncDecl
import net.internetisalie.lunar.lang.psi.LuaLocalFuncDecl
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
            .append(codeFragment(LuaHighlight.OPERATORS, "("))
            .append("\n")

        buildFunctionSignatureParams(comment, sb)

        sb.append("\n) : ")

        buildFunctionSignatureReturns(comment, sb)

        sb.append("\n</pre>").append("<hr size=1>")
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
            sb.append(codeFragment(LuaCatsHighlight.TYPE, it.argType.text))
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
                .append(codeFragment(LuaCatsHighlight.TYPE, it.argType.text))
                .append("</pre>")
        } else {
            sb.append("<pre>")
                .append(codeFragment(LuaHighlight.OPERATORS, "... : "))
                .append(codeFragment(LuaCatsHighlight.TYPE, it.argType.text))
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
                .append(codeFragment(LuaCatsHighlight.TYPE, it.argType.text))
                .append("</pre>")
        } else {
            sb.append("<pre>")
                .append(codeFragment(LuaCatsHighlight.TYPE, it.argType.text))
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
}

private val sectionTitles = mapOf(
    Section.PARAM to "Parameters",
    Section.RETURN to "Returns",
)
