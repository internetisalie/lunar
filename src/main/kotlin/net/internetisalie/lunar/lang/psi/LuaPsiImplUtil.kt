package net.internetisalie.lunar.lang.psi

import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsComment
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsCommentOwner
import net.internetisalie.lunar.luadoc.lang.psi.LuaDocComment
import net.internetisalie.lunar.luadoc.lang.psi.LuaDocCommentOwner

object LuaPsiImplUtil {
    @JvmStatic
    fun getCatsComment(owner: LuaCatsCommentOwner?): LuaCatsComment? {
        when (owner) {
            is LuaFuncDecl, is LuaLocalFuncDecl -> {
                val statementElement = owner.parent ?: return null
                val lazyElement : LuaCatsComment = statementElement.prevSiblingSkipWhitespace() ?: return null
                return lazyElement.firstChild as? LuaCatsComment
            }
        }

        return null
    }


    @JvmStatic
    fun getDocComment(owner: LuaDocCommentOwner?): LuaDocComment? {
        when (owner) {
            is LuaLocalFuncDecl, is LuaFuncDecl -> {
                val statementElement = owner.parent ?: return null
                return statementElement.prevSiblingSkipWhitespace()
            }
        }

        return null
    }

    @JvmStatic
    fun getComment(owner: LuaCommentOwner?): PsiComment? {
        when (owner) {
            is LuaLocalFuncDecl, is LuaFuncDecl -> {
                return owner.parent?.prevSiblingSkipWhitespace()
            }
        }

        return null
    }

    //    @Nullable
    //    public static LuaDocCommentOwner findDocOwner(LuaDocPsiElement docElement) {
    //        PsiElement element = docElement;
    //        while (element != null && element.getParent() instanceof LuaDocPsiElement) element = element.getParent();
    //        if (element == null) return null;
    //
    //        while (true) {
    //            element = element.getNextSibling();
    //            if (element instanceof LuaBlock)
    //                element = element.getFirstChild();
    //            if (element == null) return null;
    //            final ASTNode node = element.getNode();
    //            if (node == null) return null;
    //            if (LuaElementTypes.LUADOC_COMMENT.equals(node.getElementType()) ||
    //                    !LuaElementTypes.WHITE_SPACES_OR_COMMENTS.contains(node.getElementType())) {
    //                break;
    //            }
    //        }
    //
    //        if (element instanceof LuaDocCommentOwner) return (LuaDocCommentOwner) element;
    //
    //        if (element instanceof LuaMaybeDeclarationAssignmentStatement) {
    //            LuaExpression[] expressions = ((LuaMaybeDeclarationAssignmentStatement) element).getDefinedSymbolValues();
    //
    //            for (LuaExpression e : expressions)
    //                if (e instanceof LuaDocCommentOwner) return (LuaDocCommentOwner) e;
    //        }
    //
    //
    //        return null;
    //    }

    @JvmStatic
    fun getBlockList(element : PsiElement) : List<LuaBlock> {
        return PsiTreeUtil.getChildrenOfType(element, LuaBlock::class.java)?.toList() ?: emptyList()
    }
}

inline fun <reified T : PsiElement> PsiElement.prevSiblingSkipWhitespace(): T? {
    var prev = prevSibling
    while (prev is PsiWhiteSpace) {
        prev = prev.prevSibling
    }
    if (prev == null) {
        if (parent is LuaBlock) {
            prev = parent.prevSibling
            while (prev is PsiWhiteSpace) {
                prev = prev.prevSibling
            }
        }
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
    }
    return null
}
