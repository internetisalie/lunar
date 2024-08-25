package net.internetisalie.lunar.lang;

import com.intellij.lang.ASTNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import net.internetisalie.lunar.LuaLanguage;
import net.internetisalie.lunar.lang.lexer.LuaLexerAdapter;
import net.internetisalie.lunar.lang.lexer.LuaTokenTypes;
import net.internetisalie.lunar.lang.parser.LuaParser;
import net.internetisalie.lunar.lang.psi.LuaFile;
import org.jetbrains.annotations.NotNull;

public class LuaParserDefinition implements ParserDefinition {
    public static final IFileElementType FILE = new IFileElementType(LuaLanguage.INSTANCE);

    @NotNull
    @Override
    public Lexer createLexer(Project project) {
        return new LuaLexerAdapter();
    }

    @NotNull
    @Override
    public TokenSet getCommentTokens() {
        return LuaTokenTypes.COMMENT_SET;
    }

    @NotNull
    @Override
    public TokenSet getStringLiteralElements() {
        return LuaTokenTypes.STRING_LITERAL_SET;
    }

    @NotNull
    @Override
    public TokenSet getWhitespaceTokens() {
        return LuaTokenTypes.WHITE_SPACES_SET;
    }

    @NotNull
    @Override
    public PsiParser createParser(final Project project) {
        return new LuaParser();
    }

    @NotNull
    @Override
    public IFileElementType getFileNodeType() {
        return FILE;
    }

    @NotNull
    @Override
    public PsiFile createFile(@NotNull FileViewProvider viewProvider) {
        return new LuaFile(viewProvider);
    }

    @NotNull
    @Override
    public PsiElement createElement(ASTNode node) {
        return LuaTokenTypes.Factory.createElement(node);
    }
}