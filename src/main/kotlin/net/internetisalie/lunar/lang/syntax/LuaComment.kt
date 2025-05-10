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
        var next : PsiElement = comment.firstChild ?: return null
        val lines = mutableListOf<String>()
        while (true) {
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

                    next = next.nextSibling ?: break
                }
                else -> break
            }
        }
        return lines.joinToString("\n").trimIndent()
    }
}


fun extractLuaComment(str: String): String {
    return when {
        str.startsWith("--[[") -> str.substring(4, str.length - 4)
        str.startsWith("--") -> str.lines().joinToString("\n") { it.substring(2) }.trimIndent()
        else -> str
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
