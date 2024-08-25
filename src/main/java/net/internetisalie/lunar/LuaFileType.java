package net.internetisalie.lunar;

import com.intellij.openapi.fileTypes.LanguageFileType;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class LuaFileType extends LanguageFileType {
    public static final LuaFileType INSTANCE = new LuaFileType();

    private LuaFileType() {
        super(LuaLanguage.INSTANCE);
    }

    @NotNull
    @Override
    public String getName() {
        return "Lua File";
    }

    @NotNull
    @Override
    public String getDescription() {
        return "Lua language file";
    }

    @NotNull
    @Override
    public String getDefaultExtension() {
        return "lua";
    }

    @Override
    public Icon getIcon() {
        return LuaIcons.FILE;
    }

}
