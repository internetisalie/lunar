package net.internetisalie.lunar.luadoc.lang

import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import net.internetisalie.lunar.luadoc.lang.lexer.LuaDocTokenTypes.LDOC_COMMENT_DATA
import net.internetisalie.lunar.luadoc.lang.parser.LuaDocElementTypes.LDOC_TAG
import net.internetisalie.lunar.luadoc.lang.psi.LuaDocComment
import net.internetisalie.lunar.luadoc.lang.psi.LuaDocTag

data class LuaDocDocumentation(val summary: String, val description: String, val tags: Collection<Tag>) {
    data class Tag(val name: String, val value: String?, val data: String)
}

object LuaDocDocumentationFactory {

    // Create a new Documentation instance
    fun createDocumentation(comment: LuaDocComment): LuaDocDocumentation {
        val commentText = comment.text

        val commentSummary = getSummary(comment, commentText)
        val commentDescription = getDescription(comment, commentText)
        val commentTags = getTags(comment).map { createTag(it, commentText) }

        return LuaDocDocumentation(commentSummary, commentDescription, commentTags)
    }

    // Create a new Tag instance
    private fun createTag(tag: LuaDocTag, commentText: String): LuaDocDocumentation.Tag {
        val tagStartOffset = tag.textRangeInParent.startOffset
        val tagEndOffset = tag.textRangeInParent.endOffset
        val tagText = commentText.substring(tagStartOffset, tagEndOffset)

        val tagName = getTagName(tag)
        val tagValue = getTagValue(tag)
        val tagDescription = getDescription(tag, tagText)

        return LuaDocDocumentation.Tag(tagName, tagValue, tagDescription)
    }

    // Return the tag name.
    private fun getTagName(tag: LuaDocTag): String {
        return tag.name
    }

    // Return the optional tag value.
    private fun getTagValue(tag: LuaDocTag): String? {
        val valueElement = tag.valueElement ?: return null
        return valueElement.text
    }

    // Return the contained tag elements.
    private fun getTags(element: LuaDocComment): Collection<LuaDocTag> {
        return PsiTreeUtil.getChildrenOfType(element, LuaDocTag::class.java)?.toList() ?: emptyList()
    }

    // Return the unindented description.
    private fun getDescription(parent: PsiElement, parentText: String): String {
        val lines = ArrayList<String>()
        getDescriptionElements(parent).forEachIndexed { index, element ->
            val startOffset = if (index != 0 || element is LuaDocComment) (
                    // Collect the leading whitespace as well, so it can be evenly trimmed
                    element.prevSibling?.textRangeInParent?.endOffset
                        ?: element.textRangeInParent.startOffset
                    ) else (
                    element.textRangeInParent.startOffset
                    )
            val endOffset = element.textRangeInParent.endOffset

            lines.add(parentText.substring(startOffset, endOffset))
        }
        return lines.joinToString("\n").trimIndent()
    }

    // Return the direct children of the comment or tag which have element type LDOC_COMMENT_DATA.
    private fun getDescriptionElements(element: PsiElement): List<PsiElement> {
        val elements = ArrayList<PsiElement>()
        var child: PsiElement? = element.firstChild
        while (child != null) {
            if (child is PsiWhiteSpace) {
                child = child.getNextSibling()
                continue
            }
            val node = child.node
            if (node == null) {
                child = child.nextSibling
                continue
            }
            val i = node.elementType
            if (i === LDOC_TAG) break
            if (i === LDOC_COMMENT_DATA) {
                elements.add(child)
            }
            child = child.nextSibling
        }
        return elements
    }

    // Return the first line of the description, up to and including the first '.'
    // TODO: accept line breaks
    private fun getSummary(element: LuaDocComment, commentText: String): String {
        val elems = getDescriptionElements(element)
        if (elems.isEmpty()) return ""

        val first = StringUtil.notNullize(elems[0].text)
        val pos = first.indexOf('.')
        if (pos > 0) return first.substring(0, pos)
        return first
    }

}