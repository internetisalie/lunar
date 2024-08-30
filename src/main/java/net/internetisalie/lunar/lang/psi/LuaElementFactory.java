package net.internetisalie.lunar.lang.psi;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.util.PsiTreeUtil;
import net.internetisalie.lunar.lang.LuaFileType;

public class LuaElementFactory {
    public static PsiElement createIdentifier(Project project, String name) {
        var luaLabelRef = createLabelRef(project, name);
        return luaLabelRef.getIdentifier();
    }

    public static LuaLabelRef createLabelRef(Project project, String name) {
        var luaGotoStatement = createGotoStatement(project, name);
        return luaGotoStatement.getLabelRef();
    }

    public static LuaGotoStatement createGotoStatement(Project project, String name) {
        var luaFile = createFile(project, "goto " + name);
        return PsiTreeUtil.findChildOfType(luaFile, LuaGotoStatement.class);
    }

    public static LuaLabel createLabel(Project project, String name) {
        var luaFile = createFile(project, "%%" + name + "%%");
        return PsiTreeUtil.findChildOfType(luaFile, LuaLabel.class);
    }

    public static LuaFile createFile(Project project, String text) {
        String name = "dummy.lua";
        return (LuaFile) PsiFileFactory.getInstance(project).
                createFileFromText(name, LuaFileType.INSTANCE, text);
    }
}
