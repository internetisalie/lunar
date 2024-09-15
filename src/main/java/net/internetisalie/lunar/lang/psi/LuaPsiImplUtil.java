package net.internetisalie.lunar.lang.psi;

import com.intellij.psi.PsiWhiteSpace;
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsComment;
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsCommentOwner;
import net.internetisalie.lunar.luadoc.lang.psi.LuaDocComment;
import net.internetisalie.lunar.luadoc.lang.psi.LuaDocCommentOwner;
import org.jetbrains.annotations.Nullable;

public class LuaPsiImplUtil {
    @Nullable
    public static LuaCatsComment getCatsComment(LuaCatsCommentOwner owner) {
        if (owner instanceof LuaFuncDecl) {
            var statementElement = owner.getParent();
            if (statementElement == null) return null;

            var lazyElement = statementElement.getPrevSibling();
            while (lazyElement instanceof PsiWhiteSpace) {
                lazyElement = lazyElement.getPrevSibling();
            }
            if (!(lazyElement instanceof LuaCatsComment)) return null;

            var commentElement = lazyElement.getFirstChild();
            if (!(commentElement instanceof LuaCatsComment)) return null;

            return (LuaCatsComment) commentElement;
        }

        return null;
    }

    @Nullable
    public static LuaDocComment getDocComment(LuaDocCommentOwner owner) {
        if (owner instanceof LuaFuncDecl) {
            var statementElement = owner.getParent();
            if (statementElement == null) return null;

            var commentElement = statementElement.getPrevSibling();
            while (commentElement instanceof PsiWhiteSpace) {
                commentElement = commentElement.getPrevSibling();
            }
            if (!(commentElement instanceof LuaDocComment)) return null;
            return (LuaDocComment) commentElement;
        }

        return null;
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
}
