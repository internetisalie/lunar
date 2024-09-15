package net.internetisalie.lunar.luadoc.lang.doc

import com.intellij.psi.PsiElement
import net.internetisalie.lunar.lang.docCommentFooter
import net.internetisalie.lunar.lang.docCommentHeader
import net.internetisalie.lunar.luadoc.lang.LuaDocDocumentation
import net.internetisalie.lunar.luadoc.lang.LuaDocDocumentationFactory
import net.internetisalie.lunar.luadoc.lang.psi.LuaDocComment
import net.internetisalie.lunar.luadoc.lang.psi.LuaDocCommentOwner
import org.intellij.markdown.flavours.commonmark.CommonMarkFlavourDescriptor
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.parser.MarkdownParser
import java.util.regex.Pattern


fun computeLocalDocumentation(element: PsiElement, originalElement: PsiElement?, quickNavigation: Boolean): String? {
    if (element !is LuaDocCommentOwner) return null

    val docComment: LuaDocComment = element.getDocComment() ?: return null

    val sb = StringBuilder()
    sb.append(docCommentHeader)

    val owner = docComment.owner
    if (owner != null) sb.append("<h2>").append(owner.text).append("</h2>")

    val documentation = LuaDocDocumentationFactory.createDocumentation(docComment)

    var markdown = markdownDescription(documentation.description)
    markdown = unwrapCode(markdown)
    sb.append(markdown)

    buildTagValuesListSection("class", documentation, sb)
    buildTagValuesListSection("param", documentation, sb)
    buildTagValuesListSection("field", documentation, sb)
    buildTagListSection("return", documentation, sb)
    buildTagValuesListSection("retval", documentation, sb)
    buildTagListSection("see", documentation, sb)
    buildTagListSection("external", documentation, sb)
    buildTagSection("usage", documentation, sb)
    buildTagSection("release", documentation, sb)
    buildTagSection("author", documentation, sb)
    buildTagSection("copyright", documentation, sb)

    sb.append(docCommentFooter)
    return sb.toString()
}

// Convert the description to HTML
private fun markdownDescription(markdown : String): String {
    val flavour = CommonMarkFlavourDescriptor()
    val parsedTree = MarkdownParser(flavour).buildMarkdownTreeFromString(markdown)
    return HtmlGenerator(markdown, parsedTree, flavour).generateHtml()
}

// TODO: Hack in newlines since markdown is newline-sensitive
//    private fun trailingNewlines(desc: PsiElement): String {
//        var next = desc.nextSibling
//        var newLines = 0
//        while (next != null) {
//            if (next !is ASTNode) break
//            val astType = next.elementType
//            if (astType === LuaDocElementTypes.LDOC_DASHES) newLines++
//            else if (astType === LuaDocElementTypes.LDOC_COMMENT_DATA) break
//            next = next.nextSibling
//        }
//
//        val sb = StringBuilder()
//        while (newLines > 1) {
//            sb.append("\n")
//            newLines--
//        }
//        return sb.toString()
//    }

// IDEA doesn't respect "white-space: pre;" css property
private fun unwrapCode(html: String): String {
    var result = html
    val fencedCodePattern = Pattern.compile("<p><code>([^<]+)</code></p>")
    var fencedCodeMatcher = fencedCodePattern.matcher(result)
    while (fencedCodeMatcher.matches()) {
        val outer = fencedCodeMatcher.group(0)
        val inner = fencedCodeMatcher.group(1)
        result = result.replace(outer, "<pre><code>$inner</code></pre>")
        fencedCodeMatcher = fencedCodePattern.matcher(result)
    }
    return result
}

// Sometimes we don't want a paragraph.
private fun unwrapPara(html: String): String {
    var result = html
    if (result.startsWith("<p>")) result = result.substring(3)
    if (result.endsWith("</p>")) result = result.substring(0, result.length - 4)
    return result
}

private fun buildSectionHeader(section: String, documentation: LuaDocDocumentation, sb: StringBuilder) {
    val sectionTitle = sectionTitles[section] ?: return
    sb.append("<h3>").append(sectionTitle).append("</h3>")
}

private fun buildTagSection(section: String, documentation: LuaDocDocumentation, dest: StringBuilder) {
    val sb = StringBuilder()
    var count = 0

    buildSectionHeader(section, documentation, sb)

    documentation.tags.filter { it.name == section }.forEach {
        val markdown = unwrapCode(markdownDescription(it.data))
        sb.append(markdown)
        count++
    }

    if (count > 0) dest.append(sb)
}

private fun buildTagListSection(section: String, documentation: LuaDocDocumentation, dest: StringBuilder) {
    val sb = StringBuilder()
    var count = 0

    buildSectionHeader(section, documentation, sb)

    sb.append("<ul class=").append(section).append('>')

    documentation.tags.filter { it.name == section }.forEach {
        sb.append("<li>")
        var markdown = markdownDescription(it.data)
        markdown = unwrapPara(markdown)
        markdown = unwrapCode(markdown)
        sb.append(markdown)
        sb.append("</li>")
        count++
    }

    sb.append("</ul>")

    if (count > 0) dest.append(sb)
}

private fun buildTagValuesListSection(section: String, documentation: LuaDocDocumentation, dest: StringBuilder) {
    val sb = StringBuilder()
    var count = 0

    buildSectionHeader(section, documentation, sb)

    sb.append("<dl class=").append(section).append('>')

    documentation.tags.filter { it.name == section }.forEach {
        sb.append("<dt>").append(it.value).append("</dt>")
        sb.append("<dd>").append(markdownDescription(it.data)).append("</dd>")
        count++
    }

    sb.append("</dl>")

    if (count > 0) dest.append(sb)
}

private val sectionTitles = mapOf(
    "class" to "Class",
    "param" to "Parameters",
    "return" to "Returns",
    "retval" to "Return Values",
    "see" to "See Also",
    "usage" to "Usage",
    "release" to "Release",
    "author" to "Author",
    "copyright" to "Copyright",
    "external" to "External Links",
    "field" to "Fields",
)
