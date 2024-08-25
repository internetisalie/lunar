/*
 * Copyright 2009 Max Ishchenko
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.internetisalie.lunar.lang.lexer;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: Max
 * Date: 06.07.2009
 * Time: 16:40:05
 */
public class LuaSyntaxHighlighter extends SyntaxHighlighterBase {
    public static final TextAttributesKey LOCAL_VAR =
            TextAttributesKey.createTextAttributesKey("LUA_LOCAL_VAR", DefaultLanguageHighlighterColors.LOCAL_VARIABLE);
    public static final TextAttributesKey UPVAL = TextAttributesKey.createTextAttributesKey("UPVAL", LOCAL_VAR);
    public static final TextAttributesKey PARAMETER =
            TextAttributesKey.createTextAttributesKey("LUA_PARAMETER", DefaultLanguageHighlighterColors.PARAMETER);
    public static final TextAttributesKey GLOBAL_VAR =
            TextAttributesKey.createTextAttributesKey("LUA_GLOBAL_VAR", DefaultLanguageHighlighterColors.GLOBAL_VARIABLE);
    public static final TextAttributesKey FIELD =
            TextAttributesKey.createTextAttributesKey("LUA_FIELD", DefaultLanguageHighlighterColors.STATIC_FIELD);

    public static final TextAttributesKey TAIL_CALL =
            TextAttributesKey.createTextAttributesKey("LUA_TAIL_CALL", HighlighterColors.TEXT);

    public static final TextAttributesKey KEYWORD =
            TextAttributesKey.createTextAttributesKey("LUA_KEYWORD", DefaultLanguageHighlighterColors.KEYWORD);

    public static final TextAttributesKey COMMENT =
            TextAttributesKey.createTextAttributesKey("LUA_COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT);
    public static final TextAttributesKey LONGCOMMENT =
            TextAttributesKey.createTextAttributesKey("LUA_LONGCOMMENT", DefaultLanguageHighlighterColors.BLOCK_COMMENT);
    public static final TextAttributesKey LONGCOMMENT_BRACES = TextAttributesKey
            .createTextAttributesKey("LUA_LONGCOMMENT_BRACES", DefaultLanguageHighlighterColors.BLOCK_COMMENT);

    public static final TextAttributesKey NUMBER =
            TextAttributesKey.createTextAttributesKey("LUA_NUMBER", DefaultLanguageHighlighterColors.NUMBER);

    public static final TextAttributesKey STRING =
            TextAttributesKey.createTextAttributesKey("LUA_STRING", DefaultLanguageHighlighterColors.STRING);
    public static final TextAttributesKey LONGSTRING =
            TextAttributesKey.createTextAttributesKey("LUA_LONGSTRING", DefaultLanguageHighlighterColors.STRING);
    public static final TextAttributesKey LONGSTRING_BRACES =
            TextAttributesKey.createTextAttributesKey("LUA_LONGSTRING_BRACES", DefaultLanguageHighlighterColors.STRING);

    public static final TextAttributesKey BRACKETS =
            TextAttributesKey.createTextAttributesKey("LUA_BRACKETS", DefaultLanguageHighlighterColors.BRACKETS);
    public static final TextAttributesKey BRACES =
            TextAttributesKey.createTextAttributesKey("LUA_BRACES", DefaultLanguageHighlighterColors.BRACES);
    public static final TextAttributesKey PARENTHESES =
            TextAttributesKey.createTextAttributesKey("LUA_PARENTHS", DefaultLanguageHighlighterColors.PARENTHESES);
    public static final TextAttributesKey BAD_CHARACTER =
            TextAttributesKey.createTextAttributesKey("LUA_BAD_CHARACTER", HighlighterColors.BAD_CHARACTER);
    public static final TextAttributesKey OPERATORS =
            TextAttributesKey.createTextAttributesKey("LUA_OPERATORS", DefaultLanguageHighlighterColors.OPERATION_SIGN);
    public static final TextAttributesKey COMMA =
            TextAttributesKey.createTextAttributesKey("LUA_COMMA", DefaultLanguageHighlighterColors.COMMA);

    public static final TextAttributesKey SEMI =
            TextAttributesKey.createTextAttributesKey("LUA_SEMICOLON", DefaultLanguageHighlighterColors.SEMICOLON);

    public static final TextAttributesKey DEFINED_CONSTANTS =
            TextAttributesKey.createTextAttributesKey("LUA_DEFINED_CONSTANTS", DefaultLanguageHighlighterColors.CONSTANT);

    private final TextAttributesKey[] BAD_CHARACTER_KEYS = new TextAttributesKey[]{HighlighterColors.BAD_CHARACTER};

    private final Map<IElementType, TextAttributesKey> colors = new HashMap<IElementType, TextAttributesKey>();

    public LuaSyntaxHighlighter() {
        colors.put(LuaTokenTypes.LONGCOMMENT, LuaSyntaxHighlighter.LONGCOMMENT);
        colors.put(LuaTokenTypes.LONGCOMMENT_BEGIN, LuaSyntaxHighlighter.LONGCOMMENT_BRACES);
        colors.put(LuaTokenTypes.LONGCOMMENT_END, LuaSyntaxHighlighter.LONGCOMMENT_BRACES);
        colors.put(LuaTokenTypes.SHORTCOMMENT, LuaSyntaxHighlighter.COMMENT);
        colors.put(LuaTokenTypes.SHEBANG, LuaSyntaxHighlighter.COMMENT);
        colors.put(LuaTokenTypes.LUADOC_COMMENT, LuaSyntaxHighlighter.COMMENT);

        colors.put(LuaTokenTypes.STRING, LuaSyntaxHighlighter.STRING);
        colors.put(LuaTokenTypes.LONGSTRING, LuaSyntaxHighlighter.LONGSTRING);
        colors.put(LuaTokenTypes.LONGSTRING_BEGIN, LuaSyntaxHighlighter.LONGSTRING_BRACES);
        colors.put(LuaTokenTypes.LONGSTRING_END, LuaSyntaxHighlighter.LONGSTRING_BRACES);

        fillMap(colors, LuaTokenTypes.OPERATORS_SET, LuaSyntaxHighlighter.OPERATORS);
        fillMap(colors, LuaTokenTypes.KEYWORDS, LuaSyntaxHighlighter.KEYWORD);
        fillMap(colors, LuaTokenTypes.PARENS, LuaSyntaxHighlighter.PARENTHESES);
        fillMap(colors, LuaTokenTypes.BRACES, LuaSyntaxHighlighter.BRACES);
        fillMap(colors, LuaTokenTypes.BRACKS, LuaSyntaxHighlighter.BRACKETS);

        colors.put(LuaTokenTypes.SEMI, LuaSyntaxHighlighter.SEMI);

        fillMap(colors, LuaTokenTypes.BAD_INPUT, LuaSyntaxHighlighter.BAD_CHARACTER);
        fillMap(colors, LuaTokenTypes.DEFINED_CONSTANTS, LuaSyntaxHighlighter.DEFINED_CONSTANTS);
        colors.put(LuaTokenTypes.COMMA, LuaSyntaxHighlighter.COMMA);
        colors.put(LuaTokenTypes.NUMBER, LuaSyntaxHighlighter.NUMBER);
    }

    @NotNull
    public Lexer getHighlightingLexer() {
        return new LuaLexer();
    }

    @NotNull
    public TextAttributesKey @NotNull [] getTokenHighlights(IElementType tokenType) {
        return pack(colors.get(tokenType));
    }

}
