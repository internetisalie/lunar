package net.internetisalie.lunar.lang.psi;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import net.internetisalie.lunar.LuaFileType;
import net.internetisalie.lunar.LuaLanguage;
import org.jetbrains.annotations.NotNull;

public class LuaFile extends PsiFileBase {

    public LuaFile(@NotNull FileViewProvider viewProvider) {
        super(viewProvider, LuaLanguage.INSTANCE);
    }

    @NotNull
    @Override
    public FileType getFileType() {
        return LuaFileType.INSTANCE;
    }

    @Override
    public String toString() {
        return "Lua File";
    }

}
