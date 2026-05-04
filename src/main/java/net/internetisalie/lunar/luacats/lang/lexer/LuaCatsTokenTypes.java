package net.internetisalie.lunar.luacats.lang.lexer;

import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;

public interface LuaCatsTokenTypes {
    IElementType LCATS_DASHES = new LuaCatsElementType("LCATS_DASHES");
    IElementType LCATS_WHITESPACE = new LuaCatsElementType("LCATS_WHITESPACE");
    IElementType LCATS_TEXT = new LuaCatsElementType("LCATS_TEXT");

    IElementType LCATS_NAME = new LuaCatsElementType("LCATS_NAME");
    IElementType LCATS_STRING = new LuaCatsElementType("LCATS_STRING");
    IElementType LCATS_SYMBOL = new LuaCatsElementType("LCATS_SYMBOL");
    IElementType LCATS_TAG = new LuaCatsElementType("LCATS_TAG");
    IElementType LCATS_KEYWORD = new LuaCatsElementType("LCATS_KEYWORD");
    IElementType LCATS_CODE = new LuaCatsElementType("LCATS_CODE");
    IElementType LCATS_NUMBER = new LuaCatsElementType("LCATS_NUMBER");

    IElementType LCATS_BAD_CHARACTER = TokenType.BAD_CHARACTER;

    TokenSet LUACATS_TOKENS = TokenSet.create(
            LCATS_DASHES,
            LCATS_WHITESPACE,
            LCATS_TEXT,
            LCATS_NAME,
            LCATS_STRING,
            LCATS_SYMBOL,
            LCATS_TAG,
            LCATS_KEYWORD,
            LCATS_CODE,
            LCATS_NUMBER
    );
}
