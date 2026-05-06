package net.internetisalie.lunar.lang.doc

import com.intellij.codeInsight.documentation.DocumentationManagerProtocol
import com.intellij.codeInsight.documentation.DocumentationManagerUtil
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.ui.GuiUtils
import net.internetisalie.lunar.lang.psi.LuaCommentOwner
import net.internetisalie.lunar.lang.psi.LuaFuncDecl
import net.internetisalie.lunar.lang.psi.LuaLocalFuncDecl
import net.internetisalie.lunar.lang.psi.LuaParList
import net.internetisalie.lunar.lang.psi.types.LuaPrimitiveType
import net.internetisalie.lunar.lang.syntax.LuaHighlight
import net.internetisalie.lunar.lang.syntax.extractLuaComment
import net.internetisalie.lunar.luacats.lang.doc.LuaCatsDocumentationRenderer
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsComment
import net.internetisalie.lunar.luacats.lang.syntax.LuaCatsHighlight
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.parser.MarkdownParser

object LuaDocumentationRenderer {
    val DOC_COMMENT_HEADER = """
            <html>
                <head>
                    <style type="text/css">
                        #error {
                            background-color: #eeeeee;
                            margin-bottom: 10px;
                        }
                        .body {
                           text-indent: 20px;
                           margin-bottom: 5px;
                        }
                    </style>
                </head>
                <body>
            """.trimIndent()

    val DOC_COMMENT_FOOTER = """
                </body>
            </html>
            """.trimIndent()

    fun renderHintDocumentation(element: PsiElement): String? {
        return renderFullDocumentation(element)
    }

    // Render the full documentation for the specified element.
    fun renderFullDocumentation(element: PsiElement): String? {
        val sbContent = StringBuilder()
        if (element is LuaCommentOwner) {
            renderCommentOwnerDocumentation(sbContent, element)
        }

        if (sbContent.isEmpty()) return null

        return buildString {
            append(DOC_COMMENT_HEADER)
            append(sbContent)
            append(DOC_COMMENT_FOOTER)
        }
    }

    fun renderCommentOwnerDocumentation(sb: StringBuilder, element: LuaCommentOwner) {
        val catsComment = element.catsComment
        if (catsComment != null) {
            LuaCatsDocumentationRenderer.render(sb, element, catsComment)
            return
        }

        val comment = element.getComment()
        if (comment != null) {
            LuaPlainDocumentationRenderer.render(sb, element, comment)
            return
        }
    }

    fun highlightLuaCode(code: String): String {
        val lexer = net.internetisalie.lunar.lang.lexer.LuaLexer()
        lexer.start(code)
        val highlighter = net.internetisalie.lunar.lang.syntax.LuaSyntaxHighlighter()
        val sb = StringBuilder()
        while (lexer.tokenType != null) {
            val tokenType = lexer.tokenType!!
            val tokenText = code.substring(lexer.tokenStart, lexer.tokenEnd)
            val highlights = highlighter.getTokenHighlights(tokenType)
            if (highlights.isNotEmpty()) {
                sb.append(codeFragment(highlights[0], tokenText))
            } else {
                sb.append(HtmlChunk.text(tokenText))
            }
            lexer.advance()
        }
        return sb.toString()
    }

    fun markdownDescription(markdown: String): String {
        val flavour = GFMFlavourDescriptor()
        val parsedTree = MarkdownParser(flavour).buildMarkdownTreeFromString(markdown)
        var html = HtmlGenerator(markdown, parsedTree, flavour).generateHtml()

        // Post-process to highlight Lua code blocks
        val regex = Regex("<pre><code class=\"language-lua\">([\\s\\S]*?)</code></pre>")
        html = regex.replace(html) { matchResult ->
            val escapedCode = matchResult.groupValues[1]
            // Basic unescape (since Markdown library might have escaped it)
            val code = escapedCode.replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")

            "<pre><code>${highlightLuaCode(code)}</code></pre>"
        }

        // Strip <body> and <html> wrappers if present
        return html.removePrefix("<body>").removeSuffix("</body>")
            .removePrefix("<html>").removeSuffix("</html>")
            .trim()
    }
}

object LuaPlainDocumentationRenderer {
    fun render(sb: StringBuilder, element: LuaCommentOwner, comment: PsiComment) {
        when (element) {
            is LuaFuncDecl -> renderLuaFuncDecl(sb, element, comment)
            is LuaLocalFuncDecl -> renderLuaLocalFuncDecl(sb, element, comment)
        }
    }

    private fun renderLuaFuncDecl(sb: StringBuilder, element: LuaFuncDecl, comment: PsiComment) {
        sb.append("<pre>")
            .append("function ")
            .append(element.funcName.text)
        renderLuaParList(sb, element.parList)
        sb.append("</pre>")
        renderComment(sb, comment)
    }

    private fun renderLuaLocalFuncDecl(sb: StringBuilder, element: LuaLocalFuncDecl, comment: PsiComment) {
        sb.append("<pre>")
            .append(codeFragment(LuaHighlight.KEYWORD, "local function"))
            .append(" ")
            .append(codeFragment(LuaHighlight.VAR_LOCAL, element.nameRef.text))
        renderLuaParList(sb, element.parList)
        sb.append("</pre>")
        renderComment(sb, comment)
    }

    private fun renderComment(sb: StringBuilder, comment: PsiComment) {
        val comments = mutableListOf<PsiComment>()
        var curr: PsiElement? = comment

        // Walk backwards to find the first contiguous comment
        while (curr is PsiComment && curr !is LuaCatsComment) {
            comments.add(0, curr)
            var prev = curr.prevSibling
            var hasBlankLine = false
            while (prev is PsiWhiteSpace) {
                if (prev.text.count { it == '\n' } > 1) {
                    hasBlankLine = true
                    break
                }
                prev = prev.prevSibling
            }
            if (hasBlankLine) break
            curr = prev
        }

        val fullText = comments.joinToString("\n") { it.text }
        val text = extractLuaComment(fullText)
        sb.append("<hr size=1>\n")
        sb.append(LuaDocumentationRenderer.markdownDescription(text))
    }

    private fun renderLuaParList(sb: StringBuilder, element: LuaParList?) {
        sb.append(codeFragment(LuaHighlight.OPERATORS, "("))
            .append(element?.text)
            .append(codeFragment(LuaHighlight.OPERATORS, ")"))
    }

}

fun codeFragment(key: TextAttributesKey, text: String): String {
    val attributes = EditorColorsManager.getInstance().globalScheme.getAttributes(key)
    val fontColor = attributes?.foregroundColor
    if (fontColor == null) {
        return HtmlChunk.text(text).toString()
    }
    val fontHex = "#${GuiUtils.colorToHex(fontColor)}"
    return "<font color=${fontHex}>${HtmlChunk.text(text)}</font>"
}

fun buildTypeLink(typeName: String): String {
    if (LuaPrimitiveType.PRIMITIVES.containsKey(typeName)) {
        return codeFragment(LuaCatsHighlight.TYPE, typeName)
    }
    val sb = StringBuilder()
    DocumentationManagerUtil.createHyperlink(sb, typeName, typeName, true)
    return sb.toString()
}
