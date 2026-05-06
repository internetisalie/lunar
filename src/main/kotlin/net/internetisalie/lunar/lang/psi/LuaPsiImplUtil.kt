package net.internetisalie.lunar.lang.psi

import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import net.internetisalie.lunar.lang.psi.LuaElementTypes
import net.internetisalie.lunar.lang.psi.LuaParList
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsComment
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsCommentOwner

object LuaPsiImplUtil {
    @JvmStatic
    fun getCatsComment(owner: LuaCatsCommentOwner?): LuaCatsComment? {
        if (owner !is PsiElement) return null

        var current: PsiElement? = owner
        while (current != null && current !is LuaFile) {
            var prev = current.prevSibling
            while (prev != null && (prev is PsiWhiteSpace || (prev is PsiComment && prev !is LuaCatsComment))) {
                prev = prev.prevSibling
            }

            if (prev is LuaCatsComment) return prev

            // Also try finding by element type name as a fallback in tests
            if (prev != null && prev.node.elementType.toString().contains("COMMENT")) {
                if (prev is LuaCatsComment) return prev
                // If it's a LuaCatsComment but the 'is' check failed? (ClassLoader issue)
                val first = prev.firstChild
                if (first is LuaCatsComment) return first
            }

            current = current.parent
        }

        return null
    }


    @JvmStatic
    fun getComment(owner: LuaCommentOwner?): PsiComment? {
        if (owner !is PsiElement) return null

        var current: PsiElement? = owner
        while (current != null && current !is LuaFile) {
            val comment = current.prevSiblingSkipWhitespaceOnly<PsiComment>()
            if (comment != null && comment !is LuaCatsComment) return comment
            current = current.parent
        }

        return null
    }

    @JvmStatic
    fun getBlockList(element : PsiElement) : List<LuaBlock> {
        return PsiTreeUtil.getChildrenOfType(element, LuaBlock::class.java)?.toList() ?: emptyList()
    }

    @JvmStatic
    fun getParameters(parList: LuaParList?): List<String> {
        if (parList == null) return emptyList()
        val result = mutableListOf<String>()
        parList.nameList?.nameRefList?.forEach {
            it.name?.let { name -> result.add(name) }
        }
        if (parList.node.findChildByType(LuaElementTypes.ELLIPSIS) != null) {
            result.add("...")
        }
        return result
    }
}

inline fun <reified T : PsiElement> PsiElement.prevSiblingSkipWhitespace(): T? {
    var prev = prevSibling
    while (prev is PsiWhiteSpace || (prev is PsiComment && prev !is LuaCatsComment)) {
        prev = prev.prevSibling
    }
    return prev as? T
}

inline fun <reified T : PsiElement> PsiElement.prevSiblingSkipWhitespaceOnly(): T? {
    var prev = prevSibling
    while (prev is PsiWhiteSpace) {
        prev = prev.prevSibling
    }
    return prev as? T
}

inline fun <reified T : PsiElement> PsiElement.prevSiblingSkipNewline(): T? {
    var prev = prevSibling
    if (prev is PsiWhiteSpace) {
        if (prev.text == "\n") {
            prev = prev.prevSibling
        } else {
            return null
        }
    }
    return prev as? T
}

fun PsiElement.firstChildSkipWhitespace(): PsiElement? {
    var child = firstChild
    while (child != null) {
        if (child !is PsiWhiteSpace) { return child }
        child = child.nextSibling
    }
    return null
}
