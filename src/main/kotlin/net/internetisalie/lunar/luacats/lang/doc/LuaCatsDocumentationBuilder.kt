package net.internetisalie.lunar.luacats.lang.doc

import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.GuiUtils
import net.internetisalie.lunar.lang.psi.LuaFuncDecl
import net.internetisalie.lunar.lang.syntax.LuaHighlight
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsComment
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsParamTag
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsReturnTag
import net.internetisalie.lunar.luacats.lang.syntax.LuaCatsHighlight
import org.intellij.markdown.flavours.commonmark.CommonMarkFlavourDescriptor
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.parser.MarkdownParser

class LuaCatsDocumentationBuilder {
    companion object {
        fun buildLuaFuncDecl(comment : LuaCatsComment, element : LuaFuncDecl, sb : StringBuilder) {
                buildFunctionSignature(comment, element, sb)
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

        private fun codeFragment(key: TextAttributesKey, text: String): String {
            val fontColor = EditorColorsManager.getInstance().globalScheme.getAttributes(key).foregroundColor
            val fontHex = "#${GuiUtils.colorToHex(fontColor)}"
            return "<font color=${fontHex}>${HtmlChunk.text(text)}</font>"
        }

        private fun buildFunctionSignature(comment: LuaCatsComment, element: LuaFuncDecl, sb: StringBuilder) {
            sb.append("<pre>\n")
                .append(codeFragment(LuaHighlight.KEYWORD, "function"))
                .append(" ")
                .append(codeFragment(LuaHighlight.LOCAL_VAR, element.funcName.text))
                .append(codeFragment(LuaHighlight.OPERATORS, "("))
                .append("\n")

            var paramCount = 0
            comment.paramTagList.forEach {
                if (paramCount > 0) {
                    sb.append(codeFragment(LuaHighlight.OPERATORS, ","))
                        .append("\n")
                }
                paramCount++
                sb.append("    ")

                if (it.argName != null) {
                    sb.append(codeFragment(LuaHighlight.LOCAL_VAR, it.argName!!.text))
                } else {
                    sb.append(codeFragment(LuaHighlight.OPERATORS, "..."))
                }
                sb.append(codeFragment(LuaHighlight.OPERATORS, " : "))
                sb.append(codeFragment(LuaCatsHighlight.TYPE, it.argType.text))
            }

            sb.append("\n) : ")

            var returnCount = 0
            comment.returnTagList.forEach {
                if (returnCount > 0) {
                    sb.append(codeFragment(LuaHighlight.OPERATORS, ", "))
                }
                returnCount++

                sb.append(it.argType.text)
            }


            sb.append("\n</pre>").append("<hr size=1>")
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
                    .append(codeFragment(LuaHighlight.LOCAL_VAR, it.argName!!.text))
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
                    .append(codeFragment(LuaHighlight.LOCAL_VAR, it.argName!!.text))
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

        private fun markdownDescription(markdown : String): String {
            val flavour = CommonMarkFlavourDescriptor()
            val parsedTree = MarkdownParser(flavour).buildMarkdownTreeFromString(markdown)
            return HtmlGenerator(markdown, parsedTree, flavour).generateHtml()
        }
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
