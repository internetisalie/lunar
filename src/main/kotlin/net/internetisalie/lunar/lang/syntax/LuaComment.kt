package net.internetisalie.lunar.lang.syntax

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.elementType
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsAliasTag
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsAsyncTag
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsCastTag
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsClassTag
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsComment
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsDeprecatedTag
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsDescription
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsDiagnosticTag
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsElementTypes
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsEnumTag
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsFieldTag
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsGenericTag
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsMetaTag
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsModuleTag
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsNodiscardTag
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsOperatorTag
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsOverloadTag
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsPackageTag
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsParamTag
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsPrivateTag
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsProtectedTag
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsReturnTag
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsSeeTag
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsSourceTag
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsTypeTag
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsVarargTag
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsVersionTag

object LuaDocDescription {
    // TODO: Move out of LuaDocCommentImpl.getDescriptionElements
}

object LuaDocSummary {
    // TODO: Move out of LuaDocCommentImpl.getSummaryDescription
}

object LuaCatsSummary {
    fun getText(comment : LuaCatsComment) : String? {
        var first = comment.firstChild ?: return null
        if (first.elementType == LuaCatsElementTypes.COMMENT) {
            first = first.firstChild ?: return null
        }
        var next : PsiElement? = first
        val lines = mutableListOf<String>()
        while (next != null) {
            when {
                next is PsiWhiteSpace -> { next = next.nextSibling }
                next.elementType == LuaCatsElementTypes.DASHES -> {
                    val dashes = next
                    next = next.nextSibling ?: break
                    if (next is PsiWhiteSpace) {
                        if (next.text == "\n") {
                            lines.add("")
                            next = next.nextSibling
                            continue
                        }
                        next = next.nextSibling ?: break
                    }
                    val description = next as? LuaCatsDescription ?: break
                    val leadingSpace = description.textOffset - (dashes.textOffset + dashes.textLength)
                    val line = " ".repeat(leadingSpace) + description.text
                    lines.add(line)

                    next = next.nextSibling
                }
                else -> break
            }
        }
        return if (lines.isEmpty()) null else lines.joinToString("\n").trimIndent()
    }
}


fun getLuaCommentDelimiterLength(str: String): Int {
    if (!str.startsWith("--")) return 0
    if (str.startsWith("--[")) {
        var level = 0
        while (level + 3 < str.length && str[level + 3] == '=') level++
        if (level + 3 < str.length && str[level + 3] == '[') return level + 4
    }
    return 2
}

fun extractLuaComment(str: String): String {
    if (!str.startsWith("--")) return str
    val delimiterLength = getLuaCommentDelimiterLength(str)
    return when {
        delimiterLength > 2 -> {
            if (str.length < delimiterLength * 2 - 2) return ""
            str.substring(delimiterLength, str.length - delimiterLength + 2)
        }
        else -> str.lines().joinToString("\n") {
            if (it.startsWith("--")) it.substring(2) else it
        }.trimIndent()
    }
}

fun summarize(str : String) : String{
    val firstLine = str.trim().substringBefore('\n')
    return if (firstLine.length < str.length) {
        "$firstLine..."
    } else {
        firstLine
    }
}

internal fun collectDescriptionText(comment: LuaCatsComment): String {
    val sb = StringBuilder()
    for (desc in comment.descriptionList) {
        sb.append(desc.text).append(' ')
    }
    collectCoreTagsDescription(comment, sb)
    collectAdvancedTagsDescription(comment, sb)
    collectOtherTagsDescription(comment, sb)
    return sb.toString()
}

private fun collectCoreTagsDescription(comment: LuaCatsComment, sb: StringBuilder) {
    comment.classTagList.forEach { it.description?.text?.let { text -> sb.append(text).append(' ') } }
    comment.aliasTagList.forEach { it.description?.text?.let { text -> sb.append(text).append(' ') } }
    comment.paramTagList.forEach { it.description?.text?.let { text -> sb.append(text).append(' ') } }
    comment.returnTagList.forEach { it.description?.text?.let { text -> sb.append(text).append(' ') } }
    comment.fieldTagList.forEach { it.description?.text?.let { text -> sb.append(text).append(' ') } }
    comment.typeTagList.forEach { it.description?.text?.let { text -> sb.append(text).append(' ') } }
}

private fun collectAdvancedTagsDescription(comment: LuaCatsComment, sb: StringBuilder) {
    comment.deprecatedTagList.forEach { it.description?.text?.let { text -> sb.append(text).append(' ') } }
    comment.seeTagList.forEach { it.description?.text?.let { text -> sb.append(text).append(' ') } }
    comment.overloadTagList.forEach { it.description?.text?.let { text -> sb.append(text).append(' ') } }
    comment.enumTagList.forEach { it.description?.text?.let { text -> sb.append(text).append(' ') } }
    comment.metaTagList.forEach { it.description?.text?.let { text -> sb.append(text).append(' ') } }
    comment.nodiscardTagList.forEach { it.description?.text?.let { text -> sb.append(text).append(' ') } }
    comment.asyncTagList.forEach { it.description?.text?.let { text -> sb.append(text).append(' ') } }
    comment.genericTagList.forEach { it.description?.text?.let { text -> sb.append(text).append(' ') } }
}

private fun collectOtherTagsDescription(comment: LuaCatsComment, sb: StringBuilder) {
    comment.castTagList.forEach { it.description?.text?.let { text -> sb.append(text).append(' ') } }
    comment.diagnosticTagList.forEach { it.description?.text?.let { text -> sb.append(text).append(' ') } }
    comment.moduleTagList.forEach { it.description?.text?.let { text -> sb.append(text).append(' ') } }
    comment.operatorTagList.forEach { it.description?.text?.let { text -> sb.append(text).append(' ') } }
    comment.packageTagList.forEach { it.description?.text?.let { text -> sb.append(text).append(' ') } }
    comment.privateTagList.forEach { it.description?.text?.let { text -> sb.append(text).append(' ') } }
    comment.protectedTagList.forEach { it.description?.text?.let { text -> sb.append(text).append(' ') } }
    comment.sourceTagList.forEach { it.description?.text?.let { text -> sb.append(text).append(' ') } }
    comment.varargTagList.forEach { it.description?.text?.let { text -> sb.append(text).append(' ') } }
    comment.versionTagList.forEach { it.description?.text?.let { text -> sb.append(text).append(' ') } }
}
