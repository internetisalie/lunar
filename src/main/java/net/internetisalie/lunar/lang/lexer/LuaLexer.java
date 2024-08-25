package net.internetisalie.lunar.lang.lexer;

import com.intellij.lexer.FlexAdapter;
import com.intellij.lexer.MergingLexerAdapter;
import com.intellij.psi.tree.TokenSet;

class LuaLexer extends MergingLexerAdapter {
    public LuaLexer() {
        super(
                new FlexAdapter(new _LuaLexer(null)),
                TokenSet.create(
                        LuaTokenTypes.LONGCOMMENT,
                        LuaTokenTypes.LONGSTRING,
                        LuaTokenTypes.STRING,
                        LuaTokenTypes.SHORTCOMMENT
                )
        );
    }
}
