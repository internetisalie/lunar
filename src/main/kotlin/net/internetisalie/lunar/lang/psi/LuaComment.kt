package net.internetisalie.lunar.lang.psi

import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.elementType
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsComment
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsDescription
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsElementTypes

class LuaComment(val lines : List<PsiComment>) {
    fun getText() : String? {
        if (lines.isEmpty()) return null
        return lines.joinToString("\n") { it.text.substring(2) }.trimIndent()
    }
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