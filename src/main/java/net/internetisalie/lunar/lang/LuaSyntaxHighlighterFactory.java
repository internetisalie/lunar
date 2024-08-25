package net.internetisalie.lunar.lang;

import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import net.internetisalie.lunar.lang.lexer.LuaSyntaxHighlighter;
import org.jetbrains.annotations.NotNull;

public class LuaSyntaxHighlighterFactory extends SyntaxHighlighterFactory {

    @NotNull
    @Override
    public SyntaxHighlighter getSyntaxHighlighter(Project project, VirtualFile virtualFile) {
        return new LuaSyntaxHighlighter();
    }

}
