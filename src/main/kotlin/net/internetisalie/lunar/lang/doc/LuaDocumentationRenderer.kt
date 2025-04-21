package net.internetisalie.lunar.lang.doc

import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.psi.PsiElement
import com.intellij.ui.GuiUtils
import net.internetisalie.lunar.lang.psi.LuaComment
import net.internetisalie.lunar.lang.psi.LuaCommentOwner
import net.internetisalie.lunar.lang.psi.LuaFuncDecl
import net.internetisalie.lunar.lang.psi.LuaLocalFuncDecl
import net.internetisalie.lunar.lang.psi.LuaParList
import net.internetisalie.lunar.lang.syntax.LuaHighlight
import net.internetisalie.lunar.luacats.lang.doc.LuaCatsDocumentationRenderer
import net.internetisalie.lunar.luadoc.lang.doc.LuaDocDocumentationRenderer

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
        val sb = StringBuilder()
        sb.append(DOC_COMMENT_HEADER)
        if (element is LuaCommentOwner) {
            renderCommentOwnerDocumentation(sb, element)
        }
        sb.append(DOC_COMMENT_FOOTER)
        return sb.toString()
    }

    fun renderCommentOwnerDocumentation(sb: StringBuilder, element: LuaCommentOwner) {
        val catsComment = element.catsComment
        if (catsComment != null) {
            LuaCatsDocumentationRenderer.render(sb, element, catsComment)
            return
        }

        val docComment = element.docComment
        if (docComment != null) {
            LuaDocDocumentationRenderer.render(sb, element, docComment)
            return
        }

        val luaComment = element.getComment()
        LuaPlainDocumentationRenderer.render(sb, element, luaComment)
    }


}

object LuaPlainDocumentationRenderer {
    fun render(sb: StringBuilder, element: LuaCommentOwner, comment: LuaComment) {
        when (element) {
            is LuaFuncDecl -> renderLuaFuncDecl(sb, element, comment)
            is LuaLocalFuncDecl -> renderLuaLocalFuncDecl(sb, element, comment)
        }
    }

    private fun renderLuaFuncDecl(sb: StringBuilder, element: LuaFuncDecl, comment: LuaComment) {
        sb.append("<pre>")
            .append("function ")
            .append(element.funcName.text)
        renderLuaParList(sb, element.parList)
        sb.append("</pre>")
        renderComment(sb, comment)
    }

    private fun renderLuaLocalFuncDecl(sb: StringBuilder, element: LuaLocalFuncDecl, comment: LuaComment) {
        sb.append("<pre>")
            .append(codeFragment(LuaHighlight.KEYWORD, "local function"))
            .append(" ")
            .append(codeFragment(LuaHighlight.VAR_LOCAL, element.nameRef.text))
        renderLuaParList(sb, element.parList)
        sb.append("</pre>")
        renderComment(sb, comment)
    }

    private fun renderComment(sb: StringBuilder, comment: LuaComment) {
        val text = comment.getText() ?: return
        sb.append("<hr size=1>\n")
        sb.append(text)
    }

    private fun renderLuaParList(sb: StringBuilder, element: LuaParList?) {
        sb.append(codeFragment(LuaHighlight.OPERATORS, "("))
            .append(element?.text)
            .append(codeFragment(LuaHighlight.OPERATORS, ")"))
    }

}

fun codeFragment(key: TextAttributesKey, text: String): String {
    val fontColor = EditorColorsManager.getInstance().globalScheme.getAttributes(key).foregroundColor
    val fontHex = "#${GuiUtils.colorToHex(fontColor)}"
    return "<font color=${fontHex}>${HtmlChunk.text(text)}</font>"
}
