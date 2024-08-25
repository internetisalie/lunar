package net.internetisalie.lunar;

import com.intellij.lang.Language;

public class LuaLanguage extends Language {
    static final String LANGUAGE_NAME = "Lua";

    public static final LuaLanguage INSTANCE = new LuaLanguage();

    private LuaLanguage() {
        super(LANGUAGE_NAME);
    }
}
