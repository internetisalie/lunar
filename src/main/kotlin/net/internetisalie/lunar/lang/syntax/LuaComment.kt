package net.internetisalie.lunar.lang.syntax

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.elementType
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsComment
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsDescription
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsElementTypes

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
                        if (next.text == "\n") continue
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
