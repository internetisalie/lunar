package net.internetisalie.lunar.lang.lexer;

import com.intellij.lexer.FlexAdapter;

public class LuaLexerAdapter extends FlexAdapter {
    public LuaLexerAdapter() {
        super(new _LuaLexer(null));
    }
}
