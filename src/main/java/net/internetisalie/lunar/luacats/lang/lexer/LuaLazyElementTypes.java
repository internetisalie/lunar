package net.internetisalie.lunar.luacats.lang.lexer;

import com.intellij.lang.*;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.ILazyParseableElementType;
import net.internetisalie.lunar.lang.LuaLanguage;
import net.internetisalie.lunar.luacats.lang.parser.LuaCatsParser;
import net.internetisalie.lunar.luacats.lang.psi.impl.LuaCatsLazyCommentImpl;
import org.jetbrains.annotations.NotNull;

public class LuaLazyElementTypes {

    /**
     * LuaCats comment
     */
    public static ILazyParseableElementType LUACATS_COMMENT = new ILazyParseableElementType("LAZY_COMMENT") {
        @NotNull
        public Language getLanguage() {
            return LuaLanguage.INSTANCE;
        }

        public ASTNode parseContents(ASTNode chameleon) {
            final PsiElement parentElement = chameleon.getTreeParent().getPsi();
            assert parentElement != null;

            final Project project = parentElement.getProject();
            final PsiParser parser = new LuaCatsParser();
            final Lexer lexer = new LuaCatsLexer();

            final PsiBuilder builder = PsiBuilderFactory.getInstance().createBuilder(project, chameleon, lexer, getLanguage(), chameleon.getText());
            return parser.parse(this, builder).getFirstChildNode();
        }

        @Override
        public ASTNode createNode(CharSequence text) {
            return new LuaCatsLazyCommentImpl(text);
        }
    };
}
